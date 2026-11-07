package com.example.tools.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import com.example.file.FileLayer
import com.example.file.FileNameSanitizer
import com.example.pdf.PdfFileHelper
import com.example.pdf.PdfVerifier
import com.example.tools.common.FileResult
import com.example.tools.common.JobContext
import com.example.tools.common.JobResult
import com.tom_roush.pdfbox.cos.COSBase
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.PDResources
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.graphics.form.PDFormXObject
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException

data class PdfCompressorOptions(
  val mode: CompressionMode = CompressionMode.OPTIMIZE_IMAGES,
  val preset: CompressionPreset = CompressionPreset.BALANCED
)

object PdfCompressorHelper {

  suspend fun compressPdf(
    context: Context,
    cachedPdf: File,
    originalName: String,
    options: PdfCompressorOptions,
    jobContext: JobContext
  ): Pair<JobResult, CompressionStats> = withContext(Dispatchers.IO) {
    if (!cachedPdf.exists() || cachedPdf.length() == 0L) {
      throw IOException("Input PDF file not found or empty.")
    }

    val origSize = cachedPdf.length()
    val cacheDir = PdfFileHelper.getPdfCacheDir(context)
    val baseName = FileNameSanitizer.sanitizeFileName(originalName.substringBeforeLast('.'))
    val outFile = File(cacheDir, "${baseName}_compressed_${System.currentTimeMillis()}.pdf")
    jobContext.trackPartialOutput(outFile)

    var stats = CompressionStats(
      originalSizeBytes = origSize,
      compressedSizeBytes = 0L,
      mode = options.mode
    )

    when (options.mode) {
      CompressionMode.OPTIMIZE_IMAGES -> {
        stats = runModeAOptimizeImages(
          context = context,
          cachedPdf = cachedPdf,
          outFile = outFile,
          preset = options.preset,
          jobContext = jobContext
        )
      }
      CompressionMode.PAGES_TO_IMAGES -> {
        stats = runModeBPagesToImages(
          context = context,
          cachedPdf = cachedPdf,
          outFile = outFile,
          preset = options.preset,
          jobContext = jobContext
        )
      }
    }

    jobContext.reportProgress(98, "Verifying compressed document…", 1, 1, outFile.name)
    val isValid = PdfVerifier.verifyPdfOutput(outFile)
    if (!isValid) {
      throw IOException("Compressed PDF failed verification or structure check.")
    }

    val finalSize = outFile.length()
    val finalStats = stats.copy(compressedSizeBytes = finalSize)

    val message = if (finalStats.isSmaller) {
      "Reduced by ${finalStats.percentageReduction}%"
    } else {
      "No reduction possible for this file"
    }

    val fileResult = FileResult(
      inputName = originalName,
      outputName = outFile.name,
      outputUri = outFile.absolutePath,
      outputSizeBytes = finalSize,
      originalSizeBytes = origSize,
      success = true,
      tempFilePath = outFile.absolutePath,
      userMessage = message
    )

    Pair(JobResult(files = listOf(fileResult)), finalStats)
  }

  /**
   * Mode A: Walks resources and Form XObjects, re-compresses embedded raster images if smaller.
   */
  private suspend fun runModeAOptimizeImages(
    context: Context,
    cachedPdf: File,
    outFile: File,
    preset: CompressionPreset,
    jobContext: JobContext
  ): CompressionStats {
    val tempDir = FileLayer.getTempFolder(context)
    val doc = PdfFileHelper.openDocument(cachedPdf, tempDir)
    var recompressedCount = 0
    var skippedCount = 0

    try {
      val visitedCOS = mutableSetOf<COSBase>()
      val totalPages = doc.numberOfPages

      for (pageIndex in 0 until totalPages) {
        jobContext.ensureActive()
        val percent = (pageIndex * 90) / totalPages
        jobContext.reportProgress(
          percent = percent,
          stage = "Optimizing page ${pageIndex + 1} of $totalPages…",
          currentIndex = pageIndex + 1,
          totalFiles = totalPages,
          currentFileName = "Page ${pageIndex + 1}"
        )

        val page = doc.getPage(pageIndex)
        val resources = page.resources ?: continue

        val (rec, skip) = walkAndOptimizeResources(doc, resources, visitedCOS, preset)
        recompressedCount += rec
        skippedCount += skip
      }

      jobContext.reportProgress(92, "Saving optimized document…", totalPages, totalPages, outFile.name)
      doc.save(outFile)
    } finally {
      try {
        doc.close()
      } catch (_: Exception) {}
    }

    return CompressionStats(
      originalSizeBytes = cachedPdf.length(),
      compressedSizeBytes = outFile.length(),
      imagesRecompressed = recompressedCount,
      imagesSkipped = skippedCount,
      mode = CompressionMode.OPTIMIZE_IMAGES
    )
  }

  private fun walkAndOptimizeResources(
    doc: PDDocument,
    resources: PDResources,
    visitedCOS: MutableSet<COSBase>,
    preset: CompressionPreset
  ): Pair<Int, Int> {
    var recompressed = 0
    var skipped = 0

    for (xName in resources.xObjectNames) {
      try {
        val xobj = resources.getXObject(xName) ?: continue
        val cosObject = xobj.cosObject
        if (cosObject != null && !visitedCOS.add(cosObject)) {
          // Guard against cycles
          continue
        }

        if (xobj is PDFormXObject) {
          xobj.resources?.let { nestedRes ->
            val (rec, skip) = walkAndOptimizeResources(doc, nestedRes, visitedCOS, preset)
            recompressed += rec
            skipped += skip
          }
        } else if (xobj is PDImageXObject) {
          // Check if this is a plain DCT or Flate RGB/Gray image without SMask, Mask, or ImageMask
          val hasSMask = xobj.cosObject.containsKey(COSName.SMASK)
          val hasMask = xobj.cosObject.containsKey(COSName.MASK)
          val isImageMask = xobj.cosObject.getBoolean(COSName.IMAGE_MASK, false)
          if (hasSMask || hasMask || isImageMask) {
            skipped++
            continue
          }

          val csName = xobj.colorSpace?.name ?: ""
          if (csName != "DeviceRGB" && csName != "DeviceGray" && csName != "DeviceCMYK") {
            skipped++
            continue
          }

          val origBytes: Long = xobj.cosObject.getInt(COSName.LENGTH, 0).toLong()
          val width = xobj.width
          val height = xobj.height
          val maxEdge = maxOf(width, height)

          var bmp: Bitmap? = null
          var scaledBmp: Bitmap? = null
          try {
            bmp = xobj.image
            if (bmp == null) {
              skipped++
              continue
            }

            // Downscale if exceeds preset maxEdgePx
            val targetBmp = if (maxEdge > preset.maxEdgePx) {
              val scale = preset.maxEdgePx.toFloat() / maxEdge.toFloat()
              val newW = (width * scale).toInt().coerceAtLeast(1)
              val newH = (height * scale).toInt().coerceAtLeast(1)
              scaledBmp = Bitmap.createScaledBitmap(bmp, newW, newH, true)
              scaledBmp
            } else {
              bmp
            }

            val baos = ByteArrayOutputStream()
            targetBmp.compress(Bitmap.CompressFormat.JPEG, preset.jpegQuality, baos)
            val newBytes = baos.toByteArray()

            if (origBytes > 0L && newBytes.size.toLong() < origBytes) {
              val newPdImage = JPEGFactory.createFromByteArray(doc, newBytes)
              resources.put(xName, newPdImage)
              recompressed++
            } else {
              skipped++
            }
          } catch (_: Exception) {
            skipped++
          } finally {
            scaledBmp?.recycle()
            bmp?.recycle()
          }
        }
      } catch (_: Exception) {
        skipped++
      }
    }

    return recompressed to skipped
  }

  /**
   * Mode B: Maximum reduction. Renders every page to JPEG at preset DPI and reconstructs PDF.
   */
  private suspend fun runModeBPagesToImages(
    context: Context,
    cachedPdf: File,
    outFile: File,
    preset: CompressionPreset,
    jobContext: JobContext
  ): CompressionStats = withContext(Dispatchers.IO) {
    val newDoc = PDDocument()
    var totalPages = 0

    try {
      PdfFileHelper.pdfRendererMutex.withLock {
        var pfd: ParcelFileDescriptor? = null
        var renderer: PdfRenderer? = null
        try {
          pfd = ParcelFileDescriptor.open(cachedPdf, ParcelFileDescriptor.MODE_READ_ONLY)
          renderer = PdfRenderer(pfd)
          totalPages = renderer.pageCount

          for (pageIndex in 0 until totalPages) {
            jobContext.ensureActive()
            val percent = (pageIndex * 90) / totalPages
            jobContext.reportProgress(
              percent = percent,
              stage = "Converting page ${pageIndex + 1} of $totalPages to image…",
              currentIndex = pageIndex + 1,
              totalFiles = totalPages,
              currentFileName = "Page ${pageIndex + 1}"
            )

            var page: PdfRenderer.Page? = null
            var bmp: Bitmap? = null
            try {
              page = renderer.openPage(pageIndex)
              val origWPt = page.width
              val origHPt = page.height

              val (pixelW, pixelH) = PdfToImagesMath.calculatePixelDimensions(origWPt, origHPt, preset.modeBDpi)
              bmp = Bitmap.createBitmap(pixelW, pixelH, Bitmap.Config.ARGB_8888)
              bmp.eraseColor(Color.WHITE)
              page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

              val baos = ByteArrayOutputStream()
              bmp.compress(Bitmap.CompressFormat.JPEG, preset.jpegQuality, baos)
              val jpegBytes = baos.toByteArray()

              val pdImage = JPEGFactory.createFromByteArray(newDoc, jpegBytes)
              val pdPage = PDPage(PDRectangle(origWPt.toFloat(), origHPt.toFloat()))
              newDoc.addPage(pdPage)

              val contentStream = PDPageContentStream(newDoc, pdPage)
              contentStream.drawImage(pdImage, 0f, 0f, origWPt.toFloat(), origHPt.toFloat())
              contentStream.close()
            } finally {
              try { page?.close() } catch (_: Exception) {}
              bmp?.recycle()
            }
          }
        } finally {
          try { renderer?.close() } catch (_: Exception) {}
          try { pfd?.close() } catch (_: Exception) {}
        }
      }

      jobContext.reportProgress(92, "Writing compressed document…", totalPages, totalPages, outFile.name)
      newDoc.save(outFile)
    } finally {
      try {
        newDoc.close()
      } catch (_: Exception) {}
    }

    CompressionStats(
      originalSizeBytes = cachedPdf.length(),
      compressedSizeBytes = outFile.length(),
      imagesRecompressed = totalPages,
      imagesSkipped = 0,
      mode = CompressionMode.PAGES_TO_IMAGES
    )
  }
}
