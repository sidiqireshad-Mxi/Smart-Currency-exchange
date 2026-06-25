package com.example.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.model.ConversionHistory
import com.example.data.model.Currency
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(entities = [Currency::class, ConversionHistory::class], version = 7, exportSchema = false)
abstract class CurrencyDatabase : RoomDatabase() {
    abstract fun currencyDao(): CurrencyDao
    abstract fun historyDao(): HistoryDao

    companion object {
        @Volatile
        private var INSTANCE: CurrencyDatabase? = null

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Since the Kotlin schemas for Currency and ConversionHistory have not changed,
                // we don't need any SQL executions. This migration acts to safely upgrade the
                // database version on current installations, fully preserving all stored user 
                // records and transactions.
            }
        }

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE INDEX IF NOT EXISTS index_currencies_isFavorite ON currencies(isFavorite)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_currencies_name ON currencies(name)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_conversion_history_timestamp ON conversion_history(timestamp)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_conversion_history_isOnline ON conversion_history(isOnline)")
            }
        }

        fun getDatabase(context: Context, scope: CoroutineScope): CurrencyDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    CurrencyDatabase::class.java,
                    "smart_currency_database"
                )
                    .addMigrations(MIGRATION_5_6, MIGRATION_6_7)
                    .fallbackToDestructiveMigrationOnDowngrade()
                    .addCallback(CurrencyDatabaseCallback(scope))
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }

    private class CurrencyDatabaseCallback(
        private val scope: CoroutineScope
    ) : RoomDatabase.Callback() {
        override fun onCreate(db: SupportSQLiteDatabase) {
            super.onCreate(db)
            INSTANCE?.let { database ->
                scope.launch(Dispatchers.IO) {
                    val dao = database.currencyDao()
                    // Populate initial standard currencies to avoid an empty app experience
                    dao.insertCurrency(Currency("USD", "US Dollar", "$", 1.0, "US Dollar (Base Reference)", isFavorite = true))
                    dao.insertCurrency(Currency("TOMAN", "Iranian Toman", "T", 60000.0, "Iranian Toman", isFavorite = false))
                    dao.insertCurrency(Currency("RIAL", "Iranian Rial", "﷼", 600000.0, "Iranian Rial", isFavorite = false))
                    dao.insertCurrency(Currency("EUR", "Euro", "€", 0.92, "Euro Zone", isFavorite = false))
                    dao.insertCurrency(Currency("GBP", "Pound Sterling", "£", 0.77, "Great British Pound", isFavorite = false))
                    dao.insertCurrency(Currency("AFN", "Afghan Afghani", "؋", 71.0, "Afghan Afghani", isFavorite = false))
                    dao.insertCurrency(Currency("BTC", "Bitcoin", "₿", 0.000015, "Bitcoin Digital Gold", isFavorite = true))
                    dao.insertCurrency(Currency("TON", "Toncoin", "TON", 0.14, "The Open Network", isFavorite = false))
                    
                    // Popular Cryptocurrencies
                    dao.insertCurrency(Currency("ETH", "Ethereum", "Ξ", 0.00032, "Ethereum Network", isFavorite = false))
                    dao.insertCurrency(Currency("SOL", "Solana", "SOL", 0.0075, "Solana Ecosystem", isFavorite = false))
                    dao.insertCurrency(Currency("BNB", "Binance Coin", "BNB", 0.0018, "BNB Smart Chain", isFavorite = false))
                    dao.insertCurrency(Currency("ADA", "Cardano", "ADA", 2.5, "Cardano Blockchain", isFavorite = false))
                    dao.insertCurrency(Currency("DOGE", "Dogecoin", "Ð", 8.5, "Dogecoin Meme Asset", isFavorite = false))
                    dao.insertCurrency(Currency("XRP", "Ripple", "XRP", 2.1, "Ripple Settlement Asset", isFavorite = false))
                    dao.insertCurrency(Currency("USDT", "Tether USD", "USDT", 1.0, "Tether USD Stablecoin", isFavorite = false))
                    dao.insertCurrency(Currency("USDC", "USD Coin", "USDC", 1.0, "USD Coin Stablecoin", isFavorite = false))
                    dao.insertCurrency(Currency("LTC", "Litecoin", "Ł", 0.014, "Litecoin Digital Silver", isFavorite = false))
                    dao.insertCurrency(Currency("LINK", "Chainlink", "LINK", 0.07, "Chainlink Oracle Token", isFavorite = false))
                    dao.insertCurrency(Currency("DOT", "Polkadot", "DOT", 0.18, "Polkadot Interoperability Network", isFavorite = false))
                    dao.insertCurrency(Currency("POL", "Polygon", "POL", 1.8, "Polygon Multi-chain Network", isFavorite = false))
                    dao.insertCurrency(Currency("SHIB", "Shiba Inu", "SHIB", 55000.0, "Shiba Inu Meme Token", isFavorite = false))
                    dao.insertCurrency(Currency("AVAX", "Avalanche", "AVAX", 0.035, "Avalanche Platform Token", isFavorite = false))

                    // Popular Fiat Currencies
                    dao.insertCurrency(Currency("JPY", "Japanese Yen", "¥", 158.0, "Japanese Yen", isFavorite = false))
                    dao.insertCurrency(Currency("CAD", "Canadian Dollar", "C$", 1.37, "Canadian Dollar", isFavorite = false))
                    dao.insertCurrency(Currency("CHF", "Swiss Franc", "CHF", 0.89, "Swiss Franc", isFavorite = false))
                    dao.insertCurrency(Currency("CNY", "Chinese Yuan", "¥", 7.25, "Chinese Yuan Renminbi", isFavorite = false))
                    dao.insertCurrency(Currency("TRY", "Turkish Lira", "₺", 32.8, "Turkish Lira", isFavorite = false))
                    dao.insertCurrency(Currency("AED", "Emirati Dirham", "AED", 3.67, "United Arab Emirates Dirham", isFavorite = false))
                    dao.insertCurrency(Currency("AUD", "Australian Dollar", "A$", 1.50, "Australian Dollar", isFavorite = false))
                    
                    // Middle Eastern and Central Asian (Tajik Somoni) Fiat Currencies
                    dao.insertCurrency(Currency("TJS", "Tajikistani Somoni", "смн", 10.7, "Tajikistani Somoni (TJS)", isFavorite = false))
                    dao.insertCurrency(Currency("SAR", "Saudi Riyal", "ر.س", 3.75, "Saudi Arabian Riyal (SAR)", isFavorite = false))
                    dao.insertCurrency(Currency("KWD", "Kuwaiti Dinar", "د.ك", 0.31, "Kuwaiti Dinar (KWD)", isFavorite = false))
                    dao.insertCurrency(Currency("QAR", "Qatari Riyal", "ر.ق", 3.64, "Qatari Riyal (QAR)", isFavorite = false))
                    dao.insertCurrency(Currency("OMR", "Omani Rial", "ر.ع", 0.38, "Omani Rial (OMR)", isFavorite = false))
                    dao.insertCurrency(Currency("BHD", "Bahraini Dinar", "د.ب", 0.38, "Bahraini Dinar (BHD)", isFavorite = false))
                    dao.insertCurrency(Currency("IQD", "Iraqi Dinar", "د.ع", 1310.0, "Iraqi Dinar (IQD)", isFavorite = false))
                    dao.insertCurrency(Currency("SYP", "Syrian Pound", "ل.س", 13000.0, "Syrian Pound (SYP)", isFavorite = false))
                    dao.insertCurrency(Currency("LBP", "Lebanese Pound", "ل.ل", 89500.0, "Lebanese Pound (LBP)", isFavorite = false))
                    dao.insertCurrency(Currency("JOD", "Jordanian Dinar", "د.ا", 0.71, "Jordanian Dinar (JOD)", isFavorite = false))
                    dao.insertCurrency(Currency("EGP", "Egyptian Pound", "ج.م", 48.0, "Egyptian Pound (EGP)", isFavorite = false))
                    dao.insertCurrency(Currency("YER", "Yemeni Rial", "ر.ي", 250.0, "Yemeni Rial (YER)", isFavorite = false))
                    dao.insertCurrency(Currency("KZT", "Kazakhstani Tenge", "₸", 460.0, "Kazakhstani Tenge (KZT)", isFavorite = false))
                    dao.insertCurrency(Currency("UZS", "Uzbekistani Som", "сум", 12600.0, "Uzbekistani Som (UZS)", isFavorite = false))
                    dao.insertCurrency(Currency("AZN", "Azerbaijani Manat", "₼", 1.7, "Azerbaijani Manat (AZN)", isFavorite = false))
                    dao.insertCurrency(Currency("AMD", "Armenian Dram", "դր.", 388.0, "Armenian Dram (AMD)", isFavorite = false))
                    dao.insertCurrency(Currency("GEL", "Georgian Lari", "₾", 2.8, "Georgian Lari (GEL)", isFavorite = false))
                    dao.insertCurrency(Currency("ILS", "Israeli Shekel", "₪", 3.7, "Israeli Shekel (ILS)", isFavorite = false))
                    dao.insertCurrency(Currency("DZD", "Algerian Dinar", "د.ج", 134.0, "Algerian Dinar (DZD)", isFavorite = false))
                    dao.insertCurrency(Currency("MAD", "Moroccan Dirham", "د.م.", 10.0, "Moroccan Dirham (MAD)", isFavorite = false))
                    dao.insertCurrency(Currency("LYD", "Libyan Dinar", "ل.د", 4.85, "Libyan Dinar (LYD)", isFavorite = false))
                    dao.insertCurrency(Currency("TND", "Tunisian Dinar", "د.ت", 3.12, "Tunisian Dinar (TND)", isFavorite = false))
                    dao.insertCurrency(Currency("SDG", "Sudanese Pound", "ج.س", 601.0, "Sudanese Pound (SDG)", isFavorite = false))

                    // Wider Global Currencies
                    dao.insertCurrency(Currency("INR", "Indian Rupee", "₹", 83.5, "Indian Rupee (INR)", isFavorite = false))
                    dao.insertCurrency(Currency("RUB", "Russian Ruble", "₽", 88.0, "Russian Ruble (RUB)", isFavorite = false))
                    dao.insertCurrency(Currency("PKR", "Pakistani Rupee", "₨", 278.0, "Pakistani Rupee (PKR)", isFavorite = false))
                    dao.insertCurrency(Currency("BRL", "Brazilian Real", "R$", 5.4, "Brazilian Real (BRL)", isFavorite = false))
                    dao.insertCurrency(Currency("ZAR", "South African Rand", "R", 18.0, "South African Rand (ZAR)", isFavorite = false))
                    dao.insertCurrency(Currency("SGD", "Singapore Dollar", "S$", 1.35, "Singapore Dollar (SGD)", isFavorite = false))
                    dao.insertCurrency(Currency("NZD", "New Zealand Dollar", "NZ$", 1.63, "New Zealand Dollar (NZD)", isFavorite = false))
                    dao.insertCurrency(Currency("KRW", "South Korean Won", "₩", 1380.0, "South Korean Won (KRW)", isFavorite = false))
                    dao.insertCurrency(Currency("MXN", "Mexican Peso", "Mex$", 18.2, "Mexican Peso (MXN)", isFavorite = false))

                    // Commodities & Precious Metals
                    dao.insertCurrency(Currency("GOLD", "Gold Spot (Ounce)", "⚜️", 0.000435, "Gold price per Troy Ounce", isFavorite = true))
                    dao.insertCurrency(Currency("GOLD_24K", "Gold Spot 24K (Gram)", "⚜️", 0.01353, "Gold price 24-karat per Gram", isFavorite = false))
                    dao.insertCurrency(Currency("GOLD_18K", "Gold Spot 18K (Gram)", "⚜️", 0.01805, "Gold price 18-karat per Gram", isFavorite = false))
                    dao.insertCurrency(Currency("SILVER", "Silver Spot (Ounce)", "🥈", 0.0333, "Silver price per Troy Ounce", isFavorite = false))
                    dao.insertCurrency(Currency("DIAMOND", "Diamond (Carat)", "💎", 0.00025, "Estimated Diamond price per Carat", isFavorite = false))
                    dao.insertCurrency(Currency("PLATINUM", "Platinum (Ounce)", "💍", 0.001, "Platinum price per Troy Ounce", isFavorite = false))
                }
            }
        }
    }
}
