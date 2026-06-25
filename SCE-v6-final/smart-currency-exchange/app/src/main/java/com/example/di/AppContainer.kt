package com.example.di

import android.content.Context
import com.example.data.db.CurrencyDatabase
import com.example.data.repository.CurrencyRepository
import com.example.data.repository.HistoryRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

interface AppContainer {
    val currencyRepository: CurrencyRepository
    val historyRepository: HistoryRepository
}

class AppContainerImpl(private val context: Context) : AppContainer {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val database: CurrencyDatabase by lazy {
        CurrencyDatabase.getDatabase(context, applicationScope)
    }

    override val currencyRepository: CurrencyRepository by lazy {
        CurrencyRepository(database.currencyDao())
    }

    override val historyRepository: HistoryRepository by lazy {
        HistoryRepository(database.historyDao())
    }
}
