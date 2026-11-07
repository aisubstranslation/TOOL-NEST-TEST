package com.example.tools.qr

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.EncodeHintType
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.WriterException
import com.google.zxing.common.BitMatrix
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import java.util.EnumMap

data class QrGenerationResult(
  val bitmap: Bitmap,
  val isVerified: Boolean,
  val decodedPayload: String?,
  val contrastRatio: Float,
  val isContrastWarning: Boolean,
  val isInvertedWarning: Boolean,
  val characterCount: Int
)

object QrCodeGenerator {

  /**
   * Generates a pixel-exact QR code Bitmap and validates it using ZXing round-trip decoding.
   */
  fun generateQr(
    payload: String,
    targetDimensionPx: Int = 1024,
    ecLevel: ErrorCorrectionLevel = ErrorCorrectionLevel.M,
    marginModules: Int = 4,
    foregroundColor: Int = Color.BLACK,
    backgroundColor: Int = Color.WHITE
  ): QrGenerationResult {
    if (payload.isBlank()) {
      throw IllegalArgumentException("QR payload cannot be empty.")
    }

    val bitMatrix: BitMatrix
    try {
      bitMatrix = encodePayloadToBitMatrix(payload, ecLevel, marginModules)
    } catch (e: WriterException) {
      val count = payload.length
      throw IllegalStateException(
        "Data too large for QR code ($count characters). Try choosing a lower error-correction level (e.g. L or M) or reducing the text."
      )
    }

    val (width, height, pixels) = generateQrPixels(
      bitMatrix = bitMatrix,
      targetDimensionPx = targetDimensionPx,
      foregroundColor = foregroundColor,
      backgroundColor = backgroundColor
    )

    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    bitmap.setPixels(pixels, 0, width, 0, 0, width, height)

    // Check contrast and inversion
    val fgLum = calculateLuminance(foregroundColor)
    val bgLum = calculateLuminance(backgroundColor)
    val l1 = maxOf(fgLum, bgLum)
    val l2 = minOf(fgLum, bgLum)
    val contrastRatio = (l1 + 0.05f) / (l2 + 0.05f)

    val isContrastWarning = contrastRatio < 3.0f
    val isInvertedWarning = fgLum > bgLum // Light code on dark background

    // Round-trip verification: decode the bitmap with ZXing
    val (isVerified, decoded) = decodeBitmap(bitmap)

    return QrGenerationResult(
      bitmap = bitmap,
      isVerified = isVerified && decoded == payload,
      decodedPayload = decoded,
      contrastRatio = contrastRatio,
      isContrastWarning = isContrastWarning,
      isInvertedWarning = isInvertedWarning,
      characterCount = payload.length
    )
  }

  /**
   * Decodes a QR code Bitmap using ZXing.
   * If the bitmap contains transparency, composites onto a white background first.
   * If initial decode fails, attempts inverted luminance decode with TRY_HARDER.
   */
  fun decodeBitmap(bitmap: Bitmap): Pair<Boolean, String?> {
    val width = bitmap.width
    val height = bitmap.height
    val pixels = IntArray(width * height)

    // If bitmap has alpha, composite onto white background
    if (bitmap.hasAlpha()) {
      val opaqueBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
      val canvas = android.graphics.Canvas(opaqueBitmap)
      canvas.drawColor(Color.WHITE)
      canvas.drawBitmap(bitmap, 0f, 0f, null)
      opaqueBitmap.getPixels(pixels, 0, width, 0, 0, width, height)
      opaqueBitmap.recycle()
    } else {
      bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
    }

    return decodePixels(pixels, width, height)
  }

  /**
   * Decodes raw ARGB pixels without android.graphics.Bitmap dependency for pure JVM unit testing.
   */
  fun decodePixels(pixels: IntArray, width: Int, height: Int): Pair<Boolean, String?> {
    return try {
      val source = RGBLuminanceSource(width, height, pixels)
      val binaryBitmap = BinaryBitmap(HybridBinarizer(source))

      val decodeHints = EnumMap<DecodeHintType, Any>(DecodeHintType::class.java).apply {
        put(DecodeHintType.CHARACTER_SET, "UTF-8")
        put(DecodeHintType.POSSIBLE_FORMATS, listOf(BarcodeFormat.QR_CODE))
      }

      val reader = QRCodeReader()
      try {
        val result = reader.decode(binaryBitmap, decodeHints)
        true to result.text
      } catch (_: Exception) {
        // Fallback: try inverted luminance source with TRY_HARDER (for inverted QR codes)
        reader.reset()
        val invertedHints = EnumMap<DecodeHintType, Any>(DecodeHintType::class.java).apply {
          put(DecodeHintType.CHARACTER_SET, "UTF-8")
          put(DecodeHintType.POSSIBLE_FORMATS, listOf(BarcodeFormat.QR_CODE))
          put(DecodeHintType.TRY_HARDER, java.lang.Boolean.TRUE)
        }
        val invertedBinaryBitmap = BinaryBitmap(HybridBinarizer(source.invert()))
        val invertedResult = reader.decode(invertedBinaryBitmap, invertedHints)
        true to invertedResult.text
      }
    } catch (_: Exception) {
      false to null
    }
  }

  fun generateQrPixels(
    bitMatrix: BitMatrix,
    targetDimensionPx: Int,
    foregroundColor: Int,
    backgroundColor: Int
  ): Triple<Int, Int, IntArray> {
    val matrixWidth = bitMatrix.width
    val matrixHeight = bitMatrix.height

    val scale = (targetDimensionPx / matrixWidth).coerceAtLeast(1)
    val width = matrixWidth * scale
    val height = matrixHeight * scale

    val pixels = IntArray(width * height)
    for (y in 0 until matrixHeight) {
      val offset = y * scale * width
      for (x in 0 until matrixWidth) {
        val color = if (bitMatrix.get(x, y)) foregroundColor else backgroundColor
        for (dy in 0 until scale) {
          val rowOffset = offset + dy * width
          for (dx in 0 until scale) {
            pixels[rowOffset + x * scale + dx] = color
          }
        }
      }
    }
    return Triple(width, height, pixels)
  }

  fun encodePayloadToBitMatrix(
    payload: String,
    ecLevel: ErrorCorrectionLevel = ErrorCorrectionLevel.M,
    marginModules: Int = 4
  ): BitMatrix {
    val hints = EnumMap<EncodeHintType, Any>(EncodeHintType::class.java).apply {
      put(EncodeHintType.ERROR_CORRECTION, ecLevel)
      put(EncodeHintType.CHARACTER_SET, "UTF-8")
      put(EncodeHintType.MARGIN, marginModules.coerceAtLeast(1))
    }
    return QRCodeWriter().encode(payload, BarcodeFormat.QR_CODE, 0, 0, hints)
  }

  private fun calculateLuminance(color: Int): Float {
    val r = ((color shr 16) and 0xFF) / 255.0f
    val g = ((color shr 8) and 0xFF) / 255.0f
    val b = (color and 0xFF) / 255.0f
    return 0.2126f * r + 0.7152f * g + 0.0722f * b
  }
}
