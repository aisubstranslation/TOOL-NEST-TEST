package com.example.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.data.model.JobStatus

@Entity(tableName = "jobs")
data class JobEntity(
  @PrimaryKey
  val id: String,
  val toolId: Int,
  val inputUri: String,
  val inputName: String,
  val inputSize: Long,
  val optionsJson: String,
  val stage: String,
  val progressPercent: Int = -1,
  val status: String = JobStatus.QUEUED.name,
  val outputPath: String? = null,
  val outputName: String? = null,
  val outputSize: Long = 0L,
  val savedUri: String? = null,
  val errorMessage: String? = null,
  val errorDetail: String? = null,
  val createdAt: Long = System.currentTimeMillis(),
  val startedAt: Long? = null,
  val finishedAt: Long? = null,
  val removedFromList: Boolean = false
) {
  val jobStatus: JobStatus
    get() = runCatching { JobStatus.valueOf(status) }.getOrDefault(JobStatus.FAILED)
}
