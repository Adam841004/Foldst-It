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
import org.json.JSONObject
import java.io.File

class FoldstItWidget : GlanceAppWidget() {

    // 2x2 정사각형, 4x2 가로형 등 크기별 반응형 레이아웃 지정
    companion object {
        private val SQUARE_SIZE = DpSize(100.dp, 100.dp)
        private val WIDE_SIZE = DpSize(220.dp, 100.dp)
        private val LARGE_SIZE = DpSize(220.dp, 220.dp)

        // 홈 화면 런처 강제 업데이트 브로드캐스트
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
            var memoText = "메모를 작성해주세요."
            var themeColor = "#FFF9C4"
            var widgetFontSize = 14
            var hasDrawing = false

            try {
                val file = File(context.filesDir, "foldstit_data.json")
                if (file.exists()) {
                    val json = JSONObject(file.readText())
                    val text = json.optString("text", "").trim()
                    val pathsCount = json.optJSONArray("paths")?.length() ?: 0
                    hasDrawing = pathsCount > 0

                    memoText = when {
                        text.isNotEmpty() -> text
                        hasDrawing -> "[✍️ 손글씨 메모]"
                        else -> "새 메모를 작성하세요."
                    }

                    val themeName = json.optString("theme", "YELLOW")
                    themeColor = PastelTheme.valueOf(themeName).hex
                    widgetFontSize = (json.optInt("fontSize", 18) - 3).coerceAtLeast(12)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            // 포스트잇 위젯 메인 레이아웃
            Column(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .background(ColorProvider(android.graphics.Color.parseColor(themeColor)))
                    .padding(12.dp),
                verticalAlignment = Alignment.Top,
                horizontalAlignment = Alignment.Start
            ) {
                // 상단 헤더: 포스트잇 상단 바 형태
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
                        text = "Foldst it",
                        style = TextStyle(
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = ColorProvider(android.graphics.Color.parseColor("#8D6E63"))
                        )
                    )
                }

                // 본문 텍스트
                Text(
                    text = memoText,
                    style = TextStyle(
                        fontSize = widgetFontSize.sp,
                        color = ColorProvider(android.graphics.Color.parseColor("#2C2C2C"))
                    ),
                    maxLines = 10
                )
            }
        }
    }
}

class FoldstItWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = FoldstItWidget()
}
