package com.example.tools.media

import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Transform
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.R
import com.example.ToolsNestApp
import com.example.file.FileFormatters
import com.example.file.FileLayer
import com.example.media.MediaErrorMapper
import com.example.media.MediaProbe
import com.example.media.MediaProbeResult
import com.example.tools.common.ErrorCard
import com.example.tools.common.FilePickerCard
import com.example.tools.common.JobState
import com.example.tools.common.PickerMode
import com.example.tools.common.ProgressCard
import com.example.tools.common.ToolJobRunner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.roundToInt

private const val TOOL_ID = 19

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioTrimmerScreen(
  onBack: () -> Unit,
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val app = runCatching { ToolsNestApp.instance }.getOrNull()

  val defaultFormat by (app?.mediaSettingsDataStore?.defaultAudioFormatFlow ?: flowOf("M4A")).collectAsState(initial = "M4A")
  val defaultBitrate by (app?.mediaSettingsDataStore?.defaultAacBitrateFlow ?: flowOf(128)).collectAsState(initial = 128)

  var selectedUriString by rememberSaveable { mutableStateOf<String?>(null) }
  val selectedUri = selectedUriString?.let { Uri.parse(it) }
  var inputFileName by remember { mutableStateOf("") }
  var inputFileSizeBytes by remember { mutableLongStateOf(0L) }
  var probeResult by remember { mutableStateOf<MediaProbeResult?>(null) }
  var isProbing by remember { mutableStateOf(false) }

  var durationMs by remember { mutableLongStateOf(0L) }
  var startMs by remember { mutableLongStateOf(0L) }
  var endMs by remember { mutableLongStateOf(0L) }
  var playheadMs by remember { mutableLongStateOf(0L) }

  var outputFormat by remember { mutableStateOf(defaultFormat) }
  var aacBitrate by remember { mutableIntStateOf(defaultBitrate) }

  var waveformBuckets by remember { mutableStateOf<FloatArray?>(null) }
  var isGeneratingWaveform by remember { mutableStateOf(false) }
  var waveformProgress by remember { mutableFloatStateOf(0f) }
  var waveformFallbackNotice by remember { mutableStateOf<String?>(null) }

  var isPlaying by remember { mutableStateOf(false) }
  var isPreviewingSelection by remember { mutableStateOf(false) }
  var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }

  var hasUnsavedEdits by remember { mutableStateOf(false) }
  var showConfirmBackDialog by remember { mutableStateOf(false) }

  var currentJobId by rememberSaveable { mutableStateOf<String?>(null) }
  val jobUi by ToolJobRunner.observe(currentJobId ?: "").collectAsState(initial = null)

  LaunchedEffect(currentJobId) {
    val id = currentJobId
    if (id != null && !ToolJobRunner.hasJob(id)) {
      currentJobId = null
    }
  }

  // Handle back press
  BackHandler {
    if (hasUnsavedEdits && jobUi?.state != JobState.RUNNING) {
      showConfirmBackDialog = true
    } else {
      onBack()
    }
  }

  // Lifecycle observer to pause player on background
  val lifecycleOwner = LocalLifecycleOwner.current
  DisposableEffect(lifecycleOwner) {
    val observer = LifecycleEventObserver { _, event ->
      if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP) {
        mediaPlayer?.let {
          if (it.isPlaying) {
            it.pause()
            isPlaying = false
            isPreviewingSelection = false
          }
        }
      }
    }
    lifecycleOwner.lifecycle.addObserver(observer)
    onDispose {
      lifecycleOwner.lifecycle.removeObserver(observer)
      try {
        mediaPlayer?.stop()
        mediaPlayer?.release()
      } catch (_: Exception) {}
      mediaPlayer = null
    }
  }

  // Periodic playhead updater coroutine
  LaunchedEffect(isPlaying) {
    while (isPlaying) {
      val mp = mediaPlayer
      if (mp != null && mp.isPlaying) {
        val current = mp.currentPosition.toLong()
        playheadMs = current

        if (current >= endMs) {
          mp.pause()
          mp.seekTo(startMs.toInt())
          playheadMs = startMs
          isPlaying = false
          isPreviewingSelection = false
        }
      } else {
        isPlaying = false
      }
      delay(50)
    }
  }

  // Cleanup helper
  fun resetPlayer() {
    try {
      mediaPlayer?.stop()
      mediaPlayer?.release()
    } catch (_: Exception) {}
    mediaPlayer = null
    isPlaying = false
    isPreviewingSelection = false
  }

  // Probe audio file
  fun probeAudio(uri: Uri) {
    scope.launch {
      isProbing = true
      resetPlayer()
      waveformBuckets = null
      waveformFallbackNotice = null

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

      if (!probe.hasAudio) {
        Toast.makeText(context, context.getString(R.string.tool19_no_audio), Toast.LENGTH_LONG).show()
        selectedUriString = null
        return@launch
      }

      val dur = probe.durationMs ?: 0L
      durationMs = dur
      startMs = 0L
      endMs = dur
      playheadMs = 0L
      hasUnsavedEdits = false

      // Setup MediaPlayer asynchronously off main thread
      try {
        val player = MediaPlayer().apply {
          setDataSource(context, uri)
          setOnPreparedListener { mp ->
            mediaPlayer = mp
          }
          setOnErrorListener { mp, _, _ ->
            mp.reset()
            mp.release()
            mediaPlayer = null
            true
          }
          prepareAsync()
        }
      } catch (e: Exception) {
        Toast.makeText(context, context.getString(R.string.tool19_player_error), Toast.LENGTH_SHORT).show()
      }

      // Generate waveform in background
      if (dur > WaveformHelper.MAX_WAVEFORM_DURATION_MS) {
        waveformFallbackNotice = context.getString(R.string.tool19_waveform_too_long)
      } else {
        isGeneratingWaveform = true
        waveformProgress = 0f
        val buckets = WaveformHelper.extractWaveformPeaks(
          context = context,
          uri = uri,
          durationMs = dur,
          targetBuckets = 600,
          onProgress = { p -> waveformProgress = p }
        )
        isGeneratingWaveform = false
        if (buckets != null) {
          waveformBuckets = buckets
        } else {
          waveformFallbackNotice = context.getString(R.string.tool19_waveform_failed)
        }
      }
    }
  }

  // Playback controls (strictly restricted to [startMs, endMs])
  fun togglePlay() {
    val mp = mediaPlayer ?: return
    if (mp.isPlaying) {
      mp.pause()
      isPlaying = false
      isPreviewingSelection = false
    } else {
      val pos = playheadMs.coerceIn(startMs, endMs)
      if (pos >= endMs) {
        mp.seekTo(startMs.toInt())
        playheadMs = startMs
      } else {
        mp.seekTo(pos.toInt())
        playheadMs = pos
      }
      mp.start()
      isPlaying = true
      isPreviewingSelection = true
    }
  }

  fun previewSelection() {
    val mp = mediaPlayer ?: return
    mp.seekTo(startMs.toInt())
    playheadMs = startMs
    mp.start()
    isPlaying = true
    isPreviewingSelection = true
  }

  fun seekTo(positionMs: Long) {
    val pos = positionMs.coerceIn(startMs, endMs)
    playheadMs = pos
    mediaPlayer?.seekTo(pos.toInt())
  }

  // Validation
  val validation = AudioTrimmerValidation.validateTrimRange(startMs, endMs, durationMs)

  Scaffold(
    topBar = {
      TopAppBar(
        title = {
          Text(
            text = stringResource(R.string.tool19_name),
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
          )
        },
        navigationIcon = {
          IconButton(
            onClick = {
              if (hasUnsavedEdits && jobUi?.state != JobState.RUNNING) {
                showConfirmBackDialog = true
              } else {
                onBack()
              }
            },
            modifier = Modifier.testTag("tool19_back_button")
          ) {
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
              uris.firstOrNull()?.let { probeAudio(it) }
            },
            title = stringResource(R.string.tool19_picker_title),
            supportedFormatsText = stringResource(R.string.tool19_picker_formats),
            pickerMode = PickerMode.SAF_DOCUMENTS,
            maxFiles = 1,
            mimeTypeFilter = arrayOf("audio/*"),
            modifier = Modifier.testTag("tool19_picker_card")
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

      // 2. Selected File Info & Controls
      if (selectedUri != null && !isProbing && jobUi?.state != JobState.RUNNING && jobUi?.state != JobState.SUCCESS) {
        item {
          Card(
            modifier = Modifier.fillMaxWidth().testTag("tool19_file_card"),
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
                  Icon(Icons.Default.Audiotrack, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                  Column {
                    Text(
                      text = inputFileName,
                      style = MaterialTheme.typography.titleSmall,
                      fontWeight = FontWeight.Bold,
                      maxLines = 1,
                      overflow = TextOverflow.Ellipsis
                    )
                    Text(
                      text = "${FileFormatters.formatBytes(inputFileSizeBytes)} • ${AudioTrimmerValidation.formatMsToTime(durationMs)}",
                      style = MaterialTheme.typography.bodySmall,
                      color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                  }
                }

                OutlinedButton(
                  onClick = {
                    resetPlayer()
                    selectedUriString = null
                  },
                  modifier = Modifier.testTag("tool19_change_file_button")
                ) {
                  Text(stringResource(R.string.common_change))
                }
              }
            }
          }
        }

        // 3. Waveform / Timeline View
        item {
          Card(
            modifier = Modifier.fillMaxWidth().testTag("tool19_waveform_card"),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
          ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
              Text(
                text = stringResource(R.string.tool19_waveform_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
              )

              if (isGeneratingWaveform) {
                Row(
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                  CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                  Text(
                    text = stringResource(R.string.tool19_waveform_generating, (waveformProgress * 100).roundToInt()),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                  )
                }
              }

              if (waveformFallbackNotice != null) {
                Row(
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                  Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(16.dp))
                  Text(
                    text = waveformFallbackNotice!!,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary
                  )
                }
              }

              // Interactive Waveform & Handles Canvas
              BoxWithConstraints(
                modifier = Modifier
                  .fillMaxWidth()
                  .height(120.dp)
                  .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
              ) {
                val totalWidthPx = constraints.maxWidth.toFloat()
                val waveformHeightPx = constraints.maxHeight.toFloat()
                val dur = durationMs.coerceAtLeast(1L).toFloat()

                val primaryColor = MaterialTheme.colorScheme.primary
                val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
                val selectionColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
                val handleColor = MaterialTheme.colorScheme.primary

                val startFraction = (startMs.toFloat() / dur).coerceIn(0f, 1f)
                val endFraction = (endMs.toFloat() / dur).coerceIn(0f, 1f)
                val playheadFraction = (playheadMs.toFloat() / dur).coerceIn(0f, 1f)

                val startX = startFraction * totalWidthPx
                val endX = endFraction * totalWidthPx
                val playheadX = playheadFraction * totalWidthPx

                // Canvas drawing waveform and playhead
                Canvas(
                  modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                      detectTapGestures { offset ->
                        val tappedFraction = (offset.x / totalWidthPx).coerceIn(0f, 1f)
                        val target = (tappedFraction * dur).toLong().coerceIn(startMs, endMs)
                        seekTo(target)
                      }
                    }
                ) {
                  // 1. Draw Waveform or Timeline ruler
                  val buckets = waveformBuckets
                  if (buckets != null && buckets.isNotEmpty()) {
                    val barWidth = totalWidthPx / buckets.size
                    for (i in buckets.indices) {
                      val x = i * barWidth
                      val peak = buckets[i].coerceIn(0.05f, 1f)
                      val barHeight = peak * (waveformHeightPx * 0.75f)
                      val yStart = (waveformHeightPx - barHeight) / 2f

                      val inSelection = x in startX..endX
                      val barColor = if (inSelection) primaryColor else onSurfaceVariant.copy(alpha = 0.4f)
                      drawRect(
                        color = barColor,
                        topLeft = Offset(x, yStart),
                        size = Size(maxOf(1f, barWidth - 1f), barHeight)
                      )
                    }
                  } else {
                    // Timeline tick marks fallback
                    val majorTicks = 6
                    for (t in 0..majorTicks) {
                      val x = (t.toFloat() / majorTicks) * totalWidthPx
                      drawLine(
                        color = onSurfaceVariant.copy(alpha = 0.5f),
                        start = Offset(x, 10f),
                        end = Offset(x, waveformHeightPx - 10f),
                        strokeWidth = 2f
                      )
                    }
                  }

                  // 2. Draw selection shaded overlay
                  drawRect(
                    color = selectionColor,
                    topLeft = Offset(startX, 0f),
                    size = Size(maxOf(0f, endX - startX), waveformHeightPx)
                  )

                  // 3. Draw moving playhead line
                  drawLine(
                    color = Color.Red,
                    start = Offset(playheadX, 0f),
                    end = Offset(playheadX, waveformHeightPx),
                    strokeWidth = 3f
                  )
                }

                // Start Handle (Touch Target >= 48dp)
                Box(
                  modifier = Modifier
                    .offset { IntOffset(startX.toInt() - 24.dp.roundToPx(), 0) }
                    .size(48.dp, 120.dp)
                    .pointerInput(totalWidthPx, dur) {
                      detectDragGestures { change, dragAmount ->
                        change.consume()
                        val msDelta = if (totalWidthPx > 0f) ((dragAmount.x / totalWidthPx) * dur).toLong() else 0L
                        val newMs = (startMs + msDelta).coerceIn(0L, endMs - 500L)
                        startMs = newMs
                        hasUnsavedEdits = true
                      }
                    },
                  contentAlignment = Alignment.Center
                ) {
                  Box(
                    modifier = Modifier
                      .width(6.dp)
                      .height(80.dp)
                      .background(handleColor, RoundedCornerShape(3.dp))
                  )
                }

                // End Handle (Touch Target >= 48dp)
                Box(
                  modifier = Modifier
                    .offset { IntOffset(endX.toInt() - 24.dp.roundToPx(), 0) }
                    .size(48.dp, 120.dp)
                    .pointerInput(totalWidthPx, dur) {
                      detectDragGestures { change, dragAmount ->
                        change.consume()
                        val msDelta = if (totalWidthPx > 0f) ((dragAmount.x / totalWidthPx) * dur).toLong() else 0L
                        val newMs = (endMs + msDelta).coerceIn(startMs + 500L, durationMs)
                        endMs = newMs
                        hasUnsavedEdits = true
                      }
                    },
                  contentAlignment = Alignment.Center
                ) {
                  Box(
                    modifier = Modifier
                      .width(6.dp)
                      .height(80.dp)
                      .background(handleColor, RoundedCornerShape(3.dp))
                  )
                }
              }

              // Playback controls & preview
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
              ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                  IconButton(
                    onClick = { togglePlay() },
                    modifier = Modifier.testTag("tool19_play_pause_button")
                  ) {
                    Icon(
                      imageVector = if (isPlaying && !isPreviewingSelection) Icons.Default.Pause else Icons.Default.PlayArrow,
                      contentDescription = if (isPlaying) "Pause" else "Play",
                      tint = MaterialTheme.colorScheme.primary
                    )
                  }

                  Text(
                    text = AudioTrimmerValidation.formatMsToTime(playheadMs),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                  )
                }

                Button(
                  onClick = { previewSelection() },
                  modifier = Modifier.testTag("tool19_preview_selection_button")
                ) {
                  Icon(Icons.Default.Replay, contentDescription = null, modifier = Modifier.size(16.dp))
                  Spacer(Modifier.width(6.dp))
                  Text(stringResource(R.string.tool19_preview_selection))
                }
              }
            }
          }
        }

        // 4. Time Inputs & Nudge Buttons
        item {
          Card(
            modifier = Modifier.fillMaxWidth().testTag("tool19_boundaries_card"),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
          ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
              Text(
                text = stringResource(R.string.tool19_trim_points_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
              )

              // Start time row
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
              ) {
                Column {
                  Text(stringResource(R.string.tool19_start_time), style = MaterialTheme.typography.labelMedium)
                  Text(
                    text = AudioTrimmerValidation.formatMsToTime(startMs),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                  )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                  OutlinedButton(
                    onClick = {
                      startMs = (startMs - 100L).coerceAtLeast(0L)
                      hasUnsavedEdits = true
                    },
                    modifier = Modifier.testTag("tool19_start_minus_100")
                  ) {
                    Text("-100ms")
                  }
                  OutlinedButton(
                    onClick = {
                      startMs = (startMs + 100L).coerceAtMost(endMs - 500L)
                      hasUnsavedEdits = true
                    },
                    modifier = Modifier.testTag("tool19_start_plus_100")
                  ) {
                    Text("+100ms")
                  }
                }
              }

              // End time row
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
              ) {
                Column {
                  Text(stringResource(R.string.tool19_end_time), style = MaterialTheme.typography.labelMedium)
                  Text(
                    text = AudioTrimmerValidation.formatMsToTime(endMs),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                  )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                  OutlinedButton(
                    onClick = {
                      endMs = (endMs - 100L).coerceAtLeast(startMs + 500L)
                      hasUnsavedEdits = true
                    },
                    modifier = Modifier.testTag("tool19_end_minus_100")
                  ) {
                    Text("-100ms")
                  }
                  OutlinedButton(
                    onClick = {
                      endMs = (endMs + 100L).coerceAtMost(durationMs)
                      hasUnsavedEdits = true
                    },
                    modifier = Modifier.testTag("tool19_end_plus_100")
                  ) {
                    Text("+100ms")
                  }
                }
              }

              // Inline validation error
              if (!validation.isValid) {
                Text(
                  text = validation.errorMessage ?: "Invalid trim selection",
                  color = MaterialTheme.colorScheme.error,
                  style = MaterialTheme.typography.bodySmall
                )
              }

              // Length and cut summary
              val clipLengthMs = (endMs - startMs).coerceAtLeast(0L)
              val cutStartSeconds = String.format(java.util.Locale.US, "%.1fs", startMs / 1000f)
              val cutEndSeconds = String.format(java.util.Locale.US, "%.1fs", (durationMs - endMs).coerceAtLeast(0L) / 1000f)
              val keptFormatted = AudioTrimmerValidation.formatMsToTime(clipLengthMs)

              Text(
                text = stringResource(
                  R.string.tool19_kept_cut_summary,
                  keptFormatted,
                  cutStartSeconds,
                  cutEndSeconds
                ),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
              )
            }
          }
        }

        // 5. Output Format & Bitrate Options
        item {
          Card(
            modifier = Modifier.fillMaxWidth().testTag("tool19_options_card"),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
          ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
              Text(
                text = stringResource(R.string.tool19_output_format),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
              )

              Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("M4A", "WAV").forEach { format ->
                  FilterChip(
                    selected = outputFormat.equals(format, ignoreCase = true),
                    onClick = { outputFormat = format },
                    label = { Text(format) },
                    modifier = Modifier.testTag("tool19_format_chip_$format")
                  )
                }
              }

              if (outputFormat.equals("M4A", ignoreCase = true)) {
                Text(
                  text = stringResource(R.string.tool19_aac_bitrate),
                  style = MaterialTheme.typography.labelMedium
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                  listOf(64, 96, 128, 192, 256).forEach { br ->
                    FilterChip(
                      selected = aacBitrate == br,
                      onClick = { aacBitrate = br },
                      label = { Text("${br}k") },
                      modifier = Modifier.testTag("tool19_bitrate_chip_$br")
                    )
                  }
                }

                // Honest AAC note
                Text(
                  text = stringResource(R.string.tool19_aac_accuracy_note),
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant
                )
              }
            }
          }
        }

        // 6. Start Button
        item {
          Button(
            onClick = {
              val uri = selectedUri ?: return@Button
              resetPlayer()
              currentJobId = ToolJobRunner.start(
                toolId = TOOL_ID,
                inputName = inputFileName,
                optionsJson = "{startMs:$startMs,endMs:$endMs,format:\"$outputFormat\",bitrate:$aacBitrate}",
                inputUri = uri.toString(),
                inputSize = inputFileSizeBytes
              ) {
                AudioTrimmerHelper.trimAudio(
                  context = context,
                  inputUri = uri,
                  inputFileName = inputFileName,
                  startMs = startMs,
                  endMs = endMs,
                  outputFormat = outputFormat,
                  aacBitrateKbps = aacBitrate,
                  jobContext = this,
                  onProgress = { pct, stage ->
                    reportProgress(pct, stage, 1, 1, inputFileName)
                  }
                )
              }
            },
            enabled = validation.isValid,
            modifier = Modifier.fillMaxWidth().height(52.dp).testTag("tool19_start_button")
          ) {
            Icon(Icons.Default.Transform, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.tool19_action_trim))
          }
        }
      }

      // 7. Running State Card
      if (jobUi != null && jobUi!!.state == JobState.RUNNING) {
        item {
          ProgressCard(
            stage = jobUi!!.stage,
            progressPercent = jobUi!!.progressPercent,
            currentIndex = 1,
            totalFiles = 1,
            currentFileName = inputFileName,
            onCancel = { currentJobId?.let { ToolJobRunner.cancel(it) } },
            modifier = Modifier.testTag("tool19_progress_card")
          )
        }
      }

      // 8. Error Card
      if (jobUi != null && jobUi!!.state == JobState.FAILED) {
        item {
          ErrorCard(
            message = jobUi!!.errorMessage ?: stringResource(R.string.error_generic),
            onRetry = { currentJobId?.let { ToolJobRunner.clearJob(it) }; currentJobId = null },
            modifier = Modifier.testTag("tool19_error_card")
          )
        }
      }

      // 9. Success / Result Card
      if (jobUi != null && jobUi!!.state == JobState.SUCCESS) {
        val fileResult = jobUi!!.result?.files?.firstOrNull()
        val localFile = fileResult?.tempFilePath?.let { File(it) }

        item {
          Card(
            modifier = Modifier.fillMaxWidth().testTag("tool19_result_card"),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
          ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
              Text(
                text = stringResource(R.string.tool19_success_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
              )

              if (localFile != null && localFile.exists()) {
                val clipLength = (endMs - startMs).coerceAtLeast(0L)
                Text(
                  text = "${localFile.name} (${FileFormatters.formatBytes(localFile.length())})",
                  style = MaterialTheme.typography.bodyMedium
                )
                Text(
                  text = "Clip length: ${AudioTrimmerValidation.formatMsToTime(clipLength)}",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Pause input preview player so it doesn't conflict with result playback
                androidx.compose.runtime.LaunchedEffect(localFile) {
                  try {
                    if (mediaPlayer?.isPlaying == true) {
                      mediaPlayer?.pause()
                      isPlaying = false
                    }
                  } catch (_: Exception) {}
                }

                com.example.tools.common.AudioPlayerCard(localFile)

                // Action buttons
                val saveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(
                  if (outputFormat.equals("WAV", ignoreCase = true)) "audio/wav" else "audio/mp4"
                )) { destUri ->
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
                    modifier = Modifier.weight(1f).testTag("tool19_save_button")
                  ) {
                    Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.common_save))
                  }

                  OutlinedButton(
                    onClick = { FileLayer.shareFileAndNotify(context, localFile) },
                    modifier = Modifier.weight(1f).testTag("tool19_share_button")
                  ) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.common_share))
                  }
                }

                OutlinedButton(
                  onClick = { FileLayer.openFileAndNotify(context, localFile) },
                  modifier = Modifier.fillMaxWidth().testTag("tool19_open_button")
                ) {
                  Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                  Spacer(Modifier.width(6.dp))
                  Text(stringResource(R.string.common_open))
                }
              }

              Button(
                onClick = {
                  currentJobId?.let { ToolJobRunner.clearJob(it) }
                  currentJobId = null
                  selectedUriString = null
                },
                modifier = Modifier.fillMaxWidth().testTag("tool19_process_another_button")
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

  // Unsaved changes back confirmation dialog
  if (showConfirmBackDialog) {
    AlertDialog(
      onDismissRequest = { showConfirmBackDialog = false },
      title = { Text(stringResource(R.string.tool19_unsaved_dialog_title)) },
      text = { Text(stringResource(R.string.tool19_unsaved_dialog_desc)) },
      confirmButton = {
        Button(
          onClick = {
            showConfirmBackDialog = false
            resetPlayer()
            onBack()
          }
        ) {
          Text(stringResource(R.string.common_discard))
        }
      },
      dismissButton = {
        TextButton(onClick = { showConfirmBackDialog = false }) {
          Text(stringResource(R.string.common_cancel))
        }
      }
    )
  }
}
