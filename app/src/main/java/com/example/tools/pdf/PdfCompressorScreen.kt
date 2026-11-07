package com.example.tools.pdf

import android.content.Context
import android.net.Uri
import android.widget.Toast
import com.example.pdf.PdfErrors
import com.example.pdf.PdfFileHelper
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.example.tools.common.ToolJobRunner
import com.example.tools.common.ToolScaffold
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PdfCompressorScreen(onBack: () -> Unit) {
  val context = LocalContext.current
  val coroutineScope = rememberCoroutineScope()

  BackHandler { onBack() }

  var selectedPdfUri by rememberSaveable { mutableStateOf<Uri?>(null) }
  var cachedInputFile by remember { mutableStateOf<File?>(null) }
  var isLoadingPdf by remember { mutableStateOf(false) }

  var compressionMode by rememberSaveable { mutableStateOf(CompressionMode.OPTIMIZE_IMAGES) }
  var compressionPreset by rememberSaveable { mutableStateOf(CompressionPreset.BALANCED) }

  var localErrorMessage by remember { mutableStateOf<String?>(null) }
  var currentJobId by rememberSaveable { mutableStateOf<String?>(null) }
  val jobUi by remember(currentJobId) {
    if (currentJobId != null) ToolJobRunner.observe(currentJobId!!)
    else flowOf(null)
  }.collectAsState(initial = null)

  LaunchedEffect(currentJobId) {
    val id = currentJobId
    if (id != null && !ToolJobRunner.hasJob(id)) {
      currentJobId = null
      localErrorMessage = context.getString(R.string.common_job_restored_lost)
    }
  }

  DisposableEffect(Unit) {
    onDispose {
      PdfFileHelper.deleteQuietly(cachedInputFile)
    }
  }

  fun loadPdf(uri: Uri) {
    selectedPdfUri = uri
    isLoadingPdf = true
    localErrorMessage = null
    currentJobId = null

    coroutineScope.launch {
      try {
        val cached = withContext(Dispatchers.IO) {
          PdfFileHelper.copyPdfToCache(context, uri)
        }
        cachedInputFile = cached
      } catch (e: Exception) {
        localErrorMessage = PdfErrors.mapPdfException(e)
        selectedPdfUri = null
        cachedInputFile = null
      } finally {
        isLoadingPdf = false
      }
    }
  }

  val savePdfLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.CreateDocument("application/pdf")
  ) { targetUri: Uri? ->
    val resFile = jobUi?.result?.files?.firstOrNull()?.tempFilePath?.let { File(it) }
    if (targetUri != null && resFile != null) {
      coroutineScope.launch {
        val success = withContext(Dispatchers.IO) {
          if (resFile.exists()) {
            FileLayer.saveFileToUri(context, resFile, targetUri)
          } else {
            false
          }
        }
        if (success) {
          Toast.makeText(context, context.getString(R.string.file_saved_success), Toast.LENGTH_SHORT).show()
        } else {
          Toast.makeText(context, context.getString(R.string.error_generic), Toast.LENGTH_SHORT).show()
        }
      }
    }
  }

  fun executeCompression() {
    val cached = cachedInputFile ?: return
    if (ToolJobRunner.isToolBusy(12)) return
    localErrorMessage = null

    val info = FileLayer.getUriFileInfo(context, selectedPdfUri!!)
    val options = PdfCompressorOptions(
      mode = compressionMode,
      preset = compressionPreset
    )

    val jobId = ToolJobRunner.start(
      toolId = 12,
      inputName = info.displayName,
      optionsJson = "mode=$compressionMode,preset=$compressionPreset"
    ) {
      val (result, _) = PdfCompressorHelper.compressPdf(
        context = context,
        cachedPdf = cached,
        originalName = info.displayName,
        options = options,
        jobContext = this
      )
      result
    }

    currentJobId = jobId
  }

  ToolScaffold(
    title = stringResource(R.string.tool12_name),
    onBack = onBack
  ) { padding ->
    LazyColumn(
      modifier = Modifier
        .fillMaxSize()
        .padding(padding)
        .padding(horizontal = 16.dp, vertical = 8.dp)
        .testTag("tool_screen_12"),
      verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
      if (localErrorMessage != null) {
        item {
          ErrorCard(
            message = localErrorMessage!!
          )
        }
      }

      // 1. Progress State
      if (jobUi != null && jobUi!!.state == JobState.RUNNING) {
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

      // 2. Success State
      if (jobUi != null && jobUi!!.state == JobState.SUCCESS) {
        val fileRes = jobUi!!.result?.files?.firstOrNull()
        val localFile = fileRes?.tempFilePath?.let { File(it) }
        if (fileRes != null && localFile != null) {
          val origSize = fileRes.originalSizeBytes
          val outputSize = fileRes.outputSizeBytes
          val isReduced = outputSize < origSize && origSize > 0L
          val pct = if (isReduced) {
            (((origSize - outputSize).toDouble() / origSize) * 100).toInt()
          } else 0

          item {
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
                // Header badge
                if (isReduced) {
                  Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                  ) {
                    Row(
                      modifier = Modifier.padding(12.dp),
                      verticalAlignment = Alignment.CenterVertically,
                      horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                      Icon(Icons.Default.Compress, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                      Column {
                        Text(
                          text = stringResource(
                            R.string.tool12_reduced_label,
                            pct,
                            FileFormatters.formatBytes(origSize),
                            FileFormatters.formatBytes(outputSize)
                          ),
                          style = MaterialTheme.typography.titleSmall,
                          fontWeight = FontWeight.Bold,
                          color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                          text = "Saved ${FileFormatters.formatBytes(origSize - outputSize)}",
                          style = MaterialTheme.typography.bodySmall,
                          color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                        )
                      }
                    }
                  }
                } else {
                  Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                  ) {
                    Row(
                      modifier = Modifier.padding(12.dp),
                      verticalAlignment = Alignment.CenterVertically,
                      horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                      Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                      Column {
                        Text(
                          text = stringResource(R.string.tool12_no_reduction_title),
                          style = MaterialTheme.typography.titleSmall,
                          fontWeight = FontWeight.Bold
                        )
                        Text(
                          text = stringResource(R.string.tool12_no_reduction_desc),
                          style = MaterialTheme.typography.bodySmall,
                          color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                      }
                    }
                  }
                }

                // File info
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.spacedBy(8.dp),
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  Icon(Icons.Default.PictureAsPdf, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                  Column(modifier = Modifier.weight(1f)) {
                    Text(
                      text = fileRes.outputName ?: fileRes.inputName,
                      style = MaterialTheme.typography.bodyMedium,
                      fontWeight = FontWeight.SemiBold,
                      maxLines = 1,
                      overflow = TextOverflow.Ellipsis
                    )
                    Text(
                      text = "Output: ${FileFormatters.formatBytes(outputSize)}",
                      style = MaterialTheme.typography.bodySmall,
                      color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                  }
                }

                com.example.tools.common.PdfResultPreview(localFile)

                // Action buttons
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                  if (isReduced) {
                    Button(
                      onClick = { savePdfLauncher.launch(fileRes.outputName ?: "compressed.pdf") },
                      modifier = Modifier.weight(1f),
                      shape = RoundedCornerShape(10.dp)
                    ) {
                      Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                      Spacer(modifier = Modifier.width(6.dp))
                      Text(stringResource(R.string.common_save))
                    }
                  } else {
                    OutlinedButton(
                      onClick = { savePdfLauncher.launch(fileRes.outputName ?: "compressed.pdf") },
                      modifier = Modifier.weight(1f),
                      shape = RoundedCornerShape(10.dp)
                    ) {
                      Text(stringResource(R.string.tool12_save_anyway))
                    }
                  }

                  OutlinedButton(
                    onClick = { FileLayer.shareFileAndNotify(context, localFile, "application/pdf") },
                    shape = RoundedCornerShape(10.dp)
                  ) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                  }

                  OutlinedButton(
                    onClick = { FileLayer.openFileAndNotify(context, localFile, "application/pdf") },
                    shape = RoundedCornerShape(10.dp)
                  ) {
                    Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                  }
                }

                OutlinedButton(
                  onClick = {
                    currentJobId = null
                    selectedPdfUri = null
                    cachedInputFile = null
                  },
                  modifier = Modifier.fillMaxWidth(),
                  shape = RoundedCornerShape(10.dp)
                ) {
                  Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                  Spacer(modifier = Modifier.width(6.dp))
                  Text(stringResource(R.string.common_process_again))
                }
              }
            }
          }
        }
      }

      // 3. Failed State
      if (jobUi != null && jobUi!!.state == JobState.FAILED) {
        item {
          ErrorCard(
            message = jobUi!!.errorMessage ?: stringResource(R.string.error_generic),
            onRetry = { executeCompression() }
          )
        }
      }

      // 4. Empty State
      if (selectedPdfUri == null && jobUi?.state != JobState.RUNNING && jobUi?.state != JobState.SUCCESS) {
        item {
          FilePickerCard(
            onFilesSelected = { uris ->
              uris.firstOrNull()?.let { loadPdf(it) }
            },
            title = stringResource(R.string.tool12_picker_title),
            supportedFormatsText = stringResource(R.string.tool12_picker_desc),
            pickerMode = PickerMode.SAF_DOCUMENTS,
            mimeTypeFilter = arrayOf("application/pdf"),
            maxFiles = 1,
            modifier = Modifier.testTag("pdf_compress_picker")
          )
        }
      }

      // Loading Indicator
      if (isLoadingPdf) {
        item {
          Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
          ) {
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
              horizontalArrangement = Arrangement.Center,
              verticalAlignment = Alignment.CenterVertically
            ) {
              CircularProgressIndicator(modifier = Modifier.size(24.dp))
              Spacer(modifier = Modifier.width(12.dp))
              Text("Opening PDF document…", style = MaterialTheme.typography.bodyMedium)
            }
          }
        }
      }

      // 5. Selected State: Configuration Form
      if (selectedPdfUri != null && !isLoadingPdf && cachedInputFile != null && jobUi?.state != JobState.RUNNING && jobUi?.state != JobState.SUCCESS) {
        // Selected File Summary
        item {
          Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
          ) {
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.SpaceBetween
            ) {
              val info = FileLayer.getUriFileInfo(context, selectedPdfUri!!)
              Column(modifier = Modifier.weight(1f)) {
                Text(
                  text = info.displayName,
                  style = MaterialTheme.typography.titleSmall,
                  fontWeight = FontWeight.Bold,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis
                )
                Text(
                  text = "Original Size: ${FileFormatters.formatBytes(cachedInputFile!!.length())}",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant
                )
              }
              OutlinedButton(
                onClick = {
                  selectedPdfUri = null
                  cachedInputFile = null
                },
                shape = RoundedCornerShape(8.dp)
              ) {
                Text("Change")
              }
            }
          }
        }

        // Mode Selection Card
        item {
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
              verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
              Text(
                text = stringResource(R.string.tool12_mode),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold
              )

              // Mode A Option
              Card(
                shape = RoundedCornerShape(10.dp),
                colors = CardDefaults.cardColors(
                  containerColor = if (compressionMode == CompressionMode.OPTIMIZE_IMAGES) {
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                  } else {
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
                  }
                ),
                modifier = Modifier
                  .fillMaxWidth()
                  .clip(RoundedCornerShape(10.dp))
                  .selectable(
                    selected = compressionMode == CompressionMode.OPTIMIZE_IMAGES,
                    onClick = { compressionMode = CompressionMode.OPTIMIZE_IMAGES }
                  )
              ) {
                Row(
                  modifier = Modifier.padding(12.dp),
                  verticalAlignment = Alignment.Top,
                  horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                  RadioButton(
                    selected = compressionMode == CompressionMode.OPTIMIZE_IMAGES,
                    onClick = { compressionMode = CompressionMode.OPTIMIZE_IMAGES }
                  )
                  Column {
                    Text(
                      text = stringResource(R.string.tool12_mode_a_title),
                      style = MaterialTheme.typography.bodyMedium,
                      fontWeight = FontWeight.Bold
                    )
                    Text(
                      text = stringResource(R.string.tool12_mode_a_desc),
                      style = MaterialTheme.typography.bodySmall,
                      color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                  }
                }
              }

              // Mode B Option
              Card(
                shape = RoundedCornerShape(10.dp),
                colors = CardDefaults.cardColors(
                  containerColor = if (compressionMode == CompressionMode.PAGES_TO_IMAGES) {
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                  } else {
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
                  }
                ),
                modifier = Modifier
                  .fillMaxWidth()
                  .clip(RoundedCornerShape(10.dp))
                  .selectable(
                    selected = compressionMode == CompressionMode.PAGES_TO_IMAGES,
                    onClick = { compressionMode = CompressionMode.PAGES_TO_IMAGES }
                  )
              ) {
                Row(
                  modifier = Modifier.padding(12.dp),
                  verticalAlignment = Alignment.Top,
                  horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                  RadioButton(
                    selected = compressionMode == CompressionMode.PAGES_TO_IMAGES,
                    onClick = { compressionMode = CompressionMode.PAGES_TO_IMAGES }
                  )
                  Column {
                    Text(
                      text = stringResource(R.string.tool12_mode_b_title),
                      style = MaterialTheme.typography.bodyMedium,
                      fontWeight = FontWeight.Bold
                    )
                    Text(
                      text = stringResource(R.string.tool12_mode_b_desc),
                      style = MaterialTheme.typography.bodySmall,
                      color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                  }
                }
              }

              // Preset Selection
              Text(
                text = stringResource(R.string.tool12_preset),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold
              )
              FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                  selected = compressionPreset == CompressionPreset.HIGHER_QUALITY,
                  onClick = { compressionPreset = CompressionPreset.HIGHER_QUALITY },
                  label = { Text(stringResource(R.string.tool12_preset_high)) }
                )
                FilterChip(
                  selected = compressionPreset == CompressionPreset.BALANCED,
                  onClick = { compressionPreset = CompressionPreset.BALANCED },
                  label = { Text(stringResource(R.string.tool12_preset_balanced)) }
                )
                FilterChip(
                  selected = compressionPreset == CompressionPreset.SMALLER_FILE,
                  onClick = { compressionPreset = CompressionPreset.SMALLER_FILE },
                  label = { Text(stringResource(R.string.tool12_preset_smaller)) }
                )
              }
            }
          }
        }

        // Compress Button
        item {
          Button(
            onClick = { executeCompression() },
            modifier = Modifier
              .fillMaxWidth()
              .height(52.dp)
              .testTag("start_pdf_compress_button"),
            enabled = !ToolJobRunner.isToolBusy(12)
          ) {
            Icon(Icons.Default.Compress, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.tool12_btn_compress))
          }
        }
      }
    }
  }
}
