package com.example.ui.settings

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.ToolsNestApp
import com.example.file.FileFormatters
import com.example.tools.media.AudioConverterHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun MediaSettingsSection() {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val app = runCatching { ToolsNestApp.instance }.getOrNull()
  val dataStore = app?.mediaSettingsDataStore

  val defaultFormat by (dataStore?.defaultAudioFormatFlow ?: flowOf("M4A")).collectAsState(initial = "M4A")
  val defaultBitrate by (dataStore?.defaultAacBitrateFlow ?: flowOf(128)).collectAsState(initial = 128)
  val bgNotifications by (dataStore?.bgNotificationsFlow ?: flowOf(true)).collectAsState(initial = true)
  val deleteAfterSave by (dataStore?.deleteAfterSaveFlow ?: flowOf(true)).collectAsState(initial = true)

  var cacheSizeBytes by remember { mutableLongStateOf(0L) }
  var unsavedJobsCount by remember { mutableStateOf(0) }

  // Measure cache and unsaved count
  remember(Unit) {
    scope.launch(Dispatchers.IO) {
      val jobsDir = com.example.file.OutputDirs.getJobsDir(context)
      val size = if (jobsDir.exists()) jobsDir.walkTopDown().filter { it.isFile }.sumOf { it.length() } else 0L
      val unsaved = app?.jobRepository?.getUnsavedCompletedJobs()?.size ?: 0
      withContext(Dispatchers.Main) {
        cacheSizeBytes = size
        unsavedJobsCount = unsaved
      }
    }
  }

  Card(
    modifier = Modifier.fillMaxWidth(),
    shape = RoundedCornerShape(16.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
  ) {
    Column(
      modifier = Modifier.padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
      Text(
        text = stringResource(R.string.settings_media_title),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary
      )

      // Default format selector
      Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
          text = stringResource(R.string.settings_media_default_format),
          style = MaterialTheme.typography.bodyMedium,
          fontWeight = FontWeight.Medium
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          FilterChip(
            selected = defaultFormat == "M4A",
            onClick = { scope.launch { dataStore?.setDefaultAudioFormat("M4A") } },
            label = { Text("M4A (AAC)") }
          )
          FilterChip(
            selected = defaultFormat == "WAV",
            onClick = { scope.launch { dataStore?.setDefaultAudioFormat("WAV") } },
            label = { Text("WAV") }
          )
        }
      }

      // Default AAC bitrate selector
      Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
          text = stringResource(R.string.settings_media_default_bitrate),
          style = MaterialTheme.typography.bodyMedium,
          fontWeight = FontWeight.Medium
        )
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          for (br in AudioConverterHelper.AAC_BITRATES) {
            FilterChip(
              selected = defaultBitrate == br,
              onClick = { scope.launch { dataStore?.setDefaultAacBitrate(br) } },
              label = { Text("$br k") }
            )
          }
        }
      }

      // Background notifications switch
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = stringResource(R.string.settings_media_bg_notifications),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
          )
          Text(
            text = stringResource(R.string.settings_media_bg_notifications_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
        Switch(
          checked = bgNotifications,
          onCheckedChange = { scope.launch { dataStore?.setBgNotifications(it) } }
        )
      }

      // Delete after save switch
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = stringResource(R.string.settings_media_delete_after_save),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
          )
          Text(
            text = stringResource(R.string.settings_media_delete_after_save_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
        Switch(
          checked = deleteAfterSave,
          onCheckedChange = { scope.launch { dataStore?.setDeleteAfterSave(it) } }
        )
      }

      // Storage & cache information
      Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
          text = stringResource(R.string.settings_media_cache, FileFormatters.formatBytes(cacheSizeBytes)),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (unsavedJobsCount > 0) {
          Text(
            text = stringResource(R.string.settings_media_unsaved, unsavedJobsCount),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.tertiary
          )
        }
      }
    }
  }
}
