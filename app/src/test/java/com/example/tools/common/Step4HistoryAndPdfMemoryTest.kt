package com.example.tools.common

import com.example.data.db.HistoryDao
import com.example.data.db.HistoryEntity
import com.example.data.repository.HistoryRepository
import com.example.pdf.PdfVerifier
import com.example.tools.pdf.PdfToImagesMath
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
import java.io.IOException

class Step4HistoryAndPdfMemoryTest {

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
  fun testFailedJobInsertsHistoryRecord() = runTest {
    val jobId = ToolJobRunner.start(
      toolId = 13,
      inputName = "corrupt.pdf",
      optionsJson = "{}"
    ) {
      throw IOException("Simulated engine failure")
    }

    // Wait until job transitions to FAILED
    val finalUi = ToolJobRunner.observe(jobId).filter { it.state == JobState.FAILED }.first()
    assertEquals(JobState.FAILED, finalUi.state)

    // Verify exactly ONE history row was inserted with status FAILED
    assertEquals(1, insertedEntities.size)
    val history = insertedEntities.first()
    assertEquals(13, history.toolId)
    assertEquals("corrupt.pdf", history.inputFileName)
    assertEquals("FAILED", history.status)
  }

  @Test
  fun testPdfVerifier_signatureCheck() {
    val validBytes = "%PDF-1.7".toByteArray(Charsets.US_ASCII)
    assertTrue(PdfVerifier.hasPdfSignature(validBytes))

    val invalidBytes = "NOT_A_PDF".toByteArray(Charsets.US_ASCII)
    assertFalse(PdfVerifier.hasPdfSignature(invalidBytes))

    val shortBytes = "%PD".toByteArray(Charsets.US_ASCII)
    assertFalse(PdfVerifier.hasPdfSignature(shortBytes))
  }

  @Test
  fun testPdfToImagesMath_pixelDimensions() {
    // 72 points @ 72 DPI -> 72 pixels
    val (w1, h1) = PdfToImagesMath.calculatePixelDimensions(72, 144, 72)
    assertEquals(72, w1)
    assertEquals(144, h1)

    // Standard letter (612 x 792) @ 150 DPI
    val (w2, h2) = PdfToImagesMath.calculatePixelDimensions(612, 792, 150)
    assertEquals(1275, w2)
    assertEquals(1650, h2)
  }
}
