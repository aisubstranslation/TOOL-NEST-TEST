package com.example.tools.zip

import org.junit.Assert.assertEquals
import org.junit.Test

class ZipEntrySanitizerTest {

  @Test
  fun testSanitizeRemovesPathTraversalAndLeadingSlash() {
    val raw = "/../../etc/passwd/../photo.jpg"
    val sanitized = ZipEntrySanitizer.sanitizeEntryPath(raw)
    assertEquals("etc/passwd/photo.jpg", sanitized)
  }

  @Test
  fun testSanitizeNormalizesBackslashes() {
    val raw = "subfolder\\nested\\document.pdf"
    val sanitized = ZipEntrySanitizer.sanitizeEntryPath(raw)
    assertEquals("subfolder/nested/document.pdf", sanitized)
  }

  @Test
  fun testSanitizePreservesUnicodeAndStripsControlChars() {
    // Contains tab (\t), newline (\n) and null (\u0000) alongside Urdu & Hindi characters
    val raw = "\tتصویر\n_\u0000फ़ाइल.png"
    val sanitized = ZipEntrySanitizer.sanitizeEntryPath(raw)
    assertEquals("تصویر_फ़ाइल.png", sanitized)
  }

  @Test
  fun testResolveDuplicatePathsInOrder() {
    val input = listOf(
      "photos/vacation.jpg",
      "photos/vacation.jpg",
      "photos/vacation.jpg",
      "notes.txt",
      "notes.txt"
    )

    val resolved = ZipEntrySanitizer.resolveDuplicatePaths(input)

    val expected = listOf(
      "photos/vacation.jpg",
      "photos/vacation (1).jpg",
      "photos/vacation (2).jpg",
      "notes.txt",
      "notes (1).txt"
    )

    assertEquals(expected, resolved)
  }
}
