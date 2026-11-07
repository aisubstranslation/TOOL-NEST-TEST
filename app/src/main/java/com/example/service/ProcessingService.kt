package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.example.MainActivity
import com.example.R
import com.example.model.ToolRegistry
import com.example.tools.common.ToolJobRunner

class ProcessingService : Service() {

  companion object {
    const val CHANNEL_PROCESSING = "channel_processing"
    const val CHANNEL_COMPLETION = "channel_completion"
    const val NOTIFICATION_ID = 1001

    const val ACTION_START = "com.example.service.action.START"
    const val ACTION_STOP = "com.example.service.action.STOP"
    const val ACTION_UPDATE_PROGRESS = "com.example.service.action.UPDATE_PROGRESS"
    const val ACTION_CANCEL = "com.example.service.action.CANCEL"

    const val EXTRA_JOB_ID = "extra_job_id"
    const val EXTRA_TOOL_ID = "extra_tool_id"
    const val EXTRA_STAGE = "extra_stage"
    const val EXTRA_PERCENT = "extra_percent"

    private const val MAX_WAKE_LOCK_DURATION_MS = 3 * 60 * 60 * 1000L // 3 hours

    fun startService(context: Context, jobId: String, toolId: Int, stage: String) {
      val intent = Intent(context, ProcessingService::class.java).apply {
        action = ACTION_START
        putExtra(EXTRA_JOB_ID, jobId)
        putExtra(EXTRA_TOOL_ID, toolId)
        putExtra(EXTRA_STAGE, stage)
      }
      try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
          context.startForegroundService(intent)
        } else {
          context.startService(intent)
        }
      } catch (e: Exception) {
        // ForegroundServiceStartNotAllowedException on API 31+
        throw e
      }
    }

    fun updateProgress(context: Context, jobId: String, toolId: Int, stage: String, percent: Int?) {
      val intent = Intent(context, ProcessingService::class.java).apply {
        action = ACTION_UPDATE_PROGRESS
        putExtra(EXTRA_JOB_ID, jobId)
        putExtra(EXTRA_TOOL_ID, toolId)
        putExtra(EXTRA_STAGE, stage)
        putExtra(EXTRA_PERCENT, percent ?: -1)
      }
      try {
        context.startService(intent)
      } catch (_: Exception) {}
    }

    fun stopService(context: Context) {
      val intent = Intent(context, ProcessingService::class.java).apply {
        action = ACTION_STOP
      }
      try {
        context.startService(intent)
      } catch (_: Exception) {}
    }
  }

  private var wakeLock: PowerManager.WakeLock? = null
  private var currentJobId: String? = null
  private var currentToolId: Int = -1
  private var currentStage: String = "Processing..."
  private var currentProgress: Int = -1

  override fun onBind(intent: Intent?): IBinder? = null

  override fun onCreate() {
    super.onCreate()
    createNotificationChannels()
    acquireWakeLock()
  }

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    val action = intent?.action ?: return START_NOT_STICKY

    when (action) {
      ACTION_START -> {
        currentJobId = intent.getStringExtra(EXTRA_JOB_ID)
        currentToolId = intent.getIntExtra(EXTRA_TOOL_ID, -1)
        currentStage = intent.getStringExtra(EXTRA_STAGE) ?: "Processing..."
        currentProgress = -1
        val notification = buildOngoingNotification()
        ServiceCompat.startForeground(
          this,
          NOTIFICATION_ID,
          notification,
          if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
          } else {
            0
          }
        )
      }
      ACTION_UPDATE_PROGRESS -> {
        currentJobId = intent.getStringExtra(EXTRA_JOB_ID) ?: currentJobId
        currentToolId = intent.getIntExtra(EXTRA_TOOL_ID, currentToolId)
        currentStage = intent.getStringExtra(EXTRA_STAGE) ?: currentStage
        currentProgress = intent.getIntExtra(EXTRA_PERCENT, -1)
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, buildOngoingNotification())
      }
      ACTION_CANCEL -> {
        currentJobId?.let { ToolJobRunner.cancel(it) }
        stopForegroundAndService()
      }
      ACTION_STOP -> {
        stopForegroundAndService()
      }
    }

    return START_NOT_STICKY
  }

  override fun onDestroy() {
    releaseWakeLock()
    super.onDestroy()
  }

  /**
   * On Android 15+ (compileSdk 35+), timeout callback for dataSync foreground services.
   */
  override fun onTimeout(startId: Int, fgsType: Int) {
    currentJobId?.let { id ->
      ToolJobRunner.interruptJob(id, "Processing timed out")
    }
    stopForegroundAndService()
  }

  private fun acquireWakeLock() {
    if (wakeLock == null) {
      val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
      wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "TOOLSNEST:ProcessingWakeLock").apply {
        setReferenceCounted(false)
        acquire(MAX_WAKE_LOCK_DURATION_MS)
      }
    }
  }

  private fun releaseWakeLock() {
    try {
      wakeLock?.let {
        if (it.isHeld) it.release()
      }
    } catch (_: Exception) {}
    wakeLock = null
  }

  private fun stopForegroundAndService() {
    releaseWakeLock()
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
      stopForeground(STOP_FOREGROUND_REMOVE)
    } else {
      @Suppress("DEPRECATION")
      stopForeground(true)
    }
    stopSelf()
  }

  private fun createNotificationChannels() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
      val processingChannel = NotificationChannel(
        CHANNEL_PROCESSING,
        "Processing",
        NotificationManager.IMPORTANCE_LOW
      ).apply {
        description = "Shows ongoing background tool processing"
        setShowBadge(false)
      }
      nm.createNotificationChannel(processingChannel)

      val completionChannel = NotificationChannel(
        CHANNEL_COMPLETION,
        "Task Notifications",
        NotificationManager.IMPORTANCE_DEFAULT
      ).apply {
        description = "Alerts when tasks finish or need attention"
      }
      nm.createNotificationChannel(completionChannel)
    }
  }

  private fun buildOngoingNotification(): Notification {
    val tool = if (currentToolId > 0) ToolRegistry.getById(currentToolId) else null
    val title = if (tool != null) getString(tool.nameRes) else "TOOLSNEST Processing"

    // Tap action opens Processing Center in MainActivity
    val contentIntent = Intent(this, MainActivity::class.java).apply {
      flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
      putExtra("navigate_to", "processing_center")
    }
    val pendingContent = PendingIntent.getActivity(
      this,
      0,
      contentIntent,
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    // Cancel action
    val cancelIntent = Intent(this, ProcessingService::class.java).apply {
      action = ACTION_CANCEL
    }
    val pendingCancel = PendingIntent.getService(
      this,
      1,
      cancelIntent,
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    val builder = NotificationCompat.Builder(this, CHANNEL_PROCESSING)
      .setContentTitle(title)
      .setContentText(currentStage)
      .setSmallIcon(R.drawable.ic_launcher_monochrome)
      .setOngoing(true)
      .setContentIntent(pendingContent)
      .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Cancel", pendingCancel)
      .setPriority(NotificationCompat.PRIORITY_LOW)
      .setCategory(NotificationCompat.CATEGORY_PROGRESS)

    if (currentProgress in 0..100) {
      builder.setProgress(100, currentProgress, false)
    } else {
      builder.setProgress(100, 0, true)
    }

    return builder.build()
  }
}
