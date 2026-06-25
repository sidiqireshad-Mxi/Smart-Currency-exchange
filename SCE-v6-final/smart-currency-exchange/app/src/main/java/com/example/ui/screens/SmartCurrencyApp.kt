package com.example.ui.screens

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.compose.*
import com.example.ui.theme.SmartCurrencyTheme
import com.example.ui.viewmodel.CurrencyViewModel
import com.example.ui.util.Localization

sealed class Screen(val route: String, val icon: ImageVector) {
    object OfflineMode : Screen("offline_mode", Icons.Default.CompareArrows)
    object OnlineMode : Screen("online_mode", Icons.Default.Cloud)
    object Calculator : Screen("calculator", Icons.Default.Calculate)
    object History : Screen("history", Icons.Default.History)
    object Settings : Screen("settings", Icons.Default.Settings)
}

@Composable
fun SmartCurrencyApp(
    viewModel: CurrencyViewModel
) {
    val themeMode by viewModel.themeMode.collectAsState()
    val languageCode by viewModel.languageCode.collectAsState()
    val customAppName by viewModel.customAppName.collectAsState()

    val useDarkTheme = when (themeMode) {
        "light" -> false
        "dark" -> true
        else -> isSystemInDarkTheme()
    }

    SmartCurrencyTheme(darkTheme = useDarkTheme) {
        val isInPipMode by viewModel.isInPipMode.collectAsState()

        if (isInPipMode) {
            Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                PipCalculatorView(viewModel = viewModel)
            }
        } else {
            val navController = rememberNavController()
            val navBackStackEntry = navController.currentBackStackEntryAsState().value
            val currentRoute = navBackStackEntry?.destination?.route

            val items = listOf(
                Screen.OfflineMode,
                Screen.OnlineMode,
                Screen.Calculator,
                Screen.History,
                Screen.Settings
            )

            Scaffold(
                modifier = Modifier.fillMaxSize(),
                bottomBar = {
                    NavigationBar(
                        containerColor = Color.Transparent,
                        tonalElevation = 0.dp,
                        modifier = Modifier.height(72.dp)
                    ) {
                        items.forEach { screen ->
                            val labelText = when (screen) {
                                Screen.OfflineMode -> Localization.get("tab_offline", languageCode)
                                Screen.OnlineMode -> Localization.get("tab_online", languageCode)
                                Screen.Calculator -> Localization.get("calculator_short", languageCode)
                                Screen.History -> Localization.get("tab_history", languageCode)
                                Screen.Settings -> Localization.get("tab_settings", languageCode)
                            }

                            NavigationBarItem(
                                icon = { Icon(screen.icon, contentDescription = labelText, modifier = Modifier.size(20.dp)) },
                                label = { Text(labelText, fontSize = 9.sp) },
                                selected = currentRoute == screen.route,
                                onClick = {
                                    if (currentRoute != screen.route) {
                                        navController.navigate(screen.route) {
                                            popUpTo(navController.graph.startDestinationId) {
                                                saveState = true
                                            }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    }
                                }
                            )
                        }
                    }
                }
            ) { innerPadding ->
                NavHost(
                    navController = navController,
                    startDestination = Screen.OfflineMode.route,
                    modifier = Modifier.padding(innerPadding)
                ) {
                    composable(Screen.OfflineMode.route) {
                        OfflineModeScreen(viewModel = viewModel)
                    }
                    composable(Screen.OnlineMode.route) {
                        OnlineModeScreen(viewModel = viewModel)
                    }
                    composable(Screen.Calculator.route) {
                        CalculatorScreen(viewModel = viewModel)
                    }
                    composable(Screen.History.route) {
                        HistoryScreen(viewModel = viewModel)
                    }
                    composable(Screen.Settings.route) {
                        SettingsScreen(viewModel = viewModel)
                    }
                }
            }
        }
    }
}

@Composable
fun PipCalculatorView(viewModel: CurrencyViewModel) {
    val languageCode by viewModel.languageCode.collectAsState()
    val customAppName by viewModel.customAppName.collectAsState()
    var calcExpression by remember { mutableStateOf("") }
    var calcResult by remember { mutableStateOf("") }
    var calcErrorMsg by remember { mutableStateOf("") }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(4.dp)
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
            onPopUpToggle = {},
            calculatorSizeState = "medium",
            onSizeToggle = {},
            onClose = {},
            onPasteResult = { res ->
                viewModel.updateAmountInput(res)
                viewModel.saveCurrentConversionToHistory()
            },
            appName = customAppName
        )
    }
}
