package com.example.file

import android.content.ActivityNotFoundException
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.core.content.FileProvider
import com.example.R
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

data class UriFileInfo(
  val displayName: String,
  val sizeBytes: Long,
  val mimeType: String
)

enum class FileOpResult {
  SUCCESS,
  NO_APP_FOUND,
  FAILED;

  val isSuccess: Boolean get() = this == SUCCESS
}

object FileLayer {

  private const val BUFFER_SIZE = 64 * 1024 // 64 KB

  /**
   * Resolves the MIME type for a file from its extension with safe fallbacks.
   */
  fun getMimeType(file: File): String {
    val ext = file.extension.lowercase()
    if (ext.isEmpty()) return "*/*"
    return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: when (ext) {
      "pdf" -> "application/pdf"
      "zip" -> "application/zip"
      "png" -> "image/png"
      "jpg", "jpeg" -> "image/jpeg"
      "webp" -> "image/webp"
      "m4a", "aac" -> "audio/mp4"
      "wav" -> "audio/wav"
      "mp4" -> "video/mp4"
      else -> "*/*"
    }
  }

  /**
   * Returns the dedicated working cache directory: cacheDir/toolsnest
   */
  fun getTempFolder(context: Context): File {
    val dir = File(context.cacheDir, OutputDirs.DIR_TOOLSNEST)
    if (!dir.exists()) {
      dir.mkdirs()
    }
    return dir
  }

  /**
   * Cleans up temporary working directories, protecting any paths/filenames in [protectedPaths].
   * Deletes recursively inside temporary folders while respecting protected files.
   * Returns the total bytes deleted.
   */
  fun cleanupTempFolder(cacheDir: File, protectedPaths: Set<String> = emptySet()): Long {
    var freedBytes = 0L
    for (subDirName in OutputDirs.ALL_OUTPUT_DIRS) {
      val dir = File(cacheDir, subDirName)
      if (dir.exists()) {
        freedBytes += deleteUnprotectedRecursively(dir, protectedPaths)
      }
    }
    return freedBytes
  }

  fun cleanupTempFolder(context: Context, protectedPaths: Set<String> = emptySet()): Long {
    return cleanupTempFolder(context.cacheDir, protectedPaths)
  }

  private fun deleteUnprotectedRecursively(fileOrDir: File, protectedPaths: Set<String>): Long {
    if (protectedPaths.contains(fileOrDir.absolutePath) || protectedPaths.contains(fileOrDir.name)) {
      return 0L
    }
    var freed = 0L
    if (fileOrDir.isDirectory) {
      fileOrDir.listFiles()?.forEach { child ->
        freed += deleteUnprotectedRecursively(child, protectedPaths)
      }
      val isRootSubDir = OutputDirs.ALL_OUTPUT_DIRS.any { it == fileOrDir.name }
      if (!isRootSubDir && fileOrDir.listFiles()?.isEmpty() == true) {
        fileOrDir.delete()
      }
    } else {
      val size = fileOrDir.length()
      if (fileOrDir.delete()) {
        freed += size
      }
    }
    return freed
  }

  /**
   * Calculates current total bytes used across all temporary output directories.
   */
  fun getTempFolderSize(cacheDir: File): Long {
    var total = 0L
    for (subDirName in OutputDirs.ALL_OUTPUT_DIRS) {
      val dir = File(cacheDir, subDirName)
      if (dir.exists()) {
        total += calculateFolderSize(dir)
      }
    }
    return total
  }

  fun getTempFolderSize(context: Context): Long {
    return getTempFolderSize(context.cacheDir)
  }

  private fun calculateFolderSize(fileOrDir: File): Long {
    if (!fileOrDir.exists()) return 0L
    if (fileOrDir.isDirectory) {
      return fileOrDir.listFiles()?.sumOf { calculateFolderSize(it) } ?: 0L
    }
    return fileOrDir.length()
  }

  fun getFreeSpaceBytes(context: Context): Long {
    return getTempFolder(context).usableSpace
  }

  /**
   * Resolves display name, size, and MIME type for any content:// or file:// URI
   * strictly through ContentResolver without assuming filesystem paths.
   */
  fun getUriFileInfo(context: Context, uri: Uri): UriFileInfo {
    var name = "unknown_file"
    var size = 0L
    var mime = context.contentResolver.getType(uri) ?: "application/octet-stream"

    try {
      context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)

        if (cursor.moveToFirst()) {
          if (nameIndex != -1 && !cursor.isNull(nameIndex)) {
            name = cursor.getString(nameIndex)
          }
          if (sizeIndex != -1 && !cursor.isNull(sizeIndex)) {
            size = cursor.getLong(sizeIndex)
          }
        }
      }
    } catch (_: Exception) {
      // Fallback to last path segment if query fails
      name = uri.lastPathSegment ?: "unknown_file"
    }

    // If size couldn't be retrieved via cursor, try opening descriptor
    if (size <= 0L) {
      try {
        context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
          size = pfd.statSize
        }
      } catch (_: Exception) {
        // Size remains 0L
      }
    }

    return UriFileInfo(
      displayName = name,
      sizeBytes = size,
      mimeType = mime
    )
  }

  /**
   * Copies a local file into a target content URI (e.g. from SAF CreateDocument)
   * using a streamed 64 KB buffer.
   */
  fun saveFileToUri(context: Context, sourceFile: File, targetUri: Uri): Boolean {
    if (!sourceFile.exists()) return false

    return try {
      context.contentResolver.openOutputStream(targetUri)?.use { outStream ->
        FileInputStream(sourceFile).use { inStream ->
          val buffer = ByteArray(BUFFER_SIZE)
          var bytesRead: Int
          while (inStream.read(buffer).also { bytesRead = it } != -1) {
            outStream.write(buffer, 0, bytesRead)
          }
          outStream.flush()
        }
      }
      true
    } catch (_: Exception) {
      false
    }
  }

  /**
   * Saves an image directly to MediaStore under Pictures/TOOLSNEST.
   * On API 29+, this requires zero storage permissions.
   * On API 26-28, returns null (reporting unavailable so SAF can be used).
   */
  fun saveImageToMediaStore(
    context: Context,
    sourceFile: File,
    displayName: String,
    mimeType: String = "image/jpeg"
  ): Uri? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
      // On API 26-28, MediaStore writes without permissions are not supported; return null
      return null
    }

    if (!sourceFile.exists()) return null

    val values = ContentValues().apply {
      put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
      put(MediaStore.Images.Media.MIME_TYPE, mimeType)
      put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/TOOLSNEST")
      put(MediaStore.Images.Media.IS_PENDING, 1)
    }

    val resolver = context.contentResolver
    val collectionUri = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
    val itemUri = resolver.insert(collectionUri, values) ?: return null

    return try {
      resolver.openOutputStream(itemUri)?.use { outStream ->
        FileInputStream(sourceFile).use { inStream ->
          val buffer = ByteArray(BUFFER_SIZE)
          var bytesRead: Int
          while (inStream.read(buffer).also { bytesRead = it } != -1) {
            outStream.write(buffer, 0, bytesRead)
          }
          outStream.flush()
        }
      }

      values.clear()
      values.put(MediaStore.Images.Media.IS_PENDING, 0)
      resolver.update(itemUri, values, null, null)
      itemUri
    } catch (_: Exception) {
      resolver.delete(itemUri, null, null)
      null
    }
  }

  /**
   * Saves a video directly to MediaStore under Movies/TOOLSNEST.
   * Scoped Storage (API 29+) requires zero runtime storage permissions for app-created media.
   */
  fun saveVideoToMediaStore(
    context: Context,
    sourceFile: File,
    displayName: String,
    mimeType: String = "video/mp4"
  ): Uri? {
    if (!sourceFile.exists()) return null
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
      return null
    }

    val values = ContentValues().apply {
      put(MediaStore.Video.Media.DISPLAY_NAME, displayName)
      put(MediaStore.Video.Media.MIME_TYPE, mimeType)
      put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/TOOLSNEST")
      put(MediaStore.Video.Media.IS_PENDING, 1)
    }

    val resolver = context.contentResolver
    val collectionUri = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
    val itemUri = resolver.insert(collectionUri, values) ?: return null

    return try {
      resolver.openOutputStream(itemUri)?.use { out ->
        FileInputStream(sourceFile).use { input ->
          val buffer = ByteArray(BUFFER_SIZE)
          var read: Int
          while (input.read(buffer).also { read = it } != -1) {
            out.write(buffer, 0, read)
          }
          out.flush()
        }
      }

      values.clear()
      values.put(MediaStore.Video.Media.IS_PENDING, 0)
      resolver.update(itemUri, values, null, null)
      itemUri
    } catch (_: Exception) {
      try { resolver.delete(itemUri, null, null) } catch (_: Exception) {}
      null
    }
  }

  /**
   * Creates a secure FileProvider content URI for sharing or viewing.
   */
  fun getFileProviderUri(context: Context, file: File): Uri {
    val authority = "${context.packageName}.fileprovider"
    return FileProvider.getUriForFile(context, authority, file)
  }

  /**
   * Shares a file via the standard Android Sharesheet using FileProvider.
   * Returns FileOpResult indicating success, no app found, or failure.
   */
  fun shareFile(
    context: Context,
    file: File,
    mimeType: String = getMimeType(file),
    chooserTitle: String = context.getString(R.string.common_share)
  ): FileOpResult {
    if (!file.exists()) return FileOpResult.FAILED
    return try {
      val uri = getFileProviderUri(context, file)
      val intent = Intent(Intent.ACTION_SEND).apply {
        type = mimeType
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
      }
      val chooser = Intent.createChooser(intent, chooserTitle).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      }
      context.startActivity(chooser)
      FileOpResult.SUCCESS
    } catch (_: ActivityNotFoundException) {
      FileOpResult.NO_APP_FOUND
    } catch (_: IllegalArgumentException) {
      FileOpResult.FAILED
    } catch (_: SecurityException) {
      FileOpResult.FAILED
    } catch (_: Exception) {
      FileOpResult.FAILED
    }
  }

  /**
   * Launches ACTION_VIEW for a file with read grant.
   * Returns FileOpResult indicating success, no app found, or failure.
   */
  fun openFile(
    context: Context,
    file: File,
    mimeType: String = getMimeType(file)
  ): FileOpResult {
    if (!file.exists()) return FileOpResult.FAILED
    return try {
      val uri = getFileProviderUri(context, file)
      val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, mimeType)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      }
      context.startActivity(intent)
      FileOpResult.SUCCESS
    } catch (_: ActivityNotFoundException) {
      FileOpResult.NO_APP_FOUND
    } catch (_: IllegalArgumentException) {
      FileOpResult.FAILED
    } catch (_: SecurityException) {
      FileOpResult.FAILED
    } catch (_: Exception) {
      FileOpResult.FAILED
    }
  }

  fun notifyFileOpResult(context: Context, result: FileOpResult, isShare: Boolean) {
    when (result) {
      FileOpResult.SUCCESS -> {}
      FileOpResult.NO_APP_FOUND -> {
        Toast.makeText(context, context.getString(R.string.error_no_app_to_open), Toast.LENGTH_SHORT).show()
      }
      FileOpResult.FAILED -> {
        val resId = if (isShare) R.string.error_could_not_share else R.string.error_no_app_to_open
        Toast.makeText(context, context.getString(resId), Toast.LENGTH_SHORT).show()
      }
    }
  }

  fun shareFileAndNotify(
    context: Context,
    file: File,
    mimeType: String = getMimeType(file),
    chooserTitle: String = context.getString(R.string.common_share)
  ): FileOpResult {
    val res = shareFile(context, file, mimeType, chooserTitle)
    notifyFileOpResult(context, res, isShare = true)
    return res
  }

  fun openFileAndNotify(
    context: Context,
    file: File,
    mimeType: String = getMimeType(file)
  ): FileOpResult {
    val res = openFile(context, file, mimeType)
    notifyFileOpResult(context, res, isShare = false)
    return res
  }
}
