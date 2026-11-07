package com.example.tools.image

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.engine.ImageEngine
import com.example.engine.ImageMath
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ImageResizerScreen(
  onBack: () -> Unit,
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current
  val selectedFiles = rememberSaveable(saver = SelectedFileListSaver) { mutableStateListOf<SelectedFileItem>() }
  var pickerErrorMessage by rememberSaveable { mutableStateOf<String?>(null) }

  var selectedTab by remember { mutableIntStateOf(0) } // 0 = Percentage, 1 = Dimensions
  var percentSlider by remember { mutableFloatStateOf(50f) }

  var widthInput by remember { mutableStateOf("") }
  var heightInput by remember { mutableStateOf("") }
  var lockAspectRatio by remember { mutableStateOf(true) }
  var allowStretch by remember { mutableStateOf(false) }
  var outputFormat by remember { mutableStateOf(OutputFormat.JPEG) }

  val coroutineScope = androidx.compose.runtime.rememberCoroutineScope()
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

  // First image reference for live preview
  val referenceFile = selectedFiles.firstOrNull()
  val origW = referenceFile?.width ?: 1920
  val origH = referenceFile?.height ?: 1080

  val (calcW, calcH) = remember(selectedTab, percentSlider, widthInput, heightInput, lockAspectRatio, allowStretch, origW, origH) {
    if (selectedTab == 0) {
      ImageMath.calculateDimensionsByPercentage(origW, origH, percentSlider.toInt())
    } else {
      val w = widthInput.toIntOrNull()
      val h = heightInput.toIntOrNull()
      when {
        w != null && h != null -> {
          if (lockAspectRatio && !allowStretch) {
            ImageMath.calculateDimensionsLockedAspectByWidth(origW, origH, w)
          } else {
            w to h
          }
        }
        w != null -> ImageMath.calculateDimensionsLockedAspectByWidth(origW, origH, w)
        h != null -> ImageMath.calculateDimensionsLockedAspectByHeight(origW, origH, h)
        else -> origW to origH
      }
    }
  }

  val isDimensionsValid = calcW > 0 && calcH > 0 && ImageMath.isWithinMegaPixelLimit(calcW, calcH)
  val calculatedMp = ImageMath.calculateMegaPixels(calcW, calcH)

  ToolScaffold(
    title = stringResource(R.string.tool2_name),
    onBack = onBack,
    modifier = modifier
  ) { padding ->
    LazyColumn(
      modifier = Modifier
        .fillMaxSize()
        .padding(padding)
        .testTag("tool_screen_2"),
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
            message = jobUi?.errorMessage ?: jobUi?.stage ?: "Resize failed",
            recoveryHint = "Verify target dimensions are below 48 MP.",
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

                if (widthInput.isEmpty() && selectedFiles.isNotEmpty()) {
                  val first = selectedFiles.first()
                  if (first.width != null && first.height != null) {
                    widthInput = (first.width / 2).toString()
                    heightInput = (first.height / 2).toString()
                  }
                }

                if (result.overflowCount > 0) {
                  pickerErrorMessage = context.getString(R.string.common_max_images_reached)
                }
              }
            },
            maxFiles = 20,
            supportedFormatsText = stringResource(R.string.tool2_supported_formats)
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

          // Resizer Settings
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
                // Tab Selection (Percentage vs Exact Dimensions)
                TabRow(selectedTabIndex = selectedTab) {
                  Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text(stringResource(R.string.tool2_mode_percent)) }
                  )
                  Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text(stringResource(R.string.tool2_mode_dimensions)) }
                  )
                }

                // Presets FlowRow
                FlowRow(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.spacedBy(8.dp),
                  verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                  FilterChip(
                    selected = selectedTab == 0 && percentSlider == 25f,
                    onClick = {
                      selectedTab = 0
                      percentSlider = 25f
                    },
                    label = { Text(stringResource(R.string.tool2_preset_25)) }
                  )
                  FilterChip(
                    selected = selectedTab == 0 && percentSlider == 50f,
                    onClick = {
                      selectedTab = 0
                      percentSlider = 50f
                    },
                    label = { Text(stringResource(R.string.tool2_preset_50)) }
                  )
                  FilterChip(
                    selected = selectedTab == 0 && percentSlider == 75f,
                    onClick = {
                      selectedTab = 0
                      percentSlider = 75f
                    },
                    label = { Text(stringResource(R.string.tool2_preset_75)) }
                  )
                  FilterChip(
                    selected = false,
                    onClick = {
                      selectedTab = 1
                      val (w, h) = ImageMath.calculateDimensionsForLongEdge(origW, origH, 1080)
                      widthInput = w.toString()
                      heightInput = h.toString()
                    },
                    label = { Text(stringResource(R.string.tool2_preset_1080)) }
                  )
                  FilterChip(
                    selected = false,
                    onClick = {
                      selectedTab = 1
                      val (w, h) = ImageMath.calculateDimensionsForLongEdge(origW, origH, 720)
                      widthInput = w.toString()
                      heightInput = h.toString()
                    },
                    label = { Text(stringResource(R.string.tool2_preset_720)) }
                  )
                }

                if (selectedTab == 0) {
                  // Percentage Mode
                  Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                      text = stringResource(R.string.tool2_percent_slider, percentSlider.toInt()),
                      style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
                    )
                    Slider(
                      value = percentSlider,
                      onValueChange = { percentSlider = it },
                      valueRange = 10f..200f,
                      steps = 18
                    )
                  }
                } else {
                  // Dimensions Mode
                  Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                  ) {
                    OutlinedTextField(
                      value = widthInput,
                      onValueChange = { input ->
                        val clean = input.filter { it.isDigit() }
                        widthInput = clean
                        val w = clean.toIntOrNull()
                        if (lockAspectRatio && !allowStretch && w != null && origW > 0) {
                          val h = (w * (origH.toDouble() / origW.toDouble())).toInt()
                          heightInput = h.toString()
                        }
                      },
                      label = { Text(stringResource(R.string.tool2_width)) },
                      keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                      singleLine = true,
                      modifier = Modifier
                        .weight(1f)
                        .testTag("width_input")
                    )

                    OutlinedTextField(
                      value = heightInput,
                      onValueChange = { input ->
                        val clean = input.filter { it.isDigit() }
                        heightInput = clean
                        val h = clean.toIntOrNull()
                        if (lockAspectRatio && !allowStretch && h != null && origH > 0) {
                          val w = (h * (origW.toDouble() / origH.toDouble())).toInt()
                          widthInput = w.toString()
                        }
                      },
                      label = { Text(stringResource(R.string.tool2_height)) },
                      keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                      singleLine = true,
                      modifier = Modifier
                        .weight(1f)
                        .testTag("height_input")
                    )
                  }

                  // Aspect Ratio & Stretch toggles
                  Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                  ) {
                    Text(stringResource(R.string.tool2_lock_aspect))
                    Switch(
                      checked = lockAspectRatio,
                      onCheckedChange = { lockAspectRatio = it }
                    )
                  }

                  Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                  ) {
                    Text(stringResource(R.string.tool2_stretch_switch))
                    Switch(
                      checked = allowStretch,
                      onCheckedChange = { allowStretch = it }
                    )
                  }
                }

                // Live Preview of Expected Output Dimensions
                Surface(
                  shape = RoundedCornerShape(10.dp),
                  color = if (isDimensionsValid) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                  else MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                  modifier = Modifier.fillMaxWidth()
                ) {
                  Text(
                    text = if (isDimensionsValid) {
                      stringResource(R.string.tool2_target_preview, calcW, calcH, calculatedMp)
                    } else {
                      stringResource(R.string.tool2_error_dimensions)
                    },
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                    color = if (isDimensionsValid) MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.padding(12.dp)
                  )
                }

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

                // Start Resize Button
                Button(
                  onClick = {
                    val filesToProcess = selectedFiles.toList()
                    val targetFormat = outputFormat
                    val targetW = calcW
                    val targetH = calcH
                    val isPercent = (selectedTab == 0)
                    val pVal = percentSlider.toInt()

                    val jobId = ToolJobRunner.start(
                      toolId = 2,
                      inputName = filesToProcess.firstOrNull()?.name ?: "Images",
                      optionsJson = "targetW=$targetW,targetH=$targetH,format=$targetFormat"
                    ) {
                      val tempDir = FileLayer.getTempFolder(context)
                      val results = mutableListOf<FileResult>()

                      filesToProcess.forEachIndexed { index, item ->
                        reportProgress(
                          percent = ((index.toFloat() / filesToProcess.size) * 100).toInt(),
                          stage = "Resizing ${item.name}...",
                          currentIndex = index + 1,
                          totalFiles = filesToProcess.size,
                          currentFileName = item.name
                        )

                        // Compute individual target dimensions
                        val (finalW, finalH) = if (isPercent) {
                          ImageMath.calculateDimensionsByPercentage(item.width ?: origW, item.height ?: origH, pVal)
                        } else {
                          targetW to targetH
                        }

                        val res = ImageEngine.processImage(
                          context = context,
                          uri = item.uri,
                          originalName = item.name,
                          options = ProcessImageOptions(
                            format = targetFormat,
                            targetWidth = finalW,
                            targetHeight = finalH
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
                  enabled = isDimensionsValid && selectedFiles.isNotEmpty() && !ToolJobRunner.isToolBusy(2),
                  modifier = Modifier
                    .fillMaxWidth()
                    .testTag("start_resize_button"),
                  shape = RoundedCornerShape(12.dp)
                ) {
                  Text(
                    text = stringResource(R.string.tool2_start_button),
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
