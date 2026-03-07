package com.example.expenseapp.ui.screens.group

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import com.example.expenseapp.domain.model.Group
import com.example.expenseapp.domain.repository.ExpenseRepository
import com.example.expenseapp.domain.repository.GroupRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject
import androidx.compose.ui.res.stringResource
import com.example.expenseapp.R

data class SettleUpUiState(
    val group: Group? = null,
    val isLoading: Boolean = true,
    val isSettling: Boolean = false,
    val settleSuccess: Boolean = false
)

@HiltViewModel
class SettleUpViewModel @Inject constructor(
    private val groupRepository: GroupRepository,
    private val expenseRepository: ExpenseRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val groupId: String = checkNotNull(savedStateHandle["groupId"])

    private val _uiState = MutableStateFlow(SettleUpUiState())
    val uiState: StateFlow<SettleUpUiState> = _uiState.asStateFlow()

    init {
        loadGroup()
    }

    private fun loadGroup() {
        viewModelScope.launch {
            val group = groupRepository.getGroupById(groupId)
            _uiState.update { it.copy(group = group, isLoading = false) }
        }
    }

    fun settleUp() {
        viewModelScope.launch {
            _uiState.update { it.copy(isSettling = true) }
            try {
                expenseRepository.archiveExpenses(groupId)
                _uiState.update { it.copy(isSettling = false, settleSuccess = true) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isSettling = false) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettleUpScreen(
    navController: NavController,
    viewModel: SettleUpViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(uiState.settleSuccess) {
        if (uiState.settleSuccess) {
            navController.popBackStack("home", false)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settle_balances)) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        if (uiState.isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    modifier = Modifier.size(100.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                Text(
                    text = stringResource(R.string.settle_all_balances_prompt, uiState.group?.name ?: ""),
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    text = stringResource(R.string.settle_up_description),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Spacer(modifier = Modifier.height(48.dp))
                
                Button(
                    onClick = { viewModel.settleUp() },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !uiState.isSettling
                ) {
                    if (uiState.isSettling) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Text(stringResource(R.string.record_payment_and_settle))
                    }
                }
                
                TextButton(
                    onClick = { navController.popBackStack() },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !uiState.isSettling
                ) {
                    Text(stringResource(R.string.cancel))
                }
            }
        }
    }
}
