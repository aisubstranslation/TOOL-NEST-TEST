package com.example.tools.aiinspect

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.graphics.Color
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
import com.example.file.FileNameSanitizer
import com.example.tools.common.FilePickerCard
import com.example.tools.common.FileResult
import com.example.tools.common.JobResult
import com.example.tools.common.JobState
import com.example.tools.common.PickerMode
import com.example.tools.common.ProgressCard
import com.example.tools.common.ToolJobRunner
import com.example.tools.common.ToolScaffold
import com.example.tools.common.ZoomableImageDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

const val TOOL_ID_AI_INSPECTOR = 22

@Composable
fun AiImageInspectorScreen(
  onBack: () -> Unit,
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()

  val selectedUris = remember { mutableStateListOf<Uri>() }
  val inspectionResults = remember { mutableStateListOf<ImageInspectionResult>() }
  var generatedReportFile by remember { mutableStateOf<File?>(null) }
  var currentJobId by rememberSaveable { mutableStateOf<String?>(null) }

  val jobUi by remember(currentJobId) {
    if (currentJobId != null) ToolJobRunner.observe(currentJobId!!)
    else flowOf(null)
  }.collectAsState(initial = null)

  // SAF Document creator for saving the text report
  val saveReportLauncher = rememberLauncherForActivityResult(
    ActivityResultContracts.CreateDocument("text/plain")
  ) { destUri ->
    val report = generatedReportFile
    if (destUri != null && report != null && report.exists()) {
      val saved = FileLayer.saveFileToUri(context, report, destUri)
      if (saved) {
        Toast.makeText(context, context.getString(R.string.file_saved_success), Toast.LENGTH_SHORT).show()
      }
    }
  }

  fun executeInspection() {
    if (selectedUris.isEmpty()) return
    val urisCopy = selectedUris.toList()

    val jobId = ToolJobRunner.start(
      toolId = TOOL_ID_AI_INSPECTOR,
      inputName = "${urisCopy.size} images",
      optionsJson = "{}",
      inputUri = urisCopy.first().toString(),
      inputSize = 0L
    ) {
      reportProgress(percent = 5, stage = "Preparing analysis…")
      val tempDir = FileLayer.getTempFolder(context)
      val results = mutableListOf<ImageInspectionResult>()

      for ((index, uri) in urisCopy.withIndex()) {
        ensureActive()
        val pct = 10 + (80 * index / urisCopy.size)
        reportProgress(percent = pct, stage = "Analyzing image ${index + 1}/${urisCopy.size}…")

        try {
          val tempFile = File(tempDir, "inspect_${UUID.randomUUID()}.tmp")
          context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(tempFile).use { output -> input.copyTo(output) }
          }
          val uriInfo = FileLayer.getUriFileInfo(context, uri)
          val result = AiTraceAnalyzer.analyzeBytes(
            bytes = tempFile.readBytes(),
            fileName = uriInfo.displayName,
            totalSizeBytes = uriInfo.sizeBytes,
            backingFile = tempFile
          )
          results.add(result)
        } catch (_: Exception) {}
      }

      reportProgress(percent = 92, stage = "Compiling forensic report…")
      val reportText = AiInspectorReportBuilder.generatePlainTextReport(results)
      val reportFile = File(tempDir, "ai_inspector_report_${System.currentTimeMillis()}.txt")
      reportFile.writeText(reportText)

      withContext(Dispatchers.Main) {
        inspectionResults.clear()
        inspectionResults.addAll(results)
        generatedReportFile = reportFile
      }

      JobResult(
        files = listOf(
          FileResult(
            inputName = "AI Forensic Report",
            outputName = reportFile.name,
            outputUri = reportFile.absolutePath,
            outputSizeBytes = reportFile.length(),
            success = true,
            userMessage = "${results.size} images analyzed",
            format = "TXT",
            tempFilePath = reportFile.absolutePath
          )
        )
      )
    }
    currentJobId = jobId
  }

  ToolScaffold(
    title = stringResource(R.string.tool22_name),
    onBack = onBack,
    modifier = modifier
  ) { padding ->
    LazyColumn(
      modifier = Modifier
        .fillMaxSize()
        .padding(padding)
        .padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
      if (inspectionResults.isEmpty() && (jobUi == null || jobUi!!.state != JobState.RUNNING)) {
        // Picker Card
        item {
          FilePickerCard(
            title = stringResource(R.string.tool22_picker_title),
            supportedFormatsText = stringResource(R.string.tool22_picker_desc),
            pickerMode = PickerMode.PHOTO_PICKER,
            maxFiles = 10,
            mimeTypeFilter = arrayOf("image/*"),
            onFilesSelected = { uris ->
              selectedUris.clear()
              selectedUris.addAll(uris.take(10))
            },
            modifier = Modifier.testTag("ai_inspector_picker")
          )
        }

        if (selectedUris.isNotEmpty()) {
          item {
            Card(
              modifier = Modifier.fillMaxWidth(),
              colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
              Column(modifier = Modifier.padding(12.dp)) {
                Text(
                  text = "${selectedUris.size} images selected for inspection",
                  style = MaterialTheme.typography.bodyMedium,
                  fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                  onClick = { executeInspection() },
                  modifier = Modifier.fillMaxWidth().testTag("ai_inspect_start_btn")
                ) {
                  Icon(Icons.Default.Security, contentDescription = null, modifier = Modifier.size(18.dp))
                  Spacer(modifier = Modifier.width(6.dp))
                  Text(stringResource(R.string.tool22_btn_analyze))
                }
              }
            }
          }
        }
      }

      // Progress Card
      if (jobUi != null && jobUi!!.state == JobState.RUNNING) {
        item {
          ProgressCard(
            stage = jobUi!!.stage,
            progressPercent = jobUi!!.progressPercent,
            currentIndex = jobUi!!.currentIndex,
            totalFiles = jobUi!!.totalFiles,
            currentFileName = jobUi!!.currentFileName,
            onCancel = { currentJobId?.let { ToolJobRunner.cancel(it) } },
            modifier = Modifier.testTag("ai_inspector_progress")
          )
        }
      }

      // Results Section
      if (inspectionResults.isNotEmpty()) {
        item {
          // Action buttons bar
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            Button(
              onClick = { saveReportLauncher.launch("ai_inspector_report.txt") },
              modifier = Modifier.weight(1f)
            ) {
              Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(18.dp))
              Spacer(Modifier.width(6.dp))
              Text(stringResource(R.string.tool22_save_report))
            }
            val rFile = generatedReportFile
            if (rFile != null && rFile.exists()) {
              OutlinedButton(
                onClick = { FileLayer.shareFileAndNotify(context, rFile, "text/plain") }
              ) {
                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
              }
            }
            OutlinedButton(
              onClick = {
                inspectionResults.clear()
                selectedUris.clear()
                generatedReportFile = null
                currentJobId = null
              }
            ) {
              Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
            }
          }
        }

        items(inspectionResults) { result ->
          InspectionResultCard(result)
        }
      }
    }
  }
}

@Composable
private fun InspectionResultCard(result: ImageInspectionResult) {
  val context = LocalContext.current
  var showElaHeatmap by remember { mutableStateOf(false) }
  var showZoomViewer by remember { mutableStateOf(false) }

  val (verdictColor, verdictIcon) = when (result.verdict) {
    AiVerdict.STRONG -> MaterialTheme.colorScheme.error to Icons.Default.Warning
    AiVerdict.EDITED -> MaterialTheme.colorScheme.tertiary to Icons.Default.Info
    AiVerdict.POSSIBLE -> MaterialTheme.colorScheme.secondary to Icons.Default.HelpOutline
    AiVerdict.NONE -> MaterialTheme.colorScheme.primary to Icons.Default.CheckCircle
  }

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
      verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
      // Header: File Name & Size
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text(
          text = result.inputName,
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold,
          modifier = Modifier.weight(1f),
          maxLines = 1,
          overflow = TextOverflow.Ellipsis
        )
        Text(
          text = FileFormatters.formatBytes(result.fileSizeBytes),
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }

      // Verdict Chip
      Surface(
        color = verdictColor.copy(alpha = 0.12f),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth()
      ) {
        Row(
          modifier = Modifier.padding(10.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          Icon(verdictIcon, contentDescription = null, tint = verdictColor, modifier = Modifier.size(20.dp))
          Text(
            text = result.verdictTitle,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = verdictColor
          )
        }
      }

      Text(
        text = result.verdictDescription,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )

      // EXIF Summary Row
      if (result.cameraMakeModel != null || result.software != null) {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
            .padding(8.dp),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          if (result.cameraMakeModel != null) {
            Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(16.dp))
            Text(result.cameraMakeModel, style = MaterialTheme.typography.labelSmall)
          }
          if (result.software != null) {
            Text("•", style = MaterialTheme.typography.labelSmall)
            Text(result.software, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
          }
        }
      }

      // Extracted Prompt Block
      if (result.extractedPrompt != null) {
        Surface(
          color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
          shape = RoundedCornerShape(8.dp),
          modifier = Modifier.fillMaxWidth()
        ) {
          Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Text(
                text = "Detected Generation Parameters",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
              )
              IconButton(
                onClick = {
                  val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                  cm.setPrimaryClip(ClipData.newPlainText("AI Prompt", result.extractedPrompt))
                  Toast.makeText(context, "Prompt copied to clipboard", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.size(24.dp)
              ) {
                Icon(Icons.Default.ContentCopy, contentDescription = "Copy Prompt", modifier = Modifier.size(16.dp))
              }
            }
            Text(
              text = result.extractedPrompt,
              style = MaterialTheme.typography.bodySmall,
              maxLines = 4,
              overflow = TextOverflow.Ellipsis
            )
          }
        }
      }

      // Evidence list
      if (result.evidenceList.isNotEmpty()) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
          Text(
            text = "Forensic Traces:",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold
          )
          for (ev in result.evidenceList) {
            val evColor = when (ev.strength) {
              EvidenceStrength.STRONG -> MaterialTheme.colorScheme.error
              EvidenceStrength.MEDIUM -> MaterialTheme.colorScheme.tertiary
              EvidenceStrength.WEAK -> MaterialTheme.colorScheme.onSurfaceVariant
            }
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
              Text("•", color = evColor, fontWeight = FontWeight.Bold)
              Text(
                text = "${ev.sourceField}: ${ev.value}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface
              )
            }
          }
        }
      }

      // Error Level Analysis (ELA) section
      if (result.elaHeatmapPath != null) {
        val heatmapFile = File(result.elaHeatmapPath)
        if (heatmapFile.exists()) {
          Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Text("Error Level Analysis (ELA)", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
              FilterChip(
                selected = showElaHeatmap,
                onClick = { showElaHeatmap = !showElaHeatmap },
                label = { Text(if (showElaHeatmap) "Heatmap" else "Original") }
              )
            }

            if (showElaHeatmap) {
              val heatBmp = remember(heatmapFile) { BitmapFactory.decodeFile(heatmapFile.absolutePath) }
              if (heatBmp != null) {
                Box(
                  modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.Black)
                    .clickable { showZoomViewer = true },
                  contentAlignment = Alignment.Center
                ) {
                  Image(
                    bitmap = heatBmp.asImageBitmap(),
                    contentDescription = "ELA Heatmap",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                  )
                }
                Text(
                  text = "ELA highlights areas compressed differently (possible edits/pasted regions). It is a hint, not proof.",
                  style = MaterialTheme.typography.labelSmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant
                )
              }
            }
          }

          if (showZoomViewer) {
            ZoomableImageDialog(
              file = heatmapFile,
              title = "${result.inputName} ELA Heatmap",
              onDismiss = { showZoomViewer = false }
            )
          }
        }
      }
    }
  }
}
