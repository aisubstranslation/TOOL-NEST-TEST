package com.example.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.example.file.FileLayer
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.pdmodel.PDDocument
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.UUID

object PdfFileHelper {

  private const val BUFFER_SIZE = 64 * 1024 // 64 KB
  val pdfRendererMutex = Mutex()

  /**
   * Dedicated cache directory for PDF temporary working files.
   */
  fun getPdfCacheDir(context: Context): File {
    val dir = File(FileLayer.getTempFolder(context), "pdf_working")
    if (!dir.exists()) {
      dir.mkdirs()
    }
    return dir
  }

  /**
   * Safely copies a PDF from a content URI to a unique local cache file.
   * Performs pre-checks:
   * 1. File size <= 150 MB
   * 2. Free storage >= 1.2x input size
   */
  fun copyPdfToCache(context: Context, uri: Uri): File {
    val info = FileLayer.getUriFileInfo(context, uri)
    if (info.sizeBytes > PdfLimits.MAX_PDF_SIZE_BYTES) {
      throw IOException(PdfErrors.MSG_EXCEEDS_SIZE_LIMIT)
    }

    val cacheDir = getPdfCacheDir(context)
    if (!PdfLimits.hasSufficientStorage(cacheDir, info.sizeBytes)) {
      throw IOException(PdfErrors.MSG_NOT_ENOUGH_STORAGE)
    }

    val uniqueName = "pdf_${UUID.randomUUID()}_${info.displayName.replace("[^a-zA-Z0-9._-]".toRegex(), "_")}"
    val targetFile = File(cacheDir, if (uniqueName.endsWith(".pdf", ignoreCase = true)) uniqueName else "$uniqueName.pdf")

    context.contentResolver.openInputStream(uri)?.use { inStream ->
      BufferedInputStream(inStream, BUFFER_SIZE).use { bis ->
        FileOutputStream(targetFile).use { fos ->
          BufferedOutputStream(fos, BUFFER_SIZE).use { bos ->
            val buffer = ByteArray(BUFFER_SIZE)
            var bytesRead: Int
            while (bis.read(buffer).also { bytesRead = it } != -1) {
              bos.write(buffer, 0, bytesRead)
            }
            bos.flush()
          }
        }
      }
    } ?: throw IOException("Unable to open input stream for selected PDF.")

    if (!targetFile.exists() || targetFile.length() == 0L) {
      targetFile.delete()
      throw IOException(PdfErrors.MSG_DAMAGED_PDF)
    }

    return targetFile
  }

  /**
   * Opens a PDF file safely using PDFBox with disk-backed temp settings.
   */
  fun openDocument(file: File, tempDir: File): PDDocument {
    val memorySetting = MemoryUsageSetting.setupTempFileOnly().setTempDir(tempDir)
    return PDDocument.load(file, memorySetting)
  }

  /**
   * Renders a single page of a PDF document as a thumbnail bitmap.
   * Guarded with [pdfRendererMutex] for thread safety.
   * Fills white background before rendering.
   */
  suspend fun renderPageThumbnail(
    context: Context,
    pdfFile: File,
    pageIndex: Int,
    maxDimensionPx: Int = 300
  ): Bitmap? {
    if (!pdfFile.exists() || pdfFile.length() == 0L) return null
    return pdfRendererMutex.withLock {
      var pfd: ParcelFileDescriptor? = null
      var renderer: PdfRenderer? = null
      var page: PdfRenderer.Page? = null
      try {
        pfd = ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY)
        renderer = PdfRenderer(pfd)
        if (pageIndex < 0 || pageIndex >= renderer.pageCount) return null
        page = renderer.openPage(pageIndex)

        val srcW = page.width
        val srcH = page.height
        val scale = if (srcW > srcH) {
          maxDimensionPx.toFloat() / srcW.toFloat()
        } else {
          maxDimensionPx.toFloat() / srcH.toFloat()
        }.coerceAtMost(1.0f)

        val destW = (srcW * scale).toInt().coerceAtLeast(1)
        val destH = (srcH * scale).toInt().coerceAtLeast(1)

        val bitmap = Bitmap.createBitmap(destW, destH, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.WHITE)
        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
        bitmap
      } catch (_: Exception) {
        null
      } finally {
        try {
          page?.close()
        } catch (_: Exception) {}
        try {
          renderer?.close()
        } catch (_: Exception) {}
        try {
          pfd?.close()
        } catch (_: Exception) {}
      }
    }
  }

  /**
   * Deletes a temporary file safely.
   */
  fun deleteQuietly(file: File?) {
    if (file != null && file.exists()) {
      runCatching { file.delete() }
    }
  }
}
