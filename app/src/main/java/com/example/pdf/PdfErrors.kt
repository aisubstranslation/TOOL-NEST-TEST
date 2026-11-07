package com.example.pdf

import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import java.io.IOException

object PdfErrors {

  const val MSG_PASSWORD_PROTECTED =
    "This PDF is password-protected. TOOLSNEST cannot process protected PDFs yet. Remove the password in another app first."

  const val MSG_DAMAGED_PDF =
    "This PDF looks damaged or is not a valid PDF."

  const val MSG_OUT_OF_MEMORY =
    "Out of memory while processing PDF. Please try a smaller file or close background apps."

  const val MSG_NOT_ENOUGH_STORAGE =
    "Not enough storage space available on the device (at least 1.2x input size required)."

  const val MSG_EXCEEDS_SIZE_LIMIT =
    "This PDF exceeds the maximum allowed size of 150 MB."

  const val MSG_EXCEEDS_COMBINED_LIMIT =
    "Combined PDF size exceeds the 300 MB limit for devices with 2 GB RAM."

  fun mapPdfException(throwable: Throwable): String {
    val message = throwable.message?.lowercase() ?: ""
    return when {
      throwable is InvalidPasswordException ||
          message.contains("password") ||
          message.contains("encrypted") -> MSG_PASSWORD_PROTECTED

      throwable is OutOfMemoryError || message.contains("out of memory") -> MSG_OUT_OF_MEMORY

      throwable is IOException ||
          message.contains("format error") ||
          message.contains("header") ||
          message.contains("eof") ||
          message.contains("corrupt") -> MSG_DAMAGED_PDF

      else -> throwable.localizedMessage?.takeIf { it.isNotBlank() } ?: MSG_DAMAGED_PDF
    }
  }
}
