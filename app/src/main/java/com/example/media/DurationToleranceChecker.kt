package com.example.media

import kotlin.math.abs

object DurationToleranceChecker {

  /**
   * Tolerance rule: max(300 ms, 1% of expected duration).
   */
  fun calculateToleranceMs(expectedDurationMs: Long): Long {
    val onePercent = (expectedDurationMs * 0.01).toLong()
    return maxOf(300L, onePercent)
  }

  fun isWithinTolerance(actualDurationMs: Long, expectedDurationMs: Long): Boolean {
    if (expectedDurationMs <= 0L) {
      return actualDurationMs > 0L
    }
    val tolerance = calculateToleranceMs(expectedDurationMs)
    return abs(actualDurationMs - expectedDurationMs) <= tolerance
  }
}
