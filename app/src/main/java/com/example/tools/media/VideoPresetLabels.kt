package com.example.tools.media

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.example.R

/** Labels of the video presets in the language chosen in Settings. */
@Composable
fun VideoQualityPreset.localizedLabel(): String = stringResource(
  when (this) {
    VideoQualityPreset.HIGH -> R.string.tool18_quality_high
    VideoQualityPreset.BALANCED -> R.string.tool18_quality_balanced
    VideoQualityPreset.SMALL -> R.string.tool18_quality_small
  }
)

@Composable
fun VideoResolutionPreset.localizedLabel(): String =
  if (this == VideoResolutionPreset.ORIGINAL) stringResource(R.string.tool18_res_original) else label
