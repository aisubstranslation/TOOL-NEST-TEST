package com.example.pdf

sealed class PageRangeResult {
  data class Success(
    val pageIndices: List<Int>, // 0-based indices for PDF operations
    val pageNumbers: List<Int>, // 1-based human numbers
    val duplicatesRemoved: Int = 0
  ) : PageRangeResult()

  data class Error(val message: String) : PageRangeResult()
}

object PageRangeParser {

  /**
   * Parses page range expressions like "1-3, 5, 7-9".
   *
   * @param input Raw user string
   * @param totalPages Total pages available in the target PDF document (1-based)
   * @param keepOrderAndDuplicates If true, keeps user input order without de-duplicating; if false, de-duplicates
   */
  fun parse(
    input: String,
    totalPages: Int,
    keepOrderAndDuplicates: Boolean = false
  ): PageRangeResult {
    if (totalPages <= 0) {
      return PageRangeResult.Error("Document has no pages.")
    }
    val trimmed = input.trim()
    if (trimmed.isEmpty()) {
      return PageRangeResult.Error("Page range cannot be empty.")
    }

    val tokens = trimmed.split(',').map { it.trim() }.filter { it.isNotEmpty() }
    if (tokens.isEmpty()) {
      return PageRangeResult.Error("Please enter valid page numbers or ranges.")
    }

    val parsedNumbers = mutableListOf<Int>()

    for (token in tokens) {
      if (token.contains('-')) {
        val parts = token.split('-').map { it.trim() }
        if (parts.size != 2 || parts[0].isEmpty() || parts[1].isEmpty()) {
          return PageRangeResult.Error("Invalid range format: '$token'. Expected format: start-end (e.g. 1-5).")
        }
        val start = parts[0].toIntOrNull()
          ?: return PageRangeResult.Error("Invalid page number in range: '${parts[0]}'.")
        val end = parts[1].toIntOrNull()
          ?: return PageRangeResult.Error("Invalid page number in range: '${parts[1]}'.")

        if (start <= 0 || end <= 0) {
          return PageRangeResult.Error("Page numbers must be 1 or greater (found $start-$end).")
        }
        if (start > end) {
          return PageRangeResult.Error("Reversed range '$token': start page ($start) cannot be greater than end page ($end).")
        }
        if (end > totalPages) {
          return PageRangeResult.Error("Page $end in range '$token' is out of bounds (document has $totalPages pages).")
        }

        for (p in start..end) {
          parsedNumbers.add(p)
        }
      } else {
        val page = token.toIntOrNull()
          ?: return PageRangeResult.Error("Invalid page number: '$token'.")
        if (page <= 0) {
          return PageRangeResult.Error("Page number must be 1 or greater (found $page).")
        }
        if (page > totalPages) {
          return PageRangeResult.Error("Page $page is out of bounds (document has $totalPages pages).")
        }
        parsedNumbers.add(page)
      }
    }

    if (parsedNumbers.isEmpty()) {
      return PageRangeResult.Error("No valid pages selected.")
    }

    return if (keepOrderAndDuplicates) {
      PageRangeResult.Success(
        pageIndices = parsedNumbers.map { it - 1 },
        pageNumbers = parsedNumbers,
        duplicatesRemoved = 0
      )
    } else {
      val uniqueNumbers = mutableListOf<Int>()
      var dupCount = 0
      val seen = mutableSetOf<Int>()
      for (num in parsedNumbers) {
        if (seen.add(num)) {
          uniqueNumbers.add(num)
        } else {
          dupCount++
        }
      }
      PageRangeResult.Success(
        pageIndices = uniqueNumbers.map { it - 1 },
        pageNumbers = uniqueNumbers,
        duplicatesRemoved = dupCount
      )
    }
  }
}
