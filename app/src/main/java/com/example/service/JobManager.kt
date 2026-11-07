package com.example.service

import android.content.Context
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.MainActivity
import com.example.R
import com.example.data.db.JobEntity
import com.example.data.model.JobStatus
import com.example.data.model.JobStatusTransitions
import com.example.data.repository.JobRepository
import com.example.media.DuplicateJobHelper
import com.example.model.ToolRegistry
import com.example.tools.common.FileResult
import com.example.tools.common.JobContext
import com.example.tools.common.JobResult
import com.example.tools.common.JobState
import com.example.tools.common.JobUi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.coroutineContext

class JobManager(
  private val context: Context,
  private val jobRepository: JobRepository,
  private val scope: CoroutineScope
) {

  private val queueMutex = Mutex()
  private val activeJobsByTool = ConcurrentHashMap<Int, String>()
  private val runningCoroutineJobs = ConcurrentHashMap<String, Job>()
  private val trackedPartialFiles = ConcurrentHashMap<String, MutableSet<File>>()
  private val liveUiFlows = ConcurrentHashMap<String, MutableStateFlow<JobUi>>()

  // Duplicate lookup by key -> jobId
  private val activeDuplicateKeys = ConcurrentHashMap<String, String>()

  // Registry for executable job blocks
  private val executableBlocks = ConcurrentHashMap<String, suspend JobContext.() -> JobResult>()

  /**
   * Recovers any stale jobs left in QUEUED, PREPARING, or PROCESSING state across process restarts.
   */
  suspend fun recoverStaleJobs() = withContext(Dispatchers.IO) {
    val staleJobs = jobRepository.getStaleActiveJobs()
    for (stale in staleJobs) {
      // Delete any partial outputs
      stale.outputPath?.let { path ->
        val file = File(path)
        if (file.exists()) {
          try { file.delete() } catch (_: Exception) {}
        }
      }

      val updated = stale.copy(
        status = JobStatus.INTERRUPTED.name,
        stage = "Interrupted",
        errorMessage = "TOOLSNEST was closed or the phone restarted while this job was running. Restart will begin again from the start.",
        finishedAt = System.currentTimeMillis()
      )
      jobRepository.update(updated)
    }
  }

  fun isToolBusy(toolId: Int): Boolean {
    val activeId = activeJobsByTool[toolId] ?: return false
    val coroutine = runningCoroutineJobs[activeId]
    return coroutine != null && coroutine.isActive
  }

  fun hasJob(jobId: String): Boolean = liveUiFlows.containsKey(jobId)

  fun observeJobUi(jobId: String): Flow<JobUi> {
    return liveUiFlows.getOrPut(jobId) {
      MutableStateFlow(
        JobUi(
          jobId = jobId,
          toolId = -1,
          state = JobState.IDLE,
          stage = "",
          progressPercent = null
        )
      )
    }.asStateFlow()
  }

  /**
   * Enqueues or deduplicates a job.
   */
  fun enqueueJob(
    toolId: Int,
    inputUri: String = "",
    inputName: String,
    inputSize: Long = 0L,
    optionsJson: String = "{}",
    block: suspend JobContext.() -> JobResult
  ): String {
    val dupKey = DuplicateJobHelper.buildKey(toolId, inputUri, optionsJson)
    val existingActiveId = activeDuplicateKeys[dupKey]
    if (existingActiveId != null && runningCoroutineJobs[existingActiveId]?.isActive == true) {
      return existingActiveId
    }

    val jobId = UUID.randomUUID().toString()
    activeJobsByTool[toolId] = jobId
    activeDuplicateKeys[dupKey] = jobId
    executableBlocks[jobId] = block

    val initialEntity = JobEntity(
      id = jobId,
      toolId = toolId,
      inputUri = inputUri,
      inputName = inputName,
      inputSize = inputSize,
      optionsJson = optionsJson,
      stage = "Queued",
      progressPercent = -1,
      status = JobStatus.QUEUED.name,
      createdAt = System.currentTimeMillis()
    )

    val initialUi = JobUi(
      jobId = jobId,
      toolId = toolId,
      state = JobState.RUNNING,
      stage = "Queued...",
      progressPercent = null,
      currentFileName = inputName
    )

    val uiFlow = liveUiFlows.getOrPut(jobId) { MutableStateFlow(initialUi) }
    uiFlow.value = initialUi

    scope.launch(Dispatchers.IO) {
      jobRepository.insert(initialEntity)
      runJobQueue(jobId, dupKey)
    }

    return jobId
  }

  private suspend fun runJobQueue(jobId: String, dupKey: String) {
    queueMutex.withLock {
      val entity = jobRepository.getById(jobId) ?: return@withLock
      if (entity.jobStatus != JobStatus.QUEUED) return@withLock

      val toolId = entity.toolId
      val block = executableBlocks[jobId] ?: return@withLock
      val trackedFiles = ConcurrentHashMap.newKeySet<File>()
      trackedPartialFiles[jobId] = trackedFiles
      val uiFlow = liveUiFlows.getOrPut(jobId) {
        MutableStateFlow(
          JobUi(
            jobId = jobId,
            toolId = toolId,
            state = JobState.RUNNING,
            stage = "Preparing…",
            progressPercent = null,
            currentFileName = entity.inputName
          )
        )
      }

      // Transition to PREPARING
      val preparingEntity = entity.copy(
        status = JobStatusTransitions.transition(entity.jobStatus, JobStatus.PREPARING).name,
        stage = "Preparing…",
        startedAt = System.currentTimeMillis()
      )
      jobRepository.update(preparingEntity)

      // Start foreground service
      try {
        ProcessingService.startService(context, jobId, toolId, "Preparing…")
      } catch (e: Exception) {
        val failedEntity = preparingEntity.copy(
          status = JobStatus.FAILED.name,
          stage = "Failed to start service",
          errorMessage = "Open TOOLSNEST and tap Start again",
          finishedAt = System.currentTimeMillis()
        )
        jobRepository.update(failedEntity)
        uiFlow.value = uiFlow.value.copy(
          state = JobState.FAILED,
          stage = "Failed",
          errorMessage = "Open TOOLSNEST and tap Start again"
        )
        activeJobsByTool.remove(toolId)
        activeDuplicateKeys.remove(dupKey)
        executableBlocks.remove(jobId)
        return@withLock
      }

      // Transition to PROCESSING
      val processingEntity = preparingEntity.copy(
        status = JobStatusTransitions.transition(JobStatus.PREPARING, JobStatus.PROCESSING).name,
        stage = "Processing…"
      )
      jobRepository.update(processingEntity)

      val coroutineJob = scope.launch(Dispatchers.Default) {
        val jobContext = object : JobContext {
          override suspend fun reportProgress(
            percent: Int?,
            stage: String,
            currentIndex: Int,
            totalFiles: Int,
            currentFileName: String
          ) {
            coroutineContext.ensureActive()
            uiFlow.value = uiFlow.value.copy(
              stage = stage,
              progressPercent = percent,
              currentIndex = currentIndex,
              totalFiles = totalFiles,
              currentFileName = currentFileName
            )
            jobRepository.getById(jobId)?.let { current ->
              jobRepository.update(
                current.copy(
                  stage = stage,
                  progressPercent = percent ?: -1
                )
              )
            }
            ProcessingService.updateProgress(context, jobId, toolId, stage, percent)
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

          val firstFile = result.files.firstOrNull()
          val allSucceeded = result.files.isNotEmpty() && result.files.all { it.success }

          val terminalStatus = if (allSucceeded) JobStatus.COMPLETED else JobStatus.FAILED
          val finalEntity = jobRepository.getById(jobId)?.copy(
            status = terminalStatus.name,
            stage = if (allSucceeded) "Complete" else "Failed",
            progressPercent = 100,
            outputPath = firstFile?.tempFilePath,
            outputName = firstFile?.outputName,
            outputSize = firstFile?.outputSizeBytes ?: 0L,
            errorMessage = firstFile?.userMessage,
            finishedAt = System.currentTimeMillis()
          ) ?: processingEntity

          jobRepository.update(finalEntity)

          uiFlow.value = uiFlow.value.copy(
            state = if (allSucceeded) JobState.SUCCESS else JobState.FAILED,
            stage = if (allSucceeded) "Complete" else "Failed",
            progressPercent = 100,
            result = result,
            errorMessage = if (!allSucceeded) (firstFile?.userMessage ?: "Processing failed") else null
          )
        } catch (e: CancellationException) {
          cleanPartialFiles(trackedFiles)
          jobRepository.getById(jobId)?.let { current ->
            jobRepository.update(
              current.copy(
                status = JobStatus.CANCELLED.name,
                stage = "Cancelled",
                finishedAt = System.currentTimeMillis()
              )
            )
          }
          uiFlow.value = uiFlow.value.copy(
            state = JobState.CANCELLED,
            stage = "Cancelled",
            progressPercent = null
          )
          throw e
        } catch (e: Throwable) {
          cleanPartialFiles(trackedFiles)
          val errorMsg = e.localizedMessage ?: "Processing error"
          jobRepository.getById(jobId)?.let { current ->
            jobRepository.update(
              current.copy(
                status = JobStatus.FAILED.name,
                stage = "Error: $errorMsg",
                errorMessage = errorMsg,
                errorDetail = "${e.javaClass.simpleName}: ${e.message}",
                finishedAt = System.currentTimeMillis()
              )
            )
          }
          uiFlow.value = uiFlow.value.copy(
            state = JobState.FAILED,
            stage = "Failed",
            errorMessage = errorMsg,
            progressPercent = null
          )
        } finally {
          activeJobsByTool.remove(toolId)
          activeDuplicateKeys.remove(dupKey)
          runningCoroutineJobs.remove(jobId)
          executableBlocks.remove(jobId)
          trackedPartialFiles.remove(jobId)

          if (activeJobsByTool.isEmpty()) {
            ProcessingService.stopService(context)
          }
        }
      }

      runningCoroutineJobs[jobId] = coroutineJob
      coroutineJob.join()
    }
  }

  fun cancelJob(jobId: String) {
    val coroutine = runningCoroutineJobs[jobId]
    if (coroutine != null && coroutine.isActive) {
      coroutine.cancel()
    } else {
      scope.launch(Dispatchers.IO) {
        val entity = jobRepository.getById(jobId)
        if (entity != null && (entity.jobStatus == JobStatus.QUEUED || entity.jobStatus == JobStatus.WAITING_FOR_INPUT)) {
          jobRepository.update(
            entity.copy(
              status = JobStatus.CANCELLED.name,
              stage = "Cancelled",
              finishedAt = System.currentTimeMillis()
            )
          )
          val flow = liveUiFlows[jobId]
          flow?.value = flow.value.copy(
            state = JobState.CANCELLED,
            stage = "Cancelled"
          )
        }
      }
    }
  }

  fun interruptJob(jobId: String, reason: String) {
    scope.launch(Dispatchers.IO) {
      val entity = jobRepository.getById(jobId) ?: return@launch
      jobRepository.update(
        entity.copy(
          status = JobStatus.INTERRUPTED.name,
          stage = "Interrupted",
          errorMessage = reason,
          finishedAt = System.currentTimeMillis()
        )
      )
      runningCoroutineJobs[jobId]?.cancel()
      activeJobsByTool.remove(entity.toolId)
      val flow = liveUiFlows[jobId]
      flow?.value = flow.value.copy(
        state = JobState.FAILED,
        stage = "Interrupted",
        errorMessage = reason
      )
    }
  }

  fun getActiveJobProtectedPaths(): Set<String> {
    val protected = mutableSetOf<String>()
    for (files in trackedPartialFiles.values) {
      for (f in files) {
        protected.add(f.absolutePath)
        protected.add(f.name)
      }
    }
    return protected
  }

  private fun cleanPartialFiles(files: Set<File>) {
    for (file in files) {
      if (file.exists()) {
        try { file.delete() } catch (_: Exception) {}
      }
    }
  }
}
