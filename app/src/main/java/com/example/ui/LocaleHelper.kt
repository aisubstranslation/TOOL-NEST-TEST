package com.example.ui

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

object LocaleHelper {

  private const val PREFS_NAME = "toolsnest_locale_prefs"
  private const val KEY_LANG = "language_code"

  fun getSavedLanguage(context: Context): String {
    return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
      .getString(KEY_LANG, "system") ?: "system"
  }

  fun saveLanguage(context: Context, code: String) {
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
      .edit()
      .putString(KEY_LANG, code)
      .commit()
  }

  fun applyLocale(context: Context, langCode: String): Context {
    val locale = if (langCode == "system" || langCode.isBlank()) {
      Locale.getDefault()
    } else {
      Locale(langCode)
    }

    Locale.setDefault(locale)
    val config = Configuration(context.resources.configuration)
    config.setLocale(locale)
    config.setLayoutDirection(locale)
    return context.createConfigurationContext(config)
  }
}
