package com.app.reserve.feature.add

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.app.reserve.core.designsystem.LocalReserveColors
import com.app.reserve.core.designsystem.SheetHandle
import com.app.reserve.core.designsystem.radius_md
import com.app.reserve.core.designsystem.spacing_xxl

/** The + button now offers a planned reminder as well as normal ledger rows. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddChoiceSheet(
    onDismiss: () -> Unit,
    onAddExpense: () -> Unit,
    onAddIncome: () -> Unit,
    onAddToBePaid: () -> Unit
) {
    val colors = LocalReserveColors.current
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = colors.surface,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        tonalElevation = 0.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = spacing_xxl)
                .navigationBarsPadding()
        ) {
            SheetHandle()
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Add to Reserve",
                style = MaterialTheme.typography.titleLarge,
                color = colors.textBase
            )
            Spacer(modifier = Modifier.height(12.dp))
            AddChoiceRow(
                icon = Icons.Rounded.Add,
                title = "Expense",
                subtitle = "Log money you spent",
                onClick = onAddExpense
            )
            AddChoiceRow(
                icon = Icons.Rounded.Repeat,
                title = "Income",
                subtitle = "Log money you received",
                onClick = onAddIncome
            )
            AddChoiceRow(
                icon = Icons.Rounded.CalendarMonth,
                title = "To Be Paid",
                subtitle = "Plan a future payment or receipt",
                onClick = onAddToBePaid
            )
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun AddChoiceRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    val colors = LocalReserveColors.current
    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .padding(vertical = 4.dp),
        shape = RoundedCornerShape(radius_md),
        color = colors.fillLight,
        border = BorderStroke(1.dp, colors.strokeFaint)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = spacing_xxl, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = colors.primaryDefault,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = colors.textBase)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = colors.textLight)
            }
        }
    }
}
