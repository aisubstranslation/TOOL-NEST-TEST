package com.example.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.tools.media.VideoQualityPreset
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.videoSettingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "toolsnest_video_settings")

class VideoSettingsDataStore(private val context: Context) {

  companion object {
    val KEY_DEFAULT_VIDEO_QUALITY = stringPreferencesKey("video_default_quality")
    val DEFAULT_VIDEO_QUALITY = VideoQualityPreset.BALANCED.name
  }

  val defaultVideoQualityFlow: Flow<VideoQualityPreset> = context.videoSettingsDataStore.data.map { prefs ->
    val name = prefs[KEY_DEFAULT_VIDEO_QUALITY] ?: DEFAULT_VIDEO_QUALITY
    runCatching { VideoQualityPreset.valueOf(name) }.getOrDefault(VideoQualityPreset.BALANCED)
  }

  suspend fun setDefaultVideoQuality(preset: VideoQualityPreset) {
    context.videoSettingsDataStore.edit { prefs ->
      prefs[KEY_DEFAULT_VIDEO_QUALITY] = preset.name
    }
  }
}
