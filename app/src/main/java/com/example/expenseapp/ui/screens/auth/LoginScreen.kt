package com.example.expenseapp.ui.screens.auth

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.example.expenseapp.ui.navigation.Screen
import kotlinx.coroutines.flow.collectLatest
import androidx.compose.ui.res.stringResource
import com.example.expenseapp.R

@Composable
fun LoginScreen(
    navController: NavController,
    viewModel: AuthViewModel = hiltViewModel()
) {
    var email by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    var isSignUpMode by remember { mutableStateOf(false) }
    var passwordVisible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.events.collectLatest { event ->
            when (event) {
                is AuthEvent.NavigateToHome -> {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Auth.route) { inclusive = true }
                    }
                }
                is AuthEvent.NavigateToOnboarding -> {
                    navController.navigate(Screen.Onboarding.createRoute(event.userId)) {
                        popUpTo(Screen.Auth.route) { inclusive = true }
                    }
                }
                is AuthEvent.ShowError -> {
                    snackbarHostState.showSnackbar(event.message)
                }
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = stringResource(R.string.welcome_to_expenseapp),
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(bottom = 32.dp)
            )

            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text(stringResource(R.string.email)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                enabled = !uiState.isLoading,
                singleLine = true
            )

            if (isSignUpMode) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.whats_your_name)) },
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    enabled = !uiState.isLoading,
                    singleLine = true
                )
            }

            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text(stringResource(R.string.password)) },
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(
                            imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = if (passwordVisible) "Hide password" else "Show password"
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                enabled = !uiState.isLoading
            )

            if (uiState.isLoading) {
                CircularProgressIndicator(modifier = Modifier.padding(bottom = 16.dp))
            } else {
                Button(
                    onClick = { 
                        if (isSignUpMode) {
                            viewModel.signUpWithEmail(name, email, password)
                        } else {
                            viewModel.signInWithEmail(email, password)
                        }
                    },
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    enabled = email.isNotBlank() && password.isNotBlank() && (!isSignUpMode || name.isNotBlank())
                ) {
                    Text(if (isSignUpMode) stringResource(R.string.sign_up) else stringResource(R.string.login))
                }

                TextButton(
                    onClick = { isSignUpMode = !isSignUpMode },
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                ) {
                    Text(if (isSignUpMode) stringResource(R.string.already_have_account) else stringResource(R.string.dont_have_account))
                }
            }
        }
    }
}
