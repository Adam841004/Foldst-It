@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.example.foldstit

import android.content.Context
import android.os.Bundle
import android.widget.Toast
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
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
import androidx.compose.ui.zIndex
import androidx.glance.appwidget.updateAll
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

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
    val prefs = remember { context.getSharedPreferences("foldstit_data", Context.MODE_PRIVATE) }

    var memoText by remember { mutableStateOf(prefs.getString("text", "") ?: "") }
    var selectedTheme by remember {
        val themeName = prefs.getString("theme", "YELLOW") ?: "YELLOW"
        mutableStateOf(try { PastelTheme.valueOf(themeName) } catch (_: Exception) { PastelTheme.YELLOW })
    }
    var inputMode by remember { mutableStateOf(InputMode.KEYBOARD) }
    var fontSize by remember { mutableStateOf(prefs.getInt("fontSize", 18)) }
    var showSettingsDialog by remember { mutableStateOf(false) }

    val paths = remember { mutableStateListOf<DrawPath>() }
    val undoStack = remember { mutableStateListOf<DrawPath>() }
    var currentPathPoints by remember { mutableStateOf(listOf<Offset>()) }

    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(inputMode) {
        if (inputMode == InputMode.KEYBOARD) {
            try { focusRequester.requestFocus() } catch (_: Exception) {}
        }
    }

    fun saveData(showToast: Boolean = false) {
        try {
            prefs.edit().apply {
                putString("text", memoText)
                putString("theme", selectedTheme.name)
                putInt("fontSize", fontSize)
                putBoolean("hasDrawing", paths.isNotEmpty())
                apply()
            }

            CoroutineScope(Dispatchers.IO).launch {
                FoldstItWidget().updateAll(context)
                FoldstItWidget.notifyUpdate(context)
            }

            if (showToast) {
                Toast.makeText(context, "메모가 저장되고 위젯에 반영되었습니다.", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) { e.printStackTrace() }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
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
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = { inputMode = InputMode.KEYBOARD },
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = if (inputMode == InputMode.KEYBOARD) Color(0xFFE0E0E0) else Color.Transparent
                        )
                    ) {
                        Icon(Icons.Default.Keyboard, contentDescription = "Keyboard", tint = Color.Black)
                    }
                    IconButton(
                        onClick = { inputMode = InputMode.PEN },
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = if (inputMode == InputMode.PEN) Color(0xFFE0E0E0) else Color.Transparent
                        )
                    ) {
                        Icon(Icons.Default.Create, contentDescription = "Pen", tint = Color.Black)
                    }

                    IconButton(
                        onClick = {
                            if (paths.isNotEmpty()) {
                                undoStack.add(paths.removeAt(paths.size - 1))
                                saveData(false)
                            }
                        },
                        enabled = paths.isNotEmpty()
                    ) {
                        Icon(Icons.Default.Undo, contentDescription = "Undo", tint = if (paths.isNotEmpty()) Color.Black else Color.LightGray)
                    }

                    IconButton(
                        onClick = {
                            if (undoStack.isNotEmpty()) {
                                paths.add(undoStack.removeAt(undoStack.size - 1))
                                saveData(false)
                            }
                        },
                        enabled = undoStack.isNotEmpty()
                    ) {
                        Icon(Icons.Default.Redo, contentDescription = "Redo", tint = if (undoStack.isNotEmpty()) Color.Black else Color.LightGray)
                    }

                    IconButton(onClick = { saveData(true) }) {
                        Icon(Icons.Default.Save, contentDescription = "Save", tint = Color(0xFF1976D2))
                    }

                    IconButton(onClick = { showSettingsDialog = true }) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings", tint = Color(0xFF424242))
                    }
                }

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
                                    saveData(false)
                                }
                        )
                    }
                }
            }
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .shadow(elevation = 6.dp, shape = RoundedCornerShape(12.dp))
                .clip(RoundedCornerShape(12.dp))
                .background(selectedTheme.color)
                .padding(20.dp)
        ) {
            BasicTextField(
                value = memoText,
                onValueChange = {
                    memoText = it
                    saveData(false)
                },
                textStyle = TextStyle(
                    fontSize = fontSize.sp,
                    color = Color(0xFF2C2C2C),
                    fontFamily = FontFamily.SansSerif,
                    lineHeight = (fontSize + 8).sp
                ),
                modifier = Modifier
                    .fillMaxSize()
                    .focusRequester(focusRequester)
                    .zIndex(if (inputMode == InputMode.KEYBOARD) 2f else 0f),
                enabled = (inputMode == InputMode.KEYBOARD),
                decorationBox = { innerTextField ->
                    if (memoText.isEmpty() && paths.isEmpty()) {
                        Text(
                            "여기를 터치해 글자를 입력하거나,\n상단 펜 아이콘을 눌러 필기하세요.",
                            color = Color.Black.copy(alpha = 0.35f),
                            fontSize = fontSize.sp
                        )
                    }
                    innerTextField()
                }
            )

            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(if (inputMode == InputMode.PEN) 2f else 1f)
                    .pointerInput(inputMode) {
                        if (inputMode == InputMode.PEN) {
                            detectDragGestures(
                                onDragStart = { offset ->
                                    undoStack.clear()
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
                                        saveData(false)
                                    }
                                }
                            )
                        }
                    }
            ) {
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

    if (showSettingsDialog) {
        AlertDialog(
            onDismissRequest = { showSettingsDialog = false },
            title = { Text("Foldst it 설정", fontWeight = androidx.compose.ui.text.font.FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("1. 폰트 크기 선택", fontSize = 14.sp, color = Color.Gray)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(14 to "작게", 18 to "보통", 22 to "크게").forEach { (size, label) ->
                            OutlinedButton(
                                onClick = {
                                    fontSize = size
                                    saveData(false)
                                },
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = if (fontSize == size) Color(0xFFE0E0E0) else Color.Transparent
                                )
                            ) {
                                Text(label, color = Color.Black)
                            }
                        }
                    }

                    Divider()

                    Text("2. 위젯 등록 안내", fontSize = 14.sp, color = Color.Gray)
                    Text(
                        "홈 화면 빈 공간 길게 누르기 → [위젯] → [Foldst it] 선택 후 바탕화면에 배치하면 작성한 포스트잇이 실시간 동기화됩니다.",
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showSettingsDialog = false }) {
                    Text("확인")
                }
            }
        )
    }
}
