package com.example.expenseapp.ui.screens.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.expenseapp.domain.model.Category
import com.example.expenseapp.domain.model.Expense
import com.example.expenseapp.domain.model.Group
import com.example.expenseapp.domain.repository.CategoryRepository
import com.example.expenseapp.domain.repository.ExpenseRepository
import com.example.expenseapp.domain.repository.GroupRepository
import com.example.expenseapp.domain.repository.currency.CurrencyRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.*
import javax.inject.Inject

data class StatsUiState(
    val categoryBreakdown: List<CategoryStats> = emptyList(),
    val totalSpending: Double = 0.0,
    val preferredCurrency: String = "EUR",
    val startDate: Long = Calendar.getInstance().apply { 
        set(Calendar.DAY_OF_MONTH, 1)
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis,
    val endDate: Long = Calendar.getInstance().apply {
        set(Calendar.DAY_OF_MONTH, getActualMaximum(Calendar.DAY_OF_MONTH))
        set(Calendar.HOUR_OF_DAY, 23)
        set(Calendar.MINUTE, 59)
        set(Calendar.SECOND, 59)
        set(Calendar.MILLISECOND, 999)
    }.timeInMillis,
    val categories: List<Category> = emptyList(),
    val selectedCategoryIds: Set<String> = emptySet(),
    val groups: List<Group> = emptyList(),
    val selectedGroupId: String? = null,
    val isLoading: Boolean = false
)

data class CategoryStats(
    val category: Category?,
    val amountInBase: Double,
    val percentage: Float
)

@HiltViewModel
class StatsViewModel @Inject constructor(
    private val expenseRepository: ExpenseRepository,
    private val categoryRepository: CategoryRepository,
    private val groupRepository: GroupRepository,
    private val currencyRepository: CurrencyRepository,
    private val preferenceManager: com.example.expenseapp.core.session.PreferenceManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(StatsUiState(isLoading = true))
    val uiState: StateFlow<StatsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            categoryRepository.getAllCategories().collect { categories ->
                _uiState.update { it.copy(categories = categories) }
            }
        }
        viewModelScope.launch {
            groupRepository.getAllGroups().collect { groups ->
                _uiState.update { it.copy(groups = groups) }
            }
        }
        viewModelScope.launch {
            preferenceManager.lastCurrency.collect { currency ->
                _uiState.update { it.copy(preferredCurrency = currency ?: "EUR") }
            }
        }
        observeData()
    }

    private fun observeData() {
        viewModelScope.launch {
            combine(
                expenseRepository.getAllExpenses(),
                _uiState.map { it.startDate to it.endDate }.distinctUntilChanged(),
                _uiState.map { it.selectedCategoryIds }.distinctUntilChanged(),
                _uiState.map { it.selectedGroupId }.distinctUntilChanged(),
                _uiState.map { it.preferredCurrency }.distinctUntilChanged()
                    .flatMapLatest { flow { emit(currencyRepository.getExchangeRates(it)) } }
            ) { expenses, dates, selectedCategoryIds, selectedGroupId, rates ->
                val (start, end) = dates

                val filtered = expenses.filter { expense ->
                    val isInRange = expense.date in start..end
                    val matchesCategory = selectedCategoryIds.isEmpty() || selectedCategoryIds.contains(expense.categoryId)
                    val matchesGroup = selectedGroupId == null || expense.groupId == selectedGroupId
                    isInRange && matchesCategory && matchesGroup
                }

                val totalInBase = filtered.sumOf { expense ->
                    val rate = rates[expense.currency] ?: 1.0
                    expense.amount / rate
                }

                val breakdown = filtered.groupBy { it.categoryId }
                    .map { (categoryId, categoryExpenses) ->
                        val amountInBase = categoryExpenses.sumOf { expense ->
                            val rate = rates[expense.currency] ?: 1.0
                            expense.amount / rate
                        }
                        CategoryStats(
                            category = _uiState.value.categories.find { it.id == categoryId },
                            amountInBase = amountInBase,
                            percentage = if (totalInBase > 0.0) (amountInBase / totalInBase).toFloat() else 0f
                        )
                    }
                    .sortedByDescending { it.amountInBase }

                _uiState.update { 
                    it.copy(
                        categoryBreakdown = breakdown,
                        totalSpending = totalInBase,
                        isLoading = false
                    )
                }
            }.collect()
        }
    }

    fun onDateRangeChanged(start: Long, end: Long) {
        _uiState.update { it.copy(startDate = start, endDate = end, isLoading = true) }
    }

    fun onGroupSelected(groupId: String?) {
        _uiState.update { it.copy(selectedGroupId = groupId, isLoading = true) }
    }

    fun toggleCategoryFilter(categoryId: String) {
        _uiState.update { state ->
            val newSelection = if (state.selectedCategoryIds.contains(categoryId)) {
                state.selectedCategoryIds - categoryId
            } else {
                state.selectedCategoryIds + categoryId
            }
            state.copy(selectedCategoryIds = newSelection, isLoading = true)
        }
    }
    
    fun clearCategoryFilters() {
        _uiState.update { it.copy(selectedCategoryIds = emptySet(), isLoading = true) }
    }
}
