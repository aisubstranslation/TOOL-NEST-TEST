package com.example.file

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class Step5TempCleanupProtectionTest {

  @get:Rule
  val tempFolder = TemporaryFolder()

  @Test
  fun testCleanupTempFolder_protectsActiveAndUnsavedFiles() {
    val cacheDir = tempFolder.newFolder("cache")

    // Setup subdirectories matching OutputDirs
    val toolsnestDir = File(cacheDir, OutputDirs.DIR_TOOLSNEST).apply { mkdirs() }
    val jobsDir = File(cacheDir, OutputDirs.DIR_JOBS).apply { mkdirs() }
    val cropDir = File(cacheDir, OutputDirs.DIR_CROP_OUTPUTS).apply { mkdirs() }

    // Sub-folder under jobs to test nested directory traversal and cleanup
    val jobSubDir = File(jobsDir, "job_timestamp_123").apply { mkdirs() }
    val emptySubDir = File(cropDir, "empty_child_dir").apply { mkdirs() }

    val activeText = "active-in-progress-or-unsaved-data"
    val staleToolsnestText = "stale-data-1"
    val staleCropText = "stale-data-2"
    val staleSubDirText = "subfolder-data-3"

    val activeBytes = activeText.toByteArray().size.toLong() // 34L
    val staleToolsnestBytes = staleToolsnestText.toByteArray().size.toLong() // 12L
    val staleCropBytes = staleCropText.toByteArray().size.toLong() // 12L
    val staleSubDirBytes = staleSubDirText.toByteArray().size.toLong() // 16L

    val expectedInitialSize = activeBytes + staleToolsnestBytes + staleCropBytes + staleSubDirBytes // 34 + 12 + 12 + 16 = 74L
    val expectedFreedBytes = staleToolsnestBytes + staleCropBytes + staleSubDirBytes // 12 + 12 + 16 = 40L
    val expectedRemainingSize = activeBytes // 34L

    // Create active/protected file
    val activeJobFile = File(jobsDir, "active_job_output.m4a").apply {
      writeText(activeText)
    }

    // Create unprotected stale files
    val staleToolsnestFile = File(toolsnestDir, "stale_temp.jpg").apply {
      writeText(staleToolsnestText)
    }
    val staleCropFile = File(cropDir, "stale_crop.png").apply {
      writeText(staleCropText)
    }
    val staleSubDirFile = File(jobSubDir, "sub_temp.pcm").apply {
      writeText(staleSubDirText)
    }

    val initialSize = FileLayer.getTempFolderSize(cacheDir)
    assertEquals(expectedInitialSize, initialSize)
    assertEquals(74L, initialSize)

    // Run cleanup protecting activeJobFile path and name
    val protectedPaths = setOf(activeJobFile.absolutePath, activeJobFile.name)
    val freedBytes = FileLayer.cleanupTempFolder(cacheDir, protectedPaths)

    assertEquals(expectedFreedBytes, freedBytes)
    assertEquals(40L, freedBytes)

    // Assert protected file still exists
    assertTrue("Protected active job output must NOT be deleted", activeJobFile.exists())

    // Assert stale files were cleaned
    assertFalse("Unprotected temp file must be deleted", staleToolsnestFile.exists())
    assertFalse("Unprotected crop file must be deleted", staleCropFile.exists())
    assertFalse("Unprotected subfolder file must be deleted", staleSubDirFile.exists())

    // Assert empty sub-folders are cleaned
    assertFalse("Empty sub-folder must be deleted", emptySubDir.exists())
    assertFalse("Sub-folder whose children were all deleted must be cleaned", jobSubDir.exists())

    // Root output dirs themselves must NOT be deleted
    assertTrue("Root toolsnest dir must remain", toolsnestDir.exists())
    assertTrue("Root jobs dir must remain", jobsDir.exists())
    assertTrue("Root crop_outputs dir must remain", cropDir.exists())

    // Assert remaining size matches protected file length
    val remainingSize = FileLayer.getTempFolderSize(cacheDir)
    assertEquals(expectedRemainingSize, remainingSize)
    assertEquals(34L, remainingSize)
    assertEquals(activeJobFile.length(), remainingSize)
  }
}
