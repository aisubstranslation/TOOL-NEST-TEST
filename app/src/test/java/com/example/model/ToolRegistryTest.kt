package com.example.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolRegistryTest {

  @Test
  fun testRegistryContainsExactly22Tools() {
    assertEquals(22, ToolRegistry.tools.size)
  }

  @Test
  fun testToolIdsAreContiguous1To22() {
    val ids = ToolRegistry.tools.map { it.id }.sorted()
    val expected = (1..22).toList()
    assertEquals(expected, ids)
  }

  @Test
  fun testToolPhasesAndCategories() {
    val expectedCategoriesAndPhases = mapOf(
      1 to (Category.IMAGE to 2),
      2 to (Category.IMAGE to 2),
      3 to (Category.IMAGE to 2),
      4 to (Category.IMAGE to 3),
      5 to (Category.IMAGE to 2),
      6 to (Category.FILES to 3),
      7 to (Category.FILES to 3),
      8 to (Category.FILES to 3),
      9 to (Category.FILES to 4),
      10 to (Category.PDF to 4),
      11 to (Category.PDF to 4),
      12 to (Category.PDF to 5),
      13 to (Category.PDF to 5),
      14 to (Category.PDF to 5),
      15 to (Category.PDF to 4),
      16 to (Category.VIDEO_AUDIO to 6),
      17 to (Category.VIDEO_AUDIO to 6),
      18 to (Category.VIDEO_AUDIO to 6),
      19 to (Category.VIDEO_AUDIO to 6),
      20 to (Category.IMAGE to 7),
      21 to (Category.PDF to 8),
      22 to (Category.IMAGE to 8)
    )

    for ((id, expected) in expectedCategoriesAndPhases) {
      val tool = ToolRegistry.getById(id)
      assertNotNull("Tool with id $id should exist", tool)
      assertEquals("Category mismatch for tool $id", expected.first, tool?.category)
      assertEquals("Phase mismatch for tool $id", expected.second, tool?.phase)
    }
  }

  @Test
  fun testCategoriesDistribution() {
    val imageTools = ToolRegistry.getByCategory(Category.IMAGE)
    val filesTools = ToolRegistry.getByCategory(Category.FILES)
    val pdfTools = ToolRegistry.getByCategory(Category.PDF)
    val videoAudioTools = ToolRegistry.getByCategory(Category.VIDEO_AUDIO)

    assertEquals(listOf(1, 2, 3, 4, 5, 20, 22), imageTools.map { it.id })
    assertEquals(listOf(6, 7, 8, 9), filesTools.map { it.id })
    assertEquals(listOf(10, 11, 12, 13, 14, 15, 21), pdfTools.map { it.id })
    assertEquals(listOf(16, 17, 18, 19), videoAudioTools.map { it.id })
  }
}
