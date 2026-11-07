package com.example.bgremove

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import android.net.Uri
import androidx.compose.ui.graphics.toArgb
import com.example.R
import com.example.engine.ImageEngine
import com.example.file.FileFormatters
import com.example.file.FileLayer
import com.example.file.FileLimits
import com.example.tools.common.FileResult
import com.example.tools.common.JobContext
import com.example.tools.common.JobResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import kotlin.math.roundToInt

data class InitialCutoutData(
  val baseMask: ByteArray, // Working mask bytes (0..255)
  val maskWidth: Int,
  val maskHeight: Int,
  val workingBitmap: Bitmap, // Working ARGB_8888 bitmap
  val originalWidth: Int,
  val originalHeight: Int,
  val originalSizeBytes: Long,
  val subjectCoverageFraction: Float = 0.5f
)

object BgRemoveHelper {

  /**
   * Pre-checks limits and runs initial background detection using U2-Net.
   */
  suspend fun detectBackground(
    context: Context,
    uri: Uri,
    jobContext: JobContext? = null,
    onProgress: (suspend (percent: Int?, stage: String) -> Unit)? = null
  ): InitialCutoutData = withContext(Dispatchers.IO) {
    onProgress?.invoke(5, "Checking image limits…")

    // 1. Check file size limit (50 MB)
    val fileInfo = FileLayer.getUriFileInfo(context, uri)
    if (fileInfo.sizeBytes > BgRemoveMath.MAX_FILE_SIZE_BYTES) {
      throw IOException("File exceeds 50 MB limit (${FileFormatters.formatBytes(fileInfo.sizeBytes)})")
    }

    // 2. Check megapixel limit (16 MP)
    val meta = ImageEngine.readImageMetadata(context, uri)
    if (!BgRemoveMath.isWithinMegapixelLimit(meta.width, meta.height)) {
      val mp = (meta.width.toDouble() * meta.height.toDouble()) / 1_000_000.0
      throw IOException("Image too large for background removal on this phone (%.1f MP). Reduce it with the Image Resizer first.".format(mp))
    }

    jobContext?.ensureActive()

    // 3. Decode oriented bitmap
    onProgress?.invoke(15, "Decoding image…")
    var orientedBitmap: Bitmap? = null
    try {
      val decodeOpts = BitmapFactory.Options().apply {
        inPreferredConfig = Bitmap.Config.ARGB_8888
        inMutable = false
      }
      val decoded = context.contentResolver.openInputStream(uri)?.use { stream ->
        BitmapFactory.decodeStream(stream, null, decodeOpts)
      } ?: throw IOException("Could not decode image")

      orientedBitmap = if (meta.orientationDegrees != 0 || meta.isFlipped) {
        val matrix = Matrix()
        if (meta.isFlipped) matrix.postScale(-1f, 1f)
        if (meta.orientationDegrees != 0) matrix.postRotate(meta.orientationDegrees.toFloat())
        val transformed = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
        if (transformed != decoded) decoded.recycle()
        transformed
      } else {
        decoded
      }
    } catch (e: OutOfMemoryError) {
      throw IOException("Out of memory decoding image. Please reduce the image size first.")
    }

    jobContext?.ensureActive()

    // 4. Calculate working resolution (min(original, 2048 on long edge))
    val longEdge = maxOf(meta.width, meta.height)
    val workingScale = if (longEdge > 2048) 2048f / longEdge.toFloat() else 1.0f
    val workingW = (meta.width * workingScale).roundToInt().coerceAtLeast(1)
    val workingH = (meta.height * workingScale).roundToInt().coerceAtLeast(1)

    val workingBitmap = if (workingW != orientedBitmap.width || workingH != orientedBitmap.height) {
      Bitmap.createScaledBitmap(orientedBitmap, workingW, workingH, true)
    } else {
      orientedBitmap
    }

    // 5. Preprocess for model (320 x 320)
    onProgress?.invoke(35, "Preparing model input…")
    val modelInputBitmap = Bitmap.createScaledBitmap(workingBitmap, 320, 320, true)
    val pixels320 = IntArray(320 * 320)
    modelInputBitmap.getPixels(pixels320, 0, 320, 0, 0, 320, 320)
    if (modelInputBitmap != workingBitmap && modelInputBitmap != orientedBitmap) {
      modelInputBitmap.recycle()
    }

    val preprocessedTensor = BgRemoveMath.preprocessRgbPixels(pixels320, 320, 320)

    jobContext?.ensureActive()

    // 6. Run ONNX Model Inference
    onProgress?.invoke(55, "Detecting subject with U2-Net…")
    val rawOutput = U2NetRunner.runInference(context, preprocessedTensor)

    jobContext?.ensureActive()

    // 7. Post-process mask
    onProgress?.invoke(85, "Generating mask…")
    val normalized = BgRemoveMath.minMaxNormalize(rawOutput)
    val maskBytes320 = BgRemoveMath.normalizedTo8BitMask(normalized)

    // Calculate subject coverage fraction from 320x320 mask
    var subjectPixelCount = 0
    for (b in maskBytes320) {
      if ((b.toInt() and 0xFF) >= 128) subjectPixelCount++
    }
    val coverageFraction = subjectPixelCount.toFloat() / maskBytes320.size.toFloat()
    android.util.Log.i("BgRemoveHelper", "Subject detection coverage: ${(coverageFraction * 100f).roundToInt()}%")

    // Explicit bilinear upscale to working resolution (pure Kotlin, no ALPHA_8 bitmaps)
    val workingMask = BgRemoveMath.bilinearUpscaleMask(maskBytes320, 320, 320, workingW, workingH)

    onProgress?.invoke(100, "Subject detected")

    InitialCutoutData(
      baseMask = workingMask,
      maskWidth = workingW,
      maskHeight = workingH,
      workingBitmap = workingBitmap,
      originalWidth = meta.width,
      originalHeight = meta.height,
      originalSizeBytes = fileInfo.sizeBytes,
      subjectCoverageFraction = coverageFraction
    )
  }

  /**
   * Exports the composited cutout image at ORIGINAL dimensions using row-by-row streaming.
   */
  suspend fun exportCutout(
    context: Context,
    inputUri: Uri,
    inputFileName: String,
    baseMask: ByteArray,
    maskW: Int,
    maskH: Int,
    strokes: List<BrushStroke>,
    edgeSoftness: Int,
    backgroundChoice: BackgroundChoice,
    outputFormat: String, // "PNG", "JPEG", "WEBP"
    quality: Int = 90,
    jobContext: JobContext? = null,
    onProgress: (suspend (percent: Int?, stage: String) -> Unit)? = null
  ): JobResult = withContext(Dispatchers.IO) {
    onProgress?.invoke(5, "Preparing full-resolution export…")

    val meta = ImageEngine.readImageMetadata(context, inputUri)
    val origW = meta.width
    val origH = meta.height

    jobContext?.ensureActive()

    // 1. Re-render strokes on working base mask ByteArray
    val workingMaskWithStrokes = if (strokes.isNotEmpty()) {
      MaskRenderer.renderStrokes(baseMask, maskW, maskH, strokes)
    } else {
      baseMask.copyOf()
    }

    // 2. Scale mask to original dimensions with bilinear function
    onProgress?.invoke(25, "Scaling mask to original dimensions…")
    val fullMask = if (maskW != origW || maskH != origH) {
      BgRemoveMath.bilinearUpscaleMask(workingMaskWithStrokes, maskW, maskH, origW, origH)
    } else {
      workingMaskWithStrokes
    }

    // 3. Apply edge softness box blur if requested
    val refinedMask = if (edgeSoftness in 1..3) {
      onProgress?.invoke(40, "Applying edge refinement…")
      BgRemoveMath.applyBoxBlur(fullMask, origW, origH, edgeSoftness)
    } else {
      fullMask
    }

    jobContext?.ensureActive()

    // 4. Decode original full-resolution oriented image
    onProgress?.invoke(55, "Loading full-resolution original…")
    val decodeOpts = BitmapFactory.Options().apply {
      inPreferredConfig = Bitmap.Config.ARGB_8888
      inMutable = false
    }
    val decodedOrig = context.contentResolver.openInputStream(inputUri)?.use { stream ->
      BitmapFactory.decodeStream(stream, null, decodeOpts)
    } ?: throw IOException("Could not decode original image for export")

    val fullOrig = if (meta.orientationDegrees != 0 || meta.isFlipped) {
      val matrix = Matrix()
      if (meta.isFlipped) matrix.postScale(-1f, 1f)
      if (meta.orientationDegrees != 0) matrix.postRotate(meta.orientationDegrees.toFloat())
      val transformed = Bitmap.createBitmap(decodedOrig, 0, 0, decodedOrig.width, decodedOrig.height, matrix, true)
      if (transformed != decodedOrig) decodedOrig.recycle()
      transformed
    } else {
      decodedOrig
    }

    jobContext?.ensureActive()

    // 5. Composite Cutout with background using explicit pixel math
    onProgress?.invoke(70, "Compositing image…")
    val finalBitmap = Bitmap.createBitmap(origW, origH, Bitmap.Config.ARGB_8888)

    // Check background type
    val isCustomImage = backgroundChoice is BackgroundChoice.CustomImage
    var bgDecoded: Bitmap? = null

    if (isCustomImage) {
      val customUri = Uri.parse((backgroundChoice as BackgroundChoice.CustomImage).uriString)
      try {
        val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(customUri)?.use {
          BitmapFactory.decodeStream(it, null, boundsOptions)
        }

        if (boundsOptions.outWidth <= 0 || boundsOptions.outHeight <= 0) {
          throw IOException(context.getString(R.string.tool20_bg_image_load_failed))
        }

        val maxTargetDim = maxOf(origW, origH)
        val sampleSize = BgRemoveMath.calculateBackgroundSampleSize(
          boundsOptions.outWidth,
          boundsOptions.outHeight,
          maxTargetDim
        )

        val decodeOptions = BitmapFactory.Options().apply {
          inSampleSize = sampleSize
          inPreferredConfig = Bitmap.Config.ARGB_8888
        }

        bgDecoded = context.contentResolver.openInputStream(customUri)?.use {
          BitmapFactory.decodeStream(it, null, decodeOptions)
        }
      } catch (oom: OutOfMemoryError) {
        throw IOException(context.getString(R.string.tool20_bg_image_load_failed))
      }
    }

    // Render background canvas if not transparent
    if (backgroundChoice !is BackgroundChoice.Transparent) {
      val canvas = Canvas(finalBitmap)
      when (backgroundChoice) {
        is BackgroundChoice.White -> canvas.drawColor(android.graphics.Color.WHITE)
        is BackgroundChoice.Black -> canvas.drawColor(android.graphics.Color.BLACK)
        is BackgroundChoice.CustomColor -> canvas.drawColor(backgroundChoice.color.toArgb())
        is BackgroundChoice.CustomImage -> {
          if (bgDecoded != null) {
            val srcRect = Rect()
            val bgW = bgDecoded.width.toFloat()
            val bgH = bgDecoded.height.toFloat()
            val targetAspect = origW.toFloat() / origH.toFloat().coerceAtLeast(1f)
            val bgAspect = bgW / bgH.coerceAtLeast(1f)

            if (bgAspect > targetAspect) {
              val scaledW = bgH * targetAspect
              val startX = (bgW - scaledW) / 2f
              srcRect.set(startX.toInt(), 0, (startX + scaledW).toInt(), bgH.toInt())
            } else {
              val scaledH = bgW / targetAspect
              val startY = (bgH - scaledH) / 2f
              srcRect.set(0, startY.toInt(), bgW.toInt(), (startY + scaledH).toInt())
            }
            canvas.drawBitmap(bgDecoded, srcRect, Rect(0, 0, origW, origH), null)
          }
        }
        else -> {}
      }
    }
    bgDecoded?.recycle()

    // Chunked explicit compositing (e.g. 256 rows at a time) to avoid huge single allocations
    val chunkSize = 256
    val origRowPixels = IntArray(origW * chunkSize)
    val compRowPixels = IntArray(origW * chunkSize)

    for (startRow in 0 until origH step chunkSize) {
      val numRows = minOf(chunkSize, origH - startRow)
      val numPixels = origW * numRows

      fullOrig.getPixels(origRowPixels, 0, origW, 0, startRow, origW, numRows)
      val maskOffset = startRow * origW

      if (backgroundChoice is BackgroundChoice.Transparent) {
        // Transparent output: out = (alpha shl 24) or (rgb and 0x00FFFFFF)
        for (i in 0 until numPixels) {
          val alpha = refinedMask[maskOffset + i].toInt() and 0xFF
          val rgb = origRowPixels[i] and 0x00FFFFFF
          compRowPixels[i] = (alpha shl 24) or rgb
        }
        finalBitmap.setPixels(compRowPixels, 0, origW, 0, startRow, origW, numRows)
      } else {
        // Solid or Image background: read existing background pixels, then blend
        finalBitmap.getPixels(compRowPixels, 0, origW, 0, startRow, origW, numRows)
        for (i in 0 until numPixels) {
          val alpha = refinedMask[maskOffset + i].toInt() and 0xFF
          if (alpha == 255) {
            compRowPixels[i] = origRowPixels[i] or (0xFF shl 24)
          } else if (alpha > 0) {
            val bg = compRowPixels[i]
            val fg = origRowPixels[i]

            val fgR = (fg shr 16) and 0xFF
            val fgG = (fg shr 8) and 0xFF
            val fgB = fg and 0xFF

            val bgR = (bg shr 16) and 0xFF
            val bgG = (bg shr 8) and 0xFF
            val bgB = bg and 0xFF

            val outR = (fgR * alpha + bgR * (255 - alpha)) / 255
            val outG = (fgG * alpha + bgG * (255 - alpha)) / 255
            val outB = (fgB * alpha + bgB * (255 - alpha)) / 255

            compRowPixels[i] = (0xFF shl 24) or (outR shl 16) or (outG shl 8) or outB
          }
          // If alpha == 0, keep background as is
        }
        finalBitmap.setPixels(compRowPixels, 0, origW, 0, startRow, origW, numRows)
      }
    }

    fullOrig.recycle()

    jobContext?.ensureActive()

    // 6. Save to cache file
    onProgress?.invoke(85, "Saving output file…")
    val tempDir = FileLayer.getTempFolder(context)
    val outputName = BgRemoveMath.generateCutoutOutputName(inputFileName, outputFormat)
    val outputFile = File(tempDir, outputName)
    jobContext?.trackPartialOutput(outputFile)

    val compressFormat = when (outputFormat.uppercase()) {
      "JPG", "JPEG" -> Bitmap.CompressFormat.JPEG
      "WEBP" -> if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
        Bitmap.CompressFormat.WEBP_LOSSY
      } else {
        @Suppress("DEPRECATION")
        Bitmap.CompressFormat.WEBP
      }
      else -> Bitmap.CompressFormat.PNG
    }

    try {
      FileOutputStream(outputFile).use { fos ->
        finalBitmap.compress(compressFormat, quality.coerceIn(10, 100), fos)
        fos.flush()
      }
    } finally {
      finalBitmap.recycle()
    }

    jobContext?.ensureActive()

    // 7. Non-blocking verification of output
    onProgress?.invoke(95, "Verifying output…")
    val verification = verifyCutoutExport(outputFile, origW, origH, outputFormat, backgroundChoice is BackgroundChoice.Transparent)
    if (!verification.isValid) {
      if (outputFile.exists()) outputFile.delete()
      throw IOException(verification.errorMessage ?: "Verification failed for exported cutout")
    }

    val finalSize = outputFile.length()
    val userMsg = if (verification.warningMessage != null) {
      "Cutout saved (${verification.warningMessage}) • ${FileFormatters.formatBytes(finalSize)} ($origW×$origH)"
    } else {
      "Cutout saved • ${FileFormatters.formatBytes(finalSize)} ($origW×$origH)"
    }

    val fileResult = FileResult(
      inputName = inputFileName,
      outputName = outputFile.name,
      outputUri = outputFile.absolutePath,
      outputSizeBytes = finalSize,
      originalSizeBytes = meta.sizeBytes,
      success = true,
      width = origW,
      height = origH,
      format = outputFormat.uppercase(),
      tempFilePath = outputFile.absolutePath,
      userMessage = userMsg
    )

    JobResult(files = listOf(fileResult))
  }

  data class CutoutVerification(
    val isValid: Boolean,
    val errorMessage: String? = null,
    val warningMessage: String? = null
  )

  fun verifyCutoutExport(
    file: File,
    expectedWidth: Int,
    expectedHeight: Int,
    format: String,
    expectAlpha: Boolean
  ): CutoutVerification {
    if (!file.exists() || file.length() <= 0L) {
      return CutoutVerification(false, "Exported file is missing or empty")
    }

    val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.absolutePath, opts)
    if (opts.outWidth != expectedWidth || opts.outHeight != expectedHeight) {
      return CutoutVerification(false, "Dimensions mismatch: expected ${expectedWidth}x${expectedHeight}, got ${opts.outWidth}x${opts.outHeight}")
    }

    // For transparent PNG, check alpha non-blockingly
    if (format.equals("PNG", ignoreCase = true) && expectAlpha) {
      val sampleOpts = BitmapFactory.Options().apply {
        inSampleSize = 4
        inPreferredConfig = Bitmap.Config.ARGB_8888
      }
      val sampled = BitmapFactory.decodeFile(file.absolutePath, sampleOpts)
      if (sampled != null) {
        var foundTransparentPixel = false
        val step = maxOf(1, sampled.width / 20)
        for (y in 0 until sampled.height step step) {
          for (x in 0 until sampled.width step step) {
            val pixel = sampled.getPixel(x, y)
            val alpha = (pixel shr 24) and 0xFF
            if (alpha < 255) {
              foundTransparentPixel = true
              break
            }
          }
          if (foundTransparentPixel) break
        }
        sampled.recycle()
        if (!foundTransparentPixel) {
          // Do NOT block export; return valid with warning
          return CutoutVerification(
            isValid = true,
            warningMessage = "Notice: Entire output image appears opaque"
          )
        }
      }
    }

    return CutoutVerification(true)
  }

  /**
   * Safely decodes a custom background image with inSampleSize suitable for on-screen preview.
   * Catches OutOfMemoryError and returns null on any failure.
   */
  fun decodePreviewBackgroundImage(context: Context, uri: Uri, targetSize: Int = 1024): Bitmap? {
    return try {
      val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
      context.contentResolver.openInputStream(uri)?.use {
        BitmapFactory.decodeStream(it, null, boundsOptions)
      }
      if (boundsOptions.outWidth <= 0 || boundsOptions.outHeight <= 0) return null

      val sampleSize = BgRemoveMath.calculateBackgroundSampleSize(
        boundsOptions.outWidth,
        boundsOptions.outHeight,
        targetSize
      )
      val decodeOptions = BitmapFactory.Options().apply {
        inSampleSize = sampleSize
        inPreferredConfig = Bitmap.Config.ARGB_8888
      }
      context.contentResolver.openInputStream(uri)?.use {
        BitmapFactory.decodeStream(it, null, decodeOptions)
      }
    } catch (_: OutOfMemoryError) {
      null
    } catch (_: Exception) {
      null
    }
  }
}
