package com.app.reserve.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
    onSelect: (BottomDestination) -> Unit
) {
    val colors = LocalReserveColors.current
    Surface(color = colors.surface) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(colors.border)
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .height(64.dp)
            ) {
                BottomDestination.entries.forEach { destination ->
                    val selected = currentRoute == destination.route
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .height(64.dp)
                            .clickable { onSelect(destination) },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center
                    ) {
                        Icon(
                            imageVector = if (selected) destination.selectedIcon else destination.icon,
                            contentDescription = destination.label,
                            tint = if (selected) colors.primary else colors.textMed,
                            modifier = Modifier.size(24.dp)
                        )
                        Text(
                            text = destination.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (selected) colors.primary else colors.textMed,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }
        }
    }
}
