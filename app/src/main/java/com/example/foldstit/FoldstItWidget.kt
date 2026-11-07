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
import androidx.glance.appwidget.GlanceAppWidgetManager
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
            val widgetMapPrefs = context.getSharedPreferences("widget_id_map", Context.MODE_PRIVATE)

            // 1. 개별 위젯 ID 확인
            val appWidgetId = try {
                GlanceAppWidgetManager(context).getAppWidgetId(id)
            } catch (_: Exception) { -1 }

            // 2. 이 위젯에 매핑된 고유 memoId 가져오기
            var targetMemoId = if (appWidgetId != -1) {
                widgetMapPrefs.getString("widget_$appWidgetId", "") ?: ""
            } else ""

            // fallback (초기 등록 시 매핑이 아직 비어있다면 대기 중인 메모 할당)
            if (targetMemoId.isEmpty()) {
                val pendingPrefs = context.getSharedPreferences("pending_pin_prefs", Context.MODE_PRIVATE)
                targetMemoId = pendingPrefs.getString("pending_memo_id", "") ?: ""
                if (targetMemoId.isNotEmpty() && appWidgetId != -1) {
                    widgetMapPrefs.edit().putString("widget_$appWidgetId", targetMemoId).apply()
                }
            }

            var memoText = ""
            var themeName = "PASTEL_YELLOW"
            var fontName = "SYSTEM_ONEUI"

            // 3. 해당 메모의 데이터 추출
            val jsonStr = boardPrefs.getString("memo_list_json", null)
            if (!jsonStr.isNullOrEmpty() && targetMemoId.isNotEmpty()) {
                try {
                    val arr = JSONArray(jsonStr)
                    for (i in 0 until arr.length()) {
                        val obj = arr.getJSONObject(i)
                        if (obj.optString("id") == targetMemoId) {
                            memoText = obj.optString("text", "")
                            themeName = obj.optString("theme", "PASTEL_YELLOW")
                            fontName = obj.optString("font", "SYSTEM_ONEUI")
                            break
                        }
                    }
                } catch (_: Exception) {}
            }

            // 4. 앱 타공판 카드와 1:1 완벽 일치하는 테마 규격
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
                else -> FontFamily.SansSerif
            }

            val drawingFile = if (targetMemoId.isNotEmpty()) File(context.filesDir, "drawing_${targetMemoId}.png") else null
            val hasBitmapDrawing = drawingFile != null && drawingFile.exists()

            val clickAction = if (targetMemoId.isNotEmpty()) {
                actionStartActivity<MainActivity>(actionParametersOf(memoIdKey to targetMemoId))
            } else {
                actionStartActivity<MainActivity>()
            }

            // 위젯 외형: 앱 타공판 카드와 똑같은 둥근 모서리 및 상단 중앙 16dp 원형 핀
            Box(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .cornerRadius(8.dp)
                    .background(bgColor)
                    .clickable(clickAction)
                    .padding(8.dp)
            ) {
                // 상단 중앙 핀
                Row(
                    modifier = GlanceModifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = GlanceModifier
                            .size(16.dp)
                            .cornerRadius(8.dp)
                            .background(pinColor)
                    ) {}
                }

                // 본문: 텍스트와 드로잉 손글씨 완전 오버레이
                Box(
                    modifier = GlanceModifier
                        .fillMaxSize()
                        .padding(top = 10.dp, start = 4.dp, end = 4.dp, bottom = 4.dp)
                ) {
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

                    if (hasBitmapDrawing && drawingFile != null) {
                        val bitmap = BitmapFactory.decodeFile(drawingFile.absolutePath)
                        if (bitmap != null) {
                            Image(
                                provider = ImageProvider(bitmap),
                                contentDescription = "Drawing Overlay",
                                modifier = GlanceModifier.fillMaxSize()
                            )
                        }
                    } else if (memoText.isEmpty()) {
                        Text(
                            text = "터치하여 이 포스트잇을 편집하세요.",
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
            val appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            var memoId = intent.getStringExtra("TARGET_MEMO_ID")

            if (memoId.isNullOrEmpty()) {
                val pendingPrefs = context.getSharedPreferences("pending_pin_prefs", Context.MODE_PRIVATE)
                memoId = pendingPrefs.getString("pending_memo_id", "")
            }

            if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID && !memoId.isNullOrEmpty()) {
                val widgetMapPrefs = context.getSharedPreferences("widget_id_map", Context.MODE_PRIVATE)
                widgetMapPrefs.edit().putString("widget_$appWidgetId", memoId).apply()
                FoldstItWidget.notifyUpdate(context)
            }
        }
    }
}
