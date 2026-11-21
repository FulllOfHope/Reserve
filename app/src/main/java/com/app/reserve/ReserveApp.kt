package com.app.reserve

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.DeleteOutline
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
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.core.net.toUri
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
import com.app.reserve.core.designsystem.spacing_md
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
import com.app.reserve.feature.trash.TrashScreen
import com.app.reserve.feature.transactions.TransactionsScreen
import com.app.reserve.feature.update.UpdateSheet
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
    onOpenAddSheetConsumed: () -> Unit = {},
    initialOpenUpdateSheet: Boolean = false,
    onOpenUpdateSheetConsumed: () -> Unit = {}
) {
    val context = LocalContext.current
    val application = context.applicationContext as ReserveApplication
    val updateRepository = application.container.updateRepository
    val appScope = rememberCoroutineScope()

    val ledgerViewModel: LedgerViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                LedgerViewModel(
                    repository = application.container.repository,
                    settingsDataStore = application.container.settings
                )
            }
        }
    )
    val settingsViewModel: SettingsViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                SettingsViewModel(
                    settingsDataStore = application.container.settings,
                    smsImporter = application.container.smsImporter,
                    updateRepository = updateRepository
                )
            }
        }
    )

    val state by ledgerViewModel.state.collectAsStateWithLifecycle()
    val settings by settingsViewModel.settings.collectAsStateWithLifecycle()
    val updateChecking by settingsViewModel.updateChecking.collectAsStateWithLifecycle()
    val updateCheckMessage by settingsViewModel.updateCheckMessage.collectAsStateWithLifecycle()
    val availableUpdate by updateRepository.availableUpdate.collectAsStateWithLifecycle()

    var showUpdateSheet by rememberSaveable { mutableStateOf(initialOpenUpdateSheet) }
    LaunchedEffect(initialOpenUpdateSheet) {
        if (!initialOpenUpdateSheet) return@LaunchedEffect
        showUpdateSheet = true
        // A cold start may not have a cached release yet; refresh silently.
        if (availableUpdate == null) updateRepository.refreshForSheet()
    }
    LaunchedEffect(availableUpdate) {
        if (availableUpdate != null) showUpdateSheet = true
    }

    val darkTheme = when (settings?.themeMode ?: ThemeMode.SYSTEM) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    ReserveTheme(
        darkTheme = darkTheme,
        primaryColorHex = settings?.primaryColorHex ?: "#00B843",
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
                onOpenAddSheetConsumed = onOpenAddSheetConsumed,
                updateChecking = updateChecking,
                updateCheckMessage = updateCheckMessage
            )
        }

        // Deliberately outside the lock branch: a notification tap should show
        // the update details immediately, and they contain no private data.
        val update = availableUpdate
        if (showUpdateSheet && update != null) {
            UpdateSheet(
                update = update,
                onDownload = {
                    showUpdateSheet = false
                    onOpenUpdateSheetConsumed()
                    val intent = Intent(Intent.ACTION_VIEW, update.apkUrl.toUri()).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    runCatching { context.startActivity(intent) }
                },
                onLater = {
                    showUpdateSheet = false
                    onOpenUpdateSheetConsumed()
                    appScope.launch { updateRepository.dismiss() }
                },
                onSkipVersion = {
                    showUpdateSheet = false
                    onOpenUpdateSheetConsumed()
                    appScope.launch { updateRepository.skipVersion() }
                }
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
    onOpenAddSheetConsumed: () -> Unit = {},
    updateChecking: Boolean = false,
    updateCheckMessage: String? = null
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

    val onChooseBalanceAccounts: (Set<String>?) -> Unit = { ids ->
        ledgerViewModel.setBalanceAccountIds(ids) {
            scope.launch { ReserveWidget().updateAll(context) }
        }
    }

    val onDelete: (TransactionEntity) -> Unit = { transaction ->
        // Soft-delete first: the row leaves the ledger and its balance reverses
        // immediately, but a Undo tap can still bring it back exactly as it was.
        ledgerViewModel.requestDelete(transaction)
        scope.launch {
            val result = snackbarHostState.showSnackbar(
                message = "Transaction moved to Trash",
                actionLabel = "Undo",
                duration = SnackbarDuration.Long
            )
            if (result == SnackbarResult.ActionPerformed) {
                ledgerViewModel.undoDelete(transaction)
            }
        }
    }

    // Keep the widget's balance and month totals in step with the ledger.
    LaunchedEffect(state.transactions) {
        if (!state.loading) ReserveWidget().updateAll(context)
    }

    val drawerState = rememberDrawerState(initialValue = androidx.compose.material3.DrawerValue.Closed)

    @Composable
    fun DrawerItem(
        label: String,
        icon: androidx.compose.ui.graphics.vector.ImageVector,
        badgeCount: Int = 0,
        onClick: () -> Unit
    ) {
        NavigationDrawerItem(
            icon = { Icon(icon, contentDescription = null, tint = colors.textBase) },
            label = {
                Text(
                    label,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = colors.textBase
                )
            },
            badge = if (badgeCount > 0) {
                {
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(colors.primaryContainer)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            badgeCount.toString(),
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.primaryDefault
                        )
                    }
                }
            } else null,
            selected = false,
            onClick = {
                scope.launch { drawerState.close() }
                onClick()
            },
            modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
        )
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        // While open, the drawer owns horizontal drags on every pager tab — the
        // HorizontalPager would otherwise consume swipes on Transactions,
        // Insights and Settings and prevent swipe-to-close.
        gesturesEnabled = drawerState.isOpen,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = colors.fillBase,
                modifier = Modifier.width(280.dp)
            ) {
                // Header: official r ₹ mark inside a 44.dp squircle.
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 24.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(colors.surface)
                            .border(1.dp, colors.strokeFaint, RoundedCornerShape(14.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.ic_launcher_monochrome),
                            contentDescription = "Reserve",
                            modifier = Modifier.size(28.dp)
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(
                            "Reserve",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontSize = 20.sp, fontWeight = FontWeight.ExtraBold
                            ),
                            color = colors.textBase
                        )
                        Text(
                            "Offline Ledger",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                            color = colors.textLighter
                        )
                    }
                }

                // Group 1 — organise
                DrawerItem("Categories", Icons.Outlined.Category) {
                    navController.navigate(Routes.Categories) { launchSingleTop = true }
                }
                DrawerItem("Accounts", Icons.Outlined.AccountBalance) {
                    navController.navigate(Routes.Accounts) { launchSingleTop = true }
                }
                DrawerItem("Budgets", Icons.Outlined.PieChart) {
                    navController.navigate(Routes.Budgets) { launchSingleTop = true }
                }
                DrawerItem("Recurring", Icons.Outlined.Repeat) {
                    navController.navigate(Routes.Recurring) { launchSingleTop = true }
                }

                DrawerGroupDivider()

                // Group 2 — preferences
                DrawerItem("Settings", Icons.Outlined.Settings) {
                    scope.launch {
                        if (currentRoute == Routes.Home) {
                            pagerState.animateScrollToPage(3, animationSpec = tween(200))
                        } else {
                            navController.navigate(Routes.Settings) { launchSingleTop = true }
                        }
                    }
                }

                DrawerGroupDivider()

                // Group 3 — data
                DrawerItem("Export CSV", Icons.Outlined.FileUpload) {
                    exportLauncher.launch("reserve-${LocalDate.now()}.csv")
                }
                DrawerItem("Import CSV", Icons.Outlined.FileDownload) {
                    importLauncher.launch(arrayOf("text/csv", "text/comma-separated-values", "text/plain"))
                }
                DrawerItem("Create Backup", Icons.Outlined.Backup) {
                    backupLauncher.launch("reserve-backup-${LocalDate.now()}.json")
                }
                DrawerItem("Restore Backup", Icons.Outlined.Restore) {
                    restoreLauncher.launch(arrayOf("application/json", "text/plain"))
                }
                DrawerItem(
                    label = "Trash",
                    icon = Icons.Outlined.DeleteOutline,
                    badgeCount = state.trashedTransactions.size
                ) {
                    navController.navigate(Routes.Trash) { launchSingleTop = true }
                }
            }
        }
    ) {
        Scaffold(
            containerColor = colors.fillBase,
            snackbarHost = {
                SnackbarHost(
                    hostState = snackbarHostState,
                    modifier = Modifier
                        .navigationBarsPadding()
                        .padding(start = spacing_lg, end = spacing_lg, bottom = 88.dp)
                ) { data ->
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = colors.surface,
                        tonalElevation = 0.dp,
                        shadowElevation = 0.dp,
                        border = BorderStroke(1.dp, colors.strokeFaint)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = spacing_lg, vertical = spacing_md),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                data.visuals.message,
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.textBase,
                                modifier = Modifier.weight(1f)
                            )
                            data.visuals.actionLabel?.let { label ->
                                TextButton(onClick = { data.performAction() }) {
                                    Text(
                                        label,
                                        style = MaterialTheme.typography.labelLarge.copy(
                                            fontWeight = FontWeight.Bold
                                        ),
                                        color = colors.primaryDefault
                                    )
                                }
                            }
                        }
                    }
                }
            }
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
                                        onToggleHideAmounts = onToggleHideAmounts,
                                        onChooseBalanceAccounts = onChooseBalanceAccounts,
                                        onSetCategory = { transaction, categoryId ->
                                            ledgerViewModel.setTransactionCategory(transaction, categoryId)
                                        }
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
                                        onScanSms = onScanSms,
                                        updateChecking = updateChecking,
                                        updateCheckMessage = updateCheckMessage,
                                        onToggleUpdateChecks = settingsViewModel::setUpdateChecksEnabled,
                                        onCheckForUpdates = settingsViewModel::checkForUpdatesNow,
                                        onDismissUpdateCheckMessage = settingsViewModel::clearUpdateCheckMessage
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
                            onScanSms = onScanSms,
                            updateChecking = updateChecking,
                            updateCheckMessage = updateCheckMessage,
                            onToggleUpdateChecks = settingsViewModel::setUpdateChecksEnabled,
                            onCheckForUpdates = settingsViewModel::checkForUpdatesNow,
                            onDismissUpdateCheckMessage = settingsViewModel::clearUpdateCheckMessage
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
                    composable(Routes.Trash) {
                        TrashScreen(
                            state = state,
                            onBack = { navController.navigateUp() },
                            onRestore = { ledgerViewModel.restoreFromTrash(it) },
                            onDeleteForever = { ledgerViewModel.deleteForever(it) },
                            onEmptyTrash = { ledgerViewModel.emptyTrash() }
                        )
                    }
                }

                if (currentRoute == Routes.Home) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomCenter),
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

@Composable
private fun DrawerGroupDivider() {
    val colors = LocalReserveColors.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 12.dp)
            .height(1.dp)
            .background(colors.strokeFaint)
    )
}
