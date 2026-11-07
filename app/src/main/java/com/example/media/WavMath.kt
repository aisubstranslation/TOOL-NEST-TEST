package com.example.media

object WavMath {
  const val MAX_WAV_BYTES = 3_800_000_000L // 3.8 GB limit

  /**
   * Estimates total size of a 16-bit PCM WAV file: 44 bytes header + (durationSec * sampleRate * channels * 2 bytes)
   */
  fun estimateWavSizeBytes(durationSec: Double, sampleRate: Int, channels: Int): Long {
    if (durationSec <= 0 || sampleRate <= 0 || channels <= 0) return 44L
    val dataSize = (durationSec * sampleRate.toDouble() * channels.toDouble() * 2.0).toLong()
    return dataSize + 44L
  }

  fun durationToPcm16Bytes(durationSec: Double, sampleRate: Int = 44100, channels: Int = 2): Long {
    if (durationSec <= 0 || sampleRate <= 0 || channels <= 0) return 0L
    return (durationSec * sampleRate.toDouble() * channels.toDouble() * 2.0).toLong()
  }

  fun samplesToPcm16Bytes(sampleCount: Long, channels: Int = 2): Long {
    if (sampleCount <= 0 || channels <= 0) return 0L
    return sampleCount * channels * 2L
  }

  fun pcm16BytesToDurationSec(pcmBytes: Long, sampleRate: Int = 44100, channels: Int = 2): Double {
    val bytesPerSec = sampleRate.toDouble() * channels.toDouble() * 2.0
    if (bytesPerSec <= 0 || pcmBytes <= 0) return 0.0
    return pcmBytes.toDouble() / bytesPerSec
  }

  fun pcm16BytesToDurationMs(pcmBytes: Long, sampleRate: Int = 44100, channels: Int = 2): Long {
    return (pcm16BytesToDurationSec(pcmBytes, sampleRate, channels) * 1000.0).toLong()
  }

  fun exceedsWavLimit(estimatedBytes: Long): Boolean {
    return estimatedBytes > MAX_WAV_BYTES
  }
}
