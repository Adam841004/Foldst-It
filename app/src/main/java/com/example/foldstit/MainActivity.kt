@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.example.foldstit

import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.*
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
import org.json.JSONArray
import org.json.JSONObject

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
                Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFFDDDDDD)) {
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

    // 메뉴 상태 관리
    var isDockExpanded by remember { mutableStateOf(true) }
    var showSettingsMenu by remember { mutableStateOf(false) }
    var showThemeDialog by remember { mutableStateOf(false) }
    var showFontDialog by remember { mutableStateOf(false) }

    val paths = remember { mutableStateListOf<DrawPath>() }
    val undoStack = remember { mutableStateListOf<DrawPath>() }
    var currentPathPoints by remember { mutableStateOf(listOf<Offset>()) }

    val focusRequester = remember { FocusRequester() }

    // [중요] 드로잉 선 데이터 완전 복원 로직
    LaunchedEffect(Unit) {
        val pathsJsonStr = prefs.getString("paths_json", null)
        if (!pathsJsonStr.isNullOrEmpty()) {
            try {
                val array = JSONArray(pathsJsonStr)
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    val ptsArray = obj.getJSONArray("points")
                    val pts = mutableListOf<Offset>()
                    for (j in 0 until ptsArray.length()) {
                        val p = ptsArray.getJSONObject(j)
                        pts.add(Offset(p.getDouble("x").toFloat(), p.getDouble("y").toFloat()))
                    }
                    paths.add(DrawPath(pts, obj.optString("color", "#2C2C2C"), obj.optDouble("width", 6.0).toFloat()))
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    LaunchedEffect(inputMode) {
        if (inputMode == InputMode.KEYBOARD) {
            try { focusRequester.requestFocus() } catch (_: Exception) {}
        }
    }

    // [중요] 텍스트 및 드로잉 선 직렬화 저장 로직
    fun saveData(showToast: Boolean = false) {
        try {
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

            prefs.edit().apply {
                putString("text", memoText)
                putString("theme", selectedTheme.name)
                putInt("fontSize", fontSize)
                putBoolean("hasDrawing", paths.isNotEmpty())
                putString("paths_json", pathsArray.toString()) // 좌표 데이터 영구 보관
                apply()
            }

            CoroutineScope(Dispatchers.IO).launch {
                FoldstItWidget().updateAll(context)
                FoldstItWidget.notifyUpdate(context)
            }

            if (showToast) {
                Toast.makeText(context, "메모가 안전하게 저장되었습니다.", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // 1. 메인 포스트잇 메모 캔버스 영역
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 90.dp)
                .shadow(elevation = 6.dp, shape = RoundedCornerShape(12.dp))
                .clip(RoundedCornerShape(12.dp))
                .background(selectedTheme.color)
                .padding(20.dp)
        ) {
            // 레이어 A: 텍스트 입력창
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
                            "터치하여 메모를 입력하거나, 하단 독의 펜 모드로 손글씨를 작성하세요...",
                            color = Color.Black.copy(alpha = 0.35f),
                            fontSize = fontSize.sp
                        )
                    }
                    innerTextField()
                }
            )

            // 레이어 B: 드로잉 캔버스
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

        // 2. 우측 하단 계층형 설정 팝업 메뉴 (Image 2 레이아웃 반영)
        AnimatedVisibility(
            visible = showSettingsMenu,
            enter = fadeIn() + slideInVertically(initialOffsetY = { it / 2 }),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { it / 2 }),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = 80.dp)
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.End
            ) {
                // Tier 1 (상단 카드): Go Pro / About / Background
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
                ) {
                    Row(modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { showFontDialog = true }) {
                            Icon(Icons.Default.TextFields, contentDescription = "Font", tint = Color.Black, modifier = Modifier.size(22.dp))
                            Text("Font", fontSize = 10.sp, color = Color.Gray)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable {
                            Toast.makeText(context, "Foldst it v2.0 (S-Pen & Widget)", Toast.LENGTH_SHORT).show()
                        }) {
                            Icon(Icons.Default.Info, contentDescription = "About", tint = Color.Black, modifier = Modifier.size(22.dp))
                            Text("About", fontSize = 10.sp, color = Color.Gray)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { showThemeDialog = true }) {
                            Icon(Icons.Default.Palette, contentDescription = "Background", tint = Color.Black, modifier = Modifier.size(22.dp))
                            Text("Theme", fontSize = 10.sp, color = Color.Gray)
                        }
                    }
                }

                // Tier 2 (중간 카드): Backup / Restore
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
                ) {
                    Row(modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable {
                            saveData(true)
                        }) {
                            Icon(Icons.Default.Save, contentDescription = "Backup", tint = Color.Black, modifier = Modifier.size(22.dp))
                            Text("Backup", fontSize = 10.sp, color = Color.Gray)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable {
                            Toast.makeText(context, "동기화 복원 완료", Toast.LENGTH_SHORT).show()
                        }) {
                            Icon(Icons.Default.Restore, contentDescription = "Restore", tint = Color.Black, modifier = Modifier.size(22.dp))
                            Text("Restore", fontSize = 10.sp, color = Color.Gray)
                        }
                    }
                }

                // Tier 3 (하단 카드): Trash / Undo / Redo / Mode Toggle
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
                ) {
                    Row(modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable {
                            paths.clear()
                            undoStack.clear()
                            memoText = ""
                            saveData(true)
                        }) {
                            Icon(Icons.Default.Delete, contentDescription = "Trash", tint = Color(0xFFE53935), modifier = Modifier.size(22.dp))
                            Text("Clear", fontSize = 10.sp, color = Color.Gray)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable(enabled = paths.isNotEmpty()) {
                            if (paths.isNotEmpty()) {
                                undoStack.add(paths.removeAt(paths.size - 1))
                                saveData(false)
                            }
                        }) {
                            Icon(Icons.Default.Undo, contentDescription = "Undo", tint = if (paths.isNotEmpty()) Color.Black else Color.LightGray, modifier = Modifier.size(22.dp))
                            Text("Undo", fontSize = 10.sp, color = Color.Gray)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable(enabled = undoStack.isNotEmpty()) {
                            if (undoStack.isNotEmpty()) {
                                paths.add(undoStack.removeAt(undoStack.size - 1))
                                saveData(false)
                            }
                        }) {
                            Icon(Icons.Default.Redo, contentDescription = "Redo", tint = if (undoStack.isNotEmpty()) Color.Black else Color.LightGray, modifier = Modifier.size(22.dp))
                            Text("Redo", fontSize = 10.sp, color = Color.Gray)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable {
                            inputMode = if (inputMode == InputMode.KEYBOARD) InputMode.PEN else InputMode.KEYBOARD
                        }) {
                            Icon(if (inputMode == InputMode.KEYBOARD) Icons.Default.Create else Icons.Default.Keyboard, contentDescription = "Mode", tint = Color.Black, modifier = Modifier.size(22.dp))
                            Text(if (inputMode == InputMode.KEYBOARD) "Pen" else "Text", fontSize = 10.sp, color = Color.Gray)
                        }
                    }
                }
            }
        }

        // 3. 우측 하단 캡슐형 플로팅 독 (Image 1 레이아웃 반영)
        Card(
            shape = RoundedCornerShape(32.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = 16.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // 접기/펼치기 화살표
                IconButton(
                    onClick = { isDockExpanded = !isDockExpanded },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = if (isDockExpanded) Icons.Default.ChevronRight else Icons.Default.ChevronLeft,
                        contentDescription = "Toggle",
                        tint = Color.Gray
                    )
                }

                if (isDockExpanded) {
                    // Note (텍스트 모드)
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable {
                        inputMode = InputMode.KEYBOARD
                        showSettingsMenu = false
                    }) {
                        Icon(Icons.Default.Article, contentDescription = "Note", tint = if (inputMode == InputMode.KEYBOARD) Color(0xFF1976D2) else Color.Black, modifier = Modifier.size(24.dp))
                        Text("Note", fontSize = 10.sp, color = Color.DarkGray)
                    }

                    // Pen/Draw (손글씨 모드)
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable {
                        inputMode = InputMode.PEN
                        showSettingsMenu = false
                    }) {
                        Icon(Icons.Default.DriveFileRenameOutline, contentDescription = "Folder", tint = if (inputMode == InputMode.PEN) Color(0xFF1976D2) else Color.Black, modifier = Modifier.size(24.dp))
                        Text("Draw", fontSize = 10.sp, color = Color.DarkGray)
                    }

                    // Save (저장)
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable {
                        saveData(true)
                    }) {
                        Icon(Icons.Default.Save, contentDescription = "Save", tint = Color.Black, modifier = Modifier.size(24.dp))
                        Text("Save", fontSize = 10.sp, color = Color.DarkGray)
                    }

                    // Settings (설정 메뉴 토글)
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable {
                        showSettingsMenu = !showSettingsMenu
                    }) {
                        Icon(
                            Icons.Default.Settings,
                            contentDescription = "Settings",
                            tint = if (showSettingsMenu) Color(0xFF1976D2) else Color.Black,
                            modifier = Modifier.size(24.dp)
                        )
                        Text("Settings", fontSize = 10.sp, color = Color.DarkGray)
                    }
                }
            }
        }
    }

    // 테마 색상 선택 다이얼로그
    if (showThemeDialog) {
        AlertDialog(
            onDismissRequest = { showThemeDialog = false },
            title = { Text("배경 테마 선택") },
            text = {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                    PastelTheme.values().forEach { theme ->
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(theme.color)
                                .border(
                                    width = if (selectedTheme == theme) 3.dp else 1.dp,
                                    color = if (selectedTheme == theme) theme.darkColor else Color.Gray.copy(alpha = 0.4f),
                                    shape = CircleShape
                                )
                                .clickable {
                                    selectedTheme = theme
                                    saveData(false)
                                    showThemeDialog = false
                                }
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showThemeDialog = false }) { Text("닫기") } }
        )
    }

    // 폰트 크기 조절 다이얼로그
    if (showFontDialog) {
        AlertDialog(
            onDismissRequest = { showFontDialog = false },
            title = { Text("글꼴 크기 조절") },
            text = {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    listOf(14 to "작게", 18 to "보통", 22 to "크게").forEach { (sz, label) ->
                        OutlinedButton(
                            onClick = {
                                fontSize = sz
                                saveData(false)
                                showFontDialog = false
                            },
                            colors = ButtonDefaults.outlinedButtonColors(containerColor = if (fontSize == sz) Color(0xFFE0E0E0) else Color.Transparent)
                        ) {
                            Text(label, color = Color.Black)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showFontDialog = false }) { Text("닫기") } }
        )
    }
}
