package com.example.bgremove

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import androidx.compose.ui.graphics.Color

enum class BrushMode {
  ERASE,
  RESTORE
}

sealed interface BackgroundChoice {
  object Transparent : BackgroundChoice
  object White : BackgroundChoice
  object Black : BackgroundChoice
  data class CustomColor(val color: Color) : BackgroundChoice
  data class CustomImage(val uriString: String) : BackgroundChoice
}

data class PointF(val x: Float, val y: Float)

data class BrushStroke(
  val mode: BrushMode,
  val sizePx: Float,
  val points: List<PointF>
)

sealed interface EditAction {
  data class StrokeAction(val stroke: BrushStroke) : EditAction
  data class BackgroundAction(val background: BackgroundChoice) : EditAction
}

/**
 * Manages bounded undo / redo history for manual strokes and background changes.
 */
class BgRemoveHistoryManager(val maxHistory: Int = 25) {

  private val undoStack = mutableListOf<EditAction>()
  private val redoStack = mutableListOf<EditAction>()

  val canUndo: Boolean
    get() = undoStack.isNotEmpty()

  val canRedo: Boolean
    get() = redoStack.isNotEmpty()

  val strokeList: List<BrushStroke>
    get() = undoStack.filterIsInstance<EditAction.StrokeAction>().map { it.stroke }

  /** Background choice that is active after the current undo/redo position (null = never changed). */
  val currentBackground: BackgroundChoice?
    get() = undoStack.filterIsInstance<EditAction.BackgroundAction>().lastOrNull()?.background

  fun push(action: EditAction) {
    if (undoStack.size >= maxHistory) {
      undoStack.removeAt(0)
    }
    undoStack.add(action)
    redoStack.clear()
  }

  fun undo(): EditAction? {
    if (undoStack.isEmpty()) return null
    val action = undoStack.removeAt(undoStack.lastIndex)
    redoStack.add(action)
    return action
  }

  fun redo(): EditAction? {
    if (redoStack.isEmpty()) return null
    val action = redoStack.removeAt(redoStack.lastIndex)
    undoStack.add(action)
    return action
  }

  fun clear() {
    undoStack.clear()
    redoStack.clear()
  }

  fun size(): Int = undoStack.size
}

object MaskRenderer {

  /**
   * Re-renders the editable mask by stamping strokes onto a copy of the base mask ByteArray.
   * ERASE mode clears alpha (sets to 0).
   * RESTORE mode restores alpha to 255.
   */
  fun renderStrokes(
    baseMask: ByteArray,
    width: Int,
    height: Int,
    strokes: List<BrushStroke>
  ): ByteArray {
    val editable = baseMask.copyOf()
    if (strokes.isEmpty()) return editable

    for (stroke in strokes) {
      if (stroke.points.isEmpty()) continue
      val radius = (stroke.sizePx / 2f).coerceAtLeast(1f)

      if (stroke.points.size == 1) {
        val p = stroke.points[0]
        BgRemoveMath.stampCircle(editable, width, height, p.x, p.y, radius, stroke.mode)
      } else {
        for (i in 1 until stroke.points.size) {
          val p0 = stroke.points[i - 1]
          val p1 = stroke.points[i]
          BgRemoveMath.stampLine(editable, width, height, p0.x, p0.y, p1.x, p1.y, radius, stroke.mode)
        }
      }
    }

    return editable
  }
}
