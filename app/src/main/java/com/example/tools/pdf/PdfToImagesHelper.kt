package com.example.tools.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import com.example.pdf.PdfFileHelper
import com.example.tools.common.FileResult
import com.example.tools.common.JobContext
import com.example.tools.common.JobResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

data class PdfToImagesOptions(
  val format: String = "PNG", // "PNG" or "JPEG"
  val jpegQuality: Int = 90,
  val dpi: Int = 150
)

object PdfToImagesHelper {

  suspend fun convertPdfToImages(
    context: Context,
    cachedPdf: File,
    originalName: String,
    pageIndices: List<Int>,
    options: PdfToImagesOptions,
    jobContext: JobContext
  ): JobResult = withContext(Dispatchers.IO) {
    if (!cachedPdf.exists() || cachedPdf.length() == 0L) {
      throw IOException("Selected PDF file is missing or empty.")
    }
    if (pageIndices.isEmpty()) {
      throw IllegalArgumentException("No pages specified for extraction.")
    }

    val cacheDir = PdfFileHelper.getPdfCacheDir(context)
    val baseName = originalName.substringBeforeLast('.')
    val isJpeg = options.format.equals("JPEG", ignoreCase = true) || options.format.equals("JPG", ignoreCase = true)
    val ext = if (isJpeg) "jpg" else "png"
    val compressFormat = if (isJpeg) Bitmap.CompressFormat.JPEG else Bitmap.CompressFormat.PNG
    val compressQuality = if (isJpeg) options.jpegQuality.coerceIn(10, 100) else 100

    val results = mutableListOf<FileResult>()

    // Run PdfRenderer guarded with mutex
    PdfFileHelper.pdfRendererMutex.withLock {
      var pfd: ParcelFileDescriptor? = null
      var renderer: PdfRenderer? = null

      try {
        pfd = ParcelFileDescriptor.open(cachedPdf, ParcelFileDescriptor.MODE_READ_ONLY)
        renderer = PdfRenderer(pfd)
        val totalPagesInDoc = renderer.pageCount

        for ((idx, pageIdx) in pageIndices.withIndex()) {
          jobContext.ensureActive()
          val percent = ((idx.toFloat() / pageIndices.size) * 100).toInt()
          jobContext.reportProgress(
            percent = percent,
            stage = "Rendering page ${pageIdx + 1} (${idx + 1}/${pageIndices.size})…",
            currentIndex = idx + 1,
            totalFiles = pageIndices.size,
            currentFileName = "Page ${pageIdx + 1}"
          )

          if (pageIdx < 0 || pageIdx >= totalPagesInDoc) {
            continue
          }

          var page: PdfRenderer.Page? = null
          var bitmap: Bitmap? = null
          try {
            page = renderer.openPage(pageIdx)
            val (pixelW, pixelH) = PdfToImagesMath.calculatePixelDimensions(page.width, page.height, options.dpi)

            // Megapixels safety check
            if ((pixelW.toLong() * pixelH.toLong()) > PdfToImagesMath.MAX_PIXELS_PER_PAGE) {
              throw IOException("Page ${pageIdx + 1} exceeds 16 megapixels at ${options.dpi} DPI. Please select a lower DPI.")
            }

            try {
              bitmap = Bitmap.createBitmap(pixelW, pixelH, Bitmap.Config.ARGB_8888)
            } catch (oom: OutOfMemoryError) {
              throw IOException("Out of memory while rendering page ${pageIdx + 1} at ${options.dpi} DPI. Please choose a lower DPI.", oom)
            }
            bitmap.eraseColor(Color.WHITE)
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

            val isBlank = PdfToImagesMath.isUniformColor(bitmap)

            val outFileName = PdfToImagesMath.formatPageImageName(baseName, pageIdx, ext)
            val outFile = File(cacheDir, outFileName)
            jobContext.trackPartialOutput(outFile)

            FileOutputStream(outFile).use { fos ->
              BufferedOutputStream(fos, 64 * 1024).use { bos ->
                bitmap.compress(compressFormat, compressQuality, bos)
                bos.flush()
              }
            }

            // Verify rendered output image file
            if (!outFile.exists() || outFile.length() == 0L) {
              throw IOException("Failed to write image for page ${pageIdx + 1}")
            }

            // Quick decode check
            val decodeOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(outFile.absolutePath, decodeOptions)
            if (decodeOptions.outWidth <= 0 || decodeOptions.outHeight <= 0) {
              throw IOException("Rendered image for page ${pageIdx + 1} is invalid")
            }

            results.add(
              FileResult(
                inputName = "Page ${pageIdx + 1}",
                outputName = outFile.name,
                outputUri = outFile.absolutePath,
                outputSizeBytes = outFile.length(),
                success = true,
                tempFilePath = outFile.absolutePath,
                width = pixelW,
                height = pixelH,
                format = ext.uppercase(),
                userMessage = if (isBlank) "Page appears blank or solid color" else null
              )
            )
          } finally {
            try {
              page?.close()
            } catch (_: Exception) {}
            bitmap?.recycle()
          }
        }
      } finally {
        try {
          renderer?.close()
        } catch (_: Exception) {}
        try {
          pfd?.close()
        } catch (_: Exception) {}
      }
    }

    jobContext.reportProgress(100, "Rendering complete", pageIndices.size, pageIndices.size, "")
    JobResult(files = results)
  }
}
