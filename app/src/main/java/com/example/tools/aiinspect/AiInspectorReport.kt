package com.example.tools.aiinspect

import java.io.File

enum class EvidenceStrength {
  STRONG,
  MEDIUM,
  WEAK
}

enum class AiVerdict {
  STRONG,   // AI-generated or AI-edited markers found
  EDITED,   // Edited, no AI marker found
  POSSIBLE, // Only weak structural hints
  NONE      // No AI markers found (with honesty note)
}

data class EvidenceItem(
  val sourceField: String,
  val value: String,
  val strength: EvidenceStrength
)

data class ImageInspectionResult(
  val inputName: String,
  val fileSizeBytes: Long,
  val dimensions: String?,
  val verdict: AiVerdict,
  val verdictTitle: String,
  val verdictDescription: String,
  val evidenceList: List<EvidenceItem>,
  val extractedPrompt: String? = null,
  val elaScoreMean: Float = 0f,
  val elaScoreMax: Float = 0f,
  val elaHotRatio: Float = 0f,
  val elaHeatmapPath: String? = null,
  val cameraMakeModel: String? = null,
  val software: String? = null,
  val dateOriginal: String? = null
)

object AiInspectorReportBuilder {

  const val MANDATORY_HONESTY_TEXT =
    "No AI markers found. This does NOT prove the image is real: metadata is removed by screenshots, messaging apps and many editors, and invisible watermarks (like Google SynthID) can only be checked with the vendor's own detector."

  fun generatePlainTextReport(results: List<ImageInspectionResult>): String {
    val sb = StringBuilder()
    sb.appendLine("================================================================================")
    sb.appendLine("                      TOOLSNEST - AI IMAGE INSPECTOR REPORT                     ")
    sb.appendLine("================================================================================")
    sb.appendLine("Images Analyzed: ${results.size}")
    sb.appendLine()

    for ((index, item) in results.withIndex()) {
      sb.appendLine("--------------------------------------------------------------------------------")
      sb.appendLine("Image ${index + 1}: ${item.inputName}")
      sb.appendLine("Size: ${item.fileSizeBytes} bytes | Dimensions: ${item.dimensions ?: "Unknown"}")
      sb.appendLine("Verdict: ${item.verdictTitle}")
      sb.appendLine("Summary: ${item.verdictDescription}")
      if (item.cameraMakeModel != null) sb.appendLine("Camera: ${item.cameraMakeModel}")
      if (item.software != null) sb.appendLine("Software: ${item.software}")
      if (item.dateOriginal != null) sb.appendLine("Date: ${item.dateOriginal}")

      if (item.extractedPrompt != null) {
        sb.appendLine()
        sb.appendLine("Detected Generation Parameters / Prompt:")
        sb.appendLine(item.extractedPrompt)
      }

      if (item.evidenceList.isNotEmpty()) {
        sb.appendLine()
        sb.appendLine("Forensic Evidence:")
        for (ev in item.evidenceList) {
          sb.appendLine(" • [${ev.strength}] ${ev.sourceField}: ${ev.value}")
        }
      }

      sb.appendLine()
      sb.appendLine("Error Level Analysis (ELA):")
      sb.appendLine(" • Mean Delta: ${String.format("%.2f", item.elaScoreMean)}")
      sb.appendLine(" • Max Delta: ${String.format("%.2f", item.elaScoreMax)}")
      sb.appendLine(" • Hot Pixel Ratio: ${String.format("%.1f%%", item.elaHotRatio * 100f)}")

      if (item.verdict == AiVerdict.NONE) {
        sb.appendLine()
        sb.appendLine("Note: $MANDATORY_HONESTY_TEXT")
      }
      sb.appendLine()
    }
    sb.appendLine("================================================================================")
    return sb.toString()
  }
}
