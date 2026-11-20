package com.app.reserve.core.util

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager

object IconSwitcher {

    const val DEFAULT_ALIAS = "com.app.reserve.MainActivityDefault"
    const val GLASSY_ALIAS = "com.app.reserve.MainActivityGlassy"
    const val MONOCHROME_ALIAS = "com.app.reserve.MainActivityMonochrome"

    val aliases = listOf(
        DEFAULT_ALIAS,
        GLASSY_ALIAS,
        MONOCHROME_ALIAS
    )

    fun setAppIcon(context: Context, targetAlias: String) {
        val packageManager = context.packageManager

        for (alias in aliases) {
            val componentName = ComponentName(context, alias)
            val state = if (alias == targetAlias) {
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            } else {
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            }
            packageManager.setComponentEnabledSetting(
                componentName,
                state,
                PackageManager.DONT_KILL_APP
            )
        }
    }
}
