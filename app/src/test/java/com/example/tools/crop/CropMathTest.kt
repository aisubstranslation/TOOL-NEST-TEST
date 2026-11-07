package com.example.tools.crop

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CropMathTest {

  @Test
  fun testSafeCoerce() {
    // Standard clamping
    assertEquals(10f, CropMath.safeCoerce(5f, 10f, 20f), 0.001f)
    assertEquals(20f, CropMath.safeCoerce(25f, 10f, 20f), 0.001f)
    assertEquals(15f, CropMath.safeCoerce(15f, 10f, 20f), 0.001f)

    // Inverted bounds (hi < lo) - must return lo and never throw
    assertEquals(20f, CropMath.safeCoerce(15f, 20f, 10f), 0.001f)
    assertEquals(0f, CropMath.safeCoerce(50f, 0f, -1021f), 0.001f)

    // Int version
    assertEquals(10, CropMath.safeCoerce(5, 10, 20))
    assertEquals(20, CropMath.safeCoerce(25, 10, 20))
    assertEquals(15, CropMath.safeCoerce(15, 10, 20))
    assertEquals(20, CropMath.safeCoerce(15, 20, 10))
  }

  @Test
  fun testOrientedSize() {
    assertEquals(OrientedSize(400, 300), CropMath.orientedSize(400, 300, 0))
    assertEquals(OrientedSize(300, 400), CropMath.orientedSize(400, 300, 90))
    assertEquals(OrientedSize(400, 300), CropMath.orientedSize(400, 300, 180))
    assertEquals(OrientedSize(300, 400), CropMath.orientedSize(400, 300, 270))
  }

  @Test
  fun testFitRect() {
    val fit1 = CropMath.fitRect(canvasW = 800f, canvasH = 600f, imageW = 400f, imageH = 300f)
    assertEquals(0f, fit1.left, 0.001f)
    assertEquals(0f, fit1.top, 0.001f)
    assertEquals(800f, fit1.right, 0.001f)
    assertEquals(600f, fit1.bottom, 0.001f)

    val fit2 = CropMath.fitRect(canvasW = 1000f, canvasH = 500f, imageW = 400f, imageH = 400f)
    assertEquals(250f, fit2.left, 0.001f)
    assertEquals(0f, fit2.top, 0.001f)
    assertEquals(750f, fit2.right, 0.001f)
    assertEquals(500f, fit2.bottom, 0.001f)
  }

  @Test
  fun testViewTransformRoundTrips() {
    val fit = CropRect(50f, 100f, 450f, 400f) // width=400, height=300
    val transform = ViewTransform(
      fit = fit,
      zoom = 2.5f,
      panX = 30f,
      panY = -20f,
      canvasW = 500f,
      canvasH = 600f,
      imageW = 400f,
      imageH = 300f
    )

    val testPoints = listOf(
      CropPoint(0f, 0f),
      CropPoint(400f, 300f),
      CropPoint(123.4f, 234.5f),
      CropPoint(200f, 150f)
    )

    for (pt in testPoints) {
      val canvasPt = transform.imageToCanvas(pt)
      val recoveredPt = transform.canvasToImage(canvasPt)
      assertEquals(pt.x, recoveredPt.x, 0.01f)
      assertEquals(pt.y, recoveredPt.y, 0.01f)
    }

    val canvasTestPoints = listOf(
      CropPoint(100f, 150f),
      CropPoint(300f, 400f),
      CropPoint(250f, 300f)
    )

    for (cPt in canvasTestPoints) {
      val imgPt = transform.canvasToImage(cPt)
      val recPt = transform.imageToCanvas(imgPt)
      assertEquals(cPt.x, recPt.x, 0.01f)
      assertEquals(cPt.y, recPt.y, 0.01f)
    }
  }

  @Test
  fun testClampCropTinyImagesAndBoundsSmallerThanMin() {
    // 10x10 tiny image: minimum is min(16, 10) = 10 px
    val tinyClamped = CropMath.clampCrop(
      rect = CropRect(-5f, -5f, 20f, 20f),
      imageW = 10f,
      imageH = 10f,
      minW = 16f,
      minH = 16f
    )
    assertEquals(0f, tinyClamped.left, 0.001f)
    assertEquals(0f, tinyClamped.top, 0.001f)
    assertEquals(10f, tinyClamped.right, 0.001f)
    assertEquals(10f, tinyClamped.bottom, 0.001f)
    assertEquals(10f, tinyClamped.width, 0.001f)
    assertEquals(10f, tinyClamped.height, 0.001f)

    // Inverted bounds (right < left, bottom < top)
    val inverted = CropMath.clampCrop(
      rect = CropRect(50f, 50f, 10f, 20f),
      imageW = 100f,
      imageH = 100f,
      minW = 16f,
      minH = 16f
    )
    assertTrue(inverted.right >= inverted.left + 16f)
    assertTrue(inverted.bottom >= inverted.top + 16f)
    assertTrue(inverted.left >= 0f && inverted.right <= 100f)
    assertTrue(inverted.top >= 0f && inverted.bottom <= 100f)
  }

  @Test
  fun testApplyAspectRatios() {
    // 1:1 on 400x300 image
    val initial = CropRect(0f, 0f, 400f, 300f)
    val square = CropMath.applyAspect(
      rect = initial,
      ratio = 1f,
      handle = CropHandle.NONE,
      imageW = 400f,
      imageH = 300f,
      minW = 16f,
      minH = 16f
    )
    assertEquals(square.width, square.height, 0.01f)
    assertEquals(300f, square.width, 0.01f)
    assertEquals(50f, square.left, 0.01f)
    assertEquals(350f, square.right, 0.01f)

    // 16:9 on 400x300 image
    val aspect16_9 = CropMath.applyAspect(
      rect = initial,
      ratio = 16f / 9f,
      handle = CropHandle.NONE,
      imageW = 400f,
      imageH = 300f,
      minW = 16f,
      minH = 16f
    )
    assertEquals(16f / 9f, aspect16_9.width / aspect16_9.height, 0.01f)
    assertTrue(aspect16_9.left >= 0f && aspect16_9.right <= 400f)
    assertTrue(aspect16_9.top >= 0f && aspect16_9.bottom <= 300f)
  }

  @Test
  fun testHitTest() {
    val fit = CropRect(100f, 100f, 300f, 300f)
    val transform = ViewTransform(
      fit = fit,
      zoom = 1f,
      panX = 0f,
      panY = 0f,
      canvasW = 400f,
      canvasH = 400f,
      imageW = 200f,
      imageH = 200f
    )
    val rect = CropRect(0f, 0f, 200f, 200f) // maps to (100, 100, 300, 300) on canvas
    val radius = 24f

    assertEquals(CropHandle.TOP_LEFT, CropMath.hitTest(CropPoint(100f, 100f), rect, transform, radius))
    assertEquals(CropHandle.TOP_RIGHT, CropMath.hitTest(CropPoint(300f, 100f), rect, transform, radius))
    assertEquals(CropHandle.BOTTOM_LEFT, CropMath.hitTest(CropPoint(100f, 300f), rect, transform, radius))
    assertEquals(CropHandle.BOTTOM_RIGHT, CropMath.hitTest(CropPoint(300f, 300f), rect, transform, radius))
    assertEquals(CropHandle.TOP, CropMath.hitTest(CropPoint(200f, 100f), rect, transform, radius))
    assertEquals(CropHandle.BOTTOM, CropMath.hitTest(CropPoint(200f, 300f), rect, transform, radius))
    assertEquals(CropHandle.LEFT, CropMath.hitTest(CropPoint(100f, 200f), rect, transform, radius))
    assertEquals(CropHandle.RIGHT, CropMath.hitTest(CropPoint(300f, 200f), rect, transform, radius))
    assertEquals(CropHandle.INSIDE, CropMath.hitTest(CropPoint(200f, 200f), rect, transform, radius))
    assertEquals(CropHandle.NONE, CropMath.hitTest(CropPoint(10f, 10f), rect, transform, radius))
  }

  @Test
  fun testMapOrientedRectToRawHandVerifiedExamples() {
    val rawW = 400
    val rawH = 300

    // Prompt Hand-verified example: raw 400 x 300, EXIF rotate 90 (clockwise), oriented size 300 x 400.
    // Oriented crop left=10, top=20, width=100, height=50 maps to RAW rectangle left=20, top=190, width=50, height=100.
    val crop = CropRect(left = 10f, top = 20f, right = 110f, bottom = 70f)
    val mapped90 = CropMath.mapOrientedRectToRaw(
      rect = crop,
      rawW = rawW,
      rawH = rawH,
      orientationDegrees = 90,
      isFlipped = false
    )
    assertEquals(20, mapped90.left)
    assertEquals(190, mapped90.top)
    assertEquals(50, mapped90.width)
    assertEquals(100, mapped90.height)

    // Test 0 degrees
    val mapped0 = CropMath.mapOrientedRectToRaw(
      rect = crop,
      rawW = rawW,
      rawH = rawH,
      orientationDegrees = 0,
      isFlipped = false
    )
    assertEquals(10, mapped0.left)
    assertEquals(20, mapped0.top)
    assertEquals(100, mapped0.width)
    assertEquals(50, mapped0.height)

    // Test 180 degrees (raw left = W - right)
    // raw left = 400 - 110 = 290, raw top = 300 - 70 = 230
    val mapped180 = CropMath.mapOrientedRectToRaw(
      rect = crop,
      rawW = rawW,
      rawH = rawH,
      orientationDegrees = 180,
      isFlipped = false
    )
    assertEquals(290, mapped180.left)
    assertEquals(230, mapped180.top)
    assertEquals(100, mapped180.width)
    assertEquals(50, mapped180.height)

    // Test 270 degrees
    // ix = 400 - 70 = 330, iy = 10, iw = 50, ih = 100
    val mapped270 = CropMath.mapOrientedRectToRaw(
      rect = crop,
      rawW = rawW,
      rawH = rawH,
      orientationDegrees = 270,
      isFlipped = false
    )
    assertEquals(330, mapped270.left)
    assertEquals(10, mapped270.top)
    assertEquals(50, mapped270.width)
    assertEquals(100, mapped270.height)

    // Test flipped cases
    val mappedFlipped0 = CropMath.mapOrientedRectToRaw(
      rect = crop,
      rawW = rawW,
      rawH = rawH,
      orientationDegrees = 0,
      isFlipped = true
    )
    assertEquals(290, mappedFlipped0.left)
    assertEquals(20, mappedFlipped0.top)
    assertEquals(100, mappedFlipped0.width)
    assertEquals(50, mappedFlipped0.height)

    val mappedFlipped90 = CropMath.mapOrientedRectToRaw(
      rect = crop,
      rawW = rawW,
      rawH = rawH,
      orientationDegrees = 90,
      isFlipped = true
    )
    assertEquals(330, mappedFlipped90.left)
    assertEquals(190, mappedFlipped90.top)
    assertEquals(50, mappedFlipped90.width)
    assertEquals(100, mappedFlipped90.height)
  }

  @Test
  fun testCropHistoryPushUndoRedoAndClear() {
    val history = CropHistory(maxCapacity = 3)
    val s0 = CropStateSnapshot(CropRect(0f, 0f, 100f, 100f), CropAspectRatio.FREE)
    val s1 = CropStateSnapshot(CropRect(10f, 10f, 90f, 90f), CropAspectRatio.FREE)
    val s2 = CropStateSnapshot(CropRect(20f, 20f, 80f, 80f), CropAspectRatio.RATIO_1_1)

    history.reset(s0)
    assertFalse(history.canUndo)
    assertFalse(history.canRedo)

    history.push(s1)
    assertTrue(history.canUndo)
    assertFalse(history.canRedo)

    history.push(s2)
    assertEquals(s2, history.current())

    // Undo to s1
    val undo1 = history.undo()
    assertNotNull(undo1)
    assertEquals(s1, undo1)
    assertTrue(history.canRedo)

    // Redo back to s2
    val redo1 = history.redo()
    assertNotNull(redo1)
    assertEquals(s2, redo1)
    assertFalse(history.canRedo)

    // Undo again, then push new action -> clears redo stack
    history.undo()
    assertTrue(history.canRedo)
    val s3 = CropStateSnapshot(CropRect(30f, 30f, 70f, 70f), CropAspectRatio.RATIO_16_9)
    history.push(s3)
    assertFalse(history.canRedo)
    assertNull(history.redo())

    // Capacity check: maxCapacity = 3
    val s4 = CropStateSnapshot(CropRect(40f, 40f, 60f, 60f), CropAspectRatio.FREE)
    history.push(s4)
    assertTrue(history.undoCount <= 3)
  }

  @Test
  fun testCalculateInSampleSize() {
    // Hand-verified examples from specification:
    // (4000, 3000, 1600) -> 2
    // (1600, 1200, 1600) -> 1
    // (8000, 6000, 1600) -> 4
    // (3000, 2000, 1600) -> 1
    assertEquals(2, CropMath.calculateInSampleSize(4000, 3000, 1600))
    assertEquals(1, CropMath.calculateInSampleSize(1600, 1200, 1600))
    assertEquals(4, CropMath.calculateInSampleSize(8000, 6000, 1600))
    assertEquals(1, CropMath.calculateInSampleSize(3000, 2000, 1600))
  }

  @Test
  fun testFitRatioInImage_largestRectFitsAndClamps() {
    // Image 2307 x 2944 (portrait)
    val imgW = 2307f
    val imgH = 2944f

    // 1:1 ratio
    // maxW and maxH must be min(2307, 2944) = 2307
    val fit11 = CropMath.fitRatioInImage(CropAspectRatio.RATIO_1_1, imgW, imgH)
    assertEquals(2307f, fit11.width, 0.001f)
    assertEquals(2307f, fit11.height, 0.001f)
    assertEquals(0f, fit11.left, 0.001f)
    assertEquals(2307f, fit11.right, 0.001f)
    // Vertically centered: (2944 - 2307) / 2 = 637 / 2 = 318.5
    assertEquals(318.5f, fit11.top, 0.001f)
    assertEquals(2625.5f, fit11.bottom, 0.001f)

    // 16:9 ratio (landscape on portrait image)
    // ratio = 16/9 = 1.7777778
    // maxW = 2307, maxH = 2307 / (16/9) = 1297.6875
    val fit16_9 = CropMath.fitRatioInImage(CropAspectRatio.RATIO_16_9, imgW, imgH)
    assertEquals(2307f, fit16_9.width, 0.001f)
    assertEquals(2307f / (16f / 9f), fit16_9.height, 0.001f)
    assertTrue(fit16_9.left >= 0f && fit16_9.right <= imgW)
    assertTrue(fit16_9.top >= 0f && fit16_9.bottom <= imgH)

    // 9:16 ratio (tall portrait on portrait image)
    // ratio = 9/16 = 0.5625
    // imgW / imgH = 2307 / 2944 = 0.7836 > 0.5625
    // so maxH = 2944, maxW = 2944 * (9/16) = 1656
    val fit9_16 = CropMath.fitRatioInImage(CropAspectRatio.RATIO_9_16, imgW, imgH)
    assertEquals(1656f, fit9_16.width, 0.001f)
    assertEquals(2944f, fit9_16.height, 0.001f)
    assertEquals(0f, fit9_16.top, 0.001f)
    assertEquals(2944f, fit9_16.bottom, 0.001f)
    // Horizontally centered: (2307 - 1656) / 2 = 651 / 2 = 325.5
    assertEquals(325.5f, fit9_16.left, 0.001f)
    assertEquals(1981.5f, fit9_16.right, 0.001f)

    // FREE ratio returns full image
    val freeFit = CropMath.fitRatioInImage(CropAspectRatio.FREE, imgW, imgH)
    assertEquals(0f, freeFit.left, 0.001f)
    assertEquals(0f, freeFit.top, 0.001f)
    assertEquals(imgW, freeFit.right, 0.001f)
    assertEquals(imgH, freeFit.bottom, 0.001f)
  }
}
