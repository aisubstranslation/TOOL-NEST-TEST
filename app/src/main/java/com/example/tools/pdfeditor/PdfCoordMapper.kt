package com.example.tools.pdfeditor

import android.graphics.PointF
import android.graphics.RectF
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.util.Matrix

/**
 * Pure mathematical coordinator for converting between normalized display space
 * (0..1, origin top-left as rendered by PdfRenderer) and PDF user space
 * (points, origin bottom-left of CropBox/MediaBox, accounting for page /Rotate).
 */
object PdfCoordMapper {

  data class PageGeometry(
    val minX: Float,
    val minY: Float,
    val width: Float,
    val height: Float,
    val rotation: Int
  ) {
    val displayWidth: Float = if (rotation == 90 || rotation == 270) height else width
    val displayHeight: Float = if (rotation == 90 || rotation == 270) width else height
  }

  fun extractGeometry(cropBox: PDRectangle?, mediaBox: PDRectangle?, rotation: Int): PageGeometry {
    val box = cropBox ?: mediaBox ?: PDRectangle(0f, 0f, 595f, 842f)
    val normRot = normalizeRotation(rotation)
    return PageGeometry(
      minX = box.lowerLeftX,
      minY = box.lowerLeftY,
      width = box.width,
      height = box.height,
      rotation = normRot
    )
  }

  fun normalizeRotation(rotation: Int): Int {
    val mod = rotation % 360
    val positive = if (mod < 0) mod + 360 else mod
    return when (positive) {
      90 -> 90
      180 -> 180
      270 -> 270
      else -> 0
    }
  }

  /**
   * Pure Float mapping for normalized display point (0..1, top-left origin) to PDF user space.
   */
  fun mapPoint(
    dx: Float,
    dy: Float,
    geom: PageGeometry
  ): Pair<Float, Float> {
    val clampedX = dx.coerceIn(0f, 1f)
    val clampedY = dy.coerceIn(0f, 1f)

    return when (geom.rotation) {
      0 -> (geom.minX + clampedX * geom.width) to (geom.minY + (1f - clampedY) * geom.height)
      90 -> (geom.minX + clampedY * geom.width) to (geom.minY + clampedX * geom.height)
      180 -> (geom.minX + (1f - clampedX) * geom.width) to (geom.minY + clampedY * geom.height)
      270 -> (geom.minX + (1f - clampedY) * geom.width) to (geom.minY + (1f - clampedX) * geom.height)
      else -> (geom.minX + clampedX * geom.width) to (geom.minY + (1f - clampedY) * geom.height)
    }
  }

  /**
   * Maps a normalized display point (0..1, top-left origin) to PDF user space (points).
   */
  fun mapDisplayPointToUser(
    dx: Float,
    dy: Float,
    geom: PageGeometry
  ): PointF {
    val (userX, userY) = mapPoint(dx, dy, geom)
    return PointF(userX, userY)
  }

  /**
   * Maps a normalized display rectangle to a PDF user space bounding box.
   */
  fun mapDisplayRectToUser(
    rect: RectF,
    geom: PageGeometry
  ): RectF {
    val p1 = mapDisplayPointToUser(rect.left, rect.top, geom)
    val p2 = mapDisplayPointToUser(rect.right, rect.top, geom)
    val p3 = mapDisplayPointToUser(rect.right, rect.bottom, geom)
    val p4 = mapDisplayPointToUser(rect.left, rect.bottom, geom)

    val minX = minOf(p1.x, p2.x, p3.x, p4.x)
    val maxX = maxOf(p1.x, p2.x, p3.x, p4.x)
    val minY = minOf(p1.y, p2.y, p3.y, p4.y)
    val maxY = maxOf(p1.y, p2.y, p3.y, p4.y)

    return RectF(minX, minY, maxX, maxY)
  }

  /**
   * Constructs an affine transformation matrix for PDFBox content stream.
   * Applying this matrix transforms coordinates from display space
   * (0..displayWidth, 0..displayHeight, origin top-left) directly to user space.
   */
  fun computeDisplayToUserMatrix(geom: PageGeometry): Matrix {
    val dW = geom.displayWidth
    val dH = geom.displayHeight
    return when (geom.rotation) {
      0 -> {
        // [1, 0, 0, -1, minX, minY + height]
        Matrix(1f, 0f, 0f, -1f, geom.minX, geom.minY + geom.height)
      }
      90 -> {
        // Rotated 90 CW: x_user = minX + y_disp, y_user = minY + x_disp
        Matrix(0f, 1f, 1f, 0f, geom.minX, geom.minY)
      }
      180 -> {
        // Rotated 180: x_user = minX + width - x_disp, y_user = minY + y_disp
        Matrix(-1f, 0f, 0f, 1f, geom.minX + geom.width, geom.minY)
      }
      270 -> {
        // Rotated 270 CW: x_user = minX + width - y_disp, y_user = minY + height - x_disp
        Matrix(0f, -1f, -1f, 0f, geom.minX + geom.width, geom.minY + geom.height)
      }
      else -> Matrix(1f, 0f, 0f, -1f, geom.minX, geom.minY + geom.height)
    }
  }
}
