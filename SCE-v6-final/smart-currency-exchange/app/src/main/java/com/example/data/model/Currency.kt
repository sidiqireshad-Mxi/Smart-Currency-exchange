package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Index

@Entity(
    tableName = "currencies",
    indices = [
        Index(value = ["isFavorite"]),
        Index(value = ["name"])
    ]
)
data class Currency(
    @PrimaryKey val code: String, // e.g. "USD", must be uppercase and unique
    val name: String,             // e.g. "US Dollar"
    val symbol: String,           // e.g. "$"
    val value: Double,            // Exchange value relative to the base reference
    val notes: String = "",       // Optional custom notes
    val isFavorite: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val offlineValue: Double? = null,
    val rateValue: Double? = null,
    val baseCurrencyCode: String? = null,
    val exchangeRatesJson: String? = null
)

data class ExchangeRate(
    val referenceCurrencyCode: String,
    val referenceAmount: Double,
    val currentAmount: Double
)

fun parseExchangeRates(json: String?): List<ExchangeRate> {
    if (json.isNullOrBlank()) return emptyList()
    val list = mutableListOf<ExchangeRate>()
    try {
        val parts = json.split(";")
        for (part in parts) {
            val subparts = part.split("|")
            if (subparts.size == 3) {
                val refCode = subparts[0].trim().uppercase()
                val refAmt = subparts[1].trim().toDoubleOrNull() ?: 1.0
                val curAmt = subparts[2].trim().toDoubleOrNull() ?: 0.0
                if (refCode.isNotEmpty() && curAmt > 0.0) {
                    list.add(ExchangeRate(refCode, refAmt, curAmt))
                }
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
    return list
}

fun formatExchangeRates(rates: List<ExchangeRate>): String {
    return rates.filter { it.referenceCurrencyCode.isNotBlank() && it.currentAmount > 0.0 }
        .joinToString(";") { "${it.referenceCurrencyCode.trim().uppercase()}|${it.referenceAmount}|${it.currentAmount}" }
}

fun validateExchangeRates(ownCode: String, rates: List<ExchangeRate>, languageCode: String = "fa"): String? {
    if (rates.isEmpty()) {
        return if (languageCode == "fa") "لطفاً حداقل یک نرخ مبادلاتی معتبر وارد کنید" else "Please enter at least one exchange rate."
    }
    val seenRefs = mutableSetOf<String>()
    val cleanOwn = ownCode.uppercase().trim()
    for (rate in rates) {
        val refCode = rate.referenceCurrencyCode.uppercase().trim()
        if (refCode.isBlank()) {
            return if (languageCode == "fa") "کد ارز مرجع نمی‌تواند خالی باشد" else "Reference currency code cannot be empty."
        }
        if (refCode == cleanOwn) {
            return if (languageCode == "fa") "ارز مرجع نمی‌تواند با کد خود ارز یکسان باشد (${cleanOwn})" else "Reference currency cannot match the own currency code (${cleanOwn})."
        }
        if (rate.referenceAmount <= 0.0) {
            return if (languageCode == "fa") "مقدار مرجع باید بزرگتر از صفر باشد" else "Reference amount must be greater than zero."
        }
        if (rate.currentAmount <= 0.0) {
            return if (languageCode == "fa") "مقدار فعلی باید بزرگتر از صفر باشد" else "Current amount must be greater than zero."
        }
        if (refCode in seenRefs) {
            return if (languageCode == "fa") "نرخ مبادلاتی تکراری برای $refCode مجاز نیست" else "Duplicate exchange rate for $refCode is not allowed."
        }
        seenRefs.add(refCode)
    }
    return null
}

fun Currency.getExchangeRates(): List<ExchangeRate> {
    val parsed = parseExchangeRates(exchangeRatesJson)
    if (parsed.isNotEmpty()) {
        return parsed
    }
    // Fallback to legacy single rate structures
    val legacyBase = baseCurrencyCode ?: "USD"
    val legacyRate = rateValue ?: offlineValueOrValue
    return listOf(ExchangeRate(legacyBase, 1.0, legacyRate))
}

data class ConversionEdge(
    val toCode: String,
    val multiplier: Double
)

fun findConversionMultiplier(
    fromCode: String,
    toCode: String,
    allCurrencies: List<Currency>
): Double? {
    val fromUpper = fromCode.uppercase().trim()
    val toUpper = toCode.uppercase().trim()
    if (fromUpper == toUpper) return 1.0

    // Build adjacency list representing the graph dynamically from exchange rates.
    val adj = mutableMapOf<String, MutableMap<String, Double>>()

    for (currency in allCurrencies) {
        val u = currency.code.uppercase().trim()
        if (u.isBlank()) continue

        // Build graph edges from Exchange Rates List, fully compliant with strict storage rule.
        val rates = currency.getExchangeRates()
        for (rate in rates) {
            val v = rate.referenceCurrencyCode.uppercase().trim()
            if (u == v || v.isBlank() || rate.currentAmount <= 0.0 || rate.referenceAmount <= 0.0) {
                continue
            }

            // Relationship: currentAmount * u = referenceAmount * v
            // 1 unit of u = (referenceAmount / currentAmount) units of v
            val multForward = rate.referenceAmount / rate.currentAmount
            // 1 unit of v = (currentAmount / referenceAmount) units of u
            val multBackward = rate.currentAmount / rate.referenceAmount

            adj.getOrPut(u) { mutableMapOf() }[v] = multForward
            adj.getOrPut(v) { mutableMapOf() }[u] = multBackward
        }
    }

    // 1. Direct Conversion Check
    val startNeighbors = adj[fromUpper]
    if (startNeighbors != null) {
        val directRate = startNeighbors[toUpper]
        if (directRate != null) {
            return directRate
        }
    }

    // 2. BFS Shortest Path Traversal
    val queue = java.util.ArrayDeque<Pair<String, Double>>()
    val visited = mutableSetOf<String>()

    queue.add(fromUpper to 1.0)
    visited.add(fromUpper)

    while (queue.isNotEmpty()) {
        val (current, currentMultiplier) = queue.poll()
        if (current == toUpper) {
            return currentMultiplier
        }

        val neighbors = adj[current] ?: continue
        for ((neighborCode, multiplier) in neighbors) {
            if (neighborCode !in visited) {
                visited.add(neighborCode)
                queue.add(neighborCode to (currentMultiplier * multiplier))
            }
        }
    }

    return null
}

fun calculateRepresentativeValue(
    code: String,
    rates: List<ExchangeRate>,
    allCurrencies: List<Currency>
): Double {
    val cleanCode = code.uppercase().trim()
    val rawRatesJson = formatExchangeRates(rates)
    val tempCur = Currency(
        code = cleanCode,
        name = "",
        symbol = "",
        value = 1.0,
        exchangeRatesJson = rawRatesJson
    )
    val tempAll = allCurrencies.filter { it.code != cleanCode } + tempCur
    val usdToCur = findConversionMultiplier("USD", cleanCode, tempAll)
    if (usdToCur != null && usdToCur > 0.0) {
        return usdToCur
    }
    val firstRate = rates.firstOrNull() ?: return 1.0
    val refToUsd = findConversionMultiplier(firstRate.referenceCurrencyCode, "USD", allCurrencies) ?: 1.0
    val ratio = firstRate.currentAmount / firstRate.referenceAmount
    return if (refToUsd > 0.0) (ratio / refToUsd) else ratio
}

val Currency.offlineValueOrValue: Double
    get() = offlineValue ?: value

fun Currency.resolveUsdRate(allCurrencies: List<Currency>, visited: Set<String> = emptySet()): Double {
    if (code == "USD") return 1.0
    val baseCode = baseCurrencyCode ?: "USD"
    if (baseCode == "USD") {
        return rateValue ?: offlineValueOrValue
    }
    if (visited.contains(code)) {
        return offlineValueOrValue
    }
    val baseCur = allCurrencies.find { it.code == baseCode }
    if (baseCur == null) {
        return offlineValueOrValue
    }
    val parentRate = baseCur.resolveUsdRate(allCurrencies, visited + code)
    val rVal = rateValue ?: offlineValueOrValue
    return parentRate * rVal
}

val Currency.isCrypto: Boolean
    get() {
        val c = code.uppercase(java.util.Locale.US).trim()
        val n = name.uppercase(java.util.Locale.US).trim()
        val cryptoSet = setOf(
            "BTC", "TON", "ETH", "SOL", "BNB", "ADA", "DOGE", "XRP", "USDT", "USDC", "LTC", "LINK", "DOT", "POL", "SHIB", "AVAX",
            "TRX", "NEAR", "UNI", "ATOM", "ALGO", "FTM", "OP", "ARB", "LDO", "ICP", "VET", "FIL", "ETC", "HBAR", "GRT", "RNDR",
            "THETA", "MKR", "STX", "EGLD", "RUNE", "INJ", "SUI", "SEI", "APT", "TIA", "MINA", "IMX", "GALA", "AAVE", "FLOW", "SAND", "MANA", "AXS", "EOS", "XTZ", "IOTA", "BCH", "XLM", "XMR", "QNT", "MKR", "LRC", "BAT", "WAVES", "ZEC", "DASH", "OMG", "ZIL", "ENJ", "KNC", "CRV", "COMP", "SNX", "YFI", "CAKE", "RPL", "WOO", "CHZ", "HOT", "ONE", "ANKR", "CELO", "NANO", "RVN", "XEC", "TUSD", "USDP", "FDUSD", "BUSD", "GUSD", "PYUSD"
        )
        return cryptoSet.contains(c) || n.contains("COIN") || n.contains("TOKEN") || n.contains("CRYPTOCURRENCY")
    }

val Currency.isCommodity: Boolean
    get() {
        val c = code.uppercase(java.util.Locale.US).trim()
        val n = name.uppercase(java.util.Locale.US).trim()
        val commoditySet = setOf(
            "GOLD", "SILVER", "DIAMOND", "PLATINUM", "GOLD_24K", "GOLD_18K", "BRONZE", "COPPER", "PALLADIUM", 
            "XAU", "XAG", "XPT", "XPD"
        )
        return commoditySet.contains(c) || n.contains("GOLD") || n.contains("SILVER") || n.contains("PLATINUM") || n.contains("DIAMOND")
    }
