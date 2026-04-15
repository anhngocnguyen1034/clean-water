package com.example.clean_water

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.sin

// ─── Màu sắc Dark Mode ────────────────────────────────────────────────────────
private val BackgroundDark = Color(0xFF0A0E1A)
private val SurfaceDark    = Color(0xFF111827)
private val CardDark       = Color(0xFF1A2235)
private val AccentCyan     = Color(0xFF00E5FF)
private val AccentBlue     = Color(0xFF2979FF)
private val AccentGreen    = Color(0xFF00E676)
private val AccentRed      = Color(0xFFFF1744)
private val TextPrimary    = Color(0xFFE8F4FD)
private val TextSecondary  = Color(0xFF8899AA)

// ─── Preset tần số ────────────────────────────────────────────────────────────
data class FrequencyPreset(val hz: Int, val label: String, val description: String)

private val FREQUENCY_PRESETS = listOf(
    FrequencyPreset(165, "165 Hz", "Làm sạch\ncơ bản"),
    FrequencyPreset(432, "432 Hz", "Hài hoà\ntự nhiên"),
    FrequencyPreset(528, "528 Hz", "Tần số\nDNA"),
    FrequencyPreset(741, "741 Hz", "Thanh lọc\ngiải độc")
)

// ─── Tùy chọn thời gian chạy (giây) ─────────────────────────────────────────
private val DURATION_OPTIONS = listOf(15, 20, 30, 45, 60)
private val DURATION_LABELS  = listOf("15s", "20s", "30s", "45s", "60s")

// ─── MainActivity ─────────────────────────────────────────────────────────────
class MainActivity : ComponentActivity() {

    private lateinit var soundManager: SoundManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        soundManager = SoundManager(applicationContext)

        setContent {
            var showPhoneTest by remember { mutableStateOf(false) }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(BackgroundDark)
            ) {
                if (showPhoneTest) {
                    PhoneTestScreen(onBack = { showPhoneTest = false })
                } else {
                    CleanWaterApp(
                        soundManager    = soundManager,
                        onOpenPhoneTest = { showPhoneTest = true }
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        soundManager.release()
    }
}

// ─── Root Composable ──────────────────────────────────────────────────────────
@Composable
fun CleanWaterApp(soundManager: SoundManager, onOpenPhoneTest: () -> Unit = {}) {
    val isRunning by soundManager.isRunning.collectAsStateWithLifecycle()

    var frequencyHz        by remember { mutableIntStateOf(SoundManager.DEFAULT_FREQUENCY_HZ) }
    var vibrationAmplitude by remember { mutableIntStateOf(SoundManager.DEFAULT_VIBRATION_AMPLITUDE) }
    var vibrationMode      by remember { mutableStateOf(VibrationMode.PULSE) }
    var selectedDurationSec by remember { mutableIntStateOf(30) }
    var remainingSeconds   by remember { mutableIntStateOf(30) }
    var selectedPresetIdx  by remember { mutableIntStateOf(0) }

    // ── Đồng hồ đếm ngược ────────────────────────────────────────────────────
    LaunchedEffect(isRunning) {
        if (isRunning) {
            remainingSeconds = selectedDurationSec
            while (remainingSeconds > 0 && isRunning) {
                delay(1000L)
                remainingSeconds--
            }
            if (remainingSeconds == 0) soundManager.stop()
        } else {
            remainingSeconds = selectedDurationSec
        }
    }

    // Áp dụng tần số real-time
    LaunchedEffect(frequencyHz) { soundManager.setFrequency(frequencyHz) }

    // Reset timer khi đổi duration (chỉ khi chưa chạy)
    LaunchedEffect(selectedDurationSec) {
        if (!isRunning) remainingSeconds = selectedDurationSec
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        AppHeader()

        TimerSection(
            isRunning = isRunning,
            remainingSeconds = remainingSeconds,
            totalSeconds = selectedDurationSec,
            onToggle = {
                if (isRunning) {
                    soundManager.stop()
                } else {
                    soundManager.setVibrationMode(vibrationMode)
                    soundManager.setFrequency(frequencyHz)
                    soundManager.setVibrationAmplitude(vibrationAmplitude)
                    soundManager.start()
                }
            }
        )

        FrequencyPresetSection(
            selectedIndex = selectedPresetIdx,
            onPresetSelected = { index ->
                selectedPresetIdx = index
                frequencyHz = FREQUENCY_PRESETS[index].hz
            }
        )

        FrequencySliderSection(
            frequencyHz = frequencyHz,
            onFrequencyChange = { hz ->
                frequencyHz = hz
                val match = FREQUENCY_PRESETS.indexOfFirst { it.hz == hz }
                if (match >= 0) selectedPresetIdx = match
            }
        )

        VibrationSection(
            mode = vibrationMode,
            amplitude = vibrationAmplitude,
            isRunning = isRunning,
            onModeChange = { mode ->
                vibrationMode = mode
                soundManager.applyVibrationMode(mode)
            },
            onAmplitudeChange = { amp ->
                vibrationAmplitude = amp
                soundManager.applyVibrationAmplitude(amp)
            }
        )

        DurationPickerSection(
            selectedDuration = selectedDurationSec,
            isRunning = isRunning,
            onDurationSelected = { sec -> selectedDurationSec = sec }
        )

        // ── Nút mở Test Điện Thoại ────────────────────────────────────────────
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onOpenPhoneTest() },
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1A2235)),
            shape = RoundedCornerShape(16.dp)
        ) {
            Row(
                modifier = Modifier.padding(18.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFFF6D00).copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("🔬", fontSize = 22.sp)
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "Test Màn Hình Điện Thoại",
                        fontSize = 14.sp, fontWeight = FontWeight.Bold,
                        color = Color(0xFFFF6D00)
                    )
                    Text(
                        "Kiểm tra cảm ứng sau khi dính nước",
                        fontSize = 11.sp, color = TextSecondary
                    )
                }
                Text("›", fontSize = 24.sp, color = TextSecondary)
            }
        }
    }
}

// ─── Header ───────────────────────────────────────────────────────────────────
@Composable
private fun AppHeader() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = "💧 CLEAN WATER",
            fontSize = 26.sp,
            fontWeight = FontWeight.ExtraBold,
            color = AccentCyan,
            letterSpacing = 4.sp
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Ultrasonic Water Purifier",
            fontSize = 13.sp,
            color = TextSecondary,
            letterSpacing = 2.sp
        )
    }
}

// ─── Timer + Start/Stop ───────────────────────────────────────────────────────
@Composable
private fun TimerSection(
    isRunning: Boolean,
    remainingSeconds: Int,
    totalSeconds: Int,
    onToggle: () -> Unit
) {
    val progress = if (totalSeconds > 0) remainingSeconds.toFloat() / totalSeconds else 0f

    // Mực nước animate mượt
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(800, easing = LinearEasing),
        label = "waterLevel"
    )

    // Sóng dao động liên tục
    val infiniteTransition = rememberInfiniteTransition(label = "water")
    val wavePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wavePhase"
    )
    // Sóng thứ 2 lệch pha – tạo hiệu ứng sóng sánh
    val wavePhase2 by infiniteTransition.animateFloat(
        initialValue = (PI).toFloat(),
        targetValue = (3 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(2400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wavePhase2"
    )

    val waveAmplitude by animateFloatAsState(
        targetValue = if (isRunning) 1f else 0.35f,
        animationSpec = tween(600),
        label = "waveAmp"
    )

    val buttonColor by animateColorAsState(
        targetValue = if (isRunning) AccentRed else AccentCyan,
        animationSpec = tween(400),
        label = "btnColor"
    )

    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isRunning) 1.05f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "btnPulse"
    )

    // Màu nước theo mực: nhiều nước = xanh đậm, gần cạn = xanh nhạt / vàng
    val waterTopColor by animateColorAsState(
        targetValue = lerp(Color(0xFFFFD166), Color(0xFF00B4D8), animatedProgress),
        animationSpec = tween(800),
        label = "waterTop"
    )
    val waterBotColor by animateColorAsState(
        targetValue = lerp(Color(0xFFEF8C00), Color(0xFF023E8A), animatedProgress),
        animationSpec = tween(800),
        label = "waterBot"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // ── Hình tròn nước ────────────────────────────────────────────────────
        Canvas(Modifier.size(240.dp)) {
            val radius = size.minDimension / 2f
            val cx     = size.width  / 2f
            val cy     = size.height / 2f

            // Nền tối bên trong
            drawCircle(color = Color(0xFF060D1F), radius = radius)

            // Clip toàn bộ nội dung vào vòng tròn
            val circlePath = Path().apply {
                addOval(Rect(cx - radius, cy - radius, cx + radius, cy + radius))
            }
            clipPath(circlePath) {

                val waterY = cy + radius - animatedProgress * radius * 2f
                val waveH  = 10.dp.toPx() * waveAmplitude

                // ── Sóng chính ────────────────────────────────────────────────
                val wave1 = Path()
                var x = 0f
                wave1.moveTo(x, waterY + waveH * sin(wavePhase + x / size.width * 2 * PI.toFloat()))
                while (x <= size.width) {
                    val y = waterY + waveH * sin(wavePhase + x / size.width * 4f * PI.toFloat())
                    wave1.lineTo(x, y)
                    x += 2f
                }
                wave1.lineTo(size.width, size.height)
                wave1.lineTo(0f, size.height)
                wave1.close()

                drawPath(
                    path  = wave1,
                    brush = Brush.verticalGradient(
                        colors    = listOf(waterTopColor.copy(alpha = 0.9f), waterBotColor),
                        startY    = waterY - waveH,
                        endY      = size.height
                    )
                )

                // ── Sóng phủ (lớp trên, lệch pha) ────────────────────────────
                val wave2 = Path()
                x = 0f
                val waveH2 = waveH * 0.55f
                wave2.moveTo(x, waterY + waveH2 * sin(wavePhase2 + x / size.width * 3f * PI.toFloat()))
                while (x <= size.width) {
                    val y = waterY + waveH2 * sin(wavePhase2 + x / size.width * 3f * PI.toFloat())
                    wave2.lineTo(x, y)
                    x += 2f
                }
                wave2.lineTo(size.width, size.height)
                wave2.lineTo(0f, size.height)
                wave2.close()

                drawPath(
                    path  = wave2,
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF90E0EF).copy(alpha = 0.35f),
                            Color(0xFF0096C7).copy(alpha = 0f)
                        ),
                        startY = waterY - waveH2,
                        endY   = waterY + 80f
                    )
                )

                // ── Vệt sáng mặt nước ─────────────────────────────────────────
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.08f),
                            Color.Transparent
                        ),
                        startY = waterY - waveH - 4.dp.toPx(),
                        endY   = waterY + waveH + 10.dp.toPx()
                    )
                )
            }

            // ── Viền ngoài ────────────────────────────────────────────────────
            val sw = 6.dp.toPx()
            drawCircle(
                brush = Brush.sweepGradient(
                    listOf(AccentCyan.copy(alpha = 0.8f), AccentBlue, AccentCyan.copy(alpha = 0.8f))
                ),
                radius = radius - sw / 2f,
                style  = Stroke(sw)
            )

            // ── Bóng sáng trên ────────────────────────────────────────────────
            drawCircle(
                brush = Brush.radialGradient(
                    colors  = listOf(Color.White.copy(alpha = 0.06f), Color.Transparent),
                    center  = Offset(cx - radius * 0.25f, cy - radius * 0.35f),
                    radius  = radius * 0.65f
                ),
                radius = radius
            )
        }

        // ── Thời gian còn lại ─────────────────────────────────────────────────
        Text(
            text = formatTime(remainingSeconds),
            fontSize = 52.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary
        )

        // ── Nút START / STOP ──────────────────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer { scaleX = pulseScale; scaleY = pulseScale }
                .clip(RoundedCornerShape(18.dp))
                .background(
                    Brush.horizontalGradient(
                        listOf(buttonColor, buttonColor.copy(alpha = 0.7f))
                    )
                )
                .clickable { onToggle() }
                .padding(vertical = 18.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (isRunning) "STOP CLEANING" else "START CLEANING",
                fontSize = 17.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White,
                textAlign = TextAlign.Center,
                letterSpacing = 2.sp
            )
        }
    }
}

private fun formatTime(seconds: Int) = "%02d:%02d".format(seconds / 60, seconds % 60)

// ─── Waveform Visualizer ──────────────────────────────────────────────────────
@Composable
private fun WaveformVisualizer(isRunning: Boolean, frequencyHz: Int) {
    val infiniteTransition = rememberInfiniteTransition(label = "wave")
    val wavePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = (2000 * (200f / frequencyHz.coerceAtLeast(1)))
                    .toInt().coerceIn(300, 3000),
                easing = LinearEasing
            ),
            repeatMode = RepeatMode.Restart
        ),
        label = "wavePhase"
    )
    val amplitude by animateFloatAsState(
        targetValue = if (isRunning) 1f else 0f,
        animationSpec = tween(500),
        label = "amp"
    )

    Card(
        modifier = Modifier.fillMaxWidth().height(88.dp),
        colors = CardDefaults.cardColors(containerColor = CardDark),
        shape = RoundedCornerShape(16.dp)
    ) {
        Canvas(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 10.dp)) {
            // Đường tâm
            drawLine(
                color = TextSecondary.copy(alpha = 0.15f),
                start = Offset(0f, size.height / 2),
                end   = Offset(size.width, size.height / 2),
                strokeWidth = 1.dp.toPx()
            )
            if (amplitude == 0f) return@Canvas

            val path = Path()
            val wh   = size.height * 0.38f * amplitude
            val cycles = frequencyHz / 100f   // số chu kỳ hiển thị

            for (i in 0..300) {
                val x = size.width * i / 300f
                val y = size.height / 2 + wh *
                        sin(cycles * 2 * PI.toFloat() * i / 300f + wavePhase)
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }

            val endColor = lerp(AccentCyan, AccentBlue,
                frequencyHz.toFloat() / SoundManager.MAX_FREQUENCY_HZ)
            drawPath(
                path  = path,
                brush = Brush.horizontalGradient(listOf(AccentCyan, endColor, AccentCyan)),
                style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round)
            )
        }
    }
}

// ─── Frequency Preset ─────────────────────────────────────────────────────────
@Composable
private fun FrequencyPresetSection(selectedIndex: Int, onPresetSelected: (Int) -> Unit) {
    SectionCard(title = "FREQUENCY PRESETS") {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FREQUENCY_PRESETS.forEachIndexed { index, preset ->
                val selected = index == selectedIndex
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (selected) AccentCyan.copy(alpha = 0.18f) else SurfaceDark)
                        .clickable { onPresetSelected(index) }
                        .padding(vertical = 10.dp, horizontal = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = preset.label,
                            fontSize = 11.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                            color = if (selected) AccentCyan else TextSecondary
                        )
                        Spacer(Modifier.height(3.dp))
                        Text(
                            text = preset.description,
                            fontSize = 9.sp,
                            color = TextSecondary,
                            textAlign = TextAlign.Center,
                            lineHeight = 12.sp
                        )
                    }
                }
            }
        }
    }
}

// ─── Frequency Slider ─────────────────────────────────────────────────────────
@Composable
private fun FrequencySliderSection(frequencyHz: Int, onFrequencyChange: (Int) -> Unit) {
    SectionCard(title = "FREQUENCY") {
        Text(
            text = "$frequencyHz Hz",
            fontSize = 42.sp,
            fontWeight = FontWeight.ExtraBold,
            color = AccentCyan,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(6.dp))
        Slider(
            value = frequencyHz.toFloat(),
            onValueChange = { onFrequencyChange(it.toInt()) },
            valueRange = SoundManager.MIN_FREQUENCY_HZ.toFloat()..SoundManager.MAX_FREQUENCY_HZ.toFloat(),
            steps = (SoundManager.MAX_FREQUENCY_HZ - SoundManager.MIN_FREQUENCY_HZ) / 10 - 1,
            colors = SliderDefaults.colors(
                thumbColor = AccentCyan,
                activeTrackColor = AccentCyan,
                inactiveTrackColor = SurfaceDark
            ),
            modifier = Modifier.fillMaxWidth()
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("${SoundManager.MIN_FREQUENCY_HZ} Hz", fontSize = 11.sp, color = TextSecondary)
            Text("${SoundManager.MAX_FREQUENCY_HZ} Hz", fontSize = 11.sp, color = TextSecondary)
        }
    }
}

// ─── Vibration Section (3 chế độ: OFF / Liên tục / Ngắt quãng) ──────────────
@Composable
private fun VibrationSection(
    mode: VibrationMode,
    amplitude: Int,
    isRunning: Boolean,
    onModeChange: (VibrationMode) -> Unit,
    onAmplitudeChange: (Int) -> Unit
) {
    SectionCard(title = "VIBRATION") {
        // ── Chọn chế độ ──────────────────────────────────────────────────────
        val modes = listOf(
            Triple(VibrationMode.OFF,        "TẮT",         "📵"),
            Triple(VibrationMode.CONTINUOUS,  "LIÊN TỤC",   "〰️"),
            Triple(VibrationMode.PULSE,       "NGẮT QUÃNG", "⚡")
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            modes.forEach { (m, label, icon) ->
                val selected = m == mode
                val modeColor = when (m) {
                    VibrationMode.OFF        -> TextSecondary
                    VibrationMode.CONTINUOUS  -> AccentGreen
                    VibrationMode.PULSE       -> AccentCyan
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (selected) modeColor.copy(alpha = 0.18f) else SurfaceDark)
                        .clickable { onModeChange(m) }
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(icon, fontSize = 18.sp)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = label,
                            fontSize = 10.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                            color = if (selected) modeColor else TextSecondary
                        )
                    }
                }
            }
        }

        // ── Slider cường độ (ẩn khi tắt) ─────────────────────────────────────
        if (mode != VibrationMode.OFF) {
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "$amplitude",
                    fontSize = 24.sp, fontWeight = FontWeight.Bold,
                    color = AccentGreen,
                    modifier = Modifier.width(52.dp)
                )
                Slider(
                    value = amplitude.toFloat(),
                    onValueChange = { onAmplitudeChange(it.toInt()) },
                    valueRange = 1f..255f,
                    colors = SliderDefaults.colors(
                        thumbColor = AccentGreen,
                        activeTrackColor = AccentGreen,
                        inactiveTrackColor = SurfaceDark
                    ),
                    modifier = Modifier.weight(1f)
                )
            }
            if (isRunning) {
                val statusText = when (mode) {
                    VibrationMode.CONTINUOUS -> "● Đang rung liên tục"
                    VibrationMode.PULSE      -> "● Đang rung ngắt quãng (0.5s bật / 0.3s tắt)"
                    VibrationMode.OFF        -> ""
                }
                Text(statusText, fontSize = 11.sp, color = AccentGreen.copy(alpha = 0.8f))
            }
        }
    }
}

// ─── Duration Picker ──────────────────────────────────────────────────────────
@Composable
private fun DurationPickerSection(
    selectedDuration: Int,
    isRunning: Boolean,
    onDurationSelected: (Int) -> Unit
) {
    SectionCard(title = "DURATION") {
        // 2 hàng: hàng 1 = 3 item, hàng 2 = 2 item (căn giữa)
        val rows = listOf(
            DURATION_OPTIONS.zip(DURATION_LABELS).take(3),
            DURATION_OPTIONS.zip(DURATION_LABELS).drop(3)
        )
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            rows.forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    row.forEach { (sec, label) ->
                        val selected = sec == selectedDuration
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (selected) AccentBlue.copy(alpha = 0.28f) else SurfaceDark)
                                .then(if (!isRunning) Modifier.clickable { onDurationSelected(sec) } else Modifier)
                                .padding(vertical = 14.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                fontSize = 14.sp,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                color = if (selected) AccentBlue else TextSecondary
                            )
                        }
                    }
                }
            }
        }
        if (isRunning) {
            Spacer(Modifier.height(4.dp))
            Text("Không thể đổi thời gian khi đang chạy",
                fontSize = 10.sp, color = TextSecondary.copy(alpha = 0.5f))
        }
    }
}

// ─── Card dùng chung ─────────────────────────────────────────────────────────
@Composable
private fun SectionCard(title: String? = null, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = CardDark),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            if (title != null) {
                Text(title, fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                    color = TextSecondary, letterSpacing = 2.sp)
                Spacer(Modifier.height(12.dp))
            }
            content()
        }
    }
}
