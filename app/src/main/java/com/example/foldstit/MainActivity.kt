
@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.example.foldstit

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.Layout
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.StaticLayout
import android.text.TextPaint
import android.text.style.ForegroundColorSpan
import android.text.style.StrikethroughSpan
import android.text.style.StyleSpan
import android.text.style.TypefaceSpan
import android.text.style.UnderlineSpan
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.keyframes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextGeometricTransform
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.core.view.WindowCompat
import androidx.glance.appwidget.updateAll
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.util.UUID
import kotlin.math.hypot

enum class NoteTheme(
    val title: String,
    val bgColor: Color,
    val textColor: Color,
    val pinColor: Color,
    val hasLines: Boolean = false
) {
    PASTEL_YELLOW("노랑", Color(0xFFFFF9C4), Color(0xFF2C2C2C), Color(0xFF1E88E5)),
    PASTEL_BLUE("파랑", Color(0xFFE1F5FE), Color(0xFF2C2C2C), Color(0xFFE53935)),
    PASTEL_PINK("분홍", Color(0xFFFCE4EC), Color(0xFF2C2C2C), Color(0xFF43A047)),
    PASTEL_GREEN("초록", Color(0xFFE8F5E9), Color(0xFF2C2C2C), Color(0xFFFFB300)),
    NOTEBOOK("연습장", Color(0xFFFFFFFF), Color(0xFF1A237E), Color(0xFFE53935), true)
}

enum class MemoFont(val title: String, val fileName: String) {
    DEFAULT("내 폰 기본체", ""),
    NANUM_PEN("나눔손글씨 펜", "NanumPen.ttf"),
    GARAM("가람연꽃", "나눔손글씨 가람연꽃.ttf"),
    GALMAET("갈맷글", "나눔손글씨 갈맷글.ttf"),
    GODING("고딕 아니고 고딩", "나눔손글씨 고딕 아니고 고딩.ttf"),
    GYURI("규리의 일기", "나눔손글씨 규리의 일기.ttf")
}

object LocalFontLoader {
    private val typefaceCache = mutableMapOf<String, Typeface>()

    fun getTypeface(context: Context, font: MemoFont): Typeface {
        if (font.fileName.isEmpty()) return Typeface.DEFAULT
        if (typefaceCache.containsKey(font.name)) return typefaceCache[font.name]!!

        return try {
            val tf = Typeface.createFromAsset(context.assets, "fonts/${font.fileName}")
            typefaceCache[font.name] = tf
            tf
        } catch (e: Exception) {
            e.printStackTrace()
            Typeface.DEFAULT
        }
    }

    fun getFontFamily(context: Context, font: MemoFont): FontFamily {
        val tf = getTypeface(context, font)
        return FontFamily(tf)
    }
}

object ImageLoaderUtil {
    fun loadResizedBitmap(context: Context, uri: Uri, reqWidth: Int, reqHeight: Int): Bitmap? {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        var inputStream = context.contentResolver.openInputStream(uri)
        BitmapFactory.decodeStream(inputStream, null, options)
        inputStream?.close()

        options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight)
        options.inJustDecodeBounds = false
        options.inPreferredConfig = Bitmap.Config.RGB_565

        inputStream = context.contentResolver.openInputStream(uri)
        val bitmap = BitmapFactory.decodeStream(inputStream, null, options)
        inputStream?.close()
        return bitmap
    }

    private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        val (height: Int, width: Int) = options.run { outHeight to outWidth }
        var inSampleSize = 1
        if (height > reqHeight || width > reqWidth) {
            val halfHeight = height / 2
            val halfWidth = width / 2
            while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }
}

data class TextSpanItem(
    val start: Int,
    val end: Int,
    val fontName: String = MemoFont.DEFAULT.name,
    val colorHex: String = "#2C2C2C",
    val fontSize: Int = 16,
    val isBold: Boolean = false,
    val isItalic: Boolean = false,
    val isUnderline: Boolean = false,
    val isStrikethrough: Boolean = false
)

data class NormPoint(val x: Float, val y: Float)
data class NormStroke(val points: List<NormPoint>, val colorHex: String = "#212121", val width: Float = 5f)

data class PhotoStickerItem(
    val id: String = UUID.randomUUID().toString(),
    val fileName: String,
    var offsetX: Float = 40f,
    var offsetY: Float = 60f,
    var scale: Float = 1.0f,
    var rotation: Float = 0.0f
)

data class MemoItem(
    val id: String = UUID.randomUUID().toString(),
    var text: String = "",
    var theme: NoteTheme = NoteTheme.PASTEL_YELLOW,
    var font: MemoFont = MemoFont.DEFAULT,
    var fontSize: Int = 16,
    var penWidth: Float = 5f,
    var isBold: Boolean = false,
    var isItalic: Boolean = false,
    var isUnderline: Boolean = false,
    var isStrikethrough: Boolean = false,
    val spans: MutableList<TextSpanItem> = mutableListOf(),
    val strokes: MutableList<NormStroke> = mutableListOf(),
    val photos: MutableList<PhotoStickerItem> = mutableListOf(),
    var rotationAngle: Float = 0f,
    var updatedAt: Long = System.currentTimeMillis()
)

class MainActivity : ComponentActivity() {
    private var initialEditMemoId by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT

        initialEditMemoId = intent?.getStringExtra("EDIT_MEMO_ID")

        setContent {
            MaterialTheme {
                PegboardScreen(
                    context = this,
                    openMemoId = initialEditMemoId,
                    onClearOpenMemoId = { initialEditMemoId = null },
                    onPinSingleWidget = { memo -> requestPinWidget(memo) }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        initialEditMemoId = intent?.getStringExtra("EDIT_MEMO_ID")
    }

    fun requestPinWidget(memo: MemoItem) {
        val appWidgetManager = AppWidgetManager.getInstance(this)
        val myProvider = ComponentName(this, FoldstItWidgetReceiver::class.java)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && appWidgetManager.isRequestPinAppWidgetSupported) {
            val pendingMemoPrefs = getSharedPreferences("pending_pin_prefs", Context.MODE_PRIVATE)
            pendingMemoPrefs.edit().putString("pending_memo_id", memo.id).apply()

            val successCallback = Intent(this, FoldstItWidgetReceiver::class.java).apply {
                action = "com.example.foldstit.PIN_WIDGET_ASSIGN"
                putExtra("TARGET_MEMO_ID", memo.id)
            }
            val successPendingIntent = PendingIntent.getBroadcast(
                this, memo.id.hashCode() + (System.currentTimeMillis() % 1000).toInt(),
                successCallback,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            )
            appWidgetManager.requestPinAppWidget(myProvider, null, successPendingIntent)
        } else {
            Toast.makeText(this, "홈 화면에서 위젯을 길게 눌러 직접 추가해 주세요.", Toast.LENGTH_SHORT).show()
        }
    }
}

// 텍스트 캐시 최적화 함수 (rememberCachedStyledString 이름 통일)
@Composable
fun rememberCachedStyledString(
    context: Context,
    text: String,
    spans: List<TextSpanItem>,
    defaultFont: MemoFont,
    defaultColor: Color,
    defaultSize: Int
): AnnotatedString {
    val defaultFamily = LocalFontLoader.getFontFamily(context, defaultFont)
    val fontMap = remember(context) {
        MemoFont.values().associateWith { LocalFontLoader.getFontFamily(context, it) }
    }

    return remember(text, spans, defaultFont, defaultColor, defaultSize, defaultFamily) {
        buildAnnotatedString {
            append(text)
            if (spans.isEmpty()) {
                addStyle(
                    SpanStyle(
                        fontFamily = defaultFamily,
                        color = defaultColor,
                        fontSize = defaultSize.sp
                    ),
                    0, text.length
                )
            } else {
                spans.forEach { sp ->
                    val s = sp.start.coerceIn(0, text.length)
                    val e = sp.end.coerceIn(0, text.length)
                    if (s < e) {
                        val fontEnum = try { MemoFont.valueOf(sp.fontName) } catch (_: Exception) { defaultFont }
                        val customFamily = fontMap[fontEnum] ?: defaultFamily
                        val textDecor = TextDecoration.combine(
                            listOfNotNull(
                                if (sp.isUnderline) TextDecoration.Underline else null,
                                if (sp.isStrikethrough) TextDecoration.LineThrough else null
                            )
                        )
                        addStyle(
                            SpanStyle(
                                fontFamily = customFamily,
                                color = Color(android.graphics.Color.parseColor(sp.colorHex)),
                                fontSize = sp.fontSize.sp,
                                fontWeight = if (sp.isBold) FontWeight.ExtraBold else FontWeight.Normal,
                                textGeometricTransform = if (sp.isItalic) TextGeometricTransform(skewX = -0.28f) else null,
                                textDecoration = textDecor
                            ),
                            s, e
                        )
                    }
                }
            }
        }
    }
}

fun isStrokeHit(stroke: NormStroke, touchX: Float, touchY: Float, threshold: Float = 0.05f): Boolean {
    for (pt in stroke.points) {
        val dist = hypot((pt.x - touchX).toDouble(), (pt.y - touchY).toDouble()).toFloat()
        if (dist <= threshold) return true
    }
    return false
}

@Composable
fun PegboardScreen(
    context: Context,
    openMemoId: String?,
    onClearOpenMemoId: () -> Unit,
    onPinSingleWidget: (MemoItem) -> Unit
) {
    val prefs = remember { context.getSharedPreferences("foldstit_board", Context.MODE_PRIVATE) }
    val memoList = remember { mutableStateListOf<MemoItem>() }
    var editingMemo by remember { mutableStateOf<MemoItem?>(null) }
    var isDockExpanded by remember { mutableStateOf(true) }

    var isFilterSheetVisible by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedFilterTheme by remember { mutableStateOf<NoteTheme?>(null) }

    var isSelectionMode by remember { mutableStateOf(false) }
    val selectedMemoIds = remember { mutableStateListOf<String>() }

    var widgetQueue by remember { mutableStateOf<List<MemoItem>>(emptyList()) }
    var currentQueueIndex by remember { mutableStateOf(0) }

    var flutteringMemoId by remember { mutableStateOf<String?>(null) }
    val flutterAnim = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    fun renderCompositeBitmap(memo: MemoItem) {
        try {
            val bmpWidth = 600
            val bmpHeight = 600
            val bmp = Bitmap.createBitmap(bmpWidth, bmpHeight, Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(bmp)

            memo.photos.forEach { p ->
                val pFile = File(context.filesDir, p.fileName)
                if (pFile.exists()) {
                    val rawBmp = BitmapFactory.decodeFile(pFile.absolutePath)
                    if (rawBmp != null) {
                        val baseSize = 220f * p.scale
                        val aspect = rawBmp.height.toFloat() / rawBmp.width.toFloat()
                        val sw = baseSize
                        val sh = baseSize * aspect

                        val matrix = Matrix().apply {
                            postScale(sw / rawBmp.width, sh / rawBmp.height)
                            postRotate(p.rotation, sw / 2f, sh / 2f)
                            postTranslate(p.offsetX, p.offsetY)
                        }
                        val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
                        canvas.drawBitmap(rawBmp, matrix, bitmapPaint)
                    }
                }
            }

            if (memo.text.isNotEmpty()) {
                val basePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = when (memo.theme) {
                        NoteTheme.NOTEBOOK -> android.graphics.Color.parseColor("#1A237E")
                        else -> android.graphics.Color.parseColor("#2C2C2C")
                    }
                    textSize = (memo.fontSize * 2.8f).coerceIn(32f, 72f)
                    typeface = LocalFontLoader.getTypeface(context, memo.font)
                }

                val ssb = SpannableStringBuilder(memo.text)
                if (memo.spans.isNotEmpty()) {
                    memo.spans.forEach { sp ->
                        val s = sp.start.coerceIn(0, memo.text.length)
                        val e = sp.end.coerceIn(0, memo.text.length)
                        if (s < e) {
                            val fontEnum = try { MemoFont.valueOf(sp.fontName) } catch (_: Exception) { memo.font }
                            val customTf = LocalFontLoader.getTypeface(context, fontEnum)
                            ssb.setSpan(ForegroundColorSpan(android.graphics.Color.parseColor(sp.colorHex)), s, e, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                                ssb.setSpan(TypefaceSpan(customTf), s, e, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                            }
                            if (sp.isBold) ssb.setSpan(StyleSpan(Typeface.BOLD), s, e, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                            if (sp.isItalic) ssb.setSpan(StyleSpan(Typeface.ITALIC), s, e, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                            if (sp.isUnderline) ssb.setSpan(UnderlineSpan(), s, e, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                            if (sp.isStrikethrough) ssb.setSpan(StrikethroughSpan(), s, e, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                        }
                    }
                }

                val staticLayout = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    StaticLayout.Builder.obtain(ssb, 0, ssb.length, basePaint, bmpWidth - 48)
                        .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                        .setLineSpacing(8f, 1.15f)
                        .setIncludePad(false)
                        .setMaxLines(8)
                        .build()
                } else {
                    @Suppress("DEPRECATION")
                    StaticLayout(ssb, basePaint, bmpWidth - 48, Layout.Alignment.ALIGN_NORMAL, 1.15f, 8f, false)
                }

                canvas.save()
                canvas.translate(24f, 24f)
                staticLayout.draw(canvas)
                canvas.restore()
            }

            val textFile = File(context.filesDir, "text_${memo.id}.png")
            FileOutputStream(textFile).use { out -> bmp.compress(Bitmap.CompressFormat.PNG, 100, out) }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun parseJsonToMemos(jsonStr: String): List<MemoItem> {
        val list = mutableListOf<MemoItem>()
        try {
            val arr = JSONArray(jsonStr)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val themeName = obj.optString("theme", NoteTheme.PASTEL_YELLOW.name)
                val fontName = obj.optString("font", MemoFont.DEFAULT.name)
                val memo = MemoItem(
                    id = obj.getString("id"),
                    text = obj.optString("text", ""),
                    theme = try { NoteTheme.valueOf(themeName) } catch (_: Exception) { NoteTheme.PASTEL_YELLOW },
                    font = try { MemoFont.valueOf(fontName) } catch (_: Exception) { MemoFont.DEFAULT },
                    fontSize = obj.optInt("fontSize", 16),
                    penWidth = obj.optDouble("penWidth", 5.0).toFloat(),
                    isBold = obj.optBoolean("isBold", false),
                    isItalic = obj.optBoolean("isItalic", false),
                    isUnderline = obj.optBoolean("isUnderline", false),
                    isStrikethrough = obj.optBoolean("isStrikethrough", false),
                    rotationAngle = obj.optDouble("rotationAngle", 0.0).toFloat(),
                    updatedAt = obj.optLong("updatedAt", System.currentTimeMillis())
                )

                val spansArr = obj.optJSONArray("spans")
                if (spansArr != null) {
                    for (s in 0 until spansArr.length()) {
                        val spObj = spansArr.getJSONObject(s)
                        memo.spans.add(
                            TextSpanItem(
                                start = spObj.getInt("start"),
                                end = spObj.getInt("end"),
                                fontName = spObj.optString("fontName", MemoFont.DEFAULT.name),
                                colorHex = spObj.optString("colorHex", "#2C2C2C"),
                                fontSize = spObj.optInt("fontSize", 16),
                                isBold = spObj.optBoolean("isBold", false),
                                isItalic = spObj.optBoolean("isItalic", false),
                                isUnderline = spObj.optBoolean("isUnderline", false),
                                isStrikethrough = spObj.optBoolean("isStrikethrough", false)
                            )
                        )
                    }
                }

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

                val photosArr = obj.optJSONArray("photos")
                if (photosArr != null) {
                    for (m in 0 until photosArr.length()) {
                        val pObj = photosArr.getJSONObject(m)
                        memo.photos.add(
                            PhotoStickerItem(
                                id = pObj.optString("id", UUID.randomUUID().toString()),
                                fileName = pObj.getString("fileName"),
                                offsetX = pObj.optDouble("offsetX", 50.0).toFloat(),
                                offsetY = pObj.optDouble("offsetY", 50.0).toFloat(),
                                scale = pObj.optDouble("scale", 1.0).toFloat(),
                                rotation = pObj.optDouble("rotation", 0.0).toFloat()
                            )
                        )
                    }
                }
                list.add(memo)
            }
        } catch (e: Exception) { e.printStackTrace() }
        return list
    }

    fun memosToJsonString(list: List<MemoItem>): String {
        val arr = JSONArray()
        list.forEach { memo ->
            val obj = JSONObject().apply {
                put("id", memo.id)
                put("text", memo.text)
                put("theme", memo.theme.name)
                put("font", memo.font.name)
                put("fontSize", memo.fontSize)
                put("penWidth", memo.penWidth)
                put("isBold", memo.isBold)
                put("isItalic", memo.isItalic)
                put("isUnderline", memo.isUnderline)
                put("isStrikethrough", memo.isStrikethrough)
                put("rotationAngle", memo.rotationAngle)
                put("updatedAt", memo.updatedAt)

                val spArr = JSONArray()
                memo.spans.forEach { sp ->
                    spArr.put(JSONObject().apply {
                        put("start", sp.start)
                        put("end", sp.end)
                        put("fontName", sp.fontName)
                        put("colorHex", sp.colorHex)
                        put("fontSize", sp.fontSize)
                        put("isBold", sp.isBold)
                        put("isItalic", sp.isItalic)
                        put("isUnderline", sp.isUnderline)
                        put("isStrikethrough", sp.isStrikethrough)
                    })
                }
                put("spans", spArr)

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

                val pArr = JSONArray()
                memo.photos.forEach { p ->
                    pArr.put(JSONObject().apply {
                        put("id", p.id)
                        put("fileName", p.fileName)
                        put("offsetX", p.offsetX)
                        put("offsetY", p.offsetY)
                        put("scale", p.scale)
                        put("rotation", p.rotation)
                    })
                }
                put("photos", pArr)
            }
            arr.put(obj)
        }
        return arr.toString()
    }

    fun loadMemos() {
        memoList.clear()
        val jsonStr = prefs.getString("memo_list_json", null)
        if (!jsonStr.isNullOrEmpty()) {
            memoList.addAll(parseJsonToMemos(jsonStr))
        }
    }

    fun saveAllMemos() {
        try {
            val jsonStr = memosToJsonString(memoList)
            memoList.forEach { memo ->
                CoroutineScope(Dispatchers.IO).launch {
                    renderCompositeBitmap(memo)
                    if (memo.strokes.isNotEmpty()) {
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
                    } else {
                        val outFile = File(context.filesDir, "drawing_${memo.id}.png")
                        if (outFile.exists()) outFile.delete()
                    }
                }
            }
            prefs.edit().putString("memo_list_json", jsonStr).apply()
            CoroutineScope(Dispatchers.IO).launch {
                FoldstItWidget().updateAll(context)
                FoldstItWidget.notifyUpdate(context)
            }
        } catch (e: Exception) { e.printStackTrace() }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch(Dispatchers.IO) {
                try {
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        out.write(memosToJsonString(memoList).toByteArray(Charsets.UTF_8))
                    }
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "백업 파일이 안전하게 저장되었습니다.", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "백업 실패: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch(Dispatchers.IO) {
                try {
                    val sb = StringBuilder()
                    context.contentResolver.openInputStream(uri)?.use { stream ->
                        BufferedReader(InputStreamReader(stream)).forEachLine { sb.append(it) }
                    }
                    val imported = parseJsonToMemos(sb.toString())
                    withContext(Dispatchers.Main) {
                        if (imported.isNotEmpty()) {
                            memoList.clear()
                            memoList.addAll(imported)
                            saveAllMemos()
                            Toast.makeText(context, "${imported.size}개의 메모가 복원되었습니다.", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "올바른 백업 데이터가 아닙니다.", Toast.LENGTH_SHORT).show()
                        }
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "복원 실패: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
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

    val filteredMemoList = remember(memoList.toList(), searchQuery, selectedFilterTheme) {
        memoList.filter { memo ->
            val matchTheme = selectedFilterTheme == null || memo.theme == selectedFilterTheme
            val matchQuery = searchQuery.isEmpty() || memo.text.contains(searchQuery, ignoreCase = true)
            matchTheme && matchQuery
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFD6D6D6))
    ) {
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

        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 160.dp),
            contentPadding = PaddingValues(
                start = 16.dp, 
                end = 16.dp, 
                top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 16.dp, 
                bottom = 100.dp
            ),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(filteredMemoList, key = { it.id }) { memo ->
                val isSelected = selectedMemoIds.contains(memo.id)
                val isThisFluttering = flutteringMemoId == memo.id
                val flutterOffset = if (isThisFluttering) flutterAnim.value else 0f

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .rotate(memo.rotationAngle + flutterOffset)
                        .scale(if (isThisFluttering) 1.05f else 1f)
                        .shadow(
                            elevation = if (isThisFluttering) 18.dp else (if (isSelected) 14.dp else 8.dp),
                            shape = RoundedCornerShape(8.dp),
                            spotColor = Color.Black.copy(alpha = 0.32f),
                            ambientColor = Color.Black.copy(alpha = 0.18f)
                        )
                        .clip(RoundedCornerShape(8.dp))
                        .background(memo.theme.bgColor)
                        .border(
                            width = if (isSelected) 3.dp else 0.dp,
                            color = if (isSelected) Color(0xFF1976D2) else Color.Transparent,
                            shape = RoundedCornerShape(8.dp)
                        )
                        .pointerInput(isSelectionMode) {
                            detectTapGestures(
                                onTap = {
                                    if (isSelectionMode) {
                                        if (isSelected) selectedMemoIds.remove(memo.id) else selectedMemoIds.add(memo.id)
                                        if (selectedMemoIds.isEmpty()) isSelectionMode = false
                                    } else {
                                        scope.launch {
                                            flutteringMemoId = memo.id
                                            flutterAnim.animateTo(
                                                targetValue = 1f,
                                                animationSpec = keyframes {
                                                    durationMillis = 320
                                                    0f at 0
                                                    -5f at 90
                                                    4f at 190
                                                    0f at 320
                                                }
                                            )
                                            editingMemo = memo
                                            flutteringMemoId = null
                                            flutterAnim.snapTo(0f)
                                        }
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
                    if (memo.theme.hasLines) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val lineGap = 22.dp.toPx()
                            var y = lineGap + 20.dp.toPx()
                            while (y < size.height) {
                                drawLine(
                                    color = Color(0xFF8D6E63).copy(alpha = 0.22f),
                                    start = Offset(0f, y),
                                    end = Offset(size.width, y),
                                    strokeWidth = 1.dp.toPx()
                                )
                                y += lineGap
                            }
                        }
                    }

                    Box(modifier = Modifier.fillMaxSize()) {
                        memo.photos.forEach { p ->
                            val pFile = File(context.filesDir, p.fileName)
                            if (pFile.exists()) {
                                val b = BitmapFactory.decodeFile(pFile.absolutePath)
                                if (b != null) {
                                    Image(
                                        bitmap = b.asImageBitmap(),
                                        contentDescription = null,
                                        modifier = Modifier
                                            .size((50 * p.scale).dp)
                                            .offset(x = p.offsetX.dp, y = p.offsetY.dp)
                                            .rotate(p.rotation)
                                            .alpha(0.9f)
                                    )
                                }
                            }
                        }
                    }

                    Box(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 8.dp)
                            .size(14.dp)
                            .shadow(elevation = 3.dp, shape = CircleShape)
                            .clip(CircleShape)
                            .background(memo.theme.pinColor)
                            .border(1.2.dp, Color.White.copy(alpha = 0.85f), CircleShape)
                    )

                    if (isSelectionMode) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(8.dp)
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

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(start = 12.dp, end = 12.dp, top = 26.dp, bottom = 10.dp)
                    ) {
                        val styledText = rememberCachedStyledString(
                            context = context,
                            text = memo.text,
                            spans = memo.spans,
                            defaultFont = memo.font,
                            defaultColor = memo.theme.textColor,
                            defaultSize = memo.fontSize
                        )
                        // 명시적으로 AnnotatedString 버전 호출
                        Text(text = styledText as AnnotatedString, maxLines = 6, lineHeight = (memo.fontSize + 6).sp, modifier = Modifier.fillMaxSize())

                        if (memo.strokes.isNotEmpty()) {
                            Canvas(modifier = Modifier.fillMaxSize()) {
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

        if (isSelectionMode) {
            Card(
                shape = RoundedCornerShape(32.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF263238)),
                elevation = CardDefaults.cardElevation(defaultElevation = 12.dp),
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 20.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text("${selectedMemoIds.size}개 선택됨", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    TextButton(onClick = {
                        val selectedMemos = memoList.filter { selectedMemoIds.contains(it.id) }
                        if (selectedMemos.isNotEmpty()) {
                            widgetQueue = selectedMemos
                            currentQueueIndex = 0
                            isSelectionMode = false
                            selectedMemoIds.clear()
                        }
                    }) {
                        Icon(Icons.Default.PushPin, contentDescription = "Pin", tint = Color(0xFF81D4FA), modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("위젯 일괄 등록", color = Color(0xFF81D4FA), fontSize = 12.sp)
                    }

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

                    IconButton(onClick = { selectedMemoIds.clear(); isSelectionMode = false }) {
                        Icon(Icons.Default.Close, contentDescription = "Cancel", tint = Color.LightGray)
                    }
                }
            }
        } else {
            Card(
                shape = RoundedCornerShape(32.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 16.dp, bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 16.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    IconButton(onClick = { isDockExpanded = !isDockExpanded }, modifier = Modifier.size(32.dp)) {
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
                                val randomAngle = (-2..2).random().toFloat()
                                val newMemo = MemoItem(text = "", rotationAngle = randomAngle)
                                memoList.add(0, newMemo)
                                saveAllMemos()
                                editingMemo = newMemo
                            }
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "New", tint = Color(0xFF1976D2), modifier = Modifier.size(22.dp))
                            Text("New", fontSize = 10.sp, color = Color.DarkGray)
                        }

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.clickable { isFilterSheetVisible = !isFilterSheetVisible }
                        ) {
                            Icon(
                                Icons.Default.Search, 
                                contentDescription = "Search", 
                                tint = if (searchQuery.isNotEmpty() || selectedFilterTheme != null) Color(0xFF1976D2) else Color.DarkGray, 
                                modifier = Modifier.size(22.dp)
                            )
                            Text("Search", fontSize = 10.sp, color = Color.DarkGray)
                        }

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.clickable {
                                saveAllMemos()
                                Toast.makeText(context, "위젯과 동기화되었습니다.", Toast.LENGTH_SHORT).show()
                            }
                        ) {
                            Icon(Icons.Default.CloudSync, contentDescription = "Sync", tint = Color.Black, modifier = Modifier.size(22.dp))
                            Text("Sync", fontSize = 10.sp, color = Color.DarkGray)
                        }

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.clickable {
                                exportLauncher.launch("FoldstIt_Backup_${System.currentTimeMillis()}.json")
                            }
                        ) {
                            Icon(Icons.Default.UploadFile, contentDescription = "Backup", tint = Color(0xFF43A047), modifier = Modifier.size(22.dp))
                            Text("Backup", fontSize = 10.sp, color = Color.DarkGray)
                        }

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.clickable {
                                importLauncher.launch(arrayOf("application/json", "*/*"))
                            }
                        ) {
                            Icon(Icons.Default.Download, contentDescription = "Restore", tint = Color(0xFFE65100), modifier = Modifier.size(22.dp))
                            Text("Restore", fontSize = 10.sp, color = Color.DarkGray)
                        }
                    }
                }
            }
        }

        if (isFilterSheetVisible) {
            Card(
                shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 16.dp),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 68.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("메모 검색 및 테마 필터", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        IconButton(onClick = { isFilterSheetVisible = false }, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.Gray)
                        }
                    }

                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("단어 또는 내용 검색...", fontSize = 12.sp) },
                        singleLine = true,
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Clear", modifier = Modifier.size(18.dp))
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(24.dp)
                    )

                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        item {
                            FilterChip(
                                selected = selectedFilterTheme == null,
                                onClick = { selectedFilterTheme = null },
                                label = { Text("전체 (${memoList.size})", fontSize = 11.sp) }
                            )
                        }
                        items(NoteTheme.values().toList()) { theme ->
                            val count = memoList.count { it.theme == theme }
                            FilterChip(
                                selected = selectedFilterTheme == theme,
                                onClick = { selectedFilterTheme = if (selectedFilterTheme == theme) null else theme },
                                label = { Text("${theme.title} ($count)", fontSize = 11.sp) },
                                leadingIcon = {
                                    Box(
                                        modifier = Modifier
                                            .size(10.dp)
                                            .clip(CircleShape)
                                            .background(theme.bgColor)
                                            .border(1.dp, theme.pinColor, CircleShape)
                                    )
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    if (widgetQueue.isNotEmpty() && currentQueueIndex < widgetQueue.size) {
        val currentTarget = widgetQueue[currentQueueIndex]
        AlertDialog(
            onDismissRequest = { widgetQueue = emptyList() },
            title = { Text("위젯 등록 (${currentQueueIndex + 1}/${widgetQueue.size})", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("안드로이드 시스템 홈 런처 규약에 따라 한 번에 하나씩 배치됩니다.", fontSize = 13.sp, color = Color.Gray)
                    Card(
                        colors = CardDefaults.cardColors(containerColor = currentTarget.theme.bgColor),
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                    ) {
                        Text(
                            text = currentTarget.text.ifEmpty { "[손글씨 필기 메모]" },
                            color = currentTarget.theme.textColor,
                            fontSize = 13.sp,
                            maxLines = 2,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onPinSingleWidget(currentTarget)
                        if (currentQueueIndex + 1 < widgetQueue.size) {
                            currentQueueIndex++
                        } else {
                            widgetQueue = emptyList()
                            Toast.makeText(context, "모든 선택 위젯 등록이 완료되었습니다.", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1976D2))
                ) {
                    Text("홈 화면에 추가")
                }
            },
            dismissButton = {
                TextButton(onClick = { widgetQueue = emptyList() }) { Text("그만두기") }
            }
        )
    }

    editingMemo?.let { targetMemo ->
        BackHandler { editingMemo = null }
        MemoEditorOverlay(
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
fun MemoEditorOverlay(
    memo: MemoItem,
    onDismiss: () -> Unit,
    onSave: (MemoItem) -> Unit,
    onDelete: (String) -> Unit
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    var tfValue by remember { mutableStateOf(TextFieldValue(memo.text, TextRange(memo.text.length))) }
    var currentTheme by remember { mutableStateOf(memo.theme) }

    val spanList = remember { mutableStateListOf<TextSpanItem>().apply { addAll(memo.spans) } }
    val photoStickers = remember { mutableStateListOf<PhotoStickerItem>().apply { addAll(memo.photos) } }
    var selectedPhotoId by remember { mutableStateOf<String?>(null) }

    var activeFont by remember { mutableStateOf(memo.font) }
    var activeColorHex by remember { mutableStateOf("#2C2C2C") }
    var activeFontSize by remember { mutableStateOf(memo.fontSize) }
    var activeBold by remember { mutableStateOf(memo.isBold) }
    var activeItalic by remember { mutableStateOf(memo.isItalic) }
    var activeUnderline by remember { mutableStateOf(memo.isUnderline) }
    var activeStrikethrough by remember { mutableStateOf(memo.isStrikethrough) }

    var isPenMode by remember { mutableStateOf(false) }
    var isEraserMode by remember { mutableStateOf(false) }
    var showMemoSettings by remember { mutableStateOf(false) }

    var selectedDrawColorHex by remember { mutableStateOf("#E53935") }
    var selectedPenWidth by remember { mutableStateOf(memo.penWidth) }
    var showDrawingPalette by remember { mutableStateOf(false) }

    val colorPalette = listOf(
        "#2C2C2C", "#E53935", "#FF9800", "#FFEB3B", "#4CAF50",
        "#00BCD4", "#2196F3", "#9C27B0", "#FFFFFF", "#E91E63"
    )

    val strokes = remember { mutableStateListOf<NormStroke>().apply { addAll(memo.strokes) } }
    val undoStack = remember { mutableStateListOf<NormStroke>() }
    var currentStrokePoints by remember { mutableStateOf(listOf<NormPoint>()) }

    var canvasWidth by remember { mutableStateOf(1f) }
    var canvasHeight by remember { mutableStateOf(1f) }

    val focusRequester = remember { FocusRequester() }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val bitmap = ImageLoaderUtil.loadResizedBitmap(context, uri, 512, 512)
                if (bitmap != null) {
                    val fileName = "photo_${UUID.randomUUID()}.png"
                    val outFile = File(context.filesDir, fileName)
                    FileOutputStream(outFile).use { out ->
                        bitmap.compress(Bitmap.CompressFormat.PNG, 90, out)
                    }
                    photoStickers.add(PhotoStickerItem(fileName = fileName, offsetX = 60f, offsetY = 80f, scale = 1.0f))
                    Toast.makeText(context, "사진이 추가되었습니다.", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "사진 불러오기 실패", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun applyOrSetStyle(
        newFont: MemoFont? = null,
        newColor: String? = null,
        newSize: Int? = null,
        toggleBold: Boolean = false,
        toggleItalic: Boolean = false,
        toggleUnderline: Boolean = false,
        toggleStrikethrough: Boolean = false
    ) {
        if (newFont != null) activeFont = newFont
        if (newColor != null) activeColorHex = newColor
        if (newSize != null) activeFontSize = newSize
        if (toggleBold) activeBold = !activeBold
        if (toggleItalic) activeItalic = !activeItalic
        if (toggleUnderline) activeUnderline = !activeUnderline
        if (toggleStrikethrough) activeStrikethrough = !activeStrikethrough

        val sel = tfValue.selection
        if (!sel.collapsed && sel.min < sel.max) {
            spanList.removeAll { !(it.end <= sel.min || it.start >= sel.max) }
            spanList.add(
                TextSpanItem(
                    start = sel.min,
                    end = sel.max,
                    fontName = activeFont.name,
                    colorHex = activeColorHex,
                    fontSize = activeFontSize,
                    isBold = activeBold,
                    isItalic = activeItalic,
                    isUnderline = activeUnderline,
                    isStrikethrough = activeStrikethrough
                )
            )
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.6f))
            .zIndex(100f)
            .padding(
                top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 8.dp,
                bottom = 12.dp,
                start = 12.dp,
                end = 12.dp
            ),
        contentAlignment = Alignment.Center
    ) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = currentTheme.bgColor),
            elevation = CardDefaults.cardElevation(defaultElevation = 18.dp),
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = { isPenMode = false; isEraserMode = false; showDrawingPalette = false },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(Icons.Default.Keyboard, contentDescription = "Text", tint = if (!isPenMode && !isEraserMode) Color(0xFF1976D2) else currentTheme.textColor)
                            }
                            IconButton(
                                onClick = { isPenMode = true; isEraserMode = false; showDrawingPalette = true },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(Icons.Default.Create, contentDescription = "Pen", tint = if (isPenMode && !isEraserMode) Color(0xFF1976D2) else currentTheme.textColor)
                            }
                            IconButton(
                                onClick = { isPenMode = true; isEraserMode = true; showDrawingPalette = false },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(Icons.Default.AutoFixNormal, contentDescription = "Eraser", tint = if (isEraserMode) Color(0xFFE91E63) else currentTheme.textColor)
                            }

                            IconButton(
                                onClick = { photoPickerLauncher.launch("image/*") },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(Icons.Default.AddPhotoAlternate, contentDescription = "Add Photo", tint = Color(0xFF00897B))
                            }

                            if (isPenMode && !isEraserMode) {
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clip(CircleShape)
                                        .background(Color(android.graphics.Color.parseColor(selectedDrawColorHex)))
                                        .border(1.5.dp, Color.White, CircleShape)
                                        .clickable { showDrawingPalette = !showDrawingPalette }
                                )
                            }
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = { if (strokes.isNotEmpty()) undoStack.add(strokes.removeAt(strokes.size - 1)) },
                                enabled = strokes.isNotEmpty(),
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(Icons.Default.Undo, contentDescription = "Undo", tint = if (strokes.isNotEmpty()) currentTheme.textColor else Color.Gray)
                            }
                            IconButton(onClick = { onDelete(memo.id) }, modifier = Modifier.size(36.dp)) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.Red.copy(alpha = 0.7f))
                            }
                            IconButton(onClick = { showMemoSettings = !showMemoSettings }, modifier = Modifier.size(36.dp)) {
                                Icon(Icons.Default.Tune, contentDescription = "Settings", tint = currentTheme.textColor)
                            }
                        }
                    }

                    if (!isPenMode) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
                                TextButton(
                                    onClick = { applyOrSetStyle(toggleBold = true) },
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                                    colors = ButtonDefaults.textButtonColors(
                                        containerColor = if (activeBold) Color(0xFF1976D2) else Color.Transparent,
                                        contentColor = if (activeBold) Color.White else currentTheme.textColor
                                    )
                                ) { Text("B", fontWeight = FontWeight.ExtraBold, fontSize = 13.sp) }

                                TextButton(
                                    onClick = { applyOrSetStyle(toggleItalic = true) },
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                                    colors = ButtonDefaults.textButtonColors(
                                        containerColor = if (activeItalic) Color(0xFF1976D2) else Color.Transparent,
                                        contentColor = if (activeItalic) Color.White else currentTheme.textColor
                                    )
                                ) { Text("I", fontStyle = FontStyle.Italic, fontSize = 13.sp) }

                                TextButton(
                                    onClick = { applyOrSetStyle(toggleUnderline = true) },
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                                    colors = ButtonDefaults.textButtonColors(
                                        containerColor = if (activeUnderline) Color(0xFF1976D2) else Color.Transparent,
                                        contentColor = if (activeUnderline) Color.White else currentTheme.textColor
                                    )
                                ) { Text("U", style = TextStyle(textDecoration = TextDecoration.Underline), fontSize = 13.sp) }

                                TextButton(
                                    onClick = { applyOrSetStyle(toggleStrikethrough = true) },
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                                    colors = ButtonDefaults.textButtonColors(
                                        containerColor = if (activeStrikethrough) Color(0xFF1976D2) else Color.Transparent,
                                        contentColor = if (activeStrikethrough) Color.White else currentTheme.textColor
                                    )
                                ) { Text("S", style = TextStyle(textDecoration = TextDecoration.LineThrough), fontSize = 13.sp) }

                                Box(
                                    modifier = Modifier
                                        .size(20.dp)
                                        .clip(CircleShape)
                                        .background(Color(android.graphics.Color.parseColor(activeColorHex)))
                                        .border(1.dp, Color.White, CircleShape)
                                        .clickable {
                                            val nextColor = when (activeColorHex) {
                                                "#2C2C2C" -> "#E53935"
                                                "#E53935" -> "#2196F3"
                                                "#2196F3" -> "#4CAF50"
                                                else -> "#2C2C2C"
                                            }
                                            applyOrSetStyle(newColor = nextColor)
                                        }
                                )
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color.Black.copy(alpha = 0.07f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    "-",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = currentTheme.textColor,
                                    modifier = Modifier.clickable { if (activeFontSize > 12) applyOrSetStyle(newSize = activeFontSize - 2) }.padding(horizontal = 6.dp)
                                )
                                Text("${activeFontSize}pt", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = currentTheme.textColor, modifier = Modifier.padding(horizontal = 4.dp))
                                Text(
                                    "+",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = currentTheme.textColor,
                                    modifier = Modifier.clickable { if (activeFontSize < 34) applyOrSetStyle(newSize = activeFontSize + 2) }.padding(horizontal = 6.dp)
                                )
                            }
                        }
                    }

                    AnimatedVisibility(visible = showMemoSettings) {
                        Card(
                            shape = RoundedCornerShape(8.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.96f)),
                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                        ) {
                            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
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

                                Text("나눔 손글씨 폰트", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                                    items(MemoFont.values().toList(), key = { it.name }) { f ->
                                        OutlinedButton(
                                            onClick = { applyOrSetStyle(newFont = f) },
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                            colors = ButtonDefaults.outlinedButtonColors(containerColor = if (activeFont == f) Color(0xFFE0E0E0) else Color.Transparent)
                                        ) {
                                            Text(f.title, fontSize = 10.sp, color = Color.Black, fontFamily = LocalFontLoader.getFontFamily(context, f))
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .pointerInput(Unit) {
                                detectTapGestures {
                                    selectedPhotoId = null
                                    focusManager.clearFocus()
                                }
                            }
                    ) {
                        if (currentTheme.hasLines) {
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                val lineGap = 26.dp.toPx()
                                var y = lineGap + 10.dp.toPx()
                                while (y < size.height) {
                                    drawLine(
                                        color = Color(0xFF8D6E63).copy(alpha = 0.25f),
                                        start = Offset(0f, y),
                                        end = Offset(size.width, y),
                                        strokeWidth = 1.dp.toPx()
                                    )
                                    y += lineGap
                                }
                            }
                        }

                        BasicTextField(
                            value = tfValue,
                            onValueChange = { newVal ->
                                val oldLen = tfValue.text.length
                                val newLen = newVal.text.length
                                if (newLen > oldLen) {
                                    val insertStart = tfValue.selection.min
                                    val insertEnd = insertStart + (newLen - oldLen)
                                    spanList.add(
                                        TextSpanItem(
                                            start = insertStart,
                                            end = insertEnd,
                                            fontName = activeFont.name,
                                            colorHex = activeColorHex,
                                            fontSize = activeFontSize,
                                            isBold = activeBold,
                                            isItalic = activeItalic,
                                            isUnderline = activeUnderline,
                                            isStrikethrough = activeStrikethrough
                                        )
                                    )
                                }
                                tfValue = newVal
                            },
                            visualTransformation = {
                                androidx.compose.ui.text.input.TransformedText(
                                    rememberCachedStyledString(
                                        context = context,
                                        text = tfValue.text,
                                        spans = spanList,
                                        defaultFont = activeFont,
                                        defaultColor = currentTheme.textColor,
                                        defaultSize = activeFontSize
                                    ),
                                    androidx.compose.ui.text.input.OffsetMapping.Identity
                                )
                            },
                            textStyle = TextStyle(
                                fontSize = activeFontSize.sp,
                                lineHeight = (activeFontSize + 8).sp,
                                color = currentTheme.textColor
                            ),
                            modifier = Modifier
                                .fillMaxSize()
                                .focusRequester(focusRequester)
                                .zIndex(if (!isPenMode) 3f else 0.5f),
                            enabled = !isPenMode,
                            decorationBox = { innerTextField ->
                                if (tfValue.text.isEmpty() && strokes.isEmpty()) {
                                    Text(
                                        text = "메모를 입력하거나 상단 펜 모드로 손글씨를 써보세요...",
                                        style = TextStyle(
                                            color = currentTheme.textColor.copy(alpha = 0.4f),
                                            fontSize = activeFontSize.sp,
                                            fontFamily = LocalFontLoader.getFontFamily(context, activeFont)
                                        )
                                    )
                                }
                                innerTextField()
                            }
                        )

                        photoStickers.forEach { sticker ->
                            val pFile = File(context.filesDir, sticker.fileName)
                            if (pFile.exists()) {
                                val b = BitmapFactory.decodeFile(pFile.absolutePath)
                                if (b != null) {
                                    val isSelected = selectedPhotoId == sticker.id
                                    Box(
                                        modifier = Modifier
                                            .offset(x = sticker.offsetX.dp, y = sticker.offsetY.dp)
                                            .graphicsLayer(
                                                scaleX = sticker.scale,
                                                scaleY = sticker.scale,
                                                rotationZ = sticker.rotation,
                                                transformOrigin = androidx.compose.ui.graphics.TransformOrigin.Center
                                            )
                                            .pointerInput(sticker.id) {
                                                detectDragGestures { change, dragAmount ->
                                                    change.consume()
                                                    selectedPhotoId = sticker.id
                                                    sticker.offsetX += dragAmount.x / density
                                                    sticker.offsetY += dragAmount.y / density
                                                }
                                            }
                                            .border(
                                                width = if (isSelected) 2.dp else 0.dp,
                                                color = if (isSelected) Color(0xFF1976D2) else Color.Transparent,
                                                shape = RoundedCornerShape(4.dp)
                                            )
                                            .zIndex(if (isSelected) 500f else 50f)
                                    ) {
                                        Image(
                                            bitmap = b.asImageBitmap(),
                                            contentDescription = "Photo Sticker",
                                            modifier = Modifier
                                                .width(160.dp)
                                                .wrapContentHeight()
                                                .clip(RoundedCornerShape(4.dp))
                                                .clickable { selectedPhotoId = sticker.id }
                                        )

                                        if (isSelected) {
                                            Box(
                                                modifier = Modifier
                                                    .align(Alignment.TopEnd)
                                                    .offset(x = 10.dp, y = (-10).dp)
                                                    .size(28.dp)
                                                    .clip(CircleShape)
                                                    .background(Color.Red)
                                                    .clickable { 
                                                        photoStickers.remove(sticker)
                                                        selectedPhotoId = null
                                                    },
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(Icons.Default.Close, contentDescription = "Delete", tint = Color.White, modifier = Modifier.size(16.dp))
                                            }

                                            Box(
                                                modifier = Modifier
                                                    .align(Alignment.BottomEnd)
                                                    .offset(x = 10.dp, y = 10.dp)
                                                    .size(30.dp)
                                                    .clip(CircleShape)
                                                    .background(Color(0xFF1976D2))
                                                    .pointerInput(sticker.id + "_rotate") {
                                                        detectDragGestures { change, dragAmount ->
                                                            change.consume()
                                                            val angleDelta = (dragAmount.x - dragAmount.y) * 0.4f
                                                            sticker.rotation = (sticker.rotation + angleDelta) % 360f
                                                            val scaleDelta = (dragAmount.x + dragAmount.y) * 0.003f
                                                            sticker.scale = (sticker.scale + scaleDelta).coerceIn(0.3f, 4.0f)
                                                        }
                                                    },
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(Icons.Default.RotateRight, contentDescription = "Rotate/Scale", tint = Color.White, modifier = Modifier.size(18.dp))
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        Canvas(
                            modifier = Modifier
                                .fillMaxSize()
                                .zIndex(if (isPenMode) 4f else 1f)
                                .pointerInput(isPenMode, isEraserMode, selectedDrawColorHex, selectedPenWidth) {
                                    if (isPenMode) {
                                        detectDragGestures(
                                            onDragStart = { offset ->
                                                undoStack.clear()
                                                canvasWidth = size.width.toFloat().coerceAtLeast(1f)
                                                canvasHeight = size.height.toFloat().coerceAtLeast(1f)
                                                val normX = offset.x / canvasWidth
                                                val normY = offset.y / canvasHeight

                                                if (isEraserMode) {
                                                    strokes.removeAll { s -> isStrokeHit(s, normX, normY) }
                                                } else {
                                                    currentStrokePoints = listOf(NormPoint(normX, normY))
                                                }
                                            },
                                            onDrag = { change, _ ->
                                                change.consume()
                                                val normX = change.position.x / canvasWidth
                                                val normY = change.position.y / canvasHeight

                                                if (isEraserMode) {
                                                    strokes.removeAll { s -> isStrokeHit(s, normX, normY) }
                                                } else {
                                                    currentStrokePoints = currentStrokePoints + NormPoint(normX, normY)
                                                }
                                            },
                                            onDragEnd = {
                                                if (!isEraserMode && currentStrokePoints.isNotEmpty()) {
                                                    strokes.add(NormStroke(currentStrokePoints, selectedDrawColorHex, selectedPenWidth))
                                                    currentStrokePoints = emptyList()
                                                }
                                            }
                                        )
                                    }
                                }
                        ) {
                            canvasWidth = size.width.toFloat().coerceAtLeast(1f)
                            canvasHeight = size.height.toFloat().coerceAtLeast(1f)

                            val drawingBmp = Bitmap.createBitmap(canvasWidth.toInt(), canvasHeight.toInt(), Bitmap.Config.ARGB_8888)
                            val canvas = android.graphics.Canvas(drawingBmp)
                            val paint = Paint().apply {
                                isAntiAlias = true
                                style = Paint.Style.STROKE
                                strokeCap = Paint.Cap.ROUND
                                strokeJoin = Paint.Join.ROUND
                            }
                            strokes.forEach { s ->
                                paint.color = android.graphics.Color.parseColor(s.colorHex)
                                paint.strokeWidth = s.width * 1.5f
                                if (s.points.size > 1) {
                                    val path = android.graphics.Path()
                                    path.moveTo(s.points[0].x * canvasWidth, s.points[0].y * canvasHeight)
                                    for (pt in s.points) { path.lineTo(pt.x * canvasWidth, pt.y * canvasHeight) }
                                    canvas.drawPath(path, paint)
                                }
                            }
                            if (!isEraserMode && currentStrokePoints.size > 1) {
                                paint.color = android.graphics.Color.parseColor(selectedDrawColorHex)
                                paint.strokeWidth = selectedPenWidth * 1.5f
                                val path = android.graphics.Path()
                                path.moveTo(currentStrokePoints[0].x * canvasWidth, currentStrokePoints[0].y * canvasHeight)
                                for (p in currentStrokePoints) { path.lineTo(p.x * canvasWidth, p.y * canvasHeight) }
                                canvas.drawPath(path, paint)
                            }
                            drawImage(drawingBmp.asImageBitmap())
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = onDismiss) { Text("닫기", color = currentTheme.textColor) }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                memo.text = tfValue.text
                                memo.theme = currentTheme
                                memo.font = activeFont
                                memo.penWidth = selectedPenWidth
                                memo.fontSize = activeFontSize
                                memo.isBold = activeBold
                                memo.isItalic = activeItalic
                                memo.isUnderline = activeUnderline
                                memo.isStrikethrough = activeStrikethrough
                                memo.spans.clear()
                                memo.spans.addAll(spanList)
                                memo.strokes.clear()
                                memo.strokes.addAll(strokes)
                                memo.photos.clear()
                                memo.photos.addAll(photoStickers)
                                onSave(memo)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1976D2))
                        ) {
                            Text("완료 및 저장", color = Color.White)
                        }
                    }
                }

                if (isPenMode && !isEraserMode && showDrawingPalette) {
                    Card(
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF232528)),
                        elevation = CardDefaults.cardElevation(defaultElevation = 16.dp),
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 60.dp, start = 8.dp, end = 8.dp)
                            .fillMaxWidth()
                            .zIndex(10f)
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text("드로잉 펜 도구", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                    Box(
                                        modifier = Modifier
                                            .width(44.dp)
                                            .height((selectedPenWidth / 2f).coerceIn(2f, 10f).dp)
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(Color(android.graphics.Color.parseColor(selectedDrawColorHex)))
                                    )
                                }
                                IconButton(onClick = { showDrawingPalette = false }, modifier = Modifier.size(24.dp)) {
                                    Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.LightGray)
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("굵기", color = Color.Gray, fontSize = 11.sp)
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    listOf(2.5f to "초미세", 5f to "보통", 10f to "굵게", 18f to "형광펜").forEach { (w, label) ->
                                        OutlinedButton(
                                            onClick = { selectedPenWidth = w },
                                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                                            colors = ButtonDefaults.outlinedButtonColors(
                                                containerColor = if (selectedPenWidth == w) Color(0xFF37474F) else Color.Transparent
                                            ),
                                            border = ButtonDefaults.outlinedButtonBorder.copy(
                                                width = if (selectedPenWidth == w) 1.5.dp else 0.5.dp
                                            )
                                        ) {
                                            Text(label, color = if (selectedPenWidth == w) Color(0xFF64B5F6) else Color.LightGray, fontSize = 10.sp)
                                        }
                                    }
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                colorPalette.forEach { hex ->
                                    val isSelected = selectedDrawColorHex.equals(hex, ignoreCase = true)
                                    Box(
                                        modifier = Modifier
                                            .size(24.dp)
                                            .clip(CircleShape)
                                            .background(Color(android.graphics.Color.parseColor(hex)))
                                            .border(
                                                width = if (isSelected) 2.dp else 1.dp,
                                                color = if (isSelected) Color.White else Color.Gray.copy(alpha = 0.4f),
                                                shape = CircleShape
                                            )
                                            .clickable { selectedDrawColorHex = hex },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (isSelected) {
                                            Icon(
                                                Icons.Default.Check,
                                                contentDescription = null,
                                                tint = if (hex == "#FFFFFF" || hex == "#FFEB3B") Color.Black else Color.White,
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
