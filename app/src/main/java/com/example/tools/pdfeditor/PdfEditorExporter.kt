package com.example.tools.pdfeditor

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.RectF
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import com.example.file.FileLayer
import com.example.file.FileNameSanitizer
import com.example.pdf.PdfErrors
import com.example.pdf.PdfFileHelper
import com.example.pdf.PdfVerifier
import com.example.tools.common.ToolJobRunner
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.font.PDFont
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory
import com.tom_roush.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.UUID

object PdfEditorExporter {

  suspend fun exportEditedPdf(
    context: Context,
    inputFile: File,
    sessionState: EditorSessionState,
    jobContext: com.example.tools.common.JobContext? = null,
    /** Re-render blurry / faded (scanned) pages sharper into the saved file. */
    enhanceScans: Boolean = false
  ): File = withContext(Dispatchers.IO) {
    jobContext?.ensureActive()
    jobContext?.reportProgress(5, "Preparing document…")

    val tempDir = FileLayer.getTempFolder(context)
    val baseName = inputFile.nameWithoutExtension
    val outName = FileNameSanitizer.sanitizeFileName("${baseName}_edited.pdf")
    val outputFile = File(tempDir, outName)
    jobContext?.trackPartialOutput(outputFile)

    var doc: PDDocument? = null
    var totalPages = 1
    try {
      doc = PdfFileHelper.openDocument(inputFile, tempDir)
      if (doc.isEncrypted) {
        throw IOException(PdfErrors.MSG_PASSWORD_PROTECTED)
      }

      // 1. Apply page operations
      applyPageOperations(doc, sessionState.pageOps)

      totalPages = doc.numberOfPages
      if (totalPages == 0) {
        throw IOException("PDF has no pages remaining.")
      }

      // 2. Render and draw overlays page-by-page
      for (pageIdx in 0 until totalPages) {
        jobContext?.ensureActive()
        val pageProgress = 10 + (75 * pageIdx / totalPages)
        jobContext?.reportProgress(pageProgress, "Applying edits to page ${pageIdx + 1}/$totalPages…")

        val page = doc.getPage(pageIdx)
        val overlays = sessionState.pageOverlays[pageIdx] ?: emptyList()
        val isFlattened = sessionState.flattenedPages.contains(pageIdx)

        if (isFlattened || enhanceScans) {
          val flattened = flattenPageWithOverlays(
            context, doc, page, pageIdx, overlays,
            enhance = enhanceScans, force = isFlattened
          )
          if (flattened) {
            // Fills (cover / redact) are already baked into the page image; add the rest as vectors.
            drawOverlaysOnPage(context, doc, page, overlays, skipFills = true)
          } else if (overlays.isNotEmpty()) {
            drawOverlaysOnPage(context, doc, page, overlays)
          }
        } else if (overlays.isNotEmpty()) {
          drawOverlaysOnPage(context, doc, page, overlays)
        }
      }

      jobContext?.reportProgress(90, "Saving edited document…")
      doc.save(outputFile)
    } finally {
      try { doc?.close() } catch (_: Exception) {}
    }

    jobContext?.reportProgress(96, "Verifying output PDF…")
    val verified = PdfVerifier.verifyPdfOutput(outputFile, expectedPageCount = totalPages)
    if (!verified) {
      outputFile.delete()
      throw IOException(PdfErrors.MSG_DAMAGED_PDF)
    }

    return@withContext outputFile
  }

  private fun applyPageOperations(doc: PDDocument, pageOps: List<PageOp>) {
    for (op in pageOps) {
      when (op) {
        is PageOp.DeletePage -> {
          if (doc.numberOfPages > 1 && op.pageIndex in 0 until doc.numberOfPages) {
            doc.removePage(op.pageIndex)
          }
        }
        is PageOp.RotatePage -> {
          if (op.pageIndex in 0 until doc.numberOfPages) {
            val page = doc.getPage(op.pageIndex)
            page.rotation = ((PdfCoordMapper.normalizeRotation(page.rotation) + op.angle) % 360 + 360) % 360
          }
        }
        is PageOp.DuplicatePage -> {
          if (op.pageIndex in 0 until doc.numberOfPages) {
            val original = doc.getPage(op.pageIndex)
            // importPage appends a copy at the end; move it right after the original.
            val copy = doc.importPage(original)
            doc.pages.remove(copy)
            doc.pages.insertAfter(copy, original)
          }
        }
        is PageOp.InsertBlankPage -> {
          val reference = if (op.afterIndex in 0 until doc.numberOfPages) doc.getPage(op.afterIndex) else null
          val size = reference?.mediaBox ?: PDRectangle.A4
          val blank = PDPage(PDRectangle(size.width, size.height))
          if (reference != null) {
            doc.pages.insertAfter(blank, reference)
          } else {
            doc.addPage(blank)
          }
        }
        is PageOp.ReorderPages -> Unit
      }
    }
  }

  private fun drawOverlaysOnPage(
    context: Context,
    doc: PDDocument,
    page: PDPage,
    overlays: List<OverlayObject>,
    skipFills: Boolean = false
  ) {
    if (skipFills && overlays.all { it is RedactOverlay || (it is CoverOverlay && it.replacementText.isNullOrBlank()) }) return
    val geom = PdfCoordMapper.extractGeometry(page.cropBox, page.mediaBox, page.rotation)

    PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true, true).use { stream ->
      for (obj in overlays) {
        stream.saveGraphicsState()
        when (obj) {
          is RedactOverlay -> {
            if (!skipFills) drawCoverOrRedact(stream, geom, obj.bounds, obj.fillColorInt)
          }
          is CoverOverlay -> {
            if (!skipFills) drawCoverOrRedact(stream, geom, obj.bounds, obj.fillColorInt)
            if (!obj.replacementText.isNullOrBlank()) {
              drawText(context, doc, stream, geom, obj.bounds, obj.replacementText, "Sans", 14f, false, false, 0xFF000000.toInt(), 1f)
            }
          }
          is ShapeOverlay -> {
            drawShape(stream, geom, obj)
          }
          is DrawOverlay -> {
            drawPath(doc, stream, geom, obj)
          }
          is ImageOverlay -> {
            drawImage(doc, stream, geom, obj.bounds, obj.imagePath, obj.opacity)
          }
          is SignatureOverlay -> {
            drawImage(doc, stream, geom, obj.bounds, obj.signaturePngPath, obj.opacity)
          }
          is TextOverlay -> {
            drawText(
              context, doc, stream, geom, obj.bounds, obj.text, obj.fontFamily, obj.fontSize,
              obj.isBold, obj.isItalic, obj.colorInt, obj.opacity, obj.sourceFontName, page
            )
          }
        }
        stream.restoreGraphicsState()
      }
    }
  }

  private fun drawCoverOrRedact(
    stream: PDPageContentStream,
    geom: PdfCoordMapper.PageGeometry,
    bounds: RectF,
    colorInt: Int
  ) {
    val userRect = PdfCoordMapper.mapDisplayRectToUser(bounds, geom)
    val r = ((colorInt shr 16) and 0xFF) / 255f
    val g = ((colorInt shr 8) and 0xFF) / 255f
    val b = (colorInt and 0xFF) / 255f

    stream.setNonStrokingColor(r, g, b)
    stream.addRect(userRect.left, userRect.top, userRect.width(), userRect.height())
    stream.fill()
  }

  private fun drawShape(
    stream: PDPageContentStream,
    geom: PdfCoordMapper.PageGeometry,
    shape: ShapeOverlay
  ) {
    val userRect = PdfCoordMapper.mapDisplayRectToUser(shape.bounds, geom)
    val strokeR = ((shape.strokeColorInt shr 16) and 0xFF) / 255f
    val strokeG = ((shape.strokeColorInt shr 8) and 0xFF) / 255f
    val strokeB = (shape.strokeColorInt and 0xFF) / 255f
    stream.setStrokingColor(strokeR, strokeG, strokeB)
    stream.setLineWidth(shape.strokeWidthPx.coerceIn(1f, 20f))

    when (shape.type) {
      ShapeType.RECTANGLE -> {
        if (shape.fillColorInt != null) {
          val fillR = ((shape.fillColorInt shr 16) and 0xFF) / 255f
          val fillG = ((shape.fillColorInt shr 8) and 0xFF) / 255f
          val fillB = (shape.fillColorInt and 0xFF) / 255f
          stream.setNonStrokingColor(fillR, fillG, fillB)
          stream.addRect(userRect.left, userRect.top, userRect.width(), userRect.height())
          stream.fillAndStroke()
        } else {
          stream.addRect(userRect.left, userRect.top, userRect.width(), userRect.height())
          stream.stroke()
        }
      }
      ShapeType.LINE, ShapeType.ARROW -> {
        val p1 = PdfCoordMapper.mapDisplayPointToUser(shape.bounds.left, shape.bounds.top, geom)
        val p2 = PdfCoordMapper.mapDisplayPointToUser(shape.bounds.right, shape.bounds.bottom, geom)
        stream.moveTo(p1.x, p1.y)
        stream.lineTo(p2.x, p2.y)
        stream.stroke()
      }
      ShapeType.ELLIPSE -> {
        // Approximate ellipse with rect
        stream.addRect(userRect.left, userRect.top, userRect.width(), userRect.height())
        stream.stroke()
      }
    }
  }

  private fun drawPath(
    doc: PDDocument,
    stream: PDPageContentStream,
    geom: PdfCoordMapper.PageGeometry,
    draw: DrawOverlay
  ) {
    if (draw.points.size < 2) return

    if (draw.mode == DrawMode.HIGHLIGHTER) {
      val gState = PDExtendedGraphicsState().apply {
        nonStrokingAlphaConstant = 0.35f
        strokingAlphaConstant = 0.35f
      }
      stream.setGraphicsStateParameters(gState)
    }

    val r = ((draw.colorInt shr 16) and 0xFF) / 255f
    val g = ((draw.colorInt shr 8) and 0xFF) / 255f
    val b = (draw.colorInt and 0xFF) / 255f
    stream.setStrokingColor(r, g, b)
    stream.setLineWidth(draw.strokeWidthPx.coerceIn(1f, 40f))
    stream.setLineCapStyle(1)
    stream.setLineJoinStyle(1)

    val first = PdfCoordMapper.mapDisplayPointToUser(draw.points[0].x, draw.points[0].y, geom)
    stream.moveTo(first.x, first.y)
    for (i in 1 until draw.points.size) {
      val pt = PdfCoordMapper.mapDisplayPointToUser(draw.points[i].x, draw.points[i].y, geom)
      stream.lineTo(pt.x, pt.y)
    }
    stream.stroke()
  }

  private fun drawImage(
    doc: PDDocument,
    stream: PDPageContentStream,
    geom: PdfCoordMapper.PageGeometry,
    bounds: RectF,
    path: String,
    opacity: Float
  ) {
    val file = File(path)
    if (!file.exists() || file.length() == 0L) return

    val bitmap = BitmapFactory.decodeFile(file.absolutePath) ?: return
    try {
      val pdImage = if (path.endsWith(".png", ignoreCase = true) || bitmap.hasAlpha()) {
        LosslessFactory.createFromImage(doc, bitmap)
      } else {
        JPEGFactory.createFromImage(doc, bitmap, 0.88f)
      }

      val userRect = PdfCoordMapper.mapDisplayRectToUser(bounds, geom)
      if (opacity < 1.0f) {
        val gs = PDExtendedGraphicsState().apply {
          nonStrokingAlphaConstant = opacity
        }
        stream.setGraphicsStateParameters(gs)
      }
      stream.drawImage(pdImage, userRect.left, userRect.top, userRect.width(), userRect.height())
    } finally {
      bitmap.recycle()
    }
  }

  private fun drawText(
    context: Context,
    doc: PDDocument,
    stream: PDPageContentStream,
    geom: PdfCoordMapper.PageGeometry,
    bounds: RectF,
    text: String,
    fontFamily: String,
    fontSize: Float,
    isBold: Boolean,
    isItalic: Boolean,
    colorInt: Int,
    opacity: Float,
    sourceFontName: String? = null,
    page: PDPage? = null
  ) {
    val userRect = PdfCoordMapper.mapDisplayRectToUser(bounds, geom)
    if (opacity < 1.0f) {
      stream.setGraphicsStateParameters(PDExtendedGraphicsState().apply {
        nonStrokingAlphaConstant = opacity
      })
    }

    // Prefer the very font the original text used, when it can draw every character.
    val sourceFont = if (sourceFontName != null && page != null) findPageFont(page, sourceFontName, text) else null
    val isWinAnsi = isWinAnsiEncodable(text)

    if (sourceFont != null || isWinAnsi) {
      val font = sourceFont ?: resolveStandardFont(fontFamily, isBold, isItalic)
      val r = ((colorInt shr 16) and 0xFF) / 255f
      val g = ((colorInt shr 8) and 0xFF) / 255f
      val b = (colorInt and 0xFF) / 255f

      stream.beginText()
      stream.setFont(font, fontSize)
      stream.setNonStrokingColor(r, g, b)
      stream.newLineAtOffset(userRect.left, userRect.top + (fontSize * 0.2f))
      try {
        stream.showText(text)
      } catch (_: Exception) {
        // Fallback to bitmap
        stream.endText()
        renderTextToBitmapAndDraw(doc, stream, userRect, text, fontSize, colorInt)
        return
      }
      stream.endText()
    } else {
      // Non-WinAnsi characters (Urdu, Hindi, Arabic, emoji) -> Render high-res bitmap
      renderTextToBitmapAndDraw(doc, stream, userRect, text, fontSize, colorInt)
    }
  }

  /** Looks up a font of the page by its PDF name and checks it can encode [text]. */
  private fun findPageFont(page: PDPage, name: String, text: String): PDFont? {
    return try {
      val resources = page.resources ?: return null
      for (key in resources.fontNames) {
        val font = try { resources.getFont(key) } catch (_: Exception) { null } ?: continue
        if (font.name == name) {
          return try {
            font.encode(text)
            font
          } catch (_: Exception) {
            null
          }
        }
      }
      null
    } catch (_: Throwable) {
      null
    }
  }

  private fun isWinAnsiEncodable(text: String): Boolean {
    for (ch in text) {
      if (ch.code !in 32..126 && ch.code !in 160..255) {
        return false
      }
    }
    return true
  }

  private fun resolveStandardFont(family: String, bold: Boolean, italic: Boolean): PDFont {
    return when (family.lowercase()) {
      "serif" -> when {
        bold && italic -> PDType1Font.TIMES_BOLD_ITALIC
        bold -> PDType1Font.TIMES_BOLD
        italic -> PDType1Font.TIMES_ITALIC
        else -> PDType1Font.TIMES_ROMAN
      }
      "mono" -> when {
        bold && italic -> PDType1Font.COURIER_BOLD_OBLIQUE
        bold -> PDType1Font.COURIER_BOLD
        italic -> PDType1Font.COURIER_OBLIQUE
        else -> PDType1Font.COURIER
      }
      else -> when {
        bold && italic -> PDType1Font.HELVETICA_BOLD_OBLIQUE
        bold -> PDType1Font.HELVETICA_BOLD
        italic -> PDType1Font.HELVETICA_OBLIQUE
        else -> PDType1Font.HELVETICA
      }
    }
  }

  private fun renderTextToBitmapAndDraw(
    doc: PDDocument,
    stream: PDPageContentStream,
    userRect: RectF,
    text: String,
    fontSize: Float,
    colorInt: Int
  ) {
    val scale = 3f
    val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
      this.color = colorInt
      this.textSize = fontSize * scale
    }

    val widthPx = (userRect.width() * scale).toInt().coerceAtLeast(100)
    val layout = StaticLayout.Builder.obtain(text, 0, text.length, paint, widthPx)
      .setAlignment(Layout.Alignment.ALIGN_NORMAL)
      .setIncludePad(true)
      .build()

    val heightPx = layout.height.coerceAtLeast(50)
    val bmp = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    layout.draw(canvas)

    try {
      val pdImg = LosslessFactory.createFromImage(doc, bmp)
      val drawH = (heightPx / scale).coerceAtLeast(fontSize)
      stream.drawImage(pdImg, userRect.left, userRect.top, userRect.width(), drawH)
    } finally {
      bmp.recycle()
    }
  }

  /**
   * Renders the page to a sharp image and writes it back as the page content.
   * Returns false (and leaves the page untouched) when [force] is false and enhancement
   * found nothing to improve.
   */
  private suspend fun flattenPageWithOverlays(
    context: Context,
    doc: PDDocument,
    page: PDPage,
    pageIndex: Int,
    overlays: List<OverlayObject>,
    enhance: Boolean,
    force: Boolean
  ): Boolean = withContext(Dispatchers.IO) {
    val tempDir = FileLayer.getTempFolder(context)
    val tempSinglePdf = File(tempDir, "temp_flatten_${UUID.randomUUID()}.pdf")
    val singleDoc = PDDocument()
    try {
      singleDoc.importPage(page)
      singleDoc.save(tempSinglePdf)
    } finally {
      singleDoc.close()
    }

    var flattened = false
    try {
      PdfFileHelper.pdfRendererMutex.withLock {
        ParcelFileDescriptor.open(tempSinglePdf, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
          PdfRenderer(pfd).use { renderer ->
            if (renderer.pageCount > 0) {
              val rPage = renderer.openPage(0)
              try {
                val longEdge = maxOf(rPage.width, rPage.height).toFloat()
                val scale = (FLATTEN_MAX_EDGE / longEdge).coerceIn(0.2f, 6f)
                val w = (rPage.width * scale).toInt().coerceAtLeast(1)
                val h = (rPage.height * scale).toInt().coerceAtLeast(1)
                var bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                bitmap.eraseColor(android.graphics.Color.WHITE)
                rPage.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

                var improved = false
                if (enhance) {
                  val better = PdfPageEnhancer.enhance(bitmap)
                  if (better !== bitmap) {
                    bitmap.recycle()
                    bitmap = better
                    improved = true
                  }
                }

                if (force || improved) {
                  // Bake cover / redact fills into the image with the Android Canvas.
                  val canvas = Canvas(bitmap)
                  val paint = Paint(Paint.ANTI_ALIAS_FLAG)
                  for (obj in overlays) {
                    val rectPx = RectF(
                      obj.bounds.left * w,
                      obj.bounds.top * h,
                      obj.bounds.right * w,
                      obj.bounds.bottom * h
                    )
                    when (obj) {
                      is RedactOverlay -> {
                        paint.style = Paint.Style.FILL
                        paint.color = obj.fillColorInt
                        canvas.drawRect(rectPx, paint)
                      }
                      is CoverOverlay -> {
                        paint.style = Paint.Style.FILL
                        paint.color = obj.fillColorInt
                        canvas.drawRect(rectPx, paint)
                      }
                      else -> {}
                    }
                  }

                  // Replace page contents with the flattened bitmap
                  val pdImg = JPEGFactory.createFromImage(doc, bitmap, 0.92f)
                  val box: PDRectangle
                  if (PdfCoordMapper.normalizeRotation(page.rotation) != 0) {
                    // The image is already upright, so the page becomes upright too.
                    val upright = PDRectangle(rPage.width.toFloat(), rPage.height.toFloat())
                    page.rotation = 0
                    page.mediaBox = upright
                    page.cropBox = upright
                    box = upright
                  } else {
                    box = page.cropBox ?: page.mediaBox
                  }
                  PDPageContentStream(doc, page, PDPageContentStream.AppendMode.OVERWRITE, false).use { stream ->
                    stream.drawImage(pdImg, box.lowerLeftX, box.lowerLeftY, box.width, box.height)
                  }
                  flattened = true
                }
                bitmap.recycle()
              } finally {
                rPage.close()
              }
            }
          }
        }
      }
    } finally {
      tempSinglePdf.delete()
    }
    flattened
  }

  private const val FLATTEN_MAX_EDGE = 2600
}
