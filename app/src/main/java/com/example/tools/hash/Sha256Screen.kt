package com.example.tools.hash

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.file.FileFormatters
import com.example.file.FileLayer
import com.example.tools.common.ErrorCard
import com.example.tools.common.FilePickerCard
import com.example.tools.common.FileResult
import com.example.tools.common.FileSelectionHelper
import com.example.tools.common.JobResult
import com.example.tools.common.JobState
import com.example.tools.common.PickerMode
import com.example.tools.common.ProgressCard
import com.example.tools.common.SelectedFileItem
import com.example.tools.common.SelectedFileListSaver
import com.example.tools.common.SelectedFileRow
import com.example.tools.common.ToolJobRunner
import com.example.tools.common.ToolScaffold

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun Sha256Screen(
  onBack: () -> Unit,
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current
  val selectedFiles = rememberSaveable(saver = SelectedFileListSaver) { mutableStateListOf<SelectedFileItem>() }
  var pickerErrorMessage by rememberSaveable { mutableStateOf<String?>(null) }
  var expectedHashInput by rememberSaveable { mutableStateOf("") }

  var currentJobId by rememberSaveable { mutableStateOf<String?>(null) }
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

  // SAF CreateDocument for exporting .txt checksums
  val exportLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.CreateDocument("text/plain")
  ) { targetUri: Uri? ->
    if (targetUri != null && jobUi?.result != null) {
      val successfulHashes = jobUi!!.result!!.files.filter { it.success && it.outputName != null }
      val checksumText = Sha256Helper.formatChecksumLines(
        successfulHashes.map { it.outputName!! to it.inputName }
      )
      runCatching {
        context.contentResolver.openOutputStream(targetUri)?.use { os ->
          os.write(checksumText.toByteArray(Charsets.UTF_8))
          os.flush()
        }
        Toast.makeText(context, context.getString(R.string.tool7_export_success), Toast.LENGTH_SHORT).show()
      }.onFailure {
        Toast.makeText(context, context.getString(R.string.error_generic), Toast.LENGTH_SHORT).show()
      }
    }
  }

  ToolScaffold(
    title = stringResource(R.string.tool7_name),
    onBack = onBack,
    modifier = modifier
  ) { padding ->
    LazyColumn(
      modifier = Modifier
        .fillMaxSize()
        .padding(padding)
        .testTag("tool_screen_7"),
      contentPadding = PaddingValues(16.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
      // 1. Result View
      if (jobUi != null && (jobUi!!.state == JobState.SUCCESS || jobUi!!.state == JobState.FAILED) && jobUi!!.result != null) {
        val files = jobUi!!.result!!.files
        item {
          Card(
            modifier = Modifier
              .fillMaxWidth()
              .testTag("sha256_results_card"),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
          ) {
            Column(
              modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
              verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
              ) {
                Icon(
                  imageVector = Icons.Default.Fingerprint,
                  contentDescription = null,
                  tint = MaterialTheme.colorScheme.primary,
                  modifier = Modifier.size(28.dp)
                )
                Text(
                  text = stringResource(R.string.tool7_results_title),
                  style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                  color = MaterialTheme.colorScheme.onSurface
                )
              }

              // Expected hash comparison input
              OutlinedTextField(
                value = expectedHashInput,
                onValueChange = { expectedHashInput = it },
                label = { Text(stringResource(R.string.tool7_expected_hash_label)) },
                placeholder = { Text(stringResource(R.string.tool7_expected_hash_hint)) },
                singleLine = true,
                modifier = Modifier
                  .fillMaxWidth()
                  .testTag("expected_hash_input"),
                shape = RoundedCornerShape(12.dp)
              )

              if (expectedHashInput.isNotBlank()) {
                val hasMatch = files.any { it.success && it.outputName != null && Sha256Helper.compareHashes(it.outputName, expectedHashInput) }
                Surface(
                  shape = RoundedCornerShape(8.dp),
                  color = if (hasMatch) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer,
                  modifier = Modifier.fillMaxWidth()
                ) {
                  Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                  ) {
                    Icon(
                      imageVector = if (hasMatch) Icons.Default.Check else Icons.Default.Close,
                      contentDescription = null,
                      tint = if (hasMatch) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer,
                      modifier = Modifier.size(18.dp)
                    )
                    Text(
                      text = if (hasMatch) stringResource(R.string.tool7_hash_match) else stringResource(R.string.tool7_hash_no_match),
                      style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                      color = if (hasMatch) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer
                    )
                  }
                }
              }

              HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)

              // List of calculated hashes
              for (fileResult in files) {
                Surface(
                  shape = RoundedCornerShape(10.dp),
                  color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                  modifier = Modifier.fillMaxWidth()
                ) {
                  Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                  ) {
                    Row(
                      modifier = Modifier.fillMaxWidth(),
                      horizontalArrangement = Arrangement.SpaceBetween,
                      verticalAlignment = Alignment.CenterVertically
                    ) {
                      Text(
                        text = fileResult.inputName,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                      )
                      if (fileResult.outputSizeBytes > 0L) {
                        Text(
                          text = FileFormatters.formatBytes(fileResult.outputSizeBytes),
                          style = MaterialTheme.typography.bodySmall,
                          color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                      }
                    }

                    if (fileResult.success && fileResult.outputName != null) {
                      Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                      ) {
                        Text(
                          text = fileResult.outputName,
                          style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                          color = MaterialTheme.colorScheme.primary,
                          modifier = Modifier.weight(1f)
                        )
                        IconButton(
                          onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("SHA-256", fileResult.outputName))
                            Toast.makeText(context, context.getString(R.string.tool7_hash_copied), Toast.LENGTH_SHORT).show()
                          },
                          modifier = Modifier.size(32.dp)
                        ) {
                          Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = stringResource(R.string.tool7_copy_hash),
                            modifier = Modifier.size(16.dp)
                          )
                        }
                      }
                    } else {
                      Text(
                        text = fileResult.userMessage ?: "Read error",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                      )
                    }
                  }
                }
              }

              // Actions
              FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
              ) {
                // Copy all
                Button(
                  onClick = {
                    val successful = files.filter { it.success && it.outputName != null }
                    val text = Sha256Helper.formatChecksumLines(successful.map { it.outputName!! to it.inputName })
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("SHA-256 Checksums", text))
                    Toast.makeText(context, context.getString(R.string.tool7_all_copied), Toast.LENGTH_SHORT).show()
                  },
                  shape = RoundedCornerShape(10.dp),
                  modifier = Modifier.testTag("action_copy_all")
                ) {
                  Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                  Spacer(Modifier.size(6.dp))
                  Text(stringResource(R.string.tool7_copy_all))
                }

                // Share
                OutlinedButton(
                  onClick = {
                    val successful = files.filter { it.success && it.outputName != null }
                    val text = Sha256Helper.formatChecksumLines(successful.map { it.outputName!! to it.inputName })
                    val sendIntent = Intent(Intent.ACTION_SEND).apply {
                      type = "text/plain"
                      putExtra(Intent.EXTRA_SUBJECT, "SHA-256 Checksums")
                      putExtra(Intent.EXTRA_TEXT, text)
                    }
                    context.startActivity(Intent.createChooser(sendIntent, "Share Hashes"))
                  },
                  shape = RoundedCornerShape(10.dp),
                  modifier = Modifier.testTag("action_share_all")
                ) {
                  Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                  Spacer(Modifier.size(6.dp))
                  Text(stringResource(R.string.tool7_share_hashes))
                }

                // Export .txt
                OutlinedButton(
                  onClick = {
                    exportLauncher.launch("checksums.sha256.txt")
                  },
                  shape = RoundedCornerShape(10.dp),
                  modifier = Modifier.testTag("action_export_txt")
                ) {
                  Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                  Spacer(Modifier.size(6.dp))
                  Text(stringResource(R.string.tool7_export_txt))
                }

                // Again
                OutlinedButton(
                  onClick = {
                    currentJobId = null
                    selectedFiles.clear()
                    expectedHashInput = ""
                  },
                  shape = RoundedCornerShape(10.dp),
                  modifier = Modifier.testTag("action_hash_again")
                ) {
                  Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                  Spacer(Modifier.size(6.dp))
                  Text(stringResource(R.string.tool7_process_again))
                }
              }
            }
          }
        }
      }
      // 2. Running Progress
      else if (jobUi != null && jobUi!!.state == JobState.RUNNING) {
        item {
          ProgressCard(
            stage = jobUi!!.stage,
            progressPercent = jobUi!!.progressPercent,
            currentIndex = jobUi!!.currentIndex,
            totalFiles = jobUi!!.totalFiles,
            currentFileName = jobUi!!.currentFileName,
            onCancel = {
              currentJobId?.let { ToolJobRunner.cancel(it) }
            }
          )
        }
      }
      // 3. Selection and Configuration
      else {
        item {
          FilePickerCard(
            onFilesSelected = { uris ->
              pickerErrorMessage = null
              val newItems = mutableListOf<SelectedFileItem>()
              for (uri in uris) {
                val info = FileLayer.getUriFileInfo(context, uri)
                newItems.add(
                  SelectedFileItem(
                    uri = uri,
                    name = info.displayName,
                    sizeBytes = info.sizeBytes
                  )
                )
              }
              val res = FileSelectionHelper.addFiles(selectedFiles, newItems, maxLimit = 20)
              selectedFiles.clear()
              selectedFiles.addAll(res.updatedList)
              if (res.overflowCount > 0) {
                pickerErrorMessage = "Maximum limit of 20 files reached. Extra files were skipped."
              }
            },
            pickerMode = PickerMode.SAF_DOCUMENTS,
            mimeTypeFilter = arrayOf("*/*"),
            maxFiles = 20,
            title = stringResource(R.string.tool7_select_files),
            supportedFormatsText = stringResource(R.string.tool7_file_picker_hint)
          )
        }

        if (pickerErrorMessage != null) {
          item {
            ErrorCard(
              message = pickerErrorMessage!!,
              onRetry = { pickerErrorMessage = null }
            )
          }
        }

        if (selectedFiles.isNotEmpty()) {
          items(selectedFiles) { item ->
            SelectedFileRow(
              fileItem = item,
              onRemove = { selectedFiles.remove(item) }
            )
          }

          item {
            Button(
              enabled = selectedFiles.isNotEmpty() && !ToolJobRunner.isToolBusy(7),
              onClick = {
                val filesToHash = selectedFiles.toList()
                val totalBytesAll = filesToHash.sumOf { it.sizeBytes }

                val jobId = ToolJobRunner.start(
                  toolId = 7,
                  inputName = filesToHash.firstOrNull()?.name ?: "Files",
                  optionsJson = "{}"
                ) {
                  var bytesProcessedAll = 0L
                  val results = mutableListOf<FileResult>()

                  filesToHash.forEachIndexed { index, item ->
                    ensureActive()
                    try {
                      val stream = context.contentResolver.openInputStream(item.uri)
                        ?: throw IllegalStateException("Cannot open input stream for ${item.name}")

                      val hash = stream.use { inputStream ->
                        Sha256Helper.computeSha256(inputStream) { chunk, _ ->
                          ensureActive()
                          bytesProcessedAll += chunk
                          val pct = if (totalBytesAll > 0L) {
                            ((bytesProcessedAll.toDouble() / totalBytesAll.toDouble()) * 100).toInt().coerceIn(0, 100)
                          } else {
                            null
                          }
                          reportProgress(
                            percent = pct,
                            stage = "Hashing ${item.name}...",
                            currentIndex = index + 1,
                            totalFiles = filesToHash.size,
                            currentFileName = item.name
                          )
                        }
                      }

                      results.add(
                        FileResult(
                          inputName = item.name,
                          outputName = hash,
                          outputUri = null,
                          outputSizeBytes = item.sizeBytes,
                          success = true
                        )
                      )
                    } catch (e: Exception) {
                      results.add(
                        FileResult(
                          inputName = item.name,
                          outputName = null,
                          outputUri = null,
                          outputSizeBytes = 0L,
                          success = false,
                          userMessage = e.message ?: "Failed to read file"
                        )
                      )
                    }
                  }

                  JobResult(files = results)
                }

                currentJobId = jobId
              },
              modifier = Modifier
                .fillMaxWidth()
                .testTag("start_hash_button"),
              shape = RoundedCornerShape(12.dp)
            ) {
              Text(
                text = stringResource(R.string.tool7_start_button),
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
              )
            }
          }
        }
      }
    }
  }
}
