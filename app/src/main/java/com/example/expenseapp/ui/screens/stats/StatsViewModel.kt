package com.example.expenseapp.ui.screens.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.expenseapp.domain.model.Category
import com.example.expenseapp.domain.model.Expense
import com.example.expenseapp.domain.model.Group
import com.example.expenseapp.domain.model.User
import com.example.expenseapp.domain.repository.CategoryRepository
import com.example.expenseapp.domain.repository.ExpenseRepository
import com.example.expenseapp.domain.repository.GroupRepository
import com.example.expenseapp.domain.repository.UserRepository
import com.example.expenseapp.domain.repository.currency.CurrencyRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.text.SimpleDateFormat
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.*
import javax.inject.Inject
import android.util.Log

data class CurrencyReport(
    val currency: String,
    val totalSpending: Double,
    val categoryBreakdown: List<CategoryStats>,
    val monthlyTrend: List<MonthlySpending> = emptyList()
)

data class MonthlySpending(
    val monthLabel: String,
    val amount: Double,
    val monthStart: Long
)

data class StatsUiState(
    val reports: List<CurrencyReport> = emptyList(),
    val preferredCurrency: String = "EUR",
    val currentUserId: String? = null,
    val currentUserName: String? = null,
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
    val isLoading: Boolean = false,
    val expandedCategoryId: String? = null,
    val isOnlyMeFilter: Boolean = false,
    val showArchived: Boolean = false
)

data class ExpenseStatDetail(
    val expense: Expense,
    val amountToDisplay: Double
)

data class CategoryStats(
    val category: Category?,
    val amountInBase: Double,
    val percentage: Float,
    val expenses: List<ExpenseStatDetail> = emptyList()
)

@HiltViewModel
class StatsViewModel @Inject constructor(
    private val expenseRepository: ExpenseRepository,
    private val categoryRepository: CategoryRepository,
    private val groupRepository: GroupRepository,
    private val userRepository: UserRepository,
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
            preferenceManager.userId.collect { userId ->
                _uiState.update { it.copy(currentUserId = userId) }
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
        data class StatisticsFilterState(
            val startDate: Long,
            val endDate: Long,
            val selectedCategoryIds: Set<String>,
            val selectedGroupId: String?,
            val preferredCurrency: String,
            val isOnlyMe: Boolean,
            val currentUserId: String?,
            val showArchived: Boolean
        )

        viewModelScope.launch {
            // This flow emits the definitive Set of expense IDs the current user has a split on.
            val userExpenseIdsFlow: Flow<Set<String>?> = _uiState
                .map { state -> state.isOnlyMeFilter to state.currentUserId }
                .distinctUntilChanged()
                .flatMapLatest { pair ->
                    val isOnlyMe = pair.first
                    val userId = pair.second
                    if (isOnlyMe && userId != null) {
                        expenseRepository.getExpenseIdsForUser(userId).map { it as Set<String>? }
                    } else {
                        flowOf(null)
                    }
                }

            // Combine UI state parts into a clean data class
            val filterStateFlow = _uiState.map { state -> 
                StatisticsFilterState(
                    startDate = state.startDate,
                    endDate = state.endDate,
                    selectedCategoryIds = state.selectedCategoryIds,
                    selectedGroupId = state.selectedGroupId,
                    preferredCurrency = state.preferredCurrency,
                    isOnlyMe = state.isOnlyMeFilter,
                    currentUserId = state.currentUserId,
                    showArchived = state.showArchived
                )
            }.distinctUntilChanged()

            combine(
                expenseRepository.getAllExpenses(),
                groupRepository.getAllGroups(),
                filterStateFlow,
                userExpenseIdsFlow,
                userRepository.getCurrentUser()
            ) { expenses: List<Expense>, groups: List<Group>, filterState: StatisticsFilterState, allowedExpenseIds: Set<String>?, currentUser: User? ->
                val start = filterState.startDate
                val end = filterState.endDate
                val selectedCategoryIds = filterState.selectedCategoryIds
                val selectedGroupId = filterState.selectedGroupId
                val preferredCurrency = filterState.preferredCurrency
                val isOnlyMe = filterState.isOnlyMe
                val currentUserId = filterState.currentUserId
                val showArchived = filterState.showArchived

                // Initial filtering - allowedExpenseIds is the authoritative Room-backed allowlist
                Log.d("StatsViewModel", "Filtering for user: $currentUserId, Only Me: $isOnlyMe, Allowed IDs: ${allowedExpenseIds?.size ?: "ALL"}")

                val filtered = expenses.filter { expense ->
                    val isInRange = expense.date in start..end
                    val matchesCategory = selectedCategoryIds.isEmpty() || selectedCategoryIds.contains(expense.categoryId)
                    val matchesGroup = selectedGroupId == null || expense.groupId == selectedGroupId
                    val matchesArchive = showArchived || !expense.isArchived
                    val hasParticipation = allowedExpenseIds == null || allowedExpenseIds.contains(expense.id)

                    isInRange && matchesCategory && matchesGroup && matchesArchive && hasParticipation
                }

                // Determine target currencies
                val targetCurrencies = if (selectedGroupId != null) {
                    val group = groups.find { it.id == selectedGroupId }
                    listOf(group?.mainCurrency ?: preferredCurrency)
                } else {
                    val currencies = groups.map { it.mainCurrency }.distinct()
                    if (currencies.size <= 1) {
                        listOf(currencies.firstOrNull() ?: preferredCurrency)
                    } else {
                        currencies
                    }
                }

                val reportsList = mutableListOf<CurrencyReport>()
                for (targetCurrency in targetCurrencies) {
                    val rates = currencyRepository.getExchangeRates(targetCurrency)
                    
                    // Filter expenses for THIS report
                    // If we have multiple currencies, an expense belongs to a report if it's in a group that uses that currency.
                    // However, an expense itself has a currency. 
                    // To keep it simple and follow user request: "If there's more than one currency, display them separately".
                    // This means grouping expenses by their currency? Or by their group's main currency?
                    // Usually, if a group is in EUR, all its expenses SHOULD be in EUR (or converted).
                    // Let's assume we group by targetCurrency.
                    
                    val expensesForCurrency = if (selectedGroupId != null) {
                        filtered // All filtered belong to the selected group
                    } else {
                        filtered.filter { expense ->
                            val group = groups.find { it.id == expense.groupId }
                            group?.mainCurrency == targetCurrency
                        }
                    }

                    if (expensesForCurrency.isEmpty()) continue

                    val totalSpending = expensesForCurrency.sumOf { expense ->
                        val amount = if (isOnlyMe) {
                            if (currentUserId == null) 0.0
                            else expense.splits.find { it.owedById.equals(currentUserId, ignoreCase = true) }?.amountOwed ?: 0.0
                        } else {
                            expense.amount
                        }
                        
                        if (expense.currency == targetCurrency) {
                            amount
                        } else {
                            val rate = rates[expense.currency] ?: 1.0
                            amount / rate
                        }
                    }

                    val breakdown = expensesForCurrency.groupBy { it.categoryId }
                        .mapNotNull { (categoryId, categoryExpenses) ->
                            val details = categoryExpenses.map { expense ->
                                val amount = if (isOnlyMe) {
                                    if (currentUserId == null) 0.0
                                    else expense.splits.find { it.owedById.equals(currentUserId, ignoreCase = true) }?.amountOwed ?: 0.0
                                } else {
                                    expense.amount
                                }
                                ExpenseStatDetail(expense, amount)
                            }.filter { !isOnlyMe || it.amountToDisplay > 0.0 }
                            
                            if (details.isEmpty()) return@mapNotNull null

                            val amountInBase = details.sumOf { detail ->
                                val expense = detail.expense
                                if (expense.currency == targetCurrency) {
                                    detail.amountToDisplay
                                } else {
                                    val rate = rates[expense.currency] ?: 1.0
                                    detail.amountToDisplay / rate
                                }
                            }

                            CategoryStats(
                                category = _uiState.value.categories.find { it.id == categoryId },
                                amountInBase = amountInBase,
                                percentage = if (totalSpending > 0.0) (amountInBase / totalSpending).toFloat() else 0f,
                                expenses = details.sortedByDescending { it.expense.date }
                            )
                        }
                        .sortedByDescending { it.amountInBase }



                    // Calculate Monthly Trend (Last 6 months ending at filterState.endDate)
                    val trendList = mutableListOf<MonthlySpending>()
                    val calendar = Calendar.getInstance().apply { timeInMillis = filterState.endDate }
                    val dateFormatter = SimpleDateFormat("MMM", Locale.getDefault())
                    
                    for (i in 0 until 6) {
                        val monthEnd = calendar.timeInMillis
                        calendar.set(Calendar.DAY_OF_MONTH, 1)
                        calendar.set(Calendar.HOUR_OF_DAY, 0)
                        calendar.set(Calendar.MINUTE, 0)
                        calendar.set(Calendar.SECOND, 0)
                        calendar.set(Calendar.MILLISECOND, 0)
                        val monthStart = calendar.timeInMillis
                        
                        val expensesInMonth = filtered.filter { it.date in monthStart..monthEnd }
                        val monthTotal = expensesInMonth.sumOf { expense ->
                            val amount = if (isOnlyMe) {
                                if (currentUserId == null) 0.0
                                else expense.splits.find { it.owedById.equals(currentUserId, ignoreCase = true) }?.amountOwed ?: 0.0
                            } else {
                                expense.amount
                            }
                            
                            if (expense.currency == targetCurrency) {
                                amount
                            } else {
                                val rate = rates[expense.currency] ?: 1.0
                                amount / rate
                            }
                        }
                        
                        trendList.add(0, MonthlySpending(
                            monthLabel = dateFormatter.format(calendar.time),
                            amount = monthTotal,
                            monthStart = monthStart
                        ))
                        
                        // Move to previous month
                        calendar.add(Calendar.MONTH, -1)
                        calendar.set(Calendar.DAY_OF_MONTH, calendar.getActualMaximum(Calendar.DAY_OF_MONTH))
                        calendar.set(Calendar.HOUR_OF_DAY, 23)
                        calendar.set(Calendar.MINUTE, 59)
                    }

                    reportsList.add(CurrencyReport(targetCurrency, totalSpending, breakdown, trendList))
                }

                _uiState.update { 
                    it.copy(
                        reports = reportsList,
                        currentUserId = currentUser?.id,
                        currentUserName = currentUser?.name ?: currentUser?.email,
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
        if (groupId != null) {
            viewModelScope.launch {
                categoryRepository.syncCategoriesForGroup(groupId)
            }
        }
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

    fun toggleCategoryExpansion(categoryId: String) {
        _uiState.update { state ->
            val newExpanded = if (state.expandedCategoryId == categoryId) null else categoryId
            state.copy(expandedCategoryId = newExpanded)
        }
    }

    fun toggleOnlyMeFilter() {
        _uiState.update { it.copy(isOnlyMeFilter = !it.isOnlyMeFilter, isLoading = true) }
    }

    fun toggleShowArchived() {
        _uiState.update { it.copy(showArchived = !it.showArchived, isLoading = true) }
    }
}
