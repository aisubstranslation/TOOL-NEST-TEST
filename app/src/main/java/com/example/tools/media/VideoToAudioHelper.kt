package com.example.tools.media

import android.content.Context
import android.net.Uri
import com.example.file.FileFormatters
import com.example.file.FileLayer
import com.example.file.FileNameSanitizer
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

data class VideoToAudioOptions(
  val format: String = "M4A", // "M4A" or "WAV"
  val aacBitrateKbps: Int = 128,
  val customFileName: String? = null,
  val selectedTrackIndex: Int? = null
)

object VideoToAudioHelper {

  suspend fun extractAudioFromVideo(
    context: Context,
    videoUri: Uri,
    originalVideoName: String,
    options: VideoToAudioOptions,
    jobContext: JobContext
  ): JobResult = withContext(Dispatchers.IO) {
    jobContext.ensureActive()

    val fileInfo = FileLayer.getUriFileInfo(context, videoUri)
    if (fileInfo.sizeBytes > MediaProbe.MAX_VIDEO_BYTES) {
      throw IOException("Video file exceeds maximum allowed size of 1000 MB.")
    }

    val probe = MediaProbe.probeMedia(context, videoUri)
    if (!probe.hasAudio) {
      throw IOException("This video has no audio.")
    }

    val durationMs = probe.durationMs
    val durationSec = if (durationMs > 0) durationMs / 1000.0 else 0.0

    val estimatedOutputBytes = if (options.format.equals("WAV", ignoreCase = true)) {
      val audioTrack = probe.tracks.firstOrNull { it.isAudio }
      val sr = audioTrack?.sampleRate ?: 44100
      val ch = audioTrack?.channels ?: 2
      WavMath.estimateWavSizeBytes(durationSec, sr, ch)
    } else {
      ((options.aacBitrateKbps * 1000L / 8.0) * durationSec).toLong().coerceAtLeast(1024L)
    }

    if (options.format.equals("WAV", ignoreCase = true) && WavMath.exceedsWavLimit(estimatedOutputBytes)) {
      throw IOException("Estimated WAV size exceeds 3.8 GB limit.")
    }

    val freeBytes = FileLayer.getFreeSpaceBytes(context)
    if (!StorageRequirementCalculator.hasEnoughFreeSpace(freeBytes, estimatedOutputBytes)) {
      val needed = FileFormatters.formatBytes(StorageRequirementCalculator.calculateRequiredFreeSpace(estimatedOutputBytes))
      throw IOException("Not enough storage: need about $needed free.")
    }

    val ext = if (options.format.equals("WAV", ignoreCase = true)) "wav" else "m4a"
    val baseName = options.customFileName?.trim()?.ifEmpty { null }
      ?: "${originalVideoName.substringBeforeLast('.')}_audio"
    val cleanBase = FileNameSanitizer.sanitizeFileName(baseName)

    val jobsCacheDir = com.example.file.OutputDirs.getJobsDir(context)
    val jobFolder = File(jobsCacheDir, System.currentTimeMillis().toString())
    jobFolder.mkdirs()

    val outFile = File(jobFolder, "$cleanBase.$ext")
    jobContext.trackPartialOutput(outFile)

    jobContext.reportProgress(5, "Extracting audio…", 1, 1, outFile.name)

    if (options.format.equals("WAV", ignoreCase = true)) {
      WavEngine.extractOrConvertWav(
        context = context,
        inputUri = videoUri,
        outputFile = outFile,
        trackIndexToUse = options.selectedTrackIndex,
        jobContext = jobContext,
        onProgress = { pct, stage ->
          jobContext.reportProgress(pct, stage, 1, 1, outFile.name)
        }
      )
    } else {
      val isAlreadyAac = probe.audioMime.equals("audio/mp4a-latm", ignoreCase = true)
      val bitrateBps = if (isAlreadyAac) null else (options.aacBitrateKbps * 1000)

      MediaTransformerEngine.exportToAacM4a(
        context = context,
        inputUri = videoUri,
        outputFile = outFile,
        bitrateBps = bitrateBps,
        jobContext = jobContext,
        onProgress = { pct, stage ->
          jobContext.reportProgress(pct, stage, 1, 1, outFile.name)
        }
      )
    }

    jobContext.ensureActive()
    jobContext.reportProgress(98, "Verifying extracted audio…", 1, 1, outFile.name)

    val verification = MediaOutputVerifier.verifyOutput(
      context = context,
      outputFile = outFile,
      expectedFormat = options.format,
      expectedDurationMs = durationMs
    )

    if (!verification.isValid) {
      throw IOException(verification.errorMessage ?: "Extracted audio failed verification")
    }

    val finalSize = outFile.length()
    val fileResult = FileResult(
      inputName = originalVideoName,
      outputName = outFile.name,
      outputUri = outFile.absolutePath,
      outputSizeBytes = finalSize,
      originalSizeBytes = fileInfo.sizeBytes,
      success = true,
      tempFilePath = outFile.absolutePath,
      format = options.format,
      userMessage = "Extracted ${options.format} • ${FileFormatters.formatBytes(finalSize)}"
    )

    JobResult(files = listOf(fileResult))
  }
}
