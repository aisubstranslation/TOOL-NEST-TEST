package com.example.file

import com.example.model.Category
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FileLimitsTest {

  @Test
  fun testImageLimits() {
    val limit = 50L * 1024L * 1024L // 50 MB
    assertTrue(FileLimits.checkFileSizeLimit(Category.IMAGE, limit))
    assertTrue(FileLimits.checkFileSizeLimit(Category.IMAGE, limit - 100L))
    assertFalse(FileLimits.checkFileSizeLimit(Category.IMAGE, limit + 1L))
  }

  @Test
  fun testPdfLimits() {
    val limit = 150L * 1024L * 1024L // 150 MB
    assertTrue(FileLimits.checkFileSizeLimit(Category.PDF, limit))
    assertFalse(FileLimits.checkFileSizeLimit(Category.PDF, limit + 1L))
  }

  @Test
  fun testVideoLimits() {
    val limit = 1000L * 1024L * 1024L // 1000 MB
    assertTrue(FileLimits.checkFileSizeLimit(Category.VIDEO_AUDIO, limit))
    assertFalse(FileLimits.checkFileSizeLimit(Category.VIDEO_AUDIO, limit + 1L))
  }

  @Test
  fun testZeroAndNegativeBytesPass() {
    assertTrue(FileLimits.checkFileSizeLimit(Category.IMAGE, 0L))
    assertTrue(FileLimits.checkFileSizeLimit(Category.IMAGE, -1L))
  }
}
