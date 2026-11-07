package com.example.model

import androidx.annotation.StringRes
import com.example.R

enum class Category(
  @StringRes val titleRes: Int
) {
  IMAGE(R.string.category_image),
  FILES(R.string.category_files),
  PDF(R.string.category_pdf),
  VIDEO_AUDIO(R.string.category_video_audio)
}
