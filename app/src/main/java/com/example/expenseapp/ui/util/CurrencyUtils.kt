package com.example.expenseapp.ui.util

/**
 * Returns the symbol for a given currency code.
 */
fun getCurrencySymbol(code: String?): String {
    return when (code?.uppercase()) {
        "EUR" -> "€"
        "USD" -> "$"
        "GBP" -> "£"
        "JPY" -> "¥"
        "CHF" -> "Fr"
        "CAD" -> "C$"
        "AUD" -> "A$"
        "CNY" -> "¥"
        "KRW" -> "₩"
        "INR" -> "₹"
        else -> code ?: ""
    }
}
