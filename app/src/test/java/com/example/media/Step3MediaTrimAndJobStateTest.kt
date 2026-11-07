package com.example.media

import com.example.tools.media.AudioTrimmerValidation
import com.example.tools.media.VideoCompressorMath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Step3MediaTrimAndJobStateTest {

  @Test
  fun testAudioTrimValidation_boundaries() {
    // 1. Duration is 0ms or negative
    val zeroDur = AudioTrimmerValidation.validateTrimRange(0L, 0L, 0L)
    assertFalse(zeroDur.isValid)

    // 2. Normal valid trim
    val valid = AudioTrimmerValidation.validateTrimRange(1_000L, 6_000L, 10_000L)
    assertTrue(valid.isValid)
    assertNull(valid.errorMessage)

    // 3. Minimum length boundary (500 ms)
    val exactMin = AudioTrimmerValidation.validateTrimRange(1_000L, 1_500L, 10_000L)
    assertTrue(exactMin.isValid)

    val subMin = AudioTrimmerValidation.validateTrimRange(1_000L, 1_499L, 10_000L)
    assertFalse(subMin.isValid)

    // 4. Inverted range (end < start)
    val inverted = AudioTrimmerValidation.validateTrimRange(5_000L, 2_000L, 10_000L)
    assertFalse(inverted.isValid)

    // 5. Exceeding file duration
    val overDur = AudioTrimmerValidation.validateTrimRange(5_000L, 15_000L, 10_000L)
    assertFalse(overDur.isValid)
  }

  @Test
  fun testAudioTrimTimeFormatting() {
    assertEquals("00:00.000", AudioTrimmerValidation.formatMsToTime(0L))
    assertEquals("00:01.500", AudioTrimmerValidation.formatMsToTime(1500L))
    assertEquals("01:05.123", AudioTrimmerValidation.formatMsToTime(65123L))
    assertEquals("10:00.000", AudioTrimmerValidation.formatMsToTime(600000L))
  }

  @Test
  fun testVideoDimensionsRotationOrientation() {
    // 0 deg: raw (1920, 1080) -> stays (1920, 1080)
    val normal = VideoCompressorMath.getDisplayedDimensions(1920, 1080, 0)
    assertEquals(1920, normal.first)
    assertEquals(1080, normal.second)

    // 90 deg: raw (1920, 1080) -> swapped to (1080, 1920)
    val rot90 = VideoCompressorMath.getDisplayedDimensions(1920, 1080, 90)
    assertEquals(1080, rot90.first)
    assertEquals(1920, rot90.second)

    // 180 deg: raw (1920, 1080) -> stays (1920, 1080)
    val rot180 = VideoCompressorMath.getDisplayedDimensions(1920, 1080, 180)
    assertEquals(1920, rot180.first)
    assertEquals(1080, rot180.second)

    // 270 deg: raw (1920, 1080) -> swapped to (1080, 1920)
    val rot270 = VideoCompressorMath.getDisplayedDimensions(1920, 1080, 270)
    assertEquals(1080, rot270.first)
    assertEquals(1920, rot270.second)

    // Negative rotation (-90 deg): raw (1920, 1080) -> swapped to (1080, 1920)
    val rotNeg90 = VideoCompressorMath.getDisplayedDimensions(1920, 1080, -90)
    assertEquals(1080, rotNeg90.first)
    assertEquals(1920, rotNeg90.second)

    // 360 deg: raw (1920, 1080) -> not swapped (1920, 1080)
    val rot360 = VideoCompressorMath.getDisplayedDimensions(1920, 1080, 360)
    assertEquals(1920, rot360.first)
    assertEquals(1080, rot360.second)
  }
}
