package com.example.ui.help

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.model.Category
import com.example.model.ToolRegistry
import com.example.tools.common.ToolScaffold

/**
 * In-app help: a short guide for every tool plus common questions.
 * All text comes from string resources, so it follows the language chosen in Settings.
 */
@Composable
fun HelpScreen(
  onBack: () -> Unit,
  modifier: Modifier = Modifier
) {
  ToolScaffold(
    title = stringResource(R.string.help_title),
    onBack = onBack,
    modifier = modifier
  ) { padding ->
    LazyColumn(
      modifier = Modifier
        .padding(padding)
        .testTag("help_screen"),
      contentPadding = PaddingValues(16.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
      item {
        Text(
          text = stringResource(R.string.help_intro),
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }

      item { SectionTitle(stringResource(R.string.help_section_tools)) }

      for (category in Category.entries) {
        val toolsInCategory = ToolRegistry.tools.filter {
          it.category == category && HelpContent.toolGuides.containsKey(it.id)
        }
        if (toolsInCategory.isEmpty()) continue
        item(key = "cat_${category.name}") {
          Text(
            text = stringResource(category.titleRes),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = 6.dp)
          )
        }
        items(toolsInCategory, key = { "tool_${it.id}" }) { tool ->
          val guideRes = HelpContent.toolGuides.getValue(tool.id)
          HelpCard(
            stateKey = "tool_${tool.id}",
            title = stringResource(tool.nameRes),
            body = stringResource(guideRes),
            icon = tool.icon
          )
        }
      }

      item { Spacer(Modifier.height(6.dp)) }
      item { SectionTitle(stringResource(R.string.help_section_faq)) }

      items(HelpContent.faq.size, key = { "faq_$it" }) { index ->
        val (q, a) = HelpContent.faq[index]
        HelpCard(
          stateKey = "faq_$index",
          title = stringResource(q),
          body = stringResource(a),
          icon = null
        )
      }

      item { Spacer(Modifier.height(24.dp)) }
    }
  }
}

@Composable
private fun SectionTitle(text: String) {
  Text(
    text = text,
    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
    color = MaterialTheme.colorScheme.onSurface,
    modifier = Modifier.padding(top = 8.dp)
  )
}

@Composable
private fun HelpCard(
  stateKey: String,
  title: String,
  body: String,
  icon: ImageVector?
) {
  var expanded by rememberSaveable(stateKey) { mutableStateOf(false) }
  Card(
    modifier = Modifier
      .fillMaxWidth()
      .clickable { expanded = !expanded }
      .testTag("help_card_$stateKey"),
    shape = RoundedCornerShape(14.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
  ) {
    Column(modifier = Modifier.padding(14.dp)) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
      ) {
        if (icon != null) {
          Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(22.dp)
          )
        }
        Text(
          text = title,
          style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
          modifier = Modifier.weight(1f)
        )
        Icon(
          imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
          contentDescription = stringResource(if (expanded) R.string.help_collapse else R.string.help_expand)
        )
      }
      AnimatedVisibility(visible = expanded) {
        Text(
          text = body,
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.padding(top = 10.dp)
        )
      }
    }
  }
}
