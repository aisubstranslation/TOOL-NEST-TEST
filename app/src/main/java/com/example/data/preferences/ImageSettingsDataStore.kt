package com.example.data.preferences

import android.content.Context
import android.graphics.Color
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.engine.OutputFormat
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.imageSettingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "toolsnest_image_settings")

class ImageSettingsDataStore(private val context: Context) {

  companion object {
    val KEY_DEFAULT_QUALITY = intPreferencesKey("default_image_quality")
    val KEY_DEFAULT_FORMAT = stringPreferencesKey("default_image_format")
    val KEY_DEFAULT_BG_COLOR = intPreferencesKey("default_bg_color")

    const val DEFAULT_QUALITY = 85
    val DEFAULT_FORMAT = OutputFormat.JPEG
    const val DEFAULT_BG_COLOR = Color.WHITE
  }

  val defaultQualityFlow: Flow<Int> = context.imageSettingsDataStore.data.map { prefs ->
    prefs[KEY_DEFAULT_QUALITY] ?: DEFAULT_QUALITY
  }

  val defaultFormatFlow: Flow<OutputFormat> = context.imageSettingsDataStore.data.map { prefs ->
    when (prefs[KEY_DEFAULT_FORMAT]) {
      OutputFormat.PNG.name -> OutputFormat.PNG
      OutputFormat.WEBP.name -> OutputFormat.WEBP
      else -> OutputFormat.JPEG
    }
  }

  val defaultBgColorFlow: Flow<Int> = context.imageSettingsDataStore.data.map { prefs ->
    prefs[KEY_DEFAULT_BG_COLOR] ?: DEFAULT_BG_COLOR
  }

  suspend fun setDefaultQuality(quality: Int) {
    context.imageSettingsDataStore.edit { prefs ->
      prefs[KEY_DEFAULT_QUALITY] = quality.coerceIn(10, 100)
    }
  }

  suspend fun setDefaultFormat(format: OutputFormat) {
    context.imageSettingsDataStore.edit { prefs ->
      prefs[KEY_DEFAULT_FORMAT] = format.name
    }
  }

  suspend fun setDefaultBgColor(color: Int) {
    context.imageSettingsDataStore.edit { prefs ->
      prefs[KEY_DEFAULT_BG_COLOR] = color
    }
  }
}
