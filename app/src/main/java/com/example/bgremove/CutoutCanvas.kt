package com.example.bgremove

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ZoomOutMap
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.example.tools.common.rememberCheckerBrush
import kotlin.math.max
import kotlin.math.roundToInt

/** Pinch-zoom / pan state of the brush canvas. */
@Stable
class CanvasTransform {
  var zoom by mutableFloatStateOf(1f)
  var panX by mutableFloatStateOf(0f)
  var panY by mutableFloatStateOf(0f)

  fun reset() {
    zoom = 1f
    panX = 0f
    panY = 0f
  }

  private fun clampPan(cw: Float, ch: Float) {
    val maxX = (zoom - 1f) * cw / 2f
    val maxY = (zoom - 1f) * ch / 2f
    panX = panX.coerceIn(-maxX, maxX)
    panY = panY.coerceIn(-maxY, maxY)
  }

  /** Two-finger gesture: zoom around the fingers and follow them while panning. */
  fun applyGesture(prevCentroid: Offset, curCentroid: Offset, zoomChange: Float, cw: Float, ch: Float) {
    val z0 = zoom
    val z1 = (z0 * zoomChange).coerceIn(MIN_ZOOM, MAX_ZOOM)
    val cx = cw / 2f
    val cy = ch / 2f
    // Point of the (unzoomed) canvas that is under the fingers before the gesture step
    val ux = (prevCentroid.x - panX - cx) / z0
    val uy = (prevCentroid.y - panY - cy) / z0
    zoom = z1
    panX = curCentroid.x - cx - ux * z1
    panY = curCentroid.y - cy - uy * z1
    clampPan(cw, ch)
  }

  fun panBy(delta: Offset, cw: Float, ch: Float) {
    panX += delta.x
    panY += delta.y
    clampPan(cw, ch)
  }

  companion object {
    const val MIN_ZOOM = 1f
    const val MAX_ZOOM = 8f
  }
}

/**
 * The editable preview:
 *  - Edit mode: ONE finger paints immediately (no touch-slop delay), TWO fingers pinch-zoom / move.
 *  - View mode: two fingers zoom, one finger moves the picture when zoomed in.
 */
@Composable
fun CutoutCanvas(
  editor: CutoutEditor,
  transform: CanvasTransform,
  previewMode: PreviewMode,
  splitFraction: Float,
  onSplitChange: (Float) -> Unit,
  backgroundChoice: BackgroundChoice,
  customBackground: Bitmap?,
  isEditMode: Boolean,
  brushMode: BrushMode,
  brushSizePx: Float,
  isBusy: Boolean,
  onStrokeStarted: () -> Unit,
  onStrokeFinished: (BrushStroke) -> Unit,
  modifier: Modifier = Modifier
) {
  val checker = rememberCheckerBrush()
  val cutoutImage = remember(editor) { editor.bitmap.asImageBitmap() }
  val originalImage = remember(editor) { editor.data.workingBitmap.asImageBitmap() }
  val customImage = remember(customBackground) { customBackground?.asImageBitmap() }

  var cursor by remember { mutableStateOf<Offset?>(null) }

  val brushModeState by rememberUpdatedState(brushMode)
  val brushSizeState by rememberUpdatedState(brushSizePx)
  val busyState by rememberUpdatedState(isBusy)
  val splitCallback by rememberUpdatedState(onSplitChange)
  val startedCallback by rememberUpdatedState(onStrokeStarted)
  val finishedCallback by rememberUpdatedState(onStrokeFinished)

  BoxWithConstraints(
    modifier = modifier
      .fillMaxWidth()
      .aspectRatio(editor.width.toFloat() / max(1, editor.height).toFloat())
      .clip(RoundedCornerShape(16.dp))
  ) {
    val cw = constraints.maxWidth.toFloat().coerceAtLeast(1f)
    val ch = constraints.maxHeight.toFloat().coerceAtLeast(1f)

    Canvas(
      modifier = Modifier
        .fillMaxSize()
        .pointerInput(editor, isEditMode, previewMode, cw, ch) {
          awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)

            var multiTouch = false
            var drawing = false
            var radius = 1f
            var mode = brushModeState
            var lastX = 0f
            var lastY = 0f
            val points = ArrayList<PointF>()

            // Screen position -> mask pixel position (undoes pan + zoom + canvas scaling)
            fun toMask(p: Offset): Offset {
              val z = transform.zoom
              val ux = (p.x - transform.panX - cw / 2f) / z + cw / 2f
              val uy = (p.y - transform.panY - ch / 2f) / z + ch / 2f
              return Offset(ux * editor.width / cw, uy * editor.height / ch)
            }

            fun beginStroke(p: Offset) {
              val m = toMask(p)
              mode = brushModeState
              radius = max(1f, brushSizeState / 2f / transform.zoom * (editor.width / cw))
              points.clear()
              editor.beginStroke()
              editor.stamp(m.x, m.y, m.x, m.y, radius, mode, true)
              points.add(PointF(m.x, m.y))
              lastX = m.x
              lastY = m.y
              cursor = p
              drawing = true
              startedCallback()
            }

            fun continueStroke(p: Offset) {
              val m = toMask(p)
              editor.stamp(lastX, lastY, m.x, m.y, radius, mode, false)
              points.add(PointF(m.x, m.y))
              lastX = m.x
              lastY = m.y
              cursor = p
            }

            if (isEditMode && !busyState) {
              beginStroke(down.position)
              down.consume()
            }

            do {
              val event = awaitPointerEvent()
              val pressedCount = event.changes.count { it.pressed }

              if (pressedCount >= 2) {
                // Second finger: abort any half-drawn stroke and switch to zoom / move
                if (drawing) {
                  drawing = false
                  editor.cancelStroke()
                  cursor = null
                }
                multiTouch = true
                val zoomChange = event.calculateZoom()
                val pan = event.calculatePan()
                if (zoomChange != 1f || pan != Offset.Zero) {
                  transform.applyGesture(
                    prevCentroid = event.calculateCentroid(useCurrent = false),
                    curCentroid = event.calculateCentroid(useCurrent = true),
                    zoomChange = zoomChange,
                    cw = cw,
                    ch = ch
                  )
                }
                event.changes.forEach { if (it.positionChanged()) it.consume() }
              } else if (!multiTouch) {
                val c = event.changes.firstOrNull()
                if (c != null && c.pressed) {
                  if (drawing) {
                    continueStroke(c.position)
                    c.consume()
                  } else if (!isEditMode) {
                    if (transform.zoom > 1.01f) {
                      transform.panBy(c.positionChange(), cw, ch)
                      if (c.positionChanged()) c.consume()
                    } else if (previewMode == PreviewMode.BEFORE_AFTER) {
                      splitCallback(BgRemoveMath.clampSplitFraction(c.position.x, cw))
                      if (c.positionChanged()) c.consume()
                    }
                  }
                }
              }
            } while (event.changes.any { it.pressed })

            if (drawing) {
              editor.endStroke()
              cursor = null
              if (points.isNotEmpty()) {
                finishedCallback(BrushStroke(mode = mode, sizePx = radius * 2f, points = points.toList()))
              }
            }
          }
        }
    ) {
      val version = editor.version
      if (version == Int.MIN_VALUE) return@Canvas

      val w = size.width
      val h = size.height
      val dst = IntSize(w.roundToInt().coerceAtLeast(1), h.roundToInt().coerceAtLeast(1))
      val z = transform.zoom

      withTransform({
        translate(transform.panX, transform.panY)
        scale(z, z, pivot = Offset(w / 2f, h / 2f))
      }) {
        when (previewMode) {
          PreviewMode.ORIGINAL -> {
            drawImage(originalImage, dstSize = dst)
          }

          PreviewMode.BEFORE_AFTER -> {
            drawImage(originalImage, dstSize = dst)
            val splitX = (splitFraction * w).coerceIn(0f, w)
            clipRect(left = splitX, top = 0f, right = w, bottom = h) {
              drawBackgroundLayer(backgroundChoice, previewMode, checker, customImage, dst)
              drawImage(cutoutImage, dstSize = dst)
            }
            drawLine(
              color = Color.White,
              start = Offset(splitX, 0f),
              end = Offset(splitX, h),
              strokeWidth = 3.dp.toPx() / z
            )
          }

          else -> {
            drawBackgroundLayer(backgroundChoice, previewMode, checker, customImage, dst)
            drawImage(cutoutImage, dstSize = dst)
          }
        }
      }

      // Brush cursor ring (screen space, so it keeps the real brush size on screen)
      val pos = cursor
      if (isEditMode && pos != null) {
        val ringColor = if (brushMode == BrushMode.ERASE) {
          Color.White.copy(alpha = 0.85f)
        } else {
          Color(0xFF4CAF50).copy(alpha = 0.85f)
        }
        drawCircle(
          color = ringColor,
          radius = (brushSizePx / 2f).coerceAtLeast(3f),
          center = pos,
          style = Stroke(width = 2.dp.toPx())
        )
      }
    }

    if (transform.zoom > 1.01f) {
      Row(
        modifier = Modifier
          .align(Alignment.TopEnd)
          .padding(8.dp)
          .clip(RoundedCornerShape(16.dp))
          .background(Color.Black.copy(alpha = 0.55f))
          .clickable { transform.reset() }
          .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        Icon(
          imageVector = Icons.Default.ZoomOutMap,
          contentDescription = null,
          tint = Color.White,
          modifier = Modifier.size(16.dp)
        )
        Spacer(Modifier.width(6.dp))
        Text(
          text = "${(transform.zoom * 100f).roundToInt()}%",
          style = MaterialTheme.typography.labelMedium,
          color = Color.White
        )
      }
    }

    if (isBusy) {
      CircularProgressIndicator(
        modifier = Modifier
          .align(Alignment.TopStart)
          .padding(10.dp)
          .size(22.dp),
        strokeWidth = 2.5.dp
      )
    }
  }
}

private fun DrawScope.drawBackgroundLayer(
  choice: BackgroundChoice,
  previewMode: PreviewMode,
  checker: Brush,
  customImage: ImageBitmap?,
  dst: IntSize
) {
  val checkerOnly = previewMode == PreviewMode.CHECKERBOARD
  if (checkerOnly || choice is BackgroundChoice.Transparent) {
    drawRect(brush = checker, size = size)
  }
  if (checkerOnly) return
  when (choice) {
    is BackgroundChoice.White -> drawRect(Color.White, size = size)
    is BackgroundChoice.Black -> drawRect(Color.Black, size = size)
    is BackgroundChoice.CustomColor -> drawRect(choice.color, size = size)
    is BackgroundChoice.CustomImage -> {
      if (customImage != null) drawImage(customImage, dstSize = dst)
    }
    else -> {}
  }
}
