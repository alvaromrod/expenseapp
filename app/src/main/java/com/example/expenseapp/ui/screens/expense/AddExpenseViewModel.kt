package com.example.expenseapp.ui.screens.expense

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.expenseapp.core.session.PreferenceManager
import com.example.expenseapp.core.util.OCREngine
import com.example.expenseapp.domain.model.Category
import com.example.expenseapp.domain.model.Expense
import com.example.expenseapp.domain.model.Split
import com.example.expenseapp.domain.model.User
import com.example.expenseapp.domain.repository.CategoryRepository
import com.example.expenseapp.domain.repository.ExpenseRepository
import com.example.expenseapp.domain.repository.GroupRepository
import com.example.expenseapp.domain.repository.UserRepository
import com.example.expenseapp.domain.repository.currency.CurrencyRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class AddExpenseViewModel @Inject constructor(
    private val expenseRepository: ExpenseRepository,
    private val userRepository: UserRepository,
    private val groupRepository: GroupRepository,
    private val categoryRepository: CategoryRepository,
    private val ocrEngine: OCREngine,
    private val currencyRepository: CurrencyRepository,
    private val preferenceManager: PreferenceManager,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    val currencies: List<String> = currencyRepository.getSupportedCurrencies()

    private val _isScanning = MutableStateFlow(false)
    val isScanning = _isScanning.asStateFlow()

    private val _selectedGroupId = MutableStateFlow<String?>(null)
    val selectedGroupId = _selectedGroupId.asStateFlow()

    // Last-used currency, initialized from DataStore
    private val _selectedCurrency = MutableStateFlow("EUR")
    val selectedCurrency = _selectedCurrency.asStateFlow()

    // Category selection
    val categories: StateFlow<List<Category>> = categoryRepository.getAllCategories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _selectedCategoryId = MutableStateFlow<String?>(null)
    val selectedCategoryId = _selectedCategoryId.asStateFlow()

    private val _selectedDate = MutableStateFlow(System.currentTimeMillis())
    val selectedDate = _selectedDate.asStateFlow()

    private val _selectedPayerId = MutableStateFlow<String?>(null)
    val selectedPayerId = _selectedPayerId.asStateFlow()

    // Edit-mode pre-fill fields
    private val _editDescription = MutableStateFlow<String?>(null)
    val editDescription = _editDescription.asStateFlow()
    private val _editAmount = MutableStateFlow<String?>(null)
    val editAmount = _editAmount.asStateFlow()
    private val _editSplits = MutableStateFlow<Map<String, Double>>(emptyMap())
    val editSplits = _editSplits.asStateFlow()

    // Expense ID for edit mode (null = create mode)
    private val expenseId: String? = savedStateHandle["expenseId"]
    
    val isEditMode: Boolean = expenseId != null

    val groups: StateFlow<List<com.example.expenseapp.domain.model.Group>> = groupRepository.getAllGroups()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val currentUser = userRepository.getCurrentUser()
        .onEach { user ->
            if (expenseId == null && _selectedPayerId.value == null) {
                _selectedPayerId.value = user?.id
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // Always include the current user in the split list, plus any group members
    val users: StateFlow<List<User>> = kotlinx.coroutines.flow.combine(
        _selectedGroupId.flatMapLatest { groupId ->
            if (groupId != null) groupRepository.getMembersForGroup(groupId)
            else flowOf(emptyList())
        },
        currentUser
    ) { groupMembers, me ->
        // Merge: current user first, then any group members not already in the list
        val base = if (me != null) listOf(me) else emptyList()
        val extras = groupMembers.filter { member -> base.none { it.id == member.id } }
        base + extras
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        viewModelScope.launch {
            // Load last-used group and currency from DataStore
            val lastGroupId = preferenceManager.lastGroupId.first()
            val lastCurrency = preferenceManager.lastCurrency.first()
            _selectedCurrency.value = lastCurrency ?: "EUR"

            // In edit mode, load existing expense data
            if (expenseId != null) {
                val expense = expenseRepository.getExpenseById(expenseId)
                expense?.let {
                    _selectedGroupId.value = it.groupId
                    _selectedCurrency.value = it.currency
                    _selectedCategoryId.value = it.categoryId
                    _selectedDate.value = it.date
                    _editDescription.value = it.description
                    _editAmount.value = if (it.amount > 0) it.amount.toString() else ""
                    _selectedPayerId.value = it.paidById
                    _editSplits.value = it.splits.associate { split -> split.owedById to split.amountOwed }
                }
            } else if (lastGroupId != null) {
                _selectedGroupId.value = lastGroupId
                // If we have a group, prefer its main currency for NEW expenses
                val group = groupRepository.getGroupById(lastGroupId)
                group?.let {
                    _selectedCurrency.value = it.mainCurrency
                }
                // Trigger sync for the persistent group on start
                groupRepository.syncGroupMembersFromSupabase(lastGroupId)
                categoryRepository.syncCategoriesForGroup(lastGroupId)
            }
        }
    }

    fun onGroupSelected(groupId: String) {
        _selectedGroupId.value = groupId
        viewModelScope.launch { 
            preferenceManager.saveLastGroup(groupId)
            val group = groupRepository.getGroupById(groupId)
            group?.let {
                _selectedCurrency.value = it.mainCurrency
            }
            // Sync group members and categories
            groupRepository.syncGroupMembersFromSupabase(groupId)
            categoryRepository.syncCategoriesForGroup(groupId)
        }
    }

    fun onCurrencySelected(currency: String) {
        _selectedCurrency.value = currency
        viewModelScope.launch { preferenceManager.saveLastCurrency(currency) }
    }

    fun onCategorySelected(categoryId: String) {
        _selectedCategoryId.value = categoryId
    }

    fun onDateSelected(timestamp: Long) {
        _selectedDate.value = timestamp
    }

    fun onPayerSelected(userId: String) {
        _selectedPayerId.value = userId
    }

    fun saveCategory(id: String, name: String, iconName: String, colorHex: String) {
        viewModelScope.launch {
            val category = Category(
                id = id,
                name = name,
                iconName = iconName,
                colorHex = colorHex,
                groupId = _selectedGroupId.value // Associate with current group
            )
            categoryRepository.upsertCategory(category)
        }
    }

    fun deleteCategory(categoryId: String) {
        viewModelScope.launch {
            categoryRepository.deleteCategory(categoryId)
            if (_selectedCategoryId.value == categoryId) {
                _selectedCategoryId.value = null
            }
        }
    }

    fun scanReceipt(bitmap: android.graphics.Bitmap, onResult: (OCREngine.ScanResult) -> Unit) {
        viewModelScope.launch {
            _isScanning.value = true
            val result = ocrEngine.scanReceipt(bitmap)
            onResult(result)
            _isScanning.value = false
        }
    }

    fun saveExpense(
        description: String,
        amount: Double,
        groupId: String,
        splitsMap: Map<String, Double>,
        currency: String = "EUR"
    ) {
        val categoryId = _selectedCategoryId.value ?: "other"
        val paidById = _selectedPayerId.value ?: currentUser.value?.id ?: "me"
        viewModelScope.launch {
            val id = expenseId ?: UUID.randomUUID().toString()
            val splits = splitsMap.map { (userId, splitAmount) ->
                Split(
                    id = UUID.randomUUID().toString(),
                    expenseId = id,
                    owedById = userId,
                    amountOwed = splitAmount
                )
            }

            val expense = Expense(
                id = id,
                description = description,
                amount = amount,
                groupId = groupId,
                paidById = paidById,
                currency = currency,
                date = _selectedDate.value,
                categoryId = categoryId,
                splits = splits
            )
            expenseRepository.upsertExpense(expense)

            // Persist last-used prefs
            preferenceManager.saveLastGroup(groupId)
            preferenceManager.saveLastCurrency(currency)
        }
    }
}
