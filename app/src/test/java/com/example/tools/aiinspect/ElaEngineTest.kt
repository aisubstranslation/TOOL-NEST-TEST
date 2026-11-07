package com.example.tools.aiinspect

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ElaEngineTest {

  @Test
  fun testIdenticalPixels_yieldsZeroDelta() {
    val pixels1 = intArrayOf(
      0xFF102030.toInt(), 0xFF405060.toInt(), 0xFF708090.toInt(), 0xFFA0B0C0.toInt()
    )
    val pixels2 = pixels1.clone()

    val (mean, max) = ElaEngine.calculatePurePixelDelta(pixels1, pixels2)
    assertEquals(0f, mean, 0.0001f)
    assertEquals(0f, max, 0.0001f)
  }

  @Test
  fun testModifiedPixels_yieldsPositiveDelta() {
    val pixels1 = intArrayOf(
      0xFF000000.toInt(), 0xFF000000.toInt()
    )
    val pixels2 = intArrayOf(
      0xFF303030.toInt(), 0xFF606060.toInt()
    )

    val (mean, max) = ElaEngine.calculatePurePixelDelta(pixels1, pixels2)
    assertTrue(mean > 0f)
    assertTrue(max >= 48f)
  }
}
