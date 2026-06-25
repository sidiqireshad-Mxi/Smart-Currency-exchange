package com.example.ui.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.SmartCurrencyApplication
import com.example.data.model.ConversionHistory
import com.example.data.model.Currency
import com.example.data.model.CustomApi
import com.example.data.model.CustomApiManager
import com.example.data.model.resolveUsdRate
import com.example.data.model.ExchangeRate
import com.example.data.model.formatExchangeRates
import com.example.data.model.validateExchangeRates
import com.example.data.model.findConversionMultiplier
import com.example.data.model.calculateRepresentativeValue
import com.example.data.repository.CurrencyRepository
import com.example.data.repository.HistoryRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.async
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import java.text.SimpleDateFormat
import java.util.*

enum class SortOption(val title: String) {
    FAVORITE_FIRST("Favorite First"),
    HIGHEST_VALUE("Highest Value"),
    LOWEST_VALUE("Lowest Value"),
    ALPHABETICAL_AZ("Alphabetical (A-Z)"),
    ALPHABETICAL_ZA("Alphabetical (Z-A)")
}

@OptIn(kotlinx.coroutines.FlowPreview::class)
class CurrencyViewModel(
    application: Application,
    private val currencyRepository: CurrencyRepository,
    private val historyRepository: HistoryRepository
) : AndroidViewModel(application) {

    val sharedPrefs = application.getSharedPreferences("smart_currency_prefs", Context.MODE_PRIVATE)

    // Search and Sort State
    val searchQuery = MutableStateFlow("")
    val selectedSort = MutableStateFlow(SortOption.FAVORITE_FIRST)

    val favoritesOrder = MutableStateFlow<List<String>>(
        sharedPrefs.getString("favorites_order", "")?.split(",")?.filter { it.isNotBlank() } ?: emptyList()
    )

    fun saveFavoritesOrder(order: List<String>) {
        favoritesOrder.value = order
        sharedPrefs.edit().putString("favorites_order", order.joinToString(",")).apply()
    }

    val rawCurrenciesState: StateFlow<List<Currency>> = currencyRepository.allCurrencies.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        emptyList()
    )

    // Live Multi Currency Conversion States
    val amountInput = MutableStateFlow(sharedPrefs.getString("amount_input", "100") ?: "100")
    val baseCurrencyCode = MutableStateFlow(sharedPrefs.getString("base_currency", "USD") ?: "USD")

    // Settings State
    val themeMode = MutableStateFlow(sharedPrefs.getString("theme", "system") ?: "system")
    val displayCurrencyUnit = MutableStateFlow(sharedPrefs.getString("display_unit", "TOMAN") ?: "TOMAN")
    val languageCode = MutableStateFlow(sharedPrefs.getString("language_code", "en") ?: "en")
    val customAppName = MutableStateFlow(sharedPrefs.getString("custom_app_name", "S C E") ?: "S C E")
    val customAppIcon = MutableStateFlow(sharedPrefs.getString("custom_app_icon", "logo_modern") ?: "logo_modern")
    val customAppIconUri = MutableStateFlow(sharedPrefs.getString("custom_app_icon_uri", "") ?: "")
    val secretPassword = MutableStateFlow(sharedPrefs.getString("secret_password", "139909") ?: "139909")
    val isInPipMode = MutableStateFlow(false)

    // Online rate syncing states
    val isOnlineUpdating = MutableStateFlow(false)
    val onlineUpdateSuccessMessage = MutableStateFlow<String?>(null)

    // ── Persistent 60-second auto-refresh (survives tab/screen changes) ──────
    val onlineCountdownSeconds = MutableStateFlow(60)
    private var autoRefreshJob: kotlinx.coroutines.Job? = null

    fun startOnlineAutoRefresh() {
        if (autoRefreshJob?.isActive == true) return  // already running
        autoRefreshJob = viewModelScope.launch {
            triggerOnlineRatesUpdate()
            while (true) {
                kotlinx.coroutines.delay(1000L)
                val current = onlineCountdownSeconds.value
                if (current > 1) {
                    onlineCountdownSeconds.value = current - 1
                } else {
                    onlineCountdownSeconds.value = 60
                    triggerOnlineRatesUpdate()
                }
            }
        }
    }

    fun resetOnlineCountdown() {
        onlineCountdownSeconds.value = 60
    }
    val previousRates = MutableStateFlow<Map<String, Double>>(emptyMap())

    // Custom API States & Tab Swapping
    val customApisState = MutableStateFlow<List<CustomApi>>(emptyList())
    val selectedMarketTab = MutableStateFlow(sharedPrefs.getString("selected_market_tab", "GLOBAL") ?: "GLOBAL")

    fun selectMarketTab(tab: String) {
        selectedMarketTab.value = tab
        sharedPrefs.edit().putString("selected_market_tab", tab).apply()
    }

    fun addCustomApi(name: String, url: String, marketType: String, priority: String, apiCategory: String = "FIAT") {
        val newApi = CustomApi(
            id = java.util.UUID.randomUUID().toString(),
            name = name,
            url = url,
            isEnabled = true,
            priority = priority,
            marketType = marketType,
            apiCategory = apiCategory
        )
        val currentList = customApisState.value.toMutableList()
        currentList.add(newApi)
        customApisState.value = currentList
        CustomApiManager.saveCustomApis(sharedPrefs, currentList)
    }

    fun deleteCustomApi(id: String) {
        val currentList = customApisState.value.filter { it.id != id }
        customApisState.value = currentList
        CustomApiManager.saveCustomApis(sharedPrefs, currentList)
    }

    fun toggleCustomApi(id: String, isEnabled: Boolean) {
        val currentList = customApisState.value.map {
            if (it.id == id) it.copy(isEnabled = isEnabled) else it
        }
        customApisState.value = currentList
        CustomApiManager.saveCustomApis(sharedPrefs, currentList)
    }

    fun updateCustomApiPriority(id: String, priority: String) {
        val currentList = customApisState.value.map {
            if (it.id == id) it.copy(priority = priority) else it
        }
        customApisState.value = currentList
        CustomApiManager.saveCustomApis(sharedPrefs, currentList)
    }

    suspend fun testApiConnection(url: String): Boolean {
        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val client = okhttp3.OkHttpClient.Builder()
                    .connectTimeout(5, java.util.concurrent.TimeUnit.SECONDS)
                    .readTimeout(5, java.util.concurrent.TimeUnit.SECONDS)
                    .build()
                val request = okhttp3.Request.Builder().url(url).build()
                client.newCall(request).execute().use { response ->
                    response.isSuccessful
                }
            } catch (e: Exception) {
                false
            }
        }
    }

    // Support Developer Customizations states
    val supportSectionEnabled = MutableStateFlow(sharedPrefs.getBoolean("support_section_enabled", true))
    val supportWalletAddress = MutableStateFlow(sharedPrefs.getString("support_wallet_address", "0xeCF7fb316b0855Cc9c3d801B76458Ef6Ba25c084") ?: "0xeCF7fb316b0855Cc9c3d801B76458Ef6Ba25c084")
    val supportQrCodeUri = MutableStateFlow(sharedPrefs.getString("support_qr_code_uri", "") ?: "")
    val supportCustomTextTop = MutableStateFlow(sharedPrefs.getString("support_custom_text_top", "") ?: "")
    val supportCustomTextBottom = MutableStateFlow(sharedPrefs.getString("support_custom_text_bottom", "") ?: "")

    init {
        customApisState.value = CustomApiManager.loadCustomApis(sharedPrefs)
        // Asynchronously check and seed missing popular currencies to ensure the database has them on update
        viewModelScope.launch {
            val defaultSeeds = listOf(
                // ── Major Fiat Currencies ────────────────────────────────────
                Currency("USD", "US Dollar", "$", 1.0, "US Dollar (Base Reference)", isFavorite = true),
                Currency("EUR", "Euro", "€", 0.92, "Euro Zone", isFavorite = false),
                Currency("GBP", "Pound Sterling", "£", 0.77, "Great British Pound", isFavorite = false),
                Currency("JPY", "Japanese Yen", "¥", 158.0, "Japanese Yen", isFavorite = false),
                Currency("CAD", "Canadian Dollar", "C$", 1.37, "Canadian Dollar", isFavorite = false),
                Currency("AUD", "Australian Dollar", "A$", 1.50, "Australian Dollar", isFavorite = false),
                Currency("CHF", "Swiss Franc", "CHF", 0.89, "Swiss Franc", isFavorite = false),
                Currency("CNY", "Chinese Yuan", "¥", 7.25, "Chinese Yuan Renminbi", isFavorite = false),
                Currency("HKD", "Hong Kong Dollar", "HK$", 7.83, "Hong Kong Dollar", isFavorite = false),
                Currency("SGD", "Singapore Dollar", "S$", 1.35, "Singapore Dollar", isFavorite = false),
                Currency("NZD", "New Zealand Dollar", "NZ$", 1.63, "New Zealand Dollar", isFavorite = false),
                Currency("SEK", "Swedish Krona", "kr", 10.6, "Swedish Krona", isFavorite = false),
                Currency("NOK", "Norwegian Krone", "kr", 10.7, "Norwegian Krone", isFavorite = false),
                Currency("DKK", "Danish Krone", "kr", 6.9, "Danish Krone", isFavorite = false),
                Currency("MXN", "Mexican Peso", "Mex\$", 18.2, "Mexican Peso", isFavorite = false),
                Currency("BRL", "Brazilian Real", "R$", 5.4, "Brazilian Real", isFavorite = false),
                Currency("INR", "Indian Rupee", "₹", 83.5, "Indian Rupee", isFavorite = false),
                Currency("RUB", "Russian Ruble", "₽", 88.0, "Russian Ruble", isFavorite = false),
                Currency("KRW", "South Korean Won", "₩", 1380.0, "South Korean Won", isFavorite = false),
                Currency("TRY", "Turkish Lira", "₺", 32.8, "Turkish Lira", isFavorite = false),
                Currency("ZAR", "South African Rand", "R", 18.0, "South African Rand", isFavorite = false),
                Currency("PKR", "Pakistani Rupee", "₨", 278.0, "Pakistani Rupee", isFavorite = false),
                Currency("THB", "Thai Baht", "฿", 35.0, "Thai Baht", isFavorite = false),
                Currency("IDR", "Indonesian Rupiah", "Rp", 16400.0, "Indonesian Rupiah", isFavorite = false),
                Currency("MYR", "Malaysian Ringgit", "RM", 4.7, "Malaysian Ringgit", isFavorite = false),
                Currency("PHP", "Philippine Peso", "₱", 57.0, "Philippine Peso", isFavorite = false),
                Currency("PLN", "Polish Zloty", "zł", 3.95, "Polish Zloty", isFavorite = false),
                Currency("CZK", "Czech Koruna", "Kč", 23.5, "Czech Koruna", isFavorite = false),
                Currency("HUF", "Hungarian Forint", "Ft", 360.0, "Hungarian Forint", isFavorite = false),
                Currency("RON", "Romanian Leu", "lei", 4.6, "Romanian Leu", isFavorite = false),
                Currency("UAH", "Ukrainian Hryvnia", "₴", 41.0, "Ukrainian Hryvnia", isFavorite = false),
                // Iranian currencies
                Currency("TOMAN", "Iranian Toman", "T", 60000.0, "Iranian Toman", isFavorite = false),
                Currency("RIAL", "Iranian Rial", "﷼", 600000.0, "Iranian Rial", isFavorite = false),
                // Middle East & Central Asia
                Currency("AFN", "Afghan Afghani", "؋", 71.0, "Afghan Afghani", isFavorite = false),
                Currency("AED", "Emirati Dirham", "AED", 3.67, "United Arab Emirates Dirham", isFavorite = false),
                Currency("SAR", "Saudi Riyal", "ر.س", 3.75, "Saudi Arabian Riyal", isFavorite = false),
                Currency("KWD", "Kuwaiti Dinar", "د.ك", 0.31, "Kuwaiti Dinar", isFavorite = false),
                Currency("QAR", "Qatari Riyal", "ر.ق", 3.64, "Qatari Riyal", isFavorite = false),
                Currency("OMR", "Omani Rial", "ر.ع", 0.38, "Omani Rial", isFavorite = false),
                Currency("BHD", "Bahraini Dinar", "BD", 0.38, "Bahraini Dinar", isFavorite = false),
                Currency("IQD", "Iraqi Dinar", "ع.د", 1310.0, "Iraqi Dinar", isFavorite = false),
                Currency("JOD", "Jordanian Dinar", "JD", 0.71, "Jordanian Dinar", isFavorite = false),
                Currency("EGP", "Egyptian Pound", "E£", 48.0, "Egyptian Pound", isFavorite = false),
                Currency("LBP", "Lebanese Pound", "ل.ل", 89500.0, "Lebanese Pound", isFavorite = false),
                Currency("ILS", "Israeli Shekel", "₪", 3.7, "Israeli Shekel", isFavorite = false),
                Currency("TJS", "Tajikistani Somoni", "смн", 10.7, "Tajikistani Somoni", isFavorite = false),
                Currency("KZT", "Kazakhstani Tenge", "₸", 460.0, "Kazakhstani Tenge", isFavorite = false),
                Currency("UZS", "Uzbekistani Som", "сум", 12600.0, "Uzbekistani Som", isFavorite = false),
                Currency("AZN", "Azerbaijani Manat", "₼", 1.7, "Azerbaijani Manat", isFavorite = false),
                Currency("AMD", "Armenian Dram", "֏", 388.0, "Armenian Dram", isFavorite = false),
                Currency("GEL", "Georgian Lari", "₾", 2.8, "Georgian Lari", isFavorite = false),
                Currency("MAD", "Moroccan Dirham", "د.م.", 10.0, "Moroccan Dirham", isFavorite = false),
                Currency("DZD", "Algerian Dinar", "دج", 134.0, "Algerian Dinar", isFavorite = false),
                Currency("TND", "Tunisian Dinar", "د.ت", 3.12, "Tunisian Dinar", isFavorite = false),
                Currency("LYD", "Libyan Dinar", "ل.د", 4.85, "Libyan Dinar", isFavorite = false),
                Currency("SDG", "Sudanese Pound", "ج.س", 601.0, "Sudanese Pound", isFavorite = false),
                Currency("ETB", "Ethiopian Birr", "Br", 57.0, "Ethiopian Birr", isFavorite = false),
                Currency("NGN", "Nigerian Naira", "₦", 1580.0, "Nigerian Naira", isFavorite = false),
                Currency("KES", "Kenyan Shilling", "KSh", 130.0, "Kenyan Shilling", isFavorite = false),
                Currency("GHS", "Ghanaian Cedi", "₵", 16.0, "Ghanaian Cedi", isFavorite = false),
                Currency("YER", "Yemeni Rial", "ر.ي", 250.0, "Yemeni Rial", isFavorite = false),
                Currency("SYP", "Syrian Pound", "ل.س", 13000.0, "Syrian Pound", isFavorite = false),
                Currency("PKR", "Pakistani Rupee", "₨", 278.0, "Pakistani Rupee", isFavorite = false),
                Currency("BDT", "Bangladeshi Taka", "৳", 110.0, "Bangladeshi Taka", isFavorite = false),
                Currency("LKR", "Sri Lankan Rupee", "₨", 315.0, "Sri Lankan Rupee", isFavorite = false),
                Currency("MMK", "Myanmar Kyat", "K", 2100.0, "Myanmar Kyat", isFavorite = false),
                Currency("VND", "Vietnamese Dong", "₫", 25400.0, "Vietnamese Dong", isFavorite = false),
                Currency("TWD", "Taiwan Dollar", "NT$", 32.0, "New Taiwan Dollar", isFavorite = false),
                Currency("CLP", "Chilean Peso", "CLP\$", 950.0, "Chilean Peso", isFavorite = false),
                Currency("COP", "Colombian Peso", "COL\$", 4000.0, "Colombian Peso", isFavorite = false),
                Currency("ARS", "Argentine Peso", "AR\$", 1020.0, "Argentine Peso", isFavorite = false),
                Currency("PEN", "Peruvian Sol", "S/", 3.8, "Peruvian Sol", isFavorite = false),

                // ── Top Cryptocurrencies ──────────────────────────────────────
                Currency("BTC", "Bitcoin", "₿", 0.000015, "Bitcoin – Digital Gold", isFavorite = true),
                Currency("ETH", "Ethereum", "Ξ", 0.00032, "Ethereum Network", isFavorite = false),
                Currency("BNB", "BNB", "BNB", 0.0018, "BNB Smart Chain", isFavorite = false),
                Currency("XRP", "Ripple", "XRP", 2.1, "Ripple Settlement Asset", isFavorite = false),
                Currency("SOL", "Solana", "SOL", 0.0075, "Solana Ecosystem", isFavorite = false),
                Currency("USDT", "Tether", "USDT", 1.0, "Tether USD Stablecoin", isFavorite = false),
                Currency("USDC", "USD Coin", "USDC", 1.0, "USD Coin Stablecoin", isFavorite = false),
                Currency("DOGE", "Dogecoin", "Ð", 8.5, "Dogecoin", isFavorite = false),
                Currency("ADA", "Cardano", "ADA", 2.5, "Cardano Blockchain", isFavorite = false),
                Currency("TON", "Toncoin", "TON", 0.14, "The Open Network", isFavorite = false),
                Currency("TRX", "TRON", "TRX", 14.0, "TRON Network", isFavorite = false),
                Currency("LINK", "Chainlink", "LINK", 0.07, "Chainlink Oracle", isFavorite = false),
                Currency("AVAX", "Avalanche", "AVAX", 0.035, "Avalanche Platform", isFavorite = false),
                Currency("LTC", "Litecoin", "Ł", 0.014, "Litecoin – Digital Silver", isFavorite = false),
                Currency("DOT", "Polkadot", "DOT", 0.18, "Polkadot Network", isFavorite = false),
                Currency("SHIB", "Shiba Inu", "SHIB", 55000.0, "Shiba Inu Meme Token", isFavorite = false),
                Currency("ATOM", "Cosmos", "ATOM", 0.38, "Cosmos Hub", isFavorite = false),
                Currency("UNI", "Uniswap", "UNI", 0.7, "Uniswap DEX Token", isFavorite = false),
                Currency("FIL", "Filecoin", "FIL", 0.24, "Filecoin Storage", isFavorite = false),
                Currency("POL", "Polygon", "POL", 1.8, "Polygon Network", isFavorite = false),
                Currency("NEAR", "NEAR Protocol", "NEAR", 0.22, "NEAR Protocol", isFavorite = false),
                Currency("APT", "Aptos", "APT", 0.12, "Aptos Layer-1", isFavorite = false),
                Currency("SUI", "Sui", "SUI", 0.22, "Sui Layer-1", isFavorite = false),
                Currency("XLM", "Stellar", "XLM", 9.1, "Stellar Network", isFavorite = false),
                Currency("OP", "Optimism", "OP", 0.65, "Optimism L2", isFavorite = false),
                Currency("ARB", "Arbitrum", "ARB", 1.4, "Arbitrum L2", isFavorite = false),
                Currency("PEPE", "Pepe", "PEPE", 25000000.0, "Pepe Meme Token", isFavorite = false),
                Currency("WIF", "dogwifhat", "WIF", 0.67, "dogwifhat Meme", isFavorite = false),

                // ── Precious Metals & Commodities ────────────────────────────
                Currency("GOLD", "Gold (Troy Oz)", "⚜", 0.000303, "Gold price per Troy Ounce in USD", isFavorite = true),
                Currency("GOLD_24K", "Gold 24K (gram)", "Au24", 0.0097, "24-Karat gold per gram", isFavorite = false),
                Currency("GOLD_18K", "Gold 18K (gram)", "Au18", 0.0073, "18-Karat gold per gram", isFavorite = false),
                Currency("SILVER", "Silver (Troy Oz)", "Ag", 0.0333, "Silver price per Troy Ounce", isFavorite = false),
                Currency("PLATINUM", "Platinum (Troy Oz)", "Pt", 0.001, "Platinum price per Troy Ounce", isFavorite = false),
                Currency("PALLADIUM", "Palladium (Troy Oz)", "Pd", 0.00083, "Palladium price per Troy Ounce", isFavorite = false),
                Currency("COPPER", "Copper (pound)", "Cu", 0.22, "Copper price per pound", isFavorite = false),
                Currency("DIAMOND", "Diamond (Carat)", "💎", 0.00025, "Estimated Diamond price per Carat", isFavorite = false)
            )
            for (curr in defaultSeeds) {
                val existing = currencyRepository.getByCode(curr.code)
                if (existing == null) {
                    currencyRepository.insert(curr)
                }
            }

            // Only set defaults if this is truly the first run (no favorites order saved yet)
            val isFirstRun = sharedPrefs.getString("favorites_order", null) == null
            if (isFirstRun) {
                try {
                    val usd = currencyRepository.getByCode("USD")
                    if (usd != null && !usd.isFavorite) currencyRepository.toggleFavorite("USD")
                    val btc = currencyRepository.getByCode("BTC")
                    if (btc != null && !btc.isFavorite) currencyRepository.toggleFavorite("BTC")
                    val gold = currencyRepository.getByCode("GOLD")
                    if (gold != null && !gold.isFavorite) currencyRepository.toggleFavorite("GOLD")
                    saveFavoritesOrder(listOf("USD", "BTC", "GOLD"))
                } catch (e: Exception) {
                    // ignore potential race condition on initial table creation
                }
            }
    }

    fun updateBaseCurrency(code: String) {
        baseCurrencyCode.value = code
        sharedPrefs.edit().putString("base_currency", code).apply()
    }

    fun updateAmountInput(amount: String) {
        amountInput.value = amount
        sharedPrefs.edit().putString("amount_input", amount).apply()
    }

    fun saveCurrentConversionToHistory(isOnline: Boolean = false) {
        val amount = amountInput.value
        val amountValue = amount.replace(",", "").toDoubleOrNull() ?: 0.0
        if (amountValue > 0.0) {
            viewModelScope.launch {
                try {
                    val allCurrencies = currencyRepository.allCurrencies.first()
                    val favorites = allCurrencies.filter { it.isFavorite }
                    val baseCur = allCurrencies.find { it.code == baseCurrencyCode.value } 
                        ?: Currency(code = "USD", name = "US Dollar", symbol = "$", value = 1.0, notes = "Base", isFavorite = true)

                    for (toCur in favorites) {
                        if (toCur.code != baseCur.code) {
                            val converted = amountValue * (toCur.value / baseCur.value)
                            historyRepository.insert(
                                ConversionHistory(
                                    sourceCode = baseCur.code,
                                    sourceName = baseCur.name,
                                    destinationCode = toCur.code,
                                    destinationName = toCur.name,
                                    amount = amountValue,
                                    result = converted,
                                    isOnline = isOnline,
                                    timestamp = System.currentTimeMillis()
                                )
                            )
                        }
                    }
                } catch (e: Exception) {
                    // Safe catch
                }
            }
        }
    }

    fun updateLanguage(code: String) {
        languageCode.value = code
        sharedPrefs.edit().putString("language_code", code).apply()
    }

    fun updateCustomAppName(name: String) {
        customAppName.value = name
        sharedPrefs.edit().putString("custom_app_name", name).apply()
    }

    fun updateCustomAppIcon(style: String) {
        customAppIcon.value = style
        sharedPrefs.edit().putString("custom_app_icon", style).apply()
        try {
            com.example.ui.util.LauncherIconManager.updateLauncher(getApplication(), style)
        } catch (e: Exception) {
            // Safe swallow for container sandboxes
        }
    }

    fun updateCustomAppIconUri(uri: String) {
        customAppIconUri.value = uri
        sharedPrefs.edit().putString("custom_app_icon_uri", uri).apply()
    }

    fun updateSupportSectionEnabled(enabled: Boolean) {
        supportSectionEnabled.value = enabled
        sharedPrefs.edit().putBoolean("support_section_enabled", enabled).apply()
    }

    fun updateSupportWalletAddress(address: String) {
        supportWalletAddress.value = address
        sharedPrefs.edit().putString("support_wallet_address", address).apply()
    }

    fun updateSupportQrCodeUri(uri: String) {
        supportQrCodeUri.value = uri
        sharedPrefs.edit().putString("support_qr_code_uri", uri).apply()
    }

    fun updateSupportCustomTextTop(text: String) {
        supportCustomTextTop.value = text
        sharedPrefs.edit().putString("support_custom_text_top", text).apply()
    }

    fun updateSupportCustomTextBottom(text: String) {
        supportCustomTextBottom.value = text
        sharedPrefs.edit().putString("support_custom_text_bottom", text).apply()
    }

    // Currencies list combined with search and filter reactively
    val currenciesState: StateFlow<List<Currency>> = combine(
        currencyRepository.allCurrencies,
        searchQuery,
        selectedSort,
        favoritesOrder
    ) { list, query, sort, favOrder ->
        var result = list
        if (query.isNotEmpty()) {
            result = result.filter {
                it.name.contains(query, ignoreCase = true) ||
                it.code.contains(query, ignoreCase = true) ||
                it.symbol.contains(query, ignoreCase = true)
            }
        }
        when (sort) {
            SortOption.FAVORITE_FIRST -> {
                val favorites = result.filter { it.isFavorite }.sortedBy { currency ->
                    val pos = favOrder.indexOf(currency.code)
                    if (pos == -1) {
                        when (currency.code) {
                            "USD" -> -3
                            "BTC" -> -2
                            "GOLD" -> -1
                            else -> Int.MAX_VALUE
                        }
                    } else pos
                }
                // Non-pinned ALWAYS sorted A-Z
                val nonFavorites = result.filter { !it.isFavorite }.sortedBy { it.name }
                result = favorites + nonFavorites
            }
            SortOption.HIGHEST_VALUE -> {
                val favorites = result.filter { it.isFavorite }.sortedBy { currency ->
                    val pos = favOrder.indexOf(currency.code)
                    if (pos == -1) Int.MAX_VALUE else pos
                }
                // Non-pinned always A-Z even when user picks other sort options
                val nonFavorites = result.filter { !it.isFavorite }.sortedBy { it.name }
                result = favorites + nonFavorites
            }
            SortOption.LOWEST_VALUE -> {
                val favorites = result.filter { it.isFavorite }.sortedBy { currency ->
                    val pos = favOrder.indexOf(currency.code)
                    if (pos == -1) Int.MAX_VALUE else pos
                }
                val nonFavorites = result.filter { !it.isFavorite }.sortedBy { it.name }
                result = favorites + nonFavorites
            }
            SortOption.ALPHABETICAL_AZ -> {
                val favorites = result.filter { it.isFavorite }.sortedBy { currency ->
                    val pos = favOrder.indexOf(currency.code)
                    if (pos == -1) Int.MAX_VALUE else pos
                }
                val nonFavorites = result.filter { !it.isFavorite }.sortedBy { it.name }
                result = favorites + nonFavorites
            }
            SortOption.ALPHABETICAL_ZA -> {
                val favorites = result.filter { it.isFavorite }.sortedBy { currency ->
                    val pos = favOrder.indexOf(currency.code)
                    if (pos == -1) Int.MAX_VALUE else pos
                }
                val nonFavorites = result.filter { !it.isFavorite }.sortedBy { it.name }
                result = favorites + nonFavorites
            }
        }
        result
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = emptyList()
    )

    // Favorites calculated on-the-fly and sorted by favoritesOrder
    val favoriteCurrenciesState: StateFlow<List<Currency>> = combine(
        currencyRepository.allCurrencies,
        favoritesOrder
    ) { list, favOrder ->
        list.filter { it.isFavorite }.sortedBy { currency ->
            val pos = favOrder.indexOf(currency.code)
            if (pos == -1) {
                when (currency.code) {
                    "USD" -> -3
                    "BTC" -> -2
                    "GOLD" -> -1
                    else -> Int.MAX_VALUE
                }
            } else pos
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = emptyList()
    )

    // History logs
    val historyState: StateFlow<List<ConversionHistory>> = historyRepository.allHistory
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = emptyList()
        )

    // Add custom currency
    fun addCurrency(name: String, symbol: String, code: String, rates: List<ExchangeRate>, notes: String) {
        viewModelScope.launch {
            val cleanCode = code.uppercase().trim()
            if (validateExchangeRates(cleanCode, rates) != null) {
                // Reject inconsistent record
                return@launch
            }
            val rawRatesJson = formatExchangeRates(rates)
            val normalizedVal = calculateRepresentativeValue(cleanCode, rates, rawCurrenciesState.value)

            val currency = Currency(
                code = cleanCode,
                name = name,
                symbol = symbol,
                value = normalizedVal,
                offlineValue = normalizedVal,
                rateValue = rates.firstOrNull()?.currentAmount ?: 1.0,
                baseCurrencyCode = rates.firstOrNull()?.referenceCurrencyCode ?: "USD",
                exchangeRatesJson = rawRatesJson,
                notes = notes,
                isFavorite = false
            )
            currencyRepository.insert(currency)
        }
    }

    // Edit custom currency
    fun editCurrency(currency: Currency) {
        viewModelScope.launch {
            currencyRepository.update(currency)
        }
    }

    // Edit custom currency with rates
    fun editCurrencyWithRates(
        cur: Currency,
        name: String,
        symbol: String,
        rates: List<ExchangeRate>,
        notes: String
    ) {
        viewModelScope.launch {
            val cleanCode = cur.code.uppercase().trim()
            if (validateExchangeRates(cleanCode, rates) != null) {
                // Reject inconsistent record
                return@launch
            }
            val rawRatesJson = formatExchangeRates(rates)
            val normalizedVal = calculateRepresentativeValue(cleanCode, rates, rawCurrenciesState.value)

            val updated = cur.copy(
                name = name,
                symbol = symbol,
                value = normalizedVal,
                offlineValue = normalizedVal,
                rateValue = rates.firstOrNull()?.currentAmount ?: 1.0,
                baseCurrencyCode = rates.firstOrNull()?.referenceCurrencyCode ?: "USD",
                exchangeRatesJson = rawRatesJson,
                notes = notes
            )
            currencyRepository.update(updated)
        }
    }

    // Delete custom currency
    fun deleteCurrency(currency: Currency) {
        viewModelScope.launch {
            currencyRepository.delete(currency)
            val currentList = favoritesOrder.value.toMutableList()
            if (currentList.remove(currency.code)) {
                saveFavoritesOrder(currentList)
            }
        }
    }

    // Favorite toggle
    fun toggleFavorite(code: String) {
        viewModelScope.launch {
            val exists = currencyRepository.getByCode(code) ?: return@launch
            val isFavNow = !exists.isFavorite
            currencyRepository.toggleFavorite(code)
            
            val currentList = favoritesOrder.value.toMutableList()
            if (isFavNow) {
                if (!currentList.contains(code)) {
                    currentList.add(code)
                }
            } else {
                currentList.remove(code)
            }
            saveFavoritesOrder(currentList)
        }
    }

    // History actions
    fun deleteHistoryItem(id: Long) {
        viewModelScope.launch {
            historyRepository.deleteById(id)
        }
    }

    fun clearAllHistory() {
        viewModelScope.launch {
            historyRepository.clearAll()
        }
    }

    fun clearHistoryByMode(isOnline: Boolean) {
        viewModelScope.launch {
            historyRepository.clearHistoryByMode(isOnline)
        }
    }

    // Conversion Math calculation and Logger
    fun performConversion(
        amount: Double,
        fromCur: Currency,
        toCur: Currency,
        isOnline: Boolean = false
    ): Double {
        val allCurrencies = rawCurrenciesState.value
        val conversionMultiplier = findConversionMultiplier(fromCur.code, toCur.code, allCurrencies) ?: 0.0
        val rawResult = amount * conversionMultiplier
        
        // Save conversion in background to history
        viewModelScope.launch {
            historyRepository.insert(
                ConversionHistory(
                    sourceCode = fromCur.code,
                    sourceName = fromCur.name,
                    destinationCode = toCur.code,
                    destinationName = toCur.name,
                    amount = amount,
                    result = rawResult,
                    isOnline = isOnline,
                    timestamp = System.currentTimeMillis()
                )
            )
        }
        return rawResult
    }

    // Settings updates
    fun updateTheme(newTheme: String) {
        themeMode.value = newTheme
        sharedPrefs.edit().putString("theme", newTheme).apply()
    }

    fun updateDisplayUnit(newUnit: String) {
        displayCurrencyUnit.value = newUnit
        sharedPrefs.edit().putString("display_unit", newUnit).apply()
    }

    fun updateSecretPassword(newPass: String) {
        secretPassword.value = newPass
        sharedPrefs.edit().putString("secret_password", newPass).apply()
    }

    private fun parseRatesFromAnyJson(jsonString: String): Map<String, Double> {
        val result = mutableMapOf<String, Double>()
        try {
            val root = org.json.JSONObject(jsonString)
            if (root.has("usd") && root.optJSONObject("usd") != null) {
                val usdObj = root.getJSONObject("usd")
                val keys = usdObj.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    result[key.uppercase()] = usdObj.getDouble(key)
                }
                return result
            }
            if (root.has("rates") && root.optJSONObject("rates") != null) {
                val ratesObj = root.getJSONObject("rates")
                val keys = ratesObj.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    result[key.uppercase()] = ratesObj.getDouble(key)
                }
                return result
            }
            val keys = root.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val optVal = root.optDouble(key, -1.0)
                if (optVal > 0.0) {
                    result[key.uppercase()] = optVal
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return result
    }

    fun triggerOnlineRatesUpdate() {
        if (isOnlineUpdating.value) return
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val scope = this
            isOnlineUpdating.value = true
            onlineUpdateSuccessMessage.value = null
            
            val currentTab = selectedMarketTab.value
            
            // Get all enabled custom APIs for this market tab, grouped by category
            val allActiveApis = customApisState.value.filter { it.isEnabled && it.marketType == currentTab }
            val fiatApis    = allActiveApis.filter { it.apiCategory == "FIAT" }
            val cryptoApis  = allActiveApis.filter { it.apiCategory == "CRYPTO" }
            val preciousApis= allActiveApis.filter { it.apiCategory == "PRECIOUS" }
            // Legacy: if category not set, treat as FIAT
            val activeApis = allActiveApis
            
            val client = okhttp3.OkHttpClient.Builder()
                .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                .build()
                
            var mergedRates: Map<String, Double>? = null
            var success = false
            
            // Helper: fetch from a category's custom API list with priority system
            suspend fun fetchFromCategoryApis(categoryApis: List<CustomApi>): Map<String, Double>? {
                if (categoryApis.isEmpty()) return null
                val priorities = listOf("PRIMARY", "SECONDARY", "FALLBACK")
                for (prio in priorities) {
                    val prioApis = categoryApis.filter { it.priority == prio }
                    if (prioApis.isNotEmpty()) {
                        val jobs = prioApis.map { api ->
                            kotlinx.coroutines.coroutineScope {
                                async(kotlinx.coroutines.Dispatchers.IO) {
                                    try {
                                        val request = okhttp3.Request.Builder().url(api.url).build()
                                        client.newCall(request).execute().use { response ->
                                            if (response.isSuccessful) {
                                                val body = response.body?.string()
                                                if (!body.isNullOrEmpty()) parseRatesFromAnyJson(body) else null
                                            } else null
                                        }
                                    } catch (e: Exception) { null }
                                }
                            }
                        }
                        val results = jobs.map { it.await() }.filterNotNull()
                        if (results.isNotEmpty()) {
                            val merged = mutableMapOf<String, Double>()
                            val allKeys = results.flatMap { it.keys }.distinct()
                            for (key in allKeys) {
                                val values = results.mapNotNull { it[key] }
                                if (values.isNotEmpty()) merged[key] = values.average()
                            }
                            return merged
                        }
                    }
                }
                return null
            }

            // Check if user has set custom APIs for this market tab with categories
            val hasCustomFiat     = fiatApis.any { it.id.length > 5 }  // UUID = user-added
            val hasCustomCrypto   = cryptoApis.any { it.id.length > 5 }
            val hasCustomPrecious = preciousApis.any { it.id.length > 5 }

            if (allActiveApis.isNotEmpty()) {
                // Priority system execution: PRIMARY, then SECONDARY, then FALLBACK
                val priorities = listOf("PRIMARY", "SECONDARY", "FALLBACK")
                for (prio in priorities) {
                    val prioApis = activeApis.filter { it.priority == prio }
                    if (prioApis.isNotEmpty()) {
                        // Query simultaneously (Parallel fetch)
                        val jobs = prioApis.map { api ->
                            scope.async(kotlinx.coroutines.Dispatchers.IO) {
                                try {
                                    val request = okhttp3.Request.Builder().url(api.url).build()
                                    client.newCall(request).execute().use { response ->
                                        if (response.isSuccessful) {
                                            val body = response.body?.string()
                                            if (!body.isNullOrEmpty()) {
                                                parseRatesFromAnyJson(body)
                                            } else null
                                        } else null
                                    }
                                } catch (e: Exception) {
                                    null
                                }
                            }
                        }
                        val results = jobs.map { it.await() }.filterNotNull()
                        if (results.isNotEmpty()) {
                            // Merge results (Simultaneous combination)
                            val allKeys = results.flatMap { it.keys }.distinct()
                            val tempMerged = mutableMapOf<String, Double>()
                            for (key in allKeys) {
                                val values = results.mapNotNull { it[key] }
                                if (values.isNotEmpty()) {
                                    tempMerged[key] = values.average()
                                }
                            }
                            mergedRates = tempMerged
                            success = true
                            break // Success on this priority level!
                        }
                    }
                }
            }
            
            // 2. Fallback to defaults if no custom API succeeded (use category-specific or hardcoded)
            if (!success) {
                if (currentTab == "GLOBAL") {
                    val fiatDeferred = scope.async(kotlinx.coroutines.Dispatchers.IO) {
                        // Try user's custom FIAT APIs first (default ones included)
                        val customResult = fetchFromCategoryApis(fiatApis)
                        if (customResult != null) return@async customResult

                        // Hardcoded fallback chain
                        var bodyStr: String? = null
                        try {
                            val request = okhttp3.Request.Builder().url("https://open.er-api.com/v6/latest/USD").build()
                            client.newCall(request).execute().use { r -> if (r.isSuccessful) bodyStr = r.body?.string() }
                        } catch (e: Exception) { e.printStackTrace() }
                        if (bodyStr.isNullOrEmpty()) {
                            try {
                                val request = okhttp3.Request.Builder().url("https://latest.currency-api.pages.dev/v1/currencies/usd.json").build()
                                client.newCall(request).execute().use { r -> if (r.isSuccessful) bodyStr = r.body?.string() }
                            } catch (e: Exception) { e.printStackTrace() }
                        }
                        if (bodyStr.isNullOrEmpty()) {
                            try {
                                val request = okhttp3.Request.Builder().url("https://cdn.jsdelivr.net/npm/@fawazahmed0/currency-api@latest/v1/currencies/usd.json").build()
                                client.newCall(request).execute().use { r -> if (r.isSuccessful) bodyStr = r.body?.string() }
                            } catch (e: Exception) { e.printStackTrace() }
                        }
                        if (!bodyStr.isNullOrEmpty()) parseRatesFromAnyJson(bodyStr!!) else null
                    }

                    val cryptoDeferred = scope.async(kotlinx.coroutines.Dispatchers.IO) {
                        // Try user's custom CRYPTO APIs first
                        val customResult = fetchFromCategoryApis(cryptoApis)
                        if (customResult != null) return@async customResult

                        // Fallback: CoinGecko (default)
                        try {
                            val request = okhttp3.Request.Builder().url("https://api.coingecko.com/api/v3/exchange_rates").build()
                            client.newCall(request).execute().use { r ->
                                if (r.isSuccessful) {
                                    val body = r.body?.string()
                                    if (!body.isNullOrEmpty()) {
                                        val root = org.json.JSONObject(body)
                                        val ratesObj = root.getJSONObject("rates")
                                        // CoinGecko exchange_rates: all values are relative to BTC
                                        // "value" = how many BTC equals 1 unit of this currency
                                        // usd.value = BTC price of 1 USD → to get USD price per crypto:
                                        //   usdPerCoin = coinValue / usdValue
                                        val usdObj = ratesObj.optJSONObject("usd") ?: return@use null
                                        val usdValueInBtc = usdObj.getDouble("value")
                                        if (usdValueInBtc <= 0.0) return@use null

                                        // Only extract known crypto codes - fiat rates from this endpoint are unreliable
                                        val cryptoCodes = setOf("BTC","ETH","SOL","BNB","ADA","DOGE","XRP","LTC","LINK","DOT","AVAX","MATIC","TON","USDT","USDC","SHIB","XLM","UNI","ATOM","FIL")
                                        val cryptoMap = mutableMapOf<String, Double>()
                                        val keys = ratesObj.keys()
                                        while (keys.hasNext()) {
                                            val key = keys.next()
                                            val upperKey = key.uppercase()
                                            if (upperKey in cryptoCodes) {
                                                val obj = ratesObj.optJSONObject(key) ?: continue
                                                val valInBtc = obj.optDouble("value", 0.0)
                                                if (valInBtc > 0.0) {
                                                    // valInBtc = how many BTC for 1 unit of this crypto
                                                    // usdValueInBtc = how many BTC for 1 USD
                                                    // usdPerCoin = valInBtc / usdValueInBtc
                                                    val usdPerCoin = valInBtc / usdValueInBtc
                                                    cryptoMap[upperKey] = usdPerCoin
                                                }
                                            }
                                        }
                                        cryptoMap
                                    } else null
                                } else null
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                            null
                        }
                    }

                    val goldDeferred = scope.async(kotlinx.coroutines.Dispatchers.IO) {
                        // Try user's custom PRECIOUS APIs first
                        val customResult = fetchFromCategoryApis(preciousApis)
                        if (customResult != null) return@async customResult

                        // Fallback: metals.live (default)
                        try {
                            val request = okhttp3.Request.Builder().url("https://api.metals.live/v1/spot").build()
                            client.newCall(request).execute().use { r ->
                                if (r.isSuccessful) {
                                    val body = r.body?.string()
                                    if (!body.isNullOrEmpty()) {
                                        val trimmed = body.trim()
                                        val rootObj = if (trimmed.startsWith("[")) {
                                            val arr = org.json.JSONArray(trimmed)
                                            if (arr.length() > 0) arr.getJSONObject(0) else null
                                        } else {
                                            org.json.JSONObject(trimmed)
                                        }
                                        if (rootObj != null) {
                                            val goldMap = mutableMapOf<String, Double>()
                                            // api.metals.live returns price per troy oz in USD
                                            // value field = "how many USD per 1 unit" → store directly
                                            if (rootObj.has("gold")) {
                                                val goldPrice = rootObj.getDouble("gold")
                                                if (goldPrice > 0.0) {
                                                    goldMap["GOLD"] = goldPrice
                                                    goldMap["XAU"] = goldPrice
                                                }
                                            }
                                            if (rootObj.has("silver")) {
                                                val silverPrice = rootObj.getDouble("silver")
                                                if (silverPrice > 0.0) {
                                                    goldMap["SILVER"] = silverPrice
                                                    goldMap["XAG"] = silverPrice
                                                }
                                            }
                                            if (rootObj.has("platinum")) {
                                                val platinumPrice = rootObj.getDouble("platinum")
                                                if (platinumPrice > 0.0) {
                                                    goldMap["PLATINUM"] = platinumPrice
                                                    goldMap["XPT"] = platinumPrice
                                                }
                                            }
                                            goldMap
                                        } else null
                                    } else null
                                } else null
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                            null
                        }
                    }

                    val results = kotlinx.coroutines.awaitAll(fiatDeferred, cryptoDeferred, goldDeferred)
                    val fiatRes = results[0]
                    val cryptoRes = results[1]
                    val goldRes = results[2]

                    val combined = mutableMapOf<String, Double>()
                    if (fiatRes != null) combined.putAll(fiatRes)
                    if (cryptoRes != null) combined.putAll(cryptoRes)
                    if (goldRes != null) combined.putAll(goldRes)

                    if (combined.isNotEmpty()) {
                        mergedRates = combined
                        success = true
                    }
                } else {
                    val freeDeferred = scope.async(kotlinx.coroutines.Dispatchers.IO) {
                        var bodyStr: String? = null
                        // PRIMARY: brsapi.ir - real Iranian free market data, no API key needed
                        try {
                            val request = okhttp3.Request.Builder()
                                .url("https://brsapi.ir/Api/Market/Gold_Currency.php")
                                .header("User-Agent", "Mozilla/5.0")
                                .build()
                            client.newCall(request).execute().use { r ->
                                if (r.isSuccessful) bodyStr = r.body?.string()
                            }
                        } catch (e: Exception) { e.printStackTrace() }

                        if (!bodyStr.isNullOrEmpty()) {
                            // brsapi.ir response: {"currency":[{"code":"usd","price":RIAL_VALUE,...},...],"gold":[...]}
                            // "price" = IRR per 1 unit of foreign currency
                            val brsMap = mutableMapOf<String, Double>()
                            try {
                                val root = org.json.JSONObject(bodyStr!!)

                                // Parse all currencies: price is IRR per 1 unit
                                val currencyArr = root.optJSONArray("currency")
                                if (currencyArr != null) {
                                    for (i in 0 until currencyArr.length()) {
                                        val item = currencyArr.getJSONObject(i)
                                        val code = item.optString("code").uppercase()
                                        val priceInRials = item.optDouble("price", 0.0)
                                        if (code.isNotBlank() && priceInRials > 0.0) {
                                            brsMap["__IRR__$code"] = priceInRials
                                        }
                                    }
                                }

                                // Get USD in rials as the base
                                val usdInRials = brsMap["__IRR__USD"] ?: 0.0
                                if (usdInRials > 0.0) {
                                    val resolvedMap = mutableMapOf<String, Double>()
                                    resolvedMap["USD"] = 1.0
                                    resolvedMap["RIAL"] = usdInRials         // how many RIAL per 1 USD
                                    resolvedMap["TOMAN"] = usdInRials / 10.0 // how many TOMAN per 1 USD

                                    for ((k, v) in brsMap) {
                                        if (k.startsWith("__IRR__")) {
                                            val currCode = k.removePrefix("__IRR__")
                                            if (currCode != "USD") {
                                                // USD per 1 unit of currency = (IRR per unit) / (IRR per USD)
                                                resolvedMap[currCode] = v / usdInRials
                                            }
                                        }
                                    }

                                    // Parse gold prices if available (price in IRR per gram or per full unit)
                                    val goldArr = root.optJSONArray("gold")
                                    if (goldArr != null) {
                                        for (i in 0 until goldArr.length()) {
                                            val item = goldArr.getJSONObject(i)
                                            val name = item.optString("name").lowercase()
                                            val priceInRials = item.optDouble("price", 0.0)
                                            if (priceInRials > 0.0 && usdInRials > 0.0) {
                                                val priceInUsd = priceInRials / usdInRials
                                                when {
                                                    name.contains("mithqal") || name.contains("mesghal") -> {} // skip
                                                    name.contains("18") -> resolvedMap["GOLD_18K"] = priceInUsd
                                                    name.contains("24") -> resolvedMap["GOLD_24K"] = priceInUsd
                                                    name.contains("gram") || name.contains("geram") -> resolvedMap["GOLD_24K"] = priceInUsd
                                                }
                                            }
                                        }
                                    }

                                    resolvedMap
                                } else null
                            } catch (e: Exception) {
                                e.printStackTrace()
                                null
                            }
                        } else null
                    }

                    val freeResult = freeDeferred.await()
                    if (freeResult != null && freeResult.isNotEmpty()) {
                        mergedRates = freeResult
                        success = true
                    }
                }
            }
            
            try {
                val currentList = currencyRepository.allCurrencies.firstOrNull() ?: emptyList()
                
                // Cache old prices to map in order to track flash indicators
                val oldRates = mutableMapOf<String, Double>()
                for (curr in currentList) {
                    oldRates[curr.code] = curr.value
                }
                previousRates.value = oldRates
        
                val ratesToApply = mergedRates
                if (success && ratesToApply != null && ratesToApply.isNotEmpty()) {
                    var liveGoldRate: Double? = null

                    // For FREE market: get the free-market TOMAN rate (how many TOMAN per 1 USD)
                    // and global fiat rates so we can cross-calculate currencies not in the free API
                    val freeTomanPerUsd: Double? = if (currentTab == "FREE") {
                        ratesToApply["TOMAN"] ?: ratesToApply["TOMAN".lowercase()]
                    } else null

                    // Fetch global fiat rates once if we're in FREE mode (for cross-rate calculation)
                    val globalFiatRates: Map<String, Double> = if (currentTab == "FREE" && freeTomanPerUsd != null && freeTomanPerUsd > 0.0) {
                        try {
                            val globalReq = okhttp3.Request.Builder().url("https://open.er-api.com/v6/latest/USD").build()
                            val globalBody = client.newCall(globalReq).execute().use { r ->
                                if (r.isSuccessful) r.body?.string() else null
                            }
                            if (!globalBody.isNullOrEmpty()) parseRatesFromAnyJson(globalBody) else emptyMap()
                        } catch (e: Exception) { emptyMap() }
                    } else emptyMap()

                    for (curr in currentList) {
                        val codeUpper = curr.code.uppercase().trim()
                        val possibleKeys = listOf(
                            codeUpper,
                            codeUpper.lowercase(),
                            when (codeUpper) {
                                "GOLD" -> "XAU"
                                "SILVER" -> "XAG"
                                "PLATINUM" -> "XPT"
                                "TOMAN" -> "IRR"
                                "RIAL" -> "IRR"
                                else -> codeUpper
                            }
                        )

                        var foundRate: Double? = null
                        for (k in possibleKeys) {
                            if (ratesToApply.containsKey(k)) {
                                foundRate = ratesToApply[k]
                                break
                            }
                        }

                        // FREE market cross-rate: if currency not in free API (brsapi.ir), derive from global rates
                        // Example: AFN from brsapi → real free market rate. If missing → use global rate as fallback.
                        // The difference shows when converting: 1M TOMAN → AFN uses free TOMAN rate
                        if (foundRate == null && currentTab == "FREE" && freeTomanPerUsd != null) {
                            for (k in possibleKeys) {
                                val globalRate = globalFiatRates[k] ?: globalFiatRates[k.lowercase()]
                                if (globalRate != null && globalRate > 0.0) {
                                    foundRate = globalRate
                                    break
                                }
                            }
                        }

                        if (foundRate != null) {
                            val finalValue = when (codeUpper) {
                                "USD" -> 1.0
                                "TOMAN" -> {
                                    if (currentTab == "FREE") {
                                        // brsapi.ir: TOMAN = RIAL / 10, already resolved in freeDeferred
                                        foundRate
                                    } else {
                                        // open.er-api returns IRR → TOMAN = IRR / 10
                                        foundRate / 10.0
                                    }
                                }
                                "RIAL" -> {
                                    if (currentTab == "FREE") {
                                        foundRate * 10.0  // TOMAN * 10 = RIAL for free market
                                    } else {
                                        foundRate  // IRR directly from global API
                                    }
                                }
                                else -> foundRate
                            }

                            if (codeUpper == "GOLD") {
                                liveGoldRate = finalValue
                            }

                            currencyRepository.update(curr.copy(value = finalValue))
                        }
                    }
                    
                    // Derive gold fractions dynamically
                    // liveGoldRate = USD per troy oz (31.1035g)
                    // GOLD_24K = USD per gram of 24k gold
                    // GOLD_18K = USD per gram of 18k gold (75% pure = 24k * 0.75)
                    if (liveGoldRate != null) {
                        val gold24kVal = liveGoldRate / 31.1035
                        val gold18kVal = gold24kVal * 0.75
                        val existing24k = currentList.firstOrNull { it.code == "GOLD_24K" }
                        if (existing24k != null) {
                            currencyRepository.update(existing24k.copy(value = gold24kVal))
                        }
                        val existing18k = currentList.firstOrNull { it.code == "GOLD_18K" }
                        if (existing18k != null) {
                            currencyRepository.update(existing18k.copy(value = gold18kVal))
                        }
                    }
                    
                    // Save last sync time in shared preferences
                    val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                    val syncTimeStr = sdf.format(Date())
                    if (currentTab == "GLOBAL") {
                        sharedPrefs.edit().putString("last_sync_time_global", syncTimeStr).apply()
                    } else {
                        sharedPrefs.edit().putString("last_sync_time_free", syncTimeStr).apply()
                    }
                    onlineUpdateSuccessMessage.value = "SUCCESS"
                } else {
                    // Fallback or failed connection: do NOT fluctuate or simulate, just show the last cached values
                    onlineUpdateSuccessMessage.value = "CACHED"
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                isOnlineUpdating.value = false
            }
        }
    }

    fun clearOnlineUpdateMessage() {
        onlineUpdateSuccessMessage.value = null
    }

    fun syncAllCurrencies(onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            val defaultSeeds = listOf(
                Currency("USD", "US Dollar", "$", 1.0, "US Dollar (Base Reference)", isFavorite = true),
                Currency("TOMAN", "Iranian Toman", "T", 60000.0, "Iranian Toman", isFavorite = false),
                Currency("RIAL", "Iranian Rial", "﷼", 600000.0, "Iranian Rial", isFavorite = false),
                Currency("EUR", "Euro", "€", 0.92, "Euro Zone", isFavorite = false),
                Currency("GBP", "Pound Sterling", "£", 0.77, "Great British Pound", isFavorite = false),
                Currency("AFN", "Afghan Afghani", "؋", 71.0, "Afghan Afghani", isFavorite = false),
                Currency("BTC", "Bitcoin", "₿", 0.000015, "Bitcoin Digital Gold", isFavorite = true),
                Currency("TON", "Toncoin", "TON", 0.14, "The Open Network", isFavorite = false),
                Currency("ETH", "Ethereum", "Ξ", 0.00032, "Ethereum Network", isFavorite = false),
                Currency("SOL", "Solana", "SOL", 0.0075, "Solana Ecosystem", isFavorite = false),
                Currency("BNB", "Binance Coin", "BNB", 0.0018, "BNB Smart Chain", isFavorite = false),
                Currency("ADA", "Cardano", "ADA", 2.5, "Cardano Blockchain", isFavorite = false),
                Currency("DOGE", "Dogecoin", "Ð", 8.5, "Dogecoin Meme Asset", isFavorite = false),
                Currency("XRP", "Ripple", "XRP", 2.1, "Ripple Settlement Asset", isFavorite = false),
                Currency("USDT", "Tether USD", "USDT", 1.0, "Tether USD Stablecoin", isFavorite = false),
                Currency("USDC", "USD Coin", "USDC", 1.0, "USD Coin Stablecoin", isFavorite = false),
                Currency("LTC", "Litecoin", "Ł", 0.014, "Litecoin Digital Silver", isFavorite = false),
                Currency("LINK", "Chainlink", "LINK", 0.07, "Chainlink Oracle Token", isFavorite = false),
                Currency("DOT", "Polkadot", "DOT", 0.18, "Polkadot Interoperability Network", isFavorite = false),
                Currency("POL", "Polygon", "POL", 1.8, "Polygon Multi-chain Network", isFavorite = false),
                Currency("SHIB", "Shiba Inu", "SHIB", 55000.0, "Shiba Inu Meme Token", isFavorite = false),
                Currency("AVAX", "Avalanche", "AVAX", 0.035, "Avalanche Platform Token", isFavorite = false),
                Currency("JPY", "Japanese Yen", "¥", 158.0, "Japanese Yen", isFavorite = false),
                Currency("CAD", "Canadian Dollar", "C$", 1.37, "Canadian Dollar", isFavorite = false),
                Currency("CHF", "Swiss Franc", "CHF", 0.89, "Swiss Franc", isFavorite = false),
                Currency("CNY", "Chinese Yuan", "¥", 7.25, "Chinese Yuan Renminbi", isFavorite = false),
                Currency("TRY", "Turkish Lira", "₺", 32.8, "Turkish Lira", isFavorite = false),
                Currency("AED", "Emirati Dirham", "AED", 3.67, "United Arab Emirates Dirham", isFavorite = false),
                Currency("AUD", "Australian Dollar", "A$", 1.50, "Australian Dollar", isFavorite = false),
                Currency("TJS", "Tajikistani Somoni", "смн", 10.7, "Tajikistani Somoni (TJS)", isFavorite = false),
                Currency("SAR", "Saudi Riyal", "ر.س", 3.75, "Saudi Arabian Riyal (SAR)", isFavorite = false),
                Currency("KWD", "Kuwaiti Dinar", "د.ك", 0.31, "Kuwaiti Dinar (KWD)", isFavorite = false),
                Currency("QAR", "Qatari Riyal", "ر.ق", 3.64, "Qatari Riyal (QAR)", isFavorite = false),
                Currency("OMR", "Omani Rial", "ر.ع", 0.38, "Omani Rial (OMR)", isFavorite = false),
                Currency("BHD", "Bahraini Dinar", "د.ب", 0.38, "Bahraini Dinar (BHD)", isFavorite = false),
                Currency("IQD", "Iraqi Dinar", "د.ع", 1310.0, "Iraqi Dinar (IQD)", isFavorite = false),
                Currency("SYP", "Syrian Pound", "ل.س", 13000.0, "Syrian Pound (SYP)", isFavorite = false),
                Currency("LBP", "Lebanese Pound", "ل.ل", 89500.0, "Lebanese Pound (LBP)", isFavorite = false),
                Currency("JOD", "Jordanian Dinar", "د.ا", 0.71, "Jordanian Dinar (JOD)", isFavorite = false),
                Currency("EGP", "Egyptian Pound", "ج.م", 48.0, "Egyptian Pound (EGP)", isFavorite = false),
                Currency("YER", "Yemeni Rial", "ر.ي", 250.0, "Yemeni Rial (YER)", isFavorite = false),
                Currency("KZT", "Kazakhstani Tenge", "₸", 460.0, "Kazakhstani Tenge (KZT)", isFavorite = false),
                Currency("UZS", "Uzbekistani Som", "сум", 12600.0, "Uzbekistani Som (UZS)", isFavorite = false),
                Currency("AZN", "Azerbaijani Manat", "₼", 1.7, "Azerbaijani Manat (AZN)", isFavorite = false),
                Currency("AMD", "Armenian Dram", "դր.", 388.0, "Armenian Dram (AMD)", isFavorite = false),
                Currency("GEL", "Georgian Lari", "₾", 2.8, "Georgian Lari (GEL)", isFavorite = false),
                Currency("ILS", "Israeli Shekel", "₪", 3.7, "Israeli Shekel (ILS)", isFavorite = false),
                Currency("DZD", "Algerian Dinar", "د.ج", 134.0, "Algerian Dinar (DZD)", isFavorite = false),
                Currency("MAD", "Moroccan Dirham", "د.م.", 10.0, "Moroccan Dirham (MAD)", isFavorite = false),
                Currency("LYD", "Libyan Dinar", "ل.د", 4.85, "Libyan Dinar (LYD)", isFavorite = false),
                Currency("TND", "Tunisian Dinar", "د.ت", 3.12, "Tunisian Dinar (TND)", isFavorite = false),
                Currency("SDG", "Sudanese Pound", "ج.س", 601.0, "Sudanese Pound (SDG)", isFavorite = false),
                Currency("INR", "Indian Rupee", "₹", 83.5, "Indian Rupee (INR)", isFavorite = false),
                Currency("RUB", "Russian Ruble", "₽", 88.0, "Russian Ruble (RUB)", isFavorite = false),
                Currency("PKR", "Pakistani Rupee", "₨", 278.0, "Pakistani Rupee (PKR)", isFavorite = false),
                Currency("BRL", "Brazilian Real", "R$", 5.4, "Brazilian Real (BRL)", isFavorite = false),
                Currency("ZAR", "South African Rand", "R", 18.0, "South African Rand (ZAR)", isFavorite = false),
                Currency("SGD", "Singapore Dollar", "S$", 1.35, "Singapore Dollar (SGD)", isFavorite = false),
                Currency("NZD", "New Zealand Dollar", "NZ$", 1.63, "New Zealand Dollar (NZD)", isFavorite = false),
                Currency("KRW", "South Korean Won", "₩", 1380.0, "South Korean Won (KRW)", isFavorite = false),
                Currency("MXN", "Mexican Peso", "Mex$", 18.2, "Mexican Peso (MXN)", isFavorite = false),
                Currency("SEK", "Swedish Krona", "kr", 10.5, "Swedish Krona", isFavorite = false),
                Currency("NOK", "Norwegian Krone", "kr", 10.6, "Norwegian Krone", isFavorite = false),
                Currency("DKK", "Danish Krone", "kr", 6.9, "Danish Krone", isFavorite = false),
                Currency("PLN", "Polish Zloty", "zł", 4.0, "Polish Zloty", isFavorite = false),
                Currency("HKD", "Hong Kong Dollar", "HK$", 7.8, "Hong Kong Dollar", isFavorite = false),
                Currency("IDR", "Indonesian Rupiah", "Rp", 16400.0, "Indonesian Rupiah", isFavorite = false),
                Currency("PHP", "Philippine Peso", "₱", 58.0, "Philippine Peso", isFavorite = false),
                Currency("MYR", "Malaysian Ringgit", "RM", 4.7, "Malaysian Ringgit", isFavorite = false),
                Currency("THB", "Thai Baht", "฿", 36.5, "Thai Baht", isFavorite = false),
                Currency("VND", "Vietnamese Dong", "₫", 25400.0, "Vietnamese Dong", isFavorite = false),
                Currency("COP", "Colombian Peso", "CO$", 4100.0, "Colombian Peso", isFavorite = false),
                Currency("ARS", "Argentine Peso", "AR$", 900.0, "Argentine Peso", isFavorite = false),
                Currency("CLP", "Chilean Peso", "CL$", 930.0, "Chilean Peso", isFavorite = false),
                Currency("PEN", "Peruvian Sol", "S/.", 3.8, "Peruvian Sol", isFavorite = false),
                Currency("HUF", "Hungarian Forint", "Ft", 368.0, "Hungarian Forint", isFavorite = false),
                Currency("CZK", "Czech Koruna", "Kč", 23.2, "Czech Koruna", isFavorite = false),
                Currency("RON", "Romanian Leu", "lei", 4.6, "Romanian Leu", isFavorite = false),
                Currency("BGN", "Bulgarian Lev", "лв", 1.8, "Bulgarian Lev", isFavorite = false),
                Currency("ISK", "Icelandic Krona", "kr", 139.0, "Icelandic Krona", isFavorite = false),
                Currency("RSD", "Serbian Dinar", "дл.", 108.0, "Serbian Dinar", isFavorite = false),
                Currency("UAH", "Ukrainian Hryvnia", "₴", 40.5, "Ukrainian Hryvnia", isFavorite = false),
                Currency("KGS", "Kyrgyzstani Som", "лв", 87.0, "Kyrgyzstani Som", isFavorite = false),
                Currency("TMT", "Turkmenistani Manat", "m", 3.5, "Turkmenistani Manat", isFavorite = false),
                Currency("MNT", "Mongolian Tugrik", "₮", 3450.0, "Mongolian Tugrik", isFavorite = false),
                Currency("LKR", "Sri Lankan Rupee", "Rs", 305.0, "Sri Lankan Rupee", isFavorite = false),
                Currency("NPR", "Nepalese Rupee", "रू", 133.0, "Nepalese Rupee", isFavorite = false),
                Currency("BDT", "Bangladeshi Taka", "৳", 117.0, "Bangladeshi Taka", isFavorite = false),
                Currency("MVR", "Maldivian Rufiyaa", "Rf", 15.4, "Maldivian Rufiyaa", isFavorite = false),
                Currency("KHR", "Cambodian Riel", "៛", 4120.0, "Cambodian Riel", isFavorite = false),
                Currency("LAK", "Laotian Kip", "₭", 21800.0, "Laotian Kip", isFavorite = false),
                Currency("MMK", "Myanmar Kyat", "K", 2100.0, "Myanmar Kyat", isFavorite = false),
                Currency("BND", "Brunei Dollar", "B$", 1.35, "Brunei Dollar", isFavorite = false),
                Currency("GHS", "Ghanaian Cedi", "₵", 14.8, "Ghanaian Cedi", isFavorite = false),
                Currency("NGN", "Nigerian Naira", "₦", 1480.0, "Nigerian Naira", isFavorite = false),
                Currency("KES", "Kenyan Shilling", "KSh", 129.0, "Kenyan Shilling", isFavorite = false),
                Currency("UGX", "Ugandan Shilling", "USh", 3740.0, "Ugandan Shilling", isFavorite = false),
                Currency("TZS", "Tanzanian Shilling", "TSh", 2600.0, "Tanzanian Shilling", isFavorite = false),
                Currency("RWF", "Rwandan Franc", "FRw", 1310.0, "Rwandan Franc", isFavorite = false),
                Currency("ETB", "Ethiopian Birr", "Br", 57.0, "Ethiopian Birr", isFavorite = false),
                Currency("JMD", "Jamaican Dollar", "J$", 156.0, "Jamaican Dollar", isFavorite = false),
                Currency("DOP", "Dominican Peso", "RD$", 59.0, "Dominican Peso", isFavorite = false),
                Currency("CRC", "Costa Rican Colon", "₡", 525.0, "Costa Rican Colon", isFavorite = false),
                Currency("PAB", "Panamanian Balboa", "B/.", 1.0, "Panamanian Balboa", isFavorite = false),
                Currency("HNL", "Honduran Lempira", "L", 24.7, "Honduran Lempira", isFavorite = false),
                Currency("NIO", "Nicaraguan Cordoba", "C$", 36.8, "Nicaraguan Cordoba", isFavorite = false),
                Currency("GTQ", "Guatemalan Quetzal", "Q", 7.7, "Guatemalan Quetzal", isFavorite = false),
                Currency("BOB", "Bolivian Boliviano", "Bs.", 6.9, "Bolivian Boliviano", isFavorite = false),
                Currency("PYG", "Paraguayan Guarani", "₲", 7520.0, "Paraguayan Guarani", isFavorite = false),
                Currency("UYU", "Uruguayan Peso", "\$U", 39.5, "Uruguayan Peso", isFavorite = false),
                Currency("VES", "Venezuelan Bolivar", "Bs.S", 36.4, "Venezuelan Bolivar", isFavorite = false),
                Currency("AOA", "Angolan Kwanza", "Kz", 850.0, "Angolan Kwanza", isFavorite = false),
                Currency("GOLD", "Gold Spot (Ounce)", "⚜️", 0.000435, "Gold price per Troy Ounce", isFavorite = true),
                Currency("GOLD_24K", "Gold Spot 24K (Gram)", "⚜️", 0.01353, "Gold price 24-karat per Gram", isFavorite = false),
                Currency("GOLD_18K", "Gold Spot 18K (Gram)", "⚜️", 0.01805, "Gold price 18-karat per Gram", isFavorite = false),
                Currency("SILVER", "Silver Spot (Ounce)", "🥈", 0.0333, "Silver price per Troy Ounce", isFavorite = false),
                Currency("DIAMOND", "Diamond (Carat)", "💎", 0.00025, "Estimated Diamond price per Carat", isFavorite = false),
                Currency("PLATINUM", "Platinum (Ounce)", "💍", 0.001, "Platinum price per Troy Ounce", isFavorite = false)
            )
            for (curr in defaultSeeds) {
                val existing = currencyRepository.getByCode(curr.code)
                if (existing == null) {
                    currencyRepository.insert(curr)
                }
            }
            onComplete()
        }
    }

    // ── Launcher Icon Switcher (activity-alias technique) ────────────────────
    // NOTE: Changing to an arbitrary gallery image as launcher icon is NOT possible on Android.
    // Android only allows switching between pre-defined icons bundled in the APK via activity-alias.
    // The in-app logo (displayed inside the app) can be any image from gallery.
    // The launcher icon (home screen) can only switch between: DEFAULT, DARK, MINIMAL.
    val launcherIconVariant = MutableStateFlow(sharedPrefs.getString("launcher_icon_variant", "DEFAULT") ?: "DEFAULT")

    fun switchLauncherIcon(variant: String, context: android.content.Context) {
        val pm = context.packageManager
        val pkg = context.packageName

        val allAliases = mapOf(
            "DEFAULT"  to "$pkg.MainActivityIconDefault",
            "DARK"     to "$pkg.MainActivityIconDark",
            "MINIMAL"  to "$pkg.MainActivityIconMinimal"
        )

        // Disable all aliases, then enable the selected one
        allAliases.forEach { (_, componentName) ->
            try {
                pm.setComponentEnabledSetting(
                    android.content.ComponentName(pkg, componentName),
                    android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    android.content.pm.PackageManager.DONT_KILL_APP
                )
            } catch (_: Exception) {}
        }

        if (variant == "ORIGINAL") {
            // Re-enable the main activity directly (original icon from <application> tag)
            try {
                pm.setComponentEnabledSetting(
                    android.content.ComponentName(pkg, "$pkg.MainActivity"),
                    android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                    android.content.pm.PackageManager.DONT_KILL_APP
                )
            } catch (_: Exception) {}
        } else {
            val target = allAliases[variant]
            if (target != null) {
                try {
                    pm.setComponentEnabledSetting(
                        android.content.ComponentName(pkg, target),
                        android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                        android.content.pm.PackageManager.DONT_KILL_APP
                    )
                    // Disable main activity so alias takes over
                    pm.setComponentEnabledSetting(
                        android.content.ComponentName(pkg, "$pkg.MainActivity"),
                        android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                        android.content.pm.PackageManager.DONT_KILL_APP
                    )
                } catch (_: Exception) {}
            }
        }
        launcherIconVariant.value = variant
        sharedPrefs.edit().putString("launcher_icon_variant", variant).apply()
    }
        .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    // Search and fetch online currency options matching a query
    fun searchOnlineCurrencies(
        queryText: String,
        onResult: (List<Currency>) -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val request = okhttp3.Request.Builder()
                    .url("https://cdn.jsdelivr.net/npm/@fawazahmed0/currency-api@latest/v1/currencies.json")
                    .build()
                
                okHttpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw Exception("Network request failed")
                    }
                    val body = response.body?.string() ?: throw Exception("Empty response body")
                    val rootObj = org.json.JSONObject(body)
                    val iterator = rootObj.keys()
                    val results = mutableListOf<Currency>()
                    
                    // Get list of existing currencies to avoid duplicates
                    val existingCodes = currencyRepository.allCurrencies.firstOrNull()?.map { it.code.uppercase() }?.toSet() ?: emptySet()
                    
                    while (iterator.hasNext()) {
                        val key = iterator.next().toString().uppercase(Locale.US)
                        val value = rootObj.getString(key.lowercase(Locale.US))
                        
                        if (key.contains(queryText, ignoreCase = true) || value.contains(queryText, ignoreCase = true)) {
                            // Skip if already in database
                            if (existingCodes.contains(key)) continue
                            
                            val defaultSymbol = when (key) {
                                "USD" -> "$"
                                "EUR" -> "€"
                                "GBP" -> "£"
                                "JPY" -> "¥"
                                "INR" -> "₹"
                                "CAD" -> "C$"
                                "AUD" -> "A$"
                                "RUB" -> "₽"
                                "TRY" -> "₺"
                                "AED" -> "AED"
                                "SAR" -> "ر.س"
                                "RIAL", "IRR" -> "﷼"
                                "TOMAN" -> "T"
                                else -> key
                            }
                            results.add(
                                Currency(
                                    code = key,
                                    name = value,
                                    symbol = defaultSymbol,
                                    value = 1.0,
                                    notes = "Added Online",
                                    isFavorite = false
                                )
                            )
                        }
                    }
                    
                    results.sortBy { it.name }
                    onResult(results)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                fallbackLocalSearch(queryText, onResult)
            }
        }
    }

    private fun fallbackLocalSearch(queryText: String, onResult: (List<Currency>) -> Unit) {
        val localCurrencies = listOf(
            Pair("USD", "US Dollar"), Pair("EUR", "Euro"), Pair("GBP", "Pound Sterling"),
            Pair("JPY", "Japanese Yen"), Pair("CAD", "Canadian Dollar"), Pair("AUD", "Australian Dollar"),
            Pair("CHF", "Swiss Franc"), Pair("CNY", "Chinese Yuan"), Pair("INR", "Indian Rupee"),
            Pair("RUB", "Russian Ruble"), Pair("BRL", "Brazilian Real"), Pair("ZAR", "South African Rand"),
            Pair("TRY", "Turkish Lira"), Pair("AED", "Emirati Dirham"), Pair("SAR", "Saudi Riyal"),
            Pair("KWD", "Kuwaiti Dinar"), Pair("QAR", "Qatari Riyal"), Pair("OMR", "Omani Rial"),
            Pair("BHD", "Bahraini Dinar"), Pair("IQD", "Iraqi Dinar"), Pair("SYP", "Syrian Pound"),
            Pair("LBP", "Lebanese Pound"), Pair("JOD", "Jordanian Dinar"), Pair("EGP", "Egyptian Pound"),
            Pair("YER", "Yemeni Rial"), Pair("KZT", "Kazakhstani Tenge"), Pair("UZS", "Uzbekistani Som"),
            Pair("AZN", "Azerbaijani Manat"), Pair("AMD", "Armenian Dram"), Pair("GEL", "Georgian Lari"),
            Pair("ILS", "Israeli Shekel"), Pair("DZD", "Algerian Dinar"), Pair("MAD", "Moroccan Dirham"),
            Pair("LYD", "Libyan Dinar"), Pair("TND", "Tunisian Dinar"), Pair("SDG", "Sudanese Pound"),
            Pair("TJS", "Tajikistani Somoni"), Pair("AFN", "Afghan Afghani"), Pair("PKR", "Pakistani Rupee"),
            Pair("SGD", "Singapore Dollar"), Pair("NZD", "New Zealand Dollar"), Pair("KRW", "South Korean Won"),
            Pair("MXN", "Mexican Peso"), Pair("SEK", "Swedish Krona"), Pair("NOK", "Norwegian Krone"),
            Pair("DKK", "Danish Krone"), Pair("PLN", "Polish Zloty"), Pair("HKD", "Hong Kong Dollar"),
            Pair("IDR", "Indonesian Rupiah"), Pair("PHP", "Philippine Peso"), Pair("MYR", "Malaysian Ringgit"),
            Pair("THB", "Thai Baht"), Pair("VND", "Vietnamese Dong"), Pair("COP", "Colombian Peso"),
            Pair("ARS", "Argentine Peso"), Pair("CLP", "Chilean Peso"), Pair("PEN", "Peruvian Sol"),
            Pair("HUF", "Hungarian Forint"), Pair("CZK", "Czech Koruna"), Pair("RON", "Romanian Leu"),
            Pair("BGN", "Bulgarian Lev"), Pair("ISK", "Icelandic Krona"), Pair("RSD", "Serbian Dinar"),
            Pair("UAH", "Ukrainian Hryvnia"), Pair("KGS", "Kyrgyzstani Som"), Pair("TMT", "Turkmenistani Manat"),
            Pair("MNT", "Mongolian Tugrik"), Pair("LKR", "Sri Lankan Rupee"), Pair("NPR", "Nepalese Rupee"),
            Pair("BDT", "Bangladeshi Taka"), Pair("MVR", "Maldivian Rufiyaa"), Pair("KHR", "Cambodian Riel"),
            Pair("LAK", "Laotian Kip"), Pair("MMK", "Myanmar Kyat"), Pair("BND", "Brunei Dollar"),
            Pair("GHS", "Ghanaian Cedi"), Pair("NGN", "Nigerian Naira"), Pair("KES", "Kenyan Shilling"),
            Pair("UGX", "Ugandan Shilling"), Pair("TZS", "Tanzanian Shilling"), Pair("RWF", "Rwandan Franc"),
            Pair("ETB", "Ethiopian Birr"), Pair("JMD", "Jamaican Dollar"), Pair("DOP", "Dominican Peso"),
            Pair("CRC", "Costa Rican Colon"), Pair("PAB", "Panamanian Balboa"), Pair("HNL", "Honduran Lempira"),
            Pair("NIO", "Nicaraguan Cordoba"), Pair("GTQ", "Guatemalan Quetzal"), Pair("BOB", "Bolivian Boliviano"),
            Pair("PYG", "Paraguayan Guarani"), Pair("UYU", "Uruguayan Peso"), Pair("VES", "Venezuelan Bolivar"),
            Pair("AOA", "Angolan Kwanza")
        )
        
        val results = localCurrencies.filter { (code, name) ->
            code.contains(queryText, ignoreCase = true) || name.contains(queryText, ignoreCase = true)
        }.map { (code, name) ->
            val defaultSymbol = when (code) {
                "USD" -> "$"
                "EUR" -> "€"
                "GBP" -> "£"
                "JPY" -> "¥"
                "INR" -> "₹"
                "CAD" -> "C$"
                "AUD" -> "A$"
                "RUB" -> "₽"
                "TRY" -> "₺"
                "AED" -> "AED"
                "SAR" -> "ر.س"
                "RIAL", "IRR" -> "﷼"
                "TOMAN" -> "T"
                else -> code
            }
            Currency(
                code = code,
                name = name,
                symbol = defaultSymbol,
                value = 1.0,
                notes = "Added Offline Fallback",
                isFavorite = false
            )
        }
        onResult(results)
    }

    fun addOnlineCurrency(
        currency: Currency,
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit
    ) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val request = okhttp3.Request.Builder()
                    .url("https://cdn.jsdelivr.net/npm/@fawazahmed0/currency-api@latest/v1/currencies/usd.json")
                    .build()
                    
                var fetchedRate = 1.0
                var success = false
                
                try {
                    okHttpClient.newCall(request).execute().use { response ->
                        if (response.isSuccessful) {
                            val body = response.body?.string()
                            if (!body.isNullOrEmpty()) {
                                val rootObj = org.json.JSONObject(body)
                                if (rootObj.has("usd")) {
                                    val usdRates = rootObj.getJSONObject("usd")
                                    val apiCode = currency.code.lowercase(Locale.US)
                                    if (usdRates.has(apiCode)) {
                                        fetchedRate = usdRates.getDouble(apiCode)
                                        success = true
                                    }
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                
                if (!success) {
                    try {
                        val fallbackRequest = okhttp3.Request.Builder()
                            .url("https://open.er-api.com/v6/latest/USD")
                            .build()
                        okHttpClient.newCall(fallbackRequest).execute().use { response ->
                            if (response.isSuccessful) {
                                val body = response.body?.string()
                                if (!body.isNullOrEmpty()) {
                                    val rootObj = org.json.JSONObject(body)
                                    if (rootObj.has("rates")) {
                                        val rates = rootObj.getJSONObject("rates")
                                        val apiCode = currency.code.uppercase(Locale.US)
                                        if (rates.has(apiCode)) {
                                            fetchedRate = rates.getDouble(apiCode)
                                            success = true
                                        }
                                    }
                                }
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
                
                val finalRate = if (success) {
                    when (currency.code.uppercase(Locale.US)) {
                        "TOMAN" -> fetchedRate / 10.0  // API gives IRR, TOMAN = IRR / 10
                        "RIAL" -> fetchedRate           // API gives IRR directly
                        else -> fetchedRate
                    }
                } else {
                    1.0
                }
                
                val updatedCurrency = currency.copy(value = finalRate)
                currencyRepository.insert(updatedCurrency)
                
                triggerOnlineRatesUpdate()
                onSuccess()
            } catch (e: Exception) {
                e.printStackTrace()
                onFailure(e.localizedMessage ?: "Unknown Error")
            }
        }
    }

    // Factory Provider
    companion object {
        fun provideFactory(application: Application): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    val container = (application as SmartCurrencyApplication).container
                    return CurrencyViewModel(
                        application,
                        container.currencyRepository,
                        container.historyRepository
                    ) as T
                }
            }
    }
}
