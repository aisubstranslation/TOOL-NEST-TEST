package com.example.file

import java.util.Locale

object FileFormatters {

  /**
   * Formats raw byte count into human-readable B, KB, MB, or GB.
   * Pure Kotlin, deterministic, handles zero and extreme long values.
   */
  fun formatBytes(bytes: Long): String {
    if (bytes <= 0L) return "0 B"
    if (bytes < 1024L) return "$bytes B"

    val kb = bytes / 1024.0
    if (kb < 1024.0) {
      return String.format(Locale.US, "%.1f KB", kb)
    }

    val mb = kb / 1024.0
    if (mb < 1024.0) {
      return String.format(Locale.US, "%.1f MB", mb)
    }

    val gb = mb / 1024.0
    return String.format(Locale.US, "%.2f GB", gb)
  }

  /**
   * Calculates size difference percentage with division-by-zero protection
   * and a clear "larger than original" indicator.
   */
  fun formatPercentageChange(originalBytes: Long, newBytes: Long): String {
    if (originalBytes <= 0L) {
      return "0%"
    }
    if (newBytes == originalBytes) {
      return "0%"
    }

    return if (newBytes < originalBytes) {
      val saved = originalBytes - newBytes
      val percent = (saved.toDouble() / originalBytes.toDouble()) * 100.0
      String.format(Locale.US, "-%.1f%%", percent)
    } else {
      val increased = newBytes - originalBytes
      val percent = (increased.toDouble() / originalBytes.toDouble()) * 100.0
      String.format(Locale.US, "+%.1f%% (Larger)", percent)
    }
  }
}
