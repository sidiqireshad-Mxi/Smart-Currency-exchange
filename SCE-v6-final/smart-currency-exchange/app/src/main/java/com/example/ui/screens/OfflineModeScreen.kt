package com.example.ui.screens

import java.util.Locale
import androidx.compose.animation.*
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import android.app.Activity
import kotlin.math.roundToInt
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.model.Currency
import com.example.data.model.isCrypto
import com.example.data.model.isCommodity
import com.example.data.model.offlineValueOrValue
import com.example.data.model.resolveUsdRate
import com.example.data.model.findConversionMultiplier
import com.example.data.model.getExchangeRates
import com.example.data.model.ExchangeRate
import com.example.data.model.parseExchangeRates
import com.example.data.model.validateExchangeRates
import com.example.ui.theme.MintPrimary
import com.example.ui.theme.Slate800
import com.example.ui.theme.Slate900
import com.example.ui.theme.StarGold
import com.example.ui.viewmodel.CurrencyViewModel
import com.example.ui.viewmodel.SortOption
import com.example.ui.util.Localization
import com.example.ui.util.MathEvaluator
import coil.compose.AsyncImage
import kotlinx.coroutines.delay

private fun formatClean(value: Double, code: String): String {
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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun OfflineModeScreen(
    viewModel: CurrencyViewModel,
    modifier: Modifier = Modifier
) {
    val currencies by viewModel.currenciesState.collectAsState()
    val displayUnit by viewModel.displayCurrencyUnit.collectAsState()
    val baseCode by viewModel.baseCurrencyCode.collectAsState()
    val amountInput by viewModel.amountInput.collectAsState()
    val activeSort by viewModel.selectedSort.collectAsState()

    val isOnlineUpdating by viewModel.isOnlineUpdating.collectAsState()
    val onlineUpdateSuccessMessage by viewModel.onlineUpdateSuccessMessage.collectAsState()

    LaunchedEffect(onlineUpdateSuccessMessage) {
        if (onlineUpdateSuccessMessage != null) {
            delay(3500)
            viewModel.clearOnlineUpdateMessage()
        }
    }

    // Dynamic customization states
    val languageCode by viewModel.languageCode.collectAsState()
    val customAppName by viewModel.customAppName.collectAsState()
    val customAppIcon by viewModel.customAppIcon.collectAsState()
    val customAppIconUri by viewModel.customAppIconUri.collectAsState()

    // Calculator state fields
    var showCalculator by remember { mutableStateOf(false) }
    var calcExpression by remember { mutableStateOf("") }
    var calcResult by remember { mutableStateOf("") }
    var calcErrorMsg by remember { mutableStateOf("") }
    var isCalculatorPopUp by remember { mutableStateOf(false) }
    var calculatorSizeState by remember { mutableStateOf("medium") } // "small", "medium", "large"
    var dragOffset by remember { mutableStateOf(Offset(40f, 180f)) }

    var showAddDialog by remember { mutableStateOf(false) }
    var currencyToEdit by remember { mutableStateOf<Currency?>(null) }
    var isFavoriteEditMode by remember { mutableStateOf(false) }
    var showWarningDialog1 by remember { mutableStateOf(false) }
    var showWarningDialog2 by remember { mutableStateOf(false) }
    
    // Search query local state linked to viewModel
    var queryText by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("all") }

    // Focus Requester and Keyboard Controller for automatic interaction
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

    // Helper functions for scaling values to Rial or Toman dynamically
    fun formatDisplayValue(valDouble: Double, code: String): String {
        return if (displayUnit == "RIAL" && code.uppercase() == "TOMAN") {
            // Convert TOMAN view to RIAL
            val rialVal = valDouble * 10
            formatClean(rialVal, code) + " ﷼"
        } else {
            formatClean(valDouble, code)
        }
    }

    // Format the live computed conversion value
    fun formatConvertedValue(valueDouble: Double, code: String): String {
        return if (displayUnit == "RIAL" && code.uppercase() == "TOMAN") {
            val rialVal = valueDouble * 10
            formatClean(rialVal, "RIAL")
        } else {
            formatClean(valueDouble, code)
        }
    }

    // Identify current active base currency
    val baseCurrency = currencies.find { it.code == baseCode } 
        ?: Currency(code = "USD", name = "US Dollar", symbol = "$", value = 1.0, notes = "Base", isFavorite = true)

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            modifier = modifier,
            floatingActionButton = {
                if (!showCalculator) {
                    FloatingActionButton(
                        onClick = { showAddDialog = true },
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.alpha(0.5f)
                    ) {
                        Icon(imageVector = Icons.Default.Add, contentDescription = "Add Custom Currency")
                    }
                }
            }
        ) { innerPadding ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header Description
                item {
                    Row(
                        modifier = Modifier.padding(top = 16.dp).fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Dynamic logo rendering based on settings!
                        Box(
                            modifier = Modifier
                                .size(54.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            if (customAppIcon == "custom" && customAppIconUri.isNotEmpty()) {
                                AsyncImage(
                                    model = customAppIconUri,
                                    contentDescription = "Custom App Logo",
                                    modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp))
                                )
                            } else {
                                // Show real app logo img_app_icon for ALL presets
                                val borderColor = when (customAppIcon) {
                                    "logo_gold" -> StarGold
                                    "logo_neon" -> MintPrimary
                                    "logo_vintage" -> MaterialTheme.colorScheme.tertiary
                                    else -> Color.Transparent
                                }
                                val borderWidth = if (borderColor != Color.Transparent) 2.dp else 0.dp
                                androidx.compose.foundation.Image(
                                    painter = androidx.compose.ui.res.painterResource(id = com.example.R.drawable.img_app_icon),
                                    contentDescription = "App Logo Image",
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(RoundedCornerShape(12.dp))
                                        .then(
                                            if (borderWidth > 0.dp) {
                                                Modifier.border(borderWidth, borderColor, RoundedCornerShape(12.dp))
                                            } else {
                                                Modifier
                                            }
                                        )
                                )
                            }
                        }

                        Column {
                            Text(
                                text = customAppName,
                                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = Localization.get("created_by", languageCode),
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.padding(vertical = 1.dp)
                            )
                            val descStr = Localization.get("app_desc_headline", languageCode)
                            Text(
                                text = descStr,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                            )
                        }
                    }
                }

                // Base Currency Input Dashboard Card
                stickyHeader {
                    Surface(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
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
                                                text = if (displayUnit == "RIAL" && baseCurrency.code == "TOMAN") "﷼" else baseCurrency.symbol,
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
                                                    text = if (displayUnit == "RIAL" && baseCurrency.code == "TOMAN") "RIAL" else baseCurrency.code,
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

                                    if (isOnlineUpdating) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(24.dp),
                                            strokeWidth = 2.dp,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    } else {
                                        FilledTonalIconButton(
                                            onClick = { showWarningDialog1 = true },
                                            modifier = Modifier.size(40.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Refresh,
                                                contentDescription = Localization.get("online_sync_tooltip", languageCode),
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                }

                                // Amount Input Box
                                val inputPlaceholder = Localization.get("input_placeholder", languageCode)
                                val inputLabel = Localization.get("input_label", languageCode)

                                OutlinedTextField(
                                    value = amountInput,
                                    onValueChange = { viewModel.updateAmountInput(it) },
                                    placeholder = { Text(inputPlaceholder) },
                                    label = { Text(inputLabel) },
                                    leadingIcon = {
                                        Text(
                                            text = if (displayUnit == "RIAL" && baseCurrency.code == "TOMAN") "﷼" else baseCurrency.symbol,
                                            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.padding(start = 12.dp, end = 4.dp)
                                        )
                                    },
                                    trailingIcon = {
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
                                    },
                                    keyboardOptions = KeyboardOptions(
                                        keyboardType = KeyboardType.Number,
                                        imeAction = ImeAction.Done
                                    ),
                                    keyboardActions = KeyboardActions(
                                        onDone = {
                                            viewModel.saveCurrentConversionToHistory()
                                            keyboardController?.hide()
                                            focusManager.clearFocus()
                                        }
                                    ),
                                    singleLine = true,
                                    textStyle = MaterialTheme.typography.headlineMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                        textAlign = TextAlign.Left
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .focusRequester(focusRequester),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                                        unfocusedBorderColor = MaterialTheme.colorScheme.outline
                                    )
                                )
                            }
                        }
                    }
                }

                // Expanded interactive Pocket Calculator Grid (Inline Mode)
                if (showCalculator && !isCalculatorPopUp) {
                    item {
                        Surface(
                            modifier = Modifier.fillMaxWidth().animateContentSize(),
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
                                    viewModel.saveCurrentConversionToHistory()
                                    keyboardController?.hide()
                                    focusManager.clearFocus()
                                    showCalculator = false
                                },
                                appName = customAppName
                            )
                        }
                    }
                }

                item {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        // Search box
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
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )

                    // Sort & Category Group (Brought closer together)
                    Column(
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
                                val activeSort by viewModel.selectedSort.collectAsState()
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
                                categories.forEach { (catId, localFa, localEn) ->
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
            }

            // Main Currencies Listing
            if (currencies.isEmpty()) {
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
                    // Split currencies list: Favorite/Pinned goes first, then standard currencies
                    val pinnedFavorites = currencies.filter { it.isFavorite }
                    val standardCurrencies = currencies

                    val cryptoCodes = setOf("BTC", "TON", "ETH", "SOL", "BNB", "ADA", "DOGE", "XRP", "USDT", "USDC", "LTC", "LINK", "DOT", "POL", "SHIB", "AVAX")
                    val commodityCodes = setOf("GOLD", "SILVER", "DIAMOND", "PLATINUM")

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
                                val pinnedText = Localization.get("pinned_favorites", languageCode)
                                Text(
                                    text = pinnedText,
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

                        itemsIndexed(pinnedFavorites, key = { _, cur -> "pinned_${cur.code}" }) { index, currency ->
                            val inputVal = amountInput.toDoubleOrNull() ?: 0.0
                            val conversionMultiplier = findConversionMultiplier(baseCurrency.code, currency.code, currencies)
                            val rawConverted = if (conversionMultiplier != null) {
                                inputVal * conversionMultiplier
                            } else {
                                null
                            }

                            CurrencyDashboardCard(
                                currency = currency,
                                convertedValue = rawConverted,
                                displayUnit = displayUnit,
                                isBaseCurrency = currency.code == baseCode,
                                formatConvertedValue = ::formatConvertedValue,
                                onCardClick = {
                                    viewModel.updateBaseCurrency(currency.code)
                                },
                                onToggleFavorite = {
                                    viewModel.toggleFavorite(currency.code)
                                },
                                onEditClick = {
                                    currencyToEdit = currency
                                },
                                onDeleteClick = {
                                    viewModel.deleteCurrency(currency)
                                },
                                isReorderMode = isFavoriteEditMode,
                                languageCode = languageCode,
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
                    }

                    val standardFiat = standardCurrencies.filter { !it.isCrypto && !it.isCommodity }
                    val standardDigital = standardCurrencies.filter { it.isCrypto }
                    val standardCommodity = standardCurrencies.filter { it.isCommodity }

                    // Render Standard Sections Based on chosen Category
                    if (selectedCategory == "all" || selectedCategory == "fiat") {
                        if (standardFiat.isNotEmpty()) {
                            item {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 16.dp, bottom = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    val fiatHeaderText = Localization.get("fiat_header", languageCode)
                                    Text(
                                        text = fiatHeaderText,
                                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.ExtraBold),
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        TextButton(
                                            onClick = { selectedCategory = "crypto" },
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = Localization.get("crypto_arrow", languageCode),
                                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                                color = MaterialTheme.colorScheme.secondary
                                            )
                                        }
                                        TextButton(
                                            onClick = { selectedCategory = "commodity" },
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = Localization.get("gold_arrow", languageCode),
                                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                                color = MintPrimary
                                            )
                                        }
                                    }
                                }
                            }

                            items(standardFiat, key = { "others_fiat_${it.code}" }) { currency ->
                                val inputVal = amountInput.toDoubleOrNull() ?: 0.0
                                val conversionMultiplier = findConversionMultiplier(baseCurrency.code, currency.code, currencies)
                                val rawConverted = if (conversionMultiplier != null) {
                                    inputVal * conversionMultiplier
                                } else {
                                    null
                                }

                                CurrencyDashboardCard(
                                    currency = currency,
                                    convertedValue = rawConverted,
                                    displayUnit = displayUnit,
                                    isBaseCurrency = currency.code == baseCode,
                                    formatConvertedValue = ::formatConvertedValue,
                                    onCardClick = {
                                        viewModel.updateBaseCurrency(currency.code)
                                    },
                                    onToggleFavorite = {
                                        viewModel.toggleFavorite(currency.code)
                                    },
                                    onEditClick = {
                                        currencyToEdit = currency
                                    },
                                    onDeleteClick = {
                                        viewModel.deleteCurrency(currency)
                                    },
                                    languageCode = languageCode
                                )
                            }
                        }
                    }

                    if (selectedCategory == "all" || selectedCategory == "crypto") {
                        if (standardDigital.isNotEmpty()) {
                            item {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 16.dp, bottom = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    val digitalHeaderText = Localization.get("digital_header", languageCode)
                                    Text(
                                        text = digitalHeaderText,
                                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.ExtraBold),
                                        color = MaterialTheme.colorScheme.secondary
                                    )
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        TextButton(
                                            onClick = { selectedCategory = "fiat" },
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = Localization.get("fiat_arrow", languageCode),
                                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                        TextButton(
                                            onClick = { selectedCategory = "commodity" },
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = Localization.get("gold_arrow", languageCode),
                                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                                color = MintPrimary
                                            )
                                        }
                                    }
                                }
                            }

                            items(standardDigital, key = { "others_digital_${it.code}" }) { currency ->
                                val inputVal = amountInput.toDoubleOrNull() ?: 0.0
                                val conversionMultiplier = findConversionMultiplier(baseCurrency.code, currency.code, currencies)
                                val rawConverted = if (conversionMultiplier != null) {
                                    inputVal * conversionMultiplier
                                } else {
                                    null
                                }

                                CurrencyDashboardCard(
                                    currency = currency,
                                    convertedValue = rawConverted,
                                    displayUnit = displayUnit,
                                    isBaseCurrency = currency.code == baseCode,
                                    formatConvertedValue = ::formatConvertedValue,
                                    onCardClick = {
                                        viewModel.updateBaseCurrency(currency.code)
                                    },
                                    onToggleFavorite = {
                                        viewModel.toggleFavorite(currency.code)
                                    },
                                    onEditClick = {
                                        currencyToEdit = currency
                                    },
                                    onDeleteClick = {
                                        viewModel.deleteCurrency(currency)
                                    },
                                    languageCode = languageCode
                                )
                            }
                        }
                    }

                    if (selectedCategory == "all" || selectedCategory == "commodity") {
                        if (standardCommodity.isNotEmpty()) {
                            item {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 16.dp, bottom = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    val commodityHeaderText = Localization.get("commodity_header", languageCode)
                                    Text(
                                        text = commodityHeaderText,
                                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.ExtraBold),
                                        color = MintPrimary
                                    )
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        TextButton(
                                            onClick = { selectedCategory = "fiat" },
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = Localization.get("fiat_arrow", languageCode),
                                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                        TextButton(
                                            onClick = { selectedCategory = "crypto" },
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = Localization.get("crypto_arrow", languageCode),
                                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                                color = MaterialTheme.colorScheme.secondary
                                            )
                                        }
                                    }
                                }
                            }

                            items(standardCommodity, key = { "others_commodity_${it.code}" }) { currency ->
                                val inputVal = amountInput.toDoubleOrNull() ?: 0.0
                                val conversionMultiplier = findConversionMultiplier(baseCurrency.code, currency.code, currencies)
                                val rawConverted = if (conversionMultiplier != null) {
                                    inputVal * conversionMultiplier
                                } else {
                                    null
                                }

                                CurrencyDashboardCard(
                                    currency = currency,
                                    convertedValue = rawConverted,
                                    displayUnit = displayUnit,
                                    isBaseCurrency = currency.code == baseCode,
                                    formatConvertedValue = ::formatConvertedValue,
                                    onCardClick = {
                                        viewModel.updateBaseCurrency(currency.code)
                                    },
                                    onToggleFavorite = {
                                        viewModel.toggleFavorite(currency.code)
                                    },
                                    onEditClick = {
                                        currencyToEdit = currency
                                    },
                                    onDeleteClick = {
                                        viewModel.deleteCurrency(currency)
                                    },
                                    languageCode = languageCode
                                )
                            }
                        }
                    }
                } else {
                    // For alphabetical or value-based sorts, render a single unified list filtered by selectedCategory
                    val unifiedFiltered = when (selectedCategory) {
                        "fiat" -> currencies.filter { !it.isCrypto && !it.isCommodity }
                        "crypto" -> currencies.filter { it.isCrypto }
                        "commodity" -> currencies.filter { it.isCommodity }
                        else -> currencies
                    }

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

                    items(unifiedFiltered, key = { "unified_${it.code}" }) { currency ->
                        val inputVal = amountInput.toDoubleOrNull() ?: 0.0
                        val conversionMultiplier = findConversionMultiplier(baseCurrency.code, currency.code, currencies)
                        val rawConverted = if (conversionMultiplier != null) {
                            inputVal * conversionMultiplier
                        } else {
                            null
                        }

                        CurrencyDashboardCard(
                            currency = currency,
                            convertedValue = rawConverted,
                            displayUnit = displayUnit,
                            isBaseCurrency = currency.code == baseCode,
                            formatConvertedValue = ::formatConvertedValue,
                            onCardClick = {
                                viewModel.updateBaseCurrency(currency.code)
                            },
                            onToggleFavorite = {
                                viewModel.toggleFavorite(currency.code)
                            },
                            onEditClick = {
                                currencyToEdit = currency
                            },
                            onDeleteClick = {
                                viewModel.deleteCurrency(currency)
                            },
                            languageCode = languageCode
                        )
                    }
                }

            // Bottom Spacing for full layout look
            item { Spacer(modifier = Modifier.height(80.dp)) }
        }
    }
}

    // Add custom currency dialog
    if (showAddDialog) {
        CurrencyFormDialog(
            title = Localization.get("add_currency", languageCode),
            currencies = currencies,
            languageCode = languageCode,
            onDismiss = { showAddDialog = false },
            onSave = { name, symbol, code, exchangeRates, notes ->
                viewModel.addCurrency(name, symbol, code, exchangeRates, notes)
                showAddDialog = false
            }
        )
    }

    // Edit custom currency dialog
    if (currencyToEdit != null) {
        val cur = currencyToEdit!!
        val editDlgTitle = "${Localization.get("edit_currency", languageCode)}: ${cur.code}"
        CurrencyFormDialog(
            title = editDlgTitle,
            currencies = currencies,
            initialName = cur.name,
            initialSymbol = cur.symbol,
            initialCode = cur.code,
            initialValue = try { java.math.BigDecimal.valueOf(cur.rateValue ?: cur.offlineValueOrValue).stripTrailingZeros().toPlainString() } catch(e: Exception) { (cur.rateValue ?: cur.offlineValueOrValue).toString() },
            initialBaseCurrencyCode = cur.baseCurrencyCode ?: "USD",
            initialExchangeRatesJson = cur.exchangeRatesJson ?: "",
            initialNotes = cur.notes,
            isEditMode = true,
            languageCode = languageCode,
            onDismiss = { currencyToEdit = null },
            onSave = { name, symbol, code, exchangeRates, notes ->
                viewModel.editCurrencyWithRates(
                    cur,
                    name = name,
                    symbol = symbol,
                    rates = exchangeRates,
                    notes = notes
                )
                currencyToEdit = null
            }
        )
    }

    // Double-confirmation Warning Dialog 1 (Offline Mode Screen)
    if (showWarningDialog1) {
        AlertDialog(
            onDismissRequest = { showWarningDialog1 = false },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = if (languageCode == "fa") "تأیید بروزرسانی نرخ‌ها" else "Confirm Rates Update",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            text = {
                Text(
                    text = if (languageCode == "fa") {
                        "آیا مایلید نرخ ارزها را به صورت آنلاین بروزرسانی کنید؟ این کار اطلاعات فعلی شما را همگام‌سازی می‌کند."
                    } else {
                        "Do you want to update the currency rates online now? This will synchronize the current data."
                    },
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showWarningDialog1 = false
                        showWarningDialog2 = true
                    }
                ) {
                    Text(text = if (languageCode == "fa") "بله، ادامه بده" else "Yes, Continue")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showWarningDialog1 = false }
                ) {
                    Text(text = if (languageCode == "fa") "خیر، لغو کن" else "No, Cancel")
                }
            }
        )
    }

    // Double-confirmation Warning Dialog 2 (Offline Mode Screen)
    if (showWarningDialog2) {
        AlertDialog(
            onDismissRequest = { showWarningDialog2 = false },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error
                    )
                    Text(
                        text = if (languageCode == "fa") "⚠️ هشدار نهایی و بازنویسی" else "⚠️ Final Overwrite Confirmation",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            text = {
                Text(
                    text = if (languageCode == "fa") {
                        "کمی دقت کنید! آیا از انجام این کار ۱۰۰٪ مطمئن هستید؟ نرخ‌های فعلی با اطلاعات جدید بازنویسی خواهند شد."
                    } else {
                        "Double check! Are you 100% sure you want to proceed? Stored rates will be entirely partition-overwritten."
                    },
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showWarningDialog2 = false
                        viewModel.triggerOnlineRatesUpdate()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) {
                    Text(text = if (languageCode == "fa") "بله، ۱۰۰٪ مطمئنم" else "Yes, I'm 100% Sure")
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { showWarningDialog2 = false }
                ) {
                    Text(text = if (languageCode == "fa") "بازگشت" else "Go Back")
                }
            }
        )
    }

    // Fullscreen Overlay Calculator Screen
    if (showCalculator && isCalculatorPopUp) {
        Dialog(
            onDismissRequest = { showCalculator = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
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
                    onPopUpToggle = { isCalculatorPopUp = false },
                    calculatorSizeState = "large",
                    onSizeToggle = {},
                    onClose = { showCalculator = false },
                    onPasteResult = { res ->
                        viewModel.updateAmountInput(res)
                        viewModel.saveCurrentConversionToHistory()
                        keyboardController?.hide()
                        focusManager.clearFocus()
                        showCalculator = false
                    },
                    appName = customAppName
                )
            }
        }
    }

    // Modern Animated Toast Overlay for Success Synchronization
    AnimatedVisibility(
        visible = onlineUpdateSuccessMessage != null,
        enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
        modifier = Modifier
            .align(Alignment.TopCenter)
            .padding(top = 24.dp)
            .padding(horizontal = 24.dp)
    ) {
        Surface(
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
            shadowElevation = 8.dp
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "Success",
                    tint = MintPrimary,
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    text = Localization.get("online_sync_success", languageCode),
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                )
            }
        }
    }
    } // Closed outer content container Box(modifier = Modifier.fillMaxSize())
}

@Composable
fun CurrencyDashboardCard(
    currency: Currency,
    convertedValue: Double?,
    displayUnit: String,
    isBaseCurrency: Boolean,
    formatConvertedValue: (Double, String) -> String,
    onCardClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit,
    isReorderMode: Boolean = false,
    languageCode: String = "fa",
    onMoveUp: (() -> Unit)? = null,
    onMoveDown: (() -> Unit)? = null
) {
    val isTomanCode = currency.code.uppercase() == "TOMAN"
    val showModelCode = if (displayUnit == "RIAL" && isTomanCode) "RIAL" else currency.code
    val showModelSymbol = if (displayUnit == "RIAL" && isTomanCode) "﷼" else currency.symbol

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
                        text = showModelSymbol,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = Localization.getCurrencyName(currency.code, currency.name, languageCode),
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Text(
                        text = showModelCode,
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
                    val ratesList = currency.getExchangeRates()
                    val rateString = if (ratesList.isNotEmpty()) {
                        ratesList.joinToString(", ") { rate ->
                            "${formatClean(rate.currentAmount, currency.code)} / ${formatClean(rate.referenceAmount, rate.referenceCurrencyCode)} ${rate.referenceCurrencyCode}"
                        }
                    } else {
                        formatClean(currency.offlineValueOrValue, currency.code)
                    }
                    Text(
                        text = if (convertedValue != null) {
                            formatConvertedValue(convertedValue, currency.code)
                        } else {
                            if (languageCode == "fa") "مسیر تبدیل وجود ندارد" else "No conversion path available"
                        },
                        fontWeight = FontWeight.ExtraBold,
                        style = if (convertedValue != null) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium,
                        color = if (convertedValue != null) {
                            if (isBaseCurrency) MaterialTheme.colorScheme.primary else MintPrimary
                        } else {
                            MaterialTheme.colorScheme.error
                        },
                        textAlign = TextAlign.End
                    )
                    Text(
                        // OLD: text = "$showModelSymbol ($rateString)",
                        text = showModelSymbol,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                    )
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
                    IconButton(onClick = onToggleFavorite, modifier = Modifier.size(36.dp)) {
                        Icon(
                            imageVector = if (currency.isFavorite) Icons.Default.Star else Icons.Outlined.StarOutline,
                            contentDescription = "Toggle Favorite",
                            tint = if (currency.isFavorite) StarGold else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
                        )
                    }

                    // Action Menu trigger
                    Box {
                        var menuExpanded by remember { mutableStateOf(false) }
                        IconButton(onClick = { menuExpanded = true }, modifier = Modifier.size(24.dp)) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "Options",
                                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                            )
                        }

                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { 
                                    val editLabel = Localization.get("edit_currency", languageCode)
                                    Text(editLabel) 
                                },
                                leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null, sizeChangeModifier()) },
                                onClick = {
                                    menuExpanded = false
                                    onEditClick()
                                }
                            )
                            DropdownMenuItem(
                                text = { 
                                    val deleteLabel = Localization.get("delete_currency", languageCode)
                                    Text(deleteLabel) 
                                },
                                leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, sizeChangeModifier(), tint = MaterialTheme.colorScheme.error) },
                                onClick = {
                                    menuExpanded = false
                                    onDeleteClick()
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

// Helper fun for icon scaling
private fun sizeChangeModifier(): Modifier = Modifier.size(18.dp)

data class EditableExchangeRate(
    val id: Long,
    val referenceCurrencyCode: String,
    val referenceAmountStr: String,
    val currentAmountStr: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CurrencyFormDialog(
    title: String,
    initialName: String = "",
    initialSymbol: String = "",
    initialCode: String = "",
    initialValue: String = "",
    initialNotes: String = "",
    initialBaseCurrencyCode: String = "USD",
    initialExchangeRatesJson: String = "",
    currencies: List<Currency> = emptyList(),
    isEditMode: Boolean = false,
    languageCode: String = "fa",
    onDismiss: () -> Unit,
    onSave: (name: String, symbol: String, code: String, exchangeRates: List<ExchangeRate>, notes: String) -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    var symbol by remember { mutableStateOf(initialSymbol) }
    var code by remember { mutableStateOf(initialCode) }
    var notes by remember { mutableStateOf(initialNotes) }

    val initialRates = remember {
        val parsed = parseExchangeRates(initialExchangeRatesJson)
        if (parsed.isNotEmpty()) {
            parsed.mapIndexed { i, rate ->
                EditableExchangeRate(
                    id = i.toLong(),
                    referenceCurrencyCode = rate.referenceCurrencyCode,
                    referenceAmountStr = try { java.math.BigDecimal.valueOf(rate.referenceAmount).stripTrailingZeros().toPlainString() } catch(e: Exception) { rate.referenceAmount.toString() },
                    currentAmountStr = try { java.math.BigDecimal.valueOf(rate.currentAmount).stripTrailingZeros().toPlainString() } catch(e: Exception) { rate.currentAmount.toString() }
                )
            }
        } else {
            val legacyBase = initialBaseCurrencyCode.ifBlank { "USD" }
            val legacyVal = initialValue.ifBlank { "1.0" }
            listOf(
                EditableExchangeRate(
                    id = 0L,
                    referenceCurrencyCode = legacyBase,
                    referenceAmountStr = "1",
                    currentAmountStr = legacyVal
                )
            )
        }
    }

    var exchangeRatesList by remember { mutableStateOf(initialRates) }
    var nextId by remember { mutableStateOf(initialRates.size.toLong()) }
    var errorText by remember { mutableStateOf("") }

    fun addRateEntry() {
        val newEntry = EditableExchangeRate(
            id = nextId++,
            referenceCurrencyCode = "USD",
            referenceAmountStr = "1",
            currentAmountStr = ""
        )
        exchangeRatesList = exchangeRatesList + newEntry
    }

    fun removeRateEntry(id: Long) {
        if (exchangeRatesList.size > 1) {
            exchangeRatesList = exchangeRatesList.filter { it.id != id }
        }
    }

    fun updateRateEntry(id: Long, updated: EditableExchangeRate) {
        exchangeRatesList = exchangeRatesList.map { if (it.id == id) updated else it }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (errorText.isNotEmpty()) {
                    Text(
                        text = errorText,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(Localization.get("name_label", languageCode)) },
                    placeholder = { Text(Localization.get("currency_name_placeholder", languageCode)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = symbol,
                        onValueChange = { symbol = it },
                        label = { Text(Localization.get("symbol_label", languageCode)) },
                        placeholder = { Text("e.g. $, ₿") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )

                    OutlinedTextField(
                        value = code,
                        onValueChange = { code = it },
                        label = { Text(Localization.get("code_label", languageCode)) },
                        placeholder = { Text("e.g. USD") },
                        enabled = !isEditMode,
                        singleLine = true,
                        modifier = Modifier.weight(1f).padRight()
                    )
                }

                Divider(modifier = Modifier.padding(vertical = 4.dp))

                Text(
                    text = if (languageCode == "fa") "لیست نرخ‌های مبادلاتی" else if (languageCode == "ru") "Список курсов обмена" else "Exchange Rates List",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(vertical = 2.dp)
                )

                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    exchangeRatesList.forEach { entry ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            var isRowDropdownExpanded by remember { mutableStateOf(false) }
                            Box(modifier = Modifier.weight(1.0f)) {
                                OutlinedTextField(
                                    value = entry.referenceCurrencyCode,
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text(if (languageCode == "fa") "ارز مرجع" else "Ref. Cur") },
                                    trailingIcon = {
                                        IconButton(onClick = { isRowDropdownExpanded = true }) {
                                            Icon(
                                                imageVector = Icons.Default.ArrowDropDown,
                                                contentDescription = "Select Ref Currency"
                                            )
                                        }
                                    },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth().clickable { isRowDropdownExpanded = true }
                                )

                                DropdownMenu(
                                    expanded = isRowDropdownExpanded,
                                    onDismissRequest = { isRowDropdownExpanded = false },
                                    modifier = Modifier.fillMaxWidth(0.5f)
                                ) {
                                    val options = (listOf("USD") + currencies.filter { it.code.uppercase().trim() != code.uppercase().trim() }.map { it.code }).distinct()
                                    options.forEach { optionCode ->
                                        DropdownMenuItem(
                                            text = { Text(optionCode) },
                                            onClick = {
                                                updateRateEntry(entry.id, entry.copy(referenceCurrencyCode = optionCode))
                                                isRowDropdownExpanded = false
                                            }
                                        )
                                    }
                                }
                            }

                            OutlinedTextField(
                                value = entry.referenceAmountStr,
                                onValueChange = {
                                    updateRateEntry(entry.id, entry.copy(referenceAmountStr = it))
                                },
                                label = { Text(if (languageCode == "fa") "مقدار مرجع" else "Ref. Amt") },
                                placeholder = { Text("1") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                singleLine = true,
                                modifier = Modifier.weight(0.9f)
                            )

                            Text("=", modifier = Modifier.padding(horizontal = 2.dp), style = MaterialTheme.typography.titleMedium)

                            OutlinedTextField(
                                value = entry.currentAmountStr,
                                onValueChange = {
                                    updateRateEntry(entry.id, entry.copy(currentAmountStr = it))
                                },
                                label = { Text(if (code.isBlank()) (if (languageCode == "fa") "ارز" else "Cur") else code.uppercase()) },
                                placeholder = { Text("rate") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                singleLine = true,
                                modifier = Modifier.weight(1.2f)
                            )

                            if (exchangeRatesList.size > 1) {
                                IconButton(
                                    onClick = { removeRateEntry(entry.id) },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Delete Rate",
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }
                }

                Button(
                    onClick = ::addRateEntry,
                    modifier = Modifier.align(Alignment.End),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer)
                ) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = "Add Rate")
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(if (languageCode == "fa") "افزودن نرخ دیگر" else if (languageCode == "ru") "Добавить курс" else "Add another rate")
                }

                Divider(modifier = Modifier.padding(vertical = 4.dp))

                val noteLabel = Localization.get("currency_note_label", languageCode)
                val noteHint = Localization.get("currency_note_placeholder", languageCode)
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text(noteLabel) },
                    placeholder = { Text(noteHint) },
                    singleLine = false,
                    maxLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isBlank() || symbol.isBlank() || code.isBlank()) {
                        errorText = Localization.get("error_invalid", languageCode)
                        return@Button
                    }
                    val cleanCode = code.uppercase().trim()
                    
                    val valRatesList = mutableListOf<ExchangeRate>()
                    for (entry in exchangeRatesList) {
                        val refCode = entry.referenceCurrencyCode.uppercase().trim()
                        val refAmt = entry.referenceAmountStr.toDoubleOrNull()
                        val curAmt = entry.currentAmountStr.toDoubleOrNull()
                        if (refCode.isBlank() || refAmt == null || refAmt <= 0.0 || curAmt == null || curAmt <= 0.0) {
                            errorText = if (languageCode == "fa") "لطفاً مقادیر و نرخ‌های معتبری وارد کنید" else "Please enter valid rate parameters"
                            return@Button
                        }
                        valRatesList.add(ExchangeRate(refCode, refAmt, curAmt))
                    }

                    val validationErr = validateExchangeRates(cleanCode, valRatesList, languageCode)
                    if (validationErr != null) {
                        errorText = validationErr
                        return@Button
                    }

                    onSave(name.trim(), symbol.trim(), cleanCode, valRatesList, notes.trim())
                }
            ) {
                Text(Localization.get("save", languageCode))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(Localization.get("cancel", languageCode))
            }
        }
    )
}

// Help padding helper extension
fun Modifier.padRight(): Modifier = this.padding(end = 2.dp)

@Composable
fun CalculatorContent(
    languageCode: String,
    calcExpression: String,
    onExpressionChange: (String) -> Unit,
    calcResult: String,
    onResultChange: (String) -> Unit,
    calcErrorMsg: String,
    onErrorChange: (String) -> Unit,
    isCalculatorPopUp: Boolean,
    onPopUpToggle: () -> Unit,
    calculatorSizeState: String,
    onSizeToggle: () -> Unit,
    onClose: () -> Unit,
    onPasteResult: (String) -> Unit,
    appName: String = "S C E"
) {
    if (isCalculatorPopUp) {
        // FULLSCREEN SAMSUNG-LIKE PROFESSIONAL CALCULATOR
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Header bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Calculate,
                        contentDescription = null,
                        tint = MintPrimary,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = Localization.get("calc_with_app_title", languageCode).format(appName),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    // Minimize button (Go inline)
                    IconButton(onClick = onPopUpToggle) {
                        Icon(
                            imageVector = Icons.Default.Compress,
                            contentDescription = "Minimize",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    // Close button
                    IconButton(onClick = onClose) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Display Screen representing high-end Samsung Display
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1.4f),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f)
                ),
                shape = RoundedCornerShape(24.dp),
                border = CardDefaults.outlinedCardBorder()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.Bottom,
                    horizontalAlignment = Alignment.End
                ) {
                    // Expression input line
                    Text(
                        text = calcExpression.ifEmpty { "0" },
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.Normal,
                            fontSize = 32.sp,
                            textAlign = TextAlign.End
                        ),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        maxLines = 2,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Result line
                    if (calcErrorMsg.isNotEmpty()) {
                        Text(
                            text = calcErrorMsg,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.End,
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else if (calcResult.isNotEmpty()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            // Quick transfer "Use / وارد کردن" button
                            Button(
                                onClick = { onPasteResult(calcResult) },
                                colors = ButtonDefaults.buttonColors(containerColor = MintPrimary),
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                            ) {
                                Icon(Icons.Default.Input, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = Localization.get("use_value_btn", languageCode),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Text(
                                text = "= $calcResult",
                                style = MaterialTheme.typography.headlineLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 42.sp,
                                    textAlign = TextAlign.End
                                ),
                                color = MintPrimary
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Samsung helper bar: Backspace between screen and keypads
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = {
                        if (calcExpression.isNotEmpty()) {
                            onExpressionChange(calcExpression.substring(0, calcExpression.length - 1))
                        }
                    },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Backspace,
                        contentDescription = "Backspace",
                        tint = MintPrimary,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Keyboard styled like Samsung standard
            val sKeys = listOf(
                listOf("C", "(", ")", "÷"),
                listOf("7", "8", "9", "×"),
                listOf("4", "5", "6", "−"),
                listOf("1", "2", "3", "+"),
                listOf("⌫", "0", ".", "=")
            )

            // Keys Grid covering the rest of the height dynamically via weight (extremely premium layout)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(4.0f),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                sKeys.forEach { rowKeys ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        rowKeys.forEach { keyLabel ->
                            val isClear = keyLabel == "C"
                            val isOperator = keyLabel in listOf("÷", "×", "−", "+", "=")
                            val isParensOrDelete = keyLabel in listOf("(", ")", "⌫")

                            val keyBgColor = if (isOperator) {
                                if (keyLabel == "=") MintPrimary else MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                            } else if (isClear) {
                                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f)
                            } else if (isParensOrDelete) {
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
                            }

                            val keyTextColor = if (isOperator) {
                                if (keyLabel == "=") MaterialTheme.colorScheme.onPrimary else MintPrimary
                            } else if (isClear) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            }

                            Button(
                                onClick = {
                                    when (keyLabel) {
                                        "C" -> {
                                            onExpressionChange("")
                                            onResultChange("")
                                            onErrorChange("")
                                        }
                                        "⌫" -> {
                                            if (calcExpression.isNotEmpty()) {
                                                onExpressionChange(calcExpression.substring(0, calcExpression.length - 1))
                                            }
                                        }
                                        "=" -> {
                                            try {
                                                if (calcExpression.isNotBlank()) {
                                                    val computed = MathEvaluator.evaluate(calcExpression)
                                                    val res = if (computed % 1.0 == 0.0) {
                                                        computed.toLong().toString()
                                                    } else {
                                                        String.format(Locale.US, "%.4f", computed).trimEnd('0').trimEnd('.')
                                                    }
                                                    onResultChange(res)
                                                    onErrorChange("")
                                                }
                                            } catch (e: Exception) {
                                                onErrorChange(Localization.get("math_error", languageCode))
                                                onResultChange("")
                                            }
                                        }
                                        else -> {
                                            onExpressionChange(calcExpression + keyLabel)
                                        }
                                    }
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .aspectRatio(1f),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = keyBgColor,
                                    contentColor = keyTextColor
                                ),
                                shape = CircleShape,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.15f)),
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Text(
                                    text = keyLabel,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 24.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    } else {
        // INLINE (SMALL CALC DISPLAY) - Keep the standard inline size scaling correctly
        val scaleVal = when (calculatorSizeState) {
            "small" -> 0.85f
            "large" -> 1.15f
            else -> 1.0f
        }
        val buttonFontSize = (14 * scaleVal).sp
        val buttonHeight = (38 * scaleVal).dp
        val keysSpacing = (5 * scaleVal).dp
        val headerTextSize = (16 * scaleVal).sp
        val descTextSize = (11 * scaleVal).sp

        Column(
            modifier = Modifier.padding((12 * scaleVal).dp),
            verticalArrangement = Arrangement.spacedBy((8 * scaleVal).dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy((6 * scaleVal).dp)
                ) {
                    Icon(
                        Icons.Default.Calculate,
                        contentDescription = null,
                        tint = MintPrimary,
                        modifier = Modifier.size((22 * scaleVal).dp)
                    )
                    Text(
                        text = Localization.get("calc_title", languageCode),
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = headerTextSize
                        )
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onPopUpToggle) {
                        Icon(
                            imageVector = Icons.Default.Fullscreen,
                            contentDescription = "Open Fullscreen",
                            modifier = Modifier.size(24.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }

                    IconButton(onClick = onClose) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close Calculator",
                            modifier = Modifier.size(24.dp),
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            Text(
                text = Localization.get("calc_desc", languageCode),
                style = MaterialTheme.typography.bodySmall.copy(fontSize = descTextSize),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )

            OutlinedTextField(
                value = calcExpression,
                onValueChange = { 
                    onExpressionChange(it)
                    onErrorChange("")
                },
                placeholder = { Text(Localization.get("calc_hint", languageCode), fontSize = buttonFontSize) },
                trailingIcon = {
                    if (calcExpression.isNotEmpty()) {
                        IconButton(onClick = { 
                            onExpressionChange("")
                            onResultChange("")
                            onErrorChange("")
                        }) {
                            Icon(Icons.Default.DeleteSweep, contentDescription = "Clear", modifier = Modifier.size(20.dp))
                        }
                    }
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                textStyle = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, fontSize = buttonFontSize)
            )

            if (calcResult.isNotEmpty() || calcErrorMsg.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = (2 * scaleVal).dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (calcErrorMsg.isNotEmpty()) {
                        Text(
                            text = calcErrorMsg,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, fontSize = buttonFontSize)
                        )
                    } else {
                        Text(
                            text = "= $calcResult",
                            style = MaterialTheme.typography.headlineSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = MintPrimary,
                                fontSize = (20 * scaleVal).sp
                            )
                        )

                        Button(
                            onClick = {
                                if (calcResult.isNotEmpty()) {
                                    onPasteResult(calcResult)
                                }
                            },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MintPrimary),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            modifier = Modifier.height(buttonHeight)
                        ) {
                            Icon(Icons.Default.Input, contentDescription = null, modifier = Modifier.size((14 * scaleVal).dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(Localization.get("calc_btn", languageCode), fontSize = (11 * scaleVal).sp)
                        }
                    }
                }
            }

            val calculatorKeys = listOf(
                listOf("7", "8", "9", "÷"),
                listOf("4", "5", "6", "×"),
                listOf("1", "2", "3", "−"),
                listOf("0", ".", "(", ")"),
                listOf("+", "C", "⌫", "=")
            )

            Column(
                verticalArrangement = Arrangement.spacedBy(keysSpacing),
                modifier = Modifier.fillMaxWidth()
            ) {
                calculatorKeys.forEach { rowKeys ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(keysSpacing),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        rowKeys.forEach { keyLabel ->
                            val isMathAction = keyLabel in listOf("+", "−", "×", "÷", "C", "⌫", "=")
                            val isFinalEquals = keyLabel == "="
                            val keyBgColor = if (isFinalEquals) {
                                MintPrimary
                            } else if (isMathAction) {
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                            } else {
                                MaterialTheme.colorScheme.surface
                            }
                            val keyTextColor = if (isFinalEquals) {
                                MaterialTheme.colorScheme.onPrimary
                            } else if (isMathAction) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            }

                            Button(
                                onClick = {
                                    when (keyLabel) {
                                        "C" -> {
                                            onExpressionChange("")
                                            onResultChange("")
                                            onErrorChange("")
                                        }
                                        "⌫" -> {
                                            if (calcExpression.isNotEmpty()) {
                                                onExpressionChange(calcExpression.substring(0, calcExpression.length - 1))
                                            }
                                        }
                                        "=" -> {
                                            try {
                                                if (calcExpression.isNotBlank()) {
                                                    val computed = MathEvaluator.evaluate(calcExpression)
                                                    val res = if (computed % 1.0 == 0.0) {
                                                        computed.toLong().toString()
                                                    } else {
                                                        String.format(Locale.US, "%.4f", computed).trimEnd('0').trimEnd('.')
                                                    }
                                                    onResultChange(res)
                                                    onErrorChange("")
                                                }
                                            } catch (e: Exception) {
                                                onErrorChange(Localization.get("calc_error", languageCode))
                                                onResultChange("")
                                            }
                                        }
                                        else -> {
                                            onExpressionChange(calcExpression + keyLabel)
                                        }
                                    }
                                },
                                modifier = Modifier.weight(1f).height(buttonHeight),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = keyBgColor,
                                    contentColor = keyTextColor
                                ),
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Text(
                                    text = keyLabel,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = buttonFontSize
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
