package com.example.tools.common

import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class FileSelectionHelperTest {

  private fun createItem(id: Int, name: String = "img$id.jpg", size: Long = 1024L): SelectedFileItem {
    return SelectedFileItem(
      uri = Uri.parse("content://media/external/images/media/$id"),
      name = name,
      sizeBytes = size,
      width = 800,
      height = 600
    )
  }

  @Test
  fun testAddFilesIncreasesListCorrectly() {
    val initial = emptyList<SelectedFileItem>()
    val incoming = listOf(
      createItem(1, "photo1.jpg"),
      createItem(2, "photo2.jpg")
    )

    val result = FileSelectionHelper.addFiles(initial, incoming)

    assertEquals(2, result.updatedList.size)
    assertEquals(2, result.addedCount)
    assertEquals(0, result.duplicateCount)
    assertEquals(0, result.overflowCount)
    assertEquals("photo1.jpg", result.updatedList[0].name)
    assertEquals("photo2.jpg", result.updatedList[1].name)
  }

  @Test
  fun testEnforceMaxLimit() {
    val incoming = (1..25).map { createItem(it, "photo$it.jpg") }

    val result = FileSelectionHelper.addFiles(emptyList(), incoming, maxLimit = 20)

    assertEquals(20, result.updatedList.size)
    assertEquals(20, result.addedCount)
    assertEquals(0, result.duplicateCount)
    assertEquals(5, result.overflowCount)
  }

  @Test
  fun testSkipDuplicates() {
    val initial = listOf(
      createItem(1, "existing1.jpg"),
      createItem(2, "existing2.jpg")
    )
    val incoming = listOf(
      createItem(2, "existing2.jpg"), // Duplicate
      createItem(3, "new3.jpg"),       // New
      createItem(1, "existing1.jpg")  // Duplicate
    )

    val result = FileSelectionHelper.addFiles(initial, incoming)

    assertEquals(3, result.updatedList.size)
    assertEquals(1, result.addedCount)
    assertEquals(2, result.duplicateCount)
    assertEquals(0, result.overflowCount)
    assertEquals(1, result.updatedList.count { it.name == "existing1.jpg" })
    assertEquals(1, result.updatedList.count { it.name == "existing2.jpg" })
    assertEquals(1, result.updatedList.count { it.name == "new3.jpg" })
  }

  @Test
  fun testRemoveFile() {
    val item1 = createItem(1, "one.jpg")
    val item2 = createItem(2, "two.jpg")
    val item3 = createItem(3, "three.jpg")
    val current = listOf(item1, item2, item3)

    val updated = FileSelectionHelper.removeFile(current, item2)

    assertEquals(2, updated.size)
    assertTrue(updated.any { it.name == "one.jpg" })
    assertFalse(updated.any { it.name == "two.jpg" })
    assertTrue(updated.any { it.name == "three.jpg" })
  }

  @Test
  fun testRemoveByUri() {
    val item1 = createItem(10, "ten.jpg")
    val item2 = createItem(20, "twenty.jpg")
    val current = listOf(item1, item2)

    val updated = FileSelectionHelper.removeByUri(current, item1.uri)

    assertEquals(1, updated.size)
    assertEquals("twenty.jpg", updated[0].name)
  }
}
