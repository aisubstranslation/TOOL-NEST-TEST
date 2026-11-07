package com.example.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.db.HistoryDao
import com.example.data.db.HistoryEntity
import com.example.data.db.ToolsNestDatabase
import com.example.data.repository.HistoryRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class HistoryRepositoryTest {

  private lateinit var database: ToolsNestDatabase
  private lateinit var historyDao: HistoryDao
  private lateinit var repository: HistoryRepository

  @Before
  fun setUp() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    database = Room.inMemoryDatabaseBuilder(context, ToolsNestDatabase::class.java)
      .allowMainThreadQueries()
      .build()
    historyDao = database.historyDao()
    repository = HistoryRepository(historyDao)
  }

  @After
  fun tearDown() {
    database.close()
  }

  @Test
  fun testInsertAndOrderDescending() = runTest {
    val item1 = HistoryEntity(
      toolId = 1,
      inputFileName = "test1.jpg",
      outputFileName = "test1_out.jpg",
      timestamp = 1000L,
      status = "SUCCESS",
      outputSizeBytes = 5000L
    )
    val item2 = HistoryEntity(
      toolId = 2,
      inputFileName = "test2.png",
      outputFileName = "test2_out.png",
      timestamp = 2000L,
      status = "SUCCESS",
      outputSizeBytes = 8000L
    )

    repository.insert(item1)
    repository.insert(item2)

    val list = repository.allHistory.first()
    assertEquals(2, list.size)
    // Most recent timestamp should be first
    assertEquals(2000L, list[0].timestamp)
    assertEquals(1000L, list[1].timestamp)
    assertEquals(2, list[0].toolId)
  }

  @Test
  fun testDeleteById() = runTest {
    val id = repository.insert(
      HistoryEntity(
        toolId = 10,
        inputFileName = "doc.pdf",
        outputFileName = "doc_merged.pdf",
        timestamp = 1500L,
        status = "SUCCESS",
        outputSizeBytes = 12000L
      )
    )

    var list = repository.allHistory.first()
    assertEquals(1, list.size)

    repository.deleteById(id)
    list = repository.allHistory.first()
    assertTrue(list.isEmpty())
  }

  @Test
  fun testClearAll() = runTest {
    repository.insert(
      HistoryEntity(toolId = 1, inputFileName = "a.jpg", outputFileName = null, status = "FAILED")
    )
    repository.insert(
      HistoryEntity(toolId = 2, inputFileName = "b.jpg", outputFileName = null, status = "SUCCESS")
    )

    assertEquals(2, repository.allHistory.first().size)

    repository.clearAll()
    assertEquals(0, repository.allHistory.first().size)
  }

  @Test
  fun testRecentToolIds() = runTest {
    // Insert tool 1 at t=100
    repository.insert(
      HistoryEntity(toolId = 1, inputFileName = "a.jpg", outputFileName = null, timestamp = 100L, status = "SUCCESS")
    )
    // Insert tool 2 at t=200
    repository.insert(
      HistoryEntity(toolId = 2, inputFileName = "b.jpg", outputFileName = null, timestamp = 200L, status = "SUCCESS")
    )
    // Insert tool 1 again at t=300 (tool 1 should now be most recent)
    repository.insert(
      HistoryEntity(toolId = 1, inputFileName = "c.jpg", outputFileName = null, timestamp = 300L, status = "SUCCESS")
    )

    val recentIds = repository.recentToolIds.first()
    // Recent IDs should have tool 1 first (most recent at 300L), then tool 2 (at 200L)
    assertEquals(listOf(1, 2), recentIds)
  }
}
