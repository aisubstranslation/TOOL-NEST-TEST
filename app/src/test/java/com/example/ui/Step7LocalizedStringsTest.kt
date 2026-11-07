package com.example.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class Step7LocalizedStringsTest {

  private val requiredKeys = listOf(
    "common_max_images_reached",
    "common_images_recovery_hint",
    "tool1_supported_formats",
    "tool2_supported_formats",
    "tool3_supported_formats",
    "tool5_supported_formats",
    "tool15_picker_subtitle",
    "media_stage_processing_aac_pct",
    "media_stage_processing_aac",
    "media_stage_processing_complete",
    "media_stage_compressing_video_pct",
    "media_stage_compressing_video",
    "media_stage_compressing_complete"
  )

  private fun findStringsFile(folder: String): File {
    val candidates = listOf(
      File("src/main/res/$folder/strings.xml"),
      File("app/src/main/res/$folder/strings.xml"),
      File("../app/src/main/res/$folder/strings.xml")
    )
    return candidates.firstOrNull { it.exists() }
      ?: throw IllegalStateException("Could not find $folder/strings.xml in candidate paths")
  }

  private fun parseStrings(file: File): Map<String, String> {
    val builder = DocumentBuilderFactory.newInstance().newDocumentBuilder()
    val doc = builder.parse(file)
    val stringNodes = doc.getElementsByTagName("string")
    val result = mutableMapOf<String, String>()
    for (i in 0 until stringNodes.length) {
      val node = stringNodes.item(i)
      val name = node.attributes?.getNamedItem("name")?.nodeValue
      if (name != null) {
        result[name] = node.textContent.trim()
      }
    }
    return result
  }

  @Test
  fun testAllStep7KeysExistAndNonEmptyInAllThreeLanguages() {
    val defaultStrings = parseStrings(findStringsFile("values"))
    val urduStrings = parseStrings(findStringsFile("values-ur"))
    val hindiStrings = parseStrings(findStringsFile("values-hi"))

    for (key in requiredKeys) {
      assertTrue("values/strings.xml must contain key '$key'", defaultStrings.containsKey(key))
      assertFalse("values/strings.xml key '$key' must not be blank", defaultStrings[key].isNullOrBlank())

      assertTrue("values-ur/strings.xml must contain key '$key'", urduStrings.containsKey(key))
      assertFalse("values-ur/strings.xml key '$key' must not be blank", urduStrings[key].isNullOrBlank())

      assertTrue("values-hi/strings.xml must contain key '$key'", hindiStrings.containsKey(key))
      assertFalse("values-hi/strings.xml key '$key' must not be blank", hindiStrings[key].isNullOrBlank())
    }
  }

  @Test
  fun testFormatPlaceholdersMatchAcrossLanguages() {
    val defaultStrings = parseStrings(findStringsFile("values"))
    val urduStrings = parseStrings(findStringsFile("values-ur"))
    val hindiStrings = parseStrings(findStringsFile("values-hi"))

    val placeholderRegex = Regex("%(\\d+\\$)?[sd]")

    for (key in requiredKeys) {
      val defPlaceholders = placeholderRegex.findAll(defaultStrings[key] ?: "").map { it.value }.toList()
      val urPlaceholders = placeholderRegex.findAll(urduStrings[key] ?: "").map { it.value }.toList()
      val hiPlaceholders = placeholderRegex.findAll(hindiStrings[key] ?: "").map { it.value }.toList()

      if (defPlaceholders.isNotEmpty()) {
        assertTrue(
          "Urdu placeholders count for '$key' (${urPlaceholders.size}) must match default (${defPlaceholders.size})",
          urPlaceholders.size == defPlaceholders.size
        )
        assertTrue(
          "Hindi placeholders count for '$key' (${hiPlaceholders.size}) must match default (${defPlaceholders.size})",
          hiPlaceholders.size == defPlaceholders.size
        )
      }
    }
  }
}
