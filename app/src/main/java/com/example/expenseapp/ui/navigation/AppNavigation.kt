package com.example.expenseapp.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.navDeepLink
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.example.expenseapp.ui.screens.auth.AuthViewModel
import com.example.expenseapp.ui.screens.auth.LoginScreen
import com.example.expenseapp.ui.screens.expense.AddExpenseScreen
import com.example.expenseapp.ui.screens.group.GroupScreen
import com.example.expenseapp.ui.screens.home.HomeScreen
import com.example.expenseapp.ui.screens.onboarding.OnboardingScreen
import com.example.expenseapp.ui.screens.profile.ProfileScreen
import com.example.expenseapp.ui.screens.stats.StatsScreen
import com.example.expenseapp.ui.screens.group.JoinGroupScreen
import com.example.expenseapp.ui.screens.group.QrScannerScreen
import com.example.expenseapp.ui.screens.group.EditGroupScreen
import com.example.expenseapp.ui.screens.group.GroupBalancesScreen
import com.example.expenseapp.ui.screens.group.SettleUpScreen

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    // Use AuthViewModel to access the session state cleanly via Hilt
    val authViewModel: AuthViewModel = hiltViewModel()
    val sessionStatus by authViewModel.currentUserId.collectAsState(initial = "unknown")

    if (sessionStatus == "unknown") {
        // Transient splash while reading stored session from local DataStore (~5ms)
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
        return
    }

    val startDestination = if (sessionStatus != null) Screen.Home.route else Screen.Auth.route

    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable(Screen.Auth.route) {
            LoginScreen(navController = navController)
        }
        composable(Screen.Login.route) {
            LoginScreen(navController = navController)
        }
        composable(Screen.Home.route) {
            HomeScreen(navController = navController)
        }
        composable(Screen.GroupStats.route) {
            StatsScreen(navController = navController)
        }
        composable(
            route = "add_expense?expenseId={expenseId}",
            arguments = listOf(navArgument("expenseId") {
                type = NavType.StringType
                nullable = true
                defaultValue = null
            })
        ) {
            AddExpenseScreen(navController = navController)
        }
        composable(Screen.UserProfile.route) {
            ProfileScreen(navController = navController)
        }
        composable(Screen.Groups.route) {
            GroupScreen(navController = navController)
        }
        composable(
            route = Screen.Onboarding.route,
            arguments = listOf(navArgument("userId") {
                type = NavType.StringType
                nullable = true
                defaultValue = null
            })
        ) {
            OnboardingScreen(navController = navController)
        }
        composable(
            route = Screen.JoinGroup.route,
            arguments = listOf(navArgument("groupId") { type = NavType.StringType }),
            deepLinks = listOf(navDeepLink { uriPattern = "expenseapp://join?groupId={groupId}" })
        ) {
            JoinGroupScreen(navController = navController)
        }
        composable(Screen.QrScanner.route) {
            QrScannerScreen(navController = navController)
        }
        composable(
            route = Screen.EditGroup.route,
            arguments = listOf(navArgument("groupId") { type = NavType.StringType })
        ) {
            EditGroupScreen(navController = navController)
        }
        composable(
            route = Screen.GroupBalances.route,
            arguments = listOf(navArgument("groupId") { type = NavType.StringType })
        ) {
            GroupBalancesScreen(navController = navController)
        }
        composable(
            route = Screen.SettleUp.route,
            arguments = listOf(navArgument("groupId") { type = NavType.StringType })
        ) {
            SettleUpScreen(navController = navController)
        }
    }
}
