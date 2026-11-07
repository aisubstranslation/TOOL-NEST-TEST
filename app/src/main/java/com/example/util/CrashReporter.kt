package com.example.util

import android.content.Context
import android.os.Build
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object CrashReporter {

  const val CRASH_FILE_NAME = "toolsnest_last_crash.txt"

  /**
   * Pure Kotlin formatting function for crash reports.
   * Guarantees no file names, file paths, URIs, or personal user data are stored.
   */
  fun formatCrashReport(
    exceptionClass: String,
    message: String?,
    stackFrames: List<String>,
    appVersion: String,
    androidVersion: String,
    deviceModel: String,
    timestamp: String
  ): String {
    return buildString {
      appendLine("=== TOOLSNEST CRASH REPORT ===")
      appendLine("Time: $timestamp")
      appendLine("App Version: $appVersion")
      appendLine("Android Version: $androidVersion")
      appendLine("Device: $deviceModel")
      appendLine("Exception: $exceptionClass")
      if (!message.isNullOrBlank()) {
        appendLine("Message: $message")
      }
      appendLine("--- Stack Trace ---")
      for (frame in stackFrames) {
        appendLine(frame)
      }
      append("=== END REPORT ===")
    }
  }

  fun extractStackFrames(throwable: Throwable): List<String> {
    val sw = StringWriter()
    val pw = PrintWriter(sw)
    throwable.printStackTrace(pw)
    pw.flush()
    return sw.toString().lines().filter { it.isNotBlank() }
  }

  fun install(context: Context, appVersion: String = "1.0") {
    val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
    Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
      try {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        val timestamp = sdf.format(Date())
        val androidVer = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"
        val deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}"

        val frames = extractStackFrames(throwable)
        val report = formatCrashReport(
          exceptionClass = throwable.javaClass.name,
          message = throwable.message,
          stackFrames = frames,
          appVersion = appVersion,
          androidVersion = androidVer,
          deviceModel = deviceModel,
          timestamp = timestamp
        )

        val file = File(context.filesDir, CRASH_FILE_NAME)
        file.writeText(report)
      } catch (_: Exception) {
        // Safe: never crash inside the uncaught exception handler
      } finally {
        defaultHandler?.uncaughtException(thread, throwable)
      }
    }
  }

  fun getCrashReport(context: Context): String? {
    val file = File(context.filesDir, CRASH_FILE_NAME)
    return if (file.exists() && file.length() > 0) {
      runCatching { file.readText() }.getOrNull()
    } else {
      null
    }
  }

  fun deleteCrashReport(context: Context): Boolean {
    val file = File(context.filesDir, CRASH_FILE_NAME)
    return if (file.exists()) {
      runCatching { file.delete() }.getOrDefault(false)
    } else {
      true
    }
  }
}
