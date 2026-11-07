package com.example.tools.pdf

import org.junit.Assert.assertEquals
import org.junit.Test

class Step6ImagesToPdfSettingsTest {

  @Test
  fun testPageSizeOptionMapping() {
    assertEquals(PageSizeOption.A4, PageSizeOption.fromSetting(null))
    assertEquals(PageSizeOption.A4, PageSizeOption.fromSetting("A4"))
    assertEquals(PageSizeOption.A4, PageSizeOption.fromSetting("a4"))
    assertEquals(PageSizeOption.A4, PageSizeOption.fromSetting("unknown"))
    assertEquals(PageSizeOption.LETTER, PageSizeOption.fromSetting("LETTER"))
    assertEquals(PageSizeOption.LETTER, PageSizeOption.fromSetting("letter"))
    assertEquals(PageSizeOption.FIT_TO_IMAGE, PageSizeOption.fromSetting("FIT_TO_IMAGE"))
    assertEquals(PageSizeOption.FIT_TO_IMAGE, PageSizeOption.fromSetting("fit"))
  }

  @Test
  fun testPageDimensions() {
    assertEquals(595f, ImagesToPdfLayout.A4_WIDTH_PT, 0.01f)
    assertEquals(842f, ImagesToPdfLayout.A4_HEIGHT_PT, 0.01f)
    assertEquals(612f, ImagesToPdfLayout.LETTER_WIDTH_PT, 0.01f)
    assertEquals(792f, ImagesToPdfLayout.LETTER_HEIGHT_PT, 0.01f)

    assertEquals(0f, ImagesToPdfLayout.getMarginPoints(MarginOption.NONE), 0.01f)
    assertEquals(28.3465f, ImagesToPdfLayout.getMarginPoints(MarginOption.MARGIN_10MM), 0.01f)
    assertEquals(56.6929f, ImagesToPdfLayout.getMarginPoints(MarginOption.MARGIN_20MM), 0.01f)
  }
}
