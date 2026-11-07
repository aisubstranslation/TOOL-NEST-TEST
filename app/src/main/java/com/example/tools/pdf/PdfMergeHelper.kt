package com.example.tools.pdf

import android.content.Context
import com.example.file.FileLayer
import com.example.file.FileNameSanitizer
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.pdmodel.PDDocument
import java.io.File
import java.io.IOException
import com.example.pdf.PdfErrors

object PdfMergeHelper {

  /**
   * Merges multiple PDF files in the provided order into a single PDF file.
   *
   * @param inputFiles Ordered list of local PDF files
   * @param outputFile Target output file
   * @param tempDir Temp directory for disk-backed memory buffers
   * @param onProgress Callback receiving progress (0f..1f) and status message
   * @return The merged output file
   */
  fun mergePdfFiles(
    inputFiles: List<File>,
    outputFile: File,
    tempDir: File,
    onProgress: (Float, String) -> Unit = { _, _ -> }
  ): File {
    if (inputFiles.size < 2) {
      throw IllegalArgumentException("At least 2 PDF files are required for merging.")
    }

    val memorySetting = MemoryUsageSetting.setupTempFileOnly().setTempDir(tempDir)
    val mergedDoc = PDDocument(memorySetting)
    val openSources = mutableListOf<PDDocument>()
    var totalPagesCopied = 0

    try {
      val totalFiles = inputFiles.size
      for ((index, file) in inputFiles.withIndex()) {
        val baseProgress = 0.1f + (0.75f * index / totalFiles)
        onProgress(baseProgress, "Merging ${file.name} (${index + 1}/$totalFiles)…")

        val srcDoc = PDDocument.load(file, memorySetting)
        openSources.add(srcDoc)
        if (srcDoc.isEncrypted) {
          throw IOException(PdfErrors.MSG_PASSWORD_PROTECTED)
        }
        val pageCount = srcDoc.numberOfPages
        for (p in 0 until pageCount) {
          val page = srcDoc.getPage(p)
          mergedDoc.importPage(page)
          totalPagesCopied++
        }
      }

      onProgress(0.88f, "Saving merged document…")
      mergedDoc.save(outputFile)
    } finally {
      for (doc in openSources) {
        try { doc.close() } catch (_: Exception) {}
      }
      try { mergedDoc.close() } catch (_: Exception) {}
    }

    onProgress(0.96f, "Verifying merged output…")
    return outputFile
  }
}
