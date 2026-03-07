package com.example.expenseapp.ui.util

import android.content.Context
import com.example.expenseapp.R
import com.example.expenseapp.domain.model.Category

/**
 * Returns a localized name for the category if it's one of the default ones.
 * Otherwise returns the custom name provided by the user.
 */
fun Category.getLocalizedName(context: Context): String {
    val resId = when (id) {
        "eating_out" -> R.string.cat_eating_out
        "transport" -> R.string.cat_transport
        "rent_housing" -> R.string.cat_rent_housing
        "supermarket" -> R.string.cat_supermarket
        "traveling" -> R.string.cat_traveling
        "entertainment" -> R.string.cat_entertainment
        "health" -> R.string.cat_health
        "education" -> R.string.cat_education
        "shopping" -> R.string.cat_shopping
        "utilities" -> R.string.cat_utilities
        "other" -> R.string.cat_other
        else -> null
    }
    
    return if (resId != null) {
        context.getString(resId)
    } else {
        name
    }
}
