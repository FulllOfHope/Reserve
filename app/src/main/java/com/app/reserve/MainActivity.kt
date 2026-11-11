package com.app.reserve

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.fragment.app.FragmentActivity

class MainActivity : FragmentActivity() {

    private var openAddSheet by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        openAddSheet = intent.getBooleanExtra(EXTRA_OPEN_ADD_SHEET, false)
        setContent {
            ReserveApp(
                initialOpenAddSheet = openAddSheet,
                onOpenAddSheetConsumed = {
                    intent.removeExtra(EXTRA_OPEN_ADD_SHEET)
                    openAddSheet = false
                }
            )
        }
    }

    /** A widget tap while Reserve is already open should still open one add sheet. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        openAddSheet = intent.getBooleanExtra(EXTRA_OPEN_ADD_SHEET, false)
    }

    companion object {
        const val EXTRA_OPEN_ADD_SHEET = "EXTRA_OPEN_ADD_SHEET"
    }
}
