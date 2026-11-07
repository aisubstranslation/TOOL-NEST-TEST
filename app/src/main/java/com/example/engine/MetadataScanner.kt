package com.example.engine

data class ScannedMetadata(
  val format: String,
  val hasExif: Boolean = false,
  val hasXmp: Boolean = false,
  val hasGps: Boolean = false,
  val hasCameraInfo: Boolean = false,
  val cameraMakeModel: String? = null,
  val dateTaken: String? = null,
  val software: String? = null,
  val hasComments: Boolean = false,
  val comments: List<String> = emptyList(),
  val hasC2paOrJumbf: Boolean = false,
  val hasIptcPhotoshop: Boolean = false,
  val hasAiGenerationMarkers: Boolean = false,
  val detectedSegments: List<String> = emptyList()
) {
  val hasAnyMetadata: Boolean
    get() = hasExif || hasXmp || hasGps || hasCameraInfo || hasComments ||
        hasC2paOrJumbf || hasIptcPhotoshop || hasAiGenerationMarkers || detectedSegments.isNotEmpty()
}

object MetadataScanner {

  fun scan(bytes: ByteArray): ScannedMetadata {
    if (bytes.size < 4) {
      return ScannedMetadata(format = "UNKNOWN")
    }

    return when {
      // JPEG
      ImageMath.verifyMagicBytes(bytes, "jpeg") -> scanJpeg(bytes)
      // PNG
      ImageMath.verifyMagicBytes(bytes, "png") -> scanPng(bytes)
      // WebP
      ImageMath.verifyMagicBytes(bytes, "webp") -> scanWebp(bytes)
      else -> ScannedMetadata(format = "UNKNOWN")
    }
  }

  private fun scanJpeg(bytes: ByteArray): ScannedMetadata {
    var hasExif = false
    var hasXmp = false
    var hasGps = false
    var hasCameraInfo = false
    var cameraMakeModel: String? = null
    var dateTaken: String? = null
    var software: String? = null
    var hasComments = false
    val comments = mutableListOf<String>()
    var hasC2pa = false
    var hasIptc = false
    var hasAiMarkers = false
    val segments = mutableListOf<String>()

    var pos = 2 // Skip 0xFF, 0xD8
    while (pos < bytes.size - 1) {
      if ((bytes[pos].toInt() and 0xFF) != 0xFF) {
        pos++
        continue
      }

      val marker = bytes[pos + 1].toInt() and 0xFF
      pos += 2

      // Stop scanning on SOS (Start of Scan) or EOI (End of Image)
      if (marker == 0xDA || marker == 0xD9) {
        break
      }

      // Standalone markers without payload
      if (marker in 0xD0..0xD7 || marker == 0x01 || marker == 0xFF) {
        continue
      }

      if (pos + 2 > bytes.size) break

      val length = ((bytes[pos].toInt() and 0xFF) shl 8) or (bytes[pos + 1].toInt() and 0xFF)
      val payloadStart = pos + 2
      val payloadEnd = minOf(bytes.size, pos + length)
      val payloadSize = maxOf(0, payloadEnd - payloadStart)

      if (payloadSize > 0) {
        val payload = bytes.copyOfRange(payloadStart, payloadEnd)
        val payloadStr = String(payload, Charsets.ISO_8859_1)

        when (marker) {
          // APP1: Exif or XMP
          0xE1 -> {
            if (payloadStr.startsWith("Exif\u0000\u0000")) {
              hasExif = true
              segments.add("APP1 (Exif)")
              if (payloadStr.contains("GPS")) {
                hasGps = true
              }
              // Quick check for common camera tags in Exif ASCII
              val cameraKeywords = listOf("Canon", "Nikon", "Sony", "Apple", "Samsung", "Google", "OnePlus", "Xiaomi")
              for (kw in cameraKeywords) {
                if (payloadStr.contains(kw)) {
                  hasCameraInfo = true
                  cameraMakeModel = kw
                  break
                }
              }
            } else if (payloadStr.contains("http://ns.adobe.com/xap/1.0/")) {
              hasXmp = true
              segments.add("APP1 (XMP)")
              if (payloadStr.contains("DigitalSourceType") || payloadStr.contains("trainedAlgorithmicMedia")) {
                hasAiMarkers = true
              }
            }
          }
          // APP11: JUMBF / C2PA
          0xEB -> {
            hasC2pa = true
            segments.add("APP11 (JUMBF / C2PA)")
          }
          // APP13: Photoshop / IPTC
          0xED -> {
            hasIptc = true
            segments.add("APP13 (Photoshop / IPTC)")
          }
          // COM: Comment
          0xFE -> {
            hasComments = true
            segments.add("COM (Comment)")
            comments.add(payloadStr.trim())
          }
        }
      }

      pos += length
    }

    return ScannedMetadata(
      format = "JPEG",
      hasExif = hasExif,
      hasXmp = hasXmp,
      hasGps = hasGps,
      hasCameraInfo = hasCameraInfo,
      cameraMakeModel = cameraMakeModel,
      dateTaken = dateTaken,
      software = software,
      hasComments = hasComments,
      comments = comments,
      hasC2paOrJumbf = hasC2pa,
      hasIptcPhotoshop = hasIptc,
      hasAiGenerationMarkers = hasAiMarkers,
      detectedSegments = segments
    )
  }

  private fun scanPng(bytes: ByteArray): ScannedMetadata {
    var hasExif = false
    var hasXmp = false
    var hasComments = false
    val comments = mutableListOf<String>()
    var hasC2pa = false
    var hasAiMarkers = false
    val segments = mutableListOf<String>()

    var pos = 8 // Skip PNG 8-byte header
    while (pos + 8 <= bytes.size) {
      val length = ((bytes[pos].toInt() and 0xFF) shl 24) or
          ((bytes[pos + 1].toInt() and 0xFF) shl 16) or
          ((bytes[pos + 2].toInt() and 0xFF) shl 8) or
          (bytes[pos + 3].toInt() and 0xFF)
      val type = String(bytes, pos + 4, 4, Charsets.US_ASCII)
      val payloadStart = pos + 8
      val payloadEnd = minOf(bytes.size, payloadStart + maxOf(0, length))

      if (payloadEnd <= bytes.size && length >= 0) {
        val payloadStr = if (length > 0) String(bytes, payloadStart, payloadEnd - payloadStart, Charsets.ISO_8859_1) else ""

        when (type) {
          "tEXt", "iTXt", "zTXt" -> {
            hasComments = true
            segments.add("PNG $type")
            if (payloadStr.contains("XML:com.adobe.xmp") || payloadStr.contains("http://ns.adobe.com/xap/1.0/")) {
              hasXmp = true
            }
            if (payloadStr.contains("DigitalSourceType") || payloadStr.contains("trainedAlgorithmicMedia")) {
              hasAiMarkers = true
            }
            comments.add(payloadStr.take(100))
          }
          "eXIf" -> {
            hasExif = true
            segments.add("PNG eXIf")
          }
          "caBX" -> {
            hasC2pa = true
            segments.add("PNG caBX (C2PA)")
          }
        }
      }

      // Next chunk: length + 4(type) + 4(CRC) + length
      pos += 12 + maxOf(0, length)
    }

    return ScannedMetadata(
      format = "PNG",
      hasExif = hasExif,
      hasXmp = hasXmp,
      hasComments = hasComments,
      comments = comments,
      hasC2paOrJumbf = hasC2pa,
      hasAiGenerationMarkers = hasAiMarkers,
      detectedSegments = segments
    )
  }

  private fun scanWebp(bytes: ByteArray): ScannedMetadata {
    var hasExif = false
    var hasXmp = false
    var hasC2pa = false
    var hasAiMarkers = false
    val segments = mutableListOf<String>()

    var pos = 12 // Skip RIFF (4) + Size (4) + WEBP (4)
    while (pos + 8 <= bytes.size) {
      val fourCC = String(bytes, pos, 4, Charsets.US_ASCII)
      val chunkSize = ((bytes[pos + 4].toInt() and 0xFF)) or
          ((bytes[pos + 5].toInt() and 0xFF) shl 8) or
          ((bytes[pos + 6].toInt() and 0xFF) shl 16) or
          ((bytes[pos + 7].toInt() and 0xFF) shl 24)

      val payloadStart = pos + 8
      val payloadEnd = minOf(bytes.size, payloadStart + maxOf(0, chunkSize))

      if (payloadEnd <= bytes.size && chunkSize > 0) {
        val payloadStr = String(bytes, payloadStart, payloadEnd - payloadStart, Charsets.ISO_8859_1)

        when (fourCC.trim()) {
          "EXIF" -> {
            hasExif = true
            segments.add("WebP EXIF")
          }
          "XMP" -> {
            hasXmp = true
            segments.add("WebP XMP")
            if (payloadStr.contains("DigitalSourceType") || payloadStr.contains("trainedAlgorithmicMedia")) {
              hasAiMarkers = true
            }
          }
          "C2PA" -> {
            hasC2pa = true
            segments.add("WebP C2PA")
          }
        }
      }

      val paddedSize = if (chunkSize % 2 == 1) chunkSize + 1 else chunkSize
      pos += 8 + maxOf(0, paddedSize)
    }

    return ScannedMetadata(
      format = "WEBP",
      hasExif = hasExif,
      hasXmp = hasXmp,
      hasC2paOrJumbf = hasC2pa,
      hasAiGenerationMarkers = hasAiMarkers,
      detectedSegments = segments
    )
  }
}
