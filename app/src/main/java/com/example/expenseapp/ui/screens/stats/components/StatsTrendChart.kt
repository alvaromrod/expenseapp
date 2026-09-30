package com.example.expenseapp.ui.screens.stats.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.expenseapp.ui.screens.stats.MonthlySpending
import com.example.expenseapp.ui.util.getCurrencySymbol
import com.patrykandpatrick.vico.compose.axis.horizontal.rememberBottomAxis
import com.patrykandpatrick.vico.compose.axis.vertical.rememberStartAxis
import com.patrykandpatrick.vico.compose.chart.Chart
import com.patrykandpatrick.vico.compose.chart.column.columnChart
import com.patrykandpatrick.vico.core.entry.ChartEntryModelProducer
import com.patrykandpatrick.vico.core.entry.entryOf
import androidx.compose.ui.res.stringResource
import com.example.expenseapp.R

@Composable
fun StatsTrendChart(
    monthlyTrend: List<MonthlySpending>,
    currency: String,
    modifier: Modifier = Modifier
) {
    if (monthlyTrend.isEmpty()) return

    val entryModelProducer = remember(monthlyTrend) {
        ChartEntryModelProducer(
            monthlyTrend.mapIndexed { index, spending ->
                entryOf(index.toFloat(), spending.amount.toFloat())
            }
        )
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.monthly_spending_trend),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            Chart(
                chart = columnChart(),
                chartModelProducer = entryModelProducer,
                startAxis = rememberStartAxis(
                    valueFormatter = { value, _ ->
                        "${getCurrencySymbol(currency)}${value.toInt()}"
                    }
                ),
                bottomAxis = rememberBottomAxis(
                    valueFormatter = { value, _ ->
                        monthlyTrend.getOrNull(value.toInt())?.monthLabel ?: ""
                    }
                ),
                modifier = Modifier
                    .height(200.dp)
                    .fillMaxWidth()
            )
        }
    }
}
