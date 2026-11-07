package com.example.tools.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Build
import android.os.ParcelFileDescriptor
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import com.example.pdf.MultiOutputSaver
import com.example.pdf.PageRangeParser
import com.example.pdf.PageRangeResult
import com.example.pdf.PdfErrors
import com.example.pdf.PdfFileHelper
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.DriveFileMove
import androidx.compose.material.icons.filled.Image
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.example.tools.common.ErrorCard
import com.example.tools.common.FilePickerCard
import com.example.tools.common.FileResult
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
fun PdfToImagesScreen(onBack: () -> Unit) {
  val context = LocalContext.current
  val coroutineScope = rememberCoroutineScope()

  BackHandler { onBack() }

  var selectedPdfUri by rememberSaveable { mutableStateOf<Uri?>(null) }
  var cachedInputFile by remember { mutableStateOf<File?>(null) }
  var totalPages by rememberSaveable { mutableIntStateOf(0) }
  var pageDimensionsMap by remember { mutableStateOf<Map<Int, Pair<Int, Int>>>(emptyMap()) }
  var isLoadingPdf by remember { mutableStateOf(false) }

  var isAllPages by rememberSaveable { mutableStateOf(true) }
  var rangeText by rememberSaveable { mutableStateOf("") }
  var rangeError by remember { mutableStateOf<String?>(null) }

  var outputFormat by rememberSaveable { mutableStateOf("PNG") }
  var jpegQuality by rememberSaveable { mutableFloatStateOf(90f) }
  var selectedDpi by rememberSaveable { mutableIntStateOf(150) }

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

  // Load PDF metadata when selected
  fun loadPdfMetadata(uri: Uri) {
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

        val (pagesCount, dims) = withContext(Dispatchers.IO) {
          var pfd: ParcelFileDescriptor? = null
          var renderer: PdfRenderer? = null
          try {
            pfd = ParcelFileDescriptor.open(cached, ParcelFileDescriptor.MODE_READ_ONLY)
            renderer = PdfRenderer(pfd)
            val count = renderer.pageCount
            val map = mutableMapOf<Int, Pair<Int, Int>>()
            for (i in 0 until count) {
              val p = renderer.openPage(i)
              map[i] = p.width to p.height
              p.close()
            }
            count to map
          } finally {
            try { renderer?.close() } catch (_: Exception) {}
            try { pfd?.close() } catch (_: Exception) {}
          }
        }

        totalPages = pagesCount
        pageDimensionsMap = dims
      } catch (e: Exception) {
        localErrorMessage = PdfErrors.mapPdfException(e)
        selectedPdfUri = null
        cachedInputFile = null
        totalPages = 0
      } finally {
        isLoadingPdf = false
      }
    }
  }

  // Determine which page indices will be rendered
  val selectedPageIndices: List<Int> = remember(isAllPages, rangeText, totalPages) {
    if (totalPages <= 0) emptyList()
    else if (isAllPages) (0 until totalPages).toList()
    else {
      when (val parsed = PageRangeParser.parse(rangeText, totalPages)) {
        is PageRangeResult.Success -> {
          rangeError = null
          parsed.pageIndices
        }
        is PageRangeResult.Error -> {
          rangeError = parsed.message
          emptyList()
        }
      }
    }
  }

  // Check 16 Megapixel limit across all selected pages
  val mpExceededPage = remember(selectedPageIndices, pageDimensionsMap, selectedDpi) {
    selectedPageIndices.firstOrNull { pageIdx ->
      val (w, h) = pageDimensionsMap[pageIdx] ?: (595 to 842)
      PdfToImagesMath.checkExceedsMaxPixels(w, h, selectedDpi)
    }
  }

  // Launchers for MultiOutputSaver actions
  val saveZipLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.CreateDocument("application/zip")
  ) { targetUri: Uri? ->
    val files = jobUi?.result?.files?.mapNotNull { it.tempFilePath?.let { p -> File(p) } } ?: emptyList()
    if (targetUri != null && files.isNotEmpty()) {
      coroutineScope.launch {
        val success = withContext(Dispatchers.IO) {
          val zip = MultiOutputSaver.createZipArchive(context, files, "pdf_images")
          FileLayer.saveFileToUri(context, zip, targetUri)
        }
        if (success) {
          Toast.makeText(context, context.getString(R.string.file_saved_success), Toast.LENGTH_SHORT).show()
        } else {
          Toast.makeText(context, context.getString(R.string.error_generic), Toast.LENGTH_SHORT).show()
        }
      }
    }
  }

  val saveFolderLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.OpenDocumentTree()
  ) { treeUri: Uri? ->
    val files = jobUi?.result?.files?.mapNotNull { it.tempFilePath?.let { p -> File(p) } } ?: emptyList()
    if (treeUri != null && files.isNotEmpty()) {
      coroutineScope.launch {
        val mime = if (outputFormat == "JPEG") "image/jpeg" else "image/png"
        val result = withContext(Dispatchers.IO) {
          MultiOutputSaver.saveToFolder(context, treeUri, files, mime)
        }
        Toast.makeText(context, "Saved ${result.successCount} of ${files.size} images", Toast.LENGTH_SHORT).show()
      }
    }
  }

  fun executeConversion() {
    val cached = cachedInputFile ?: return
    if (selectedPageIndices.isEmpty() || mpExceededPage != null || ToolJobRunner.isToolBusy(13)) return
    localErrorMessage = null

    val info = FileLayer.getUriFileInfo(context, selectedPdfUri!!)
    val options = PdfToImagesOptions(
      format = outputFormat,
      jpegQuality = jpegQuality.toInt(),
      dpi = selectedDpi
    )
    val pagesToRender = selectedPageIndices.toList()

    val jobId = ToolJobRunner.start(
      toolId = 13,
      inputName = "${pagesToRender.size} pages from ${info.displayName}",
      optionsJson = "format=$outputFormat,dpi=$selectedDpi,quality=${jpegQuality.toInt()}"
    ) {
      PdfToImagesHelper.convertPdfToImages(
        context = context,
        cachedPdf = cached,
        originalName = info.displayName,
        pageIndices = pagesToRender,
        options = options,
        jobContext = this
      )
    }

    currentJobId = jobId
  }

  ToolScaffold(
    title = stringResource(R.string.tool13_name),
    onBack = onBack
  ) { padding ->
    LazyColumn(
      modifier = Modifier
        .fillMaxSize()
        .padding(padding)
        .padding(horizontal = 16.dp, vertical = 8.dp)
        .testTag("tool_screen_13"),
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
        val resultFiles = jobUi!!.result?.files ?: emptyList()
        val localFiles = resultFiles.mapNotNull { it.tempFilePath?.let { p -> File(p) } }

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
              Text(
                text = stringResource(R.string.tool13_success_title, resultFiles.size),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
              )

              // Multi-Output Saver Action Buttons
              FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
              ) {
                Button(
                  onClick = { saveZipLauncher.launch("pdf_pages.zip") },
                  shape = RoundedCornerShape(8.dp)
                ) {
                  Icon(Icons.Default.Archive, contentDescription = null, modifier = Modifier.size(16.dp))
                  Spacer(modifier = Modifier.width(6.dp))
                  Text(stringResource(R.string.tool13_action_zip))
                }

                OutlinedButton(
                  onClick = { saveFolderLauncher.launch(null) },
                  shape = RoundedCornerShape(8.dp)
                ) {
                  Icon(Icons.Default.DriveFileMove, contentDescription = null, modifier = Modifier.size(16.dp))
                  Spacer(modifier = Modifier.width(6.dp))
                  Text(stringResource(R.string.tool13_action_folder))
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                  OutlinedButton(
                    onClick = {
                      coroutineScope.launch {
                        val mime = if (outputFormat == "JPEG") "image/jpeg" else "image/png"
                        val res = withContext(Dispatchers.IO) {
                          MultiOutputSaver.saveImagesToGallery(context, localFiles, mime)
                        }
                        Toast.makeText(context, context.getString(R.string.tool13_saved_to_gallery, res.successCount), Toast.LENGTH_SHORT).show()
                      }
                    },
                    shape = RoundedCornerShape(8.dp)
                  ) {
                    Icon(Icons.Default.Collections, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.tool13_action_gallery))
                  }
                }

                OutlinedButton(
                  onClick = {
                    val mime = if (outputFormat == "JPEG") "image/jpeg" else "image/png"
                    MultiOutputSaver.shareAll(context, localFiles, mime)
                  },
                  shape = RoundedCornerShape(8.dp)
                ) {
                  Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                  Spacer(modifier = Modifier.width(6.dp))
                  Text(stringResource(R.string.tool13_action_share))
                }
              }

              OutlinedButton(
                onClick = {
                  currentJobId = null
                  selectedPdfUri = null
                  cachedInputFile = null
                  totalPages = 0
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp)
              ) {
                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(stringResource(R.string.common_process_again))
              }
            }
          }
        }

        // Preview Grid of Rendered Results
        item {
          Text(
            text = "Rendered Images (${resultFiles.size})",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
          )
        }

        items(resultFiles.chunked(2)) { pair ->
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
          ) {
            for (fileRes in pair) {
              Box(modifier = Modifier.weight(1f)) {
                RenderedResultCard(fileRes)
              }
            }
            if (pair.size == 1) {
              Spacer(modifier = Modifier.weight(1f))
            }
          }
        }
      }

      // 3. Failed State
      if (jobUi != null && jobUi!!.state == JobState.FAILED) {
        item {
          ErrorCard(
            message = jobUi!!.errorMessage ?: stringResource(R.string.error_generic),
            onRetry = { executeConversion() }
          )
        }
      }

      // 4. Input View: Empty state
      if (selectedPdfUri == null && jobUi?.state != JobState.RUNNING && jobUi?.state != JobState.SUCCESS) {
        item {
          FilePickerCard(
            onFilesSelected = { uris ->
              uris.firstOrNull()?.let { loadPdfMetadata(it) }
            },
            title = stringResource(R.string.tool13_picker_title),
            supportedFormatsText = stringResource(R.string.tool13_picker_desc),
            pickerMode = PickerMode.SAF_DOCUMENTS,
            mimeTypeFilter = arrayOf("application/pdf"),
            maxFiles = 1,
            modifier = Modifier.testTag("pdf_to_images_picker")
          )
        }
      }

      // Loading indicator while copying / analyzing PDF
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
              Text("Analyzing PDF document…", style = MaterialTheme.typography.bodyMedium)
            }
          }
        }
      }

      // 5. Input View: PDF loaded
      if (selectedPdfUri != null && !isLoadingPdf && totalPages > 0 && jobUi?.state != JobState.RUNNING && jobUi?.state != JobState.SUCCESS) {
        // Document Summary Card
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
                  text = "$totalPages pages • ${FileFormatters.formatBytes(info.sizeBytes)}",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant
                )
              }
              OutlinedButton(
                onClick = {
                  selectedPdfUri = null
                  cachedInputFile = null
                  totalPages = 0
                },
                shape = RoundedCornerShape(8.dp)
              ) {
                Text("Change")
              }
            }
          }
        }

        // Options Card
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
              // Page Range Selection
              Text(stringResource(R.string.tool13_page_range), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
              Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                  selected = isAllPages,
                  onClick = { isAllPages = true },
                  label = { Text(stringResource(R.string.tool13_range_all, totalPages)) }
                )
                FilterChip(
                  selected = !isAllPages,
                  onClick = { isAllPages = false },
                  label = { Text(stringResource(R.string.tool13_range_custom)) }
                )
              }

              if (!isAllPages) {
                OutlinedTextField(
                  value = rangeText,
                  onValueChange = { rangeText = it },
                  label = { Text(stringResource(R.string.tool13_range_custom)) },
                  placeholder = { Text(stringResource(R.string.tool13_range_placeholder)) },
                  isError = rangeError != null,
                  supportingText = {
                    if (rangeError != null) {
                      Text(rangeError!!, color = MaterialTheme.colorScheme.error)
                    } else {
                      Text("${selectedPageIndices.size} pages will be rendered")
                    }
                  },
                  singleLine = true,
                  modifier = Modifier.fillMaxWidth(),
                  shape = RoundedCornerShape(10.dp)
                )
              }

              // Image Format (PNG vs JPEG)
              Text(stringResource(R.string.tool13_format), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
              Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                  selected = outputFormat == "PNG",
                  onClick = { outputFormat = "PNG" },
                  label = { Text(stringResource(R.string.tool13_format_png)) }
                )
                FilterChip(
                  selected = outputFormat == "JPEG",
                  onClick = { outputFormat = "JPEG" },
                  label = { Text(stringResource(R.string.tool13_format_jpeg)) }
                )
              }

              // Quality Slider for JPEG
              if (outputFormat == "JPEG") {
                Text(
                  text = stringResource(R.string.tool13_quality, jpegQuality.toInt()),
                  style = MaterialTheme.typography.labelLarge,
                  fontWeight = FontWeight.Bold
                )
                Slider(
                  value = jpegQuality,
                  onValueChange = { jpegQuality = it },
                  valueRange = 20f..100f,
                  steps = 15,
                  modifier = Modifier.fillMaxWidth()
                )
              }

              // Resolution DPI Presets
              Text(stringResource(R.string.tool13_resolution), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
              FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (dpi in PdfToImagesMath.DPI_PRESETS) {
                  FilterChip(
                    selected = selectedDpi == dpi,
                    onClick = { selectedDpi = dpi },
                    label = { Text("$dpi DPI") }
                  )
                }
              }

              // 16 Megapixel Limit Warning
              if (mpExceededPage != null) {
                val (wPt, hPt) = pageDimensionsMap[mpExceededPage] ?: (595 to 842)
                val (wPx, hPx) = PdfToImagesMath.calculatePixelDimensions(wPt, hPt, selectedDpi)
                Surface(
                  color = MaterialTheme.colorScheme.errorContainer,
                  shape = RoundedCornerShape(8.dp),
                  modifier = Modifier.fillMaxWidth()
                ) {
                  Row(
                    modifier = Modifier.padding(10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                  ) {
                    Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                    Text(
                      text = stringResource(R.string.tool13_error_exceeds_16mp, mpExceededPage + 1, selectedDpi, wPx, hPx),
                      style = MaterialTheme.typography.bodySmall,
                      color = MaterialTheme.colorScheme.onErrorContainer
                    )
                  }
                }
              }
            }
          }
        }

        // Start Conversion Button
        item {
          val canStart = selectedPageIndices.isNotEmpty() &&
            mpExceededPage == null &&
            (isAllPages || rangeError == null) &&
            !ToolJobRunner.isToolBusy(13)

          Button(
            onClick = { executeConversion() },
            enabled = canStart,
            modifier = Modifier
              .fillMaxWidth()
              .height(52.dp)
              .testTag("start_pdf_to_images_button")
          ) {
            Icon(Icons.Default.Image, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.tool13_btn_convert))
          }
        }
      }
    }
  }
}

@Composable
private fun RenderedResultCard(fileRes: FileResult) {
  val thumbnailBitmap by produceState<Bitmap?>(initialValue = null, key1 = fileRes.outputUri) {
    value = withContext(Dispatchers.IO) {
      val path = fileRes.tempFilePath ?: return@withContext null
      try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        var sample = 1
        val maxEdge = maxOf(bounds.outWidth, bounds.outHeight)
        while (maxEdge / (sample * 2) >= 200) sample *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        BitmapFactory.decodeFile(path, options)
      } catch (_: Exception) {
        null
      }
    }
  }

  var showZoomDialog by remember { mutableStateOf(false) }
  val targetFile = remember(fileRes.tempFilePath) { fileRes.tempFilePath?.let { File(it) } }

  Card(
    shape = RoundedCornerShape(10.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
    modifier = Modifier.fillMaxWidth()
  ) {
    Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .height(110.dp)
          .clip(RoundedCornerShape(6.dp))
          .background(MaterialTheme.colorScheme.surface)
          .clickable(enabled = targetFile != null && targetFile.exists()) {
            showZoomDialog = true
          },
        contentAlignment = Alignment.Center
      ) {
        if (thumbnailBitmap != null) {
          Image(
            bitmap = thumbnailBitmap!!.asImageBitmap(),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize()
          )
        } else {
          Icon(Icons.Default.Image, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
      }

      if (showZoomDialog && targetFile != null && targetFile.exists()) {
        com.example.tools.common.ZoomableImageDialog(
          file = targetFile,
          title = fileRes.outputName ?: fileRes.inputName,
          onDismiss = { showZoomDialog = false }
        )
      }

      Text(
        text = fileRes.outputName ?: fileRes.inputName,
        style = MaterialTheme.typography.bodySmall,
        fontWeight = FontWeight.Bold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
      )

      val dims = if (fileRes.width != null && fileRes.height != null) "${fileRes.width} × ${fileRes.height} px • " else ""
      Text(
        text = "$dims${FileFormatters.formatBytes(fileRes.outputSizeBytes)}",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )

      if (fileRes.userMessage != null) {
        Text(
          text = fileRes.userMessage,
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.tertiary,
          fontWeight = FontWeight.Medium
        )
      }
    }
  }
}
