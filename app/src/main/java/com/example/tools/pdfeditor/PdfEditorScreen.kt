package com.example.tools.pdfeditor

import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.RectF
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Gesture
import androidx.compose.material.icons.filled.Highlight
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Pages
import androidx.compose.material.icons.filled.PanTool
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import kotlinx.coroutines.flow.flowOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.R
import com.example.file.FileFormatters
import com.example.file.FileLayer
import com.example.file.FileNameSanitizer
import com.example.pdf.PdfErrors
import com.example.pdf.PdfFileHelper
import com.example.tools.common.FilePickerCard
import com.example.tools.common.FileResult
import com.example.tools.common.JobResult
import com.example.tools.common.JobState
import com.example.tools.common.PdfResultPreview
import com.example.tools.common.PickerMode
import com.example.tools.common.ProgressCard
import com.example.tools.common.ToolJobRunner
import com.example.tools.common.ToolScaffold
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

const val TOOL_ID_PDF_EDITOR = 21

enum class ActiveEditorTool {
  MOVE_SELECT,
  TEXT,
  IMAGE,
  SIGNATURE,
  DRAW_PEN,
  DRAW_HIGHLIGHTER,
  SHAPE_RECT,
  COVER_REPLACE,
  REDACT,
  PAGE_TOOLS
}

@Composable
fun PdfEditorScreen(
  onBack: () -> Unit,
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()

  var selectedUri by remember { mutableStateOf<Uri?>(null) }
  var cachedPdfFile by remember { mutableStateOf<File?>(null) }
  var pageCount by remember { mutableIntStateOf(0) }
  var currentPageIndex by remember { mutableIntStateOf(0) }
  var isLoadingPdf by remember { mutableStateOf(false) }
  var loadError by remember { mutableStateOf<String?>(null) }

  var activeTool by remember { mutableStateOf(ActiveEditorTool.MOVE_SELECT) }
  val history = remember { PdfEditorHistory() }
  var sessionState by remember { mutableStateOf(EditorSessionState()) }

  var showUnsavedDialog by remember { mutableStateOf(false) }
  var editTarget by remember { mutableStateOf<EditTarget?>(null) }
  var enhanceEnabled by rememberSaveable { mutableStateOf(true) }
  var showSignatureDialog by remember { mutableStateOf(false) }
  var selectedId by remember { mutableStateOf<String?>(null) }
  var cropTarget by remember { mutableStateOf<ImageOverlay?>(null) }
  var pageDims by remember { mutableStateOf(Pair(595f, 842f)) }

  // The pages exactly as the user sees them after rotate / delete / duplicate / insert.
  val pageEntries = remember(pageCount, sessionState.pageOps) {
    PageMapper.entries(pageCount, sessionState.pageOps)
  }
  val displayCount = pageEntries.size
  LaunchedEffect(displayCount) {
    if (displayCount > 0 && currentPageIndex >= displayCount) currentPageIndex = displayCount - 1
  }
  LaunchedEffect(currentPageIndex) { selectedId = null }

  fun commitState(updated: EditorSessionState) {
    history.pushState(updated)
    sessionState = updated
  }

  fun pageList(): List<OverlayObject> = sessionState.pageOverlays[currentPageIndex] ?: emptyList()

  fun addToPage(objs: List<OverlayObject>) {
    val updated = sessionState.copy(
      pageOverlays = sessionState.pageOverlays + (currentPageIndex to (pageList() + objs))
    )
    commitState(updated)
  }

  fun replaceOnPage(newObj: OverlayObject) {
    val list = pageList().map { if (it.id == newObj.id) newObj else it }
    commitState(sessionState.copy(pageOverlays = sessionState.pageOverlays + (currentPageIndex to list)))
  }

  fun deleteFromPage(id: String) {
    val list = pageList().filter { it.id != id }
    var flat = sessionState.flattenedPages
    if (list.none { it is RedactOverlay }) flat = flat - currentPageIndex
    commitState(
      sessionState.copy(
        pageOverlays = sessionState.pageOverlays + (currentPageIndex to list),
        flattenedPages = flat
      )
    )
    selectedId = null
  }

  fun applyPageOp(op: PageOp) {
    val (overlays, flat) = PageMapper.remapForOp(sessionState.pageOverlays, sessionState.flattenedPages, op)
    commitState(sessionState.copy(pageOps = sessionState.pageOps + op, pageOverlays = overlays, flattenedPages = flat))
    selectedId = null
  }

  // Place an image / signature in the middle of the page, keeping its shape.
  fun centeredBounds(imageWidth: Int, imageHeight: Int, widthFraction: Float): RectF {
    val (pw, ph) = pageDims
    val w = widthFraction.coerceIn(0.05f, 0.9f)
    var h = if (imageWidth > 0) w * (imageHeight.toFloat() / imageWidth) * (pw / ph) else w
    var ww = w
    if (h > 0.8f) {
      ww = ww * (0.8f / h)
      h = 0.8f
    }
    return RectF(0.5f - ww / 2f, 0.5f - h / 2f, 0.5f + ww / 2f, 0.5f + h / 2f)
  }
  var currentJobId by rememberSaveable { mutableStateOf<String?>(null) }

  val jobUi by remember(currentJobId) {
    if (currentJobId != null) ToolJobRunner.observe(currentJobId!!)
    else flowOf(null)
  }.collectAsState(initial = null)

  BackHandler {
    if (sessionState.pageOverlays.isNotEmpty() || sessionState.pageOps.isNotEmpty()) {
      showUnsavedDialog = true
    } else {
      onBack()
    }
  }

  // SAF Document picker for saving edited PDF
  val savePdfLauncher = rememberLauncherForActivityResult(
    ActivityResultContracts.CreateDocument("application/pdf")
  ) { destUri ->
    val output = jobUi?.result?.files?.firstOrNull()?.tempFilePath?.let { File(it) }
    if (destUri != null && output != null && output.exists()) {
      val saved = FileLayer.saveFileToUri(context, output, destUri)
      if (saved) {
        Toast.makeText(context, context.getString(R.string.file_saved_success), Toast.LENGTH_SHORT).show()
      }
    }
  }

  // Photo picker for stamping images into PDF
  val imagePickerLauncher = rememberLauncherForActivityResult(
    ActivityResultContracts.PickVisualMedia()
  ) { imgUri ->
    if (imgUri != null) {
      scope.launch(Dispatchers.IO) {
        try {
          val tempDir = FileLayer.getTempFolder(context)
          val tempImg = File(tempDir, "stamp_${UUID.randomUUID()}.png")
          context.contentResolver.openInputStream(imgUri)?.use { input ->
            FileOutputStream(tempImg).use { output -> input.copyTo(output) }
          }
          val dims = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
          android.graphics.BitmapFactory.decodeFile(tempImg.absolutePath, dims)
          withContext(Dispatchers.Main) {
            val newObj = ImageOverlay(
              bounds = centeredBounds(dims.outWidth, dims.outHeight, 0.4f),
              imagePath = tempImg.absolutePath
            )
            addToPage(listOf(newObj))
            selectedId = newObj.id
            activeTool = ActiveEditorTool.MOVE_SELECT
          }
        } catch (_: Exception) {}
      }
    }
  }

  fun loadSelectedPdf(uri: Uri) {
    selectedUri = uri
    isLoadingPdf = true
    loadError = null
    scope.launch(Dispatchers.IO) {
      try {
        val file = PdfFileHelper.copyPdfToCache(context, uri)
        val count = PdfFileHelper.pdfRendererMutex.withLock {
          ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
            PdfRenderer(pfd).use { it.pageCount }
          }
        }
        withContext(Dispatchers.Main) {
          cachedPdfFile = file
          pageCount = count
          currentPageIndex = 0
          sessionState = EditorSessionState()
          history.reset()
          isLoadingPdf = false
        }
      } catch (e: Exception) {
        withContext(Dispatchers.Main) {
          loadError = PdfErrors.mapPdfException(e)
          isLoadingPdf = false
        }
      }
    }
  }

  ToolScaffold(
    title = stringResource(R.string.tool21_name),
    onBack = {
      if (sessionState.pageOverlays.isNotEmpty() || sessionState.pageOps.isNotEmpty()) {
        showUnsavedDialog = true
      } else {
        onBack()
      }
    },
    modifier = modifier
  ) { padding ->
    Box(
      modifier = Modifier
        .fillMaxSize()
        .padding(padding)
    ) {
      if (selectedUri == null) {
        // Picker Card
        Column(
          modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
          verticalArrangement = Arrangement.Center
        ) {
          FilePickerCard(
            title = stringResource(R.string.tool21_picker_title),
            supportedFormatsText = stringResource(R.string.tool21_picker_desc),
            pickerMode = PickerMode.SAF_DOCUMENTS,
            maxFiles = 1,
            mimeTypeFilter = arrayOf("application/pdf"),
            onFilesSelected = { uris ->
              uris.firstOrNull()?.let { loadSelectedPdf(it) }
            },
            modifier = Modifier.testTag("pdf_editor_picker")
          )
          if (loadError != null) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
              text = loadError!!,
              color = MaterialTheme.colorScheme.error,
              style = MaterialTheme.typography.bodyMedium
            )
          }
        }
      } else if (jobUi != null && jobUi!!.state == JobState.RUNNING) {
        // Running progress card
        Column(
          modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
          verticalArrangement = Arrangement.Center
        ) {
          ProgressCard(
            stage = jobUi!!.stage,
            progressPercent = jobUi!!.progressPercent,
            currentIndex = jobUi!!.currentIndex,
            totalFiles = jobUi!!.totalFiles,
            currentFileName = jobUi!!.currentFileName,
            onCancel = { currentJobId?.let { ToolJobRunner.cancel(it) } },
            modifier = Modifier.testTag("pdf_editor_progress")
          )
        }
      } else if (jobUi != null && jobUi!!.state == JobState.SUCCESS) {
        // Result card
        val fileRes = jobUi!!.result?.files?.firstOrNull()
        val localFile = fileRes?.tempFilePath?.let { File(it) }
        Column(
          modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
          verticalArrangement = Arrangement.Center
        ) {
          Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
          ) {
            Column(
              modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
              verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
              Text(
                text = stringResource(R.string.tool21_success_title),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary
              )
              if (localFile != null && localFile.exists()) {
                Text(
                  text = "${localFile.name} (${FileFormatters.formatBytes(localFile.length())})",
                  style = MaterialTheme.typography.bodyMedium,
                  color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                PdfResultPreview(localFile)

                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                  Button(
                    onClick = { savePdfLauncher.launch(localFile.name) },
                    modifier = Modifier.weight(1f)
                  ) {
                    Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.common_save))
                  }
                  OutlinedButton(
                    onClick = { FileLayer.shareFileAndNotify(context, localFile, "application/pdf") }
                  ) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                  }
                  OutlinedButton(
                    onClick = { FileLayer.openFileAndNotify(context, localFile, "application/pdf") }
                  ) {
                    Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                  }
                }
              }
              OutlinedButton(
                onClick = {
                  selectedUri = null
                  cachedPdfFile = null
                  currentJobId = null
                },
                modifier = Modifier.fillMaxWidth()
              ) {
                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.common_process_again))
              }
            }
          }
        }
      } else {
        // Main Editor Canvas and Toolbar UI
        Column(modifier = Modifier.fillMaxSize()) {
          // Top bar: Page pager and Undo/Redo/Save
          Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            tonalElevation = 2.dp
          ) {
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                  onClick = { if (currentPageIndex > 0) currentPageIndex-- },
                  enabled = currentPageIndex > 0
                ) {
                  Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.tool21_prev_page))
                }
                Text(
                  text = "${currentPageIndex + 1} / $displayCount",
                  style = MaterialTheme.typography.titleSmall,
                  fontWeight = FontWeight.Bold
                )
                IconButton(
                  onClick = { if (currentPageIndex < displayCount - 1) currentPageIndex++ },
                  enabled = currentPageIndex < displayCount - 1
                ) {
                  Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = stringResource(R.string.tool21_next_page))
                }
              }

              Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                IconButton(
                  onClick = { enhanceEnabled = !enhanceEnabled },
                  modifier = Modifier.testTag("pdf_editor_enhance_toggle")
                ) {
                  Icon(
                    Icons.Default.AutoFixHigh,
                    contentDescription = stringResource(R.string.tool21_enhance_toggle),
                    tint = if (enhanceEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                  )
                }
                IconButton(
                  onClick = {
                    val undone = history.undo()
                    if (undone != null) sessionState = undone
                  },
                  enabled = history.canUndo
                ) {
                  Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = stringResource(R.string.tool21_undo))
                }
                IconButton(
                  onClick = {
                    val redone = history.redo()
                    if (redone != null) sessionState = redone
                  },
                  enabled = history.canRedo
                ) {
                  Icon(Icons.AutoMirrored.Filled.Redo, contentDescription = stringResource(R.string.tool21_redo))
                }
                Button(
                  onClick = {
                    val file = cachedPdfFile ?: return@Button
                    val stateCopy = sessionState
                    val jobId = ToolJobRunner.start(
                      toolId = TOOL_ID_PDF_EDITOR,
                      inputName = file.name,
                      optionsJson = "{}",
                      inputUri = selectedUri.toString(),
                      inputSize = file.length()
                    ) {
                      val out = PdfEditorExporter.exportEditedPdf(context, file, stateCopy, this, enhanceScans = enhanceEnabled)
                      JobResult(
                        files = listOf(
                          FileResult(
                            inputName = file.name,
                            outputName = out.name,
                            outputUri = out.absolutePath,
                            outputSizeBytes = out.length(),
                            success = true,
                            userMessage = "PDF edited successfully",
                            format = "PDF",
                            tempFilePath = out.absolutePath
                          )
                        )
                      )
                    }
                    currentJobId = jobId
                  },
                  shape = RoundedCornerShape(8.dp)
                ) {
                  Text(stringResource(R.string.tool21_btn_save_pdf))
                }
              }
            }
          }

          // Middle: Interactive Page Canvas
          Box(
            modifier = Modifier
              .weight(1f)
              .fillMaxWidth()
              .background(Color(0xFF2C2C2C)),
            contentAlignment = Alignment.Center
          ) {
            val file = cachedPdfFile
            if (file != null && displayCount > 0 && currentPageIndex in 0 until displayCount) {
              EditorPageCanvas(
                file = file,
                entry = pageEntries[currentPageIndex],
                activeTool = activeTool,
                enhance = enhanceEnabled,
                overlays = pageList(),
                selectedId = selectedId,
                onAddOverlay = { newObj -> addToPage(listOf(newObj)) },
                onEditTarget = { target -> editTarget = target },
                onRegionSelected = { target ->
                  val area = target.coverBounds
                  if (activeTool == ActiveEditorTool.REDACT && area != null) {
                    commitState(
                      sessionState.copy(
                        pageOverlays = sessionState.pageOverlays +
                          (currentPageIndex to (pageList() + RedactOverlay(bounds = RectF(area)))),
                        flattenedPages = sessionState.flattenedPages + currentPageIndex
                      )
                    )
                  } else {
                    editTarget = target
                  }
                },
                onSelect = { id -> selectedId = id },
                onCommitOverlay = { obj -> replaceOnPage(obj) },
                onPageMetrics = { w, h -> pageDims = Pair(w, h) }
              )
            }
          }

          // Actions for the selected object (edit / crop / duplicate / delete / done)
          val selectedObj = pageList().firstOrNull { it.id == selectedId }
          if (selectedObj != null) {
            SelectionBar(
              selected = selectedObj,
              onEdit = {
                if (selectedObj is TextOverlay) {
                  editTarget = editTargetForText(selectedObj, pageDims.first, pageDims.second)
                } else if (selectedObj is ImageOverlay) {
                  cropTarget = selectedObj
                }
              },
              onDuplicate = {
                val copy = selectedObj.withNewId()
                val moved = withBounds(
                  copy,
                  RectF(
                    (selectedObj.bounds.left + 0.03f).coerceAtMost(1f - selectedObj.bounds.width()),
                    (selectedObj.bounds.top + 0.03f).coerceAtMost(1f - selectedObj.bounds.height()),
                    (selectedObj.bounds.left + 0.03f).coerceAtMost(1f - selectedObj.bounds.width()) + selectedObj.bounds.width(),
                    (selectedObj.bounds.top + 0.03f).coerceAtMost(1f - selectedObj.bounds.height()) + selectedObj.bounds.height()
                  )
                )
                addToPage(listOf(if (copy is DrawOverlay) copy else moved))
                selectedId = copy.id
              },
              onDelete = { deleteFromPage(selectedObj.id) },
              onDone = { selectedId = null }
            )
          }

          // Bottom: Tool Selector Strip
          Surface(
            modifier = Modifier.fillMaxWidth(),
            tonalElevation = 4.dp,
            color = MaterialTheme.colorScheme.surface
          ) {
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 8.dp, vertical = 6.dp),
              horizontalArrangement = Arrangement.spacedBy(6.dp),
              verticalAlignment = Alignment.CenterVertically
            ) {
              FilterChip(
                selected = activeTool == ActiveEditorTool.MOVE_SELECT,
                onClick = { activeTool = ActiveEditorTool.MOVE_SELECT },
                label = { Text(stringResource(R.string.tool21_tool_select)) },
                leadingIcon = { Icon(Icons.Default.PanTool, contentDescription = null, modifier = Modifier.size(16.dp)) }
              )
              FilterChip(
                selected = activeTool == ActiveEditorTool.TEXT,
                onClick = { activeTool = ActiveEditorTool.TEXT },
                label = { Text(stringResource(R.string.tool21_tool_text)) },
                leadingIcon = { Icon(Icons.Default.TextFields, contentDescription = null, modifier = Modifier.size(16.dp)) }
              )
              FilterChip(
                selected = activeTool == ActiveEditorTool.DRAW_PEN,
                onClick = { activeTool = ActiveEditorTool.DRAW_PEN },
                label = { Text(stringResource(R.string.tool21_tool_pen)) },
                leadingIcon = { Icon(Icons.Default.Draw, contentDescription = null, modifier = Modifier.size(16.dp)) }
              )
              FilterChip(
                selected = activeTool == ActiveEditorTool.DRAW_HIGHLIGHTER,
                onClick = { activeTool = ActiveEditorTool.DRAW_HIGHLIGHTER },
                label = { Text(stringResource(R.string.tool21_tool_highlighter)) },
                leadingIcon = { Icon(Icons.Default.Highlight, contentDescription = null, modifier = Modifier.size(16.dp)) }
              )
              FilterChip(
                selected = activeTool == ActiveEditorTool.SIGNATURE,
                onClick = {
                  activeTool = ActiveEditorTool.SIGNATURE
                  showSignatureDialog = true
                },
                label = { Text(stringResource(R.string.tool21_tool_signature)) },
                leadingIcon = { Icon(Icons.Default.Gesture, contentDescription = null, modifier = Modifier.size(16.dp)) }
              )
              FilterChip(
                selected = activeTool == ActiveEditorTool.IMAGE,
                onClick = {
                  activeTool = ActiveEditorTool.IMAGE
                  imagePickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                },
                label = { Text(stringResource(R.string.tool21_tool_image)) },
                leadingIcon = { Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(16.dp)) }
              )
              FilterChip(
                selected = activeTool == ActiveEditorTool.COVER_REPLACE,
                onClick = { activeTool = ActiveEditorTool.COVER_REPLACE },
                label = { Text(stringResource(R.string.tool21_tool_cover)) },
                leadingIcon = { Icon(Icons.Default.Layers, contentDescription = null, modifier = Modifier.size(16.dp)) }
              )
              FilterChip(
                selected = activeTool == ActiveEditorTool.REDACT,
                onClick = { activeTool = ActiveEditorTool.REDACT },
                label = { Text(stringResource(R.string.tool21_tool_redact)) },
                leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp)) }
              )
              FilterChip(
                selected = activeTool == ActiveEditorTool.PAGE_TOOLS,
                onClick = { activeTool = ActiveEditorTool.PAGE_TOOLS },
                label = { Text(stringResource(R.string.tool21_tool_pages)) },
                leadingIcon = { Icon(Icons.Default.Pages, contentDescription = null, modifier = Modifier.size(16.dp)) }
              )
            }
          }

          // Page management sub-bar if PAGE_TOOLS selected
          if (activeTool == ActiveEditorTool.PAGE_TOOLS) {
            Surface(
              modifier = Modifier.fillMaxWidth(),
              color = MaterialTheme.colorScheme.surfaceVariant
            ) {
              Row(
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceAround
              ) {
                IconButton(
                  onClick = {
                    if (pageList().isNotEmpty()) {
                      Toast.makeText(context, context.getString(R.string.tool21_rotate_blocked), Toast.LENGTH_SHORT).show()
                    } else {
                      applyPageOp(PageOp.RotatePage(currentPageIndex, 90))
                    }
                  }
                ) {
                  Icon(Icons.Default.RotateRight, contentDescription = stringResource(R.string.tool21_page_rotate))
                }
                IconButton(
                  onClick = { if (displayCount > 1) applyPageOp(PageOp.DeletePage(currentPageIndex)) },
                  enabled = displayCount > 1
                ) {
                  Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.tool21_page_delete))
                }
                IconButton(
                  onClick = {
                    applyPageOp(PageOp.DuplicatePage(currentPageIndex))
                    currentPageIndex += 1
                  }
                ) {
                  Icon(Icons.Default.ContentCopy, contentDescription = stringResource(R.string.tool21_page_duplicate))
                }
                IconButton(
                  onClick = {
                    applyPageOp(PageOp.InsertBlankPage(currentPageIndex))
                    currentPageIndex += 1
                  }
                ) {
                  Icon(Icons.Default.Add, contentDescription = stringResource(R.string.tool21_page_insert))
                }
              }
            }
          }
        }
      }
    }
  }

  // Text insertion dialog
  editTarget?.let { target ->
    TextEditDialog(
      target = target,
      onDismiss = { editTarget = null },
      onConfirm = { result ->
        val newObjs = buildEditOverlays(target, result)
        if (newObjs.isNotEmpty()) {
          if (target.existingId != null) {
            replaceOnPage(newObjs.first())
            selectedId = target.existingId
          } else {
            addToPage(newObjs)
          }
        }
        editTarget = null
      }
    )
  }

  cropTarget?.let { img ->
    ImageCropDialog(
      image = img,
      onDismiss = { cropTarget = null },
      onCropped = { updated ->
        replaceOnPage(updated)
        cropTarget = null
      }
    )
  }

  if (showSignatureDialog) {
    SignaturePadDialog(
      onDismiss = { showSignatureDialog = false },
      onSignatureSaved = { sigFile ->
        val dims = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
        android.graphics.BitmapFactory.decodeFile(sigFile.absolutePath, dims)
        val newObj = SignatureOverlay(
          bounds = centeredBounds(dims.outWidth, dims.outHeight, 0.4f),
          signaturePngPath = sigFile.absolutePath
        )
        addToPage(listOf(newObj))
        selectedId = newObj.id
        activeTool = ActiveEditorTool.MOVE_SELECT
        showSignatureDialog = false
      }
    )
  }

  // Unsaved changes confirmation dialog
  if (showUnsavedDialog) {
    AlertDialog(
      onDismissRequest = { showUnsavedDialog = false },
      title = { Text(stringResource(R.string.tool21_unsaved_title)) },
      text = { Text(stringResource(R.string.tool21_unsaved_desc)) },
      confirmButton = {
        Button(
          onClick = {
            showUnsavedDialog = false
            onBack()
          }
        ) {
          Text(stringResource(R.string.common_discard))
        }
      },
      dismissButton = {
        TextButton(onClick = { showUnsavedDialog = false }) { Text(stringResource(R.string.common_cancel)) }
      }
    )
  }
}

@Composable
private fun SignaturePadDialog(
  onDismiss: () -> Unit,
  onSignatureSaved: (File) -> Unit
) {
  val context = LocalContext.current
  val points = remember { mutableStateListOf<Offset>() }
  var strokeColor by remember { mutableStateOf(Color.Black) }

  Dialog(
    onDismissRequest = onDismiss,
    properties = DialogProperties(usePlatformDefaultWidth = false)
  ) {
    Surface(
      modifier = Modifier.fillMaxSize(),
      color = MaterialTheme.colorScheme.surface
    ) {
      Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Text(
            text = stringResource(R.string.tool21_signature_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
          )
          IconButton(onClick = onDismiss) {
            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.tool21_close))
          }
        }

        Box(
          modifier = Modifier
            .weight(1f)
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White)
            .border(2.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
            .pointerInput(Unit) {
              awaitEachGesture {
                do {
                  val event = awaitPointerEvent()
                  event.changes.forEach { change ->
                    points.add(change.position)
                    change.consume()
                  }
                } while (event.changes.any { it.pressed })
              }
            }
        ) {
          Canvas(modifier = Modifier.fillMaxSize()) {
            for (i in 0 until points.size - 1) {
              drawLine(
                color = strokeColor,
                start = points[i],
                end = points[i + 1],
                strokeWidth = 4f
              )
            }
          }
        }

        Spacer(modifier = Modifier.height(12.dp))
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(onClick = { points.clear() }) {
              Text(stringResource(R.string.tool21_signature_clear))
            }
            FilterChip(
              selected = strokeColor == Color.Black,
              onClick = { strokeColor = Color.Black },
              label = { Text("Black") }
            )
            FilterChip(
              selected = strokeColor == Color(0xFF003366),
              onClick = { strokeColor = Color(0xFF003366) },
              label = { Text("Blue") }
            )
          }

          Button(
            onClick = {
              if (points.isNotEmpty()) {
                val tempDir = FileLayer.getTempFolder(context)
                val sigFile = File(tempDir, "signature_${UUID.randomUUID()}.png")
                val bmp = Bitmap.createBitmap(800, 400, Bitmap.Config.ARGB_8888)
                val canvas = android.graphics.Canvas(bmp)
                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                  color = if (strokeColor == Color.Black) Color.Black.hashCode() else 0xFF003366.toInt()
                  strokeWidth = 6f
                }
                for (i in 0 until points.size - 1) {
                  canvas.drawLine(points[i].x, points[i].y, points[i + 1].x, points[i + 1].y, paint)
                }
                FileOutputStream(sigFile).use { out ->
                  bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
                }
                bmp.recycle()
                onSignatureSaved(sigFile)
              }
            },
            enabled = points.isNotEmpty()
          ) {
            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(stringResource(R.string.common_done))
          }
        }
      }
    }
  }
}
