package com.example.data.model

enum class JobStatus {
  QUEUED,
  PREPARING,
  PROCESSING,
  WAITING_FOR_INPUT,
  COMPLETED,
  FAILED,
  CANCELLED,
  INTERRUPTED
}

object JobStatusTransitions {

  private val ALLOWED_TRANSITIONS: Map<JobStatus, Set<JobStatus>> = mapOf(
    JobStatus.QUEUED to setOf(
      JobStatus.PREPARING,
      JobStatus.CANCELLED,
      JobStatus.INTERRUPTED
    ),
    JobStatus.PREPARING to setOf(
      JobStatus.PROCESSING,
      JobStatus.FAILED,
      JobStatus.CANCELLED,
      JobStatus.INTERRUPTED,
      JobStatus.WAITING_FOR_INPUT
    ),
    JobStatus.PROCESSING to setOf(
      JobStatus.COMPLETED,
      JobStatus.FAILED,
      JobStatus.CANCELLED,
      JobStatus.INTERRUPTED
    ),
    JobStatus.WAITING_FOR_INPUT to setOf(
      JobStatus.QUEUED,
      JobStatus.CANCELLED
    ),
    JobStatus.INTERRUPTED to setOf(
      JobStatus.QUEUED
    ),
    JobStatus.FAILED to setOf(
      JobStatus.QUEUED
    ),
    JobStatus.CANCELLED to setOf(
      JobStatus.QUEUED
    ),
    JobStatus.COMPLETED to emptySet()
  )

  fun canTransition(from: JobStatus, to: JobStatus): Boolean {
    if (from == to) return true
    return ALLOWED_TRANSITIONS[from]?.contains(to) == true
  }

  fun transition(from: JobStatus, to: JobStatus): JobStatus {
    if (canTransition(from, to)) {
      return to
    }
    throw IllegalStateException("Illegal job status transition from $from to $to")
  }
}
