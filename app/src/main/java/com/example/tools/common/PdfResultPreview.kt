package com.example.tools.common

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.util.LruCache
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PictureAsPdf
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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.R
import com.example.file.FileLayer
import com.example.pdf.PdfFileHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun PdfResultPreview(
  file: File,
  modifier: Modifier = Modifier,
  previewHeight: Dp = 220.dp
) {
  val context = LocalContext.current
  var thumbnail by remember(file) { mutableStateOf<Bitmap?>(null) }
  var pageCount by remember(file) { mutableIntStateOf(0) }
  var isLoading by remember(file) { mutableStateOf(true) }
  var showViewer by remember { mutableStateOf(false) }

  LaunchedEffect(file) {
    isLoading = true
    withContext(Dispatchers.IO) {
      if (file.exists() && file.length() > 0L) {
        val count = try {
          PdfFileHelper.pdfRendererMutex.withLock {
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
              PdfRenderer(pfd).use { it.pageCount }
            }
          }
        } catch (_: Exception) { 0 }
        pageCount = count

        val thumb = PdfFileHelper.renderPageThumbnail(context, file, 0, 700)
        thumbnail = thumb
      }
      isLoading = false
    }
  }

  Surface(
    modifier = modifier
      .fillMaxWidth()
      .height(previewHeight)
      .clip(RoundedCornerShape(12.dp))
      .clickable(enabled = file.exists()) { showViewer = true }
      .testTag("pdf_result_preview"),
    shape = RoundedCornerShape(12.dp),
    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
    tonalElevation = 2.dp
  ) {
    Box(
      modifier = Modifier.fillMaxSize(),
      contentAlignment = Alignment.Center
    ) {
      val bmp = thumbnail
      if (bmp != null && !bmp.isRecycled) {
        Image(
          bitmap = bmp.asImageBitmap(),
          contentDescription = "PDF Page 1 Preview",
          modifier = Modifier.fillMaxSize(),
          contentScale = ContentScale.Fit
        )
      } else if (isLoading) {
        CircularProgressIndicator(modifier = Modifier.size(32.dp), strokeWidth = 3.dp)
      } else {
        Column(
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.Center
        ) {
          Icon(
            imageVector = Icons.Default.PictureAsPdf,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.primary
          )
          Spacer(modifier = Modifier.height(4.dp))
          Text(
            text = file.name,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
          )
        }
      }

      // Bottom overlay bar with page count and "Tap to open" hint
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .align(Alignment.BottomCenter)
          .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.65f))
          .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        val countText = if (pageCount <= 1) {
          stringResource(R.string.common_pages_count_one)
        } else {
          stringResource(R.string.common_pages_count_other, pageCount)
        }
        Text(
          text = countText,
          style = MaterialTheme.typography.labelSmall,
          color = androidx.compose.ui.graphics.Color.White,
          fontWeight = FontWeight.SemiBold
        )
        Text(
          text = stringResource(R.string.common_tap_to_open),
          style = MaterialTheme.typography.labelSmall,
          color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.85f)
        )
      }
    }
  }

  if (showViewer && file.exists()) {
    PdfViewerDialog(
      file = file,
      title = file.name,
      onDismiss = { showViewer = false }
    )
  }
}

@Composable
fun PdfViewerDialog(
  file: File,
  title: String,
  onDismiss: () -> Unit
) {
  val context = LocalContext.current
  var pageCount by remember(file) { mutableIntStateOf(0) }
  val listState = rememberLazyListState()

  // Track page indices
  val firstVisibleItem by remember { derivedStateOf { listState.firstVisibleItemIndex } }

  // LRU cache for rendered full pages
  val pageCache = remember(file) {
    object : LruCache<Int, Bitmap>(10) {
      override fun entryRemoved(evicted: Boolean, key: Int?, oldValue: Bitmap?, newValue: Bitmap?) {
        // Do not immediately recycle so active views don't flicker
      }
    }
  }

  DisposableEffect(file) {
    onDispose {
      pageCache.evictAll()
    }
  }

  LaunchedEffect(file) {
    withContext(Dispatchers.IO) {
      val count = try {
        PdfFileHelper.pdfRendererMutex.withLock {
          ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
            PdfRenderer(pfd).use { it.pageCount }
          }
        }
      } catch (_: Exception) { 0 }
      pageCount = count
    }
  }

  Dialog(
    onDismissRequest = onDismiss,
    properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnBackPress = true)
  ) {
    BoxWithConstraints(
      modifier = Modifier
        .fillMaxSize()
        .background(androidx.compose.ui.graphics.Color(0xFF121212))
    ) {
      val density = LocalDensity.current
      val screenWidthPx = with(density) { maxWidth.toPx() }.toInt().coerceAtLeast(100)

      var zoom by remember { mutableFloatStateOf(1f) }
      var panX by remember { mutableFloatStateOf(0f) }
      var panY by remember { mutableFloatStateOf(0f) }

      Column(modifier = Modifier.fillMaxSize()) {
        // Top action bar
        Surface(
          modifier = Modifier.fillMaxWidth(),
          color = androidx.compose.ui.graphics.Color(0xFF1E1E1E),
          tonalElevation = 4.dp
        ) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            IconButton(onClick = onDismiss, modifier = Modifier.testTag("pdf_viewer_close")) {
              Icon(
                imageVector = Icons.Default.Close,
                contentDescription = stringResource(R.string.common_close),
                tint = androidx.compose.ui.graphics.Color.White
              )
            }

            Column(modifier = Modifier.weight(1f).padding(horizontal = 8.dp)) {
              Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = androidx.compose.ui.graphics.Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
              )
              if (pageCount > 0) {
                Text(
                  text = "${firstVisibleItem + 1} / $pageCount",
                  style = MaterialTheme.typography.labelSmall,
                  color = androidx.compose.ui.graphics.Color.LightGray
                )
              }
            }

            IconButton(
              onClick = { FileLayer.shareFileAndNotify(context, file, "application/pdf") }
            ) {
              Icon(
                imageVector = Icons.Default.Share,
                contentDescription = stringResource(R.string.common_share),
                tint = androidx.compose.ui.graphics.Color.White
              )
            }

            IconButton(
              onClick = { FileLayer.openFileAndNotify(context, file, "application/pdf") }
            ) {
              Icon(
                imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                contentDescription = stringResource(R.string.common_open),
                tint = androidx.compose.ui.graphics.Color.White
              )
            }
          }
        }

        // Gesture-enabled content area
        Box(
          modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
              awaitEachGesture {
                var isFirstTouch = true
                var lastCentroid = Offset.Zero
                do {
                  val event = awaitPointerEvent()
                  val pointerCount = event.changes.size

                  if (pointerCount >= 2) {
                    val zoomChange = event.calculateZoom()
                    val panChange = event.calculatePan()
                    zoom = (zoom * zoomChange).coerceIn(1f, 5f)
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
            }
        ) {
          if (pageCount > 0) {
            LazyColumn(
              state = listState,
              modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
              verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
              items(count = pageCount, key = { it }) { pageIdx ->
                PdfPageView(
                  file = file,
                  pageIndex = pageIdx,
                  targetWidthPx = screenWidthPx,
                  cache = pageCache
                )
              }
            }
          } else {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
              CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
          }
        }
      }
    }
  }
}

@Composable
private fun PdfPageView(
  file: File,
  pageIndex: Int,
  targetWidthPx: Int,
  cache: LruCache<Int, Bitmap>
) {
  var pageBitmap by remember(file, pageIndex) { mutableStateOf(cache.get(pageIndex)) }
  var isLoading by remember(file, pageIndex) { mutableStateOf(pageBitmap == null) }

  LaunchedEffect(file, pageIndex, targetWidthPx) {
    val cached = cache.get(pageIndex)
    if (cached != null && !cached.isRecycled) {
      pageBitmap = cached
      isLoading = false
    } else {
      isLoading = true
      withContext(Dispatchers.IO) {
        val bmp = renderPageAtWidth(file, pageIndex, targetWidthPx)
        if (bmp != null) {
          cache.put(pageIndex, bmp)
          pageBitmap = bmp
        }
        isLoading = false
      }
    }
  }

  Surface(
    modifier = Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(6.dp)),
    color = androidx.compose.ui.graphics.Color.White,
    tonalElevation = 2.dp
  ) {
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .aspectRatio(0.707f), // standard A4 aspect ratio approximation until loaded
      contentAlignment = Alignment.Center
    ) {
      val bmp = pageBitmap
      if (bmp != null && !bmp.isRecycled) {
        Image(
          bitmap = bmp.asImageBitmap(),
          contentDescription = "Page ${pageIndex + 1}",
          modifier = Modifier.fillMaxWidth(),
          contentScale = ContentScale.FillWidth
        )
      } else {
        CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 2.dp)
      }
    }
  }
}

private suspend fun renderPageAtWidth(file: File, pageIndex: Int, targetWidth: Int): Bitmap? {
  return withContext(Dispatchers.IO) {
    PdfFileHelper.pdfRendererMutex.withLock {
      var pfd: ParcelFileDescriptor? = null
      var renderer: PdfRenderer? = null
      var page: PdfRenderer.Page? = null
      try {
        pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        renderer = PdfRenderer(pfd)
        if (pageIndex < 0 || pageIndex >= renderer.pageCount) return@withLock null
        page = renderer.openPage(pageIndex)

        val srcW = page.width
        val srcH = page.height
        val scale = targetWidth.toFloat() / srcW.toFloat()
        val destW = targetWidth.coerceIn(100, 2048)
        val destH = ((srcH * scale).toInt()).coerceIn(100, 4096)

        val bitmap = Bitmap.createBitmap(destW, destH, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.WHITE)
        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
        bitmap
      } catch (_: Exception) {
        null
      } finally {
        try { page?.close() } catch (_: Exception) {}
        try { renderer?.close() } catch (_: Exception) {}
        try { pfd?.close() } catch (_: Exception) {}
      }
    }
  }
}
