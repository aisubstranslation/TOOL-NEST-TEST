package com.example.media

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import com.example.tools.common.JobContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.ByteBuffer

object WavEngine {

  private const val BUFFER_SIZE = 64 * 1024
  private const val TIMEOUT_US = 10_000L

  suspend fun extractOrConvertWav(
    context: Context,
    inputUri: Uri,
    outputFile: File,
    trackIndexToUse: Int? = null,
    startMs: Long = 0L,
    endMs: Long? = null,
    jobContext: JobContext? = null,
    onProgress: (suspend (percent: Int, stage: String) -> Unit)? = null
  ): Long = withContext(Dispatchers.IO) {
    var extractor: MediaExtractor? = null
    var codec: MediaCodec? = null
    var fos: FileOutputStream? = null
    var bos: BufferedOutputStream? = null

    var totalAudioBytesWritten = 0L

    try {
      extractor = MediaExtractor()
      context.contentResolver.openFileDescriptor(inputUri, "r")?.use { pfd ->
        extractor.setDataSource(pfd.fileDescriptor)
      } ?: extractor.setDataSource(context, inputUri, null)

      // Find audio track
      var selectedTrack = -1
      if (trackIndexToUse != null && trackIndexToUse in 0 until extractor.trackCount) {
        val format = extractor.getTrackFormat(trackIndexToUse)
        val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
        if (mime.startsWith("audio/")) {
          selectedTrack = trackIndexToUse
        }
      }

      if (selectedTrack == -1) {
        for (i in 0 until extractor.trackCount) {
          val format = extractor.getTrackFormat(i)
          val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
          if (mime.startsWith("audio/")) {
            selectedTrack = i
            break
          }
        }
      }

      if (selectedTrack == -1) {
        throw IOException("This video has no audio")
      }

      extractor.selectTrack(selectedTrack)
      val format = extractor.getTrackFormat(selectedTrack)
      val mime = format.getString(MediaFormat.KEY_MIME) ?: throw IOException("Missing audio MIME")

      var sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
      var channelCount = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
      val durationUs = if (format.containsKey(MediaFormat.KEY_DURATION)) format.getLong(MediaFormat.KEY_DURATION) else 0L

      if (startMs > 0) {
        extractor.seekTo(startMs * 1000L, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
      }

      codec = MediaCodec.createDecoderByType(mime)
      codec.configure(format, null, null, 0)
      codec.start()

      outputFile.parentFile?.mkdirs()
      fos = FileOutputStream(outputFile)
      bos = BufferedOutputStream(fos, BUFFER_SIZE)

      // Write placeholder 44-byte PCM16 header
      val placeholderHeader = WavHeaderHelper.createPcm16Header(sampleRate, channelCount, 0L)
      bos.write(placeholderHeader)

      val bufferInfo = MediaCodec.BufferInfo()
      var inputEos = false
      var outputEos = false
      var pcmEncoding = AudioFormat.ENCODING_PCM_16BIT
      val floatConversionArray = ByteArray(BUFFER_SIZE * 2)

      var lastReportedPercent = -1

      while (!outputEos) {
        jobContext?.ensureActive()

        // 1. Feed input buffers
        if (!inputEos) {
          val inIndex = codec.dequeueInputBuffer(TIMEOUT_US)
          if (inIndex >= 0) {
            val inBuf = codec.getInputBuffer(inIndex)
            if (inBuf != null) {
              val sampleSize = extractor.readSampleData(inBuf, 0)
              val sampleTime = extractor.sampleTime

              if (sampleSize < 0 || (endMs != null && sampleTime > endMs * 1000L)) {
                codec.queueInputBuffer(inIndex, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                inputEos = true
              } else {
                codec.queueInputBuffer(inIndex, 0, sampleSize, sampleTime, 0)
                extractor.advance()
              }
            }
          }
        }

        // 2. Drain output buffers
        val outIndex = codec.dequeueOutputBuffer(bufferInfo, TIMEOUT_US)
        when {
          outIndex >= 0 -> {
            if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
              outputEos = true
            }

            val outBuf = codec.getOutputBuffer(outIndex)
            if (outBuf != null && bufferInfo.size > 0) {
              val presentationTimeUs = bufferInfo.presentationTimeUs

              // Check start trimming
              val shouldWrite = (startMs <= 0 || presentationTimeUs >= startMs * 1000L) &&
                (endMs == null || presentationTimeUs <= endMs * 1000L)

              if (shouldWrite) {
                outBuf.position(bufferInfo.offset)
                outBuf.limit(bufferInfo.offset + bufferInfo.size)

                if (pcmEncoding == AudioFormat.ENCODING_PCM_FLOAT) {
                  val floatCount = bufferInfo.size / 4
                  var remainingFloats = floatCount
                  while (remainingFloats > 0) {
                    val batch = minOf(remainingFloats, floatConversionArray.size / 2)
                    val bytesWritten = PcmConversionHelper.convertFloatTo16Bit(outBuf, floatConversionArray, batch)
                    bos.write(floatConversionArray, 0, bytesWritten)
                    totalAudioBytesWritten += bytesWritten
                    remainingFloats -= batch
                  }
                } else {
                  // Direct 16-bit PCM pass-through
                  val chunk = ByteArray(bufferInfo.size)
                  outBuf.get(chunk)
                  bos.write(chunk)
                  totalAudioBytesWritten += bufferInfo.size
                }

                if (WavMath.exceedsWavLimit(totalAudioBytesWritten + 44L)) {
                  throw IOException("WAV output exceeded 3.8 GB limit.")
                }
              }

              // Progress reporting
              if (durationUs > 0) {
                val effectiveDuration = if (endMs != null) (endMs * 1000L - startMs * 1000L) else durationUs
                val progressUs = (presentationTimeUs - (startMs * 1000L)).coerceAtLeast(0L)
                val pct = ((progressUs.toFloat() / effectiveDuration.toFloat()) * 95f).toInt().coerceIn(0, 95)
                if (pct != lastReportedPercent) {
                  lastReportedPercent = pct
                  onProgress?.invoke(pct, "Extracting audio to WAV ($pct%)…")
                }
              }
            }

            codec.releaseOutputBuffer(outIndex, false)
          }
          outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
            val newFormat = codec.outputFormat
            if (newFormat.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
              sampleRate = newFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            }
            if (newFormat.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
              channelCount = newFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            }
            if (newFormat.containsKey(MediaFormat.KEY_PCM_ENCODING)) {
              pcmEncoding = newFormat.getInteger(MediaFormat.KEY_PCM_ENCODING)
            }
          }
        }
      }

      bos.flush()
    } finally {
      try { bos?.close() } catch (_: Exception) {}
      try { fos?.close() } catch (_: Exception) {}
      try { codec?.stop() } catch (_: Exception) {}
      try { codec?.release() } catch (_: Exception) {}
      try { extractor?.release() } catch (_: Exception) {}
    }

    // Patch header sizes now that all audio data is written
    WavHeaderHelper.patchWavSizes(outputFile)
    onProgress?.invoke(99, "Finalizing WAV file…")
    totalAudioBytesWritten
  }
}
