package com.app.reserve.feature.insights

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import com.app.reserve.core.designsystem.EmptyState
import com.app.reserve.core.designsystem.LocalReserveColors
import com.app.reserve.core.designsystem.MoneyText
import com.app.reserve.core.designsystem.MonthSwitcher
import com.app.reserve.core.designsystem.ReserveCard
import com.app.reserve.core.designsystem.ScreenTitle
import com.app.reserve.core.designsystem.SectionLabel
import com.app.reserve.core.designsystem.categoryColor
import com.app.reserve.core.designsystem.tabular
import com.app.reserve.core.domain.BudgetCalculator
import com.app.reserve.core.domain.CategorySpend
import com.app.reserve.core.domain.Money
import com.app.reserve.feature.ledger.LedgerState

@Composable
fun InsightsScreen(
    state: LedgerState,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit
) {
    val colors = LocalReserveColors.current
    val breakdown = BudgetCalculator.breakdown(
        state.visibleTransactions,
        state.monthYear,
        state.categories
    ).filter { it.spent > 0 }

    Column(modifier = Modifier.fillMaxSize()) {
        ScreenTitle(title = "Insights")
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
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 96.dp)
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
                                    color = colors.textLow
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                MoneyText(
                                    amount = state.spent,
                                    style = MaterialTheme.typography.headlineSmall.tabular(),
                                    color = colors.textHigh
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(modifier = Modifier.fillMaxWidth()) {
                            Summary(
                                label = "Income",
                                value = state.income,
                                color = colors.income,
                                modifier = Modifier.weight(1f)
                            )
                            Summary(
                                label = "Net",
                                value = state.net,
                                color = if (state.net < 0) colors.expense else colors.textHigh,
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
                        TrendChart(series = series.map { it.spent })
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(modifier = Modifier.fillMaxWidth()) {
                            series.forEach { month ->
                                Text(
                                    text = month.label,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = colors.textLow,
                                    modifier = Modifier.weight(1f),
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Expenses only. Transfers move money between your own accounts and are never counted.",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textLow
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
            color = colors.textLow
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
            color = colors.textHigh,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = "$share%",
            style = MaterialTheme.typography.labelSmall,
            color = colors.textLow,
            modifier = Modifier.padding(end = 12.dp)
        )
        MoneyText(
            amount = entry.spent,
            style = MaterialTheme.typography.titleMedium.tabular(),
            color = colors.textHigh
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
                color = colors.surfaceVariant,
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
private fun TrendChart(series: List<Long>, modifier: Modifier = Modifier) {
    val colors = LocalReserveColors.current
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(140.dp)
    ) {
        if (series.isEmpty()) return@Canvas
        val max = series.maxOrNull()?.coerceAtLeast(1L) ?: 1L
        val inset = 6.dp.toPx()
        val usableWidth = size.width - inset * 2
        val usableHeight = size.height - inset * 2
        val step = if (series.size <= 1) 0f else usableWidth / (series.size - 1)
        val points = series.mapIndexed { index, value ->
            Offset(
                x = inset + step * index,
                y = inset + usableHeight - (value.toFloat() / max.toFloat()) * usableHeight
            )
        }
        if (points.size > 1) {
            val path = Path().apply {
                moveTo(points.first().x, points.first().y)
                points.drop(1).forEach { lineTo(it.x, it.y) }
            }
            drawPath(
                path = path,
                color = colors.primary,
                style = Stroke(
                    width = 2.dp.toPx(),
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                )
            )
        }
        points.forEach { point ->
            drawCircle(color = colors.primary, radius = 3.dp.toPx(), center = point)
        }
    }
}
