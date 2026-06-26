package com.example.expenseapp.ui.screens.stats

import android.app.DatePickerDialog
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.example.expenseapp.domain.model.Category
import com.example.expenseapp.domain.model.Group
import java.text.SimpleDateFormat
import java.util.*
import com.example.expenseapp.R
import com.example.expenseapp.ui.screens.stats.components.StatsTrendChart

import com.example.expenseapp.ui.util.getLocalizedName
import com.example.expenseapp.ui.util.getCurrencySymbol
import androidx.compose.ui.res.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(
    navController: NavController,
    viewModel: StatsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val dateFormatter = remember { SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()) }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text(stringResource(R.string.spending_analysis)) })
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            
            // Group Selector
            GroupSelector(
                selectedGroupId = uiState.selectedGroupId,
                groups = uiState.groups,
                onGroupSelected = viewModel::onGroupSelected
            )

            // Date Range Selector
            DateRangeSelector(
                startDate = uiState.startDate,
                endDate = uiState.endDate,
                dateFormatter = dateFormatter,
                onRangeChanged = viewModel::onDateRangeChanged
            )
            // Identity Debug Label
            Text(
                text = "Viewing as: ${uiState.currentUserName ?: "Loading..."}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )

            // Filters
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Only Me / Full Toggle
                FilterChip(
                    selected = uiState.isOnlyMeFilter,
                    onClick = { viewModel.toggleOnlyMeFilter() },
                    label = { 
                        Text(
                            if (uiState.isOnlyMeFilter) stringResource(R.string.only_me) 
                            else stringResource(R.string.full_expenses)
                        ) 
                    },
                    leadingIcon = {
                        Icon(
                            if (uiState.isOnlyMeFilter) Icons.Default.AccountCircle else Icons.Default.Group,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                )
                
                Spacer(modifier = Modifier.width(8.dp))
                
                // Active / Archived Toggle
                FilterChip(
                    selected = uiState.showArchived,
                    onClick = { viewModel.toggleShowArchived() },
                    label = { 
                        Text(
                            if (uiState.showArchived) stringResource(R.string.stats_show_archived) 
                            else stringResource(R.string.stats_active_only)
                        ) 
                    },
                    leadingIcon = {
                        Icon(
                            if (uiState.showArchived) Icons.Default.FilterList else Icons.Default.FilterList, // Use appropriate icons if available
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                )

                Spacer(modifier = Modifier.width(8.dp))
                
                CategoryFilterBar(
                    categories = uiState.categories,
                    selectedCategoryIds = uiState.selectedCategoryIds,
                    onToggleCategory = viewModel::toggleCategoryFilter,
                    onClear = viewModel::clearCategoryFilters
                )
            }

            if (uiState.isLoading) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (uiState.reports.isEmpty()) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text(
                        text = stringResource(R.string.no_expenses_found),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            } else {
                LazyColumn(modifier = Modifier.weight(1f)) {
                    uiState.reports.forEach { report ->
                        if (uiState.reports.size > 1) {
                            item {
                                Text(
                                    text = report.currency,
                                    style = MaterialTheme.typography.titleLarge,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        item {
                            TotalSpendingCard(report.totalSpending, report.currency)
                        }
                        
                        item {
                            StatsTrendChart(
                                monthlyTrend = report.monthlyTrend,
                                currency = report.currency
                            )
                        }
                        
                        item {
                            Text(
                                text = stringResource(R.string.breakdown_by_category),
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.padding(16.dp)
                            )
                        }

                        items(report.categoryBreakdown) { stat ->
                            CategoryStatItem(
                                stat = stat, 
                                currency = report.currency,
                                isExpanded = uiState.expandedCategoryId == stat.category?.id,
                                onToggle = { stat.category?.id?.let { viewModel.toggleCategoryExpansion(it) } }
                            )
                        }
                        
                        item { Spacer(modifier = Modifier.height(16.dp)) }
                    }
                }
            }
        }
    }
}

@Composable
fun GroupSelector(
    selectedGroupId: String?,
    groups: List<Group>,
    onGroupSelected: (String?) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedGroup = groups.find { it.id == selectedGroupId }

    Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        OutlinedCard(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Group, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = selectedGroup?.name ?: stringResource(R.string.all_groups_general),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyLarge
                )
                Icon(Icons.Default.ArrowDropDown, contentDescription = null)
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.all_groups_general)) },
                onClick = {
                    onGroupSelected(null)
                    expanded = false
                }
            )
            groups.forEach { group ->
                DropdownMenuItem(
                    text = { Text(group.name) },
                    onClick = {
                        onGroupSelected(group.id)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
fun DateRangeSelector(
    startDate: Long,
    endDate: Long,
    dateFormatter: SimpleDateFormat,
    onRangeChanged: (Long, Long) -> Unit
) {
    val context = LocalContext.current

    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Start Date
        OutlinedCard(
            onClick = {
                val cal = Calendar.getInstance().apply { timeInMillis = startDate }
                DatePickerDialog(
                    context,
                    { _, year, month, day ->
                        val newStart = Calendar.getInstance().apply {
                            set(year, month, day, 0, 0, 0)
                            set(Calendar.MILLISECOND, 0)
                        }.timeInMillis
                        onRangeChanged(newStart, endDate)
                    },
                    cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)
                ).show()
            },
            modifier = Modifier.weight(1f)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(stringResource(R.string.from), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CalendarToday, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(dateFormatter.format(startDate), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        // End Date
        OutlinedCard(
            onClick = {
                val cal = Calendar.getInstance().apply { timeInMillis = endDate }
                DatePickerDialog(
                    context,
                    { _, year, month, day ->
                        val newEnd = Calendar.getInstance().apply {
                            set(year, month, day, 23, 59, 59)
                            set(Calendar.MILLISECOND, 999)
                        }.timeInMillis
                        onRangeChanged(startDate, newEnd)
                    },
                    cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)
                ).show()
            },
            modifier = Modifier.weight(1f)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(stringResource(R.string.to), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CalendarToday, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(dateFormatter.format(endDate), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryFilterBar(
    categories: List<Category>,
    selectedCategoryIds: Set<String>,
    onToggleCategory: (String) -> Unit,
    onClear: () -> Unit
) {
    Row(
        modifier = Modifier.padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        InputChip(
            selected = selectedCategoryIds.isEmpty(),
            onClick = onClear,
            label = { Text(stringResource(R.string.all_categories)) },
            leadingIcon = if (selectedCategoryIds.isEmpty()) { { Icon(Icons.Default.FilterList, contentDescription = null, modifier = Modifier.size(18.dp)) } } else null
        )
        
        categories.forEach { category ->
            val context = LocalContext.current
            FilterChip(
                selected = selectedCategoryIds.contains(category.id),
                onClick = { onToggleCategory(category.id) },
                label = { Text(category.getLocalizedName(context)) }
            )
        }
    }
}

@Composable
fun TotalSpendingCard(total: Double, currency: String) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            Text(
                stringResource(R.string.total_spent_normalized), 
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
            )
            Text(
                "${getCurrencySymbol(currency)}${String.format(Locale.US, "%.2f", total)}", 
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

@Composable
fun CategoryStatItem(
    stat: CategoryStats, 
    currency: String,
    isExpanded: Boolean,
    onToggle: () -> Unit
) {
    val category = stat.category
    val chipColor = try {
        Color(android.graphics.Color.parseColor(category?.colorHex ?: "#95A5A6"))
    } catch (e: Exception) {
        MaterialTheme.colorScheme.secondary
    }

    val iconEmoji = when (category?.iconName) {
        "restaurant" -> "🍽️"
        "directions_car" -> "🚗"
        "home" -> "🏠"
        "shopping_cart" -> "🛒"
        "flight" -> "✈️"
        "movie" -> "🎭"
        "local_hospital" -> "🏥"
        "school" -> "📚"
        "shopping_bag" -> "👕"
        "lightbulb" -> "💡"
        "more_horiz" -> "❓"
        else -> "📦"
    }
    
    val context = LocalContext.current
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .animateContentSize(),
        onClick = onToggle,
        colors = CardDefaults.cardColors(
            containerColor = if (isExpanded) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent
        ),
        shape = MaterialTheme.shapes.medium
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = chipColor.copy(alpha = 0.2f),
                        modifier = Modifier.size(32.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(iconEmoji)
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = category?.getLocalizedName(context) ?: stringResource(R.string.unknown),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "${getCurrencySymbol(currency)}${String.format(Locale.US, "%.2f", stat.amountInBase)}",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${(stat.percentage * 100).toInt()}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
            LinearProgressIndicator(
                progress = { stat.percentage },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp).height(6.dp),
                color = chipColor,
                trackColor = chipColor.copy(alpha = 0.1f),
                strokeCap = androidx.compose.ui.graphics.StrokeCap.Round
            )
            
            if (isExpanded) {
                Spacer(modifier = Modifier.height(16.dp))
                stat.expenses.forEach { detail ->
                    val expense = detail.expense
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = expense.description.ifBlank { stringResource(R.string.no_description) },
                                style = MaterialTheme.typography.bodyMedium
                            )
                            val dateFormatter = remember { SimpleDateFormat("MMM dd", Locale.getDefault()) }
                            Text(
                                text = dateFormatter.format(expense.date),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                        Text(
                            text = "${getCurrencySymbol(expense.currency)}${"%.2f".format(detail.amountToDisplay)}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    if (stat.expenses.last() != detail) {
                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 4.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                        )
                    }
                }
            }
        }
    }
}
