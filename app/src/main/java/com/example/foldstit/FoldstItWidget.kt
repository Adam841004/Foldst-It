package com.example.foldstit

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.compose.ui.graphics.Color
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
import androidx.glance.appwidget.cornerRadius
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
        private val WIDE_SIZE = DpSize(200.dp, 100.dp)
        private val LARGE_SIZE = DpSize(200.dp, 200.dp)

        fun notifyUpdate(context: Context) {
            try {
                val appWidgetManager = AppWidgetManager.getInstance(context)
                val componentName = ComponentName(context, FoldstItWidgetReceiver::class.java)
                val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)

                if (appWidgetIds.isNotEmpty()) {
                    val intent = Intent(context, FoldstItWidgetReceiver::class.java).apply {
                        action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                        putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, appWidgetIds)
                    }
                    context.sendBroadcast(intent)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
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

            // Compose Color 직접 지정 (Resources$NotFoundException 크래시 방지)
            val backgroundColor = when (themeName) {
                "BLUE" -> Color(0xFFE1F5FE)
                "GREEN" -> Color(0xFFE8F5E9)
                "PURPLE" -> Color(0xFFF3E5F5)
                "PEACH" -> Color(0xFFFFE0B2)
                else -> Color(0xFFFFF9C4)
            }

            val memoText = when {
                text.isNotEmpty() && hasDrawing -> "$text\n\n[✍️ 필기 포함]"
                text.isNotEmpty() -> text
                hasDrawing -> "[✍️ 손글씨 필기 메모]"
                else -> "터치하여 포스트잇 메모를 작성하세요 📌"
            }

            val widgetFontSize = (fontSizePref - 3).coerceAtLeast(13)

            // 3M 포스트잇 모양 (라운드 코너 + 패딩 + 클릭 시 앱 실행)
            Box(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .cornerRadius(16.dp)
                    .background(backgroundColor)
                    .clickable(actionStartActivity<MainActivity>())
                    .padding(14.dp)
            ) {
                Column(
                    modifier = GlanceModifier.fillMaxSize(),
                    verticalAlignment = Alignment.Top,
                    horizontalAlignment = Alignment.Start
                ) {
                    // 상단 헤더
                    Row(
                        modifier = GlanceModifier.fillMaxWidth().padding(bottom = 6.dp),
                        horizontalAlignment = Alignment.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Foldst it 📌",
                            style = TextStyle(
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = ColorProvider(Color(0xFF795548))
                            )
                        )
                    }

                    // 본문 메모 텍스트
                    Text(
                        text = memoText,
                        style = TextStyle(
                            fontSize = widgetFontSize.sp,
                            color = ColorProvider(Color(0xFF2C2C2C))
                        ),
                        maxLines = 10
                    )
                }
            }
        }
    }
}

class FoldstItWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = FoldstItWidget()
}
