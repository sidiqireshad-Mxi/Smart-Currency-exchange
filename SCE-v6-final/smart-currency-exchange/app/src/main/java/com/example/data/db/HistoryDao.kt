package com.example.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.example.data.model.ConversionHistory
import kotlinx.coroutines.flow.Flow

@Dao
interface HistoryDao {
    @Query("SELECT * FROM conversion_history ORDER BY timestamp DESC")
    fun getAllHistory(): Flow<List<ConversionHistory>>

    @Insert
    suspend fun insertHistory(history: ConversionHistory): Long

    @Query("DELETE FROM conversion_history WHERE id = :id")
    suspend fun deleteHistoryById(id: Long)

    @Query("DELETE FROM conversion_history WHERE isOnline = :isOnline")
    suspend fun clearHistoryByMode(isOnline: Boolean)

    @Query("DELETE FROM conversion_history")
    suspend fun clearAllHistory()
}
