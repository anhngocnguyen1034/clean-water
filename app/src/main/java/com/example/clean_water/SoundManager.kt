package com.example.clean_water

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.PI
import kotlin.math.sin

/** Ba chế độ rung */
enum class VibrationMode { OFF, CONTINUOUS, PULSE }

/**
 * SoundManager – quản lý âm thanh (sine wave) và rung.
 *
 * Fix đã áp dụng:
 *  1. setFrequency() có debounce restart rung (≤1 lần/giây) để đảm bảo rung
 *     không bị mất khi kéo slider Hz.
 *  2. CONTINUOUS dùng createWaveform với repeat=1 (loop từ đoạn rung, không có
 *     khoảng lặng giữa các chu kỳ).
 *  3. PULSE áp dụng linear fade-out/fade-in 128 mẫu (~3ms) khi chuyển
 *     âm thanh ↔ im lặng, loại bỏ hoàn toàn tiếng click.
 */
class SoundManager(private val context: Context) {

    // ─── Cấu hình AudioTrack ──────────────────────────────────────────────────
    private val SAMPLE_RATE = 44100
    private val BUFFER_SIZE = AudioTrack.getMinBufferSize(
        SAMPLE_RATE,
        AudioFormat.CHANNEL_OUT_MONO,
        AudioFormat.ENCODING_PCM_16BIT
    ).coerceAtLeast(2048)

    /** Số mẫu dùng để fade in/out khi PULSE chuyển trạng thái (~3ms) */
    private val FADE_SAMPLES = 128

    /** Số mẫu fade-out khi stop() được gọi (~46ms) — tránh tiếng "bụp" */
    private val STOP_FADE_SAMPLES = 2048

    // ─── Trạng thái thread-safe ───────────────────────────────────────────────
    private val _frequencyHz       = AtomicInteger(DEFAULT_FREQUENCY_HZ)
    private val _vibrationAmplitude = AtomicInteger(DEFAULT_VIBRATION_AMPLITUDE)
    private val _vibrationMode     = AtomicReference(VibrationMode.PULSE)
    private val isPlaying          = AtomicBoolean(false)
    private val stopRequested      = AtomicBoolean(false)

    /** Timestamp lần cuối restart rung – dùng cho debounce */
    private val lastVibrationRestartMs = AtomicLong(0L)

    // ─── StateFlow cho UI ─────────────────────────────────────────────────────
    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning

    // ─── AudioTrack & Vibrator ────────────────────────────────────────────────
    private var audioTrack: AudioTrack? = null
    private var audioThread: Thread? = null

    private val vibrator: Vibrator by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager)
                .defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
    }

    // ─── Companion ────────────────────────────────────────────────────────────
    companion object {
        const val MIN_FREQUENCY_HZ          = 100
        const val MAX_FREQUENCY_HZ          = 1000
        const val DEFAULT_FREQUENCY_HZ      = 165
        const val MIN_VIBRATION_AMPLITUDE   = 0
        const val MAX_VIBRATION_AMPLITUDE   = 255
        const val DEFAULT_VIBRATION_AMPLITUDE = 180

        private const val PULSE_ON_MS  = 500L
        private const val PULSE_OFF_MS = 300L
    }

    // ─── API công khai ────────────────────────────────────────────────────────

    /**
     * Cập nhật tần số real-time.
     * FIX 1: Nếu đang phát, đảm bảo rung vẫn hoạt động sau khi đổi Hz
     * (debounce 1 giây để không restart quá nhiều khi kéo slider nhanh).
     */
    fun setFrequency(hz: Int) {
        _frequencyHz.set(hz.coerceIn(MIN_FREQUENCY_HZ, MAX_FREQUENCY_HZ))

        if (isPlaying.get() && _vibrationMode.get() != VibrationMode.OFF) {
            val now  = System.currentTimeMillis()
            val last = lastVibrationRestartMs.get()
            // Chỉ restart rung tối đa 1 lần mỗi giây
            if (now - last > 1000L && lastVibrationRestartMs.compareAndSet(last, now)) {
                stopVibration()
                startVibration()
            }
        }
    }

    fun setVibrationAmplitude(amplitude: Int) {
        _vibrationAmplitude.set(amplitude.coerceIn(MIN_VIBRATION_AMPLITUDE, MAX_VIBRATION_AMPLITUDE))
    }

    fun setVibrationMode(mode: VibrationMode) {
        _vibrationMode.set(mode)
    }

    fun setPulseMode(enabled: Boolean) {
        _vibrationMode.set(if (enabled) VibrationMode.PULSE else VibrationMode.CONTINUOUS)
    }

    fun start() {
        if (isPlaying.get()) return
        stopRequested.set(false)
        isPlaying.set(true)
        _isRunning.value = true
        lastVibrationRestartMs.set(System.currentTimeMillis())

        audioTrack = buildAudioTrack()
        audioTrack?.play()
        audioThread = Thread(::audioLoop, "SoundManager-AudioThread").also { it.start() }
        startVibration()
    }

    fun stop() {
        if (!isPlaying.get()) return
        stopRequested.set(true)
        isPlaying.set(false)
        _isRunning.value = false

        // Chờ audioLoop tự fade-out rồi thoát (tối đa 1.5 giây)
        audioThread?.join(1500)
        audioThread = null
        audioTrack?.stop()
        audioTrack?.release()
        audioTrack = null
        stopVibration()
    }

    fun release() = stop()

    fun applyVibrationAmplitude(amplitude: Int) {
        setVibrationAmplitude(amplitude)
        if (isPlaying.get()) { stopVibration(); startVibration() }
    }

    fun applyVibrationMode(mode: VibrationMode) {
        setVibrationMode(mode)
        if (isPlaying.get()) { stopVibration(); startVibration() }
    }

    // ─── Xây dựng AudioTrack ─────────────────────────────────────────────────

    private fun buildAudioTrack(): AudioTrack {
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()
        val format = AudioFormat.Builder()
            .setSampleRate(SAMPLE_RATE)
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
            .build()
        return AudioTrack(attrs, format, BUFFER_SIZE, AudioTrack.MODE_STREAM,
            AudioManager.AUDIO_SESSION_ID_GENERATE)
    }

    // ─── Vòng lặp sinh âm thanh ───────────────────────────────────────────────

    /**
     * audioLoop – chạy trên background thread.
     *
     * FIX 3: Khi PULSE chuyển âm thanh↔im lặng, áp dụng fade linear
     * FADE_SAMPLES mẫu thay vì cắt đột ngột → loại bỏ tiếng click.
     *
     * Cơ chế fade:
     *  fadeOutRemaining > 0 → đang fade-out (envelope 1→0)
     *  fadeInRemaining  > 0 → đang fade-in  (envelope 0→1)
     */
    private fun audioLoop() {
        val buffer       = ShortArray(BUFFER_SIZE)
        var phase        = 0.0
        var pulseOnTimer = 0L
        var pulseSilent  = false
        var prevSilent   = false
        var fadeOutRemaining = 0
        var fadeInRemaining  = 0
        var stopFadeRemaining = -1   // -1 = chưa bắt đầu fade stop

        while (true) {
            val stopping = stopRequested.get()

            // ── Khi nhận tín hiệu stop, bắt đầu đếm fade-out dừng ────────────
            if (stopping && stopFadeRemaining < 0) {
                stopFadeRemaining = STOP_FADE_SAMPLES
            }

            val freq           = _frequencyHz.get().toDouble()
            val phaseIncrement = 2.0 * PI * freq / SAMPLE_RATE

            // ── Cập nhật trạng thái PULSE ─────────────────────────────────────
            if (!stopping) {
                val mode = _vibrationMode.get()
                if (mode == VibrationMode.PULSE) {
                    val bufMs = (BUFFER_SIZE.toLong() * 1000L) / SAMPLE_RATE
                    pulseOnTimer += bufMs
                    if (!pulseSilent && pulseOnTimer >= PULSE_ON_MS) {
                        pulseSilent = true;  pulseOnTimer = 0L
                    } else if (pulseSilent && pulseOnTimer >= PULSE_OFF_MS) {
                        pulseSilent = false; pulseOnTimer = 0L
                    }
                } else {
                    pulseSilent  = false
                    pulseOnTimer = 0L
                }

                // ── Phát hiện chuyển trạng thái → bắt đầu fade ───────────────
                if (pulseSilent && !prevSilent) {
                    fadeOutRemaining = FADE_SAMPLES
                    fadeInRemaining  = 0
                } else if (!pulseSilent && prevSilent) {
                    fadeInRemaining  = FADE_SAMPLES
                    fadeOutRemaining = 0
                }
            }
            prevSilent = pulseSilent

            // ── Tính từng mẫu PCM ─────────────────────────────────────────────
            for (i in buffer.indices) {
                val raw = (Short.MAX_VALUE * sin(phase)).toInt()
                phase += phaseIncrement
                if (phase >= 2.0 * PI) phase -= 2.0 * PI

                // Tính mẫu sau khi áp dụng pulse fade
                val pulseSample: Short = when {
                    fadeOutRemaining > 0 -> {
                        val env = fadeOutRemaining.toFloat() / FADE_SAMPLES
                        fadeOutRemaining--
                        (raw * env).toInt().toShort()
                    }
                    fadeInRemaining > 0 -> {
                        val env = (FADE_SAMPLES - fadeInRemaining).toFloat() / FADE_SAMPLES
                        fadeInRemaining--
                        (raw * env).toInt().toShort()
                    }
                    pulseSilent -> 0.toShort()
                    else        -> raw.toShort()
                }

                // Áp dụng thêm stop fade-out (ưu tiên cao nhất)
                buffer[i] = if (stopFadeRemaining > 0) {
                    val env = stopFadeRemaining.toFloat() / STOP_FADE_SAMPLES
                    stopFadeRemaining--
                    (pulseSample * env).toInt().toShort()
                } else if (stopping) {
                    0.toShort()
                } else {
                    pulseSample
                }
            }

            audioTrack?.write(buffer, 0, buffer.size)

            // Thoát vòng lặp sau khi đã fade-out hoàn toàn
            if (stopping && stopFadeRemaining == 0) break
        }
    }

    // ─── Rung ─────────────────────────────────────────────────────────────────

    /**
     * startVibration – khởi động rung theo chế độ hiện tại.
     *
     * FIX 2 CONTINUOUS: Dùng createWaveform([0, 1000], [0, amp], repeat=1)
     *   → loop vô hạn TỪ đoạn rung (index 1), không có khoảng lặng giữa các
     *   chu kỳ như repeat=0 với đoạn 30 giây.
     *
     * FIX PULSE: Dùng createWaveform([500, 300], [amp, 0], repeat=0)
     *   → loại bỏ phần tử delay 0ms ở đầu, pattern sạch hơn.
     */
    private fun startVibration() {
        val amplitude = _vibrationAmplitude.get()
        val mode      = _vibrationMode.get()
        if (mode == VibrationMode.OFF || amplitude == 0) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val effect = when (mode) {

                VibrationMode.CONTINUOUS ->
                    // [index 0] delay 0ms (silent)  → [index 1] rung 1000ms
                    // repeat=1: sau khi chạy xong, loop từ index 1 → rung liên tục không gián đoạn
                    VibrationEffect.createWaveform(
                        longArrayOf(0L, 1000L),
                        intArrayOf(0, amplitude),
                        1            // ← loop từ đoạn rung, không phải từ đầu
                    )

                VibrationMode.PULSE ->
                    // [index 0] rung 500ms  → [index 1] im 300ms
                    // repeat=0: loop từ đầu → 500ms on, 300ms off, lặp lại
                    VibrationEffect.createWaveform(
                        longArrayOf(PULSE_ON_MS, PULSE_OFF_MS),
                        intArrayOf(amplitude, 0),
                        0
                    )

                VibrationMode.OFF -> return
            }
            vibrator.vibrate(effect)
        } else {
            @Suppress("DEPRECATION")
            when (mode) {
                VibrationMode.CONTINUOUS ->
                    // pattern [0ms, 1000ms] repeat từ index 1
                    vibrator.vibrate(longArrayOf(0L, 1000L), 1)
                VibrationMode.PULSE ->
                    vibrator.vibrate(longArrayOf(PULSE_ON_MS, PULSE_OFF_MS), 0)
                VibrationMode.OFF -> return
            }
        }
    }

    private fun stopVibration() {
        vibrator.cancel()
    }
}
