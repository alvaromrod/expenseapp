package com.example.expenseapp.domain.repository.currency

import kotlinx.coroutines.flow.Flow

interface CurrencyRepository {
    suspend fun getExchangeRates(baseCurrency: String): Map<String, Double>
    fun getSupportedCurrencies(): List<String>
}
