package com.example.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CrashReporterTest {

  @Test
  fun `verify formatCrashReport output matches hand-verified format`() {
    val exceptionClass = "java.lang.IllegalArgumentException"
    val message = "Max items must be greater than 1"
    val stackFrames = listOf(
      "at com.example.tools.common.FilePickerCardKt.FilePickerCard(FilePickerCard.kt:49)",
      "at com.example.tools.file.FileInformationScreenKt.FileInformationScreen(FileInformationScreen.kt:266)"
    )
    val appVersion = "1.0"
    val androidVersion = "Android 14 (API 34)"
    val deviceModel = "Google Pixel 8"
    val timestamp = "2026-10-16 12:00:00"

    val report = CrashReporter.formatCrashReport(
      exceptionClass = exceptionClass,
      message = message,
      stackFrames = stackFrames,
      appVersion = appVersion,
      androidVersion = androidVersion,
      deviceModel = deviceModel,
      timestamp = timestamp
    )

    val expected = """
      === TOOLSNEST CRASH REPORT ===
      Time: 2026-10-16 12:00:00
      App Version: 1.0
      Android Version: Android 14 (API 34)
      Device: Google Pixel 8
      Exception: java.lang.IllegalArgumentException
      Message: Max items must be greater than 1
      --- Stack Trace ---
      at com.example.tools.common.FilePickerCardKt.FilePickerCard(FilePickerCard.kt:49)
      at com.example.tools.file.FileInformationScreenKt.FileInformationScreen(FileInformationScreen.kt:266)
      === END REPORT ===
    """.trimIndent()

    assertEquals(expected, report)
  }

  @Test
  fun `verify formatCrashReport handles null message safely`() {
    val report = CrashReporter.formatCrashReport(
      exceptionClass = "java.lang.NullPointerException",
      message = null,
      stackFrames = listOf("at com.example.MainActivity.onCreate(MainActivity.kt:30)"),
      appVersion = "1.0",
      androidVersion = "Android 13 (API 33)",
      deviceModel = "Samsung SM-S918B",
      timestamp = "2026-10-16 15:30:00"
    )

    assertTrue(report.startsWith("=== TOOLSNEST CRASH REPORT ==="))
    assertTrue(report.contains("Exception: java.lang.NullPointerException"))
    assertTrue(!report.contains("Message:"))
    assertTrue(report.endsWith("=== END REPORT ==="))
  }
}
