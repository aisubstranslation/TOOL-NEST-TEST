package com.example.tools.pdf

import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.pdmodel.PDDocument
import java.io.File
import java.io.IOException
import com.example.pdf.PdfErrors

object PdfSplitHelper {

  /**
   * Extracts specific pages (0-based indices) into a single destination PDF.
   */
  fun extractPagesToSinglePdf(
    inputFile: File,
    pageIndices: List<Int>,
    outputFile: File,
    tempDir: File
  ): File {
    val memorySetting = MemoryUsageSetting.setupTempFileOnly().setTempDir(tempDir)
    PDDocument.load(inputFile, memorySetting).use { srcDoc ->
      if (srcDoc.isEncrypted) {
        throw IOException(PdfErrors.MSG_PASSWORD_PROTECTED)
      }
      val destDoc = PDDocument(memorySetting)
      try {
        for (idx in pageIndices) {
          if (idx in 0 until srcDoc.numberOfPages) {
            destDoc.importPage(srcDoc.getPage(idx))
          }
        }
        destDoc.save(outputFile)
      } finally {
        try { destDoc.close() } catch (_: Exception) {}
      }
    }
    return outputFile
  }

  /**
   * Splits each page into a separate individual PDF file.
   */
  fun splitIntoSinglePages(
    inputFile: File,
    outputDir: File,
    baseName: String,
    tempDir: File,
    onProgress: (Float, String) -> Unit = { _, _ -> }
  ): List<File> {
    val memorySetting = MemoryUsageSetting.setupTempFileOnly().setTempDir(tempDir)
    val resultFiles = mutableListOf<File>()

    PDDocument.load(inputFile, memorySetting).use { srcDoc ->
      if (srcDoc.isEncrypted) {
        throw IOException(PdfErrors.MSG_PASSWORD_PROTECTED)
      }
      val totalPages = srcDoc.numberOfPages
      for (i in 0 until totalPages) {
        val pageDoc = PDDocument(memorySetting)
        val outFile = File(outputDir, "${baseName}_page_${i + 1}.pdf")
        try {
          pageDoc.importPage(srcDoc.getPage(i))
          pageDoc.save(outFile)
          resultFiles.add(outFile)
        } finally {
          try { pageDoc.close() } catch (_: Exception) {}
        }
        val progress = 0.1f + (0.8f * (i + 1) / totalPages)
        onProgress(progress, "Extracting page ${i + 1} of $totalPages…")
      }
    }

    return resultFiles
  }

  /**
   * Splits a PDF by multiple distinct ranges (each range is a list of 0-based page indices).
   */
  fun splitByRanges(
    inputFile: File,
    ranges: List<List<Int>>,
    outputDir: File,
    baseName: String,
    tempDir: File,
    onProgress: (Float, String) -> Unit = { _, _ -> }
  ): List<File> {
    val memorySetting = MemoryUsageSetting.setupTempFileOnly().setTempDir(tempDir)
    val resultFiles = mutableListOf<File>()

    PDDocument.load(inputFile, memorySetting).use { srcDoc ->
      if (srcDoc.isEncrypted) {
        throw IOException(PdfErrors.MSG_PASSWORD_PROTECTED)
      }
      val totalRanges = ranges.size
      for ((rIdx, pageList) in ranges.withIndex()) {
        val rangeDoc = PDDocument(memorySetting)
        val outFile = File(outputDir, "${baseName}_part_${rIdx + 1}.pdf")
        try {
          for (pageIdx in pageList) {
            if (pageIdx in 0 until srcDoc.numberOfPages) {
              rangeDoc.importPage(srcDoc.getPage(pageIdx))
            }
          }
          rangeDoc.save(outFile)
          resultFiles.add(outFile)
        } finally {
          try { rangeDoc.close() } catch (_: Exception) {}
        }
        val progress = 0.1f + (0.8f * (rIdx + 1) / totalRanges)
        onProgress(progress, "Exporting part ${rIdx + 1} of $totalRanges…")
      }
    }

    return resultFiles
  }
}
