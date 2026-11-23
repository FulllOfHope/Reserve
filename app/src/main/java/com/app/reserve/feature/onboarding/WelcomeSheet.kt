package com.app.reserve.feature.onboarding

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.automirrored.outlined.Message
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.WindowInsets
import com.app.reserve.core.designsystem.EnteButton
import com.app.reserve.core.designsystem.LocalReserveColors
import com.app.reserve.core.designsystem.SheetHandle
import com.app.reserve.core.designsystem.radius_md
import com.app.reserve.core.designsystem.spacing_lg
import com.app.reserve.core.designsystem.spacing_md
import com.app.reserve.core.designsystem.spacing_sm
import com.app.reserve.core.designsystem.spacing_xl
import com.app.reserve.core.designsystem.spacing_xxxl
import com.app.reserve.core.designsystem.tabular
import java.time.ZoneId
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

private enum class StartMode { SMS, MANUAL }

/**
 * First-run choice. The scan-start month becomes the hard floor for every
 * future inbox query, so a new user never imports years of unrelated alerts.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WelcomeSheet(
    defaultScanStartEpochMs: Long,
    onSmsSetup: (Long) -> Unit,
    onManualSetup: () -> Unit,
    onDismiss: () -> Unit
) {
    val colors = LocalReserveColors.current
    var mode by remember { mutableStateOf(StartMode.SMS) }
    // Current month first, then eleven earlier months. Every option is a real
    // month start so the scan floor is always exact.
    val months = remember {
        val zone = ZoneId.systemDefault()
        (0 until 12).map { offset ->
            val month = YearMonth.now(zone).minusMonths(offset.toLong())
            val epochMs = month.atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
            val label = month.format(DateTimeFormatter.ofPattern("MMM yyyy", Locale.getDefault()))
            epochMs to label
        }
    }
    var selectedMonth by remember {
        mutableStateOf(defaultScanStartEpochMs.takeIf { it > 0L } ?: months.first().first)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
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
                text = "Welcome to Reserve",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
                color = colors.textBase
            )
            Spacer(Modifier.height(spacing_sm))
            Text(
                text = "Choose how to begin. Your ledger stays on this device, offline.",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textLight
            )
            Spacer(Modifier.height(spacing_lg))

            ModeCard(
                icon = Icons.AutoMirrored.Outlined.Message,
                title = "Import from SMS",
                subtitle = "Bank alerts become transactions automatically.",
                selected = mode == StartMode.SMS,
                onClick = { mode = StartMode.SMS }
            )
            Spacer(Modifier.height(spacing_sm))
            ModeCard(
                icon = Icons.Outlined.Description,
                title = "Enter manually",
                subtitle = "Add transactions yourself, at your own pace.",
                selected = mode == StartMode.MANUAL,
                onClick = { mode = StartMode.MANUAL }
            )

            if (mode == StartMode.SMS) {
                Spacer(Modifier.height(spacing_lg))
                Text(
                    text = "Start scanning from",
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.textLight
                )
                Spacer(Modifier.height(spacing_sm))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(spacing_sm)
                ) {
                    months.forEach { (epochMs, label) ->
                        val selected = selectedMonth == epochMs
                        Box(
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .background(
                                    color = if (selected) colors.primaryContainer else colors.fillLight,
                                    shape = RoundedCornerShape(radius_md)
                                )
                                .then(
                                    if (selected) {
                                        Modifier.border(
                                            width = 1.dp,
                                            color = colors.primaryDefault,
                                            shape = RoundedCornerShape(radius_md)
                                        )
                                    } else {
                                        Modifier.border(
                                            width = 1.dp,
                                            color = colors.strokeFaint,
                                            shape = RoundedCornerShape(radius_md)
                                        )
                                    }
                                )
                                .clickable { selectedMonth = epochMs }
                                .padding(horizontal = spacing_md),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelMedium.tabular(),
                                color = if (selected) colors.primaryDefault else colors.textLight
                            )
                        }
                    }
                }
                Spacer(Modifier.height(spacing_sm))
                Text(
                    text = "Older messages are never read. You can change this later in Settings.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textLighter
                )
            }

            Spacer(Modifier.height(spacing_lg))
            EnteButton(
                text = if (mode == StartMode.SMS) "Continue with SMS" else "Start manually",
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    if (mode == StartMode.SMS) onSmsSetup(selectedMonth) else onManualSetup()
                }
            )
            Spacer(Modifier.height(spacing_sm))
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            ) {
                Text("Not now", color = colors.textLight)
            }
        }
    }
}

@Composable
private fun ModeCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val colors = LocalReserveColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(onClick = onClick, onClickLabel = title, role = Role.Button)
            .background(
                color = if (selected) colors.primaryContainer.copy(alpha = 0.10f) else colors.fillLight,
                shape = RoundedCornerShape(radius_md)
            )
            .then(
                if (selected) {
                    Modifier.border(
                        width = 1.dp,
                        color = colors.primaryDefault,
                        shape = RoundedCornerShape(radius_md)
                    )
                } else {
                    Modifier.border(
                        width = 1.dp,
                        color = colors.strokeFaint,
                        shape = RoundedCornerShape(radius_md)
                    )
                }
            )
            .padding(spacing_md),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(
                    color = if (selected) colors.primaryContainer else colors.fillLight,
                    shape = RoundedCornerShape(radius_md)
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (selected) colors.primaryDefault else colors.textLight,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(Modifier.size(spacing_md))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = colors.textBase
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = colors.textLighter
            )
        }
    }
}
