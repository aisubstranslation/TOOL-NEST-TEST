package com.example.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.pdfSettingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "toolsnest_pdf_settings")

class PdfSettingsDataStore(private val context: Context) {

  companion object {
    val KEY_DEFAULT_PAGE_SIZE = stringPreferencesKey("pdf_default_page_size")
    val KEY_DEFAULT_QR_SIZE = intPreferencesKey("qr_default_size")
    val KEY_DEFAULT_QR_EC = stringPreferencesKey("qr_default_ec_level")

    const val DEFAULT_PAGE_SIZE = "A4"
    const val DEFAULT_QR_SIZE = 1024
    const val DEFAULT_QR_EC = "M"
  }

  val defaultPageSizeFlow: Flow<String> = context.pdfSettingsDataStore.data.map { prefs ->
    prefs[KEY_DEFAULT_PAGE_SIZE] ?: DEFAULT_PAGE_SIZE
  }

  val defaultQrSizeFlow: Flow<Int> = context.pdfSettingsDataStore.data.map { prefs ->
    prefs[KEY_DEFAULT_QR_SIZE] ?: DEFAULT_QR_SIZE
  }

  val defaultQrEcFlow: Flow<String> = context.pdfSettingsDataStore.data.map { prefs ->
    prefs[KEY_DEFAULT_QR_EC] ?: DEFAULT_QR_EC
  }

  suspend fun setDefaultPageSize(pageSize: String) {
    context.pdfSettingsDataStore.edit { prefs ->
      prefs[KEY_DEFAULT_PAGE_SIZE] = pageSize
    }
  }

  suspend fun setDefaultQrSize(sizePx: Int) {
    context.pdfSettingsDataStore.edit { prefs ->
      prefs[KEY_DEFAULT_QR_SIZE] = sizePx
    }
  }

  suspend fun setDefaultQrEc(ecLevel: String) {
    context.pdfSettingsDataStore.edit { prefs ->
      prefs[KEY_DEFAULT_QR_EC] = ecLevel
    }
  }
}
