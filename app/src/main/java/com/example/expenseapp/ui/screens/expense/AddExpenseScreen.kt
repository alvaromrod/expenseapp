package com.example.expenseapp.ui.screens.expense

import androidx.compose.ui.draw.clip
import coil.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale
import com.example.expenseapp.R
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.material.icons.filled.Check
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items as lazyItems
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.example.expenseapp.core.util.OCREngine
import com.example.expenseapp.domain.model.Category
import java.util.UUID
import java.util.Calendar
import java.text.SimpleDateFormat
import java.util.Locale
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.ui.res.stringResource
import com.example.expenseapp.ui.util.getLocalizedName
import com.example.expenseapp.ui.util.getCurrencySymbol

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
    val categories by viewModel.categories.collectAsState()
    val selectedCategoryId by viewModel.selectedCategoryId.collectAsState()
    val selectedDate by viewModel.selectedDate.collectAsState()
    val selectedPayerId by viewModel.selectedPayerId.collectAsState()
    val context = LocalContext.current

    // Pre-fill fields in edit mode
    val editDescription by viewModel.editDescription.collectAsState()
    val editAmount by viewModel.editAmount.collectAsState()
    val editSplits by viewModel.editSplits.collectAsState()
    val isEditMode = viewModel.isEditMode
    
    val selectedUserIds = remember { mutableStateListOf<String>() }
    val customAmounts = remember { mutableStateMapOf<String, String>() }
    var isCustomSplit by remember { mutableStateOf(false) }

    var hasPreFilled by remember { mutableStateOf(false) }
    LaunchedEffect(editDescription, editAmount, editSplits, users) {
        if (isEditMode && !hasPreFilled && editDescription != null && users.isNotEmpty()) {
            // WAIT until all participants from the database are found in our users list
            // If they aren't all here yet, we might be mid-sync, so wait for the next emission.
            val allParticipantsLoaded = editSplits.isEmpty() || 
                editSplits.keys.all { splitUserId -> users.any { it.id == splitUserId } }
            
            if (editSplits.isNotEmpty() && !allParticipantsLoaded) {
                // Not all participants are in the 'users' list yet, wait for next update.
                return@LaunchedEffect
            }

            description = editDescription ?: ""
            amountValue = editAmount ?: ""
            
            if (editSplits.isNotEmpty()) {
                selectedUserIds.clear()
                // Safely include participants
                selectedUserIds.addAll(editSplits.keys.filter { userId -> users.any { it.id == userId } })
                
                customAmounts.clear()
                editSplits.forEach { (userId, amount) ->
                    customAmounts[userId] = String.format(Locale.US, "%.2f", amount)
                }
                
                // Determine if it was custom split
                val totalAmt = editAmount?.replace(',', '.')?.toDoubleOrNull() ?: 0.0
                val equalShare = if (selectedUserIds.isNotEmpty()) totalAmt / selectedUserIds.size else 0.0
                isCustomSplit = editSplits.values.any { Math.abs(it - equalShare) > 0.01 }
            }
            
            hasPreFilled = true
        }
    }

    var hasInteracted by remember { mutableStateOf(false) }
    var lastGroupId by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(selectedGroupId) {
        if (selectedGroupId != lastGroupId) {
            hasInteracted = false
            lastGroupId = selectedGroupId
        }
    }

    LaunchedEffect(users, hasInteracted) {
        if (!isEditMode && users.isNotEmpty() && !hasInteracted) {
            selectedUserIds.clear()
            selectedUserIds.addAll(users.map { it.id })
        }
    }

    var showManageCategoriesDialog by remember { mutableStateOf(false) }

    if (showManageCategoriesDialog) {
        CategoryManagementDialog(
            categories = categories,
            onDismiss = { showManageCategoriesDialog = false },
            onSaveCategory = { name, icon, color ->
                viewModel.saveCategory(
                    id = UUID.randomUUID().toString(),
                    name = name,
                    iconName = icon,
                    colorHex = color
                )
            },
            onDeleteCategory = { categoryId ->
                viewModel.deleteCategory(categoryId)
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.log_expense)) },
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
                            Icon(Icons.Default.Search, contentDescription = stringResource(R.string.scan_receipt))
                        }
                    }
                }
            )
        }
    ) { padding ->
        val scrollState = rememberScrollState()
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .fillMaxSize()
                .verticalScroll(scrollState)
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
                    value = groups.find { it.id == selectedGroupId }?.name ?: stringResource(R.string.select_group),
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(stringResource(R.string.group)) },
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

            // Category Selection (Primary field)
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    stringResource(R.string.category),
                    style = MaterialTheme.typography.titleMedium
                )
                TextButton(onClick = { showManageCategoriesDialog = true }) {
                    Text(stringResource(R.string.manage))
                }
            }
            @OptIn(ExperimentalLayoutApi::class)
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                maxItemsInEachRow = Int.MAX_VALUE
            ) {
                categories.forEach { category ->
                    CategoryChip(
                        category = category,
                        isSelected = selectedCategoryId == category.id,
                        onClick = { viewModel.onCategorySelected(category.id) }
                    )
                }
            }

            // Amount + Currency row
            var selectedCurrencyExpanded by remember { mutableStateOf(false) }

            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = amountValue,
                    onValueChange = { amountValue = it },
                    label = { Text(stringResource(R.string.amount)) },
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
                        label = { Text(stringResource(R.string.unit)) },
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

            // Description (optional)
            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text(stringResource(R.string.description_optional)) },
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
            )

            // Date & Time Selection
            val dateFormatter = remember { SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()) }
            val timeFormatter = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
            
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        val calendar = Calendar.getInstance().apply { timeInMillis = selectedDate }
                        DatePickerDialog(
                            context,
                            { _, year, month, dayOfMonth ->
                                val newCalendar = Calendar.getInstance().apply { 
                                    timeInMillis = selectedDate
                                    set(Calendar.YEAR, year)
                                    set(Calendar.MONTH, month)
                                    set(Calendar.DAY_OF_MONTH, dayOfMonth)
                                }
                                viewModel.onDateSelected(newCalendar.timeInMillis)
                            },
                            calendar.get(Calendar.YEAR),
                            calendar.get(Calendar.MONTH),
                            calendar.get(Calendar.DAY_OF_MONTH)
                        ).show()
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(dateFormatter.format(selectedDate))
                }

                OutlinedButton(
                    onClick = {
                        val calendar = Calendar.getInstance().apply { timeInMillis = selectedDate }
                        TimePickerDialog(
                            context,
                            { _, hourOfDay, minute ->
                                val newCalendar = Calendar.getInstance().apply {
                                    timeInMillis = selectedDate
                                    set(Calendar.HOUR_OF_DAY, hourOfDay)
                                    set(Calendar.MINUTE, minute)
                                }
                                viewModel.onDateSelected(newCalendar.timeInMillis)
                            },
                            calendar.get(Calendar.HOUR_OF_DAY),
                            calendar.get(Calendar.MINUTE),
                            true // 24 hour view
                        ).show()
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(timeFormatter.format(selectedDate))
                }
            }
            
            // Payer Selection
            if (selectedGroupId != null) {
                val selectedPayerName = users.find { it.id == selectedPayerId }?.name ?: ""
                Text(
                    stringResource(R.string.paid_by_label, selectedPayerName),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
                ) {
                    lazyItems(users) { user ->
                        FilterChip(
                            selected = selectedPayerId == user.id,
                            onClick = { viewModel.onPayerSelected(user.id) },
                            label = { Text(user.name) },
                            leadingIcon = {
                                if (selectedPayerId == user.id) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                } else if (user.avatarUrl != null) {
                                    AsyncImage(
                                        model = user.avatarUrl,
                                        contentDescription = "Avatar",
                                        modifier = Modifier.size(24.dp).clip(androidx.compose.foundation.shape.CircleShape),
                                        contentScale = ContentScale.Crop
                                    )
                                } else {
                                    Icon(
                                        Icons.Default.AccountCircle,
                                        contentDescription = null,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }
                        )
                    }
                }
            }

            if (selectedGroupId != null) {
                Text(stringResource(R.string.split_with), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 8.dp))
                
                // Toggle
                Row(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                    FilterChip(
                        selected = !isCustomSplit,
                        onClick = { isCustomSplit = false },
                        label = { Text(stringResource(R.string.equally)) },
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    FilterChip(
                        selected = isCustomSplit,
                        onClick = { 
                            isCustomSplit = true 
                            val amount = amountValue.replace(',', '.').toDoubleOrNull() ?: 0.0
                            val equalShare = if (selectedUserIds.isNotEmpty()) amount / selectedUserIds.size else 0.0
                            val formattedShare = String.format(Locale.US, "%.2f", equalShare)
                            selectedUserIds.forEach { userId ->
                                if (!customAmounts.containsKey(userId)) {
                                    customAmounts[userId] = formattedShare
                                }
                            }
                        },
                        label = { Text(stringResource(R.string.custom_amounts)) }
                    )
                }

                val totalAmount = amountValue.replace(',', '.').toDoubleOrNull() ?: 0.0
                val currentCustomTotal = customAmounts.filterKeys { selectedUserIds.contains(it) }.values.sumOf { it.replace(',', '.').toDoubleOrNull() ?: 0.0 }
                val remaining = totalAmount - currentCustomTotal

                if (isCustomSplit) {
                    Text(
                        text = stringResource(R.string.remaining_to_assign, String.format(Locale.US, "%.2f", remaining)),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (Math.abs(remaining) < 0.01) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }

                users.forEach { user ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                    ) {
                        Checkbox(
                            checked = selectedUserIds.contains(user.id),
                            onCheckedChange = { checked ->
                                hasInteracted = true
                                if (checked) {
                                    selectedUserIds.add(user.id)
                                    val equalShare = if (selectedUserIds.isNotEmpty()) totalAmount / selectedUserIds.size else 0.0
                                    customAmounts[user.id] = String.format(Locale.US, "%.2f", equalShare)
                                } else {
                                    selectedUserIds.remove(user.id)
                                }
                            }
                        )
                        if (user.avatarUrl != null) {
                            AsyncImage(
                                model = user.avatarUrl,
                                contentDescription = "Avatar",
                                modifier = Modifier.size(32.dp).clip(androidx.compose.foundation.shape.CircleShape),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Icon(
                                Icons.Default.AccountCircle,
                                contentDescription = null,
                                modifier = Modifier.size(32.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(user.name, modifier = Modifier.padding(start = 8.dp).weight(1f))
                        
                        if (selectedUserIds.contains(user.id)) {
                            if (isCustomSplit) {
                                OutlinedTextField(
                                    value = customAmounts[user.id] ?: "",
                                    onValueChange = { customAmounts[user.id] = it },
                                    modifier = Modifier.width(100.dp).height(56.dp),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    singleLine = true
                                )
                            } else {
                                val equalShare = if (selectedUserIds.isNotEmpty()) totalAmount / selectedUserIds.size else 0.0
                                Text(
                                    text = "${getCurrencySymbol(selectedCurrency)}${"%.2f".format(equalShare)}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                Button(
                    onClick = {
                        val splitsMap = mutableMapOf<String, Double>()
                        if (isCustomSplit) {
                            selectedUserIds.forEach { userId ->
                                splitsMap[userId] = customAmounts[userId]?.replace(',', '.')?.toDoubleOrNull() ?: 0.0
                            }
                        } else {
                            val equalShare = if (selectedUserIds.isNotEmpty()) totalAmount / selectedUserIds.size else 0.0
                            selectedUserIds.forEach { userId ->
                                splitsMap[userId] = equalShare
                            }
                        }
                        
                        viewModel.saveExpense(
                            description, 
                            totalAmount, 
                            selectedGroupId!!, 
                            splitsMap,
                            selectedCurrency
                        )
                        navController.popBackStack()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = selectedCategoryId != null && 
                              amountValue.isNotBlank() && 
                              selectedUserIds.isNotEmpty() &&
                              (!isCustomSplit || Math.abs(remaining) < 0.01)
                ) {
                    Text(stringResource(R.string.save_expense))
                }
            } else {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.select_group_to_split))
                }
            }
        }
    }
}

@Composable
private fun CategoryChip(
    category: Category,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val chipColor = try {
        Color(android.graphics.Color.parseColor(category.colorHex))
    } catch (e: Exception) {
        MaterialTheme.colorScheme.primary
    }

    val iconEmoji = when (category.iconName) {
        "restaurant" -> "🍽️"
        "directions_car" -> "🚗"
        "home" -> "🏠"
        "shopping_cart" -> "🛒"
        "flight" -> "✈️"
        "movie" -> "🎭"
        "local_hospital" -> "🏥"
        "school" -> "📚"
        "shopping_bag" -> "👕"
        "lightbulb" -> "💡"
        "more_horiz" -> "❓"
        else -> "📦"
    }

    FilterChip(
        selected = isSelected,
        onClick = onClick,
        label = {
            val context = LocalContext.current
            Text(
                "$iconEmoji ${category.getLocalizedName(context)}",
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1
            )
        },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = chipColor.copy(alpha = 0.25f),
            selectedLabelColor = MaterialTheme.colorScheme.onSurface
        ),
        border = if (isSelected) {
            FilterChipDefaults.filterChipBorder(
                enabled = true,
                selected = true,
                borderColor = chipColor,
                selectedBorderColor = chipColor,
                borderWidth = 2.dp,
                selectedBorderWidth = 2.dp
            )
        } else {
            FilterChipDefaults.filterChipBorder(
                enabled = true,
                selected = false
            )
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryManagementDialog(
    categories: List<Category>,
    onDismiss: () -> Unit,
    onSaveCategory: (name: String, icon: String, color: String) -> Unit,
    onDeleteCategory: (id: String) -> Unit
) {
    var showAddForm by remember { mutableStateOf(false) }
    var newCategoryName by remember { mutableStateOf("") }
    // Default color options
    val colors = listOf("#FF6B35", "#4ECDC4", "#45B7D1", "#96CEB4", "#DDA0DD", "#FFD93D", "#FF6B6B", "#A8D8EA", "#F38181", "#6C5CE7", "#95A5A6")
    var selectedColor by remember { mutableStateOf(colors.first()) }
    
    val icons = listOf("restaurant", "directions_car", "home", "shopping_cart", "flight", "movie", "local_hospital", "school", "shopping_bag", "lightbulb", "more_horiz")
    var selectedIcon by remember { mutableStateOf(icons.first()) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = MaterialTheme.shapes.extraLarge
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
            ) {
                Text(
                    text = stringResource(R.string.manage_categories),
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                if (showAddForm) {
                    OutlinedTextField(
                        value = newCategoryName,
                        onValueChange = { newCategoryName = it },
                        label = { Text(stringResource(R.string.category_name_label)) },
                        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
                    )

                    Text(stringResource(R.string.select_icon), style = MaterialTheme.typography.labelMedium)
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(5),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.height(120.dp).padding(vertical = 8.dp)
                    ) {
                        items(icons) { iconName ->
                            val iconEmoji = when (iconName) {
                                "restaurant" -> "🍽️"
                                "directions_car" -> "🚗"
                                "home" -> "🏠"
                                "shopping_cart" -> "🛒"
                                "flight" -> "✈️"
                                "movie" -> "🎭"
                                "local_hospital" -> "🏥"
                                "school" -> "📚"
                                "shopping_bag" -> "👕"
                                "lightbulb" -> "💡"
                                "more_horiz" -> "❓"
                                else -> "📦"
                            }
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .padding(4.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                FilterChip(
                                    selected = selectedIcon == iconName,
                                    onClick = { selectedIcon = iconName },
                                    label = { Text(iconEmoji) }
                                )
                            }
                        }
                    }

                    Text(stringResource(R.string.select_color), style = MaterialTheme.typography.labelMedium)
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(5),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.height(120.dp).padding(vertical = 8.dp)
                    ) {
                        items(colors) { hex ->
                            val color = try { Color(android.graphics.Color.parseColor(hex)) } catch (e: Exception) { Color.Gray }
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .padding(4.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                FilterChip(
                                    selected = selectedColor == hex,
                                    onClick = { selectedColor = hex },
                                    label = { Text("") },
                                    colors = FilterChipDefaults.filterChipColors(
                                        containerColor = color,
                                        selectedContainerColor = color
                                    )
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = { showAddForm = false }) {
                            Text(stringResource(R.string.cancel))
                        }
                        Button(
                            onClick = {
                                if (newCategoryName.isNotBlank()) {
                                    onSaveCategory(newCategoryName, selectedIcon, selectedColor)
                                    showAddForm = false
                                    newCategoryName = ""
                                }
                            },
                            enabled = newCategoryName.isNotBlank()
                        ) {
                            Text(stringResource(R.string.save))
                        }
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(1),
                        modifier = Modifier.heightIn(max = 300.dp).padding(bottom = 16.dp)
                    ) {
                        items(categories) { category ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                CategoryChip(category = category, isSelected = false, onClick = {})
                                TextButton(
                                    onClick = { onDeleteCategory(category.id) },
                                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                                ) {
                                    Text(stringResource(R.string.delete))
                                }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        TextButton(onClick = onDismiss) {
                            Text(stringResource(R.string.close))
                        }
                        Button(onClick = { showAddForm = true }) {
                            Text(stringResource(R.string.add_new))
                        }
                    }
                }
            }
        }
    }
}
