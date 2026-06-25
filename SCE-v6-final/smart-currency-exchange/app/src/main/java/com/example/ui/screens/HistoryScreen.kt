package com.example.ui.screens

import java.text.SimpleDateFormat
import java.util.*
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.ConversionHistory
import com.example.ui.theme.MintPrimary
import com.example.ui.viewmodel.CurrencyViewModel
import com.example.ui.util.Localization

@Composable
fun HistoryScreen(
    viewModel: CurrencyViewModel,
    modifier: Modifier = Modifier
) {
    val history by viewModel.historyState.collectAsState()
    val favoriteCurrencies by viewModel.favoriteCurrenciesState.collectAsState()
    val displayUnit by viewModel.displayCurrencyUnit.collectAsState()
    val languageCode by viewModel.languageCode.collectAsState()

    val favoriteCodes = remember(favoriteCurrencies) { 
        favoriteCurrencies.map { it.code.uppercase() }.toSet() 
    }

    var selectedTab by remember { mutableStateOf(0) } // 0 = Offline Mode, 1 = Online Mode
    val filteredHistory = remember(history, selectedTab) {
        history.filter { it.isOnline == (selectedTab == 1) }
    }

    // Helper formatter for time and date
    val dateFormatter = remember { SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()) }

    fun formatDate(timestamp: Long): String {
        return dateFormatter.format(Date(timestamp))
    }

    fun formatClean(value: Double, code: String): String {
        if (value == 0.0) return "0"
        val isCrypto = code.uppercase() in listOf("BTC", "ETH", "SOL", "BNB", "ADA", "DOGE", "XRP")
        val useHighPrecision = isCrypto || (Math.abs(value) < 0.01 && Math.abs(value) > 0.0)
        
        val formattedStr = if (useHighPrecision) {
            val str = String.format(Locale.US, "%.12f", value)
            if (str.contains(".")) {
                val trimmed = str.trimEnd('0')
                if (trimmed.endsWith(".")) {
                    trimmed.dropLast(1)
                } else {
                    trimmed
                }
            } else {
                str
            }
        } else {
            if (value % 1.0 == 0.0) {
                String.format(Locale.US, "%,.0f", value)
            } else {
                val str = String.format(Locale.US, "%,.2f", value)
                if (str.contains(".")) {
                    val trimmed = str.trimEnd('0')
                    if (trimmed.endsWith(".")) {
                        trimmed.dropLast(1)
                    } else {
                        trimmed
                    }
                } else {
                    str
                }
            }
        }
        
        if (formattedStr.contains("E", ignoreCase = true)) {
            try {
                return java.math.BigDecimal.valueOf(value).stripTrailingZeros().toPlainString()
            } catch (e: Exception) {
                // fallback
            }
        }
        return formattedStr
    }

    fun formatResultValue(valDouble: Double, destCode: String): String {
        return if (displayUnit == "RIAL" && destCode.uppercase() == "TOMAN") {
            // Apply Toman-Rial multiplier
            formatClean(valDouble * 10, "RIAL") + " RIAL"
        } else {
            formatClean(valDouble, destCode) + " " + destCode
        }
    }

    Scaffold(
        modifier = modifier
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header Content
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = Localization.get("hist_header", languageCode),
                            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = Localization.get("hist_desc", languageCode),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                        )
                    }

                    if (filteredHistory.isNotEmpty()) {
                        Button(
                            onClick = { viewModel.clearHistoryByMode(isOnline = (selectedTab == 1)) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer,
                                contentColor = MaterialTheme.colorScheme.onErrorContainer
                            ),
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeleteSweep,
                                contentDescription = Localization.get("hist_clear_btn", languageCode),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = Localization.get("clear_btn", languageCode),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // Mode Selection Section (Offline Mode History vs Online Mode History)
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .background(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            shape = RoundedCornerShape(14.dp)
                        )
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val offlineText = if (languageCode == "fa") "تاریخچه موتور آفلاین" else "Offline Engine History"
                    val onlineText = if (languageCode == "fa") "تاریخچه بخش آنلاین" else "Online Mode History"

                    Button(
                        onClick = { selectedTab = 0 },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (selectedTab == 0) MaterialTheme.colorScheme.primary else androidx.compose.ui.graphics.Color.Transparent,
                            contentColor = if (selectedTab == 0) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(vertical = 8.dp),
                        elevation = if (selectedTab == 0) ButtonDefaults.buttonElevation(defaultElevation = 2.dp) else null
                    ) {
                        Icon(
                            imageVector = Icons.Default.CloudOff,
                            contentDescription = offlineText,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = offlineText,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            textAlign = TextAlign.Center
                        )
                    }

                    Button(
                        onClick = { selectedTab = 1 },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (selectedTab == 1) MaterialTheme.colorScheme.primary else androidx.compose.ui.graphics.Color.Transparent,
                            contentColor = if (selectedTab == 1) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(vertical = 8.dp),
                        elevation = if (selectedTab == 1) ButtonDefaults.buttonElevation(defaultElevation = 2.dp) else null
                    ) {
                        Icon(
                            imageVector = Icons.Default.Cloud,
                            contentDescription = onlineText,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = onlineText,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            if (filteredHistory.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 48.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.History,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                            modifier = Modifier.size(64.dp)
                        )
                        Text(
                            text = Localization.get("history_empty_title", languageCode),
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                        Text(
                            text = Localization.get("history_empty_desc", languageCode),
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                    }
                }
            } else {
                items(filteredHistory, key = { it.id }) { item ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = formatDate(item.timestamp),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                )

                                IconButton(
                                    onClick = { viewModel.deleteHistoryItem(item.id) },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Delete Single Item",
                                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.6f),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.Bottom
                            ) {
                                Column {
                                    Text(
                                        text = "${formatClean(item.amount, item.sourceCode)} ${item.sourceCode}",
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "${Localization.get("from_label", languageCode)}: ${Localization.getCurrencyName(item.sourceCode, item.sourceName, languageCode)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                    )
                                }

                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = formatResultValue(item.result, item.destinationCode),
                                        fontWeight = FontWeight.ExtraBold,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MintPrimary
                                    )
                                    Text(
                                        text = "${Localization.get("to_label", languageCode)}: ${Localization.getCurrencyName(item.destinationCode, item.destinationName, languageCode)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Bottom spacing
            item { Box(modifier = Modifier.height(32.dp)) }
        }
    }
}
