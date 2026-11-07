package com.example.tools.pdfeditor

import org.junit.Assert.assertEquals
import org.junit.Test

class PdfCoordMapperTest {

  @Test
  fun testRotationNormalization() {
    assertEquals(0, PdfCoordMapper.normalizeRotation(0))
    assertEquals(90, PdfCoordMapper.normalizeRotation(90))
    assertEquals(180, PdfCoordMapper.normalizeRotation(180))
    assertEquals(270, PdfCoordMapper.normalizeRotation(270))
    assertEquals(0, PdfCoordMapper.normalizeRotation(360))
    assertEquals(90, PdfCoordMapper.normalizeRotation(450))
    assertEquals(270, PdfCoordMapper.normalizeRotation(-90))
    assertEquals(180, PdfCoordMapper.normalizeRotation(-180))
  }

  @Test
  fun testDisplayToUser_rotation0_nonZeroOrigin() {
    val geom = PdfCoordMapper.PageGeometry(
      minX = 50f,
      minY = 100f,
      width = 500f,
      height = 800f,
      rotation = 0
    )

    // Top-left (0,0) in display -> (minX, minY + height) in user space
    val (tlX, tlY) = PdfCoordMapper.mapPoint(0f, 0f, geom)
    assertEquals(50f, tlX, 0.001f)
    assertEquals(900f, tlY, 0.001f)

    // Bottom-right (1,1) in display -> (minX + width, minY) in user space
    val (brX, brY) = PdfCoordMapper.mapPoint(1f, 1f, geom)
    assertEquals(550f, brX, 0.001f)
    assertEquals(100f, brY, 0.001f)

    // Center (0.5, 0.5)
    val (cX, cY) = PdfCoordMapper.mapPoint(0.5f, 0.5f, geom)
    assertEquals(300f, cX, 0.001f)
    assertEquals(500f, cY, 0.001f)
  }

  @Test
  fun testDisplayToUser_rotation90() {
    val geom = PdfCoordMapper.PageGeometry(
      minX = 0f,
      minY = 0f,
      width = 600f,
      height = 800f,
      rotation = 90
    )

    // Top-left (0,0)
    val (tlX, tlY) = PdfCoordMapper.mapPoint(0f, 0f, geom)
    assertEquals(0f, tlX, 0.001f)
    assertEquals(0f, tlY, 0.001f)

    // Top-right (1,0)
    val (trX, trY) = PdfCoordMapper.mapPoint(1f, 0f, geom)
    assertEquals(0f, trX, 0.001f)
    assertEquals(800f, trY, 0.001f)

    // Bottom-right (1,1)
    val (brX, brY) = PdfCoordMapper.mapPoint(1f, 1f, geom)
    assertEquals(600f, brX, 0.001f)
    assertEquals(800f, brY, 0.001f)
  }

  @Test
  fun testDisplayToUser_rotation180() {
    val geom = PdfCoordMapper.PageGeometry(
      minX = 0f,
      minY = 0f,
      width = 500f,
      height = 700f,
      rotation = 180
    )

    // Top-left (0,0)
    val (tlX, tlY) = PdfCoordMapper.mapPoint(0f, 0f, geom)
    assertEquals(500f, tlX, 0.001f)
    assertEquals(0f, tlY, 0.001f)

    // Bottom-right (1,1)
    val (brX, brY) = PdfCoordMapper.mapPoint(1f, 1f, geom)
    assertEquals(0f, brX, 0.001f)
    assertEquals(700f, brY, 0.001f)
  }

  @Test
  fun testDisplayToUser_rotation270() {
    val geom = PdfCoordMapper.PageGeometry(
      minX = 0f,
      minY = 0f,
      width = 600f,
      height = 800f,
      rotation = 270
    )

    // Top-left (0,0)
    val (tlX, tlY) = PdfCoordMapper.mapPoint(0f, 0f, geom)
    assertEquals(600f, tlX, 0.001f)
    assertEquals(800f, tlY, 0.001f)

    // Bottom-right (1,1)
    val (brX, brY) = PdfCoordMapper.mapPoint(1f, 1f, geom)
    assertEquals(0f, brX, 0.001f)
    assertEquals(0f, brY, 0.001f)
  }
}
