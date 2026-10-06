package com.example.expenseapp.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.util.Log
import com.example.expenseapp.domain.model.Category
import com.example.expenseapp.domain.model.Expense
import com.example.expenseapp.domain.repository.CategoryRepository
import com.example.expenseapp.domain.repository.ExpenseRepository
import com.example.expenseapp.domain.repository.UserRepository
import com.example.expenseapp.domain.repository.currency.CurrencyRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.example.expenseapp.core.sync.SyncManager

data class HomeUiState(
    val expenses: List<Expense> = emptyList(),
    val groups: List<com.example.expenseapp.domain.model.Group> = emptyList(),
    val selectedGroup: com.example.expenseapp.domain.model.Group? = null,
    val totalBalance: Double = 0.0,
    val isLoading: Boolean = true,
    val isSynced: Boolean = false,
    val isRefreshing: Boolean = false,
    val categoryMap: Map<String, Category> = emptyMap(),
    val userMap: Map<String, com.example.expenseapp.domain.model.User> = emptyMap()
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val expenseRepository: ExpenseRepository,
    private val userRepository: UserRepository,
    private val groupRepository: com.example.expenseapp.domain.repository.GroupRepository,
    private val currencyRepository: com.example.expenseapp.domain.repository.currency.CurrencyRepository,
    private val preferenceManager: com.example.expenseapp.core.session.PreferenceManager,
    private val categoryRepository: CategoryRepository,
    private val syncManager: SyncManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState(isLoading = true))
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        loadData()
        viewModelScope.launch {
            syncManager.syncPendingItems()
        }
    }

    private data class CombinedData(
        val groups: List<com.example.expenseapp.domain.model.Group>,
        val lastGroupId: String?,
        val currentUser: com.example.expenseapp.domain.model.User?,
        val rates: Map<String, Double>,
        val categories: List<Category>,
        val users: List<com.example.expenseapp.domain.model.User>
    )

    private fun loadData() {
        viewModelScope.launch {
            val groupsFlow = groupRepository.getAllGroups().distinctUntilChanged()
            val lastGroupIdFlow = preferenceManager.lastGroupId.distinctUntilChanged()
            val currentUserFlow = userRepository.getCurrentUser()
                .distinctUntilChanged { old, new -> 
                    old?.id == new?.id && 
                    old?.name == new?.name && 
                    old?.email == new?.email && 
                    old?.fcmToken == new?.fcmToken
                }
            val categoriesFlow = categoryRepository.getAllCategories().distinctUntilChanged()
            val usersFlow = userRepository.getAllUsers().distinctUntilChanged()

            combine(
                groupsFlow,
                lastGroupIdFlow,
                currentUserFlow,
                categoriesFlow,
                usersFlow
            ) { groups, lastId, user, categories, allUsers ->
                val selectedGroup = groups.find { it.id == lastId } ?: groups.firstOrNull()
                data class BaseData(
                    val groups: List<com.example.expenseapp.domain.model.Group>,
                    val selectedGroup: com.example.expenseapp.domain.model.Group?,
                    val currentUser: com.example.expenseapp.domain.model.User?,
                    val categories: List<Category>,
                    val users: List<com.example.expenseapp.domain.model.User>
                )
                BaseData(groups, selectedGroup, user, categories, allUsers)
            }.flatMapLatest { data ->
                if (data.selectedGroup != null) {
                    viewModelScope.launch {
                        categoryRepository.syncCategoriesForGroup(data.selectedGroup.id)
                    }
                    val expensesFlow = expenseRepository.getExpensesByGroup(data.selectedGroup.id)
                        .distinctUntilChanged { old, new ->
                            if (old.size != new.size) return@distinctUntilChanged false
                            for (i in old.indices) {
                                val o = old[i]
                                val n = new[i]
                                if (o.id != n.id || o.amount != n.amount || o.description != n.description ||
                                    o.date != n.date || o.currency != n.currency || o.paidById != n.paidById ||
                                    o.categoryId != n.categoryId || o.isArchived != n.isArchived || o.splits != n.splits) {
                                    return@distinctUntilChanged false
                                }
                            }
                            true
                        }

                    combine(
                        expensesFlow,
                        flow { emit(currencyRepository.getExchangeRates(data.selectedGroup.mainCurrency)) }
                    ) { expenses, rates ->
                        val effectiveUserId = data.currentUser?.id ?: preferenceManager.userId.firstOrNull()
                        val activeExpenses = expenses.filter { !it.isArchived }

                        // Guard: if any non-trivial active expense has zero splits,
                        // Room emitted a partial update mid-sync. Use a sentinel to keep the old balance.
                        val isMidSync = activeExpenses.any { it.amount > 0.01 && it.splits.isEmpty() }

                        HomeUiState(
                            expenses = expenses,
                            groups = data.groups,
                            selectedGroup = data.selectedGroup,
                            // Keep the current balance during a mid-sync transient state.
                            // The caller will detect isMidSync=true and not override the existing balance.
                            totalBalance = if (isMidSync) Double.NaN
                                          else calculateTotalBalance(expenses, effectiveUserId, rates),
                            isLoading = false,
                            categoryMap = data.categories.associateBy { it.id },
                            userMap = data.users.associateBy { it.id }
                        )
                    }
                } else {
                    flowOf(
                        HomeUiState(
                            expenses = emptyList(),
                            groups = data.groups,
                            selectedGroup = null,
                            totalBalance = 0.0,
                            isLoading = false,
                            categoryMap = data.categories.associateBy { it.id },
                            userMap = data.users.associateBy { it.id }
                        )
                    )
                }
            }
            .distinctUntilChanged { old, new ->
                old.isLoading == new.isLoading &&
                old.selectedGroup?.id == new.selectedGroup?.id &&
                old.totalBalance == new.totalBalance &&
                old.expenses.size == new.expenses.size &&
                old.groups.size == new.groups.size &&
                old.isRefreshing == new.isRefreshing
            }
            .collect { state ->
                // Loading Lock: If we have already successfully loaded data, do not revert to a loading or empty state 
                // just because a background sync temporarily cleared a table.
                val currState = _uiState.value
                if (!currState.isLoading && state.groups.isEmpty() && currState.groups.isNotEmpty()) {
                    // Ignore transient empty states if we already had data
                    return@collect
                }
                // Mid-sync guard: don't override a valid balance with NaN (mid-sync transient)
                if (state.totalBalance.isNaN()) {
                    _uiState.value = state.copy(totalBalance = currState.totalBalance)
                    return@collect
                }
                _uiState.value = state
            }
        }
    }

    fun selectGroup(groupId: String) {
        viewModelScope.launch {
            preferenceManager.saveLastGroup(groupId)
        }
    }

    private fun calculateTotalBalance(
        expenses: List<Expense>,
        currentUserId: String?,
        rates: Map<String, Double>
    ): Double {
        if (currentUserId == null) return 0.0
        var balance = 0.0
        expenses.filter { !it.isArchived }.forEach { expense ->
            val rate = rates[expense.currency] ?: 1.0
            
            // If the user paid, their balance increases by the total amount (converted)
            if (expense.paidById == currentUserId) {
                balance += (expense.amount / rate)
            }
            
            // Subtract what the user owes from the total (converted)
            val userSplit = expense.splits.find { it.owedById == currentUserId }
            if (userSplit != null) {
                balance -= (userSplit.amountOwed / rate)
            }
        }
        return balance
    }
    fun deleteExpense(expense: com.example.expenseapp.domain.model.Expense) {
        viewModelScope.launch {
            expenseRepository.deleteExpense(expense)
        }
    }

    fun refresh() {
        val groupId = _uiState.value.selectedGroup?.id
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true) }
            try {
                // First push any pending offline changes
                syncManager.syncPendingItems()

                // Sync everything relevant to the Home screen
                userRepository.syncUserFromSupabase(force = true)
                groupRepository.syncGroupsFromSupabase(force = true)
                if (groupId != null) {
                    expenseRepository.syncExpensesFromSupabase(groupId, force = true)
                    groupRepository.syncGroupMembersFromSupabase(groupId)
                    categoryRepository.syncCategoriesForGroup(groupId)
                }
            } catch (e: Exception) {
                // We might want to show an error, but for pull-to-refresh
                // usually silently failing or just stopping the spinner is fine
                Log.e("HomeViewModel", "Refresh failed", e)
            } finally {
                _uiState.update { it.copy(isRefreshing = false) }
            }
        }
    }
}
