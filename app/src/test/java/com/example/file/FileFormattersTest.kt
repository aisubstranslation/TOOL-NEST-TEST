package com.example.file

import org.junit.Assert.assertEquals
import org.junit.Test

class FileFormattersTest {

  @Test
  fun testFormatBytes_zeroAndNegative() {
    assertEquals("0 B", FileFormatters.formatBytes(0L))
    assertEquals("0 B", FileFormatters.formatBytes(-100L))
  }

  @Test
  fun testFormatBytes_bytesAndKb() {
    assertEquals("500 B", FileFormatters.formatBytes(500L))
    assertEquals("1.0 KB", FileFormatters.formatBytes(1024L))
    assertEquals("1.5 KB", FileFormatters.formatBytes(1536L))
  }

  @Test
  fun testFormatBytes_megabytesAndGigabytes() {
    assertEquals("1.0 MB", FileFormatters.formatBytes(1024L * 1024L))
    assertEquals("50.0 MB", FileFormatters.formatBytes(50L * 1024L * 1024L))
    assertEquals("1.00 GB", FileFormatters.formatBytes(1024L * 1024L * 1024L))
    assertEquals("2.00 GB", FileFormatters.formatBytes(2L * 1024L * 1024L * 1024L))
  }

  @Test
  fun testFormatPercentageChange_zeroOriginal() {
    assertEquals("0%", FileFormatters.formatPercentageChange(0L, 500L))
    assertEquals("0%", FileFormatters.formatPercentageChange(-10L, 500L))
  }

  @Test
  fun testFormatPercentageChange_equal() {
    assertEquals("0%", FileFormatters.formatPercentageChange(1000L, 1000L))
  }

  @Test
  fun testFormatPercentageChange_reduction() {
    assertEquals("-40.0%", FileFormatters.formatPercentageChange(1000L, 600L))
    assertEquals("-50.0%", FileFormatters.formatPercentageChange(2000L, 1000L))
  }

  @Test
  fun testFormatPercentageChange_increase() {
    assertEquals("+20.0% (Larger)", FileFormatters.formatPercentageChange(1000L, 1200L))
  }
}
