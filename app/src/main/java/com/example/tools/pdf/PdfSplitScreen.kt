package com.example.tools.pdf

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CallSplit
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.file.FileFormatters
import com.example.file.FileLayer
import com.example.file.FileNameSanitizer
import com.example.pdf.MultiOutputSaver
import com.example.pdf.PageRangeParser
import com.example.pdf.PageRangeResult
import com.example.pdf.PdfErrors
import com.example.pdf.PdfFileHelper
import com.example.pdf.PdfThumbnailCache
import com.example.pdf.PdfVerifier
import com.example.tools.common.ErrorCard
import com.example.tools.common.FilePickerCard
import com.example.tools.common.FileResult
import com.example.tools.common.JobResult
import com.example.tools.common.JobState
import com.example.tools.common.PickerMode
import com.example.tools.common.ProgressCard
import com.example.tools.common.SelectedFileItem
import com.example.tools.common.SelectedFileRow
import com.example.tools.common.ToolJobRunner
import com.example.tools.common.ToolScaffold
import com.tom_roush.pdfbox.pdmodel.PDDocument
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

enum class SplitMode {
  EXTRACT,
  ALL_PAGES,
  RANGES
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PdfSplitScreen(onBack: () -> Unit) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()

  BackHandler { onBack() }

  var selectedItem by remember { mutableStateOf<SelectedFileItem?>(null) }
  var cachedInputFile by remember { mutableStateOf<File?>(null) }
  var totalPages by remember { mutableIntStateOf(0) }
  var isLoadingPdf by remember { mutableStateOf(false) }

  var splitMode by remember { mutableStateOf(SplitMode.EXTRACT) }
  var rangeText by remember { mutableStateOf("") }
  var rangeError by remember { mutableStateOf<String?>(null) }
  val selectedPages = remember { mutableStateMapOf<Int, Boolean>() }

  var currentJobId by rememberSaveable { mutableStateOf<String?>(null) }
  val jobUi by remember(currentJobId) {
    if (currentJobId != null) ToolJobRunner.observe(currentJobId!!)
    else flowOf(null)
  }.collectAsState(initial = null)

  var localErrorMessage by remember { mutableStateOf<String?>(null) }

  LaunchedEffect(currentJobId) {
    val id = currentJobId
    if (id != null && !ToolJobRunner.hasJob(id)) {
      currentJobId = null
      localErrorMessage = context.getString(R.string.common_job_restored_lost)
    }
  }
  val thumbnailCache = remember { PdfThumbnailCache(maxEntries = 16) }

  DisposableEffect(Unit) {
    onDispose {
      thumbnailCache.clear()
      PdfFileHelper.deleteQuietly(cachedInputFile)
    }
  }

  // SAF CreateDocument for saving a single PDF
  val saveSinglePdfLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.CreateDocument("application/pdf")
  ) { targetUri: Uri? ->
    val singleFile = jobUi?.result?.files?.firstOrNull()?.tempFilePath?.let { File(it) }
    if (targetUri != null && singleFile != null && singleFile.exists()) {
      val success = FileLayer.saveFileToUri(context, singleFile, targetUri)
      if (success) {
        Toast.makeText(context, context.getString(R.string.file_saved_success), Toast.LENGTH_SHORT).show()
      } else {
        Toast.makeText(context, context.getString(R.string.error_generic), Toast.LENGTH_SHORT).show()
      }
    }
  }

  // SAF CreateDocument for saving all output files as ZIP
  val saveZipLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.CreateDocument("application/zip")
  ) { targetUri: Uri? ->
    val outputFiles = jobUi?.result?.files?.mapNotNull { it.tempFilePath?.let { p -> File(p) } } ?: emptyList()
    if (targetUri != null && outputFiles.isNotEmpty()) {
      scope.launch(Dispatchers.IO) {
        try {
          val zipFile = MultiOutputSaver.createZipArchive(
            context = context,
            outputFiles = outputFiles,
            archiveBaseName = selectedItem?.name?.substringBeforeLast('.') ?: "split_pages"
          )
          context.contentResolver.openOutputStream(targetUri)?.use { outStream ->
            zipFile.inputStream().use { inStream -> inStream.copyTo(outStream) }
          }
          PdfFileHelper.deleteQuietly(zipFile)
          withContext(Dispatchers.Main) {
            Toast.makeText(context, context.getString(R.string.file_saved_success), Toast.LENGTH_SHORT).show()
          }
        } catch (e: Exception) {
          withContext(Dispatchers.Main) {
            Toast.makeText(context, "Failed to save ZIP: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
          }
        }
      }
    }
  }

  // SAF OpenDocumentTree for saving all output files into a chosen folder
  val saveToFolderLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.OpenDocumentTree()
  ) { treeUri: Uri? ->
    val outputFiles = jobUi?.result?.files?.mapNotNull { it.tempFilePath?.let { p -> File(p) } } ?: emptyList()
    if (treeUri != null && outputFiles.isNotEmpty()) {
      scope.launch(Dispatchers.IO) {
        val saveResult = MultiOutputSaver.saveToFolder(
          context = context,
          treeUri = treeUri,
          files = outputFiles,
          mimeType = "application/pdf"
        )
        withContext(Dispatchers.Main) {
          if (saveResult.failedCount == 0) {
            val msg = context.getString(R.string.tool11_folder_save_success, saveResult.successCount)
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
          } else {
            val msg = "Saved ${saveResult.successCount} files, ${saveResult.failedCount} failed."
            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
          }
        }
      }
    }
  }

  fun onPdfFilePicked(uri: Uri) {
    scope.launch {
      isLoadingPdf = true
      localErrorMessage = null
      currentJobId = null
      selectedPages.clear()
      rangeError = null

      PdfFileHelper.deleteQuietly(cachedInputFile)
      cachedInputFile = null

      try {
        val info = FileLayer.getUriFileInfo(context, uri)
        selectedItem = SelectedFileItem(uri = uri, name = info.displayName, sizeBytes = info.sizeBytes)

        val localCopy = withContext(Dispatchers.IO) {
          PdfFileHelper.copyPdfToCache(context, uri)
        }
        cachedInputFile = localCopy

        val pages = withContext(Dispatchers.IO) {
          PDDocument.load(localCopy).use { doc -> doc.numberOfPages }
        }
        totalPages = pages
        rangeText = if (pages > 1) "1-${pages.coerceAtMost(3)}" else "1"
      } catch (t: Throwable) {
        localErrorMessage = PdfErrors.mapPdfException(t)
        selectedItem = null
        cachedInputFile = null
        totalPages = 0
      } finally {
        isLoadingPdf = false
      }
    }
  }

  fun executeSplit() {
    val inputFile = cachedInputFile ?: return
    val originalName = selectedItem?.name ?: "document.pdf"
    val baseName = FileNameSanitizer.sanitizeFileName(originalName.substringBeforeLast('.'))

    val parsedIndices = mutableListOf<Int>()
    val parsedRanges = mutableListOf<List<Int>>()

    when (splitMode) {
      SplitMode.EXTRACT -> {
        val textToParse = rangeText.trim()
        if (textToParse.isNotEmpty()) {
          when (val res = PageRangeParser.parse(textToParse, totalPages)) {
            is PageRangeResult.Success -> {
              parsedIndices.addAll(res.pageIndices)
            }
            is PageRangeResult.Error -> {
              rangeError = res.message
              return
            }
          }
        } else {
          val indices = selectedPages.filter { it.value }.keys.sorted()
          if (indices.isEmpty()) {
            rangeError = context.getString(R.string.tool11_error_empty_range)
            return
          }
          parsedIndices.addAll(indices)
        }
      }
      SplitMode.ALL_PAGES -> {
        // Will split all pages
      }
      SplitMode.RANGES -> {
        val rawParts = rangeText.split(',').map { it.trim() }.filter { it.isNotEmpty() }
        if (rawParts.isEmpty()) {
          rangeError = context.getString(R.string.tool11_error_empty_range)
          return
        }
        for (part in rawParts) {
          when (val res = PageRangeParser.parse(part, totalPages)) {
            is PageRangeResult.Success -> {
              parsedRanges.add(res.pageIndices)
            }
            is PageRangeResult.Error -> {
              rangeError = res.message
              return
            }
          }
        }
      }
    }

    localErrorMessage = null
    val currentMode = splitMode

    val jobId = ToolJobRunner.start(
      toolId = 11,
      inputName = originalName,
      optionsJson = "{}"
    ) {
      reportProgress(0, context.getString(R.string.tool11_processing), 1, 1, originalName)
      val cacheDir = PdfFileHelper.getPdfCacheDir(context)
      val generated = mutableListOf<File>()

      when (currentMode) {
        SplitMode.EXTRACT -> {
          reportProgress(20, "Extracting ${parsedIndices.size} pages…", 1, 1, originalName)
          val outFile = File(cacheDir, "${baseName}_extracted_${System.currentTimeMillis()}.pdf")
          trackPartialOutput(outFile)
          PdfSplitHelper.extractPagesToSinglePdf(
            inputFile = inputFile,
            pageIndices = parsedIndices,
            outputFile = outFile,
            tempDir = cacheDir
          )
          reportProgress(85, "Verifying extracted document…", 1, 1, originalName)
          val verified = PdfVerifier.verifyPdfOutput(outFile, expectedPageCount = parsedIndices.size)
          if (!verified) {
            throw IOException("Verification failed on extracted PDF.")
          }
          generated.add(outFile)
        }
        SplitMode.ALL_PAGES -> {
          reportProgress(10, "Splitting $totalPages pages…", 1, 1, originalName)
          val results = PdfSplitHelper.splitIntoSinglePages(
            inputFile = inputFile,
            outputDir = cacheDir,
            baseName = "${baseName}_${System.currentTimeMillis().toString().takeLast(5)}",
            tempDir = cacheDir,
            onProgress = { prg, msg ->
              val pct = (prg * 85).toInt()
            }
          )
          for (f in results) {
            trackPartialOutput(f)
            val verified = PdfVerifier.verifyPdfOutput(f, expectedPageCount = 1)
            if (!verified) throw IOException("Verification failed on single-page PDF: ${f.name}")
          }
          generated.addAll(results)
        }
        SplitMode.RANGES -> {
          reportProgress(10, "Splitting into ${parsedRanges.size} parts…", 1, 1, originalName)
          val results = PdfSplitHelper.splitByRanges(
            inputFile = inputFile,
            ranges = parsedRanges,
            outputDir = cacheDir,
            baseName = "${baseName}_${System.currentTimeMillis().toString().takeLast(5)}",
            tempDir = cacheDir,
            onProgress = { prg, msg ->
              val pct = (prg * 85).toInt()
            }
          )
          for ((idx, f) in results.withIndex()) {
            trackPartialOutput(f)
            val expected = parsedRanges[idx].size
            val verified = PdfVerifier.verifyPdfOutput(f, expectedPageCount = expected)
            if (!verified) throw IOException("Verification failed on split part: ${f.name}")
          }
          generated.addAll(results)
        }
      }

      reportProgress(100, "Done", 1, 1, originalName)
      JobResult(
        files = generated.map { f ->
          FileResult(
            inputName = originalName,
            outputName = f.name,
            outputUri = null,
            outputSizeBytes = f.length(),
            success = true,
            originalSizeBytes = selectedItem?.sizeBytes ?: 0L,
            format = "pdf",
            tempFilePath = f.absolutePath
          )
        }
      )
    }

    currentJobId = jobId
  }

  ToolScaffold(
    title = stringResource(R.string.tool11_name),
    onBack = onBack
  ) { padding ->
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(padding)
        .padding(horizontal = 16.dp, vertical = 8.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
      if (selectedItem == null) {
        FilePickerCard(
          onFilesSelected = { uris -> uris.firstOrNull()?.let { onPdfFilePicked(it) } },
          title = stringResource(R.string.tool11_picker_title),
          supportedFormatsText = stringResource(R.string.tool11_picker_subtitle),
          pickerMode = PickerMode.SAF_DOCUMENTS,
          maxFiles = 1,
          mimeTypeFilter = arrayOf("application/pdf"),
          modifier = Modifier.testTag("pdf_split_picker")
        )
      } else {
        SelectedFileRow(
          fileItem = selectedItem!!,
          onRemove = {
            selectedItem = null
            PdfFileHelper.deleteQuietly(cachedInputFile)
            cachedInputFile = null
            totalPages = 0
            selectedPages.clear()
            currentJobId = null
            localErrorMessage = null
          }
        )
      }

      if (isLoadingPdf) {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
          horizontalArrangement = Arrangement.Center,
          verticalAlignment = Alignment.CenterVertically
        ) {
          CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
          Spacer(modifier = Modifier.width(12.dp))
          Text(text = "Loading PDF…", style = MaterialTheme.typography.bodyMedium)
        }
      }

      val displayError = localErrorMessage ?: jobUi?.errorMessage
      if (displayError != null) {
        ErrorCard(
          message = displayError,
          onRetry = if (cachedInputFile != null) { { executeSplit() } } else null
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

      // Result Section
      if (jobUi != null && jobUi!!.state == JobState.SUCCESS) {
        val outputFiles = jobUi!!.result?.files?.mapNotNull { it.tempFilePath?.let { p -> File(p) } } ?: emptyList()
        if (outputFiles.size == 1) {
          val res = outputFiles.first()
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
                text = stringResource(R.string.tool11_success_title),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary
              )
              Text(
                text = "${res.name} (${FileFormatters.formatBytes(res.length())})",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )

              com.example.tools.common.PdfResultPreview(res)

              FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
              ) {
                Button(
                  onClick = { saveSinglePdfLauncher.launch(res.name) },
                  shape = RoundedCornerShape(10.dp),
                  modifier = Modifier.testTag("action_save_pdf")
                ) {
                  Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                  Spacer(Modifier.size(6.dp))
                  Text(stringResource(R.string.tool11_btn_save_pdf))
                }

                OutlinedButton(
                  onClick = {
                    FileLayer.shareFileAndNotify(context, res, "application/pdf")
                  },
                  shape = RoundedCornerShape(10.dp)
                ) {
                  Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                  Spacer(Modifier.size(6.dp))
                  Text(stringResource(R.string.tool11_btn_share))
                }

                OutlinedButton(
                  onClick = {
                    FileLayer.openFileAndNotify(context, res, "application/pdf")
                  },
                  shape = RoundedCornerShape(10.dp)
                ) {
                  Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                  Spacer(Modifier.size(6.dp))
                  Text(stringResource(R.string.tool11_btn_open))
                }
              }
            }
          }
        } else if (outputFiles.size > 1) {
          var viewingSplitFile by remember { mutableStateOf<File?>(null) }
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
                text = stringResource(R.string.tool11_success_title),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary
              )
              Text(
                text = stringResource(R.string.tool11_success_desc_multi, outputFiles.size),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )

              // Multi-file thumbnails
              Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
              ) {
                for (file in outputFiles) {
                  Row(
                    modifier = Modifier
                      .fillMaxWidth()
                      .clip(RoundedCornerShape(8.dp))
                      .clickable { viewingSplitFile = file }
                      .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                      .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                  ) {
                    Box(modifier = Modifier.size(width = 54.dp, height = 72.dp)) {
                      com.example.tools.common.PdfResultPreview(file, previewHeight = 72.dp)
                    }
                    Column(modifier = Modifier.weight(1f)) {
                      Text(
                        text = file.name,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                      )
                      Text(
                        text = FileFormatters.formatBytes(file.length()),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                      )
                    }
                  }
                }
              }

              if (viewingSplitFile != null) {
                com.example.tools.common.PdfViewerDialog(
                  file = viewingSplitFile!!,
                  title = viewingSplitFile!!.name,
                  onDismiss = { viewingSplitFile = null }
                )
              }

              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
              ) {
                Button(
                  onClick = {
                    val zipName = "${selectedItem?.name?.substringBeforeLast('.') ?: "split"}.zip"
                    saveZipLauncher.launch(zipName)
                  },
                  modifier = Modifier.weight(1f)
                ) {
                  Icon(Icons.Default.FolderZip, contentDescription = null, modifier = Modifier.size(18.dp))
                  Spacer(modifier = Modifier.width(6.dp))
                  Text(stringResource(R.string.tool11_btn_save_zip), fontSize = 12.sp)
                }

                OutlinedButton(
                  onClick = { saveToFolderLauncher.launch(null) },
                  modifier = Modifier.weight(1f)
                ) {
                  Icon(Icons.Default.Folder, contentDescription = null, modifier = Modifier.size(18.dp))
                  Spacer(modifier = Modifier.width(6.dp))
                  Text(stringResource(R.string.tool11_btn_save_folder), fontSize = 12.sp)
                }
              }

              OutlinedButton(
                onClick = {
                  MultiOutputSaver.shareAll(
                    context = context,
                    files = outputFiles,
                    chooserTitle = "Share Split PDFs"
                  )
                },
                modifier = Modifier.fillMaxWidth()
              ) {
                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(stringResource(R.string.tool11_btn_share_all))
              }
            }
          }
        }
      }

      // Split Mode Tabs and configuration
      if (selectedItem != null && totalPages > 0 && !isLoadingPdf && jobUi?.state != JobState.RUNNING) {
        Card(
          modifier = Modifier.fillMaxWidth(),
          shape = RoundedCornerShape(16.dp),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
          elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
          Column(
            modifier = Modifier
              .fillMaxWidth()
              .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
          ) {
            Text(
              text = stringResource(R.string.tool11_pages_count, totalPages),
              style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
              color = MaterialTheme.colorScheme.primary
            )

            TabRow(
              selectedTabIndex = splitMode.ordinal,
              containerColor = MaterialTheme.colorScheme.surface
            ) {
              Tab(
                selected = splitMode == SplitMode.EXTRACT,
                onClick = {
                  splitMode = SplitMode.EXTRACT
                  rangeError = null
                },
                text = { Text(stringResource(R.string.tool11_mode_extract), fontSize = 12.sp) }
              )
              Tab(
                selected = splitMode == SplitMode.ALL_PAGES,
                onClick = {
                  splitMode = SplitMode.ALL_PAGES
                  rangeError = null
                },
                text = { Text(stringResource(R.string.tool11_mode_all_pages), fontSize = 12.sp) }
              )
              Tab(
                selected = splitMode == SplitMode.RANGES,
                onClick = {
                  splitMode = SplitMode.RANGES
                  rangeError = null
                },
                text = { Text(stringResource(R.string.tool11_mode_by_ranges), fontSize = 12.sp) }
              )
            }

            when (splitMode) {
              SplitMode.EXTRACT -> {
                Text(
                  text = stringResource(R.string.tool11_extract_hint),
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                  value = rangeText,
                  onValueChange = {
                    rangeText = it
                    rangeError = null
                  },
                  modifier = Modifier.fillMaxWidth(),
                  label = { Text(stringResource(R.string.tool11_page_range_label)) },
                  placeholder = { Text(stringResource(R.string.tool11_page_range_placeholder)) },
                  singleLine = true,
                  isError = rangeError != null
                )
              }
              SplitMode.ALL_PAGES -> {
                Text(
                  text = "Each page of this $totalPages-page document will be exported as an independent PDF.",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant
                )
              }
              SplitMode.RANGES -> {
                Text(
                  text = stringResource(R.string.tool11_ranges_hint),
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                  value = rangeText,
                  onValueChange = {
                    rangeText = it
                    rangeError = null
                  },
                  modifier = Modifier.fillMaxWidth(),
                  label = { Text("Ranges (comma separated)") },
                  placeholder = { Text("e.g. 1-3, 4-7") },
                  singleLine = true,
                  isError = rangeError != null
                )
              }
            }

            if (rangeError != null) {
              Text(
                text = rangeError!!,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
              )
            }
          }
        }

        // Thumbnails Grid (showing document pages)
        Box(
          modifier = Modifier
            .weight(1f)
            .fillMaxWidth()
        ) {
          LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 95.dp),
            contentPadding = PaddingValues(vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize()
          ) {
            items(
              count = totalPages,
              key = { it }
            ) { pageIdx ->
              val isSelected = selectedPages[pageIdx] == true

              var thumbBitmap by remember { mutableStateOf<Bitmap?>(null) }
              LaunchedEffect(pageIdx, cachedInputFile) {
                cachedInputFile?.let { f ->
                  thumbBitmap = thumbnailCache.getThumbnail(context, f, pageIdx, 200)
                }
              }

              Card(
                modifier = Modifier
                  .fillMaxWidth()
                  .clickable {
                    if (splitMode == SplitMode.EXTRACT) {
                      selectedPages[pageIdx] = !isSelected
                      val activeIndices = selectedPages.filter { it.value }.keys.sorted()
                      rangeText = activeIndices.map { it + 1 }.joinToString(", ")
                      rangeError = null
                    }
                  }
                  .border(
                    width = if (isSelected) 2.dp else 1.dp,
                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                    shape = RoundedCornerShape(8.dp)
                  ),
                shape = RoundedCornerShape(8.dp),
                colors = CardDefaults.cardColors(
                  containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)
                  else MaterialTheme.colorScheme.surface
                )
              ) {
                Column(
                  modifier = Modifier
                    .fillMaxWidth()
                    .padding(4.dp),
                  horizontalAlignment = Alignment.CenterHorizontally,
                  verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                  Box(
                    modifier = Modifier
                      .fillMaxWidth()
                      .aspectRatio(0.75f)
                      .background(Color.White, RoundedCornerShape(4.dp))
                      .clip(RoundedCornerShape(4.dp)),
                    contentAlignment = Alignment.Center
                  ) {
                    if (thumbBitmap != null) {
                      Image(
                        bitmap = thumbBitmap!!.asImageBitmap(),
                        contentDescription = "Page ${pageIdx + 1}",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                      )
                    } else {
                      CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                    }

                    if (isSelected) {
                      Box(
                        modifier = Modifier
                          .align(Alignment.TopStart)
                          .padding(2.dp)
                          .size(16.dp)
                          .background(MaterialTheme.colorScheme.primary, CircleShape),
                        contentAlignment = Alignment.Center
                      ) {
                        Icon(
                          Icons.Default.Check,
                          contentDescription = null,
                          tint = MaterialTheme.colorScheme.onPrimary,
                          modifier = Modifier.size(10.dp)
                        )
                      }
                    }
                  }

                  Text(
                    text = "Page ${pageIdx + 1}",
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                  )
                }
              }
            }
          }
        }

        Button(
          onClick = { executeSplit() },
          modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .testTag("split_pdf_button"),
          enabled = jobUi?.state != JobState.RUNNING && !ToolJobRunner.isToolBusy(11)
        ) {
          Icon(Icons.Default.CallSplit, contentDescription = null)
          Spacer(modifier = Modifier.width(8.dp))
          Text(
            if (splitMode == SplitMode.EXTRACT) stringResource(R.string.tool11_btn_extract)
            else stringResource(R.string.tool11_btn_split)
          )
        }
      }
    }
  }
}
