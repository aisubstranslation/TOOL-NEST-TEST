package com.example.tools.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R

@Composable
fun ErrorCard(
  message: String,
  recoveryHint: String? = null,
  onRetry: (() -> Unit)? = null,
  modifier: Modifier = Modifier
) {
  Card(
    modifier = modifier.fillMaxWidth(),
    shape = RoundedCornerShape(16.dp),
    colors = CardDefaults.cardColors(
      containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f)
    )
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(18.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
      ) {
        Icon(
          imageVector = Icons.Default.ErrorOutline,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.error,
          modifier = Modifier.size(28.dp)
        )
        Text(
          text = message,
          style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
          color = MaterialTheme.colorScheme.onErrorContainer
        )
      }

      if (!recoveryHint.isNullOrBlank()) {
        Text(
          text = recoveryHint,
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onErrorContainer
        )
      }

      if (onRetry != null) {
        Button(
          onClick = onRetry,
          modifier = Modifier
            .align(Alignment.End)
            .testTag("retry_button"),
          shape = RoundedCornerShape(10.dp)
        ) {
          Text(stringResource(R.string.common_retry))
        }
      }
    }
  }
}
