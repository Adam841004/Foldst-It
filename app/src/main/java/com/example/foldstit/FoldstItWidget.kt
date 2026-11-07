package com.example.foldstit

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
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

        fun getNotebookBackground(context: Context): Bitmap {
            val file = File(context.cacheDir, "notebook_grid_clean.png")
            if (file.exists()) {
                val b = BitmapFactory.decodeFile(file.absolutePath)
                if (b != null) return b
            }
            val bmp = Bitmap.createBitmap(400, 400, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)
            canvas.drawColor(android.graphics.Color.WHITE)
            val paint = Paint().apply {
                color = android.graphics.Color.parseColor("#388D6E63")
                strokeWidth = 2f
            }
            var y = 60f
            while (y < 400f) {
                canvas.drawLine(0f, y, 400f, y, paint)
                y += 44f
            }
            try {
                java.io.FileOutputStream(file).use { out -> bmp.compress(Bitmap.CompressFormat.PNG, 100, out) }
            } catch (_: Exception) {}
            return bmp
        }
    }

    override val sizeMode: SizeMode = SizeMode.Responsive(
        setOf(SQUARE_SIZE, WIDE_SIZE, LARGE_SIZE)
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            val boardPrefs = context.getSharedPreferences("foldstit_board", Context.MODE_PRIVATE)
            val widgetMapPrefs = context.getSharedPreferences("widget_id_map", Context.MODE_PRIVATE)

            val appWidgetId = try {
                GlanceAppWidgetManager(context).getAppWidgetId(id)
            } catch (_: Exception) { -1 }

            var targetMemoId = if (appWidgetId != -1) {
                widgetMapPrefs.getString("widget_$appWidgetId", "") ?: ""
            } else ""

            if (targetMemoId.isEmpty()) {
                val pendingPrefs = context.getSharedPreferences("pending_pin_prefs", Context.MODE_PRIVATE)
                targetMemoId = pendingPrefs.getString("pending_memo_id", "") ?: ""
                if (targetMemoId.isNotEmpty() && appWidgetId != -1) {
                    widgetMapPrefs.edit().putString("widget_$appWidgetId", targetMemoId).apply()
                }
            }

            var memoText = ""
            var themeName = "PASTEL_YELLOW"
            var fontName = "DEFAULT"

            val jsonStr = boardPrefs.getString("memo_list_json", null)
            if (!jsonStr.isNullOrEmpty() && targetMemoId.isNotEmpty()) {
                try {
                    val arr = JSONArray(jsonStr)
                    for (i in 0 until arr.length()) {
                        val obj = arr.getJSONObject(i)
                        if (obj.optString("id") == targetMemoId) {
                            memoText = obj.optString("text", "")
                            themeName = obj.optString("theme", "PASTEL_YELLOW")
                            fontName = obj.optString("font", "DEFAULT")
                            break
                        }
                    }
                } catch (_: Exception) {}
            }

            val (bgColor, textColor, pinColor) = when (themeName) {
                "PASTEL_BLUE" -> Triple(Color(0xFFE1F5FE), Color(0xFF2C2C2C), Color(0xFFE53935))
                "PASTEL_PINK" -> Triple(Color(0xFFFCE4EC), Color(0xFF2C2C2C), Color(0xFF43A047))
                "PASTEL_GREEN" -> Triple(Color(0xFFE8F5E9), Color(0xFF2C2C2C), Color(0xFFFFB300))
                "NOTEBOOK" -> Triple(Color(0xFFFFFFFF), Color(0xFF1A237E), Color(0xFFD84315))
                else -> Triple(Color(0xFFFFF9C4), Color(0xFF2C2C2C), Color(0xFF1E88E5))
            }

            val widgetFontFamily: FontFamily? = when (fontName) {
                "MYEONGJO", "COOL_JAZZ" -> FontFamily.Serif
                "MONO" -> FontFamily.Monospace
                else -> null
            }

            val drawingFile = if (targetMemoId.isNotEmpty()) File(context.filesDir, "drawing_${targetMemoId}.png") else null
            val hasBitmapDrawing = drawingFile != null && drawingFile.exists()

            val clickAction = if (targetMemoId.isNotEmpty()) {
                actionStartActivity<MainActivity>(actionParametersOf(memoIdKey to targetMemoId))
            } else {
                actionStartActivity<MainActivity>()
            }

            // 위젯 프레임: 테두리 없이 둥근 모서리와 은은한 여백
            Box(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .cornerRadius(8.dp)
                    .background(bgColor)
                    .clickable(clickAction)
                    .padding(6.dp)
            ) {
                // 연습장 줄눈금
                if (themeName == "NOTEBOOK") {
                    val gridBmp = getNotebookBackground(context)
                    Image(
                        provider = ImageProvider(gridBmp),
                        contentDescription = "Lines Grid",
                        modifier = GlanceModifier.fillMaxSize()
                    )
                }

                // 온전한 원형 핀 (상단 안쪽에 위치하여 잘림 없음)
                Row(
                    modifier = GlanceModifier.fillMaxWidth().padding(top = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = GlanceModifier
                            .size(14.dp)
                            .cornerRadius(7.dp)
                            .background(pinColor)
                    ) {}
                }

                // 본문 오버레이
                Box(
                    modifier = GlanceModifier
                        .fillMaxSize()
                        .padding(top = 18.dp, start = 6.dp, end = 6.dp, bottom = 4.dp)
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
                            text = "터치하여 편집...",
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
