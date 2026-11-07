package com.example.pdf

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.core.content.FileProvider
import com.example.file.FileLayer
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

data class SaveFolderResult(
  val successCount: Int,
  val failedCount: Int,
  val failedFiles: List<String>
)

object MultiOutputSaver {

  private const val BUFFER_SIZE = 64 * 1024 // 64 KB

  /**
   * (a) Packages all output files into a single ZIP archive in cache.
   */
  fun createZipArchive(
    context: Context,
    outputFiles: List<File>,
    archiveBaseName: String = "split_pages"
  ): File {
    val tempDir = FileLayer.getTempFolder(context)
    val zipFile = File(tempDir, "${archiveBaseName}_${UUID.randomUUID().toString().take(6)}.zip")

    FileOutputStream(zipFile).use { fos ->
      BufferedOutputStream(fos, BUFFER_SIZE).use { bos ->
        ZipOutputStream(bos, StandardCharsets.UTF_8).use { zos ->
          val buffer = ByteArray(BUFFER_SIZE)
          val usedNames = mutableSetOf<String>()

          for (file in outputFiles) {
            if (!file.exists() || file.length() == 0L) continue

            // Ensure unique entry names inside ZIP
            var entryName = file.name
            var counter = 1
            val base = entryName.substringBeforeLast('.')
            val ext = entryName.substringAfterLast('.', "")
            while (!usedNames.add(entryName)) {
              entryName = if (ext.isNotEmpty()) "${base}_$counter.$ext" else "${base}_$counter"
              counter++
            }

            val entry = ZipEntry(entryName)
            entry.time = file.lastModified()
            entry.size = file.length()
            zos.putNextEntry(entry)

            FileInputStream(file).use { fis ->
              BufferedInputStream(fis, BUFFER_SIZE).use { bis ->
                var read: Int
                while (bis.read(buffer).also { read = it } != -1) {
                  zos.write(buffer, 0, read)
                }
              }
            }
            zos.closeEntry()
          }
        }
      }
    }

    return zipFile
  }

  /**
   * (b) Saves multiple files into a user-selected SAF folder (OpenDocumentTree).
   * Generates unique names with numeric suffixes on collision.
   * Tracks per-file success and failures.
   */
  fun saveToFolder(
    context: Context,
    treeUri: Uri,
    files: List<File>,
    mimeType: String = "application/pdf"
  ): SaveFolderResult {
    var successCount = 0
    var failedCount = 0
    val failedFiles = mutableListOf<String>()

    val docUri = DocumentsContract.buildDocumentUriUsingTree(
      treeUri,
      DocumentsContract.getTreeDocumentId(treeUri)
    )

    val buffer = ByteArray(BUFFER_SIZE)

    for (file in files) {
      if (!file.exists()) {
        failedCount++
        failedFiles.add("${file.name} (File not found)")
        continue
      }

      var targetDocUri: Uri? = null
      try {
        targetDocUri = DocumentsContract.createDocument(
          context.contentResolver,
          docUri,
          mimeType,
          file.name
        ) ?: throw IllegalStateException("Cannot create document")

        context.contentResolver.openOutputStream(targetDocUri)?.use { outStream ->
          BufferedOutputStream(outStream, BUFFER_SIZE).use { bos ->
            FileInputStream(file).use { fis ->
              BufferedInputStream(fis, BUFFER_SIZE).use { bis ->
                var read: Int
                while (bis.read(buffer).also { read = it } != -1) {
                  bos.write(buffer, 0, read)
                }
                bos.flush()
              }
            }
          }
        } ?: throw IllegalStateException("Cannot open output stream")

        successCount++
      } catch (e: Exception) {
        failedCount++
        failedFiles.add("${file.name} (${e.localizedMessage ?: "Failed to write"})")
        // If document was created partially, try to clean it up
        if (targetDocUri != null) {
          try {
            DocumentsContract.deleteDocument(context.contentResolver, targetDocUri)
          } catch (_: Exception) {}
        }
      }
    }

    return SaveFolderResult(
      successCount = successCount,
      failedCount = failedCount,
      failedFiles = failedFiles
    )
  }

  /**
   * (c) Shares multiple files via ACTION_SEND_MULTIPLE.
   */
  fun shareAll(
    context: Context,
    files: List<File>,
    mimeType: String = "application/pdf",
    chooserTitle: String = "Share files"
  ): com.example.file.FileOpResult {
    val validFiles = files.filter { it.exists() && it.length() > 0 }
    if (validFiles.isEmpty()) return com.example.file.FileOpResult.FAILED

    return try {
      val uris = ArrayList<Uri>()
      for (file in validFiles) {
        uris.add(FileLayer.getFileProviderUri(context, file))
      }

      val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
        type = mimeType
        putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
      }

      val chooser = Intent.createChooser(intent, chooserTitle).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      }
      context.startActivity(chooser)
      com.example.file.FileOpResult.SUCCESS
    } catch (_: ActivityNotFoundException) {
      FileLayer.notifyFileOpResult(context, com.example.file.FileOpResult.NO_APP_FOUND, isShare = true)
      com.example.file.FileOpResult.NO_APP_FOUND
    } catch (_: Exception) {
      FileLayer.notifyFileOpResult(context, com.example.file.FileOpResult.FAILED, isShare = true)
      com.example.file.FileOpResult.FAILED
    }
  }

  /**
   * (d) Saves multiple image files to device gallery under Pictures/TOOLSNEST (API 29+).
   */
  fun saveImagesToGallery(
    context: Context,
    files: List<File>,
    mimeType: String = "image/png"
  ): SaveFolderResult {
    var successCount = 0
    var failedCount = 0
    val failedFiles = mutableListOf<String>()

    for (file in files) {
      if (!file.exists()) {
        failedCount++
        failedFiles.add("${file.name} (File not found)")
        continue
      }

      val uri = FileLayer.saveImageToMediaStore(
        context = context,
        sourceFile = file,
        displayName = file.name,
        mimeType = mimeType
      )

      if (uri != null) {
        successCount++
      } else {
        failedCount++
        failedFiles.add("${file.name} (Gallery save unavailable on this device version)")
      }
    }

    return SaveFolderResult(
      successCount = successCount,
      failedCount = failedCount,
      failedFiles = failedFiles
    )
  }
}
