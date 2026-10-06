package com.example.expenseapp.ui.screens.group

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.expenseapp.domain.model.Group
import com.example.expenseapp.domain.model.User
import com.example.expenseapp.domain.repository.ExpenseRepository
import com.example.expenseapp.domain.repository.GroupRepository
import com.example.expenseapp.domain.repository.currency.CurrencyRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DebtTransfer(
    val fromUser: User,
    val toUser: User,
    val amount: Double
)

data class UserBalance(
    val user: User,
    val balance: Double
)

data class GroupBalancesUiState(
    val group: Group? = null,
    val userBalances: List<UserBalance> = emptyList(),
    val suggestedTransfers: List<DebtTransfer> = emptyList(),
    val isLoading: Boolean = true
)

@HiltViewModel
class GroupBalancesViewModel @Inject constructor(
    private val groupRepository: GroupRepository,
    private val expenseRepository: ExpenseRepository,
    private val currencyRepository: CurrencyRepository,
    private val categoryRepository: com.example.expenseapp.domain.repository.CategoryRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val groupId: String = checkNotNull(savedStateHandle["groupId"])

    private val _uiState = MutableStateFlow(GroupBalancesUiState())
    val uiState: StateFlow<GroupBalancesUiState> = _uiState.asStateFlow()

    init {
        loadBalances()
    }

    private fun loadBalances() {
        viewModelScope.launch {
            combine(
                groupRepository.getMembersForGroup(groupId),
                expenseRepository.getExpensesByGroup(groupId),
                groupRepository.getGroupFlow(groupId)
            ) { members, expenses, group ->
                if (group == null || members.isEmpty() || members.any { it.name.contains("Unknown", ignoreCase = true) }) {
                    return@combine GroupBalancesUiState(isLoading = true)
                }

                // Guard: if any non-trivial non-archived expense has zero splits it means
                // Room emitted a partial update mid-sync (expenses written, splits not yet).
                // Skip this emission to avoid showing a temporarily wrong balance.
                val activeExpenses = expenses.filter { !it.isArchived }
                val hasSplitlessPaidExpense = activeExpenses.any { expense ->
                    expense.amount > 0.01 && expense.splits.isEmpty()
                }
                if (hasSplitlessPaidExpense) {
                    return@combine GroupBalancesUiState(isLoading = true)
                }

                val rates = currencyRepository.getExchangeRates(group.mainCurrency)
                
                val balances = members.map { member ->
                    var balance = 0.0
                    activeExpenses.forEach { expense ->
                        val rate = rates[expense.currency] ?: 1.0
                        if (expense.paidById == member.id) {
                            balance += (expense.amount / rate)
                        }
                        val split = expense.splits.find { it.owedById == member.id }
                        if (split != null) {
                            balance -= (split.amountOwed / rate)
                        }
                    }
                    UserBalance(member, balance)
                }

                val transfers = calculateSuggestedTransfers(balances)

                GroupBalancesUiState(
                    group = group,
                    userBalances = balances.sortedByDescending { it.balance },
                    suggestedTransfers = transfers,
                    isLoading = false
                )
            }
            // Increased from 150ms to 400ms: allows the SQLite @Transaction
            // (which writes expenses then splits) to fully commit before recalculating.
            .debounce(400)
            .collect { state ->
                val currState = _uiState.value
                if (!currState.isLoading && state.isLoading) {
                    // Lock: don't revert to loading if we already have correct data
                    return@collect
                }
                _uiState.value = state
            }
        }
    }

    private fun calculateSuggestedTransfers(balances: List<UserBalance>): List<DebtTransfer> {
        val debtors = balances.filter { it.balance < -0.01 }
            .map { it.user to -it.balance }
            .toMutableList()
        val creditors = balances.filter { it.balance > 0.01 }
            .map { it.user to it.balance }
            .toMutableList()

        val transfers = mutableListOf<DebtTransfer>()
        var dIdx = 0
        var cIdx = 0

        while (dIdx < debtors.size && cIdx < creditors.size) {
            val (debtor, debtAmount) = debtors[dIdx]
            val (creditor, creditAmount) = creditors[cIdx]

            val transferAmount = minOf(debtAmount, creditAmount)
            transfers.add(DebtTransfer(debtor, creditor, transferAmount))

            debtors[dIdx] = debtor to (debtAmount - transferAmount)
            creditors[cIdx] = creditor to (creditAmount - transferAmount)

            if (debtors[dIdx].second < 0.01) dIdx++
            if (creditors[cIdx].second < 0.01) cIdx++
        }
        return transfers
    }

    fun settleTransfer(transfer: DebtTransfer) {
        viewModelScope.launch {
            val group = _uiState.value.group ?: return@launch
            
            // Create a settlement expense
            val expense = com.example.expenseapp.domain.model.Expense(
                id = java.util.UUID.randomUUID().toString(),
                groupId = group.id,
                paidById = transfer.fromUser.id, // Debtor pays creditor
                description = "Settlement: ${transfer.fromUser.name} -> ${transfer.toUser.name}",
                amount = transfer.amount,
                currency = group.mainCurrency,
                date = System.currentTimeMillis(),
                categoryId = "settlement", // Special ID for filtering if needed
                splits = listOf(
                    com.example.expenseapp.domain.model.Split(
                        id = java.util.UUID.randomUUID().toString(),
                        expenseId = "", // Filled by repository usually
                        owedById = transfer.toUser.id,
                        amountOwed = transfer.amount
                    )
                )
            )
            expenseRepository.upsertExpense(expense)
        }
    }
}
