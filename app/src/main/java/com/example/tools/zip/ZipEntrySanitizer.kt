package com.example.tools.zip

object ZipEntrySanitizer {

  /**
   * Sanitizes an entry path:
   * - Normalizes backslashes to '/'
   * - Strips leading and trailing slashes
   * - Removes ".." path traversal segments
   * - Strips ASCII control characters (< 32, 127) while preserving Unicode characters
   */
  fun sanitizeEntryPath(rawPath: String): String {
    val normalized = rawPath.replace('\\', '/')
    val rawSegments = normalized.split('/')
    val cleanSegments = mutableListOf<String>()

    for (segment in rawSegments) {
      val trimmed = segment.trim()
      if (trimmed.isEmpty() || trimmed == ".") continue
      if (trimmed == "..") continue // Disallow traversal

      // Strip control chars
      val cleanSegment = trimmed.filter { ch ->
        val code = ch.code
        (code >= 32 && code != 127)
      }

      if (cleanSegment.isNotEmpty()) {
        cleanSegments.add(cleanSegment)
      }
    }

    return if (cleanSegments.isEmpty()) "file" else cleanSegments.joinToString("/")
  }

  /**
   * Resolves duplicate paths in selection order by adding deterministic suffixes:
   * "name (1).ext", "name (2).ext", etc.
   */
  fun resolveDuplicatePaths(paths: List<String>): List<String> {
    val usedCounts = mutableMapOf<String, Int>()
    val result = mutableListOf<String>()

    for (path in paths) {
      val sanitized = sanitizeEntryPath(path)
      val count = usedCounts.getOrDefault(sanitized, 0)
      if (count == 0) {
        usedCounts[sanitized] = 1
        result.add(sanitized)
      } else {
        usedCounts[sanitized] = count + 1
        val dir = sanitized.substringBeforeLast('/', "")
        val fileName = sanitized.substringAfterLast('/')
        val dotIndex = fileName.lastIndexOf('.')
        val uniqueFileName = if (dotIndex > 0) {
          val base = fileName.substring(0, dotIndex)
          val ext = fileName.substring(dotIndex)
          "$base ($count)$ext"
        } else {
          "$fileName ($count)"
        }
        val resolved = if (dir.isNotEmpty()) "$dir/$uniqueFileName" else uniqueFileName
        result.add(resolved)
      }
    }

    return result
  }
}
