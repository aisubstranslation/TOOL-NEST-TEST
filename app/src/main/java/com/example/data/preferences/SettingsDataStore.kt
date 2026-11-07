package com.example.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.ui.theme.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "toolsnest_settings")

class SettingsDataStore(private val context: Context) {

  companion object {
    val KEY_THEME_MODE = stringPreferencesKey("theme_mode")
    val KEY_LANGUAGE_CODE = stringPreferencesKey("language_code")
    val KEY_OUTPUT_PREFIX = stringPreferencesKey("output_naming_prefix")

    const val DEFAULT_PREFIX = "TOOLSNEST_"
    const val DEFAULT_LANGUAGE = "system"
  }

  val themeModeFlow: Flow<ThemeMode> = context.dataStore.data.map { prefs ->
    when (prefs[KEY_THEME_MODE]) {
      ThemeMode.LIGHT.name -> ThemeMode.LIGHT
      ThemeMode.DARK.name -> ThemeMode.DARK
      else -> ThemeMode.SYSTEM
    }
  }

  val languageCodeFlow: Flow<String> = context.dataStore.data.map { prefs ->
    prefs[KEY_LANGUAGE_CODE] ?: DEFAULT_LANGUAGE
  }

  val outputPrefixFlow: Flow<String> = context.dataStore.data.map { prefs ->
    prefs[KEY_OUTPUT_PREFIX] ?: DEFAULT_PREFIX
  }

  suspend fun setThemeMode(mode: ThemeMode) {
    context.dataStore.edit { prefs ->
      prefs[KEY_THEME_MODE] = mode.name
    }
  }

  suspend fun setLanguageCode(languageCode: String) {
    context.dataStore.edit { prefs ->
      prefs[KEY_LANGUAGE_CODE] = languageCode
    }
  }

  suspend fun setOutputPrefix(prefix: String) {
    context.dataStore.edit { prefs ->
      prefs[KEY_OUTPUT_PREFIX] = prefix.trim()
    }
  }
}
