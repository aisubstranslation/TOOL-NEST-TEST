package com.example.tools.common

import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.foundation.clickable
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.example.file.FileLayer
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ResultCard(
  fileResult: FileResult,
  onProcessAnother: () -> Unit,
  onDone: () -> Unit,
  modifier: Modifier = Modifier,
  showTransparencyBackdrop: Boolean = false,
  previewHeight: androidx.compose.ui.unit.Dp = if (showTransparencyBackdrop) 300.dp else 180.dp
) {
  val context = LocalContext.current
  val checkerBrush = rememberCheckerBrush()
  val localFile = remember(fileResult.tempFilePath) {
    fileResult.tempFilePath?.let { File(it) }
  }

  val fileKey = run {
    val basePath = fileResult.tempFilePath ?: fileResult.outputUri ?: ""
    // Same output name can be re-exported with different edits: include the file time so the
    // preview never shows a stale cached picture.
    if (localFile != null && localFile.exists()) "$basePath#${localFile.lastModified()}" else basePath
  }
  val previewBitmap by androidx.compose.runtime.produceState<android.graphics.Bitmap?>(
    initialValue = if (fileKey.isNotEmpty()) ImageThumbnailCache.get(fileKey) else null,
    key1 = fileKey
  ) {
    if (fileKey.isEmpty() || localFile == null || !localFile.exists()) return@produceState
    val cached = ImageThumbnailCache.get(fileKey)
    if (cached != null && !cached.isRecycled) {
      value = cached
      return@produceState
    }

    val loaded = withContext(Dispatchers.IO) {
      try {
        val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(localFile.absolutePath, boundsOptions)
        val rawW = boundsOptions.outWidth
        val rawH = boundsOptions.outHeight
        val sample = if (rawW > 0 && rawH > 0) {
          com.example.tools.crop.CropMath.calculateInSampleSize(rawW, rawH, 800)
        } else {
          2
        }

        val decodeOptions = BitmapFactory.Options().apply {
          inSampleSize = sample
          inPreferredConfig = android.graphics.Bitmap.Config.ARGB_8888
        }
        BitmapFactory.decodeFile(localFile.absolutePath, decodeOptions)
      } catch (_: Throwable) {
        null
      }
    }

    if (loaded != null) {
      ImageThumbnailCache.put(fileKey, loaded)
      value = loaded
    }
  }

  val mimeType = when (fileResult.format?.lowercase()) {
    "png" -> "image/png"
    "webp" -> "image/webp"
    else -> "image/jpeg"
  }

  // SAF CreateDocument launcher for Save
  val saveSafLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.CreateDocument(mimeType)
  ) { targetUri: Uri? ->
    if (targetUri != null && localFile != null) {
      val success = FileLayer.saveFileToUri(context, localFile, targetUri)
      if (success) {
        Toast.makeText(context, context.getString(R.string.file_saved_success), Toast.LENGTH_SHORT).show()
      } else {
        Toast.makeText(context, context.getString(R.string.error_generic), Toast.LENGTH_SHORT).show()
      }
    }
  }

  Card(
    modifier = modifier.fillMaxWidth(),
    shape = RoundedCornerShape(16.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(18.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
      // Header: Success Icon + Name
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
      ) {
        Icon(
          imageVector = Icons.Default.CheckCircle,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.primary,
          modifier = Modifier.size(28.dp)
        )
        Text(
          text = fileResult.outputName ?: fileResult.inputName,
          style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
          color = MaterialTheme.colorScheme.onSurface,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis
        )
      }

      var showZoomDialog by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }

      // Thumbnail preview if available
      val bmp = previewBitmap
      if (bmp != null) {
        Column(
          modifier = Modifier.fillMaxWidth(),
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
          Box(
            modifier = Modifier
              .fillMaxWidth()
              .height(previewHeight)
              .clip(RoundedCornerShape(12.dp))
              .then(if (showTransparencyBackdrop) Modifier.background(checkerBrush) else Modifier)
              .testTag("result_preview")
              .clickable(enabled = localFile != null && localFile.exists()) {
                showZoomDialog = true
              },
            contentAlignment = Alignment.Center
          ) {
            Image(
              bitmap = bmp.asImageBitmap(),
              contentDescription = "Output image preview",
              modifier = Modifier.fillMaxWidth(),
              contentScale = ContentScale.Fit
            )
          }

          Text(
            text = stringResource(R.string.common_tap_to_view),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
      }

      if (showZoomDialog && localFile != null && localFile.exists()) {
        ZoomableImageDialog(
          file = localFile,
          title = fileResult.outputName ?: fileResult.inputName,
          onDismiss = { showZoomDialog = false }
        )
      }

      // Output details grid
      Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = Modifier.fillMaxWidth()
      ) {
        Column(
          modifier = Modifier.padding(14.dp),
          verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
          // Size comparison with safe percentage
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
          ) {
            Text(
              text = "${stringResource(R.string.history_output_size)}:",
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            val sizeText = buildString {
              append(FileFormatters.formatBytes(fileResult.outputSizeBytes))
              if (fileResult.originalSizeBytes > 0L) {
                append(" (")
                append(FileFormatters.formatPercentageChange(fileResult.originalSizeBytes, fileResult.outputSizeBytes))
                append(")")
              }
            }
            Text(
              text = sizeText,
              style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
              color = MaterialTheme.colorScheme.primary
            )
          }

          // Dimensions
          if (fileResult.width != null && fileResult.height != null && fileResult.width > 0 && fileResult.height > 0) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween
            ) {
              Text(
                text = "Dimensions:",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
              Text(
                text = "${fileResult.width} × ${fileResult.height} px",
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
              )
            }
          }

          // Format
          if (!fileResult.format.isNullOrBlank()) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween
            ) {
              Text(
                text = "Format:",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
              Text(
                text = fileResult.format.uppercase(),
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
              )
            }
          }
        }
      }

      // MediaStore notice for API 26-28
      if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
          Icon(
            imageVector = Icons.Default.Info,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp)
          )
          Text(
            text = stringResource(R.string.common_gallery_api_notice),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
      }

      // Action Buttons
      FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        // Save via SAF
        Button(
          onClick = {
            val defaultName = fileResult.outputName ?: "image.${fileResult.format?.lowercase() ?: "jpg"}"
            saveSafLauncher.launch(defaultName)
          },
          shape = RoundedCornerShape(10.dp),
          modifier = Modifier.testTag("action_save")
        ) {
          Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(18.dp))
          Spacer(Modifier.size(6.dp))
          Text(stringResource(R.string.common_save))
        }

        // Save to Gallery (API 29+ only)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && localFile != null) {
          FilledTonalButton(
            onClick = {
              val name = fileResult.outputName ?: "image_${System.currentTimeMillis()}.${fileResult.format?.lowercase() ?: "jpg"}"
              val savedUri = FileLayer.saveImageToMediaStore(context, localFile, name, mimeType)
              if (savedUri != null) {
                Toast.makeText(context, context.getString(R.string.common_saved_to_gallery), Toast.LENGTH_SHORT).show()
              } else {
                Toast.makeText(context, context.getString(R.string.error_generic), Toast.LENGTH_SHORT).show()
              }
            },
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.testTag("action_save_gallery")
          ) {
            Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.size(6.dp))
            Text(stringResource(R.string.common_save_gallery))
          }
        }

        // Share
        if (localFile != null) {
          OutlinedButton(
            onClick = {
              FileLayer.shareFileAndNotify(context, localFile, mimeType)
            },
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.testTag("action_share")
          ) {
            Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.size(6.dp))
            Text(stringResource(R.string.common_share))
          }
        }

        // Open
        if (localFile != null) {
          OutlinedButton(
            onClick = {
              FileLayer.openFileAndNotify(context, localFile, mimeType)
            },
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.testTag("action_open")
          ) {
            Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.size(6.dp))
            Text(stringResource(R.string.common_open))
          }
        }

        // Convert again / Process another
        OutlinedButton(
          onClick = onProcessAnother,
          shape = RoundedCornerShape(10.dp),
          modifier = Modifier.testTag("action_process_again")
        ) {
          Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
          Spacer(Modifier.size(6.dp))
          Text(stringResource(R.string.common_process_again))
        }

        // Done
        FilledTonalButton(
          onClick = onDone,
          shape = RoundedCornerShape(10.dp),
          colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
          ),
          modifier = Modifier.testTag("action_done")
        ) {
          Text(stringResource(R.string.common_done))
        }
      }
    }
  }
}
