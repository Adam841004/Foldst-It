package com.example.foldstit

import android.content.Context
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.*
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import org.json.JSONObject
import java.io.File

class FoldstItWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            var memoText = "메모를 작성해주세요."
            var themeColor = "#FFF9C4"
            var widgetFontSize = 15

            try {
                val file = File(context.filesDir, "foldstit_data.json")
                if (file.exists()) {
                    val json = JSONObject(file.readText())
                    val text = json.optString("text", "").trim()
                    val pathsCount = json.optJSONArray("paths")?.length() ?: 0

                    memoText = when {
                        text.isNotEmpty() && pathsCount > 0 -> "$text\n\n[✍️ 손글씨 포함됨]"
                        text.isNotEmpty() -> text
                        pathsCount > 0 -> "[✍️ 손글씨 메모]"
                        else -> "작성된 메모가 없습니다."
                    }

                    val themeName = json.optString("theme", "YELLOW")
                    themeColor = PastelTheme.valueOf(themeName).hex
                    widgetFontSize = json.optInt("fontSize", 18) - 2
                }
            } catch (e: Exception) { e.printStackTrace() }

            Column(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .background(ColorProvider(android.graphics.Color.parseColor(themeColor)))
                    .padding(14.dp),
                verticalAlignment = Alignment.Top,
                horizontalAlignment = Alignment.Start
            ) {
                Row(
                    modifier = GlanceModifier.fillMaxWidth().padding(bottom = 6.dp),
                    horizontalAlignment = Alignment.End
                ) {
                    Text(
                        text = "Foldst it 📌",
                        style = TextStyle(
                            fontSize = 11.sp,
                            color = ColorProvider(android.graphics.Color.parseColor("#757575"))
                        )
                    )
                }

                Text(
                    text = memoText,
                    style = TextStyle(
                        fontSize = widgetFontSize.sp,
                        color = ColorProvider(android.graphics.Color.parseColor("#2C2C2C"))
                    )
                )
            }
        }
    }
}

class FoldstItWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = FoldstItWidget()
}
