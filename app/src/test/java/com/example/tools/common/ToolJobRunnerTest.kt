package com.example.tools.common

import com.example.data.db.HistoryDao
import com.example.data.db.HistoryEntity
import com.example.data.repository.HistoryRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ToolJobRunnerTest {

  private lateinit var testScope: CoroutineScope
  private val insertedEntities = mutableListOf<HistoryEntity>()

  private val fakeDao = object : HistoryDao {
    override fun getAllHistory(): Flow<List<HistoryEntity>> = flowOf(insertedEntities)

    override suspend fun insert(item: HistoryEntity): Long {
      insertedEntities.add(item)
      return insertedEntities.size.toLong()
    }

    override suspend fun deleteById(id: Long) {
      insertedEntities.removeAll { it.id == id }
    }

    override suspend fun clearAll() {
      insertedEntities.clear()
    }

    override fun getRecentToolIds(limit: Int): Flow<List<Int>> = flowOf(emptyList())
  }

  private val fakeRepository = HistoryRepository(fakeDao)

  @Before
  fun setUp() {
    testScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    ToolJobRunner.customScope = testScope
    ToolJobRunner.customRepository = fakeRepository
    insertedEntities.clear()
  }

  @After
  fun tearDown() {
    testScope.cancel()
    ToolJobRunner.customScope = null
    ToolJobRunner.customRepository = null
  }

  @Test
  fun testRefusesSecondJobWhileRunning() = runTest {
    val sync = CompletableDeferred<Unit>()
    var secondExecuted = false
    val jobId1 = ToolJobRunner.start(
      toolId = 1,
      inputName = "first.jpg",
      optionsJson = "{}"
    ) {
      sync.await()
      JobResult(emptyList())
    }

    assertTrue(ToolJobRunner.isToolBusy(1))

    // Starting a second job for the same toolId returns the existing jobId and does not throw
    val jobId2 = ToolJobRunner.start(
      toolId = 1,
      inputName = "second.jpg",
      optionsJson = "{}"
    ) {
      secondExecuted = true
      JobResult(emptyList())
    }

    assertEquals(jobId1, jobId2)
    assertFalse(secondExecuted)

    ToolJobRunner.cancel(jobId1)
    sync.complete(Unit)
  }

  @Test
  fun testJobCancellation() = runTest {
    val sync = CompletableDeferred<Unit>()
    val jobId = ToolJobRunner.start(
      toolId = 2,
      inputName = "test.png",
      optionsJson = "{}"
    ) {
      sync.await()
      JobResult(emptyList())
    }

    ToolJobRunner.cancel(jobId)
    sync.complete(Unit)

    val finalState = ToolJobRunner.observe(jobId)
      .filter { it.state == JobState.CANCELLED }
      .first()

    assertEquals(JobState.CANCELLED, finalState.state)
  }

  @Test
  fun testHistoryLoggedPerFileResult() = runTest {
    val file1 = FileResult(
      inputName = "img1.jpg",
      outputName = "img1_out.jpg",
      outputUri = "/cache/img1_out.jpg",
      outputSizeBytes = 1200L,
      success = true
    )
    val file2 = FileResult(
      inputName = "img2.jpg",
      outputName = null,
      outputUri = null,
      outputSizeBytes = 0L,
      success = false,
      userMessage = "Failed"
    )

    val jobId = ToolJobRunner.start(
      toolId = 3,
      inputName = "batch",
      optionsJson = "{}"
    ) {
      JobResult(listOf(file1, file2))
    }

    ToolJobRunner.observe(jobId)
      .filter { it.state == JobState.FAILED || it.state == JobState.SUCCESS }
      .first()

    assertEquals(2, insertedEntities.size)
    assertEquals(3, insertedEntities[0].toolId)
    assertEquals("SUCCESS", insertedEntities[0].status)
    assertEquals("FAILED", insertedEntities[1].status)
  }
}
