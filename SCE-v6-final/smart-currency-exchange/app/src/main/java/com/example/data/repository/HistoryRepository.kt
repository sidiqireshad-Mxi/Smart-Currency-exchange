package com.example.data.repository

import com.example.data.db.HistoryDao
import com.example.data.model.ConversionHistory
import kotlinx.coroutines.flow.Flow

class HistoryRepository(private val historyDao: HistoryDao) {
    val allHistory: Flow<List<ConversionHistory>> = historyDao.getAllHistory()

    suspend fun insert(history: ConversionHistory): Long {
        return historyDao.insertHistory(history)
    }

    suspend fun deleteById(id: Long) {
        historyDao.deleteHistoryById(id)
    }

    suspend fun clearHistoryByMode(isOnline: Boolean) {
        historyDao.clearHistoryByMode(isOnline)
    }

    suspend fun clearAll() {
        historyDao.clearAllHistory()
    }
}
