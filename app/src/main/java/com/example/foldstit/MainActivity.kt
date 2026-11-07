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
import androidx.compose.ui.draw.rotate
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

// 4색 파스텔 + 줄눈 연습장
enum class NoteTheme(
    val title: String,
    val bgColor: Color,
    val textColor: Color,
    val pinColor: Color,
    val hasLines: Boolean = false
) {
    PASTEL_YELLOW("파스텔 노랑", Color(0xFFFFF9C4), Color(0xFF2C2C2C), Color(0xFF1E88E5)),
    PASTEL_BLUE("파스텔 파랑", Color(0xFFE1F5FE), Color(0xFF2C2C2C), Color(0xFFE53935)),
    PASTEL_PINK("파스텔 분홍", Color(0xFFFCE4EC), Color(0xFF2C2C2C), Color(0xFF43A047)),
    PASTEL_GREEN("파스텔 초록", Color(0xFFE8F5E9), Color(0xFF2C2C2C), Color(0xFFFFB300)),
    NOTEBOOK("연습장 줄노트", Color(0xFFFFFDF9), Color(0xFF1A237E), Color(0xFFD84315), true)
}

enum class MemoFont(val title: String, val family: FontFamily) {
    SANS("기본 고딕", FontFamily.SansSerif),
    SERIF("감성 명조", FontFamily.Serif),
    CURSIVE("손글씨 필기", FontFamily.Cursive),
    MONO("타자기체", FontFamily.Monospace)
}

data class NormPoint(val x: Float, val y: Float)
data class NormStroke(val points: List<NormPoint>, val colorHex: String = "#212121", val width: Float = 5f)

data class MemoItem(
    val id: String = UUID.randomUUID().toString(),
    var text: String = "",
    var theme: NoteTheme = NoteTheme.PASTEL_YELLOW,
    var font: MemoFont = MemoFont.SANS,
    var fontSize: Int = 16,
    var penWidth: Float = 5f,
    val strokes: MutableList<NormStroke> = mutableListOf(),
    var rotationAngle: Float = 0f,
    var updatedAt: Long = System.currentTimeMillis()
)

class MainActivity : ComponentActivity() {
    private var initialEditMemoId by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        initialEditMemoId = intent?.getStringExtra("EDIT_MEMO_ID")

        setContent {
            MaterialTheme {
                PegboardScreen(
                    context = this,
                    openMemoId = initialEditMemoId,
                    onClearOpenMemoId = { initialEditMemoId = null },
                    onPinWidget = { memo -> requestPinWidget(memo) }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        initialEditMemoId = intent?.getStringExtra("EDIT_MEMO_ID")
    }

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
            Toast.makeText(this, "홈 화면에 포스트잇 위젯 등록 창이 열렸습니다.", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "홈 화면에서 위젯을 길게 눌러 직접 추가해 주세요.", Toast.LENGTH_SHORT).show()
        }
    }
}

@Composable
fun PegboardScreen(
    context: Context,
    openMemoId: String?,
    onClearOpenMemoId: () -> Unit,
    onPinWidget: (MemoItem) -> Unit
) {
    val prefs = remember { context.getSharedPreferences("foldstit_board", Context.MODE_PRIVATE) }
    val memoList = remember { mutableStateListOf<MemoItem>() }
    var editingMemo by remember { mutableStateOf<MemoItem?>(null) }
    var isDockExpanded by remember { mutableStateOf(true) }

    // 다중 선택 모드 상태
    var isSelectionMode by remember { mutableStateOf(false) }
    val selectedMemoIds = remember { mutableStateListOf<String>() }

    fun loadMemos() {
        memoList.clear()
        val jsonStr = prefs.getString("memo_list_json", null)
        if (!jsonStr.isNullOrEmpty()) {
            try {
                val arr = JSONArray(jsonStr)
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    val themeName = obj.optString("theme", NoteTheme.PASTEL_YELLOW.name)
                    val fontName = obj.optString("font", MemoFont.SANS.name)
                    val memo = MemoItem(
                        id = obj.getString("id"),
                        text = obj.optString("text", ""),
                        theme = try { NoteTheme.valueOf(themeName) } catch (_: Exception) { NoteTheme.PASTEL_YELLOW },
                        font = try { MemoFont.valueOf(fontName) } catch (_: Exception) { MemoFont.SANS },
                        fontSize = obj.optInt("fontSize", 16),
                        penWidth = obj.optDouble("penWidth", 5.0).toFloat(),
                        rotationAngle = obj.optDouble("rotationAngle", 0.0).toFloat(),
                        updatedAt = obj.optLong("updatedAt", System.currentTimeMillis())
                    )
                    val strokesArr = obj.optJSONArray("strokes")
                    if (strokesArr != null) {
                        for (j in 0 until strokesArr.length()) {
                            val sObj = strokesArr.getJSONObject(j)
                            val ptsArr = sObj.getJSONArray("points")
                            val pts = mutableListOf<NormPoint>()
                            for (k in 0 until ptsArr.length()) {
                                val p = ptsArr.getJSONObject(k)
                                pts.add(NormPoint(p.getDouble("x").toFloat(), p.getDouble("y").toFloat()))
                            }
                            memo.strokes.add(NormStroke(pts, sObj.optString("color", "#212121"), sObj.optDouble("width", 5.0).toFloat()))
                        }
                    }
                    memoList.add(memo)
                }
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    fun saveAllMemos() {
        try {
            val arr = JSONArray()
            memoList.forEach { memo ->
                val obj = JSONObject().apply {
                    put("id", memo.id)
                    put("text", memo.text)
                    put("theme", memo.theme.name)
                    put("font", memo.font.name)
                    put("fontSize", memo.fontSize)
                    put("penWidth", memo.penWidth)
                    put("rotationAngle", memo.rotationAngle)
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

                // 위젯용 고화질 비트맵 렌더링 캐싱
                if (memo.strokes.isNotEmpty()) {
                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            val bmp = Bitmap.createBitmap(500, 500, Bitmap.Config.ARGB_8888)
                            val canvas = android.graphics.Canvas(bmp)
                            val paint = Paint().apply {
                                isAntiAlias = true
                                style = Paint.Style.STROKE
                                strokeCap = Paint.Cap.ROUND
                                strokeJoin = Paint.Join.ROUND
                            }
                            memo.strokes.forEach { s ->
                                paint.color = android.graphics.Color.parseColor(s.colorHex)
                                paint.strokeWidth = s.width * 1.5f
                                if (s.points.size > 1) {
                                    val path = android.graphics.Path()
                                    path.moveTo(s.points[0].x * 500f, s.points[0].y * 500f)
                                    for (pt in s.points) { path.lineTo(pt.x * 500f, pt.y * 500f) }
                                    canvas.drawPath(path, paint)
                                }
                            }
                            val outFile = File(context.filesDir, "drawing_${memo.id}.png")
                            FileOutputStream(outFile).use { out -> bmp.compress(Bitmap.CompressFormat.PNG, 100, out) }
                        } catch (_: Exception) {}
                    }
                } else {
                    val outFile = File(context.filesDir, "drawing_${memo.id}.png")
                    if (outFile.exists()) outFile.delete()
                }
            }
            prefs.edit().putString("memo_list_json", arr.toString()).apply()

            if (memoList.isNotEmpty()) {
                val latest = memoList.maxByOrNull { it.updatedAt } ?: memoList.first()
                val legacyPrefs = context.getSharedPreferences("foldstit_data", Context.MODE_PRIVATE)
                legacyPrefs.edit()
                    .putString("text", latest.text)
                    .putString("theme", latest.theme.name)
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

    LaunchedEffect(openMemoId, memoList.size) {
        if (!openMemoId.isNullOrEmpty()) {
            val target = memoList.find { it.id == openMemoId }
            if (target != null) {
                editingMemo = target
                onClearOpenMemoId()
            }
        }
    }

    // 타공판 화면
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFD4D4D4))
    ) {
        // 미세 타공 패턴
        Canvas(modifier = Modifier.fillMaxSize()) {
            val step = 28.dp.toPx()
            for (x in 0..(size.width / step).toInt()) {
                for (y in 0..(size.height / step).toInt()) {
                    drawCircle(
                        color = Color.Black.copy(alpha = 0.07f),
                        radius = 2.2.dp.toPx(),
                        center = Offset(x * step + 14.dp.toPx(), y * step + 14.dp.toPx())
                    )
                }
            }
        }

        // 포스트잇 목록 그리드
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 160.dp),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 36.dp, bottom = 100.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(memoList, key = { it.id }) { memo ->
                val isSelected = selectedMemoIds.contains(memo.id)

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .rotate(memo.rotationAngle)
                        .shadow(elevation = if (isSelected) 10.dp else 5.dp, shape = RoundedCornerShape(6.dp))
                        .clip(RoundedCornerShape(6.dp))
                        .background(memo.theme.bgColor)
                        .border(
                            width = if (isSelected) 3.dp else 0.dp,
                            color = if (isSelected) Color(0xFF1976D2) else Color.Transparent,
                            shape = RoundedCornerShape(6.dp)
                        )
                        .pointerInput(isSelectionMode) {
                            detectTapGestures(
                                onTap = {
                                    if (isSelectionMode) {
                                        if (isSelected) selectedMemoIds.remove(memo.id) else selectedMemoIds.add(memo.id)
                                        if (selectedMemoIds.isEmpty()) isSelectionMode = false
                                    } else {
                                        editingMemo = memo
                                    }
                                },
                                onLongPress = {
                                    if (!isSelectionMode) {
                                        isSelectionMode = true
                                        selectedMemoIds.add(memo.id)
                                    }
                                }
                            )
                        }
                ) {
                    // 연습장 모드 줄눈금
                    if (memo.theme.hasLines) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val lineGap = 22.dp.toPx()
                            var y = lineGap + 20.dp.toPx()
                            while (y < size.height) {
                                drawLine(
                                    color = Color(0xFF8D6E63).copy(alpha = 0.25f),
                                    start = Offset(0f, y),
                                    end = Offset(size.width, y),
                                    strokeWidth = 1.2.dp.toPx()
                                )
                                y += lineGap
                            }
                        }
                    }

                    // 상단 압정 핀
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .offset(y = (-4).dp)
                            .size(16.dp)
                            .clip(CircleShape)
                            .background(memo.theme.pinColor)
                            .border(1.5.dp, Color.White.copy(alpha = 0.8f), CircleShape)
                    )

                    // 다중 선택 모드 체크박스 표시
                    if (isSelectionMode) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(6.dp)
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(if (isSelected) Color(0xFF1976D2) else Color.White.copy(alpha = 0.7f))
                                .border(1.5.dp, Color(0xFF1976D2), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isSelected) {
                                Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                            }
                        }
                    }

                    // 텍스트 & 손글씨 동시 렌더링
                    Box(modifier = Modifier.fillMaxSize().padding(12.dp)) {
                        Text(
                            text = memo.text,
                            style = TextStyle(
                                fontSize = 13.sp,
                                color = memo.theme.textColor,
                                fontFamily = memo.font.family,
                                lineHeight = 18.sp
                            ),
                            maxLines = 6,
                            modifier = Modifier.padding(top = 8.dp)
                        )

                        if (memo.strokes.isNotEmpty()) {
                            Canvas(modifier = Modifier.fillMaxSize().padding(top = 8.dp)) {
                                memo.strokes.forEach { s ->
                                    if (s.points.size > 1) {
                                        val path = Path().apply {
                                            moveTo(s.points[0].x * size.width, s.points[0].y * size.height)
                                            for (p in s.points) { lineTo(p.x * size.width, p.y * size.height) }
                                        }
                                        drawPath(
                                            path = path,
                                            color = Color(android.graphics.Color.parseColor(s.colorHex)),
                                            style = Stroke(width = s.width, cap = StrokeCap.Round, join = StrokeJoin.Round)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 다중 선택 플로팅 액션 바
        if (isSelectionMode) {
            Card(
                shape = RoundedCornerShape(32.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF263238)),
                elevation = CardDefaults.cardElevation(defaultElevation = 12.dp),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 20.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text("${selectedMemoIds.size}개 선택됨", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)

                    // 선택 위젯 등록 (첫 번째 선택된 메모 기준 핀 요청)
                    TextButton(onClick = {
                        val firstSelected = memoList.find { it.id == selectedMemoIds.firstOrNull() }
                        if (firstSelected != null) {
                            onPinWidget(firstSelected)
                        }
                    }) {
                        Icon(Icons.Default.PushPin, contentDescription = "Pin", tint = Color(0xFF81D4FA), modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("위젯 등록", color = Color(0xFF81D4FA), fontSize = 12.sp)
                    }

                    // 일괄 삭제
                    TextButton(onClick = {
                        memoList.removeAll { selectedMemoIds.contains(it.id) }
                        selectedMemoIds.clear()
                        isSelectionMode = false
                        saveAllMemos()
                        Toast.makeText(context, "선택된 메모가 삭제되었습니다.", Toast.LENGTH_SHORT).show()
                    }) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color(0xFFFF8A80), modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("일괄 삭제", color = Color(0xFFFF8A80), fontSize = 12.sp)
                    }

                    // 선택 취소
                    IconButton(onClick = {
                        selectedMemoIds.clear()
                        isSelectionMode = false
                    }) {
                        Icon(Icons.Default.Close, contentDescription = "Cancel", tint = Color.LightGray)
                    }
                }
            }
        } else {
            // 일반 모드: 우측 하단 플로팅 독
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
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.clickable {
                                val randomAngle = (-3..3).random().toFloat()
                                val newMemo = MemoItem(text = "", rotationAngle = randomAngle)
                                memoList.add(0, newMemo)
                                saveAllMemos()
                                editingMemo = newMemo
                            }
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "New", tint = Color(0xFF1976D2), modifier = Modifier.size(24.dp))
                            Text("New", fontSize = 10.sp, color = Color.DarkGray)
                        }

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.clickable {
                                saveAllMemos()
                                Toast.makeText(context, "타공판과 위젯이 동기화되었습니다.", Toast.LENGTH_SHORT).show()
                            }
                        ) {
                            Icon(Icons.Default.CloudSync, contentDescription = "Sync", tint = Color.Black, modifier = Modifier.size(24.dp))
                            Text("Sync", fontSize = 10.sp, color = Color.DarkGray)
                        }
                    }
                }
            }
        }
    }

    // 포스트잇 상세 편집기
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
    var currentFont by remember { mutableStateOf(memo.font) }
    var currentPenWidth by remember { mutableStateOf(memo.penWidth) }
    var memoFontSize by remember { mutableStateOf(memo.fontSize) }
    var isPenMode by remember { mutableStateOf(false) }
    var showMemoSettings by remember { mutableStateOf(false) }

    val strokes = remember { mutableStateListOf<NormStroke>().apply { addAll(memo.strokes) } }
    val undoStack = remember { mutableStateListOf<NormStroke>() }
    var currentStrokePoints by remember { mutableStateOf(listOf<NormPoint>()) }

    var canvasWidth by remember { mutableStateOf(1f) }
    var canvasHeight by remember { mutableStateOf(1f) }

    val focusRequester = remember { FocusRequester() }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.55f))
                .padding(20.dp),
            contentAlignment = Alignment.Center
        ) {
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = currentTheme.bgColor),
                elevation = CardDefaults.cardElevation(defaultElevation = 14.dp),
                modifier = Modifier.fillMaxWidth().fillMaxHeight(0.88f)
            ) {
                Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                    // 상단 헤더
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
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

                        // 메모 내 상세 설정 토글 (테마, 폰트, 펜 굵기)
                        IconButton(onClick = { showMemoSettings = !showMemoSettings }) {
                            Icon(Icons.Default.Tune, contentDescription = "Memo Settings", tint = currentTheme.textColor)
                        }
                    }

                    // 메모 설정 패널
                    AnimatedVisibility(visible = showMemoSettings) {
                        Card(
                            shape = RoundedCornerShape(8.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.9f)),
                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                        ) {
                            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                // 1. 테마
                                Text("메모지 테마", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    NoteTheme.values().forEach { t ->
                                        Box(
                                            modifier = Modifier
                                                .size(26.dp)
                                                .clip(CircleShape)
                                                .background(t.bgColor)
                                                .border(
                                                    width = if (currentTheme == t) 2.5.dp else 1.dp,
                                                    color = if (currentTheme == t) t.pinColor else Color.Gray.copy(alpha = 0.4f),
                                                    shape = CircleShape
                                                )
                                                .clickable { currentTheme = t }
                                        )
                                    }
                                }

                                // 2. 폰트 선택
                                Text("글꼴 선택", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    MemoFont.values().forEach { f ->
                                        OutlinedButton(
                                            onClick = { currentFont = f },
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                            colors = ButtonDefaults.outlinedButtonColors(
                                                containerColor = if (currentFont == f) Color(0xFFE0E0E0) else Color.Transparent
                                            )
                                        ) {
                                            Text(f.title, fontSize = 10.sp, color = Color.Black, fontFamily = f.family)
                                        }
                                    }
                                }

                                // 3. 펜 굵기 선택
                                Text("드로잉 펜 굵기", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    listOf(2.5f to "얇게", 5.0f to "보통", 9.0f to "두껍게").forEach { (w, label) ->
                                        OutlinedButton(
                                            onClick = { currentPenWidth = w },
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                            colors = ButtonDefaults.outlinedButtonColors(
                                                containerColor = if (currentPenWidth == w) Color(0xFFE0E0E0) else Color.Transparent
                                            )
                                        ) {
                                            Text(label, fontSize = 10.sp, color = Color.Black)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // 본문 캔버스 영역
                    Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        if (currentTheme.hasLines) {
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                val lineGap = 26.dp.toPx()
                                var y = lineGap + 10.dp.toPx()
                                while (y < size.height) {
                                    drawLine(
                                        color = Color(0xFF8D6E63).copy(alpha = 0.3f),
                                        start = Offset(0f, y),
                                        end = Offset(size.width, y),
                                        strokeWidth = 1.2.dp.toPx()
                                    )
                                    y += lineGap
                                }
                            }
                        }

                        BasicTextField(
                            value = memoText,
                            onValueChange = { memoText = it },
                            textStyle = TextStyle(
                                fontSize = memoFontSize.sp,
                                color = currentTheme.textColor,
                                fontFamily = currentFont.family,
                                lineHeight = (memoFontSize + 8).sp
                            ),
                            modifier = Modifier
                                .fillMaxSize()
                                .focusRequester(focusRequester)
                                .zIndex(if (!isPenMode) 2f else 0f),
                            enabled = !isPenMode,
                            decorationBox = { innerTextField ->
                                if (memoText.isEmpty() && strokes.isEmpty()) {
                                    Text("메모를 입력하거나 상단 펜 모드로 손글씨를 써보세요...", color = currentTheme.textColor.copy(alpha = 0.4f), fontSize = memoFontSize.sp, fontFamily = currentFont.family)
                                }
                                innerTextField()
                            }
                        )

                        Canvas(
                            modifier = Modifier
                                .fillMaxSize()
                                .zIndex(if (isPenMode) 2f else 1f)
                                .pointerInput(isPenMode, currentPenWidth) {
                                    if (isPenMode) {
                                        detectDragGestures(
                                            onDragStart = { offset ->
                                                undoStack.clear()
                                                canvasWidth = size.width.toFloat().coerceAtLeast(1f)
                                                canvasHeight = size.height.toFloat().coerceAtLeast(1f)
                                                currentStrokePoints = listOf(NormPoint(offset.x / canvasWidth, offset.y / canvasHeight))
                                            },
                                            onDrag = { change, _ ->
                                                change.consume()
                                                currentStrokePoints = currentStrokePoints + NormPoint(change.position.x / canvasWidth, change.position.y / canvasHeight)
                                            },
                                            onDragEnd = {
                                                if (currentStrokePoints.isNotEmpty()) {
                                                    strokes.add(NormStroke(currentStrokePoints, "#212121", currentPenWidth))
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
                                        moveTo(s.points[0].x * size.width, s.points[0].y * size.height)
                                        for (p in s.points) { lineTo(p.x * size.width, p.y * size.height) }
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
                                    moveTo(currentStrokePoints[0].x * size.width, currentStrokePoints[0].y * size.height)
                                    for (p in currentStrokePoints) { lineTo(p.x * size.width, p.y * size.height) }
                                }
                                drawPath(
                                    path = path,
                                    color = Color(0xFF212121),
                                    style = Stroke(width = currentPenWidth, cap = StrokeCap.Round, join = StrokeJoin.Round)
                                )
                            }
                        }
                    }

                    // 하단 바
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
                                memo.font = currentFont
                                memo.penWidth = currentPenWidth
                                memo.fontSize = memoFontSize
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
