package com.example.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream

class MetadataScannerTest {

  @Test
  fun testCleanJpegHasNoMetadata() {
    // SOI + SOS + EOI
    val cleanJpeg = byteArrayOf(
      0xFF.toByte(), 0xD8.toByte(), // SOI
      0xFF.toByte(), 0xDA.toByte(), // SOS
      0x00.toByte(), 0x02.toByte(), // Length 2
      0xFF.toByte(), 0xD9.toByte()  // EOI
    )

    val result = MetadataScanner.scan(cleanJpeg)
    assertEquals("JPEG", result.format)
    assertFalse(result.hasExif)
    assertFalse(result.hasXmp)
    assertFalse(result.hasAiGenerationMarkers)
    assertFalse(result.hasAnyMetadata)
  }

  @Test
  fun testJpegWithExifSegment() {
    val bos = ByteArrayOutputStream()
    bos.write(byteArrayOf(0xFF.toByte(), 0xD8.toByte())) // SOI

    // APP1 Exif marker: 0xFF 0xE1
    val exifPayload = "Exif\u0000\u0000GPS_DATA_MODEL_Canon".toByteArray(Charsets.ISO_8859_1)
    val len = exifPayload.size + 2
    bos.write(0xFF)
    bos.write(0xE1)
    bos.write((len shr 8) and 0xFF)
    bos.write(len and 0xFF)
    bos.write(exifPayload)

    // EOI
    bos.write(byteArrayOf(0xFF.toByte(), 0xD9.toByte()))

    val result = MetadataScanner.scan(bos.toByteArray())
    assertEquals("JPEG", result.format)
    assertTrue(result.hasExif)
    assertTrue(result.hasGps)
    assertTrue(result.hasCameraInfo)
    assertEquals("Canon", result.cameraMakeModel)
    assertTrue(result.hasAnyMetadata)
  }

  @Test
  fun testJpegWithXmpAiMarker() {
    val bos = ByteArrayOutputStream()
    bos.write(byteArrayOf(0xFF.toByte(), 0xD8.toByte())) // SOI

    // APP1 XMP marker: 0xFF 0xE1
    val xmpPayload = "http://ns.adobe.com/xap/1.0/\u0000<xmp:DigitalSourceType>trainedAlgorithmicMedia</xmp:DigitalSourceType>".toByteArray(Charsets.ISO_8859_1)
    val len = xmpPayload.size + 2
    bos.write(0xFF)
    bos.write(0xE1)
    bos.write((len shr 8) and 0xFF)
    bos.write(len and 0xFF)
    bos.write(xmpPayload)

    // EOI
    bos.write(byteArrayOf(0xFF.toByte(), 0xD9.toByte()))

    val result = MetadataScanner.scan(bos.toByteArray())
    assertEquals("JPEG", result.format)
    assertTrue(result.hasXmp)
    assertTrue(result.hasAiGenerationMarkers)
    assertFalse(result.hasExif)
    assertTrue(result.hasAnyMetadata)
  }

  @Test
  fun testPngWithTextChunk() {
    val bos = ByteArrayOutputStream()
    // PNG Header
    bos.write(byteArrayOf(0x89.toByte(), 0x50.toByte(), 0x4E.toByte(), 0x47.toByte(), 0x0D.toByte(), 0x0A.toByte(), 0x1A.toByte(), 0x0A.toByte()))

    // tEXt Chunk: Length (4) + 'tEXt' (4) + data + CRC (4)
    val textData = "Author\u0000ToolsNest".toByteArray(Charsets.ISO_8859_1)
    val len = textData.size
    bos.write((len shr 24) and 0xFF)
    bos.write((len shr 16) and 0xFF)
    bos.write((len shr 8) and 0xFF)
    bos.write(len and 0xFF)
    bos.write("tEXt".toByteArray(Charsets.US_ASCII))
    bos.write(textData)
    bos.write(byteArrayOf(0x00, 0x00, 0x00, 0x00)) // Dummy CRC

    val result = MetadataScanner.scan(bos.toByteArray())
    assertEquals("PNG", result.format)
    assertTrue(result.hasComments)
    assertTrue(result.detectedSegments.contains("PNG tEXt"))
    assertTrue(result.hasAnyMetadata)
  }
}
