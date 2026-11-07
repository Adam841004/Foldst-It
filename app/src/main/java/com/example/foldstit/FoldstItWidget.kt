package com.example.foldstit

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
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
import java.io.File

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
            val legacyPrefs = context.getSharedPreferences("foldstit_data", Context.MODE_PRIVATE)
            val memoText = legacyPrefs.getString("text", "")?.trim() ?: ""
            val themeName = legacyPrefs.getString("theme", "POSTIT_YELLOW") ?: "POSTIT_YELLOW"
            val targetMemoId = legacyPrefs.getString("latest_memo_id", "") ?: ""

            // 테마별 색상 매핑
            val (bgColor, textColor, pinColor) = when (themeName) {
                "POSTIT_BLUE" -> Triple(Color(0xFF81D4FA), Color(0xFF212121), Color(0xFFE53935))
                "NOTEBOOK" -> Triple(Color(0xFFFAFAFA), Color(0xFF1A237E), Color(0xFF43A047))
                "BLACK_PAPER" -> Triple(Color(0xFF263238), Color(0xFFECEFF1), Color(0xFFFFB300))
                "WHITE_PAPER" -> Triple(Color(0xFFFFFFFF), Color(0xFF212121), Color(0xFFE91E63))
                else -> Triple(Color(0xFFFFF176), Color(0xFF212121), Color(0xFF1E88E5)) // 노랑 포스트잇
            }

            // 손글씨 비트맵 파일 캐시 확인
            val drawingFile = if (targetMemoId.isNotEmpty()) File(context.filesDir, "drawing_${targetMemoId}.png") else null
            val hasBitmapDrawing = drawingFile != null && drawingFile.exists()

            Box(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .cornerRadius(12.dp)
                    .background(bgColor)
                    .clickable(actionStartActivity<MainActivity>())
                    .padding(12.dp)
            ) {
                Column(
                    modifier = GlanceModifier.fillMaxSize(),
                    verticalAlignment = Alignment.Top,
                    horizontalAlignment = Alignment.Start
                ) {
                    // 상단 중앙 핀 & 헤더
                    Row(
                        modifier = GlanceModifier.fillMaxWidth().padding(bottom = 4.dp),
                        horizontalAlignment = Alignment.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "📌",
                            style = TextStyle(fontSize = 11.sp)
                        )
                        Text(
                            text = " Foldst it",
                            style = TextStyle(
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = ColorProvider(pinColor)
                            )
                        )
                    }

                    // 텍스트 내용
                    if (memoText.isNotEmpty()) {
                        Text(
                            text = memoText,
                            style = TextStyle(
                                fontSize = 13.sp,
                                color = ColorProvider(textColor)
                            ),
                            maxLines = if (hasBitmapDrawing) 3 else 8
                        )
                    }

                    // 손글씨 드로잉 실제 비트맵 렌더링
                    if (hasBitmapDrawing && drawingFile != null) {
                        val bitmap = BitmapFactory.decodeFile(drawingFile.absolutePath)
                        if (bitmap != null) {
                            Spacer(modifier = GlanceModifier.height(4.dp))
                            Image(
                                provider = ImageProvider(bitmap),
                                contentDescription = "Drawing Preview",
                                modifier = GlanceModifier.fillMaxWidth().height(70.dp)
                            )
                        }
                    } else if (memoText.isEmpty()) {
                        Text(
                            text = "빈 포스트잇 메모입니다.",
                            style = TextStyle(fontSize = 12.sp, color = ColorProvider(Color.Gray))
                        )
                    }
                }
            }
        }
    }
}

class FoldstItWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = FoldstItWidget()

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == "com.example.foldstit.PIN_WIDGET_ASSIGN") {
            val memoId = intent.getStringExtra("TARGET_MEMO_ID")
            if (!memoId.isNullOrEmpty()) {
                val prefs = context.getSharedPreferences("foldstit_data", Context.MODE_PRIVATE)
                prefs.edit().putString("latest_memo_id", memoId).apply()
                FoldstItWidget.notifyUpdate(context)
            }
        }
    }
}
