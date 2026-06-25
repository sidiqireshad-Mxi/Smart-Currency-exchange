package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import com.example.ui.theme.MintPrimary
import com.example.ui.theme.StarGold
import com.example.ui.viewmodel.CurrencyViewModel
import com.example.ui.util.Localization
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import androidx.compose.ui.text.style.TextOverflow

@Composable
fun SettingsScreen(
    viewModel: CurrencyViewModel,
    modifier: Modifier = Modifier
) {
    val themeMode by viewModel.themeMode.collectAsState()
    val displayUnit by viewModel.displayCurrencyUnit.collectAsState()
    val languageCode by viewModel.languageCode.collectAsState()
    val customAppName by viewModel.customAppName.collectAsState()
    val customAppIcon by viewModel.customAppIcon.collectAsState()
    val customAppIconUri by viewModel.customAppIconUri.collectAsState()

    // Support Developer state variables
    val supportSectionEnabled by viewModel.supportSectionEnabled.collectAsState()
    val supportWalletAddress by viewModel.supportWalletAddress.collectAsState()
    val supportQrCodeUri by viewModel.supportQrCodeUri.collectAsState()
    val supportCustomTextTop by viewModel.supportCustomTextTop.collectAsState()
    val supportCustomTextBottom by viewModel.supportCustomTextBottom.collectAsState()

    val uriHandler = LocalUriHandler.current

    var nameTapCount by remember { mutableStateOf(0) }
    var lastTapTime by remember { mutableStateOf(0L) }
    var showSecretPasswordDialog by remember { mutableStateOf(false) }
    var enteredPassword by remember { mutableStateOf("") }
    var passwordError by remember { mutableStateOf(false) }
    var showSecretPanel by remember { mutableStateOf(false) }
    var newPasswordInput by remember { mutableStateOf("") }

    var selectedGalleryUriState by remember { mutableStateOf<Uri?>(null) }

    // Launcher for picking images from device/gallery
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            selectedGalleryUriState = uri
        }
    }

    // Launcher for support section custom QR Code
    val qrCodeLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.updateSupportQrCodeUri(uri.toString())
        }
    }

    var appNameInput by remember { mutableStateOf(customAppName) }

    var walletAddressInput by remember { mutableStateOf(supportWalletAddress) }
    var textTopInput by remember { mutableStateOf(supportCustomTextTop) }
    var textBottomInput by remember { mutableStateOf(supportCustomTextBottom) }

    LaunchedEffect(customAppName) {
        appNameInput = customAppName
    }

    LaunchedEffect(supportWalletAddress) {
        walletAddressInput = supportWalletAddress
    }

    LaunchedEffect(supportCustomTextTop) {
        textTopInput = supportCustomTextTop
    }

    LaunchedEffect(supportCustomTextBottom) {
        textBottomInput = supportCustomTextBottom
    }

    val customApis by viewModel.customApisState.collectAsState()
    // Track which market section is expanded in API manual panel
    var expandedMarketSection by remember { mutableStateOf("GLOBAL") } // "GLOBAL" or "FREE"

    Scaffold(
        modifier = modifier
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header Content
            item {
                Text(
                    text = Localization.get("settings_header", languageCode),
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 16.dp, bottom = 4.dp)
                )
                Text(
                    text = Localization.get("settings_desc", languageCode),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }

            // Section 1: Language Selector (Compact, beautiful dropdown!)
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 2.dp
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Language, contentDescription = null, tint = MintPrimary)
                            }
                            Text(
                                text = Localization.get("lang_title", languageCode),
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Text(
                            text = Localization.get("lang_desc", languageCode),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )

                        Divider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))

                        var expanded by remember { mutableStateOf(false) }
                        val currentLangName = when (languageCode) {
                            "fa" -> "فارسی (Persian)"
                            "en" -> "English (US)"
                            "ar" -> "العربية (Arabic)"
                            "zh" -> "中文 (Chinese)"
                            "de" -> "Deutsch (German)"
                            "tr" -> "Türkçe (Turkish)"
                            "fr" -> "Français (French)"
                            "ru" -> "Русский (Russian)"
                            else -> "English (US)"
                        }

                        Box(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                                    .clickable { expanded = true }
                                    .padding(vertical = 12.dp, horizontal = 16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = currentLangName,
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Icon(
                                    imageVector = if (expanded) Icons.Default.ArrowDropUp else Icons.Default.ArrowDropDown,
                                    contentDescription = "Select Language",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }

                            DropdownMenu(
                                expanded = expanded,
                                onDismissRequest = { expanded = false },
                                modifier = Modifier
                                    .fillMaxWidth(0.9f)
                                    .background(MaterialTheme.colorScheme.surface)
                            ) {
                                val langs = listOf(
                                    "fa" to "فارسی (Persian)",
                                    "en" to "English (US)",
                                    "ar" to "العربية (Arabic)",
                                    "zh" to "中文 (Chinese)",
                                    "de" to "Deutsch (German)",
                                    "tr" to "Türkçe (Turkish)",
                                    "fr" to "Français (French)",
                                    "ru" to "Русский (Russian)"
                                )
                                langs.forEach { (code, label) ->
                                    DropdownMenuItem(
                                        text = { 
                                            Text(
                                                text = label, 
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = if (languageCode == code) FontWeight.Bold else FontWeight.Normal
                                            ) 
                                        },
                                        onClick = {
                                            viewModel.updateLanguage(code)
                                            expanded = false
                                        },
                                        trailingIcon = {
                                            if (languageCode == code) {
                                                Icon(
                                                    imageVector = Icons.Default.Check,
                                                    contentDescription = "Selected",
                                                    tint = MintPrimary,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Section 2: Currency Display Units
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 2.dp
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.PriceChange, contentDescription = null, tint = MintPrimary)
                            }
                            Text(
                                text = Localization.get("currency_display_title", languageCode),
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Text(
                            text = Localization.get("currency_display_desc", languageCode),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )

                        Divider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))

                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(Localization.get("toman_label", languageCode), style = MaterialTheme.typography.bodyMedium)
                                RadioButton(
                                    selected = displayUnit == "TOMAN",
                                    onClick = { viewModel.updateDisplayUnit("TOMAN") }
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(Localization.get("rial_label", languageCode), style = MaterialTheme.typography.bodyMedium)
                                RadioButton(
                                    selected = displayUnit == "RIAL",
                                    onClick = { viewModel.updateDisplayUnit("RIAL") }
                                )
                            }
                        }
                    }
                }
            }



            // Section 5: Themes
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 2.dp
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Palette, contentDescription = null, tint = MintPrimary)
                            }
                            Text(
                                text = Localization.get("theme_title", languageCode),
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Divider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))

                        val themes = listOf(
                            "system" to "theme_system",
                            "light" to "theme_light",
                            "dark" to "theme_dark"
                        )

                        themes.forEach { (key, langKey) ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(Localization.get(langKey, languageCode), style = MaterialTheme.typography.bodyMedium)
                                RadioButton(
                                    selected = themeMode == key,
                                    onClick = { viewModel.updateTheme(key) }
                                )
                            }
                        }
                    }
                }
            }

            // Section 6: About App (Updated with Telegram & personal website!)
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 2.dp
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Info, contentDescription = null, tint = MintPrimary)
                            }
                            Text(
                                text = Localization.get("about_title", languageCode),
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Divider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))

                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                  Text(Localization.get("about_name_label", languageCode), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                                  Text(customAppName, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                  Text(Localization.get("about_version_label", languageCode), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                                  Text("2.1", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                  Text(Localization.get("about_engine_label", languageCode), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                                  Text("SQLite Live Room DB", fontWeight = FontWeight.SemiBold, color = MintPrimary, style = MaterialTheme.typography.bodyMedium)
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        val currentTime = System.currentTimeMillis()
                                        if (currentTime - lastTapTime < 1500) {
                                            nameTapCount++
                                        } else {
                                            nameTapCount = 1
                                        }
                                        lastTapTime = currentTime
                                        if (nameTapCount >= 5) {
                                            nameTapCount = 0
                                            showSecretPasswordDialog = true
                                        }
                                    }
                                    .padding(vertical = 2.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = Localization.get("creator_label", languageCode),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                )
                                Text(
                                    text = "Mr.Maxii",
                                    fontWeight = FontWeight.Bold,
                                    color = MintPrimary,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                                             // Support the Developer Section
                        if (supportSectionEnabled) {
                            Divider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))

                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = Localization.get("support_dev_title", languageCode),
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                        color = MintPrimary
                                    )
                                }
                                
                                Text(
                                    text = if (supportCustomTextTop.isNotBlank()) {
                                        supportCustomTextTop
                                    } else {
                                        Localization.get("support_dev_desc", languageCode)
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                                    lineHeight = 18.sp
                                )

                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                    ),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                                ) {
                                    Column(
                                        modifier = Modifier.padding(12.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        val clipboardManager = LocalClipboardManager.current
                                        val context = LocalContext.current

                                        Text(
                                            text = "USDT (BEP20) Wallet Address:",
                                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                            color = MaterialTheme.colorScheme.onSurface
                                        )

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                text = supportWalletAddress,
                                                style = MaterialTheme.typography.bodySmall.copy(
                                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                                    fontWeight = FontWeight.SemiBold
                                                ),
                                                color = MintPrimary,
                                                modifier = Modifier.weight(1f)
                                            )
                                            IconButton(
                                                onClick = {
                                                    clipboardManager.setText(AnnotatedString(supportWalletAddress))
                                                    android.widget.Toast.makeText(
                                                        context,
                                                        Localization.get("wallet_copied_msg", languageCode),
                                                        android.widget.Toast.LENGTH_SHORT
                                                    ).show()
                                                },
                                                modifier = Modifier.size(32.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.ContentCopy,
                                                    contentDescription = "Copy Wallet Address",
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }

                                        // Display the QR Code image (centered, beautifully styled)
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 8.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (supportQrCodeUri.isNotBlank()) {
                                                AsyncImage(
                                                    model = supportQrCodeUri,
                                                    contentDescription = "USDT QR Code",
                                                    modifier = Modifier
                                                        .size(160.dp)
                                                        .clip(RoundedCornerShape(8.dp))
                                                        .background(Color.White)
                                                        .padding(6.dp)
                                                )
                                            } else {
                                                androidx.compose.foundation.Image(
                                                    painter = androidx.compose.ui.res.painterResource(id = com.example.R.drawable.usdt_qr),
                                                    contentDescription = "USDT QR Code",
                                                    modifier = Modifier
                                                        .size(160.dp)
                                                        .clip(RoundedCornerShape(8.dp))
                                                        .background(Color.White)
                                                        .padding(6.dp)
                                                )
                                            }
                                        }

                                        // Warning message below the QR Code
                                        Text(
                                            text = if (supportCustomTextBottom.isNotBlank()) {
                                                supportCustomTextBottom
                                            } else {
                                                Localization.get("support_dev_warning", languageCode)
                                            },
                                            style = MaterialTheme.typography.bodySmall.copy(
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Medium
                                            ),
                                            color = MaterialTheme.colorScheme.error,
                                            lineHeight = 16.sp
                                        )
                                    }
                                }
                            }
                        }

                        Divider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))

                        Text(
                            text = Localization.get("about_links_header", languageCode),
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary
                        )

                        // Telegram link block
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { uriHandler.openUri("https://t.me/digit_bazar") },
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.Send, contentDescription = null, tint = MintPrimary, modifier = Modifier.size(18.dp))
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = Localization.get("about_telegram", languageCode),
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                                    )
                                    Text(
                                        text = "https://t.me/digit_bazar",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                Icon(Icons.Default.ArrowOutward, contentDescription = null, tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
                            }
                        }

                        // Website link block
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { uriHandler.openUri("https://digitbazar.carrd.co") },
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.08f)
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(MaterialTheme.colorScheme.tertiary.copy(alpha = 0.15f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.Launch, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(18.dp))
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = Localization.get("about_website", languageCode),
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                                    )
                                    Text(
                                        text = "https://digitbazar.carrd.co",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.tertiary
                                    )
                                }
                                Icon(Icons.Default.ArrowOutward, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.5f))
                            }
                        }
                    }
                }
            }

            // Section 2.5: Sync & Restore System Currencies (Moved under About!)
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 2.dp
                ) {
                    var showToastMessage by remember { mutableStateOf(false) }
                    var showConfirmDialog by remember { mutableStateOf(false) }

                    if (showConfirmDialog) {
                        AlertDialog(
                            onDismissRequest = { showConfirmDialog = false },
                            title = {
                                Text(
                                    text = Localization.get("sync_confirm_title", languageCode),
                                    fontWeight = FontWeight.Bold
                                )
                            },
                            text = {
                                Text(
                                    text = Localization.get("sync_confirm_msg", languageCode)
                                )
                            },
                            confirmButton = {
                                TextButton(
                                    onClick = {
                                        showConfirmDialog = false
                                        viewModel.syncAllCurrencies {
                                            showToastMessage = true
                                        }
                                    }
                                ) {
                                    Text(
                                        text = Localization.get("yes", languageCode),
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            },
                            dismissButton = {
                                TextButton(
                                    onClick = { showConfirmDialog = false }
                                ) {
                                    Text(
                                        text = Localization.get("no", languageCode),
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                }
                            }
                        )
                    }

                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.CloudSync, contentDescription = null, tint = MintPrimary)
                            }
                            Text(
                                text = Localization.get("sync_title", languageCode),
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Text(
                            text = Localization.get("sync_desc", languageCode),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )

                        Divider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))

                        Button(
                            onClick = {
                                showConfirmDialog = true
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                            Text(
                                text = Localization.get("sync_btn", languageCode), 
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }

                        if (showToastMessage) {
                            Text(
                                text = Localization.get("sync_success", languageCode),
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                color = MintPrimary,
                                modifier = Modifier.padding(top = 4.dp).fillMaxWidth(),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }

            // Section 7: Secret Settings Panel
            if (showSecretPanel) {
                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f),
                        tonalElevation = 4.dp,
                        border = CardDefaults.outlinedCardBorder()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.LockOpen,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = Localization.get("secret_panel_title", languageCode),
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                IconButton(onClick = { showSecretPanel = false }) {
                                    Icon(Icons.Default.Close, contentDescription = "Close Panel")
                                }
                            }

                            Text(
                                text = Localization.get("secret_panel_desc", languageCode),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                            )

                            Divider(color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.15f))

                            // Change password UI
                            Text(
                                text = Localization.get("change_pass_title", languageCode),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = newPasswordInput,
                                    onValueChange = { newPasswordInput = it },
                                    placeholder = { 
                                        Text(Localization.get("new_pass_placeholder", languageCode)) 
                                    },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                                Button(
                                    onClick = {
                                        if (newPasswordInput.isNotBlank()) {
                                            viewModel.updateSecretPassword(newPasswordInput.trim())
                                            newPasswordInput = ""
                                        }
                                    },
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text(Localization.get("save", languageCode))
                                }
                            }

                            Divider(color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.15f))

                            // 1. Dynamic App Name Configuration
                            Text(
                                text = Localization.get("change_app_name_title", languageCode),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = appNameInput,
                                    onValueChange = { appNameInput = it },
                                    placeholder = { 
                                        Text(Localization.get("new_app_name_placeholder", languageCode)) 
                                    },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                                Button(
                                    onClick = {
                                        if (appNameInput.isNotBlank()) {
                                            viewModel.updateCustomAppName(appNameInput.trim())
                                        }
                                    },
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text(Localization.get("save", languageCode))
                                }
                            }

                            Divider(color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.15f))

                            // 2. Dynamic App Icon Theme Choice & Gallery Picker
                            Text(
                                text = Localization.get("change_app_icon_title", languageCode),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold
                            )
                            
                            // Button to pick dynamic icons from user's gallery
                            Button(
                                onClick = { galleryLauncher.launch("image/*") },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.UploadFile, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                                Text(Localization.get("app_icon_gallery_btn", languageCode))
                            }

                            // Show premium preview of the selected image from gallery with an explicit save button
                            if (selectedGalleryUriState != null) {
                                Card(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                                    shape = RoundedCornerShape(14.dp)
                                ) {
                                    Column(
                                        modifier = Modifier.padding(14.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(
                                            text = Localization.get("picked_img_preview", languageCode),
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer
                                        )
                                        
                                        AsyncImage(
                                            model = selectedGalleryUriState,
                                            contentDescription = "Custom App Icon Preview",
                                            modifier = Modifier
                                                .size(80.dp)
                                                .clip(RoundedCornerShape(16.dp))
                                        )
                                        
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Button(
                                                onClick = {
                                                    selectedGalleryUriState?.let { uri ->
                                                        viewModel.updateCustomAppIconUri(uri.toString())
                                                        viewModel.updateCustomAppIcon("custom")
                                                    }
                                                },
                                                modifier = Modifier.weight(1f),
                                                shape = RoundedCornerShape(10.dp),
                                                colors = ButtonDefaults.buttonColors(containerColor = MintPrimary)
                                            ) {
                                                Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(
                                                    text = Localization.get("save_apply_log", languageCode),
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                            
                                            OutlinedButton(
                                                onClick = { selectedGalleryUriState = null },
                                                modifier = Modifier.weight(1f),
                                                shape = RoundedCornerShape(10.dp)
                                            ) {
                                                Text(
                                                    text = Localization.get("cancel", languageCode),
                                                    fontSize = 11.sp
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            // Preset icon design choices
                            val iconOptions = listOf(
                                "logo_modern" to "app_icon_modern",
                                "logo_gold" to "app_icon_gold",
                                "logo_neon" to "app_icon_neon",
                                "logo_vintage" to "app_icon_vintage"
                            )

                            iconOptions.forEach { (key, langKey) ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { viewModel.updateCustomAppIcon(key) }
                                        .padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        val borderColor = when(key) {
                                            "logo_gold" -> StarGold
                                            "logo_neon" -> MintPrimary
                                            "logo_vintage" -> MaterialTheme.colorScheme.tertiary
                                            else -> Color.Transparent
                                        }
                                        val borderWidth = if (borderColor != Color.Transparent) 1.5.dp else 0.dp
                                        androidx.compose.foundation.Image(
                                            painter = androidx.compose.ui.res.painterResource(id = com.example.R.drawable.img_app_icon),
                                            contentDescription = "App Logo Option",
                                            modifier = Modifier
                                                .size(24.dp)
                                                .clip(RoundedCornerShape(6.dp))
                                                .then(
                                                    if (borderWidth > 0.dp) {
                                                        Modifier.border(borderWidth, borderColor, RoundedCornerShape(6.dp))
                                                    } else {
                                                        Modifier
                                                    }
                                                )
                                        )
                                        Text(
                                            text = Localization.get(langKey, languageCode),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    }
                                    RadioButton(
                                        selected = customAppIcon == key,
                                        onClick = { viewModel.updateCustomAppIcon(key) },
                                        colors = RadioButtonDefaults.colors(
                                            selectedColor = MaterialTheme.colorScheme.primary,
                                            unselectedColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.5f)
                                        )
                                    )
                                }
                            }

                            // Showcase currently set image uri (if custom chosen)
                            if (customAppIcon == "custom") {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                                        .padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MintPrimary)
                                    Text(
                                        text = Localization.get("app_icon_custom", languageCode),
                                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }

                            Divider(color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.15f))

                            // Custom Support Developer configurations
                            Text(
                                text = Localization.get("support_wallet_settings", languageCode),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold
                            )

                            // 1. Toggle support section
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = Localization.get("enable_support_section", languageCode),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Switch(
                                    checked = supportSectionEnabled,
                                    onCheckedChange = { viewModel.updateSupportSectionEnabled(it) },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = MintPrimary,
                                        checkedTrackColor = MintPrimary.copy(alpha = 0.5f)
                                    )
                                )
                            }

                            // 2. Change Wallet Address
                            Text(
                                text = Localization.get("change_wallet_address", languageCode),
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = walletAddressInput,
                                    onValueChange = { walletAddressInput = it },
                                    placeholder = { 
                                        Text(Localization.get("new_wallet_placeholder", languageCode)) 
                                    },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                                Button(
                                    onClick = {
                                        if (walletAddressInput.isNotBlank()) {
                                            viewModel.updateSupportWalletAddress(walletAddressInput.trim())
                                        }
                                    },
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text(Localization.get("save", languageCode))
                                }
                            }

                            // 3. Change QR Code Image
                            Text(
                                text = Localization.get("change_wallet_qr", languageCode),
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = { qrCodeLauncher.launch("image/*") },
                                    modifier = Modifier.weight(1.5f),
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = Localization.get("pick_from_gallery", languageCode),
                                        fontSize = 11.sp
                                    )
                                }

                                if (supportQrCodeUri.isNotBlank()) {
                                    OutlinedButton(
                                        onClick = { viewModel.updateSupportQrCodeUri("") },
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Text(
                                            text = Localization.get("reset_image", languageCode),
                                            fontSize = 11.sp
                                        )
                                    }
                                }
                            }

                            // 4. Custom Text Top (Above wallet)
                            Text(
                                text = Localization.get("custom_text_above_wallet", languageCode),
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            OutlinedTextField(
                                value = textTopInput,
                                onValueChange = { textTopInput = it },
                                placeholder = { 
                                    Text(Localization.get("custom_text_above_placeholder", languageCode)) 
                                },
                                maxLines = 3,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = {
                                        viewModel.updateSupportCustomTextTop(textTopInput.trim())
                                    },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text(Localization.get("save", languageCode))
                                }
                                if (supportCustomTextTop.isNotBlank()) {
                                    OutlinedButton(
                                        onClick = {
                                            viewModel.updateSupportCustomTextTop("")
                                            textTopInput = ""
                                        },
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Text(Localization.get("reset", languageCode))
                                    }
                                }
                            }

                            // 5. Custom Text Bottom (Below wallet)
                            Text(
                                text = Localization.get("custom_text_below_wallet", languageCode),
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            OutlinedTextField(
                                value = textBottomInput,
                                onValueChange = { textBottomInput = it },
                                placeholder = { 
                                    Text(Localization.get("custom_text_below_placeholder", languageCode)) 
                                },
                                maxLines = 3,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = {
                                        viewModel.updateSupportCustomTextBottom(textBottomInput.trim())
                                    },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text(Localization.get("save", languageCode))
                                }
                                if (supportCustomTextBottom.isNotBlank()) {
                                    OutlinedButton(
                                        onClick = {
                                            viewModel.updateSupportCustomTextBottom("")
                                            textBottomInput = ""
                                        },
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Text(Localization.get("reset", languageCode))
                                    }
                                }
                            }

                            Divider(color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.15f))

                            // ── API Manual Section Header ──────────────────────────────────
                            Text(
                                text = if (languageCode == "fa") "تنظیم دستی API" else "API Manual",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                text = if (languageCode == "fa")
                                    "API های دلخواه خود را جایگزین منابع پیش‌فرض برنامه کنید."
                                else
                                    "Replace the app's default API sources with your own preferred endpoints.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                            )

                            val scope = rememberCoroutineScope()

                            // ── Helper composable: one category input card ─────────────────
                            @Composable
                            fun ApiCategoryCard(
                                marketType: String,   // "GLOBAL" or "FREE"
                                category: String,     // "FIAT", "CRYPTO", "PRECIOUS"
                                labelFa: String,
                                labelEn: String,
                                iconVector: androidx.compose.ui.graphics.vector.ImageVector,
                                descFa: String,
                                descEn: String
                            ) {
                                val categoryApis = customApis.filter { it.marketType == marketType && it.apiCategory == category }
                                var localName by remember { mutableStateOf("") }
                                var localUrl by remember { mutableStateOf("") }
                                var localPriority by remember { mutableStateOf("PRIMARY") }
                                var localTesting by remember { mutableStateOf(false) }
                                var localResult by remember { mutableStateOf<Boolean?>(null) }
                                var priorityDropdown by remember { mutableStateOf(false) }

                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                                    ),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Column(
                                        modifier = Modifier.padding(12.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        // Card title row
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Icon(
                                                imageVector = iconVector,
                                                contentDescription = null,
                                                modifier = Modifier.size(18.dp),
                                                tint = MintPrimary
                                            )
                                            Text(
                                                text = if (languageCode == "fa") labelFa else labelEn,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                        Text(
                                            text = if (languageCode == "fa") descFa else descEn,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                        )

                                        // Active APIs list for this category
                                        if (categoryApis.isNotEmpty()) {
                                            categoryApis.forEach { api ->
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .clip(RoundedCornerShape(8.dp))
                                                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.8f))
                                                        .padding(horizontal = 10.dp, vertical = 6.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.SpaceBetween
                                                ) {
                                                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                                        Text(
                                                            text = api.name,
                                                            style = MaterialTheme.typography.bodySmall,
                                                            fontWeight = FontWeight.SemiBold
                                                        )
                                                        Text(
                                                            text = api.url,
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                        Badge(
                                                            containerColor = when (api.priority) {
                                                                "PRIMARY" -> MintPrimary.copy(alpha = 0.25f)
                                                                "SECONDARY" -> StarGold.copy(alpha = 0.25f)
                                                                else -> MaterialTheme.colorScheme.surfaceVariant
                                                            }
                                                        ) {
                                                            Text(
                                                                text = api.priority,
                                                                fontSize = 9.sp,
                                                                modifier = Modifier.padding(2.dp)
                                                            )
                                                        }
                                                    }
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                                                    ) {
                                                        Switch(
                                                            checked = api.isEnabled,
                                                            onCheckedChange = { viewModel.toggleCustomApi(api.id, it) },
                                                            modifier = Modifier.size(40.dp, 24.dp)
                                                        )
                                                        IconButton(
                                                            onClick = { viewModel.deleteCustomApi(api.id) },
                                                            modifier = Modifier.size(32.dp)
                                                        ) {
                                                            Icon(
                                                                Icons.Default.Delete,
                                                                contentDescription = null,
                                                                tint = MaterialTheme.colorScheme.error,
                                                                modifier = Modifier.size(16.dp)
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                            Divider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                                        }

                                        // Add new API input fields
                                        OutlinedTextField(
                                            value = localName,
                                            onValueChange = { localName = it; localResult = null },
                                            placeholder = {
                                                Text(
                                                    if (languageCode == "fa") "نام منبع (مثلاً Bonbast)"
                                                    else "Source name (e.g. Bonbast)",
                                                    style = MaterialTheme.typography.labelSmall
                                                )
                                            },
                                            singleLine = true,
                                            modifier = Modifier.fillMaxWidth(),
                                            textStyle = MaterialTheme.typography.bodySmall
                                        )
                                        OutlinedTextField(
                                            value = localUrl,
                                            onValueChange = { localUrl = it; localResult = null },
                                            placeholder = {
                                                Text(
                                                    "https://api.example.com/rates.json",
                                                    style = MaterialTheme.typography.labelSmall
                                                )
                                            },
                                            singleLine = true,
                                            modifier = Modifier.fillMaxWidth(),
                                            textStyle = MaterialTheme.typography.bodySmall,
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done)
                                        )

                                        // Priority selector + Add button
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            // Priority dropdown
                                            Box(modifier = Modifier.weight(1f)) {
                                                OutlinedButton(
                                                    onClick = { priorityDropdown = true },
                                                    modifier = Modifier.fillMaxWidth(),
                                                    shape = RoundedCornerShape(8.dp),
                                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                                ) {
                                                    Icon(
                                                        Icons.Default.Star,
                                                        contentDescription = null,
                                                        modifier = Modifier.size(14.dp)
                                                    )
                                                    Spacer(Modifier.width(4.dp))
                                                    Text(localPriority, style = MaterialTheme.typography.labelSmall)
                                                }
                                                DropdownMenu(
                                                    expanded = priorityDropdown,
                                                    onDismissRequest = { priorityDropdown = false }
                                                ) {
                                                    listOf("PRIMARY", "SECONDARY", "FALLBACK").forEach { p ->
                                                        DropdownMenuItem(
                                                            text = { Text(p, style = MaterialTheme.typography.bodySmall) },
                                                            onClick = { localPriority = p; priorityDropdown = false }
                                                        )
                                                    }
                                                }
                                            }

                                            // Test & Add button
                                            Button(
                                                onClick = {
                                                    if (localName.isNotBlank() && localUrl.isNotBlank()) {
                                                        localTesting = true
                                                        localResult = null
                                                        scope.launch {
                                                            val ok = viewModel.testApiConnection(localUrl.trim())
                                                            localTesting = false
                                                            localResult = ok
                                                            if (ok) {
                                                                viewModel.addCustomApi(
                                                                    name = localName.trim(),
                                                                    url = localUrl.trim(),
                                                                    marketType = marketType,
                                                                    priority = localPriority,
                                                                    apiCategory = category
                                                                )
                                                                localName = ""
                                                                localUrl = ""
                                                                localResult = null
                                                            }
                                                        }
                                                    }
                                                },
                                                modifier = Modifier.weight(2f),
                                                enabled = !localTesting && localName.isNotBlank() && localUrl.isNotBlank(),
                                                shape = RoundedCornerShape(8.dp),
                                                colors = ButtonDefaults.buttonColors(containerColor = MintPrimary),
                                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
                                            ) {
                                                if (localTesting) {
                                                    CircularProgressIndicator(
                                                        modifier = Modifier.size(16.dp),
                                                        strokeWidth = 2.dp,
                                                        color = MaterialTheme.colorScheme.onPrimary
                                                    )
                                                } else {
                                                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                                                    Spacer(Modifier.width(4.dp))
                                                    Text(
                                                        if (languageCode == "fa") "تست و افزودن" else "Test & Add",
                                                        style = MaterialTheme.typography.labelSmall
                                                    )
                                                }
                                            }
                                        }

                                        // Connection result message
                                        when (localResult) {
                                            false -> Text(
                                                text = if (languageCode == "fa") "⚠ اتصال برقرار نشد" else "⚠ Could not connect to this URL",
                                                color = MaterialTheme.colorScheme.error,
                                                style = MaterialTheme.typography.labelSmall
                                            )
                                            true -> Text(
                                                text = if (languageCode == "fa") "✓ اتصال موفق بود" else "✓ Connection successful",
                                                color = MintPrimary,
                                                style = MaterialTheme.typography.labelSmall
                                            )
                                            null -> {}
                                        }
                                    }
                                }
                            }

                            // ── GLOBAL MARKET SECTION ─────────────────────────────────────
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        expandedMarketSection = if (expandedMarketSection == "GLOBAL") "" else "GLOBAL"
                                    },
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                                ),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    // Section header row (always visible)
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Icon(
                                                Icons.Default.Public,
                                                contentDescription = null,
                                                tint = MintPrimary,
                                                modifier = Modifier.size(20.dp)
                                            )
                                            Text(
                                                text = if (languageCode == "fa") "بازار جهانی" else "Global Market",
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer
                                            )
                                        }
                                        Icon(
                                            imageVector = if (expandedMarketSection == "GLOBAL") Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f)
                                        )
                                    }

                                    if (expandedMarketSection == "GLOBAL") {
                                        // FIAT card
                                        ApiCategoryCard(
                                            marketType = "GLOBAL",
                                            category = "FIAT",
                                            labelFa = "ارزهای فیات",
                                            labelEn = "Fiat Currencies",
                                            iconVector = Icons.Default.AccountBalance,
                                            descFa = "API برای دریافت نرخ ارزهای رسمی جهانی (USD، EUR، GBP، AFN و ...)",
                                            descEn = "API for global fiat exchange rates (USD, EUR, GBP, AFN, etc.)"
                                        )
                                        // CRYPTO card
                                        ApiCategoryCard(
                                            marketType = "GLOBAL",
                                            category = "CRYPTO",
                                            labelFa = "ارزهای دیجیتال",
                                            labelEn = "Cryptocurrencies",
                                            iconVector = Icons.Default.CurrencyBitcoin,
                                            descFa = "API برای دریافت قیمت لحظه‌ای رمزارزها (BTC، ETH، SOL و ...)",
                                            descEn = "API for live crypto prices (BTC, ETH, SOL, etc.)"
                                        )
                                        // PRECIOUS card
                                        ApiCategoryCard(
                                            marketType = "GLOBAL",
                                            category = "PRECIOUS",
                                            labelFa = "فلزات گرانبها",
                                            labelEn = "Precious Metals",
                                            iconVector = Icons.Default.Diamond,
                                            descFa = "API برای دریافت قیمت طلا، نقره، پلاتین و سایر فلزات ارزشمند",
                                            descEn = "API for gold, silver, platinum and other precious metal prices"
                                        )
                                    }
                                }
                            }

                            // ── FREE MARKET SECTION ───────────────────────────────────────
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        expandedMarketSection = if (expandedMarketSection == "FREE") "" else "FREE"
                                    },
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f)
                                ),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    // Section header row (always visible)
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Icon(
                                                Icons.Default.Storefront,
                                                contentDescription = null,
                                                tint = StarGold,
                                                modifier = Modifier.size(20.dp)
                                            )
                                            Text(
                                                text = if (languageCode == "fa") "بازار آزاد" else "Free Market",
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSecondaryContainer
                                            )
                                        }
                                        Icon(
                                            imageVector = if (expandedMarketSection == "FREE") Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.6f)
                                        )
                                    }

                                    if (expandedMarketSection == "FREE") {
                                        // FIAT card
                                        ApiCategoryCard(
                                            marketType = "FREE",
                                            category = "FIAT",
                                            labelFa = "ارزهای فیات",
                                            labelEn = "Fiat Currencies",
                                            iconVector = Icons.Default.AccountBalance,
                                            descFa = "API برای دریافت نرخ ارزها در بازار آزاد ایران (مثل BrsApi)",
                                            descEn = "API for free market fiat rates in Iran (e.g. BrsApi)"
                                        )
                                        // CRYPTO card
                                        ApiCategoryCard(
                                            marketType = "FREE",
                                            category = "CRYPTO",
                                            labelFa = "ارزهای دیجیتال",
                                            labelEn = "Cryptocurrencies",
                                            iconVector = Icons.Default.CurrencyBitcoin,
                                            descFa = "API برای دریافت قیمت رمزارزها در بازار آزاد یا صرافی‌های ایرانی",
                                            descEn = "API for crypto prices on free market or Iranian exchanges"
                                        )
                                        // PRECIOUS card
                                        ApiCategoryCard(
                                            marketType = "FREE",
                                            category = "PRECIOUS",
                                            labelFa = "فلزات گرانبها",
                                            labelEn = "Precious Metals",
                                            iconVector = Icons.Default.Diamond,
                                            descFa = "API برای دریافت قیمت طلا و سکه در بازار آزاد ایران",
                                            descEn = "API for gold and coin prices in the Iranian free market"
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Bottom space
            item { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }

    // Secret Password Verification Dialog
    if (showSecretPasswordDialog) {
        val currentPass by viewModel.secretPassword.collectAsState()
        AlertDialog(
            onDismissRequest = { 
                showSecretPasswordDialog = false 
                enteredPassword = ""
                passwordError = false
            },
            title = {
                Text(
                    text = Localization.get("secret_pass_title", languageCode),
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = Localization.get("secret_pass_desc", languageCode)
                    )
                    OutlinedTextField(
                        value = enteredPassword,
                        onValueChange = { 
                            enteredPassword = it 
                            passwordError = false
                        },
                        placeholder = { Text("••••••") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        isError = passwordError,
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (passwordError) {
                        Text(
                            text = Localization.get("incorrect_pass", languageCode),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (enteredPassword == currentPass) {
                            showSecretPasswordDialog = false
                            showSecretPanel = true
                            enteredPassword = ""
                            passwordError = false
                        } else {
                            passwordError = true
                        }
                    }
                ) {
                    Text(Localization.get("verify", languageCode))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { 
                        showSecretPasswordDialog = false 
                        enteredPassword = ""
                        passwordError = false
                    }
                ) {
                    Text(Localization.get("cancel", languageCode))
                }
            }
        )
    }
}
