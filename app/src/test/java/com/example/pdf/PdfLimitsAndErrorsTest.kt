package com.example.pdf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class PdfLimitsAndErrorsTest {

  @Test
  fun testPdfLimits() {
    assertTrue(PdfLimits.isWithinSingleFileLimit(1024L))
    assertTrue(PdfLimits.isWithinSingleFileLimit(150L * 1024L * 1024L))
    assertFalse(PdfLimits.isWithinSingleFileLimit(0L))
    assertFalse(PdfLimits.isWithinSingleFileLimit(150L * 1024L * 1024L + 1L))
  }

  @Test
  fun testPdfErrorsMapping() {
    val passwordErr = PdfErrors.mapPdfException(IOException("encrypted document"))
    assertEquals(PdfErrors.MSG_PASSWORD_PROTECTED, passwordErr)

    val oomErr = PdfErrors.mapPdfException(OutOfMemoryError("Java heap space"))
    assertEquals(PdfErrors.MSG_OUT_OF_MEMORY, oomErr)

    val corruptErr = PdfErrors.mapPdfException(IOException("Corrupt EOF"))
    assertEquals(PdfErrors.MSG_DAMAGED_PDF, corruptErr)
  }

  @Test
  fun testPdfSignature() {
    val validPdfHeader = byteArrayOf(0x25, 0x50, 0x44, 0x46, 0x2D, 0x31, 0x2E, 0x37) // "%PDF-1.7"
    assertTrue(PdfVerifier.hasPdfSignature(validPdfHeader))

    val invalidHeader = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47) // PNG header
    assertFalse(PdfVerifier.hasPdfSignature(invalidHeader))

    val shortHeader = byteArrayOf(0x25, 0x50)
    assertFalse(PdfVerifier.hasPdfSignature(shortHeader))
  }
}
