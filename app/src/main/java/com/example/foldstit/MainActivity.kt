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

// 테마 규격: 4색 파스텔 포스트잇 + 줄눈 연습장
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
    NOTEBOOK("연습장 노트", Color(0xFFFFFDF9), Color(0xFF1A237E), Color(0xFFD84315), true)
}

// 0.0 ~ 1.0 비율 정규화 포인트 (어떤 크기에서도 완벽 스케일링)
data class NormPoint(val x: Float, val y: Float)
data class NormStroke(val points: List<NormPoint>, val colorHex: String = "#212121", val width: Float = 4f)

data class MemoItem(
    val id: String = UUID.randomUUID().toString(),
    var text: String = "",
    var theme: NoteTheme = NoteTheme.PASTEL_YELLOW,
    var fontSize: Int = 16,
    val strokes: MutableList<NormStroke> = mutableListOf(),
    var rotationAngle: Float = 0f, // 삐딱하게 붙인 각도 (-3f ~ 3f)
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
    var showAppSettingsDialog by remember { mutableStateOf(false) }

    fun loadMemos() {
        memoList.clear()
        val jsonStr = prefs.getString("memo_list_json", null)
        if (!jsonStr.isNullOrEmpty()) {
            try {
                val arr = JSONArray(jsonStr)
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    val themeName = obj.optString("theme", NoteTheme.PASTEL_YELLOW.name)
                    val memo = MemoItem(
                        id = obj.getString("id"),
                        text = obj.optString("text", ""),
                        theme = try { NoteTheme.valueOf(themeName) } catch (_: Exception) { NoteTheme.PASTEL_YELLOW },
                        fontSize = obj.optInt("fontSize", 16),
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
                            memo.strokes.add(NormStroke(pts, sObj.optString("color", "#212121"), sObj.optDouble("width", 4.0).toFloat()))
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
                    put("fontSize", memo.fontSize)
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

            // 레거시 단일 위젯 지원용 동기화
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

    // 딥링크: 위젯에서 특정 메모 탭 시 즉시 에디터 오픈
    LaunchedEffect(openMemoId, memoList.size) {
        if (!openMemoId.isNullOrEmpty()) {
            val target = memoList.find { it.id == openMemoId }
            if (target != null) {
                editingMemo = target
                onClearOpenMemoId()
            }
        }
    }

    // 타공판(벽면) 뷰
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFD4D4D4))
    ) {
        // 미세 타공판 펀칭 패턴
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

        // 벽면에 삐딱하게 붙은 포스트잇 그리드
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 160.dp),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 32.dp, bottom = 96.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(memoList, key = { it.id }) { memo ->
                // 단일 포스트잇 카드 (미세 회전각 적용)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .rotate(memo.rotationAngle)
                        .shadow(elevation = 5.dp, shape = RoundedCornerShape(4.dp))
                        .clip(RoundedCornerShape(4.dp))
                        .background(memo.theme.bgColor)
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onTap = { editingMemo = memo },
                                onLongPress = { onPinWidget(memo) }
                            )
                        }
                ) {
                    // 연습장 모드일 때 갈색 줄눈금 렌더링
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

                    // 상단 압정 핀 그래픽
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .offset(y = (-4).dp)
                            .size(16.dp)
                            .clip(CircleShape)
                            .background(memo.theme.pinColor)
                            .border(1.5.dp, Color.White.copy(alpha = 0.8f), CircleShape)
                    )

                    // 본문 영역 (텍스트 + 손글씨 동시 렌더링)
                    Box(modifier = Modifier.fillMaxSize().padding(12.dp)) {
                        Text(
                            text = memo.text,
                            style = TextStyle(
                                fontSize = 13.sp,
                                color = memo.theme.textColor,
                                fontFamily = FontFamily.SansSerif,
                                lineHeight = 18.sp
                            ),
                            maxLines = 6,
                            modifier = Modifier.padding(top = 8.dp)
                        )

                        // [핵심] 타공판 카드 손글씨 벡터 오버레이
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

        if (memoList.isEmpty()) {
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(Icons.Default.PushPin, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(48.dp))
                Spacer(modifier = Modifier.height(8.dp))
                Text("타공판에 부착된 포스트잇이 없습니다.", color = Color.Gray, fontSize = 14.sp)
                Text("하단 독의 New 버튼을 눌러 메모를 붙여보세요!", color = Color.DarkGray, fontSize = 12.sp)
            }
        }

        // 우측 하단 캡슐형 플로팅 독 (앱 전체 제어)
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
                    // 새 메모 생성 (랜덤 회전각 부여하여 삐딱한 손맛 재현)
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

                    // 동기화
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

                    // 앱 전체 설정
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.clickable { showAppSettingsDialog = true }
                    ) {
                        Icon(Icons.Default.Settings, contentDescription = "App Settings", tint = Color.Black, modifier = Modifier.size(24.dp))
                        Text("App Set", fontSize = 10.sp, color = Color.DarkGray)
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

    // 앱 전체 환경설정 다이얼로그
    if (showAppSettingsDialog) {
        AlertDialog(
            onDismissRequest = { showAppSettingsDialog = false },
            title = { Text("Foldst it 앱 설정", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("• 타공판 보드 버전: v2.5", fontSize = 14.sp)
                    Text("• 포스트잇을 1초간 길게 누르면 홈 화면 위젯으로 즉시 등록됩니다.", fontSize = 13.sp, color = Color.DarkGray)
                    Text("• 등록된 위젯을 탭하면 이 메모 편집창으로 바로 이동합니다.", fontSize = 13.sp, color = Color.DarkGray)
                }
            },
            confirmButton = { TextButton(onClick = { showAppSettingsDialog = false }) { Text("닫기") } }
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
                modifier = Modifier.fillMaxWidth().fillMaxHeight(0.85f)
            ) {
                Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                    // 상단 헤더: 모드 전환 + 메모 자체 설정 버튼
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

                        // 메모 자체 설정 토글 버튼 (폰트 크기, 테마)
                        IconButton(onClick = { showMemoSettings = !showMemoSettings }) {
                            Icon(Icons.Default.Tune, contentDescription = "Memo Settings", tint = currentTheme.textColor)
                        }
                    }

                    // 메모 자체 설정 패널 (접이식)
                    AnimatedVisibility(visible = showMemoSettings) {
                        Card(
                            shape = RoundedCornerShape(8.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.85f)),
                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                        ) {
                            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("메모지 테마 선택", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    NoteTheme.values().forEach { t ->
                                        Box(
                                            modifier = Modifier
                                                .size(28.dp)
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

                                Text("글자 크기", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    listOf(14 to "작게", 18 to "보통", 22 to "크게").forEach { (sz, label) ->
                                        OutlinedButton(
                                            onClick = { memoFontSize = sz },
                                            colors = ButtonDefaults.outlinedButtonColors(
                                                containerColor = if (memoFontSize == sz) Color(0xFFE0E0E0) else Color.Transparent
                                            )
                                        ) {
                                            Text(label, fontSize = 11.sp, color = Color.Black)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // 본문 입력 및 캔버스
                    Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        // 연습장 줄눈금
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

                        // 텍스트 필드
                        BasicTextField(
                            value = memoText,
                            onValueChange = { memoText = it },
                            textStyle = TextStyle(fontSize = memoFontSize.sp, color = currentTheme.textColor, lineHeight = (memoFontSize + 8).sp),
                            modifier = Modifier
                                .fillMaxSize()
                                .focusRequester(focusRequester)
                                .zIndex(if (!isPenMode) 2f else 0f),
                            enabled = !isPenMode,
                            decorationBox = { innerTextField ->
                                if (memoText.isEmpty() && strokes.isEmpty()) {
                                    Text("여기를 터치해 글을 쓰거나 상단 펜 모드로 손글씨를 작성하세요...", color = currentTheme.textColor.copy(alpha = 0.4f), fontSize = memoFontSize.sp)
                                }
                                innerTextField()
                            }
                        )

                        // 손글씨 레이어 (비율 정규화)
                        Canvas(
                            modifier = Modifier
                                .fillMaxSize()
                                .zIndex(if (isPenMode) 2f else 1f)
                                .pointerInput(isPenMode) {
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
                                                    strokes.add(NormStroke(currentStrokePoints, "#212121", 4f))
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
                                    style = Stroke(width = 4f, cap = StrokeCap.Round, join = StrokeJoin.Round)
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
