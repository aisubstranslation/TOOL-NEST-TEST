package com.example.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class StringResourcesParityTest {

  private val locales = listOf("values", "values-ur", "values-hi")

  private fun findResDir(): File {
    val candidates = listOf(
      File("src/main/res"),
      File("app/src/main/res"),
      File("../app/src/main/res")
    )
    return candidates.firstOrNull { it.exists() && it.isDirectory }
      ?: throw IllegalStateException("Could not find res directory in candidate paths: $candidates")
  }

  private fun parseStringKeys(file: File): Set<String> {
    val builder = DocumentBuilderFactory.newInstance().newDocumentBuilder()
    val doc = builder.parse(file)
    val stringNodes = doc.getElementsByTagName("string")
    val keys = mutableSetOf<String>()
    for (i in 0 until stringNodes.length) {
      val node = stringNodes.item(i)
      val name = node.attributes?.getNamedItem("name")?.nodeValue
      if (name != null) {
        keys.add(name)
      }
    }
    return keys
  }

  @Test
  fun testStringFilesKeySetsAreIdenticalAcrossLocales() {
    val resDir = findResDir()
    val valuesDir = File(resDir, "values")
    val stringFiles = valuesDir.listFiles { _, name ->
      name.startsWith("strings") && name.endsWith(".xml")
    }?.map { it.name } ?: emptyList()

    assertTrue("Expected to find string resource files in res/values", stringFiles.isNotEmpty())

    for (fileName in stringFiles) {
      val defaultFile = File(valuesDir, fileName)
      val defaultKeys = parseStringKeys(defaultFile)

      for (loc in listOf("values-ur", "values-hi")) {
        val locFile = File(File(resDir, loc), fileName)
        assertTrue("File '$fileName' must exist in res/$loc", locFile.exists())
        val locKeys = parseStringKeys(locFile)

        val missingInLoc = defaultKeys - locKeys
        val extraInLoc = locKeys - defaultKeys

        assertTrue(
          "Keys in res/$loc/$fileName must match res/values/$fileName exactly. Missing: $missingInLoc, Extra: $extraInLoc",
          missingInLoc.isEmpty() && extraInLoc.isEmpty()
        )
      }
    }
  }

  @Test
  fun testNoDuplicateKeyAcrossAllStringFilesInSameLocale() {
    val resDir = findResDir()

    for (loc in locales) {
      val locDir = File(resDir, loc)
      assertTrue("res/$loc directory must exist", locDir.exists() && locDir.isDirectory)

      val stringFiles = locDir.listFiles { _, name ->
        name.startsWith("strings") && name.endsWith(".xml")
      }?.toList() ?: emptyList()

      val seenKeys = mutableMapOf<String, String>()

      for (file in stringFiles) {
        val builder = DocumentBuilderFactory.newInstance().newDocumentBuilder()
        val doc = builder.parse(file)
        val stringNodes = doc.getElementsByTagName("string")

        for (i in 0 until stringNodes.length) {
          val node = stringNodes.item(i)
          val key = node.attributes?.getNamedItem("name")?.nodeValue
          if (key != null) {
            val previousFile = seenKeys[key]
            assertFalse(
              "Duplicate string key '$key' found in res/$loc: appears in both '$previousFile' and '${file.name}'",
              previousFile != null
            )
            seenKeys[key] = file.name
          }
        }
      }
    }
  }
}
