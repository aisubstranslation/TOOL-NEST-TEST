package com.example

import android.app.Application
import com.example.data.db.ToolsNestDatabase
import com.example.data.preferences.ImageSettingsDataStore
import com.example.data.preferences.MediaSettingsDataStore
import com.example.data.preferences.PdfSettingsDataStore
import com.example.data.preferences.SettingsDataStore
import com.example.data.repository.HistoryRepository
import com.example.data.repository.JobRepository
import com.example.service.JobManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class ToolsNestApp : Application() {

  val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

  val database: ToolsNestDatabase by lazy {
    ToolsNestDatabase.getInstance(this)
  }

  val historyRepository: HistoryRepository by lazy {
    HistoryRepository(database.historyDao())
  }

  val jobRepository: JobRepository by lazy {
    JobRepository(database.jobDao())
  }

  val jobManager: JobManager by lazy {
    JobManager(this, jobRepository, appScope)
  }

  val settingsDataStore: SettingsDataStore by lazy {
    SettingsDataStore(this)
  }

  val imageSettingsDataStore: ImageSettingsDataStore by lazy {
    ImageSettingsDataStore(this)
  }

  val pdfSettingsDataStore: PdfSettingsDataStore by lazy {
    PdfSettingsDataStore(this)
  }

  val mediaSettingsDataStore: MediaSettingsDataStore by lazy {
    MediaSettingsDataStore(this)
  }

  val videoSettingsDataStore: com.example.data.preferences.VideoSettingsDataStore by lazy {
    com.example.data.preferences.VideoSettingsDataStore(this)
  }

  companion object {
    lateinit var instance: ToolsNestApp
      private set
  }

  override fun onCreate() {
    super.onCreate()
    instance = this
    com.example.util.CrashReporter.install(this)
    com.tom_roush.pdfbox.android.PDFBoxResourceLoader.init(applicationContext)
    val savedLang = com.example.ui.LocaleHelper.getSavedLanguage(this)
    com.example.ui.LocaleHelper.applyLocale(this, savedLang)

    // Recover any interrupted jobs left across process kill/restart
    appScope.launch {
      runCatching {
        jobManager.recoverStaleJobs()
      }
    }
  }
}
