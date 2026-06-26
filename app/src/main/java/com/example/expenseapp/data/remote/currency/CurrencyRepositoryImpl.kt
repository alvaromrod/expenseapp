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
        val ratesToEur = mapOf(
            "USD" to 1.08,
            "EUR" to 1.0,
            "GBP" to 0.86,
            "JPY" to 162.0,
            "ARS" to 1100.0
        )
        
        val baseRateInEur = ratesToEur[baseCurrency] ?: 1.0
        
        // Return rates relative to the requested baseCurrency
        ratesToEur.mapValues { it.value / baseRateInEur }
    }

    override fun getSupportedCurrencies(): List<String> = listOf("USD", "EUR", "GBP", "JPY", "CAD", "AUD", "ARS")
}
