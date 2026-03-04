package com.example.expenseapp.ui.screens.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.expenseapp.domain.model.Expense
import com.example.expenseapp.domain.repository.ExpenseRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.*
import javax.inject.Inject

data class StatsUiState(
    val categoryBreakdown: Map<String, Double> = emptyMap(),
    val totalSpending: Double = 0.0,
    val selectedMonth: Int = Calendar.getInstance().get(Calendar.MONTH),
    val selectedYear: Int = Calendar.getInstance().get(Calendar.YEAR),
    val isLoading: Boolean = false
)

@HiltViewModel
class StatsViewModel @Inject constructor(
    private val expenseRepository: ExpenseRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(StatsUiState(isLoading = true))
    val uiState: StateFlow<StatsUiState> = _uiState.asStateFlow()

    init {
        loadStats()
    }

    fun onDateChanged(month: Int, year: Int) {
        _uiState.update { it.copy(selectedMonth = month, selectedYear = year, isLoading = true) }
        loadStats()
    }

    private fun loadStats() {
        viewModelScope.launch {
            expenseRepository.getAllExpenses().collect { expenses ->
                val filtered = filterExpenses(expenses, _uiState.value.selectedMonth, _uiState.value.selectedYear)
                val breakdown = filtered.groupBy { it.categoryId }
                    .mapValues { entry -> entry.value.sumOf { it.amount } }
                
                _uiState.update { 
                    it.copy(
                        categoryBreakdown = breakdown,
                        totalSpending = breakdown.values.sum(),
                        isLoading = false
                    )
                }
            }
        }
    }

    private fun filterExpenses(expenses: List<Expense>, month: Int, year: Int): List<Expense> {
        val cal = Calendar.getInstance()
        return expenses.filter { 
            cal.timeInMillis = it.date
            cal.get(Calendar.MONTH) == month && cal.get(Calendar.YEAR) == year
        }
    }
}
