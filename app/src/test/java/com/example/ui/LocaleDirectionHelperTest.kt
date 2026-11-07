package com.example.ui

import androidx.compose.ui.unit.LayoutDirection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocaleDirectionHelperTest {

  @Test
  fun `verify RTL languages return true`() {
    assertTrue(LocaleDirectionHelper.isRtl("ur"))
    assertTrue(LocaleDirectionHelper.isRtl("ar"))
    assertTrue(LocaleDirectionHelper.isRtl("fa"))
    assertTrue(LocaleDirectionHelper.isRtl("he"))
  }

  @Test
  fun `verify LTR languages return false`() {
    assertFalse(LocaleDirectionHelper.isRtl("en"))
    assertFalse(LocaleDirectionHelper.isRtl("hi"))
    assertFalse(LocaleDirectionHelper.isRtl("fr"))
    assertFalse(LocaleDirectionHelper.isRtl("es"))
  }

  @Test
  fun `verify case insensitivity and regional tags`() {
    assertTrue(LocaleDirectionHelper.isRtl("UR"))
    assertTrue(LocaleDirectionHelper.isRtl("ur-PK"))
    assertTrue(LocaleDirectionHelper.isRtl("ar_EG"))
    assertFalse(LocaleDirectionHelper.isRtl("EN-US"))
    assertFalse(LocaleDirectionHelper.isRtl("hi_IN"))
  }

  @Test
  fun `verify empty and null handling`() {
    assertFalse(LocaleDirectionHelper.isRtl(null))
    assertFalse(LocaleDirectionHelper.isRtl(""))
    assertFalse(LocaleDirectionHelper.isRtl("   "))
  }

  @Test
  fun `verify layout direction mapping`() {
    assertEquals(LayoutDirection.Rtl, LocaleDirectionHelper.getLayoutDirection("ur"))
    assertEquals(LayoutDirection.Rtl, LocaleDirectionHelper.getLayoutDirection("ar"))
    assertEquals(LayoutDirection.Rtl, LocaleDirectionHelper.getLayoutDirection("fa"))
    assertEquals(LayoutDirection.Rtl, LocaleDirectionHelper.getLayoutDirection("he"))
    assertEquals(LayoutDirection.Ltr, LocaleDirectionHelper.getLayoutDirection("en"))
    assertEquals(LayoutDirection.Ltr, LocaleDirectionHelper.getLayoutDirection("hi"))
  }

  @Test
  fun `verify system fallback behaviour`() {
    assertEquals(LayoutDirection.Rtl, LocaleDirectionHelper.getLayoutDirection("system", systemIsRtl = true))
    assertEquals(LayoutDirection.Ltr, LocaleDirectionHelper.getLayoutDirection("system", systemIsRtl = false))
    assertEquals(LayoutDirection.Rtl, LocaleDirectionHelper.getLayoutDirection("", systemIsRtl = true))
    assertEquals(LayoutDirection.Ltr, LocaleDirectionHelper.getLayoutDirection("", systemIsRtl = false))
    assertEquals(LayoutDirection.Rtl, LocaleDirectionHelper.getLayoutDirection(null, systemIsRtl = true))
    assertEquals(LayoutDirection.Ltr, LocaleDirectionHelper.getLayoutDirection(null, systemIsRtl = false))
  }
}
