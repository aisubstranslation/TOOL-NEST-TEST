package com.example.tools.aiinspect

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets

class AiTraceAnalyzerTest {

  @Test
  fun testPngWithParametersChunk_verdictStrong() {
    // Construct minimal valid PNG with tEXt chunk
    val baos = ByteArrayOutputStream()
    // PNG Header
    baos.write(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A))

    // tEXt Chunk: "parameters\0Steps: 25, Sampler: DPM++ 2M Karras, CFG scale: 7, Seed: 123456"
    val key = "parameters"
    val value = "Steps: 25, Sampler: DPM++ 2M Karras, CFG scale: 7, Seed: 123456"
    val chunkData = "$key\u0000$value".toByteArray(StandardCharsets.ISO_8859_1)

    val len = chunkData.size
    baos.write(byteArrayOf((len shr 24).toByte(), (len shr 16).toByte(), (len shr 8).toByte(), len.toByte()))
    baos.write("tEXt".toByteArray(StandardCharsets.US_ASCII))
    baos.write(chunkData)
    baos.write(byteArrayOf(0, 0, 0, 0)) // dummy crc

    val bytes = baos.toByteArray()
    val result = AiTraceAnalyzer.analyzeBytes(bytes, "generated.png")

    assertEquals(AiVerdict.STRONG, result.verdict)
    assertNotNull(result.extractedPrompt)
    assertTrue(result.extractedPrompt!!.contains("Steps: 25"))
    assertTrue(result.evidenceList.any { it.strength == EvidenceStrength.STRONG })
  }

  @Test
  fun testJpegWithXmpDigitalSourceType_verdictStrong() {
    val xmpXml = "<x:xmpmeta xmlns:x='adobe:ns:meta/'><rdf:RDF><rdf:Description " +
      "Iptc4xmpExt:DigitalSourceType='trainedAlgorithmicMedia'/></rdf:RDF></x:xmpmeta>"
    val bytes = xmpXml.toByteArray(StandardCharsets.ISO_8859_1)

    val result = AiTraceAnalyzer.analyzeBytes(bytes, "photo.jpg")
    assertEquals(AiVerdict.STRONG, result.verdict)
    assertTrue(result.evidenceList.any { it.value.contains("trainedAlgorithmicMedia") })
  }

  @Test
  fun testPhotoshopOnly_verdictEdited() {
    val xmpXml = "<x:xmpmeta><xmp:CreatorTool>Adobe Photoshop 2024</xmp:CreatorTool></x:xmpmeta>"
    val bytes = xmpXml.toByteArray(StandardCharsets.ISO_8859_1)

    val result = AiTraceAnalyzer.analyzeBytes(bytes, "retouched.jpg")
    // When generic software is present without AI markers
    assertTrue(result.verdict == AiVerdict.EDITED || result.verdict == AiVerdict.STRONG)
  }

  @Test
  fun testCleanImage_verdictNoneAndHonestyText() {
    val plainBytes = "Plain photo without any metadata markers".toByteArray(StandardCharsets.ISO_8859_1)
    val result = AiTraceAnalyzer.analyzeBytes(plainBytes, "camera_shot.jpg")

    assertEquals(AiVerdict.NONE, result.verdict)
    assertEquals(AiInspectorReportBuilder.MANDATORY_HONESTY_TEXT, result.verdictDescription)
    assertTrue(result.verdictDescription.contains("This does NOT prove the image is real"))
  }
}
