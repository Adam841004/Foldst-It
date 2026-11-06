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
            // 로컬 데이터 로드
            var memoText = "작성된 메모가 없습니다."
            var themeColor = "#FFF9C4" // 기본 포스트잇 노랑
            
            try {
                val file = File(context.filesDir, "foldstit_data.json")
                if (file.exists()) {
                    val json = JSONObject(file.readText())
                    val text = json.optString("text", "").trim()
                    if (text.isNotEmpty()) {
                        memoText = text
                    } else if (json.optJSONArray("paths")?.let { it.length() > 0 } == true) {
                        memoText = "[손글씨 메모가 포함되어 있습니다]"
                    }
                    val themeName = json.optString("theme", "YELLOW")
                    themeColor = PastelTheme.valueOf(themeName).hex
                }
            } catch (e: Exception) { e.printStackTrace() }

            // 위젯 렌더링 UI
            Column(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .background(ColorProvider(android.graphics.Color.parseColor(themeColor)))
                    .padding(12.dp),
                verticalAlignment = Alignment.Top,
                horizontalAlignment = Alignment.Start
            ) {
                Text(
                    text = "Foldst it 📌",
                    style = TextStyle(
                        fontSize = 12.sp,
                        color = ColorProvider(android.graphics.Color.parseColor("#757575"))
                    ),
                    modifier = GlanceModifier.padding(bottom = 6.dp)
                )
                Text(
                    text = memoText,
                    style = TextStyle(
                        fontSize = 15.sp,
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
