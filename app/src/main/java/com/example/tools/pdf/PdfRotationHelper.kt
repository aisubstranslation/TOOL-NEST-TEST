package com.example.tools.pdf

object PdfRotationHelper {

  /**
   * Normalizes any integer rotation angle (positive or negative) to standard PDF rotation:
   * 0, 90, 180, or 270 degrees.
   */
  fun normalize(angle: Int): Int {
    val mod = angle % 360
    return if (mod < 0) mod + 360 else mod
  }

  /**
   * Computes the new rotation by adding delta to the current angle and normalizing.
   */
  fun applyDelta(currentAngle: Int, deltaDegrees: Int): Int {
    return normalize(currentAngle + deltaDegrees)
  }

  /**
   * Determines if the given page index (0-based) matches the selection filter:
   * "all", "odd" (1-based pages 1, 3, 5...), or "even" (1-based pages 2, 4, 6...).
   */
  fun matchesFilter(pageIndex: Int, filter: String): Boolean {
    val pageNumber = pageIndex + 1
    return when (filter.lowercase()) {
      "all" -> true
      "odd" -> pageNumber % 2 != 0
      "even" -> pageNumber % 2 == 0
      else -> false
    }
  }
}
