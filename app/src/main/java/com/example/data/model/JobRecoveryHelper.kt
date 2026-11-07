package com.example.data.model

object JobRecoveryHelper {

  /**
   * Stale jobs in PREPARING, PROCESSING, or WAITING_FOR_INPUT must be transitioned to INTERRUPTED
   * upon app process restart. Terminal and queued states remain unchanged.
   */
  fun getRecoveredStatus(status: JobStatus): JobStatus {
    return when (status) {
      JobStatus.PREPARING,
      JobStatus.PROCESSING,
      JobStatus.WAITING_FOR_INPUT -> JobStatus.INTERRUPTED
      JobStatus.QUEUED,
      JobStatus.COMPLETED,
      JobStatus.FAILED,
      JobStatus.CANCELLED,
      JobStatus.INTERRUPTED -> status
    }
  }

  fun shouldRecover(status: JobStatus): Boolean {
    return status == JobStatus.PREPARING ||
      status == JobStatus.PROCESSING ||
      status == JobStatus.WAITING_FOR_INPUT
  }
}
