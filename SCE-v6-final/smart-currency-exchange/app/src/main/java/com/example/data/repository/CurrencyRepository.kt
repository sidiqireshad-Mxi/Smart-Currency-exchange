package com.example.data.repository

import com.example.data.db.CurrencyDao
import com.example.data.model.Currency
import kotlinx.coroutines.flow.Flow

class CurrencyRepository(private val currencyDao: CurrencyDao) {
    val allCurrencies: Flow<List<Currency>> = currencyDao.getAllCurrencies()

    fun searchCurrencies(query: String): Flow<List<Currency>> {
        return currencyDao.searchCurrencies(query)
    }

    suspend fun insert(currency: Currency): Long {
        return currencyDao.insertCurrency(currency.copy(code = currency.code.uppercase().trim()))
    }

    suspend fun update(currency: Currency) {
        currencyDao.updateCurrency(currency.copy(code = currency.code.uppercase().trim()))
    }

    suspend fun delete(currency: Currency) {
        currencyDao.deleteCurrency(currency)
    }

    suspend fun toggleFavorite(code: String) {
        val currency = currencyDao.getCurrencyByCode(code)
        if (currency != null) {
            currencyDao.updateFavorite(code, !currency.isFavorite)
        }
    }

    suspend fun getByCode(code: String): Currency? {
        return currencyDao.getCurrencyByCode(code.uppercase().trim())
    }
}
