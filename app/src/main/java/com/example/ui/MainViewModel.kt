package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.ToolsNestApp
import com.example.data.db.HistoryEntity
import com.example.data.model.JobStatus
import com.example.file.FileLayer
import com.example.model.Category
import com.example.model.Tool
import com.example.model.ToolRegistry
import com.example.model.ToolSearch
import com.example.tools.common.ToolJobRunner
import com.example.ui.navigation.Screen
import com.example.ui.theme.ThemeMode
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers

class MainViewModel(application: Application) : AndroidViewModel(application) {

  private val app = application as ToolsNestApp
  private val historyRepository = app.historyRepository
  private val settingsDataStore = app.settingsDataStore

  // Navigation state & backstack
  private val _screenStack = mutableListOf<Screen>(Screen.Home)
  private val _currentScreen = MutableStateFlow<Screen>(Screen.Home)
  val currentScreen: StateFlow<Screen> = _currentScreen.asStateFlow()

  // Search & Filter state
  private val _searchQuery = MutableStateFlow("")
  val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

  private val _selectedCategory = MutableStateFlow<Category?>(null)
  val selectedCategory: StateFlow<Category?> = _selectedCategory.asStateFlow()

  // Real history from Room
  val historyList: StateFlow<List<HistoryEntity>> = historyRepository.allHistory
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

  // Real recent tools derived solely from Room history (never fake)
  val recentTools: StateFlow<List<Tool>> = historyRepository.recentToolIds
    .combine(MutableStateFlow(ToolRegistry.tools)) { ids, allTools ->
      ids.mapNotNull { id -> allTools.find { it.id == id } }
    }
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

  // Dynamic search results
  val searchResults: StateFlow<List<Tool>> = _searchQuery
    .combine(_selectedCategory) { query, category ->
      if (query.isNotBlank()) {
        val found = ToolSearch.search(query)
        if (category != null) found.filter { it.category == category } else found
      } else {
        emptyList()
      }
    }
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

  // Settings
  val themeMode: StateFlow<ThemeMode> = settingsDataStore.themeModeFlow
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ThemeMode.SYSTEM)

  val languageCode: StateFlow<String> = settingsDataStore.languageCodeFlow
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), LocaleHelper.getSavedLanguage(app))

  val outputPrefix: StateFlow<String> = settingsDataStore.outputPrefixFlow
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "TOOLSNEST_")

  // Cache size
  private val _tempCacheSizeBytes = MutableStateFlow(0L)
  val tempCacheSizeBytes: StateFlow<Long> = _tempCacheSizeBytes.asStateFlow()

  init {
    refreshCacheSize()
  }

  fun navigateTo(screen: Screen) {
    if (_currentScreen.value != screen) {
      _screenStack.add(screen)
      _currentScreen.value = screen
    }
  }

  fun navigateBack(): Boolean {
    return if (_screenStack.size > 1) {
      _screenStack.removeAt(_screenStack.size - 1)
      _currentScreen.value = _screenStack.last()
      true
    } else {
      false
    }
  }

  fun setSearchQuery(query: String) {
    _searchQuery.value = query
  }

  fun selectCategory(category: Category?) {
    _selectedCategory.value = category
  }

  fun deleteHistoryItem(id: Long) {
    viewModelScope.launch {
      historyRepository.deleteById(id)
    }
  }

  fun clearAllHistory() {
    viewModelScope.launch {
      historyRepository.clearAll()
    }
  }

  fun updateThemeMode(mode: ThemeMode) {
    viewModelScope.launch {
      settingsDataStore.setThemeMode(mode)
    }
  }

  fun updateLanguageCode(code: String) {
    viewModelScope.launch {
      settingsDataStore.setLanguageCode(code)
    }
  }

  fun updateOutputPrefix(prefix: String) {
    viewModelScope.launch {
      settingsDataStore.setOutputPrefix(prefix)
    }
  }

  fun refreshCacheSize() {
    viewModelScope.launch {
      _tempCacheSizeBytes.value = withContext(Dispatchers.IO) { FileLayer.getTempFolderSize(app) }
    }
  }

  fun clearTempCache(
    additionalProtectedPaths: Set<String> = emptySet(),
    onFreed: (Long) -> Unit = {}
  ) {
    viewModelScope.launch {
      val protected = mutableSetOf<String>()
      protected.addAll(additionalProtectedPaths)
      protected.addAll(ToolJobRunner.getActiveProtectedPaths())

      // Protect all outputs of active jobs and unsaved completed jobs from JobRepository
      runCatching {
        val jobs = app.jobRepository.getAllJobs()
        for (job in jobs) {
          val isActive = job.jobStatus == JobStatus.QUEUED ||
              job.jobStatus == JobStatus.PREPARING ||
              job.jobStatus == JobStatus.PROCESSING ||
              job.jobStatus == JobStatus.WAITING_FOR_INPUT
          val isUnsavedCompleted = job.jobStatus == JobStatus.COMPLETED && job.savedUri == null
          if (isActive || isUnsavedCompleted) {
            job.outputPath?.let { path ->
              protected.add(path)
              protected.add(File(path).name)
            }
          }
        }
      }

      val freed = withContext(Dispatchers.IO) { FileLayer.cleanupTempFolder(app, protected) }
      refreshCacheSize()
      onFreed(freed)
    }
  }
}
