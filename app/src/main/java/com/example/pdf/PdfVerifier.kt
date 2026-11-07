package com.example.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.pdmodel.PDDocument
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.io.FileInputStream

object PdfVerifier {

  private val PDF_MAGIC_BYTES = byteArrayOf(0x25, 0x50, 0x44, 0x46, 0x2D) // "%PDF-"

  /**
   * Pure check on bytes: verifies if the given byte array starts with "%PDF-".
   */
  fun hasPdfSignature(headerBytes: ByteArray): Boolean {
    if (headerBytes.size < PDF_MAGIC_BYTES.size) return false
    for (i in PDF_MAGIC_BYTES.indices) {
      if (headerBytes[i] != PDF_MAGIC_BYTES[i]) return false
    }
    return true
  }

  /**
   * Complete verification of a generated PDF output:
   * 1. File exists and size > 0
   * 2. Starts with "%PDF-"
   * 3. Reopens with PDFBox
   * 4. Page count matches expected count (if specified)
   * 5. First page renders with PdfRenderer to a non-null Bitmap
   *
   * If verification fails, the corrupted output file is automatically deleted.
   */
  suspend fun verifyPdfOutput(
    file: File,
    expectedPageCount: Int? = null
  ): Boolean {
    if (!file.exists() || file.length() == 0L) {
      PdfFileHelper.deleteQuietly(file)
      return false
    }

    // 1. Signature check
    try {
      FileInputStream(file).use { fis ->
        val header = ByteArray(PDF_MAGIC_BYTES.size)
        val read = fis.read(header)
        if (read < PDF_MAGIC_BYTES.size || !hasPdfSignature(header)) {
          PdfFileHelper.deleteQuietly(file)
          return false
        }
      }
    } catch (_: Exception) {
      PdfFileHelper.deleteQuietly(file)
      return false
    }

    // 2. PDFBox structure and page count check (using disk-backed temp settings)
    var doc: PDDocument? = null
    var pageCount = 0
    try {
      val tempDir = file.parentFile ?: File(System.getProperty("java.io.tmpdir") ?: ".")
      val memorySetting = MemoryUsageSetting.setupTempFileOnly().setTempDir(tempDir)
      doc = PDDocument.load(file, memorySetting)
      pageCount = doc.numberOfPages
      if (pageCount <= 0) {
        PdfFileHelper.deleteQuietly(file)
        return false
      }
      if (expectedPageCount != null && pageCount != expectedPageCount) {
        PdfFileHelper.deleteQuietly(file)
        return false
      }
    } catch (_: Exception) {
      PdfFileHelper.deleteQuietly(file)
      return false
    } finally {
      try {
        doc?.close()
      } catch (_: Exception) {}
    }

    // 3. PdfRenderer render check for first page
    val renderSuccess = PdfFileHelper.pdfRendererMutex.withLock {
      var pfd: ParcelFileDescriptor? = null
      var renderer: PdfRenderer? = null
      var firstPage: PdfRenderer.Page? = null
      var bmp: Bitmap? = null
      try {
        pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        renderer = PdfRenderer(pfd)
        if (renderer.pageCount <= 0) return@withLock false
        firstPage = renderer.openPage(0)
        val w = (firstPage.width / 4).coerceAtLeast(1)
        val h = (firstPage.height / 4).coerceAtLeast(1)
        bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        firstPage.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
        bmp != null
      } catch (_: Throwable) {
        false
      } finally {
        try {
          firstPage?.close()
        } catch (_: Exception) {}
        try {
          renderer?.close()
        } catch (_: Exception) {}
        try {
          pfd?.close()
        } catch (_: Exception) {}
        bmp?.recycle()
      }
    }

    if (!renderSuccess) {
      PdfFileHelper.deleteQuietly(file)
      return false
    }

    return true
  }
}
