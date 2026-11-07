package com.example.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolSearchTest {

  @Test
  fun testEveryToolFoundByName() {
    for (tool in ToolRegistry.tools) {
      val results = ToolSearch.search(tool.defaultName)
      assertFalse("Search for '${tool.defaultName}' should return results", results.isEmpty())
      assertEquals("First result should be the matching tool", tool.id, results.first().id)
    }
  }

  @Test
  fun testCommonTaskPhrases() {
    // "compress image" -> Tool 1
    val compressResults = ToolSearch.search("compress image")
    assertTrue(compressResults.any { it.id == 1 })

    // "merge pdf" -> Tool 10
    val mergePdfResults = ToolSearch.search("merge pdf")
    assertTrue(mergePdfResults.any { it.id == 10 })

    // "extract audio" -> Tool 16
    val extractAudioResults = ToolSearch.search("extract audio")
    assertTrue(extractAudioResults.any { it.id == 16 })

    // "remove background" -> Tool 20
    val removeBgResults = ToolSearch.search("remove background")
    assertTrue(removeBgResults.any { it.id == 20 })

    // "sha256" -> Tool 7
    val sha256Results = ToolSearch.search("sha256")
    assertTrue(sha256Results.any { it.id == 7 })

    // "qr" -> Tool 9
    val qrResults = ToolSearch.search("qr")
    assertTrue(qrResults.any { it.id == 9 })

    // "zip" -> Tool 8
    val zipResults = ToolSearch.search("zip")
    assertTrue(zipResults.any { it.id == 8 })

    // "pdf editor" -> Tool 21
    val pdfEditorResults = ToolSearch.search("pdf editor")
    assertTrue(pdfEditorResults.any { it.id == 21 })

    // "ai image" -> Tool 22
    val aiImageResults = ToolSearch.search("ai image")
    assertTrue(aiImageResults.any { it.id == 22 })
  }

  @Test
  fun testUrduKeywords() {
    // "تصویر" -> contains image tools
    val urduImg = ToolSearch.search("تصویر")
    assertTrue(urduImg.isNotEmpty())
    assertTrue(urduImg.any { it.category == Category.IMAGE })

    // "پی ڈی ایف" -> contains PDF tools
    val urduPdf = ToolSearch.search("پی ڈی ایف")
    assertTrue(urduPdf.isNotEmpty())
    assertTrue(urduPdf.any { it.category == Category.PDF })

    // "آڈیو" -> contains audio tools
    val urduAudio = ToolSearch.search("آڈیو")
    assertTrue(urduAudio.isNotEmpty())
    assertTrue(urduAudio.any { it.category == Category.VIDEO_AUDIO })
  }

  @Test
  fun testHindiKeywords() {
    // "फोटो" -> image tools
    val hindiImg = ToolSearch.search("फोटो")
    assertTrue(hindiImg.isNotEmpty())
    assertTrue(hindiImg.any { it.category == Category.IMAGE })

    // "पीडीएफ" -> PDF tools
    val hindiPdf = ToolSearch.search("पीडीएफ")
    assertTrue(hindiPdf.isNotEmpty())
    assertTrue(hindiPdf.any { it.category == Category.PDF })

    // "ऑडियो" -> Audio tools
    val hindiAudio = ToolSearch.search("ऑडियो")
    assertTrue(hindiAudio.isNotEmpty())
    assertTrue(hindiAudio.any { it.category == Category.VIDEO_AUDIO })
  }

  @Test
  fun testEmptyAndUnmatchedQueries() {
    assertTrue(ToolSearch.search("").isEmpty())
    assertTrue(ToolSearch.search("   ").isEmpty())
    assertTrue(ToolSearch.search("xyznonexistenttoolquery12345").isEmpty())
  }
}
