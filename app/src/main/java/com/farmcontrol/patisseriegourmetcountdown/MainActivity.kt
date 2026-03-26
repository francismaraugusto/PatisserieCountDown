package com.farmcontrol.patisseriegourmetcountdown

import android.animation.ObjectAnimator
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import android.view.animation.LinearInterpolator
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var minutePicker: NumberPicker
    private lateinit var secondPicker: NumberPicker
    private lateinit var countdownText: TextView
    private lateinit var statusLabel: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var historyText: TextView
    private lateinit var startButton: com.google.android.material.button.MaterialButton
    private lateinit var pauseButton: com.google.android.material.button.MaterialButton
    private lateinit var cancelButton: com.google.android.material.button.MaterialButton
    private lateinit var cycleText: TextView
    private lateinit var cycleBadge: TextView

    private var countdownTimer: CountDownTimer? = null
    private var timeRemaining: Long = 0
    private var totalTime: Long = 0
    private var isPaused = false
    private var roundCounter = 0

    private var mediaPlayer: MediaPlayer? = null
    private lateinit var audioManager: AudioManager
    private var audioFocusRequest: AudioFocusRequest? = null
    private var savedAlarmVolume: Int = -1

    // Histórico persiste enquanto o app está aberto
    private val historyList = mutableListOf<String>()
    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    private val handler = Handler(Looper.getMainLooper())
    private var alarmStartTime: Long = 0

    private val ALARM_DURATION_MS = 10_000L
    private val ALARM_REPEAT_MS = 2500L

    private val alarmRunnable = object : Runnable {
        override fun run() {
            val elapsed = System.currentTimeMillis() - alarmStartTime
            if (elapsed < ALARM_DURATION_MS) {
                playAlarmSound()
                handler.postDelayed(this, ALARM_REPEAT_MS)
            }
        }
    }

    private val restartRunnable = Runnable {
        stopAlarm()
        val endTime = timeFormat.format(Date())
        // Registra fim do ciclo na entrada existente
        if (historyList.isNotEmpty()) {
            val idx = historyList.indexOfFirst { it.contains("em andamento") }
            if (idx >= 0) {
                historyList[idx] = historyList[idx].replace("em andamento", "✓ Fim $endTime")
            }
        }
        updateHistoryText()
        timeRemaining = totalTime
        startCountdown()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        minutePicker   = findViewById(R.id.minutePicker)
        secondPicker   = findViewById(R.id.secondPicker)
        countdownText  = findViewById(R.id.countdownText)
        statusLabel    = findViewById(R.id.statusLabel)
        progressBar    = findViewById(R.id.progressBar)
        historyText    = findViewById(R.id.historyText)
        startButton    = findViewById(R.id.startButton)
        pauseButton    = findViewById(R.id.pauseButton)
        cancelButton   = findViewById(R.id.cancelButton)
        cycleText      = findViewById(R.id.cycleText)
        cycleBadge     = findViewById(R.id.cycleBadge)

        audioManager = getSystemService(AUDIO_SERVICE) as AudioManager

        minutePicker.minValue = 0
        minutePicker.maxValue = 59
        minutePicker.value = 7
        minutePicker.wrapSelectorWheel = true

        secondPicker.minValue = 0
        secondPicker.maxValue = 59
        secondPicker.value = 0
        secondPicker.wrapSelectorWheel = true

        progressBar.max = 1000
        progressBar.progress = 1000

        updateHistoryText()
        cycleText.text  = "Ciclo: —"
        cycleBadge.text = "0 ciclos"

        // Estado inicial: só Iniciar habilitado
        setButtonState(State.IDLE)

        // ── INICIAR / RETOMAR ──────────────────────────────────────────
        startButton.setOnClickListener {
            if (!isPaused) {
                val minutes = minutePicker.value
                val seconds = secondPicker.value
                if (minutes == 0 && seconds == 0) {
                    Toast.makeText(this, "Selecione um tempo válido", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                totalTime     = (minutes * 60 + seconds) * 1000L
                timeRemaining = totalTime
                startCountdown()
            } else {
                // Retomar — adiciona anotação de retomada no log do ciclo atual
                isPaused = false
                val resumeTime = timeFormat.format(Date())
                appendToCurrentEntry("▶ Retomado $resumeTime")
                setButtonState(State.RUNNING)
                startCountdown(isResume = true)
            }
        }

        // ── PAUSAR ────────────────────────────────────────────────────
        pauseButton.setOnClickListener {
            countdownTimer?.cancel()
            handler.removeCallbacks(restartRunnable)
            handler.removeCallbacks(alarmRunnable)
            stopAlarm()
            isPaused = true

            val pauseTime = timeFormat.format(Date())
            appendToCurrentEntry("⏸ Pausado $pauseTime")
            updateHistoryText()

            statusLabel.text = "pausado"
            setButtonState(State.PAUSED)
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }

        // ── CANCELAR ──────────────────────────────────────────────────
        cancelButton.setOnClickListener {
            countdownTimer?.cancel()
            handler.removeCallbacks(restartRunnable)
            handler.removeCallbacks(alarmRunnable)
            stopAlarm()

            val cancelTime = timeFormat.format(Date())
            appendToCurrentEntry("✕ Cancelado $cancelTime")
            updateHistoryText()

            // Reset visual mas NÃO limpa o histórico
            countdownText.text = "00:00"
            statusLabel.text   = "pronto"
            progressBar.progress = 1000
            minutePicker.isEnabled = true
            secondPicker.isEnabled = true
            cycleText.text = "Ciclo: —"

            isPaused      = false
            timeRemaining = 0
            totalTime     = 0
            alarmStartTime = 0
            roundCounter  = 0

            setButtonState(State.IDLE)
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    // ── Estados dos botões ─────────────────────────────────────────────
    private enum class State { IDLE, RUNNING, PAUSED }

    private fun setButtonState(state: State) {
        when (state) {
            State.IDLE -> {
                startButton.isEnabled  = true
                startButton.text       = "Iniciar"
                pauseButton.isEnabled  = false
                cancelButton.isEnabled = false
            }
            State.RUNNING -> {
                startButton.isEnabled  = false
                startButton.text       = "Iniciar"
                pauseButton.isEnabled  = true
                cancelButton.isEnabled = true
            }
            State.PAUSED -> {
                startButton.isEnabled  = true
                startButton.text       = "Retomar"
                pauseButton.isEnabled  = false
                cancelButton.isEnabled = true
            }
        }
    }

    // ── Helpers de histórico ───────────────────────────────────────────

    /** Adiciona uma linha de evento dentro da entrada do ciclo atual */
    private fun appendToCurrentEntry(event: String) {
        if (historyList.isEmpty()) return
        historyList[0] = historyList[0] + "\n    $event"
    }

    private fun updateHistoryText() {
        val count = historyList.size
        cycleBadge.text = "$count ciclo${if (count != 1) "s" else ""}"
        historyText.text = if (historyList.isEmpty()) {
            "Nenhum ciclo registrado ainda."
        } else {
            historyList.joinToString("\n\n")
        }
    }

    // ── Countdown ─────────────────────────────────────────────────────

    private fun startCountdown(isResume: Boolean = false) {
        minutePicker.isEnabled = false
        secondPicker.isEnabled = false
        setButtonState(State.RUNNING)
        statusLabel.text = "em andamento"
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        if (!isResume) {
            // Novo ciclo — cria entrada no histórico
            roundCounter++
            val startTime = timeFormat.format(Date())
            val entry = "● Ciclo $roundCounter — ${formatTime(totalTime)}\n    ▷ Início $startTime — em andamento"
            historyList.add(0, entry)
            cycleText.text  = "Ciclo $roundCounter"
            cycleBadge.text = "$roundCounter ciclo${if (roundCounter != 1) "s" else ""}"
            updateHistoryText()
        }

        countdownTimer = object : CountDownTimer(timeRemaining, 100) {
            override fun onTick(millisUntilFinished: Long) {
                timeRemaining = millisUntilFinished
                countdownText.text = formatTime(millisUntilFinished)
                progressBar.progress = ((millisUntilFinished.toFloat() / totalTime) * 1000).toInt()
            }

            override fun onFinish() {
                countdownText.text   = "00:00"
                progressBar.progress = 0
                statusLabel.text     = "concluído!"
                cycleText.text       = "Ciclo $roundCounter ✓"

                Toast.makeText(
                    this@MainActivity,
                    "Ciclo $roundCounter concluído!",
                    Toast.LENGTH_SHORT
                ).show()

                startBlinkAnimation()
                alarmStartTime = System.currentTimeMillis()
                handler.post(alarmRunnable)
                handler.postDelayed(restartRunnable, ALARM_DURATION_MS)
            }
        }.start()
    }

    // ── Alarme ────────────────────────────────────────────────────────

    private fun forceMaxAlarmVolume() {
        savedAlarmVolume = audioManager.getStreamVolume(AudioManager.STREAM_ALARM)
        audioManager.setStreamVolume(
            AudioManager.STREAM_ALARM,
            audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM),
            0
        )
    }

    private fun restoreAlarmVolume() {
        if (savedAlarmVolume >= 0) {
            audioManager.setStreamVolume(AudioManager.STREAM_ALARM, savedAlarmVolume, 0)
            savedAlarmVolume = -1
        }
    }

    private fun requestAlarmAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                .setWillPauseWhenDucked(false)
                .build()
            audioFocusRequest = req
            audioManager.requestAudioFocus(req)
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(
                null,
                AudioManager.STREAM_ALARM,
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE
            )
        }
    }

    private fun releaseAlarmAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
        }
    }

    private fun buildMediaPlayer(): MediaPlayer? {
        val uris = listOf(
            android.provider.Settings.System.DEFAULT_ALARM_ALERT_URI,
            android.provider.Settings.System.DEFAULT_RINGTONE_URI,
            android.provider.Settings.System.DEFAULT_NOTIFICATION_URI
        )
        for (uri in uris) {
            try {
                val mp = MediaPlayer()
                mp.setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    mp.setDataSource(this.createAttributionContext("audio"), uri)
                } else {
                    mp.setDataSource(this, uri)
                }
                mp.prepare()
                return mp
            } catch (_: Exception) {}
        }
        return null
    }

    private fun playAlarmSound() {
        try {
            mediaPlayer?.release()
            mediaPlayer = buildMediaPlayer()
            forceMaxAlarmVolume()
            requestAlarmAudioFocus()
            mediaPlayer?.start()
        } catch (e: Exception) {
            Toast.makeText(this, "Erro no alarme: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun stopAlarm() {
        handler.removeCallbacks(alarmRunnable)
        try {
            mediaPlayer?.let { if (it.isPlaying) it.stop(); it.release() }
        } catch (_: Exception) {}
        mediaPlayer = null
        releaseAlarmAudioFocus()
        restoreAlarmVolume()
    }

    private fun startBlinkAnimation() {
        val animator = ObjectAnimator.ofFloat(countdownText, "alpha", 1f, 0.2f).apply {
            duration = 500
            repeatCount = ((ALARM_DURATION_MS / 500) - 1).toInt()
            repeatMode = ObjectAnimator.REVERSE
            interpolator = LinearInterpolator()
        }
        animator.start()
        handler.postDelayed({ countdownText.alpha = 1f }, ALARM_DURATION_MS)
    }

    private fun formatTime(millis: Long): String {
        val minutes = millis / 1000 / 60
        val seconds = (millis / 1000) % 60
        return String.format("%02d:%02d", minutes, seconds)
    }

    // ── Ciclo de vida ─────────────────────────────────────────────────

    override fun onDestroy() {
        super.onDestroy()
        countdownTimer?.cancel()
        handler.removeCallbacks(alarmRunnable)
        handler.removeCallbacks(restartRunnable)
        stopAlarm()
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        // Histórico liberado aqui junto com a Activity
    }
}