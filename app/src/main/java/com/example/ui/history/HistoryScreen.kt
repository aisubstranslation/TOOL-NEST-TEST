package com.example.ui.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.db.HistoryEntity
import com.example.file.FileFormatters
import com.example.model.ToolRegistry
import com.example.ui.MainViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HistoryScreen(
  viewModel: MainViewModel,
  modifier: Modifier = Modifier
) {
  val historyList by viewModel.historyList.collectAsState()
  var showClearConfirmDialog by remember { mutableStateOf(false) }

  if (showClearConfirmDialog) {
    AlertDialog(
      onDismissRequest = { showClearConfirmDialog = false },
      title = { Text(stringResource(R.string.history_clear_confirm_title)) },
      text = { Text(stringResource(R.string.history_clear_confirm_desc)) },
      confirmButton = {
        TextButton(
          onClick = {
            viewModel.clearAllHistory()
            showClearConfirmDialog = false
          },
          modifier = Modifier.testTag("confirm_clear_history_button")
        ) {
          Text(stringResource(R.string.history_clear_all), color = MaterialTheme.colorScheme.error)
        }
      },
      dismissButton = {
        TextButton(onClick = { showClearConfirmDialog = false }) {
          Text(stringResource(R.string.common_cancel))
        }
      }
    )
  }

  LazyColumn(
    modifier = modifier
      .fillMaxSize()
      .testTag("history_screen"),
    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 88.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp)
  ) {
    item {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text(
          text = stringResource(R.string.history_title),
          style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
          color = MaterialTheme.colorScheme.onBackground
        )

        if (historyList.isNotEmpty()) {
          IconButton(
            onClick = { showClearConfirmDialog = true },
            modifier = Modifier.testTag("clear_history_icon_button")
          ) {
            Icon(
              imageVector = Icons.Default.DeleteSweep,
              contentDescription = stringResource(R.string.history_clear_all),
              tint = MaterialTheme.colorScheme.error
            )
          }
        }
      }
    }

    if (historyList.isEmpty()) {
      item {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 64.dp)
            .testTag("history_empty_state"),
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.Center
        ) {
          Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.size(80.dp)
          ) {
            Box(contentAlignment = Alignment.Center) {
              Icon(
                imageVector = Icons.Default.History,
                contentDescription = null,
                modifier = Modifier.size(44.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
              )
            }
          }
          Spacer(modifier = Modifier.height(16.dp))
          Text(
            text = stringResource(R.string.history_empty_title),
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface
          )
          Spacer(modifier = Modifier.height(6.dp))
          Text(
            text = stringResource(R.string.history_empty_desc),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 32.dp)
          )
        }
      }
    } else {
      items(historyList, key = { it.id }) { item ->
        HistoryItemCard(
          item = item,
          onDelete = { viewModel.deleteHistoryItem(item.id) }
        )
      }
    }
  }
}

@Composable
private fun HistoryItemCard(
  item: HistoryEntity,
  onDelete: () -> Unit
) {
  val tool = ToolRegistry.getById(item.toolId)
  val toolName = if (tool != null) stringResource(tool.nameRes) else "Tool #${item.toolId}"
  val dateFormatted = remember(item.timestamp) {
    val sdf = SimpleDateFormat("MMM d, yyyy  HH:mm", Locale.getDefault())
    sdf.format(Date(item.timestamp))
  }

  Card(
    modifier = Modifier
      .fillMaxWidth()
      .testTag("history_item_${item.id}"),
    shape = RoundedCornerShape(16.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(16.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
      // Status icon
      val (statusIcon, statusColor) = if (item.status == "SUCCESS") {
        Icons.Default.CheckCircle to MaterialTheme.colorScheme.primary
      } else {
        Icons.Default.Error to MaterialTheme.colorScheme.error
      }

      Icon(
        imageVector = statusIcon,
        contentDescription = item.status,
        tint = statusColor,
        modifier = Modifier.size(32.dp)
      )

      Column(
        modifier = Modifier.weight(1f),
        verticalArrangement = Arrangement.spacedBy(4.dp)
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Text(
            text = toolName,
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface
          )
          Text(
            text = dateFormatted,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }

        Text(
          text = item.inputFileName,
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurface,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis
        )

        if (item.outputSizeBytes > 0L) {
          Text(
            text = "${stringResource(R.string.history_output_size)}: ${FileFormatters.formatBytes(item.outputSizeBytes)}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
      }

      IconButton(
        onClick = onDelete,
        modifier = Modifier.testTag("delete_history_item_${item.id}")
      ) {
        Icon(
          imageVector = Icons.Default.Delete,
          contentDescription = stringResource(R.string.history_delete_item),
          tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
          modifier = Modifier.size(20.dp)
        )
      }
    }
  }
}
