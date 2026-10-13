package com.qingxi.ledger.widget

import android.content.Context
import android.content.Intent
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartActivity
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
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.qingxi.ledger.MainActivity
import com.qingxi.ledger.R
import com.qingxi.ledger.data.db.AppDatabase
import com.qingxi.ledger.util.Dates
import com.qingxi.ledger.util.Money

/** 今日支出小组件：展示当天支出，右侧「记一笔」直达记账页。 */
class TodayWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val dayStart = Dates.startOfDay(System.currentTimeMillis())
        val totals = AppDatabase.init(context).transactionDao()
            .dayTotals(dayStart, Dates.endOfDay(dayStart))
        val expenseMinor = totals.firstOrNull { it.dayKey == Dates.dayKeyOf(dayStart) }?.expenseMinor ?: 0L

        val recordAction = actionStartActivity(
            Intent(context, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_OPEN_RECORD, true)
        )
        val openAction = actionStartActivity(Intent(context, MainActivity::class.java))

        provideContent {
            Row(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .background(R.drawable.widget_background)
                    .clickable(onClick = openAction)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = GlanceModifier.defaultWeight()) {
                    Text(
                        text = "今日支出",
                        style = TextStyle(color = ColorProvider(R.color.widget_text_secondary), fontSize = 12.sp)
                    )
                    Spacer(GlanceModifier.height(2.dp))
                    Text(
                        text = "¥${Money.format(expenseMinor)}",
                        maxLines = 1,
                        style = TextStyle(
                            color = ColorProvider(if (expenseMinor > 0L) R.color.widget_expense else R.color.widget_text_primary),
                            fontSize = 21.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                    Spacer(GlanceModifier.height(4.dp))
                    Text(
                        text = Dates.dayLabel(dayStart),
                        maxLines = 1,
                        style = TextStyle(color = ColorProvider(R.color.widget_text_secondary), fontSize = 12.sp)
                    )
                }

                Spacer(GlanceModifier.width(8.dp))
                Box(
                    modifier = GlanceModifier
                        .background(R.drawable.widget_button_background)
                        .clickable(onClick = recordAction)
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "记一笔",
                        style = TextStyle(
                            color = ColorProvider(R.color.widget_button_text),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                    )
                }
            }
        }
    }
}

/** 系统「添加小组件」列表中的入口。 */
class TodayWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TodayWidget()
}

/** 记账数据变化后刷新所有已添加的小组件，未添加时为空操作。 */
suspend fun refreshTodayWidgets(context: Context) {
    val appContext = context.applicationContext
    val widget = TodayWidget()
    runCatching {
        GlanceAppWidgetManager(appContext)
            .getGlanceIds(TodayWidget::class.java)
            .forEach { widget.update(appContext, it) }
    }
}
