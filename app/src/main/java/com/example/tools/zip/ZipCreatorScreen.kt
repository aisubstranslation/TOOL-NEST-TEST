package com.example.tools.zip

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.text.font.FontWeight
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.file.FileFormatters
import com.example.file.FileLayer
import com.example.tools.common.ErrorCard
import com.example.tools.common.FileResult
import com.example.tools.common.JobResult
import com.example.tools.common.JobState
import com.example.tools.common.ProgressCard
import com.example.tools.common.ToolJobRunner
import com.example.tools.common.ToolScaffold
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ZipCreatorScreen(
  onBack: () -> Unit,
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current
  val selectedEntries = remember { mutableStateListOf<ZipInputEntry>() }
  var compressionMode by rememberSaveable { mutableStateOf(ZipCompressionMode.NORMAL) }
  var archiveNameInput by rememberSaveable {
    mutableStateOf(
      SimpleDateFormat("'TOOLSNEST_'yyyyMMdd_HHmmss'.zip'", Locale.US).format(Date())
    )
  }

  var validationError by rememberSaveable { mutableStateOf<String?>(null) }
  var unreadableFilesDetected by remember { mutableStateOf<List<Pair<String, String>>?>(null) }

  var currentJobId by rememberSaveable { mutableStateOf<String?>(null) }
  val jobUi by remember(currentJobId) {
    if (currentJobId != null) ToolJobRunner.observe(currentJobId!!)
    else kotlinx.coroutines.flow.flowOf(null)
  }.collectAsState(initial = null)

  LaunchedEffect(currentJobId) {
    val id = currentJobId
    if (id != null && !ToolJobRunner.hasJob(id)) {
      currentJobId = null
      validationError = context.getString(R.string.common_job_restored_lost)
    }
  }

  val coroutineScope = androidx.compose.runtime.rememberCoroutineScope()

  // SAF Files launcher
  val filesLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.OpenMultipleDocuments()
  ) { uris ->
    if (uris.isNotEmpty()) {
      coroutineScope.launch {
        validationError = null
        val currentTotalBytes = selectedEntries.sumOf { it.sizeBytes }
        var addedBytes = 0L
        val newItems = mutableListOf<ZipInputEntry>()

        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
          for (uri in uris) {
            if (selectedEntries.size + newItems.size >= ZipWriterHelper.MAX_ENTRIES) {
              validationError = context.getString(R.string.tool8_error_limit_entries)
              break
            }
            val info = FileLayer.getUriFileInfo(context, uri)
            if (currentTotalBytes + addedBytes + info.sizeBytes > ZipWriterHelper.MAX_TOTAL_BYTES) {
              validationError = context.getString(R.string.tool8_error_limit_size)
              break
            }
            addedBytes += info.sizeBytes
            newItems.add(
              ZipInputEntry(
                uri = uri,
                relativePath = info.displayName,
                sizeBytes = info.sizeBytes
              )
            )
          }
        }
        selectedEntries.addAll(newItems)
      }
    }
  }

  // SAF Folder Tree launcher
  val folderLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.OpenDocumentTree()
  ) { treeUri: Uri? ->
    if (treeUri != null) {
      coroutineScope.launch {
        validationError = null
        val treeDocId = DocumentsContract.getTreeDocumentId(treeUri)
        val folderItems = mutableListOf<ZipInputEntry>()
        withContext(Dispatchers.IO) {
          queryTreeRecursively(
            context = context,
            treeUri = treeUri,
            parentDocId = treeDocId,
            currentPath = "",
            sink = folderItems
          )
        }

        val currentTotalBytes = selectedEntries.sumOf { it.sizeBytes }
        val folderTotalBytes = folderItems.sumOf { it.sizeBytes }

        if (selectedEntries.size + folderItems.size > ZipWriterHelper.MAX_ENTRIES) {
          validationError = context.getString(R.string.tool8_error_limit_entries)
        } else if (currentTotalBytes + folderTotalBytes > ZipWriterHelper.MAX_TOTAL_BYTES) {
          validationError = context.getString(R.string.tool8_error_limit_size)
        } else {
          selectedEntries.addAll(folderItems)
        }
      }
    }
  }

  // SAF CreateDocument for saving the completed ZIP
  val saveSafLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.CreateDocument("application/zip")
  ) { targetUri: Uri? ->
    val localFilePath = jobUi?.result?.files?.firstOrNull()?.tempFilePath
    if (targetUri != null && localFilePath != null) {
      coroutineScope.launch {
        val success = withContext(Dispatchers.IO) {
          val sourceFile = File(localFilePath)
          if (sourceFile.exists()) {
            FileLayer.saveFileToUri(context, sourceFile, targetUri)
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

  fun startZipCreation(entriesToZip: List<ZipInputEntry>) {
    val tempDir = FileLayer.getTempFolder(context)
    val sanitizedName = if (archiveNameInput.endsWith(".zip", ignoreCase = true)) {
      archiveNameInput
    } else {
      "$archiveNameInput.zip"
    }
    val destinationFile = File(tempDir, sanitizedName)

    val jobId = ToolJobRunner.start(
      toolId = 8,
      inputName = "${entriesToZip.size} files",
      optionsJson = "mode=$compressionMode"
    ) {
      val result = ZipWriterHelper.createAndVerifyZip(
        context = context,
        entries = entriesToZip,
        destinationTempFile = destinationFile,
        compressionMode = compressionMode,
        jobContext = this
      )
      JobResult(files = listOf(result))
    }
    currentJobId = jobId
  }

  ToolScaffold(
    title = stringResource(R.string.tool8_name),
    onBack = onBack,
    modifier = modifier
  ) { padding ->
    LazyColumn(
      modifier = Modifier
        .fillMaxSize()
        .padding(padding)
        .testTag("tool_screen_8"),
      contentPadding = PaddingValues(16.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
      // 1. Success or Failure Result View
      if (jobUi != null && (jobUi!!.state == JobState.SUCCESS || jobUi!!.state == JobState.FAILED) && jobUi!!.result != null) {
        val res = jobUi!!.result!!.files.firstOrNull()
        if (res != null && res.success) {
          item {
            Card(
              modifier = Modifier
                .fillMaxWidth()
                .testTag("zip_result_card"),
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
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                  )
                  Text(
                    text = stringResource(R.string.tool8_result_title),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                  )
                }

                Surface(
                  shape = RoundedCornerShape(12.dp),
                  color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                  modifier = Modifier.fillMaxWidth()
                ) {
                  Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                  ) {
                    ZipResultRow(
                      label = stringResource(R.string.tool8_archive_size),
                      value = FileFormatters.formatBytes(res.outputSizeBytes)
                    )
                    ZipResultRow(
                      label = stringResource(R.string.tool8_entries_count),
                      value = "${selectedEntries.size}"
                    )
                    ZipResultRow(
                      label = stringResource(R.string.tool8_input_size),
                      value = FileFormatters.formatBytes(res.originalSizeBytes)
                    )

                    val savedPct = if (res.originalSizeBytes > 0L) {
                      maxOf(0, (((res.originalSizeBytes - res.outputSizeBytes).toDouble() / res.originalSizeBytes.toDouble()) * 100).toInt())
                    } else 0
                    ZipResultRow(
                      label = stringResource(R.string.tool8_compression_saved),
                      value = "$savedPct%"
                    )
                  }
                }

                // Actions
                FlowRow(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.spacedBy(8.dp),
                  verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                  Button(
                    onClick = {
                      saveSafLauncher.launch(res.outputName ?: "archive.zip")
                    },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.testTag("action_save_zip")
                  ) {
                    Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(6.dp))
                    Text(stringResource(R.string.common_save))
                  }

                  if (res.tempFilePath != null) {
                    OutlinedButton(
                      onClick = {
                        val file = File(res.tempFilePath)
                        FileLayer.shareFileAndNotify(context, file, "application/zip")
                      },
                      shape = RoundedCornerShape(10.dp),
                      modifier = Modifier.testTag("action_share_zip")
                    ) {
                      Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                      Spacer(Modifier.size(6.dp))
                      Text(stringResource(R.string.common_share))
                    }
                  }

                  OutlinedButton(
                    onClick = {
                      currentJobId = null
                      selectedEntries.clear()
                      archiveNameInput = SimpleDateFormat("'TOOLSNEST_'yyyyMMdd_HHmmss'.zip'", Locale.US).format(Date())
                    },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.testTag("action_create_another_zip")
                  ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(6.dp))
                    Text(stringResource(R.string.common_process_again))
                  }
                }
              }
            }
          }
        } else {
          item {
            ErrorCard(
              message = res?.userMessage ?: "Failed to create archive",
              onRetry = { currentJobId = null }
            )
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
      // 3. Selection & Options
      else {
        // Picker Card
        item {
          Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
              containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
          ) {
            Column(
              modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
              horizontalAlignment = Alignment.CenterHorizontally,
              verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
              Icon(
                imageVector = Icons.Default.FolderZip,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(48.dp)
              )

              Text(
                text = stringResource(R.string.tool8_picker_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )

              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
              ) {
                Button(
                  onClick = { filesLauncher.launch(arrayOf("*/*")) },
                  modifier = Modifier
                    .weight(1f)
                    .testTag("add_files_button"),
                  shape = RoundedCornerShape(10.dp)
                ) {
                  Icon(Icons.Default.UploadFile, contentDescription = null, modifier = Modifier.size(16.dp))
                  Spacer(Modifier.size(6.dp))
                  Text(stringResource(R.string.tool8_add_files))
                }

                FilledTonalButton(
                  onClick = { folderLauncher.launch(null) },
                  modifier = Modifier
                    .weight(1f)
                    .testTag("add_folder_button"),
                  shape = RoundedCornerShape(10.dp)
                ) {
                  Icon(Icons.Default.CreateNewFolder, contentDescription = null, modifier = Modifier.size(16.dp))
                  Spacer(Modifier.size(6.dp))
                  Text(stringResource(R.string.tool8_add_folder))
                }
              }
            }
          }
        }

        // Limit / Validation error
        if (validationError != null) {
          item {
            ErrorCard(
              message = validationError!!,
              onRetry = { validationError = null }
            )
          }
        }

        // Unreadable Files Prompt
        val unreadable = unreadableFilesDetected
        if (unreadable != null) {
          item {
            Card(
              modifier = Modifier
                .fillMaxWidth()
                .testTag("unreadable_files_prompt"),
              shape = RoundedCornerShape(14.dp),
              colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.8f))
            ) {
              Column(
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
              ) {
                Text(
                  text = stringResource(R.string.tool8_unreadable_title),
                  style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                  color = MaterialTheme.colorScheme.onErrorContainer
                )
                Text(
                  text = stringResource(R.string.tool8_unreadable_desc),
                  style = MaterialTheme.typography.bodyMedium,
                  color = MaterialTheme.colorScheme.onErrorContainer
                )
                for (item in unreadable.take(5)) {
                  Text(
                    text = "• ${item.first} (${item.second})",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer
                  )
                }
                if (unreadable.size > 5) {
                  Text(
                    text = "... and ${unreadable.size - 5} more",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer
                  )
                }

                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                  Button(
                    onClick = {
                      val badPaths = unreadable.map { it.first }.toSet()
                      val validEntries = selectedEntries.filterNot { badPaths.contains(it.relativePath) }
                      selectedEntries.clear()
                      selectedEntries.addAll(validEntries)
                      unreadableFilesDetected = null
                      if (validEntries.isNotEmpty()) {
                        startZipCreation(validEntries)
                      }
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp)
                  ) {
                    Text(stringResource(R.string.tool8_continue_without))
                  }

                  OutlinedButton(
                    onClick = { unreadableFilesDetected = null },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp)
                  ) {
                    Text(stringResource(R.string.tool8_cancel_archive))
                  }
                }
              }
            }
          }
        }

        // Summary of items
        if (selectedEntries.isNotEmpty()) {
          item {
            val totalBytes = selectedEntries.sumOf { it.sizeBytes }
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Text(
                text = stringResource(R.string.tool8_items_summary, selectedEntries.size, FileFormatters.formatBytes(totalBytes)),
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary
              )

              ButtonDefaults.buttonColors()
              OutlinedButton(
                onClick = { selectedEntries.clear() },
                shape = RoundedCornerShape(8.dp)
              ) {
                Text(stringResource(R.string.tool8_clear_all))
              }
            }
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
                verticalArrangement = Arrangement.spacedBy(14.dp)
              ) {
                // Name
                OutlinedTextField(
                  value = archiveNameInput,
                  onValueChange = { archiveNameInput = it },
                  label = { Text(stringResource(R.string.tool8_output_name_label)) },
                  singleLine = true,
                  shape = RoundedCornerShape(12.dp),
                  modifier = Modifier
                    .fillMaxWidth()
                    .testTag("zip_name_input")
                )

                // Compression choice
                Text(
                  text = stringResource(R.string.tool8_compression_mode),
                  style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                )

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                  Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                      .fillMaxWidth()
                      .clickable { compressionMode = ZipCompressionMode.NORMAL }
                  ) {
                    RadioButton(
                      selected = compressionMode == ZipCompressionMode.NORMAL,
                      onClick = { compressionMode = ZipCompressionMode.NORMAL }
                    )
                    Text(
                      text = stringResource(R.string.tool8_compression_normal),
                      style = MaterialTheme.typography.bodyMedium,
                      modifier = Modifier.padding(start = 6.dp)
                    )
                  }

                  Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                      .fillMaxWidth()
                      .clickable { compressionMode = ZipCompressionMode.STORE }
                  ) {
                    RadioButton(
                      selected = compressionMode == ZipCompressionMode.STORE,
                      onClick = { compressionMode = ZipCompressionMode.STORE }
                    )
                    Text(
                      text = stringResource(R.string.tool8_compression_store),
                      style = MaterialTheme.typography.bodyMedium,
                      modifier = Modifier.padding(start = 6.dp)
                    )
                  }
                }

                Button(
                  enabled = selectedEntries.isNotEmpty() && !ToolJobRunner.isToolBusy(8),
                  onClick = {
                    val entries = selectedEntries.toList()
                    val unreadable = ZipWriterHelper.probeInputEntries(context, entries)
                    if (unreadable.isNotEmpty()) {
                      unreadableFilesDetected = unreadable
                    } else {
                      startZipCreation(entries)
                    }
                  },
                  modifier = Modifier
                    .fillMaxWidth()
                    .testTag("start_zip_button"),
                  shape = RoundedCornerShape(12.dp)
                ) {
                  Text(
                    text = stringResource(R.string.tool8_start_button),
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                  )
                }
              }
            }
          }

          // List of items with remove button
          items(selectedEntries) { entry ->
            Surface(
              shape = RoundedCornerShape(10.dp),
              color = MaterialTheme.colorScheme.surface,
              tonalElevation = 1.dp,
              modifier = Modifier.fillMaxWidth()
            ) {
              Row(
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
              ) {
                Column(modifier = Modifier.weight(1f)) {
                  Text(
                    text = entry.relativePath,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                  )
                  if (entry.sizeBytes > 0L) {
                    Text(
                      text = FileFormatters.formatBytes(entry.sizeBytes),
                      style = MaterialTheme.typography.bodySmall,
                      color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                  }
                }

                IconButton(
                  onClick = { selectedEntries.remove(entry) },
                  modifier = Modifier.size(32.dp)
                ) {
                  Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = stringResource(R.string.common_remove_file),
                    modifier = Modifier.size(16.dp)
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

@Composable
private fun ZipResultRow(label: String, value: String) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically
  ) {
    Text(
      text = label,
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Text(
      text = value,
      style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
      color = MaterialTheme.colorScheme.primary
    )
  }
}

private fun queryTreeRecursively(
  context: Context,
  treeUri: Uri,
  parentDocId: String,
  currentPath: String,
  sink: MutableList<ZipInputEntry>,
  maxItems: Int = ZipWriterHelper.MAX_ENTRIES
) {
  val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentDocId)
  val projection = arrayOf(
    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
    DocumentsContract.Document.COLUMN_MIME_TYPE,
    DocumentsContract.Document.COLUMN_SIZE
  )
  try {
    context.contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
      val idIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
      val nameIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
      val mimeIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
      val sizeIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE)

      while (cursor.moveToNext() && sink.size < maxItems) {
        val docId = cursor.getString(idIndex)
        val name = cursor.getString(nameIndex) ?: "file"
        val mime = cursor.getString(mimeIndex) ?: ""
        val size = if (sizeIndex != -1 && !cursor.isNull(sizeIndex)) cursor.getLong(sizeIndex) else 0L

        if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
          queryTreeRecursively(
            context = context,
            treeUri = treeUri,
            parentDocId = docId,
            currentPath = if (currentPath.isEmpty()) name else "$currentPath/$name",
            sink = sink,
            maxItems = maxItems
          )
        } else {
          val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
          val relPath = if (currentPath.isEmpty()) name else "$currentPath/$name"
          sink.add(
            ZipInputEntry(
              uri = docUri,
              localFile = null,
              relativePath = relPath,
              sizeBytes = size
            )
          )
        }
      }
    }
  } catch (_: Exception) {}
}
