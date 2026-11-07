package com.example.file

import com.example.model.Category

object FileLimits {
  const val IMAGE_MAX_BYTES: Long = 50L * 1024L * 1024L        // 50 MB
  const val IMAGE_MAX_MEGAPIXELS: Int = 48                      // 48 MP
  const val PDF_MAX_BYTES: Long = 150L * 1024L * 1024L          // 150 MB
  const val VIDEO_MAX_BYTES: Long = 1000L * 1024L * 1024L       // 1000 MB
  const val AUDIO_MAX_BYTES: Long = 500L * 1024L * 1024L        // 500 MB

  fun getLimitBytes(category: Category): Long {
    return when (category) {
      Category.IMAGE -> IMAGE_MAX_BYTES
      Category.PDF -> PDF_MAX_BYTES
      Category.VIDEO_AUDIO -> VIDEO_MAX_BYTES
      Category.FILES -> VIDEO_MAX_BYTES // Files default upper ceiling
    }
  }

  fun checkFileSizeLimit(category: Category, sizeBytes: Long): Boolean {
    if (sizeBytes <= 0L) return true
    return sizeBytes <= getLimitBytes(category)
  }
}
