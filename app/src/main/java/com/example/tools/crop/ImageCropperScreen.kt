package com.example.tools.crop

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.engine.ImageCroppingHelper
import com.example.engine.ImageEngine
import com.example.engine.OutputFormat
import com.example.file.FileLayer
import com.example.tools.common.ErrorCard
import com.example.tools.common.FilePickerCard
import com.example.tools.common.JobResult
import com.example.tools.common.JobState
import com.example.tools.common.ProgressCard
import com.example.tools.common.ResultCard
import com.example.tools.common.ToolJobRunner
import com.example.tools.common.ToolScaffold
import java.io.File
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.withContext

private data class PreviewCacheEntry(
  val uriString: String,
  val bitmap: Bitmap,
  val orientedWidth: Int,
  val orientedHeight: Int,
  val rawWidth: Int,
  val rawHeight: Int,
  val orientationDegrees: Int,
  val isFlipped: Boolean,
  val fileName: String,
  val fileSize: Long
)

// Single-entry cache across device rotations
private var globalPreviewCache: PreviewCacheEntry? = null

private class CropperStateHolder(
  val cropRect: CropRect,
  val aspectRatio: CropAspectRatio,
  val viewTransform: ViewTransform,
  val hitRadiusPx: Float,
  val orientedWidth: Int,
  val orientedHeight: Int,
  val onHandleDrag: (CropHandle, Offset) -> Unit,
  val onPinchZoomPan: (Float, Offset) -> Unit,
  val onGestureComplete: (CropStateSnapshot) -> Unit
)

@Composable
fun ImageCropperScreen(
  onBack: () -> Unit
) {
  val context = LocalContext.current
  val density = LocalDensity.current

  var selectedUri by rememberSaveable { mutableStateOf<Uri?>(null) }
  var imageFileName by rememberSaveable { mutableStateOf("") }
  var imageFileSize by rememberSaveable { mutableStateOf(0L) }

  var previewBitmap by remember { mutableStateOf<Bitmap?>(null) }
  var orientedWidth by rememberSaveable { mutableIntStateOf(0) }
  var orientedHeight by rememberSaveable { mutableIntStateOf(0) }

  var isLoadingPreview by remember { mutableStateOf(false) }
  var previewError by remember { mutableStateOf<String?>(null) }

  // Crop box in oriented source-image pixel coordinates (preserved across rotation)
  var cropLeft by rememberSaveable { mutableFloatStateOf(-1f) }
  var cropTop by rememberSaveable { mutableFloatStateOf(-1f) }
  var cropRight by rememberSaveable { mutableFloatStateOf(-1f) }
  var cropBottom by rememberSaveable { mutableFloatStateOf(-1f) }

  var selectedAspect by rememberSaveable { mutableStateOf(CropAspectRatio.FREE) }
  val cropHistory = remember { CropHistory(maxCapacity = 30) }
  var hasUnsavedChanges by rememberSaveable { mutableStateOf(false) }
  var showDiscardDialog by remember { mutableStateOf(false) }

  var outputFormat by rememberSaveable { mutableStateOf(OutputFormat.JPEG) }
  var quality by rememberSaveable { mutableIntStateOf(90) }

  // Real Zoom and Pan in ViewTransform
  var imageZoom by rememberSaveable { mutableFloatStateOf(1f) }
  var imagePanX by rememberSaveable { mutableFloatStateOf(0f) }
  var imagePanY by rememberSaveable { mutableFloatStateOf(0f) }

  // ToolJobRunner state
  var currentJobId by rememberSaveable { mutableStateOf<String?>(null) }
  val activeJobUi by remember(currentJobId) {
    val id = currentJobId
    if (id != null) {
      ToolJobRunner.observe(id)
    } else {
      flowOf(null)
    }
  }.collectAsState(initial = null)

  LaunchedEffect(currentJobId) {
    val id = currentJobId
    if (id != null && !ToolJobRunner.hasJob(id)) {
      currentJobId = null
      previewError = context.getString(R.string.common_job_restored_lost)
    }
  }

  val canUndo = cropHistory.canUndo
  val canRedo = cropHistory.canRedo

  fun requestBack() {
    if (hasUnsavedChanges && selectedUri != null && activeJobUi?.state != JobState.SUCCESS) {
      showDiscardDialog = true
    } else {
      onBack()
    }
  }

  BackHandler(enabled = true) {
    requestBack()
  }

  // Load image preview off the main thread with Dispatchers.IO
  LaunchedEffect(selectedUri) {
    val uri = selectedUri ?: run {
      previewBitmap = null
      previewError = null
      isLoadingPreview = false
      return@LaunchedEffect
    }

    val uriStr = uri.toString()
    val cached = globalPreviewCache
    if (cached != null && cached.uriString == uriStr && !cached.bitmap.isRecycled) {
      previewBitmap = cached.bitmap
      orientedWidth = cached.orientedWidth
      orientedHeight = cached.orientedHeight
      imageFileName = cached.fileName
      imageFileSize = cached.fileSize
      isLoadingPreview = false
      previewError = null
      if (cropLeft < 0f) {
        val initial = CropMath.clampCrop(
          CropRect(0f, 0f, cached.orientedWidth.toFloat(), cached.orientedHeight.toFloat()),
          cached.orientedWidth.toFloat(),
          cached.orientedHeight.toFloat()
        )
        cropLeft = initial.left
        cropTop = initial.top
        cropRight = initial.right
        cropBottom = initial.bottom
        cropHistory.reset(CropStateSnapshot(initial, selectedAspect))
      }
      return@LaunchedEffect
    }

    isLoadingPreview = true
    previewError = null

    try {
      val entry = withContext(Dispatchers.IO) {
        val fileInfo = FileLayer.getUriFileInfo(context, uri)
        val meta = ImageEngine.readImageMetadata(context, uri)
        val sample = CropMath.calculateInSampleSize(meta.width, meta.height, 1600)

        val options = BitmapFactory.Options().apply {
          inSampleSize = sample
          inPreferredConfig = Bitmap.Config.ARGB_8888
          inMutable = false
        }

        val decoded = context.contentResolver.openInputStream(uri)?.use { stream ->
          BitmapFactory.decodeStream(stream, null, options)
        } ?: throw IllegalArgumentException("Could not decode image from URI")

        val oriented = if (meta.orientationDegrees != 0 || meta.isFlipped) {
          val matrix = Matrix()
          if (meta.isFlipped) {
            matrix.postScale(-1f, 1f)
          }
          if (meta.orientationDegrees != 0) {
            matrix.postRotate(meta.orientationDegrees.toFloat())
          }
          val transformed = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
          if (transformed != decoded) {
            decoded.recycle()
          }
          transformed
        } else {
          decoded
        }

        val orientedDimensions = CropMath.orientedSize(meta.width, meta.height, meta.orientationDegrees)

        PreviewCacheEntry(
          uriString = uriStr,
          bitmap = oriented,
          orientedWidth = orientedDimensions.width,
          orientedHeight = orientedDimensions.height,
          rawWidth = meta.width,
          rawHeight = meta.height,
          orientationDegrees = meta.orientationDegrees,
          isFlipped = meta.isFlipped,
          fileName = fileInfo.displayName,
          fileSize = fileInfo.sizeBytes
        )
      }

      if (globalPreviewCache != null && globalPreviewCache?.uriString != uriStr) {
        globalPreviewCache?.bitmap?.recycle()
      }
      globalPreviewCache = entry

      previewBitmap = entry.bitmap
      orientedWidth = entry.orientedWidth
      orientedHeight = entry.orientedHeight
      imageFileName = entry.fileName
      imageFileSize = entry.fileSize
      isLoadingPreview = false

      if (cropLeft < 0f) {
        val initial = CropMath.clampCrop(
          CropRect(0f, 0f, entry.orientedWidth.toFloat(), entry.orientedHeight.toFloat()),
          entry.orientedWidth.toFloat(),
          entry.orientedHeight.toFloat()
        )
        cropLeft = initial.left
        cropTop = initial.top
        cropRight = initial.right
        cropBottom = initial.bottom
        cropHistory.reset(CropStateSnapshot(initial, selectedAspect))
      }
    } catch (e: Exception) {
      if (e !is CancellationException) {
        previewError = e.message ?: "Failed to decode image preview"
        isLoadingPreview = false
      }
    }
  }

  ToolScaffold(
    title = stringResource(R.string.tool4_name),
    onBack = { requestBack() }
  ) { paddingValues ->
    LazyColumn(
      modifier = Modifier
        .fillMaxSize()
        .padding(paddingValues),
      contentPadding = PaddingValues(16.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
      // Once the crop is finished only the result card is shown (same flow as the other image tools).
      val cropFinished = activeJobUi?.state == JobState.SUCCESS &&
        activeJobUi?.result?.files?.firstOrNull() != null

      if (!cropFinished) {
      // 1. File Picker Card
      if (selectedUri == null) {
        item {
          FilePickerCard(
            title = stringResource(R.string.tool4_select_image),
            supportedFormatsText = stringResource(R.string.tool4_picker_hint),
            maxFiles = 1,
            onFilesSelected = { uris ->
              if (uris.isNotEmpty()) {
                selectedUri = uris.first()
                cropLeft = -1f
                cropTop = -1f
                cropRight = -1f
                cropBottom = -1f
                imageZoom = 1f
                imagePanX = 0f
                imagePanY = 0f
                hasUnsavedChanges = false
              }
            }
          )
        }
      }

      // 2. Loading State
      if (isLoadingPreview) {
        item {
          Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
          ) {
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
              horizontalArrangement = Arrangement.Center,
              verticalAlignment = Alignment.CenterVertically
            ) {
              CircularProgressIndicator(modifier = Modifier.size(28.dp))
              Spacer(modifier = Modifier.size(16.dp))
              Text(
                text = stringResource(R.string.common_processing),
                style = MaterialTheme.typography.bodyMedium
              )
            }
          }
        }
      }

      // 3. Error Card
      previewError?.let { err ->
        item {
          ErrorCard(
            message = err
          )
        }
      }

      // 4. Interactive Cropper Canvas
      val bmp = previewBitmap
      if (bmp != null && !bmp.isRecycled && orientedWidth > 0 && orientedHeight > 0) {
        val currentCrop = CropMath.clampCrop(
          rect = CropRect(cropLeft, cropTop, cropRight, cropBottom),
          imageW = orientedWidth.toFloat(),
          imageH = orientedHeight.toFloat()
        )

        item {
          Card(
            modifier = Modifier
              .fillMaxWidth()
              .testTag("cropper_preview_card"),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            shape = RoundedCornerShape(12.dp)
          ) {
            Column(
              modifier = Modifier.padding(12.dp),
              verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
              // Top Action Row: Undo, Redo, Reset, Dimensions
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
              ) {
                Text(
                  text = stringResource(
                    R.string.tool4_crop_dimensions,
                    currentCrop.width.roundToInt(),
                    currentCrop.height.roundToInt()
                  ),
                  style = MaterialTheme.typography.labelMedium,
                  fontWeight = FontWeight.SemiBold
                )

                Row(
                  horizontalArrangement = Arrangement.spacedBy(4.dp),
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  IconButton(
                    onClick = {
                      val prev = cropHistory.undo()
                      if (prev != null) {
                        cropLeft = prev.cropRect.left
                        cropTop = prev.cropRect.top
                        cropRight = prev.cropRect.right
                        cropBottom = prev.cropRect.bottom
                        selectedAspect = prev.aspectRatio
                        hasUnsavedChanges = true
                      }
                    },
                    enabled = canUndo,
                    modifier = Modifier
                      .size(48.dp)
                      .testTag("cropper_undo_button")
                  ) {
                    Icon(
                      imageVector = Icons.AutoMirrored.Filled.Undo,
                      contentDescription = stringResource(R.string.tool4_undo)
                    )
                  }

                  IconButton(
                    onClick = {
                      val next = cropHistory.redo()
                      if (next != null) {
                        cropLeft = next.cropRect.left
                        cropTop = next.cropRect.top
                        cropRight = next.cropRect.right
                        cropBottom = next.cropRect.bottom
                        selectedAspect = next.aspectRatio
                        hasUnsavedChanges = true
                      }
                    },
                    enabled = canRedo,
                    modifier = Modifier
                      .size(48.dp)
                      .testTag("cropper_redo_button")
                  ) {
                    Icon(
                      imageVector = Icons.AutoMirrored.Filled.Redo,
                      contentDescription = stringResource(R.string.tool4_redo)
                    )
                  }

                  IconButton(
                    onClick = {
                      imageZoom = 1f
                      imagePanX = 0f
                      imagePanY = 0f
                      selectedAspect = CropAspectRatio.FREE
                      val reset = CropMath.clampCrop(
                        CropRect(0f, 0f, orientedWidth.toFloat(), orientedHeight.toFloat()),
                        orientedWidth.toFloat(),
                        orientedHeight.toFloat()
                      )
                      cropLeft = reset.left
                      cropTop = reset.top
                      cropRight = reset.right
                      cropBottom = reset.bottom
                      cropHistory.push(CropStateSnapshot(reset, CropAspectRatio.FREE))
                      hasUnsavedChanges = true
                    },
                    modifier = Modifier
                      .size(48.dp)
                      .testTag("cropper_reset_button")
                  ) {
                    Icon(
                      imageVector = Icons.Filled.Refresh,
                      contentDescription = stringResource(R.string.tool4_reset)
                    )
                  }
                }
              }

              // Canvas viewport
              BoxWithConstraints(
                modifier = Modifier
                  .fillMaxWidth()
                  .aspectRatio(1f)
                  .testTag("cropper_canvas_box")
              ) {
                val canvasW = constraints.maxWidth.toFloat()
                val canvasH = constraints.maxHeight.toFloat()

                val fitRect = CropMath.fitRect(canvasW, canvasH, orientedWidth.toFloat(), orientedHeight.toFloat())
                val transform = ViewTransform(
                  fit = fitRect,
                  zoom = imageZoom,
                  panX = imagePanX,
                  panY = imagePanY,
                  canvasW = canvasW,
                  canvasH = canvasH,
                  imageW = orientedWidth.toFloat(),
                  imageH = orientedHeight.toFloat()
                )

                // Density-aware touch target for handles (24.dp to px)
                val hitRadiusPx = with(density) { 24.dp.toPx() }

                val gestureStateHolder = rememberUpdatedState(
                  CropperStateHolder(
                    cropRect = currentCrop,
                    aspectRatio = selectedAspect,
                    viewTransform = transform,
                    hitRadiusPx = hitRadiusPx,
                    orientedWidth = orientedWidth,
                    orientedHeight = orientedHeight,
                    onHandleDrag = { handle, delta ->
                      val scale = (transform.fit.width / orientedWidth.toFloat()) * transform.safeZoom
                      if (scale > 0f) {
                        val deltaX = delta.x / scale
                        val deltaY = delta.y / scale

                        if (handle == CropHandle.INSIDE) {
                          val curW = currentCrop.width
                          val curH = currentCrop.height
                          var newL = currentCrop.left + deltaX
                          var newT = currentCrop.top + deltaY
                          if (newL < 0f) newL = 0f
                          if (newL + curW > orientedWidth) newL = orientedWidth - curW
                          if (newT < 0f) newT = 0f
                          if (newT + curH > orientedHeight) newT = orientedHeight - curH

                          val clamped = CropMath.clampCrop(
                            CropRect(newL, newT, newL + curW, newT + curH),
                            orientedWidth.toFloat(),
                            orientedHeight.toFloat()
                          )
                          cropLeft = clamped.left
                          cropTop = clamped.top
                          cropRight = clamped.right
                          cropBottom = clamped.bottom
                        } else {
                          val newRect = when (handle) {
                            CropHandle.TOP_LEFT -> CropRect(currentCrop.left + deltaX, currentCrop.top + deltaY, currentCrop.right, currentCrop.bottom)
                            CropHandle.TOP_RIGHT -> CropRect(currentCrop.left, currentCrop.top + deltaY, currentCrop.right + deltaX, currentCrop.bottom)
                            CropHandle.BOTTOM_LEFT -> CropRect(currentCrop.left + deltaX, currentCrop.top, currentCrop.right, currentCrop.bottom + deltaY)
                            CropHandle.BOTTOM_RIGHT -> CropRect(currentCrop.left, currentCrop.top, currentCrop.right + deltaX, currentCrop.bottom + deltaY)
                            CropHandle.LEFT -> CropRect(currentCrop.left + deltaX, currentCrop.top, currentCrop.right, currentCrop.bottom)
                            CropHandle.RIGHT -> CropRect(currentCrop.left, currentCrop.top, currentCrop.right + deltaX, currentCrop.bottom)
                            CropHandle.TOP -> CropRect(currentCrop.left, currentCrop.top + deltaY, currentCrop.right, currentCrop.bottom)
                            CropHandle.BOTTOM -> CropRect(currentCrop.left, currentCrop.top, currentCrop.right, currentCrop.bottom + deltaY)
                            else -> currentCrop
                          }
                          val adjusted = CropMath.applyAspect(
                            rect = newRect,
                            aspectRatio = selectedAspect,
                            handle = handle,
                            imageW = orientedWidth.toFloat(),
                            imageH = orientedHeight.toFloat()
                          )
                          cropLeft = adjusted.left
                          cropTop = adjusted.top
                          cropRight = adjusted.right
                          cropBottom = adjusted.bottom
                        }
                      }
                    },
                    onPinchZoomPan = { zoomDelta, panDelta ->
                      imageZoom = CropMath.safeCoerce(imageZoom * zoomDelta, 1f, 5f)
                      imagePanX += panDelta.x
                      imagePanY += panDelta.y
                    },
                    onGestureComplete = { startSnapshot ->
                      val updatedCrop = CropRect(cropLeft, cropTop, cropRight, cropBottom)
                      if (startSnapshot.cropRect != updatedCrop) {
                        cropHistory.push(CropStateSnapshot(updatedCrop, selectedAspect))
                        hasUnsavedChanges = true
                      }
                    }
                  )
                )

                Canvas(
                  modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                      awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val holder = gestureStateHolder.value
                        val initialHandle = CropMath.hitTest(
                          point = CropPoint(down.position.x, down.position.y),
                          rect = holder.cropRect,
                          transform = holder.viewTransform,
                          radiusPx = holder.hitRadiusPx
                        )
                        var hasMoved = false
                        val startSnapshot = CropStateSnapshot(holder.cropRect, holder.aspectRatio)

                        do {
                          val event = awaitPointerEvent()
                          val pressedPointers = event.changes.filter { it.pressed }

                          if (pressedPointers.size >= 2) {
                            // Two fingers: pinch zoom and pan the image
                            val p1 = pressedPointers[0]
                            val p2 = pressedPointers[1]
                            val prevDist = hypot(
                              p1.previousPosition.x - p2.previousPosition.x,
                              p1.previousPosition.y - p2.previousPosition.y
                            )
                            val curDist = hypot(
                              p1.position.x - p2.position.x,
                              p1.position.y - p2.position.y
                            )
                            val zoomFactor = if (prevDist > 0f) curDist / prevDist else 1f

                            val prevCenter = Offset(
                              (p1.previousPosition.x + p2.previousPosition.x) / 2f,
                              (p1.previousPosition.y + p2.previousPosition.y) / 2f
                            )
                            val curCenter = Offset(
                              (p1.position.x + p2.position.x) / 2f,
                              (p1.position.y + p2.position.y) / 2f
                            )
                            val panDelta = curCenter - prevCenter

                            gestureStateHolder.value.onPinchZoomPan(zoomFactor, panDelta)
                            event.changes.forEach { it.consume() }
                          } else if (pressedPointers.size == 1) {
                            // One finger: move crop box or handle
                            val change = pressedPointers[0]
                            val delta = change.positionChange()
                            if (delta != Offset.Zero && initialHandle != CropHandle.NONE) {
                              gestureStateHolder.value.onHandleDrag(initialHandle, delta)
                              hasMoved = true
                              change.consume()
                            }
                          }
                        } while (event.changes.any { it.pressed })

                        if (hasMoved) {
                          gestureStateHolder.value.onGestureComplete(startSnapshot)
                        }
                      }
                    }
                ) {
                  // 1. Draw transformed image
                  val canvasImageRect = transform.imageToCanvas(
                    CropRect(0f, 0f, orientedWidth.toFloat(), orientedHeight.toFloat())
                  )

                  val imageBitmap = bmp.asImageBitmap()
                  drawImage(
                    image = imageBitmap,
                    dstOffset = IntOffset(canvasImageRect.left.roundToInt(), canvasImageRect.top.roundToInt()),
                    dstSize = IntSize(
                      maxOf(1, canvasImageRect.width.roundToInt()),
                      maxOf(1, canvasImageRect.height.roundToInt())
                    )
                  )

                  // 2. Draw Dim Overlay outside crop box
                  val canvasCrop = transform.imageToCanvas(currentCrop)
                  val cropPath = Path().apply {
                    addRect(Rect(canvasCrop.left, canvasCrop.top, canvasCrop.right, canvasCrop.bottom))
                  }

                  clipPath(path = cropPath, clipOp = ClipOp.Difference) {
                    drawRect(color = Color(0x99000000))
                  }

                  // 3. Draw Crop Border
                  drawRect(
                    color = Color.White,
                    topLeft = Offset(canvasCrop.left, canvasCrop.top),
                    size = Size(canvasCrop.width, canvasCrop.height),
                    style = Stroke(width = 2.dp.toPx())
                  )

                  // 4. Draw Rule-of-Thirds Grid
                  val thirdW = canvasCrop.width / 3f
                  val thirdH = canvasCrop.height / 3f
                  val gridStroke = Stroke(width = 1.dp.toPx())
                  val gridColor = Color(0x80FFFFFF)

                  // Vertical grid lines
                  drawLine(
                    color = gridColor,
                    start = Offset(canvasCrop.left + thirdW, canvasCrop.top),
                    end = Offset(canvasCrop.left + thirdW, canvasCrop.bottom),
                    strokeWidth = gridStroke.width
                  )
                  drawLine(
                    color = gridColor,
                    start = Offset(canvasCrop.left + 2f * thirdW, canvasCrop.top),
                    end = Offset(canvasCrop.left + 2f * thirdW, canvasCrop.bottom),
                    strokeWidth = gridStroke.width
                  )
                  // Horizontal grid lines
                  drawLine(
                    color = gridColor,
                    start = Offset(canvasCrop.left, canvasCrop.top + thirdH),
                    end = Offset(canvasCrop.right, canvasCrop.top + thirdH),
                    strokeWidth = gridStroke.width
                  )
                  drawLine(
                    color = gridColor,
                    start = Offset(canvasCrop.left, canvasCrop.top + 2f * thirdH),
                    end = Offset(canvasCrop.right, canvasCrop.top + 2f * thirdH),
                    strokeWidth = gridStroke.width
                  )

                  // 5. Draw 8 Resize Handles
                  val handleLength = 16.dp.toPx()
                  val handleThickness = 3.dp.toPx()
                  val handleColor = Color.White

                  // Top-Left Corner
                  drawLine(handleColor, Offset(canvasCrop.left, canvasCrop.top), Offset(canvasCrop.left + handleLength, canvasCrop.top), handleThickness)
                  drawLine(handleColor, Offset(canvasCrop.left, canvasCrop.top), Offset(canvasCrop.left, canvasCrop.top + handleLength), handleThickness)

                  // Top-Right Corner
                  drawLine(handleColor, Offset(canvasCrop.right - handleLength, canvasCrop.top), Offset(canvasCrop.right, canvasCrop.top), handleThickness)
                  drawLine(handleColor, Offset(canvasCrop.right, canvasCrop.top), Offset(canvasCrop.right, canvasCrop.top + handleLength), handleThickness)

                  // Bottom-Left Corner
                  drawLine(handleColor, Offset(canvasCrop.left, canvasCrop.bottom), Offset(canvasCrop.left + handleLength, canvasCrop.bottom), handleThickness)
                  drawLine(handleColor, Offset(canvasCrop.left, canvasCrop.bottom - handleLength), Offset(canvasCrop.left, canvasCrop.bottom), handleThickness)

                  // Bottom-Right Corner
                  drawLine(handleColor, Offset(canvasCrop.right - handleLength, canvasCrop.bottom), Offset(canvasCrop.right, canvasCrop.bottom), handleThickness)
                  drawLine(handleColor, Offset(canvasCrop.right, canvasCrop.bottom - handleLength), Offset(canvasCrop.right, canvasCrop.bottom), handleThickness)

                  // Edge Midpoints
                  val midX = (canvasCrop.left + canvasCrop.right) / 2f
                  val midY = (canvasCrop.top + canvasCrop.bottom) / 2f
                  val midLength = 12.dp.toPx()

                  drawLine(handleColor, Offset(midX - midLength / 2f, canvasCrop.top), Offset(midX + midLength / 2f, canvasCrop.top), handleThickness)
                  drawLine(handleColor, Offset(midX - midLength / 2f, canvasCrop.bottom), Offset(midX + midLength / 2f, canvasCrop.bottom), handleThickness)
                  drawLine(handleColor, Offset(canvasCrop.left, midY - midLength / 2f), Offset(canvasCrop.left, midY + midLength / 2f), handleThickness)
                  drawLine(handleColor, Offset(canvasCrop.right, midY - midLength / 2f), Offset(canvasCrop.right, midY + midLength / 2f), handleThickness)
                }
              }
            }
          }
        }

        // 5. Aspect Ratio Selection Chips
        item {
          Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
              text = stringResource(R.string.tool4_aspect_ratio),
              style = MaterialTheme.typography.titleSmall,
              fontWeight = FontWeight.SemiBold
            )

            val ratios = listOf(
              CropAspectRatio.FREE to stringResource(R.string.tool4_aspect_free),
              CropAspectRatio.RATIO_1_1 to stringResource(R.string.tool4_aspect_1_1),
              CropAspectRatio.RATIO_4_3 to stringResource(R.string.tool4_aspect_4_3),
              CropAspectRatio.RATIO_3_4 to stringResource(R.string.tool4_aspect_3_4),
              CropAspectRatio.RATIO_16_9 to stringResource(R.string.tool4_aspect_16_9),
              CropAspectRatio.RATIO_9_16 to stringResource(R.string.tool4_aspect_9_16)
            )

            LazyRow(
              horizontalArrangement = Arrangement.spacedBy(8.dp),
              modifier = Modifier.fillMaxWidth()
            ) {
              items(ratios) { (ratio, label) ->
                FilterChip(
                  selected = selectedAspect == ratio,
                  onClick = {
                    selectedAspect = ratio
                    val center = CropPoint(
                      (currentCrop.left + currentCrop.right) / 2f,
                      (currentCrop.top + currentCrop.bottom) / 2f
                    )
                    val updated = if (ratio == CropAspectRatio.FREE) {
                      currentCrop
                    } else {
                      CropMath.fitRatioInImage(
                        aspectRatio = ratio,
                        imageW = orientedWidth.toFloat(),
                        imageH = orientedHeight.toFloat(),
                        currentCenter = center
                      )
                    }
                    cropLeft = updated.left
                    cropTop = updated.top
                    cropRight = updated.right
                    cropBottom = updated.bottom
                    cropHistory.push(CropStateSnapshot(updated, ratio))
                    hasUnsavedChanges = true
                  },
                  label = { Text(label) },
                  modifier = Modifier.testTag("crop_aspect_${ratio.name.lowercase()}")
                )
              }
            }
          }
        }

        // 6. Format and Quality Options
        item {
          Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
          ) {
            Column(
              modifier = Modifier.padding(16.dp),
              verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
              Text(
                text = stringResource(R.string.tool4_output_format),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
              )

              Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutputFormat.entries.forEach { fmt ->
                  FilterChip(
                    selected = outputFormat == fmt,
                    onClick = {
                      outputFormat = fmt
                      hasUnsavedChanges = true
                    },
                    label = { Text(fmt.name) },
                    modifier = Modifier.testTag("crop_format_${fmt.name.lowercase()}")
                  )
                }
              }

              if (outputFormat != OutputFormat.PNG) {
                Text(
                  text = "${stringResource(R.string.tool4_quality)}: $quality%",
                  style = MaterialTheme.typography.bodyMedium
                )
                Slider(
                  value = quality.toFloat(),
                  onValueChange = {
                    quality = it.roundToInt()
                    hasUnsavedChanges = true
                  },
                  valueRange = 10f..100f,
                  steps = 17,
                  modifier = Modifier
                    .fillMaxWidth()
                    .testTag("crop_quality_slider")
                )
              }
            }
          }
        }

        // 7. Save Button
        item {
          Button(
            onClick = {
              val uri = selectedUri ?: return@Button
              val newJobId = ToolJobRunner.start(
                toolId = 4,
                inputName = imageFileName,
                optionsJson = "{\"format\":\"${outputFormat.name}\",\"quality\":$quality}"
              ) {
                val targetDir = com.example.file.OutputDirs.getCropOutputsDir(context)
                val result = ImageCroppingHelper.cropAndSave(
                  context = context,
                  uri = uri,
                  originalName = imageFileName,
                  cropRect = currentCrop,
                  outputFormat = outputFormat,
                  quality = quality,
                  jobContext = this,
                  targetCacheDir = targetDir
                )
                JobResult(files = listOf(result))
              }
              currentJobId = newJobId
              hasUnsavedChanges = false
            },
            enabled = activeJobUi?.state != JobState.RUNNING && !ToolJobRunner.isToolBusy(4),
            modifier = Modifier
              .fillMaxWidth()
              .height(52.dp)
              .testTag("crop_save_button")
          ) {
            Icon(
              imageVector = Icons.Filled.Crop,
              contentDescription = null,
              modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.size(8.dp))
            Text(
              text = stringResource(R.string.tool4_save_button),
              style = MaterialTheme.typography.labelLarge
            )
          }
        }
      }
      } // end if (!cropFinished)

      // 8. Progress and Result Cards
      activeJobUi?.let { ui ->
        when (ui.state) {
          JobState.RUNNING -> {
            item {
              ProgressCard(
                stage = ui.stage,
                progressPercent = ui.progressPercent,
                currentIndex = ui.currentIndex,
                totalFiles = ui.totalFiles,
                currentFileName = ui.currentFileName,
                onCancel = { currentJobId?.let { ToolJobRunner.cancel(it) } }
              )
            }
          }
          JobState.SUCCESS -> {
            val firstFile = ui.result?.files?.firstOrNull()
            if (firstFile != null) {
              item {
                ResultCard(
                  fileResult = firstFile,
                  onProcessAnother = {
                    currentJobId = null
                    selectedUri = null
                    previewBitmap = null
                    cropLeft = -1f
                    cropTop = -1f
                    cropRight = -1f
                    cropBottom = -1f
                    hasUnsavedChanges = false
                  },
                  onDone = {
                    currentJobId = null
                    onBack()
                  }
                )
              }
            }
          }
          JobState.FAILED -> {
            item {
              ErrorCard(
                message = ui.errorMessage ?: ui.stage
              )
            }
          }
          else -> {}
        }
      }
    }
  }

  // Unsaved Changes Confirmation Dialog
  if (showDiscardDialog) {
    AlertDialog(
      onDismissRequest = { showDiscardDialog = false },
      title = { Text(stringResource(R.string.tool4_discard_dialog_title)) },
      text = { Text(stringResource(R.string.tool4_discard_dialog_message)) },
      confirmButton = {
        Button(
          onClick = {
            showDiscardDialog = false
            hasUnsavedChanges = false
            onBack()
          },
          modifier = Modifier.testTag("crop_discard_confirm")
        ) {
          Text(stringResource(R.string.tool4_discard_confirm))
        }
      },
      dismissButton = {
        TextButton(
          onClick = { showDiscardDialog = false },
          modifier = Modifier.testTag("crop_discard_cancel")
        ) {
          Text(stringResource(R.string.tool4_discard_cancel))
        }
      }
    )
  }
}
