package com.app.reserve

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.PieChart
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.appwidget.updateAll
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
import com.app.reserve.core.designsystem.ReserveTheme
import com.app.reserve.core.designsystem.spacing_lg
import com.app.reserve.core.designsystem.spacing_xxl
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
import com.app.reserve.feature.widget.ReserveWidget
import com.app.reserve.ui.AppLockScreen
import com.app.reserve.ui.AppLockViewModel
import com.app.reserve.ui.ReserveBottomBar
import com.app.reserve.ui.Routes
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ReserveApp(
    initialOpenAddSheet: Boolean = false,
    onOpenAddSheetConsumed: () -> Unit = {}
) {
    val context = LocalContext.current
    val application = context.applicationContext as ReserveApplication

    val ledgerViewModel: LedgerViewModel = viewModel(
        factory = viewModelFactory {
            initializer { LedgerViewModel(application.container.repository) }
        }
    )
    val settingsViewModel: SettingsViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                SettingsViewModel(
                    settingsDataStore = application.container.settings,
                    smsImporter = application.container.smsImporter
                )
            }
        }
    )

    val state by ledgerViewModel.state.collectAsStateWithLifecycle()
    val settings by settingsViewModel.settings.collectAsStateWithLifecycle()

    val darkTheme = when (settings?.themeMode ?: ThemeMode.SYSTEM) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    ReserveTheme(
        darkTheme = darkTheme,
        primaryColorHex = settings?.primaryColorHex ?: "#0FB70F",
        amountsHidden = settings?.hideAmounts == true
    ) {
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
            Box(modifier = Modifier.fillMaxSize().background(colors.fillBase))
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
                settingsViewModel = settingsViewModel,
                initialOpenAddSheet = initialOpenAddSheet,
                onOpenAddSheetConsumed = onOpenAddSheetConsumed
            )
        }
    }
}

@Composable
private fun MainNavigation(
    state: com.app.reserve.feature.ledger.LedgerState,
    ledgerViewModel: LedgerViewModel,
    settingsViewModel: SettingsViewModel,
    initialOpenAddSheet: Boolean = false,
    onOpenAddSheetConsumed: () -> Unit = {}
) {
    val colors = LocalReserveColors.current
    val context = LocalContext.current
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val pagerState = rememberPagerState(pageCount = { 4 })

    // A widget tap asks for the add screen exactly once. Keeping this outside
    // the Home destination stops the request from firing again after the user
    // presses back and Home re-enters composition.
    var openAddSheetPending by rememberSaveable { mutableStateOf(initialOpenAddSheet) }

    LaunchedEffect(initialOpenAddSheet) {
        if (initialOpenAddSheet) openAddSheetPending = true
    }

    LaunchedEffect(openAddSheetPending) {
        if (!openAddSheetPending) return@LaunchedEffect
        openAddSheetPending = false
        onOpenAddSheetConsumed()
        if (navController.currentBackStackEntry?.destination?.route?.startsWith("add/") != true) {
            navController.navigate(Routes.add(TransactionType.EXPENSE.name))
        }
    }

    fun toast(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
    }

    fun readDocument(uri: android.net.Uri): String? = runCatching {
        context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
    }.getOrNull()

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val csv = ledgerViewModel.exportCsv()
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openOutputStream(uri)?.use { output ->
                        output.write(csv.toByteArray())
                    } != null
                }.getOrDefault(false)
            }
            toast(if (ok) "CSV exported." else "Could not write that file.")
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val text = withContext(Dispatchers.IO) { readDocument(uri) }
            if (text == null) {
                toast("Could not read that file.")
                return@launch
            }
            val summary = ledgerViewModel.importCsv(text)
            toast(
                if (summary.skipped < 0) {
                    "That file is not a Reserve CSV."
                } else {
                    "Imported ${summary.imported} transactions" +
                        if (summary.skipped > 0) ", skipped ${summary.skipped}." else "."
                }
            )
        }
    }

    val backupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val json = ledgerViewModel.createBackup()
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openOutputStream(uri)?.use { output ->
                        output.write(json.toByteArray())
                    } != null
                }.getOrDefault(false)
            }
            if (ok) {
                settingsViewModel.markBackupNow()
                toast("Backup saved.")
            } else {
                toast("Could not write that backup.")
            }
        }
    }

    val restoreLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val text = withContext(Dispatchers.IO) { readDocument(uri) }
            if (text == null) {
                toast("Could not read that file.")
                return@launch
            }
            val restored = ledgerViewModel.restoreBackup(text)
            toast(if (restored) "Backup restored." else "That backup could not be read.")
        }
    }

    val smsPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions.entries.all { it.value }
        if (granted) {
            toast("Scanning your SMS inbox…")
            settingsViewModel.scanSms()
        } else {
            toast("SMS capture stays off until permission is granted.")
        }
    }

    LaunchedEffect(Unit) {
        if (androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.READ_SMS
            ) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            smsPermissionLauncher.launch(
                arrayOf(
                    android.Manifest.permission.RECEIVE_SMS,
                    android.Manifest.permission.READ_SMS
                )
            )
        }
    }

    // Ask for permission on first use, otherwise scan immediately. The
    // permission callback kicks off the same scan once the user approves.
    val onScanSms: () -> Unit = {
        if (androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.READ_SMS
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            settingsViewModel.scanSms()
        } else {
            smsPermissionLauncher.launch(
                arrayOf(
                    android.Manifest.permission.RECEIVE_SMS,
                    android.Manifest.permission.READ_SMS
                )
            )
        }
    }

    val onToggleHideAmounts: (Boolean) -> Unit = { hidden ->
        settingsViewModel.setHideAmounts(hidden) {
            scope.launch { ReserveWidget().updateAll(context) }
        }
    }

    val onDelete: (TransactionEntity) -> Unit = { transaction ->
        ledgerViewModel.commitDelete(transaction)
        scope.launch {
            snackbarHostState.showSnackbar(
                message = "Transaction deleted",
                duration = SnackbarDuration.Short
            )
        }
    }

    // Keep the widget's balance and month totals in step with the ledger.
    LaunchedEffect(state.transactions) {
        if (!state.loading) ReserveWidget().updateAll(context)
    }

    val drawerState = rememberDrawerState(initialValue = androidx.compose.material3.DrawerValue.Closed)
    val isHomeActive = currentRoute == Routes.Home && pagerState.currentPage == 0

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = isHomeActive,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = colors.fillLight
            ) {
                // Header with Logo
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 24.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_launcher_monochrome),
                        contentDescription = "App Logo",
                        modifier = Modifier
                            .size(42.dp)
                            .clip(RoundedCornerShape(10.dp))
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        text = "Reserve",
                        style = MaterialTheme.typography.headlineSmall,
                        color = colors.textBase
                    )
                }

                NavigationDrawerItem(
                    icon = { Icon(Icons.Outlined.Category, contentDescription = null, tint = colors.textBase) },
                    label = { Text("Categories", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = colors.textBase) },
                    selected = false,
                    onClick = {
                        scope.launch { drawerState.close() }
                        navController.navigate(Routes.Categories) {
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                )

                NavigationDrawerItem(
                    icon = { Icon(Icons.Outlined.AccountBalance, contentDescription = null, tint = colors.textBase) },
                    label = { Text("Accounts", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = colors.textBase) },
                    selected = false,
                    onClick = {
                        scope.launch { drawerState.close() }
                        navController.navigate(Routes.Accounts) {
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                )

                NavigationDrawerItem(
                    icon = { Icon(Icons.Outlined.PieChart, contentDescription = null, tint = colors.textBase) },
                    label = { Text("Budgets", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = colors.textBase) },
                    selected = false,
                    onClick = {
                        scope.launch { drawerState.close() }
                        navController.navigate(Routes.Budgets) {
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                )

                NavigationDrawerItem(
                    icon = { Icon(Icons.Outlined.Repeat, contentDescription = null, tint = colors.textBase) },
                    label = { Text("Recurring", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = colors.textBase) },
                    selected = false,
                    onClick = {
                        scope.launch { drawerState.close() }
                        navController.navigate(Routes.Recurring) {
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                )

                NavigationDrawerItem(
                    icon = { Icon(Icons.Outlined.Settings, contentDescription = null, tint = colors.textBase) },
                    label = { Text("Settings", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = colors.textBase) },
                    selected = false,
                    onClick = {
                        scope.launch {
                            drawerState.close()
                            if (currentRoute == Routes.Home) {
                                pagerState.animateScrollToPage(3, animationSpec = tween(200))
                            } else {
                                navController.navigate(Routes.Settings) {
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        }
                    },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                )

                NavigationDrawerItem(
                    icon = { Icon(Icons.Outlined.FileUpload, contentDescription = null, tint = colors.textBase) },
                    label = { Text("Export CSV", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = colors.textBase) },
                    selected = false,
                    onClick = {
                        scope.launch { drawerState.close() }
                        exportLauncher.launch("reserve-${LocalDate.now()}.csv")
                    },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                )

                NavigationDrawerItem(
                    icon = { Icon(Icons.Outlined.FileDownload, contentDescription = null, tint = colors.textBase) },
                    label = { Text("Import CSV", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = colors.textBase) },
                    selected = false,
                    onClick = {
                        scope.launch { drawerState.close() }
                        importLauncher.launch(arrayOf("text/csv", "text/comma-separated-values", "text/plain"))
                    },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                )

                NavigationDrawerItem(
                    icon = { Icon(Icons.Outlined.Backup, contentDescription = null, tint = colors.textBase) },
                    label = { Text("Create Backup", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = colors.textBase) },
                    selected = false,
                    onClick = {
                        scope.launch { drawerState.close() }
                        backupLauncher.launch("reserve-backup-${LocalDate.now()}.json")
                    },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                )

                NavigationDrawerItem(
                    icon = { Icon(Icons.Outlined.Restore, contentDescription = null, tint = colors.textBase) },
                    label = { Text("Restore Backup", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = colors.textBase) },
                    selected = false,
                    onClick = {
                        scope.launch { drawerState.close() }
                        restoreLauncher.launch(arrayOf("application/json", "text/plain"))
                    },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                )
            }
        }
    ) {
        Scaffold(
            containerColor = colors.fillBase,
            snackbarHost = { SnackbarHost(snackbarHostState) }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .consumeWindowInsets(ScaffoldDefaults.contentWindowInsets)
            ) {
                NavHost(
                    navController = navController,
                    startDestination = Routes.Home
                ) {
                    composable(Routes.Home) {
                        Column(modifier = Modifier.fillMaxSize()) {
                            // Dynamic Tab Header (Single Title Rule, Anti-Jitter, 56.dp static height)
                            val headerTitle = when (pagerState.currentPage) {
                                0 -> "Reserve"
                                1 -> "Transactions"
                                2 -> "Insights"
                                3 -> "Settings"
                                else -> "Reserve"
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(56.dp)
                                    .padding(horizontal = spacing_lg),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                    Icon(
                                        imageVector = Icons.Rounded.Menu,
                                        contentDescription = "Menu",
                                        tint = colors.textBase
                                    )
                                }
                                Spacer(modifier = Modifier.width(spacing_lg))
                                Text(
                                    text = headerTitle,
                                    style = MaterialTheme.typography.displayLarge.copy(fontSize = 24.sp, fontWeight = FontWeight.ExtraBold),
                                    color = colors.textBase,
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(onClick = { navController.navigate(Routes.add(TransactionType.EXPENSE.name)) }) {
                                    Icon(
                                        imageVector = Icons.Rounded.Add,
                                        contentDescription = "Add Expense",
                                        tint = colors.textBase
                                    )
                                }
                            }

                            HorizontalPager(
                                state = pagerState,
                                modifier = Modifier.weight(1f)
                            ) { page ->
                                when (page) {
                                    0 -> HomeScreen(
                                        state = state,
                                        onOpenAdd = { navController.navigate(Routes.add(TransactionType.EXPENSE.name)) },
                                        onEditTransaction = { transaction ->
                                            navController.navigate(Routes.add(transaction.type.name, transaction.id))
                                        },
                                        onDeleteTransaction = onDelete,
                                        onSeeAll = { scope.launch { pagerState.animateScrollToPage(1, animationSpec = tween(200)) } },
                                        onOpenBudgets = { navController.navigate(Routes.Budgets) },
                                        onPreviousMonth = { ledgerViewModel.shiftMonth(-1) },
                                        onNextMonth = { ledgerViewModel.shiftMonth(1) },
                                        onOpenMenu = { scope.launch { drawerState.open() } },
                                        onToggleHideAmounts = onToggleHideAmounts
                                    )
                                    1 -> TransactionsScreen(
                                        state = state,
                                        onEditTransaction = { transaction ->
                                            navController.navigate(Routes.add(transaction.type.name, transaction.id))
                                        },
                                        onDeleteTransaction = onDelete,
                                        onPreviousMonth = { ledgerViewModel.shiftMonth(-1) },
                                        onNextMonth = { ledgerViewModel.shiftMonth(1) }
                                    )
                                    2 -> InsightsScreen(
                                        state = state,
                                        onPreviousMonth = { ledgerViewModel.shiftMonth(-1) },
                                        onNextMonth = { ledgerViewModel.shiftMonth(1) }
                                    )
                                    3 -> SettingsScreen(
                                        state = state,
                                        settings = settingsViewModel.settings.collectAsStateWithLifecycle().value,
                                        onToggleBiometric = { settingsViewModel.setBiometricLock(it) },
                                        onToggleHideAmounts = onToggleHideAmounts,
                                        onThemeMode = { settingsViewModel.setThemeMode(it) },
                                        onSetPrimaryColorHex = { settingsViewModel.setPrimaryColorHex(it) },
                                        onSetAppIconAlias = { settingsViewModel.setAppIconAlias(it) },
                                        onOpenCategories = { navController.navigate(Routes.Categories) },
                                        onOpenAccounts = { navController.navigate(Routes.Accounts) },
                                        onOpenBudgets = { navController.navigate(Routes.Budgets) },
                                        onOpenRecurring = { navController.navigate(Routes.Recurring) },
                                        exportCsv = { ledgerViewModel.exportCsv() },
                                        importCsv = { ledgerViewModel.importCsv(it) },
                                        createBackup = { ledgerViewModel.createBackup() },
                                        restoreBackup = { ledgerViewModel.restoreBackup(it) },
                                        onBackupCreated = { settingsViewModel.markBackupNow() },
                                        smsScanning = settingsViewModel.smsScanning.collectAsStateWithLifecycle().value,
                                        smsScanMessage = settingsViewModel.smsScanMessage.collectAsStateWithLifecycle().value,
                                        onScanSms = onScanSms
                                    )
                                }
                            }
                        }
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
                            onToggleHideAmounts = onToggleHideAmounts,
                            onThemeMode = { settingsViewModel.setThemeMode(it) },
                            onSetPrimaryColorHex = { settingsViewModel.setPrimaryColorHex(it) },
                            onSetAppIconAlias = { settingsViewModel.setAppIconAlias(it) },
                            onOpenCategories = { navController.navigate(Routes.Categories) },
                            onOpenAccounts = { navController.navigate(Routes.Accounts) },
                            onOpenBudgets = { navController.navigate(Routes.Budgets) },
                            onOpenRecurring = { navController.navigate(Routes.Recurring) },
                            exportCsv = { ledgerViewModel.exportCsv() },
                            importCsv = { ledgerViewModel.importCsv(it) },
                            createBackup = { ledgerViewModel.createBackup() },
                            restoreBackup = { ledgerViewModel.restoreBackup(it) },
                            onBackupCreated = { settingsViewModel.markBackupNow() },
                            smsScanning = settingsViewModel.smsScanning.collectAsStateWithLifecycle().value,
                            smsScanMessage = settingsViewModel.smsScanMessage.collectAsStateWithLifecycle().value,
                            onScanSms = onScanSms
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

                if (currentRoute == Routes.Home) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        ReserveBottomBar(
                            currentRoute = currentRoute,
                            selectedIndex = pagerState.currentPage,
                            onSelectIndex = { index ->
                                scope.launch {
                                    pagerState.animateScrollToPage(index, animationSpec = tween(200))
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}
