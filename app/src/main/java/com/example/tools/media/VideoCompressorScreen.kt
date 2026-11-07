package com.example.tools.media

import android.net.Uri
import android.widget.MediaController
import android.widget.VideoView
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.FilledIconButton
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.viewinterop.AndroidView
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Transform
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
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
import com.example.ToolsNestApp
import com.example.file.FileFormatters
import com.example.file.FileLayer
import com.example.media.MediaProbe
import com.example.media.MediaProbeResult
import com.example.media.VideoAudioMode
import com.example.tools.common.ErrorCard
import com.example.tools.common.FilePickerCard
import com.example.tools.common.JobState
import com.example.tools.common.PickerMode
import com.example.tools.common.ProgressCard
import com.example.tools.common.ToolJobRunner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.roundToInt

private const val TOOL_ID = 18

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoCompressorScreen(
  onBack: () -> Unit,
  modifier: Modifier = Modifier
) {
  BackHandler { onBack() }

  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val app = runCatching { ToolsNestApp.instance }.getOrNull()
  val defaultQuality by (app?.videoSettingsDataStore?.defaultVideoQualityFlow ?: flowOf(VideoQualityPreset.BALANCED))
    .collectAsState(initial = VideoQualityPreset.BALANCED)

  var selectedUriString by rememberSaveable { mutableStateOf<String?>(null) }
  val selectedUri = selectedUriString?.let { Uri.parse(it) }
  var inputFileName by remember { mutableStateOf("") }
  var inputFileSizeBytes by remember { mutableLongStateOf(0L) }
  var probeResult by remember { mutableStateOf<MediaProbeResult?>(null) }
  var isProbing by remember { mutableStateOf(false) }

  var resolutionPreset by remember { mutableStateOf(VideoResolutionPreset.ORIGINAL) }
  var qualityPreset by remember(defaultQuality) { mutableStateOf(defaultQuality) }
  var audioMode by remember { mutableStateOf(VideoAudioMode.KEEP_ORIGINAL) }

  var currentJobId by rememberSaveable { mutableStateOf<String?>(null) }
  val jobUi by ToolJobRunner.observe(currentJobId ?: "").collectAsState(initial = null)

  LaunchedEffect(currentJobId) {
    val id = currentJobId
    if (id != null && !ToolJobRunner.hasJob(id)) {
      currentJobId = null
    }
  }

  fun probeVideo(uri: Uri) {
    scope.launch {
      isProbing = true
      val info = withContext(Dispatchers.IO) {
        FileLayer.getUriFileInfo(context, uri)
      }
      inputFileName = info.displayName
      inputFileSizeBytes = info.sizeBytes
      selectedUriString = uri.toString()

      val probe = withContext(Dispatchers.IO) {
        MediaProbe.probeMedia(context, uri)
      }
      probeResult = probe
      isProbing = false

      if (!probe.hasVideo) {
        Toast.makeText(context, context.getString(R.string.tool18_no_video), Toast.LENGTH_LONG).show()
        selectedUriString = null
      }
    }
  }

  Scaffold(
    topBar = {
      TopAppBar(
        title = {
          Text(
            text = stringResource(R.string.tool18_name),
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
          )
        },
        navigationIcon = {
          IconButton(onClick = onBack, modifier = Modifier.testTag("tool18_back_button")) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
          }
        }
      )
    },
    modifier = modifier
  ) { paddingValues ->
    LazyColumn(
      modifier = Modifier
        .fillMaxSize()
        .padding(paddingValues)
        .padding(horizontal = 16.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
      item { Spacer(Modifier.height(4.dp)) }

      // 1. File Picker
      if (selectedUri == null && jobUi?.state != JobState.RUNNING && jobUi?.state != JobState.SUCCESS) {
        item {
          FilePickerCard(
            onFilesSelected = { uris ->
              uris.firstOrNull()?.let { probeVideo(it) }
            },
            title = stringResource(R.string.tool18_picker_title),
            supportedFormatsText = stringResource(R.string.tool18_picker_desc),
            pickerMode = PickerMode.SAF_DOCUMENTS,
            maxFiles = 1,
            mimeTypeFilter = arrayOf("video/*"),
            modifier = Modifier.testTag("tool18_picker_card")
          )
        }
      }

      // Probing state
      if (isProbing) {
        item {
          Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
          }
        }
      }

      // 2. Video Info Card
      if (selectedUri != null && !isProbing && jobUi?.state != JobState.RUNNING && jobUi?.state != JobState.SUCCESS) {
        val probe = probeResult
        item {
          Card(
            modifier = Modifier.fillMaxWidth().testTag("tool18_info_card"),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
          ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
              ) {
                Row(
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(12.dp),
                  modifier = Modifier.weight(1f)
                ) {
                  Icon(Icons.Default.VideoFile, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                  Column {
                    Text(
                      text = inputFileName,
                      style = MaterialTheme.typography.titleSmall,
                      fontWeight = FontWeight.Bold,
                      maxLines = 1,
                      overflow = TextOverflow.Ellipsis
                    )
                    Text(
                      text = "${FileFormatters.formatBytes(inputFileSizeBytes)} • ${AudioTrimmerValidation.formatMsToTime(probe?.durationMs ?: 0L)}",
                      style = MaterialTheme.typography.bodySmall,
                      color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                  }
                }

                OutlinedButton(
                  onClick = { selectedUriString = null },
                  modifier = Modifier.testTag("tool18_change_file_button")
                ) {
                  Text(stringResource(R.string.common_change))
                }
              }

              if (probe != null) {
                val rot = probe.rotationDegrees
                val rawW = if (rot == 90 || rot == 270) probe.height else probe.width
                val rawH = if (rot == 90 || rot == 270) probe.width else probe.height
                val (dispW, dispH) = VideoCompressorMath.getDisplayedDimensions(
                  rawW,
                  rawH,
                  rot
                )
                val fps = probe.frameRate?.toInt() ?: VideoCompressorMath.DEFAULT_FRAME_RATE
                val videoTrack = probe.tracks.firstOrNull { it.isVideo }
                val vBitrate = (videoTrack?.bitrate ?: (if (probe.bitrate > 0) probe.bitrate.toInt() else null))?.let { "${it / 1000} kbps" } ?: "Unknown"

                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.SpaceBetween
                ) {
                  Text(
                    text = "${dispW}x${dispH} • ${fps}fps",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                  )
                  Text(
                    text = "Bitrate: $vBitrate",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                  )
                }
              }
            }
          }
        }

        // 3. Resolution Presets
        val rot = probe?.rotationDegrees ?: 0
        val rawW = if (rot == 90 || rot == 270) (probe?.height ?: 0) else (probe?.width ?: 0)
        val rawH = if (rot == 90 || rot == 270) (probe?.width ?: 0) else (probe?.height ?: 0)

        item {
          Card(
            modifier = Modifier.fillMaxWidth().testTag("tool18_resolution_card"),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
          ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
              Text(
                text = stringResource(R.string.tool18_resolution_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
              )

              Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                VideoResolutionPreset.values().forEach { preset ->
                  val allowed = VideoCompressorMath.isPresetAllowed(rawW, rawH, rot, preset.shortEdge)
                  FilterChip(
                    selected = resolutionPreset == preset,
                    onClick = { if (allowed) resolutionPreset = preset },
                    enabled = allowed,
                    label = { Text(preset.localizedLabel()) },
                    modifier = Modifier.testTag("tool18_res_chip_${preset.name}")
                  )
                }
              }

              val (targetW, targetH) = VideoCompressorMath.calculateTargetDimensions(
                rawW, rawH, rot, resolutionPreset.shortEdge
              )
              Text(
                text = stringResource(R.string.tool18_target_resolution, targetW, targetH),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Medium
              )
            }
          }
        }

        // 4. Quality Presets & Audio Mode
        item {
          Card(
            modifier = Modifier.fillMaxWidth().testTag("tool18_quality_card"),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
          ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
              // Quality
              Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                  text = stringResource(R.string.tool18_quality_title),
                  style = MaterialTheme.typography.titleSmall,
                  fontWeight = FontWeight.Bold
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                  VideoQualityPreset.values().forEach { preset ->
                    FilterChip(
                      selected = qualityPreset == preset,
                      onClick = { qualityPreset = preset },
                      label = { Text(preset.localizedLabel()) },
                      modifier = Modifier.testTag("tool18_quality_chip_${preset.name}")
                    )
                  }
                }
              }

              // Audio Mode
              Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                  text = stringResource(R.string.tool18_audio_title),
                  style = MaterialTheme.typography.titleSmall,
                  fontWeight = FontWeight.Bold
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                  FilterChip(
                    selected = audioMode == VideoAudioMode.KEEP_ORIGINAL,
                    onClick = { audioMode = VideoAudioMode.KEEP_ORIGINAL },
                    label = { Text(stringResource(R.string.tool18_audio_keep)) },
                    modifier = Modifier.testTag("tool18_audio_keep")
                  )
                  FilterChip(
                    selected = audioMode == VideoAudioMode.COMPRESS_AAC_96KBPS,
                    onClick = { audioMode = VideoAudioMode.COMPRESS_AAC_96KBPS },
                    label = { Text("AAC 96k") },
                    modifier = Modifier.testTag("tool18_audio_aac")
                  )
                  FilterChip(
                    selected = audioMode == VideoAudioMode.REMOVE_AUDIO,
                    onClick = { audioMode = VideoAudioMode.REMOVE_AUDIO },
                    label = { Text(stringResource(R.string.tool18_audio_remove)) },
                    modifier = Modifier.testTag("tool18_audio_remove")
                  )
                }
              }
            }
          }
        }

        // 5. Size Estimation & Honest Notes
        val (targetW, targetH) = VideoCompressorMath.calculateTargetDimensions(
          rawW, rawH, rot, resolutionPreset.shortEdge
        )
        val videoTrack = probe?.tracks?.firstOrNull { it.isVideo }
        val sourceVideoBitrate = videoTrack?.bitrate ?: (if ((probe?.bitrate ?: 0L) > 0) probe?.bitrate?.toInt() else null)
        val audioTrack = probe?.tracks?.firstOrNull { it.isAudio }
        val calculatedVideoBitrate = VideoCompressorMath.calculateVideoBitrate(
          preset = qualityPreset,
          outputWidth = targetW,
          outputHeight = targetH,
          sourceFrameRate = probe?.frameRate?.toInt(),
          sourceVideoBitrate = sourceVideoBitrate
        )
        val audioBitrate = when (audioMode) {
          VideoAudioMode.KEEP_ORIGINAL -> audioTrack?.bitrate ?: 128_000
          VideoAudioMode.COMPRESS_AAC_96KBPS -> 96_000
          VideoAudioMode.REMOVE_AUDIO -> 0
        }
        val estimatedSize = VideoCompressorMath.calculateEstimatedOutputSize(
          videoBitrateBps = calculatedVideoBitrate,
          audioBitrateBps = audioBitrate,
          durationSec = (probe?.durationMs ?: 0L).toDouble() / 1000.0
        )
        val isLittleOrNoReduction = VideoCompressorMath.isLittleOrNoReduction(
          estimatedSizeBytes = estimatedSize,
          sourceFileSizeBytes = inputFileSizeBytes
        )

        item {
          Card(
            modifier = Modifier.fillMaxWidth().testTag("tool18_estimate_card"),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
          ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
              ) {
                Text(
                  text = stringResource(R.string.tool18_estimate_label),
                  style = MaterialTheme.typography.bodyMedium,
                  fontWeight = FontWeight.SemiBold
                )
                Text(
                  text = "~${FileFormatters.formatBytes(estimatedSize)}",
                  style = MaterialTheme.typography.titleMedium,
                  fontWeight = FontWeight.Bold,
                  color = MaterialTheme.colorScheme.primary
                )
              }

              if (isLittleOrNoReduction) {
                Row(
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                  Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                  Text(
                    text = stringResource(R.string.tool18_little_reduction_warning),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                  )
                }
              }

              Text(
                text = stringResource(R.string.tool18_codec_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
              Text(
                text = stringResource(R.string.tool18_ram_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
              Text(
                text = stringResource(R.string.tool18_hdr_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary
              )
            }
          }
        }

        // 6. Start Button
        item {
          Button(
            onClick = {
              val uri = selectedUri ?: return@Button
              currentJobId = ToolJobRunner.start(
                toolId = TOOL_ID,
                inputName = inputFileName,
                optionsJson = "{res:\"${resolutionPreset.name}\",quality:\"${qualityPreset.name}\",audio:\"${audioMode.name}\"}",
                inputUri = uri.toString(),
                inputSize = inputFileSizeBytes
              ) {
                VideoCompressorHelper.compressVideo(
                  context = context,
                  inputUri = uri,
                  inputFileName = inputFileName,
                  resolutionPreset = resolutionPreset,
                  qualityPreset = qualityPreset,
                  audioMode = audioMode,
                  jobContext = this,
                  onProgress = { pct, stage ->
                    reportProgress(pct, stage, 1, 1, inputFileName)
                  }
                )
              }
            },
            modifier = Modifier.fillMaxWidth().height(52.dp).testTag("tool18_start_button")
          ) {
            Icon(Icons.Default.Transform, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.tool18_action_compress))
          }
        }
      }

      // 7. Running State
      if (jobUi != null && jobUi!!.state == JobState.RUNNING) {
        item {
          ProgressCard(
            stage = jobUi!!.stage,
            progressPercent = jobUi!!.progressPercent,
            currentIndex = 1,
            totalFiles = 1,
            currentFileName = inputFileName,
            onCancel = { currentJobId?.let { ToolJobRunner.cancel(it) } },
            modifier = Modifier.testTag("tool18_progress_card")
          )
        }
      }

      // 8. Error State
      if (jobUi != null && jobUi!!.state == JobState.FAILED) {
        item {
          ErrorCard(
            message = jobUi!!.errorMessage ?: stringResource(R.string.error_generic),
            onRetry = { currentJobId?.let { ToolJobRunner.clearJob(it) }; currentJobId = null },
            modifier = Modifier.testTag("tool18_error_card")
          )
        }
      }

      // 9. Result Card
      if (jobUi != null && jobUi!!.state == JobState.SUCCESS) {
        val fileResult = jobUi!!.result?.files?.firstOrNull()
        val localFile = fileResult?.tempFilePath?.let { File(it) }

        item {
          Card(
            modifier = Modifier.fillMaxWidth().testTag("tool18_result_card"),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
          ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
              Text(
                text = stringResource(R.string.tool18_success_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
              )

              if (localFile != null && localFile.exists()) {
                // Inline preview: the user can play the compressed video right here before saving it.
                InlineVideoPreview(file = localFile)

                val origBytes = fileResult.originalSizeBytes.takeIf { it > 0 } ?: inputFileSizeBytes
                val outBytes = fileResult.outputSizeBytes.takeIf { it > 0 } ?: localFile.length()
                val reduction = if (origBytes > 0) (((origBytes - outBytes).toDouble() / origBytes) * 100).roundToInt() else 0
                val res = if (fileResult.width != null && fileResult.height != null) "${fileResult.width}x${fileResult.height}" else ""

                Row(
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                  Text(
                    text = FileFormatters.formatBytes(origBytes),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                  )
                  Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                  )
                  Text(
                    text = FileFormatters.formatBytes(outBytes),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                  )
                }

                if (reduction > 0) {
                  Text(
                    text = stringResource(R.string.tool18_size_reduced, reduction, res),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium
                  )
                } else {
                  Text(
                    text = stringResource(R.string.tool18_size_larger_warning),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                  )
                }

                // Action buttons: Save, Share, Open, Save to Gallery
                val saveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("video/mp4")) { destUri ->
                  if (destUri != null) {
                    val saved = FileLayer.saveFileToUri(context, localFile, destUri)
                    if (saved) {
                      Toast.makeText(context, context.getString(R.string.file_saved_success), Toast.LENGTH_SHORT).show()
                    }
                  }
                }

                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                  Button(
                    onClick = { saveLauncher.launch(localFile.name) },
                    modifier = Modifier.weight(1f).testTag("tool18_save_button")
                  ) {
                    Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.common_save))
                  }

                  OutlinedButton(
                    onClick = { FileLayer.shareFileAndNotify(context, localFile) },
                    modifier = Modifier.weight(1f).testTag("tool18_share_button")
                  ) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.common_share))
                  }
                }

                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                  OutlinedButton(
                    onClick = { FileLayer.openFileAndNotify(context, localFile) },
                    modifier = Modifier.weight(1f).testTag("tool18_open_button")
                  ) {
                    Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.common_open))
                  }

                  // Save to Gallery
                  OutlinedButton(
                    onClick = {
                      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        val uri = FileLayer.saveVideoToMediaStore(context, localFile, localFile.name)
                        if (uri != null) {
                          Toast.makeText(context, context.getString(R.string.tool18_saved_to_gallery), Toast.LENGTH_SHORT).show()
                        } else {
                          Toast.makeText(context, context.getString(R.string.error_generic), Toast.LENGTH_SHORT).show()
                        }
                      } else {
                        Toast.makeText(context, context.getString(R.string.common_gallery_api_notice), Toast.LENGTH_LONG).show()
                      }
                    },
                    modifier = Modifier.weight(1f).testTag("tool18_gallery_button")
                  ) {
                    Icon(Icons.Default.VideoLibrary, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.tool18_save_gallery))
                  }
                }
              }

              Button(
                onClick = {
                  currentJobId?.let { ToolJobRunner.clearJob(it) }
                  currentJobId = null
                  selectedUriString = null
                },
                modifier = Modifier.fillMaxWidth().testTag("tool18_process_another_button")
              ) {
                Text(stringResource(R.string.common_process_again))
              }
            }
          }
        }
      }

      item { Spacer(Modifier.height(24.dp)) }
    }
  }
}

/**
 * Small in-app player for the compressed result. Tap the play button to start; the standard
 * media controller (play/pause, seek bar) appears on touch. Uses the platform VideoView, so no
 * extra dependency is needed.
 */
@Composable
private fun InlineVideoPreview(file: File, modifier: Modifier = Modifier) {
  var videoView by remember(file.absolutePath) { mutableStateOf<VideoView?>(null) }
  var started by remember(file.absolutePath) { mutableStateOf(false) }

  DisposableEffect(file.absolutePath) {
    onDispose {
      try {
        videoView?.stopPlayback()
      } catch (_: Exception) {
      }
    }
  }

  Box(
    modifier = modifier
      .fillMaxWidth()
      .height(220.dp)
      .clip(RoundedCornerShape(12.dp))
      .background(Color.Black)
      .testTag("tool18_video_preview"),
    contentAlignment = Alignment.Center
  ) {
    AndroidView(
      modifier = Modifier.fillMaxSize(),
      factory = { ctx ->
        VideoView(ctx).apply {
          val controller = MediaController(ctx)
          controller.setAnchorView(this)
          setMediaController(controller)
          setVideoURI(Uri.fromFile(file))
          setOnPreparedListener { mp ->
            mp.isLooping = false
            // Show the first frame as a poster until the user presses play.
            seekTo(100)
          }
          setOnCompletionListener { started = false }
          videoView = this
        }
      }
    )

    if (!started) {
      FilledIconButton(
        onClick = {
          videoView?.start()
          started = true
        },
        modifier = Modifier.size(56.dp).testTag("tool18_video_play")
      ) {
        Icon(
          imageVector = Icons.Default.PlayArrow,
          contentDescription = stringResource(R.string.tool18_play_preview),
          modifier = Modifier.size(32.dp)
        )
      }
    }
  }
}
