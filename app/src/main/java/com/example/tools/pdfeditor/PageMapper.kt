package com.example.tools.pdfeditor

import java.util.UUID

/** One page as the user sees it after page operations. [sourceIndex] is null for an inserted blank page. */
data class PageEntry(
  val sourceIndex: Int?,
  /** Original page whose size a blank page copies (and which is rendered for normal pages). */
  val refSource: Int,
  /** Extra rotation applied by the user, in degrees (0, 90, 180, 270). */
  val rotation: Int
)

/**
 * Works out what the page list looks like after the user's page operations, using the very same
 * rules as [PdfEditorExporter], so what is shown in the editor is what gets saved.
 */
object PageMapper {

  fun entries(sourcePageCount: Int, ops: List<PageOp>): List<PageEntry> {
    val list = MutableList(sourcePageCount.coerceAtLeast(0)) { PageEntry(it, it, 0) }
    for (op in ops) {
      when (op) {
        is PageOp.DeletePage -> {
          if (list.size > 1 && op.pageIndex in list.indices) list.removeAt(op.pageIndex)
        }
        is PageOp.RotatePage -> {
          if (op.pageIndex in list.indices) {
            val e = list[op.pageIndex]
            list[op.pageIndex] = e.copy(rotation = ((e.rotation + op.angle) % 360 + 360) % 360)
          }
        }
        is PageOp.DuplicatePage -> {
          if (op.pageIndex in list.indices) list.add(op.pageIndex + 1, list[op.pageIndex])
        }
        is PageOp.InsertBlankPage -> {
          val idx = (op.afterIndex + 1).coerceIn(0, list.size)
          val ref = list.getOrNull(op.afterIndex)?.refSource ?: list.firstOrNull()?.refSource ?: 0
          list.add(idx, PageEntry(null, ref, 0))
        }
        is PageOp.ReorderPages -> Unit
      }
    }
    return list
  }

  /** Moves the per-page edits so they follow their pages when a page operation is applied. */
  fun remapForOp(
    overlays: Map<Int, List<OverlayObject>>,
    flattened: Set<Int>,
    op: PageOp
  ): Pair<Map<Int, List<OverlayObject>>, Set<Int>> {
    return when (op) {
      is PageOp.DeletePage -> {
        val k = op.pageIndex
        val newOverlays = overlays.filterKeys { it != k }.mapKeys { if (it.key > k) it.key - 1 else it.key }
        val newFlat = flattened.filter { it != k }.map { if (it > k) it - 1 else it }.toSet()
        newOverlays to newFlat
      }
      is PageOp.DuplicatePage -> {
        val k = op.pageIndex
        val moved = overlays.mapKeys { if (it.key > k) it.key + 1 else it.key }.toMutableMap()
        overlays[k]?.let { list -> moved[k + 1] = list.map { it.withNewId() } }
        val newFlat = flattened.map { if (it > k) it + 1 else it }.toMutableSet()
        if (flattened.contains(k)) newFlat.add(k + 1)
        moved to newFlat
      }
      is PageOp.InsertBlankPage -> {
        val k = op.afterIndex
        overlays.mapKeys { if (it.key > k) it.key + 1 else it.key } to
          flattened.map { if (it > k) it + 1 else it }.toSet()
      }
      else -> overlays to flattened
    }
  }
}

fun OverlayObject.withNewId(): OverlayObject {
  val newId = UUID.randomUUID().toString()
  return when (this) {
    is TextOverlay -> copy(id = newId)
    is ImageOverlay -> copy(id = newId)
    is SignatureOverlay -> copy(id = newId)
    is DrawOverlay -> copy(id = newId)
    is ShapeOverlay -> copy(id = newId)
    is CoverOverlay -> copy(id = newId)
    is RedactOverlay -> copy(id = newId)
  }
}
