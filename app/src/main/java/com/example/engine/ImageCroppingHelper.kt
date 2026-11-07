package com.example.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import com.example.file.FileNameSanitizer
import com.example.tools.common.FileResult
import com.example.tools.common.JobContext
import com.example.tools.crop.CropMath
import com.example.tools.crop.CropRect
import com.example.tools.crop.CropRectInt
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import kotlin.math.max

object ImageCroppingHelper {

  suspend fun cropAndSave(
    context: Context,
    uri: Uri,
    originalName: String,
    cropRect: CropRect,
    outputFormat: OutputFormat,
    quality: Int,
    jobContext: JobContext,
    targetCacheDir: File
  ): FileResult {
    jobContext.ensureActive()

    var originalSizeBytes = 0L
    var croppedRawBitmap: Bitmap? = null
    var orientedCroppedBitmap: Bitmap? = null
    var flattenedBitmap: Bitmap? = null
    var partialOutputFile: File? = null

    try {
      val meta = ImageEngine.readImageMetadata(context, uri)
      originalSizeBytes = meta.sizeBytes
      jobContext.reportProgress(
        percent = 15,
        stage = "Preparing crop region...",
        currentIndex = 1,
        totalFiles = 1,
        currentFileName = originalName
      )

      // Map oriented source pixel crop to raw image coordinates
      val rawCrop = CropMath.mapOrientedRectToRaw(
        rect = cropRect,
        rawW = meta.width,
        rawH = meta.height,
        orientationDegrees = meta.orientationDegrees,
        isFlipped = meta.isFlipped
      )

      val safeRawLeft = CropMath.safeCoerce(rawCrop.left, 0, max(0, meta.width - 1))
      val safeRawTop = CropMath.safeCoerce(rawCrop.top, 0, max(0, meta.height - 1))
      val safeRawWidth = CropMath.safeCoerce(rawCrop.width, 1, meta.width - safeRawLeft)
      val safeRawHeight = CropMath.safeCoerce(rawCrop.height, 1, meta.height - safeRawTop)
      val rawRegionRect = Rect(safeRawLeft, safeRawTop, safeRawLeft + safeRawWidth, safeRawTop + safeRawHeight)

      jobContext.ensureActive()
      jobContext.reportProgress(
        percent = 30,
        stage = "Decoding crop region...",
        currentIndex = 1,
        totalFiles = 1,
        currentFileName = originalName
      )

      // 1. Try decoding ONLY the crop region with BitmapRegionDecoder at full resolution
      val decodeOptions = BitmapFactory.Options().apply {
        inPreferredConfig = Bitmap.Config.ARGB_8888
        inMutable = false
      }

      var regionDecoder: BitmapRegionDecoder? = null
      try {
        regionDecoder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
          context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapRegionDecoder.newInstance(stream)
          }
        } else {
          @Suppress("DEPRECATION")
          context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapRegionDecoder.newInstance(stream, false)
          }
        }
      } catch (_: Throwable) {
        regionDecoder = null
      }

      if (regionDecoder != null) {
        try {
          croppedRawBitmap = regionDecoder.decodeRegion(rawRegionRect, decodeOptions)
        } catch (_: Throwable) {
          croppedRawBitmap = null
        } finally {
          if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            @Suppress("DEPRECATION")
            regionDecoder.recycle()
          }
        }
      }

      // 2. Fall back to full decode if BitmapRegionDecoder could not open or decode the format
      if (croppedRawBitmap == null) {
        val fullBitmap = context.contentResolver.openInputStream(uri)?.use { stream ->
          BitmapFactory.decodeStream(stream, null, decodeOptions)
        } ?: throw IllegalArgumentException("Could not decode image bitmap")

        try {
          val actualLeft = CropMath.safeCoerce(rawRegionRect.left, 0, max(0, fullBitmap.width - 1))
          val actualTop = CropMath.safeCoerce(rawRegionRect.top, 0, max(0, fullBitmap.height - 1))
          val actualWidth = CropMath.safeCoerce(rawRegionRect.width(), 1, fullBitmap.width - actualLeft)
          val actualHeight = CropMath.safeCoerce(rawRegionRect.height(), 1, fullBitmap.height - actualTop)
          croppedRawBitmap = Bitmap.createBitmap(fullBitmap, actualLeft, actualTop, actualWidth, actualHeight)
        } finally {
          fullBitmap.recycle()
        }
      }

      jobContext.ensureActive()
      jobContext.reportProgress(
        percent = 55,
        stage = "Orienting crop selection...",
        currentIndex = 1,
        totalFiles = 1,
        currentFileName = originalName
      )

      val rawPiece = croppedRawBitmap ?: throw IllegalStateException("Cropped bitmap is null")

      // 3. Apply EXIF orientation/flip to the small cropped piece only
      orientedCroppedBitmap = if (meta.orientationDegrees != 0 || meta.isFlipped) {
        val matrix = Matrix()
        if (meta.isFlipped) {
          matrix.postScale(-1f, 1f)
        }
        if (meta.orientationDegrees != 0) {
          matrix.postRotate(meta.orientationDegrees.toFloat())
        }
        val transformed = Bitmap.createBitmap(
          rawPiece,
          0,
          0,
          rawPiece.width,
          rawPiece.height,
          matrix,
          true
        )
        if (transformed != rawPiece) {
          rawPiece.recycle()
          croppedRawBitmap = null
        }
        transformed
      } else {
        rawPiece
      }

      jobContext.ensureActive()

      val orientedPiece = orientedCroppedBitmap ?: throw IllegalStateException("Oriented bitmap is null")
      val outWidth = orientedPiece.width
      val outHeight = orientedPiece.height

      // 4. Flatten transparency for JPEG into an ARGB_8888 bitmap (NOT RGB_565)
      val finalBitmap = if (outputFormat == OutputFormat.JPEG && orientedPiece.hasAlpha()) {
        val opaque = Bitmap.createBitmap(outWidth, outHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(opaque)
        canvas.drawColor(Color.WHITE)
        canvas.drawBitmap(orientedPiece, 0f, 0f, Paint(Paint.FILTER_BITMAP_FLAG))
        orientedPiece.recycle()
        orientedCroppedBitmap = null
        flattenedBitmap = opaque
        opaque
      } else {
        orientedPiece
      }

      jobContext.ensureActive()
      jobContext.reportProgress(
        percent = 75,
        stage = "Compressing output...",
        currentIndex = 1,
        totalFiles = 1,
        currentFileName = originalName
      )

      val ext = when (outputFormat) {
        OutputFormat.JPEG -> "jpg"
        OutputFormat.PNG -> "png"
        OutputFormat.WEBP -> "webp"
      }

      val sanitizedName = FileNameSanitizer.sanitizeFileName(
        rawName = originalName,
        prefix = "CROP_",
        extensionOverride = ext
      )

      if (!targetCacheDir.exists()) {
        targetCacheDir.mkdirs()
      }

      val existingNames = targetCacheDir.list()?.toSet() ?: emptySet()
      val uniqueName = FileNameSanitizer.generateUniqueFileName(sanitizedName, existingNames)
      val outputFile = File(targetCacheDir, uniqueName)
      partialOutputFile = outputFile
      jobContext.trackPartialOutput(outputFile)

      val compressFormat = when (outputFormat) {
        OutputFormat.JPEG -> Bitmap.CompressFormat.JPEG
        OutputFormat.PNG -> Bitmap.CompressFormat.PNG
        OutputFormat.WEBP -> {
          if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Bitmap.CompressFormat.WEBP_LOSSY
          } else {
            @Suppress("DEPRECATION")
            Bitmap.CompressFormat.WEBP
          }
        }
      }

      val actualQuality = when (outputFormat) {
        OutputFormat.PNG -> 100
        else -> CropMath.safeCoerce(quality, 10, 100)
      }

      FileOutputStream(outputFile).use { fos ->
        val success = finalBitmap.compress(compressFormat, actualQuality, fos)
        if (!success) {
          throw IllegalStateException("Bitmap compression failed")
        }
        fos.flush()
      }

      // Verification
      if (!outputFile.exists() || outputFile.length() <= 0L) {
        throw IllegalStateException("Verification failed: output file is empty or missing")
      }

      val headerBytes = ByteArray(16)
      FileInputStream(outputFile).use { fis ->
        fis.read(headerBytes)
      }

      val formatStr = when (outputFormat) {
        OutputFormat.JPEG -> "jpeg"
        OutputFormat.PNG -> "png"
        OutputFormat.WEBP -> "webp"
      }

      if (!ImageMath.verifyMagicBytes(headerBytes, formatStr)) {
        throw IllegalStateException("Verification failed: invalid magic bytes for $formatStr")
      }

      val verifyOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
      BitmapFactory.decodeFile(outputFile.absolutePath, verifyOptions)

      if (verifyOptions.outWidth != outWidth || verifyOptions.outHeight != outHeight) {
        throw IllegalStateException("Verification failed: decoded dimensions (${verifyOptions.outWidth}x${verifyOptions.outHeight}) do not match expected (${outWidth}x${outHeight})")
      }

      return FileResult(
        inputName = originalName,
        outputName = outputFile.name,
        outputUri = outputFile.absolutePath,
        outputSizeBytes = outputFile.length(),
        success = true,
        width = outWidth,
        height = outHeight,
        originalSizeBytes = originalSizeBytes,
        format = ext,
        tempFilePath = outputFile.absolutePath
      )
    } catch (oom: OutOfMemoryError) {
      partialOutputFile?.delete()
      return FileResult(
        inputName = originalName,
        outputName = "",
        outputUri = "",
        outputSizeBytes = 0L,
        success = false,
        userMessage = "Not enough memory to crop this image. Try a smaller crop or close background apps.",
        originalSizeBytes = originalSizeBytes
      )
    } catch (e: Throwable) {
      partialOutputFile?.delete()
      throw e
    } finally {
      flattenedBitmap?.recycle()
      orientedCroppedBitmap?.recycle()
      croppedRawBitmap?.recycle()
    }
  }

  suspend fun cropAndSave(
    context: Context,
    uri: Uri,
    originalName: String,
    cropRect: CropRectInt,
    outputFormat: OutputFormat,
    quality: Int,
    jobContext: JobContext,
    targetCacheDir: File
  ): FileResult = cropAndSave(
    context = context,
    uri = uri,
    originalName = originalName,
    cropRect = CropRect(
      cropRect.left.toFloat(),
      cropRect.top.toFloat(),
      (cropRect.left + cropRect.width).toFloat(),
      (cropRect.top + cropRect.height).toFloat()
    ),
    outputFormat = outputFormat,
    quality = quality,
    jobContext = jobContext,
    targetCacheDir = targetCacheDir
  )
}
