package com.example.tools.file

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import android.webkit.MimeTypeMap
import androidx.exifinterface.media.ExifInterface
import com.example.R
import com.example.file.FileFormatters
import com.example.file.FileLayer
import java.io.BufferedInputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipInputStream

data class FilePropertyItem(
  val labelRes: Int,
  val value: String
)

data class FilePropertiesResult(
  val uriString: String,
  val fileName: String,
  val extension: String,
  val mimeType: String,
  val sizeBytes: Long,
  val formattedSize: String,
  val lastModified: String,
  val categoryProperties: List<FilePropertyItem>
)

object FileInfoAnalyzer {

  fun analyzeFile(context: Context, uri: Uri): FilePropertiesResult {
    val notAvailable = context.getString(R.string.tool6_not_available)
    val basicInfo = FileLayer.getUriFileInfo(context, uri)
    val displayName = basicInfo.displayName.ifEmpty { "unknown_file" }
    val extension = displayName.substringAfterLast('.', "").lowercase()

    val detectedMime = context.contentResolver.getType(uri)
      ?: (if (extension.isNotEmpty()) MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension) else null)
      ?: "application/octet-stream"

    var lastModifiedFormatted = notAvailable
    runCatching {
      context.contentResolver.query(
        uri,
        arrayOf(DocumentsContract.Document.COLUMN_LAST_MODIFIED),
        null,
        null,
        null
      )?.use { cursor ->
        val modIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED)
        if (modIndex != -1 && cursor.moveToFirst() && !cursor.isNull(modIndex)) {
          val millis = cursor.getLong(modIndex)
          if (millis > 0) {
            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            lastModifiedFormatted = sdf.format(Date(millis))
          }
        }
      }
    }

    val categoryList = mutableListOf<FilePropertyItem>()

    // 1. Image inspection
    val isImage = detectedMime.startsWith("image/") ||
        extension in listOf("jpg", "jpeg", "png", "webp", "gif", "bmp", "heic", "heif")

    if (isImage) {
      val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
      runCatching {
        context.contentResolver.openInputStream(uri)?.use { stream ->
          BitmapFactory.decodeStream(BufferedInputStream(stream), null, options)
        }
      }

      val dims = if (options.outWidth > 0 && options.outHeight > 0) {
        "${options.outWidth} × ${options.outHeight} px"
      } else {
        notAvailable
      }
      categoryList.add(FilePropertyItem(R.string.tool6_property_dimensions, dims))

      val formatStr = options.outMimeType ?: detectedMime
      categoryList.add(FilePropertyItem(R.string.tool6_property_format, formatStr))

      var orientationText = notAvailable
      runCatching {
        context.contentResolver.openInputStream(uri)?.use { stream ->
          val exif = ExifInterface(stream)
          val orientation = exif.getAttributeInt(
            ExifInterface.TAG_ORIENTATION,
            ExifInterface.ORIENTATION_UNDEFINED
          )
          orientationText = when (orientation) {
            ExifInterface.ORIENTATION_NORMAL -> "Normal (0°)"
            ExifInterface.ORIENTATION_ROTATE_90 -> "Rotate 90° CW"
            ExifInterface.ORIENTATION_ROTATE_180 -> "Rotate 180°"
            ExifInterface.ORIENTATION_ROTATE_270 -> "Rotate 270° CW"
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> "Flip horizontal"
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> "Flip vertical"
            ExifInterface.ORIENTATION_TRANSPOSE -> "Transpose"
            ExifInterface.ORIENTATION_TRANSVERSE -> "Transverse"
            ExifInterface.ORIENTATION_UNDEFINED -> "Undefined / Normal"
            else -> "Orientation $orientation"
          }
        }
      }
      categoryList.add(FilePropertyItem(R.string.tool6_property_orientation, orientationText))
    }

    // 2. Audio & Video inspection
    val isAudioOrVideo = detectedMime.startsWith("audio/") || detectedMime.startsWith("video/") ||
        extension in listOf("mp4", "mkv", "webm", "avi", "3gp", "mov", "mp3", "wav", "m4a", "aac", "ogg", "flac")

    if (isAudioOrVideo) {
      var retriever: MediaMetadataRetriever? = null
      try {
        retriever = MediaMetadataRetriever()
        retriever.setDataSource(context, uri)

        val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
        val durationStr = if (durationMs != null && durationMs > 0) {
          val totalSec = durationMs / 1000
          val min = totalSec / 60
          val sec = totalSec % 60
          val hr = min / 60
          val remMin = min % 60
          if (hr > 0) String.format(Locale.US, "%d:%02d:%02d", hr, remMin, sec)
          else String.format(Locale.US, "%d:%02d", remMin, sec)
        } else {
          notAvailable
        }
        categoryList.add(FilePropertyItem(R.string.tool6_property_duration, durationStr))

        val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
        val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
        if (width != null && height != null && width.toIntOrNull() != null && height.toIntOrNull() != null) {
          categoryList.add(
            FilePropertyItem(
              R.string.tool6_property_video_res,
              "$width × $height px"
            )
          )
        }

        val bitrate = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toLongOrNull()
        val bitrateStr = if (bitrate != null && bitrate > 0) {
          "${bitrate / 1000} kbps"
        } else {
          notAvailable
        }
        categoryList.add(FilePropertyItem(R.string.tool6_property_bitrate, bitrateStr))

        val frameRate = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE)
        if (!frameRate.isNullOrBlank()) {
          categoryList.add(FilePropertyItem(R.string.tool6_property_framerate, "$frameRate fps"))
        }
      } catch (_: Exception) {
        // Corrupted media or unreadable container handled gracefully
      } finally {
        try {
          retriever?.release()
        } catch (_: Exception) {}
      }

      var extractor: MediaExtractor? = null
      try {
        extractor = MediaExtractor()
        extractor.setDataSource(context, uri, null)
        val trackCount = extractor.trackCount
        if (trackCount > 0) {
          val trackMimes = mutableListOf<String>()
          for (i in 0 until trackCount) {
            val format = extractor.getTrackFormat(i)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: "track-$i"
            trackMimes.add(mime.substringAfter('/'))
          }
          categoryList.add(
            FilePropertyItem(
              R.string.tool6_property_tracks,
              "$trackCount (${trackMimes.joinToString(", ")})"
            )
          )
        }
      } catch (_: Exception) {
        // Corrupted tracks handled gracefully
      } finally {
        try {
          extractor?.release()
        } catch (_: Exception) {}
      }
    }

    // 3. PDF inspection
    val isPdf = detectedMime == "application/pdf" || extension == "pdf"
    if (isPdf) {
      var pfd: ParcelFileDescriptor? = null
      var renderer: PdfRenderer? = null
      var pageText = notAvailable
      try {
        pfd = context.contentResolver.openFileDescriptor(uri, "r")
        if (pfd != null) {
          renderer = PdfRenderer(pfd)
          pageText = "${renderer.pageCount}"
        }
      } catch (_: SecurityException) {
        pageText = context.getString(R.string.tool6_pdf_unreadable)
      } catch (_: Exception) {
        pageText = context.getString(R.string.tool6_pdf_unreadable)
      } finally {
        try {
          renderer?.close()
        } catch (_: Exception) {}
        try {
          pfd?.close()
        } catch (_: Exception) {}
      }
      categoryList.add(FilePropertyItem(R.string.tool6_property_pages, pageText))
    }

    // 4. ZIP inspection
    val isZip = detectedMime.contains("zip") || extension == "zip"
    if (isZip) {
      var entryCount = 0
      var readSuccess = false
      runCatching {
        context.contentResolver.openInputStream(uri)?.use { stream ->
          ZipInputStream(BufferedInputStream(stream)).use { zis ->
            while (true) {
              val entry = zis.nextEntry ?: break
              entryCount++
              zis.closeEntry()
            }
            readSuccess = true
          }
        }
      }
      val zipText = if (readSuccess) "$entryCount entries" else notAvailable
      categoryList.add(FilePropertyItem(R.string.tool6_property_zip_entries, zipText))
    }

    return FilePropertiesResult(
      uriString = uri.toString(),
      fileName = displayName,
      extension = if (extension.isNotEmpty()) ".$extension" else "None",
      mimeType = detectedMime,
      sizeBytes = basicInfo.sizeBytes,
      formattedSize = FileFormatters.formatBytes(basicInfo.sizeBytes),
      lastModified = lastModifiedFormatted,
      categoryProperties = categoryList
    )
  }

  fun formatForClipboardOrShare(context: Context, info: FilePropertiesResult): String {
    return buildString {
      appendLine("=== File Information ===")
      appendLine("Name: ${info.fileName}")
      appendLine("Extension: ${info.extension}")
      appendLine("MIME: ${info.mimeType}")
      appendLine("Size: ${info.formattedSize} (${info.sizeBytes} bytes)")
      appendLine("Last Modified: ${info.lastModified}")
      if (info.categoryProperties.isNotEmpty()) {
        appendLine("--- Detailed Properties ---")
        for (prop in info.categoryProperties) {
          val label = context.getString(prop.labelRes)
          appendLine("$label: ${prop.value}")
        }
      }
    }
  }
}
