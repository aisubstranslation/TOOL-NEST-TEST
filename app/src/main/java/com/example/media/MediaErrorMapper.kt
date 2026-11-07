package com.example.media

import java.io.FileNotFoundException
import java.io.IOException

object MediaErrorMapper {

  fun mapErrorToUserFriendlyMessage(throwable: Throwable?): String {
    if (throwable == null) return "An unexpected error occurred"
    val msg = throwable.message?.lowercase() ?: ""

    return when {
      throwable is SecurityException || throwable is FileNotFoundException ||
        msg.contains("permission") || msg.contains("enoent") ->
        "Cannot read selected file. Please select the file again."

      msg.contains("no audio") ->
        "This video has no audio track."

      msg.contains("cannot decode") || msg.contains("unsupported codec") ->
        throwable.message ?: "This phone cannot decode this media format."

      msg.contains("codec init") || msg.contains("mediacodec") || msg.contains("encoder init") ->
        "Codec initialization failed on this device."

      throwable is OutOfMemoryError || msg.contains("out of memory") ->
        "Out of memory while processing media. Please try a smaller file or close background apps."

      msg.contains("storage") || msg.contains("enospc") || msg.contains("not enough") ->
        throwable.message ?: "Not enough storage space available."

      msg.contains("cancel") ->
        "Job was cancelled."

      msg.contains("interrupted") || msg.contains("closed") ->
        "TOOLSNEST was closed or the phone restarted while this job was running. Restart will begin again from the start."

      else ->
        "Processing failed. Try a different quality or format."
    }
  }

  fun extractTechnicalDetails(throwable: Throwable?): String {
    if (throwable == null) return "Unknown error"
    val className = throwable.javaClass.simpleName
    val message = throwable.message ?: "No message"
    return "$className: $message"
  }
}
