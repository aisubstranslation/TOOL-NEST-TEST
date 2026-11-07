package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun ToolsNestLogo(
  modifier: Modifier = Modifier,
  iconSize: Dp = 44.dp,
  showSubtitle: Boolean = true
) {
  val primaryColor = MaterialTheme.colorScheme.primary
  val secondaryColor = MaterialTheme.colorScheme.secondary

  Row(
    modifier = modifier.testTag("app_logo"),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(12.dp)
  ) {
    Canvas(modifier = Modifier.size(iconSize)) {
      val w = size.width
      val h = size.height

      // Background rounded shield/circle
      drawCircle(
        color = primaryColor.copy(alpha = 0.12f),
        radius = w * 0.48f,
        center = Offset(w * 0.5f, h * 0.5f)
      )

      // Stylized tool icons inside the nest
      // 1. Wrench line
      drawLine(
        color = primaryColor,
        start = Offset(w * 0.42f, h * 0.32f),
        end = Offset(w * 0.34f, h * 0.52f),
        strokeWidth = w * 0.07f,
        cap = StrokeCap.Round
      )

      // 2. Document folded rectangle
      val docPath = Path().apply {
        moveTo(w * 0.50f, h * 0.28f)
        lineTo(w * 0.64f, h * 0.28f)
        lineTo(w * 0.68f, h * 0.36f)
        lineTo(w * 0.68f, h * 0.52f)
        lineTo(w * 0.50f, h * 0.52f)
        close()
      }
      drawPath(
        path = docPath,
        color = secondaryColor.copy(alpha = 0.85f)
      )

      // 3. Central nest bowl (rich curved base)
      val nestPath = Path().apply {
        moveTo(w * 0.18f, h * 0.55f)
        quadraticTo(
          w * 0.5f, h * 0.88f,
          w * 0.82f, h * 0.55f
        )
      }
      drawPath(
        path = nestPath,
        color = secondaryColor,
        style = Stroke(width = w * 0.10f, cap = StrokeCap.Round)
      )

      // 4. Overlapping accent twig
      val twigPath = Path().apply {
        moveTo(w * 0.24f, h * 0.62f)
        quadraticTo(
          w * 0.52f, h * 0.80f,
          w * 0.78f, h * 0.62f
        )
      }
      drawPath(
        path = twigPath,
        color = Color(0xFFFDBA74),
        style = Stroke(width = w * 0.05f, cap = StrokeCap.Round)
      )
    }

    Column {
      Text(
        text = buildAnnotatedString {
          withStyle(
            SpanStyle(
              color = primaryColor,
              fontWeight = FontWeight.Black,
              letterSpacing = 1.sp
            )
          ) {
            append("TOOLS")
          }
          withStyle(
            SpanStyle(
              color = secondaryColor,
              fontWeight = FontWeight.Black,
              letterSpacing = 1.sp
            )
          ) {
            append("NEST")
          }
        },
        style = MaterialTheme.typography.titleLarge
      )
      if (showSubtitle) {
        Text(
          text = "OFFLINE TOOL SUITE",
          style = MaterialTheme.typography.labelSmall.copy(
            letterSpacing = 1.2.sp,
            fontWeight = FontWeight.SemiBold
          ),
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }
    }
  }
}
