package com.example.tools.pdfeditor

import android.graphics.PointF
import android.graphics.RectF
import java.util.UUID

sealed interface OverlayObject {
  val id: String
  val bounds: RectF // Normalized display bounds [0..1] x [0..1]
}

data class TextOverlay(
  override val id: String = UUID.randomUUID().toString(),
  override val bounds: RectF,
  val text: String,
  val fontFamily: String = "Sans", // "Sans", "Serif", "Mono"
  val fontSize: Float = 16f,
  val isBold: Boolean = false,
  val isItalic: Boolean = false,
  val colorInt: Int = 0xFF000000.toInt(),
  val bgColorInt: Int? = null,
  val opacity: Float = 1.0f,
  /** Name of the PDF font the text was copied from; the exporter reuses it when it can. */
  val sourceFontName: String? = null
) : OverlayObject

data class ImageOverlay(
  override val id: String = UUID.randomUUID().toString(),
  override val bounds: RectF,
  val imagePath: String,
  val rotationDegrees: Float = 0f,
  val opacity: Float = 1.0f
) : OverlayObject

data class SignatureOverlay(
  override val id: String = UUID.randomUUID().toString(),
  override val bounds: RectF,
  val signaturePngPath: String,
  val opacity: Float = 1.0f
) : OverlayObject

enum class DrawMode { PEN, HIGHLIGHTER }

data class DrawOverlay(
  override val id: String = UUID.randomUUID().toString(),
  override val bounds: RectF,
  val points: List<PointF>,
  val mode: DrawMode = DrawMode.PEN,
  val colorInt: Int = 0xFF000000.toInt(),
  val strokeWidthPx: Float = 3f,
  val alpha: Float = 1.0f
) : OverlayObject

enum class ShapeType { LINE, ARROW, RECTANGLE, ELLIPSE }

data class ShapeOverlay(
  override val id: String = UUID.randomUUID().toString(),
  override val bounds: RectF,
  val type: ShapeType,
  val strokeColorInt: Int = 0xFF000000.toInt(),
  val strokeWidthPx: Float = 3f,
  val fillColorInt: Int? = null
) : OverlayObject

data class CoverOverlay(
  override val id: String = UUID.randomUUID().toString(),
  override val bounds: RectF,
  val fillColorInt: Int = 0xFFFFFFFF.toInt(),
  val replacementText: String? = null
) : OverlayObject

data class RedactOverlay(
  override val id: String = UUID.randomUUID().toString(),
  override val bounds: RectF,
  val fillColorInt: Int = 0xFF000000.toInt()
) : OverlayObject

sealed interface PageOp {
  data class DeletePage(val pageIndex: Int) : PageOp
  data class RotatePage(val pageIndex: Int, val angle: Int) : PageOp
  data class DuplicatePage(val pageIndex: Int) : PageOp
  data class ReorderPages(val fromIndex: Int, val toIndex: Int) : PageOp
  data class InsertBlankPage(val afterIndex: Int) : PageOp
}

data class EditorSessionState(
  val pageOverlays: Map<Int, List<OverlayObject>> = emptyMap(),
  val pageOps: List<PageOp> = emptyList(),
  val flattenedPages: Set<Int> = emptySet()
)

class PdfEditorHistory(initialState: EditorSessionState = EditorSessionState()) {
  private val undoStack = mutableListOf<EditorSessionState>()
  private val redoStack = mutableListOf<EditorSessionState>()
  var currentState: EditorSessionState = initialState
    private set

  val canUndo: Boolean get() = undoStack.isNotEmpty()
  val canRedo: Boolean get() = redoStack.isNotEmpty()

  fun pushState(newState: EditorSessionState) {
    if (newState != currentState) {
      undoStack.add(currentState)
      redoStack.clear()
      currentState = newState
    }
  }

  fun undo(): EditorSessionState? {
    if (undoStack.isEmpty()) return null
    redoStack.add(currentState)
    currentState = undoStack.removeAt(undoStack.lastIndex)
    return currentState
  }

  fun redo(): EditorSessionState? {
    if (redoStack.isEmpty()) return null
    undoStack.add(currentState)
    currentState = redoStack.removeAt(redoStack.lastIndex)
    return currentState
  }

  fun reset(initialState: EditorSessionState = EditorSessionState()) {
    undoStack.clear()
    redoStack.clear()
    currentState = initialState
  }
}
