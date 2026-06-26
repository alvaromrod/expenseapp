package com.example.expenseapp.ui.screens.group

import androidx.compose.ui.draw.clip
import coil.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.example.expenseapp.ui.navigation.Screen
import com.example.expenseapp.ui.util.getCurrencySymbol
import androidx.compose.ui.res.stringResource
import com.example.expenseapp.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupBalancesScreen(
    navController: NavController,
    viewModel: GroupBalancesViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(uiState.group?.name?.let { stringResource(R.string.group_balances_named, it) } ?: stringResource(R.string.group_balances_default)) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { navController.navigate(Screen.SettleUp.createRoute(uiState.group?.id ?: "")) },
                icon = { Icon(Icons.Default.Check, contentDescription = null) },
                text = { Text(stringResource(R.string.settle_up)) }
            )
        }
    ) { padding ->
        Crossfade(targetState = uiState.isLoading, label = "balances_loading") { isLoading ->
            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        Text(
                            text = stringResource(R.string.net_balances),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }
                    
                    items(
                        items = uiState.userBalances,
                        key = { it.user.id }
                    ) { userBalance ->
                        BalanceItem(userBalance, uiState.group?.mainCurrency ?: "USD")
                    }

                    if (uiState.userBalances.size > 2) {
                        item {
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = stringResource(R.string.suggested_transfers),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                        }
                        
                        if (uiState.suggestedTransfers.isEmpty()) {
                            item {
                                Text(
                                    text = stringResource(R.string.all_settled_up),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        } else {
                            items(
                                items = uiState.suggestedTransfers,
                                key = { "${it.fromUser.id}-${it.toUser.id}" }
                            ) { transfer ->
                                TransferItem(
                                    transfer = transfer,
                                    currencyCode = uiState.group?.mainCurrency ?: "USD",
                                    onSettle = { viewModel.settleTransfer(transfer) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TransferItem(
    transfer: DebtTransfer,
    currencyCode: String,
    onSettle: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        )
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            if (transfer.fromUser.avatarUrl != null) {
                AsyncImage(
                    model = transfer.fromUser.avatarUrl,
                    contentDescription = "Avatar",
                    modifier = Modifier.size(32.dp).clip(androidx.compose.foundation.shape.CircleShape).padding(end = 8.dp),
                    contentScale = ContentScale.Crop
                )
            } else {
                Icon(
                    Icons.Default.AccountCircle,
                    contentDescription = null,
                    modifier = Modifier.size(32.dp).padding(end = 8.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.transfer_summary, transfer.fromUser.name, transfer.toUser.name),
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = "${getCurrencySymbol(currencyCode)}${"%.2f".format(transfer.amount)}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            
            Button(
                onClick = onSettle,
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(stringResource(R.string.settle))
            }
        }
    }
}

@Composable
fun BalanceItem(userBalance: UserBalance, currencyCode: String) {
    val isPositive = userBalance.balance >= 0
    val valueColor by animateColorAsState(
        targetValue = if (isPositive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
        animationSpec = tween(durationMillis = 500),
        label = "balanceValueColor"
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            if (userBalance.user.avatarUrl != null) {
                AsyncImage(
                    model = userBalance.user.avatarUrl,
                    contentDescription = "Avatar",
                    modifier = Modifier.size(40.dp).clip(androidx.compose.foundation.shape.CircleShape).padding(end = 12.dp),
                    contentScale = ContentScale.Crop
                )
            } else {
                Icon(
                    Icons.Default.AccountCircle,
                    contentDescription = null,
                    modifier = Modifier.size(40.dp).padding(end = 12.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = userBalance.user.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold
                )
                val statusText = when {
                    userBalance.balance > 0.01 -> stringResource(R.string.is_owed)
                    userBalance.balance < -0.01 -> stringResource(R.string.owes)
                    else -> stringResource(R.string.is_settled_up)
                }
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            Text(
                text = "${getCurrencySymbol(currencyCode)}${"%.2f".format(Math.abs(userBalance.balance))}",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                color = valueColor
            )
        }
    }
}
