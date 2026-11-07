package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.example.R
import com.example.model.ToolRegistry
import com.example.tools.ToolRouter
import com.example.ui.categories.CategoriesScreen
import com.example.ui.history.HistoryScreen
import com.example.ui.home.HomeScreen
import com.example.ui.navigation.NavTab
import com.example.ui.navigation.Screen
import com.example.ui.settings.SettingsScreen
import com.example.ui.tool.ToolShellScreen

@Composable
fun MainScaffold(
  viewModel: MainViewModel,
  onLanguageChanged: (String) -> Unit,
  onFinishApp: () -> Unit,
  modifier: Modifier = Modifier
) {
  val currentScreen by viewModel.currentScreen.collectAsState()

  BackHandler {
    if (!viewModel.navigateBack()) {
      onFinishApp()
    }
  }

  val isToolDetail = currentScreen is Screen.ToolDetail || currentScreen is Screen.ProcessingCenter || currentScreen is Screen.Help

  Scaffold(
    modifier = modifier.fillMaxSize(),
    bottomBar = {
      if (!isToolDetail) {
        NavigationBar(
          modifier = Modifier.testTag("bottom_nav_bar")
        ) {
          NavigationBarItem(
            selected = currentScreen is Screen.Home,
            onClick = { viewModel.navigateTo(Screen.Home) },
            icon = { Icon(Icons.Default.Home, contentDescription = stringResource(R.string.nav_home)) },
            label = { Text(stringResource(R.string.nav_home)) },
            modifier = Modifier.testTag("nav_item_home")
          )

          NavigationBarItem(
            selected = currentScreen is Screen.Categories,
            onClick = { viewModel.navigateTo(Screen.Categories) },
            icon = { Icon(Icons.Default.Category, contentDescription = stringResource(R.string.nav_categories)) },
            label = { Text(stringResource(R.string.nav_categories)) },
            modifier = Modifier.testTag("nav_item_categories")
          )

          NavigationBarItem(
            selected = currentScreen is Screen.History,
            onClick = { viewModel.navigateTo(Screen.History) },
            icon = { Icon(Icons.Default.History, contentDescription = stringResource(R.string.nav_history)) },
            label = { Text(stringResource(R.string.nav_history)) },
            modifier = Modifier.testTag("nav_item_history")
          )

          NavigationBarItem(
            selected = currentScreen is Screen.Settings,
            onClick = { viewModel.navigateTo(Screen.Settings) },
            icon = { Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.nav_settings)) },
            label = { Text(stringResource(R.string.nav_settings)) },
            modifier = Modifier.testTag("nav_item_settings")
          )
        }
      }
    }
  ) { innerPadding ->
    Box(
      modifier = Modifier
        .fillMaxSize()
        .padding(if (isToolDetail) innerPadding else innerPadding)
    ) {
      Crossfade(targetState = currentScreen, label = "screen_crossfade") { screen ->
        when (screen) {
          is Screen.Home -> {
            HomeScreen(
              viewModel = viewModel,
              onToolClick = { toolId -> viewModel.navigateTo(Screen.ToolDetail(toolId)) },
              onOpenProcessingCenter = { viewModel.navigateTo(Screen.ProcessingCenter) },
              onOpenHelp = { viewModel.navigateTo(Screen.Help) }
            )
          }

          is Screen.Categories -> {
            CategoriesScreen(
              onToolClick = { toolId -> viewModel.navigateTo(Screen.ToolDetail(toolId)) }
            )
          }

          is Screen.History -> {
            HistoryScreen(viewModel = viewModel)
          }

          is Screen.Settings -> {
            SettingsScreen(
              viewModel = viewModel,
              onLanguageChanged = onLanguageChanged
            )
          }

          is Screen.ProcessingCenter -> {
            com.example.ui.processing.ProcessingCenterScreen(
              onBack = { viewModel.navigateBack() }
            )
          }

          is Screen.Help -> {
            com.example.ui.help.HelpScreen(
              onBack = { viewModel.navigateBack() }
            )
          }

          is Screen.ToolDetail -> {
            val toolId = screen.toolId
            val registeredScreen = ToolRouter.getToolScreen(toolId)

            if (registeredScreen != null) {
              registeredScreen { viewModel.navigateBack() }
            } else {
              val tool = ToolRegistry.getById(toolId)
              if (tool != null) {
                ToolShellScreen(
                  tool = tool,
                  onBack = { viewModel.navigateBack() }
                )
              }
            }
          }
        }
      }
    }
  }
}
