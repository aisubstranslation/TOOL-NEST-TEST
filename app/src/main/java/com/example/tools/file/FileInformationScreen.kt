package com.example.tools.file

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.example.tools.common.ErrorCard
import com.example.tools.common.FilePickerCard
import com.example.tools.common.PickerMode
import com.example.tools.common.ToolScaffold
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun FileInformationScreen(
  onBack: () -> Unit,
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current
  var selectedUriString by rememberSaveable { mutableStateOf<String?>(null) }
  var fileProperties by remember { mutableStateOf<FilePropertiesResult?>(null) }
  var errorMessage by rememberSaveable { mutableStateOf<String?>(null) }
  var isLoading by remember { mutableStateOf(false) }

  LaunchedEffect(selectedUriString) {
    val uriStr = selectedUriString
    if (uriStr != null) {
      isLoading = true
      errorMessage = null
      try {
        val uri = Uri.parse(uriStr)
        val result = withContext(Dispatchers.IO) {
          FileInfoAnalyzer.analyzeFile(context, uri)
        }
        fileProperties = result
      } catch (e: Exception) {
        errorMessage = e.message ?: "Failed to read file properties"
        fileProperties = null
      } finally {
        isLoading = false
      }
    } else {
      fileProperties = null
    }
  }

  ToolScaffold(
    title = stringResource(R.string.tool6_name),
    onBack = onBack,
    modifier = modifier
  ) { padding ->
    LazyColumn(
      modifier = Modifier
        .fillMaxSize()
        .padding(padding)
        .testTag("tool_screen_6"),
      contentPadding = PaddingValues(16.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
      // 1. Error state if inspection fails
      if (errorMessage != null) {
        item {
          ErrorCard(
            message = errorMessage!!,
            recoveryHint = stringResource(R.string.tool6_file_picker_hint),
            onRetry = {
              errorMessage = null
              selectedUriString = null
              fileProperties = null
            }
          )
        }
      }

      // 2. Details display if file is loaded
      val props = fileProperties
      if (props != null) {
        item {
          Card(
            modifier = Modifier
              .fillMaxWidth()
              .testTag("file_info_card"),
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
              // Header
              Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
              ) {
                Surface(
                  shape = RoundedCornerShape(10.dp),
                  color = MaterialTheme.colorScheme.primaryContainer,
                  modifier = Modifier.size(44.dp)
                ) {
                  Icon(
                    imageVector = Icons.Default.Description,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier
                      .padding(10.dp)
                      .fillMaxSize()
                  )
                }

                Column(modifier = Modifier.weight(1f)) {
                  Text(
                    text = props.fileName,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                  )
                  Text(
                    text = props.formattedSize,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                  )
                }
              }

              HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)

              // Basic Properties Table
              PropertyRow(stringResource(R.string.tool6_property_name), props.fileName)
              PropertyRow(stringResource(R.string.tool6_property_extension), props.extension)
              PropertyRow(stringResource(R.string.tool6_property_mime), props.mimeType)
              PropertyRow(
                stringResource(R.string.tool6_property_size),
                "${props.formattedSize} (${props.sizeBytes} bytes)"
              )
              PropertyRow(stringResource(R.string.tool6_property_modified), props.lastModified)

              // Category-specific properties
              if (props.categoryProperties.isNotEmpty()) {
                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                Text(
                  text = stringResource(R.string.tool6_details_title),
                  style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                  color = MaterialTheme.colorScheme.primary
                )

                for (catProp in props.categoryProperties) {
                  PropertyRow(stringResource(catProp.labelRes), catProp.value)
                }
              }

              Spacer(Modifier.height(4.dp))

              // Action buttons: Copy, Share, Select Another
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
              ) {
                Button(
                  onClick = {
                    val formatted = FileInfoAnalyzer.formatForClipboardOrShare(context, props)
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("File Information", formatted))
                    Toast.makeText(context, context.getString(R.string.tool6_info_copied), Toast.LENGTH_SHORT).show()
                  },
                  modifier = Modifier
                    .weight(1f)
                    .testTag("action_copy_info"),
                  shape = RoundedCornerShape(10.dp)
                ) {
                  Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                  Spacer(Modifier.size(6.dp))
                  Text(stringResource(R.string.tool6_copy_info))
                }

                OutlinedButton(
                  onClick = {
                    val formatted = FileInfoAnalyzer.formatForClipboardOrShare(context, props)
                    val sendIntent = Intent(Intent.ACTION_SEND).apply {
                      type = "text/plain"
                      putExtra(Intent.EXTRA_SUBJECT, "File Information: ${props.fileName}")
                      putExtra(Intent.EXTRA_TEXT, formatted)
                    }
                    context.startActivity(Intent.createChooser(sendIntent, "Share File Info"))
                  },
                  modifier = Modifier
                    .weight(1f)
                    .testTag("action_share_info"),
                  shape = RoundedCornerShape(10.dp)
                ) {
                  Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                  Spacer(Modifier.size(6.dp))
                  Text(stringResource(R.string.tool6_share_info))
                }
              }

              OutlinedButton(
                onClick = {
                  selectedUriString = null
                  fileProperties = null
                },
                modifier = Modifier
                  .fillMaxWidth()
                  .testTag("action_select_another"),
                shape = RoundedCornerShape(10.dp)
              ) {
                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.size(6.dp))
                Text(stringResource(R.string.tool6_select_another))
              }
            }
          }
        }
      }
      // 3. Initial Picker State
      else if (!isLoading) {
        item {
          FilePickerCard(
            onFilesSelected = { uris ->
              val first = uris.firstOrNull()
              if (first != null) {
                selectedUriString = first.toString()
              }
            },
            pickerMode = PickerMode.SAF_DOCUMENTS,
            mimeTypeFilter = arrayOf("*/*"),
            maxFiles = 1,
            title = stringResource(R.string.tool6_select_file),
            supportedFormatsText = stringResource(R.string.tool6_file_picker_hint)
          )
        }
      }
    }
  }
}

@Composable
private fun PropertyRow(label: String, value: String) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically
  ) {
    Text(
      text = label,
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.weight(1f)
    )
    Text(
      text = value,
      style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
      color = MaterialTheme.colorScheme.onSurface,
      modifier = Modifier.padding(start = 8.dp)
    )
  }
}
