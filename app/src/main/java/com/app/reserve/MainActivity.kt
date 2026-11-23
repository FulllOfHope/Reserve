package com.app.reserve

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.app.reserve.core.application.ReserveApplication

class MainActivity : FragmentActivity() {

    private var openAddSheet by mutableStateOf(false)
    private var openUpdateSheet by mutableStateOf(false)
    private var openInstallUpdate by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        openAddSheet = intent.getBooleanExtra(EXTRA_OPEN_ADD_SHEET, false)
        openUpdateSheet = intent.getBooleanExtra(EXTRA_OPEN_UPDATE_SHEET, false)
        openInstallUpdate = intent.getBooleanExtra(EXTRA_OPEN_INSTALL_UPDATE, false)
        setContent {
            val container = (application as ReserveApplication).container
            val repository = container.updateRepository
            val reserveSettings by container.settings.settings
                .collectAsStateWithLifecycle(initialValue = null)
            val appLockEnabled = reserveSettings?.biometricLock == true

            // Block screenshots and the recent-apps preview whenever App Lock is
            // enabled, including the brief unlocked window after authentication.
            DisposableEffect(appLockEnabled) {
                if (appLockEnabled) {
                    window.setFlags(
                        WindowManager.LayoutParams.FLAG_SECURE,
                        WindowManager.LayoutParams.FLAG_SECURE
                    )
                } else {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                }
                onDispose { window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) }
            }

            val needNotificationPermission by repository.needNotificationPermission
                .collectAsStateWithLifecycle()
            val notificationPermissionLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission()
            ) { granted ->
                repository.onNotificationPermissionRequestHandled()
                if (granted) repository.onNotificationPermissionGranted()
            }
            LaunchedEffect(needNotificationPermission) {
                if (needNotificationPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }

            ReserveApp(
                initialOpenAddSheet = openAddSheet,
                onOpenAddSheetConsumed = {
                    intent.removeExtra(EXTRA_OPEN_ADD_SHEET)
                    openAddSheet = false
                },
                initialOpenUpdateSheet = openUpdateSheet,
                onOpenUpdateSheetConsumed = {
                    intent.removeExtra(EXTRA_OPEN_UPDATE_SHEET)
                    openUpdateSheet = false
                },
                initialOpenInstallUpdate = openInstallUpdate,
                onOpenInstallUpdateConsumed = {
                    intent.removeExtra(EXTRA_OPEN_INSTALL_UPDATE)
                    openInstallUpdate = false
                }
            )
        }
    }

    /** A widget tap or notification tap while Reserve is open should still work. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        openAddSheet = intent.getBooleanExtra(EXTRA_OPEN_ADD_SHEET, false)
        openUpdateSheet = intent.getBooleanExtra(EXTRA_OPEN_UPDATE_SHEET, false)
        openInstallUpdate = intent.getBooleanExtra(EXTRA_OPEN_INSTALL_UPDATE, false)
    }

    companion object {
        const val EXTRA_OPEN_ADD_SHEET = "EXTRA_OPEN_ADD_SHEET"
        const val EXTRA_OPEN_UPDATE_SHEET = "EXTRA_OPEN_UPDATE_SHEET"
        const val EXTRA_OPEN_INSTALL_UPDATE = "EXTRA_OPEN_INSTALL_UPDATE"
    }
}
