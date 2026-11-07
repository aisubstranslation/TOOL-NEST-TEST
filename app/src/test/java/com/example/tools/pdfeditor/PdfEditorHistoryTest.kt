package com.example.tools.pdfeditor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PdfEditorHistoryTest {

  @Test
  fun testInitialState() {
    val history = PdfEditorHistory()
    assertFalse(history.canUndo)
    assertFalse(history.canRedo)
    assertTrue(history.currentState.pageOverlays.isEmpty())
    assertTrue(history.currentState.pageOps.isEmpty())
  }

  @Test
  fun testPushStateAndUndoRedo() {
    val history = PdfEditorHistory()

    val state1 = EditorSessionState(
      pageOps = listOf(PageOp.RotatePage(0, 90))
    )
    history.pushState(state1)

    assertTrue(history.canUndo)
    assertFalse(history.canRedo)
    assertEquals(1, history.currentState.pageOps.size)

    val state2 = EditorSessionState(
      pageOps = listOf(PageOp.RotatePage(0, 90), PageOp.DeletePage(1))
    )
    history.pushState(state2)

    assertEquals(2, history.currentState.pageOps.size)

    // Undo -> returns to state1
    val undone = history.undo()
    assertEquals(state1, undone)
    assertEquals(1, history.currentState.pageOps.size)
    assertTrue(history.canUndo)
    assertTrue(history.canRedo)

    // Undo again -> returns to initial empty state
    val undone2 = history.undo()
    assertTrue(undone2?.pageOps?.isEmpty() == true)
    assertFalse(history.canUndo)
    assertTrue(history.canRedo)

    // Redo -> returns to state1
    val redone = history.redo()
    assertEquals(state1, redone)
    assertTrue(history.canUndo)
    assertTrue(history.canRedo)

    // Redo -> returns to state2
    val redone2 = history.redo()
    assertEquals(state2, redone2)
    assertTrue(history.canUndo)
    assertFalse(history.canRedo)
  }

  @Test
  fun testReset() {
    val history = PdfEditorHistory()
    history.pushState(EditorSessionState(pageOps = listOf(PageOp.DeletePage(0))))
    assertTrue(history.canUndo)

    history.reset()
    assertFalse(history.canUndo)
    assertFalse(history.canRedo)
    assertTrue(history.currentState.pageOps.isEmpty())
  }
}
