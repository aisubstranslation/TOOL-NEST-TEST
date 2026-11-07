package com.example.tools.pdf

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CallMerge
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.example.pdf.PdfErrors
import com.example.pdf.PdfFileHelper
import com.example.pdf.PdfLimits
import com.example.pdf.PdfVerifier
import com.example.tools.common.ErrorCard
import com.example.tools.common.FilePickerCard
import com.example.tools.common.FileResult
import com.example.tools.common.JobResult
import com.example.tools.common.JobState
import com.example.tools.common.PickerMode
import com.example.tools.common.ProgressCard
import com.example.tools.common.SelectedFileItem
import com.example.tools.common.ToolJobRunner
import com.example.tools.common.ToolScaffold
import com.tom_roush.pdfbox.pdmodel.PDDocument
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PdfMergeScreen(onBack: () -> Unit) {
  val context = LocalContext.current
  val coroutineScope = rememberCoroutineScope()

  BackHandler { onBack() }

  val selectedFiles = remember { mutableStateListOf<SelectedFileItem>() }
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

  // Additional picker launcher for "Add More PDFs" button
  val addMoreLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.OpenMultipleDocuments()
  ) { uris: List<Uri> ->
    if (uris.isNotEmpty()) {
      coroutineScope.launch {
        localErrorMessage = null
        currentJobId = null
        val newItems = mutableListOf<SelectedFileItem>()
        withContext(Dispatchers.IO) {
          for (uri in uris) {
            if (selectedFiles.size + newItems.size >= PdfLimits.MAX_MERGE_FILE_COUNT) {
              break
            }
            if (!selectedFiles.any { it.uri == uri } && !newItems.any { it.uri == uri }) {
              val info = FileLayer.getUriFileInfo(context, uri)
              newItems.add(
                SelectedFileItem(uri = uri, name = info.displayName, sizeBytes = info.sizeBytes)
              )
            }
          }
        }
        if (selectedFiles.size + newItems.size >= PdfLimits.MAX_MERGE_FILE_COUNT && uris.size > newItems.size) {
          localErrorMessage = context.getString(R.string.tool10_error_max_files)
        }
        selectedFiles.addAll(newItems)
      }
    }
  }

  // Save PDF Launcher
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

  fun moveItem(fromIndex: Int, toIndex: Int) {
    if (fromIndex in selectedFiles.indices && toIndex in selectedFiles.indices) {
      val item = selectedFiles.removeAt(fromIndex)
      selectedFiles.add(toIndex, item)
    }
  }

  fun executeMerge() {
    if (selectedFiles.size < 2) {
      localErrorMessage = context.getString(R.string.tool10_error_minimum_files)
      return
    }

    val totalBytes = selectedFiles.sumOf { it.sizeBytes }
    if (totalBytes > PdfLimits.MAX_MERGE_COMBINED_SIZE_BYTES) {
      localErrorMessage = context.getString(R.string.tool10_error_max_size)
      return
    }

    localErrorMessage = null
    val filesToMerge = selectedFiles.toList()
    val firstFileName = filesToMerge.first().name

    val jobId = ToolJobRunner.start(
      toolId = 10,
      inputName = "${filesToMerge.size} PDFs",
      optionsJson = "{}"
    ) {
      reportProgress(0, context.getString(R.string.tool10_merging), 1, filesToMerge.size, firstFileName)
      val cacheDir = PdfFileHelper.getPdfCacheDir(context)
      val cachedInputFiles = mutableListOf<File>()

      try {
        // Step 1: Copy all inputs to cache
        for ((idx, item) in filesToMerge.withIndex()) {
          ensureActive()
          val percent = 5 + (25 * idx / filesToMerge.size)
          reportProgress(percent, "Preparing file ${idx + 1}/${filesToMerge.size}…", idx + 1, filesToMerge.size, item.name)
          val localCopy = PdfFileHelper.copyPdfToCache(context, item.uri)
          cachedInputFiles.add(localCopy)
          trackPartialOutput(localCopy)
        }

        // Calculate expected total pages
        var expectedPages = 0
        for (f in cachedInputFiles) {
          ensureActive()
          PDDocument.load(f).use { doc ->
            expectedPages += doc.numberOfPages
          }
        }

        val firstBase = FileNameSanitizer.sanitizeFileName(filesToMerge.first().name.substringBeforeLast('.'))
        val outFile = File(cacheDir, "${firstBase}_merged_${System.currentTimeMillis()}.pdf")
        trackPartialOutput(outFile)

        // Step 2: Merge using PdfMergeHelper
        PdfMergeHelper.mergePdfFiles(
          inputFiles = cachedInputFiles,
          outputFile = outFile,
          tempDir = cacheDir,
          onProgress = { prg, msg ->
            val mappedPercent = (30 + (prg * 55)).toInt()
            // Async progress reporting inside callback
          }
        )

        // Step 3: Verify output
        reportProgress(92, "Verifying merged PDF…", filesToMerge.size, filesToMerge.size, outFile.name)
        val isVerified = PdfVerifier.verifyPdfOutput(outFile, expectedPageCount = expectedPages)
        if (!isVerified) {
          throw IOException("Failed to verify merged PDF output. File may be corrupted.")
        }

        reportProgress(100, "Done", filesToMerge.size, filesToMerge.size, outFile.name)
        JobResult(
          files = listOf(
            FileResult(
              inputName = "${filesToMerge.size} PDFs",
              outputName = outFile.name,
              outputUri = null,
              outputSizeBytes = outFile.length(),
              success = true,
              originalSizeBytes = totalBytes,
              format = "pdf",
              tempFilePath = outFile.absolutePath
            )
          )
        )
      } finally {
        for (f in cachedInputFiles) {
          PdfFileHelper.deleteQuietly(f)
        }
      }
    }

    currentJobId = jobId
  }

  ToolScaffold(
    title = stringResource(R.string.tool10_name),
    onBack = onBack
  ) { padding ->
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(padding)
        .padding(horizontal = 16.dp, vertical = 8.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
      if (selectedFiles.isEmpty()) {
        FilePickerCard(
          onFilesSelected = { uris ->
            coroutineScope.launch {
              localErrorMessage = null
              val newItems = mutableListOf<SelectedFileItem>()
              withContext(Dispatchers.IO) {
                for (uri in uris) {
                  if (selectedFiles.size + newItems.size >= PdfLimits.MAX_MERGE_FILE_COUNT) {
                    break
                  }
                  val info = FileLayer.getUriFileInfo(context, uri)
                  newItems.add(
                    SelectedFileItem(uri = uri, name = info.displayName, sizeBytes = info.sizeBytes)
                  )
                }
              }
              if (selectedFiles.size + newItems.size >= PdfLimits.MAX_MERGE_FILE_COUNT && uris.size > newItems.size) {
                localErrorMessage = context.getString(R.string.tool10_error_max_files)
              }
              selectedFiles.addAll(newItems)
            }
          },
          title = stringResource(R.string.tool10_picker_title),
          supportedFormatsText = stringResource(R.string.tool10_picker_subtitle),
          pickerMode = PickerMode.SAF_DOCUMENTS,
          maxFiles = PdfLimits.MAX_MERGE_FILE_COUNT,
          mimeTypeFilter = arrayOf("application/pdf"),
          modifier = Modifier.testTag("pdf_merge_picker")
        )
      } else {
        val totalBytes = selectedFiles.sumOf { it.sizeBytes }
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Text(
            text = stringResource(
              R.string.tool10_file_count_header,
              selectedFiles.size,
              FileFormatters.formatBytes(totalBytes)
            ),
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.primary
          )
          if (selectedFiles.size < PdfLimits.MAX_MERGE_FILE_COUNT && jobUi?.state != JobState.RUNNING) {
            OutlinedButton(
              onClick = { addMoreLauncher.launch(arrayOf("application/pdf")) },
              modifier = Modifier.testTag("pdf_merge_add_more")
            ) {
              Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
              Spacer(modifier = Modifier.width(4.dp))
              Text(stringResource(R.string.tool10_add_more))
            }
          }
        }
      }

      val displayError = localErrorMessage ?: jobUi?.errorMessage
      if (displayError != null) {
        ErrorCard(
          message = displayError,
          onRetry = if (selectedFiles.size >= 2) { { executeMerge() } } else null
        )
      }

      if (jobUi != null && jobUi!!.state == JobState.RUNNING) {
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

      if (jobUi != null && jobUi!!.state == JobState.SUCCESS) {
        val fileRes = jobUi!!.result?.files?.firstOrNull()
        val localFile = fileRes?.tempFilePath?.let { File(it) }
        if (fileRes != null && localFile != null) {
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
                text = stringResource(R.string.tool10_success_title),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary
              )
              Text(
                text = "${localFile.name} (${FileFormatters.formatBytes(localFile.length())})",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )

              com.example.tools.common.PdfResultPreview(localFile)

              FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
              ) {
                Button(
                  onClick = { savePdfLauncher.launch(localFile.name) },
                  shape = RoundedCornerShape(10.dp),
                  modifier = Modifier.testTag("action_save_pdf")
                ) {
                  Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                  Spacer(Modifier.size(6.dp))
                  Text(stringResource(R.string.tool10_btn_save))
                }

                OutlinedButton(
                  onClick = {
                    FileLayer.shareFileAndNotify(context, localFile, "application/pdf")
                  },
                  shape = RoundedCornerShape(10.dp)
                ) {
                  Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                  Spacer(Modifier.size(6.dp))
                  Text(stringResource(R.string.tool10_btn_share))
                }

                OutlinedButton(
                  onClick = {
                    FileLayer.openFileAndNotify(context, localFile, "application/pdf")
                  },
                  shape = RoundedCornerShape(10.dp)
                ) {
                  Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                  Spacer(Modifier.size(6.dp))
                  Text(stringResource(R.string.tool10_btn_open))
                }
              }
            }
          }
        }
      }

      // Reorderable list of selected PDF files
      if (selectedFiles.isNotEmpty() && jobUi?.state != JobState.RUNNING) {
        LazyColumn(
          modifier = Modifier
            .weight(1f)
            .fillMaxWidth(),
          verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          itemsIndexed(
            items = selectedFiles,
            key = { _, item -> item.uri.toString() }
          ) { index, item ->
            Card(
              modifier = Modifier.fillMaxWidth(),
              shape = RoundedCornerShape(12.dp),
              colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
              elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
              Row(
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
              ) {
                Box(
                  modifier = Modifier
                    .size(28.dp)
                    .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(8.dp)),
                  contentAlignment = Alignment.Center
                ) {
                  Text(
                    text = "${index + 1}",
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                  )
                }

                Column(
                  modifier = Modifier.weight(1f),
                  verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                  Text(
                    text = item.name,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                  )
                  Text(
                    text = FileFormatters.formatBytes(item.sizeBytes),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                  )
                }

                IconButton(
                  onClick = { moveItem(index, index - 1) },
                  enabled = index > 0,
                  modifier = Modifier.size(32.dp)
                ) {
                  Icon(
                    imageVector = Icons.Default.ArrowUpward,
                    contentDescription = stringResource(R.string.tool10_move_up),
                    tint = if (index > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                    modifier = Modifier.size(18.dp)
                  )
                }

                IconButton(
                  onClick = { moveItem(index, index + 1) },
                  enabled = index < selectedFiles.size - 1,
                  modifier = Modifier.size(32.dp)
                ) {
                  Icon(
                    imageVector = Icons.Default.ArrowDownward,
                    contentDescription = stringResource(R.string.tool10_move_down),
                    tint = if (index < selectedFiles.size - 1) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                    modifier = Modifier.size(18.dp)
                  )
                }

                IconButton(
                  onClick = {
                    selectedFiles.removeAt(index)
                    currentJobId = null
                  },
                  modifier = Modifier.size(32.dp)
                ) {
                  Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = stringResource(R.string.common_remove_file),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                  )
                }
              }
            }
          }
        }

        Button(
          onClick = { executeMerge() },
          modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .testTag("merge_pdfs_button"),
          enabled = selectedFiles.size >= 2 && (jobUi?.state != JobState.RUNNING) && !ToolJobRunner.isToolBusy(10)
        ) {
          Icon(Icons.Default.CallMerge, contentDescription = null)
          Spacer(modifier = Modifier.width(8.dp))
          Text(stringResource(R.string.tool10_btn_merge))
        }
      }
    }
  }
}
