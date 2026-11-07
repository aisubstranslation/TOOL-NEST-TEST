package com.example.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
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
import kotlinx.coroutines.launch

@Composable
fun PdfSettingsSection() {
  val context = LocalContext.current
  val pdfSettings = (context.applicationContext as ToolsNestApp).pdfSettingsDataStore
  val scope = rememberCoroutineScope()

  val defaultPageSize by pdfSettings.defaultPageSizeFlow.collectAsState(initial = "A4")
  val defaultQrSize by pdfSettings.defaultQrSizeFlow.collectAsState(initial = 1024)

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
          imageVector = Icons.Default.PictureAsPdf,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.primary
        )
        Text(
          text = stringResource(R.string.settings_pdf_title),
          style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
          color = MaterialTheme.colorScheme.onSurface
        )
      }

      // Default PDF Page Size
      Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
          text = stringResource(R.string.settings_pdf_page_size),
          style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
        )
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
          listOf("A4", "Letter").forEach { size ->
            Row(
              verticalAlignment = Alignment.CenterVertically,
              modifier = Modifier.clickable {
                scope.launch { pdfSettings.setDefaultPageSize(size) }
              }
            ) {
              RadioButton(
                selected = defaultPageSize == size,
                onClick = { scope.launch { pdfSettings.setDefaultPageSize(size) } }
              )
              Text(
                text = size,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(start = 4.dp)
              )
            }
          }
        }
      }

      // Default QR Resolution
      Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
          text = stringResource(R.string.settings_qr_resolution),
          style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
        )
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
          listOf(512 to "512 px", 1024 to "1024 px", 2048 to "2048 px").forEach { (dim, label) ->
            Row(
              verticalAlignment = Alignment.CenterVertically,
              modifier = Modifier.clickable {
                scope.launch { pdfSettings.setDefaultQrSize(dim) }
              }
            ) {
              RadioButton(
                selected = defaultQrSize == dim,
                onClick = { scope.launch { pdfSettings.setDefaultQrSize(dim) } }
              )
              Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(start = 4.dp)
              )
            }
          }
        }
      }
    }
  }
}
