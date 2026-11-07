package com.example.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.tools.media.localizedLabel
import com.example.ToolsNestApp
import com.example.tools.media.VideoQualityPreset
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

@Composable
fun VideoSettingsSection() {
  val scope = rememberCoroutineScope()
  val app = runCatching { ToolsNestApp.instance }.getOrNull()
  val dataStore = app?.videoSettingsDataStore

  val defaultQuality by (dataStore?.defaultVideoQualityFlow ?: flowOf(VideoQualityPreset.BALANCED))
    .collectAsState(initial = VideoQualityPreset.BALANCED)

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
        text = stringResource(R.string.settings_video_title),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary
      )

      Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
          text = stringResource(R.string.settings_video_default_quality),
          style = MaterialTheme.typography.bodyMedium,
          fontWeight = FontWeight.Medium
        )
        Text(
          text = stringResource(R.string.settings_video_default_quality_desc),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          VideoQualityPreset.values().forEach { preset ->
            FilterChip(
              selected = defaultQuality == preset,
              onClick = {
                scope.launch {
                  dataStore?.setDefaultVideoQuality(preset)
                }
              },
              label = { Text(preset.localizedLabel()) }
            )
          }
        }
      }
    }
  }
}
