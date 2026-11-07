package com.example.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
  entities = [HistoryEntity::class, JobEntity::class],
  version = 2,
  exportSchema = false
)
abstract class ToolsNestDatabase : RoomDatabase() {

  abstract fun historyDao(): HistoryDao
  abstract fun jobDao(): JobDao

  companion object {
    @Volatile
    private var INSTANCE: ToolsNestDatabase? = null

    val MIGRATION_1_2 = object : Migration(1, 2) {
      override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
          """
          CREATE TABLE IF NOT EXISTS `jobs` (
            `id` TEXT NOT NULL PRIMARY KEY,
            `toolId` INTEGER NOT NULL,
            `inputUri` TEXT NOT NULL,
            `inputName` TEXT NOT NULL,
            `inputSize` INTEGER NOT NULL,
            `optionsJson` TEXT NOT NULL,
            `stage` TEXT NOT NULL,
            `progressPercent` INTEGER NOT NULL,
            `status` TEXT NOT NULL,
            `outputPath` TEXT,
            `outputName` TEXT,
            `outputSize` INTEGER NOT NULL,
            `savedUri` TEXT,
            `errorMessage` TEXT,
            `errorDetail` TEXT,
            `createdAt` INTEGER NOT NULL,
            `startedAt` INTEGER,
            `finishedAt` INTEGER,
            `removedFromList` INTEGER NOT NULL
          )
          """.trimIndent()
        )
      }
    }

    fun getInstance(context: Context): ToolsNestDatabase {
      return INSTANCE ?: synchronized(this) {
        INSTANCE ?: Room.databaseBuilder(
          context.applicationContext,
          ToolsNestDatabase::class.java,
          "toolsnest.db"
        )
          .addMigrations(MIGRATION_1_2)
          .build()
          .also { INSTANCE = it }
      }
    }
  }
}
