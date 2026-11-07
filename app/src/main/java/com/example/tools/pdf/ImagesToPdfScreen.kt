package com.example.tools.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
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
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
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
import com.example.ToolsNestApp
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
fun ImagesToPdfScreen(onBack: () -> Unit) {
  val context = LocalContext.current
  val coroutineScope = rememberCoroutineScope()
  val app = runCatching { ToolsNestApp.instance }.getOrNull()

  BackHandler { onBack() }

  val defaultPageSizeName by (app?.pdfSettingsDataStore?.defaultPageSizeFlow ?: flowOf("A4")).collectAsState(initial = "A4")
  val initialPageSize = remember(defaultPageSizeName) {
    PageSizeOption.fromSetting(defaultPageSizeName)
  }

  val selectedImages = remember { mutableStateListOf<ImagePdfInputItem>() }
  var pageSize by rememberSaveable(initialPageSize) { mutableStateOf(initialPageSize) }
  var orientation by rememberSaveable { mutableStateOf(OrientationOption.AUTO) }
  var margin by rememberSaveable { mutableStateOf(MarginOption.NONE) }
  var quality by rememberSaveable { mutableStateOf(QualityOption.HIGH) }
  var customNameInput by rememberSaveable { mutableStateOf("") }

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

  // Multi-image picker launcher
  val addMoreLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.PickMultipleVisualMedia(ImagesToPdfHelper.MAX_IMAGE_COUNT)
  ) { uris: List<Uri> ->
    if (uris.isNotEmpty()) {
      coroutineScope.launch {
        localErrorMessage = null
        currentJobId = null
        val itemsToAdd = mutableListOf<ImagePdfInputItem>()
        withContext(Dispatchers.IO) {
          for (uri in uris) {
            if (selectedImages.size + itemsToAdd.size >= ImagesToPdfHelper.MAX_IMAGE_COUNT) {
              break
            }
            val info = FileLayer.getUriFileInfo(context, uri)
            itemsToAdd.add(
              ImagePdfInputItem(
                uri = uri,
                name = info.displayName,
                sizeBytes = info.sizeBytes,
                rotationDegrees = 0
              )
            )
          }
        }
        if (selectedImages.size + itemsToAdd.size >= ImagesToPdfHelper.MAX_IMAGE_COUNT && uris.size > itemsToAdd.size) {
          localErrorMessage = context.getString(R.string.tool14_error_max_images)
        }
        selectedImages.addAll(itemsToAdd)
      }
    }
  }

  // SAF Save PDF launcher
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

  fun executeConversion() {
    if (selectedImages.isEmpty() || ToolJobRunner.isToolBusy(14)) return
    localErrorMessage = null
    val snapshotImages = selectedImages.toList()
    val options = ImagesToPdfOptions(
      pageSize = pageSize,
      orientation = orientation,
      margin = margin,
      quality = quality,
      customFileName = customNameInput.trim().ifEmpty { null }
    )

    val jobId = ToolJobRunner.start(
      toolId = 14,
      inputName = "${snapshotImages.size} Images",
      optionsJson = "size=$pageSize,orient=$orientation,quality=$quality"
    ) {
      ImagesToPdfHelper.convertImagesToPdf(
        context = context,
        images = snapshotImages,
        options = options,
        jobContext = this
      )
    }

    currentJobId = jobId
  }

  ToolScaffold(
    title = stringResource(R.string.tool14_name),
    onBack = onBack
  ) { padding ->
    LazyColumn(
      modifier = Modifier
        .fillMaxSize()
        .padding(padding)
        .padding(horizontal = 16.dp, vertical = 8.dp)
        .testTag("tool_screen_14"),
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
                Row(
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                  Icon(
                    imageVector = Icons.Default.PictureAsPdf,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(36.dp)
                  )
                  Column(modifier = Modifier.weight(1f)) {
                    Text(
                      text = stringResource(R.string.tool14_success_title),
                      style = MaterialTheme.typography.titleMedium,
                      fontWeight = FontWeight.Bold
                    )
                    Text(
                      text = "${fileRes.outputName} • ${FileFormatters.formatBytes(fileRes.outputSizeBytes)}",
                      style = MaterialTheme.typography.bodySmall,
                      color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                      text = if (selectedImages.size == 1) {
                        stringResource(R.string.tool14_page_count_one)
                      } else {
                        stringResource(R.string.tool14_page_count, selectedImages.size)
                      },
                      style = MaterialTheme.typography.bodySmall,
                      color = MaterialTheme.colorScheme.primary,
                      fontWeight = FontWeight.SemiBold
                    )
                  }
                }

                com.example.tools.common.PdfResultPreview(localFile)

                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                  Button(
                    onClick = { savePdfLauncher.launch(fileRes.outputName ?: "converted.pdf") },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp)
                  ) {
                    Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.common_save))
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
                    selectedImages.clear()
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
            onRetry = { executeConversion() }
          )
        }
      }

      // 4. Input View: Empty state
      if (selectedImages.isEmpty() && jobUi?.state != JobState.RUNNING && jobUi?.state != JobState.SUCCESS) {
        item {
          FilePickerCard(
            onFilesSelected = { uris ->
              coroutineScope.launch {
                localErrorMessage = null
                val newItems = mutableListOf<ImagePdfInputItem>()
                withContext(Dispatchers.IO) {
                  for (uri in uris) {
                    if (newItems.size >= ImagesToPdfHelper.MAX_IMAGE_COUNT) break
                    val info = FileLayer.getUriFileInfo(context, uri)
                    newItems.add(
                      ImagePdfInputItem(
                        uri = uri,
                        name = info.displayName,
                        sizeBytes = info.sizeBytes,
                        rotationDegrees = 0
                      )
                    )
                  }
                }
                selectedImages.addAll(newItems)
              }
            },
            title = stringResource(R.string.tool14_picker_title),
            supportedFormatsText = stringResource(R.string.tool14_picker_desc),
            pickerMode = PickerMode.PHOTO_PICKER,
            maxFiles = ImagesToPdfHelper.MAX_IMAGE_COUNT,
            modifier = Modifier.testTag("images_to_pdf_picker")
          )
        }
      }

      // 5. Input View: Items selected
      if (selectedImages.isNotEmpty() && jobUi?.state != JobState.RUNNING && jobUi?.state != JobState.SUCCESS) {
        item {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            val totalBytes = selectedImages.sumOf { it.sizeBytes }
            Text(
              text = "${selectedImages.size}/${ImagesToPdfHelper.MAX_IMAGE_COUNT} images • ${FileFormatters.formatBytes(totalBytes)}",
              style = MaterialTheme.typography.titleSmall,
              fontWeight = FontWeight.SemiBold
            )

            if (selectedImages.size < ImagesToPdfHelper.MAX_IMAGE_COUNT) {
              OutlinedButton(
                onClick = {
                  addMoreLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                  )
                },
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
              ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(stringResource(R.string.tool14_add_more), fontSize = 12.sp)
              }
            }
          }
        }

        // List of selected images
        itemsIndexed(selectedImages) { index, item ->
          ImageItemCard(
            item = item,
            index = index,
            totalCount = selectedImages.size,
            onMoveUp = {
              if (index > 0) {
                val temp = selectedImages.removeAt(index)
                selectedImages.add(index - 1, temp)
              }
            },
            onMoveDown = {
              if (index < selectedImages.size - 1) {
                val temp = selectedImages.removeAt(index)
                selectedImages.add(index + 1, temp)
              }
            },
            onRotate = {
              val newDegrees = (item.rotationDegrees + 90) % 360
              selectedImages[index] = item.copy(rotationDegrees = newDegrees)
            },
            onRemove = {
              selectedImages.removeAt(index)
            }
          )
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
              // Page Size
              Text(stringResource(R.string.tool14_page_size), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
              FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                  selected = pageSize == PageSizeOption.A4,
                  onClick = { pageSize = PageSizeOption.A4 },
                  label = { Text(stringResource(R.string.tool14_page_size_a4)) }
                )
                FilterChip(
                  selected = pageSize == PageSizeOption.LETTER,
                  onClick = { pageSize = PageSizeOption.LETTER },
                  label = { Text(stringResource(R.string.tool14_page_size_letter)) }
                )
                FilterChip(
                  selected = pageSize == PageSizeOption.FIT_TO_IMAGE,
                  onClick = { pageSize = PageSizeOption.FIT_TO_IMAGE },
                  label = { Text(stringResource(R.string.tool14_page_size_fit)) }
                )
              }

              // Orientation (only applicable if not fit to image)
              if (pageSize != PageSizeOption.FIT_TO_IMAGE) {
                Text(stringResource(R.string.tool14_orientation), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                  FilterChip(
                    selected = orientation == OrientationOption.AUTO,
                    onClick = { orientation = OrientationOption.AUTO },
                    label = { Text(stringResource(R.string.tool14_orientation_auto)) }
                  )
                  FilterChip(
                    selected = orientation == OrientationOption.PORTRAIT,
                    onClick = { orientation = OrientationOption.PORTRAIT },
                    label = { Text(stringResource(R.string.tool14_orientation_portrait)) }
                  )
                  FilterChip(
                    selected = orientation == OrientationOption.LANDSCAPE,
                    onClick = { orientation = OrientationOption.LANDSCAPE },
                    label = { Text(stringResource(R.string.tool14_orientation_landscape)) }
                  )
                }
              }

              // Margins
              Text(stringResource(R.string.tool14_margin), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
              FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                  selected = margin == MarginOption.NONE,
                  onClick = { margin = MarginOption.NONE },
                  label = { Text(stringResource(R.string.tool14_margin_none)) }
                )
                FilterChip(
                  selected = margin == MarginOption.MARGIN_10MM,
                  onClick = { margin = MarginOption.MARGIN_10MM },
                  label = { Text(stringResource(R.string.tool14_margin_10mm)) }
                )
                FilterChip(
                  selected = margin == MarginOption.MARGIN_20MM,
                  onClick = { margin = MarginOption.MARGIN_20MM },
                  label = { Text(stringResource(R.string.tool14_margin_20mm)) }
                )
              }

              // Quality Options
              Text(stringResource(R.string.tool14_quality), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
              FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                  selected = quality == QualityOption.HIGH,
                  onClick = { quality = QualityOption.HIGH },
                  label = { Text(stringResource(R.string.tool14_quality_high)) }
                )
                FilterChip(
                  selected = quality == QualityOption.MEDIUM,
                  onClick = { quality = QualityOption.MEDIUM },
                  label = { Text(stringResource(R.string.tool14_quality_medium)) }
                )
              }
              Text(
                text = stringResource(R.string.tool14_quality_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )

              // Transparency notice
              Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
              ) {
                Text(
                  text = stringResource(R.string.tool14_png_notice),
                  style = MaterialTheme.typography.bodySmall,
                  modifier = Modifier.padding(10.dp),
                  color = MaterialTheme.colorScheme.onSurfaceVariant
                )
              }

              // Output file name
              OutlinedTextField(
                value = customNameInput,
                onValueChange = { customNameInput = it },
                label = { Text("Output PDF Name (Optional)") },
                placeholder = { Text("e.g. MyDocument") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp)
              )
            }
          }
        }

        // Start Button
        item {
          Button(
            onClick = { executeConversion() },
            modifier = Modifier
              .fillMaxWidth()
              .height(52.dp)
              .testTag("start_images_to_pdf_button"),
            enabled = selectedImages.isNotEmpty() && !ToolJobRunner.isToolBusy(14)
          ) {
            Icon(Icons.Default.PictureAsPdf, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.tool14_btn_convert))
          }
        }
      }
    }
  }
}

@Composable
private fun ImageItemCard(
  item: ImagePdfInputItem,
  index: Int,
  totalCount: Int,
  onMoveUp: () -> Unit,
  onMoveDown: () -> Unit,
  onRotate: () -> Unit,
  onRemove: () -> Unit
) {
  val context = LocalContext.current
  val thumbnailBitmap by produceState<Bitmap?>(initialValue = null, key1 = item.uri) {
    value = withContext(Dispatchers.IO) {
      try {
        val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(item.uri)?.use { stream ->
          BitmapFactory.decodeStream(stream, null, boundsOptions)
        }
        var sample = 1
        val maxEdge = maxOf(boundsOptions.outWidth, boundsOptions.outHeight)
        while (maxEdge / (sample * 2) >= 120) sample *= 2
        val decodeOptions = BitmapFactory.Options().apply { inSampleSize = sample }
        context.contentResolver.openInputStream(item.uri)?.use { stream ->
          BitmapFactory.decodeStream(stream, null, decodeOptions)
        }
      } catch (_: Exception) {
        null
      }
    }
  }

  Card(
    modifier = Modifier.fillMaxWidth(),
    shape = RoundedCornerShape(12.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(10.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
      // Thumbnail with rotation applied
      Box(
        modifier = Modifier
          .size(56.dp)
          .clip(RoundedCornerShape(8.dp))
          .background(MaterialTheme.colorScheme.surface),
        contentAlignment = Alignment.Center
      ) {
        if (thumbnailBitmap != null) {
          Image(
            bitmap = thumbnailBitmap!!.asImageBitmap(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
              .fillMaxSize()
              .rotate(item.rotationDegrees.toFloat())
          )
        } else {
          Icon(
            imageVector = Icons.Default.Image,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.size(24.dp)
          )
        }
      }

      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = item.name,
          style = MaterialTheme.typography.bodyMedium,
          fontWeight = FontWeight.Medium,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis
        )
        Text(
          text = "${FileFormatters.formatBytes(item.sizeBytes)}${if (item.rotationDegrees != 0) " • ${item.rotationDegrees}°" else ""}",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }

      // Action buttons: Reorder, Rotate, Remove
      Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(
          onClick = onMoveUp,
          enabled = index > 0,
          modifier = Modifier.size(32.dp)
        ) {
          Icon(
            imageVector = Icons.Default.ArrowUpward,
            contentDescription = stringResource(R.string.tool14_move_up),
            modifier = Modifier.size(16.dp)
          )
        }

        IconButton(
          onClick = onMoveDown,
          enabled = index < totalCount - 1,
          modifier = Modifier.size(32.dp)
        ) {
          Icon(
            imageVector = Icons.Default.ArrowDownward,
            contentDescription = stringResource(R.string.tool14_move_down),
            modifier = Modifier.size(16.dp)
          )
        }

        IconButton(
          onClick = onRotate,
          modifier = Modifier.size(32.dp)
        ) {
          Icon(
            imageVector = Icons.Default.RotateRight,
            contentDescription = stringResource(R.string.tool14_rotate),
            modifier = Modifier.size(18.dp)
          )
        }

        IconButton(
          onClick = onRemove,
          modifier = Modifier.size(32.dp)
        ) {
          Icon(
            imageVector = Icons.Default.Close,
            contentDescription = stringResource(R.string.tool14_remove),
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(18.dp)
          )
        }
      }
    }
  }
}
