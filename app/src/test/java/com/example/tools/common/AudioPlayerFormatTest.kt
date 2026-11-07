package com.example.tools.common

import org.junit.Assert.assertEquals
import org.junit.Test

class AudioPlayerFormatTest {

  @Test
  fun testFormatDuration() {
    assertEquals("00:00", AudioPlayerFormat.formatDuration(0L))
    assertEquals("00:05", AudioPlayerFormat.formatDuration(5000L))
    assertEquals("01:05", AudioPlayerFormat.formatDuration(65000L))
    assertEquals("10:00", AudioPlayerFormat.formatDuration(600000L))
    assertEquals("01:00:00", AudioPlayerFormat.formatDuration(3600000L))
    assertEquals("01:01:05", AudioPlayerFormat.formatDuration(3665000L))
    assertEquals("00:00", AudioPlayerFormat.formatDuration(-500L))
  }
}
