package com.example.tools.pdfeditor

import android.graphics.RectF
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.R

enum class EditKind { EDIT_EXISTING, COVER_AREA, NEW_TEXT, EDIT_OVERLAY }

/** What the user tapped / selected on a page, plus the style guessed from the page. */
data class EditTarget(
  val kind: EditKind,
  /** Area to hide with the page colour (null when only new text is added). */
  val coverBounds: RectF?,
  val leftX: Float,
  val rightX: Float,
  val baselineY: Float,
  val initialText: String,
  val fontFamily: String,
  val isBold: Boolean,
  val isItalic: Boolean,
  val fontSizePt: Float,
  val bgColor: Int,
  val inkColor: Int,
  val pageWidthPt: Float,
  val pageHeightPt: Float,
  /** PDF font name of the original text, so the saved file can reuse that font. */
  val fontName: String? = null,
  /** Set when an already placed text object is being edited. */
  val existingId: String? = null
)

data class TextEditResult(
  val text: String,
  val fontFamily: String,
  val isBold: Boolean,
  val isItalic: Boolean,
  val fontSizePt: Float,
  val colorInt: Int
)

/** Turns a confirmed edit into overlay objects: an optional page-coloured cover plus the new text. */
fun buildEditOverlays(target: EditTarget, result: TextEditResult): List<OverlayObject> {
  val list = mutableListOf<OverlayObject>()
  val cover = target.coverBounds
  if (cover != null) {
    list.add(CoverOverlay(bounds = RectF(cover), fillColorInt = target.bgColor))
  }
  if (result.text.isNotBlank() && target.pageHeightPt > 0f) {
    val fsNorm = result.fontSizePt / target.pageHeightPt
    val top = (target.baselineY - 0.8f * fsNorm).coerceIn(0f, 1f)
    val bottom = (target.baselineY + 0.2f * fsNorm).coerceIn(0f, 1f)
    val left = target.leftX.coerceIn(0f, 0.98f)
    val right = maxOf(target.rightX, left + 0.25f).coerceAtMost(1f)
    val sameFont = target.fontName != null &&
      result.fontFamily == target.fontFamily &&
      result.isBold == target.isBold &&
      result.isItalic == target.isItalic
    val overlay = TextOverlay(
      id = target.existingId ?: java.util.UUID.randomUUID().toString(),
      bounds = RectF(left, top, maxOf(right, left + 0.01f), maxOf(bottom, top + 0.005f)),
      text = result.text,
      fontFamily = result.fontFamily,
      fontSize = result.fontSizePt,
      isBold = result.isBold,
      isItalic = result.isItalic,
      colorInt = result.colorInt,
      sourceFontName = if (sameFont) target.fontName else null
    )
    list.add(overlay)
  }
  return list
}

@Composable
fun TextEditDialog(
  target: EditTarget,
  onDismiss: () -> Unit,
  onConfirm: (TextEditResult) -> Unit
) {
  var text by remember(target) { mutableStateOf(target.initialText) }
  var font by remember(target) { mutableStateOf(target.fontFamily) }
  var bold by remember(target) { mutableStateOf(target.isBold) }
  var italic by remember(target) { mutableStateOf(target.isItalic) }
  var size by remember(target) { mutableFloatStateOf(target.fontSizePt.coerceIn(6f, 96f)) }
  // 0 = original colour of the page text, 1 = black, 2 = blue, 3 = red
  var colorChoice by remember(target) { mutableIntStateOf(0) }

  val title = when (target.kind) {
    EditKind.EDIT_EXISTING, EditKind.EDIT_OVERLAY -> stringResource(R.string.tool21_edit_title)
    EditKind.COVER_AREA -> stringResource(R.string.tool21_cover_title)
    EditKind.NEW_TEXT -> stringResource(R.string.tool21_dialog_add_text)
  }
  val hint = when (target.kind) {
    EditKind.EDIT_EXISTING -> stringResource(R.string.tool21_edit_hint)
    EditKind.COVER_AREA -> stringResource(R.string.tool21_cover_note)
    EditKind.NEW_TEXT, EditKind.EDIT_OVERLAY -> null
  }
  val label = if (target.kind == EditKind.COVER_AREA) {
    stringResource(R.string.tool21_replacement_optional)
  } else {
    stringResource(R.string.tool21_text_label)
  }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(title) },
    text = {
      Column(
        modifier = Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)
      ) {
        if (hint != null) {
          Text(
            text = hint,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
        OutlinedTextField(
          value = text,
          onValueChange = { text = it },
          label = { Text(label) },
          minLines = 1,
          maxLines = 4,
          modifier = Modifier.fillMaxWidth().testTag("pdf_edit_text_field")
        )

        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          FilterChip(selected = font == "Sans", onClick = { font = "Sans" }, label = { Text("Sans") })
          FilterChip(selected = font == "Serif", onClick = { font = "Serif" }, label = { Text("Serif") })
          FilterChip(selected = font == "Mono", onClick = { font = "Mono" }, label = { Text("Mono") })
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          FilterChip(
            selected = bold,
            onClick = { bold = !bold },
            label = { Text(stringResource(R.string.tool21_bold)) }
          )
          FilterChip(
            selected = italic,
            onClick = { italic = !italic },
            label = { Text(stringResource(R.string.tool21_italic)) }
          )
        }

        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
          Text(
            text = stringResource(R.string.tool21_font_size),
            style = MaterialTheme.typography.labelLarge
          )
          IconButton(onClick = { size = (size - 1f).coerceAtLeast(6f) }) {
            Icon(Icons.Default.Remove, contentDescription = stringResource(R.string.tool21_size_smaller), modifier = Modifier.size(20.dp))
          }
          Text(text = "${size.toInt()} pt", style = MaterialTheme.typography.bodyLarge)
          IconButton(onClick = { size = (size + 1f).coerceAtMost(96f) }) {
            Icon(Icons.Default.Add, contentDescription = stringResource(R.string.tool21_size_larger), modifier = Modifier.size(20.dp))
          }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          FilterChip(selected = colorChoice == 0, onClick = { colorChoice = 0 }, label = { Text(stringResource(R.string.tool21_color_original)) })
          FilterChip(selected = colorChoice == 1, onClick = { colorChoice = 1 }, label = { Text(stringResource(R.string.tool21_color_black)) })
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          FilterChip(selected = colorChoice == 2, onClick = { colorChoice = 2 }, label = { Text(stringResource(R.string.tool21_color_blue)) })
          FilterChip(selected = colorChoice == 3, onClick = { colorChoice = 3 }, label = { Text(stringResource(R.string.tool21_color_red)) })
        }
      }
    },
    confirmButton = {
      Button(
        onClick = {
          val color = when (colorChoice) {
            1 -> 0xFF000000.toInt()
            2 -> 0xFF1A3A8F.toInt()
            3 -> 0xFFC62828.toInt()
            else -> target.inkColor
          }
          onConfirm(TextEditResult(text.trim(), font, bold, italic, size, color))
        },
        enabled = text.isNotBlank() || target.kind == EditKind.COVER_AREA,
        modifier = Modifier.testTag("pdf_edit_confirm")
      ) {
        Text(stringResource(R.string.common_done))
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
    }
  )
}
