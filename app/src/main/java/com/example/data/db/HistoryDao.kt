package com.example.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface HistoryDao {

  @Query("SELECT * FROM history ORDER BY timestamp DESC")
  fun getAllHistory(): Flow<List<HistoryEntity>>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insert(item: HistoryEntity): Long

  @Query("DELETE FROM history WHERE id = :id")
  suspend fun deleteById(id: Long)

  @Query("DELETE FROM history")
  suspend fun clearAll()

  @Query("SELECT toolId FROM history GROUP BY toolId ORDER BY MAX(timestamp) DESC LIMIT :limit")
  fun getRecentToolIds(limit: Int = 10): Flow<List<Int>>
}
