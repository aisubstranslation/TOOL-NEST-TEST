package com.example.tools.pdfeditor

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.RectF
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.file.FileLayer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import kotlin.math.min

/** Actions for the object that is currently selected on the page. */
@Composable
internal fun SelectionBar(
  selected: OverlayObject,
  onEdit: () -> Unit,
  onDuplicate: () -> Unit,
  onDelete: () -> Unit,
  onDone: () -> Unit
) {
  Surface(
    modifier = Modifier.fillMaxWidth().testTag("pdf_editor_selection_bar"),
    color = MaterialTheme.colorScheme.primaryContainer,
    tonalElevation = 3.dp
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .horizontalScroll(rememberScrollState())
        .padding(horizontal = 8.dp, vertical = 2.dp),
      horizontalArrangement = Arrangement.spacedBy(4.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      if (selected is TextOverlay || selected is ImageOverlay) {
        TextButton(onClick = onEdit) {
          Icon(
            imageVector = if (selected is TextOverlay) Icons.Default.Edit else Icons.Default.Crop,
            contentDescription = null,
            modifier = Modifier.size(18.dp)
          )
          Spacer(Modifier.width(4.dp))
          Text(
            stringResource(if (selected is TextOverlay) R.string.tool21_sel_edit else R.string.tool21_sel_crop)
          )
        }
      }
      TextButton(onClick = onDuplicate) {
        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(4.dp))
        Text(stringResource(R.string.tool21_sel_duplicate))
      }
      TextButton(onClick = onDelete) {
        Icon(
          Icons.Default.Delete,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.error,
          modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(4.dp))
        Text(stringResource(R.string.tool21_sel_delete), color = MaterialTheme.colorScheme.error)
      }
      TextButton(onClick = onDone) {
        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(4.dp))
        Text(stringResource(R.string.tool21_sel_done))
      }
    }
  }
}

private fun decodeForCrop(path: String, maxEdge: Int): Bitmap? {
  return try {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, bounds)
    var sample = 1
    while (bounds.outWidth / sample > maxEdge || bounds.outHeight / sample > maxEdge) sample *= 2
    BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
  } catch (_: Throwable) {
    null
  }
}

/** Crops a placed picture by trimming each side; the visible part stays where it is on the page. */
@Composable
internal fun ImageCropDialog(
  image: ImageOverlay,
  onDismiss: () -> Unit,
  onCropped: (ImageOverlay) -> Unit
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  var preview by remember(image.imagePath) { mutableStateOf<Bitmap?>(null) }
  var left by remember { mutableFloatStateOf(0f) }
  var right by remember { mutableFloatStateOf(0f) }
  var top by remember { mutableFloatStateOf(0f) }
  var bottom by remember { mutableFloatStateOf(0f) }
  var busy by remember { mutableStateOf(false) }

  LaunchedEffect(image.imagePath) {
    preview = withContext(Dispatchers.IO) { decodeForCrop(image.imagePath, 1200) }
  }

  AlertDialog(
    onDismissRequest = { if (!busy) onDismiss() },
    title = { Text(stringResource(R.string.tool21_crop_title)) },
    text = {
      Column(
        modifier = Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(6.dp)
      ) {
        val bmp = preview
        if (bmp != null) {
          Box(
            modifier = Modifier
              .fillMaxWidth()
              .aspectRatio(bmp.width.toFloat() / bmp.height.toFloat())
          ) {
            Image(
              bitmap = bmp.asImageBitmap(),
              contentDescription = null,
              contentScale = ContentScale.FillBounds,
              modifier = Modifier.fillMaxSize()
            )
            Canvas(modifier = Modifier.fillMaxSize()) {
              val w = size.width
              val h = size.height
              val dim = Color(0x99000000)
              drawRect(dim, Offset(0f, 0f), Size(w, top * h))
              drawRect(dim, Offset(0f, h - bottom * h), Size(w, bottom * h))
              drawRect(dim, Offset(0f, top * h), Size(left * w, h - (top + bottom) * h))
              drawRect(dim, Offset(w - right * w, top * h), Size(right * w, h - (top + bottom) * h))
            }
          }
        }
        CropSlider(stringResource(R.string.tool21_crop_left), left) { left = it }
        CropSlider(stringResource(R.string.tool21_crop_right), right) { right = it }
        CropSlider(stringResource(R.string.tool21_crop_top), top) { top = it }
        CropSlider(stringResource(R.string.tool21_crop_bottom), bottom) { bottom = it }
      }
    },
    confirmButton = {
      Button(
        enabled = preview != null && !busy && (left + right + top + bottom) > 0.001f,
        onClick = {
          busy = true
          scope.launch {
            val newPath = withContext(Dispatchers.IO) {
              try {
                val src = decodeForCrop(image.imagePath, 2400) ?: return@withContext null
                val x = (left * src.width).toInt().coerceIn(0, src.width - 1)
                val y = (top * src.height).toInt().coerceIn(0, src.height - 1)
                val w = ((1f - left - right) * src.width).toInt().coerceAtLeast(1)
                val h = ((1f - top - bottom) * src.height).toInt().coerceAtLeast(1)
                val cropped = Bitmap.createBitmap(src, x, y, min(w, src.width - x), min(h, src.height - y))
                val out = File(FileLayer.getTempFolder(context), "stamp_${UUID.randomUUID()}.png")
                FileOutputStream(out).use { cropped.compress(Bitmap.CompressFormat.PNG, 100, it) }
                out.absolutePath
              } catch (_: Throwable) {
                null
              }
            }
            busy = false
            if (newPath != null) {
              val b = image.bounds
              val nb = RectF(
                b.left + b.width() * left,
                b.top + b.height() * top,
                b.left + b.width() * (1f - right),
                b.top + b.height() * (1f - bottom)
              )
              onCropped(image.copy(bounds = nb, imagePath = newPath))
            } else {
              onDismiss()
            }
          }
        }
      ) {
        Text(stringResource(R.string.tool21_crop_apply))
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss, enabled = !busy) { Text(stringResource(R.string.common_cancel)) }
    }
  )
}

@Composable
private fun CropSlider(label: String, value: Float, onChange: (Float) -> Unit) {
  Row(verticalAlignment = Alignment.CenterVertically) {
    Text(label, style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(64.dp))
    Slider(
      value = value,
      onValueChange = onChange,
      valueRange = 0f..0.45f,
      modifier = Modifier.weight(1f)
    )
  }
}
