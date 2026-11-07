package com.example.tools.zip

import android.content.Context
import android.net.Uri
import com.example.tools.common.FileResult
import com.example.tools.common.JobContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

data class ZipInputEntry(
  val uri: Uri?,
  val localFile: File? = null,
  val relativePath: String,
  val sizeBytes: Long
)

enum class ZipCompressionMode {
  NORMAL, // Deflated
  STORE   // No compression
}

object ZipWriterHelper {
  const val MAX_ENTRIES = 5000
  const val MAX_TOTAL_BYTES = (1.5 * 1024 * 1024 * 1024).toLong() // 1.5 GB
  const val BUFFER_SIZE = 64 * 1024 // 64 KB

  /**
   * Probes each input entry to see if its stream can be opened.
   * Returns list of unreadable items (path to error message).
   */
  fun probeInputEntries(context: Context, entries: List<ZipInputEntry>): List<Pair<String, String>> {
    val unreadable = mutableListOf<Pair<String, String>>()
    for (entry in entries) {
      try {
        val stream = openEntryStream(context, entry)
        if (stream == null) {
          unreadable.add(entry.relativePath to "Cannot open stream")
        } else {
          stream.close()
        }
      } catch (e: Exception) {
        unreadable.add(entry.relativePath to (e.message ?: "Unreadable file"))
      }
    }
    return unreadable
  }

  fun openEntryStream(context: Context?, entry: ZipInputEntry): InputStream? {
    return if (entry.localFile != null) {
      FileInputStream(entry.localFile)
    } else if (entry.uri != null && context != null) {
      context.contentResolver.openInputStream(entry.uri)
    } else {
      null
    }
  }

  /**
   * Writes the ZIP archive streamed to a temporary cache file and verifies it before returning.
   */
  suspend fun createAndVerifyZip(
    context: Context?,
    entries: List<ZipInputEntry>,
    destinationTempFile: File,
    compressionMode: ZipCompressionMode,
    jobContext: JobContext
  ): FileResult {
    jobContext.ensureActive()
    jobContext.trackPartialOutput(destinationTempFile)

    // Check free disk space
    val totalInputBytes = entries.sumOf { it.sizeBytes }
    val freeSpace = destinationTempFile.parentFile?.usableSpace ?: Long.MAX_VALUE
    if (freeSpace < totalInputBytes) {
      throw IllegalStateException("Insufficient free disk space in cache (need at least $totalInputBytes bytes, available $freeSpace bytes)")
    }

    // Resolve duplicate paths
    val originalPaths = entries.map { it.relativePath }
    val sanitizedPaths = ZipEntrySanitizer.resolveDuplicatePaths(originalPaths)

    var bytesWrittenAll = 0L
    val totalItems = entries.size

    val buffer = ByteArray(BUFFER_SIZE)

    FileOutputStream(destinationTempFile).use { fos ->
      BufferedOutputStream(fos, BUFFER_SIZE).use { bos ->
        ZipOutputStream(bos, StandardCharsets.UTF_8).use { zos ->
          val method = if (compressionMode == ZipCompressionMode.NORMAL) {
            ZipEntry.DEFLATED
          } else {
            ZipEntry.STORED
          }
          zos.setMethod(method)

          for (i in entries.indices) {
            jobContext.ensureActive()
            val entry = entries[i]
            val path = sanitizedPaths[i]

            // If destination file is accidentally included in entries, skip
            if (entry.localFile?.absolutePath == destinationTempFile.absolutePath) {
              continue
            }

            jobContext.reportProgress(
              percent = if (totalInputBytes > 0) ((bytesWrittenAll.toDouble() / totalInputBytes.toDouble()) * 100).toInt().coerceIn(0, 99) else null,
              stage = "Archiving $path...",
              currentIndex = i + 1,
              totalFiles = totalItems,
              currentFileName = path
            )

            if (compressionMode == ZipCompressionMode.STORE) {
              // Precompute CRC and size for STORED mode
              val crcCalculator = CRC32()
              var exactSize = 0L
              openEntryStream(context, entry)?.use { stream ->
                BufferedInputStream(stream, BUFFER_SIZE).use { bis ->
                  var read: Int
                  while (bis.read(buffer).also { read = it } != -1) {
                    crcCalculator.update(buffer, 0, read)
                    exactSize += read
                  }
                }
              }

              val zipEntry = ZipEntry(path).apply {
                setMethod(ZipEntry.STORED)
                size = exactSize
                compressedSize = exactSize
                crc = crcCalculator.value
              }
              zos.putNextEntry(zipEntry)

              openEntryStream(context, entry)?.use { stream ->
                BufferedInputStream(stream, BUFFER_SIZE).use { bis ->
                  var read: Int
                  while (bis.read(buffer).also { read = it } != -1) {
                    jobContext.ensureActive()
                    zos.write(buffer, 0, read)
                    bytesWrittenAll += read
                  }
                }
              }
              zos.closeEntry()
            } else {
              // DEFLATED mode
              val zipEntry = ZipEntry(path)
              zos.putNextEntry(zipEntry)

              openEntryStream(context, entry)?.use { stream ->
                BufferedInputStream(stream, BUFFER_SIZE).use { bis ->
                  var read: Int
                  while (bis.read(buffer).also { read = it } != -1) {
                    jobContext.ensureActive()
                    zos.write(buffer, 0, read)
                    bytesWrittenAll += read
                  }
                }
              }
              zos.closeEntry()
            }
          }
          zos.finish()
          zos.flush()
        }
      }
    }

    jobContext.ensureActive()

    // VERIFICATION: Reopen with java.util.zip.ZipFile
    if (!destinationTempFile.exists() || destinationTempFile.length() <= 0L) {
      throw IllegalStateException("Verification failed: output ZIP archive is missing or empty")
    }

    val zipFile = ZipFile(destinationTempFile)
    try {
      if (zipFile.size() != entries.size) {
        throw IllegalStateException("Verification failed: expected ${entries.size} entries, found ${zipFile.size()}")
      }

      val verifyBuffer = ByteArray(BUFFER_SIZE)
      val zipEntries = zipFile.entries()
      while (zipEntries.hasMoreElements()) {
        jobContext.ensureActive()
        val zEntry = zipEntries.nextElement()
        val computedCrc = CRC32()
        var readBytesTotal = 0L

        zipFile.getInputStream(zEntry).use { zis ->
          var readCount: Int
          while (zis.read(verifyBuffer).also { readCount = it } != -1) {
            computedCrc.update(verifyBuffer, 0, readCount)
            readBytesTotal += readCount
          }
        }

        if (zEntry.crc != -1L && computedCrc.value != zEntry.crc) {
          throw IllegalStateException("Verification failed: CRC mismatch on entry ${zEntry.name}")
        }
        if (zEntry.size != -1L && readBytesTotal != zEntry.size) {
          throw IllegalStateException("Verification failed: Size mismatch on entry ${zEntry.name}")
        }
      }
    } finally {
      zipFile.close()
    }

    val finalArchiveSize = destinationTempFile.length()

    return FileResult(
      inputName = "${entries.size} files",
      outputName = destinationTempFile.name,
      outputUri = destinationTempFile.absolutePath,
      outputSizeBytes = finalArchiveSize,
      success = true,
      originalSizeBytes = totalInputBytes,
      format = "zip",
      tempFilePath = destinationTempFile.absolutePath
    )
  }
}
