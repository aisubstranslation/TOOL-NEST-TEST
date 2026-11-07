package com.example.data.repository

import com.example.data.db.JobDao
import com.example.data.db.JobEntity
import kotlinx.coroutines.flow.Flow

class JobRepository(private val jobDao: JobDao) {

  suspend fun insert(job: JobEntity) = jobDao.insertJob(job)

  suspend fun update(job: JobEntity) = jobDao.updateJob(job)

  suspend fun getById(id: String): JobEntity? = jobDao.getJobById(id)

  fun observeById(id: String): Flow<JobEntity?> = jobDao.observeJobById(id)

  fun observeVisibleJobs(): Flow<List<JobEntity>> = jobDao.observeVisibleJobs()

  fun observeActiveJobsCount(): Flow<Int> = jobDao.observeActiveJobsCount()

  suspend fun getStaleActiveJobs(): List<JobEntity> = jobDao.getStaleActiveJobs()

  suspend fun getUnsavedCompletedJobs(): List<JobEntity> = jobDao.getUnsavedCompletedJobs()

  suspend fun getAllJobs(): List<JobEntity> = jobDao.getAllJobs()

  suspend fun markRemovedFromList(id: String) = jobDao.markRemovedFromList(id)

  suspend fun markJobSaved(id: String, savedUri: String) = jobDao.markJobSaved(id, savedUri)

  suspend fun delete(id: String) = jobDao.deleteJobById(id)
}
