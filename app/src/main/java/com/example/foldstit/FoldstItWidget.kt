package com.example.foldstit

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.*
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider

class FoldstItWidget : GlanceAppWidget() {

    companion object {
        private val SQUARE_SIZE = DpSize(100.dp, 100.dp)
        private val WIDE_SIZE = DpSize(220.dp, 100.dp)
        private val LARGE_SIZE = DpSize(220.dp, 220.dp)

        fun notifyUpdate(context: Context) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val componentName = ComponentName(context, FoldstItWidgetReceiver::class.java)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)

            val intent = Intent(context, FoldstItWidgetReceiver::class.java).apply {
                action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, appWidgetIds)
            }
            context.sendBroadcast(intent)
        }
    }

    override val sizeMode: SizeMode = SizeMode.Responsive(
        setOf(SQUARE_SIZE, WIDE_SIZE, LARGE_SIZE)
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            val prefs = context.getSharedPreferences("foldstit_data", Context.MODE_PRIVATE)
            val text = prefs.getString("text", "")?.trim() ?: ""
            val themeName = prefs.getString("theme", "YELLOW") ?: "YELLOW"
            val fontSizePref = prefs.getInt("fontSize", 18)
            val hasDrawing = prefs.getBoolean("hasDrawing", false)

            val themeColor = when (themeName) {
                "BLUE" -> "#E1F5FE"
                "GREEN" -> "#E8F5E9"
                "PURPLE" -> "#F3E5F5"
                "PEACH" -> "#FFE0B2"
                else -> "#FFF9C4"
            }

            val memoText = when {
                text.isNotEmpty() -> text
                hasDrawing -> "[✍️ 손글씨 메모 포함]"
                else -> "작성된 메모가 없습니다."
            }

            val widgetFontSize = (fontSizePref - 3).coerceAtLeast(12)

            Column(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .background(ColorProvider(android.graphics.Color.parseColor(themeColor)))
                    .padding(14.dp)
                    .clickable(actionStartActivity<MainActivity>()),
                verticalAlignment = Alignment.Top,
                horizontalAlignment = Alignment.Start
            ) {
                Row(
                    modifier = GlanceModifier.fillMaxWidth().padding(bottom = 6.dp),
                    horizontalAlignment = Alignment.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (hasDrawing) {
                        Text(
                            text = "✍️ ",
                            style = TextStyle(fontSize = 11.sp)
                        )
                    }
                    Text(
                        text = "Foldst it 📌",
                        style = TextStyle(
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = ColorProvider(android.graphics.Color.parseColor("#795548"))
                        )
                    )
                }

                Text(
                    text = memoText,
                    style = TextStyle(
                        fontSize = widgetFontSize.sp,
                        color = ColorProvider(android.graphics.Color.parseColor("#2C2C2C"))
                    ),
                    maxLines = 8
                )
            }
        }
    }
}

class FoldstItWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = FoldstItWidget()
}
