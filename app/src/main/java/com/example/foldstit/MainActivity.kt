@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.example.foldstit

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Paint
import android.os.Build
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
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.zIndex
import androidx.glance.appwidget.updateAll
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

// 메모지 테마 규격
enum class NoteTheme(val title: String, val bgColor: Color, val textColor: Color, val pinColor: Color) {
    POSTIT_YELLOW("노랑 포스트잇", Color(0xFFFFF176), Color(0xFF212121), Color(0xFF1E88E5)),
    POSTIT_BLUE("파랑 포스트잇", Color(0xFF81D4FA), Color(0xFF212121), Color(0xFFE53935)),
    NOTEBOOK("연습장 줄노트", Color(0xFFFAFAFA), Color(0xFF1A237E), Color(0xFF43A047)),
    BLACK_PAPER("흑지(다크)", Color(0xFF263238), Color(0xFFECEFF1), Color(0xFFFFB300)),
    WHITE_PAPER("백지(클린)", Color(0xFFFFFFFF), Color(0xFF212121), Color(0xFFE91E63))
}

data class DrawPoint(val x: Float, val y: Float)
data class DrawStroke(val points: List<DrawPoint>, val colorHex: String = "#212121", val width: Float = 5f)

data class MemoItem(
    val id: String = UUID.randomUUID().toString(),
    var text: String = "",
    var theme: NoteTheme = NoteTheme.POSTIT_YELLOW,
    val strokes: MutableList<DrawStroke> = mutableListOf(),
    var updatedAt: Long = System.currentTimeMillis()
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                PegboardScreen(
                    context = this,
                    onPinWidget = { memo -> requestPinWidget(memo) }
                )
            }
        }
    }

    // 안드로이드 홈 화면에 특정 메모 위젯 즉시 박제 (롱클릭 시 호출)
    private fun requestPinWidget(memo: MemoItem) {
        val appWidgetManager = AppWidgetManager.getInstance(this)
        val myProvider = ComponentName(this, FoldstItWidgetReceiver::class.java)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && appWidgetManager.isRequestPinAppWidgetSupported) {
            val successCallback = Intent(this, FoldstItWidgetReceiver::class.java).apply {
                action = "com.example.foldstit.PIN_WIDGET_ASSIGN"
                putExtra("TARGET_MEMO_ID", memo.id)
            }
            val successPendingIntent = PendingIntent.getBroadcast(
                this, memo.id.hashCode(), successCallback,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            )
            appWidgetManager.requestPinAppWidget(myProvider, null, successPendingIntent)
            Toast.makeText(this, "홈 화면에 위젯 추가 창이 열렸습니다.", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "홈 화면에서 위젯을 직접 추가해 주세요.", Toast.LENGTH_SHORT).show()
        }
    }
}

@Composable
fun PegboardScreen(context: Context, onPinWidget: (MemoItem) -> Unit) {
    val prefs = remember { context.getSharedPreferences("foldstit_board", Context.MODE_PRIVATE) }
    val memoList = remember { mutableStateListOf<MemoItem>() }
    var editingMemo by remember { mutableStateOf<MemoItem?>(null) }
    var isDockExpanded by remember { mutableStateOf(true) }

    // 다중 메모 로드
    fun loadMemos() {
        memoList.clear()
        val jsonStr = prefs.getString("memo_list_json", null)
        if (!jsonStr.isNullOrEmpty()) {
            try {
                val arr = JSONArray(jsonStr)
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    val themeName = obj.optString("theme", NoteTheme.POSTIT_YELLOW.name)
                    val memo = MemoItem(
                        id = obj.getString("id"),
                        text = obj.optString("text", ""),
                        theme = try { NoteTheme.valueOf(themeName) } catch (_: Exception) { NoteTheme.POSTIT_YELLOW },
                        updatedAt = obj.optLong("updatedAt", System.currentTimeMillis())
                    )
                    val strokesArr = obj.optJSONArray("strokes")
                    if (strokesArr != null) {
                        for (j in 0 until strokesArr.length()) {
                            val sObj = strokesArr.getJSONObject(j)
                            val ptsArr = sObj.getJSONArray("points")
                            val pts = mutableListOf<DrawPoint>()
                            for (k in 0 until ptsArr.length()) {
                                val p = ptsArr.getJSONObject(k)
                                pts.add(DrawPoint(p.getDouble("x").toFloat(), p.getDouble("y").toFloat()))
                            }
                            memo.strokes.add(DrawStroke(pts, sObj.optString("color", "#212121"), sObj.optDouble("width", 5.0).toFloat()))
                        }
                    }
                    memoList.add(memo)
                }
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    // 메모 전체 영구 저장 & 위젯용 비트맵 렌더링
    fun saveAllMemos() {
        try {
            val arr = JSONArray()
            memoList.forEach { memo ->
                val obj = JSONObject().apply {
                    put("id", memo.id)
                    put("text", memo.text)
                    put("theme", memo.theme.name)
                    put("updatedAt", memo.updatedAt)
                    val sArr = JSONArray()
                    memo.strokes.forEach { stroke ->
                        val strokeObj = JSONObject().apply {
                            put("color", stroke.colorHex)
                            put("width", stroke.width)
                            val pArr = JSONArray()
                            stroke.points.forEach { pt ->
                                pArr.put(JSONObject().apply {
                                    put("x", pt.x)
                                    put("y", pt.y)
                                })
                            }
                            put("points", pArr)
                        }
                        sArr.put(strokeObj)
                    }
                    put("strokes", sArr)
                }
                arr.put(obj)

                // 위젯용 드로잉 비트맵 생성 캐싱
                if (memo.strokes.isNotEmpty()) {
                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            val bmp = Bitmap.createBitmap(400, 400, Bitmap.Config.ARGB_8888)
                            val canvas = android.graphics.Canvas(bmp)
                            val paint = Paint().apply {
                                isAntiAlias = true
                                style = Paint.Style.STROKE
                                strokeCap = Paint.Cap.ROUND
                                strokeJoin = Paint.Join.ROUND
                            }
                            memo.strokes.forEach { s ->
                                paint.color = android.graphics.Color.parseColor(s.colorHex)
                                paint.strokeWidth = s.width
                                if (s.points.size > 1) {
                                    val path = android.graphics.Path()
                                    path.moveTo(s.points[0].x, s.points[0].y)
                                    for (pt in s.points) { path.lineTo(pt.x, pt.y) }
                                    canvas.drawPath(path, paint)
                                }
                            }
                            val outFile = File(context.filesDir, "drawing_${memo.id}.png")
                            FileOutputStream(outFile).use { out -> bmp.compress(Bitmap.CompressFormat.PNG, 100, out) }
                        } catch (_: Exception) {}
                    }
                }
            }
            prefs.edit().putString("memo_list_json", arr.toString()).apply()

            // 마지막 편집 메모를 기본 위젯용으로도 동기화
            if (memoList.isNotEmpty()) {
                val latest = memoList.maxByOrNull { it.updatedAt } ?: memoList.first()
                val legacyPrefs = context.getSharedPreferences("foldstit_data", Context.MODE_PRIVATE)
                legacyPrefs.edit()
                    .putString("text", latest.text)
                    .putString("theme", latest.theme.name)
                    .putBoolean("hasDrawing", latest.strokes.isNotEmpty())
                    .putString("latest_memo_id", latest.id)
                    .apply()
            }

            CoroutineScope(Dispatchers.IO).launch {
                FoldstItWidget().updateAll(context)
                FoldstItWidget.notifyUpdate(context)
            }
        } catch (e: Exception) { e.printStackTrace() }
    }

    LaunchedEffect(Unit) { loadMemos() }

    // 타공판 / 벽면 배경 화면 (Pegboard)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFD6D6D6)) // 타공판 베이스 그레이톤
    ) {
        // 미세한 타공판 패턴 캔버스
        Canvas(modifier = Modifier.fillMaxSize()) {
            val step = 32.dp.toPx()
            for (x in 0..(size.width / step).toInt()) {
                for (y in 0..(size.height / step).toInt()) {
                    drawCircle(
                        color = Color.Black.copy(alpha = 0.06f),
                        radius = 2.5.dp.toPx(),
                        center = Offset(x * step + 16.dp.toPx(), y * step + 16.dp.toPx())
                    )
                }
            }
        }

        // 벽면에 꽂힌 포스트잇 그리드 목록
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 150.dp),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 28.dp, bottom = 96.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(memoList, key = { it.id }) { memo ->
                // 단일 포스트잇 카드
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .shadow(elevation = 6.dp, shape = RoundedCornerShape(6.dp))
                        .clip(RoundedCornerShape(6.dp))
                        .background(memo.theme.bgColor)
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onTap = { editingMemo = memo },
                                onLongPress = {
                                    onPinWidget(memo)
                                }
                            )
                        }
                        .padding(12.dp)
                ) {
                    // 상단 중앙 압정 핀 (Pin)
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .offset(y = (-6).dp)
                            .size(16.dp)
                            .clip(CircleShape)
                            .background(memo.theme.pinColor)
                            .border(1.5.dp, Color.White.copy(alpha = 0.8f), CircleShape)
                    )

                    // 포스트잇 내부 메모 텍스트 미리보기
                    Text(
                        text = memo.text.ifEmpty { if (memo.strokes.isNotEmpty()) "[✍️ 드로잉 메모]" else "빈 메모" },
                        style = TextStyle(
                            fontSize = 13.sp,
                            color = memo.theme.textColor,
                            fontFamily = FontFamily.SansSerif,
                            lineHeight = 18.sp
                        ),
                        maxLines = 6,
                        modifier = Modifier.padding(top = 10.dp)
                    )
                }
            }
        }

        // 빈 상태 안내문
        if (memoList.isEmpty()) {
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(Icons.Default.PushPin, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(48.dp))
                Spacer(modifier = Modifier.height(8.dp))
                Text("타공판에 붙은 포스트잇이 없습니다.", color = Color.Gray, fontSize = 14.sp)
                Text("하단 독의 + Note를 눌러 새 메모를 붙여보세요!", color = Color.DarkGray, fontSize = 12.sp)
            }
        }

        // 우측 하단 플로팅 독 (Floating Dock)
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
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
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
                    // + Note (새 포스트잇 생성)
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.clickable {
                            val newMemo = MemoItem(text = "")
                            memoList.add(0, newMemo)
                            saveAllMemos()
                            editingMemo = newMemo
                        }
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "New", tint = Color(0xFF1976D2), modifier = Modifier.size(24.dp))
                        Text("New", fontSize = 10.sp, color = Color.DarkGray)
                    }

                    // Backup (전체 저장 동기화)
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.clickable {
                            saveAllMemos()
                            Toast.makeText(context, "타공판 상태가 위젯과 동기화되었습니다.", Toast.LENGTH_SHORT).show()
                        }
                    ) {
                        Icon(Icons.Default.CloudSync, contentDescription = "Sync", tint = Color.Black, modifier = Modifier.size(24.dp))
                        Text("Sync", fontSize = 10.sp, color = Color.DarkGray)
                    }
                }
            }
        }
    }

    // 포스트잇 상세 편집기 다이얼로그 (탭 시 열림)
    editingMemo?.let { targetMemo ->
        MemoEditorDialog(
            memo = targetMemo,
            onDismiss = { editingMemo = null },
            onSave = { updated ->
                val index = memoList.indexOfFirst { it.id == updated.id }
                if (index != -1) {
                    memoList[index] = updated.copy(updatedAt = System.currentTimeMillis())
                }
                saveAllMemos()
                editingMemo = null
            },
            onDelete = { deletedId ->
                memoList.removeAll { it.id == deletedId }
                saveAllMemos()
                editingMemo = null
            }
        )
    }
}

@Composable
fun MemoEditorDialog(
    memo: MemoItem,
    onDismiss: () -> Unit,
    onSave: (MemoItem) -> Unit,
    onDelete: (String) -> Unit
) {
    var memoText by remember { mutableStateOf(memo.text) }
    var currentTheme by remember { mutableStateOf(memo.theme) }
    var isPenMode by remember { mutableStateOf(false) }

    val strokes = remember { mutableStateListOf<DrawStroke>().apply { addAll(memo.strokes) } }
    val undoStack = remember { mutableStateListOf<DrawStroke>() }
    var currentStrokePoints by remember { mutableStateOf(listOf<DrawPoint>()) }

    val focusRequester = remember { FocusRequester() }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.5f))
                .padding(20.dp),
            contentAlignment = Alignment.Center
        ) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = currentTheme.bgColor),
                elevation = CardDefaults.cardElevation(defaultElevation = 12.dp),
                modifier = Modifier.fillMaxWidth().fillMaxHeight(0.85f)
            ) {
                Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                    // 상단 헤더 툴바
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 펜/키보드 전환 및 Undo
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            IconButton(onClick = { isPenMode = false }) {
                                Icon(Icons.Default.Keyboard, contentDescription = "Text", tint = if (!isPenMode) Color(0xFF1976D2) else currentTheme.textColor)
                            }
                            IconButton(onClick = { isPenMode = true }) {
                                Icon(Icons.Default.Create, contentDescription = "Pen", tint = if (isPenMode) Color(0xFF1976D2) else currentTheme.textColor)
                            }
                            IconButton(
                                onClick = {
                                    if (strokes.isNotEmpty()) {
                                        undoStack.add(strokes.removeAt(strokes.size - 1))
                                    }
                                },
                                enabled = strokes.isNotEmpty()
                            ) {
                                Icon(Icons.Default.Undo, contentDescription = "Undo", tint = if (strokes.isNotEmpty()) currentTheme.textColor else Color.Gray)
                            }
                            IconButton(onClick = { onDelete(memo.id) }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.Red.copy(alpha = 0.7f))
                            }
                        }

                        // 테마 선택 팔레트
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            NoteTheme.values().forEach { t ->
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clip(CircleShape)
                                        .background(t.bgColor)
                                        .border(
                                            width = if (currentTheme == t) 2.dp else 1.dp,
                                            color = if (currentTheme == t) t.pinColor else Color.Gray.copy(alpha = 0.4f),
                                            shape = CircleShape
                                        )
                                        .clickable { currentTheme = t }
                                )
                            }
                        }
                    }

                    // 포스트잇 메모 본문 입력 및 드로잉 영역
                    Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        BasicTextField(
                            value = memoText,
                            onValueChange = { memoText = it },
                            textStyle = TextStyle(fontSize = 18.sp, color = currentTheme.textColor, lineHeight = 26.sp),
                            modifier = Modifier
                                .fillMaxSize()
                                .focusRequester(focusRequester)
                                .zIndex(if (!isPenMode) 2f else 0f),
                            enabled = !isPenMode,
                            decorationBox = { innerTextField ->
                                if (memoText.isEmpty() && strokes.isEmpty()) {
                                    Text("메모를 입력하거나 상단 펜 모드로 손글씨를 써보세요...", color = currentTheme.textColor.copy(alpha = 0.4f), fontSize = 16.sp)
                                }
                                innerTextField()
                            }
                        )

                        // 손글씨 필기 캔버스
                        Canvas(
                            modifier = Modifier
                                .fillMaxSize()
                                .zIndex(if (isPenMode) 2f else 1f)
                                .pointerInput(isPenMode) {
                                    if (isPenMode) {
                                        detectDragGestures(
                                            onDragStart = { offset ->
                                                undoStack.clear()
                                                currentStrokePoints = listOf(DrawPoint(offset.x, offset.y))
                                            },
                                            onDrag = { change, _ ->
                                                change.consume()
                                                currentStrokePoints = currentStrokePoints + DrawPoint(change.position.x, change.position.y)
                                            },
                                            onDragEnd = {
                                                if (currentStrokePoints.isNotEmpty()) {
                                                    val strokeColor = if (currentTheme == NoteTheme.BLACK_PAPER) "#ECEFF1" else "#212121"
                                                    strokes.add(DrawStroke(currentStrokePoints, strokeColor, 5f))
                                                    currentStrokePoints = emptyList()
                                                }
                                            }
                                        )
                                    }
                                }
                        ) {
                            strokes.forEach { s ->
                                if (s.points.size > 1) {
                                    val path = Path().apply {
                                        moveTo(s.points.first().x, s.points.first().y)
                                        for (p in s.points) { lineTo(p.x, p.y) }
                                    }
                                    drawPath(
                                        path = path,
                                        color = Color(android.graphics.Color.parseColor(s.colorHex)),
                                        style = Stroke(width = s.width, cap = StrokeCap.Round, join = StrokeJoin.Round)
                                    )
                                }
                            }
                            if (currentStrokePoints.size > 1) {
                                val path = Path().apply {
                                    moveTo(currentStrokePoints.first().x, currentStrokePoints.first().y)
                                    for (p in currentStrokePoints) { lineTo(p.x, p.y) }
                                }
                                drawPath(
                                    path = path,
                                    color = if (currentTheme == NoteTheme.BLACK_PAPER) Color.White else Color(0xFF212121),
                                    style = Stroke(width = 5f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                                )
                            }
                        }
                    }

                    // 하단 저장 / 닫기 바
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = onDismiss) { Text("닫기", color = currentTheme.textColor) }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                memo.text = memoText
                                memo.theme = currentTheme
                                memo.strokes.clear()
                                memo.strokes.addAll(strokes)
                                onSave(memo)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1976D2))
                        ) {
                            Text("완료 및 저장", color = Color.White)
                        }
                    }
                }
            }
        }
    }
}
