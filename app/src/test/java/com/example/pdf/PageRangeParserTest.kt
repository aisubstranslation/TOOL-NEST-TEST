package com.example.pdf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PageRangeParserTest {

  @Test
  fun testSinglePage() {
    val result = PageRangeParser.parse("3", 5)
    assertTrue(result is PageRangeResult.Success)
    val success = result as PageRangeResult.Success
    assertEquals(listOf(2), success.pageIndices)
    assertEquals(listOf(3), success.pageNumbers)
    assertEquals(0, success.duplicatesRemoved)
  }

  @Test
  fun testContinuousRange() {
    val result = PageRangeParser.parse("1-4", 10)
    assertTrue(result is PageRangeResult.Success)
    val success = result as PageRangeResult.Success
    assertEquals(listOf(0, 1, 2, 3), success.pageIndices)
    assertEquals(listOf(1, 2, 3, 4), success.pageNumbers)
  }

  @Test
  fun testMixedRangesWithSpaces() {
    val result = PageRangeParser.parse(" 1 - 3 , 5 , 7 - 8 ", 10)
    assertTrue(result is PageRangeResult.Success)
    val success = result as PageRangeResult.Success
    assertEquals(listOf(0, 1, 2, 4, 6, 7), success.pageIndices)
    assertEquals(listOf(1, 2, 3, 5, 7, 8), success.pageNumbers)
  }

  @Test
  fun testDeduplication() {
    val result = PageRangeParser.parse("1, 2, 1, 3, 2", 5)
    assertTrue(result is PageRangeResult.Success)
    val success = result as PageRangeResult.Success
    assertEquals(listOf(0, 1, 2), success.pageIndices)
    assertEquals(2, success.duplicatesRemoved)
  }

  @Test
  fun testReversedRangeFails() {
    val result = PageRangeParser.parse("5-2", 10)
    assertTrue(result is PageRangeResult.Error)
    val err = result as PageRangeResult.Error
    assertTrue(err.message.contains("start page (5) cannot be greater than end page (2)"))
  }

  @Test
  fun testOutOfBoundsFails() {
    val result = PageRangeParser.parse("1-15", 10)
    assertTrue(result is PageRangeResult.Error)
    val err = result as PageRangeResult.Error
    assertTrue(err.message.contains("out of bounds"))
  }

  @Test
  fun testZeroOrNegativeFails() {
    val result = PageRangeParser.parse("0", 5)
    assertTrue(result is PageRangeResult.Error)

    val result2 = PageRangeParser.parse("-1-3", 5)
    assertTrue(result2 is PageRangeResult.Error)
  }

  @Test
  fun testEmptyInputFails() {
    val result = PageRangeParser.parse("   ", 5)
    assertTrue(result is PageRangeResult.Error)
  }

  @Test
  fun testInvalidCharactersFails() {
    val result = PageRangeParser.parse("1, abc, 3", 5)
    assertTrue(result is PageRangeResult.Error)
  }
}
