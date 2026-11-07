package com.example.tools.crop

import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

data class CropRect(
  val left: Float,
  val top: Float,
  val right: Float,
  val bottom: Float
) {
  val width: Float get() = max(0f, right - left)
  val height: Float get() = max(0f, bottom - top)
}

data class CropPoint(val x: Float, val y: Float)

data class CropRectInt(
  val left: Int,
  val top: Int,
  val width: Int,
  val height: Int
)

data class OrientedSize(val width: Int, val height: Int)

enum class CropAspectRatio(val ratioW: Int, val ratioH: Int) {
  FREE(0, 0),
  RATIO_1_1(1, 1),
  RATIO_4_3(4, 3),
  RATIO_3_4(3, 4),
  RATIO_16_9(16, 9),
  RATIO_9_16(9, 16);

  val hasFixedRatio: Boolean get() = ratioW > 0 && ratioH > 0
  val ratio: Float get() = if (hasFixedRatio) ratioW.toFloat() / ratioH.toFloat() else 0f
}

enum class CropHandle {
  NONE,
  INSIDE,
  TOP_LEFT,
  TOP_RIGHT,
  BOTTOM_LEFT,
  BOTTOM_RIGHT,
  TOP,
  BOTTOM,
  LEFT,
  RIGHT
}

data class CropStateSnapshot(
  val cropRect: CropRect,
  val aspectRatio: CropAspectRatio
)

class CropHistory(private val maxCapacity: Int = 30) {
  private val undoStack = mutableListOf<CropStateSnapshot>()
  private val redoStack = mutableListOf<CropStateSnapshot>()

  val canUndo: Boolean get() = undoStack.size > 1
  val canRedo: Boolean get() = redoStack.isNotEmpty()

  fun push(state: CropStateSnapshot) {
    redoStack.clear()
    if (undoStack.isNotEmpty() && undoStack.last() == state) return
    undoStack.add(state)
    if (undoStack.size > maxCapacity) {
      undoStack.removeAt(0)
    }
  }

  fun undo(): CropStateSnapshot? {
    if (undoStack.size <= 1) return null
    val current = undoStack.removeAt(undoStack.lastIndex)
    redoStack.add(current)
    return undoStack.last()
  }

  fun redo(): CropStateSnapshot? {
    if (redoStack.isEmpty()) return null
    val next = redoStack.removeAt(redoStack.lastIndex)
    undoStack.add(next)
    return next
  }

  fun reset(initial: CropStateSnapshot) {
    undoStack.clear()
    redoStack.clear()
    undoStack.add(initial)
  }

  fun current(): CropStateSnapshot? = undoStack.lastOrNull()

  val undoCount: Int get() = undoStack.size
  val redoCount: Int get() = redoStack.size
}

data class ViewTransform(
  val fit: CropRect,
  val zoom: Float = 1f,
  val panX: Float = 0f,
  val panY: Float = 0f,
  val canvasW: Float = if (fit.width > 0f) fit.left * 2f + fit.width else 0f,
  val canvasH: Float = if (fit.height > 0f) fit.top * 2f + fit.height else 0f,
  val imageW: Float = if (fit.width > 0f) fit.width else 1f,
  val imageH: Float = if (fit.height > 0f) fit.height else 1f
) {
  val safeZoom: Float = CropMath.safeCoerce(zoom, 1f, 5f)

  val clampedPanX: Float
  val clampedPanY: Float

  constructor(
    fit: CropRect,
    zoom: Float,
    pan: CropPoint,
    canvasW: Float = if (fit.width > 0f) fit.left * 2f + fit.width else 0f,
    canvasH: Float = if (fit.height > 0f) fit.top * 2f + fit.height else 0f,
    imageW: Float = if (fit.width > 0f) fit.width else 1f,
    imageH: Float = if (fit.height > 0f) fit.height else 1f
  ) : this(fit, zoom, pan.x, pan.y, canvasW, canvasH, imageW, imageH)

  init {
    val effCanvasW = if (canvasW > 0f) canvasW else fit.left * 2f + fit.width
    val effCanvasH = if (canvasH > 0f) canvasH else fit.top * 2f + fit.height
    val dispW = fit.width * safeZoom
    val dispH = fit.height * safeZoom
    val centerX = (fit.left + fit.right) / 2f
    val centerY = (fit.top + fit.bottom) / 2f

    // Clamp pan so the displayed image never leaves the canvas view completely
    val minVisibleX = CropMath.safeCoerce(min(32f, fit.width * 0.25f), 1f, max(1f, effCanvasW))
    val minVisibleY = CropMath.safeCoerce(min(32f, fit.height * 0.25f), 1f, max(1f, effCanvasH))

    val minPanX = minVisibleX - (dispW / 2f) - centerX
    val maxPanX = effCanvasW - minVisibleX + (dispW / 2f) - centerX
    val minPanY = minVisibleY - (dispH / 2f) - centerY
    val maxPanY = effCanvasH - minVisibleY + (dispH / 2f) - centerY

    clampedPanX = CropMath.safeCoerce(panX, minPanX, maxPanX)
    clampedPanY = CropMath.safeCoerce(panY, minPanY, maxPanY)
  }

  fun imageToCanvas(ix: Float, iy: Float): CropPoint {
    val effImgW = if (imageW > 0f) imageW else fit.width
    val effImgH = if (imageH > 0f) imageH else fit.height
    if (effImgW <= 0f || effImgH <= 0f || fit.width <= 0f || fit.height <= 0f) {
      return CropPoint(ix, iy)
    }
    val scale = (fit.width / effImgW) * safeZoom
    val centerX = (fit.left + fit.right) / 2f
    val centerY = (fit.top + fit.bottom) / 2f
    val cx = centerX + clampedPanX + (ix - effImgW / 2f) * scale
    val cy = centerY + clampedPanY + (iy - effImgH / 2f) * scale
    return CropPoint(cx, cy)
  }

  fun imageToCanvas(point: CropPoint): CropPoint = imageToCanvas(point.x, point.y)

  fun imageToCanvas(rect: CropRect): CropRect {
    val p1 = imageToCanvas(rect.left, rect.top)
    val p2 = imageToCanvas(rect.right, rect.bottom)
    return CropRect(
      left = min(p1.x, p2.x),
      top = min(p1.y, p2.y),
      right = max(p1.x, p2.x),
      bottom = max(p1.y, p2.y)
    )
  }

  fun canvasToImage(cx: Float, cy: Float): CropPoint {
    val effImgW = if (imageW > 0f) imageW else fit.width
    val effImgH = if (imageH > 0f) imageH else fit.height
    if (effImgW <= 0f || effImgH <= 0f || fit.width <= 0f || fit.height <= 0f) {
      return CropPoint(cx, cy)
    }
    val scale = (fit.width / effImgW) * safeZoom
    val centerX = (fit.left + fit.right) / 2f
    val centerY = (fit.top + fit.bottom) / 2f
    val ix = effImgW / 2f + (cx - centerX - clampedPanX) / scale
    val iy = effImgH / 2f + (cy - centerY - clampedPanY) / scale
    return CropPoint(ix, iy)
  }

  fun canvasToImage(point: CropPoint): CropPoint = canvasToImage(point.x, point.y)

  fun canvasToImage(rect: CropRect): CropRect {
    val p1 = canvasToImage(rect.left, rect.top)
    val p2 = canvasToImage(rect.right, rect.bottom)
    return CropRect(
      left = min(p1.x, p2.x),
      top = min(p1.y, p2.y),
      right = max(p1.x, p2.x),
      bottom = max(p1.y, p2.y)
    )
  }
}

object CropMath {

  /**
   * Safe clamp helper: if hi < lo, returns lo. Never throws.
   */
  fun safeCoerce(value: Float, lo: Float, hi: Float): Float {
    if (hi < lo) return lo
    return if (value < lo) lo else if (value > hi) hi else value
  }

  fun safeCoerce(value: Int, lo: Int, hi: Int): Int {
    if (hi < lo) return lo
    return if (value < lo) lo else if (value > hi) hi else value
  }

  fun safeCoerce(value: Double, lo: Double, hi: Double): Double {
    if (hi < lo) return lo
    return if (value < lo) lo else if (value > hi) hi else value
  }

  /**
   * Returns oriented size, swapping dimensions for 90 and 270 degrees.
   */
  fun orientedSize(rawW: Int, rawH: Int, orientationDegrees: Int): OrientedSize {
    val norm = ((orientationDegrees % 360) + 360) % 360
    return if (norm == 90 || norm == 270) {
      OrientedSize(rawH, rawW)
    } else {
      OrientedSize(rawW, rawH)
    }
  }

  /**
   * Returns the largest power of two sample size such that longEdge / sample >= maxEdge.
   */
  fun calculateInSampleSize(w: Int, h: Int, maxEdge: Int = 1600): Int {
    val longEdge = max(w, h)
    var sample = 1
    while ((longEdge / (sample * 2)) >= maxEdge) {
      sample *= 2
    }
    return sample
  }

  /**
   * Fits an image rectangle of size (imageW, imageH) inside a canvas of size (canvasW, canvasH)
   * preserving aspect ratio and centering it.
   */
  fun fitRect(canvasW: Float, canvasH: Float, imageW: Float, imageH: Float): CropRect {
    if (canvasW <= 0f || canvasH <= 0f || imageW <= 0f || imageH <= 0f) {
      return CropRect(0f, 0f, 0f, 0f)
    }
    val scale = min(canvasW / imageW, canvasH / imageH)
    val fittedW = imageW * scale
    val fittedH = imageH * scale
    val left = (canvasW - fittedW) / 2f
    val top = (canvasH - fittedH) / 2f
    return CropRect(left, top, left + fittedW, top + fittedH)
  }

  /**
   * Clamps crop rectangle to stay inside oriented image bounds [0, imageW] x [0, imageH].
   * Minimum dimension is min(16f, min(imageW, imageH)). Never throws.
   */
  fun clampCrop(
    rect: CropRect,
    imageW: Float,
    imageH: Float,
    minW: Float = 16f,
    minH: Float = 16f
  ): CropRect {
    if (imageW <= 0f || imageH <= 0f) {
      return CropRect(0f, 0f, max(0f, imageW), max(0f, imageH))
    }
    val effMinW = safeCoerce(min(minW, imageW), 1f, imageW)
    val effMinH = safeCoerce(min(minH, imageH), 1f, imageH)

    var l = min(rect.left, rect.right)
    var r = max(rect.left, rect.right)
    var t = min(rect.top, rect.bottom)
    var b = max(rect.top, rect.bottom)

    if (r - l < effMinW) {
      r = l + effMinW
      if (r > imageW) {
        r = imageW
        l = max(0f, r - effMinW)
      }
    }
    if (b - t < effMinH) {
      b = t + effMinH
      if (b > imageH) {
        b = imageH
        t = max(0f, b - effMinH)
      }
    }

    l = safeCoerce(l, 0f, imageW - effMinW)
    t = safeCoerce(t, 0f, imageH - effMinH)
    r = safeCoerce(r, l + effMinW, imageW)
    b = safeCoerce(b, t + effMinH, imageH)

    return CropRect(l, t, r, b)
  }

  /**
   * Applies aspect ratio constraint. Result is always inside the oriented image bounds.
   */
  fun applyAspect(
    rect: CropRect,
    ratio: Float,
    handle: CropHandle = CropHandle.NONE,
    imageW: Float,
    imageH: Float,
    minW: Float = 16f,
    minH: Float = 16f
  ): CropRect {
    val clamped = clampCrop(rect, imageW, imageH, minW, minH)
    if (ratio <= 0f) return clamped

    val effMinW = safeCoerce(min(minW, imageW), 1f, imageW)
    val effMinH = safeCoerce(min(minH, imageH), 1f, imageH)

    return when (handle) {
      CropHandle.BOTTOM_RIGHT -> {
        var w = max(effMinW, clamped.width)
        var h = w / ratio
        if (clamped.left + w > imageW) {
          w = imageW - clamped.left
          h = w / ratio
        }
        if (clamped.top + h > imageH) {
          h = imageH - clamped.top
          w = h * ratio
        }
        clampCrop(CropRect(clamped.left, clamped.top, clamped.left + w, clamped.top + h), imageW, imageH, minW, minH)
      }
      CropHandle.BOTTOM_LEFT -> {
        var w = max(effMinW, clamped.width)
        var h = w / ratio
        if (clamped.right - w < 0f) {
          w = clamped.right
          h = w / ratio
        }
        if (clamped.top + h > imageH) {
          h = imageH - clamped.top
          w = h * ratio
        }
        clampCrop(CropRect(clamped.right - w, clamped.top, clamped.right, clamped.top + h), imageW, imageH, minW, minH)
      }
      CropHandle.TOP_RIGHT -> {
        var w = max(effMinW, clamped.width)
        var h = w / ratio
        if (clamped.left + w > imageW) {
          w = imageW - clamped.left
          h = w / ratio
        }
        if (clamped.bottom - h < 0f) {
          h = clamped.bottom
          w = h * ratio
        }
        clampCrop(CropRect(clamped.left, clamped.bottom - h, clamped.left + w, clamped.bottom), imageW, imageH, minW, minH)
      }
      CropHandle.TOP_LEFT -> {
        var w = max(effMinW, clamped.width)
        var h = w / ratio
        if (clamped.right - w < 0f) {
          w = clamped.right
          h = w / ratio
        }
        if (clamped.bottom - h < 0f) {
          h = clamped.bottom
          w = h * ratio
        }
        clampCrop(CropRect(clamped.right - w, clamped.bottom - h, clamped.right, clamped.bottom), imageW, imageH, minW, minH)
      }
      CropHandle.LEFT -> {
        var w = max(effMinW, clamped.width)
        var h = w / ratio
        if (clamped.right - w < 0f) {
          w = clamped.right
          h = w / ratio
        }
        if (h > imageH) {
          h = imageH
          w = h * ratio
        }
        val midY = (clamped.top + clamped.bottom) / 2f
        var newT = midY - (h / 2f)
        if (newT < 0f) newT = 0f
        if (newT + h > imageH) newT = imageH - h
        clampCrop(CropRect(clamped.right - w, newT, clamped.right, newT + h), imageW, imageH, minW, minH)
      }
      CropHandle.RIGHT -> {
        var w = max(effMinW, clamped.width)
        var h = w / ratio
        if (clamped.left + w > imageW) {
          w = imageW - clamped.left
          h = w / ratio
        }
        if (h > imageH) {
          h = imageH
          w = h * ratio
        }
        val midY = (clamped.top + clamped.bottom) / 2f
        var newT = midY - (h / 2f)
        if (newT < 0f) newT = 0f
        if (newT + h > imageH) newT = imageH - h
        clampCrop(CropRect(clamped.left, newT, clamped.left + w, newT + h), imageW, imageH, minW, minH)
      }
      CropHandle.TOP -> {
        var h = max(effMinH, clamped.height)
        var w = h * ratio
        if (clamped.bottom - h < 0f) {
          h = clamped.bottom
          w = h * ratio
        }
        if (w > imageW) {
          w = imageW
          h = w / ratio
        }
        val midX = (clamped.left + clamped.right) / 2f
        var newL = midX - (w / 2f)
        if (newL < 0f) newL = 0f
        if (newL + w > imageW) newL = imageW - w
        clampCrop(CropRect(newL, clamped.bottom - h, newL + w, clamped.bottom), imageW, imageH, minW, minH)
      }
      CropHandle.BOTTOM -> {
        var h = max(effMinH, clamped.height)
        var w = h * ratio
        if (clamped.top + h > imageH) {
          h = imageH - clamped.top
          w = h * ratio
        }
        if (w > imageW) {
          w = imageW
          h = w / ratio
        }
        val midX = (clamped.left + clamped.right) / 2f
        var newL = midX - (w / 2f)
        if (newL < 0f) newL = 0f
        if (newL + w > imageW) newL = imageW - w
        clampCrop(CropRect(newL, clamped.top, newL + w, clamped.top + h), imageW, imageH, minW, minH)
      }
      CropHandle.NONE, CropHandle.INSIDE -> {
        var newW: Float
        var newH: Float
        if (clamped.width / clamped.height > ratio) {
          newH = clamped.height
          newW = newH * ratio
          if (newW > imageW) {
            newW = imageW
            newH = newW / ratio
          }
        } else {
          newW = clamped.width
          newH = newW / ratio
          if (newH > imageH) {
            newH = imageH
            newW = newH * ratio
          }
        }
        newW = max(effMinW, newW)
        newH = max(effMinH, newH)
        val midX = (clamped.left + clamped.right) / 2f
        val midY = (clamped.top + clamped.bottom) / 2f
        var newL = midX - (newW / 2f)
        var newT = midY - (newH / 2f)
        if (newL < 0f) newL = 0f
        if (newL + newW > imageW) newL = imageW - newW
        if (newT < 0f) newT = 0f
        if (newT + newH > imageH) newT = imageH - newH
        clampCrop(CropRect(newL, newT, newL + newW, newT + newH), imageW, imageH, minW, minH)
      }
    }
  }

  fun applyAspect(
    rect: CropRect,
    aspectRatio: CropAspectRatio,
    handle: CropHandle = CropHandle.NONE,
    imageW: Float,
    imageH: Float,
    minW: Float = 16f,
    minH: Float = 16f
  ): CropRect {
    return applyAspect(rect, aspectRatio.ratio, handle, imageW, imageH, minW, minH)
  }

  /**
   * Hit test for the 8 resize handles and the inside region.
   * `rect` is in oriented source-image pixel coordinates.
   * `point` is in canvas pixel coordinates.
   */
  fun hitTest(
    point: CropPoint,
    rect: CropRect,
    transform: ViewTransform,
    radiusPx: Float
  ): CropHandle {
    val canvasCrop = transform.imageToCanvas(rect)

    fun dist(x1: Float, y1: Float, x2: Float, y2: Float): Float = hypot(x1 - x2, y1 - y2)

    val midX = (canvasCrop.left + canvasCrop.right) / 2f
    val midY = (canvasCrop.top + canvasCrop.bottom) / 2f

    // 1. Corners (highest priority)
    if (dist(point.x, point.y, canvasCrop.left, canvasCrop.top) <= radiusPx) return CropHandle.TOP_LEFT
    if (dist(point.x, point.y, canvasCrop.right, canvasCrop.top) <= radiusPx) return CropHandle.TOP_RIGHT
    if (dist(point.x, point.y, canvasCrop.left, canvasCrop.bottom) <= radiusPx) return CropHandle.BOTTOM_LEFT
    if (dist(point.x, point.y, canvasCrop.right, canvasCrop.bottom) <= radiusPx) return CropHandle.BOTTOM_RIGHT

    // 2. Edges
    if (dist(point.x, point.y, midX, canvasCrop.top) <= radiusPx) return CropHandle.TOP
    if (dist(point.x, point.y, midX, canvasCrop.bottom) <= radiusPx) return CropHandle.BOTTOM
    if (dist(point.x, point.y, canvasCrop.left, midY) <= radiusPx) return CropHandle.LEFT
    if (dist(point.x, point.y, canvasCrop.right, midY) <= radiusPx) return CropHandle.RIGHT

    // 3. Inside
    if (point.x >= canvasCrop.left && point.x <= canvasCrop.right &&
      point.y >= canvasCrop.top && point.y <= canvasCrop.bottom
    ) {
      return CropHandle.INSIDE
    }

    return CropHandle.NONE
  }

  /**
   * Maps an oriented source-pixel crop rectangle back to RAW image pixel coordinates.
   * Uses the exact inverse of preview transformation (flip first, then rotate).
   */
  fun mapOrientedRectToRaw(
    rect: CropRect,
    rawW: Int,
    rawH: Int,
    orientationDegrees: Int,
    isFlipped: Boolean
  ): CropRectInt {
    val ol = rect.left
    val ot = rect.top
    val ow = rect.width
    val oh = rect.height

    val normDeg = ((orientationDegrees % 360) + 360) % 360

    // 1. Invert the rotation: from oriented space back to intermediate post-flip space
    val ix: Float
    val iy: Float
    val iw: Float
    val ih: Float

    when (normDeg) {
      90 -> {
        ix = ot
        iy = rawH - (ol + ow)
        iw = oh
        ih = ow
      }
      180 -> {
        ix = rawW - (ol + ow)
        iy = rawH - (ot + oh)
        iw = ow
        ih = oh
      }
      270 -> {
        ix = rawW - (ot + oh)
        iy = ol
        iw = oh
        ih = ow
      }
      else -> {
        ix = ol
        iy = ot
        iw = ow
        ih = oh
      }
    }

    // 2. Invert the horizontal flip: from post-flip intermediate space back to raw space
    val rx: Float = if (isFlipped) rawW - (ix + iw) else ix
    val ry: Float = iy
    val rw: Float = iw
    val rh: Float = ih

    val intLeft = safeCoerce(rx.roundToInt(), 0, max(0, rawW - 1))
    val intTop = safeCoerce(ry.roundToInt(), 0, max(0, rawH - 1))
    val intWidth = safeCoerce(rw.roundToInt(), 1, rawW - intLeft)
    val intHeight = safeCoerce(rh.roundToInt(), 1, rawH - intTop)

    return CropRectInt(
      left = intLeft,
      top = intTop,
      width = intWidth,
      height = intHeight
    )
  }

  // Backward compatibility helpers
  fun clampCropRect(
    rect: CropRect,
    boundsWidth: Float,
    boundsHeight: Float,
    minSize: Float = 16f
  ): CropRect = clampCrop(rect, boundsWidth, boundsHeight, minSize, minSize)

  fun applyAspectRatio(
    rect: CropRect,
    aspectRatio: CropAspectRatio,
    boundsWidth: Float,
    boundsHeight: Float,
    minSize: Float = 16f
  ): CropRect = applyAspect(rect, aspectRatio, CropHandle.NONE, boundsWidth, boundsHeight, minSize, minSize)

  fun mapScreenCropToImageCrop(
    screenCrop: CropRect,
    imageDisplayRect: CropRect,
    imageWidth: Int,
    imageHeight: Int,
    minPixels: Int = 16
  ): CropRectInt {
    if (imageDisplayRect.width <= 0f || imageDisplayRect.height <= 0f || imageWidth <= 0 || imageHeight <= 0) {
      return CropRectInt(0, 0, max(minPixels, imageWidth), max(minPixels, imageHeight))
    }

    val scaleX = imageWidth.toFloat() / imageDisplayRect.width
    val scaleY = imageHeight.toFloat() / imageDisplayRect.height

    val relLeft = max(0f, screenCrop.left - imageDisplayRect.left)
    val relTop = max(0f, screenCrop.top - imageDisplayRect.top)
    val relRight = min(imageDisplayRect.width, screenCrop.right - imageDisplayRect.left)
    val relBottom = min(imageDisplayRect.height, screenCrop.bottom - imageDisplayRect.top)

    val imgLeft = safeCoerce((relLeft * scaleX).roundToInt(), 0, imageWidth - minPixels)
    val imgTop = safeCoerce((relTop * scaleY).roundToInt(), 0, imageHeight - minPixels)
    val imgRight = safeCoerce((relRight * scaleX).roundToInt(), imgLeft + minPixels, imageWidth)
    val imgBottom = safeCoerce((relBottom * scaleY).roundToInt(), imgTop + minPixels, imageHeight)

    return CropRectInt(
      left = imgLeft,
      top = imgTop,
      width = max(minPixels, imgRight - imgLeft),
      height = max(minPixels, imgBottom - imgTop)
    )
  }

  fun mapCropForExifOrientation(
    crop: CropRectInt,
    rawWidth: Int,
    rawHeight: Int,
    orientationDegrees: Int,
    isFlipped: Boolean
  ): CropRectInt {
    var l = crop.left
    var t = crop.top
    var w = crop.width
    var h = crop.height

    if (isFlipped) {
      l = rawWidth - (l + w)
    }

    return when (((orientationDegrees % 360) + 360) % 360) {
      90 -> CropRectInt(left = t, top = rawWidth - (l + w), width = h, height = w)
      180 -> CropRectInt(left = rawWidth - (l + w), top = rawHeight - (t + h), width = w, height = h)
      270 -> CropRectInt(left = rawHeight - (t + h), top = l, width = h, height = w)
      else -> CropRectInt(left = l, top = t, width = w, height = h)
    }
  }

  /**
   * Computes the largest rectangle of the given aspect ratio that fits entirely within
   * [0, imageW] x [0, imageH], centered at [currentCenter] and clamped to image bounds.
   */
  fun fitRatioInImage(
    aspectRatio: CropAspectRatio,
    imageW: Float,
    imageH: Float,
    currentCenter: CropPoint = CropPoint(imageW / 2f, imageH / 2f)
  ): CropRect {
    if (imageW <= 0f || imageH <= 0f) {
      return CropRect(0f, 0f, maxOf(0f, imageW), maxOf(0f, imageH))
    }
    if (!aspectRatio.hasFixedRatio || aspectRatio.ratio <= 0f) {
      return CropRect(0f, 0f, imageW, imageH)
    }

    val r = aspectRatio.ratio
    var maxW: Float
    var maxH: Float
    if (imageW / imageH > r) {
      maxH = imageH
      maxW = maxH * r
    } else {
      maxW = imageW
      maxH = maxW / r
    }

    val halfW = maxW / 2f
    val halfH = maxH / 2f

    var left = currentCenter.x - halfW
    var top = currentCenter.y - halfH

    if (left < 0f) left = 0f
    if (left + maxW > imageW) left = imageW - maxW

    if (top < 0f) top = 0f
    if (top + maxH > imageH) top = imageH - maxH

    return CropRect(left, top, left + maxW, top + maxH)
  }
}
