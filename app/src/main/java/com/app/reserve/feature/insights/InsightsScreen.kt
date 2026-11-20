package com.app.reserve.feature.insights

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PieChart
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.pow
import com.app.reserve.core.designsystem.EmptyState
import com.app.reserve.core.designsystem.HiddenAmount
import com.app.reserve.core.designsystem.LocalAmountsHidden
import com.app.reserve.core.designsystem.LocalReserveColors
import com.app.reserve.core.designsystem.MoneyText
import com.app.reserve.core.designsystem.MonthSwitcher
import com.app.reserve.core.designsystem.ReserveCard
import com.app.reserve.core.designsystem.SectionLabel
import com.app.reserve.core.designsystem.categoryColor
import com.app.reserve.core.designsystem.tabular
import com.app.reserve.core.domain.BudgetCalculator
import com.app.reserve.core.domain.CategorySpend
import com.app.reserve.core.domain.Money
import androidx.compose.ui.text.drawText
import com.app.reserve.feature.ledger.LedgerState

@Composable
fun InsightsScreen(
    state: LedgerState,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit
) {
    val colors = LocalReserveColors.current
    val amountsHidden = LocalAmountsHidden.current
    val breakdown = BudgetCalculator.breakdown(
        state.visibleTransactions,
        state.monthYear,
        state.categories
    ).filter { it.spent > 0 }

    Column(modifier = Modifier.fillMaxSize()) {
        MonthSwitcher(
            monthYear = state.monthYear,
            onPrevious = onPreviousMonth,
            onNext = onNextMonth
        )

        if (breakdown.isEmpty()) {
            EmptyState(
                icon = Icons.Rounded.PieChart,
                text = "No expenses this month, so there is nothing to chart yet."
            )
            return@Column
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 108.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(8.dp))
                ReserveCard(modifier = Modifier.padding(horizontal = 16.dp)) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            DonutChart(
                                entries = breakdown.map {
                                    categoryColor(it.category?.colorHex ?: "#948B7A") to it.spent
                                },
                                total = state.spent,
                                modifier = Modifier.size(180.dp)
                            )
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "SPENT",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = colors.textLighter
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                MoneyText(
                                    amount = state.spent,
                                    style = MaterialTheme.typography.headlineSmall.tabular(),
                                    color = colors.textBase
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(modifier = Modifier.fillMaxWidth()) {
                            Summary(
                                label = "Income",
                                value = state.income,
                                color = colors.primaryDefault,
                                modifier = Modifier.weight(1f)
                            )
                            Summary(
                                label = "Net",
                                value = state.net,
                                color = if (state.net < 0) colors.warningDefault else colors.textBase,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            item { SectionLabel(text = "Top categories") }

            items(
                count = breakdown.size.coerceAtMost(7)
            ) { index ->
                val entry = breakdown[index]
                CategoryLegendRow(
                    entry = entry,
                    total = state.spent,
                    index = index
                )
            }

            item {
                SectionLabel(text = "Last 6 months")
                ReserveCard(modifier = Modifier.padding(horizontal = 16.dp)) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        val series = BudgetCalculator.monthlySeries(
                            state.visibleTransactions,
                            state.monthYear,
                            6
                        )
                        TrendChart(series = series, amountsHidden = amountsHidden)
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(modifier = Modifier.fillMaxWidth()) {
                            series.forEach { month ->
                                Text(
                                    text = month.label,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = colors.textLighter,
                                    modifier = Modifier.weight(1f),
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Expenses only. Transfers move money between your own accounts and are never counted.",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textLighter
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Summary(
    label: String,
    value: Long,
    color: Color,
    modifier: Modifier = Modifier
) {
    val colors = LocalReserveColors.current
    Column(modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = colors.textLighter
        )
        Spacer(modifier = Modifier.height(4.dp))
        MoneyText(
            amount = value,
            style = MaterialTheme.typography.titleMedium.tabular(),
            color = color
        )
    }
}

@Composable
private fun CategoryLegendRow(entry: CategorySpend, total: Long, index: Int) {
    val colors = LocalReserveColors.current
    val accent = categoryColor(entry.category?.colorHex ?: "#948B7A")
    val share = if (total <= 0L) 0 else (entry.spent * 100L / total).toInt()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .background(accent, CircleShape)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = entry.category?.name ?: "Uncategorized",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textBase,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = "$share%",
            style = MaterialTheme.typography.labelSmall,
            color = colors.textLighter,
            modifier = Modifier.padding(end = 12.dp)
        )
        MoneyText(
            amount = entry.spent,
            style = MaterialTheme.typography.titleMedium.tabular(),
            color = colors.textBase
        )
    }
}

@Composable
private fun DonutChart(
    entries: List<Pair<Color, Long>>,
    total: Long,
    modifier: Modifier = Modifier
) {
    val colors = LocalReserveColors.current
    Canvas(modifier = modifier) {
        val stroke = 16.dp.toPx()
        val diameter = size.minDimension - stroke
        val topLeft = Offset((size.width - diameter) / 2f, (size.height - diameter) / 2f)
        val arcSize = Size(diameter, diameter)
        if (total <= 0L || entries.isEmpty()) {
            drawArc(
                color = colors.fillDark,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Butt)
            )
            return@Canvas
        }
        var start = -90f
        entries.forEach { (color, value) ->
            val sweep = 360f * value.toFloat() / total.toFloat()
            val gap = if (sweep > 4f) 2f else 0f
            drawArc(
                color = color,
                startAngle = start + gap / 2f,
                sweepAngle = (sweep - gap).coerceAtLeast(0.5f),
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Butt)
            )
            start += sweep
        }
    }
}

@Composable
private fun TrendChart(
    series: List<com.app.reserve.core.domain.MonthTotal>,
    amountsHidden: Boolean,
    modifier: Modifier = Modifier
) {
    val colors = LocalReserveColors.current
    val textMeasurer = androidx.compose.ui.text.rememberTextMeasurer()

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(180.dp)
    ) {
        if (series.isEmpty()) return@Canvas

        // Expenses only — matching the "Expenses only" label above the chart.
        val maxAmount = series.maxOfOrNull { it.spent }?.coerceAtLeast(1L) ?: 1L

        val textStyle = androidx.compose.ui.text.TextStyle(
            color = colors.textLighter,
            fontSize = 10.sp,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
            fontFeatureSettings = "tnum"
        )

        val labelSpace = 28.dp.toPx()
        val usableHeight = (size.height - labelSpace).coerceAtLeast(1f)
        val usableWidth = size.width
        val minBarHeight = 6.dp.toPx()
        val barWidth = 14.dp.toPx()
        val radius = 6.dp.toPx()

        // Baseline
        drawLine(
            color = colors.strokeFaint,
            start = Offset(0f, usableHeight),
            end = Offset(usableWidth, usableHeight),
            strokeWidth = 1.dp.toPx()
        )

        val step = usableWidth / series.size.coerceAtLeast(1)
        series.forEachIndexed { index, monthData ->
            val centerX = (index * step) + (step / 2)
            if (monthData.spent <= 0L) return@forEachIndexed

            // Power scaling keeps ordinary months visible next to spike months.
            val ratio = (monthData.spent.toFloat() / maxAmount.toFloat())
                .coerceIn(0f, 1f).pow(0.45f)
            val barHeight = (ratio * usableHeight).coerceAtLeast(minBarHeight)
            val top = usableHeight - barHeight
            val left = centerX - (barWidth / 2f)

            // Square bottom, 6.dp rounded top corners.
            drawRect(
                color = colors.primaryDefault,
                topLeft = Offset(left, top + radius),
                size = Size(barWidth, (barHeight - radius).coerceAtLeast(0f))
            )
            drawRoundRect(
                color = colors.primaryDefault,
                topLeft = Offset(left, top),
                size = Size(barWidth, (radius * 2f).coerceAtMost(barHeight)),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(radius, radius)
            )

            val label = if (amountsHidden) HiddenAmount else compactAmount(monthData.spent)
            val textLayout = textMeasurer.measure(
                androidx.compose.ui.text.AnnotatedString(label), textStyle
            )
            drawText(
                textLayoutResult = textLayout,
                topLeft = Offset(
                    centerX - textLayout.size.width / 2f,
                    (top - textLayout.size.height - 4.dp.toPx()).coerceAtLeast(0f)
                )
            )
        }
    }
}

/** 1.1L, 2.1k, 450 — compact enough that adjacent labels never collide. */
private fun compactAmount(minor: Long): String {
    // Ledger amounts are paise; chart labels are rupees.
    val rupees = minor / 100.0
    fun short(divisor: Double, suffix: String): String {
        val rounded = kotlin.math.round(rupees / divisor * 10.0) / 10.0
        val whole = rounded.toLong()
        return if (rounded == whole.toDouble()) "$whole$suffix" else "$rounded$suffix"
    }
    return when {
        rupees >= 10_000_000.0 -> short(10_000_000.0, "C")
        rupees >= 100_000.0 -> short(100_000.0, "L")
        rupees >= 1_000.0 -> short(1_000.0, "k")
        else -> {
            val rounded = kotlin.math.round(rupees * 10.0) / 10.0
            val whole = rounded.toLong()
            if (rounded == whole.toDouble()) whole.toString() else rounded.toString()
        }
    }
}
