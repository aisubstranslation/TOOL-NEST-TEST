package com.example.media

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import java.io.File

object MediaOutputVerifier {

  data class VerificationResult(
    val isValid: Boolean,
    val durationMs: Long = 0L,
    val sizeBytes: Long = 0L,
    val mimeType: String? = null,
    val errorMessage: String? = null
  )

  /**
   * Verifies output file against existence, non-zero size, duration tolerance, and expected track.
   * If verification fails, deletes the invalid output file.
   */
  fun verifyOutput(
    context: Context,
    outputFile: File,
    expectedFormat: String, // "WAV" or "M4A" (or AAC)
    expectedDurationMs: Long = 0L
  ): VerificationResult {
    if (!outputFile.exists() || outputFile.length() == 0L) {
      if (outputFile.exists()) outputFile.delete()
      return VerificationResult(isValid = false, errorMessage = "Output file is missing or empty")
    }

    val fileSize = outputFile.length()

    if (expectedFormat.equals("WAV", ignoreCase = true)) {
      val wavInfo = WavHeaderHelper.validateWavFile(outputFile)
      if (!wavInfo.isValid) {
        outputFile.delete()
        return VerificationResult(isValid = false, errorMessage = wavInfo.errorMessage ?: "Invalid WAV header structure")
      }

      if (expectedDurationMs > 0 && !DurationToleranceChecker.isWithinTolerance(wavInfo.durationMs, expectedDurationMs)) {
        outputFile.delete()
        return VerificationResult(
          isValid = false,
          errorMessage = "WAV duration (${wavInfo.durationMs} ms) is outside tolerance of expected duration (${expectedDurationMs} ms)"
        )
      }

      return VerificationResult(
        isValid = true,
        durationMs = wavInfo.durationMs,
        sizeBytes = fileSize,
        mimeType = "audio/wav"
      )
    }

    // M4A / AAC verification
    var retriever: MediaMetadataRetriever? = null
    var extractor: MediaExtractor? = null

    try {
      retriever = MediaMetadataRetriever()
      retriever.setDataSource(outputFile.absolutePath)
      val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
      val durationMs = durationStr?.toLongOrNull() ?: 0L

      if (durationMs <= 0) {
        outputFile.delete()
        return VerificationResult(isValid = false, errorMessage = "Generated M4A has zero or unreadable duration")
      }

      if (expectedDurationMs > 0 && !DurationToleranceChecker.isWithinTolerance(durationMs, expectedDurationMs)) {
        outputFile.delete()
        return VerificationResult(
          isValid = false,
          errorMessage = "M4A duration ($durationMs ms) is outside tolerance of expected duration ($expectedDurationMs ms)"
        )
      }

      extractor = MediaExtractor()
      extractor.setDataSource(outputFile.absolutePath)
      var foundAacTrack = false
      var detectedMime: String? = null

      for (i in 0 until extractor.trackCount) {
        val format = extractor.getTrackFormat(i)
        val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
        if (mime.equals("audio/mp4a-latm", ignoreCase = true) || mime.startsWith("audio/")) {
          foundAacTrack = true
          detectedMime = mime
          break
        }
      }

      if (!foundAacTrack) {
        outputFile.delete()
        return VerificationResult(isValid = false, errorMessage = "M4A output does not contain a valid AAC audio track")
      }

      return VerificationResult(
        isValid = true,
        durationMs = durationMs,
        sizeBytes = fileSize,
        mimeType = detectedMime ?: "audio/mp4"
      )
    } catch (e: Exception) {
      if (outputFile.exists()) outputFile.delete()
      return VerificationResult(isValid = false, errorMessage = "Verification failed: ${e.message}")
    } finally {
      try { retriever?.release() } catch (_: Exception) {}
      try { extractor?.release() } catch (_: Exception) {}
    }
  }

  data class VideoVerificationResult(
    val isValid: Boolean,
    val durationMs: Long = 0L,
    val width: Int = 0,
    val height: Int = 0,
    val sizeBytes: Long = 0L,
    val hasAudio: Boolean = false,
    val errorMessage: String? = null
  )

  /**
   * Verifies video output against existence, duration tolerance (maxOf(500ms, 1%)),
   * displayed dimensions, non-null frame at time 0, and audio track presence.
   */
  fun verifyVideoOutput(
    outputFile: File,
    expectedDurationMs: Long = 0L,
    expectedWidth: Int = 0,
    expectedHeight: Int = 0,
    expectAudio: Boolean = true
  ): VideoVerificationResult {
    if (!outputFile.exists() || outputFile.length() == 0L) {
      if (outputFile.exists()) outputFile.delete()
      return VideoVerificationResult(isValid = false, errorMessage = "Output video is missing or empty")
    }

    val fileSize = outputFile.length()
    var retriever: MediaMetadataRetriever? = null
    var extractor: MediaExtractor? = null

    try {
      retriever = MediaMetadataRetriever()
      retriever.setDataSource(outputFile.absolutePath)

      val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
      val durationMs = durationStr?.toLongOrNull() ?: 0L
      if (durationMs <= 0L) {
        outputFile.delete()
        return VideoVerificationResult(isValid = false, errorMessage = "Output video has zero or unreadable duration")
      }

      if (expectedDurationMs > 0L) {
        val tolerance = maxOf(500L, (expectedDurationMs * 0.01).toLong())
        val diff = kotlin.math.abs(durationMs - expectedDurationMs)
        if (diff > tolerance) {
          outputFile.delete()
          return VideoVerificationResult(
            isValid = false,
            errorMessage = "Video duration ($durationMs ms) differs from expected ($expectedDurationMs ms) by $diff ms (allowed: $tolerance ms)"
          )
        }
      }

      val widthStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
      val heightStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
      val rotationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)

      val rawW = widthStr?.toIntOrNull() ?: 0
      val rawH = heightStr?.toIntOrNull() ?: 0
      val rotation = rotationStr?.toIntOrNull() ?: 0

      val (dispW, dispH) = if (rotation == 90 || rotation == 270) rawH to rawW else rawW to rawH

      if (expectedWidth > 0 && expectedHeight > 0) {
        // Allow ±4 pixels due to macroblock/codec even alignment
        val diffW = kotlin.math.abs(dispW - expectedWidth)
        val diffH = kotlin.math.abs(dispH - expectedHeight)
        if (diffW > 4 || diffH > 4) {
          outputFile.delete()
          return VideoVerificationResult(
            isValid = false,
            errorMessage = "Output dimensions ($dispW x $dispH) do not match expected ($expectedWidth x $expectedHeight)"
          )
        }
      }

      // Verify frame rendering
      val frameBitmap = retriever.getFrameAtTime(0)
      if (frameBitmap == null) {
        outputFile.delete()
        return VideoVerificationResult(isValid = false, errorMessage = "Failed to render video frame from output")
      }

      // Check audio track if expected
      extractor = MediaExtractor()
      extractor.setDataSource(outputFile.absolutePath)
      var foundAudioTrack = false
      for (i in 0 until extractor.trackCount) {
        val format = extractor.getTrackFormat(i)
        val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
        if (mime.startsWith("audio/")) {
          foundAudioTrack = true
          break
        }
      }

      if (expectAudio && !foundAudioTrack) {
        outputFile.delete()
        return VideoVerificationResult(isValid = false, errorMessage = "Expected audio track not found in output video")
      }

      return VideoVerificationResult(
        isValid = true,
        durationMs = durationMs,
        width = dispW,
        height = dispH,
        sizeBytes = fileSize,
        hasAudio = foundAudioTrack
      )
    } catch (e: Exception) {
      if (outputFile.exists()) outputFile.delete()
      return VideoVerificationResult(isValid = false, errorMessage = "Verification failed: ${e.message}")
    } finally {
      try { retriever?.release() } catch (_: Exception) {}
      try { extractor?.release() } catch (_: Exception) {}
    }
  }
}
