package com.example.ui.help

import com.example.R

/** Help texts. Tool guides are keyed by Tool.id; every text lives in the language resource files. */
object HelpContent {
  val toolGuides: Map<Int, Int> = mapOf(
    1 to R.string.help_tool1,
    2 to R.string.help_tool2,
    3 to R.string.help_tool3,
    4 to R.string.help_tool4,
    5 to R.string.help_tool5,
    6 to R.string.help_tool6,
    7 to R.string.help_tool7,
    8 to R.string.help_tool8,
    9 to R.string.help_tool9,
    10 to R.string.help_tool10,
    11 to R.string.help_tool11,
    12 to R.string.help_tool12,
    13 to R.string.help_tool13,
    14 to R.string.help_tool14,
    15 to R.string.help_tool15,
    16 to R.string.help_tool16,
    17 to R.string.help_tool17,
    18 to R.string.help_tool18,
    19 to R.string.help_tool19,
    20 to R.string.help_tool20,
    21 to R.string.help_tool21,
    22 to R.string.help_tool22
  )

  /** Question to answer pairs. */
  val faq: List<Pair<Int, Int>> = listOf(
    R.string.help_faq1_q to R.string.help_faq1_a,
    R.string.help_faq2_q to R.string.help_faq2_a,
    R.string.help_faq3_q to R.string.help_faq3_a,
    R.string.help_faq4_q to R.string.help_faq4_a,
    R.string.help_faq5_q to R.string.help_faq5_a,
    R.string.help_faq6_q to R.string.help_faq6_a,
    R.string.help_faq7_q to R.string.help_faq7_a,
    R.string.help_faq8_q to R.string.help_faq8_a,
    R.string.help_faq9_q to R.string.help_faq9_a
  )
}
