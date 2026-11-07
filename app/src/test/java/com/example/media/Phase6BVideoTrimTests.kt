package com.example.media

import com.example.tools.media.AudioTrimmerValidation
import com.example.tools.media.VideoCompressorMath
import com.example.tools.media.VideoQualityPreset
import com.example.tools.media.VideoResolutionPreset
import com.example.tools.media.WaveformHelper
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Phase6BVideoTrimTests {

  // ==========================================
  // 1. Trim Range Validator
  // ==========================================

  @Test
  fun testTrimRangeValidator() {
    val duration = 10_000L // 10 seconds

    // 1. Valid range
    val valid = AudioTrimmerValidation.validateTrimRange(1_000L, 5_000L, duration)
    assertTrue(valid.isValid)
    assertNull(valid.errorMessage)

    // 2. Exactly minimum length (500 ms)
    val exactMin = AudioTrimmerValidation.validateTrimRange(2_000L, 2_500L, duration)
    assertTrue(exactMin.isValid)

    // 3. Negative start
    val negStart = AudioTrimmerValidation.validateTrimRange(-50L, 2_000L, duration)
    assertFalse(negStart.isValid)
    assertTrue(negStart.errorMessage?.contains("negative") == true)

    // 4. Reversed boundaries (end <= start)
    val reversed = AudioTrimmerValidation.validateTrimRange(5_000L, 2_000L, duration)
    assertFalse(reversed.isValid)
    assertTrue(reversed.errorMessage?.contains("greater than") == true)

    val equal = AudioTrimmerValidation.validateTrimRange(2_000L, 2_000L, duration)
    assertFalse(equal.isValid)

    // 5. Exceeding duration
    val beyond = AudioTrimmerValidation.validateTrimRange(2_000L, 12_000L, duration)
    assertFalse(beyond.isValid)
    assertTrue(beyond.errorMessage?.contains("exceeds") == true)

    // 6. Too short (< 500 ms)
    val tooShort = AudioTrimmerValidation.validateTrimRange(1_000L, 1_400L, duration)
    assertFalse(tooShort.isValid)
    assertTrue(tooShort.errorMessage?.contains("500 ms") == true)
  }

  @Test
  fun testTimeFormattingAndParsing() {
    // mm:ss.SSS formatting
    assertEquals("00:00.000", AudioTrimmerValidation.formatMsToTime(0L))
    assertEquals("01:23.456", AudioTrimmerValidation.formatMsToTime(83_456L))
    assertEquals("10:00.000", AudioTrimmerValidation.formatMsToTime(600_000L))

    // Parsing
    assertEquals(83_456L, AudioTrimmerValidation.parseTimeToMs("01:23.456"))
    assertEquals(60_000L, AudioTrimmerValidation.parseTimeToMs("01:00"))
    assertEquals(5_500L, AudioTrimmerValidation.parseTimeToMs("5.5"))
    assertEquals(10_000L, AudioTrimmerValidation.parseTimeToMs("10"))
    assertNull(AudioTrimmerValidation.parseTimeToMs("invalid"))
  }

  // ==========================================
  // 2. Waveform Peak Bucketing
  // ==========================================

  @Test
  fun testWaveformPeakBucketingKnownValues() {
    // 8 samples split into 2 buckets of 4 samples each
    // Bucket 0: [0.1, 0.5, -0.8, 0.2] -> max absolute = 0.8
    // Bucket 1: [0.9, -1.0, 0.3, 0.4] -> max absolute = 1.0
    // Overall max = 1.0, normalized: bucket 0 = 0.8, bucket 1 = 1.0
    val samples = floatArrayOf(0.1f, 0.5f, -0.8f, 0.2f, 0.9f, -1.0f, 0.3f, 0.4f)
    val buckets = WaveformHelper.bucketPeaks(samples, 2)

    assertEquals(2, buckets.size)
    assertEquals(0.8f, buckets[0], 0.001f)
    assertEquals(1.0f, buckets[1], 0.001f)
  }

  @Test
  fun testWaveformPeakBucketingNormalization() {
    // 4 samples: [0.2, -0.4, 0.1, -0.5] in 2 buckets
    // Bucket 0: [0.2, -0.4] -> 0.4
    // Bucket 1: [0.1, -0.5] -> 0.5
    // Max overall = 0.5 -> normalized: [0.4 / 0.5 = 0.8, 0.5 / 0.5 = 1.0]
    val samples = floatArrayOf(0.2f, -0.4f, 0.1f, -0.5f)
    val buckets = WaveformHelper.bucketPeaks(samples, 2)

    assertEquals(2, buckets.size)
    assertEquals(0.8f, buckets[0], 0.001f)
    assertEquals(1.0f, buckets[1], 0.001f)
  }

  // ==========================================
  // 3. Target Resolution & Dimension Calculator
  // ==========================================

  @Test
  fun testTargetDimensionsLandscape() {
    // Landscape 1920 x 1080, rot 0. Short edge is 1080.
    val (w720, h720) = VideoCompressorMath.calculateTargetDimensions(
      sourceWidth = 1920,
      sourceHeight = 1080,
      rotationDegrees = 0,
      targetShortEdge = 720
    )
    // Scale = 720 / 1080 = 2/3. w = 1920 * 2/3 = 1280. h = 1080 * 2/3 = 720.
    assertEquals(1280, w720)
    assertEquals(720, h720)
    assertTrue(w720 % 2 == 0)
    assertTrue(h720 % 2 == 0)

    // Original returns even dimensions without scaling
    val (origW, origH) = VideoCompressorMath.calculateTargetDimensions(1920, 1080, 0, null)
    assertEquals(1920, origW)
    assertEquals(1080, origH)
  }

  @Test
  fun testTargetDimensionsPortrait() {
    // Portrait 1080 x 1920, rot 0. Short edge is 1080.
    val (w720, h720) = VideoCompressorMath.calculateTargetDimensions(
      sourceWidth = 1080,
      sourceHeight = 1920,
      rotationDegrees = 0,
      targetShortEdge = 720
    )
    // Scale = 720 / 1080 = 2/3. w = 1080 * 2/3 = 720. h = 1920 * 2/3 = 1280.
    assertEquals(720, w720)
    assertEquals(1280, h720)
    assertTrue(w720 % 2 == 0)
    assertTrue(h720 % 2 == 0)
  }

  @Test
  fun testTargetDimensionsRotated90() {
    // Raw video file 1920 x 1080 with rotation 90 degrees -> displayed is 1080 x 1920
    val (dispW, dispH) = VideoCompressorMath.getDisplayedDimensions(1920, 1080, 90)
    assertEquals(1080, dispW)
    assertEquals(1920, dispH)

    val (w480, h480) = VideoCompressorMath.calculateTargetDimensions(
      sourceWidth = 1920,
      sourceHeight = 1080,
      rotationDegrees = 90,
      targetShortEdge = 480
    )
    // Scale = 480 / 1080 = 4/9. dispW = 1080 * 4/9 = 480. dispH = 1920 * 4/9 = 853.33 -> even = 852
    assertEquals(480, w480)
    assertEquals(852, h480)
    assertTrue(w480 % 2 == 0)
    assertTrue(h480 % 2 == 0)
  }

  @Test
  fun testResolutionPresetDisablesUpscaling() {
    // Source displayed is 640 x 480 (short edge 480)
    // Presets 720p (720) and 1080p (1080) must be rejected / disallowed
    assertFalse(VideoCompressorMath.isPresetAllowed(640, 480, 0, 720))
    assertFalse(VideoCompressorMath.isPresetAllowed(640, 480, 0, 1080))
    assertFalse(VideoCompressorMath.isPresetAllowed(640, 480, 0, 480)) // equal is also disallowed (only downscale)
    assertTrue(VideoCompressorMath.isPresetAllowed(640, 480, 0, 360))
    assertTrue(VideoCompressorMath.isPresetAllowed(640, 480, 0, null)) // Original is always allowed
  }

  // ==========================================
  // 4. Bitrate from Preset & Clamping
  // ==========================================

  @Test
  fun testVideoBitrateCalculationAndClamping() {
    // 1. High preset (bpp 0.10), 1280 x 720, 30 fps
    // 0.10 * 1280 * 720 * 30 = 2,764,800 bps
    val highBitrate = VideoCompressorMath.calculateVideoBitrate(
      preset = VideoQualityPreset.HIGH,
      outputWidth = 1280,
      outputHeight = 720,
      sourceFrameRate = 30,
      sourceVideoBitrate = null
    )
    assertEquals(2_764_800, highBitrate)

    // 2. Minimum clamp: 300 kbps (300,000 bps)
    // Small (bpp 0.04), 320 x 240, 24 fps -> 0.04 * 320 * 240 * 24 = 73,728 bps -> clamped to 300,000
    val clampedMin = VideoCompressorMath.calculateVideoBitrate(
      preset = VideoQualityPreset.SMALL,
      outputWidth = 320,
      outputHeight = 240,
      sourceFrameRate = 24,
      sourceVideoBitrate = null
    )
    assertEquals(300_000, clampedMin)

    // 3. Maximum clamp: 0.9 * sourceVideoBitrate
    // Source bitrate is 1,000,000 bps (1 Mbps) -> max allowed is 900,000 bps
    val clampedMax = VideoCompressorMath.calculateVideoBitrate(
      preset = VideoQualityPreset.HIGH,
      outputWidth = 1280,
      outputHeight = 720,
      sourceFrameRate = 30,
      sourceVideoBitrate = 1_000_000
    )
    assertEquals(900_000, clampedMax)
  }

  // ==========================================
  // 5. Estimated Size & Reduction Classifier
  // ==========================================

  @Test
  fun testEstimatedOutputSizeAndClassifier() {
    // videoBitrate = 2,000,000 bps, audioBitrate = 128,000 bps, duration = 10.0s
    // total = 2,128,000 bps * 10 / 8 = 2,660,000 bytes + 65,536 overhead = 2,725,536 bytes
    val estimated = VideoCompressorMath.calculateEstimatedOutputSize(
      videoBitrateBps = 2_000_000,
      audioBitrateBps = 128_000,
      durationSec = 10.0
    )
    assertEquals(2_725_536L, estimated)

    // If source file is 2,800,000 bytes: estimated is >= 95% of source -> little reduction
    assertTrue(VideoCompressorMath.isLittleOrNoReduction(estimated, 2_800_000L))

    // If source file is 20,000,000 bytes: clear reduction
    assertFalse(VideoCompressorMath.isLittleOrNoReduction(estimated, 20_000_000L))

    // Reduction percentage calculation: (100MB - 40MB) / 100MB = 60%
    val reduction = VideoCompressorMath.calculateReductionPercentage(100_000_000L, 40_000_000L)
    assertEquals(60, reduction)
  }

  // ==========================================
  // 6. Output Name Generation
  // ==========================================

  @Test
  fun testOutputNameGeneration() {
    // Tool 19: Audio Trimmer
    val trimmedM4a = AudioTrimmerValidation.generateTrimmedOutputName("song.mp3", "m4a")
    assertEquals("song_trimmed.m4a", trimmedM4a)

    val trimmedWav = AudioTrimmerValidation.generateTrimmedOutputName("recording.wav", "wav")
    assertEquals("recording_trimmed.wav", trimmedWav)

    // Tool 18: Video Compressor
    val compressedMp4 = VideoCompressorMath.generateCompressedOutputName("holiday_video.mp4")
    assertEquals("holiday_video_compressed.mp4", compressedMp4)

    val compressedMov = VideoCompressorMath.generateCompressedOutputName("clip.mov")
    assertEquals("clip_compressed.mp4", compressedMov)
  }
}
