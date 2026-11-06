@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.example.foldstit

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.appwidget.updateAll
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

enum class PastelTheme(val color: Color, val darkColor: Color, val hex: String) {
    YELLOW(Color(0xFFFFF9C4), Color(0xFFFBC02D), "#FFF9C4"),
    BLUE(Color(0xFFE1F5FE), Color(0xFF03A9F4), "#E1F5FE"),
    GREEN(Color(0xFFE8F5E9), Color(0xFF4CAF50), "#E8F5E9"),
    PURPLE(Color(0xFFF3E5F5), Color(0xFFAB47BC), "#F3E5F5"),
    PEACH(Color(0xFFFFE0B2), Color(0xFFFF9800), "#FFE0B2")
}

data class DrawPath(val points: List<Offset>, val hexColor: String = "#2C2C2C", val strokeWidth: Float = 6f)

enum class InputMode { KEYBOARD, PEN }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFFF5F5F7)) {
                    FoldstItEditor(context = this)
                }
            }
        }
    }
}

@Composable
fun FoldstItEditor(context: Context) {
    var memoText by remember { mutableStateOf("") }
    var selectedTheme by remember { mutableStateOf(PastelTheme.YELLOW) }
    var inputMode by remember { mutableStateOf(InputMode.KEYBOARD) }
    
    val paths = remember { mutableStateListOf<DrawPath>() }
    val undoStack = remember { mutableStateListOf<DrawPath>() } // 실행 취소용 스택
    var currentPathPoints by remember { mutableStateOf(listOf<Offset>()) }

    // 데이터 로드
    LaunchedEffect(Unit) {
        try {
            val file = File(context.filesDir, "foldstit_data.json")
            if (file.exists()) {
                val json = JSONObject(file.readText())
                memoText = json.optString("text", "")
                val themeName = json.optString("theme", "YELLOW")
                selectedTheme = PastelTheme.valueOf(themeName)
                
                val pathsArray = json.optJSONArray("paths")
                if (pathsArray != null) {
                    for (i in 0 until pathsArray.length()) {
                        val pathObj = pathsArray.getJSONObject(i)
                        val pointsArray = pathObj.getJSONArray("points")
                        val points = mutableListOf<Offset>()
                        for (j in 0 until pointsArray.length()) {
                            val pt = pointsArray.getJSONObject(j)
                            points.add(Offset(pt.getDouble("x").toFloat(), pt.getDouble("y").toFloat()))
                        }
                        paths.add(DrawPath(points, pathObj.optString("color", "#2C2C2C"), pathObj.optDouble("width", 6.0).toFloat()))
                    }
                }
            }
        } catch (e: Exception) { e.printStackTrace() }
    }

    // 데이터 저장 & 위젯 갱신
    fun saveData() {
        try {
            val file = File(context.filesDir, "foldstit_data.json")
            val json = JSONObject().apply {
                put("text", memoText)
                put("theme", selectedTheme.name)
                
                val pathsArray = JSONArray()
                paths.forEach { drawPath ->
                    val pathObj = JSONObject().apply {
                        put("color", drawPath.hexColor)
                        put("width", drawPath.strokeWidth)
                        val pointsArray = JSONArray()
                        drawPath.points.forEach { pt ->
                            pointsArray.put(JSONObject().apply {
                                put("x", pt.x)
                                put("y", pt.y)
                            })
                        }
                        put("points", pointsArray)
                    }
                    pathsArray.put(pathObj)
                }
                put("paths", pathsArray)
            }
            file.writeText(json.toString())
            
            // 홈 화면 위젯 실시간 강제 업데이트 트리거
            CoroutineScope(Dispatchers.IO).launch {
                FoldstItWidget().updateAll(context)
            }
        } catch (e: Exception) { e.printStackTrace() }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 상단 툴바
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 모드 & 편집 컨트롤
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    IconButton(
                        onClick = { inputMode = InputMode.KEYBOARD },
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = if (inputMode == InputMode.KEYBOARD) Color(0xFFEEEEEE) else Color.Transparent
                        )
                    ) {
                        Icon(Icons.Default.Keyboard, contentDescription = "Keyboard", tint = Color.Black)
                    }
                    IconButton(
                        onClick = { inputMode = InputMode.PEN },
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = if (inputMode == InputMode.PEN) Color(0xFFEEEEEE) else Color.Transparent
                        )
                    ) {
                        Icon(Icons.Default.Create, contentDescription = "Pen", tint = Color.Black)
                    }
                    
                    // 실행 취소 (Undo)
                    IconButton(
                        onClick = {
                            if (paths.isNotEmpty()) {
                                val removed = paths.removeAt(paths.size - 1)
                                undoStack.add(removed)
                                saveData()
                            }
                        },
                        enabled = paths.isNotEmpty()
                    ) {
                        Icon(Icons.Default.Undo, contentDescription = "Undo", tint = if (paths.isNotEmpty()) Color.Black else Color.LightGray)
                    }

                    // 다시 실행 (Redo)
                    IconButton(
                        onClick = {
                            if (undoStack.isNotEmpty()) {
                                val restored = undoStack.removeAt(undoStack.size - 1)
                                paths.add(restored)
                                saveData()
                            }
                        },
                        enabled = undoStack.isNotEmpty()
                    ) {
                        Icon(Icons.Default.Redo, contentDescription = "Redo", tint = if (undoStack.isNotEmpty()) Color.Black else Color.LightGray)
                    }

                    // 전체 초기화
                    IconButton(onClick = { 
                        paths.clear()
                        undoStack.clear()
                        memoText = ""
                        saveData()
                    }) {
                        Icon(Icons.Default.Delete, contentDescription = "Clear All", tint = Color.Red.copy(alpha = 0.7f))
                    }
                }

                // 색상 선택
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    PastelTheme.values().forEach { theme ->
                        Box(
                            modifier = Modifier
                                .size(22.dp)
                                .clip(CircleShape)
                                .background(theme.color)
                                .border(
                                    width = if (selectedTheme == theme) 2.dp else 1.dp,
                                    color = if (selectedTheme == theme) theme.darkColor else Color.Gray.copy(alpha = 0.3f),
                                    shape = CircleShape
                                )
                                .clickable { 
                                    selectedTheme = theme 
                                    saveData()
                                }
                        )
                    }
                }
            }
        }

        // 캔버스 & 텍스트 필드 영역
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .shadow(elevation = 6.dp, shape = RoundedCornerShape(12.dp))
                .clip(RoundedCornerShape(12.dp))
                .background(selectedTheme.color)
                .padding(20.dp)
        ) {
            // 레이어 1: 텍스트 입력창 (키보드 모드일 때만 제스처 입력 허용)
            BasicTextField(
                value = memoText,
                onValueChange = { 
                    memoText = it
                    saveData()
                },
                textStyle = TextStyle(fontSize = 18.sp, color = Color(0xFF2C2C2C), fontFamily = FontFamily.SansSerif, lineHeight = 26.sp),
                modifier = Modifier.fillMaxSize(),
                enabled = (inputMode == InputMode.KEYBOARD), // 펜 모드일 때 키보드 충돌 차단
                decorationBox = { innerTextField ->
                    if (memoText.isEmpty() && paths.isEmpty()) {
                        Text("여기를 터치해 메모를 적거나, 상단 펜 모드로 손글씨를 써보세요...", color = Color.Black.copy(alpha = 0.3f), fontSize = 16.sp)
                    }
                    innerTextField()
                }
            )

            // 레이어 2: 펜 필기 캔버스 (오버레이)
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(inputMode) {
                        if (inputMode == InputMode.PEN) {
                            detectDragGestures(
                                onDragStart = { offset ->
                                    undoStack.clear() // 새 그리기를 시작하면 다시 실행 스택 폭파
                                    currentPathPoints = listOf(offset)
                                },
                                onDrag = { change, _ ->
                                    change.consume()
                                    currentPathPoints = currentPathPoints + change.position
                                },
                                onDragEnd = {
                                    if (currentPathPoints.isNotEmpty()) {
                                        paths.add(DrawPath(currentPathPoints))
                                        currentPathPoints = emptyList()
                                        saveData() // 그리기 완료 시 자동 저장
                                    }
                                }
                            )
                        }
                    }
            ) {
                // 그린 획 그리기
                paths.forEach { drawPath ->
                    if (drawPath.points.size > 1) {
                        val path = Path().apply {
                            moveTo(drawPath.points.first().x, drawPath.points.first().y)
                            for (point in drawPath.points) { lineTo(point.x, point.y) }
                        }
                        drawPath(
                            path = path,
                            color = Color(android.graphics.Color.parseColor(drawPath.hexColor)),
                            style = Stroke(width = drawPath.strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round)
                        )
                    }
                }

                // 실시간 그리는 획 그리기
                if (currentPathPoints.size > 1) {
                    val path = Path().apply {
                        moveTo(currentPathPoints.first().x, currentPathPoints.first().y)
                        for (point in currentPathPoints) { lineTo(point.x, point.y) }
                    }
                    drawPath(
                        path = path,
                        color = Color(0xFF2C2C2C),
                        style = Stroke(width = 6f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                    )
                }
            }
        }
    }
}
