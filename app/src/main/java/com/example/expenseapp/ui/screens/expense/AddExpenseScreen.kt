package com.example.expenseapp.ui.screens.expense

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.example.expenseapp.core.util.OCREngine

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddExpenseScreen(
    navController: NavController,
    viewModel: AddExpenseViewModel = hiltViewModel()
) {
    var description by remember { mutableStateOf("") }
    var amountValue by remember { mutableStateOf("") }
    
    val currentUser by viewModel.currentUser.collectAsState()
    val groups by viewModel.groups.collectAsState()
    val selectedGroupId by viewModel.selectedGroupId.collectAsState()
    val selectedCurrency by viewModel.selectedCurrency.collectAsState()
    val users by viewModel.users.collectAsState()
    
    val categoryId = "food"

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Log Expense") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    val isScanning by viewModel.isScanning.collectAsState()
                    if (isScanning) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp).padding(end = 16.dp), strokeWidth = 2.dp)
                    } else {
                        IconButton(onClick = { 
                            // In a real app, this would launch the camera.
                            // For this demo, we'll simulate scanning with the dummy engine.
                            viewModel.scanReceipt(android.graphics.Bitmap.createBitmap(1, 1, android.graphics.Bitmap.Config.ARGB_8888)) { result: OCREngine.ScanResult ->
                                result.description?.let { description = it }
                                result.amount?.let { amountValue = it.toString() }
                            }
                        }) {
                            Icon(Icons.Default.Search, contentDescription = "Scan Receipt")
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .fillMaxSize()
        ) {
            val isScanning by viewModel.isScanning.collectAsState()
            if (isScanning) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp))
            }
            
            // Group Selection Dropdown
            var groupExpanded by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(
                expanded = groupExpanded,
                onExpandedChange = { groupExpanded = !groupExpanded },
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
            ) {
                OutlinedTextField(
                    value = groups.find { it.id == selectedGroupId }?.name ?: "Select Group",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Group") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = groupExpanded) },
                    modifier = Modifier.menuAnchor().fillMaxWidth()
                )
                ExposedDropdownMenu(
                    expanded = groupExpanded,
                    onDismissRequest = { groupExpanded = false }
                ) {
                    groups.forEach { group ->
                        DropdownMenuItem(
                            text = { Text(group.name) },
                            onClick = {
                                viewModel.onGroupSelected(group.id)
                                groupExpanded = false
                            }
                        )
                    }
                }
            }

            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("What was it for?") },
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
            )

            var selectedCurrencyExpanded by remember { mutableStateOf(false) }

            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = amountValue,
                    onValueChange = { amountValue = it },
                    label = { Text("Amount") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f).padding(end = 8.dp)
                )

                ExposedDropdownMenuBox(
                    expanded = selectedCurrencyExpanded,
                    onExpandedChange = { selectedCurrencyExpanded = !selectedCurrencyExpanded },
                    modifier = Modifier.width(100.dp)
                ) {
                    OutlinedTextField(
                        value = selectedCurrency,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Unit") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = selectedCurrencyExpanded) },
                        modifier = Modifier.menuAnchor()
                    )
                    ExposedDropdownMenu(
                        expanded = selectedCurrencyExpanded,
                        onDismissRequest = { selectedCurrencyExpanded = false }
                    ) {
                        viewModel.currencies.forEach { currency ->
                            DropdownMenuItem(
                                text = { Text(currency) },
                                onClick = {
                                    viewModel.onCurrencySelected(currency)
                                    selectedCurrencyExpanded = false
                                }
                            )
                        }
                    }
                }
            }

            if (selectedGroupId != null) {
                Text("Split with:", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 8.dp))
                
                val selectedUserIds = remember { mutableStateListOf<String>() }

                // Initialize selection with current users
                LaunchedEffect(users) {
                    if (selectedUserIds.isEmpty()) {
                        selectedUserIds.addAll(users.map { it.id })
                    }
                }

                users.forEach { user ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                    ) {
                        Checkbox(
                            checked = selectedUserIds.contains(user.id),
                            onCheckedChange = { checked ->
                                if (checked) selectedUserIds.add(user.id)
                                else selectedUserIds.remove(user.id)
                            }
                        )
                        Text(user.name, modifier = Modifier.padding(start = 8.dp))
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                Button(
                    onClick = {
                        val amountDouble = amountValue.toDoubleOrNull() ?: 0.0
                        viewModel.saveExpense(
                            description, 
                            amountDouble, 
                            selectedGroupId!!, 
                            currentUser?.id ?: "me", 
                            categoryId,
                            selectedUserIds.toList(),
                            selectedCurrency
                        )
                        navController.popBackStack()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = description.isNotBlank() && amountValue.isNotBlank() && selectedUserIds.isNotEmpty()
                ) {
                    Text("Save Expense")
                }
            } else {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text("Select a group to split expenses")
                }
            }
        }
    }
}
