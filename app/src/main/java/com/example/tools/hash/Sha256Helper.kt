package com.example.tools.hash

import java.io.InputStream
import java.security.MessageDigest

object Sha256Helper {
  const val BUFFER_SIZE = 64 * 1024 // 64 KB buffer

  fun bytesToHex(bytes: ByteArray): String {
    val hexChars = CharArray(bytes.size * 2)
    val hexArray = "0123456789abcdef".toCharArray()
    for (i in bytes.indices) {
      val v = bytes[i].toInt() and 0xFF
      hexChars[i * 2] = hexArray[v ushr 4]
      hexChars[i * 2 + 1] = hexArray[v and 0x0F]
    }
    return String(hexChars)
  }

  fun compareHashes(actualHash: String, expectedHash: String): Boolean {
    val cleanActual = actualHash.replace(" ", "").trim()
    val cleanExpected = expectedHash.replace(" ", "").trim()
    if (cleanActual.isEmpty() || cleanExpected.isEmpty()) return false
    return cleanActual.equals(cleanExpected, ignoreCase = true)
  }

  fun formatChecksumLine(hash: String, fileName: String): String {
    return "$hash  $fileName"
  }

  fun formatChecksumLines(items: List<Pair<String, String>>): String {
    return items.joinToString("\n") { (hash, fileName) -> formatChecksumLine(hash, fileName) }
  }

  /**
   * Computes SHA-256 of an input stream using 64 KB buffer, calling onChunkRead after each buffer read.
   */
  suspend fun computeSha256(
    inputStream: InputStream,
    onChunkRead: (suspend (bytesReadInChunk: Long, totalReadSoFar: Long) -> Unit)? = null
  ): String {
    val digest = MessageDigest.getInstance("SHA-256")
    val buffer = ByteArray(BUFFER_SIZE)
    var bytesRead: Int
    var totalBytes = 0L

    while (inputStream.read(buffer).also { bytesRead = it } != -1) {
      if (bytesRead > 0) {
        digest.update(buffer, 0, bytesRead)
        totalBytes += bytesRead
        onChunkRead?.invoke(bytesRead.toLong(), totalBytes)
      }
    }

    return bytesToHex(digest.digest())
  }
}
