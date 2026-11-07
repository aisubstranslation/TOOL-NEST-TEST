package com.example.tools.pdfeditor

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import com.example.pdf.PdfFileHelper
import kotlinx.coroutines.sync.withLock
import java.io.File
import kotlin.math.max

/**
 * Renders one PDF page for the editor at a high resolution.
 *
 * The old thumbnail helper never rendered above 1.0x (72 dpi), which made text look blurry in the
 * editor. Here the page is rendered so its long edge is about [maxEdgePx] pixels, which keeps text
 * sharp even when the user zooms in.
 */
object EditorPageRenderer {

  const val DISPLAY_MAX_EDGE = 2200

  class Rendered(
    val bitmap: Bitmap,
    /** Page size in PDF points, as the renderer sees it (rotation already applied). */
    val pageWidthPt: Float,
    val pageHeightPt: Float
  ) {
    fun withBitmap(newBitmap: Bitmap): Rendered = Rendered(newBitmap, pageWidthPt, pageHeightPt)
  }

  suspend fun render(file: File, pageIndex: Int, maxEdgePx: Int = DISPLAY_MAX_EDGE): Rendered? {
    if (!file.exists() || file.length() == 0L) return null
    return PdfFileHelper.pdfRendererMutex.withLock {
      var pfd: ParcelFileDescriptor? = null
      var renderer: PdfRenderer? = null
      var page: PdfRenderer.Page? = null
      try {
        pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        renderer = PdfRenderer(pfd)
        if (pageIndex < 0 || pageIndex >= renderer.pageCount) {
          null
        } else {
          page = renderer.openPage(pageIndex)
          val pw = page.width.toFloat()
          val ph = page.height.toFloat()
          val scale = (maxEdgePx / max(pw, ph)).coerceIn(0.2f, 6f)
          val bw = (pw * scale).toInt().coerceAtLeast(1)
          val bh = (ph * scale).toInt().coerceAtLeast(1)
          val bitmap = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888)
          bitmap.eraseColor(Color.WHITE)
          page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
          Rendered(bitmap, pw, ph)
        }
      } catch (_: Throwable) {
        null
      } finally {
        try { page?.close() } catch (_: Exception) {}
        try { renderer?.close() } catch (_: Exception) {}
        try { pfd?.close() } catch (_: Exception) {}
      }
    }
  }

  /** Renders a page as the user sees it: with extra rotation applied, or as a blank sheet. */
  suspend fun renderEntry(file: File, entry: PageEntry, maxEdgePx: Int = DISPLAY_MAX_EDGE): Rendered? {
    val base = render(file, entry.refSource, maxEdgePx) ?: return null
    if (entry.sourceIndex == null) {
      base.bitmap.eraseColor(Color.WHITE)
      return base
    }
    val rot = ((entry.rotation % 360) + 360) % 360
    if (rot == 0) return base
    return try {
      val matrix = android.graphics.Matrix().apply { postRotate(rot.toFloat()) }
      val rotated = Bitmap.createBitmap(base.bitmap, 0, 0, base.bitmap.width, base.bitmap.height, matrix, true)
      if (rotated !== base.bitmap) base.bitmap.recycle()
      val swap = rot == 90 || rot == 270
      Rendered(
        rotated,
        if (swap) base.pageHeightPt else base.pageWidthPt,
        if (swap) base.pageWidthPt else base.pageHeightPt
      )
    } catch (_: Throwable) {
      base
    }
  }
}
