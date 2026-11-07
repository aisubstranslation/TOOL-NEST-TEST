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
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Rotate90DegreesCcw
import androidx.compose.material.icons.filled.Rotate90DegreesCw
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.rotate
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PdfRotatorScreen(onBack: () -> Unit) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()

  BackHandler { onBack() }

  var selectedItem by remember { mutableStateOf<SelectedFileItem?>(null) }
  var cachedInputFile by remember { mutableStateOf<File?>(null) }
  var totalPages by remember { mutableIntStateOf(0) }
  var isLoadingPdf by remember { mutableStateOf(false) }

  val selectedPages = remember { mutableStateMapOf<Int, Boolean>() }
  val pendingRotations = remember { mutableStateMapOf<Int, Int>() }

  var customRangeText by remember { mutableStateOf("") }
  var customRangeError by remember { mutableStateOf<String?>(null) }
  var activeFilter by remember { mutableStateOf("all") }

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

  // SAF CreateDocument for saving rotated PDF
  val savePdfLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.CreateDocument("application/pdf")
  ) { targetUri: Uri? ->
    val resFile = jobUi?.result?.files?.firstOrNull()?.tempFilePath?.let { File(it) }
    if (targetUri != null && resFile != null && resFile.exists()) {
      val success = FileLayer.saveFileToUri(context, resFile, targetUri)
      if (success) {
        Toast.makeText(context, context.getString(R.string.file_saved_success), Toast.LENGTH_SHORT).show()
      } else {
        Toast.makeText(context, context.getString(R.string.error_generic), Toast.LENGTH_SHORT).show()
      }
    }
  }

  fun onPdfFilePicked(uri: Uri) {
    scope.launch {
      isLoadingPdf = true
      localErrorMessage = null
      currentJobId = null
      selectedPages.clear()
      pendingRotations.clear()
      customRangeError = null

      PdfFileHelper.deleteQuietly(cachedInputFile)
      cachedInputFile = null

      try {
        val info = FileLayer.getUriFileInfo(context, uri)
        selectedItem = SelectedFileItem(uri = uri, name = info.displayName, sizeBytes = info.sizeBytes)

        val localFile = withContext(Dispatchers.IO) {
          PdfFileHelper.copyPdfToCache(context, uri)
        }
        cachedInputFile = localFile

        val pages = withContext(Dispatchers.IO) {
          PDDocument.load(localFile).use { doc -> doc.numberOfPages }
        }
        totalPages = pages

        for (i in 0 until pages) {
          selectedPages[i] = true
        }
        activeFilter = "all"
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

  fun rotateSelected(delta: Int) {
    for (i in 0 until totalPages) {
      if (selectedPages[i] == true) {
        val currentPending = pendingRotations[i] ?: 0
        pendingRotations[i] = PdfRotationHelper.applyDelta(currentPending, delta)
      }
    }
  }

  fun resetRotations() {
    pendingRotations.clear()
  }

  fun executeRotation() {
    val inputFile = cachedInputFile ?: return
    val originalName = selectedItem?.name ?: "document.pdf"
    val baseName = FileNameSanitizer.sanitizeFileName(originalName.substringBeforeLast('.'))

    localErrorMessage = null
    val anglesCopy = pendingRotations.toMap()

    val jobId = ToolJobRunner.start(
      toolId = 15,
      inputName = originalName,
      optionsJson = "{}"
    ) {
      reportProgress(0, context.getString(R.string.tool15_processing), 1, 1, originalName)
      val cacheDir = PdfFileHelper.getPdfCacheDir(context)
      val outFile = File(cacheDir, "${baseName}_rotated_${System.currentTimeMillis()}.pdf")
      trackPartialOutput(outFile)

      var doc: PDDocument? = null
      try {
        doc = PdfFileHelper.openDocument(inputFile, cacheDir)
        val count = doc.numberOfPages
        for (i in 0 until count) {
          ensureActive()
          val delta = anglesCopy[i] ?: 0
          if (delta != 0) {
            val page = doc.getPage(i)
            page.rotation = PdfRotationHelper.applyDelta(page.rotation, delta)
          }
          val percent = 10 + (70 * (i + 1) / count)
          reportProgress(percent, "Applying rotations ($i/$count)…", 1, 1, originalName)
        }

        reportProgress(85, "Writing document…", 1, 1, originalName)
        doc.save(outFile)
      } finally {
        try { doc?.close() } catch (_: Exception) {}
      }

      reportProgress(95, "Verifying PDF structure…", 1, 1, originalName)
      val verified = PdfVerifier.verifyPdfOutput(outFile, expectedPageCount = totalPages)
      if (!verified) {
        throw IOException("PDF verification failed after rotation. The file could not be rendered safely.")
      }

      reportProgress(100, "Done", 1, 1, originalName)
      JobResult(
        files = listOf(
          FileResult(
            inputName = originalName,
            outputName = outFile.name,
            outputUri = null,
            outputSizeBytes = outFile.length(),
            success = true,
            originalSizeBytes = selectedItem?.sizeBytes ?: 0L,
            format = "pdf",
            tempFilePath = outFile.absolutePath
          )
        )
      )
    }

    currentJobId = jobId
  }

  ToolScaffold(
    title = stringResource(R.string.tool15_name),
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
          title = stringResource(R.string.tool15_select_pdf),
          supportedFormatsText = stringResource(R.string.tool15_picker_subtitle),
          pickerMode = PickerMode.SAF_DOCUMENTS,
          maxFiles = 1,
          mimeTypeFilter = arrayOf("application/pdf"),
          modifier = Modifier.testTag("pdf_rotator_picker")
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
            pendingRotations.clear()
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
          Text(text = "Loading PDF pages…", style = MaterialTheme.typography.bodyMedium)
        }
      }

      val displayError = localErrorMessage ?: jobUi?.errorMessage
      if (displayError != null) {
        ErrorCard(
          message = displayError,
          onRetry = if (cachedInputFile != null) { { executeRotation() } } else null
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
                text = stringResource(R.string.tool15_success_title),
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
                  Text(stringResource(R.string.tool15_btn_save))
                }

                OutlinedButton(
                  onClick = {
                    FileLayer.shareFileAndNotify(context, localFile, "application/pdf")
                  },
                  shape = RoundedCornerShape(10.dp)
                ) {
                  Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                  Spacer(Modifier.size(6.dp))
                  Text(stringResource(R.string.tool15_btn_share))
                }

                OutlinedButton(
                  onClick = {
                    FileLayer.openFileAndNotify(context, localFile, "application/pdf")
                  },
                  shape = RoundedCornerShape(10.dp)
                ) {
                  Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                  Spacer(Modifier.size(6.dp))
                  Text(stringResource(R.string.tool15_btn_open))
                }
              }
            }
          }
        }
      }

      // Main controls and thumbnail grid when PDF is loaded and not running
      if (selectedItem != null && cachedInputFile != null && totalPages > 0 && !isLoadingPdf && jobUi?.state != JobState.RUNNING) {
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
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Text(
                text = stringResource(R.string.tool15_pages_count, totalPages),
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary
              )
              val selectedCount = selectedPages.count { it.value }
              Text(
                text = stringResource(R.string.tool15_selected_count, selectedCount),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
            }

            // Quick Selection Chips
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
              horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
              FilterChip(
                selected = activeFilter == "all",
                onClick = {
                  activeFilter = "all"
                  for (i in 0 until totalPages) selectedPages[i] = true
                },
                label = { Text(stringResource(R.string.tool15_select_all)) }
              )
              FilterChip(
                selected = activeFilter == "odd",
                onClick = {
                  activeFilter = "odd"
                  for (i in 0 until totalPages) {
                    selectedPages[i] = PdfRotationHelper.matchesFilter(i, "odd")
                  }
                },
                label = { Text(stringResource(R.string.tool15_select_odd)) }
              )
              FilterChip(
                selected = activeFilter == "even",
                onClick = {
                  activeFilter = "even"
                  for (i in 0 until totalPages) {
                    selectedPages[i] = PdfRotationHelper.matchesFilter(i, "even")
                  }
                },
                label = { Text(stringResource(R.string.tool15_select_even)) }
              )
              FilterChip(
                selected = activeFilter == "custom",
                onClick = { activeFilter = "custom" },
                label = { Text(stringResource(R.string.tool15_select_custom)) }
              )
            }

            if (activeFilter == "custom") {
              Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
              ) {
                OutlinedTextField(
                  value = customRangeText,
                  onValueChange = {
                    customRangeText = it
                    customRangeError = null
                  },
                  modifier = Modifier.weight(1f),
                  placeholder = { Text(stringResource(R.string.tool15_custom_range_placeholder)) },
                  singleLine = true,
                  isError = customRangeError != null
                )
                Button(
                  onClick = {
                    val res = PageRangeParser.parse(customRangeText, totalPages)
                    when (res) {
                      is PageRangeResult.Success -> {
                        selectedPages.clear()
                        for (idx in res.pageIndices) {
                          selectedPages[idx] = true
                        }
                        customRangeError = null
                      }
                      is PageRangeResult.Error -> {
                        customRangeError = res.message
                      }
                    }
                  }
                ) {
                  Text(stringResource(R.string.tool15_apply_range))
                }
              }
              if (customRangeError != null) {
                Text(
                  text = customRangeError!!,
                  color = MaterialTheme.colorScheme.error,
                  style = MaterialTheme.typography.bodySmall
                )
              }
            }

            // Rotation action buttons
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
              horizontalArrangement = Arrangement.spacedBy(8.dp),
              verticalAlignment = Alignment.CenterVertically
            ) {
              OutlinedButton(
                onClick = { rotateSelected(90) },
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
              ) {
                Icon(Icons.Default.Rotate90DegreesCw, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(stringResource(R.string.tool15_rotate_cw), fontSize = 12.sp)
              }
              OutlinedButton(
                onClick = { rotateSelected(180) },
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
              ) {
                Icon(Icons.Default.RotateRight, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(stringResource(R.string.tool15_rotate_180), fontSize = 12.sp)
              }
              OutlinedButton(
                onClick = { rotateSelected(-90) },
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
              ) {
                Icon(Icons.Default.Rotate90DegreesCcw, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(stringResource(R.string.tool15_rotate_ccw), fontSize = 12.sp)
              }
              IconButton(
                onClick = { resetRotations() },
                modifier = Modifier.size(36.dp)
              ) {
                Icon(
                  Icons.Default.Refresh,
                  contentDescription = stringResource(R.string.tool15_reset),
                  tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
              }
            }
          }
        }

        val hasChanges = pendingRotations.any { it.value != 0 }

        Box(
          modifier = Modifier
            .weight(1f)
            .fillMaxWidth()
        ) {
          LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 105.dp),
            contentPadding = PaddingValues(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxSize()
          ) {
            items(
              count = totalPages,
              key = { it }
            ) { pageIdx ->
              val isSelected = selectedPages[pageIdx] == true
              val pendingAngle = pendingRotations[pageIdx] ?: 0

              var thumbBitmap by remember { mutableStateOf<Bitmap?>(null) }
              LaunchedEffect(pageIdx, cachedInputFile) {
                cachedInputFile?.let { f ->
                  thumbBitmap = thumbnailCache.getThumbnail(context, f, pageIdx, 250)
                }
              }

              Card(
                modifier = Modifier
                  .fillMaxWidth()
                  .clickable {
                    selectedPages[pageIdx] = !isSelected
                  }
                  .border(
                    width = if (isSelected) 2.dp else 1.dp,
                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                    shape = RoundedCornerShape(10.dp)
                  ),
                shape = RoundedCornerShape(10.dp),
                colors = CardDefaults.cardColors(
                  containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)
                  else MaterialTheme.colorScheme.surface
                )
              ) {
                Column(
                  modifier = Modifier
                    .fillMaxWidth()
                    .padding(6.dp),
                  horizontalAlignment = Alignment.CenterHorizontally,
                  verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                  Box(
                    modifier = Modifier
                      .fillMaxWidth()
                      .aspectRatio(0.75f)
                      .background(Color.White, RoundedCornerShape(6.dp))
                      .clip(RoundedCornerShape(6.dp)),
                    contentAlignment = Alignment.Center
                  ) {
                    if (thumbBitmap != null) {
                      Image(
                        bitmap = thumbBitmap!!.asImageBitmap(),
                        contentDescription = "Page ${pageIdx + 1}",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                          .fillMaxSize()
                          .rotate(pendingAngle.toFloat())
                      )
                    } else {
                      CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    }

                    if (isSelected) {
                      Box(
                        modifier = Modifier
                          .align(Alignment.TopStart)
                          .padding(4.dp)
                          .size(18.dp)
                          .background(MaterialTheme.colorScheme.primary, CircleShape),
                        contentAlignment = Alignment.Center
                      ) {
                        Icon(
                          Icons.Default.Check,
                          contentDescription = null,
                          tint = MaterialTheme.colorScheme.onPrimary,
                          modifier = Modifier.size(12.dp)
                        )
                      }
                    }

                    if (pendingAngle != 0) {
                      Surface(
                        modifier = Modifier
                          .align(Alignment.TopEnd)
                          .padding(4.dp),
                        shape = RoundedCornerShape(4.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer
                      ) {
                        Text(
                          text = "+$pendingAngle°",
                          fontSize = 10.sp,
                          fontWeight = FontWeight.Bold,
                          color = MaterialTheme.colorScheme.onSecondaryContainer,
                          modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                        )
                      }
                    }
                  }

                  Text(
                    text = stringResource(R.string.tool15_page_label, pageIdx + 1),
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                  )
                }
              }
            }
          }
        }

        Button(
          onClick = { executeRotation() },
          modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .testTag("apply_rotations_button"),
          enabled = hasChanges && (jobUi?.state != JobState.RUNNING) && !ToolJobRunner.isToolBusy(15)
        ) {
          Icon(Icons.Default.RotateRight, contentDescription = null)
          Spacer(modifier = Modifier.width(8.dp))
          Text(stringResource(R.string.tool15_btn_rotate))
        }
      }
    }
  }
}
