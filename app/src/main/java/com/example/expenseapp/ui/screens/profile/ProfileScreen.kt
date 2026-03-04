package com.example.expenseapp.ui.screens.profile
import android.util.Log

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.Indication
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.example.expenseapp.ui.navigation.Screen
import kotlinx.coroutines.flow.collectLatest

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    navController: NavController,
    viewModel: ProfileViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var name by remember { mutableStateOf("") }
    
    // Stabilize name initialization
    LaunchedEffect(uiState.user) {
        if (uiState.user != null && name.isBlank()) {
            name = uiState.user?.name ?: ""
            Log.d("ProfileScreen", "Name state initialized: $name")
        }
    }
    
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            context.contentResolver.openInputStream(it)?.use { stream ->
                viewModel.updateAvatar(stream.readBytes())
            }
        }
    }

    LaunchedEffect(Unit) {
        Log.d("ProfileScreen", "Screen launched")
    }

    LaunchedEffect(viewModel.events) {
        viewModel.events.collectLatest { event ->
            when (event) {
                is ProfileEvent.NavigateToAuth -> {
                    navController.navigate(Screen.Auth.route) {
                        popUpTo(0) // Clear back stack
                    }
                }
                is ProfileEvent.ShowError -> {
                    // Optional: Show snackbar or toast
                    Log.e("ProfileScreen", "Error: ${event.message}")
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Profile") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        // Stable root Box that always consumes padding and stays in tree
        Box(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            // Main content is ALWAYS in the tree to prevent LayoutNode measurement issues
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxSize()
                    .graphicsLayer {
                        // Optional: Fade out content if loading
                        alpha = if (uiState.isLoading) 0.5f else 1.0f
                    }
            ) {
                Log.d("ProfileScreen", "Rendering content. User: ${uiState.user?.email}")
                
                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .clip(CircleShape)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = LocalIndication.current,
                            enabled = !uiState.isLoading
                        ) { launcher.launch("image/*") },
                    contentAlignment = androidx.compose.ui.Alignment.Center
                ) {
                    if (uiState.user?.avatarUrl != null) {
                        AsyncImage(
                            model = uiState.user?.avatarUrl,
                            contentDescription = "Avatar",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Surface(
                            modifier = Modifier.fillMaxSize(),
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Icon(
                                Icons.Default.Person,
                                contentDescription = "Default Avatar",
                                modifier = Modifier.size(48.dp).padding(24.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Display Name") },
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                    enabled = !uiState.isSaving && !uiState.isLoading
                )

                Text(
                    text = "Email: ${uiState.user?.email ?: "N/A"}",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(bottom = 32.dp)
                )

                if (uiState.error != null) {
                    Text(
                        text = uiState.error!!,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                }

                Button(
                    onClick = { viewModel.updateProfile(name) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = name.isNotBlank() && !uiState.isSaving && !uiState.isLoading && name != uiState.user?.name
                ) {
                    if (uiState.isSaving) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary)
                    } else {
                        Text("Save Changes")
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))

                OutlinedButton(
                    onClick = { viewModel.signOut() },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !uiState.isLoading,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Sign Out")
                }
            }

            // Loading overlay instead of tree-swapping branch
            if (uiState.isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = androidx.compose.ui.Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
        }
    }
}
