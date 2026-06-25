package com.example.data.model

import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

data class CustomApi(
    val id: String,
    val name: String,
    val url: String,
    val isEnabled: Boolean = true,
    val priority: String = "PRIMARY", // "PRIMARY", "SECONDARY", "FALLBACK"
    val marketType: String = "GLOBAL", // "GLOBAL" (World Market) or "FREE" (Free Market)
    val apiCategory: String = "FIAT"  // "FIAT", "CRYPTO", "PRECIOUS"
)

object CustomApiManager {
    fun loadCustomApis(sharedPrefs: SharedPreferences): List<CustomApi> {
        val jsonString = sharedPrefs.getString("custom_api_sources_json", null)
        if (jsonString.isNullOrEmpty()) {
            val defaults = listOf(
                CustomApi("1", "Open Exchange Rates", "https://open.er-api.com/v6/latest/USD", true, "PRIMARY", "GLOBAL", "FIAT"),
                CustomApi("2", "Currency API Pages", "https://latest.currency-api.pages.dev/v1/currencies/usd.json", true, "SECONDARY", "GLOBAL", "FIAT"),
                CustomApi("3", "Fawaz Ahmed CDN", "https://cdn.jsdelivr.net/npm/@fawazahmed0/currency-api@latest/v1/currencies/usd.json", true, "FALLBACK", "GLOBAL", "FIAT"),
                CustomApi("4", "CoinGecko Crypto", "https://api.coingecko.com/api/v3/exchange_rates", true, "PRIMARY", "GLOBAL", "CRYPTO"),
                CustomApi("5", "Metals Live", "https://api.metals.live/v1/spot", true, "PRIMARY", "GLOBAL", "PRECIOUS"),
                CustomApi("6", "BrsApi Iran Free Market", "https://brsapi.ir/Api/Market/Gold_Currency.php", true, "PRIMARY", "FREE", "FIAT")
            )
            saveCustomApis(sharedPrefs, defaults)
            return defaults
        }
        val list = mutableListOf<CustomApi>()
        try {
            val array = JSONArray(jsonString)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(
                    CustomApi(
                        id = obj.getString("id"),
                        name = obj.getString("name"),
                        url = obj.getString("url"),
                        isEnabled = obj.getBoolean("isEnabled"),
                        priority = obj.getString("priority"),
                        marketType = obj.optString("marketType", "GLOBAL"),
                        apiCategory = obj.optString("apiCategory", "FIAT")
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return listOf(
                CustomApi("1", "Open Exchange Rates", "https://open.er-api.com/v6/latest/USD", true, "PRIMARY", "GLOBAL", "FIAT")
            )
        }
        return list
    }

    fun saveCustomApis(sharedPrefs: SharedPreferences, apis: List<CustomApi>) {
        try {
            val array = JSONArray()
            for (api in apis) {
                val obj = JSONObject()
                obj.put("id", api.id)
                obj.put("name", api.name)
                obj.put("url", api.url)
                obj.put("isEnabled", api.isEnabled)
                obj.put("priority", api.priority)
                obj.put("marketType", api.marketType)
                obj.put("apiCategory", api.apiCategory)
                array.put(obj)
            }
            sharedPrefs.edit().putString("custom_api_sources_json", array.toString()).apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
