package com.example.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.mediaSettingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "toolsnest_media_settings")

class MediaSettingsDataStore(private val context: Context) {

  companion object {
    val KEY_DEFAULT_AUDIO_FORMAT = stringPreferencesKey("media_default_audio_format")
    val KEY_DEFAULT_AAC_BITRATE = intPreferencesKey("media_default_aac_bitrate")
    val KEY_BG_NOTIFICATIONS = booleanPreferencesKey("media_bg_notifications")
    val KEY_DELETE_AFTER_SAVE = booleanPreferencesKey("media_delete_after_save")

    const val DEFAULT_AUDIO_FORMAT = "M4A"
    const val DEFAULT_AAC_BITRATE = 128
    const val DEFAULT_BG_NOTIFICATIONS = true
    const val DEFAULT_DELETE_AFTER_SAVE = true
  }

  val defaultAudioFormatFlow: Flow<String> = context.mediaSettingsDataStore.data.map { prefs ->
    prefs[KEY_DEFAULT_AUDIO_FORMAT] ?: DEFAULT_AUDIO_FORMAT
  }

  val defaultAacBitrateFlow: Flow<Int> = context.mediaSettingsDataStore.data.map { prefs ->
    prefs[KEY_DEFAULT_AAC_BITRATE] ?: DEFAULT_AAC_BITRATE
  }

  val bgNotificationsFlow: Flow<Boolean> = context.mediaSettingsDataStore.data.map { prefs ->
    prefs[KEY_BG_NOTIFICATIONS] ?: DEFAULT_BG_NOTIFICATIONS
  }

  val deleteAfterSaveFlow: Flow<Boolean> = context.mediaSettingsDataStore.data.map { prefs ->
    prefs[KEY_DELETE_AFTER_SAVE] ?: DEFAULT_DELETE_AFTER_SAVE
  }

  suspend fun setDefaultAudioFormat(format: String) {
    context.mediaSettingsDataStore.edit { prefs ->
      prefs[KEY_DEFAULT_AUDIO_FORMAT] = format
    }
  }

  suspend fun setDefaultAacBitrate(bitrateKbps: Int) {
    context.mediaSettingsDataStore.edit { prefs ->
      prefs[KEY_DEFAULT_AAC_BITRATE] = bitrateKbps
    }
  }

  suspend fun setBgNotifications(enabled: Boolean) {
    context.mediaSettingsDataStore.edit { prefs ->
      prefs[KEY_BG_NOTIFICATIONS] = enabled
    }
  }

  suspend fun setDeleteAfterSave(enabled: Boolean) {
    context.mediaSettingsDataStore.edit { prefs ->
      prefs[KEY_DELETE_AFTER_SAVE] = enabled
    }
  }
}
