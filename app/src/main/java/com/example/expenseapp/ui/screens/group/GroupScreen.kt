package com.example.expenseapp.ui.screens.group

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController

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
                title = { Text("Groups") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
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
                Text("No groups yet. Create one!")
            }
        } else {
            LazyColumn(modifier = Modifier.padding(padding).fillMaxSize()) {
                items(groups) { group ->
                    var showMemberDialog by remember { mutableStateOf(false) }
                    val users by viewModel.users.collectAsState()
                    val members by viewModel.getMembersForGroup(group.id).collectAsState(emptyList())

                    ListItem(
                        headlineContent = { Text(group.name) },
                        supportingContent = { 
                            Text("Members: ${members.joinToString { it.name }}")
                        },
                        trailingContent = {
                            Row {
                                IconButton(onClick = { showMemberDialog = true }) {
                                    Icon(Icons.Default.Add, contentDescription = "Add Member")
                                }
                                IconButton(onClick = { viewModel.deleteGroup(group.id) }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete")
                                }
                            }
                        }
                    )
                    
                    if (showMemberDialog) {
                        AlertDialog(
                            onDismissRequest = { showMemberDialog = false },
                            title = { Text("Add Member to ${group.name}") },
                            text = {
                                LazyColumn {
                                    items(users.filter { user -> members.none { it.id == user.id } }) { user ->
                                        ListItem(
                                            headlineContent = { Text(user.name) },
                                            trailingContent = {
                                                Button(onClick = {
                                                    viewModel.addMemberToGroup(group.id, user.id)
                                                    showMemberDialog = false
                                                }) {
                                                    Text("Add")
                                                }
                                            }
                                        )
                                    }
                                }
                            },
                            confirmButton = {},
                            dismissButton = {
                                TextButton(onClick = { showMemberDialog = false }) {
                                    Text("Close")
                                }
                            }
                        )
                    }
                }
            }
        }

        if (showAddDialog) {
            AlertDialog(
                onDismissRequest = { showAddDialog = false },
                title = { Text("Create Group") },
                text = {
                    OutlinedTextField(
                        value = newGroupName,
                        onValueChange = { newGroupName = it },
                        label = { Text("Group Name") },
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (newGroupName.isNotBlank()) {
                                viewModel.createGroup(newGroupName)
                                newGroupName = ""
                                showAddDialog = false
                            }
                        }
                    ) {
                        Text("Create")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showAddDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}
