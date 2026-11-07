package com.example.file

object FileNameSanitizer {

  private val RESERVED_CHARS_REGEX = Regex("[<>:\"|?*\\\\/]")
  private val CONTROL_CHARS_REGEX = Regex("[\\p{Cntrl}]")
  private const val MAX_BASE_LENGTH = 100

  /**
   * Cleans an input file name: preserves Unicode characters, strips path traversal (".."),
   * separators, control codes, and filesystem reserved symbols. Prepends prefix cleanly.
   */
  fun sanitizeFileName(
    rawName: String,
    prefix: String = "",
    extensionOverride: String? = null
  ): String {
    var cleaned = rawName.trim()

    // 1. Remove path separators and traversal
    cleaned = cleaned.replace("..", "_")
    cleaned = cleaned.replace('/', '_').replace('\\', '_')

    // 2. Remove reserved characters and control codes
    cleaned = RESERVED_CHARS_REGEX.replace(cleaned, "_")
    cleaned = CONTROL_CHARS_REGEX.replace(cleaned, "")

    // 3. Separate base name and extension
    val lastDotIndex = cleaned.lastIndexOf('.')
    var baseName: String
    var extension: String

    if (lastDotIndex in 1 until cleaned.length) {
      baseName = cleaned.substring(0, lastDotIndex)
      extension = cleaned.substring(lastDotIndex + 1)
    } else {
      baseName = if (cleaned.isNotEmpty()) cleaned else "file"
      extension = ""
    }

    if (extensionOverride != null) {
      extension = extensionOverride.removePrefix(".")
    }

    // Fallback if baseName was reduced to empty
    if (baseName.isBlank()) {
      baseName = "file"
    }

    // 4. Truncate base name if it exceeds limit
    if (baseName.length > MAX_BASE_LENGTH) {
      baseName = baseName.substring(0, MAX_BASE_LENGTH).trimEnd()
    }

    // 5. Apply prefix if specified and not already present
    val cleanPrefix = prefix.trim()
    if (cleanPrefix.isNotEmpty() && !baseName.startsWith(cleanPrefix)) {
      baseName = "$cleanPrefix$baseName"
    }

    return if (extension.isNotEmpty()) "$baseName.$extension" else baseName
  }

  /**
   * Generates a unique filename if the base name already exists in the given set:
   * e.g., "doc.pdf" -> "doc (1).pdf", "doc (2).pdf"
   */
  fun generateUniqueFileName(candidateName: String, existingNames: Set<String>): String {
    if (!existingNames.contains(candidateName)) {
      return candidateName
    }

    val lastDotIndex = candidateName.lastIndexOf('.')
    val base = if (lastDotIndex > 0) candidateName.substring(0, lastDotIndex) else candidateName
    val ext = if (lastDotIndex > 0) candidateName.substring(lastDotIndex) else ""

    var counter = 1
    while (true) {
      val numberedName = "$base ($counter)$ext"
      if (!existingNames.contains(numberedName)) {
        return numberedName
      }
      counter++
    }
  }
}
