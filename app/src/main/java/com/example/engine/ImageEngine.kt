package com.example.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.net.Uri
import android.os.Build
import androidx.exifinterface.media.ExifInterface
import com.example.file.FileFormatters
import com.example.file.FileLayer
import com.example.file.FileLimits
import com.example.file.FileNameSanitizer
import com.example.tools.common.FileResult
import com.example.tools.common.JobContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream

data class ImageMetadataInfo(
  val width: Int,
  val height: Int,
  val mimeType: String,
  val orientationDegrees: Int = 0,
  val isFlipped: Boolean = false,
  val sizeBytes: Long = 0L
)

enum class OutputFormat {
  JPEG,
  PNG,
  WEBP
}

data class ProcessImageOptions(
  val format: OutputFormat = OutputFormat.JPEG,
  val quality: Int = 85, // 10..100
  val targetWidth: Int? = null,
  val targetHeight: Int? = null,
  val backgroundColor: Int = Color.WHITE, // For JPEG transparency flattening
  val outputPrefix: String = "TOOLSNEST_",
  val customOutputName: String? = null
)

object ImageEngine {

  /**
   * Reads image bounds and EXIF orientation without loading bitmap pixels into memory.
   * Checks the 50 MB file limit and 48 Megapixel resolution ceiling.
   */
  fun readImageMetadata(context: Context, uri: Uri): ImageMetadataInfo {
    // 1. Check file size limit safely
    var sizeBytes = 0L
    try {
      context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
        sizeBytes = pfd.statSize
      }
    } catch (_: Exception) {
      // Some content providers do not support direct file descriptors
    }

    if (sizeBytes <= 0L) {
      val info = FileLayer.getUriFileInfo(context, uri)
      sizeBytes = info.sizeBytes
    }

    if (sizeBytes > FileLimits.IMAGE_MAX_BYTES) {
      throw IllegalArgumentException("File size exceeds 50 MB limit (${FileFormatters.formatBytes(sizeBytes)})")
    }

    // 2. Decode bounds only
    val options = BitmapFactory.Options().apply {
      inJustDecodeBounds = true
    }

    var streamOpened = false
    try {
      context.contentResolver.openInputStream(uri)?.use { stream ->
        streamOpened = true
        val bis = if (stream is java.io.BufferedInputStream) stream else java.io.BufferedInputStream(stream)
        BitmapFactory.decodeStream(bis, null, options)
      }
    } catch (_: Exception) {}

    val rawW = options.outWidth
    val rawH = options.outHeight
    val mime = options.outMimeType ?: (context.contentResolver.getType(uri) ?: "image/unknown")

    if (rawW <= 0 || rawH <= 0) {
      if (!streamOpened) {
        throw IllegalArgumentException("Cannot open stream for selected image.")
      } else {
        throw IllegalArgumentException("Unsupported image format ($mime) or unreadable bounds.")
      }
    }

    if (!ImageMath.isWithinMegaPixelLimit(rawW, rawH)) {
      val mp = ImageMath.calculateMegaPixels(rawW, rawH)
      throw IllegalArgumentException("Image exceeds 48 megapixels limit (found %.1f MP)".format(mp))
    }

    // 3. Read EXIF orientation
    var degrees = 0
    var isFlipped = false

    runCatching {
      context.contentResolver.openInputStream(uri)?.use { stream ->
        val exif = ExifInterface(stream)
        val orientation = exif.getAttributeInt(
          ExifInterface.TAG_ORIENTATION,
          ExifInterface.ORIENTATION_NORMAL
        )
        when (orientation) {
          ExifInterface.ORIENTATION_ROTATE_90 -> degrees = 90
          ExifInterface.ORIENTATION_ROTATE_180 -> degrees = 180
          ExifInterface.ORIENTATION_ROTATE_270 -> degrees = 270
          ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> isFlipped = true
          ExifInterface.ORIENTATION_FLIP_VERTICAL -> {
            isFlipped = true
            degrees = 180
          }
          ExifInterface.ORIENTATION_TRANSPOSE -> {
            isFlipped = true
            degrees = 90
          }
          ExifInterface.ORIENTATION_TRANSVERSE -> {
            isFlipped = true
            degrees = 270
          }
        }
      }
    }

    // Correct width and height if rotated by 90 or 270 degrees
    val finalW = if (degrees == 90 || degrees == 270) rawH else rawW
    val finalH = if (degrees == 90 || degrees == 270) rawW else rawH

    return ImageMetadataInfo(
      width = finalW,
      height = finalH,
      mimeType = mime,
      orientationDegrees = degrees,
      isFlipped = isFlipped,
      sizeBytes = sizeBytes
    )
  }

  /**
   * Processes a single image:
   * - Decodes full resolution
   * - Applies EXIF rotation
   * - Applies target resizing (bilinear filter)
   * - Flattens background color if saving transparent image as JPEG
   * - Compresses to target format in cache
   * - Verifies magic bytes, size > 0, and bounds readability
   * - Deletes partial file on error
   */
  fun processImage(
    context: Context,
    uri: Uri,
    originalName: String,
    options: ProcessImageOptions,
    jobContext: JobContext,
    targetCacheDir: File
  ): FileResult {
    jobContext.ensureActive()

    // 1. Read metadata & enforce limits
    val meta = readImageMetadata(context, uri)

    // 2. Decode at full resolution
    var decodedBitmap: Bitmap? = null
    var orientedBitmap: Bitmap? = null
    var scaledBitmap: Bitmap? = null
    var flattenedBitmap: Bitmap? = null

    var partialOutputFile: File? = null

    try {
      val decodeOptions = BitmapFactory.Options().apply {
        inPreferredConfig = Bitmap.Config.ARGB_8888
        inMutable = false
      }

      decodedBitmap = context.contentResolver.openInputStream(uri)?.use { stream ->
        BitmapFactory.decodeStream(stream, null, decodeOptions)
      } ?: throw IllegalArgumentException("Could not decode image bitmap")

      jobContext.ensureActive()

      // 3. Apply EXIF orientation
      orientedBitmap = if (meta.orientationDegrees != 0 || meta.isFlipped) {
        val matrix = Matrix()
        if (meta.isFlipped) {
          matrix.postScale(-1f, 1f)
        }
        if (meta.orientationDegrees != 0) {
          matrix.postRotate(meta.orientationDegrees.toFloat())
        }
        val transformed = Bitmap.createBitmap(
          decodedBitmap,
          0,
          0,
          decodedBitmap.width,
          decodedBitmap.height,
          matrix,
          true
        )
        if (transformed != decodedBitmap) {
          decodedBitmap.recycle()
        }
        transformed
      } else {
        decodedBitmap
      }

      jobContext.ensureActive()

      // 4. Scaling / Resizing if requested
      val targetW = options.targetWidth ?: orientedBitmap.width
      val targetH = options.targetHeight ?: orientedBitmap.height

      scaledBitmap = if (targetW != orientedBitmap.width || targetH != orientedBitmap.height) {
        val scaled = Bitmap.createScaledBitmap(orientedBitmap, targetW, targetH, true)
        if (scaled != orientedBitmap) {
          orientedBitmap.recycle()
        }
        scaled
      } else {
        orientedBitmap
      }

      jobContext.ensureActive()

      // 5. Transparency handling for JPEG
      val finalBitmap = if (options.format == OutputFormat.JPEG && scaledBitmap.hasAlpha()) {
        val opaque = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(opaque)
        canvas.drawColor(options.backgroundColor)
        canvas.drawBitmap(scaledBitmap, 0f, 0f, Paint(Paint.FILTER_BITMAP_FLAG))
        scaledBitmap.recycle()
        flattenedBitmap = opaque
        opaque
      } else {
        scaledBitmap
      }

      jobContext.ensureActive()

      // 6. Generate output file name
      val ext = when (options.format) {
        OutputFormat.JPEG -> "jpg"
        OutputFormat.PNG -> "png"
        OutputFormat.WEBP -> "webp"
      }

      val baseOutputName = if (!options.customOutputName.isNullOrBlank()) {
        options.customOutputName
      } else {
        originalName
      }

      val sanitizedName = FileNameSanitizer.sanitizeFileName(
        rawName = baseOutputName,
        prefix = options.outputPrefix,
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

      // 7. Compress to cache file
      val compressFormat = when (options.format) {
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

      // Quality applies to JPEG and WebP; PNG ignores quality
      val actualQuality = when (options.format) {
        OutputFormat.PNG -> 100
        else -> options.quality.coerceIn(10, 100)
      }

      FileOutputStream(outputFile).use { fos ->
        val success = finalBitmap.compress(compressFormat, actualQuality, fos)
        if (!success) {
          throw IllegalStateException("Bitmap compression failed")
        }
        fos.flush()
      }

      // 8. VERIFICATION
      if (!outputFile.exists() || outputFile.length() <= 0L) {
        throw IllegalStateException("Verification failed: output file is empty or missing")
      }

      // Verify magic bytes
      val headerBytes = ByteArray(16)
      FileInputStream(outputFile).use { fis ->
        fis.read(headerBytes)
      }

      val formatStr = when (options.format) {
        OutputFormat.JPEG -> "jpeg"
        OutputFormat.PNG -> "png"
        OutputFormat.WEBP -> "webp"
      }

      if (!ImageMath.verifyMagicBytes(headerBytes, formatStr)) {
        throw IllegalStateException("Verification failed: invalid magic bytes for $formatStr")
      }

      // Verify image bounds decodes properly
      val verifyOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
      BitmapFactory.decodeFile(outputFile.absolutePath, verifyOptions)

      if (verifyOptions.outWidth != targetW || verifyOptions.outHeight != targetH) {
        throw IllegalStateException("Verification failed: decoded dimensions (${verifyOptions.outWidth}x${verifyOptions.outHeight}) do not match expected (${targetW}x${targetH})")
      }

      return FileResult(
        inputName = originalName,
        outputName = outputFile.name,
        outputUri = outputFile.absolutePath,
        outputSizeBytes = outputFile.length(),
        success = true,
        width = targetW,
        height = targetH,
        originalSizeBytes = meta.sizeBytes,
        format = ext,
        tempFilePath = outputFile.absolutePath
      )

    } catch (e: OutOfMemoryError) {
      partialOutputFile?.delete()
      return FileResult(
        inputName = originalName,
        outputName = null,
        outputUri = null,
        outputSizeBytes = 0L,
        success = false,
        userMessage = "Out of memory: Image is too large for device RAM. Resolution preserved.",
        originalSizeBytes = meta.sizeBytes
      )
    } catch (e: Throwable) {
      partialOutputFile?.delete()
      return FileResult(
        inputName = originalName,
        outputName = null,
        outputUri = null,
        outputSizeBytes = 0L,
        success = false,
        userMessage = e.message ?: "Failed to process image",
        originalSizeBytes = meta.sizeBytes
      )
    } finally {
      // Free bitmap references
      flattenedBitmap?.recycle()
      scaledBitmap?.recycle()
      orientedBitmap?.recycle()
      decodedBitmap?.recycle()
    }
  }
}
