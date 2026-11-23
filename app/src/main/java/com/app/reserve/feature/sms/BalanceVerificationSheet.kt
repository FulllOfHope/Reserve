package com.app.reserve.feature.sms

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.app.reserve.core.designsystem.EnteButton
import com.app.reserve.core.designsystem.LocalReserveColors
import com.app.reserve.core.designsystem.MoneyText
import com.app.reserve.core.designsystem.SheetHandle
import com.app.reserve.core.designsystem.radius_md
import com.app.reserve.core.designsystem.spacing_lg
import com.app.reserve.core.designsystem.spacing_md
import com.app.reserve.core.designsystem.spacing_sm
import com.app.reserve.core.designsystem.spacing_xl
import com.app.reserve.core.designsystem.spacing_xxxl
import com.app.reserve.core.designsystem.tabular
import com.app.reserve.core.domain.LedgerReconciliation.PendingBalanceDiscrepancy
import com.app.reserve.core.domain.Money

/**
 * One prompt from the persisted verification queue. The user decides what the
 * difference means; nothing is posted silently. "Not now" removes only this
 * account's prompt so the remaining queue can still be reviewed.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BalanceVerificationSheet(
    discrepancy: PendingBalanceDiscrepancy,
    position: Int,
    total: Int,
    onPostDifference: () -> Unit,
    onUseBankBalance: () -> Unit,
    onSetManualBalance: (Long) -> Unit,
    onSkip: () -> Unit
) {
    val colors = LocalReserveColors.current
    var manualEntry by remember { mutableStateOf(false) }
    var manualText by remember { mutableStateOf("") }
    val manualAmount = Money.parse(manualText)
    val difference = discrepancy.differenceMinor

    ModalBottomSheet(
        onDismissRequest = onSkip,
        containerColor = colors.surface,
        contentColor = colors.textBase,
        contentWindowInsets = { WindowInsets.ime },
        tonalElevation = 0.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = spacing_lg)
                .padding(bottom = spacing_xxxl)
        ) {
            SheetHandle()
            Spacer(Modifier.height(spacing_xl))

            Text(
                text = "Verify balance",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
                color = colors.textBase
            )
            Spacer(Modifier.height(spacing_sm))
            Text(
                text = if (total > 1) {
                    "${discrepancy.accountName} · $position of $total"
                } else {
                    discrepancy.accountName
                },
                style = MaterialTheme.typography.labelMedium,
                color = colors.textLighter
            )
            Spacer(Modifier.height(spacing_lg))

            Row(modifier = Modifier.fillMaxWidth()) {
                BalanceColumn(
                    label = "Reserve shows",
                    amount = discrepancy.reserveBalanceMinor,
                    modifier = Modifier.weight(1f)
                )
                BalanceColumn(
                    label = "Bank SMS shows",
                    amount = discrepancy.smsBalanceMinor,
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(Modifier.height(spacing_md))
            Text(
                text = "Difference",
                style = MaterialTheme.typography.labelSmall,
                color = colors.textLighter
            )
            MoneyText(
                amount = difference,
                showPlus = true,
                style = MaterialTheme.typography.titleMedium.tabular(),
                color = if (difference > 0L) colors.primaryDefault else colors.warningDefault
            )
            Spacer(Modifier.height(spacing_sm))
            Text(
                text = "Bank alerts can be wrong or out of date. Check your statement before choosing.",
                style = MaterialTheme.typography.bodySmall,
                color = colors.textLighter
            )
            Spacer(Modifier.height(spacing_lg))

            EnteButton(
                text = if (difference > 0L) "Post difference as income" else "Post difference as expense",
                modifier = Modifier.fillMaxWidth(),
                onClick = onPostDifference
            )
            Spacer(Modifier.height(spacing_sm))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .background(colors.fillLight, RoundedCornerShape(radius_md))
                    .then(
                        Modifier.border(
                            width = 1.dp,
                            color = colors.strokeFaint,
                            shape = RoundedCornerShape(radius_md)
                        )
                    )
                    .clickable(onClick = onUseBankBalance)
                    .padding(horizontal = spacing_lg),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Use bank balance",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                    color = colors.textBase
                )
            }

            TextButton(onClick = { manualEntry = !manualEntry }) {
                Text("Enter another balance", color = colors.primaryDefault)
            }
            if (manualEntry) {
                OutlinedTextField(
                    value = manualText,
                    onValueChange = { manualText = it },
                    label = { Text("Correct balance") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    shape = RoundedCornerShape(radius_md),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = colors.primaryDefault,
                        unfocusedBorderColor = colors.strokeFaint,
                        focusedTextColor = colors.textBase,
                        unfocusedTextColor = colors.textBase,
                        focusedLabelColor = colors.textLight,
                        unfocusedLabelColor = colors.textLighter,
                        cursorColor = colors.primaryDefault
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(spacing_sm))
                EnteButton(
                    text = "Save balance",
                    enabled = manualAmount != null,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { manualAmount?.let(onSetManualBalance) }
                )
            }

            TextButton(
                onClick = onSkip,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            ) {
                Text("Not now", color = colors.textLight)
            }
            Spacer(Modifier.height(spacing_md))
            Text(
                text = "Corrections adjust the opening balance. No fake transaction is added.",
                style = MaterialTheme.typography.labelSmall,
                color = colors.textLighter
            )
        }
    }
}

@Composable
private fun BalanceColumn(
    label: String,
    amount: Long,
    modifier: Modifier = Modifier
) {
    val colors = LocalReserveColors.current
    Column(
        modifier = modifier
            .background(colors.fillLight, RoundedCornerShape(radius_md))
            .then(
                Modifier.border(
                    width = 1.dp,
                    color = colors.strokeFaint,
                    shape = RoundedCornerShape(radius_md)
                )
            )
            .padding(spacing_md)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = colors.textLighter
        )
        Spacer(Modifier.height(spacing_sm))
        MoneyText(amount = amount, style = MaterialTheme.typography.titleMedium.tabular())
    }
}
