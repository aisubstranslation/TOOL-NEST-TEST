package com.example.pdf

import com.example.tools.pdf.PdfRotationHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PdfRotationHelperTest {

  @Test
  fun testNormalize() {
    assertEquals(0, PdfRotationHelper.normalize(0))
    assertEquals(90, PdfRotationHelper.normalize(90))
    assertEquals(180, PdfRotationHelper.normalize(180))
    assertEquals(270, PdfRotationHelper.normalize(270))
    assertEquals(0, PdfRotationHelper.normalize(360))
    assertEquals(90, PdfRotationHelper.normalize(450))
    assertEquals(270, PdfRotationHelper.normalize(-90))
    assertEquals(180, PdfRotationHelper.normalize(-180))
    assertEquals(90, PdfRotationHelper.normalize(-270))
  }

  @Test
  fun testApplyDelta() {
    assertEquals(180, PdfRotationHelper.applyDelta(90, 90))
    assertEquals(0, PdfRotationHelper.applyDelta(270, 90))
    assertEquals(270, PdfRotationHelper.applyDelta(0, -90))
    assertEquals(180, PdfRotationHelper.applyDelta(0, 180))
  }

  @Test
  fun testMatchesFilter() {
    // 0-based page 0 -> human page 1 (Odd)
    assertTrue(PdfRotationHelper.matchesFilter(0, "all"))
    assertTrue(PdfRotationHelper.matchesFilter(0, "odd"))
    assertFalse(PdfRotationHelper.matchesFilter(0, "even"))

    // 0-based page 1 -> human page 2 (Even)
    assertTrue(PdfRotationHelper.matchesFilter(1, "all"))
    assertFalse(PdfRotationHelper.matchesFilter(1, "odd"))
    assertTrue(PdfRotationHelper.matchesFilter(1, "even"))
  }
}
