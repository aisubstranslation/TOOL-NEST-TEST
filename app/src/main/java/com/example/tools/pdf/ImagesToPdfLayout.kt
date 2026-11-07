package com.example.tools.pdf

enum class PageSizeOption {
  A4,
  LETTER,
  FIT_TO_IMAGE;

  companion object {
    fun fromSetting(name: String?): PageSizeOption {
      if (name == null) return A4
      return when (name.uppercase()) {
        "LETTER" -> LETTER
        "FIT_TO_IMAGE", "FIT" -> FIT_TO_IMAGE
        else -> A4
      }
    }
  }
}

enum class OrientationOption {
  AUTO,
  PORTRAIT,
  LANDSCAPE
}

enum class MarginOption {
  NONE,
  MARGIN_10MM,
  MARGIN_20MM
}

enum class QualityOption {
  HIGH,
  MEDIUM
}

data class PageDimensions(
  val pageWidthPt: Float,
  val pageHeightPt: Float
)

data class ImagePlacement(
  val pageWidthPt: Float,
  val pageHeightPt: Float,
  val drawX: Float,
  val drawY: Float,
  val drawWidth: Float,
  val drawHeight: Float
)

object ImagesToPdfLayout {
  const val A4_WIDTH_PT = 595f
  const val A4_HEIGHT_PT = 842f
  const val LETTER_WIDTH_PT = 612f
  const val LETTER_HEIGHT_PT = 792f
  const val MARGIN_10MM_PT = 28.3465f
  const val MARGIN_20MM_PT = 56.6929f

  fun mmToPoints(mm: Float): Float = mm * 72f / 25.4f

  fun getMarginPoints(option: MarginOption): Float {
    return when (option) {
      MarginOption.NONE -> 0f
      MarginOption.MARGIN_10MM -> MARGIN_10MM_PT
      MarginOption.MARGIN_20MM -> MARGIN_20MM_PT
    }
  }

  fun determinePageDimensions(
    pageSize: PageSizeOption,
    orientation: OrientationOption,
    imageWidth: Int,
    imageHeight: Int,
    marginPt: Float
  ): PageDimensions {
    if (pageSize == PageSizeOption.FIT_TO_IMAGE) {
      val imgW = imageWidth.toFloat().coerceAtLeast(1f)
      val imgH = imageHeight.toFloat().coerceAtLeast(1f)
      return PageDimensions(
        pageWidthPt = imgW + 2f * marginPt,
        pageHeightPt = imgH + 2f * marginPt
      )
    }

    val (baseW, baseH) = when (pageSize) {
      PageSizeOption.A4 -> A4_WIDTH_PT to A4_HEIGHT_PT
      PageSizeOption.LETTER -> LETTER_WIDTH_PT to LETTER_HEIGHT_PT
      PageSizeOption.FIT_TO_IMAGE -> A4_WIDTH_PT to A4_HEIGHT_PT
    }

    val isLandscape = when (orientation) {
      OrientationOption.AUTO -> imageWidth > imageHeight
      OrientationOption.LANDSCAPE -> true
      OrientationOption.PORTRAIT -> false
    }

    val finalW = if (isLandscape) maxOf(baseW, baseH) else minOf(baseW, baseH)
    val finalH = if (isLandscape) minOf(baseW, baseH) else maxOf(baseW, baseH)

    return PageDimensions(finalW, finalH)
  }

  fun calculateImagePlacement(
    pageWidthPt: Float,
    pageHeightPt: Float,
    marginPt: Float,
    imageWidth: Int,
    imageHeight: Int
  ): ImagePlacement {
    val safeImgW = imageWidth.coerceAtLeast(1).toFloat()
    val safeImgH = imageHeight.coerceAtLeast(1).toFloat()

    val printableW = (pageWidthPt - 2f * marginPt).coerceAtLeast(1f)
    val printableH = (pageHeightPt - 2f * marginPt).coerceAtLeast(1f)

    val scaleX = printableW / safeImgW
    val scaleY = printableH / safeImgH
    val scale = minOf(scaleX, scaleY)

    val drawW = safeImgW * scale
    val drawH = safeImgH * scale

    // PDF coordinate system origin (0,0) is bottom-left
    val drawX = marginPt + (printableW - drawW) / 2f
    val drawY = marginPt + (printableH - drawH) / 2f

    return ImagePlacement(
      pageWidthPt = pageWidthPt,
      pageHeightPt = pageHeightPt,
      drawX = drawX,
      drawY = drawY,
      drawWidth = drawW,
      drawHeight = drawH
    )
  }

  fun calculateMaxPixelsForQuality(
    pageWidthPt: Float,
    pageHeightPt: Float,
    qualityOption: QualityOption
  ): Int {
    val dpi = when (qualityOption) {
      QualityOption.HIGH -> 300
      QualityOption.MEDIUM -> 150
    }
    val maxPt = maxOf(pageWidthPt, pageHeightPt)
    return (maxPt * dpi / 72f).toInt()
  }

  fun getQualityCompressionParams(qualityOption: QualityOption): Pair<Int, Float> {
    return when (qualityOption) {
      QualityOption.HIGH -> 300 to 0.90f
      QualityOption.MEDIUM -> 150 to 0.75f
    }
  }
}
