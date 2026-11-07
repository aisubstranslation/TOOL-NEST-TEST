package com.example.pdf

import android.os.StatFs
import java.io.File

object PdfLimits {

  const val MAX_PDF_SIZE_BYTES: Long = 150L * 1024L * 1024L // 150 MB
  const val MAX_MERGE_COMBINED_SIZE_BYTES: Long = 300L * 1024L * 1024L // 300 MB
  const val MAX_MERGE_FILE_COUNT: Int = 20
  const val STORAGE_HEADROOM_MULTIPLIER: Double = 1.2

  /**
   * Verifies if the file size is within the 150 MB single-file limit.
   */
  fun isWithinSingleFileLimit(sizeBytes: Long): Boolean {
    return sizeBytes in 1..MAX_PDF_SIZE_BYTES
  }

  /**
   * Verifies that the cache filesystem has at least 1.2x headroom for the required payload.
   */
  fun hasSufficientStorage(targetDir: File, requiredBytes: Long): Boolean {
    return try {
      val statFs = StatFs(targetDir.absolutePath)
      val available = statFs.availableBytes
      val needed = (requiredBytes * STORAGE_HEADROOM_MULTIPLIER).toLong()
      available >= needed
    } catch (_: Exception) {
      true // Best-effort fallback if StatFs fails
    }
  }
}
