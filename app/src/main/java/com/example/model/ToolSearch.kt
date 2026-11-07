package com.example.model

object ToolSearch {

  /**
   * Pure Kotlin search function (no Android framework classes).
   * Matches tool names and keywords across English, Urdu, and Hindi.
   * Handles partial words, multi-word phrases, and token overlaps with ranking.
   */
  fun search(query: String, tools: List<Tool> = ToolRegistry.tools): List<Tool> {
    val cleanQuery = query.trim().lowercase()
    if (cleanQuery.isEmpty()) {
      return emptyList()
    }

    val queryTokens = cleanQuery.split(Regex("[\\s,._-]+")).filter { it.isNotEmpty() }
    if (queryTokens.isEmpty()) {
      return emptyList()
    }

    val scored = mutableListOf<Pair<Tool, Int>>()

    for (tool in tools) {
      val score = calculateScore(tool, cleanQuery, queryTokens)
      if (score > 0) {
        scored.add(tool to score)
      }
    }

    // Sort by highest score first, then by tool ID
    return scored.sortedWith(
      compareByDescending<Pair<Tool, Int>> { it.second }
        .thenBy { it.first.id }
    ).map { it.first }
  }

  private fun calculateScore(tool: Tool, rawQuery: String, tokens: List<String>): Int {
    val nameLower = tool.defaultName.lowercase()
    val allSearchable = listOf(nameLower) + tool.keywords.map { it.lowercase() }

    var score = 0

    // 1. Direct exact or prefix matches
    for (phrase in allSearchable) {
      when {
        phrase == rawQuery -> score = maxOf(score, 120)
        phrase.startsWith(rawQuery) -> score = maxOf(score, 80)
        phrase.contains(rawQuery) -> score = maxOf(score, 50)
      }
    }

    // 2. Token-level matching (e.g., "compress image", "extract audio")
    var matchedTokensCount = 0
    for (token in tokens) {
      val tokenMatched = allSearchable.any { phrase ->
        phrase.split(Regex("[\\s,._-]+")).any { word ->
          word.startsWith(token) || word.contains(token)
        } || phrase.contains(token)
      }
      if (tokenMatched) {
        matchedTokensCount++
      }
    }

    if (matchedTokensCount > 0) {
      score += matchedTokensCount * 25
      if (matchedTokensCount == tokens.size) {
        // All query words matched
        score += 40
      }
    }

    return score
  }
}
