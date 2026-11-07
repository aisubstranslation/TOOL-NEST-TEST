package com.example.media

object StorageRequirementCalculator {

  const val SAFETY_MARGIN_BYTES = 300L * 1024L * 1024L // 300 MB margin

  /**
   * Required free storage = estimated output upper bound + 300 MB margin.
   */
  fun calculateRequiredFreeSpace(estimatedOutputUpperBoundBytes: Long): Long {
    return estimatedOutputUpperBoundBytes.coerceAtLeast(0L) + SAFETY_MARGIN_BYTES
  }

  fun hasEnoughFreeSpace(availableFreeBytes: Long, estimatedOutputUpperBoundBytes: Long): Boolean {
    return availableFreeBytes >= calculateRequiredFreeSpace(estimatedOutputUpperBoundBytes)
  }
}
