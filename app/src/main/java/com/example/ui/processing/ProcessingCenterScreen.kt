package com.example.ui.processing

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PauseCircleOutline
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SyncProblem
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.ToolsNestApp
import com.example.data.db.JobEntity
import com.example.data.model.JobStatus
import com.example.file.FileFormatters
import com.example.file.FileLayer
import com.example.model.ToolRegistry
import com.example.tools.common.ToolJobRunner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProcessingCenterScreen(
  onBack: () -> Unit,
  modifier: Modifier = Modifier
) {
  BackHandler { onBack() }

  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val app = runCatching { ToolsNestApp.instance }.getOrNull()
  val jobRepo = app?.jobRepository
  val jobManager = app?.jobManager

  val jobsList by (jobRepo?.observeVisibleJobs() ?: flowOf(emptyList())).collectAsState(initial = emptyList())

  var detailsJob by remember { mutableStateOf<JobEntity?>(null) }
  var confirmRemoveJob by remember { mutableStateOf<JobEntity?>(null) }
  var pendingRetryJob by remember { mutableStateOf<JobEntity?>(null) }

  // Launcher to save output file via CreateDocument
  var targetSaveFile by remember { mutableStateOf<File?>(null) }
  var targetSaveJobId by remember { mutableStateOf<String?>(null) }

  val saveLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.CreateDocument("*/*")
  ) { uri ->
    val file = targetSaveFile
    val jId = targetSaveJobId
    if (uri != null && file != null && file.exists()) {
      scope.launch(Dispatchers.IO) {
        val success = FileLayer.saveFileToUri(context, file, uri)
        if (success) {
          if (jId != null) {
            jobRepo?.markJobSaved(jId, uri.toString())
          }
          // Check delete after save setting
          val deleteAfterSave = app?.mediaSettingsDataStore?.deleteAfterSaveFlow ?: flowOf(true)
          // Default true
          withContext(Dispatchers.Main) {
            Toast.makeText(context, context.getString(R.string.file_saved_success), Toast.LENGTH_SHORT).show()
          }
        }
      }
    }
  }

  // Re-select document launcher when input is missing
  val reselectDocLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.OpenDocument()
  ) { uri ->
    val job = pendingRetryJob
    if (uri != null && job != null) {
      runCatching {
        context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
      }
      scope.launch(Dispatchers.IO) {
        jobRepo?.update(
          job.copy(
            inputUri = uri.toString(),
            status = JobStatus.QUEUED.name,
            stage = "Queued",
            errorMessage = null
          )
        )
      }
    }
    pendingRetryJob = null
  }

  Scaffold(
    topBar = {
      TopAppBar(
        title = {
          Text(
            text = stringResource(R.string.proc_center_title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
          )
        },
        navigationIcon = {
          IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
          }
        }
      )
    },
    modifier = modifier.fillMaxSize().testTag("processing_center_screen")
  ) { padding ->
    if (jobsList.isEmpty()) {
      Box(
        modifier = Modifier
          .fillMaxSize()
          .padding(padding)
          .padding(24.dp),
        contentAlignment = Alignment.Center
      ) {
        Column(
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          Icon(
            imageVector = Icons.Default.HourglassTop,
            contentDescription = null,
            modifier = Modifier.size(56.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
          )
          Text(
            text = stringResource(R.string.proc_center_empty_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
          )
          Text(
            text = stringResource(R.string.proc_center_empty_desc),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
      }
    } else {
      val activeJobs = jobsList.filter { it.jobStatus == JobStatus.PREPARING || it.jobStatus == JobStatus.PROCESSING }
      val queuedJobs = jobsList.filter { it.jobStatus == JobStatus.QUEUED }
      val attentionJobs = jobsList.filter {
        it.jobStatus == JobStatus.INTERRUPTED || it.jobStatus == JobStatus.WAITING_FOR_INPUT || it.jobStatus == JobStatus.FAILED
      }
      val completedJobs = jobsList.filter { it.jobStatus == JobStatus.COMPLETED }
      val cancelledJobs = jobsList.filter { it.jobStatus == JobStatus.CANCELLED }

      LazyColumn(
        modifier = Modifier
          .fillMaxSize()
          .padding(padding)
          .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
      ) {
        if (activeJobs.isNotEmpty()) {
          item(key = "header_active") {
            SectionHeader(title = stringResource(R.string.proc_section_active), count = activeJobs.size)
          }
          items(activeJobs, key = { it.id }) { job ->
            JobCard(
              job = job,
              onCancel = { jobManager?.cancelJob(job.id) },
              onDetails = { detailsJob = job },
              onRemove = { confirmRemoveJob = job }
            )
          }
        }

        if (queuedJobs.isNotEmpty()) {
          item(key = "header_queued") {
            SectionHeader(title = stringResource(R.string.proc_section_queued), count = queuedJobs.size)
          }
          items(queuedJobs, key = { it.id }) { job ->
            JobCard(
              job = job,
              onCancel = { jobManager?.cancelJob(job.id) },
              onDetails = { detailsJob = job },
              onRemove = { confirmRemoveJob = job }
            )
          }
        }

        if (attentionJobs.isNotEmpty()) {
          item(key = "header_attention") {
            SectionHeader(title = stringResource(R.string.proc_section_attention), count = attentionJobs.size)
          }
          items(attentionJobs, key = { it.id }) { job ->
            JobCard(
              job = job,
              onRetry = {
                // Check if input URI is readable
                val isReadable = runCatching {
                  if (job.inputUri.isBlank()) false
                  else {
                    context.contentResolver.openFileDescriptor(Uri.parse(job.inputUri), "r")?.use { true } ?: false
                  }
                }.getOrDefault(false)

                if (isReadable) {
                  scope.launch(Dispatchers.IO) {
                    jobRepo?.update(
                      job.copy(
                        status = JobStatus.QUEUED.name,
                        stage = "Queued",
                        errorMessage = null
                      )
                    )
                  }
                } else {
                  pendingRetryJob = job
                  reselectDocLauncher.launch(arrayOf("*/*"))
                }
              },
              onDetails = { detailsJob = job },
              onRemove = { confirmRemoveJob = job }
            )
          }
        }

        if (completedJobs.isNotEmpty()) {
          item(key = "header_completed") {
            SectionHeader(title = stringResource(R.string.proc_section_completed), count = completedJobs.size)
          }
          items(completedJobs, key = { it.id }) { job ->
            val outputFile = job.outputPath?.let { File(it) }
            val fileExists = outputFile != null && outputFile.exists() && outputFile.length() > 0

            JobCard(
              job = job,
              outputAvailable = fileExists,
              onSave = {
                if (outputFile != null && fileExists) {
                  targetSaveFile = outputFile
                  targetSaveJobId = job.id
                  saveLauncher.launch(job.outputName ?: outputFile.name)
                }
              },
              onShare = {
                if (outputFile != null && fileExists) {
                  val mime = FileLayer.getMimeType(outputFile)
                  FileLayer.shareFileAndNotify(context, outputFile, mime)
                }
              },
              onOpen = {
                if (outputFile != null && fileExists) {
                  val mime = FileLayer.getMimeType(outputFile)
                  FileLayer.openFileAndNotify(context, outputFile, mime)
                }
              },
              onDetails = { detailsJob = job },
              onRemove = { confirmRemoveJob = job }
            )
          }
        }

        if (cancelledJobs.isNotEmpty()) {
          item(key = "header_cancelled") {
            SectionHeader(title = stringResource(R.string.proc_section_cancelled), count = cancelledJobs.size)
          }
          items(cancelledJobs, key = { it.id }) { job ->
            JobCard(
              job = job,
              onDetails = { detailsJob = job },
              onRemove = { confirmRemoveJob = job }
            )
          }
        }

        item { Spacer(modifier = Modifier.height(32.dp)) }
      }
    }
  }

  // Details Dialog
  if (detailsJob != null) {
    val job = detailsJob!!
    val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    AlertDialog(
      onDismissRequest = { detailsJob = null },
      title = { Text(stringResource(R.string.proc_details_title)) },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
          Text(text = "Job ID: ${job.id.take(8)}…", style = MaterialTheme.typography.bodySmall)
          Text(text = "Input: ${job.inputName}", style = MaterialTheme.typography.bodySmall)
          if (job.inputSize > 0) {
            Text(text = "Input Size: ${FileFormatters.formatBytes(job.inputSize)}", style = MaterialTheme.typography.bodySmall)
          }
          Text(text = "Status: ${job.status}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
          Text(text = "Created: ${dateFormat.format(Date(job.createdAt))}", style = MaterialTheme.typography.bodySmall)
          job.startedAt?.let { Text(text = "Started: ${dateFormat.format(Date(it))}", style = MaterialTheme.typography.bodySmall) }
          job.finishedAt?.let { Text(text = "Finished: ${dateFormat.format(Date(it))}", style = MaterialTheme.typography.bodySmall) }
          if (job.outputSize > 0) {
            Text(text = "Output Size: ${FileFormatters.formatBytes(job.outputSize)}", style = MaterialTheme.typography.bodySmall)
          }
          if (!job.errorMessage.isNullOrBlank()) {
            Text(text = "Message: ${job.errorMessage}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
          }
          if (!job.errorDetail.isNullOrBlank()) {
            Text(text = "Technical Detail: ${job.errorDetail}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
          }
        }
      },
      confirmButton = {
        TextButton(onClick = { detailsJob = null }) {
          Text(stringResource(R.string.common_close))
        }
      }
    )
  }

  // Remove confirmation Dialog
  if (confirmRemoveJob != null) {
    val job = confirmRemoveJob!!
    val outputFile = job.outputPath?.let { File(it) }
    val hasUnsavedCopy = outputFile != null && outputFile.exists() && job.savedUri == null

    AlertDialog(
      onDismissRequest = { confirmRemoveJob = null },
      title = { Text(stringResource(R.string.proc_remove_title)) },
      text = {
        Text(
          if (hasUnsavedCopy) stringResource(R.string.proc_remove_warn_unsaved)
          else stringResource(R.string.proc_remove_confirm_message)
        )
      },
      confirmButton = {
        Button(
          onClick = {
            scope.launch(Dispatchers.IO) {
              jobRepo?.markRemovedFromList(job.id)
            }
            confirmRemoveJob = null
          }
        ) {
          Text(stringResource(R.string.common_remove))
        }
      },
      dismissButton = {
        TextButton(onClick = { confirmRemoveJob = null }) {
          Text(stringResource(R.string.common_cancel))
        }
      }
    )
  }
}

@Composable
private fun SectionHeader(title: String, count: Int) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .padding(top = 8.dp, bottom = 4.dp),
    verticalAlignment = Alignment.CenterVertically
  ) {
    Text(
      text = "$title ($count)",
      style = MaterialTheme.typography.titleSmall,
      fontWeight = FontWeight.Bold,
      color = MaterialTheme.colorScheme.primary
    )
  }
}

@Composable
private fun JobCard(
  job: JobEntity,
  outputAvailable: Boolean = true,
  onCancel: (() -> Unit)? = null,
  onRetry: (() -> Unit)? = null,
  onSave: (() -> Unit)? = null,
  onShare: (() -> Unit)? = null,
  onOpen: (() -> Unit)? = null,
  onDetails: (() -> Unit)? = null,
  onRemove: (() -> Unit)? = null
) {
  val tool = ToolRegistry.getById(job.toolId)
  val toolName = if (tool != null) stringResource(tool.nameRes) else "Tool #${job.toolId}"

  val (statusIcon, statusColor) = when (job.jobStatus) {
    JobStatus.QUEUED -> Icons.Default.HourglassTop to MaterialTheme.colorScheme.secondary
    JobStatus.PREPARING, JobStatus.PROCESSING -> Icons.Default.Autorenew to MaterialTheme.colorScheme.primary
    JobStatus.WAITING_FOR_INPUT -> Icons.Default.PauseCircleOutline to MaterialTheme.colorScheme.tertiary
    JobStatus.COMPLETED -> Icons.Default.CheckCircle to MaterialTheme.colorScheme.primary
    JobStatus.FAILED -> Icons.Default.Error to MaterialTheme.colorScheme.error
    JobStatus.CANCELLED -> Icons.Default.Cancel to MaterialTheme.colorScheme.onSurfaceVariant
    JobStatus.INTERRUPTED -> Icons.Default.SyncProblem to MaterialTheme.colorScheme.error
  }

  Card(
    modifier = Modifier.fillMaxWidth(),
    shape = RoundedCornerShape(12.dp),
    colors = CardDefaults.cardColors(
      containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    )
  ) {
    Column(
      modifier = Modifier.padding(14.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = toolName,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
          )
          Text(
            text = job.inputName,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
          )
        }

        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
          Icon(
            imageVector = statusIcon,
            contentDescription = job.status,
            tint = statusColor,
            modifier = Modifier.size(18.dp)
          )
          Text(
            text = job.status,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = statusColor
          )
        }
      }

      // Stage / Progress
      if (job.jobStatus == JobStatus.PROCESSING || job.jobStatus == JobStatus.PREPARING) {
        if (job.progressPercent in 0..100) {
          LinearProgressIndicator(
            progress = { job.progressPercent / 100f },
            modifier = Modifier.fillMaxWidth().height(6.dp),
          )
        } else {
          LinearProgressIndicator(
            modifier = Modifier.fillMaxWidth().height(6.dp)
          )
        }
      }

      Text(
        text = job.stage,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurface
      )

      if (!outputAvailable && job.jobStatus == JobStatus.COMPLETED) {
        Text(
          text = stringResource(R.string.proc_output_unavailable),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.error
        )
      }

      // Action row
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically
      ) {
        if (onCancel != null && (job.jobStatus == JobStatus.QUEUED || job.jobStatus == JobStatus.PREPARING || job.jobStatus == JobStatus.PROCESSING)) {
          TextButton(onClick = onCancel) {
            Icon(Icons.Default.Cancel, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text(stringResource(R.string.common_cancel))
          }
        }

        if (onRetry != null) {
          TextButton(onClick = onRetry) {
            Icon(Icons.Default.Replay, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text(stringResource(R.string.common_retry))
          }
        }

        if (job.jobStatus == JobStatus.COMPLETED && outputAvailable) {
          if (onSave != null) {
            IconButton(onClick = onSave) {
              Icon(Icons.Default.FileDownload, contentDescription = stringResource(R.string.common_save))
            }
          }
          if (onShare != null) {
            IconButton(onClick = onShare) {
              Icon(Icons.Default.Share, contentDescription = stringResource(R.string.common_share))
            }
          }
          if (onOpen != null) {
            IconButton(onClick = onOpen) {
              Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = stringResource(R.string.common_open))
            }
          }
        }

        if (onDetails != null) {
          IconButton(onClick = onDetails) {
            Icon(Icons.Default.Info, contentDescription = stringResource(R.string.proc_action_details))
          }
        }

        if (onRemove != null && (job.jobStatus == JobStatus.COMPLETED || job.jobStatus == JobStatus.CANCELLED || job.jobStatus == JobStatus.FAILED || job.jobStatus == JobStatus.INTERRUPTED)) {
          IconButton(onClick = onRemove) {
            Icon(Icons.Default.DeleteOutline, contentDescription = stringResource(R.string.common_remove))
          }
        }
      }
    }
  }
}
