package com.example.file

import android.content.Context
import java.io.File

/**
 * Central registry of all cache directory folder names used by TOOLSNEST for outputs.
 * Every directory listed here MUST have a matching cache-path declared in res/xml/file_paths.xml
 * so FileProvider can safely share and open output files without throwing IllegalArgumentException.
 */
object OutputDirs {
  const val DIR_TOOLSNEST = "toolsnest"
  const val DIR_JOBS = "jobs"
  const val DIR_CROP_OUTPUTS = "crop_outputs"

  val ALL_OUTPUT_DIRS: List<String> = listOf(
    DIR_TOOLSNEST,
    DIR_JOBS,
    DIR_CROP_OUTPUTS
  )

  fun getToolsnestDir(context: Context): File = File(context.cacheDir, DIR_TOOLSNEST).apply { mkdirs() }

  fun getJobsDir(context: Context): File = File(context.cacheDir, DIR_JOBS).apply { mkdirs() }

  fun getCropOutputsDir(context: Context): File = File(context.cacheDir, DIR_CROP_OUTPUTS).apply { mkdirs() }
}
