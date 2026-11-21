package com.app.reserve.feature.widget

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontFamily
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.app.reserve.MainActivity
import com.app.reserve.R
import com.app.reserve.core.database.ReserveDatabase
import com.app.reserve.core.database.TransactionType
import com.app.reserve.core.datastore.SettingsDataStore
import com.app.reserve.core.datastore.ThemeMode
import com.app.reserve.core.designsystem.HiddenAmount
import com.app.reserve.core.designsystem.categoryColor
import com.app.reserve.core.designsystem.resolveAccentHex
import com.app.reserve.core.domain.Dates
import com.app.reserve.core.domain.Money
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private data class WidgetFigures(
    val balance: Long = 0L,
    val spent: Long = 0L,
    val income: Long = 0L
)

private data class RecentRow(
    val title: String,
    val amount: Long,
    val income: Boolean,
    val categoryColor: Color
)

/** Ente tokens used by the widget. Glance cannot read Compose CompositionLocals. */
private data class WidgetPalette(
    val base: Color,
    val surface: Color,
    val textBase: Color,
    val textLight: Color,
    val textLighter: Color,
    val textReverse: Color,
    val stroke: Color,
    val primary: Color,
    val warning: Color
)

private val LightPalette = WidgetPalette(
    base = Color(0xFFF8F9FA),
    surface = Color(0xFFFFFFFF),
    textBase = Color(0xFF111315),
    textLight = Color(0xFF5C626A),
    textLighter = Color(0xFF8A9099),
    textReverse = Color(0xFFFFFFFF),
    stroke = Color(0xFFE6E8EC),
    primary = Color(0xFF00B843),
    warning = Color(0xFFF03E3E)
)

private val DarkPalette = WidgetPalette(
    base = Color(0xFF121316),
    surface = Color(0xFF1B1D21),
    textBase = Color(0xFFF8F9FA),
    textLight = Color(0xFFA6ACB5),
    textLighter = Color(0xFF727882),
    textReverse = Color(0xFF0B140E),
    stroke = Color(0xFF282B30),
    primary = Color(0xFF1CE865),
    warning = Color(0xFFFF5252)
)

private val SansSerif = FontFamily("sans-serif-medium")

class ReserveWidget : GlanceAppWidget() {

    /**
     * Three Ente size classes. Glance picks the nearest one for the actual launcher
     * size, so [LocalSize] is deterministic and the breakpoints below never clip.
     */
    override val sizeMode: SizeMode = SizeMode.Responsive(
        setOf(
            DpSize(160.dp, 100.dp), // compact   2x1 / 2x2
            DpSize(250.dp, 140.dp), // standard  4x2
            DpSize(250.dp, 220.dp)  // expanded  4x3
        )
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val settings = runCatching { SettingsDataStore(context).currentSettings() }.getOrNull()

        val dark = when (settings?.themeMode ?: ThemeMode.SYSTEM) {
            ThemeMode.DARK -> true
            ThemeMode.LIGHT -> false
            ThemeMode.SYSTEM -> {
                val mode = context.resources.configuration.uiMode and
                    Configuration.UI_MODE_NIGHT_MASK
                mode == Configuration.UI_MODE_NIGHT_YES
            }
        }
        val basePalette = if (dark) DarkPalette else LightPalette
        val accentHex = settings?.primaryColorHex?.let { resolveAccentHex(it, dark) }
        val accent = accentHex?.let { hex ->
            runCatching { Color(android.graphics.Color.parseColor(hex)) }.getOrNull()
        }
        val palette = if (accent != null) basePalette.copy(primary = accent) else basePalette
        val hidden = settings?.hideAmounts == true

        val figures = runCatching {
            val database = ReserveDatabase.get(context)
            val monthKey = Dates.currentMonthKey()
            val transactions = database.transactionDao().getAll()
            val spent = transactions
                .filter {
                    it.type == TransactionType.EXPENSE &&
                        Dates.monthKey(Dates.toLocalDate(it.timestamp)) == monthKey
                }
                .sumOf { it.amount }
            val income = transactions
                .filter {
                    it.type == TransactionType.INCOME &&
                        Dates.monthKey(Dates.toLocalDate(it.timestamp)) == monthKey
                }
                .sumOf { it.amount }
            val selectedAccountIds = settings?.balanceAccountIds
            val balance = database.accountDao().getAll()
                .filterNot { it.isArchived }
                .filter { selectedAccountIds == null || it.id in selectedAccountIds }
                .sumOf { it.balance }
            WidgetFigures(balance = balance, spent = spent, income = income)
        }.getOrDefault(WidgetFigures())

        val recent = runCatching {
            val database = ReserveDatabase.get(context)
            val categories = database.categoryDao().getAll().associateBy { it.id }
            database.transactionDao().getAll()
                .filter { it.type != TransactionType.TRANSFER }
                .take(2)
                .map { transaction ->
                    val category = categories[transaction.categoryId]
                    val title = category?.name
                        ?: transaction.note.ifBlank {
                            if (transaction.type == TransactionType.INCOME) "Income" else "Expense"
                        }
                    RecentRow(
                        title = title,
                        amount = transaction.amount,
                        income = transaction.type == TransactionType.INCOME,
                        categoryColor = category?.let { categoryColor(it.colorHex) }
                            ?: palette.primary
                    )
                }
        }.getOrDefault(emptyList())

        val monthLabel = runCatching {
            LocalDate.now()
                .format(DateTimeFormatter.ofPattern("MMM yyyy", Locale.getDefault()))
                .uppercase(Locale.getDefault())
        }.getOrDefault("")

        provideContent {
            val size = LocalSize.current
            ReserveWidgetContent(
                palette = palette,
                figures = figures,
                recent = recent,
                monthLabel = monthLabel,
                hidden = hidden,
                compact = size.height < 130.dp,
                showMonth = size.width >= 200.dp,
                pillAction = size.height >= 130.dp && size.width >= 240.dp,
                showStats = size.height >= 136.dp,
                showRecent = size.height >= 210.dp,
                recentRows = if (size.height >= 260.dp) 2 else 1,
                addIntent = addTransactionIntent(context)
            )
        }
    }
}

@Composable
private fun ReserveWidgetContent(
    palette: WidgetPalette,
    figures: WidgetFigures,
    recent: List<RecentRow>,
    monthLabel: String,
    hidden: Boolean,
    compact: Boolean,
    showMonth: Boolean,
    pillAction: Boolean,
    showStats: Boolean,
    showRecent: Boolean,
    recentRows: Int,
    addIntent: Intent
) {
    val outerPadding = if (compact) 12.dp else 16.dp

    // 1 dp strokeFaint hairline via a layered box — zero elevation / shadow / gradient.
    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(palette.stroke)
            .cornerRadius(20.dp)
    ) {
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .padding(1.dp)
                .background(palette.base)
                .cornerRadius(19.dp)
                .padding(outerPadding)
        ) {
            Header(
                palette = palette,
                monthLabel = monthLabel,
                showMonth = showMonth,
                pillAction = pillAction,
                addIntent = addIntent
            )

            Spacer(modifier = GlanceModifier.height(if (compact) 4.dp else 10.dp))

            HeroBalance(palette = palette, figure = figures.balance, hidden = hidden, compact = compact)

            Spacer(modifier = GlanceModifier.defaultWeight())

            if (showStats) {
                StatsCard(palette = palette, figures = figures, hidden = hidden)
            }

            if (showRecent && recent.isNotEmpty()) {
                Spacer(modifier = GlanceModifier.height(12.dp))
                // One row at 4x3; a taller launcher-resized widget gets a second row.
                recent.take(recentRows).forEach { row ->
                    RecentTransactionRow(palette = palette, row = row, hidden = hidden)
                    Spacer(modifier = GlanceModifier.height(6.dp))
                }
            }
        }
    }
}

@Composable
private fun Header(
    palette: WidgetPalette,
    monthLabel: String,
    showMonth: Boolean,
    pillAction: Boolean,
    addIntent: Intent
) {
    Row(
        modifier = GlanceModifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 22 dp lowercase `r ₹` Reserve mark.
        Image(
            provider = ImageProvider(R.drawable.ic_reserve_mark),
            contentDescription = "Reserve",
            modifier = GlanceModifier.size(22.dp)
        )
        Spacer(modifier = GlanceModifier.width(8.dp))
        Text(
            text = "Reserve",
            maxLines = 1,
            style = TextStyle(
                color = ColorProvider(palette.textBase),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = SansSerif
            )
        )
        if (showMonth && monthLabel.isNotEmpty()) {
            Spacer(modifier = GlanceModifier.width(6.dp))
            Text(
                text = monthLabel,
                maxLines = 1,
                style = TextStyle(
                    color = ColorProvider(palette.textLighter),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    fontFamily = SansSerif
                )
            )
        }
        Spacer(modifier = GlanceModifier.defaultWeight())
        AddButton(palette = palette, pill = pillAction, addIntent = addIntent)
    }
}

@Composable
private fun HeroBalance(
    palette: WidgetPalette,
    figure: Long,
    hidden: Boolean,
    compact: Boolean
) {
    // Glance TextStyle has no letter-spacing API; uppercase + Medium is the closest match.
    Text(
        text = "CURRENT BALANCE",
        style = TextStyle(
            color = ColorProvider(palette.textLighter),
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            fontFamily = SansSerif
        )
    )
    Spacer(modifier = GlanceModifier.height(if (compact) 2.dp else 4.dp))
    Text(
        text = if (hidden) HiddenAmount else Money.format(figure),
        maxLines = 1,
        style = TextStyle(
            color = ColorProvider(if (!hidden && figure < 0) palette.warning else palette.textBase),
            fontSize = if (compact) 22.sp else 28.sp,
            // Glance exposes Normal / Medium / Bold only; Bold is our ExtraBold.
            fontWeight = FontWeight.Bold
        )
    )
}

@Composable
private fun StatsCard(palette: WidgetPalette, figures: WidgetFigures, hidden: Boolean) {
    Box(
        modifier = GlanceModifier
            .fillMaxWidth()
            .background(palette.stroke)
            .cornerRadius(14.dp)
    ) {
        Row(
            modifier = GlanceModifier
                .fillMaxWidth()
                .padding(1.dp)
                .background(palette.surface)
                .cornerRadius(13.dp)
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            StatColumn(
                label = "SPENT",
                value = figures.spent,
                hidden = hidden,
                color = palette.textBase,
                palette = palette,
                modifier = GlanceModifier.defaultWeight()
            )
            Box(
                modifier = GlanceModifier
                    .width(1.dp)
                    .height(24.dp)
                    .background(palette.stroke)
            ) {}
            StatColumn(
                label = "INCOME",
                value = figures.income,
                hidden = hidden,
                color = palette.primary,
                palette = palette,
                modifier = GlanceModifier.defaultWeight().padding(start = 12.dp)
            )
        }
    }
}

@Composable
private fun StatColumn(
    label: String,
    value: Long,
    hidden: Boolean,
    color: Color,
    palette: WidgetPalette,
    modifier: GlanceModifier
) {
    Column(modifier = modifier) {
        Text(
            text = label,
            style = TextStyle(
                color = ColorProvider(palette.textLighter),
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                fontFamily = SansSerif
            )
        )
        Spacer(modifier = GlanceModifier.height(2.dp))
        Text(
            text = if (hidden) HiddenAmount else Money.format(value, showPlus = label == "INCOME"),
            maxLines = 1,
            style = TextStyle(
                color = ColorProvider(color),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = SansSerif
            )
        )
    }
}

@Composable
private fun RecentTransactionRow(palette: WidgetPalette, row: RecentRow, hidden: Boolean) {
    Row(
        modifier = GlanceModifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = GlanceModifier
                .size(28.dp)
                .background(row.categoryColor)
                .cornerRadius(8.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = row.title.take(1).uppercase(Locale.getDefault()),
                style = TextStyle(
                    color = ColorProvider(palette.textReverse),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = SansSerif
                )
            )
        }
        Spacer(modifier = GlanceModifier.width(8.dp))
        Text(
            text = row.title,
            maxLines = 1,
            modifier = GlanceModifier.defaultWeight(),
            style = TextStyle(
                color = ColorProvider(palette.textBase),
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                fontFamily = SansSerif
            )
        )
        Spacer(modifier = GlanceModifier.width(8.dp))
        Text(
            text = if (hidden) HiddenAmount else Money.format(row.amount, showPlus = row.income),
            maxLines = 1,
            style = TextStyle(
                color = ColorProvider(if (row.income) palette.primary else palette.textBase),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = SansSerif
            )
        )
    }
}

@Composable
private fun AddButton(palette: WidgetPalette, pill: Boolean, addIntent: Intent) {
    if (pill) {
        Row(
            modifier = GlanceModifier
                .height(32.dp)
                .background(palette.primary)
                .cornerRadius(16.dp)
                .padding(horizontal = 12.dp)
                .clickable(actionStartActivity(addIntent)),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "+",
                style = TextStyle(
                    color = ColorProvider(palette.textReverse),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            )
            Spacer(modifier = GlanceModifier.width(4.dp))
            Text(
                text = "Add",
                style = TextStyle(
                    color = ColorProvider(palette.textReverse),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = SansSerif
                )
            )
        }
    } else {
        Box(
            modifier = GlanceModifier
                .size(32.dp)
                .background(palette.primary)
                .cornerRadius(16.dp)
                .clickable(actionStartActivity(addIntent)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "+",
                style = TextStyle(
                    color = ColorProvider(palette.textReverse),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            )
        }
    }
}

private fun addTransactionIntent(context: Context): Intent =
    Intent(context, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or
            Intent.FLAG_ACTIVITY_SINGLE_TOP or
            Intent.FLAG_ACTIVITY_CLEAR_TOP
        putExtra(MainActivity.EXTRA_OPEN_ADD_SHEET, true)
    }

class ReserveWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ReserveWidget()
}
