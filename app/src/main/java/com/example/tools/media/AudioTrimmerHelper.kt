package com.example.tools.media

import android.content.Context
import android.net.Uri
import com.example.file.FileLayer
import com.example.media.DurationToleranceChecker
import com.example.media.MediaErrorMapper
import com.example.media.MediaOutputVerifier
import com.example.media.MediaProbe
import com.example.media.MediaTransformerEngine
import com.example.media.StorageRequirementCalculator
import com.example.media.WavEngine
import com.example.media.WavMath
import com.example.tools.common.FileResult
import com.example.tools.common.JobContext
import com.example.tools.common.JobResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

object AudioTrimmerHelper {

  suspend fun trimAudio(
    context: Context,
    inputUri: Uri,
    inputFileName: String,
    startMs: Long,
    endMs: Long,
    outputFormat: String, // "M4A" or "WAV"
    aacBitrateKbps: Int = 128,
    jobContext: JobContext? = null,
    onProgress: (suspend (percent: Int?, stage: String) -> Unit)? = null
  ): JobResult = withContext(Dispatchers.IO) {
    onProgress?.invoke(0, "Analyzing audio file…")

    val fileInfo = FileLayer.getUriFileInfo(context, inputUri)
    if (fileInfo.sizeBytes > 500L * 1024L * 1024L) {
      throw IOException("Audio file exceeds the 500 MB limit")
    }

    val probe = MediaProbe.probeMedia(context, inputUri)
    if (!probe.hasAudio) {
      throw IOException("This file has no audio track")
    }

    val clipDurationMs = (endMs - startMs).coerceAtLeast(0L)
    val clipDurationSec = clipDurationMs.toDouble() / 1000.0

    // Free space check
    val estimatedOutputBytes = if (outputFormat.equals("WAV", ignoreCase = true)) {
      val audioTrack = probe.tracks.firstOrNull { it.isAudio }
      val sampleRate = audioTrack?.sampleRate ?: 44100
      val channels = audioTrack?.channels ?: 2
      WavMath.estimateWavSizeBytes(clipDurationSec, sampleRate, channels)
    } else {
      val bps = aacBitrateKbps * 1000
      ((bps * clipDurationSec) / 8.0).toLong() + 64 * 1024L
    }

    val freeBytes = FileLayer.getFreeSpaceBytes(context)
    if (!StorageRequirementCalculator.hasEnoughFreeSpace(freeBytes, estimatedOutputBytes)) {
      throw IOException("Not enough free storage space in cache for trimmed audio")
    }

    val tempFolder = FileLayer.getTempFolder(context)
    val ext = if (outputFormat.equals("WAV", ignoreCase = true)) "wav" else "m4a"
    val outputFileName = AudioTrimmerValidation.generateTrimmedOutputName(inputFileName, ext)
    val outputFile = File(tempFolder, outputFileName)

    try {
      if (outputFormat.equals("WAV", ignoreCase = true)) {
        onProgress?.invoke(5, "Trimming uncompressed WAV…")
        WavEngine.extractOrConvertWav(
          context = context,
          inputUri = inputUri,
          outputFile = outputFile,
          startMs = startMs,
          endMs = endMs,
          jobContext = jobContext,
          onProgress = { pct, stage ->
            onProgress?.invoke(pct, stage)
          }
        )
      } else {
        onProgress?.invoke(5, "Trimming AAC M4A…")
        val bitrateBps = aacBitrateKbps * 1000
        MediaTransformerEngine.exportToAacM4a(
          context = context,
          inputUri = inputUri,
          outputFile = outputFile,
          bitrateBps = bitrateBps,
          startMs = startMs,
          endMs = endMs,
          jobContext = jobContext,
          onProgress = onProgress
        )
      }

      onProgress?.invoke(95, "Verifying trimmed output…")
      val verification = MediaOutputVerifier.verifyOutput(
        context = context,
        outputFile = outputFile,
        expectedFormat = outputFormat,
        expectedDurationMs = clipDurationMs
      )

      if (!verification.isValid) {
        throw IOException(verification.errorMessage ?: "Verification failed for trimmed audio")
      }

      val finalSize = outputFile.length()
      val fileResult = FileResult(
        inputName = inputFileName,
        outputName = outputFile.name,
        outputUri = outputFile.absolutePath,
        outputSizeBytes = finalSize,
        originalSizeBytes = fileInfo.sizeBytes,
        success = true,
        tempFilePath = outputFile.absolutePath,
        format = outputFormat.uppercase(),
        userMessage = "Trimmed to ${AudioTrimmerValidation.formatMsToTime(clipDurationMs)} • ${com.example.file.FileFormatters.formatBytes(finalSize)}"
      )

      JobResult(files = listOf(fileResult))
    } catch (t: Throwable) {
      if (outputFile.exists()) {
        try { outputFile.delete() } catch (_: Exception) {}
      }
      throw t
    }
  }
}
