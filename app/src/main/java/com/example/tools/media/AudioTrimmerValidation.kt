package com.example.tools.media

import java.util.Locale

object AudioTrimmerValidation {

  const val MIN_TRIM_DURATION_MS = 500L

  data class ValidationResult(
    val isValid: Boolean,
    val errorMessage: String? = null
  )

  /**
   * Validates audio trim boundaries.
   * Requirements:
   * - startMs >= 0
   * - endMs <= durationMs
   * - endMs > startMs
   * - (endMs - startMs) >= 500 ms
   */
  fun validateTrimRange(startMs: Long, endMs: Long, durationMs: Long): ValidationResult {
    if (startMs < 0) {
      return ValidationResult(false, "Start time cannot be negative")
    }
    if (durationMs > 0 && endMs > durationMs) {
      return ValidationResult(false, "End time exceeds audio duration")
    }
    if (endMs <= startMs) {
      return ValidationResult(false, "End time must be greater than start time")
    }
    if (endMs - startMs < MIN_TRIM_DURATION_MS) {
      return ValidationResult(false, "Selected clip must be at least 500 ms long")
    }
    return ValidationResult(true, null)
  }

  /**
   * Formats milliseconds to mm:ss.SSS (e.g. 01:23.450)
   */
  fun formatMsToTime(ms: Long): String {
    val totalMs = ms.coerceAtLeast(0L)
    val minutes = totalMs / 60_000L
    val seconds = (totalMs % 60_000L) / 1_000L
    val millis = totalMs % 1_000L
    return String.format(Locale.US, "%02d:%02d.%03d", minutes, seconds, millis)
  }

  /**
   * Parses mm:ss.SSS, mm:ss, ss.SSS, or ss into milliseconds.
   * Returns null if unparseable or negative.
   */
  fun parseTimeToMs(text: String): Long? {
    val trimmed = text.trim()
    if (trimmed.isEmpty()) return null

    return try {
      if (trimmed.contains(":")) {
        val parts = trimmed.split(":")
        if (parts.size != 2) return null
        val minutes = parts[0].toLongOrNull() ?: return null
        val secParts = parts[1].split(".")
        val seconds = secParts[0].toLongOrNull() ?: return null
        val millis = if (secParts.size > 1) {
          secParts[1].padEnd(3, '0').take(3).toLongOrNull() ?: return null
        } else 0L
        (minutes * 60_000L) + (seconds * 1_000L) + millis
      } else {
        val secParts = trimmed.split(".")
        val seconds = secParts[0].toLongOrNull() ?: return null
        val millis = if (secParts.size > 1) {
          secParts[1].padEnd(3, '0').take(3).toLongOrNull() ?: return null
        } else 0L
        (seconds * 1_000L) + millis
      }
    } catch (_: Exception) {
      null
    }
  }

  /**
   * Generates default trimmed output filename from input filename and format.
   * e.g. "my_song.mp3" -> "my_song_trimmed.m4a"
   */
  fun generateTrimmedOutputName(inputName: String, extension: String): String {
    val base = inputName.substringBeforeLast(".")
    val ext = extension.removePrefix(".")
    return "${base}_trimmed.$ext"
  }
}
