package com.example.bgremove

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.file.FileFormatters
import com.example.file.FileLayer
import com.example.tools.common.ErrorCard
import com.example.tools.common.FilePickerCard
import com.example.tools.common.JobState
import com.example.tools.common.PickerMode
import com.example.tools.common.ProgressCard
import com.example.tools.common.ResultCard
import com.example.tools.common.ToolJobRunner
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

private const val TOOL_ID = 20

enum class PreviewMode {
  CUTOUT,
  ORIGINAL,
  BEFORE_AFTER,
  CHECKERBOARD
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackgroundRemoverScreen(
  onBack: () -> Unit,
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()

  var selectedUriString by rememberSaveable { mutableStateOf<String?>(null) }
  val selectedUri = selectedUriString?.let { Uri.parse(it) }

  var inputFileName by remember { mutableStateOf("") }
  var inputFileSizeBytes by remember { mutableStateOf(0L) }

  var cutoutData by remember { mutableStateOf<InitialCutoutData?>(null) }
  var isProcessingInitial by remember { mutableStateOf(false) }
  var initialStage by remember { mutableStateOf("") }
  var initialPercent by remember { mutableStateOf<Int?>(null) }
  var initialError by remember { mutableStateOf<String?>(null) }

  // Result and Edit State
  var previewMode by remember { mutableStateOf(PreviewMode.CUTOUT) }
  var splitFraction by remember { mutableFloatStateOf(0.5f) }
  var backgroundChoice by remember { mutableStateOf<BackgroundChoice>(BackgroundChoice.Transparent) }
  var edgeSoftness by remember { mutableIntStateOf(1) } // 0..3

  // Manual Edit Brush & Transform State
  var isEditMode by remember { mutableStateOf(false) }
  var brushMode by remember { mutableStateOf(BrushMode.ERASE) }
  var brushSizePx by remember { mutableFloatStateOf(35f) }
  val transform = remember { CanvasTransform() }
  // 200 steps: strokes are tiny, and keeping them all lets undo/redo replay exactly.
  val historyManager = remember { BgRemoveHistoryManager(maxHistory = 200) }
  var historyVersion by remember { mutableIntStateOf(0) }
  var hasUnsavedEdits by remember { mutableStateOf(false) }
  var showConfirmBackDialog by remember { mutableStateOf(false) }

  // Full re-render bookkeeping (used by undo/redo/reset/softness only; strokes are drawn instantly)
  var isRendering by remember { mutableStateOf(false) }
  val renderToken = remember { IntArray(1) }

  // Export Job State
  var exportFormat by remember { mutableStateOf("PNG") }
  var exportJobId by rememberSaveable { mutableStateOf<String?>(null) }
  val exportJobUi by ToolJobRunner.observe(exportJobId ?: "").collectAsState(initial = null)

  LaunchedEffect(exportJobId) {
    val id = exportJobId
    if (id != null && !ToolJobRunner.hasJob(id)) {
      exportJobId = null
    }
  }

  // The live editing surface (pristine AI mask + working mask + on-screen bitmap)
  val data = cutoutData
  val editor = remember(data) { data?.let { CutoutEditor(it) } }

  fun requestFullRender(replayStrokes: Boolean) {
    val ed = editor ?: return
    val token = ++renderToken[0]
    val strokes = if (replayStrokes) historyManager.strokeList else emptyList()
    val soft = edgeSoftness
    ed.softness = soft
    isRendering = true
    scope.launch {
      try {
        val result = withContext(Dispatchers.Default) {
          val m = if (replayStrokes) {
            MaskRenderer.renderStrokes(ed.data.baseMask, ed.width, ed.height, strokes)
          } else {
            ed.mask
          }
          m to ed.computeFull(m, soft)
        }
        if (token == renderToken[0]) {
          ed.commitFull(result.first, result.second)
        }
      } finally {
        if (token == renderToken[0]) {
          isRendering = false
        }
      }
    }
  }

  // First render + every softness change
  LaunchedEffect(editor, edgeSoftness) {
    requestFullRender(false)
  }

  // Custom background image preview (no manual recycle: the canvas may still be drawing the old one)
  val previewCustomBgBitmap by produceState<Bitmap?>(initialValue = null, key1 = backgroundChoice) {
    val choice = backgroundChoice
    value = if (choice is BackgroundChoice.CustomImage) {
      val bgUri = Uri.parse(choice.uriString)
      withContext(Dispatchers.IO) {
        try {
          BgRemoveHelper.decodePreviewBackgroundImage(context, bgUri, 1024)
        } catch (e: Exception) {
          null
        }
      }
    } else {
      null
    }
  }

  // Back handling
  BackHandler {
    if (hasUnsavedEdits && exportJobUi?.state != JobState.RUNNING) {
      showConfirmBackDialog = true
    } else {
      onBack()
    }
  }

  fun resetEditState() {
    historyManager.clear()
    historyVersion++
    backgroundChoice = BackgroundChoice.Transparent
    isEditMode = false
    previewMode = PreviewMode.CUTOUT
    hasUnsavedEdits = false
    transform.reset()
  }

  fun processSelectedImage(uri: Uri) {
    selectedUriString = uri.toString()
    cutoutData = null
    initialError = null
    resetEditState()

    scope.launch {
      isProcessingInitial = true
      initialPercent = null
      initialStage = context.getString(R.string.tool20_stage_loading)

      try {
        val info = withContext(Dispatchers.IO) { FileLayer.getUriFileInfo(context, uri) }
        inputFileName = info.displayName
        inputFileSizeBytes = info.sizeBytes

        val detected = BgRemoveHelper.detectBackground(
          context = context,
          uri = uri,
          onProgress = { pct, stage ->
            initialPercent = pct
            initialStage = stage
          }
        )
        cutoutData = detected
        isProcessingInitial = false
      } catch (ce: CancellationException) {
        isProcessingInitial = false
        throw ce
      } catch (oom: OutOfMemoryError) {
        isProcessingInitial = false
        initialError = context.getString(R.string.error_generic)
      } catch (t: Throwable) {
        isProcessingInitial = false
        initialError = t.message ?: context.getString(R.string.error_generic)
      }
    }
  }

  fun resetToPicker() {
    exportJobId?.let { ToolJobRunner.clearJob(it) }
    exportJobId = null
    selectedUriString = null
    cutoutData = null
    initialError = null
    resetEditState()
  }

  fun afterUndoRedo(action: EditAction?) {
    if (action == null) return
    historyVersion++
    hasUnsavedEdits = true
    if (action is EditAction.BackgroundAction) {
      backgroundChoice = historyManager.currentBackground ?: BackgroundChoice.Transparent
    } else {
      requestFullRender(true)
    }
  }

  // Photo Picker for background image
  val bgImagePicker = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.PickVisualMedia()
  ) { uri ->
    if (uri != null) {
      val choice = BackgroundChoice.CustomImage(uri.toString())
      historyManager.push(EditAction.BackgroundAction(choice))
      backgroundChoice = choice
      historyVersion++
      hasUnsavedEdits = true
    }
  }

  // Single Photo Picker for main subject image
  val imagePickerLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.PickVisualMedia()
  ) { uri ->
    if (uri != null) {
      processSelectedImage(uri)
    }
  }

  val jobState = exportJobUi?.state
  val exportFileResult = if (jobState == JobState.SUCCESS) exportJobUi?.result?.files?.firstOrNull() else null

  Scaffold(
    topBar = {
      TopAppBar(
        title = {
          Text(
            text = stringResource(R.string.tool20_name),
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
          )
        },
        navigationIcon = {
          IconButton(
            onClick = {
              if (hasUnsavedEdits && exportJobUi?.state != JobState.RUNNING) {
                showConfirmBackDialog = true
              } else {
                onBack()
              }
            },
            modifier = Modifier.testTag("tool20_back_button")
          ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
          }
        }
      )
    },
    modifier = modifier
  ) { padding ->
    LazyColumn(
      modifier = Modifier
        .fillMaxSize()
        .padding(padding)
        .padding(horizontal = 16.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
      item { Spacer(Modifier.height(4.dp)) }

      if (exportFileResult != null) {
        // ---- Export finished: same result page as the other tools (preview, size, Save, Gallery...) ----
        item {
          ResultCard(
            fileResult = exportFileResult,
            onProcessAnother = { resetToPicker() },
            onDone = onBack,
            showTransparencyBackdrop = true,
            modifier = Modifier.testTag("tool20_result_card")
          )
        }
      } else if (jobState == JobState.RUNNING) {
        // ---- Export running ----
        item {
          ProgressCard(
            stage = exportJobUi?.stage ?: "",
            progressPercent = exportJobUi?.progressPercent,
            currentIndex = 1,
            totalFiles = 1,
            currentFileName = inputFileName,
            onCancel = { exportJobId?.let { ToolJobRunner.cancel(it) } },
            modifier = Modifier.testTag("tool20_progress_card")
          )
        }
      } else {
        // 1. File Picker Card
        if (selectedUri == null) {
          item {
            FilePickerCard(
              onFilesSelected = { uris ->
                uris.firstOrNull()?.let { uri ->
                  processSelectedImage(uri)
                }
              },
              title = stringResource(R.string.tool20_picker_title),
              supportedFormatsText = stringResource(R.string.tool20_picker_desc),
              pickerMode = PickerMode.PHOTO_PICKER,
              maxFiles = 1,
              mimeTypeFilter = arrayOf("image/*"),
              modifier = Modifier.testTag("tool20_picker_card")
            )
          }
        }

        // Initial Detection Running Indicator
        if (isProcessingInitial) {
          item {
            Card(
              modifier = Modifier.fillMaxWidth().testTag("tool20_detecting_card"),
              shape = RoundedCornerShape(16.dp),
              colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
              Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                horizontalAlignment = Alignment.CenterHorizontally
              ) {
                CircularProgressIndicator()
                Text(
                  text = initialStage.ifEmpty { stringResource(R.string.tool20_detecting) },
                  style = MaterialTheme.typography.titleSmall,
                  fontWeight = FontWeight.Bold
                )
                if (initialPercent != null) {
                  Text(
                    text = "$initialPercent%",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                  )
                }
              }
            }
          }
        }

        // Initial Detection Error
        if (initialError != null) {
          item {
            ErrorCard(
              message = initialError!!,
              recoveryHint = if (initialError?.contains("missing", ignoreCase = true) == true) {
                stringResource(R.string.tool20_model_missing_hint)
              } else null,
              onRetry = {
                selectedUri?.let { uri ->
                  processSelectedImage(uri)
                }
              },
              modifier = Modifier.testTag("tool20_error_card")
            )
          }
        }

        // Export Error Card
        if (jobState == JobState.FAILED) {
          item {
            ErrorCard(
              message = exportJobUi?.errorMessage ?: stringResource(R.string.error_generic),
              onRetry = {
                exportJobId?.let { ToolJobRunner.clearJob(it) }
                exportJobId = null
              },
              modifier = Modifier.testTag("tool20_export_error_card")
            )
          }
        }

        // 2. Main Result and Editing UI
        if (selectedUri != null && data != null && editor != null && !isProcessingInitial) {
          // Image Info Header
          item {
            Card(
              modifier = Modifier.fillMaxWidth().testTag("tool20_info_card"),
              shape = RoundedCornerShape(16.dp),
              colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
              Row(
                modifier = Modifier.fillMaxWidth().padding(14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
              ) {
                Column(modifier = Modifier.weight(1f)) {
                  Text(
                    text = inputFileName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                  )
                  Text(
                    text = "${data.originalWidth}×${data.originalHeight} • ${FileFormatters.formatBytes(data.originalSizeBytes)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                  )
                }

                OutlinedButton(
                  onClick = {
                    imagePickerLauncher.launch(
                      androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                  },
                  modifier = Modifier.testTag("tool20_change_button")
                ) {
                  Text(stringResource(R.string.common_change))
                }
              }
            }
          }

          // Detection uncertainty banner if coverage is extreme (<3% or >97%)
          if (data.subjectCoverageFraction < 0.03f || data.subjectCoverageFraction > 0.97f) {
            item {
              Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.6f)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().testTag("tool20_detection_uncertainty_card")
              ) {
                Row(
                  modifier = Modifier.padding(12.dp),
                  horizontalArrangement = Arrangement.spacedBy(8.dp),
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary)
                  Text(
                    text = stringResource(R.string.tool20_detection_uncertain),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                  )
                }
              }
            }
          }

          // Preview Mode Tabs
          item {
            TabRow(
              selectedTabIndex = previewMode.ordinal,
              modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
            ) {
              Tab(
                selected = previewMode == PreviewMode.CUTOUT,
                onClick = { previewMode = PreviewMode.CUTOUT },
                text = { Text(stringResource(R.string.tool20_tab_cutout)) }
              )
              Tab(
                selected = previewMode == PreviewMode.ORIGINAL,
                onClick = { previewMode = PreviewMode.ORIGINAL },
                text = { Text(stringResource(R.string.tool20_tab_original)) }
              )
              Tab(
                selected = previewMode == PreviewMode.BEFORE_AFTER,
                onClick = { previewMode = PreviewMode.BEFORE_AFTER },
                text = { Text(stringResource(R.string.tool20_tab_compare)) }
              )
              Tab(
                selected = previewMode == PreviewMode.CHECKERBOARD,
                onClick = { previewMode = PreviewMode.CHECKERBOARD },
                text = { Text(stringResource(R.string.tool20_tab_checkerboard)) }
              )
            }
          }

          // 3. Interactive Canvas View (instant brush, pinch zoom)
          item {
            Card(
              modifier = Modifier.fillMaxWidth().testTag("tool20_canvas_card"),
              shape = RoundedCornerShape(16.dp),
              colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
              CutoutCanvas(
                editor = editor,
                transform = transform,
                previewMode = previewMode,
                splitFraction = splitFraction,
                onSplitChange = { splitFraction = it },
                backgroundChoice = backgroundChoice,
                customBackground = previewCustomBgBitmap,
                isEditMode = isEditMode,
                brushMode = brushMode,
                brushSizePx = brushSizePx,
                isBusy = isRendering,
                onStrokeStarted = { hasUnsavedEdits = true },
                onStrokeFinished = { stroke ->
                  historyManager.push(EditAction.StrokeAction(stroke))
                  historyVersion++
                }
              )
            }
          }

          // 4. Manual Edit Controls (Brush, Erase, Restore, Undo, Redo, Reset)
          item {
            // Reading the version makes the undo/redo buttons refresh after every stroke
            val version = historyVersion
            val canUndo = version >= 0 && historyManager.canUndo
            val canRedo = version >= 0 && historyManager.canRedo

            Card(
              modifier = Modifier.fillMaxWidth().testTag("tool20_edit_controls_card"),
              shape = RoundedCornerShape(16.dp),
              colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
              Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  Text(
                    text = stringResource(R.string.tool20_manual_edit_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                  )

                  Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    // Undo
                    IconButton(
                      onClick = { afterUndoRedo(historyManager.undo()) },
                      enabled = canUndo && !isRendering,
                      modifier = Modifier.testTag("tool20_undo_button")
                    ) {
                      Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = stringResource(R.string.common_undo))
                    }

                    // Redo
                    IconButton(
                      onClick = { afterUndoRedo(historyManager.redo()) },
                      enabled = canRedo && !isRendering,
                      modifier = Modifier.testTag("tool20_redo_button")
                    ) {
                      Icon(Icons.AutoMirrored.Filled.Redo, contentDescription = stringResource(R.string.common_redo))
                    }

                    // Reset
                    IconButton(
                      onClick = {
                        historyManager.clear()
                        historyVersion++
                        backgroundChoice = BackgroundChoice.Transparent
                        hasUnsavedEdits = false
                        transform.reset()
                        requestFullRender(true)
                      },
                      modifier = Modifier.testTag("tool20_reset_button")
                    ) {
                      Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.tool20_reset_tooltip))
                    }
                  }
                }

                // Mode toggle (View / Edit)
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                  FilterChip(
                    selected = !isEditMode,
                    onClick = { isEditMode = false },
                    label = { Text(stringResource(R.string.tool20_mode_preview)) },
                    leadingIcon = { Icon(Icons.Default.Visibility, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    modifier = Modifier.weight(1f).testTag("tool20_mode_preview")
                  )
                  FilterChip(
                    selected = isEditMode,
                    onClick = { isEditMode = true },
                    label = { Text(stringResource(R.string.tool20_mode_brush)) },
                    leadingIcon = { Icon(Icons.Default.Brush, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    modifier = Modifier.weight(1f).testTag("tool20_mode_brush")
                  )
                }

                if (isEditMode) {
                  // Erase vs Restore
                  Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                  ) {
                    FilterChip(
                      selected = brushMode == BrushMode.ERASE,
                      onClick = { brushMode = BrushMode.ERASE },
                      label = { Text(stringResource(R.string.tool20_brush_erase)) },
                      modifier = Modifier.weight(1f).testTag("tool20_brush_erase")
                    )
                    FilterChip(
                      selected = brushMode == BrushMode.RESTORE,
                      onClick = { brushMode = BrushMode.RESTORE },
                      label = { Text(stringResource(R.string.tool20_brush_restore)) },
                      modifier = Modifier.weight(1f).testTag("tool20_brush_restore")
                    )
                  }

                  // Brush size slider
                  Column {
                    Text(
                      text = "${stringResource(R.string.tool20_brush_size)}: ${brushSizePx.roundToInt()} px",
                      style = MaterialTheme.typography.bodySmall,
                      color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Slider(
                      value = brushSizePx,
                      onValueChange = { brushSizePx = it },
                      valueRange = 10f..80f,
                      modifier = Modifier.fillMaxWidth().testTag("tool20_brush_slider")
                    )
                  }

                  Text(
                    text = stringResource(R.string.tool20_zoom_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                  )
                }

                // Edge Softness Slider (Box Blur)
                Column {
                  Text(
                    text = "${stringResource(R.string.tool20_edge_softness)}: $edgeSoftness px",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                  )
                  Slider(
                    value = edgeSoftness.toFloat(),
                    onValueChange = {
                      val newValue = it.roundToInt()
                      if (newValue != edgeSoftness) {
                        edgeSoftness = newValue
                        hasUnsavedEdits = true
                      }
                    },
                    valueRange = 0f..3f,
                    steps = 2,
                    modifier = Modifier.fillMaxWidth().testTag("tool20_softness_slider")
                  )
                }
              }
            }
          }

          // 5. Background Options
          item {
            Card(
              modifier = Modifier.fillMaxWidth().testTag("tool20_bg_card"),
              shape = RoundedCornerShape(16.dp),
              colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
              Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                  text = stringResource(R.string.tool20_bg_title),
                  style = MaterialTheme.typography.titleSmall,
                  fontWeight = FontWeight.Bold
                )

                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                  FilterChip(
                    selected = backgroundChoice is BackgroundChoice.Transparent,
                    onClick = {
                      backgroundChoice = BackgroundChoice.Transparent
                      historyManager.push(EditAction.BackgroundAction(BackgroundChoice.Transparent))
                      historyVersion++
                      hasUnsavedEdits = true
                    },
                    label = { Text(stringResource(R.string.tool20_bg_transparent)) },
                    modifier = Modifier.testTag("tool20_bg_transparent")
                  )
                  FilterChip(
                    selected = backgroundChoice is BackgroundChoice.White,
                    onClick = {
                      backgroundChoice = BackgroundChoice.White
                      historyManager.push(EditAction.BackgroundAction(BackgroundChoice.White))
                      historyVersion++
                      hasUnsavedEdits = true
                    },
                    label = { Text(stringResource(R.string.tool20_bg_white)) },
                    modifier = Modifier.testTag("tool20_bg_white")
                  )
                  FilterChip(
                    selected = backgroundChoice is BackgroundChoice.Black,
                    onClick = {
                      backgroundChoice = BackgroundChoice.Black
                      historyManager.push(EditAction.BackgroundAction(BackgroundChoice.Black))
                      historyVersion++
                      hasUnsavedEdits = true
                    },
                    label = { Text(stringResource(R.string.tool20_bg_black)) },
                    modifier = Modifier.testTag("tool20_bg_black")
                  )
                }

                // Custom Color Palette & Custom Image Picker
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.spacedBy(10.dp),
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  // Preset Color Swatches
                  listOf(
                    Color(0xFFE53935), Color(0xFF1E88E5), Color(0xFF43A047),
                    Color(0xFFFB8C00), Color(0xFF8E24AA)
                  ).forEach { color ->
                    Box(
                      modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(color)
                        .clickable {
                          val choice = BackgroundChoice.CustomColor(color)
                          backgroundChoice = choice
                          historyManager.push(EditAction.BackgroundAction(choice))
                          historyVersion++
                          hasUnsavedEdits = true
                        }
                        .border(
                          width = if (backgroundChoice == BackgroundChoice.CustomColor(color)) 2.dp else 0.dp,
                          color = MaterialTheme.colorScheme.primary,
                          shape = CircleShape
                        )
                    )
                  }

                  Spacer(Modifier.weight(1f))

                  // Custom Background Image Button
                  OutlinedButton(
                    onClick = {
                      bgImagePicker.launch(
                        androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                      )
                    },
                    modifier = Modifier.testTag("tool20_pick_bg_button")
                  ) {
                    Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.tool20_bg_image))
                  }
                }
              }
            }
          }

          // 6. Export Format & Start Export Button
          item {
            Card(
              modifier = Modifier.fillMaxWidth().testTag("tool20_export_card"),
              shape = RoundedCornerShape(16.dp),
              colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
              Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                  text = stringResource(R.string.tool20_export_format),
                  style = MaterialTheme.typography.titleSmall,
                  fontWeight = FontWeight.Bold
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                  listOf("PNG", "JPEG", "WEBP").forEach { fmt ->
                    FilterChip(
                      selected = exportFormat.equals(fmt, ignoreCase = true),
                      onClick = { exportFormat = fmt },
                      label = { Text(fmt) },
                      modifier = Modifier.testTag("tool20_fmt_$fmt")
                    )
                  }
                }

                // Honest text
                Text(
                  text = stringResource(R.string.tool20_honest_note),
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Start Export Button
                Button(
                  enabled = !isRendering,
                  onClick = {
                    val uri = selectedUri ?: return@Button
                    // Pristine AI mask + the recorded strokes are replayed by the exporter
                    val strokes = historyManager.strokeList
                    val bg = backgroundChoice
                    val fmt = exportFormat
                    val softness = edgeSoftness

                    exportJobId = ToolJobRunner.start(
                      toolId = TOOL_ID,
                      inputName = inputFileName,
                      optionsJson = "{format:\"$fmt\",softness:$softness}",
                      inputUri = uri.toString(),
                      inputSize = inputFileSizeBytes
                    ) {
                      BgRemoveHelper.exportCutout(
                        context = context,
                        inputUri = uri,
                        inputFileName = inputFileName,
                        baseMask = data.baseMask,
                        maskW = data.maskWidth,
                        maskH = data.maskHeight,
                        strokes = strokes,
                        edgeSoftness = softness,
                        backgroundChoice = bg,
                        outputFormat = fmt,
                        jobContext = this,
                        onProgress = { pct, stage ->
                          reportProgress(pct, stage, 1, 1, inputFileName)
                        }
                      )
                    }
                    hasUnsavedEdits = false
                  },
                  modifier = Modifier.fillMaxWidth().height(52.dp).testTag("tool20_export_button")
                ) {
                  Icon(Icons.Default.AutoFixHigh, contentDescription = null)
                  Spacer(Modifier.width(8.dp))
                  Text(stringResource(R.string.tool20_action_export))
                }
              }
            }
          }
        }
      }

      item { Spacer(Modifier.height(24.dp)) }
    }
  }

  // Unsaved Edits Dialog
  if (showConfirmBackDialog) {
    AlertDialog(
      onDismissRequest = { showConfirmBackDialog = false },
      title = { Text(stringResource(R.string.tool20_unsaved_title)) },
      text = { Text(stringResource(R.string.tool20_unsaved_desc)) },
      confirmButton = {
        Button(
          onClick = {
            showConfirmBackDialog = false
            onBack()
          }
        ) {
          Text(stringResource(R.string.common_discard))
        }
      },
      dismissButton = {
        TextButton(onClick = { showConfirmBackDialog = false }) {
          Text(stringResource(R.string.common_cancel))
        }
      }
    )
  }
}
