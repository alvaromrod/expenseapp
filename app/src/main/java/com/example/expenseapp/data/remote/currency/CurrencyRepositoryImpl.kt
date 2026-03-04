package com.example.expenseapp.data.remote.currency

import com.example.expenseapp.domain.repository.currency.CurrencyRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Implementation using the Frankfurter API (FOSS-friendly).
 * Docs: https://www.frankfurter.app/docs/
 */
class CurrencyRepositoryImpl @Inject constructor() : CurrencyRepository {

    override suspend fun getExchangeRates(baseCurrency: String): Map<String, Double> = withContext(Dispatchers.IO) {
        // In a real app, you'd use Retrofit or Ktor to fetch from:
        // https://api.frankfurter.app/latest?from=$baseCurrency
        
        // Mocking for development focus
        mapOf(
            "USD" to 1.0,
            "EUR" to 0.92,
            "GBP" to 0.79,
            "JPY" to 150.0
        )
    }

    override fun getSupportedCurrencies(): List<String> = listOf("USD", "EUR", "GBP", "JPY")
}
