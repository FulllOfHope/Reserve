package com.app.reserve.core.util

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager

object IconSwitcher {

    const val DEFAULT_ALIAS = "com.app.reserve.MainActivityDefault"
    const val GLASSY_ALIAS = "com.app.reserve.MainActivityGlassy"

    val aliases = listOf(
        DEFAULT_ALIAS,
        GLASSY_ALIAS
    )

    /** Aliases removed in later releases still map to the default icon. */
    private val legacyAliases = setOf("com.app.reserve.MainActivityMonochrome")

    fun normalizeAlias(alias: String?): String =
        if (alias in aliases) alias!! else DEFAULT_ALIAS

    fun isLegacyAlias(alias: String?): Boolean = alias in legacyAliases

    fun setAppIcon(context: Context, targetAlias: String) {
        val packageManager = context.packageManager
        val resolvedAlias = normalizeAlias(targetAlias)

        for (alias in aliases) {
            val componentName = ComponentName(context, alias)
            val state = if (alias == resolvedAlias) {
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
