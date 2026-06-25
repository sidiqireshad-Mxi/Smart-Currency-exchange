package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.Currency
import com.example.data.model.isCrypto
import com.example.data.model.isCommodity
import com.example.ui.theme.MintPrimary
import com.example.ui.theme.StarGold
import com.example.ui.theme.Slate800
import com.example.ui.util.Localization
import com.example.ui.viewmodel.CurrencyViewModel
import com.example.ui.viewmodel.SortOption
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.draw.alpha
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalAnimationApi::class, ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun OnlineModeScreen(
    viewModel: CurrencyViewModel,
    modifier: Modifier = Modifier
) {
    val currencies by viewModel.currenciesState.collectAsState()
    val isOnlineUpdating by viewModel.isOnlineUpdating.collectAsState()
    val languageCode by viewModel.languageCode.collectAsState()
    val baseCurrencyCode by viewModel.baseCurrencyCode.collectAsState()
    val previousRates by viewModel.previousRates.collectAsState()
    val onlineUpdateSuccessMessage by viewModel.onlineUpdateSuccessMessage.collectAsState()
    val activeSort by viewModel.selectedSort.collectAsState()
    val amountInput by viewModel.amountInput.collectAsState()

    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    val keyboardController = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current

    var showCalculator by remember { mutableStateOf(false) }
    var calcExpression by remember { mutableStateOf("") }
    var calcResult by remember { mutableStateOf("") }
    var calcErrorMsg by remember { mutableStateOf("") }
    var isCalculatorPopUp by remember { mutableStateOf(false) }
    var calculatorSizeState by remember { mutableStateOf("medium") } // "small", "medium", "large"

    var selectedCategory by remember { mutableStateOf("all") }
    var queryText by remember { mutableStateOf("") }
    var secondsRemaining by remember { mutableStateOf(60) }
    var isFavoriteEditMode by remember { mutableStateOf(false) }
    var showAddOnlineDialog by remember { mutableStateOf(false) }

    val cryptoCodes = remember { setOf("BTC", "TON", "ETH", "SOL", "BNB", "ADA", "DOGE", "XRP", "USDT", "USDC", "LTC", "LINK", "DOT", "POL", "SHIB", "AVAX", "TRX", "ATOM", "UNI", "FIL", "NEAR", "APT", "SUI", "XLM", "OP", "ARB", "PEPE", "WIF") }
    val commodityCodes = remember { setOf("GOLD", "SILVER", "DIAMOND", "PLATINUM", "PALLADIUM", "COPPER", "GOLD_24K", "GOLD_18K") }

    val currentMarketTab by viewModel.selectedMarketTab.collectAsState()
    val freeMarketCodes = remember { setOf("TOMAN", "RIAL", "USD", "EUR", "GBP", "AFN", "AED", "SAR", "TRY", "GOLD", "GOLD_24K", "GOLD_18K", "USDT", "BTC", "ETH") }
    
    val displayCurrencies = remember(currencies, currentMarketTab) {
        if (currentMarketTab == "FREE") {
            currencies.filter { freeMarketCodes.contains(it.code.uppercase().trim()) }
        } else {
            currencies
        }
    }

    // Use ViewModel's persistent auto-refresh (survives tab/screen changes)
    LaunchedEffect(Unit) {
        viewModel.startOnlineAutoRefresh()
    }
    // Sync display countdown with ViewModel
    val vmCountdown by viewModel.onlineCountdownSeconds.collectAsState()
    LaunchedEffect(vmCountdown) {
        secondsRemaining = vmCountdown
    }

    val lastSyncTimestamp by remember(currentMarketTab, onlineUpdateSuccessMessage) {
        val key = if (currentMarketTab == "GLOBAL") "last_sync_time_global" else "last_sync_time_free"
        val cached = viewModel.sharedPrefs.getString(key, "") ?: ""
        mutableStateOf(cached)
    }

    val baseCurrency = remember(currencies, baseCurrencyCode) {
        currencies.find { it.code == baseCurrencyCode } 
            ?: Currency("USD", "US Dollar", "$", 1.0, "Base", isFavorite = true)
    }

    // Beautiful pulse animation for live green indicator
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val livePulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Live Feed Status Top Header Card
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(
                                            Color(0xFF2ECC71).copy(alpha = livePulseAlpha)
                                        )
                                )
                                Text(
                                    text = Localization.get("status_connected", languageCode),
                                    style = MaterialTheme.typography.labelLarge.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF2ECC71)
                                    )
                                )
                            }
                            Text(
                                text = "${Localization.get("auto_update_notice", languageCode)} (${secondsRemaining}s)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                            if (lastSyncTimestamp.isNotEmpty()) {
                                Text(
                                    text = "${Localization.get("last_sync_time", languageCode)}$lastSyncTimestamp",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                                )
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(
                                    progress = secondsRemaining / 60f,
                                    modifier = Modifier.size(36.dp),
                                    strokeWidth = 3.dp,
                                    color = MintPrimary,
                                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                                )
                                Text(
                                    text = secondsRemaining.toString(),
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }

                            IconButton(
                                onClick = { 
                                    viewModel.triggerOnlineRatesUpdate()
                                    viewModel.resetOnlineCountdown()
                                },
                                enabled = !isOnlineUpdating,
                                colors = IconButtonDefaults.iconButtonColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                ),
                                modifier = Modifier.size(40.dp)
                            ) {
                                if (isOnlineUpdating) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.Refresh,
                                        contentDescription = "Sync Now",
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Market Mode Tab Switcher (Free vs Global)
            item {
                TabRow(
                    selectedTabIndex = if (currentMarketTab == "GLOBAL") 0 else 1,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clip(RoundedCornerShape(12.dp)),
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                ) {
                    Tab(
                        selected = currentMarketTab == "GLOBAL",
                        onClick = { 
                            viewModel.selectMarketTab("GLOBAL")
                            viewModel.triggerOnlineRatesUpdate()
                            viewModel.resetOnlineCountdown()
                        },
                        text = {
                            Text(
                                text = Localization.get("market_global", languageCode),
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                            )
                        },
                        modifier = Modifier.testTag("tab_global_market")
                    )
                    Tab(
                        selected = currentMarketTab == "FREE",
                        onClick = { 
                            viewModel.selectMarketTab("FREE")
                            viewModel.triggerOnlineRatesUpdate()
                            viewModel.resetOnlineCountdown()
                        },
                        text = {
                            Text(
                                text = Localization.get("market_free", languageCode),
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                            )
                        },
                        modifier = Modifier.testTag("tab_free_market")
                    )
                }
            }

            // Real-Time Conversion Input Field Card
            stickyHeader {
                Surface(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    color = MaterialTheme.colorScheme.background
                ) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                        border = CardDefaults.outlinedCardBorder()
                    ) {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(46.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = baseCurrency.symbol.ifEmpty { baseCurrency.code },
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.titleLarge,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    Column {
                                        Text(
                                            text = Localization.getCurrencyName(baseCurrency.code, baseCurrency.name, languageCode),
                                            fontWeight = FontWeight.ExtraBold,
                                            style = MaterialTheme.typography.titleMedium,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                            Text(
                                                text = baseCurrency.code,
                                                fontWeight = FontWeight.Bold,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                            Box(
                                                modifier = Modifier
                                                    .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                                            ) {
                                                Text(
                                                    text = "ACTIVE BASE REFERENCE",
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.ExtraBold,
                                                    color = MaterialTheme.colorScheme.secondary
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            // Large intuitive textfield
                            val inputPlaceholder = Localization.get("input_placeholder", languageCode)
                            val inputLabel = Localization.get("input_label", languageCode)

                            OutlinedTextField(
                                value = amountInput,
                                onValueChange = { viewModel.updateAmountInput(it) },
                                placeholder = { Text(inputPlaceholder) },
                                label = { Text(inputLabel) },
                                singleLine = true,
                                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                    keyboardType = androidx.compose.ui.text.input.KeyboardType.Number,
                                    imeAction = androidx.compose.ui.text.input.ImeAction.Done
                                ),
                                keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                                    onDone = {
                                        viewModel.saveCurrentConversionToHistory(isOnline = true)
                                        keyboardController?.hide()
                                        focusManager.clearFocus()
                                    }
                                ),
                                leadingIcon = {
                                    Text(
                                        text = baseCurrency.symbol.ifEmpty { baseCurrency.code },
                                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(start = 12.dp, end = 4.dp)
                                    )
                                },
                                trailingIcon = {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        modifier = Modifier.padding(end = 4.dp)
                                    ) {
                                        if (amountInput.isNotEmpty()) {
                                            IconButton(
                                                onClick = { viewModel.updateAmountInput("") },
                                                modifier = Modifier.size(36.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Clear,
                                                    contentDescription = "Clear Input",
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                        }
                                        IconButton(
                                            onClick = { showCalculator = !showCalculator },
                                            modifier = Modifier.size(36.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Calculate,
                                                contentDescription = "Toggle Calculator Panel",
                                                tint = if (showCalculator) MintPrimary else MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            )
                        }
                    }
                }
            }

            // Expanded interactive Pocket Calculator Grid (Inline Mode in Online Screen)
            if (showCalculator && !isCalculatorPopUp) {
                item {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .animateContentSize(),
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        border = CardDefaults.outlinedCardBorder()
                    ) {
                        CalculatorContent(
                            languageCode = languageCode,
                            calcExpression = calcExpression,
                            onExpressionChange = { calcExpression = it },
                            calcResult = calcResult,
                            onResultChange = { calcResult = it },
                            calcErrorMsg = calcErrorMsg,
                            onErrorChange = { calcErrorMsg = it },
                            isCalculatorPopUp = false,
                            onPopUpToggle = { isCalculatorPopUp = !isCalculatorPopUp },
                            calculatorSizeState = calculatorSizeState,
                            onSizeToggle = {
                                calculatorSizeState = when (calculatorSizeState) {
                                    "small" -> "medium"
                                    "medium" -> "large"
                                    else -> "small"
                                }
                            },
                            onClose = { showCalculator = false },
                            onPasteResult = { res ->
                                viewModel.updateAmountInput(res)
                                viewModel.saveCurrentConversionToHistory(isOnline = true)
                                keyboardController?.hide()
                                focusManager.clearFocus()
                                showCalculator = false
                            }
                        )
                    }
                }
            }

            if (showCalculator && isCalculatorPopUp) {
                item {
                    androidx.compose.ui.window.Dialog(
                        onDismissRequest = { showCalculator = false },
                        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
                    ) {
                        CalculatorContent(
                            languageCode = languageCode,
                            calcExpression = calcExpression,
                            onExpressionChange = { calcExpression = it },
                            calcResult = calcResult,
                            onResultChange = { calcResult = it },
                            calcErrorMsg = calcErrorMsg,
                            onErrorChange = { calcErrorMsg = it },
                            isCalculatorPopUp = true,
                            onPopUpToggle = { isCalculatorPopUp = !isCalculatorPopUp },
                            calculatorSizeState = calculatorSizeState,
                            onSizeToggle = {
                                calculatorSizeState = when (calculatorSizeState) {
                                    "small" -> "medium"
                                    "medium" -> "large"
                                    else -> "small"
                                }
                            },
                            onClose = { showCalculator = false },
                            onPasteResult = { res ->
                                viewModel.updateAmountInput(res)
                                viewModel.saveCurrentConversionToHistory(isOnline = true)
                                keyboardController?.hide()
                                focusManager.clearFocus()
                                showCalculator = false
                            }
                        )
                    }
                }
            }

            // Search box
            item {
                val searchHint = Localization.get("search_hint", languageCode)
                OutlinedTextField(
                    value = queryText,
                    onValueChange = {
                        queryText = it
                        viewModel.searchQuery.value = it
                    },
                    placeholder = { Text(searchHint) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (queryText.isNotEmpty()) {
                            IconButton(onClick = {
                                queryText = ""
                                viewModel.searchQuery.value = ""
                            }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear Search")
                            }
                        }
                    },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    shape = RoundedCornerShape(12.dp)
                )
            }

            // Sort & Category Group (Brought closer together)
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp),
                    verticalArrangement = Arrangement.spacedBy((-8).dp)
                ) {
                    // Sort Chips Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val sortByText = Localization.get("sort_by", languageCode)
                        Text(text = sortByText, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.width(4.dp))
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            SortOption.values().forEach { sortOption ->
                                val localKey = when(sortOption) {
                                    SortOption.FAVORITE_FIRST -> "sort_fav_first"
                                    SortOption.HIGHEST_VALUE -> "sort_high_val"
                                    SortOption.LOWEST_VALUE -> "sort_low_val"
                                    SortOption.ALPHABETICAL_AZ -> "sort_alpha_az"
                                    SortOption.ALPHABETICAL_ZA -> "sort_alpha_za"
                                }
                                val labelText = Localization.get(localKey, languageCode)
                                FilterChip(
                                    selected = activeSort == sortOption,
                                    onClick = { viewModel.selectedSort.value = sortOption },
                                    label = { Text(labelText, fontSize = 10.sp) }
                                )
                            }
                        }
                    }

                    // Category Filter Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val categoryLabelText = Localization.get("category_label", languageCode)
                        Text(
                            text = categoryLabelText,
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            val categories = listOf(
                                Triple("all", "cat_all", "cat_all"),
                                Triple("fiat", "cat_fiat", "cat_fiat"),
                                Triple("crypto", "cat_crypto", "cat_crypto"),
                                Triple("commodity", "cat_commodity", "cat_commodity")
                            )
                            categories.forEach { (catId, _, _) ->
                                val labelText = Localization.get(catId, languageCode)
                                val isSelected = selectedCategory == catId
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { selectedCategory = catId },
                                    label = { Text(labelText, fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                )
                            }
                        }
                    }
                }
            }

            if (displayCurrencies.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 48.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.SearchOff,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                            modifier = Modifier.size(64.dp)
                        )
                        val noMatchText = Localization.get("no_match_text", languageCode)
                        Text(
                            text = noMatchText,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                    }
                }
            } else {
                if (activeSort == SortOption.FAVORITE_FIRST) {
                    val pinnedFavorites = displayCurrencies.filter { it.isFavorite }
                    val standardCurrencies = displayCurrencies.filter { !it.isFavorite }

                    if (pinnedFavorites.isNotEmpty()) {
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Star,
                                    contentDescription = null,
                                    tint = StarGold,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = Localization.get("pinned_favorites", languageCode),
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.ExtraBold),
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.weight(1f))
                                val reorderBtnText = if (isFavoriteEditMode) {
                                    Localization.get("done", languageCode)
                                } else {
                                    Localization.get("edit", languageCode)
                                }
                                Text(
                                    text = reorderBtnText,
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.ExtraBold),
                                    color = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier
                                        .clickable { isFavoriteEditMode = !isFavoriteEditMode }
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }

                        itemsIndexed(pinnedFavorites, key = { _, it -> "pinned_${it.code}" }) { index, item ->
                                val prevVal = previousRates[item.code]
                                val direction = remember(prevVal, item.value) {
                                    if (prevVal != null && prevVal != item.value) {
                                        val currentPrice = 1.0 / item.value
                                        val prevPrice = 1.0 / prevVal
                                        if (currentPrice > prevPrice) "UP" else "DOWN"
                                    } else "NONE"
                                }

                                OnlineCurrencyItem(
                                    currency = item,
                                    baseCurrency = baseCurrency,
                                    amountInput = amountInput,
                                    direction = direction,
                                    languageCode = languageCode,
                                    onFavoriteToggle = { viewModel.toggleFavorite(item.code) },
                                    onCardClick = { viewModel.updateBaseCurrency(item.code) },
                                    isReorderMode = isFavoriteEditMode,
                                    onMoveUp = if (index > 0) {
                                        {
                                            val mutableFavorites = pinnedFavorites.toMutableList()
                                            val temp = mutableFavorites[index]
                                            mutableFavorites[index] = mutableFavorites[index - 1]
                                            mutableFavorites[index - 1] = temp
                                            viewModel.saveFavoritesOrder(mutableFavorites.map { it.code })
                                        }
                                    } else null,
                                    onMoveDown = if (index < pinnedFavorites.size - 1) {
                                        {
                                            val mutableFavorites = pinnedFavorites.toMutableList()
                                            val temp = mutableFavorites[index]
                                            mutableFavorites[index] = mutableFavorites[index + 1]
                                            mutableFavorites[index + 1] = temp
                                            viewModel.saveFavoritesOrder(mutableFavorites.map { it.code })
                                        }
                                    } else null
                                )
                            }

                            item {
                                HorizontalDivider(
                                    modifier = Modifier.padding(vertical = 8.dp),
                                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
                                )
                            }
                        }

                        val standardFiat = standardCurrencies.filter { !it.isCrypto && !it.isCommodity }
                        val standardDigital = standardCurrencies.filter { it.isCrypto }
                        val standardCommodity = standardCurrencies.filter { it.isCommodity }

                        // Fiat section
                        if (selectedCategory == "all" || selectedCategory == "fiat") {
                            if (standardFiat.isNotEmpty()) {
                                item {
                                    Text(
                                        text = Localization.get("fiat_header", languageCode),
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.ExtraBold),
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                                    )
                                }
                                items(standardFiat, key = { "fiat_${it.code}" }) { item ->
                                    val prevVal = previousRates[item.code]
                                    val direction = remember(prevVal, item.value) {
                                        if (prevVal != null && prevVal != item.value) {
                                            val currentPrice = 1.0 / item.value
                                            val prevPrice = 1.0 / prevVal
                                            if (currentPrice > prevPrice) "UP" else "DOWN"
                                        } else "NONE"
                                    }
                                    OnlineCurrencyItem(
                                        currency = item,
                                        baseCurrency = baseCurrency,
                                        amountInput = amountInput,
                                        direction = direction,
                                        languageCode = languageCode,
                                        onFavoriteToggle = { viewModel.toggleFavorite(item.code) },
                                        onCardClick = { viewModel.updateBaseCurrency(item.code) }
                                    )
                                }
                            }
                        }

                        // Crypto section
                        if (selectedCategory == "all" || selectedCategory == "crypto") {
                            if (standardDigital.isNotEmpty()) {
                                item {
                                    Text(
                                        text = Localization.get("digital_header", languageCode),
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.ExtraBold),
                                        color = MaterialTheme.colorScheme.secondary,
                                        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                                    )
                                }
                                items(standardDigital, key = { "crypto_${it.code}" }) { item ->
                                    val prevVal = previousRates[item.code]
                                    val direction = remember(prevVal, item.value) {
                                        if (prevVal != null && prevVal != item.value) {
                                            val currentPrice = 1.0 / item.value
                                            val prevPrice = 1.0 / prevVal
                                            if (currentPrice > prevPrice) "UP" else "DOWN"
                                        } else "NONE"
                                    }
                                    OnlineCurrencyItem(
                                        currency = item,
                                        baseCurrency = baseCurrency,
                                        amountInput = amountInput,
                                        direction = direction,
                                        languageCode = languageCode,
                                        onFavoriteToggle = { viewModel.toggleFavorite(item.code) },
                                        onCardClick = { viewModel.updateBaseCurrency(item.code) }
                                    )
                                }
                            }
                        }

                        // Commodities section
                        if (selectedCategory == "all" || selectedCategory == "commodity") {
                            if (standardCommodity.isNotEmpty()) {
                                item {
                                    Text(
                                        text = Localization.get("commodity_header", languageCode),
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.ExtraBold),
                                        color = MintPrimary,
                                        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                                    )
                                }
                                items(standardCommodity, key = { "commodity_${it.code}" }) { item ->
                                    val prevVal = previousRates[item.code]
                                    val direction = remember(prevVal, item.value) {
                                        if (prevVal != null && prevVal != item.value) {
                                            val currentPrice = 1.0 / item.value
                                            val prevPrice = 1.0 / prevVal
                                            if (currentPrice > prevPrice) "UP" else "DOWN"
                                        } else "NONE"
                                    }
                                    OnlineCurrencyItem(
                                        currency = item,
                                        baseCurrency = baseCurrency,
                                        amountInput = amountInput,
                                        direction = direction,
                                        languageCode = languageCode,
                                        onFavoriteToggle = { viewModel.toggleFavorite(item.code) },
                                        onCardClick = { viewModel.updateBaseCurrency(item.code) }
                                    )
                                }
                            }
                        }

                    } else {
                        val unifiedFiltered = when (selectedCategory) {
                            "fiat" -> displayCurrencies.filter { !it.isCrypto && !it.isCommodity }
                            "crypto" -> displayCurrencies.filter { it.isCrypto }
                            "commodity" -> displayCurrencies.filter { it.isCommodity }
                            else -> displayCurrencies
                        }

                        if (unifiedFiltered.isEmpty()) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 32.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.SearchOff,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                                            modifier = Modifier.size(48.dp)
                                        )
                                        Text(
                                            text = Localization.get("no_match_text", languageCode),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }
                            }
                        } else {
                            item {
                                Text(
                                    text = when (activeSort) {
                                        SortOption.ALPHABETICAL_AZ -> Localization.get("sort_alpha_az", languageCode)
                                        SortOption.ALPHABETICAL_ZA -> Localization.get("sort_alpha_za", languageCode)
                                        SortOption.HIGHEST_VALUE -> Localization.get("sort_high_val", languageCode)
                                        SortOption.LOWEST_VALUE -> Localization.get("sort_low_val", languageCode)
                                        else -> Localization.get("sort_fav_first", languageCode)
                                    },
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.ExtraBold),
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(top = 8.dp)
                                )
                            }

                            items(unifiedFiltered, key = { "unified_${it.code}" }) { item ->
                                val prevVal = previousRates[item.code]
                                val direction = remember(prevVal, item.value) {
                                    if (prevVal != null && prevVal != item.value) {
                                        val currentPrice = 1.0 / item.value
                                        val prevPrice = 1.0 / prevVal
                                        if (currentPrice > prevPrice) "UP" else "DOWN"
                                    } else "NONE"
                                }
                                OnlineCurrencyItem(
                                    currency = item,
                                    baseCurrency = baseCurrency,
                                    amountInput = amountInput,
                                    direction = direction,
                                    languageCode = languageCode,
                                    onFavoriteToggle = { viewModel.toggleFavorite(item.code) },
                                    onCardClick = { viewModel.updateBaseCurrency(item.code) }
                                )
                            }
                        }
                    }
                }
            }

            FloatingActionButton(
                onClick = { showAddOnlineDialog = true },
                containerColor = MintPrimary,
                contentColor = Color.White,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(24.dp)
                    .testTag("add_online_currency_fab")
                    .alpha(0.5f),
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Add Online Currency"
                    )
                    Text(
                        text = if (languageCode == "fa") "افزودن ارز آنلاین" else "Add Online Currency",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }

            if (showAddOnlineDialog) {
                var searchQueryOnline by remember { mutableStateOf("") }
                var onlineResults by remember { mutableStateOf<List<Currency>>(emptyList()) }
                var isSearchingOnline by remember { mutableStateOf(false) }
                var onlineSearchError by remember { mutableStateOf<String?>(null) }
                val scope = rememberCoroutineScope()
                var addingStates by remember { mutableStateOf<Map<String, Boolean>>(emptyMap()) }

                LaunchedEffect(searchQueryOnline) {
                    val query = searchQueryOnline.trim()
                    if (query.length >= 2) {
                        isSearchingOnline = true
                        onlineSearchError = null
                        viewModel.searchOnlineCurrencies(
                            query,
                            onResult = { results ->
                                onlineResults = results
                                isSearchingOnline = false
                            },
                            onError = { error ->
                                onlineSearchError = error
                                isSearchingOnline = false
                            }
                        )
                    } else {
                        onlineResults = emptyList()
                        isSearchingOnline = false
                    }
                }

                AlertDialog(
                    onDismissRequest = { showAddOnlineDialog = false },
                    confirmButton = {},
                    dismissButton = {
                        TextButton(onClick = { showAddOnlineDialog = false }) {
                            Text(
                                text = if (languageCode == "fa") "بستن" else "Close",
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    },
                    title = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Public,
                                contentDescription = null,
                                tint = MintPrimary,
                                modifier = Modifier.size(24.dp)
                            )
                            Text(
                                text = if (languageCode == "fa") "یافتن و افزودن ارز آنلاین" else "Find & Add Currency Online",
                                fontWeight = FontWeight.ExtraBold,
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                    },
                    text = {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .animateContentSize(),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Text(
                                text = if (languageCode == "fa") 
                                    "نام یا کد بین‌المللی ارز مورد نظر خود را تایپ کنید تا بلافاصله به شکل زنده در بستر اینترنت جستجو و همراه با نرخ لحظه‌ای به لیست اضافه شود." 
                                    else "Type the name or international code of a currency to search it online in real-time and add it with its live rate to the database.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )

                            OutlinedTextField(
                                value = searchQueryOnline,
                                onValueChange = { searchQueryOnline = it },
                                placeholder = {
                                    Text(
                                        text = if (languageCode == "fa") "مثلاً دلار، لیر، CAD..." else "e.g. Canadian Dollar, CAD..."
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Search,
                                        contentDescription = null
                                    )
                                },
                                trailingIcon = {
                                    if (searchQueryOnline.isNotEmpty()) {
                                        IconButton(onClick = { searchQueryOnline = "" }) {
                                            Icon(
                                                imageVector = Icons.Default.Clear,
                                                contentDescription = "Clear Search"
                                            )
                                        }
                                    }
                                },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            )

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 100.dp, max = 280.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                if (isSearchingOnline) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        CircularProgressIndicator(
                                            color = MintPrimary,
                                            modifier = Modifier.size(36.dp)
                                        )
                                        Text(
                                            text = if (languageCode == "fa") "در حال جستجو در شبکه جهانی..." else "Searching global network...",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                        )
                                    }
                                } else if (searchQueryOnline.trim().length < 2) {
                                    Text(
                                        text = if (languageCode == "fa") "برای شروع جستجو حداقل ۲ کاراکتر بنویسید" else "Type at least 2 characters to search",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                                        textAlign = TextAlign.Center
                                    )
                                } else if (onlineResults.isEmpty()) {
                                    Text(
                                        text = if (languageCode == "fa") "هیچ ارزی با این مشخصات در شبکه یافت نشد" else "No matching currencies found online",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                                        textAlign = TextAlign.Center
                                    )
                                } else {
                                    LazyColumn(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        items(onlineResults) { item ->
                                            val isAdding = addingStates[item.code] == true
                                            
                                            Card(
                                                modifier = Modifier.fillMaxWidth(),
                                                colors = CardDefaults.cardColors(
                                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
                                                ),
                                                shape = RoundedCornerShape(12.dp),
                                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
                                            ) {
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(10.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.SpaceBetween
                                                ) {
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                                        modifier = Modifier.weight(1f)
                                                    ) {
                                                        Box(
                                                            modifier = Modifier
                                                                .size(38.dp)
                                                                .clip(CircleShape)
                                                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                                                            contentAlignment = Alignment.Center
                                                        ) {
                                                            Text(
                                                                text = item.symbol,
                                                                fontWeight = FontWeight.Bold,
                                                                style = MaterialTheme.typography.bodyMedium,
                                                                color = MaterialTheme.colorScheme.primary
                                                            )
                                                        }
                                                        Column(modifier = Modifier.weight(1f)) {
                                                            Text(
                                                                text = item.name,
                                                                fontWeight = FontWeight.Bold,
                                                                style = MaterialTheme.typography.bodyMedium,
                                                                color = MaterialTheme.colorScheme.onSurface,
                                                                maxLines = 1,
                                                                overflow = TextOverflow.Ellipsis
                                                            )
                                                            Text(
                                                                text = item.code,
                                                                fontWeight = FontWeight.Medium,
                                                                style = MaterialTheme.typography.bodySmall,
                                                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                                            )
                                                        }
                                                    }

                                                    Button(
                                                        onClick = {
                                                            addingStates = addingStates + (item.code to true)
                                                            viewModel.addOnlineCurrency(
                                                                item,
                                                                onSuccess = {
                                                                    scope.launch {
                                                                        addingStates = addingStates + (item.code to false)
                                                                        onlineResults = onlineResults.filter { it.code != item.code }
                                                                    }
                                                                },
                                                                onFailure = {
                                                                    addingStates = addingStates + (item.code to false)
                                                                }
                                                            )
                                                        },
                                                        enabled = !isAdding,
                                                        colors = ButtonDefaults.buttonColors(
                                                            containerColor = MintPrimary,
                                                            contentColor = Color.White
                                                        ),
                                                        shape = RoundedCornerShape(8.dp),
                                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                                        modifier = Modifier.height(34.dp)
                                                    ) {
                                                        if (isAdding) {
                                                            CircularProgressIndicator(
                                                                color = Color.White,
                                                                modifier = Modifier.size(16.dp),
                                                                strokeWidth = 2.dp
                                                            )
                                                        } else {
                                                            Row(
                                                                verticalAlignment = Alignment.CenterVertically,
                                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                            ) {
                                                                Icon(
                                                                    imageVector = Icons.Default.Add,
                                                                    contentDescription = null,
                                                                    modifier = Modifier.size(14.dp)
                                                                )
                                                                Text(
                                                                    text = if (languageCode == "fa") "افزودن" else "Add",
                                                                    style = MaterialTheme.typography.labelMedium,
                                                                    fontWeight = FontWeight.Bold
                                                                )
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                )
            }
        }
    }

@Composable
fun OnlineCurrencyItem(
    currency: Currency,
    baseCurrency: Currency,
    amountInput: String,
    direction: String,
    languageCode: String,
    onFavoriteToggle: () -> Unit,
    onCardClick: () -> Unit,
    isReorderMode: Boolean = false,
    onMoveUp: (() -> Unit)? = null,
    onMoveDown: (() -> Unit)? = null
) {
    val inputVal = amountInput.toDoubleOrNull() ?: 0.0
    val hasValidInput = inputVal > 0.0

    val priceInUSD = 1.0 / currency.value
    val priceInBase = baseCurrency.value / currency.value

    val convertedAmount = if (hasValidInput) {
        inputVal * (currency.value / baseCurrency.value)
    } else {
        baseCurrency.value / currency.value
    }

    fun formatPrice(value: Double): String {
        return when {
            value == 0.0 -> "0"
            value >= 1000.0 -> String.format(Locale.US, "%,.2f", value)
            value >= 1.0 -> String.format(Locale.US, "%,.4f", value)
            else -> String.format(Locale.US, "%,.8f", value)
        }
    }

    val isBaseCurrency = currency.code == baseCurrency.code

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !isReorderMode) { onCardClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isBaseCurrency) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        border = if (isBaseCurrency) {
            CardDefaults.outlinedCardBorder()
        } else {
            null
        },
        elevation = CardDefaults.cardElevation(defaultElevation = if (isBaseCurrency) 4.dp else 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Currency Core Info Block
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(
                            if (isBaseCurrency) {
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                            } else {
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = currency.symbol.ifEmpty { currency.code.take(1) },
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = Localization.getCurrencyName(currency.code, currency.name, languageCode),
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = currency.code.uppercase(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }
            }

            // Computed Rate value and action icons
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Computed conversion value text
                Column(
                    horizontalAlignment = Alignment.End,
                    modifier = Modifier.padding(end = 6.dp)
                ) {
                    Text(
                        text = if (hasValidInput) {
                            formatPrice(convertedAmount)
                        } else {
                            formatPrice(priceInBase)
                        },
                        fontWeight = FontWeight.ExtraBold,
                        style = MaterialTheme.typography.titleMedium,
                        color = if (isBaseCurrency) MaterialTheme.colorScheme.primary else MintPrimary,
                        textAlign = TextAlign.End
                    )
                    
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = if (hasValidInput) {
                                "1 ${baseCurrency.code} = ${formatPrice(currency.value / baseCurrency.value)} ${currency.code}"
                            } else {
                                "$${formatPrice(priceInUSD)}"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                        )

                        // Direction Arrow indicator beautifully matching market updates
                        when (direction) {
                            "UP" -> {
                                Icon(
                                    imageVector = Icons.Default.TrendingUp,
                                    contentDescription = "Up",
                                    tint = Color(0xFF2ECC71),
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                            "DOWN" -> {
                                Icon(
                                    imageVector = Icons.Default.TrendingDown,
                                    contentDescription = "Down",
                                    tint = Color(0xFFE74C3C),
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }
                }

                if (isReorderMode) {
                    IconButton(
                        onClick = { onMoveUp?.invoke() },
                        enabled = onMoveUp != null,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowUpward,
                            contentDescription = "Move Up",
                            tint = if (onMoveUp != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
                        )
                    }

                    IconButton(
                        onClick = { onMoveDown?.invoke() },
                        enabled = onMoveDown != null,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowDownward,
                            contentDescription = "Move Down",
                            tint = if (onMoveDown != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
                        )
                    }
                } else {
                    // Star toggle (favorite)
                    IconButton(onClick = onFavoriteToggle, modifier = Modifier.size(36.dp)) {
                        Icon(
                            imageVector = if (currency.isFavorite) Icons.Default.Star else Icons.Outlined.StarOutline,
                            contentDescription = "Toggle Favorite",
                            tint = if (currency.isFavorite) StarGold else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
                        )
                    }
                }
            }
        }
    }
}
