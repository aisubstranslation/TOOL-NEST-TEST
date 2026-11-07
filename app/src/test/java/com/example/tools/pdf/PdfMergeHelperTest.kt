package com.example.tools.pdf

import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class PdfMergeHelperTest {

  @get:Rule
  val tempFolder = TemporaryFolder()

  @Test
  fun testMergePdfFiles_threePdfs_sumOfPagesAndNoCosStreamClosedException() {
    val tempDir = tempFolder.newFolder("pdf_temp")

    // File 1 has 1 page
    val file1 = File(tempDir, "input1.pdf")
    PDDocument().use { doc ->
      doc.addPage(PDPage())
      doc.save(file1)
    }

    // File 2 has 2 pages
    val file2 = File(tempDir, "input2.pdf")
    PDDocument().use { doc ->
      doc.addPage(PDPage())
      doc.addPage(PDPage())
      doc.save(file2)
    }

    // File 3 has 1 page
    val file3 = File(tempDir, "input3.pdf")
    PDDocument().use { doc ->
      doc.addPage(PDPage())
      doc.save(file3)
    }

    val outputFile = File(tempDir, "merged_output.pdf")
    val result = PdfMergeHelper.mergePdfFiles(
      inputFiles = listOf(file1, file2, file3),
      outputFile = outputFile,
      tempDir = tempDir
    )

    assertTrue("Output file must exist", result.exists())
    assertTrue("Output file must have non-zero size", result.length() > 0L)

    // Verify total page count is 1 + 2 + 1 = 4 pages
    PDDocument.load(result).use { mergedDoc ->
      assertEquals(4, mergedDoc.numberOfPages)
    }
  }
}
