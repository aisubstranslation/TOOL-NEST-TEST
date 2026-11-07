package com.example.tools.common

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.UploadFile
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.R

enum class PickerMode {
  PHOTO_PICKER,
  SAF_DOCUMENTS
}

@Composable
fun FilePickerCard(
  onFilesSelected: (List<Uri>) -> Unit,
  modifier: Modifier = Modifier,
  title: String = stringResource(R.string.common_select_file),
  supportedFormatsText: String = "Supported formats: JPEG, PNG, WebP (up to 20 files, max 50 MB each)",
  pickerMode: PickerMode = PickerMode.PHOTO_PICKER,
  maxFiles: Int = 20,
  mimeTypeFilter: Array<String> = arrayOf("image/*")
) {
  // Photo Picker launcher (zero storage permissions)
  // Note: PickMultipleVisualMedia requires maxItems > 1 in AndroidX. Use maxOf(2, maxFiles)
  // to avoid IllegalArgumentException when maxFiles == 1, then trim the result to maxFiles.
  val photoPickerLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = maxOf(2, maxFiles))
  ) { uris ->
    if (uris.isNotEmpty()) {
      onFilesSelected(uris.take(maxFiles))
    }
  }

  // SAF Documents launcher
  val safLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.OpenMultipleDocuments()
  ) { uris ->
    if (uris.isNotEmpty()) {
      onFilesSelected(uris.take(maxFiles))
    }
  }

  Card(
    modifier = modifier.fillMaxWidth(),
    shape = RoundedCornerShape(16.dp),
    colors = CardDefaults.cardColors(
      containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    ),
    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(24.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
      Icon(
        imageVector = if (pickerMode == PickerMode.PHOTO_PICKER) {
          Icons.Default.AddPhotoAlternate
        } else {
          Icons.Default.UploadFile
        },
        contentDescription = null,
        tint = MaterialTheme.colorScheme.primary,
        modifier = Modifier.size(48.dp)
      )

      Text(
        text = supportedFormatsText,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center
      )

      Button(
        onClick = {
          if (pickerMode == PickerMode.PHOTO_PICKER) {
            photoPickerLauncher.launch(
              PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            )
          } else {
            safLauncher.launch(mimeTypeFilter)
          }
        },
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.testTag("select_files_button")
      ) {
        Text(
          text = title,
          style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold)
        )
      }
    }
  }
}
