package com.app.reserve.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel

/**
 * Holds the unlock state for the current activity instance. A ViewModel
 * survives rotation but not process death, so rotating does not re-prompt while
 * a fresh process always starts locked.
 */
class AppLockViewModel : ViewModel() {
    var armed by mutableStateOf(false)
    var unlocked by mutableStateOf(false)
    var stoppedAt by mutableLongStateOf(0L)
}
