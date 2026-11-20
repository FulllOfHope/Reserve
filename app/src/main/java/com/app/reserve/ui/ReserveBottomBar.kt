package com.app.reserve.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.PieChart
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.PieChart
import androidx.compose.material.icons.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.app.reserve.core.designsystem.LocalReserveColors

enum class BottomDestination(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val icon: ImageVector
) {
    Home(Routes.Home, "Home", Icons.Rounded.Home, Icons.Outlined.Home),
    Transactions(Routes.Transactions, "Transactions", Icons.Rounded.ReceiptLong, Icons.Outlined.ReceiptLong),
    Insights(Routes.Insights, "Insights", Icons.Rounded.PieChart, Icons.Outlined.PieChart),
    Settings(Routes.Settings, "Settings", Icons.Rounded.Settings, Icons.Outlined.Settings)
}

@Composable
fun ReserveBottomBar(
    currentRoute: String?,
    selectedIndex: Int = 0,
    onSelectIndex: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalReserveColors.current
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 14.dp)
            .height(68.dp),
        shape = RoundedCornerShape(26.dp),
        color = colors.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, colors.strokeFaint),
        shadowElevation = 0.dp,
        tonalElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            BottomDestination.entries.forEachIndexed { index, destination ->
                val selected = selectedIndex == index
                Box(
                    modifier = Modifier
                        .width(48.dp)
                        .height(44.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (selected) colors.fillLight else Color.Transparent)
                        .clickable { onSelectIndex(index) },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (selected) destination.selectedIcon else destination.icon,
                        contentDescription = destination.label,
                        tint = if (selected) colors.textBase else colors.textLight,
                        modifier = Modifier.height(22.dp).width(22.dp)
                    )
                }
            }
        }
    }
}
