package com.example.file

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class OutputDirsFilePathsTest {

  @Test
  fun testAllOutputDirsAreDeclaredInFilePathsXml() {
    val candidates = listOf(
      File("src/main/res/xml/file_paths.xml"),
      File("app/src/main/res/xml/file_paths.xml"),
      File("../app/src/main/res/xml/file_paths.xml")
    )
    val xmlFile = candidates.firstOrNull { it.exists() }
      ?: throw IllegalStateException("Could not find file_paths.xml in candidate paths")

    val docBuilder = DocumentBuilderFactory.newInstance().newDocumentBuilder()
    val doc = docBuilder.parse(xmlFile)
    val cachePathNodes = doc.getElementsByTagName("cache-path")

    val declaredPaths = mutableSetOf<String>()
    for (i in 0 until cachePathNodes.length) {
      val node = cachePathNodes.item(i)
      val pathAttr = node.attributes?.getNamedItem("path")?.nodeValue
      if (pathAttr != null) {
        val trimmed = pathAttr.trim().trimEnd('/')
        declaredPaths.add(trimmed)
      }
    }

    // Verify all directories in OutputDirs are declared
    for (dirName in OutputDirs.ALL_OUTPUT_DIRS) {
      assertTrue(
        "Expected OutputDirs entry '$dirName' to be declared as a <cache-path> in file_paths.xml. Found: $declaredPaths",
        declaredPaths.contains(dirName)
      )
    }

    // Verify existing toolsnest entry is kept
    assertTrue("toolsnest must be declared in file_paths.xml", declaredPaths.contains("toolsnest"))
    assertTrue("jobs must be declared in file_paths.xml", declaredPaths.contains("jobs"))
    assertTrue("crop_outputs must be declared in file_paths.xml", declaredPaths.contains("crop_outputs"))
  }
}
