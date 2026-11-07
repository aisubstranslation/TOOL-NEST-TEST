package com.example.tools.media

import android.content.Context
import android.net.Uri
import com.example.file.FileFormatters
import com.example.file.FileLayer
import com.example.media.MediaOutputVerifier
import com.example.media.MediaProbe
import com.example.media.MediaTransformerEngine
import com.example.media.StorageRequirementCalculator
import com.example.media.VideoAudioMode
import com.example.tools.common.FileResult
import com.example.tools.common.JobContext
import com.example.tools.common.JobResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

object VideoCompressorHelper {

  suspend fun compressVideo(
    context: Context,
    inputUri: Uri,
    inputFileName: String,
    resolutionPreset: VideoResolutionPreset,
    qualityPreset: VideoQualityPreset,
    audioMode: VideoAudioMode,
    jobContext: JobContext? = null,
    onProgress: (suspend (percent: Int?, stage: String) -> Unit)? = null
  ): JobResult = withContext(Dispatchers.IO) {
    onProgress?.invoke(0, "Analyzing video stream…")

    val fileInfo = FileLayer.getUriFileInfo(context, inputUri)
    if (fileInfo.sizeBytes > 1000L * 1024L * 1024L) {
      throw IOException("Video exceeds the 1000 MB limit")
    }

    val probe = MediaProbe.probeMedia(context, inputUri)
    if (!probe.hasVideo) {
      throw IOException("This file contains no video track")
    }

    val rotation = probe.rotationDegrees
    val rawW = if (rotation == 90 || rotation == 270) probe.height else probe.width
    val rawH = if (rotation == 90 || rotation == 270) probe.width else probe.height
    val sourceFps = probe.frameRate?.toInt() ?: VideoCompressorMath.DEFAULT_FRAME_RATE
    val videoTrack = probe.tracks.firstOrNull { it.isVideo }
    val sourceVideoBitrate = videoTrack?.bitrate ?: (if (probe.bitrate > 0) probe.bitrate.toInt() else null)
    val durationMs = probe.durationMs
    val durationSec = durationMs.toDouble() / 1000.0

    val (targetW, targetH) = VideoCompressorMath.calculateTargetDimensions(
      sourceWidth = rawW,
      sourceHeight = rawH,
      rotationDegrees = rotation,
      targetShortEdge = resolutionPreset.shortEdge
    )

    val videoBitrate = VideoCompressorMath.calculateVideoBitrate(
      preset = qualityPreset,
      outputWidth = targetW,
      outputHeight = targetH,
      sourceFrameRate = sourceFps,
      sourceVideoBitrate = sourceVideoBitrate
    )

    val audioTrack = probe.tracks.firstOrNull { it.isAudio }
    val audioBitrate = when (audioMode) {
      VideoAudioMode.KEEP_ORIGINAL -> audioTrack?.bitrate ?: 128_000
      VideoAudioMode.COMPRESS_AAC_96KBPS -> 96_000
      VideoAudioMode.REMOVE_AUDIO -> 0
    }

    val estimatedBytes = VideoCompressorMath.calculateEstimatedOutputSize(
      videoBitrateBps = videoBitrate,
      audioBitrateBps = audioBitrate,
      durationSec = durationSec
    )

    // Storage check with 300 MB margin
    val freeBytes = FileLayer.getFreeSpaceBytes(context)
    if (!StorageRequirementCalculator.hasEnoughFreeSpace(freeBytes, estimatedBytes)) {
      throw IOException("Not enough free storage space in cache for compressed video")
    }

    val tempFolder = FileLayer.getTempFolder(context)
    val outputFileName = VideoCompressorMath.generateCompressedOutputName(inputFileName)
    val outputFile = File(tempFolder, outputFileName)

    try {
      onProgress?.invoke(5, "Compressing video…")
      val applyScaling = resolutionPreset != VideoResolutionPreset.ORIGINAL

      MediaTransformerEngine.compressVideo(
        context = context,
        inputUri = inputUri,
        outputFile = outputFile,
        targetWidth = if (applyScaling) targetW else null,
        targetHeight = if (applyScaling) targetH else null,
        videoBitrateBps = videoBitrate,
        audioMode = audioMode,
        jobContext = jobContext,
        onProgress = onProgress
      )

      onProgress?.invoke(95, "Verifying output video…")
      val expectAudio = audioMode != VideoAudioMode.REMOVE_AUDIO && probe.hasAudio
      val verification = MediaOutputVerifier.verifyVideoOutput(
        outputFile = outputFile,
        expectedDurationMs = durationMs,
        expectedWidth = targetW,
        expectedHeight = targetH,
        expectAudio = expectAudio
      )

      if (!verification.isValid) {
        throw IOException(verification.errorMessage ?: "Verification failed for compressed video")
      }

      val reductionPercent = VideoCompressorMath.calculateReductionPercentage(
        originalBytes = fileInfo.sizeBytes,
        newBytes = outputFile.length()
      )

      val finalSize = outputFile.length()
      val fileResult = FileResult(
        inputName = inputFileName,
        outputName = outputFile.name,
        outputUri = outputFile.absolutePath,
        outputSizeBytes = finalSize,
        originalSizeBytes = fileInfo.sizeBytes,
        success = true,
        width = verification.width,
        height = verification.height,
        format = "MP4",
        tempFilePath = outputFile.absolutePath,
        userMessage = "Compressed • ${FileFormatters.formatBytes(finalSize)} (-$reductionPercent%)"
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
