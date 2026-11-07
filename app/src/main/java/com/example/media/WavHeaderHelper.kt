package com.example.media

import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

object WavHeaderHelper {

  const val HEADER_SIZE = 44

  /**
   * Generates a standard 44-byte RIFF/WAVE header for 16-bit PCM.
   *
   * @param sampleRate e.g. 44100, 48000
   * @param channels e.g. 1 (mono), 2 (stereo)
   * @param dataBytes audio data size in bytes. 0 can be passed as placeholder.
   */
  fun createPcm16Header(sampleRate: Int, channels: Int, dataBytes: Long): ByteArray {
    val totalChunkSize = (dataBytes + 36).coerceAtMost(0xFFFFFFFFL).toInt()
    val subChunk2Size = dataBytes.coerceAtMost(0xFFFFFFFFL).toInt()
    val byteRate = sampleRate * channels * 2
    val blockAlign = channels * 2

    val buffer = ByteBuffer.allocate(HEADER_SIZE)
    buffer.order(ByteOrder.LITTLE_ENDIAN)

    // RIFF chunk descriptor
    buffer.put('R'.code.toByte())
    buffer.put('I'.code.toByte())
    buffer.put('F'.code.toByte())
    buffer.put('F'.code.toByte())
    buffer.putInt(totalChunkSize)
    buffer.put('W'.code.toByte())
    buffer.put('A'.code.toByte())
    buffer.put('V'.code.toByte())
    buffer.put('E'.code.toByte())

    // "fmt " sub-chunk
    buffer.put('f'.code.toByte())
    buffer.put('m'.code.toByte())
    buffer.put('t'.code.toByte())
    buffer.put(' '.code.toByte())
    buffer.putInt(16) // Subchunk1Size for PCM
    buffer.putShort(1.toShort()) // AudioFormat 1 = PCM
    buffer.putShort(channels.toShort())
    buffer.putInt(sampleRate)
    buffer.putInt(byteRate)
    buffer.putShort(blockAlign.toShort())
    buffer.putShort(16.toShort()) // BitsPerSample = 16

    // "data" sub-chunk
    buffer.put('d'.code.toByte())
    buffer.put('a'.code.toByte())
    buffer.put('t'.code.toByte())
    buffer.put('a'.code.toByte())
    buffer.putInt(subChunk2Size)

    return buffer.array()
  }

  /**
   * Patches the totalChunkSize and dataSize in an existing file using RandomAccessFile.
   */
  fun patchWavSizes(file: File) {
    if (!file.exists()) return
    val fileLength = file.length()
    if (fileLength < HEADER_SIZE) return

    val dataSize = (fileLength - HEADER_SIZE).coerceAtLeast(0L)
    val totalChunkSize = (dataSize + 36).coerceAtLeast(0L)

    RandomAccessFile(file, "rw").use { raf ->
      // Patch RIFF total chunk size at offset 4
      raf.seek(4)
      val totalBytes = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(totalChunkSize.toInt()).array()
      raf.write(totalBytes)

      // Patch data subchunk size at offset 40
      raf.seek(40)
      val dataBytes = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(dataSize.toInt()).array()
      raf.write(dataBytes)
    }
  }

  data class WavHeaderInfo(
    val isValid: Boolean,
    val sampleRate: Int = 0,
    val channels: Int = 0,
    val bitsPerSample: Int = 0,
    val dataSizeBytes: Long = 0L,
    val durationMs: Long = 0L,
    val errorMessage: String? = null
  )

  /**
   * Validates a WAV file header against required signatures:
   * RIFF, WAVE, fmt , data, matching declared data size against file length - 44.
   */
  fun validateWavFile(file: File): WavHeaderInfo {
    if (!file.exists() || file.length() < HEADER_SIZE) {
      return WavHeaderInfo(isValid = false, errorMessage = "File too small or does not exist")
    }

    val headerBytes = ByteArray(HEADER_SIZE)
    RandomAccessFile(file, "r").use { raf ->
      raf.readFully(headerBytes)
    }

    val buffer = ByteBuffer.wrap(headerBytes).order(ByteOrder.LITTLE_ENDIAN)

    val riff = String(headerBytes, 0, 4)
    if (riff != "RIFF") {
      return WavHeaderInfo(isValid = false, errorMessage = "Missing RIFF header signature")
    }

    val totalChunkSize = buffer.getInt(4).toLong() and 0xFFFFFFFFL

    val wave = String(headerBytes, 8, 4)
    if (wave != "WAVE") {
      return WavHeaderInfo(isValid = false, errorMessage = "Missing WAVE header signature")
    }

    val fmt = String(headerBytes, 12, 4)
    if (fmt != "fmt ") {
      return WavHeaderInfo(isValid = false, errorMessage = "Missing 'fmt ' chunk signature")
    }

    val audioFormat = buffer.getShort(20).toInt()
    if (audioFormat != 1) {
      return WavHeaderInfo(isValid = false, errorMessage = "Unsupported audio format: expected PCM (1) but was $audioFormat")
    }

    val channels = buffer.getShort(22).toInt()
    val sampleRate = buffer.getInt(24)
    val bitsPerSample = buffer.getShort(34).toInt()

    val dataTag = String(headerBytes, 36, 4)
    if (dataTag != "data") {
      return WavHeaderInfo(isValid = false, errorMessage = "Missing 'data' chunk signature")
    }

    val declaredDataSize = buffer.getInt(40).toLong() and 0xFFFFFFFFL
    val actualDataSize = file.length() - HEADER_SIZE

    if (declaredDataSize != actualDataSize) {
      return WavHeaderInfo(
        isValid = false,
        errorMessage = "Declared data size ($declaredDataSize) does not match actual audio payload size ($actualDataSize)"
      )
    }

    val bytesPerSec = sampleRate * channels * (bitsPerSample / 8)
    val durationMs = if (bytesPerSec > 0) (actualDataSize * 1000L) / bytesPerSec else 0L

    return WavHeaderInfo(
      isValid = true,
      sampleRate = sampleRate,
      channels = channels,
      bitsPerSample = bitsPerSample,
      dataSizeBytes = actualDataSize,
      durationMs = durationMs
    )
  }
}
