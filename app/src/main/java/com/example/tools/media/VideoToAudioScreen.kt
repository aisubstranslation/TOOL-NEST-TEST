package com.example.tools.media

import android.net.Uri
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
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import com.example.media.WavMath
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

private const val TOOL_ID = 16

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoToAudioScreen(
  onBack: () -> Unit,
  modifier: Modifier = Modifier
) {
  BackHandler { onBack() }

  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val app = runCatching { ToolsNestApp.instance }.getOrNull()

  val defaultFormat by (app?.mediaSettingsDataStore?.defaultAudioFormatFlow ?: flowOf("M4A")).collectAsState(initial = "M4A")
  val defaultBitrate by (app?.mediaSettingsDataStore?.defaultAacBitrateFlow ?: flowOf(128)).collectAsState(initial = 128)

  var selectedUriString by rememberSaveable { mutableStateOf<String?>(null) }
  val selectedUri = selectedUriString?.let { Uri.parse(it) }
  var inputVideoName by remember { mutableStateOf("") }
  var inputVideoSize by remember { mutableStateOf(0L) }
  var probeResult by remember { mutableStateOf<MediaProbeResult?>(null) }
  var isProbing by remember { mutableStateOf(false) }

  var outputFormat by remember(defaultFormat) { mutableStateOf(defaultFormat) }
  var aacBitrate by remember(defaultBitrate) { mutableIntStateOf(defaultBitrate) }
  var customName by remember { mutableStateOf("") }

  var currentJobId by rememberSaveable { mutableStateOf<String?>(null) }
  var localErrorMessage by remember { mutableStateOf<String?>(null) }

  val jobUi by ToolJobRunner.observe(currentJobId ?: "").collectAsState(initial = null)

  LaunchedEffect(currentJobId) {
    val id = currentJobId
    if (id != null && !ToolJobRunner.hasJob(id)) {
      currentJobId = null
    }
  }

  fun probeVideo(uri: Uri) {
    selectedUriString = uri.toString()
    isProbing = true
    localErrorMessage = null
    currentJobId = null

    scope.launch {
      val info = withContext(Dispatchers.IO) { FileLayer.getUriFileInfo(context, uri) }
      inputVideoName = info.displayName
      inputVideoSize = info.sizeBytes

      val probe = withContext(Dispatchers.IO) {
        runCatching { MediaProbe.probeMedia(context, uri) }.getOrNull()
      }

      isProbing = false
      if (probe == null) {
        localErrorMessage = context.getString(R.string.error_generic)
        probeResult = null
      } else if (!probe.hasAudio) {
        localErrorMessage = context.getString(R.string.tool16_no_audio)
        probeResult = probe
      } else {
        probeResult = probe
      }
    }
  }

  val videoPicker = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.OpenDocument()
  ) { uri ->
    if (uri != null) {
      runCatching {
        context.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
      }
      probeVideo(uri)
    }
  }

  val saveLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.CreateDocument("*/*")
  ) { targetUri ->
    val fileResult = jobUi?.result?.files?.firstOrNull()
    val localFile = fileResult?.tempFilePath?.let { File(it) }
    if (targetUri != null && localFile != null && localFile.exists()) {
      scope.launch(Dispatchers.IO) {
        val success = FileLayer.saveFileToUri(context, localFile, targetUri)
        withContext(Dispatchers.Main) {
          if (success) {
            Toast.makeText(context, context.getString(R.string.file_saved_success), Toast.LENGTH_SHORT).show()
          } else {
            Toast.makeText(context, context.getString(R.string.error_generic), Toast.LENGTH_SHORT).show()
          }
        }
      }
    }
  }

  fun executeExtraction() {
    val uri = selectedUri ?: return
    val probe = probeResult ?: return
    if (!probe.hasAudio || ToolJobRunner.isToolBusy(TOOL_ID)) return
    localErrorMessage = null

    val options = VideoToAudioOptions(
      format = outputFormat,
      aacBitrateKbps = aacBitrate,
      customFileName = customName.trim().ifEmpty { null }
    )

    val jobId = ToolJobRunner.start(
      toolId = TOOL_ID,
      inputName = inputVideoName,
      optionsJson = "{format:\"${options.format}\",bitrate:${options.aacBitrateKbps}}",
      inputUri = uri.toString(),
      inputSize = inputVideoSize
    ) {
      VideoToAudioHelper.extractAudioFromVideo(
        context = context,
        videoUri = uri,
        originalVideoName = inputVideoName,
        options = options,
        jobContext = this
      )
    }

    currentJobId = jobId
  }

  Scaffold(
    topBar = {
      TopAppBar(
        title = {
          Text(
            text = stringResource(R.string.tool16_name),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
          )
        },
        navigationIcon = {
          IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
          }
        }
      )
    },
    modifier = modifier.fillMaxSize().testTag("tool_screen_16")
  ) { padding ->
    LazyColumn(
      modifier = Modifier
        .fillMaxSize()
        .padding(padding)
        .padding(horizontal = 16.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
      if (localErrorMessage != null) {
        item {
          ErrorCard(message = localErrorMessage!!)
        }
      }

      // Running State
      if (jobUi != null && jobUi!!.state == JobState.RUNNING) {
        item {
          ProgressCard(
            stage = jobUi!!.stage,
            progressPercent = jobUi!!.progressPercent,
            currentIndex = 1,
            totalFiles = 1,
            currentFileName = inputVideoName,
            onCancel = { currentJobId?.let { ToolJobRunner.cancel(it) } }
          )
        }
      }

      // Success State
      if (jobUi != null && jobUi!!.state == JobState.SUCCESS) {
        val fileRes = jobUi!!.result?.files?.firstOrNull()
        if (fileRes != null) {
          val localFile = fileRes.tempFilePath?.let { File(it) }
          item {
            Card(
              modifier = Modifier.fillMaxWidth(),
              shape = RoundedCornerShape(14.dp),
              colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
              Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
              ) {
                Text(
                  text = stringResource(R.string.tool16_success_title),
                  style = MaterialTheme.typography.titleMedium,
                  fontWeight = FontWeight.Bold
                )
                Text(
                  text = "${fileRes.outputName} • ${FileFormatters.formatBytes(fileRes.outputSizeBytes)}",
                  style = MaterialTheme.typography.bodyMedium
                )

                if (localFile != null && localFile.exists()) {
                  com.example.tools.common.AudioPlayerCard(localFile)
                }

                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                  Button(
                    onClick = { saveLauncher.launch(fileRes.outputName ?: "audio") },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp)
                  ) {
                    Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.common_save))
                  }

                  if (localFile != null && localFile.exists()) {
                    OutlinedButton(
                      onClick = { FileLayer.shareFileAndNotify(context, localFile, "audio/*") },
                      shape = RoundedCornerShape(10.dp)
                    ) {
                      Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                    OutlinedButton(
                      onClick = { FileLayer.openFileAndNotify(context, localFile, "audio/*") },
                      shape = RoundedCornerShape(10.dp)
                    ) {
                      Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                  }
                }

                OutlinedButton(
                  onClick = {
                    currentJobId = null
                    selectedUriString = null
                    probeResult = null
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

      // Failed State
      if (jobUi != null && jobUi!!.state == JobState.FAILED) {
        item {
          ErrorCard(
            message = jobUi!!.errorMessage ?: stringResource(R.string.error_generic),
            onRetry = { executeExtraction() }
          )
        }
      }

      // Empty State
      if (selectedUri == null && jobUi?.state != JobState.RUNNING && jobUi?.state != JobState.SUCCESS) {
        item {
          FilePickerCard(
            onFilesSelected = { uris ->
              uris.firstOrNull()?.let { probeVideo(it) }
            },
            title = stringResource(R.string.tool16_picker_title),
            supportedFormatsText = stringResource(R.string.tool16_picker_desc),
            pickerMode = PickerMode.SAF_DOCUMENTS,
            maxFiles = 1,
            mimeTypeFilter = arrayOf("video/*"),
            modifier = Modifier.testTag("video_picker_card")
          )
        }
      }

      // Probing State
      if (isProbing) {
        item {
          Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
          }
        }
      }

      // Video Options Panel
      if (selectedUri != null && probeResult != null && jobUi?.state != JobState.RUNNING && jobUi?.state != JobState.SUCCESS) {
        val probe = probeResult!!

        // Video Info Card
        item {
          Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
          ) {
            Column(
              modifier = Modifier.padding(14.dp),
              verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
              ) {
                Text(
                  text = inputVideoName,
                  style = MaterialTheme.typography.titleSmall,
                  fontWeight = FontWeight.Bold,
                  modifier = Modifier.weight(1f),
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis
                )
                OutlinedButton(
                  onClick = { videoPicker.launch(arrayOf("video/*")) },
                  shape = RoundedCornerShape(8.dp)
                ) {
                  Text(stringResource(R.string.common_change))
                }
              }

              val durationSec = (probe.durationMs / 1000).toInt()
              val min = durationSec / 60
              val sec = durationSec % 60
              val durationFormatted = String.format("%02d:%02d", min, sec)

              Text(
                text = "${FileFormatters.formatBytes(inputVideoSize)} • $durationFormatted • ${probe.width}×${probe.height}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )

              // Audio track details
              val audioTracks = probe.tracks.filter { it.isAudio }
              if (audioTracks.isNotEmpty()) {
                val first = audioTracks.first()
                val ch = if (first.channels == 1) "Mono" else if (first.channels == 2) "Stereo" else "${first.channels ?: 0} ch"
                Text(
                  text = "Audio: ${first.mime.substringAfter('/')} • ${first.sampleRate ?: 0}Hz • $ch",
                  style = MaterialTheme.typography.bodySmall,
                  fontWeight = FontWeight.Medium,
                  color = MaterialTheme.colorScheme.primary
                )
              } else {
                Text(
                  text = stringResource(R.string.tool16_no_audio),
                  style = MaterialTheme.typography.bodySmall,
                  fontWeight = FontWeight.Bold,
                  color = MaterialTheme.colorScheme.error
                )
              }
            }
          }
        }

        if (probe.hasAudio) {
          val isSourceAac = probe.audioMime.equals("audio/mp4a-latm", ignoreCase = true)

          // Output Format Selector
          item {
            Text(
              text = stringResource(R.string.tool17_output_format),
              style = MaterialTheme.typography.titleSmall,
              fontWeight = FontWeight.Bold
            )
            Row(
              modifier = Modifier.padding(top = 8.dp),
              horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
              FilterChip(
                selected = outputFormat == "M4A",
                onClick = { outputFormat = "M4A" },
                label = { Text("M4A (AAC)") }
              )
              FilterChip(
                selected = outputFormat == "WAV",
                onClick = { outputFormat = "WAV" },
                label = { Text("WAV (16-bit PCM)") }
              )
            }
          }

          // Encoding Note & Bitrate selector
          if (outputFormat == "M4A") {
            item {
              Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
              ) {
                Row(
                  modifier = Modifier.padding(12.dp),
                  horizontalArrangement = Arrangement.spacedBy(8.dp),
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                  Text(
                    text = if (isSourceAac) {
                      stringResource(R.string.tool16_audio_copy_note)
                    } else {
                      stringResource(R.string.tool16_audio_reencode_note)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                  )
                }
              }
            }

            if (!isSourceAac) {
              item {
                Text(
                  text = stringResource(R.string.tool17_aac_bitrate),
                  style = MaterialTheme.typography.titleSmall,
                  fontWeight = FontWeight.Bold
                )
                Row(
                  modifier = Modifier.padding(top = 8.dp),
                  horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                  for (br in AudioConverterHelper.AAC_BITRATES) {
                    FilterChip(
                      selected = aacBitrate == br,
                      onClick = { aacBitrate = br },
                      label = { Text("$br kbps") }
                    )
                  }
                }
              }
            }
          }

          // WAV estimate
          if (outputFormat == "WAV") {
            val durSec = probe.durationMs / 1000.0
            val audioTrack = probe.tracks.firstOrNull { it.isAudio }
            val estBytes = WavMath.estimateWavSizeBytes(durSec, audioTrack?.sampleRate ?: 44100, audioTrack?.channels ?: 2)

            item {
              Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
              ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                  Text(
                    text = "Estimated output size: ~${FileFormatters.formatBytes(estBytes)}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                  )
                  Text(
                    text = stringResource(R.string.tool16_wav_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                  )
                }
              }
            }
          }

          // Custom filename
          item {
            OutlinedTextField(
              value = customName,
              onValueChange = { customName = it },
              label = { Text(stringResource(R.string.common_output_filename)) },
              placeholder = { Text("${inputVideoName.substringBeforeLast('.')}_audio") },
              modifier = Modifier.fillMaxWidth(),
              singleLine = true
            )
          }

          // Action button
          item {
            val isBusy = ToolJobRunner.isToolBusy(TOOL_ID)
            Button(
              onClick = { executeExtraction() },
              modifier = Modifier.fillMaxWidth().height(52.dp).testTag("extract_audio_button"),
              enabled = !isBusy,
              shape = RoundedCornerShape(12.dp)
            ) {
              Icon(Icons.Default.MusicNote, contentDescription = null)
              Spacer(modifier = Modifier.width(8.dp))
              Text(stringResource(R.string.tool16_action_extract))
            }
          }
        }
      }

      item { Spacer(modifier = Modifier.height(24.dp)) }
    }
  }
}
