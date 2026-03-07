package com.example.expenseapp.ui.screens.group

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.asImageBitmap
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.example.expenseapp.ui.navigation.Screen
import androidx.compose.ui.res.stringResource
import com.example.expenseapp.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupScreen(
    navController: NavController,
    viewModel: GroupViewModel = hiltViewModel()
) {
    val groups by viewModel.groups.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }
    var newGroupName by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.groups)) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    var showJoinByLinkDialog by remember { mutableStateOf(false) }
                    IconButton(onClick = { showJoinByLinkDialog = true }) {
                        Icon(Icons.Default.Link, contentDescription = "Join by Link")
                    }
                    IconButton(onClick = { navController.navigate(Screen.QrScanner.route) }) {
                        Icon(Icons.Default.QrCodeScanner, contentDescription = "Scan QR")
                    }

                    if (showJoinByLinkDialog) {
                        var linkText by remember { mutableStateOf("") }
                        AlertDialog(
                            onDismissRequest = { showJoinByLinkDialog = false },
                            title = { Text(stringResource(R.string.join_group)) },
                            text = {
                                Column {
                                    Text(stringResource(R.string.paste_group_link))
                                    Spacer(modifier = Modifier.height(8.dp))
                                    OutlinedTextField(
                                        value = linkText,
                                        onValueChange = { linkText = it },
                                        placeholder = { Text("expenseapp://join?groupId=...") },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            },
                            confirmButton = {
                                Button(
                                    onClick = {
                                        if (linkText.isNotBlank()) {
                                            viewModel.joinGroupByLink(linkText, navController)
                                            showJoinByLinkDialog = false
                                        }
                                    }
                                ) { Text(stringResource(R.string.join)) }
                            },
                            dismissButton = {
                                TextButton(onClick = { showJoinByLinkDialog = false }) {
                                    Text(stringResource(R.string.cancel))
                                }
                            }
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "Create Group")
            }
        }
    ) { padding ->
        if (groups.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.no_groups_yet))
            }
        } else {
            LazyColumn(modifier = Modifier.padding(padding).fillMaxSize()) {
                items(groups) { group ->
                    val members by viewModel.getMembersForGroup(group.id).collectAsState(emptyList())

                    var showShareDialog by remember { mutableStateOf(false) }
                    val shareLink = "expenseapp://join?groupId=${group.id}"

                    ListItem(
                        headlineContent = { Text(group.name) },
                        supportingContent = { 
                            Text("${stringResource(R.string.members)} ${members.joinToString { it.name.ifBlank { it.email } }}")
                        },
                        trailingContent = {
                            Row {
                                IconButton(onClick = { navController.navigate(Screen.EditGroup.createRoute(group.id)) }) {
                                    Icon(Icons.Default.Edit, contentDescription = "Edit Group")
                                }
                                IconButton(onClick = { showShareDialog = true }) {
                                    Icon(Icons.Default.Share, contentDescription = "Share Group")
                                }
                                IconButton(onClick = { viewModel.deleteGroup(group.id) }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete")
                                }
                            }
                        }
                    )
                    
                    if (showShareDialog) {
                        val qrCodeBitmap = remember(shareLink) {
                            com.example.expenseapp.core.util.QrCodeGenerator.generateQrCode(shareLink)
                        }

                        AlertDialog(
                            onDismissRequest = { showShareDialog = false },
                            title = { Text(stringResource(R.string.share_group, group.name)) },
                            text = {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    if (qrCodeBitmap != null) {
                                        androidx.compose.foundation.Image(
                                            bitmap = qrCodeBitmap.asImageBitmap(),
                                            contentDescription = "QR Code",
                                            modifier = Modifier.size(200.dp)
                                        )
                                        Spacer(modifier = Modifier.height(16.dp))
                                    }
                                    Text(stringResource(R.string.invite_others))
                                    Spacer(modifier = Modifier.height(8.dp))
                                    OutlinedTextField(
                                        value = shareLink,
                                        onValueChange = {},
                                        readOnly = true,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            },
                            confirmButton = {
                                val context = androidx.compose.ui.platform.LocalContext.current
                                Button(onClick = {
                                    val clipboardManager = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                    val clip = android.content.ClipData.newPlainText("Join Group Link", shareLink)
                                    clipboardManager.setPrimaryClip(clip)
                                    showShareDialog = false
                                }) {
                                    Text(stringResource(R.string.copy_link))
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = { showShareDialog = false }) {
                                    Text(stringResource(R.string.close))
                                }
                            }
                        )
                    }
                }
            }
        }

        if (showAddDialog) {
            var description by remember { mutableStateOf("") }
            var selectedCurrency by remember { mutableStateOf("EUR") }
            val currencies = listOf("EUR", "USD", "GBP", "JPY", "CAD", "AUD")
            var expanded by remember { mutableStateOf(false) }

            AlertDialog(
                onDismissRequest = { showAddDialog = false },
                title = { Text(stringResource(R.string.create_group)) },
                text = {
                    Column {
                        OutlinedTextField(
                            value = newGroupName,
                            onValueChange = { newGroupName = it },
                            label = { Text(stringResource(R.string.group_name)) },
                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                        )
                        OutlinedTextField(
                            value = description,
                            onValueChange = { description = it },
                            label = { Text(stringResource(R.string.description_optional_caps)) },
                            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
                        )
                        ExposedDropdownMenuBox(
                            expanded = expanded,
                            onExpandedChange = { expanded = !expanded },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedTextField(
                                value = selectedCurrency,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text(stringResource(R.string.main_currency)) },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                                modifier = Modifier.menuAnchor().fillMaxWidth()
                            )
                            ExposedDropdownMenu(
                                expanded = expanded,
                                onDismissRequest = { expanded = false }
                            ) {
                                currencies.forEach { currency ->
                                    DropdownMenuItem(
                                        text = { Text(currency) },
                                        onClick = {
                                            selectedCurrency = currency
                                            expanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (newGroupName.isNotBlank()) {
                                viewModel.createGroup(newGroupName, description.ifBlank { null }, selectedCurrency)
                                newGroupName = ""
                                showAddDialog = false
                            }
                        }
                    ) {
                        Text(stringResource(R.string.create))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showAddDialog = false }) {
                        Text(stringResource(R.string.cancel))
                    }
                }
            )
        }
    }
}
