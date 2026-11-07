package com.example.foldstit

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
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
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.*
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

        // [핵심] 우측 하단이 접힌 진짜 종이 포스트잇 셰이프 비트맵 렌더러
        fun getPostitShapeBackground(context: Context, themeName: String, memoId: String): Bitmap {
            val file = File(context.cacheDir, "postit_bg_${themeName}_${memoId}.png")
            if (file.exists()) {
                val b = BitmapFactory.decodeFile(file.absolutePath)
                if (b != null) return b
            }

            val w = 500f
            val h = 500f
            val fold = 64f // 우측 하단 접힌 귀퉁이 크기

            val bmp = Bitmap.createBitmap(w.toInt(), h.toInt(), Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)

            val (bgColorInt, pinColorInt, hasLines) = when (themeName) {
                "PASTEL_BLUE" -> Triple(android.graphics.Color.parseColor("#E1F5FE"), android.graphics.Color.parseColor("#E53935"), false)
                "PASTEL_PINK" -> Triple(android.graphics.Color.parseColor("#FCE4EC"), android.graphics.Color.parseColor("#43A047"), false)
                "PASTEL_GREEN" -> Triple(android.graphics.Color.parseColor("#E8F5E9"), android.graphics.Color.parseColor("#FFB300"), false)
                "NOTEBOOK" -> Triple(android.graphics.Color.WHITE, android.graphics.Color.parseColor("#E53935"), true)
                else -> Triple(android.graphics.Color.parseColor("#FFF9C4"), android.graphics.Color.parseColor("#1E88E5"), false)
            }

            // 1. 접힌 부분 아래 그림자
            val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = android.graphics.Color.parseColor("#33000000")
            }
            val shadowPath = Path().apply {
                moveTo(w - fold - 10f, h - fold - 10f)
                lineTo(w, h - fold)
                lineTo(w - fold, h)
                close()
            }
            canvas.drawPath(shadowPath, shadowPaint)

            // 2. 포스트잇 본체 종이 (우측 하단이 대각선으로 깎인 다각형)
            val paperPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = bgColorInt
                style = Paint.Style.FILL
            }
            val paperPath = Path().apply {
                moveTo(16f, 0f)
                lineTo(w - 16f, 0f)
                quadTo(w, 0f, w, 16f)
                lineTo(w, h - fold)
                lineTo(w - fold, h)
                lineTo(16f, h)
                quadTo(0f, h, 0f, h - 16f)
                lineTo(0f, 16f)
                quadTo(0f, 0f, 16f, 0f)
                close()
            }
            canvas.drawPath(paperPath, paperPaint)

            // 3. 연습장 줄눈금
            if (hasLines) {
                val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = android.graphics.Color.parseColor("#388D6E63")
                    strokeWidth = 2.5f
                }
                var y = 75f
                while (y < h - 40f) {
                    canvas.drawLine(15f, y, w - 15f, y, linePaint)
                    y += 50f
                }
            }

            // 4. 우측 하단 접혀 올라온 귀퉁이 삼각형 (살짝 어두운 톤)
            val foldPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = android.graphics.Color.parseColor("#E0D8A8")
                style = Paint.Style.FILL
            }
            val foldPath = Path().apply {
                moveTo(w - fold, h)
                lineTo(w, h - fold)
                lineTo(w - fold, h - fold)
                close()
            }
            canvas.drawPath(foldPath, foldPaint)

            // 5. 상단 중앙 압정 핀 (입체 원형)
            val pinPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = pinColorInt
                style = Paint.Style.FILL
            }
            canvas.drawCircle(w / 2f, 26f, 16f, pinPaint)

            val pinBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = android.graphics.Color.WHITE
                style = Paint.Style.STROKE
                strokeWidth = 3f
            }
            canvas.drawCircle(w / 2f, 26f, 16f, pinBorderPaint)

            try {
                FileOutputStream(file).use { out -> bmp.compress(Bitmap.CompressFormat.PNG, 100, out) }
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

            val jsonStr = boardPrefs.getString("memo_list_json", null)
            if (!jsonStr.isNullOrEmpty() && targetMemoId.isNotEmpty()) {
                try {
                    val arr = JSONArray(jsonStr)
                    for (i in 0 until arr.length()) {
                        val obj = arr.getJSONObject(i)
                        if (obj.optString("id") == targetMemoId) {
                            memoText = obj.optString("text", "")
                            themeName = obj.optString("theme", "PASTEL_YELLOW")
                            break
                        }
                    }
                } catch (_: Exception) {}
            }

            // 고화질 단어별 서식 비트맵
            val textFile = if (targetMemoId.isNotEmpty()) File(context.filesDir, "text_${targetMemoId}.png") else null
            val hasTextBitmap = textFile != null && textFile.exists()

            // 손글씨 드로잉 비트맵
            val drawingFile = if (targetMemoId.isNotEmpty()) File(context.filesDir, "drawing_${targetMemoId}.png") else null
            val hasBitmapDrawing = drawingFile != null && drawingFile.exists()

            val clickAction = if (targetMemoId.isNotEmpty()) {
                actionStartActivity<MainActivity>(actionParametersOf(memoIdKey to targetMemoId))
            } else {
                actionStartActivity<MainActivity>()
            }

            // 시스템 둥근 사각 테두리를 없애기 위해 컨테이너 배경을 투명으로 설정
            Box(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .background(Color.Transparent)
                    .clickable(clickAction)
            ) {
                // 레이어 1: 우측 하단이 접힌 종이 포스트잇 셰이프
                val postitBgBmp = getPostitShapeBackground(context, themeName, targetMemoId)
                Image(
                    provider = ImageProvider(postitBgBmp),
                    contentDescription = "Post-it Shape Paper",
                    modifier = GlanceModifier.fillMaxSize()
                )

                // 레이어 2: 본문 (단어별 폰트/색상 텍스트 비트맵 + 손글씨 오버레이)
                Box(
                    modifier = GlanceModifier
                        .fillMaxSize()
                        .padding(top = 22.dp, start = 8.dp, end = 12.dp, bottom = 14.dp)
                ) {
                    if (hasTextBitmap && textFile != null) {
                        val txtBmp = BitmapFactory.decodeFile(textFile.absolutePath)
                        if (txtBmp != null) {
                            Image(
                                provider = ImageProvider(txtBmp),
                                contentDescription = "Rich Text",
                                modifier = GlanceModifier.fillMaxSize()
                            )
                        }
                    } else if (memoText.isNotEmpty()) {
                        Text(
                            text = memoText,
                            style = TextStyle(fontSize = 13.sp, color = ColorProvider(Color.Black)),
                            maxLines = 8,
                            modifier = GlanceModifier.fillMaxSize()
                        )
                    }

                    if (hasBitmapDrawing && drawingFile != null) {
                        val drawBmp = BitmapFactory.decodeFile(drawingFile.absolutePath)
                        if (drawBmp != null) {
                            Image(
                                provider = ImageProvider(drawBmp),
                                contentDescription = "Drawing Overlay",
                                modifier = GlanceModifier.fillMaxSize()
                            )
                        }
                    } else if (memoText.isEmpty() && !hasTextBitmap) {
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
