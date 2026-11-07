package com.example.data.repository

import com.example.data.db.HistoryDao
import com.example.data.db.HistoryEntity
import kotlinx.coroutines.flow.Flow

class HistoryRepository(
  private val historyDao: HistoryDao
) {

  val allHistory: Flow<List<HistoryEntity>> = historyDao.getAllHistory()

  val recentToolIds: Flow<List<Int>> = historyDao.getRecentToolIds()

  suspend fun insert(item: HistoryEntity): Long {
    return historyDao.insert(item)
  }

  suspend fun deleteById(id: Long) {
    historyDao.deleteById(id)
  }

  suspend fun clearAll() {
    historyDao.clearAll()
  }
}
