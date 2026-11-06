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
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Refresh
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// 파스텔 포스트잇 컬러 테마 정의
enum class PastelTheme(val color: Color, val darkColor: Color) {
    YELLOW(Color(0xFFFFF9C4), Color(0xFFFBC02D)),
    BLUE(Color(0xFFE1F5FE), Color(0xFF03A9F4)),
    GREEN(Color(0xFFE8F5E9), Color(0xFF4CAF50)),
    PURPLE(Color(0xFFF3E5F5), Color(0xFFAB47BC)),
    PEACH(Color(0xFFFFE0B2), Color(0xFFFF9800))
}

data class DrawPath(val points: List<Offset>, val color: Color, val strokeWidth: Float)

enum class InputMode { KEYBOARD, PEN, ERASER }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color(0xFFF5F5F7)
                ) {
                    FoldstItEditor(context = this)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FoldstItEditor(context: Context) {
    val prefs = remember { context.getSharedPreferences("foldstit_prefs", Context.MODE_PRIVATE) }
    
    var memoText by remember { mutableStateOf(prefs.getString("saved_memo", "") ?: "") }
    var selectedTheme by remember { mutableStateOf(PastelTheme.YELLOW) }
    var inputMode by remember { mutableStateOf(InputMode.KEYBOARD) }
    
    val paths = remember { mutableStateListOf<DrawPath>() }
    var currentPathPoints by remember { mutableStateOf(listOf<Offset>()) }

    // 자동 저장
    LaunchedEffect(memoText) {
        prefs.edit().putString("saved_memo", memoText).apply()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 상단 컨트롤 툴바
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 모드 전환 버튼 (키보드 / 펜 / 지우개)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
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
                    IconButton(
                        onClick = { paths.clear() }
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Clear Pen", tint = Color.Red.copy(alpha = 0.7f))
                    }
                }

                // 파스텔 컬러 선택기
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PastelTheme.values().forEach { theme ->
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(theme.color)
                                .border(
                                    width = if (selectedTheme == theme) 2.dp else 1.dp,
                                    color = if (selectedTheme == theme) theme.darkColor else Color.Gray.copy(alpha = 0.3f),
                                    shape = CircleShape
                                )
                                .clickable { selectedTheme = theme }
                        )
                    }
                }
            }
        }

        // 3M 포스트잇 캔버스
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .shadow(elevation = 6.dp, shape = RoundedCornerShape(12.dp))
                .clip(RoundedCornerShape(12.dp))
                .background(selectedTheme.color)
                .padding(20.dp)
        ) {
            // 레이어 1: 텍스트 입력창 (키보드 모드)
            BasicTextField(
                value = memoText,
                onValueChange = { memoText = it },
                textStyle = TextStyle(
                    fontSize = 18.sp,
                    color = Color(0xFF2C2C2C),
                    fontFamily = FontFamily.SansSerif,
                    lineHeight = 26.sp
                ),
                modifier = Modifier.fillMaxSize(),
                decorationBox = { innerTextField ->
                    if (memoText.isEmpty() && paths.isEmpty()) {
                        Text(
                            text = "메모를 적거나 펜으로 필기해보세요...",
                            color = Color.Black.copy(alpha = 0.3f),
                            fontSize = 18.sp
                        )
                    }
                    innerTextField()
                }
            )

            // 레이어 2: 터치/S펜 손글씨 캔버스 (오버레이)
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(inputMode) {
                        if (inputMode == InputMode.PEN) {
                            detectDragGestures(
                                onDragStart = { offset ->
                                    currentPathPoints = listOf(offset)
                                },
                                onDrag = { change, _ ->
                                    change.consume()
                                    currentPathPoints = currentPathPoints + change.position
                                },
                                onDragEnd = {
                                    if (currentPathPoints.isNotEmpty()) {
                                        paths.add(DrawPath(currentPathPoints, Color(0xFF2C2C2C), 6f))
                                        currentPathPoints = emptyList()
                                    }
                                }
                            )
                        }
                    }
            ) {
                // 저장된 드로잉 패스 렌더링
                paths.forEach { drawPath ->
                    if (drawPath.points.size > 1) {
                        val path = Path().apply {
                            moveTo(drawPath.points.first().x, drawPath.points.first().y)
                            for (point in drawPath.points) {
                                lineTo(point.x, point.y)
                            }
                        }
                        drawPath(
                            path = path,
                            color = drawPath.color,
                            style = Stroke(
                                width = drawPath.strokeWidth,
                                cap = StrokeCap.Round,
                                join = StrokeJoin.Round
                            )
                        )
                    }
                }

                // 현재 실시간으로 그리는 중인 패스
                if (currentPathPoints.size > 1) {
                    val path = Path().apply {
                        moveTo(currentPathPoints.first().x, currentPathPoints.first().y)
                        for (point in currentPathPoints) {
                            lineTo(point.x, point.y)
                        }
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
