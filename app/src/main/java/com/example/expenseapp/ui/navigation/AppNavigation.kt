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
import com.example.expenseapp.ui.screens.auth.AuthViewModel
import com.example.expenseapp.ui.screens.auth.LoginScreen
import com.example.expenseapp.ui.screens.expense.AddExpenseScreen
import com.example.expenseapp.ui.screens.group.GroupScreen
import com.example.expenseapp.ui.screens.home.HomeScreen
import com.example.expenseapp.ui.screens.profile.ProfileScreen
import com.example.expenseapp.ui.screens.stats.StatsScreen

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    // Use AuthViewModel to access the session state cleanly via Hilt
    val authViewModel: AuthViewModel = hiltViewModel()
    val currentUserId by authViewModel.currentUserId.collectAsState(initial = null)

    NavHost(
        navController = navController,
        startDestination = if (currentUserId != null) Screen.Home.route else Screen.Auth.route
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
    }
}
