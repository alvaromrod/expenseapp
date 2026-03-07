package com.example.expenseapp.ui.navigation

sealed class Screen(val route: String) {
    object Splash : Screen("splash")
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
    object Onboarding : Screen("onboarding?userId={userId}") {
        fun createRoute(userId: String?) = if (userId != null) "onboarding?userId=$userId" else "onboarding"
    }
    object JoinGroup : Screen("join_group/{groupId}") {
        fun createRoute(groupId: String) = "join_group/$groupId"
    }
    object QrScanner : Screen("qr_scanner")
    object EditGroup : Screen("edit_group/{groupId}") {
        fun createRoute(groupId: String) = "edit_group/$groupId"
    }
    object GroupBalances : Screen("group_balances/{groupId}") {
        fun createRoute(groupId: String) = "group_balances/$groupId"
    }
    object SettleUp : Screen("settle_up/{groupId}") {
        fun createRoute(groupId: String) = "settle_up/$groupId"
    }
}
