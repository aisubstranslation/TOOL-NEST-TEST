package com.example

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ui.LocaleDirectionHelper
import com.example.ui.LocaleHelper
import com.example.ui.MainScaffold
import com.example.ui.MainViewModel
import com.example.ui.theme.ToolsNestTheme
import com.example.util.CrashReporter

class MainActivity : ComponentActivity() {

  private val mainViewModel: MainViewModel by viewModels()

  override fun attachBaseContext(newBase: Context) {
    val lang = LocaleHelper.getSavedLanguage(newBase)
    val localizedContext = LocaleHelper.applyLocale(newBase, lang)
    super.attachBaseContext(localizedContext)
    applyOverrideConfiguration(localizedContext.resources.configuration)
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    handleIntent(intent)

    setContent {
      val themeMode by mainViewModel.themeMode.collectAsState()
      val currentSavedLang = LocaleHelper.getSavedLanguage(this@MainActivity)
      val languageCode by mainViewModel.languageCode.collectAsState(initial = currentSavedLang)
      val effectiveLang = if (languageCode.isNotBlank()) languageCode else currentSavedLang

      val systemConfig = LocalConfiguration.current
      val systemIsRtl = (systemConfig.screenLayout and Configuration.SCREENLAYOUT_LAYOUTDIR_MASK) == Configuration.SCREENLAYOUT_LAYOUTDIR_RTL
      val layoutDirection = LocaleDirectionHelper.getLayoutDirection(effectiveLang, systemIsRtl)

      var crashReport by remember { mutableStateOf(CrashReporter.getCrashReport(this@MainActivity)) }

      CompositionLocalProvider(LocalLayoutDirection provides layoutDirection) {
        ToolsNestTheme(themeMode = themeMode) {
          MainScaffold(
            viewModel = mainViewModel,
            onLanguageChanged = { langCode ->
              LocaleHelper.saveLanguage(this@MainActivity, langCode)
              recreate()
            },
            onFinishApp = { finish() }
          )

          if (crashReport != null) {
            CrashReportDialog(
              report = crashReport!!,
              onDismiss = { crashReport = null },
              onCopy = {
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("Crash Report", crashReport))
                Toast.makeText(this@MainActivity, getString(R.string.crash_report_copied), Toast.LENGTH_SHORT).show()
              },
              onDelete = {
                CrashReporter.deleteCrashReport(this@MainActivity)
                crashReport = null
              }
            )
          }
        }
      }
    }
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    setIntent(intent)
    handleIntent(intent)
  }

  private fun handleIntent(intent: Intent?) {
    if (intent?.getStringExtra("navigate_to") == "processing_center") {
      mainViewModel.navigateTo(com.example.ui.navigation.Screen.ProcessingCenter)
    }
  }
}

@Composable
private fun CrashReportDialog(
  report: String,
  onDismiss: () -> Unit,
  onCopy: () -> Unit,
  onDelete: () -> Unit
) {
  AlertDialog(
    onDismissRequest = onDismiss,
    title = {
      Text(
        text = stringResource(R.string.crash_dialog_title),
        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
      )
    },
    text = {
      Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxWidth()
      ) {
        Text(
          text = stringResource(R.string.crash_dialog_desc),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Surface(
          shape = RoundedCornerShape(8.dp),
          color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
          modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 240.dp)
        ) {
          val scrollState = rememberScrollState()
          Text(
            text = report,
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            modifier = Modifier
              .padding(10.dp)
              .verticalScroll(scrollState)
          )
        }
      }
    },
    confirmButton = {
      Button(onClick = onCopy) {
        Text(stringResource(R.string.crash_dialog_copy))
      }
    },
    dismissButton = {
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = onDelete) {
          Text(stringResource(R.string.crash_dialog_delete))
        }
        TextButton(onClick = onDismiss) {
          Text(stringResource(R.string.crash_dialog_close))
        }
      }
    }
  )
}
