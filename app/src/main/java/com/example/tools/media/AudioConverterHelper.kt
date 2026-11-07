package com.example.tools.media

import android.content.Context
import android.net.Uri
import com.example.file.FileFormatters
import com.example.file.FileLayer
import com.example.file.FileNameSanitizer
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

data class AudioConverterOptions(
  val format: String = "M4A", // "M4A" or "WAV"
  val aacBitrateKbps: Int = 128,
  val customFileName: String? = null
)

object AudioConverterHelper {

  val AAC_BITRATES = listOf(64, 96, 128, 192, 256)

  suspend fun convertAudio(
    context: Context,
    inputUri: Uri,
    originalName: String,
    options: AudioConverterOptions,
    jobContext: JobContext
  ): JobResult = withContext(Dispatchers.IO) {
    jobContext.ensureActive()

    val fileInfo = FileLayer.getUriFileInfo(context, inputUri)
    if (fileInfo.sizeBytes > MediaProbe.MAX_AUDIO_BYTES) {
      throw IOException("Audio file exceeds maximum allowed size of 500 MB.")
    }

    // Probe input media
    val probe = MediaProbe.probeMedia(context, inputUri)
    if (!probe.hasAudio) {
      throw IOException("This media file has no audio track.")
    }

    val durationMs = probe.durationMs
    val durationSec = if (durationMs > 0) durationMs / 1000.0 else 0.0

    // Estimate output size & check storage
    val estimatedOutputBytes = if (options.format.equals("WAV", ignoreCase = true)) {
      val audioTrack = probe.tracks.firstOrNull { it.isAudio }
      val sr = audioTrack?.sampleRate ?: 44100
      val ch = audioTrack?.channels ?: 2
      WavMath.estimateWavSizeBytes(durationSec, sr, ch)
    } else {
      // AAC: bitrate (bps) / 8 * durationSec
      ((options.aacBitrateKbps * 1000L / 8.0) * durationSec).toLong().coerceAtLeast(1024L)
    }

    if (options.format.equals("WAV", ignoreCase = true) && WavMath.exceedsWavLimit(estimatedOutputBytes)) {
      throw IOException("Estimated WAV size exceeds the 3.8 GB WAV limit.")
    }

    val freeBytes = FileLayer.getFreeSpaceBytes(context)
    if (!StorageRequirementCalculator.hasEnoughFreeSpace(freeBytes, estimatedOutputBytes)) {
      val neededFormatted = FileFormatters.formatBytes(StorageRequirementCalculator.calculateRequiredFreeSpace(estimatedOutputBytes))
      throw IOException("Not enough storage: need about $neededFormatted free.")
    }

    val ext = if (options.format.equals("WAV", ignoreCase = true)) "wav" else "m4a"
    val baseName = options.customFileName?.trim()?.ifEmpty { null }
      ?: "${originalName.substringBeforeLast('.')}_converted"
    val cleanBase = FileNameSanitizer.sanitizeFileName(baseName)

    val jobsCacheDir = com.example.file.OutputDirs.getJobsDir(context)
    val jobFolder = File(jobsCacheDir, System.currentTimeMillis().toString())
    jobFolder.mkdirs()

    val outFile = File(jobFolder, "$cleanBase.$ext")
    jobContext.trackPartialOutput(outFile)

    jobContext.reportProgress(5, "Starting audio conversion…", 1, 1, outFile.name)

    if (options.format.equals("WAV", ignoreCase = true)) {
      WavEngine.extractOrConvertWav(
        context = context,
        inputUri = inputUri,
        outputFile = outFile,
        jobContext = jobContext,
        onProgress = { pct, stage ->
          jobContext.reportProgress(pct, stage, 1, 1, outFile.name)
        }
      )
    } else {
      val bitrateBps = options.aacBitrateKbps * 1000
      MediaTransformerEngine.exportToAacM4a(
        context = context,
        inputUri = inputUri,
        outputFile = outFile,
        bitrateBps = bitrateBps,
        jobContext = jobContext,
        onProgress = { pct, stage ->
          jobContext.reportProgress(pct, stage, 1, 1, outFile.name)
        }
      )
    }

    jobContext.ensureActive()
    jobContext.reportProgress(98, "Verifying converted audio…", 1, 1, outFile.name)

    val verification = MediaOutputVerifier.verifyOutput(
      context = context,
      outputFile = outFile,
      expectedFormat = options.format,
      expectedDurationMs = durationMs
    )

    if (!verification.isValid) {
      throw IOException(verification.errorMessage ?: "Output audio failed verification")
    }

    val finalSize = outFile.length()
    val fileResult = FileResult(
      inputName = originalName,
      outputName = outFile.name,
      outputUri = outFile.absolutePath,
      outputSizeBytes = finalSize,
      originalSizeBytes = fileInfo.sizeBytes,
      success = true,
      tempFilePath = outFile.absolutePath,
      format = options.format,
      userMessage = "Converted to ${options.format} • ${FileFormatters.formatBytes(finalSize)}"
    )

    JobResult(files = listOf(fileResult))
  }
}
