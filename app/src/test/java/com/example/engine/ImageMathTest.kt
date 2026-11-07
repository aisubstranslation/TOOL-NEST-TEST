package com.example.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ImageMathTest {

  @Test
  fun testPercentageDimensions() {
    val (w50, h50) = ImageMath.calculateDimensionsByPercentage(1000, 500, 50)
    assertEquals(500, w50)
    assertEquals(250, h50)

    val (w25, h25) = ImageMath.calculateDimensionsByPercentage(1000, 500, 25)
    assertEquals(250, w25)
    assertEquals(125, h25)

    val (w200, h200) = ImageMath.calculateDimensionsByPercentage(1000, 500, 200)
    assertEquals(2000, w200)
    assertEquals(1000, h200)
  }

  @Test
  fun testLockedAspectByWidthAndHeight() {
    val (w1, h1) = ImageMath.calculateDimensionsLockedAspectByWidth(1920, 1080, 960)
    assertEquals(960, w1)
    assertEquals(540, h1)

    val (w2, h2) = ImageMath.calculateDimensionsLockedAspectByHeight(1920, 1080, 540)
    assertEquals(960, w2)
    assertEquals(540, h2)
  }

  @Test
  fun testLongEdgeDimensions() {
    val (wLandscape, hLandscape) = ImageMath.calculateDimensionsForLongEdge(4000, 3000, 1080)
    assertEquals(1080, wLandscape)
    assertEquals(810, hLandscape)

    val (wPortrait, hPortrait) = ImageMath.calculateDimensionsForLongEdge(3000, 4000, 1080)
    assertEquals(810, wPortrait)
    assertEquals(1080, hPortrait)
  }

  @Test
  fun testMegaPixelLimitChecks() {
    // 48 MP exact limit: 8000 x 6000 = 48,000,000 pixels
    assertTrue(ImageMath.isWithinMegaPixelLimit(8000, 6000, 48))
    assertTrue(ImageMath.isWithinMegaPixelLimit(1920, 1080, 48))

    // 8001 x 6000 = 48,006,000 pixels (> 48 MP)
    assertFalse(ImageMath.isWithinMegaPixelLimit(8001, 6000, 48))
  }

  @Test
  fun testMagicBytesVerification() {
    // Hand-crafted JPEG header
    val jpegHeader = byteArrayOf(
      0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(),
      0x00.toByte(), 0x10.toByte(), 0x4A.toByte(), 0x46.toByte()
    )
    assertTrue(ImageMath.verifyMagicBytes(jpegHeader, "jpeg"))
    assertTrue(ImageMath.verifyMagicBytes(jpegHeader, "jpg"))
    assertFalse(ImageMath.verifyMagicBytes(jpegHeader, "png"))
    assertFalse(ImageMath.verifyMagicBytes(jpegHeader, "webp"))

    // Hand-crafted PNG header
    val pngHeader = byteArrayOf(
      0x89.toByte(), 0x50.toByte(), 0x4E.toByte(), 0x47.toByte(),
      0x0D.toByte(), 0x0A.toByte(), 0x1A.toByte(), 0x0A.toByte()
    )
    assertTrue(ImageMath.verifyMagicBytes(pngHeader, "png"))
    assertFalse(ImageMath.verifyMagicBytes(pngHeader, "jpeg"))
    assertFalse(ImageMath.verifyMagicBytes(pngHeader, "webp"))

    // Hand-crafted WebP header (RIFF + 4 bytes + WEBP)
    val webpHeader = byteArrayOf(
      0x52.toByte(), 0x49.toByte(), 0x46.toByte(), 0x46.toByte(), // RIFF
      0x24.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), // Size (36)
      0x57.toByte(), 0x45.toByte(), 0x42.toByte(), 0x50.toByte(), // WEBP
      0x56.toByte(), 0x50.toByte(), 0x38.toByte(), 0x20.toByte()  // VP8_
    )
    assertTrue(ImageMath.verifyMagicBytes(webpHeader, "webp"))
    assertFalse(ImageMath.verifyMagicBytes(webpHeader, "jpeg"))
    assertFalse(ImageMath.verifyMagicBytes(webpHeader, "png"))

    // Invalid header
    val invalidHeader = byteArrayOf(0x00, 0x01, 0x02, 0x03, 0x04, 0x05)
    assertFalse(ImageMath.verifyMagicBytes(invalidHeader, "jpeg"))
    assertFalse(ImageMath.verifyMagicBytes(invalidHeader, "png"))
    assertFalse(ImageMath.verifyMagicBytes(invalidHeader, "webp"))
  }
}
