package com.example.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.file.FileFormatters
import com.example.ui.MainViewModel
import com.example.ui.theme.ThemeMode
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
  viewModel: MainViewModel,
  onLanguageChanged: (String) -> Unit,
  modifier: Modifier = Modifier
) {
  val themeMode by viewModel.themeMode.collectAsState()
  val languageCode by viewModel.languageCode.collectAsState()
  val outputPrefix by viewModel.outputPrefix.collectAsState()
  val cacheSizeBytes by viewModel.tempCacheSizeBytes.collectAsState()
  androidx.compose.runtime.LaunchedEffect(Unit) { viewModel.refreshCacheSize() }

  val context = LocalContext.current
  val snackbarHostState = remember { SnackbarHostState() }
  val scope = rememberCoroutineScope()

  var prefixInput by remember(outputPrefix) { mutableStateOf(outputPrefix) }

  LazyColumn(
    modifier = modifier
      .fillMaxSize()
      .testTag("settings_screen"),
    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 88.dp),
    verticalArrangement = Arrangement.spacedBy(20.dp)
  ) {
    item {
      Text(
        text = stringResource(R.string.settings_title),
        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
        color = MaterialTheme.colorScheme.onBackground
      )
    }

    // 1. Appearance / Theme
    item {
      SettingsSectionCard(
        title = stringResource(R.string.settings_section_appearance),
        icon = Icons.Default.Palette
      ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
          ThemeOptionRow(
            title = stringResource(R.string.settings_theme_system),
            selected = themeMode == ThemeMode.SYSTEM,
            onClick = { viewModel.updateThemeMode(ThemeMode.SYSTEM) }
          )
          ThemeOptionRow(
            title = stringResource(R.string.settings_theme_light),
            selected = themeMode == ThemeMode.LIGHT,
            onClick = { viewModel.updateThemeMode(ThemeMode.LIGHT) }
          )
          ThemeOptionRow(
            title = stringResource(R.string.settings_theme_dark),
            selected = themeMode == ThemeMode.DARK,
            onClick = { viewModel.updateThemeMode(ThemeMode.DARK) }
          )
        }
      }
    }

    // 2. Language
    item {
      SettingsSectionCard(
        title = stringResource(R.string.settings_section_language),
        icon = Icons.Default.Language
      ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
          LanguageOptionRow(
            title = stringResource(R.string.settings_language_system),
            selected = languageCode == "system",
            onClick = {
              viewModel.updateLanguageCode("system")
              onLanguageChanged("system")
            }
          )
          LanguageOptionRow(
            title = stringResource(R.string.settings_language_en),
            selected = languageCode == "en",
            onClick = {
              viewModel.updateLanguageCode("en")
              onLanguageChanged("en")
            }
          )
          LanguageOptionRow(
            title = stringResource(R.string.settings_language_ur),
            selected = languageCode == "ur",
            onClick = {
              viewModel.updateLanguageCode("ur")
              onLanguageChanged("ur")
            }
          )
          LanguageOptionRow(
            title = stringResource(R.string.settings_language_hi),
            selected = languageCode == "hi",
            onClick = {
              viewModel.updateLanguageCode("hi")
              onLanguageChanged("hi")
            }
          )
        }
      }
    }

    // 3. Storage & Naming Prefix
    item {
      SettingsSectionCard(
        title = stringResource(R.string.settings_section_storage),
        icon = Icons.Default.Storage
      ) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
          // Prefix field
          Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
              text = stringResource(R.string.settings_prefix_title),
              style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
            )
            Text(
              text = stringResource(R.string.settings_prefix_desc),
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedTextField(
              value = prefixInput,
              onValueChange = {
                prefixInput = it
                viewModel.updateOutputPrefix(it)
              },
              singleLine = true,
              modifier = Modifier
                .fillMaxWidth()
                .testTag("output_prefix_input"),
              shape = RoundedCornerShape(12.dp),
              leadingIcon = {
                Icon(imageVector = Icons.Default.TextFields, contentDescription = null)
              }
            )
          }

          // Cache Cleanup
          Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Column(modifier = Modifier.weight(1f)) {
                Text(
                  text = stringResource(R.string.settings_temp_cache_title),
                  style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
                )
                Text(
                  text = "${stringResource(R.string.settings_temp_cache_desc)} (${FileFormatters.formatBytes(cacheSizeBytes)})",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant
                )
              }

              OutlinedButton(
                onClick = {
                  viewModel.clearTempCache { freed ->
                    scope.launch {
                      snackbarHostState.showSnackbar(
                        context.getString(R.string.settings_temp_cache_cleared, FileFormatters.formatBytes(freed))
                      )
                    }
                  }
                },
                modifier = Modifier.testTag("clear_temp_cache_button"),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.outlinedButtonColors()
              ) {
                Icon(
                  imageVector = Icons.Default.CleaningServices,
                  contentDescription = null,
                  modifier = Modifier.padding(end = 4.dp)
                )
                Text(stringResource(R.string.settings_temp_cache_clear))
              }
            }
          }
        }
      }
    }

    // 4. Privacy & Limits
    item {
      SettingsSectionCard(
        title = stringResource(R.string.settings_privacy_title),
        icon = Icons.Default.Security
      ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
          Text(
            text = stringResource(R.string.settings_privacy_desc),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
          )
          Text(
            text = stringResource(R.string.settings_help_limits),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Medium
          )
          Text(
            text = stringResource(R.string.settings_help_background_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
      }
    }

    // 5. About & Licenses
    item {
      SettingsSectionCard(
        title = stringResource(R.string.settings_section_about),
        icon = Icons.Default.Info
      ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
          Text(
            text = "TOOLSNEST",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.primary
          )
          Text(
            text = stringResource(R.string.settings_about_version),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
          Text(
            text = stringResource(R.string.settings_about_app_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
          Text(
            text = stringResource(R.string.settings_licenses_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
      }
    }

    // 6. Settings Extensions (from later phases)
    items(settingsExtensions.size) { index ->
      settingsExtensions[index]()
    }

    item {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(top = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
      ) {
        Text(
          text = stringResource(R.string.app_name),
          style = MaterialTheme.typography.labelMedium,
          fontWeight = FontWeight.SemiBold,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
          text = stringResource(
            R.string.settings_app_version,
            com.example.BuildConfig.VERSION_NAME
          ),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }
    }

    item {
      SnackbarHost(hostState = snackbarHostState)
    }
  }
}

@Composable
private fun SettingsSectionCard(
  title: String,
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  content: @Composable () -> Unit
) {
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
          imageVector = icon,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.primary
        )
        Text(
          text = title,
          style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
          color = MaterialTheme.colorScheme.onSurface
        )
      }
      content()
    }
  }
}

@Composable
private fun ThemeOptionRow(
  title: String,
  selected: Boolean,
  onClick: () -> Unit
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .clickable(onClick = onClick)
      .padding(vertical = 4.dp),
    verticalAlignment = Alignment.CenterVertically
  ) {
    RadioButton(
      selected = selected,
      onClick = onClick
    )
    Text(
      text = title,
      style = MaterialTheme.typography.bodyMedium,
      modifier = Modifier.padding(start = 8.dp)
    )
  }
}

@Composable
private fun LanguageOptionRow(
  title: String,
  selected: Boolean,
  onClick: () -> Unit
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .clickable(onClick = onClick)
      .padding(vertical = 4.dp),
    verticalAlignment = Alignment.CenterVertically
  ) {
    RadioButton(
      selected = selected,
      onClick = onClick
    )
    Text(
      text = title,
      style = MaterialTheme.typography.bodyMedium,
      modifier = Modifier.padding(start = 8.dp)
    )
  }
}
