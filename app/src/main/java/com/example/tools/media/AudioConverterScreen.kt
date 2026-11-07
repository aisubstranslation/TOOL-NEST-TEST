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
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Transform
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
import com.example.media.MediaErrorMapper
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

private const val TOOL_ID = 17

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioConverterScreen(
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
  var inputFileName by remember { mutableStateOf("") }
  var inputFileSize by remember { mutableStateOf(0L) }
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

  fun probeSelectedUri(uri: Uri) {
    selectedUriString = uri.toString()
    isProbing = true
    localErrorMessage = null
    currentJobId = null

    scope.launch {
      val info = withContext(Dispatchers.IO) { FileLayer.getUriFileInfo(context, uri) }
      inputFileName = info.displayName
      inputFileSize = info.sizeBytes

      val probe = withContext(Dispatchers.IO) {
        runCatching { MediaProbe.probeMedia(context, uri) }.getOrNull()
      }

      isProbing = false
      if (probe == null || !probe.hasAudio) {
        localErrorMessage = context.getString(R.string.tool17_err_no_audio)
        probeResult = null
      } else {
        probeResult = probe
      }
    }
  }

  val docPicker = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.OpenDocument()
  ) { uri ->
    if (uri != null) {
      runCatching {
        context.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
      }
      probeSelectedUri(uri)
    }
  }

  // Save converted file launcher
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

  fun executeConversion() {
    val uri = selectedUri ?: return
    val probe = probeResult ?: return
    if (ToolJobRunner.isToolBusy(TOOL_ID)) return
    localErrorMessage = null

    val options = AudioConverterOptions(
      format = outputFormat,
      aacBitrateKbps = aacBitrate,
      customFileName = customName.trim().ifEmpty { null }
    )

    val jobId = ToolJobRunner.start(
      toolId = TOOL_ID,
      inputName = inputFileName,
      optionsJson = "{format:\"${options.format}\",bitrate:${options.aacBitrateKbps}}",
      inputUri = uri.toString(),
      inputSize = inputFileSize
    ) {
      AudioConverterHelper.convertAudio(
        context = context,
        inputUri = uri,
        originalName = inputFileName,
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
            text = stringResource(R.string.tool17_name),
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
    modifier = modifier.fillMaxSize().testTag("tool_screen_17")
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

      // Running Progress
      if (jobUi != null && jobUi!!.state == JobState.RUNNING) {
        item {
          ProgressCard(
            stage = jobUi!!.stage,
            progressPercent = jobUi!!.progressPercent,
            currentIndex = 1,
            totalFiles = 1,
            currentFileName = inputFileName,
            onCancel = { currentJobId?.let { ToolJobRunner.cancel(it) } }
          )
        }
      }

      // Success Result Card
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
                  text = stringResource(R.string.tool17_success_title),
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
                    onClick = { saveLauncher.launch(fileRes.outputName ?: "converted") },
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
            onRetry = { executeConversion() }
          )
        }
      }

      // Empty State: Picker
      if (selectedUri == null && jobUi?.state != JobState.RUNNING && jobUi?.state != JobState.SUCCESS) {
        item {
          FilePickerCard(
            onFilesSelected = { uris ->
              uris.firstOrNull()?.let { probeSelectedUri(it) }
            },
            title = stringResource(R.string.tool17_picker_title),
            supportedFormatsText = stringResource(R.string.tool17_picker_formats),
            pickerMode = PickerMode.SAF_DOCUMENTS,
            maxFiles = 1,
            mimeTypeFilter = arrayOf("audio/*"),
            modifier = Modifier.testTag("audio_picker_card")
          )
        }

        item {
          Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
          ) {
            Row(
              modifier = Modifier.padding(14.dp),
              horizontalArrangement = Arrangement.spacedBy(10.dp),
              verticalAlignment = Alignment.CenterVertically
            ) {
              Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
              Text(
                text = stringResource(R.string.tool17_format_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
            }
          }
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

      // Options Panel when file selected
      if (selectedUri != null && probeResult != null && jobUi?.state != JobState.RUNNING && jobUi?.state != JobState.SUCCESS) {
        val probe = probeResult!!

        // Source Audio Info Card
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
                  text = inputFileName,
                  style = MaterialTheme.typography.titleSmall,
                  fontWeight = FontWeight.Bold,
                  modifier = Modifier.weight(1f),
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis
                )
                OutlinedButton(
                  onClick = { docPicker.launch(arrayOf("audio/*", "video/*")) },
                  shape = RoundedCornerShape(8.dp)
                ) {
                  Text(stringResource(R.string.common_change))
                }
              }

              val durationSec = (probe.durationMs / 1000).toInt()
              val min = durationSec / 60
              val sec = durationSec % 60
              val durationFormatted = String.format("%02d:%02d", min, sec)

              val audioTrack = probe.tracks.firstOrNull { it.isAudio }
              val codec = probe.audioMime?.substringAfter('/')?.uppercase() ?: "AUDIO"
              val sr = audioTrack?.sampleRate ?: 0
              val ch = if (audioTrack?.channels == 1) "Mono" else if (audioTrack?.channels == 2) "Stereo" else "${audioTrack?.channels ?: 0} ch"
              val kbps = if (probe.bitrate > 0) " • ${probe.bitrate / 1000} kbps" else ""

              Text(
                text = "${FileFormatters.formatBytes(inputFileSize)} • $durationFormatted • $codec • ${sr}Hz • $ch$kbps",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
            }
          }
        }

        // Format selector
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

        // AAC Bitrate selector
        if (outputFormat == "M4A") {
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

        // Size estimate and honest quality note
        item {
          val durSec = probe.durationMs / 1000.0
          val estBytes = if (outputFormat == "WAV") {
            val audioTrack = probe.tracks.firstOrNull { it.isAudio }
            WavMath.estimateWavSizeBytes(durSec, audioTrack?.sampleRate ?: 44100, audioTrack?.channels ?: 2)
          } else {
            ((aacBitrate * 1000L / 8.0) * durSec).toLong().coerceAtLeast(1024L)
          }

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
                text = stringResource(R.string.tool17_quality_honesty_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
            }
          }
        }

        // Custom output filename
        item {
          OutlinedTextField(
            value = customName,
            onValueChange = { customName = it },
            label = { Text(stringResource(R.string.common_output_filename)) },
            placeholder = { Text("${inputFileName.substringBeforeLast('.')}_converted") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
          )
        }

        // Action button
        item {
          val isBusy = ToolJobRunner.isToolBusy(TOOL_ID)
          Button(
            onClick = { executeConversion() },
            modifier = Modifier.fillMaxWidth().height(52.dp).testTag("convert_audio_button"),
            enabled = !isBusy,
            shape = RoundedCornerShape(12.dp)
          ) {
            Icon(Icons.Default.Transform, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.tool17_action_convert))
          }
        }
      }

      item { Spacer(modifier = Modifier.height(24.dp)) }
    }
  }
}
