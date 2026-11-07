package com.example.tools.zip

import com.example.tools.common.JobContext
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.zip.CRC32
import java.util.zip.ZipFile

class ZipCreatorTest {

  @get:Rule
  val tempFolder = TemporaryFolder()

  private val mockJobContext = object : JobContext {
    override suspend fun reportProgress(
      percent: Int?,
      stage: String,
      currentIndex: Int,
      totalFiles: Int,
      currentFileName: String
    ) {}

    override fun ensureActive() {}
    override fun trackPartialOutput(file: File) {}
  }

  @Test
  fun testZipRoundTripNormalDeflated() = kotlinx.coroutines.runBlocking {
    val f1 = tempFolder.newFile("file1.txt").apply { writeText("Hello ToolsNest ZIP Creator!") }
    val f2 = tempFolder.newFile("file2.bin").apply {
      writeBytes(ByteArray(1024) { (it % 127).toByte() })
    }
    val f3 = tempFolder.newFile("unicode.txt").apply { writeText("اردو اور ہندی") }

    val entries = listOf(
      ZipInputEntry(uri = null, localFile = f1, relativePath = "file1.txt", sizeBytes = f1.length()),
      ZipInputEntry(uri = null, localFile = f2, relativePath = "nested/file2.bin", sizeBytes = f2.length()),
      ZipInputEntry(uri = null, localFile = f3, relativePath = "folder/unicode_تصویر.txt", sizeBytes = f3.length())
    )

    val outputZip = File(tempFolder.root, "test_output.zip")

    val result = ZipWriterHelper.createAndVerifyZip(
      context = null,
      entries = entries,
      destinationTempFile = outputZip,
      compressionMode = ZipCompressionMode.NORMAL,
      jobContext = mockJobContext
    )

    assertTrue(result.success)
    assertTrue(outputZip.exists())
    assertTrue(outputZip.length() > 0L)

    // Verify round-trip with ZipFile
    ZipFile(outputZip).use { zip ->
      assertEquals(3, zip.size())

      // 1. file1.txt
      val e1 = zip.getEntry("file1.txt")
      assertNotNull("file1.txt entry should exist", e1)
      val e1Bytes = zip.getInputStream(e1).readBytes()
      assertEquals("Hello ToolsNest ZIP Creator!", String(e1Bytes))

      val crc1 = CRC32().apply { update(f1.readBytes()) }
      assertEquals(crc1.value, e1.crc)

      // 2. nested/file2.bin
      val e2 = zip.getEntry("nested/file2.bin")
      assertNotNull("nested/file2.bin entry should exist", e2)
      val e2Bytes = zip.getInputStream(e2).readBytes()
      assertArrayEquals(f2.readBytes(), e2Bytes)

      // 3. folder/unicode_تصویر.txt
      val e3 = zip.getEntry("folder/unicode_تصویر.txt")
      assertNotNull("Unicode entry should exist", e3)
      val e3Bytes = zip.getInputStream(e3).readBytes()
      assertEquals("اردو اور ہندی", String(e3Bytes, Charsets.UTF_8))
    }
  }

  @Test
  fun testZipRoundTripStoreMode() = kotlinx.coroutines.runBlocking {
    val f1 = tempFolder.newFile("stored1.txt").apply { writeText("Uncompressed store mode test") }
    val f2 = tempFolder.newFile("stored2.dat").apply {
      writeBytes(ByteArray(512) { (it * 3).toByte() })
    }

    val entries = listOf(
      ZipInputEntry(uri = null, localFile = f1, relativePath = "stored1.txt", sizeBytes = f1.length()),
      ZipInputEntry(uri = null, localFile = f2, relativePath = "data/stored2.dat", sizeBytes = f2.length())
    )

    val outputZip = File(tempFolder.root, "stored_output.zip")

    val result = ZipWriterHelper.createAndVerifyZip(
      context = null,
      entries = entries,
      destinationTempFile = outputZip,
      compressionMode = ZipCompressionMode.STORE,
      jobContext = mockJobContext
    )

    assertTrue(result.success)
    assertTrue(outputZip.exists())

    ZipFile(outputZip).use { zip ->
      assertEquals(2, zip.size())

      val e1 = zip.getEntry("stored1.txt")
      assertNotNull(e1)
      val b1 = zip.getInputStream(e1).readBytes()
      assertEquals("Uncompressed store mode test", String(b1))

      val e2 = zip.getEntry("data/stored2.dat")
      assertNotNull(e2)
      val b2 = zip.getInputStream(e2).readBytes()
      assertArrayEquals(f2.readBytes(), b2)
    }
  }
}
