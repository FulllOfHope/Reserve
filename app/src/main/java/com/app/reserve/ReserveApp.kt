package com.app.reserve

import androidx.biometric.BiometricManager
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.app.reserve.core.application.ReserveApplication
import com.app.reserve.core.database.TransactionEntity
import com.app.reserve.core.database.TransactionType
import com.app.reserve.core.designsystem.LocalReserveColors
import com.app.reserve.core.designsystem.PrimaryAddButton
import com.app.reserve.core.designsystem.ReserveTheme
import com.app.reserve.core.datastore.ThemeMode
import com.app.reserve.feature.add.AddTransactionScreen
import com.app.reserve.feature.budgets.BudgetsScreen
import com.app.reserve.feature.home.HomeScreen
import com.app.reserve.feature.insights.InsightsScreen
import com.app.reserve.feature.ledger.LedgerViewModel
import com.app.reserve.feature.manage.AccountsScreen
import com.app.reserve.feature.manage.CategoriesScreen
import com.app.reserve.feature.manage.RecurringScreen
import com.app.reserve.feature.settings.SettingsScreen
import com.app.reserve.feature.settings.SettingsViewModel
import com.app.reserve.feature.transactions.TransactionsScreen
import com.app.reserve.ui.AppLockScreen
import com.app.reserve.ui.AppLockViewModel
import com.app.reserve.ui.ReserveBottomBar
import com.app.reserve.ui.Routes
import kotlinx.coroutines.launch

@Composable
fun ReserveApp() {
    val context = LocalContext.current
    val application = context.applicationContext as ReserveApplication

    val ledgerViewModel: LedgerViewModel = viewModel(
        factory = viewModelFactory {
            initializer { LedgerViewModel(application.container.repository) }
        }
    )
    val settingsViewModel: SettingsViewModel = viewModel(
        factory = viewModelFactory {
            initializer { SettingsViewModel(application.container.settings) }
        }
    )

    val state by ledgerViewModel.state.collectAsStateWithLifecycle()
    val settings by settingsViewModel.settings.collectAsStateWithLifecycle()

    val darkTheme = when (settings?.themeMode ?: ThemeMode.SYSTEM) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    ReserveTheme(darkTheme = darkTheme) {
        val colors = LocalReserveColors.current
        val biometricAvailable = remember {
            BiometricManager.from(context).canAuthenticate(
                BiometricManager.Authenticators.BIOMETRIC_WEAK or
                    BiometricManager.Authenticators.DEVICE_CREDENTIAL
            ) == BiometricManager.BIOMETRIC_SUCCESS
        }

        val lock = viewModel<AppLockViewModel>()

        LaunchedEffect(settings) {
            val current = settings ?: return@LaunchedEffect
            if (!lock.armed) {
                lock.unlocked = !current.biometricLock || !biometricAvailable
                lock.armed = true
            }
        }

        val lifecycleOwner = LocalLifecycleOwner.current
        DisposableEffect(lifecycleOwner, settings?.biometricLock) {
            val observer = LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_STOP -> lock.stoppedAt = System.currentTimeMillis()
                    Lifecycle.Event.ON_START -> {
                        if (lock.stoppedAt > 0L && System.currentTimeMillis() - lock.stoppedAt > 30_000L) {
                            if (settings?.biometricLock == true && biometricAvailable) {
                                lock.unlocked = false
                            }
                        }
                        lock.stoppedAt = 0L
                    }
                    else -> Unit
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
        }

        if (settings == null) {
            Box(modifier = Modifier.fillMaxSize().background(colors.canvas))
            return@ReserveTheme
        }

        val locked = lock.armed &&
            settings?.biometricLock == true &&
            biometricAvailable &&
            !lock.unlocked

        if (locked) {
            AppLockScreen(onUnlocked = { lock.unlocked = true })
        } else {
            MainNavigation(
                state = state,
                ledgerViewModel = ledgerViewModel,
                settingsViewModel = settingsViewModel
            )
        }
    }
}

@Composable
private fun MainNavigation(
    state: com.app.reserve.feature.ledger.LedgerState,
    ledgerViewModel: LedgerViewModel,
    settingsViewModel: SettingsViewModel
) {
    val colors = LocalReserveColors.current
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val mainRoutes = remember {
        setOf(Routes.Home, Routes.Transactions, Routes.Insights, Routes.Settings)
    }

    val onDelete: (TransactionEntity) -> Unit = { transaction ->
        ledgerViewModel.requestDelete(transaction)
        scope.launch {
            val result = snackbarHostState.showSnackbar(
                message = "Transaction removed",
                actionLabel = "Undo",
                duration = SnackbarDuration.Long
            )
            if (result == SnackbarResult.ActionPerformed) {
                ledgerViewModel.undoDelete(transaction)
            } else {
                ledgerViewModel.commitDelete(transaction)
            }
        }
    }

    Scaffold(
        containerColor = colors.canvas,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            if (currentRoute in mainRoutes) {
                ReserveBottomBar(
                    currentRoute = currentRoute,
                    onSelect = { destination ->
                        navController.navigate(destination.route) {
                            popUpTo(Routes.Home) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
            }
        },
        floatingActionButton = {
            if (currentRoute in mainRoutes) {
                PrimaryAddButton(
                    onClick = {
                        navController.navigate(Routes.add(TransactionType.EXPENSE.name))
                    }
                )
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Routes.Home,
            modifier = Modifier.padding(innerPadding),
            enterTransition = { fadeIn(tween(150)) },
            exitTransition = { fadeOut(tween(150)) }
        ) {
            composable(Routes.Home) {
                HomeScreen(
                    state = state,
                    onOpenAdd = { navController.navigate(Routes.add(TransactionType.EXPENSE.name)) },
                    onEditTransaction = { transaction ->
                        navController.navigate(Routes.add(transaction.type.name, transaction.id))
                    },
                    onSeeAll = { navController.navigate(Routes.Transactions) },
                    onOpenBudgets = { navController.navigate(Routes.Budgets) },
                    onPreviousMonth = { ledgerViewModel.shiftMonth(-1) },
                    onNextMonth = { ledgerViewModel.shiftMonth(1) }
                )
            }
            composable(Routes.Transactions) {
                TransactionsScreen(
                    state = state,
                    onEditTransaction = { transaction ->
                        navController.navigate(Routes.add(transaction.type.name, transaction.id))
                    },
                    onDeleteTransaction = onDelete,
                    onPreviousMonth = { ledgerViewModel.shiftMonth(-1) },
                    onNextMonth = { ledgerViewModel.shiftMonth(1) }
                )
            }
            composable(Routes.Insights) {
                InsightsScreen(
                    state = state,
                    onPreviousMonth = { ledgerViewModel.shiftMonth(-1) },
                    onNextMonth = { ledgerViewModel.shiftMonth(1) }
                )
            }
            composable(Routes.Settings) {
                SettingsScreen(
                    state = state,
                    settings = settingsViewModel.settings.collectAsStateWithLifecycle().value,
                    onToggleBiometric = { settingsViewModel.setBiometricLock(it) },
                    onThemeMode = { settingsViewModel.setThemeMode(it) },
                    onOpenCategories = { navController.navigate(Routes.Categories) },
                    onOpenAccounts = { navController.navigate(Routes.Accounts) },
                    onOpenBudgets = { navController.navigate(Routes.Budgets) },
                    onOpenRecurring = { navController.navigate(Routes.Recurring) },
                    exportCsv = { ledgerViewModel.exportCsv() },
                    importCsv = { ledgerViewModel.importCsv(it) },
                    createBackup = { ledgerViewModel.createBackup() },
                    restoreBackup = { ledgerViewModel.restoreBackup(it) },
                    onBackupCreated = { settingsViewModel.markBackupNow() }
                )
            }
            composable(
                route = Routes.AddPattern,
                arguments = listOf(
                    navArgument("type") { type = NavType.StringType },
                    navArgument("id") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    }
                )
            ) { entry ->
                val initialType = runCatching {
                    TransactionType.valueOf(entry.arguments?.getString("type") ?: "EXPENSE")
                }.getOrDefault(TransactionType.EXPENSE)
                val editingId = entry.arguments?.getString("id")
                val existing = state.transactions.firstOrNull { it.id == editingId }
                AddTransactionScreen(
                    state = state,
                    initialType = initialType,
                    existing = existing,
                    onClose = { navController.navigateUp() },
                    onSave = { transaction, previous ->
                        ledgerViewModel.saveTransaction(transaction, previous)
                        navController.navigateUp()
                    },
                    onCreateRecurring = { rule ->
                        ledgerViewModel.saveRecurring(rule)
                    }
                )
            }
            composable(Routes.Budgets) {
                BudgetsScreen(
                    state = state,
                    onSaveBudget = { ledgerViewModel.saveBudget(it) },
                    onDeleteBudget = { ledgerViewModel.deleteBudget(it) },
                    onPreviousMonth = { ledgerViewModel.shiftMonth(-1) },
                    onNextMonth = { ledgerViewModel.shiftMonth(1) },
                    onBack = { navController.navigateUp() }
                )
            }
            composable(Routes.Categories) {
                CategoriesScreen(
                    state = state,
                    onSaveCategory = { ledgerViewModel.saveCategory(it) },
                    onDeleteCategory = { ledgerViewModel.deleteCategory(it) },
                    onBack = { navController.navigateUp() }
                )
            }
            composable(Routes.Accounts) {
                AccountsScreen(
                    state = state,
                    onSaveAccount = { ledgerViewModel.saveAccount(it) },
                    onDeleteAccount = { account, result ->
                        ledgerViewModel.deleteAccount(account, result)
                    },
                    onRestoreAccount = { ledgerViewModel.restoreAccount(it) },
                    onBack = { navController.navigateUp() }
                )
            }
            composable(Routes.Recurring) {
                RecurringScreen(
                    state = state,
                    onSaveRecurring = { ledgerViewModel.saveRecurring(it) },
                    onDeleteRecurring = { ledgerViewModel.deleteRecurring(it) },
                    onBack = { navController.navigateUp() }
                )
            }
        }
    }
}
