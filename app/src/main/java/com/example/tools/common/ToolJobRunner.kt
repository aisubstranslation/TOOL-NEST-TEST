package com.example.tools.common

import com.example.ToolsNestApp
import com.example.data.db.HistoryEntity
import com.example.data.repository.HistoryRepository
import com.example.service.JobManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.coroutineContext

enum class JobState {
  IDLE,
  RUNNING,
  SUCCESS,
  FAILED,
  CANCELLED
}

data class FileResult(
  val inputName: String,
  val outputName: String?,
  val outputUri: String?,
  val outputSizeBytes: Long,
  val success: Boolean,
  val userMessage: String? = null,
  val width: Int? = null,
  val height: Int? = null,
  val originalSizeBytes: Long = 0L,
  val format: String? = null,
  val tempFilePath: String? = null
)

data class JobResult(
  val files: List<FileResult>
)

data class JobUi(
  val jobId: String,
  val toolId: Int,
  val state: JobState,
  val stage: String,
  val progressPercent: Int?,
  val currentIndex: Int = 0,
  val totalFiles: Int = 0,
  val currentFileName: String = "",
  val result: JobResult? = null,
  val errorMessage: String? = null
)

interface JobContext {
  suspend fun reportProgress(
    percent: Int?,
    stage: String,
    currentIndex: Int = 0,
    totalFiles: Int = 0,
    currentFileName: String = ""
  )
  fun ensureActive()
  fun trackPartialOutput(file: File)
}

object ToolJobRunner {

  // For testing injection
  var customScope: CoroutineScope? = null
  var customRepository: HistoryRepository? = null

  private val activeJobs = ConcurrentHashMap<Int, Job>()
  private val activeJobIds = ConcurrentHashMap<Int, String>()
  private val runningJobsById = ConcurrentHashMap<String, Job>()
  private val jobUiFlows = ConcurrentHashMap<String, MutableStateFlow<JobUi>>()
  private val jobPartialFiles = ConcurrentHashMap<String, MutableSet<File>>()

  private fun getScope(): CoroutineScope {
    return customScope ?: runCatching { ToolsNestApp.instance.appScope }.getOrNull() ?: CoroutineScope(Dispatchers.Default)
  }

  private fun getRepository(): HistoryRepository? {
    return customRepository ?: runCatching { ToolsNestApp.instance.historyRepository }.getOrNull()
  }

  private fun getJobManager(): JobManager? {
    return if (customScope == null) runCatching { ToolsNestApp.instance.jobManager }.getOrNull() else null
  }

  fun isToolBusy(toolId: Int): Boolean {
    val jm = getJobManager()
    if (jm != null) {
      return jm.isToolBusy(toolId)
    }
    val job = activeJobs[toolId]
    return job != null && job.isActive
  }

  fun hasJob(jobId: String): Boolean {
    val jm = getJobManager()
    if (jm != null) {
      return jm.hasJob(jobId)
    }
    return jobUiFlows.containsKey(jobId)
  }

  fun getActiveProtectedPaths(): Set<String> {
    val protected = mutableSetOf<String>()
    val jm = getJobManager()
    if (jm != null) {
      protected.addAll(jm.getActiveJobProtectedPaths())
    }
    for (files in jobPartialFiles.values) {
      for (f in files) {
        protected.add(f.absolutePath)
        protected.add(f.name)
      }
    }
    return protected
  }

  fun start(
    toolId: Int,
    inputName: String,
    optionsJson: String,
    block: suspend JobContext.() -> JobResult
  ): String {
    return start(
      toolId = toolId,
      inputName = inputName,
      optionsJson = optionsJson,
      inputUri = "",
      inputSize = 0L,
      block = block
    )
  }

  fun start(
    toolId: Int,
    inputName: String,
    optionsJson: String,
    inputUri: String = "",
    inputSize: Long = 0L,
    block: suspend JobContext.() -> JobResult
  ): String {
    val jm = getJobManager()
    if (jm != null) {
      return jm.enqueueJob(
        toolId = toolId,
        inputUri = inputUri,
        inputName = inputName,
        inputSize = inputSize,
        optionsJson = optionsJson,
        block = {
          try {
            val result = block()
            // Automatically persist history records for backwards compatibility
            val repo = getRepository()
            if (repo != null) {
              for (fileResult in result.files) {
                val statusStr = if (fileResult.success) "SUCCESS" else "FAILED"
                repo.insert(
                  HistoryEntity(
                    toolId = toolId,
                    inputFileName = fileResult.inputName,
                    outputFileName = fileResult.outputName,
                    timestamp = System.currentTimeMillis(),
                    status = statusStr,
                    outputSizeBytes = fileResult.outputSizeBytes,
                    outputUri = fileResult.outputUri
                  )
                )
              }
            }
            result
          } catch (t: Throwable) {
            if (t !is CancellationException) {
              val repo = getRepository()
              if (repo != null) {
                runCatching {
                  repo.insert(
                    HistoryEntity(
                      toolId = toolId,
                      inputFileName = inputName,
                      outputFileName = null,
                      timestamp = System.currentTimeMillis(),
                      status = "FAILED",
                      outputSizeBytes = 0L,
                      outputUri = null
                    )
                  )
                }
              }
            }
            throw t
          }
        }
      )
    }

    synchronized(this) {
      val existingJob = activeJobs[toolId]
      if (existingJob != null && existingJob.isActive) {
        val existingId = activeJobIds[toolId]
        if (existingId != null) {
          return existingId
        }
      }

      val jobId = UUID.randomUUID().toString()
      activeJobIds[toolId] = jobId
      val initialUi = JobUi(
        jobId = jobId,
        toolId = toolId,
        state = JobState.RUNNING,
        stage = "Starting...",
        progressPercent = null,
        currentFileName = inputName
      )

      val flow = MutableStateFlow(initialUi)
      jobUiFlows[jobId] = flow
      val trackedFiles = ConcurrentHashMap.newKeySet<File>()
      jobPartialFiles[jobId] = trackedFiles

      val coroutineJob = getScope().launch(Dispatchers.Default) {
        val jobContext = object : JobContext {
          override suspend fun reportProgress(
            percent: Int?,
            stage: String,
            currentIndex: Int,
            totalFiles: Int,
            currentFileName: String
          ) {
            coroutineContext.ensureActive()
            flow.value = flow.value.copy(
              stage = stage,
              progressPercent = percent,
              currentIndex = currentIndex,
              totalFiles = totalFiles,
              currentFileName = currentFileName
            )
          }

          override fun ensureActive() {
            coroutineContext.ensureActive()
          }

          override fun trackPartialOutput(file: File) {
            trackedFiles.add(file)
          }
        }

        try {
          val result = block(jobContext)

          // Write one history row per finished file
          val repository = getRepository()
          if (repository != null) {
            for (fileResult in result.files) {
              val statusStr = if (fileResult.success) "SUCCESS" else "FAILED"
              repository.insert(
                HistoryEntity(
                  toolId = toolId,
                  inputFileName = fileResult.inputName,
                  outputFileName = fileResult.outputName,
                  timestamp = System.currentTimeMillis(),
                  status = statusStr,
                  outputSizeBytes = fileResult.outputSizeBytes,
                  outputUri = fileResult.outputUri
                )
              )
            }
          }

          val allSucceeded = result.files.isNotEmpty() && result.files.all { it.success }
          flow.value = flow.value.copy(
            state = if (allSucceeded) JobState.SUCCESS else JobState.FAILED,
            stage = if (allSucceeded) "Complete" else "Failed",
            progressPercent = 100,
            result = result
          )
        } catch (e: CancellationException) {
          // Delete partial output on cancel
          for (file in trackedFiles) {
            if (file.exists()) {
              runCatching { file.delete() }
            }
          }
          flow.value = flow.value.copy(
            state = JobState.CANCELLED,
            stage = "Cancelled",
            progressPercent = null
          )
          throw e
        } catch (e: Throwable) {
          // Record failed history row on exception
          val repo = getRepository()
          if (repo != null) {
            runCatching {
              repo.insert(
                HistoryEntity(
                  toolId = toolId,
                  inputFileName = inputName,
                  outputFileName = null,
                  timestamp = System.currentTimeMillis(),
                  status = "FAILED",
                  outputSizeBytes = 0L,
                  outputUri = null
                )
              )
            }
          }
          // Delete partial output on error
          for (file in trackedFiles) {
            if (file.exists()) {
              runCatching { file.delete() }
            }
          }
          flow.value = flow.value.copy(
            state = JobState.FAILED,
            stage = "Error: ${e.message ?: "Processing error"}",
            progressPercent = null,
            errorMessage = e.message ?: "An unexpected error occurred"
          )
        } finally {
          activeJobs.remove(toolId)
          activeJobIds.remove(toolId)
          runningJobsById.remove(jobId)
        }
      }

      coroutineJob.invokeOnCompletion { cause ->
        activeJobs.remove(toolId)
        activeJobIds.remove(toolId)
        runningJobsById.remove(jobId)
        if (cause is CancellationException) {
          for (file in trackedFiles) {
            if (file.exists()) {
              runCatching { file.delete() }
            }
          }
          val f = jobUiFlows[jobId]
          if (f != null && f.value.state != JobState.CANCELLED) {
            f.value = f.value.copy(
              state = JobState.CANCELLED,
              stage = "Cancelled",
              progressPercent = null
            )
          }
        }
      }

      activeJobs[toolId] = coroutineJob
      runningJobsById[jobId] = coroutineJob
      return jobId
    }
  }

  fun cancel(jobId: String) {
    val jm = getJobManager()
    if (jm != null) {
      jm.cancelJob(jobId)
      return
    }

    val job = runningJobsById[jobId]
    if (job != null) {
      job.cancel()
      val flow = jobUiFlows[jobId]
      if (flow != null && flow.value.state == JobState.RUNNING) {
        flow.value = flow.value.copy(
          state = JobState.CANCELLED,
          stage = "Cancelled",
          progressPercent = null
        )
      }
    }
  }

  fun interruptJob(jobId: String, reason: String) {
    val jm = getJobManager()
    jm?.interruptJob(jobId, reason)
  }

  fun observe(jobId: String): Flow<JobUi> {
    val jm = getJobManager()
    if (jm != null) {
      return jm.observeJobUi(jobId)
    }

    val flow = jobUiFlows[jobId] ?: MutableStateFlow(
      JobUi(
        jobId = jobId,
        toolId = -1,
        state = JobState.IDLE,
        stage = "",
        progressPercent = null
      )
    )
    return flow.asStateFlow()
  }

  fun clearJob(jobId: String) {
    jobUiFlows.remove(jobId)
    jobPartialFiles.remove(jobId)
  }
}
