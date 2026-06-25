package com.example.data.db

import androidx.room.*
import com.example.data.model.Currency
import kotlinx.coroutines.flow.Flow

@Dao
interface CurrencyDao {
    @Query("SELECT * FROM currencies ORDER BY isFavorite DESC, name ASC")
    fun getAllCurrencies(): Flow<List<Currency>>

    @Query("SELECT * FROM currencies WHERE name LIKE '%' || :query || '%' OR code LIKE '%' || :query || '%' ORDER BY isFavorite DESC, name ASC")
    fun searchCurrencies(query: String): Flow<List<Currency>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCurrency(currency: Currency): Long

    @Update
    suspend fun updateCurrency(currency: Currency)

    @Delete
    suspend fun deleteCurrency(currency: Currency)

    @Query("SELECT * FROM currencies WHERE code = :code LIMIT 1")
    suspend fun getCurrencyByCode(code: String): Currency?

    @Query("UPDATE currencies SET isFavorite = :isFavorite WHERE code = :code")
    suspend fun updateFavorite(code: String, isFavorite: Boolean)
}
