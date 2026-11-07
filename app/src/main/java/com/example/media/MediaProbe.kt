package com.example.media

import android.content.Context
import android.media.MediaCodecList
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import java.io.File
import java.io.IOException

data class MediaTrackInfo(
  val index: Int,
  val mime: String,
  val isAudio: Boolean,
  val isVideo: Boolean,
  val sampleRate: Int? = null,
  val channels: Int? = null,
  val language: String? = null,
  val bitrate: Int? = null,
  val width: Int? = null,
  val height: Int? = null
)

data class MediaProbeResult(
  val durationMs: Long = 0L,
  val width: Int = 0,
  val height: Int = 0,
  val rotationDegrees: Int = 0,
  val bitrate: Long = 0L,
  val frameRate: Float? = null,
  val hasAudio: Boolean = false,
  val hasVideo: Boolean = false,
  val audioMime: String? = null,
  val videoMime: String? = null,
  val tracks: List<MediaTrackInfo> = emptyList()
)

object MediaProbe {

  const val MAX_VIDEO_BYTES = 1000L * 1024L * 1024L // 1000 MB
  const val MAX_AUDIO_BYTES = 500L * 1024L * 1024L  // 500 MB

  fun probeMedia(context: Context, uri: Uri): MediaProbeResult {
    var retriever: MediaMetadataRetriever? = null
    var extractor: MediaExtractor? = null

    try {
      retriever = MediaMetadataRetriever()
      context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
        retriever.setDataSource(pfd.fileDescriptor)
      } ?: retriever.setDataSource(context, uri)

      val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
      val durationMs = durationStr?.toLongOrNull() ?: 0L

      val rawWidth = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
      val rawHeight = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
      val rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
      val bitrate = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toLongOrNull() ?: 0L

      val (displayWidth, displayHeight) = if (rotation == 90 || rotation == 270) {
        rawHeight to rawWidth
      } else {
        rawWidth to rawHeight
      }

      val trackList = mutableListOf<MediaTrackInfo>()
      extractor = MediaExtractor()
      context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
        extractor.setDataSource(pfd.fileDescriptor)
      } ?: extractor.setDataSource(context, uri, null)

      val numTracks = extractor.trackCount
      var hasAudio = false
      var hasVideo = false
      var primaryAudioMime: String? = null
      var primaryVideoMime: String? = null

      for (i in 0 until numTracks) {
        val format = extractor.getTrackFormat(i)
        val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
        val isAudio = mime.startsWith("audio/")
        val isVideo = mime.startsWith("video/")

        if (isAudio && !hasAudio) {
          hasAudio = true
          primaryAudioMime = mime
        }
        if (isVideo && !hasVideo) {
          hasVideo = true
          primaryVideoMime = mime
        }

        val sampleRate = if (format.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
          format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        } else null

        val channels = if (format.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
          format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
        } else null

        val lang = if (format.containsKey(MediaFormat.KEY_LANGUAGE)) {
          format.getString(MediaFormat.KEY_LANGUAGE)
        } else null

        val trackBitrate = if (format.containsKey(MediaFormat.KEY_BIT_RATE)) {
          format.getInteger(MediaFormat.KEY_BIT_RATE)
        } else null

        val trackW = if (format.containsKey(MediaFormat.KEY_WIDTH)) {
          format.getInteger(MediaFormat.KEY_WIDTH)
        } else null

        val trackH = if (format.containsKey(MediaFormat.KEY_HEIGHT)) {
          format.getInteger(MediaFormat.KEY_HEIGHT)
        } else null

        trackList.add(
          MediaTrackInfo(
            index = i,
            mime = mime,
            isAudio = isAudio,
            isVideo = isVideo,
            sampleRate = sampleRate,
            channels = channels,
            language = lang,
            bitrate = trackBitrate,
            width = trackW,
            height = trackH
          )
        )
      }

      return MediaProbeResult(
        durationMs = durationMs,
        width = displayWidth,
        height = displayHeight,
        rotationDegrees = rotation,
        bitrate = bitrate,
        hasAudio = hasAudio,
        hasVideo = hasVideo,
        audioMime = primaryAudioMime,
        videoMime = primaryVideoMime,
        tracks = trackList
      )
    } finally {
      try { retriever?.release() } catch (_: Exception) {}
      try { extractor?.release() } catch (_: Exception) {}
    }
  }

  /**
   * Validates that the device has a hardware/software decoder capable of decoding the format.
   */
  fun hasDecoderForFormat(format: MediaFormat): Boolean {
    val codecList = MediaCodecList(MediaCodecList.REGULAR_CODECS)
    return codecList.findDecoderForFormat(format) != null
  }

  fun hasDecoderForMime(mime: String): Boolean {
    val format = MediaFormat.createAudioFormat(mime, 44100, 2)
    val codecList = MediaCodecList(MediaCodecList.REGULAR_CODECS)
    return codecList.findDecoderForFormat(format) != null
  }
}
