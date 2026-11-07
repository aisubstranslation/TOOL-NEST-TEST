package com.example.tools.hash

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.security.MessageDigest

class Sha256HelperTest {

  @Test
  fun testSha256KnownVectorEmptyString() = kotlinx.coroutines.runBlocking {
    val input = ByteArrayInputStream(ByteArray(0))
    val hash = Sha256Helper.computeSha256(input)
    val expected = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
    assertEquals(expected, hash)
  }

  @Test
  fun testSha256KnownVectorAbc() = kotlinx.coroutines.runBlocking {
    val input = ByteArrayInputStream("abc".toByteArray(Charsets.UTF_8))
    val hash = Sha256Helper.computeSha256(input)
    val expected = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"
    assertEquals(expected, hash)
  }

  @Test
  fun testSha256StreamLargerThan64KbBuffer() = kotlinx.coroutines.runBlocking {
    // Generate deterministic 100 KB payload
    val size = 100 * 1024
    val data = ByteArray(size) { i -> (i % 251).toByte() }

    val directDigest = MessageDigest.getInstance("SHA-256").digest(data)
    val directHex = Sha256Helper.bytesToHex(directDigest)

    val streamedHex = Sha256Helper.computeSha256(ByteArrayInputStream(data))
    assertEquals(directHex, streamedHex)
  }

  @Test
  fun testExpectedHashComparison() {
    val actual = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"

    // Exact match
    assertTrue(Sha256Helper.compareHashes(actual, actual))

    // Uppercase
    assertTrue(Sha256Helper.compareHashes(actual, actual.uppercase()))

    // Leading and trailing whitespace
    assertTrue(Sha256Helper.compareHashes(actual, "   $actual   "))

    // Internal whitespace (e.g. grouped hex)
    val spaced = actual.chunked(8).joinToString(" ")
    assertTrue(Sha256Helper.compareHashes(actual, spaced))

    // Mismatched
    assertFalse(Sha256Helper.compareHashes(actual, "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"))
    assertFalse(Sha256Helper.compareHashes(actual, ""))
  }

  @Test
  fun testFormatChecksumLines() {
    val items = listOf(
      "hash1" to "file1.png",
      "hash2" to "file2.jpg"
    )
    val formatted = Sha256Helper.formatChecksumLines(items)
    val expected = "hash1  file1.png\nhash2  file2.jpg"
    assertEquals(expected, formatted)
  }
}
