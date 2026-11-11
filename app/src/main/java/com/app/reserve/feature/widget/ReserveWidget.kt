package com.app.reserve.feature.widget

import android.content.Context
import android.content.Intent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.glance.Button
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.app.reserve.MainActivity
import com.app.reserve.R
import com.app.reserve.core.database.ReserveDatabase
import com.app.reserve.core.domain.Dates
import com.app.reserve.core.domain.Money
import kotlinx.coroutines.flow.first

class ReserveWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val db = ReserveDatabase.get(context)
        // Simple logic for total expenses this month for widget
        val monthKey = Dates.currentMonthKey()
        val allTransactions = db.transactionDao().getAll()
        val spentThisMonth = allTransactions.filter { 
            it.type == com.app.reserve.core.database.TransactionType.EXPENSE && 
            Dates.monthKey(Dates.toLocalDate(it.timestamp)) == monthKey 
        }.sumOf { it.amount }

        provideContent {
            GlanceTheme {
                Row(
                    modifier = GlanceModifier
                        .fillMaxSize()
                        .background(Color(0xFFFFFFFF)) // fillBase
                        .cornerRadius(20.dp)
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = GlanceModifier.defaultWeight()) {
                        Text(
                            text = "Spent this month",
                            style = TextStyle(
                                color = androidx.glance.unit.ColorProvider(Color(0xFF8E8E93)), // textLight
                                fontSize = androidx.compose.ui.unit.TextUnit(14f, androidx.compose.ui.unit.TextUnitType.Sp)
                            )
                        )
                        Text(
                            text = Money.format(spentThisMonth),
                            style = TextStyle(
                                color = androidx.glance.unit.ColorProvider(Color(0xFF1C1C1E)), // textBase
                                fontSize = androidx.compose.ui.unit.TextUnit(20f, androidx.compose.ui.unit.TextUnitType.Sp),
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                    
                    Spacer(modifier = GlanceModifier.width(12.dp))
                    
                    val intent = Intent(context, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        putExtra("EXTRA_OPEN_ADD_SHEET", true)
                    }
                    
                    Button(
                        text = "+",
                        onClick = androidx.glance.appwidget.action.actionStartActivity(intent)
                    )
                }
            }
        }
    }
}

class ReserveWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ReserveWidget()
}
