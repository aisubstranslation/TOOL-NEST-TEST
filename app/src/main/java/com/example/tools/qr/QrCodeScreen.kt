package com.example.tools.qr

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.example.R
import com.example.ToolsNestApp
import com.example.file.FileLayer
import com.example.tools.common.ToolScaffold
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

enum class QrType {
  TEXT,
  URL,
  WIFI,
  CONTACT,
  EMAIL,
  PHONE
}

data class QrPalette(val name: String, val fg: Int, val bg: Int)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QrCodeScreen(
  onBack: () -> Unit,
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val pdfSettings = remember { ToolsNestApp.instance.pdfSettingsDataStore }

  val defaultQrSizePref by pdfSettings.defaultQrSizeFlow.collectAsState(initial = 1024)
  val defaultQrEcPref by pdfSettings.defaultQrEcFlow.collectAsState(initial = "M")

  var selectedType by rememberSaveable { mutableStateOf(QrType.TEXT) }

  // Form states
  var textInput by rememberSaveable { mutableStateOf("Welcome to TOOLSNEST") }
  var urlInput by rememberSaveable { mutableStateOf("toolsnest.app") }
  var wifiSsid by rememberSaveable { mutableStateOf("MyHomeNetwork") }
  var wifiPass by rememberSaveable { mutableStateOf("") }
  var wifiSecurity by rememberSaveable { mutableStateOf("WPA") }
  var wifiHidden by rememberSaveable { mutableStateOf(false) }

  var contactName by rememberSaveable { mutableStateOf("John Doe") }
  var contactPhone by rememberSaveable { mutableStateOf("+1234567890") }
  var contactEmail by rememberSaveable { mutableStateOf("john@example.com") }
  var contactOrg by rememberSaveable { mutableStateOf("Acme Corp") }
  var contactUrl by rememberSaveable { mutableStateOf("example.com") }

  var emailRecipient by rememberSaveable { mutableStateOf("contact@example.com") }
  var emailSubject by rememberSaveable { mutableStateOf("Hello") }
  var emailBody by rememberSaveable { mutableStateOf("Hi there,") }

  var phoneInput by rememberSaveable { mutableStateOf("+1234567890") }

  // Style states
  var qrSizePx by rememberSaveable { mutableIntStateOf(defaultQrSizePref) }
  var ecLevelStr by rememberSaveable { mutableStateOf(defaultQrEcPref) }

  val palettes = remember {
    listOf(
      QrPalette("Classic", Color.BLACK, Color.WHITE),
      QrPalette("Navy", Color.parseColor("#0D47A1"), Color.WHITE),
      QrPalette("Forest", Color.parseColor("#1B5E20"), Color.WHITE),
      QrPalette("Dark Purple", Color.parseColor("#4A148C"), Color.parseColor("#F3E5F5")),
      QrPalette("Inverted", Color.WHITE, Color.BLACK)
    )
  }
  var selectedPaletteIndex by rememberSaveable { mutableIntStateOf(0) }

  // Result state
  var qrResult by remember { mutableStateOf<QrGenerationResult?>(null) }
  var generationError by remember { mutableStateOf<String?>(null) }
  var isUrlPrepended by rememberSaveable { mutableStateOf(false) }

  // Build payload
  val currentPayload = remember(
    selectedType, textInput, urlInput, wifiSsid, wifiPass, wifiSecurity, wifiHidden,
    contactName, contactPhone, contactEmail, contactOrg, contactUrl,
    emailRecipient, emailSubject, emailBody, phoneInput
  ) {
    when (selectedType) {
      QrType.TEXT -> textInput
      QrType.URL -> {
        val (p, prepended) = QrPayloadBuilders.buildUrlPayload(urlInput)
        isUrlPrepended = prepended
        p
      }
      QrType.WIFI -> QrPayloadBuilders.buildWifiPayload(wifiSsid, wifiPass, wifiSecurity, wifiHidden)
      QrType.CONTACT -> QrPayloadBuilders.buildVCardPayload(contactName, contactPhone, contactEmail, contactOrg, contactUrl)
      QrType.EMAIL -> QrPayloadBuilders.buildEmailPayload(emailRecipient, emailSubject, emailBody)
      QrType.PHONE -> QrPayloadBuilders.buildPhonePayload(phoneInput)
    }
  }

  // Error correction mapping
  val currentEcLevel = remember(ecLevelStr) {
    when (ecLevelStr) {
      "L" -> ErrorCorrectionLevel.L
      "Q" -> ErrorCorrectionLevel.Q
      "H" -> ErrorCorrectionLevel.H
      else -> ErrorCorrectionLevel.M
    }
  }

  // Active palette
  val currentPalette = palettes[selectedPaletteIndex.coerceIn(0, palettes.lastIndex)]

  // Generate on input change
  LaunchedEffect(currentPayload, qrSizePx, currentEcLevel, currentPalette) {
    if (currentPayload.isBlank()) {
      qrResult = null
      generationError = null
      return@LaunchedEffect
    }
    withContext(Dispatchers.Default) {
      try {
        val res = QrCodeGenerator.generateQr(
          payload = currentPayload,
          targetDimensionPx = qrSizePx,
          ecLevel = currentEcLevel,
          marginModules = 4,
          foregroundColor = currentPalette.fg,
          backgroundColor = currentPalette.bg
        )
        qrResult = res
        generationError = null
      } catch (e: Exception) {
        generationError = e.message ?: "Failed to generate QR code"
        qrResult = null
      }
    }
  }

  // SAF Save Launcher
  val savePngLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.CreateDocument("image/png")
  ) { uri ->
    if (uri != null && qrResult != null) {
      scope.launch(Dispatchers.IO) {
        try {
          context.contentResolver.openOutputStream(uri)?.use { out ->
            qrResult!!.bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
          }
          recordHistory(selectedType)
          withContext(Dispatchers.Main) {
            Toast.makeText(context, context.getString(R.string.tool9_save_success), Toast.LENGTH_SHORT).show()
          }
        } catch (_: Exception) {
          withContext(Dispatchers.Main) {
            Toast.makeText(context, "Failed to save QR code.", Toast.LENGTH_SHORT).show()
          }
        }
      }
    }
  }

  ToolScaffold(
    title = stringResource(R.string.tool9_name),
    onBack = onBack,
    modifier = modifier
  ) { padding ->
    LazyColumn(
      modifier = Modifier
        .fillMaxSize()
        .padding(padding)
        .testTag("tool_screen_9"),
      contentPadding = PaddingValues(16.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
      // 1. Type selector chips
      item {
        val typeScroll = rememberScrollState()
        Row(
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(typeScroll)
        ) {
          QrType.entries.forEach { type ->
            val label = when (type) {
              QrType.TEXT -> stringResource(R.string.tool9_type_text)
              QrType.URL -> stringResource(R.string.tool9_type_url)
              QrType.WIFI -> stringResource(R.string.tool9_type_wifi)
              QrType.CONTACT -> stringResource(R.string.tool9_type_contact)
              QrType.EMAIL -> stringResource(R.string.tool9_type_email)
              QrType.PHONE -> stringResource(R.string.tool9_type_phone)
            }
            FilterChip(
              selected = selectedType == type,
              onClick = { selectedType = type },
              label = { Text(label) },
              modifier = Modifier.testTag("qr_type_${type.name.lowercase()}")
            )
          }
        }
      }

      // 2. Input Form Card
      item {
        Card(
          shape = RoundedCornerShape(16.dp),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
          elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
          modifier = Modifier.fillMaxWidth()
        ) {
          Column(
            modifier = Modifier
              .fillMaxWidth()
              .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
          ) {
            when (selectedType) {
              QrType.TEXT -> {
                OutlinedTextField(
                  value = textInput,
                  onValueChange = { textInput = it },
                  label = { Text(stringResource(R.string.tool9_input_text_label)) },
                  modifier = Modifier
                    .fillMaxWidth()
                    .testTag("input_qr_text"),
                  maxLines = 5
                )
              }
              QrType.URL -> {
                OutlinedTextField(
                  value = urlInput,
                  onValueChange = { urlInput = it },
                  label = { Text(stringResource(R.string.tool9_input_url_label)) },
                  modifier = Modifier
                    .fillMaxWidth()
                    .testTag("input_qr_url"),
                  singleLine = true
                )
                if (isUrlPrepended) {
                  Text(
                    text = stringResource(R.string.tool9_url_prepended_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                  )
                }
              }
              QrType.WIFI -> {
                OutlinedTextField(
                  value = wifiSsid,
                  onValueChange = { wifiSsid = it },
                  label = { Text(stringResource(R.string.tool9_wifi_ssid_label)) },
                  modifier = Modifier
                    .fillMaxWidth()
                    .testTag("input_wifi_ssid"),
                  singleLine = true
                )
                OutlinedTextField(
                  value = wifiPass,
                  onValueChange = { wifiPass = it },
                  label = { Text(stringResource(R.string.tool9_wifi_pass_label)) },
                  modifier = Modifier
                    .fillMaxWidth()
                    .testTag("input_wifi_pass"),
                  singleLine = true
                )
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  Text(stringResource(R.string.tool9_wifi_sec_label), style = MaterialTheme.typography.bodyMedium)
                  Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf("WPA", "WEP", "None").forEach { sec ->
                      FilterChip(
                        selected = wifiSecurity == sec,
                        onClick = { wifiSecurity = sec },
                        label = { Text(sec) }
                      )
                    }
                  }
                }
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  Text(stringResource(R.string.tool9_wifi_hidden_label), style = MaterialTheme.typography.bodyMedium)
                  Switch(checked = wifiHidden, onCheckedChange = { wifiHidden = it })
                }
              }
              QrType.CONTACT -> {
                OutlinedTextField(
                  value = contactName,
                  onValueChange = { contactName = it },
                  label = { Text(stringResource(R.string.tool9_contact_name_label)) },
                  modifier = Modifier.fillMaxWidth(),
                  singleLine = true
                )
                OutlinedTextField(
                  value = contactPhone,
                  onValueChange = { contactPhone = it },
                  label = { Text(stringResource(R.string.tool9_contact_phone_label)) },
                  modifier = Modifier.fillMaxWidth(),
                  singleLine = true
                )
                OutlinedTextField(
                  value = contactEmail,
                  onValueChange = { contactEmail = it },
                  label = { Text(stringResource(R.string.tool9_contact_email_label)) },
                  modifier = Modifier.fillMaxWidth(),
                  singleLine = true
                )
                OutlinedTextField(
                  value = contactOrg,
                  onValueChange = { contactOrg = it },
                  label = { Text(stringResource(R.string.tool9_contact_org_label)) },
                  modifier = Modifier.fillMaxWidth(),
                  singleLine = true
                )
                OutlinedTextField(
                  value = contactUrl,
                  onValueChange = { contactUrl = it },
                  label = { Text(stringResource(R.string.tool9_contact_url_label)) },
                  modifier = Modifier.fillMaxWidth(),
                  singleLine = true
                )
              }
              QrType.EMAIL -> {
                OutlinedTextField(
                  value = emailRecipient,
                  onValueChange = { emailRecipient = it },
                  label = { Text(stringResource(R.string.tool9_email_to_label)) },
                  modifier = Modifier.fillMaxWidth(),
                  singleLine = true
                )
                OutlinedTextField(
                  value = emailSubject,
                  onValueChange = { emailSubject = it },
                  label = { Text(stringResource(R.string.tool9_email_subj_label)) },
                  modifier = Modifier.fillMaxWidth(),
                  singleLine = true
                )
                OutlinedTextField(
                  value = emailBody,
                  onValueChange = { emailBody = it },
                  label = { Text(stringResource(R.string.tool9_email_body_label)) },
                  modifier = Modifier.fillMaxWidth(),
                  maxLines = 3
                )
              }
              QrType.PHONE -> {
                OutlinedTextField(
                  value = phoneInput,
                  onValueChange = { phoneInput = it },
                  label = { Text(stringResource(R.string.tool9_phone_num_label)) },
                  modifier = Modifier.fillMaxWidth(),
                  singleLine = true
                )
              }
            }

            // Privacy Notice for sensitive types
            if (selectedType == QrType.WIFI || selectedType == QrType.CONTACT) {
              Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
              ) {
                Text(
                  text = stringResource(R.string.tool9_privacy_notice),
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                  modifier = Modifier.padding(10.dp)
                )
              }
            }
          }
        }
      }

      // 3. Settings Card (Size, EC, Palette)
      item {
        Card(
          shape = RoundedCornerShape(16.dp),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
          elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
          modifier = Modifier.fillMaxWidth()
        ) {
          Column(
            modifier = Modifier
              .fillMaxWidth()
              .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
          ) {
            // Resolution chips
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Text(stringResource(R.string.tool9_size_label), style = MaterialTheme.typography.bodyMedium)
              Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf(512, 1024, 2048).forEach { sz ->
                  FilterChip(
                    selected = qrSizePx == sz,
                    onClick = { qrSizePx = sz },
                    label = { Text("${sz}px") }
                  )
                }
              }
            }

            // Error Correction chips
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Text(stringResource(R.string.tool9_ec_label), style = MaterialTheme.typography.bodyMedium)
              Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf("L", "M", "Q", "H").forEach { ec ->
                  FilterChip(
                    selected = ecLevelStr == ec,
                    onClick = { ecLevelStr = ec },
                    label = { Text(ec) }
                  )
                }
              }
            }

            // Color palette selector
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
              Text(stringResource(R.string.tool9_color_palette), style = MaterialTheme.typography.bodyMedium)
              Row(
                modifier = Modifier
                  .fillMaxWidth()
                  .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
              ) {
                palettes.forEachIndexed { idx, p ->
                  FilterChip(
                    selected = selectedPaletteIndex == idx,
                    onClick = { selectedPaletteIndex = idx },
                    label = {
                      Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                      ) {
                        Box(
                          modifier = Modifier
                            .size(14.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(androidx.compose.ui.graphics.Color(p.fg))
                            .border(1.dp, androidx.compose.ui.graphics.Color(p.bg), RoundedCornerShape(3.dp))
                        )
                        Text(p.name)
                      }
                    }
                  )
                }
              }
            }
          }
        }
      }

      // 4. Live Preview & Result
      if (generationError != null) {
        item {
          Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
          ) {
            Row(
              modifier = Modifier.padding(16.dp),
              horizontalArrangement = Arrangement.spacedBy(10.dp),
              verticalAlignment = Alignment.CenterVertically
            ) {
              Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error)
              Text(generationError!!, color = MaterialTheme.colorScheme.onErrorContainer, style = MaterialTheme.typography.bodyMedium)
            }
          }
        }
      } else if (qrResult != null) {
        val result = qrResult!!
        item {
          Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            modifier = Modifier.fillMaxWidth()
          ) {
            Column(
              modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
              horizontalAlignment = Alignment.CenterHorizontally,
              verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
              // QR Bitmap Display with white surrounding frame
              Box(
                modifier = Modifier
                  .size(240.dp)
                  .clip(RoundedCornerShape(12.dp))
                  .background(androidx.compose.ui.graphics.Color.White)
                  .padding(8.dp),
                contentAlignment = Alignment.Center
              ) {
                Image(
                  bitmap = result.bitmap.asImageBitmap(),
                  contentDescription = "Generated QR Code",
                  modifier = Modifier.fillMaxSize()
                )
              }

              // Verification badge
              Surface(
                shape = RoundedCornerShape(20.dp),
                color = if (result.isVerified) androidx.compose.ui.graphics.Color(0xFFE8F5E9) else androidx.compose.ui.graphics.Color(0xFFFFEBEE),
                modifier = Modifier.padding(top = 4.dp)
              ) {
                Row(
                  modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                  Icon(
                    imageVector = if (result.isVerified) Icons.Default.CheckCircle else Icons.Default.ErrorOutline,
                    contentDescription = null,
                    tint = if (result.isVerified) androidx.compose.ui.graphics.Color(0xFF2E7D32) else androidx.compose.ui.graphics.Color(0xFFC62828),
                    modifier = Modifier.size(16.dp)
                  )
                  Text(
                    text = if (result.isVerified) stringResource(R.string.tool9_verified_badge) else stringResource(R.string.tool9_unverified_badge),
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                    color = if (result.isVerified) androidx.compose.ui.graphics.Color(0xFF2E7D32) else androidx.compose.ui.graphics.Color(0xFFC62828)
                  )
                }
              }

              // Unverified Warning Banner
              if (!result.isVerified) {
                Card(
                  colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)),
                  shape = RoundedCornerShape(8.dp),
                  modifier = Modifier.fillMaxWidth()
                ) {
                  Row(
                    modifier = Modifier.padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                  ) {
                    Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                    Text(stringResource(R.string.tool9_warning_unverified_banner), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
                  }
                }
              }

              // Inverted Warning
              if (result.isInvertedWarning) {
                Row(
                  modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                  horizontalArrangement = Arrangement.spacedBy(8.dp),
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  Icon(Icons.Default.Warning, contentDescription = null, tint = androidx.compose.ui.graphics.Color(0xFFF57C00), modifier = Modifier.size(16.dp))
                  Text(stringResource(R.string.tool9_warning_inverted), style = MaterialTheme.typography.bodySmall, color = androidx.compose.ui.graphics.Color(0xFFE65100))
                }
              }

              // Contrast Warning
              if (result.isContrastWarning) {
                Row(
                  modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                  horizontalArrangement = Arrangement.spacedBy(8.dp),
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(16.dp))
                  Text(stringResource(R.string.tool9_warning_contrast), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary)
                }
              }

              // Actions (Always available)
              Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
              ) {
                  Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                  ) {
                    Button(
                      onClick = {
                        val fileName = "TOOLSNEST_QR_${selectedType.name}_${System.currentTimeMillis()}.png"
                        savePngLauncher.launch(fileName)
                      },
                      modifier = Modifier
                        .weight(1f)
                        .testTag("action_save_qr_png"),
                      shape = RoundedCornerShape(10.dp)
                    ) {
                      Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                      Spacer(Modifier.size(6.dp))
                      Text(stringResource(R.string.tool9_btn_save_png))
                    }

                    OutlinedButton(
                      onClick = {
                        scope.launch(Dispatchers.IO) {
                          val tempFile = File(FileLayer.getTempFolder(context), "qr_gallery_${System.currentTimeMillis()}.png")
                          FileOutputStream(tempFile).use { fos ->
                            result.bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos)
                          }
                          val displayName = "TOOLSNEST_QR_${selectedType.name}_${System.currentTimeMillis()}.png"
                          val savedUri = FileLayer.saveImageToMediaStore(context, tempFile, displayName, "image/png")
                          tempFile.delete()
                          recordHistory(selectedType)
                          withContext(Dispatchers.Main) {
                            if (savedUri != null) {
                              Toast.makeText(context, context.getString(R.string.tool9_saved_gallery), Toast.LENGTH_SHORT).show()
                            } else {
                              Toast.makeText(context, "Saving to Gallery requires Android 10+. Please use Save PNG.", Toast.LENGTH_LONG).show()
                            }
                          }
                        }
                      },
                      modifier = Modifier
                        .weight(1f)
                        .testTag("action_save_qr_gallery"),
                      shape = RoundedCornerShape(10.dp)
                    ) {
                      Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(16.dp))
                      Spacer(Modifier.size(6.dp))
                      Text(stringResource(R.string.tool9_btn_save_gallery))
                    }
                  }

                  Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                  ) {
                    OutlinedButton(
                      onClick = {
                        scope.launch(Dispatchers.IO) {
                          val tempFile = File(FileLayer.getTempFolder(context), "qr_share_${System.currentTimeMillis()}.png")
                          FileOutputStream(tempFile).use { fos ->
                            result.bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos)
                          }
                          recordHistory(selectedType)
                          withContext(Dispatchers.Main) {
                            FileLayer.shareFileAndNotify(context, tempFile, "image/png", context.getString(R.string.tool9_btn_share))
                          }
                        }
                      },
                      modifier = Modifier
                        .weight(1f)
                        .testTag("action_share_qr"),
                      shape = RoundedCornerShape(10.dp)
                    ) {
                      Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                      Spacer(Modifier.size(6.dp))
                      Text(stringResource(R.string.tool9_btn_share))
                    }

                    OutlinedButton(
                      onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("QR Content", currentPayload))
                        Toast.makeText(context, context.getString(R.string.tool9_text_copied), Toast.LENGTH_SHORT).show()
                      },
                      modifier = Modifier
                        .weight(1f)
                        .testTag("action_copy_qr_text"),
                      shape = RoundedCornerShape(10.dp)
                    ) {
                      Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                      Spacer(Modifier.size(6.dp))
                      Text(stringResource(R.string.tool9_btn_copy_text))
                    }
                  }
                }
              }
            }
          }
        }
      }
    }
}

private fun recordHistory(type: QrType) {
  // CRITICAL PRIVACY: Store only the type name (e.g. "QR - Wi-Fi"), NEVER passwords or private data
  ToolsNestApp.instance.appScope.launch(Dispatchers.IO) {
    try {
      ToolsNestApp.instance.historyRepository.insert(
        com.example.data.db.HistoryEntity(
          toolId = 9,
          inputFileName = "QR - ${type.name}",
          outputFileName = "qr_code.png",
          status = "SUCCESS",
          outputSizeBytes = 0L
        )
      )
    } catch (_: Exception) {}
  }
}
