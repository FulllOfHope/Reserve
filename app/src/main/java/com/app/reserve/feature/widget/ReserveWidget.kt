package com.app.reserve.feature.widget

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
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

private data class WidgetPalette(
    val base: Color,
    val surface: Color,
    val textBase: Color,
    val textLight: Color,
    val stroke: Color,
    val primary: Color
)

private val LightPalette = WidgetPalette(
    base = Color(0xFFF8F9FA),
    surface = Color(0xFFFFFFFF),
    textBase = Color(0xFF111315),
    textLight = Color(0xFF5C626A),
    stroke = Color(0xFFE6E8EC),
    primary = Color(0xFF00B843)
)

private val DarkPalette = WidgetPalette(
    base = Color(0xFF121316),
    surface = Color(0xFF1B1D21),
    textBase = Color(0xFFF8F9FA),
    textLight = Color(0xFFA6ACB5),
    stroke = Color(0xFF282B30),
    primary = Color(0xFF1CE865)
)

class ReserveWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val settings = runCatching {
            SettingsDataStore(context).currentSettings()
        }.getOrNull()

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
            val balance = database.accountDao().getAll()
                .filterNot { it.isArchived }
                .sumOf { it.balance }
            WidgetFigures(balance = balance, spent = spent, income = income)
        }.getOrDefault(WidgetFigures())

        val monthLabel = runCatching {
            LocalDate.now()
                .format(DateTimeFormatter.ofPattern("MMM", Locale.getDefault()))
                .uppercase(Locale.getDefault())
        }.getOrDefault("")

        provideContent {
            val size = LocalSize.current
            val compact = size.height < 136.dp
            val showWordmark = size.width >= 188.dp && size.height >= 92.dp
            ReserveWidgetContent(
                palette = palette,
                figures = figures,
                monthLabel = monthLabel,
                hidden = hidden,
                compact = compact,
                showWordmark = showWordmark,
                addIntent = addTransactionIntent(context)
            )
        }
    }
}

@Composable
private fun ReserveWidgetContent(
    palette: WidgetPalette,
    figures: WidgetFigures,
    monthLabel: String,
    hidden: Boolean,
    compact: Boolean,
    showWordmark: Boolean,
    addIntent: Intent
) {
    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(palette.stroke)
            .cornerRadius(24.dp)
    ) {
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .padding(1.dp)
                .background(palette.base)
                .cornerRadius(23.dp)
                .padding(
                    horizontal = 15.dp,
                    vertical = if (compact) 10.dp else 13.dp
                )
        ) {
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Image(
                    provider = ImageProvider(R.drawable.ic_launcher_foreground),
                    contentDescription = "Reserve",
                    modifier = GlanceModifier
                        .size(26.dp)
                        .cornerRadius(7.dp)
                )
                if (showWordmark) {
                    Spacer(modifier = GlanceModifier.width(8.dp))
                    Text(
                        text = "Reserve",
                        style = TextStyle(
                            color = ColorProvider(palette.textBase),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily("sans-serif-medium")
                        )
                    )
                    Spacer(modifier = GlanceModifier.width(7.dp))
                    if (monthLabel.isNotEmpty()) {
                        Text(
                            text = monthLabel,
                            style = TextStyle(
                                color = ColorProvider(palette.textLight),
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Medium,
                                fontFamily = FontFamily("sans-serif-medium")
                            )
                        )
                    }
                }
                Spacer(modifier = GlanceModifier.defaultWeight())
                AddButton(palette = palette, addIntent = addIntent)
            }

            Spacer(modifier = GlanceModifier.height(if (compact) 8.dp else 9.dp))

            Text(
                text = "CURRENT BALANCE",
                style = TextStyle(
                    color = ColorProvider(palette.textLight),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Medium,
                    fontFamily = FontFamily("sans-serif-medium")
                )
            )
            Spacer(modifier = GlanceModifier.height(2.dp))
            Text(
                text = if (hidden) HiddenAmount else Money.format(figures.balance),
                style = TextStyle(
                    color = ColorProvider(palette.textBase),
                    fontSize = if (compact) 22.sp else 26.sp,
                    fontWeight = FontWeight.Bold
                )
            )

            Spacer(modifier = GlanceModifier.defaultWeight())

            if (!compact) {
                Row(
                    modifier = GlanceModifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Stat(
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
                    Stat(
                        label = "INCOME",
                        value = figures.income,
                        hidden = hidden,
                        color = palette.primary,
                        palette = palette,
                        modifier = GlanceModifier
                            .defaultWeight()
                            .padding(start = 12.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun Stat(
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
                color = ColorProvider(palette.textLight),
                fontSize = 8.sp,
                fontWeight = FontWeight.Medium,
                fontFamily = FontFamily("sans-serif-medium")
            )
        )
        Spacer(modifier = GlanceModifier.height(2.dp))
        Text(
            text = if (hidden) HiddenAmount else Money.format(value),
            style = TextStyle(
                color = ColorProvider(color),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily("sans-serif-medium")
            )
        )
    }
}

@Composable
private fun AddButton(palette: WidgetPalette, addIntent: Intent) {
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
                color = ColorProvider(Color.White),
                fontSize = 21.sp,
                fontWeight = FontWeight.Bold
            )
        )
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
