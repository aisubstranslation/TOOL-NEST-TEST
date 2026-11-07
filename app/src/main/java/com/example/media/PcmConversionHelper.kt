package com.example.media

import java.nio.ByteBuffer
import java.nio.ByteOrder

object PcmConversionHelper {

  /**
   * Converts float PCM samples (-1.0f to 1.0f) to signed 16-bit PCM little-endian bytes.
   */
  fun convertFloatTo16Bit(floatBuffer: ByteBuffer, output: ByteArray, floatCount: Int): Int {
    var outIndex = 0
    for (i in 0 until floatCount) {
      val sample = floatBuffer.getFloat()
      val clamped = sample.coerceIn(-1.0f, 1.0f)
      val pcm16 = if (clamped < 0.0f) {
        (clamped * 32768.0f).toInt().coerceIn(-32768, 32767).toShort()
      } else {
        (clamped * 32767.0f).toInt().coerceIn(-32768, 32767).toShort()
      }
      output[outIndex++] = (pcm16.toInt() and 0xFF).toByte()
      output[outIndex++] = ((pcm16.toInt() shr 8) and 0xFF).toByte()
    }
    return outIndex
  }

  /**
   * Pure single-sample converter for testing.
   * Returns Short value and (lowByte, highByte) little-endian.
   */
  fun convertSingleFloatToShort(sample: Float): Short {
    val clamped = sample.coerceIn(-1.0f, 1.0f)
    return if (clamped < 0.0f) {
      (clamped * 32768.0f).toInt().coerceIn(-32768, 32767).toShort()
    } else {
      (clamped * 32767.0f).toInt().coerceIn(-32768, 32767).toShort()
    }
  }

  fun convertSingleFloatTo16Bit(sample: Float): Pair<Byte, Byte> {
    val pcm16 = convertSingleFloatToShort(sample)
    val low = (pcm16.toInt() and 0xFF).toByte()
    val high = ((pcm16.toInt() shr 8) and 0xFF).toByte()
    return low to high
  }
}
