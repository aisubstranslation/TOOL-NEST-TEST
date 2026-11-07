package com.example.tools.image

import android.graphics.Color
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import com.example.tools.common.FileSelectionHelper
import com.example.tools.common.SelectedFileListSaver
import kotlinx.coroutines.launch

@Composable
fun ImageConverterScreen(
  onBack: () -> Unit,
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current
  val coroutineScope = rememberCoroutineScope()
  val imageSettings = (context.applicationContext as ToolsNestApp).imageSettingsDataStore

  val defaultQuality by imageSettings.defaultQualityFlow.collectAsState(initial = 85)
  val defaultFormat by imageSettings.defaultFormatFlow.collectAsState(initial = OutputFormat.PNG)

  val selectedFiles = rememberSaveable(saver = SelectedFileListSaver) { mutableStateListOf<SelectedFileItem>() }
  var pickerErrorMessage by rememberSaveable { mutableStateOf<String?>(null) }
  var targetFormat by remember(defaultFormat) { mutableStateOf(defaultFormat) }
  var qualitySlider by remember(defaultQuality) { mutableFloatStateOf(defaultQuality.toFloat()) }

  // Background color palette
  var selectedBgColor by remember { mutableIntStateOf(Color.WHITE) }
  var hexInput by remember { mutableStateOf("#FFFFFF") }

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
    title = stringResource(R.string.tool3_name),
    onBack = onBack,
    modifier = modifier
  ) { padding ->
    LazyColumn(
      modifier = Modifier
        .fillMaxSize()
        .padding(padding)
        .testTag("tool_screen_3"),
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
            message = jobUi?.errorMessage ?: jobUi?.stage ?: "Conversion failed",
            recoveryHint = "Try a different target format or verify input files.",
            onRetry = { currentJobId = null }
          )
        }
      }
      // 4. Configuration State
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
            supportedFormatsText = stringResource(R.string.tool3_supported_formats)
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
                // Target Format
                Text(
                  text = stringResource(R.string.tool3_select_format),
                  style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                )

                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                  OutputFormat.entries.forEach { fmt ->
                    Row(
                      verticalAlignment = Alignment.CenterVertically,
                      modifier = Modifier.clickable { targetFormat = fmt }
                    ) {
                      RadioButton(
                        selected = targetFormat == fmt,
                        onClick = { targetFormat = fmt }
                      )
                      Text(
                        text = fmt.name,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(start = 4.dp)
                      )
                    }
                  }
                }

                // Quality Slider for JPEG / WebP
                if (targetFormat != OutputFormat.PNG) {
                  Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                      modifier = Modifier.fillMaxWidth(),
                      horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                      Text(
                        text = stringResource(R.string.tool3_quality, qualitySlider.toInt()),
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
                      )
                    }
                    Slider(
                      value = qualitySlider,
                      onValueChange = { qualitySlider = it },
                      valueRange = 10f..100f,
                      steps = 17
                    )
                  }
                }

                // Background Color for JPEG (when converting transparency)
                if (targetFormat == OutputFormat.JPEG) {
                  Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                      text = stringResource(R.string.tool3_bg_color),
                      style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
                    )
                    Text(
                      text = stringResource(R.string.tool3_bg_color_note),
                      style = MaterialTheme.typography.bodySmall,
                      color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // Palette circles
                    val palette = listOf(
                      Color.WHITE to "#FFFFFF",
                      Color.BLACK to "#000000",
                      Color.GRAY to "#808080",
                      Color.parseColor("#FFF7ED") to "#FFF7ED",
                      Color.parseColor("#EFF6FF") to "#EFF6FF"
                    )

                    Row(
                      horizontalArrangement = Arrangement.spacedBy(12.dp),
                      verticalAlignment = Alignment.CenterVertically
                    ) {
                      palette.forEach { (colorInt, hex) ->
                        Box(
                          modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(androidx.compose.ui.graphics.Color(colorInt))
                            .border(
                              width = if (selectedBgColor == colorInt) 3.dp else 1.dp,
                              color = if (selectedBgColor == colorInt) MaterialTheme.colorScheme.primary else androidx.compose.ui.graphics.Color.Gray,
                              shape = CircleShape
                            )
                            .clickable {
                              selectedBgColor = colorInt
                              hexInput = hex
                            }
                        )
                      }
                    }

                    OutlinedTextField(
                      value = hexInput,
                      onValueChange = { input ->
                        hexInput = input
                        runCatching {
                          val parsed = Color.parseColor(if (input.startsWith("#")) input else "#$input")
                          selectedBgColor = parsed
                        }
                      },
                      label = { Text(stringResource(R.string.tool3_color_custom)) },
                      singleLine = true,
                      shape = RoundedCornerShape(10.dp),
                      modifier = Modifier.fillMaxWidth()
                    )
                  }
                }

                // Start Convert Button
                Button(
                  enabled = selectedFiles.isNotEmpty() && !ToolJobRunner.isToolBusy(3),
                  onClick = {
                    val filesToProcess = selectedFiles.toList()
                    val fmt = targetFormat
                    val q = qualitySlider.toInt()
                    val bg = selectedBgColor

                    val jobId = ToolJobRunner.start(
                      toolId = 3,
                      inputName = filesToProcess.firstOrNull()?.name ?: "Images",
                      optionsJson = "targetFormat=$fmt,quality=$q"
                    ) {
                      val tempDir = FileLayer.getTempFolder(context)
                      val results = mutableListOf<FileResult>()

                      filesToProcess.forEachIndexed { index, item ->
                        reportProgress(
                          percent = ((index.toFloat() / filesToProcess.size) * 100).toInt(),
                          stage = "Converting ${item.name} to ${fmt.name}...",
                          currentIndex = index + 1,
                          totalFiles = filesToProcess.size,
                          currentFileName = item.name
                        )

                        val res = ImageEngine.processImage(
                          context = context,
                          uri = item.uri,
                          originalName = item.name,
                          options = ProcessImageOptions(
                            format = fmt,
                            quality = q,
                            backgroundColor = bg
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
                    .testTag("start_convert_button"),
                  shape = RoundedCornerShape(12.dp)
                ) {
                  Text(
                    text = stringResource(R.string.tool3_start_button),
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
