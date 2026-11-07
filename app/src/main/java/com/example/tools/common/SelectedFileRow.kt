package com.example.tools.common

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.file.FileFormatters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class SelectedFileItem(
  val uri: Uri,
  val name: String,
  val sizeBytes: Long,
  val width: Int? = null,
  val height: Int? = null,
  val durationMs: Long? = null
)

val SelectedFileListSaver: Saver<SnapshotStateList<SelectedFileItem>, List<List<Any>>> = Saver(
  save = { stateList ->
    stateList.map { item ->
      listOf(
        item.uri.toString(),
        item.name,
        item.sizeBytes,
        item.width ?: -1,
        item.height ?: -1,
        item.durationMs ?: -1L
      )
    }
  },
  restore = { savedList ->
    val restored = mutableStateListOf<SelectedFileItem>()
    for (it in savedList) {
      restored.add(
        SelectedFileItem(
          uri = Uri.parse(it[0] as String),
          name = it[1] as String,
          sizeBytes = it[2] as Long,
          width = (it[3] as Int).takeIf { w -> w != -1 },
          height = (it[4] as Int).takeIf { h -> h != -1 },
          durationMs = (it[5] as Long).takeIf { d -> d != -1L }
        )
      )
    }
    restored
  }
)

@Composable
fun SelectedFileRow(
  fileItem: SelectedFileItem,
  onRemove: () -> Unit,
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current
  val uriKey = fileItem.uri.toString()

  val previewBitmap by androidx.compose.runtime.produceState<Bitmap?>(
    initialValue = ImageThumbnailCache.get(uriKey),
    key1 = fileItem.uri
  ) {
    val cached = ImageThumbnailCache.get(uriKey)
    if (cached != null && !cached.isRecycled) {
      value = cached
      return@produceState
    }

    val loaded = withContext(Dispatchers.IO) {
      try {
        val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(fileItem.uri)?.use { stream ->
          BitmapFactory.decodeStream(stream, null, boundsOptions)
        }
        val rawW = boundsOptions.outWidth
        val rawH = boundsOptions.outHeight
        val sample = if (rawW > 0 && rawH > 0) {
          com.example.tools.crop.CropMath.calculateInSampleSize(rawW, rawH, 96)
        } else {
          4
        }

        val decodeOptions = BitmapFactory.Options().apply {
          inSampleSize = sample
          inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        context.contentResolver.openInputStream(fileItem.uri)?.use { stream ->
          BitmapFactory.decodeStream(stream, null, decodeOptions)
        }
      } catch (_: Throwable) {
        null
      }
    }

    if (loaded != null) {
      ImageThumbnailCache.put(uriKey, loaded)
      value = loaded
    }
  }

  Card(
    modifier = modifier.fillMaxWidth(),
    shape = RoundedCornerShape(12.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 14.dp, vertical = 10.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
      val bmp = previewBitmap
      if (bmp != null) {
        Image(
          bitmap = bmp.asImageBitmap(),
          contentDescription = fileItem.name,
          contentScale = ContentScale.Crop,
          modifier = Modifier
            .size(44.dp)
            .clip(RoundedCornerShape(8.dp))
        )
      } else {
        Icon(
          imageVector = Icons.Default.Image,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.primary,
          modifier = Modifier.size(28.dp)
        )
      }

      Column(
        modifier = Modifier.weight(1f),
        verticalArrangement = Arrangement.spacedBy(2.dp)
      ) {
        Text(
          text = fileItem.name,
          style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
          color = MaterialTheme.colorScheme.onSurface,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis
        )

        val details = buildString {
          append(FileFormatters.formatBytes(fileItem.sizeBytes))
          if (fileItem.width != null && fileItem.height != null && fileItem.width > 0 && fileItem.height > 0) {
            append(" • ${fileItem.width}×${fileItem.height}")
          }
        }

        Text(
          text = details,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }

      IconButton(
        onClick = onRemove,
        modifier = Modifier
          .size(36.dp)
          .testTag("remove_file_button")
      ) {
        Icon(
          imageVector = Icons.Default.Close,
          contentDescription = stringResource(R.string.common_remove_file),
          tint = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.size(18.dp)
        )
      }
    }
  }
}
