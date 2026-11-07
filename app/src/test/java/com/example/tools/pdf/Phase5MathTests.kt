package com.example.tools.pdf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Phase5MathTests {

  @Test
  fun testMmToPointsConversion() {
    val tenMmInPoints = ImagesToPdfLayout.mmToPoints(10f)
    // 10 * 72 / 25.4 = 28.346457...
    assertEquals(28.346457f, tenMmInPoints, 0.001f)
    assertEquals(28.3465f, ImagesToPdfLayout.MARGIN_10MM_PT, 0.001f)

    val twentyMmInPoints = ImagesToPdfLayout.mmToPoints(20f)
    // 20 * 72 / 25.4 = 56.692913...
    assertEquals(56.692913f, twentyMmInPoints, 0.001f)
    assertEquals(56.6929f, ImagesToPdfLayout.MARGIN_20MM_PT, 0.001f)
  }

  @Test
  fun testImagesToPdfPlacementPreservesAspectAndCenters() {
    // Page: 600 x 800, margin: 0, image: 1000 x 500 (2:1 aspect)
    val placement = ImagesToPdfLayout.calculateImagePlacement(
      pageWidthPt = 600f,
      pageHeightPt = 800f,
      marginPt = 0f,
      imageWidth = 1000,
      imageHeight = 500
    )

    // Printable width is 600, scale = 600/1000 = 0.6
    assertEquals(600f, placement.drawWidth, 0.01f)
    assertEquals(300f, placement.drawHeight, 0.01f)
    // X is 0, Y is centered: (800 - 300) / 2 = 250
    assertEquals(0f, placement.drawX, 0.01f)
    assertEquals(250f, placement.drawY, 0.01f)
  }

  @Test
  fun testImagesToPdfPlacementWithMargins() {
    val margin = 20f
    // Page: 500 x 500, margin: 20 -> printable area = 460 x 460
    // Image: 230 x 460 (1:2 aspect) -> scale = 460 / 460 = 1.0
    val placement = ImagesToPdfLayout.calculateImagePlacement(
      pageWidthPt = 500f,
      pageHeightPt = 500f,
      marginPt = margin,
      imageWidth = 230,
      imageHeight = 460
    )

    assertEquals(230f, placement.drawWidth, 0.01f)
    assertEquals(460f, placement.drawHeight, 0.01f)
    // X centered in printable: 20 + (460 - 230) / 2 = 135
    assertEquals(135f, placement.drawX, 0.01f)
    // Y centered in printable: 20 + (460 - 460) / 2 = 20
    assertEquals(20f, placement.drawY, 0.01f)
  }

  @Test
  fun testOrientationAutoDecision() {
    // Landscape image (1200 x 800) with AUTO orientation on A4 (595 x 842)
    val landscapeDim = ImagesToPdfLayout.determinePageDimensions(
      pageSize = PageSizeOption.A4,
      orientation = OrientationOption.AUTO,
      imageWidth = 1200,
      imageHeight = 800,
      marginPt = 0f
    )
    assertEquals(842f, landscapeDim.pageWidthPt, 0.01f)
    assertEquals(595f, landscapeDim.pageHeightPt, 0.01f)

    // Portrait image (800 x 1200) with AUTO orientation on A4
    val portraitDim = ImagesToPdfLayout.determinePageDimensions(
      pageSize = PageSizeOption.A4,
      orientation = OrientationOption.AUTO,
      imageWidth = 800,
      imageHeight = 1200,
      marginPt = 0f
    )
    assertEquals(595f, portraitDim.pageWidthPt, 0.01f)
    assertEquals(842f, portraitDim.pageHeightPt, 0.01f)
  }

  @Test
  fun testPdfToImagesPixelCalculationAnd16MegapixelLimit() {
    // Standard A4 page: 595 x 842 pt
    // At 72 DPI: 595 x 842 px
    val (w72, h72) = PdfToImagesMath.calculatePixelDimensions(595, 842, 72)
    assertEquals(595, w72)
    assertEquals(842, h72)
    assertFalse(PdfToImagesMath.checkExceedsMaxPixels(595, 842, 72))

    // At 300 DPI:
    // 595 * 300 / 72 = 2479.16 -> 2479 px
    // 842 * 300 / 72 = 3508.33 -> 3508 px
    val (w300, h300) = PdfToImagesMath.calculatePixelDimensions(595, 842, 300)
    assertEquals(2479, w300)
    assertEquals(3508, h300)
    // 2479 * 3508 = 8,696,332 px (< 16 MP)
    assertFalse(PdfToImagesMath.checkExceedsMaxPixels(595, 842, 300))

    // Huge architecture blueprint (2500 x 2500 pt) at 300 DPI:
    // (2500 * 300 / 72) = 10416 px -> 10416 * 10416 = 108,493,056 px (> 16 MP)
    assertTrue(PdfToImagesMath.checkExceedsMaxPixels(2500, 2500, 300))
  }

  @Test
  fun testOutputNameGeneration() {
    val name1 = PdfToImagesMath.formatPageImageName("Invoice.pdf", 0, "png")
    assertEquals("Invoice_page_001.png", name1)

    val name2 = PdfToImagesMath.formatPageImageName("Annual Report 2026", 19, "jpg")
    assertEquals("Annual_Report_2026_page_020.jpg", name2)
  }

  @Test
  fun testCompressionClassificationAndPercentage() {
    assertEquals("REDUCED", PdfCompressorMath.classifyResult(origSize = 1000L, compressedSize = 600L))
    assertEquals(40, PdfCompressorMath.calculateReductionPercentage(origSize = 1000L, compressedSize = 600L))

    assertEquals("EQUAL", PdfCompressorMath.classifyResult(origSize = 500L, compressedSize = 500L))
    assertEquals(0, PdfCompressorMath.calculateReductionPercentage(origSize = 500L, compressedSize = 500L))

    assertEquals("LARGER", PdfCompressorMath.classifyResult(origSize = 500L, compressedSize = 600L))
    assertEquals(0, PdfCompressorMath.calculateReductionPercentage(origSize = 500L, compressedSize = 600L))

    // Safe zero handling
    assertEquals("INVALID_INPUT", PdfCompressorMath.classifyResult(origSize = 0L, compressedSize = 0L))
    assertEquals(0, PdfCompressorMath.calculateReductionPercentage(origSize = 0L, compressedSize = 0L))
  }

  @Test
  fun testCompressionPresetsIntegrity() {
    val (edgeH, qH, dpiH) = PdfCompressorMath.getPresetParams(CompressionPreset.HIGHER_QUALITY)
    assertEquals(2400, edgeH)
    assertEquals(85, qH)
    assertEquals(160, dpiH)

    val (edgeB, qB, dpiB) = PdfCompressorMath.getPresetParams(CompressionPreset.BALANCED)
    assertEquals(1600, edgeB)
    assertEquals(70, qB)
    assertEquals(130, dpiB)

    val (edgeS, qS, dpiS) = PdfCompressorMath.getPresetParams(CompressionPreset.SMALLER_FILE)
    assertEquals(1200, edgeS)
    assertEquals(55, qS)
    assertEquals(100, dpiS)
  }
}
