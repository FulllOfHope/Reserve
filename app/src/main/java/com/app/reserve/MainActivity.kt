package com.app.reserve

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        openAddSheet = intent.getBooleanExtra(EXTRA_OPEN_ADD_SHEET, false)
        openUpdateSheet = intent.getBooleanExtra(EXTRA_OPEN_UPDATE_SHEET, false)
        setContent {
            val repository = (application as ReserveApplication).container.updateRepository
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
    }

    companion object {
        const val EXTRA_OPEN_ADD_SHEET = "EXTRA_OPEN_ADD_SHEET"
        const val EXTRA_OPEN_UPDATE_SHEET = "EXTRA_OPEN_UPDATE_SHEET"
    }
}
