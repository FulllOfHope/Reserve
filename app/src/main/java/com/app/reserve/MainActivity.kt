package com.app.reserve

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.fragment.app.FragmentActivity

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val openAddSheet = intent.getBooleanExtra("EXTRA_OPEN_ADD_SHEET", false)
        setContent { ReserveApp(openAddSheet) }
    }
}
