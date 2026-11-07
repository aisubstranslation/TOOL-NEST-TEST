package com.example.tools.common

import android.media.AudioAttributes
import android.media.MediaPlayer
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.R
import kotlinx.coroutines.delay
import java.io.File

object AudioPlayerFormat {
  /**
   * Formats a millisecond duration into standard mm:ss or hh:mm:ss format.
   */
  fun formatDuration(ms: Long): String {
    val totalSeconds = (ms.coerceAtLeast(0L) / 1000L)
    val hours = totalSeconds / 3600L
    val minutes = (totalSeconds % 3600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0) {
      String.format("%02d:%02d:%02d", hours, minutes, seconds)
    } else {
      String.format("%02d:%02d", minutes, seconds)
    }
  }
}

@Composable
fun AudioPlayerCard(
  file: File,
  modifier: Modifier = Modifier
) {
  val fileKey = remember(file) { "${file.absolutePath}:${file.lastModified()}" }

  var mediaPlayer by remember(fileKey) { mutableStateOf<MediaPlayer?>(null) }
  var isPrepared by remember(fileKey) { mutableStateOf(false) }
  var isError by remember(fileKey) { mutableStateOf(false) }
  var isPlaying by remember(fileKey) { mutableStateOf(false) }
  var durationMs by remember(fileKey) { mutableIntStateOf(0) }
  var currentPositionMs by remember(fileKey) { mutableIntStateOf(0) }
  var isDraggingSlider by remember(fileKey) { mutableStateOf(false) }
  var sliderPosition by remember(fileKey) { mutableFloatStateOf(0f) }

  // Lifecycle handling - pause playback when screen goes to background
  val lifecycleOwner = LocalLifecycleOwner.current
  DisposableEffect(lifecycleOwner, mediaPlayer) {
    val observer = LifecycleEventObserver { _, event ->
      if (event == Lifecycle.Event.ON_PAUSE) {
        mediaPlayer?.let { player ->
          if (player.isPlaying) {
            player.pause()
            isPlaying = false
          }
        }
      }
    }
    lifecycleOwner.lifecycle.addObserver(observer)
    onDispose {
      lifecycleOwner.lifecycle.removeObserver(observer)
    }
  }

  // MediaPlayer initialization and cleanup
  DisposableEffect(fileKey) {
    var player: MediaPlayer? = null
    try {
      player = MediaPlayer().apply {
        setAudioAttributes(
          AudioAttributes.Builder()
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .build()
        )
        setDataSource(file.absolutePath)
        setOnPreparedListener { mp ->
          isPrepared = true
          isError = false
          durationMs = mp.duration
        }
        setOnCompletionListener {
          isPlaying = false
          currentPositionMs = 0
          sliderPosition = 0f
        }
        setOnErrorListener { _, _, _ ->
          isError = true
          isPrepared = false
          isPlaying = false
          true
        }
        prepareAsync()
      }
      mediaPlayer = player
    } catch (_: Exception) {
      isError = true
      isPrepared = false
    }

    onDispose {
      try {
        player?.let {
          if (it.isPlaying) it.stop()
          it.reset()
          it.release()
        }
      } catch (_: Exception) {}
      mediaPlayer = null
      isPlaying = false
      isPrepared = false
    }
  }

  // Position updates every 200ms while playing
  LaunchedEffect(isPlaying, isDraggingSlider, mediaPlayer) {
    while (isPlaying && !isDraggingSlider) {
      val player = mediaPlayer
      if (player != null) {
        try {
          if (player.isPlaying) {
            val pos = player.currentPosition
            currentPositionMs = pos
            if (durationMs > 0) {
              sliderPosition = (pos.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
            }
          }
        } catch (_: Exception) {}
      }
      delay(200)
    }
  }

  Surface(
    modifier = modifier.fillMaxWidth(),
    shape = RoundedCornerShape(12.dp),
    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
  ) {
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .padding(12.dp)
    ) {
      if (isError) {
        Text(
          text = stringResource(R.string.common_audio_player_error),
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.error,
          modifier = Modifier.align(Alignment.Center)
        )
      } else if (!isPrepared) {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
          horizontalArrangement = Arrangement.Center,
          verticalAlignment = Alignment.CenterVertically
        ) {
          CircularProgressIndicator(
            modifier = Modifier.size(24.dp),
            strokeWidth = 2.dp
          )
        }
      } else {
        Column(modifier = Modifier.fillMaxWidth()) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
          ) {
            FilledIconButton(
              onClick = {
                val player = mediaPlayer ?: return@FilledIconButton
                try {
                  if (player.isPlaying) {
                    player.pause()
                    isPlaying = false
                  } else {
                    if (currentPositionMs >= durationMs && durationMs > 0) {
                      player.seekTo(0)
                      currentPositionMs = 0
                    }
                    player.start()
                    isPlaying = true
                  }
                } catch (_: Exception) {
                  isError = true
                }
              },
              modifier = Modifier
                .size(44.dp)
                .testTag("audio_play_pause_button")
                .semantics {
                  contentDescription = if (isPlaying) "Pause" else "Play"
                },
              shape = CircleShape,
              colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
              )
            ) {
              val isEnded = durationMs > 0 && currentPositionMs >= durationMs
              Icon(
                imageVector = when {
                  isEnded -> Icons.Default.Replay
                  isPlaying -> Icons.Default.Pause
                  else -> Icons.Default.PlayArrow
                },
                contentDescription = null
              )
            }

            Column(modifier = Modifier.weight(1f)) {
              Slider(
                value = if (isDraggingSlider) sliderPosition else {
                  if (durationMs > 0) (currentPositionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) else 0f
                },
                onValueChange = { newValue ->
                  isDraggingSlider = true
                  sliderPosition = newValue
                },
                onValueChangeFinished = {
                  isDraggingSlider = false
                  val seekTarget = (sliderPosition * durationMs).toInt()
                  currentPositionMs = seekTarget
                  try {
                    mediaPlayer?.seekTo(seekTarget)
                  } catch (_: Exception) {}
                },
                modifier = Modifier
                  .fillMaxWidth()
                  .testTag("audio_player_slider")
              )

              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
              ) {
                Text(
                  text = AudioPlayerFormat.formatDuration(
                    if (isDraggingSlider) (sliderPosition * durationMs).toLong() else currentPositionMs.toLong()
                  ),
                  style = MaterialTheme.typography.labelSmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                  text = AudioPlayerFormat.formatDuration(durationMs.toLong()),
                  style = MaterialTheme.typography.labelSmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant
                )
              }
            }
          }
        }
      }
    }
  }
}
