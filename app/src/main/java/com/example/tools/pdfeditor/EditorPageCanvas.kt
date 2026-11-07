package com.example.tools.pdfeditor

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.RectF
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.example.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * Interactive page view of the PDF editor.
 *
 *  - The page is rendered sharply (see [EditorPageRenderer]) and cleaned up when it needs it.
 *  - Pinch to zoom, drag to pan (when zoomed).
 *  - Text tool: tap text to edit it (found from the PDF text layer, or from pixels on scans),
 *    or tap an empty spot to add new text.
 *  - Cover / Redact tools: drag a rectangle.
 *  - Pen / Highlighter: draw.
 * Every touch position is converted with the page box size, so strokes and taps land exactly where
 * the finger is (the old version used the wrong box and drew in the wrong place).
 */
@Composable
internal fun EditorPageCanvas(
  file: File,
  entry: PageEntry,
  activeTool: ActiveEditorTool,
  enhance: Boolean,
  overlays: List<OverlayObject>,
  selectedId: String?,
  onAddOverlay: (OverlayObject) -> Unit,
  onEditTarget: (EditTarget) -> Unit,
  onRegionSelected: (EditTarget) -> Unit,
  onSelect: (String?) -> Unit,
  onCommitOverlay: (OverlayObject) -> Unit,
  onPageMetrics: (Float, Float) -> Unit,
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current
  val currentAddOverlay by rememberUpdatedState(onAddOverlay)
  val currentSelect by rememberUpdatedState(onSelect)
  val currentCommit by rememberUpdatedState(onCommitOverlay)
  val currentEditTarget by rememberUpdatedState(onEditTarget)
  val overlaysNow by rememberUpdatedState(overlays)
  val selectedIdNow by rememberUpdatedState(selectedId)
  var draft by remember { mutableStateOf<OverlayObject?>(null) }

  var rendered by remember(file, entry, enhance) { mutableStateOf<EditorPageRenderer.Rendered?>(null) }
  var segments by remember(file, entry) { mutableStateOf<List<TextSegment>>(emptyList()) }
  var zoom by remember(file, entry) { mutableFloatStateOf(1f) }
  var panX by remember(file, entry) { mutableFloatStateOf(0f) }
  var panY by remember(file, entry) { mutableFloatStateOf(0f) }
  var pageSize by remember { mutableStateOf(IntSize.Zero) }
  val liveDrawPoints = remember { mutableStateListOf<PointF>() }
  var dragStart by remember { mutableStateOf<Offset?>(null) }
  var dragEnd by remember { mutableStateOf<Offset?>(null) }
  var tapPoint by remember { mutableStateOf<Offset?>(null) }
  var regionRect by remember { mutableStateOf<RectF?>(null) }
  val imageCache = remember { mutableStateMapOf<String, Bitmap>() }

  // Render (and clean up) the page.
  LaunchedEffect(file, entry, enhance) {
    rendered = null
    rendered = withContext(Dispatchers.IO) {
      val r = EditorPageRenderer.renderEntry(file, entry)
      if (r != null && enhance) {
        val better = PdfPageEnhancer.enhance(r.bitmap)
        if (better !== r.bitmap) {
          r.bitmap.recycle()
          r.withBitmap(better)
        } else {
          r
        }
      } else {
        r
      }
    }
  }

  // Load the text layer so words can be tapped.
  LaunchedEffect(file, entry) {
    segments = emptyList()
    val src = entry.sourceIndex
    if (src != null && entry.rotation == 0) {
      segments = PdfTextLocator.extractSegments(context, file, src)
    }
  }

  LaunchedEffect(rendered) {
    rendered?.let { onPageMetrics(it.pageWidthPt, it.pageHeightPt) }
  }

  // Decode images / signatures used by overlays.
  val imagePaths = overlays.mapNotNull {
    when (it) {
      is ImageOverlay -> it.imagePath
      is SignatureOverlay -> it.signaturePngPath
      else -> null
    }
  }
  LaunchedEffect(imagePaths) {
    for (p in imagePaths) {
      if (!imageCache.containsKey(p)) {
        val bmp = withContext(Dispatchers.IO) { decodeSampled(p, 900) }
        if (bmp != null) imageCache[p] = bmp
      }
    }
  }

  // Tap on the text tool: find the text under the finger.
  LaunchedEffect(tapPoint) {
    val t = tapPoint ?: return@LaunchedEffect
    tapPoint = null
    val r = rendered ?: return@LaunchedEffect
    val target = withContext(Dispatchers.Default) {
      val hiddenAreas = overlaysNow.filter { it is CoverOverlay || it is RedactOverlay }.map { it.bounds }
      val visibleSegments = segments.filter { seg ->
        val cx = (seg.coverBounds.left + seg.coverBounds.right) / 2f
        val cy = (seg.coverBounds.top + seg.coverBounds.bottom) / 2f
        hiddenAreas.none { it.contains(cx, cy) }
      }
      val seg = PdfTextLocator.findAt(visibleSegments, t.x, t.y)
        ?: PdfTextLocator.findInBitmap(r.bitmap, t.x, t.y, r.pageWidthPt)
      if (seg != null) {
        val (bg, ink) = PdfTextLocator.sampleColors(r.bitmap, seg.coverBounds)
        EditTarget(
          kind = EditKind.EDIT_EXISTING,
          coverBounds = seg.coverBounds,
          leftX = seg.leftX,
          rightX = seg.rightX,
          baselineY = seg.baselineY,
          initialText = if (seg.textKnown && !seg.isRtl) seg.text else "",
          fontFamily = seg.fontFamily,
          isBold = seg.isBold,
          isItalic = seg.isItalic,
          fontSizePt = seg.fontSizePt,
          bgColor = bg,
          inkColor = ink,
          pageWidthPt = r.pageWidthPt,
          pageHeightPt = r.pageHeightPt,
          fontName = seg.fontName
        )
      } else {
        EditTarget(
          kind = EditKind.NEW_TEXT,
          coverBounds = null,
          leftX = t.x.coerceIn(0f, 0.95f),
          rightX = (t.x + 0.5f).coerceAtMost(1f),
          baselineY = t.y.coerceIn(0f, 1f),
          initialText = "",
          fontFamily = "Sans",
          isBold = false,
          isItalic = false,
          fontSizePt = 14f,
          bgColor = 0xFFFFFFFF.toInt(),
          inkColor = 0xFF000000.toInt(),
          pageWidthPt = r.pageWidthPt,
          pageHeightPt = r.pageHeightPt
        )
      }
    }
    onEditTarget(target)
  }

  // Cover / Redact: a rectangle was dragged.
  LaunchedEffect(regionRect) {
    val rect = regionRect ?: return@LaunchedEffect
    regionRect = null
    val r = rendered ?: return@LaunchedEffect
    val target = withContext(Dispatchers.Default) {
      val (bg, ink) = PdfTextLocator.sampleColors(r.bitmap, rect)
      EditTarget(
        kind = EditKind.COVER_AREA,
        coverBounds = rect,
        leftX = rect.left,
        rightX = rect.right,
        baselineY = rect.bottom - 0.22f * rect.height(),
        initialText = "",
        fontFamily = "Sans",
        isBold = false,
        isItalic = false,
        fontSizePt = (rect.height() * r.pageHeightPt * 0.85f).coerceIn(6f, 72f),
        bgColor = bg,
        inkColor = ink,
        pageWidthPt = r.pageWidthPt,
        pageHeightPt = r.pageHeightPt
      )
    }
    onRegionSelected(target)
  }

  fun clampPan(cw: Float, ch: Float) {
    val maxX = (zoom - 1f) * cw / 2f
    val maxY = (zoom - 1f) * ch / 2f
    panX = panX.coerceIn(-maxX, maxX)
    panY = panY.coerceIn(-maxY, maxY)
  }

  // Screen position -> position on the page (0..1). Null until the page box is measured.
  fun toNorm(p: Offset, cw: Float, ch: Float): Offset? {
    val pw = pageSize.width.toFloat()
    val ph = pageSize.height.toFloat()
    if (pw <= 0f || ph <= 0f) return null
    val cx = cw / 2f
    val cy = ch / 2f
    val qx = (p.x - cx - panX) / zoom + cx
    val qy = (p.y - cy - panY) / zoom + cy
    return Offset((qx - (cw - pw) / 2f) / pw, (qy - (ch - ph) / 2f) / ph)
  }

  Box(
    modifier = modifier
      .fillMaxSize()
      .clipToBounds()
      .pointerInput(activeTool) {
        awaitEachGesture {
          val down = awaitFirstDown(requireUnconsumed = false)
          val cw = size.width.toFloat()
          val ch = size.height.toFloat()
          val slop = viewConfiguration.touchSlop
          val isDraw = activeTool == ActiveEditorTool.DRAW_PEN || activeTool == ActiveEditorTool.DRAW_HIGHLIGHTER
          val isRegion = activeTool == ActiveEditorTool.COVER_REPLACE || activeTool == ActiveEditorTool.REDACT
          val pw = pageSize.width.toFloat()
          val ph = pageSize.height.toFloat()
          var multi = false
          var moved = false
          val startNorm = toNorm(down.position, cw, ch)

          liveDrawPoints.clear()
          dragStart = null
          dragEnd = null
          draft = null
          if (isDraw && startNorm != null) {
            liveDrawPoints.add(PointF(startNorm.x.coerceIn(0f, 1f), startNorm.y.coerceIn(0f, 1f)))
          }
          if (isRegion && startNorm != null) {
            dragStart = startNorm
            dragEnd = startNorm
          }

          // What was touched? (resize handle of the selected object, or an object to move)
          var mode = 0 // 0 = page (tap / pan), 1 = move object, 2 = resize object
          var target: OverlayObject? = null
          var corner = -1
          var wasSelected = false
          if (!isDraw && !isRegion && startNorm != null && pw > 0f && ph > 0f) {
            val selected = overlaysNow.firstOrNull { it.id == selectedIdNow }
            if (selected != null && isResizable(selected)) {
              corner = hitCorner(selected.bounds, startNorm, pw, ph, 30.dp.toPx() / zoom)
              if (corner >= 0) {
                mode = 2
                target = selected
                wasSelected = true
              }
            }
            if (mode == 0) {
              val hit = hitTestOverlay(overlaysNow, startNorm, pw, ph)
              if (hit != null) {
                mode = 1
                target = hit
                wasSelected = hit.id == selectedIdNow
              }
            }
          }
          val base = target
          var pinchTarget: OverlayObject? = null
          var pinchScale = 1f

          do {
            val event = awaitPointerEvent()
            val pressed = event.changes.filter { it.pressed }
            if (pressed.size >= 2) {
              if (!multi) {
                multi = true
                liveDrawPoints.clear()
                dragStart = null
                dragEnd = null
                // Pinching over the selected object resizes it; anywhere else zooms the page.
                val sel = overlaysNow.firstOrNull { it.id == selectedIdNow }
                val centre = toNorm(event.calculateCentroid(useCurrent = true), cw, ch)
                if (sel != null && centre != null && !isDraw && !isRegion &&
                  sel.bounds.contains(centre.x, centre.y)
                ) {
                  pinchTarget = sel
                  pinchScale = 1f
                  draft = null
                }
              }
              val zc = event.calculateZoom()
              val pt = pinchTarget
              if (pt != null) {
                pinchScale = (pinchScale * zc).coerceIn(0.2f, 6f)
                draft = scaleObject(pt, pinchScale)
              } else {
                zoom = (zoom * zc).coerceIn(1f, 6f)
                val pan = event.calculatePan()
                panX += pan.x
                panY += pan.y
                clampPan(cw, ch)
              }
              event.changes.forEach { it.consume() }
            } else if (!multi && pressed.size == 1) {
              val change = pressed[0]
              if ((change.position - down.position).getDistance() > slop) moved = true
              val n = toNorm(change.position, cw, ch)
              when {
                isDraw -> {
                  if (n != null) liveDrawPoints.add(PointF(n.x.coerceIn(0f, 1f), n.y.coerceIn(0f, 1f)))
                  change.consume()
                }
                isRegion -> {
                  if (n != null) dragEnd = n
                  change.consume()
                }
                mode == 1 && base != null && startNorm != null && n != null -> {
                  if (moved) {
                    draft = translateObject(base, n.x - startNorm.x, n.y - startNorm.y)
                    change.consume()
                  }
                }
                mode == 2 && base != null && n != null -> {
                  if (moved) {
                    draft = resizeObject(base, corner, n)
                    change.consume()
                  }
                }
                zoom > 1.01f -> {
                  val d = change.position - change.previousPosition
                  panX += d.x
                  panY += d.y
                  clampPan(cw, ch)
                  if (moved) change.consume()
                }
              }
            }
          } while (event.changes.any { it.pressed })

          if (zoom <= 1.01f) {
            zoom = 1f
            panX = 0f
            panY = 0f
          }

          val finalDraft = draft
          draft = null

          if (multi) {
            liveDrawPoints.clear()
            if (pinchTarget != null && finalDraft != null) {
              currentCommit(finalDraft)
              currentSelect(finalDraft.id)
            }
          } else {
            when {
              isDraw -> {
                if (liveDrawPoints.size >= 2) {
                  val pts = liveDrawPoints.map { PointF(it.x, it.y) }
                  val bounds = RectF(
                    pts.minOf { it.x }, pts.minOf { it.y },
                    pts.maxOf { it.x }, pts.maxOf { it.y }
                  )
                  val highlighter = activeTool == ActiveEditorTool.DRAW_HIGHLIGHTER
                  currentAddOverlay(
                    DrawOverlay(
                      bounds = bounds,
                      points = pts,
                      mode = if (highlighter) DrawMode.HIGHLIGHTER else DrawMode.PEN,
                      colorInt = if (highlighter) 0xFFFFEB3B.toInt() else 0xFF000000.toInt(),
                      strokeWidthPx = if (highlighter) 14f else 3f
                    )
                  )
                }
                liveDrawPoints.clear()
              }
              isRegion -> {
                val s0 = dragStart
                val e0 = dragEnd
                dragStart = null
                dragEnd = null
                if (s0 != null && e0 != null) {
                  val rect = RectF(
                    min(s0.x, e0.x).coerceIn(0f, 1f), min(s0.y, e0.y).coerceIn(0f, 1f),
                    max(s0.x, e0.x).coerceIn(0f, 1f), max(s0.y, e0.y).coerceIn(0f, 1f)
                  )
                  if (rect.width() > 0.01f && rect.height() > 0.004f) regionRect = rect
                }
              }
              (mode == 1 || mode == 2) && base != null -> {
                if (moved && finalDraft != null) {
                  currentCommit(finalDraft)
                  currentSelect(finalDraft.id)
                } else if (!moved) {
                  // A tap on an object: select it, and open the editor for text.
                  currentSelect(base.id)
                  if (base is TextOverlay && (wasSelected || activeTool == ActiveEditorTool.TEXT)) {
                    val r = rendered
                    if (r != null) {
                      currentEditTarget(editTargetForText(base, r.pageWidthPt, r.pageHeightPt))
                    }
                  }
                }
              }
              activeTool == ActiveEditorTool.TEXT && !moved -> {
                if (startNorm != null && startNorm.x in 0f..1f && startNorm.y in 0f..1f) {
                  currentSelect(null)
                  tapPoint = startNorm
                }
              }
              !moved -> currentSelect(null)
            }
          }
        }
      }
  ) {
    BoxWithConstraints(
      modifier = Modifier
        .fillMaxSize()
        .graphicsLayer {
          scaleX = zoom
          scaleY = zoom
          translationX = panX
          translationY = panY
        },
      contentAlignment = Alignment.Center
    ) {
      val r = rendered
      if (r != null && !r.bitmap.isRecycled) {
        val ratio = r.bitmap.width.toFloat() / r.bitmap.height.toFloat()
        val maxW = maxWidth * 0.96f
        val maxH = maxHeight * 0.98f
        val fitW = if (maxW / ratio <= maxH) maxW else maxH * ratio
        val fitH = fitW / ratio

        Box(
          modifier = Modifier
            .size(fitW, fitH)
            .onSizeChanged { pageSize = it }
            .background(Color.White)
            .border(1.dp, Color.LightGray)
            .testTag("pdf_editor_page")
        ) {
          Image(
            bitmap = r.bitmap.asImageBitmap(),
            contentDescription = null,
            contentScale = ContentScale.FillBounds,
            filterQuality = FilterQuality.High,
            modifier = Modifier.fillMaxSize()
          )
          Canvas(modifier = Modifier.fillMaxSize()) {
            val cw = size.width
            val ch = size.height
            val ptToPx = cw / r.pageWidthPt
            val nc = drawContext.canvas.nativeCanvas

            val shown = overlays.map { o -> draft?.takeIf { it.id == o.id } ?: o }
            for (obj in shown) {
              val b = obj.bounds
              val topLeft = Offset(b.left * cw, b.top * ch)
              val boxSize = Size(b.width() * cw, b.height() * ch)
              when (obj) {
                is RedactOverlay -> drawRect(Color(obj.fillColorInt), topLeft, boxSize)
                is CoverOverlay -> drawRect(Color(obj.fillColorInt), topLeft, boxSize)
                is DrawOverlay -> {
                  val pts = obj.points
                  val color = if (obj.mode == DrawMode.HIGHLIGHTER) {
                    Color(obj.colorInt).copy(alpha = 0.35f)
                  } else {
                    Color(obj.colorInt)
                  }
                  for (i in 0 until pts.size - 1) {
                    drawLine(
                      color = color,
                      start = Offset(pts[i].x * cw, pts[i].y * ch),
                      end = Offset(pts[i + 1].x * cw, pts[i + 1].y * ch),
                      strokeWidth = obj.strokeWidthPx * ptToPx
                    )
                  }
                }
                is ShapeOverlay -> {
                  drawRect(
                    color = Color(obj.strokeColorInt),
                    topLeft = topLeft,
                    size = boxSize,
                    style = Stroke(width = obj.strokeWidthPx * ptToPx)
                  )
                }
                is ImageOverlay -> drawBitmapOverlay(nc, imageCache[obj.imagePath], b, cw, ch, obj.opacity)
                is SignatureOverlay -> drawBitmapOverlay(nc, imageCache[obj.signaturePngPath], b, cw, ch, obj.opacity)
                is TextOverlay -> drawTextOverlay(nc, obj, cw, ch, ptToPx)
              }
            }

            // Selection outline and resize handles.
            val selObj = shown.firstOrNull { it.id == selectedId }
            if (selObj != null) {
              val sb = selObj.bounds
              val tl = Offset(sb.left * cw, sb.top * ch)
              val sz = Size(sb.width() * cw, sb.height() * ch)
              drawRect(
                color = Color(0xFF2196F3),
                topLeft = tl,
                size = sz,
                style = Stroke(width = 2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f)))
              )
              if (isResizable(selObj)) {
                val hs = 7.dp.toPx()
                val corners = listOf(
                  Offset(tl.x, tl.y), Offset(tl.x + sz.width, tl.y),
                  Offset(tl.x, tl.y + sz.height), Offset(tl.x + sz.width, tl.y + sz.height)
                )
                for (c in corners) {
                  drawRect(Color.White, Offset(c.x - hs, c.y - hs), Size(hs * 2, hs * 2))
                  drawRect(Color(0xFF2196F3), Offset(c.x - hs, c.y - hs), Size(hs * 2, hs * 2), style = Stroke(width = 2.dp.toPx()))
                }
              }
            }

            // Live pen / highlighter stroke.
            if (liveDrawPoints.size >= 2) {
              val highlighter = activeTool == ActiveEditorTool.DRAW_HIGHLIGHTER
              val color = if (highlighter) Color(0xFFFFEB3B).copy(alpha = 0.35f) else Color.Black
              val width = (if (highlighter) 14f else 3f) * ptToPx
              for (i in 0 until liveDrawPoints.size - 1) {
                drawLine(
                  color = color,
                  start = Offset(liveDrawPoints[i].x * cw, liveDrawPoints[i].y * ch),
                  end = Offset(liveDrawPoints[i + 1].x * cw, liveDrawPoints[i + 1].y * ch),
                  strokeWidth = width
                )
              }
            }

            // Live rectangle for Cover / Redact.
            val s = dragStart
            val e = dragEnd
            if (s != null && e != null) {
              val left = min(s.x, e.x) * cw
              val top = min(s.y, e.y) * ch
              val sz = Size(kotlin.math.abs(e.x - s.x) * cw, kotlin.math.abs(e.y - s.y) * ch)
              val fill = if (activeTool == ActiveEditorTool.REDACT) Color(0x66000000) else Color(0x552196F3)
              drawRect(fill, Offset(left, top), sz)
              drawRect(Color(0xFF2196F3), Offset(left, top), sz, style = Stroke(width = 2f))
            }
          }
        }
      } else {
        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
      }
    }

    val hint = when (activeTool) {
      ActiveEditorTool.TEXT -> stringResource(R.string.tool21_tap_hint)
      ActiveEditorTool.COVER_REPLACE -> stringResource(R.string.tool21_cover_drag_hint)
      ActiveEditorTool.REDACT -> stringResource(R.string.tool21_redact_drag_hint)
      ActiveEditorTool.MOVE_SELECT -> stringResource(R.string.tool21_select_hint)
      else -> null
    }
    if (hint != null) {
      Surface(
        modifier = Modifier
          .align(Alignment.TopCenter)
          .padding(8.dp),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)
      ) {
        Text(
          text = hint,
          style = MaterialTheme.typography.labelMedium,
          color = MaterialTheme.colorScheme.onSurface,
          modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        )
      }
    }
  }
}

private fun decodeSampled(path: String, maxEdge: Int): Bitmap? {
  return try {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, bounds)
    var sample = 1
    while (bounds.outWidth / sample > maxEdge * 2 || bounds.outHeight / sample > maxEdge * 2) sample *= 2
    BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
  } catch (_: Throwable) {
    null
  }
}

private fun drawBitmapOverlay(
  nc: android.graphics.Canvas,
  bmp: Bitmap?,
  b: RectF,
  cw: Float,
  ch: Float,
  opacity: Float
) {
  if (bmp == null || bmp.isRecycled) return
  val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply { alpha = (opacity * 255f).toInt().coerceIn(0, 255) }
  nc.drawBitmap(bmp, null, RectF(b.left * cw, b.top * ch, b.right * cw, b.bottom * ch), paint)
}

private fun typefaceFor(obj: TextOverlay): Typeface {
  val base = when (obj.fontFamily.lowercase()) {
    "serif" -> Typeface.SERIF
    "mono" -> Typeface.MONOSPACE
    else -> Typeface.SANS_SERIF
  }
  val style = when {
    obj.isBold && obj.isItalic -> Typeface.BOLD_ITALIC
    obj.isBold -> Typeface.BOLD
    obj.isItalic -> Typeface.ITALIC
    else -> Typeface.NORMAL
  }
  return Typeface.create(base, style)
}

/** Same placement rule as the exporter: baseline sits 0.2 x font size above the bottom of the box. */
private fun drawTextOverlay(nc: android.graphics.Canvas, obj: TextOverlay, cw: Float, ch: Float, ptToPx: Float) {
  val fsPx = obj.fontSize * ptToPx
  val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
    color = obj.colorInt
    alpha = (obj.opacity * 255f).toInt().coerceIn(0, 255)
    textSize = fsPx
    typeface = typefaceFor(obj)
  }
  val left = obj.bounds.left * cw
  val bottom = obj.bounds.bottom * ch
  val latin = obj.text.all { it.code in 32..126 || it.code in 160..255 }
  if (latin) {
    nc.drawText(obj.text, left, bottom - 0.2f * fsPx, paint)
  } else {
    val width = (obj.bounds.width() * cw).toInt().coerceAtLeast(50)
    val layout = StaticLayout.Builder.obtain(obj.text, 0, obj.text.length, paint, width)
      .setAlignment(Layout.Alignment.ALIGN_NORMAL)
      .build()
    nc.save()
    nc.translate(left, bottom - layout.height)
    layout.draw(nc)
    nc.restore()
  }
}

// ---------------------------------------------------------------- object helpers

internal fun isResizable(obj: OverlayObject): Boolean =
  obj is ImageOverlay || obj is SignatureOverlay || obj is ShapeOverlay ||
    obj is CoverOverlay || obj is RedactOverlay

private fun keepsAspect(obj: OverlayObject): Boolean = obj is ImageOverlay || obj is SignatureOverlay

internal fun withBounds(obj: OverlayObject, nb: RectF): OverlayObject = when (obj) {
  is TextOverlay -> obj.copy(bounds = nb)
  is ImageOverlay -> obj.copy(bounds = nb)
  is SignatureOverlay -> obj.copy(bounds = nb)
  is DrawOverlay -> obj.copy(bounds = nb)
  is ShapeOverlay -> obj.copy(bounds = nb)
  is CoverOverlay -> obj.copy(bounds = nb)
  is RedactOverlay -> obj.copy(bounds = nb)
}

/** Builds the edit dialog data for a text object that is already on the page. */
internal fun editTargetForText(obj: TextOverlay, pageWidthPt: Float, pageHeightPt: Float): EditTarget {
  val baseline = obj.bounds.bottom - 0.2f * obj.fontSize / pageHeightPt
  return EditTarget(
    kind = EditKind.EDIT_OVERLAY,
    coverBounds = null,
    leftX = obj.bounds.left,
    rightX = obj.bounds.right,
    baselineY = baseline,
    initialText = obj.text,
    fontFamily = obj.fontFamily,
    isBold = obj.isBold,
    isItalic = obj.isItalic,
    fontSizePt = obj.fontSize,
    bgColor = 0xFFFFFFFF.toInt(),
    inkColor = obj.colorInt,
    pageWidthPt = pageWidthPt,
    pageHeightPt = pageHeightPt,
    fontName = obj.sourceFontName,
    existingId = obj.id
  )
}

private fun translateObject(obj: OverlayObject, dxIn: Float, dyIn: Float): OverlayObject {
  val b = obj.bounds
  val dx = dxIn.coerceIn(-b.left, 1f - b.right)
  val dy = dyIn.coerceIn(-b.top, 1f - b.bottom)
  val nb = RectF(b.left + dx, b.top + dy, b.right + dx, b.bottom + dy)
  return if (obj is DrawOverlay) {
    obj.copy(bounds = nb, points = obj.points.map { PointF(it.x + dx, it.y + dy) })
  } else {
    withBounds(obj, nb)
  }
}

private fun resizeObject(obj: OverlayObject, corner: Int, n: Offset): OverlayObject {
  val start = obj.bounds
  // The corner opposite to the dragged one stays where it is.
  val anchorX = if (corner % 2 == 0) start.right else start.left
  val anchorY = if (corner < 2) start.bottom else start.top
  var w = abs(n.x.coerceIn(0f, 1f) - anchorX).coerceAtLeast(0.03f)
  var h = abs(n.y.coerceIn(0f, 1f) - anchorY).coerceAtLeast(0.02f)
  if (keepsAspect(obj) && start.width() > 0f) {
    h = w * (start.height() / start.width())
  }
  val left = if (corner % 2 == 0) anchorX - w else anchorX
  val top = if (corner < 2) anchorY - h else anchorY
  val nb = RectF(left, top, left + w, top + h)
  if (nb.left < 0f || nb.top < 0f || nb.right > 1f || nb.bottom > 1f) {
    // Keep it inside the page: do not apply a resize that would leave it.
    nb.set(
      nb.left.coerceAtLeast(0f), nb.top.coerceAtLeast(0f),
      nb.right.coerceAtMost(1f), nb.bottom.coerceAtMost(1f)
    )
  }
  return withBounds(obj, nb)
}

private fun scaleObject(obj: OverlayObject, s: Float): OverlayObject {
  val b = obj.bounds
  return when (obj) {
    is DrawOverlay -> obj
    is TextOverlay -> {
      val fs = (obj.fontSize * s).coerceIn(6f, 200f)
      val ratio = fs / obj.fontSize
      // A text box is about one font size tall; the baseline (bottom) stays where it is.
      val newHeight = (b.height() * ratio).coerceAtMost(1f)
      val right = (b.left + b.width() * ratio).coerceAtMost(1f)
      obj.copy(
        fontSize = fs,
        bounds = RectF(b.left, (b.bottom - newHeight).coerceAtLeast(0f), right, b.bottom)
      )
    }
    else -> {
      val cx = (b.left + b.right) / 2f
      val cy = (b.top + b.bottom) / 2f
      val w = (b.width() * s).coerceIn(0.03f, 1f)
      val h = (b.height() * s).coerceIn(0.02f, 1f)
      val l = (cx - w / 2f).coerceIn(0f, 1f - w)
      val t = (cy - h / 2f).coerceIn(0f, 1f - h)
      withBounds(obj, RectF(l, t, l + w, t + h))
    }
  }
}

private fun hitCorner(b: RectF, p: Offset, pw: Float, ph: Float, radiusPx: Float): Int {
  val xs = floatArrayOf(b.left, b.right, b.left, b.right)
  val ys = floatArrayOf(b.top, b.top, b.bottom, b.bottom)
  var best = -1
  var bestDist = radiusPx
  for (i in 0 until 4) {
    val d = hypot((p.x - xs[i]) * pw, (p.y - ys[i]) * ph)
    if (d <= bestDist) {
      best = i
      bestDist = d
    }
  }
  return best
}

private fun hitTestOverlay(overlays: List<OverlayObject>, p: Offset, pw: Float, ph: Float): OverlayObject? {
  for (obj in overlays.asReversed()) {
    if (obj is DrawOverlay) {
      val pts = obj.points
      for (i in 0 until pts.size - 1) {
        if (distanceToSegmentPx(p, pts[i], pts[i + 1], pw, ph) <= 24f) return obj
      }
    } else {
      val b = obj.bounds
      val pad = 0.008f
      if (p.x >= b.left - pad && p.x <= b.right + pad && p.y >= b.top - pad && p.y <= b.bottom + pad) return obj
    }
  }
  return null
}

private fun distanceToSegmentPx(p: Offset, a: PointF, b: PointF, pw: Float, ph: Float): Float {
  val px = p.x * pw
  val py = p.y * ph
  val ax = a.x * pw
  val ay = a.y * ph
  val bx = b.x * pw
  val by = b.y * ph
  val dx = bx - ax
  val dy = by - ay
  val len2 = dx * dx + dy * dy
  val t = if (len2 == 0f) 0f else (((px - ax) * dx + (py - ay) * dy) / len2).coerceIn(0f, 1f)
  return hypot(px - (ax + t * dx), py - (ay + t * dy))
}
