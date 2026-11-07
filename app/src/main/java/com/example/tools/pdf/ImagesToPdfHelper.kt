package com.example.tools.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import com.example.file.FileLayer
import com.example.file.FileNameSanitizer
import com.example.pdf.PdfFileHelper
import com.example.pdf.PdfVerifier
import com.example.tools.common.FileResult
import com.example.tools.common.JobContext
import com.example.tools.common.JobResult
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.File
import java.io.IOException

data class ImagePdfInputItem(
  val uri: Uri,
  val name: String,
  val sizeBytes: Long,
  val rotationDegrees: Int = 0
)

data class ImagesToPdfOptions(
  val pageSize: PageSizeOption = PageSizeOption.A4,
  val orientation: OrientationOption = OrientationOption.AUTO,
  val margin: MarginOption = MarginOption.NONE,
  val quality: QualityOption = QualityOption.HIGH,
  val customFileName: String? = null
)

object ImagesToPdfHelper {

  const val MAX_IMAGE_COUNT = 50

  suspend fun convertImagesToPdf(
    context: Context,
    images: List<ImagePdfInputItem>,
    options: ImagesToPdfOptions,
    jobContext: JobContext
  ): JobResult = withContext(Dispatchers.IO) {
    if (images.isEmpty()) {
      throw IllegalArgumentException("No images selected to convert to PDF")
    }
    if (images.size > MAX_IMAGE_COUNT) {
      throw IllegalArgumentException("Maximum $MAX_IMAGE_COUNT images allowed")
    }

    val cacheDir = PdfFileHelper.getPdfCacheDir(context)
    val baseName = options.customFileName?.trim()?.ifEmpty { null }
      ?: images.first().name.substringBeforeLast('.')
    val safeBase = FileNameSanitizer.sanitizeFileName(baseName)
    val outFile = File(cacheDir, "${safeBase}_${System.currentTimeMillis()}.pdf")
    jobContext.trackPartialOutput(outFile)

    val doc = PDDocument()
    try {
      val marginPt = ImagesToPdfLayout.getMarginPoints(options.margin)
      val (targetDpi, jpegQuality) = ImagesToPdfLayout.getQualityCompressionParams(options.quality)

      for ((index, item) in images.withIndex()) {
        jobContext.ensureActive()
        val percent = (index * 100) / images.size
        jobContext.reportProgress(
          percent = percent,
          stage = "Processing image ${index + 1} of ${images.size}…",
          currentIndex = index + 1,
          totalFiles = images.size,
          currentFileName = item.name
        )

        try {
          processSingleImageToPage(
            context = context,
            doc = doc,
            item = item,
            options = options,
            marginPt = marginPt,
            jpegQuality = jpegQuality
          )
        } catch (oom: OutOfMemoryError) {
          throw IOException("Out of memory while processing image '${item.name}'. Please choose Medium quality or smaller images.")
        }
      }

      jobContext.ensureActive()
      jobContext.reportProgress(95, "Finalizing PDF document…", images.size, images.size, outFile.name)
      doc.save(outFile)
    } finally {
      try {
        doc.close()
      } catch (_: Exception) {}
    }

    jobContext.reportProgress(99, "Verifying output document…", images.size, images.size, outFile.name)
    val isValid = PdfVerifier.verifyPdfOutput(outFile, expectedPageCount = images.size)
    if (!isValid) {
      throw IOException("Generated PDF failed verification or was corrupted.")
    }

    val fileResult = FileResult(
      inputName = "${images.size} Images",
      outputName = outFile.name,
      outputUri = outFile.absolutePath,
      outputSizeBytes = outFile.length(),
      success = true,
      tempFilePath = outFile.absolutePath
    )

    JobResult(files = listOf(fileResult))
  }

  private fun processSingleImageToPage(
    context: Context,
    doc: PDDocument,
    item: ImagePdfInputItem,
    options: ImagesToPdfOptions,
    marginPt: Float,
    jpegQuality: Float
  ) {
    val info = FileLayer.getUriFileInfo(context, item.uri)
    val isJpeg = info.mimeType.equals("image/jpeg", ignoreCase = true) ||
      info.mimeType.equals("image/jpg", ignoreCase = true) ||
      item.name.endsWith(".jpg", ignoreCase = true) ||
      item.name.endsWith(".jpeg", ignoreCase = true)

    var exifOrientation = ExifInterface.ORIENTATION_NORMAL
    if (isJpeg) {
      try {
        context.contentResolver.openInputStream(item.uri)?.use { stream ->
          val exif = ExifInterface(stream)
          exifOrientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        }
      } catch (_: Exception) {}
    }

    val isNormalExif = exifOrientation == ExifInterface.ORIENTATION_NORMAL ||
      exifOrientation == ExifInterface.ORIENTATION_UNDEFINED
    val hasNoRotation = item.rotationDegrees == 0 && isNormalExif

    var pdImage: PDImageXObject? = null
    var imgW: Int = 0
    var imgH: Int = 0

    if (isJpeg && hasNoRotation && options.quality == QualityOption.HIGH) {
      // Direct embedding of untouched JPEG bytes
      try {
        context.contentResolver.openInputStream(item.uri)?.use { inStream ->
          BufferedInputStream(inStream).use { bis ->
            pdImage = JPEGFactory.createFromStream(doc, bis)
            imgW = pdImage!!.width
            imgH = pdImage!!.height
          }
        }
      } catch (_: Exception) {
        pdImage = null
      }
    }

    // Fallback or non-JPEG / rotated / medium quality: decode, rotate, flatten transparency, and compress
    if (pdImage == null) {
      val (decodedBmp, w, h) = decodeAndPrepareBitmap(context, item, options)
      try {
        imgW = w
        imgH = h
        pdImage = JPEGFactory.createFromImage(doc, decodedBmp, jpegQuality)
      } finally {
        decodedBmp.recycle()
      }
    }

    val pageDim = ImagesToPdfLayout.determinePageDimensions(
      pageSize = options.pageSize,
      orientation = options.orientation,
      imageWidth = imgW,
      imageHeight = imgH,
      marginPt = marginPt
    )

    val placement = ImagesToPdfLayout.calculateImagePlacement(
      pageWidthPt = pageDim.pageWidthPt,
      pageHeightPt = pageDim.pageHeightPt,
      marginPt = marginPt,
      imageWidth = imgW,
      imageHeight = imgH
    )

    val page = PDPage(PDRectangle(placement.pageWidthPt, placement.pageHeightPt))
    doc.addPage(page)

    val contentStream = PDPageContentStream(doc, page)
    try {
      contentStream.drawImage(
        pdImage,
        placement.drawX,
        placement.drawY,
        placement.drawWidth,
        placement.drawHeight
      )
    } finally {
      contentStream.close()
    }
  }

  private fun decodeAndPrepareBitmap(
    context: Context,
    item: ImagePdfInputItem,
    options: ImagesToPdfOptions
  ): Triple<Bitmap, Int, Int> {
    // 1. Read raw bounds
    val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.contentResolver.openInputStream(item.uri)?.use { stream ->
      BitmapFactory.decodeStream(stream, null, boundsOptions)
    } ?: throw IOException("Cannot open stream for ${item.name}")

    val rawW = boundsOptions.outWidth
    val rawH = boundsOptions.outHeight
    if (rawW <= 0 || rawH <= 0) {
      throw IOException("Invalid image dimensions for ${item.name}")
    }

    // Read EXIF orientation
    var exifDegrees = 0
    try {
      context.contentResolver.openInputStream(item.uri)?.use { stream ->
        val exif = ExifInterface(stream)
        val orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        exifDegrees = when (orientation) {
          ExifInterface.ORIENTATION_ROTATE_90 -> 90
          ExifInterface.ORIENTATION_ROTATE_180 -> 180
          ExifInterface.ORIENTATION_ROTATE_270 -> 270
          else -> 0
        }
      }
    } catch (_: Exception) {}

    val totalRotation = (exifDegrees + item.rotationDegrees) % 360

    // Determine oriented dimensions
    val (orientedW, orientedH) = if (totalRotation == 90 || totalRotation == 270) {
      rawH to rawW
    } else {
      rawW to rawH
    }

    // Calculate max pixel bounds based on quality preset
    val targetMaxPixels = when (options.quality) {
      QualityOption.HIGH -> 3500 // ~300 DPI on A4
      QualityOption.MEDIUM -> 1800 // ~150 DPI on A4
    }

    var inSampleSize = 1
    val maxRawEdge = maxOf(rawW, rawH)
    while (maxRawEdge / (inSampleSize * 2) >= targetMaxPixels) {
      inSampleSize *= 2
    }

    val decodeOptions = BitmapFactory.Options().apply {
      this.inSampleSize = inSampleSize
      inPreferredConfig = Bitmap.Config.ARGB_8888
    }

    val rawBitmap = context.contentResolver.openInputStream(item.uri)?.use { stream ->
      BitmapFactory.decodeStream(stream, null, decodeOptions)
    } ?: throw IOException("Failed to decode image ${item.name}")

    // Apply rotation and downscaling if needed
    val matrix = Matrix()
    if (totalRotation != 0) {
      matrix.postRotate(totalRotation.toFloat())
    }

    val finalRotated = if (totalRotation != 0) {
      val rotated = Bitmap.createBitmap(rawBitmap, 0, 0, rawBitmap.width, rawBitmap.height, matrix, true)
      if (rotated != rawBitmap) {
        rawBitmap.recycle()
      }
      rotated
    } else {
      rawBitmap
    }

    // Flatten transparency onto solid white
    val flattened = Bitmap.createBitmap(finalRotated.width, finalRotated.height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(flattened)
    canvas.drawColor(Color.WHITE)
    canvas.drawBitmap(finalRotated, 0f, 0f, null)

    if (flattened != finalRotated) {
      finalRotated.recycle()
    }

    return Triple(flattened, flattened.width, flattened.height)
  }
}
