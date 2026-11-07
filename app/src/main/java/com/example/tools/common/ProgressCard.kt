package com.example.tools.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R

@Composable
fun ProgressCard(
  stage: String,
  progressPercent: Int?,
  currentIndex: Int,
  totalFiles: Int,
  currentFileName: String,
  onCancel: () -> Unit,
  modifier: Modifier = Modifier
) {
  Card(
    modifier = modifier.fillMaxWidth(),
    shape = RoundedCornerShape(16.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(20.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text(
          text = stage.ifEmpty { stringResource(R.string.common_processing) },
          style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
          color = MaterialTheme.colorScheme.onSurface,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier.weight(1f)
        )

        if (progressPercent != null && progressPercent in 0..100) {
          Text(
            text = "$progressPercent%",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 8.dp)
          )
        }
      }

      if (progressPercent != null && progressPercent in 0..100) {
        LinearProgressIndicator(
          progress = { progressPercent / 100f },
          modifier = Modifier.fillMaxWidth(),
          trackColor = MaterialTheme.colorScheme.surfaceVariant
        )
      } else {
        LinearProgressIndicator(
          modifier = Modifier.fillMaxWidth(),
          trackColor = MaterialTheme.colorScheme.surfaceVariant
        )
      }

      if (totalFiles > 1 && currentIndex > 0) {
        Text(
          text = stringResource(R.string.common_file_counter, currentIndex, totalFiles, currentFileName),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis
        )
      } else if (currentFileName.isNotEmpty()) {
        Text(
          text = currentFileName,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis
        )
      }

      OutlinedButton(
        onClick = onCancel,
        modifier = Modifier
          .align(Alignment.End)
          .testTag("cancel_job_button"),
        shape = RoundedCornerShape(10.dp)
      ) {
        Text(stringResource(R.string.common_cancel))
      }
    }
  }
}
