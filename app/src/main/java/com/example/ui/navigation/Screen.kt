package com.example.ui.navigation

sealed class Screen {
  data object Home : Screen()
  data object Categories : Screen()
  data object History : Screen()
  data object Settings : Screen()
  data object ProcessingCenter : Screen()
  data object Help : Screen()
  data class ToolDetail(val toolId: Int) : Screen()
}

enum class NavTab {
  HOME,
  CATEGORIES,
  HISTORY,
  SETTINGS
}
