package com.example.bgremove

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Step2BgRemovePureMathTest {

  @Test
  fun testClampSplitFraction_normalValues() {
    val fraction = BgRemoveMath.clampSplitFraction(250f, 500f)
    assertEquals(0.5f, fraction, 0.001f)

    val lowerBound = BgRemoveMath.clampSplitFraction(10f, 500f)
    assertEquals(0.05f, lowerBound, 0.001f)

    val upperBound = BgRemoveMath.clampSplitFraction(490f, 500f)
    assertEquals(0.95f, upperBound, 0.001f)
  }

  @Test
  fun testClampSplitFraction_zeroOrNegativeOrExtremeWidth() {
    // Zero width must not divide by zero or crash, defaults safely
    val zeroW = BgRemoveMath.clampSplitFraction(100f, 0f)
    assertEquals(0.5f, zeroW, 0.001f)

    // Negative width must not produce negative or inverted fraction
    val negW = BgRemoveMath.clampSplitFraction(100f, -500f)
    assertEquals(0.5f, negW, 0.001f)

    // NaN or Infinity handling
    val nanX = BgRemoveMath.clampSplitFraction(Float.NaN, 500f)
    assertEquals(0.5f, nanX, 0.001f)

    val infW = BgRemoveMath.clampSplitFraction(100f, Float.POSITIVE_INFINITY)
    assertEquals(0.5f, infW, 0.001f)
  }

  @Test
  fun testCalculateBackgroundSampleSize() {
    // Exact or smaller fits in target 2048: sampleSize 1
    assertEquals(1, BgRemoveMath.calculateBackgroundSampleSize(2000, 1000, 2048))
    assertEquals(1, BgRemoveMath.calculateBackgroundSampleSize(2048, 2048, 2048))

    // 6000 x 4000 against 2000 target: sample size 2
    assertEquals(2, BgRemoveMath.calculateBackgroundSampleSize(6000, 4000, 2000))

    // 12000 x 8000 against 2000 target: sample size 4
    assertEquals(4, BgRemoveMath.calculateBackgroundSampleSize(12000, 8000, 2000))

    // Zero or negative input sizes safeguard to 1
    assertEquals(1, BgRemoveMath.calculateBackgroundSampleSize(0, 0, 2048))
    assertEquals(1, BgRemoveMath.calculateBackgroundSampleSize(-100, 2000, 2048))
  }

  @Test
  fun testComputeCutoutCacheKey_stability() {
    val key1 = BgRemoveMath.computeCutoutCacheKey(uriString = "content://media/1", maskVersion = 3, edgeSoftness = 1)
    val key2 = BgRemoveMath.computeCutoutCacheKey(uriString = "content://media/1", maskVersion = 3, edgeSoftness = 1)
    val key3 = BgRemoveMath.computeCutoutCacheKey(uriString = "content://media/1", maskVersion = 4, edgeSoftness = 1)

    assertEquals(key1, key2)
    assertTrue(key1 != key3)
    assertEquals("content://media/1:3:1", key1)
  }

  @Test
  fun testBilinearUpscaleMask_pureMath() {
    // 2x2 mask:
    // 0    255
    // 255  0
    val src = byteArrayOf(
      0.toByte(), 255.toByte(),
      255.toByte(), 0.toByte()
    )

    // Upscale to 4x4
    val dst = BgRemoveMath.bilinearUpscaleMask(src, 2, 2, 4, 4)
    assertEquals(16, dst.size)

    // Center interpolation should be around 128
    val centerVal = dst[5].toInt() and 0xFF
    assertTrue("Interpolated value should be between 0 and 255", centerVal in 50..200)

    // Test identical dimension copy
    val same = BgRemoveMath.bilinearUpscaleMask(src, 2, 2, 2, 2)
    org.junit.Assert.assertArrayEquals(src, same)
  }

  @Test
  fun testStampCircleEraseAndRestore() {
    val mask = ByteArray(25) { 255.toByte() } // 5x5 full mask

    // Erase circle at center (2f, 2f) radius 1.5f
    BgRemoveMath.stampCircle(mask, 5, 5, 2f, 2f, 1.5f, BrushMode.ERASE)
    // Center pixel (idx = 12) must be 0
    assertEquals(0, mask[12].toInt() and 0xFF)

    // Restore circle at center
    BgRemoveMath.stampCircle(mask, 5, 5, 2f, 2f, 1.5f, BrushMode.RESTORE)
    assertEquals(255, mask[12].toInt() and 0xFF)
  }

  @Test
  fun testCompositePixels() {
    val origRgb = intArrayOf(
      0x00FF0000, // Red
      0x0000FF00  // Green
    )
    val mask = byteArrayOf(128.toByte(), 255.toByte())
    val out = IntArray(2)

    BgRemoveMath.compositePixels(origRgb, mask, out, 0, 2)

    assertEquals((128 shl 24) or 0x00FF0000, out[0])
    assertEquals((255 shl 24) or 0x0000FF00, out[1])
  }
}
