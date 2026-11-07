package com.example.bgremove

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Phase7BgRemoveTests {

  // ==========================================
  // 1. Pre-processing Maths (Hand-Verified)
  // ==========================================

  @Test
  fun testPreprocessRgbPixelsHandCalculated() {
    // 2 pixels (width=2, height=1)
    // Pixel 0: A=255, R=128, G=64, B=32 (0xFF804020)
    // Pixel 1: A=255, R=255, G=128, B=0 (0xFFFF8000)
    val pixels = intArrayOf(
      ((255 shl 24) or (128 shl 16) or (64 shl 8) or 32),
      ((255 shl 24) or (255 shl 16) or (128 shl 8) or 0)
    )

    val tensor = BgRemoveMath.preprocessRgbPixels(pixels, width = 2, height = 1)

    // Tensor shape: 3 channels * 2 pixels = 6 floats
    assertEquals(6, tensor.size)

    // Hand-calculated expected values:
    // maxVal = 255
    // R0: (128/255 - 0.485) / 0.229 = 0.07406f
    // R1: (255/255 - 0.485) / 0.229 = 2.24891f
    // G0: (64/255 - 0.456) / 0.224 = -0.91527f
    // G1: (128/255 - 0.456) / 0.224 = 0.20518f
    // B0: (32/255 - 0.406) / 0.225 = -1.24671f
    // B1: (0/255 - 0.406) / 0.225 = -1.80444f

    assertEquals(0.07406f, tensor[0], 0.001f) // R0
    assertEquals(2.24891f, tensor[1], 0.001f) // R1
    assertEquals(-0.91527f, tensor[2], 0.001f) // G0
    assertEquals(0.20518f, tensor[3], 0.001f) // G1
    assertEquals(-1.24671f, tensor[4], 0.001f) // B0
    assertEquals(-1.80444f, tensor[5], 0.001f) // B1
  }

  // ==========================================
  // 2. Post-processing Min-Max Normalization
  // ==========================================

  @Test
  fun testMinMaxNormalizeHandCalculated() {
    val raw = floatArrayOf(-2.0f, 0.0f, 2.0f, 6.0f)
    // min = -2.0, max = 6.0, diff = 8.0
    // expected: [(-2 - -2)/8 = 0.0, (0 - -2)/8 = 0.25, (2 - -2)/8 = 0.5, (6 - -2)/8 = 1.0]
    val norm = BgRemoveMath.minMaxNormalize(raw)

    assertEquals(4, norm.size)
    assertEquals(0.0f, norm[0], 0.0001f)
    assertEquals(0.25f, norm[1], 0.0001f)
    assertEquals(0.5f, norm[2], 0.0001f)
    assertEquals(1.0f, norm[3], 0.0001f)
  }

  @Test
  fun testMinMaxNormalizeConstantArray() {
    // When all values are identical (max == min), output must be 0.0 and avoid div by zero
    val constantRaw = floatArrayOf(4.5f, 4.5f, 4.5f, 4.5f)
    val norm = BgRemoveMath.minMaxNormalize(constantRaw)

    assertEquals(4, norm.size)
    for (v in norm) {
      assertEquals(0.0f, v, 0.0001f)
    }
  }

  @Test
  fun testNormalizedTo8BitMask() {
    val norm = floatArrayOf(0.0f, 0.25f, 0.5f, 1.0f)
    val mask = BgRemoveMath.normalizedTo8BitMask(norm)

    assertEquals(4, mask.size)
    assertEquals(0.toByte(), mask[0])
    assertEquals(64.toByte(), mask[1]) // 0.25 * 255 = 63.75 -> 64
    assertEquals(128.toByte(), mask[2]) // 0.5 * 255 = 127.5 -> 128
    assertEquals(255.toByte(), mask[3]) // 1.0 * 255 = 255
  }

  // ==========================================
  // 3. Stroke History (Undo, Redo, Size Limit)
  // ==========================================

  @Test
  fun testStrokeHistoryUndoRedo() {
    val manager = BgRemoveHistoryManager(maxHistory = 3)

    assertFalse(manager.canUndo)
    assertFalse(manager.canRedo)

    val stroke1 = BrushStroke(BrushMode.ERASE, 20f, listOf(PointF(10f, 10f)))
    val stroke2 = BrushStroke(BrushMode.RESTORE, 30f, listOf(PointF(20f, 20f)))

    manager.push(EditAction.StrokeAction(stroke1))
    manager.push(EditAction.StrokeAction(stroke2))

    assertTrue(manager.canUndo)
    assertFalse(manager.canRedo)
    assertEquals(2, manager.strokeList.size)

    // Undo stroke2
    val undone = manager.undo()
    assertNotNull(undone)
    assertTrue(undone is EditAction.StrokeAction)
    assertEquals(stroke2, (undone as EditAction.StrokeAction).stroke)
    assertTrue(manager.canRedo)
    assertEquals(1, manager.strokeList.size)

    // Redo stroke2
    val redone = manager.redo()
    assertNotNull(redone)
    assertEquals(stroke2, (redone as EditAction.StrokeAction).stroke)
    assertFalse(manager.canRedo)
    assertEquals(2, manager.strokeList.size)

    // Undo again, then push new stroke -> redo stack must be cleared
    manager.undo()
    val stroke3 = BrushStroke(BrushMode.ERASE, 15f, listOf(PointF(5f, 5f)))
    manager.push(EditAction.StrokeAction(stroke3))
    assertFalse(manager.canRedo)
    assertEquals(2, manager.strokeList.size)
    assertEquals(stroke3, manager.strokeList.last())
  }

  @Test
  fun testStrokeHistoryMaxLimit() {
    val manager = BgRemoveHistoryManager(maxHistory = 2)

    val s1 = BrushStroke(BrushMode.ERASE, 10f, listOf(PointF(1f, 1f)))
    val s2 = BrushStroke(BrushMode.ERASE, 20f, listOf(PointF(2f, 2f)))
    val s3 = BrushStroke(BrushMode.ERASE, 30f, listOf(PointF(3f, 3f)))

    manager.push(EditAction.StrokeAction(s1))
    manager.push(EditAction.StrokeAction(s2))
    manager.push(EditAction.StrokeAction(s3))

    // s1 dropped due to maxHistory = 2
    assertEquals(2, manager.size())
    assertEquals(s2, manager.strokeList[0])
    assertEquals(s3, manager.strokeList[1])
  }

  // ==========================================
  // 4. Box Blur on Mask
  // ==========================================

  @Test
  fun testBoxBlurRadiusZero() {
    val mask = byteArrayOf(10, 20, 30, 40)
    val blurred = BgRemoveMath.applyBoxBlur(mask, 2, 2, radius = 0)
    assertArrayEquals(mask, blurred)
  }

  @Test
  fun testBoxBlurSmoothing() {
    // 3x3 mask with a bright center impulse:
    // 0   0   0
    // 0  255  0
    // 0   0   0
    val mask = ByteArray(9) { 0 }
    mask[4] = 255.toByte()

    val blurred = BgRemoveMath.applyBoxBlur(mask, width = 3, height = 3, radius = 1)
    assertEquals(9, blurred.size)

    // Center pixel should be smoothed (non-zero and lower than 255)
    val center = blurred[4].toInt() and 0xFF
    assertTrue(center in 1..254)

    // Surrounding pixels should now receive diffused value > 0
    val neighbor = blurred[1].toInt() and 0xFF
    assertTrue(neighbor > 0)
  }

  // ==========================================
  // 5. 16 Megapixel Limit Check
  // ==========================================

  @Test
  fun testMegapixelLimit() {
    // 16 MP is 16 * 1024 * 1024 = 16,777,216 pixels
    assertTrue(BgRemoveMath.isWithinMegapixelLimit(4000, 3000)) // 12,000,000 px <= 16 MP
    assertTrue(BgRemoveMath.isWithinMegapixelLimit(4608, 3456)) // 15,925,248 px <= 16 MP
    assertFalse(BgRemoveMath.isWithinMegapixelLimit(5000, 4000)) // 20,000,000 px > 16 MP
    assertFalse(BgRemoveMath.isWithinMegapixelLimit(6000, 4000)) // 24,000,000 px > 16 MP
    assertFalse(BgRemoveMath.isWithinMegapixelLimit(0, 0)) // Invalid dimensions
  }

  // ==========================================
  // 6. Export Filename Generation
  // ==========================================

  @Test
  fun testOutputNameGeneration() {
    assertEquals("portrait_cutout.png", BgRemoveMath.generateCutoutOutputName("portrait.jpg", "png"))
    assertEquals("portrait_cutout.jpg", BgRemoveMath.generateCutoutOutputName("portrait.jpg", "jpg"))
    assertEquals("my_cat_cutout.webp", BgRemoveMath.generateCutoutOutputName("my_cat.png", "webp"))
    assertEquals("sample.test_cutout.png", BgRemoveMath.generateCutoutOutputName("sample.test.jpeg", "png"))
    assertEquals("image_cutout.png", BgRemoveMath.generateCutoutOutputName("", "png"))
  }
}
