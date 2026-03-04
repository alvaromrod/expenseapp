package com.example.expenseapp.ui.navigation

sealed class Screen(val route: String) {
    object Auth : Screen("auth")
    object Login : Screen("login")
    object Register : Screen("register")
    object Home : Screen("home")
    object ExpenseDetail : Screen("expense_detail/{expenseId}") {
        fun createRoute(expenseId: String) = "expense_detail/$expenseId"
    }
    object GroupStats : Screen("group_stats/{groupId}") {
        fun createRoute(groupId: String) = "group_stats/$groupId"
    }
    object UserProfile : Screen("user_profile")
    object Groups : Screen("groups")
}
