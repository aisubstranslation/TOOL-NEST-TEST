package com.example.ui

import androidx.compose.ui.unit.LayoutDirection

object LocaleDirectionHelper {

  private val RTL_LANGUAGE_CODES = setOf("ur", "ar", "fa", "he")

  /**
   * Checks whether the given ISO-639 language code represents an RTL language.
   * Supports inputs such as "ur", "UR", "ur-PK", "ar_SA".
   */
  fun isRtl(languageCode: String?): Boolean {
    if (languageCode.isNullOrBlank()) return false
    val normalized = languageCode.trim().lowercase().split("-", "_")[0]
    return normalized in RTL_LANGUAGE_CODES
  }

  /**
   * Resolves the [LayoutDirection] based on the selected language code.
   * If code is "system", null, or blank, it relies on [systemIsRtl].
   */
  fun getLayoutDirection(languageCode: String?, systemIsRtl: Boolean = false): LayoutDirection {
    if (languageCode == null || languageCode.isBlank() || languageCode.trim().lowercase() == "system") {
      return if (systemIsRtl) LayoutDirection.Rtl else LayoutDirection.Ltr
    }
    return if (isRtl(languageCode)) LayoutDirection.Rtl else LayoutDirection.Ltr
  }
}
