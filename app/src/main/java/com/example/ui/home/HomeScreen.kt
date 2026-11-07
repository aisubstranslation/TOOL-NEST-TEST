package com.example.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.R
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.model.Category
import com.example.model.Tool
import com.example.model.ToolRegistry
import com.example.ui.MainViewModel
import com.example.ui.components.ToolCard
import com.example.ui.components.ToolsNestLogo

@Composable
fun HomeScreen(
  viewModel: MainViewModel,
  onToolClick: (Int) -> Unit,
  onOpenProcessingCenter: () -> Unit = {},
  onOpenHelp: () -> Unit = {},
  modifier: Modifier = Modifier
) {
  val searchQuery by viewModel.searchQuery.collectAsState()
  val selectedCategory by viewModel.selectedCategory.collectAsState()
  val recentTools by viewModel.recentTools.collectAsState()
  val searchResults by viewModel.searchResults.collectAsState()

  // remember: a brand-new Flow on every recomposition made Home re-subscribe to the database again and again
  val activeJobsFlow = remember {
    val app = runCatching { com.example.ToolsNestApp.instance }.getOrNull()
    app?.jobRepository?.observeActiveJobsCount() ?: kotlinx.coroutines.flow.flowOf(0)
  }
  val activeJobsCount by activeJobsFlow.collectAsState(initial = 0)

  val isSearching = searchQuery.isNotBlank()

  val displayTools: List<Tool> = remember(isSearching, searchResults, selectedCategory) {
    if (isSearching) {
      searchResults
    } else {
      val all = ToolRegistry.tools
      if (selectedCategory != null) all.filter { it.category == selectedCategory } else all
    }
  }

  LazyColumn(
    modifier = modifier
      .fillMaxSize()
      .testTag("home_screen"),
    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 88.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp)
  ) {
    // Top Logo Header
    item {
      var menuOpen by remember { mutableStateOf(false) }
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
      ) {
        ToolsNestLogo(
          modifier = Modifier
            .weight(1f)
            .padding(bottom = 4.dp),
          iconSize = 48.dp
        )
        Box {
          IconButton(
            onClick = { menuOpen = true },
            modifier = Modifier.testTag("home_menu_button")
          ) {
            Icon(
              imageVector = Icons.Default.Menu,
              contentDescription = stringResource(R.string.help_menu_button)
            )
          }
          DropdownMenu(
            expanded = menuOpen,
            onDismissRequest = { menuOpen = false }
          ) {
            DropdownMenuItem(
              text = { Text(stringResource(R.string.help_menu)) },
              leadingIcon = { Icon(Icons.AutoMirrored.Filled.HelpOutline, contentDescription = null) },
              onClick = {
                menuOpen = false
                onOpenHelp()
              },
              modifier = Modifier.testTag("home_menu_help")
            )
          }
        }
      }
    }

    // Processing Center Card
    item {
      androidx.compose.material3.Card(
        modifier = Modifier
          .fillMaxWidth()
          .clickable { onOpenProcessingCenter() }
          .testTag("processing_center_home_card"),
        shape = RoundedCornerShape(14.dp),
        colors = androidx.compose.material3.CardDefaults.cardColors(
          containerColor = if (activeJobsCount > 0) {
            MaterialTheme.colorScheme.primaryContainer
          } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
          }
        )
      ) {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(14.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
          Icon(
            imageVector = if (activeJobsCount > 0) Icons.Default.Refresh else Icons.Default.Search,
            contentDescription = null,
            tint = if (activeJobsCount > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp)
          )

          Column(modifier = Modifier.weight(1f)) {
            Text(
              text = stringResource(R.string.proc_center_card_title),
              style = MaterialTheme.typography.titleSmall,
              fontWeight = FontWeight.Bold,
              color = if (activeJobsCount > 0) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
            )
            Text(
              text = if (activeJobsCount > 0) {
                stringResource(R.string.proc_center_card_active, activeJobsCount)
              } else {
                stringResource(R.string.proc_center_card_none)
              },
              style = MaterialTheme.typography.bodySmall,
              color = if (activeJobsCount > 0) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant
            )
          }

          Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
      }
    }

    // Search Bar
    item {
      OutlinedTextField(
        value = searchQuery,
        onValueChange = { viewModel.setSearchQuery(it) },
        modifier = Modifier
          .fillMaxWidth()
          .testTag("search_bar_input"),
        placeholder = {
          Text(
            text = stringResource(R.string.home_search_placeholder),
            style = MaterialTheme.typography.bodyMedium
          )
        },
        leadingIcon = {
          Icon(
            imageVector = Icons.Default.Search,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
          )
        },
        trailingIcon = {
          if (searchQuery.isNotEmpty()) {
            IconButton(
              onClick = { viewModel.setSearchQuery("") },
              modifier = Modifier.testTag("clear_search_button")
            ) {
              Icon(
                imageVector = Icons.Default.Clear,
                contentDescription = stringResource(R.string.common_cancel),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
              )
            }
          }
        },
        singleLine = true,
        shape = RoundedCornerShape(16.dp),
        colors = OutlinedTextFieldDefaults.colors(
          focusedContainerColor = MaterialTheme.colorScheme.surface,
          unfocusedContainerColor = MaterialTheme.colorScheme.surface
        )
      )
    }

    // Category Filter Chips
    item {
      LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 2.dp)
      ) {
        item {
          FilterChip(
            selected = selectedCategory == null,
            onClick = { viewModel.selectCategory(null) },
            label = { Text(stringResource(R.string.category_all)) },
            modifier = Modifier.testTag("chip_category_all"),
            colors = FilterChipDefaults.filterChipColors(
              selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
              selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
            )
          )
        }

        items(Category.entries, key = { it.name }, contentType = { "category_chip" }) { cat ->
          FilterChip(
            selected = selectedCategory == cat,
            onClick = { viewModel.selectCategory(if (selectedCategory == cat) null else cat) },
            label = { Text(stringResource(cat.titleRes)) },
            modifier = Modifier.testTag("chip_category_${cat.name.lowercase()}"),
            colors = FilterChipDefaults.filterChipColors(
              selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
              selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
            )
          )
        }
      }
    }

    // Real Recent Tools (Shown ONLY when real usage exists in Room history)
    if (!isSearching && recentTools.isNotEmpty()) {
      item {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Text(
            text = stringResource(R.string.home_recent_tools),
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onBackground
          )

          LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp)
          ) {
            items(recentTools, key = { it.id }) { tool ->
              Surface(
                modifier = Modifier
                  .width(160.dp)
                  .testTag("recent_tool_${tool.id}")
                  .clickable { onToolClick(tool.id) },
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 2.dp
              ) {
                Column(
                  modifier = Modifier.padding(12.dp),
                  verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                  Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(36.dp)
                  ) {
                    Box(contentAlignment = Alignment.Center) {
                      Icon(
                        imageVector = tool.icon,
                        contentDescription = stringResource(tool.nameRes),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(20.dp)
                      )
                    }
                  }
                  Text(
                    text = stringResource(tool.nameRes),
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                    maxLines = 1
                  )
                }
              }
            }
          }
        }
      }
    }

    // Section title
    item {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text(
          text = if (isSearching) {
            stringResource(R.string.home_all_tools)
          } else {
            stringResource(R.string.home_all_tools)
          },
          style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
          color = MaterialTheme.colorScheme.onBackground
        )
        Text(
          text = stringResource(R.string.home_tools_count, displayTools.size),
          style = MaterialTheme.typography.labelMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }
    }

    // Tool Cards or Empty State
    if (isSearching && displayTools.isEmpty()) {
      item {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp)
            .testTag("search_no_results"),
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.Center
        ) {
          Icon(
            imageVector = Icons.Default.SearchOff,
            contentDescription = null,
            modifier = Modifier.size(56.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
          )
          Spacer(modifier = Modifier.height(12.dp))
          Text(
            text = stringResource(R.string.home_no_results_title),
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface
          )
          Spacer(modifier = Modifier.height(4.dp))
          Text(
            text = stringResource(R.string.home_no_results_desc),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 24.dp)
          )
        }
      }
    } else {
      items(displayTools, key = { it.id }, contentType = { "tool_card" }) { tool ->
        ToolCard(
          tool = tool,
          onClick = { onToolClick(tool.id) }
        )
      }
    }
  }
}
