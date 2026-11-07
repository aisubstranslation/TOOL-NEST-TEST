package com.example.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "history")
data class HistoryEntity(
  @PrimaryKey(autoGenerate = true)
  val id: Long = 0,
  val toolId: Int,
  val inputFileName: String,
  val outputFileName: String?,
  val timestamp: Long = System.currentTimeMillis(),
  val status: String, // "SUCCESS", "FAILED", "CANCELLED"
  val outputSizeBytes: Long = 0L,
  val outputUri: String? = null
)
