package com.example.expenseapp.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.expenseapp.domain.model.Expense
import com.example.expenseapp.domain.repository.ExpenseRepository
import com.example.expenseapp.domain.repository.UserRepository
import com.example.expenseapp.domain.repository.currency.CurrencyRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    val expenses: List<Expense> = emptyList(),
    val totalBalance: Double = 0.0,
    val isLoading: Boolean = false,
    val isSynced: Boolean = false
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val expenseRepository: ExpenseRepository,
    private val userRepository: UserRepository,
    private val currencyRepository: CurrencyRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState(isLoading = true))
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        loadData()
    }

    private fun loadData() {
        viewModelScope.launch {
            combine(
                expenseRepository.getAllExpenses(),
                userRepository.getCurrentUser(),
                flow { emit(currencyRepository.getExchangeRates("USD")) }
            ) { expenses, currentUser, rates ->
                HomeUiState(
                    expenses = expenses,
                    totalBalance = calculateTotalBalance(expenses, currentUser?.id, rates),
                    isLoading = false
                )
            }.collect { state ->
                _uiState.value = state
            }
        }
    }

    private fun calculateTotalBalance(
        expenses: List<Expense>,
        currentUserId: String?,
        rates: Map<String, Double>
    ): Double {
        if (currentUserId == null) return 0.0
        var balance = 0.0
        expenses.forEach { expense ->
            val rate = rates[expense.currency] ?: 1.0
            val amountInBase = expense.amount / rate
            if (expense.paidById == currentUserId) {
                balance += amountInBase
            } else {
                balance -= (amountInBase / 2)
            }
        }
        return balance
    }
    fun deleteExpense(expense: com.example.expenseapp.domain.model.Expense) {
        viewModelScope.launch {
            expenseRepository.deleteExpense(expense)
        }
    }
}
