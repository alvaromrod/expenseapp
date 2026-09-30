package com.example.expenseapp.ui.screens.home

import androidx.compose.ui.draw.clip
import coil.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.animation.Crossfade
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.example.expenseapp.domain.model.Expense
import com.example.expenseapp.domain.model.Category
import com.example.expenseapp.ui.navigation.Screen
import com.example.expenseapp.ui.util.getCurrencySymbol
import java.text.SimpleDateFormat
import java.util.Locale
import androidx.compose.ui.res.stringResource
import com.example.expenseapp.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    navController: NavController,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var expenseToDelete by remember { mutableStateOf<Expense?>(null) }
    var showArchived by remember { mutableStateOf(false) }

    // Confirmation dialog for deletion
    if (expenseToDelete != null) {
        AlertDialog(
            onDismissRequest = { expenseToDelete = null },
            title = { Text(stringResource(R.string.delete_expense)) },
            text = { Text(stringResource(R.string.confirm_delete_expense)) },
            confirmButton = {
                TextButton(onClick = {
                    expenseToDelete?.let { viewModel.deleteExpense(it) }
                    expenseToDelete = null
                }) { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { expenseToDelete = null }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }

    Scaffold(
        topBar = {
            var showGroupSelector by remember { mutableStateOf(false) }
            TopAppBar(
                title = {
                    TextButton(onClick = { showGroupSelector = true }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = uiState.selectedGroup?.name ?: stringResource(R.string.select_group),
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Icon(
                                Icons.Default.ArrowDropDown,
                                contentDescription = stringResource(R.string.select_group),
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                    DropdownMenu(
                        expanded = showGroupSelector,
                        onDismissRequest = { showGroupSelector = false }
                    ) {
                        uiState.groups.forEach { group ->
                            DropdownMenuItem(
                                text = { Text(group.name) },
                                onClick = {
                                    viewModel.selectGroup(group.id)
                                    showGroupSelector = false
                                }
                            )
                        }
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.manage_groups)) },
                            onClick = {
                                navController.navigate(Screen.Groups.route)
                                showGroupSelector = false
                            }
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { navController.navigate(Screen.GroupStats.route) }) {
                        Icon(Icons.Default.BarChart, contentDescription = stringResource(R.string.statistics))
                    }
                    IconButton(onClick = { navController.navigate(Screen.UserProfile.route) }) {
                        Icon(Icons.Default.AccountCircle, contentDescription = stringResource(R.string.profile))
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { navController.navigate("add_expense") }) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.add_expense))
            }
        }
    ) { padding ->
        Crossfade(targetState = uiState.isLoading, label = "home_loading") { isLoading ->
            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                PullToRefreshBox(
                    isRefreshing = uiState.isRefreshing,
                    onRefresh = { viewModel.refresh() },
                    modifier = Modifier
                        .padding(padding)
                        .fillMaxSize()
                ) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        BalanceCard(
                        balance = uiState.totalBalance,
                        currencyCode = uiState.selectedGroup?.mainCurrency ?: "USD",
                        onClick = {
                            uiState.selectedGroup?.let { group ->
                                navController.navigate(Screen.GroupBalances.createRoute(group.id))
                            }
                        }
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (showArchived) stringResource(R.string.archived_expenses) else stringResource(R.string.recent_expenses),
                            style = MaterialTheme.typography.titleLarge
                        )
                        
                        FilterChip(
                            selected = showArchived,
                            onClick = { showArchived = !showArchived },
                            label = { Text(if (showArchived) stringResource(R.string.show_active) else stringResource(R.string.show_archived)) },
                            leadingIcon = if (showArchived) { { Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp)) } } else null
                        )
                    }

                    val filteredExpenses = remember(uiState.expenses, showArchived) {
                        uiState.expenses.filter { it.isArchived == showArchived }
                    }
                    if (filteredExpenses.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                text = if (showArchived) stringResource(R.string.no_archived_expenses) 
                                       else stringResource(R.string.no_active_expenses), 
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    } else {
                        LazyColumn(modifier = Modifier.weight(1f)) {
                            items(filteredExpenses, key = { it.id }) { expense ->
                                val category = uiState.categoryMap[expense.categoryId]
                                val user = uiState.userMap[expense.paidById]
                                val paidByName = user?.name?.ifBlank { user.email } ?: expense.paidById
                                ExpenseItem(
                                    expense = expense,
                                    categoryName = category?.name,
                                    categoryIcon = category?.iconName,
                                    paidByName = paidByName,
                                    paidByAvatarUrl = user?.avatarUrl,
                                    onEditClick = {
                                        navController.navigate("add_expense?expenseId=${expense.id}")
                                    },
                                    onDeleteClick = { expenseToDelete = expense }
                                )
                                HorizontalDivider()
                            }
                        }
                    }
                }
            }
        }
    }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BalanceCard(balance: Double, currencyCode: String, onClick: () -> Unit) {
    val isPositive = balance >= 0
    val containerColor by animateColorAsState(
        targetValue = if (isPositive) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.errorContainer,
        animationSpec = tween(durationMillis = 500),
        label = "containerColor"
    )
    val contentColor by animateColorAsState(
        targetValue = if (isPositive) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onErrorContainer,
        animationSpec = tween(durationMillis = 500),
        label = "contentColor"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        onClick = onClick,
        colors = CardDefaults.cardColors(
            containerColor = containerColor,
            contentColor = contentColor
        )
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Text(
                text = if (isPositive) stringResource(R.string.you_are_owed) else stringResource(R.string.you_owe),
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = "${getCurrencySymbol(currencyCode)}${"%.2f".format(Math.abs(balance))}",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun ExpenseItem(
    expense: Expense,
    categoryName: String?,
    categoryIcon: String?,
    paidByName: String,
    paidByAvatarUrl: String?,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    val iconEmoji = when (categoryIcon) {
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
    val displayName = if (categoryName != null) "$iconEmoji $categoryName" else expense.description
    
    val dateFormatter = remember { SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault()) }
    val formattedDate = remember(expense.date) { dateFormatter.format(expense.date) }
    val paidByLabel = stringResource(R.string.paid_by_label, paidByName)
    
    val supportingText = remember(expense.description, categoryName, formattedDate, paidByLabel) {
        buildString {
            if (expense.description.isNotBlank() && categoryName != null) {
                append(expense.description)
                append(" · ")
            }
            append(formattedDate)
            append(" · ")
            append(paidByLabel)
        }
    }

    Surface(
        onClick = onEditClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        ListItem(
            headlineContent = { Text(displayName) },
            supportingContent = { Text(supportingText) },
            leadingContent = {
                if (paidByAvatarUrl != null) {
                    AsyncImage(
                        model = paidByAvatarUrl,
                        contentDescription = "Avatar",
                        modifier = Modifier.size(40.dp).clip(androidx.compose.foundation.shape.CircleShape),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Surface(
                        shape = androidx.compose.foundation.shape.CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Default.AccountCircle,
                                contentDescription = null,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
            },
            trailingContent = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "${getCurrencySymbol(expense.currency)}${"%.2f".format(expense.amount)}",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onDeleteClick) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = stringResource(R.string.delete),
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        )
    }
}
