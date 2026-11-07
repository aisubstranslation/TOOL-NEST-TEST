package com.example.tools.media

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.coroutines.coroutineContext
import kotlin.math.abs

object WaveformHelper {

  const val DEFAULT_BUCKET_COUNT = 600
  const val MAX_WAVEFORM_DURATION_MS = 60 * 60 * 1000L // 60 minutes

  /**
   * Pure bucketing function for unit tests.
   * Takes an array of normalized float samples in [-1.0f, 1.0f], splits into [targetBuckets],
   * takes the max absolute value in each bucket, and normalizes the output to [0.0f, 1.0f].
   */
  fun bucketPeaks(rawSamples: FloatArray, targetBuckets: Int): FloatArray {
    if (rawSamples.isEmpty() || targetBuckets <= 0) {
      return FloatArray(maxOf(1, targetBuckets)) { 0f }
    }

    val result = FloatArray(targetBuckets)
    val samplesPerBucket = rawSamples.size.toDouble() / targetBuckets.toDouble()

    var maxOverall = 0f

    for (b in 0 until targetBuckets) {
      val startIdx = (b * samplesPerBucket).toInt().coerceIn(0, rawSamples.size - 1)
      val endIdx = ((b + 1) * samplesPerBucket).toInt().coerceIn(startIdx + 1, rawSamples.size)

      var peak = 0f
      for (i in startIdx until endIdx) {
        val mag = abs(rawSamples[i])
        if (mag > peak) peak = mag
      }
      result[b] = peak
      if (peak > maxOverall) maxOverall = peak
    }

    // Normalize so highest peak is 1.0f if > 0
    if (maxOverall > 0f) {
      for (b in 0 until targetBuckets) {
        result[b] = (result[b] / maxOverall).coerceIn(0f, 1f)
      }
    }

    return result
  }

  /**
   * Streaming decoder that extracts ~600 peak buckets directly from audio track
   * without buffering raw PCM in memory.
   * Returns null if audio duration exceeds 60 minutes or decoding fails.
   */
  suspend fun extractWaveformPeaks(
    context: Context,
    uri: Uri,
    durationMs: Long,
    targetBuckets: Int = DEFAULT_BUCKET_COUNT,
    onProgress: ((Float) -> Unit)? = null
  ): FloatArray? = withContext(Dispatchers.IO) {
    if (durationMs <= 0 || durationMs > MAX_WAVEFORM_DURATION_MS) {
      return@withContext null
    }

    var extractor: MediaExtractor? = null
    var codec: MediaCodec? = null

    try {
      extractor = MediaExtractor()
      context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
        extractor.setDataSource(pfd.fileDescriptor)
      } ?: extractor.setDataSource(context, uri, null)

      var audioTrackIndex = -1
      for (i in 0 until extractor.trackCount) {
        val trackFormat = extractor.getTrackFormat(i)
        val mime = trackFormat.getString(MediaFormat.KEY_MIME) ?: ""
        if (mime.startsWith("audio/")) {
          audioTrackIndex = i
          break
        }
      }

      if (audioTrackIndex == -1) return@withContext null

      extractor.selectTrack(audioTrackIndex)
      val format = extractor.getTrackFormat(audioTrackIndex)
      val mime = format.getString(MediaFormat.KEY_MIME) ?: return@withContext null

      val channelCount = if (format.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
        format.getInteger(MediaFormat.KEY_CHANNEL_COUNT).coerceAtLeast(1)
      } else 1

      codec = MediaCodec.createDecoderByType(mime)
      codec.configure(format, null, null, 0)
      codec.start()

      val rawBuckets = FloatArray(targetBuckets)
      var maxOverall = 0f

      val totalEstimatedUs = durationMs * 1000L
      val usPerBucket = if (totalEstimatedUs > 0) totalEstimatedUs.toDouble() / targetBuckets.toDouble() else 1.0

      val bufferInfo = MediaCodec.BufferInfo()
      val timeoutUs = 5000L
      var inputEos = false
      var outputEos = false
      var lastReportedPct = -1

      while (!outputEos) {
        coroutineContext.ensureActive()

        if (!inputEos) {
          val inIndex = codec.dequeueInputBuffer(timeoutUs)
          if (inIndex >= 0) {
            val inBuf = codec.getInputBuffer(inIndex)
            if (inBuf != null) {
              val sampleSize = extractor.readSampleData(inBuf, 0)
              if (sampleSize < 0) {
                codec.queueInputBuffer(inIndex, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                inputEos = true
              } else {
                codec.queueInputBuffer(inIndex, 0, sampleSize, extractor.sampleTime, 0)
                extractor.advance()
              }
            }
          }
        }

        val outIndex = codec.dequeueOutputBuffer(bufferInfo, timeoutUs)
        if (outIndex >= 0) {
          if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
            outputEos = true
          }

          val outBuf = codec.getOutputBuffer(outIndex)
          if (outBuf != null && bufferInfo.size > 0) {
            val presentationUs = bufferInfo.presentationTimeUs
            val bucketIndex = (presentationUs / usPerBucket).toInt().coerceIn(0, targetBuckets - 1)

            outBuf.position(bufferInfo.offset)
            outBuf.limit(bufferInfo.offset + bufferInfo.size)
            outBuf.order(ByteOrder.LITTLE_ENDIAN)

            // Read 16-bit PCM samples and update peak in current bucket
            val shortCount = bufferInfo.size / 2
            var currentPeak = rawBuckets[bucketIndex]

            for (s in 0 until shortCount) {
              if (outBuf.remaining() < 2) break
              val sampleShort = outBuf.short
              val mag = abs(sampleShort.toFloat()) / 32768.0f
              if (mag > currentPeak) {
                currentPeak = mag
              }
            }

            rawBuckets[bucketIndex] = currentPeak
            if (currentPeak > maxOverall) {
              maxOverall = currentPeak
            }

            if (totalEstimatedUs > 0) {
              val pct = ((presentationUs.toDouble() / totalEstimatedUs.toDouble()) * 100.0).toInt().coerceIn(0, 100)
              if (pct != lastReportedPct) {
                lastReportedPct = pct
                onProgress?.invoke(pct / 100.0f)
              }
            }
          }

          codec.releaseOutputBuffer(outIndex, false)
        }
      }

      // Normalize buckets
      if (maxOverall > 0f) {
        for (i in 0 until targetBuckets) {
          rawBuckets[i] = (rawBuckets[i] / maxOverall).coerceIn(0f, 1f)
        }
      }

      rawBuckets
    } catch (_: Exception) {
      null
    } finally {
      try { codec?.stop() } catch (_: Exception) {}
      try { codec?.release() } catch (_: Exception) {}
      try { extractor?.release() } catch (_: Exception) {}
    }
  }
}
