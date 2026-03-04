package com.example.expenseapp.ui.screens.stats

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import java.text.DateFormatSymbols

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(
    navController: NavController,
    viewModel: StatsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val months = DateFormatSymbols().months

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Statistics") })
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            // Simple Month/Year selector row
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Simplified Month Selector (for demo)
                Text(
                    text = "${months[uiState.selectedMonth]} ${uiState.selectedYear}",
                    style = MaterialTheme.typography.titleLarge
                )
                // Add actual dropdowns or pickers here
            }

            if (uiState.isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                TotalSpendingCard(uiState.totalSpending)

                Text(
                    text = "Breakdown by Category",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(16.dp)
                )

                LazyColumn {
                    items(uiState.categoryBreakdown.toList()) { (category, amount) ->
                        CategoryStatItem(category, amount, uiState.totalSpending)
                    }
                }
            }
        }
    }
}

@Composable
fun TotalSpendingCard(total: Double) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            Text("Total Spent", style = MaterialTheme.typography.labelLarge)
            Text("$${String.format("%.2f", total)}", style = MaterialTheme.typography.headlineLarge)
        }
    }
}

@Composable
fun CategoryStatItem(category: String, amount: Double, total: Double) {
    val percentage = if (total > 0) (amount / total).toFloat() else 0f
    
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(category)
            Text("$${String.format("%.2f", amount)}")
        }
        LinearProgressIndicator(
            progress = percentage,
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp).height(8.dp),
            strokeCap = androidx.compose.ui.graphics.StrokeCap.Round
        )
    }
}
