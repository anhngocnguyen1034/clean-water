package com.example.clean_water

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size as GeoSize
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.ceil

// ─── Màu dùng chung ──────────────────────────────────────────────────────────
private val BgDark     = Color(0xFF0A0E1A)
private val CardBg     = Color(0xFF1A2235)
private val SurfaceBg  = Color(0xFF111827)
private val Cyan       = Color(0xFF00E5FF)
private val Blue       = Color(0xFF2979FF)
private val Green      = Color(0xFF00E676)
private val Red        = Color(0xFFFF1744)
private val Orange     = Color(0xFFFF6D00)
private val TxtPrimary = Color(0xFFE8F4FD)
private val TxtSecond  = Color(0xFF8899AA)

enum class TestType { MENU, DEAD_ZONE, GRID, MULTITOUCH, GHOST_TOUCH, MIC_SPEAKER, PROXIMITY }

// ─── Root ─────────────────────────────────────────────────────────────────────
@Composable
fun PhoneTestScreen(onBack: () -> Unit) {
    var currentTest by remember { mutableStateOf(TestType.MENU) }

    // FIX 3: Vuốt ngang / nhấn back → về màn trước, không thoát app
    BackHandler {
        if (currentTest == TestType.MENU) onBack() else currentTest = TestType.MENU
    }

    when (currentTest) {
        TestType.MENU        -> TestMenuScreen(onBack = onBack, onSelectTest = { currentTest = it })
        TestType.DEAD_ZONE   -> DeadZoneTestScreen   { currentTest = TestType.MENU }
        TestType.GRID        -> GridTestScreen        { currentTest = TestType.MENU }
        TestType.MULTITOUCH  -> MultiTouchTestScreen  { currentTest = TestType.MENU }
        TestType.GHOST_TOUCH -> GhostTouchTestScreen  { currentTest = TestType.MENU }
        TestType.MIC_SPEAKER -> MicSpeakerTestScreen  { currentTest = TestType.MENU }
        TestType.PROXIMITY   -> ProximityTestScreen   { currentTest = TestType.MENU }
    }
}

// ─── Menu ─────────────────────────────────────────────────────────────────────
@Composable
private fun TestMenuScreen(onBack: () -> Unit, onSelectTest: (TestType) -> Unit) {
    val tests  = listOf(
        Triple(TestType.DEAD_ZONE,   "Dead Zone Test",    "Vẽ kín màn hình\nPhát hiện điểm chết cảm ứng"),
        Triple(TestType.GRID,        "Grid Test",         "Lưới ô vuông\nTô màu từng ô để kiểm tra 100% diện tích"),
        Triple(TestType.MULTITOUCH,  "Multi-touch Test",  "Chạm nhiều ngón\nKiểm tra cảm ứng đa điểm"),
        Triple(TestType.GHOST_TOUCH, "Ghost Touch Test",  "Để yên điện thoại\nPhát hiện loạn cảm ứng do nước"),
        Triple(TestType.MIC_SPEAKER, "Test Loa / Mic",    "Ghi âm 5 giây rồi phát lại\nKiểm tra loa & mic có bị nghẹt nước"),
        Triple(TestType.PROXIMITY,   "Test Cảm biến",     "Che / mở cảm biến tiệm cận\nKiểm tra cảm biến có bị nước tác động")
    )
    val icons  = listOf("✏️", "⊞", "👆", "👻", "🎙️", "📡")
    val colors = listOf(Cyan, Green, Blue, Orange, Color(0xFFE040FB), Color(0xFFFFEB3B))

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgDark)
            .padding(horizontal = 20.dp, vertical = 48.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = Cyan,
                modifier = Modifier.clickable { onBack() }
            )
            Spacer(Modifier.width(12.dp))
            Column {
                Text("TEST ĐIỆN THOẠI", fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold, color = Cyan, letterSpacing = 2.sp)
                Text("Kiểm tra màn hình sau khi dính nước",
                    fontSize = 12.sp, color = TxtSecond)
            }
        }
        Spacer(Modifier.height(8.dp))
        tests.forEachIndexed { i, (type, title, desc) ->
            Card(
                modifier = Modifier.fillMaxWidth().clickable { onSelectTest(type) },
                colors = CardDefaults.cardColors(containerColor = CardBg),
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier.size(52.dp).clip(CircleShape)
                            .background(colors[i].copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) { Text(icons[i], fontSize = 24.sp) }
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text(title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = colors[i])
                        Spacer(Modifier.height(2.dp))
                        Text(desc, fontSize = 11.sp, color = TxtSecond, lineHeight = 16.sp)
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════
// 1. DEAD ZONE TEST
// ═══════════════════════════════════════════════════════════════════
private typealias Stroke_ = androidx.compose.runtime.snapshots.SnapshotStateList<Offset>

@Composable
fun DeadZoneTestScreen(onBack: () -> Unit) {
    val density = LocalDensity.current

    // Kích thước canvas thực tế (pixel) — lấy từ onSizeChanged
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }

    // Ô lưới: kích thước mỗi ô = đường kính nét vẽ (18dp) → 1 lần vuốt phủ đúng 1 ô
    val cellPx = with(density) { 18.dp.toPx() }
    val gridCols = remember(canvasSize) {
        if (canvasSize.width > 0) ceil(canvasSize.width / cellPx).toInt() else 1
    }
    val gridRows = remember(canvasSize) {
        if (canvasSize.height > 0) ceil(canvasSize.height / cellPx).toInt() else 1
    }
    val totalCells = gridCols * gridRows

    // Tập hợp ô đã được phủ (row * gridCols + col)
    val touchedCells = remember { mutableStateSetOf<Int>() }
    val coverage     = if (totalCells > 0) touchedCells.size.toFloat() / totalCells else 0f
    val isDone       = coverage >= 0.98f

    // Strokes vẽ real-time
    val finishedStrokes = remember { mutableStateListOf<List<Offset>>() }
    val activeStrokes   = remember { mutableStateMapOf<Long, Stroke_>() }

    fun markCell(pos: Offset) {
        if (canvasSize == IntSize.Zero || cellPx == 0f) return
        val col = (pos.x / cellPx).toInt().coerceIn(0, gridCols - 1)
        val row = (pos.y / cellPx).toInt().coerceIn(0, gridRows - 1)
        touchedCells.add(row * gridCols + col)
    }

    Box(Modifier.fillMaxSize().background(BgDark)) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 140.dp)
                .background(Color(0xFF050810))
                // Lấy kích thước canvas thực tế một lần khi layout xong
                .onSizeChanged { canvasSize = it }
                .pointerInput(Unit) {
                    while (true) {
                        val event = awaitPointerEventScope {
                            awaitPointerEvent(PointerEventPass.Initial)
                        }
                        event.changes.forEach { change ->
                            val id = change.id.value
                            when {
                                change.changedToDown() -> {
                                    val stroke = mutableStateListOf<Offset>()
                                    stroke.add(change.position)
                                    activeStrokes[id] = stroke
                                    markCell(change.position)
                                }
                                change.pressed && activeStrokes.containsKey(id) -> {
                                    activeStrokes[id]!!.add(change.position)
                                    markCell(change.position)
                                    change.consume()
                                }
                                change.changedToUp() -> {
                                    activeStrokes.remove(id)?.let {
                                        if (it.size > 1) finishedStrokes.add(it.toList())
                                    }
                                }
                            }
                        }
                    }
                }
        ) {
            val sw = 18.dp.toPx()
            val style = Stroke(sw, cap = StrokeCap.Round, join = StrokeJoin.Round)

            finishedStrokes.forEach { pts ->
                if (pts.size < 2) return@forEach
                drawPath(Path().apply {
                    moveTo(pts[0].x, pts[0].y); pts.drop(1).forEach { lineTo(it.x, it.y) }
                }, color = Cyan.copy(alpha = 0.7f), style = style)
            }
            activeStrokes.values.forEach { pts ->
                if (pts.size < 2) return@forEach
                drawPath(Path().apply {
                    moveTo(pts[0].x, pts[0].y); pts.drop(1).forEach { lineTo(it.x, it.y) }
                }, color = Cyan, style = style)
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .background(CardBg)
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (isDone) {
                Text("✅ Hoàn thành! Màn hình cảm ứng tốt",
                    fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Green)
            } else {
                Text("Vẽ kín toàn bộ màn hình – dùng nhiều ngón tay cùng lúc",
                    fontSize = 12.sp, color = TxtSecond, textAlign = TextAlign.Center)
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { coverage },
                    modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                    color = Cyan, trackColor = SurfaceBg
                )
                Spacer(Modifier.height(4.dp))
                Text("${(coverage * 100).toInt()}%  •  ${touchedCells.size}/${totalCells} ô",
                    fontSize = 12.sp, color = Cyan)
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = {
                        finishedStrokes.clear(); activeStrokes.clear(); touchedCells.clear()
                    },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TxtSecond),
                    border = ButtonDefaults.outlinedButtonBorder(enabled = true)
                ) { Text("Xoá & Làm lại") }
                Button(onClick = onBack,
                    colors = ButtonDefaults.buttonColors(containerColor = Blue)
                ) { Text("← Quay lại") }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════
// 2. GRID TEST
// ═══════════════════════════════════════════════════════════════════
private const val GRID_COLS = 9
private const val GRID_ROWS = 16

@Composable
fun GridTestScreen(onBack: () -> Unit) {
    val grid = remember {
        Array(GRID_ROWS) { BooleanArray(GRID_COLS) { false } }
            .let { mutableStateListOf(*it) }
    }
    var touchedCount by remember { mutableIntStateOf(0) }
    val totalCells = GRID_COLS * GRID_ROWS
    val isDone = touchedCount >= totalCells

    fun markCell(x: Float, y: Float, canvasW: Float, canvasH: Float) {
        val col = (x / canvasW * GRID_COLS).toInt().coerceIn(0, GRID_COLS - 1)
        val row = (y / canvasH * GRID_ROWS).toInt().coerceIn(0, GRID_ROWS - 1)
        if (!grid[row][col]) {
            grid[row] = grid[row].copyOf().also { it[col] = true }
            touchedCount++
        }
    }

    Box(Modifier.fillMaxSize().background(BgDark)) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 130.dp)
                .pointerInput(Unit) {
                    while (true) {
                        val event = awaitPointerEventScope {
                            awaitPointerEvent(PointerEventPass.Initial)
                        }
                        event.changes.forEach { change ->
                            if (change.pressed) {
                                markCell(change.position.x, change.position.y,
                                    size.width.toFloat(), size.height.toFloat())
                                change.consume()
                            }
                        }
                    }
                }
        ) {
            val cellW = size.width  / GRID_COLS
            val cellH = size.height / GRID_ROWS
            for (row in 0 until GRID_ROWS) {
                for (col in 0 until GRID_COLS) {
                    val left = col * cellW
                    val top  = row * cellH
                    drawRect(
                        color   = if (grid[row][col]) Green.copy(alpha = 0.55f) else Color(0xFF0D1525),
                        topLeft = Offset(left + 1f, top + 1f),
                        size    = GeoSize(cellW - 2f, cellH - 2f)
                    )
                    drawRect(
                        color   = TxtSecond.copy(alpha = 0.15f),
                        topLeft = Offset(left, top),
                        size    = GeoSize(cellW, cellH),
                        style   = Stroke(1f)
                    )
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .background(CardBg)
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (isDone) {
                Text("✅ $totalCells/$totalCells ô – Cảm ứng toàn màn hình OK",
                    fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Green)
            } else {
                Text("Vuốt ngón tay qua tất cả các ô",
                    fontSize = 12.sp, color = TxtSecond)
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { touchedCount.toFloat() / totalCells },
                    modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                    color = Green, trackColor = SurfaceBg
                )
                Spacer(Modifier.height(4.dp))
                Text("$touchedCount / $totalCells ô", fontSize = 12.sp, color = Green)
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = { for (r in 0 until GRID_ROWS) grid[r] = BooleanArray(GRID_COLS); touchedCount = 0 },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TxtSecond),
                    border = ButtonDefaults.outlinedButtonBorder(enabled = true)
                ) { Text("Xoá & Làm lại") }
                Button(onClick = onBack,
                    colors = ButtonDefaults.buttonColors(containerColor = Blue)
                ) { Text("← Quay lại") }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════
// 3. MULTI-TOUCH TEST
// ═══════════════════════════════════════════════════════════════════
private data class TouchPoint(val id: Long, val x: Float, val y: Float)

@Composable
fun MultiTouchTestScreen(onBack: () -> Unit) {
    val points   = remember { mutableStateMapOf<Long, TouchPoint>() }
    val maxSeen  = remember { mutableIntStateOf(0) }
    val fingerColors = listOf(Cyan, Green, Orange, Blue, Red,
        Color(0xFFE040FB), Color(0xFFFFEB3B), Color(0xFF00BFA5))

    Box(Modifier.fillMaxSize().background(BgDark)) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 130.dp)
                .pointerInput(Unit) {
                    while (true) {
                        val event = awaitPointerEventScope {
                            awaitPointerEvent(PointerEventPass.Initial)
                        }
                        event.changes.forEach { change ->
                            val id = change.id.value
                            when {
                                // Ngón đặt xuống hoặc di chuyển → cập nhật vị trí
                                change.pressed -> {
                                    points[id] = TouchPoint(id, change.position.x, change.position.y)
                                    change.consume()
                                }
                                // Ngón nhả → xoá ngay lập tức, không chờ frame tiếp theo
                                change.changedToUp() -> {
                                    points.remove(id)
                                    change.consume()
                                }
                            }
                        }
                        if (points.size > maxSeen.intValue) maxSeen.intValue = points.size
                    }
                }
        ) {
            val density   = LocalDensity.current
            val pointList = points.values.toList()

            // Tất cả vòng tròn vẽ trên một Canvas duy nhất → không bị overdraw
            Canvas(Modifier.fillMaxSize()) {
                pointList.forEachIndexed { index, tp ->
                    val color = fingerColors[index % fingerColors.size]
                    drawCircle(color = color.copy(alpha = 0.2f), radius = 65.dp.toPx(), center = Offset(tp.x, tp.y))
                    drawCircle(color = color, radius = 28.dp.toPx(), center = Offset(tp.x, tp.y))
                    drawCircle(color = color, radius = 30.dp.toPx(), center = Offset(tp.x, tp.y),
                        style = Stroke(2.dp.toPx()))
                }
            }

            // Label số ngón (dùng Box offset, convert px→dp với density thực tế)
            pointList.forEachIndexed { index, tp ->
                with(density) {
                    Box(
                        modifier = Modifier
                            .offset(x = tp.x.toDp() - 14.dp, y = tp.y.toDp() - 14.dp)
                            .size(28.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("${index + 1}", fontSize = 14.sp,
                            fontWeight = FontWeight.ExtraBold, color = Color.White)
                    }
                }
            }

            if (points.isEmpty()) {
                Column(
                    Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text("👆", fontSize = 48.sp)
                    Spacer(Modifier.height(16.dp))
                    Text("Đặt nhiều ngón tay lên màn hình",
                        fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = TxtPrimary)
                    Text("Tối đa ${fingerColors.size} ngón cùng lúc",
                        fontSize = 12.sp, color = TxtSecond)
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .background(CardBg)
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                StatBox("Đang chạm", "${points.size}", Cyan)
                StatBox("Cao nhất",  "${maxSeen.intValue}", Green)
                StatBox("Hỗ trợ",   "≥5", Blue)
            }
            Spacer(Modifier.height(8.dp))
            val msg = when {
                maxSeen.intValue == 0 -> "Chưa test"
                maxSeen.intValue >= 5 -> "✅ Đa điểm tốt (${maxSeen.intValue} ngón)"
                maxSeen.intValue >= 2 -> "⚠️ Chỉ nhận ${maxSeen.intValue} điểm – có thể bị ảnh hưởng bởi nước"
                else                  -> "❌ 1 điểm – cảm ứng đa điểm hỏng"
            }
            Text(msg, fontSize = 12.sp, color = when {
                maxSeen.intValue >= 5 -> Green
                maxSeen.intValue >= 2 -> Orange
                else                  -> Red
            }, textAlign = TextAlign.Center)
            Spacer(Modifier.height(12.dp))
            Button(onClick = onBack,
                colors = ButtonDefaults.buttonColors(containerColor = Blue),
                modifier = Modifier.fillMaxWidth()
            ) { Text("← Quay lại") }
        }
    }
}

@Composable
private fun StatBox(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, color = color)
        Text(label, fontSize = 10.sp, color = TxtSecond)
    }
}

// ═══════════════════════════════════════════════════════════════════
// 4. GHOST TOUCH TEST
// ═══════════════════════════════════════════════════════════════════
private enum class GhostTestState { IDLE, COUNTING, GHOST_DETECTED, PASS }

@Composable
fun GhostTouchTestScreen(onBack: () -> Unit) {
    var testState     by remember { mutableStateOf(GhostTestState.IDLE) }
    var countdown     by remember { mutableIntStateOf(15) }
    var ghostCount    by remember { mutableIntStateOf(0) }
    val ghostPositions = remember { mutableStateListOf<Offset>() }

    LaunchedEffect(testState) {
        if (testState == GhostTestState.COUNTING) {
            countdown = 15
            while (countdown > 0 && testState == GhostTestState.COUNTING) {
                delay(1000L)
                countdown--
            }
            if (testState == GhostTestState.COUNTING) testState = GhostTestState.PASS
        }
    }

    Box(Modifier.fillMaxSize().background(BgDark)) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 160.dp)
                .pointerInput(testState) {
                    if (testState != GhostTestState.COUNTING) return@pointerInput
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        ghostCount++
                        ghostPositions.add(down.position)
                        testState = GhostTestState.GHOST_DETECTED
                    }
                }
        ) {
            Canvas(Modifier.fillMaxSize()) {
                ghostPositions.forEach { pos ->
                    drawCircle(Red.copy(alpha = 0.3f), 40.dp.toPx(), pos)
                    drawCircle(Red, 12.dp.toPx(), pos)
                    val r = 20.dp.toPx()
                    drawLine(Red, pos - Offset(r, r), pos + Offset(r, r), 3.dp.toPx(), StrokeCap.Round)
                    drawLine(Red, pos + Offset(r, -r), pos - Offset(r, -r), 3.dp.toPx(), StrokeCap.Round)
                }
            }

            Column(
                Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                when (testState) {
                    GhostTestState.IDLE -> {
                        Text("👻", fontSize = 56.sp, textAlign = TextAlign.Center)
                        Spacer(Modifier.height(16.dp))
                        Text("Đặt điện thoại xuống bàn",
                            fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TxtPrimary,
                            textAlign = TextAlign.Center)
                        Spacer(Modifier.height(8.dp))
                        Text("Không chạm vào màn hình trong 15 giây.\nNếu app phát hiện chạm → màn hình bị loạn.",
                            fontSize = 13.sp, color = TxtSecond, textAlign = TextAlign.Center,
                            lineHeight = 20.sp, modifier = Modifier.padding(horizontal = 24.dp))
                    }
                    GhostTestState.COUNTING -> {
                        CountdownCircle(countdown)
                        Spacer(Modifier.height(20.dp))
                        Text("Đang theo dõi…", fontSize = 14.sp, color = TxtSecond)
                        Text("ĐỪNG chạm vào màn hình!", fontSize = 13.sp,
                            fontWeight = FontWeight.Bold, color = Orange)
                    }
                    GhostTestState.GHOST_DETECTED -> {
                        Text("❌", fontSize = 56.sp, textAlign = TextAlign.Center)
                        Spacer(Modifier.height(12.dp))
                        Text("Phát hiện loạn cảm ứng!",
                            fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = Red,
                            textAlign = TextAlign.Center)
                        Spacer(Modifier.height(8.dp))
                        Text("Màn hình tự đăng ký $ghostCount lần chạm.\nCó thể do nước hoặc bụi bẩn gây đoản mạch.",
                            fontSize = 13.sp, color = TxtSecond, textAlign = TextAlign.Center,
                            lineHeight = 20.sp, modifier = Modifier.padding(horizontal = 24.dp))
                    }
                    GhostTestState.PASS -> {
                        Text("✅", fontSize = 56.sp, textAlign = TextAlign.Center)
                        Spacer(Modifier.height(12.dp))
                        Text("Không phát hiện loạn cảm ứng",
                            fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Green,
                            textAlign = TextAlign.Center)
                        Spacer(Modifier.height(8.dp))
                        Text("Màn hình không tự phát sinh sự kiện cảm ứng.\nCảm ứng đang hoạt động bình thường.",
                            fontSize = 13.sp, color = TxtSecond, textAlign = TextAlign.Center,
                            lineHeight = 20.sp, modifier = Modifier.padding(horizontal = 24.dp))
                    }
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .background(CardBg)
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            when (testState) {
                GhostTestState.IDLE -> Button(
                    onClick = { testState = GhostTestState.COUNTING },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Orange)
                ) { Text("Bắt đầu Test (15 giây)") }

                GhostTestState.COUNTING -> OutlinedButton(
                    onClick = { testState = GhostTestState.IDLE; ghostPositions.clear(); ghostCount = 0 },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TxtSecond),
                    border = ButtonDefaults.outlinedButtonBorder(enabled = true)
                ) { Text("Huỷ") }

                GhostTestState.GHOST_DETECTED, GhostTestState.PASS -> Button(
                    onClick = { testState = GhostTestState.IDLE; ghostPositions.clear(); ghostCount = 0 },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Blue)
                ) { Text("Test lại") }
            }
            OutlinedButton(
                onClick = onBack,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = TxtSecond),
                border = ButtonDefaults.outlinedButtonBorder(enabled = true)
            ) { Text("← Quay lại danh sách") }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════
// 5. TEST LOA / MIC
// ═══════════════════════════════════════════════════════════════════
private enum class MicTestState { INSTRUCTION, RECORDING, PLAYING, RESULT }

@Composable
fun MicSpeakerTestScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var state       by remember { mutableStateOf(MicTestState.INSTRUCTION) }
    var countdown   by remember { mutableIntStateOf(5) }
    // null = chưa đánh giá, true = rõ, false = rè/nhỏ
    var userResult  by remember { mutableStateOf<Boolean?>(null) }

    val outputFile = remember { java.io.File(context.cacheDir, "mic_test.3gp") }
    val recorderRef = remember { mutableStateOf<MediaRecorder?>(null) }
    val playerRef   = remember { mutableStateOf<MediaPlayer?>(null) }

    // Giải phóng tài nguyên khi rời màn hình
    DisposableEffect(Unit) {
        onDispose {
            recorderRef.value?.runCatching { stop(); release() }
            playerRef.value?.runCatching { stop(); release() }
            outputFile.delete()
        }
    }

    // Xin quyền RECORD_AUDIO
    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) state = MicTestState.RECORDING }

    fun startRecordingIfAllowed() {
        val perm = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
        if (perm == PackageManager.PERMISSION_GRANTED) {
            state = MicTestState.RECORDING
        } else {
            permLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    // Vòng đời ghi âm → phát lại
    LaunchedEffect(state) {
        when (state) {
            MicTestState.RECORDING -> {
                countdown = 5
                // Khởi tạo và bắt đầu ghi âm
                val rec = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    MediaRecorder(context)
                } else {
                    @Suppress("DEPRECATION") MediaRecorder()
                }
                rec.apply {
                    setAudioSource(MediaRecorder.AudioSource.MIC)
                    setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP)
                    setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)
                    setOutputFile(outputFile.absolutePath)
                    prepare()
                    start()
                }
                recorderRef.value = rec

                // Đếm ngược 5 giây
                repeat(5) {
                    delay(1000L)
                    countdown--
                }

                // Dừng ghi âm
                rec.runCatching { stop(); release() }
                recorderRef.value = null
                state = MicTestState.PLAYING
            }

            MicTestState.PLAYING -> {
                // Đẩy âm lượng lên ~75%
                val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                val maxVol = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                am.setStreamVolume(AudioManager.STREAM_MUSIC, (maxVol * 0.75f).toInt(), 0)

                // Phát lại
                val mp = MediaPlayer().apply {
                    setDataSource(outputFile.absolutePath)
                    prepare()
                    start()
                }
                playerRef.value = mp

                // Chờ phát xong (poll mỗi 100ms)
                while (mp.isPlaying) delay(100L)
                mp.runCatching { release() }
                playerRef.value = null

                if (state == MicTestState.PLAYING) state = MicTestState.RESULT
            }

            else -> {}
        }
    }

    Box(
        Modifier.fillMaxSize().background(BgDark),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Back header
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color(0xFFE040FB),
                    modifier = Modifier.clickable { onBack() })
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("TEST LOA / MIC", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFFE040FB), letterSpacing = 2.sp)
                    Text("Kiểm tra loa & mic sau khi dính nước",
                        fontSize = 12.sp, color = TxtSecond)
                }
            }

            Spacer(Modifier.weight(1f))

            when (state) {
                MicTestState.INSTRUCTION -> {
                    Text("🎙️", fontSize = 64.sp, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(8.dp))
                    Text("Cách hoạt động",
                        fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TxtPrimary,
                        textAlign = TextAlign.Center)
                    Spacer(Modifier.height(4.dp))
                    Card(
                        colors = CardDefaults.cardColors(containerColor = CardBg),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            listOf(
                                "1️⃣  Bấm nút bên dưới để bắt đầu ghi âm",
                                "2️⃣  Nói to vào micro trong 5 giây",
                                "3️⃣  App tự động phát lại để bạn nghe",
                                "4️⃣  Đánh giá chất lượng âm thanh"
                            ).forEach { step ->
                                Text(step, fontSize = 13.sp, color = TxtSecond, lineHeight = 20.sp)
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = { startRecordingIfAllowed() },
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE040FB)),
                        shape = RoundedCornerShape(14.dp)
                    ) { Text("Bắt đầu ghi âm", fontSize = 15.sp, fontWeight = FontWeight.Bold) }
                }

                MicTestState.RECORDING -> {
                    // Vòng tròn nhấp nháy đỏ
                    val pulse = rememberInfiniteTransition(label = "rec")
                    val scale by pulse.animateFloat(
                        initialValue = 0.85f, targetValue = 1.1f,
                        animationSpec = infiniteRepeatable(tween(600, easing = EaseInOutSine), RepeatMode.Reverse),
                        label = "recScale"
                    )
                    Box(
                        Modifier.size((110 * scale).dp).clip(CircleShape)
                            .background(Red.copy(alpha = 0.2f))
                            .border(2.dp, Red, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(Modifier.size(60.dp).clip(CircleShape).background(Red))
                    }
                    Spacer(Modifier.height(16.dp))
                    Text("● ĐANG GHI ÂM", fontSize = 16.sp,
                        fontWeight = FontWeight.ExtraBold, color = Red, letterSpacing = 2.sp)
                    Text("Hãy nói to vào micro…",
                        fontSize = 13.sp, color = TxtSecond)
                    Spacer(Modifier.height(8.dp))
                    // Đồng hồ đếm ngược 5 giây
                    RecordCountdownCircle(countdown)
                }

                MicTestState.PLAYING -> {
                    val waveAnim = rememberInfiniteTransition(label = "play")
                    val wavePhase by waveAnim.animateFloat(
                        initialValue = 0f, targetValue = (2 * Math.PI).toFloat(),
                        animationSpec = infiniteRepeatable(tween(800, easing = LinearEasing)), label = "wph"
                    )
                    Text("🔊", fontSize = 64.sp, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(8.dp))
                    Text("ĐANG PHÁT LẠI", fontSize = 16.sp,
                        fontWeight = FontWeight.ExtraBold, color = Green, letterSpacing = 2.sp)
                    Text("Lắng nghe chất lượng âm thanh…",
                        fontSize = 13.sp, color = TxtSecond)
                    Spacer(Modifier.height(12.dp))
                    // Mini waveform
                    Canvas(Modifier.fillMaxWidth().height(60.dp)) {
                        val path = Path().also { p ->
                            for (i in 0..200) {
                                val x = size.width * i / 200f
                                val y = size.height / 2 + size.height * 0.3f *
                                        kotlin.math.sin(3f * 2 * Math.PI.toFloat() * i / 200f + wavePhase)
                                if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
                            }
                        }
                        drawPath(path, color = Green, style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round))
                    }
                }

                MicTestState.RESULT -> {
                    if (userResult == null) {
                        Text("Âm thanh nghe như thế nào?",
                            fontSize = 20.sp, fontWeight = FontWeight.Bold, color = TxtPrimary,
                            textAlign = TextAlign.Center)
                        Spacer(Modifier.height(24.dp))
                        // Nút "Nghe rất rõ"
                        Button(
                            onClick = { userResult = true },
                            modifier = Modifier.fillMaxWidth().height(56.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Green),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Text("✅  Nghe rất rõ", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.height(12.dp))
                        // Nút "Âm thanh bị rè/nhỏ"
                        OutlinedButton(
                            onClick = { userResult = false },
                            modifier = Modifier.fillMaxWidth().height(56.dp),
                            shape = RoundedCornerShape(14.dp),
                            border = ButtonDefaults.outlinedButtonBorder(enabled = true),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Orange)
                        ) {
                            Text("⚠️  Âm thanh bị rè / nhỏ", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        }
                    } else if (userResult == true) {
                        Text("✅", fontSize = 64.sp, textAlign = TextAlign.Center)
                        Spacer(Modifier.height(12.dp))
                        Text("Phần cứng an toàn!", fontSize = 22.sp,
                            fontWeight = FontWeight.ExtraBold, color = Green, textAlign = TextAlign.Center)
                        Spacer(Modifier.height(8.dp))
                        Text("Loa và micro hoạt động tốt,\nkhông phát hiện dấu hiệu đọng nước.",
                            fontSize = 14.sp, color = TxtSecond, textAlign = TextAlign.Center, lineHeight = 22.sp)
                    } else {
                        Text("⚠️", fontSize = 64.sp, textAlign = TextAlign.Center)
                        Spacer(Modifier.height(12.dp))
                        Text("Cần làm sạch thêm", fontSize = 22.sp,
                            fontWeight = FontWeight.ExtraBold, color = Orange, textAlign = TextAlign.Center)
                        Spacer(Modifier.height(8.dp))
                        Text("Âm thanh bị ảnh hưởng — có thể loa hoặc mic\nđang bị đọng nước. Hãy quay lại màn hình\nchính và chạy thêm chu kỳ làm sạch.",
                            fontSize = 14.sp, color = TxtSecond, textAlign = TextAlign.Center, lineHeight = 22.sp)
                    }
                }
            }

            Spacer(Modifier.weight(1f))

            // Nút dưới cùng
            when {
                state == MicTestState.RESULT && userResult != null ->
                    Button(
                        onClick = { state = MicTestState.INSTRUCTION; userResult = null },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Blue)
                    ) { Text("Test lại") }
                state != MicTestState.RECORDING && state != MicTestState.PLAYING ->
                    OutlinedButton(
                        onClick = onBack, modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = TxtSecond),
                        border = ButtonDefaults.outlinedButtonBorder(enabled = true)
                    ) { Text("← Quay lại") }
            }
        }
    }
}

@Composable
private fun RecordCountdownCircle(seconds: Int) {
    val progress by animateFloatAsState(seconds / 5f, tween(900), label = "rec_cd")
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(110.dp)) {
        Canvas(Modifier.fillMaxSize()) {
            val sw = 8.dp.toPx()
            val r  = size.minDimension / 2 - sw
            drawCircle(SurfaceBg, r, style = Stroke(sw))
            drawArc(Red, -90f, 360f * progress, false, style = Stroke(sw, cap = StrokeCap.Round))
        }
        Text("$seconds", fontSize = 38.sp, fontWeight = FontWeight.ExtraBold, color = Red)
    }
}

// ═══════════════════════════════════════════════════════════════════
// 6. TEST CẢM BIẾN TIỆM CẬN (PROXIMITY SENSOR)
// ═══════════════════════════════════════════════════════════════════
@Composable
fun ProximityTestScreen(onBack: () -> Unit) {
    val context = LocalContext.current

    var isNear       by remember { mutableStateOf(false) }
    var cycleCount   by remember { mutableIntStateOf(0) }    // số lần che+mở thành công
    var showSuccess  by remember { mutableStateOf(false) }
    var lastWasNear  by remember { mutableStateOf(false) }
    var sensorAvail  by remember { mutableStateOf(true) }

    val REQUIRED_CYCLES = 3

    // Vibrator để haptic khi cảm biến trigger
    val vibrator = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
    }

    fun hapticTap() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(60L, 200))
        } else {
            @Suppress("DEPRECATION") vibrator.vibrate(60L)
        }
    }

    // Đăng ký lắng nghe cảm biến tiệm cận
    DisposableEffect(Unit) {
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val proxSensor = sm.getDefaultSensor(Sensor.TYPE_PROXIMITY)

        if (proxSensor == null) {
            sensorAvail = false
            return@DisposableEffect onDispose {}
        }

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val near = event.values[0] < (proxSensor.maximumRange * 0.5f)
                if (near == isNear) return   // không đổi trạng thái → bỏ qua

                isNear = near
                if (near) {
                    // Vừa che → haptic feedback
                    hapticTap()
                    lastWasNear = true
                } else if (lastWasNear) {
                    // Vừa mở sau khi đã che → đếm 1 chu kỳ
                    lastWasNear = false
                    if (cycleCount < REQUIRED_CYCLES) {
                        cycleCount++
                        if (cycleCount >= REQUIRED_CYCLES) showSuccess = true
                    }
                }
            }
            override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {}
        }

        sm.registerListener(listener, proxSensor, SensorManager.SENSOR_DELAY_UI)
        onDispose { sm.unregisterListener(listener) }
    }

    val bgColor by animateColorAsState(
        targetValue = if (isNear) Green.copy(alpha = 0.12f) else BgDark,
        animationSpec = tween(200), label = "proxBg"
    )

    Box(Modifier.fillMaxSize().background(bgColor)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back",
                    tint = Color(0xFFFFEB3B),
                    modifier = Modifier.clickable { onBack() })
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("TEST CẢM BIẾN TIỆM CẬN", fontSize = 16.sp,
                        fontWeight = FontWeight.ExtraBold, color = Color(0xFFFFEB3B), letterSpacing = 1.sp)
                    Text("Proximity Sensor", fontSize = 12.sp, color = TxtSecond)
                }
            }

            if (!sensorAvail) {
                Spacer(Modifier.weight(1f))
                Text("❌", fontSize = 56.sp, textAlign = TextAlign.Center)
                Text("Thiết bị không có cảm biến tiệm cận",
                    fontSize = 16.sp, color = Red, textAlign = TextAlign.Center)
                Spacer(Modifier.weight(1f))
            } else {
                // Hướng dẫn vị trí cảm biến
                Card(
                    colors = CardDefaults.cardColors(containerColor = CardBg),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        Modifier.padding(18.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Minh hoạ vị trí cảm biến (hình chữ nhật đại diện điện thoại)
                        Box(
                            Modifier.fillMaxWidth().height(80.dp),
                            contentAlignment = Alignment.TopCenter
                        ) {
                            Canvas(Modifier.size(width = 120.dp, height = 60.dp)) {
                                // Thân điện thoại
                                drawRoundRect(
                                    color = Color(0xFF2A3A50),
                                    size = this.size,
                                    cornerRadius = CornerRadius(12.dp.toPx())
                                )
                                // Cảm biến (chấm nhỏ ở trên)
                                drawCircle(
                                    color = if (isNear) Green else Color(0xFF607D8B),
                                    radius = 6.dp.toPx(),
                                    center = Offset(this.size.width * 0.35f, 14.dp.toPx())
                                )
                                // Loa thoại (đường ngang)
                                drawRoundRect(
                                    color = Color(0xFF607D8B),
                                    topLeft = Offset(this.size.width * 0.42f, 10.dp.toPx()),
                                    size = GeoSize(this.size.width * 0.25f, 8.dp.toPx()),
                                    cornerRadius = CornerRadius(4.dp.toPx())
                                )
                            }
                        }
                        Text("Che phần đỉnh điện thoại (nơi có loa thoại)\nbằng bàn tay, rồi mở ra",
                            fontSize = 13.sp, color = TxtSecond, textAlign = TextAlign.Center, lineHeight = 20.sp)
                    }
                }

                Spacer(Modifier.weight(1f))

                // Trạng thái real-time
                val statusColor = if (isNear) Green else TxtSecond
                val statusIcon  = if (isNear) "💡" else "🔦"
                val statusText  = if (isNear) "Đã nhận diện vật cản!" else "Đang chờ…"

                Text(statusIcon, fontSize = 72.sp, textAlign = TextAlign.Center)
                Text(statusText, fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold, color = statusColor,
                    textAlign = TextAlign.Center)

                // Tiến trình chu kỳ
                Spacer(Modifier.height(8.dp))
                Text("Chu kỳ thành công: $cycleCount / $REQUIRED_CYCLES",
                    fontSize = 13.sp, color = TxtSecond)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    repeat(REQUIRED_CYCLES) { i ->
                        Box(
                            Modifier.size(16.dp).clip(CircleShape)
                                .background(if (i < cycleCount) Green else SurfaceBg)
                        )
                    }
                }

                Spacer(Modifier.weight(1f))
            }

            OutlinedButton(
                onClick = onBack, modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = TxtSecond),
                border = ButtonDefaults.outlinedButtonBorder(enabled = true)
            ) { Text("← Quay lại") }
        }

        // Popup khi hoàn thành 3 chu kỳ
        if (showSuccess) {
            Box(
                Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f)),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = CardBg),
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier.padding(32.dp)
                ) {
                    Column(
                        Modifier.padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("✅", fontSize = 56.sp)
                        Text("Cảm biến hoạt động hoàn hảo!",
                            fontSize = 18.sp, fontWeight = FontWeight.ExtraBold,
                            color = Green, textAlign = TextAlign.Center)
                        Text("Cảm biến tiệm cận của bạn không bị đọng nước bên trong kính.",
                            fontSize = 13.sp, color = TxtSecond, textAlign = TextAlign.Center, lineHeight = 20.sp)
                        Spacer(Modifier.height(4.dp))
                        Button(
                            onClick = { showSuccess = false; cycleCount = 0; lastWasNear = false },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = Blue)
                        ) { Text("Test lại") }
                        OutlinedButton(
                            onClick = onBack, modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = TxtSecond),
                            border = ButtonDefaults.outlinedButtonBorder(enabled = true)
                        ) { Text("← Quay lại") }
                    }
                }
            }
        }
    }
}

@Composable
private fun CountdownCircle(seconds: Int) {
    val progress by animateFloatAsState(seconds / 15f, tween(800), label = "cd")
    val color by animateColorAsState(
        targetValue = when { seconds > 10 -> Green; seconds > 5 -> Orange; else -> Red },
        animationSpec = tween(400), label = "cdColor"
    )
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(130.dp)) {
        Canvas(Modifier.fillMaxSize()) {
            val sw = 8.dp.toPx()
            val r  = size.minDimension / 2 - sw
            drawCircle(SurfaceBg, r, style = Stroke(sw))
            drawArc(color, -90f, 360f * progress, false, style = Stroke(sw, cap = StrokeCap.Round))
        }
        Text("$seconds", fontSize = 44.sp, fontWeight = FontWeight.ExtraBold, color = color)
    }
}
