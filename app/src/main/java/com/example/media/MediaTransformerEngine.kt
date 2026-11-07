package com.example.media

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Presentation
import androidx.media3.transformer.AudioEncoderSettings
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.VideoEncoderSettings
import com.example.R
import com.example.tools.common.JobContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.File

enum class VideoAudioMode {
  KEEP_ORIGINAL,
  COMPRESS_AAC_96KBPS,
  REMOVE_AUDIO
}

@androidx.annotation.OptIn(UnstableApi::class)
object MediaTransformerEngine {

  /**
   * Converts or trims an audio or video file into an AAC M4A file with removeVideo=true.
   */
  suspend fun exportToAacM4a(
    context: Context,
    inputUri: Uri,
    outputFile: File,
    bitrateBps: Int? = null,
    startMs: Long = 0L,
    endMs: Long? = null,
    jobContext: JobContext? = null,
    onProgress: (suspend (percent: Int?, stage: String) -> Unit)? = null
  ) = withContext(Dispatchers.Main) {
    outputFile.parentFile?.mkdirs()

    val mediaItemBuilder = MediaItem.Builder().setUri(inputUri)
    if (startMs > 0L || (endMs != null && endMs > 0L)) {
      val clipping = MediaItem.ClippingConfiguration.Builder().apply {
        if (startMs > 0L) setStartPositionMs(startMs)
        if (endMs != null && endMs > 0L) setEndPositionMs(endMs)
      }.build()
      mediaItemBuilder.setClippingConfiguration(clipping)
    }
    val mediaItem = mediaItemBuilder.build()

    val editedMediaItem = EditedMediaItem.Builder(mediaItem)
      .setRemoveVideo(true)
      .build()

    val completionDeferred = CompletableDeferred<Unit>()

    val listener = object : Transformer.Listener {
      override fun onCompleted(composition: Composition, exportResult: ExportResult) {
        completionDeferred.complete(Unit)
      }

      override fun onError(
        composition: Composition,
        exportResult: ExportResult,
        exportException: ExportException
      ) {
        completionDeferred.completeExceptionally(exportException)
      }
    }

    val transformerBuilder = Transformer.Builder(context)
      .setAudioMimeType(MimeTypes.AUDIO_AAC)
      .addListener(listener)

    if (bitrateBps != null && bitrateBps > 0) {
      val audioSettings = AudioEncoderSettings.Builder()
        .setBitrate(bitrateBps)
        .build()
      val encoderFactory = DefaultEncoderFactory.Builder(context)
        .setRequestedAudioEncoderSettings(audioSettings)
        .build()
      transformerBuilder.setEncoderFactory(encoderFactory)
    }

    val transformer = transformerBuilder.build()
    val outputPath = outputFile.absolutePath

    transformer.start(editedMediaItem, outputPath)

    val progressHolder = ProgressHolder()

    try {
      while (!completionDeferred.isCompleted) {
        jobContext?.ensureActive()
        val state = transformer.getProgress(progressHolder)
        if (state == Transformer.PROGRESS_STATE_AVAILABLE) {
          val pct = progressHolder.progress.coerceIn(0, 99)
          onProgress?.invoke(pct, context.getString(R.string.media_stage_processing_aac_pct, pct))
        } else {
          onProgress?.invoke(null, context.getString(R.string.media_stage_processing_aac))
        }

        withContext(Dispatchers.Default) {
          delay(400)
        }
      }

      completionDeferred.await()
      onProgress?.invoke(100, context.getString(R.string.media_stage_processing_complete))
    } catch (e: Exception) {
      try {
        transformer.cancel()
      } catch (_: Exception) {}
      if (outputFile.exists()) {
        try { outputFile.delete() } catch (_: Exception) {}
      }
      throw e
    }
  }

  /**
   * Compresses a video using H.264 (video/avc) with optional resolution downscaling,
   * bitrate limits, tone mapping for HDR sources, and audio handling.
   */
  suspend fun compressVideo(
    context: Context,
    inputUri: Uri,
    outputFile: File,
    targetWidth: Int? = null,
    targetHeight: Int? = null,
    videoBitrateBps: Int,
    audioMode: VideoAudioMode = VideoAudioMode.KEEP_ORIGINAL,
    jobContext: JobContext? = null,
    onProgress: (suspend (percent: Int?, stage: String) -> Unit)? = null
  ) = withContext(Dispatchers.Main) {
    outputFile.parentFile?.mkdirs()

    val mediaItem = MediaItem.fromUri(inputUri)
    val editedMediaItemBuilder = EditedMediaItem.Builder(mediaItem)

    if (audioMode == VideoAudioMode.REMOVE_AUDIO) {
      editedMediaItemBuilder.setRemoveAudio(true)
    }

    if (targetWidth != null && targetHeight != null) {
      val presentation = Presentation.createForWidthAndHeight(
        targetWidth,
        targetHeight,
        Presentation.LAYOUT_SCALE_TO_FIT
      )
      editedMediaItemBuilder.setEffects(
        Effects(emptyList(), listOf(presentation))
      )
    }

    val editedMediaItem = editedMediaItemBuilder.build()

    val completionDeferred = CompletableDeferred<Unit>()

    val listener = object : Transformer.Listener {
      override fun onCompleted(composition: Composition, exportResult: ExportResult) {
        completionDeferred.complete(Unit)
      }

      override fun onError(
        composition: Composition,
        exportResult: ExportResult,
        exportException: ExportException
      ) {
        completionDeferred.completeExceptionally(exportException)
      }
    }

    val transformerBuilder = Transformer.Builder(context)
      .setVideoMimeType(MimeTypes.VIDEO_H264)
      .addListener(listener)

    val videoEncoderSettings = VideoEncoderSettings.Builder()
      .setBitrate(videoBitrateBps)
      .build()

    val encoderFactoryBuilder = DefaultEncoderFactory.Builder(context)
      .setRequestedVideoEncoderSettings(videoEncoderSettings)
      .setEnableFallback(true)

    if (audioMode == VideoAudioMode.COMPRESS_AAC_96KBPS) {
      transformerBuilder.setAudioMimeType(MimeTypes.AUDIO_AAC)
      val audioSettings = AudioEncoderSettings.Builder()
        .setBitrate(96_000)
        .build()
      encoderFactoryBuilder.setRequestedAudioEncoderSettings(audioSettings)
    }

    transformerBuilder.setEncoderFactory(encoderFactoryBuilder.build())
    val transformer = transformerBuilder.build()
    val outputPath = outputFile.absolutePath

    val composition = Composition.Builder(EditedMediaItemSequence(editedMediaItem))
      .setHdrMode(Composition.HDR_MODE_TONE_MAP_HDR_TO_SDR_USING_OPEN_GL)
      .build()

    transformer.start(composition, outputPath)

    val progressHolder = ProgressHolder()

    try {
      while (!completionDeferred.isCompleted) {
        jobContext?.ensureActive()
        val state = transformer.getProgress(progressHolder)
        if (state == Transformer.PROGRESS_STATE_AVAILABLE) {
          val pct = progressHolder.progress.coerceIn(0, 99)
          onProgress?.invoke(pct, context.getString(R.string.media_stage_compressing_video_pct, pct))
        } else {
          onProgress?.invoke(null, context.getString(R.string.media_stage_compressing_video))
        }

        withContext(Dispatchers.Default) {
          delay(400)
        }
      }

      completionDeferred.await()
      onProgress?.invoke(100, context.getString(R.string.media_stage_compressing_complete))
    } catch (e: Exception) {
      try {
        transformer.cancel()
      } catch (_: Exception) {}
      if (outputFile.exists()) {
        try { outputFile.delete() } catch (_: Exception) {}
      }
      throw e
    }
  }
}
