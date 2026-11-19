package com.app.reserve.core.designsystem

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Backspace
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.app.reserve.core.database.CategoryEntity
import com.app.reserve.core.database.TransactionEntity
import com.app.reserve.core.database.TransactionType
import com.app.reserve.core.domain.Dates

@Composable
fun ReserveCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val colors = LocalReserveColors.current
    val shape = RoundedCornerShape(radius_xl)
    if (onClick != null) {
        Surface(
            modifier = modifier.fillMaxWidth(),
            shape = shape,
            color = colors.fillLight,
            shadowElevation = 0.dp,
            onClick = onClick
        ) {
            Column(modifier = Modifier.padding(spacing_xl), content = content)
        }
    } else {
        Surface(
            modifier = modifier.fillMaxWidth(),
            shape = shape,
            color = colors.fillLight,
            shadowElevation = 0.dp
        ) {
            Column(modifier = Modifier.padding(spacing_xl), content = content)
        }
    }
}

@Composable
fun ScreenTitle(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    onMenu: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null
) {
    val colors = LocalReserveColors.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = spacing_lg, vertical = spacing_md),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.Rounded.ChevronLeft,
                    contentDescription = "Back",
                    tint = colors.textBase
                )
            }
        } else if (onMenu != null) {
            IconButton(onClick = onMenu) {
                Icon(
                    imageVector = Icons.Rounded.Menu,
                    contentDescription = "Menu",
                    tint = colors.textBase
                )
            }
        }
        
        Text(
            text = title,
            style = MaterialTheme.typography.displayLarge, // H1Nunito
            color = colors.textBase,
            modifier = Modifier
                .weight(1f)
                .padding(start = if (onBack == null && onMenu == null) 0.dp else spacing_sm)
        )
        
        if (trailing != null) {
            trailing()
        }
    }
}

@Composable
fun SectionLabel(
    text: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    val colors = LocalReserveColors.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = spacing_lg, vertical = spacing_sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = text.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = colors.textLighter,
            modifier = Modifier.weight(1f)
        )
        if (actionLabel != null && onAction != null) {
            Text(
                text = actionLabel,
                style = MaterialTheme.typography.labelLarge,
                color = colors.primaryDefault,
                modifier = Modifier
                    .clip(RoundedCornerShape(radius_sm))
                    .clickable(onClick = onAction)
                    .padding(horizontal = spacing_sm, vertical = spacing_xs)
            )
        }
    }
}

@Composable
fun MoneyText(
    amount: Long,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.titleMedium.tabular(),
    color: Color = LocalReserveColors.current.textBase,
    showPlus: Boolean = false,
    maxLines: Int = 1
) {
    val hidden = LocalAmountsHidden.current
    Text(
        text = formatMoney(amount, hidden = hidden, showPlus = showPlus),
        modifier = modifier,
        style = style.tabular(),
        color = color,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis
    )
}

@Composable
fun CategoryAvatar(
    category: CategoryEntity?,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp
) {
    val colors = LocalReserveColors.current
    val tint = category?.let { categoryColor(it.colorHex) } ?: colors.textLighter
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(EnteRadius.md))
            .background(colors.fillDarker),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = categoryIcon(category?.iconName ?: "other"),
            contentDescription = category?.name,
            tint = tint,
            modifier = Modifier.size(size * 0.5f)
        )
    }
}

@Composable
fun TransactionRow(
    transaction: TransactionEntity,
    category: CategoryEntity?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalReserveColors.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = EnteSpacing.lg, vertical = EnteSpacing.md),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CategoryAvatar(
            category = category,
            size = 40.dp
        )
        Spacer(modifier = Modifier.width(EnteSpacing.lg))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = when {
                    transaction.note.isNotBlank() -> transaction.note
                    category != null -> category.name
                    transaction.type == TransactionType.TRANSFER -> "Transfer"
                    else -> "Transaction"
                },
                style = MaterialTheme.typography.titleMedium,
                color = colors.textBase,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = Dates.fullDate(transaction.timestamp),
                style = MaterialTheme.typography.labelMedium,
                color = colors.textLight,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(modifier = Modifier.width(spacing_sm))
        val amountColor = when (transaction.type) {
            TransactionType.INCOME -> colors.primaryDefault
            TransactionType.EXPENSE -> colors.textBase
            TransactionType.TRANSFER -> colors.textBase
        }
        MoneyText(
            amount = if (transaction.type == TransactionType.EXPENSE) -transaction.amount else transaction.amount,
            style = MaterialTheme.typography.titleMedium.tabular(),
            color = amountColor,
            showPlus = transaction.type == TransactionType.INCOME
        )
    }
}

@Composable
fun ReserveDivider(modifier: Modifier = Modifier) {
    val colors = LocalReserveColors.current
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(1.dp)
            .padding(start = 72.dp, end = spacing_lg)
            .background(colors.strokeFaint)
    )
}

@Composable
fun MonthSwitcher(
    monthYear: String,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalReserveColors.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = spacing_lg),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        IconButton(onClick = onPrevious) {
            Icon(Icons.Rounded.ChevronLeft, contentDescription = "Previous month", tint = colors.textLight)
        }
        Text(
            text = Dates.monthLabel(monthYear),
            style = MaterialTheme.typography.titleMedium,
            color = colors.textBase,
            modifier = Modifier.padding(horizontal = spacing_sm)
        )
        IconButton(onClick = onNext) {
            Icon(Icons.Rounded.ChevronRight, contentDescription = "Next month", tint = colors.textLight)
        }
    }
}

@Composable
fun BudgetBar(
    spent: Long,
    limit: Long,
    modifier: Modifier = Modifier
) {
    val colors = LocalReserveColors.current
    val ratio = if (limit <= 0L) 0f else spent.toFloat() / limit.toFloat()
    val fill = when {
        ratio > 1f -> colors.warningDefault
        ratio >= 0.9f -> colors.warningDefault
        else -> colors.primaryDefault
    }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(RoundedCornerShape(radius_sm))
            .background(colors.fillDarker)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(ratio.coerceIn(0f, 1f))
                .height(8.dp)
                .clip(RoundedCornerShape(radius_sm))
                .background(fill)
        )
    }
}

@Composable
fun EmptyState(
    icon: ImageVector,
    text: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    val colors = LocalReserveColors.current
    AnimatedVisibility(
        visible = true,
        enter = slideInVertically(
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessMedium
            )
        ) { it / 2 } + fadeIn()
    ) {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = spacing_lg, vertical = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = colors.textLighter,
                modifier = Modifier.size(48.dp)
            )
            Spacer(modifier = Modifier.height(spacing_md))
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textLight,
                modifier = Modifier.padding(horizontal = spacing_lg)
            )
            if (actionLabel != null && onAction != null) {
                Spacer(modifier = Modifier.height(spacing_md))
                Text(
                    text = actionLabel,
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.primaryDefault,
                    modifier = Modifier
                        .clip(RoundedCornerShape(radius_sm))
                        .clickable(onClick = onAction)
                        .padding(horizontal = spacing_md, vertical = spacing_sm)
                )
            }
        }
    }
}

@Composable
fun PrimaryAddButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentDescription: String = "Add"
) {
    val colors = LocalReserveColors.current
    Surface(
        onClick = onClick,
        modifier = modifier.size(56.dp),
        shape = RoundedCornerShape(radius_xl),
        color = colors.primaryDefault
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = Icons.Rounded.Add,
                contentDescription = contentDescription,
                tint = colors.textReverse,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@Composable
fun EnteButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    containerColor: Color? = null
) {
    val colors = LocalReserveColors.current
    val bg = containerColor ?: colors.primaryDefault
    Surface(
        modifier = modifier
            .clip(CircleShape)
            .clickable(enabled = enabled, onClick = onClick),
        shape = CircleShape,
        color = if (enabled) bg else colors.fillDark
    ) {
        Row(
            modifier = Modifier.padding(horizontal = EnteSpacing.xxl, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (enabled) Color.White else colors.textLighter,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(EnteSpacing.sm))
            }
            Text(
                text = text,
                style = MaterialTheme.typography.titleMedium,
                color = if (enabled) Color.White else colors.textLighter
            )
        }
    }
}

@Composable
fun CategoryChip(
    category: CategoryEntity,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalReserveColors.current
    val accent = categoryColor(category.colorHex)
    Surface(
        onClick = onClick,
        modifier = modifier.height(48.dp),
        shape = RoundedCornerShape(radius_md),
        color = if (selected) accent.copy(alpha = 0.12f) else colors.fillLight,
        border = if (selected) BorderStroke(1.dp, accent) else null
    ) {
        Row(
            modifier = Modifier.padding(horizontal = spacing_md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = categoryIcon(category.iconName),
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(spacing_sm))
            Text(
                text = category.name,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textBase,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun AmountKeypad(
    onKey: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalReserveColors.current
    val haptics = LocalHapticFeedback.current
    val keys = listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9"),
        listOf(".", "0", "back")
    )
    Column(modifier = modifier.fillMaxWidth()) {
        keys.forEach { row ->
            Row(modifier = Modifier.fillMaxWidth()) {
                row.forEach { key ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(64.dp)
                            .clickable {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onKey(key)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        if (key == "back") {
                            Icon(
                                imageVector = Icons.Rounded.Backspace,
                                contentDescription = "Delete digit",
                                tint = colors.textBase,
                                modifier = Modifier.size(24.dp)
                            )
                        } else {
                            Text(
                                text = key,
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Normal,
                                color = colors.textBase
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionActionSheet(
    transaction: TransactionEntity,
    onEdit: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    val colors = LocalReserveColors.current
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = colors.fillBase,
        contentWindowInsets = { WindowInsets.ime },
        shape = RoundedCornerShape(topStart = EnteRadius.xl, topEnd = EnteRadius.xl)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = EnteSpacing.xxl, vertical = EnteSpacing.xl)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Edit Action
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .clip(RoundedCornerShape(EnteRadius.md))
                        .clickable {
                            onDismiss()
                            onEdit()
                        }
                        .padding(EnteSpacing.md)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Edit,
                        contentDescription = "Edit",
                        tint = colors.textBase,
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        text = "Edit",
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.textBase,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }

                // Duplicate Action
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .clip(RoundedCornerShape(EnteRadius.md))
                        .clickable {
                            onDismiss()
                            onDuplicate()
                        }
                        .padding(EnteSpacing.md)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.ContentCopy,
                        contentDescription = "Duplicate",
                        tint = colors.textBase,
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        text = "Duplicate",
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.textBase,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }

                // Delete Action
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .clip(RoundedCornerShape(EnteRadius.md))
                        .clickable {
                            onDismiss()
                            onDelete()
                        }
                        .padding(EnteSpacing.md)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.DeleteOutline,
                        contentDescription = "Delete",
                        tint = colors.warningDefault,
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        text = "Delete",
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.warningDefault,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun KeyValueRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = LocalReserveColors.current.textBase,
    onClick: (() -> Unit)? = null
) {
    val colors = LocalReserveColors.current
    val base = modifier
        .fillMaxWidth()
        .heightIn(min = 56.dp)
    val clickable = if (onClick != null) base.clickable(onClick = onClick) else base
    Row(
        modifier = clickable.padding(horizontal = spacing_lg, vertical = spacing_md),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textLight,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            color = valueColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
