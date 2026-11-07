package com.example.tools.common

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.R
import com.example.file.FileLayer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.max

@Composable
fun ZoomableImageDialog(
  file: File,
  title: String,
  onDismiss: () -> Unit
) {
  val context = LocalContext.current
  var bitmap by remember(file) { mutableStateOf<Bitmap?>(null) }
  var isLoading by remember(file) { mutableStateOf(true) }
  var isError by remember(file) { mutableStateOf(false) }

  LaunchedEffect(file) {
    isLoading = true
    isError = false
    withContext(Dispatchers.IO) {
      try {
        if (!file.exists() || file.length() == 0L) {
          isError = true
          isLoading = false
          return@withContext
        }
        val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, boundsOpts)
        val origW = boundsOpts.outWidth
        val origH = boundsOpts.outHeight
        val maxEdge = max(origW, origH)

        var sampleSize = 1
        while ((maxEdge / sampleSize) > 2048) {
          sampleSize *= 2
        }

        val decodeOpts = BitmapFactory.Options().apply {
          inSampleSize = sampleSize
          inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val decoded = BitmapFactory.decodeFile(file.absolutePath, decodeOpts)
        if (decoded != null) {
          bitmap = decoded
        } else {
          isError = true
        }
      } catch (_: OutOfMemoryError) {
        isError = true
      } catch (_: Exception) {
        isError = true
      } finally {
        isLoading = false
      }
    }
  }

  DisposableEffect(file) {
    onDispose {
      bitmap?.recycle()
      bitmap = null
    }
  }

  Dialog(
    onDismissRequest = onDismiss,
    properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnBackPress = true)
  ) {
    BoxWithConstraints(
      modifier = Modifier
        .fillMaxSize()
        .background(Color.Black)
    ) {
      val checkerBrush = rememberCheckerBrush()
      var zoom by remember { mutableFloatStateOf(1f) }
      var panX by remember { mutableFloatStateOf(0f) }
      var panY by remember { mutableFloatStateOf(0f) }

      Column(modifier = Modifier.fillMaxSize()) {
        // Action bar
        Surface(
          modifier = Modifier.fillMaxWidth(),
          color = Color(0xFF1E1E1E),
          tonalElevation = 4.dp
        ) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            IconButton(onClick = onDismiss, modifier = Modifier.testTag("image_viewer_close")) {
              Icon(
                imageVector = Icons.Default.Close,
                contentDescription = stringResource(R.string.common_close),
                tint = Color.White
              )
            }

            Text(
              text = title,
              style = MaterialTheme.typography.titleSmall,
              color = Color.White,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
              modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
            )

            IconButton(
              onClick = { FileLayer.shareFileAndNotify(context, file, "image/*") }
            ) {
              Icon(
                imageVector = Icons.Default.Share,
                contentDescription = stringResource(R.string.common_share),
                tint = Color.White
              )
            }

            IconButton(
              onClick = { FileLayer.openFileAndNotify(context, file, "image/*") }
            ) {
              Icon(
                imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                contentDescription = stringResource(R.string.common_open),
                tint = Color.White
              )
            }
          }
        }

        // Image container with gesture detection
        Box(
          modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
              awaitEachGesture {
                var lastTapTime = 0L
                do {
                  val event = awaitPointerEvent()
                  val pointerCount = event.changes.size

                  if (pointerCount >= 2) {
                    val zoomChange = event.calculateZoom()
                    val panChange = event.calculatePan()
                    zoom = (zoom * zoomChange).coerceIn(1f, 8f)
                    if (zoom > 1f) {
                      panX += panChange.x
                      panY += panChange.y
                    } else {
                      panX = 0f
                      panY = 0f
                    }
                    event.changes.forEach { it.consume() }
                  } else if (pointerCount == 1 && zoom > 1f) {
                    val panChange = event.calculatePan()
                    panX += panChange.x
                    panY += panChange.y
                    event.changes.forEach { it.consume() }
                  }
                } while (event.changes.any { it.pressed })
              }
            }
            .graphicsLayer {
              scaleX = zoom
              scaleY = zoom
              translationX = panX
              translationY = panY
            },
          contentAlignment = Alignment.Center
        ) {
          val bmp = bitmap
          if (bmp != null && !bmp.isRecycled) {
            Box(
              modifier = Modifier
                .background(checkerBrush)
            ) {
              Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit
              )
            }
          } else if (isLoading) {
            CircularProgressIndicator(modifier = Modifier.size(36.dp), color = MaterialTheme.colorScheme.primary)
          } else {
            Text(
              text = stringResource(R.string.common_failed_load_image),
              style = MaterialTheme.typography.bodyMedium,
              color = Color.White
            )
          }
        }
      }
    }
  }
}
