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
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.*
import androidx.glance.text.FontFamily
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import org.json.JSONArray
import java.io.File

class FoldstItWidget : GlanceAppWidget() {

    companion object {
        private val SQUARE_SIZE = DpSize(100.dp, 100.dp)
        private val WIDE_SIZE = DpSize(200.dp, 100.dp)
        private val LARGE_SIZE = DpSize(200.dp, 200.dp)

        val memoIdKey = ActionParameters.Key<String>("EDIT_MEMO_ID")

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
            val boardPrefs = context.getSharedPreferences("foldstit_board", Context.MODE_PRIVATE)
            val legacyPrefs = context.getSharedPreferences("foldstit_data", Context.MODE_PRIVATE)
            val targetMemoId = legacyPrefs.getString("latest_memo_id", "") ?: ""

            var memoText = ""
            var themeName = "PASTEL_YELLOW"
            var fontName = "SYSTEM_DEFAULT"

            val jsonStr = boardPrefs.getString("memo_list_json", null)
            if (!jsonStr.isNullOrEmpty() && targetMemoId.isNotEmpty()) {
                try {
                    val arr = JSONArray(jsonStr)
                    for (i in 0 until arr.length()) {
                        val obj = arr.getJSONObject(i)
                        if (obj.optString("id") == targetMemoId) {
                            memoText = obj.optString("text", "")
                            themeName = obj.optString("theme", "PASTEL_YELLOW")
                            fontName = obj.optString("font", "SYSTEM_DEFAULT")
                            break
                        }
                    }
                } catch (_: Exception) {}
            }

            if (memoText.isEmpty() && targetMemoId.isEmpty()) {
                memoText = legacyPrefs.getString("text", "") ?: ""
                themeName = legacyPrefs.getString("theme", "PASTEL_YELLOW") ?: "PASTEL_YELLOW"
                fontName = legacyPrefs.getString("font", "SYSTEM_DEFAULT") ?: "SYSTEM_DEFAULT"
            }

            val (bgColor, textColor, pinColor) = when (themeName) {
                "PASTEL_BLUE" -> Triple(Color(0xFFE1F5FE), Color(0xFF2C2C2C), Color(0xFFE53935))
                "PASTEL_PINK" -> Triple(Color(0xFFFCE4EC), Color(0xFF2C2C2C), Color(0xFF43A047))
                "PASTEL_GREEN" -> Triple(Color(0xFFE8F5E9), Color(0xFF2C2C2C), Color(0xFFFFB300))
                "NOTEBOOK" -> Triple(Color(0xFFFFFDF9), Color(0xFF1A237E), Color(0xFFD84315))
                else -> Triple(Color(0xFFFFF9C4), Color(0xFF2C2C2C), Color(0xFF1E88E5))
            }

            val widgetFontFamily = when (fontName) {
                "SYSTEM_SERIF" -> FontFamily.Serif
                "SYSTEM_MONO" -> FontFamily.Monospace
                "SYSTEM_SANS" -> FontFamily.SansSerif
                else -> FontFamily.SansSerif // 시스템 기본 연동
            }

            val drawingFile = if (targetMemoId.isNotEmpty()) File(context.filesDir, "drawing_${targetMemoId}.png") else null
            val hasBitmapDrawing = drawingFile != null && drawingFile.exists()

            val clickAction = if (targetMemoId.isNotEmpty()) {
                actionStartActivity<MainActivity>(actionParametersOf(memoIdKey to targetMemoId))
            } else {
                actionStartActivity<MainActivity>()
            }

            // 앱 타공판 카드와 100% 동일한 포스트잇 룩앤필
            Box(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .cornerRadius(8.dp)
                    .background(bgColor)
                    .clickable(clickAction)
                    .padding(8.dp)
            ) {
                // 1. 상단 중앙 압정 핀 그래픽 (앱 카드와 위치/색상 일치)
                Row(
                    modifier = GlanceModifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = GlanceModifier
                            .size(12.dp)
                            .cornerRadius(6.dp)
                            .background(pinColor)
                    ) {}
                }

                // 2. 메모 본문 캔버스 영역 (텍스트 + 드로잉 비트맵 오버레이)
                Box(
                    modifier = GlanceModifier
                        .fillMaxSize()
                        .padding(top = 10.dp, start = 4.dp, end = 4.dp, bottom = 4.dp)
                ) {
                    // 레이어 A: 텍스트 (지정된 시스템 폰트 적용)
                    if (memoText.isNotEmpty()) {
                        Text(
                            text = memoText,
                            style = TextStyle(
                                fontSize = 13.sp,
                                color = ColorProvider(textColor),
                                fontFamily = widgetFontFamily
                            ),
                            maxLines = 8,
                            modifier = GlanceModifier.fillMaxSize()
                        )
                    }

                    // 레이어 B: 드로잉 손글씨 (투명 배경으로 텍스트 위에 1:1 오버레이)
                    if (hasBitmapDrawing && drawingFile != null) {
                        val bitmap = BitmapFactory.decodeFile(drawingFile.absolutePath)
                        if (bitmap != null) {
                            Image(
                                provider = ImageProvider(bitmap),
                                contentDescription = "Drawing Overlay",
                                modifier = GlanceModifier.fillMaxSize()
                            )
                        }
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
