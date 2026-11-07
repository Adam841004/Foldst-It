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
import androidx.glance.text.FontWeight
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

            if (memoText.isEmpty() && targetMemoId.isEmpty()) {
                memoText = legacyPrefs.getString("text", "") ?: ""
                themeName = legacyPrefs.getString("theme", "PASTEL_YELLOW") ?: "PASTEL_YELLOW"
            }

            val (bgColor, textColor, pinColor) = when (themeName) {
                "PASTEL_BLUE" -> Triple(Color(0xFFE1F5FE), Color(0xFF2C2C2C), Color(0xFFE53935))
                "PASTEL_PINK" -> Triple(Color(0xFFFCE4EC), Color(0xFF2C2C2C), Color(0xFF43A047))
                "PASTEL_GREEN" -> Triple(Color(0xFFE8F5E9), Color(0xFF2C2C2C), Color(0xFFFFB300))
                "NOTEBOOK" -> Triple(Color(0xFFFFFDF9), Color(0xFF1A237E), Color(0xFFD84315))
                else -> Triple(Color(0xFFFFF9C4), Color(0xFF2C2C2C), Color(0xFF1E88E5))
            }

            val drawingFile = if (targetMemoId.isNotEmpty()) File(context.filesDir, "drawing_${targetMemoId}.png") else null
            val hasBitmapDrawing = drawingFile != null && drawingFile.exists()

            // Glance 규격: actionStartActivity 제네릭 호출 (타입 매칭 오류 완전 해결)
            val clickAction = if (targetMemoId.isNotEmpty()) {
                actionStartActivity<MainActivity>(actionParametersOf(memoIdKey to targetMemoId))
            } else {
                actionStartActivity<MainActivity>()
            }

            Box(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .cornerRadius(10.dp)
                    .background(bgColor)
                    .clickable(clickAction)
                    .padding(10.dp)
            ) {
                Column(
                    modifier = GlanceModifier.fillMaxSize(),
                    verticalAlignment = Alignment.Top,
                    horizontalAlignment = Alignment.Start
                ) {
                    Row(
                        modifier = GlanceModifier.fillMaxWidth().padding(bottom = 2.dp),
                        horizontalAlignment = Alignment.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (themeName == "NOTEBOOK") "🗒️ " else "📌 ",
                            style = TextStyle(fontSize = 11.sp)
                        )
                        Text(
                            text = "Foldst it",
                            style = TextStyle(
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = ColorProvider(pinColor)
                            )
                        )
                    }

                    if (memoText.isNotEmpty()) {
                        Text(
                            text = memoText,
                            style = TextStyle(
                                fontSize = 13.sp,
                                color = ColorProvider(textColor)
                            ),
                            maxLines = if (hasBitmapDrawing) 2 else 7
                        )
                    }

                    if (hasBitmapDrawing && drawingFile != null) {
                        val bitmap = BitmapFactory.decodeFile(drawingFile.absolutePath)
                        if (bitmap != null) {
                            Spacer(modifier = GlanceModifier.height(4.dp))
                            Image(
                                provider = ImageProvider(bitmap),
                                contentDescription = "Drawing Preview",
                                modifier = GlanceModifier.fillMaxWidth().height(75.dp)
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
            val memoId = intent.getStringExtra("TARGET_MEMO_ID")
            if (!memoId.isNullOrEmpty()) {
                val prefs = context.getSharedPreferences("foldstit_data", Context.MODE_PRIVATE)
                prefs.edit().putString("latest_memo_id", memoId).apply()
                FoldstItWidget.notifyUpdate(context)
            }
        }
    }
}
