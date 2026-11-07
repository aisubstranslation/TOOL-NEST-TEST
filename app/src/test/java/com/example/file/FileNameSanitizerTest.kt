package com.example.file

import org.junit.Assert.assertEquals
import org.junit.Test

class FileNameSanitizerTest {

  @Test
  fun testPathTraversalStripped() {
    val result = FileNameSanitizer.sanitizeFileName("../../etc/passwd.txt")
    assertEquals("____etc_passwd.txt", result)
  }

  @Test
  fun testSeparatorsReplaced() {
    val result = FileNameSanitizer.sanitizeFileName("folder/sub/test.png")
    assertEquals("folder_sub_test.png", result)
  }

  @Test
  fun testReservedCharactersStripped() {
    val result = FileNameSanitizer.sanitizeFileName("file<cool>:name*?|.pdf")
    assertEquals("file_cool__name___.pdf", result)
  }

  @Test
  fun testUnicodePreserved() {
    val resultUrdu = FileNameSanitizer.sanitizeFileName("اردو_فائل.jpg")
    assertEquals("اردو_فائل.jpg", resultUrdu)

    val resultHindi = FileNameSanitizer.sanitizeFileName("हिंदी_फ़ाइल.png")
    assertEquals("हिंदी_फ़ाइल.png", resultHindi)
  }

  @Test
  fun testPrefixAddedCleanly() {
    val withPrefix = FileNameSanitizer.sanitizeFileName("image.jpg", prefix = "TOOLSNEST_")
    assertEquals("TOOLSNEST_image.jpg", withPrefix)

    val alreadyHasPrefix = FileNameSanitizer.sanitizeFileName("TOOLSNEST_image.jpg", prefix = "TOOLSNEST_")
    assertEquals("TOOLSNEST_image.jpg", alreadyHasPrefix)
  }

  @Test
  fun testDuplicateNumbering() {
    val existing = setOf("document.pdf", "document (1).pdf")
    val uniqueName = FileNameSanitizer.generateUniqueFileName("document.pdf", existing)
    assertEquals("document (2).pdf", uniqueName)
  }
}
