package com.example.media

object DuplicateJobHelper {

  /**
   * Pure function building a stable deduplication key for (toolId, inputUri, optionsJson).
   */
  fun buildKey(toolId: Int, inputUri: String, optionsJson: String): String {
    return "$toolId:${inputUri.trim()}:${optionsJson.trim()}"
  }
}
