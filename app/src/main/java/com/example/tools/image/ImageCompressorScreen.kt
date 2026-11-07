package com.example.tools.image

import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.ToolsNestApp
import com.example.engine.ImageEngine
import com.example.engine.OutputFormat
import com.example.engine.ProcessImageOptions
import com.example.file.FileLayer
import com.example.tools.common.ErrorCard
import com.example.tools.common.FilePickerCard
import com.example.tools.common.FileResult
import com.example.tools.common.JobResult
import com.example.tools.common.JobState
import com.example.tools.common.ProgressCard
import com.example.tools.common.ResultCard
import com.example.tools.common.SelectedFileItem
import com.example.tools.common.SelectedFileRow
import com.example.tools.common.ToolJobRunner
import com.example.tools.common.ToolScaffold

import androidx.compose.runtime.saveable.rememberSaveable
import com.example.tools.common.FileSelectionHelper
import com.example.tools.common.SelectedFileListSaver
import kotlinx.coroutines.launch

@Composable
fun ImageCompressorScreen(
  onBack: () -> Unit,
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current
  val coroutineScope = rememberCoroutineScope()
  val imageSettings = (context.applicationContext as ToolsNestApp).imageSettingsDataStore

  val defaultQuality by imageSettings.defaultQualityFlow.collectAsState(initial = 80)
  val defaultFormat by imageSettings.defaultFormatFlow.collectAsState(initial = OutputFormat.JPEG)

  val selectedFiles = rememberSaveable(saver = SelectedFileListSaver) { mutableStateListOf<SelectedFileItem>() }
  var pickerErrorMessage by rememberSaveable { mutableStateOf<String?>(null) }
  var outputFormat by remember(defaultFormat) { mutableStateOf(defaultFormat) }
  var qualitySlider by remember(defaultQuality) { mutableFloatStateOf(defaultQuality.toFloat()) }
  var isResizeEnabled by remember { mutableStateOf(false) }
  var resizeScalePercent by remember { mutableFloatStateOf(75f) }
  var customNameInput by remember { mutableStateOf("") }

  var currentJobId by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf<String?>(null) }
  val jobUi by remember(currentJobId) {
    if (currentJobId != null) ToolJobRunner.observe(currentJobId!!)
    else kotlinx.coroutines.flow.flowOf(null)
  }.collectAsState(initial = null)

  LaunchedEffect(currentJobId) {
    val id = currentJobId
    if (id != null && !ToolJobRunner.hasJob(id)) {
      currentJobId = null
      pickerErrorMessage = context.getString(R.string.common_job_restored_lost)
    }
  }

  ToolScaffold(
    title = stringResource(R.string.tool1_name),
    onBack = onBack,
    modifier = modifier
  ) { padding ->
    LazyColumn(
      modifier = Modifier
        .fillMaxSize()
        .padding(padding)
        .testTag("tool_screen_1"),
      contentPadding = PaddingValues(16.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
      // 1. Result State
      if (jobUi != null && jobUi?.state == JobState.SUCCESS && jobUi?.result != null) {
        val files = jobUi!!.result!!.files
        items(files) { fileResult ->
          ResultCard(
            fileResult = fileResult,
            onProcessAnother = {
              currentJobId = null
              selectedFiles.clear()
            },
            onDone = onBack
          )
        }
      }
      // 2. Running State
      else if (jobUi != null && jobUi?.state == JobState.RUNNING) {
        item {
          ProgressCard(
            stage = jobUi!!.stage,
            progressPercent = jobUi!!.progressPercent,
            currentIndex = jobUi!!.currentIndex,
            totalFiles = jobUi!!.totalFiles,
            currentFileName = jobUi!!.currentFileName,
            onCancel = { currentJobId?.let { ToolJobRunner.cancel(it) } }
          )
        }
      }
      // 3. Error / Cancelled State
      else if (jobUi != null && (jobUi?.state == JobState.FAILED || jobUi?.state == JobState.CANCELLED)) {
        item {
          ErrorCard(
            message = jobUi?.errorMessage ?: jobUi?.stage ?: "Processing failed",
            recoveryHint = "Try adjusting compression settings or select fewer images.",
            onRetry = { currentJobId = null }
          )
        }
      }
      // 4. Input Configuration State
      else {
        // Picker
        item {
          FilePickerCard(
            onFilesSelected = { uris ->
              coroutineScope.launch {
                pickerErrorMessage = null
                val result = FileSelectionHelper.loadSelectedImages(
                  context = context,
                  uris = uris,
                  currentItems = selectedFiles,
                  onFileError = { name, err ->
                    pickerErrorMessage = "$name: $err"
                  }
                )
                selectedFiles.clear()
                selectedFiles.addAll(result.updatedList)

                if (result.overflowCount > 0) {
                  pickerErrorMessage = context.getString(R.string.common_max_images_reached)
                }
              }
            },
            maxFiles = 20,
            supportedFormatsText = stringResource(R.string.tool1_supported_formats)
          )
        }

        // Picker Error Card if any file failed to load
        if (pickerErrorMessage != null) {
          item {
            ErrorCard(
              message = pickerErrorMessage!!,
              recoveryHint = stringResource(R.string.common_images_recovery_hint),
              onRetry = { pickerErrorMessage = null }
            )
          }
        }

        // Selected Files List
        if (selectedFiles.isNotEmpty()) {
          items(selectedFiles) { fileItem ->
            SelectedFileRow(
              fileItem = fileItem,
              onRemove = { selectedFiles.remove(fileItem) }
            )
          }

          // Options Card
          item {
            Card(
              modifier = Modifier.fillMaxWidth(),
              shape = RoundedCornerShape(16.dp),
              colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
              elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
              Column(
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
              ) {
                // Output Format
                Text(
                  text = stringResource(R.string.tool1_output_format),
                  style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                )

                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                  OutputFormat.entries.forEach { fmt ->
                    Row(
                      verticalAlignment = Alignment.CenterVertically,
                      modifier = Modifier.clickable { outputFormat = fmt }
                    ) {
                      RadioButton(
                        selected = outputFormat == fmt,
                        onClick = { outputFormat = fmt }
                      )
                      Text(
                        text = fmt.name,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(start = 4.dp)
                      )
                    }
                  }
                }

                // Quality Slider (disabled for PNG)
                val isQualityApplicable = outputFormat != OutputFormat.PNG
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                  Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                  ) {
                    Text(
                      text = stringResource(R.string.tool1_quality_label, qualitySlider.toInt()),
                      style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                      color = if (isQualityApplicable) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                  }

                  Slider(
                    value = qualitySlider,
                    onValueChange = { qualitySlider = it },
                    valueRange = 10f..100f,
                    steps = 17,
                    enabled = isQualityApplicable
                  )

                  Text(
                    text = if (isQualityApplicable) {
                      stringResource(R.string.tool1_quality_hint)
                    } else {
                      stringResource(R.string.tool1_png_note)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                  )
                }

                // Optional Resize Switch
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                  Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                  ) {
                    Column(modifier = Modifier.weight(1f)) {
                      Text(
                        text = stringResource(R.string.tool1_resize_toggle),
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
                      )
                      Text(
                        text = stringResource(R.string.tool1_resize_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                      )
                    }
                    Switch(
                      checked = isResizeEnabled,
                      onCheckedChange = { isResizeEnabled = it },
                      modifier = Modifier.testTag("resize_switch")
                    )
                  }

                  if (isResizeEnabled) {
                    Text(
                      text = "Scale: ${resizeScalePercent.toInt()}%",
                      style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                      color = MaterialTheme.colorScheme.primary
                    )
                    Slider(
                      value = resizeScalePercent,
                      onValueChange = { resizeScalePercent = it },
                      valueRange = 20f..95f,
                      steps = 14
                    )
                  }
                }

                // Optional custom output name
                OutlinedTextField(
                  value = customNameInput,
                  onValueChange = { customNameInput = it },
                  label = { Text(stringResource(R.string.tool1_custom_name)) },
                  singleLine = true,
                  shape = RoundedCornerShape(12.dp),
                  modifier = Modifier
                    .fillMaxWidth()
                    .testTag("custom_name_input")
                )

                // Start Compress Button
                Button(
                  enabled = selectedFiles.isNotEmpty() && !ToolJobRunner.isToolBusy(1),
                  onClick = {
                    val filesToProcess = selectedFiles.toList()
                    val targetFormat = outputFormat
                    val targetQuality = qualitySlider.toInt()
                    val doResize = isResizeEnabled
                    val scalePct = resizeScalePercent.toInt()
                    val customName = customNameInput.trim().ifEmpty { null }

                    val jobId = ToolJobRunner.start(
                      toolId = 1,
                      inputName = filesToProcess.firstOrNull()?.name ?: "Images",
                      optionsJson = "quality=$targetQuality,format=$targetFormat"
                    ) {
                      val tempDir = FileLayer.getTempFolder(context)
                      val results = mutableListOf<FileResult>()

                      filesToProcess.forEachIndexed { index, item ->
                        reportProgress(
                          percent = ((index.toFloat() / filesToProcess.size) * 100).toInt(),
                          stage = "Compressing ${item.name}...",
                          currentIndex = index + 1,
                          totalFiles = filesToProcess.size,
                          currentFileName = item.name
                        )

                        val targetW = if (doResize && item.width != null) (item.width * (scalePct / 100.0)).toInt() else null
                        val targetH = if (doResize && item.height != null) (item.height * (scalePct / 100.0)).toInt() else null

                        val res = ImageEngine.processImage(
                          context = context,
                          uri = item.uri,
                          originalName = item.name,
                          options = ProcessImageOptions(
                            format = targetFormat,
                            quality = targetQuality,
                            targetWidth = targetW,
                            targetHeight = targetH,
                            customOutputName = customName
                          ),
                          jobContext = this,
                          targetCacheDir = tempDir
                        )
                        results.add(res)
                      }

                      JobResult(files = results)
                    }

                    currentJobId = jobId
                  },
                  modifier = Modifier
                    .fillMaxWidth()
                    .testTag("start_compress_button"),
                  shape = RoundedCornerShape(12.dp)
                ) {
                  Text(
                    text = stringResource(R.string.tool1_start_button),
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                  )
                }
              }
            }
          }
        }
      }
    }
  }
}
