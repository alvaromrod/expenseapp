package com.example.expenseapp.ui.screens.group

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.example.expenseapp.domain.model.Group
import com.example.expenseapp.ui.navigation.Screen
import kotlinx.coroutines.flow.collectLatest
import androidx.compose.ui.res.stringResource
import com.example.expenseapp.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JoinGroupScreen(
    navController: NavController,
    viewModel: JoinGroupViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.events.collectLatest { event ->
            when (event) {
                is JoinGroupEvent.NavigateToHome -> {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.JoinGroup.route) { inclusive = true }
                    }
                }
                is JoinGroupEvent.ShowError -> {
                    snackbarHostState.showSnackbar(event.message)
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.join_group)) }
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            when {
                uiState.isLoading -> {
                    CircularProgressIndicator()
                }
                uiState.error != null -> {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = uiState.error ?: stringResource(R.string.unknown_error),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = { navController.popBackStack() }) {
                            Text(stringResource(R.string.go_back))
                        }
                    }
                }
                uiState.group != null -> {
                    GroupInviteContent(
                        groupName = uiState.group?.name ?: "Unknown Group",
                        isJoining = uiState.isJoining,
                        onCancel = { navController.popBackStack() },
                        onJoin = { viewModel.joinGroup() }
                    )
                }
                !uiState.isLoading && uiState.group == null -> {
                    // Blind Join case: Metadata not visible (RLS), but we have the ID
                    GroupInviteContent(
                        groupName = stringResource(R.string.a_new_group),
                        isJoining = uiState.isJoining,
                        onCancel = { navController.popBackStack() },
                        onJoin = { viewModel.joinGroup() }
                    )
                }
            }
        }
    }
}

@Composable
private fun GroupInviteContent(
    groupName: String,
    isJoining: Boolean,
    onCancel: () -> Unit,
    onJoin: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Default.Groups,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.invited_to_join),
            style = MaterialTheme.typography.bodyLarge
        )
        Text(
            text = groupName,
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(vertical = 8.dp)
        )
        Spacer(modifier = Modifier.height(32.dp))

        if (isJoining) {
            CircularProgressIndicator()
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                OutlinedButton(
                    onClick = onCancel,
                    modifier = Modifier.weight(1.0f)
                ) {
                    Text(stringResource(R.string.cancel))
                }
                Button(
                    onClick = onJoin,
                    modifier = Modifier.weight(1.0f)
                ) {
                    Text(stringResource(R.string.join_now))
                }
            }
        }
    }
}
