package com.example.ui.settings

import android.graphics.Color
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ToolsNestApp
import com.example.engine.OutputFormat
import kotlinx.coroutines.launch

@Composable
fun ImageSettingsSection() {
  val context = LocalContext.current
  val imageSettings = (context.applicationContext as ToolsNestApp).imageSettingsDataStore
  val scope = rememberCoroutineScope()

  val defaultQuality by imageSettings.defaultQualityFlow.collectAsState(initial = 85)
  val defaultFormat by imageSettings.defaultFormatFlow.collectAsState(initial = OutputFormat.JPEG)

  Card(
    modifier = Modifier.fillMaxWidth(),
    shape = RoundedCornerShape(16.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
      ) {
        Icon(
          imageVector = Icons.Default.Image,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.primary
        )
        Text(
          text = stringResource(R.string.settings_image_title),
          style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
          color = MaterialTheme.colorScheme.onSurface
        )
      }

      // Default Format
      Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
          text = stringResource(R.string.settings_image_default_format),
          style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
        )
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
          OutputFormat.entries.forEach { fmt ->
            Row(
              verticalAlignment = Alignment.CenterVertically,
              modifier = Modifier.clickable {
                scope.launch { imageSettings.setDefaultFormat(fmt) }
              }
            ) {
              RadioButton(
                selected = defaultFormat == fmt,
                onClick = { scope.launch { imageSettings.setDefaultFormat(fmt) } }
              )
              Text(
                text = fmt.name,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(start = 4.dp)
              )
            }
          }
        }
      }

      // Default Quality
      Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween
        ) {
          Text(
            text = stringResource(R.string.settings_image_default_quality),
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
          )
          Text(
            text = "$defaultQuality%",
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.primary
          )
        }
        Slider(
          value = defaultQuality.toFloat(),
          onValueChange = { scope.launch { imageSettings.setDefaultQuality(it.toInt()) } },
          valueRange = 10f..100f,
          steps = 17
        )
      }
    }
  }
}
