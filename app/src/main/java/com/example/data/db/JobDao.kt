package com.example.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface JobDao {

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertJob(job: JobEntity)

  @Update
  suspend fun updateJob(job: JobEntity)

  @Query("SELECT * FROM jobs WHERE id = :id LIMIT 1")
  suspend fun getJobById(id: String): JobEntity?

  @Query("SELECT * FROM jobs WHERE id = :id LIMIT 1")
  fun observeJobById(id: String): Flow<JobEntity?>

  @Query("SELECT * FROM jobs WHERE removedFromList = 0 ORDER BY createdAt DESC")
  fun observeVisibleJobs(): Flow<List<JobEntity>>

  @Query("SELECT COUNT(*) FROM jobs WHERE removedFromList = 0 AND status IN ('QUEUED', 'PREPARING', 'PROCESSING')")
  fun observeActiveJobsCount(): Flow<Int>

  @Query("SELECT * FROM jobs WHERE status IN ('PREPARING', 'PROCESSING', 'WAITING_FOR_INPUT')")
  suspend fun getStaleActiveJobs(): List<JobEntity>

  @Query("SELECT * FROM jobs WHERE removedFromList = 0 AND status = 'COMPLETED' AND savedUri IS NULL")
  suspend fun getUnsavedCompletedJobs(): List<JobEntity>

  @Query("SELECT * FROM jobs")
  suspend fun getAllJobs(): List<JobEntity>

  @Query("UPDATE jobs SET removedFromList = 1 WHERE id = :id")
  suspend fun markRemovedFromList(id: String)

  @Query("UPDATE jobs SET savedUri = :savedUri WHERE id = :id")
  suspend fun markJobSaved(id: String, savedUri: String)

  @Query("DELETE FROM jobs WHERE id = :id")
  suspend fun deleteJobById(id: String)
}
