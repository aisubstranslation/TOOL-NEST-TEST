package com.example.bgremove

import android.graphics.Bitmap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/**
 * Live editing surface for the brush tool.
 *
 *  - [data].baseMask is NEVER modified (it is the pristine AI result, so undo/redo and export can
 *    always replay strokes on top of it).
 *  - [mask] is the working copy that brush strokes paint into.
 *  - [bitmap] is the on-screen cutout. While drawing, only the small rectangle touched by the brush is
 *    re-composited, so erase / restore appear instantly (no full-image re-render per touch event).
 */
class CutoutEditor(val data: InitialCutoutData) {

  val width: Int = data.maskWidth
  val height: Int = data.maskHeight

  /** Original RGB pixels of the working-size image (read only). */
  private val orig: IntArray = IntArray(width * height).also {
    data.workingBitmap.getPixels(it, 0, width, 0, 0, width, height)
  }

  var mask: ByteArray = data.baseMask.copyOf()
    private set

  val bitmap: Bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

  /** Edge softness (box blur radius 0..3) used by region refreshes. */
  @Volatile
  var softness: Int = 1

  /** Bumped after every bitmap change so the canvas redraws. */
  var version by mutableIntStateOf(0)
    private set

  // Stroke in progress (for cancel when a second finger lands)
  private var backup: ByteArray? = null
  private var dirtyL = Int.MAX_VALUE
  private var dirtyT = Int.MAX_VALUE
  private var dirtyR = Int.MIN_VALUE
  private var dirtyB = Int.MIN_VALUE

  /** Pure function: builds all output pixels for a given mask. Safe to run on a background thread. */
  fun computeFull(maskSnapshot: ByteArray, softnessValue: Int): IntArray {
    val finalMask = if (softnessValue in 1..3) {
      BgRemoveMath.applyBoxBlur(maskSnapshot, width, height, softnessValue)
    } else {
      maskSnapshot
    }
    val out = IntArray(width * height)
    BgRemoveMath.compositePixels(orig, finalMask, out, 0, width * height)
    return out
  }

  /** Main thread: swap in a freshly computed mask + pixels. */
  fun commitFull(newMask: ByteArray, pixels: IntArray) {
    mask = newMask
    bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
    version++
  }

  fun beginStroke() {
    backup = mask.copyOf()
    dirtyL = Int.MAX_VALUE
    dirtyT = Int.MAX_VALUE
    dirtyR = Int.MIN_VALUE
    dirtyB = Int.MIN_VALUE
  }

  /**
   * Paints a dot (when from == to and [single]) or a line segment into the mask and refreshes only the
   * touched rectangle of the on-screen bitmap. Coordinates and radius are in mask pixels.
   */
  fun stamp(
    fromX: Float,
    fromY: Float,
    toX: Float,
    toY: Float,
    radius: Float,
    mode: BrushMode,
    single: Boolean
  ) {
    if (single) {
      BgRemoveMath.stampCircle(mask, width, height, toX, toY, radius, mode)
    } else {
      BgRemoveMath.stampLine(mask, width, height, fromX, fromY, toX, toY, radius, mode)
    }
    val pad = radius + 2f
    val l = floor(min(fromX, toX) - pad).toInt()
    val t = floor(min(fromY, toY) - pad).toInt()
    val r = floor(max(fromX, toX) + pad).toInt() + 1
    val b = floor(max(fromY, toY) + pad).toInt() + 1
    refreshRegion(l, t, r, b)
  }

  fun endStroke() {
    backup = null
  }

  /** Second finger landed: throw the unfinished stroke away without any full re-render. */
  fun cancelStroke() {
    val saved = backup ?: return
    System.arraycopy(saved, 0, mask, 0, mask.size)
    backup = null
    if (dirtyL <= dirtyR && dirtyT <= dirtyB) {
      refreshRegion(dirtyL, dirtyT, dirtyR, dirtyB)
    }
  }

  private fun refreshRegion(left: Int, top: Int, right: Int, bottom: Int) {
    val l = left.coerceIn(0, width - 1)
    val t = top.coerceIn(0, height - 1)
    val r = right.coerceIn(0, width - 1)
    val b = bottom.coerceIn(0, height - 1)
    if (r < l || b < t) return

    dirtyL = min(dirtyL, l)
    dirtyT = min(dirtyT, t)
    dirtyR = max(dirtyR, r)
    dirtyB = max(dirtyB, b)

    val m = softness.coerceIn(0, 3)
    val x0 = max(0, l - m)
    val y0 = max(0, t - m)
    val x1 = min(width - 1, r + m)
    val y1 = min(height - 1, b + m)
    val sw = x1 - x0 + 1
    val sh = y1 - y0 + 1

    val sub = ByteArray(sw * sh)
    for (j in 0 until sh) {
      System.arraycopy(mask, (y0 + j) * width + x0, sub, j * sw, sw)
    }
    val alphaSrc = if (m > 0) BgRemoveMath.applyBoxBlur(sub, sw, sh, m) else sub

    val rw = r - l + 1
    val rh = b - t + 1
    val out = IntArray(rw * rh)
    for (y in t..b) {
      val srcRow = (y - y0) * sw
      val dstRow = (y - t) * rw
      val origRow = y * width
      for (x in l..r) {
        val a = alphaSrc[srcRow + (x - x0)].toInt() and 0xFF
        out[dstRow + (x - l)] = (a shl 24) or (orig[origRow + x] and 0x00FFFFFF)
      }
    }
    bitmap.setPixels(out, 0, rw, l, t, rw, rh)
    version++
  }
}
