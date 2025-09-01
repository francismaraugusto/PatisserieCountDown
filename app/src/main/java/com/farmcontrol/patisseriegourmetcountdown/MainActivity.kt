package com.farmcontrol.patisseriegourmetcountdown

import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Bundle
import android.os.CountDownTimer
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import android.widget.Button
import android.widget.NumberPicker
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var minutePicker: NumberPicker
    private lateinit var secondPicker: NumberPicker
    private lateinit var countdownText: TextView
    private lateinit var historyText: TextView
    private lateinit var startButton: Button
    private lateinit var pauseButton: Button
    private lateinit var cancelButton: Button
    private var countdownTimer: CountDownTimer? = null
    private var timeRemaining: Long = 0
    private var totalTime: Long = 0
    private var isPaused = false
    private var roundCounter = 0
    private lateinit var mediaPlayer: MediaPlayer
    private val historyList = mutableListOf<String>()
    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    private val handler = Handler(Looper.getMainLooper())
    private var alarmStartTime: Long = 0
    private val alarmRunnable = object : Runnable {
        override fun run() {
            if (!isPaused && System.currentTimeMillis() - alarmStartTime < 7000) { // Aumentado para 7 segundos
                try {
                    mediaPlayer.seekTo(0)
                    mediaPlayer.start()
                    handler.postDelayed(this, 2000) // Repete a cada 2 segundos
                } catch (e: Exception) {
                    Toast.makeText(this@MainActivity, "Erro ao tocar alarme", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    private val restartRunnable = object : Runnable {
        override fun run() {
            stopAlarm()
            timeRemaining = totalTime
            startCountdown()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Inicializar componentes da UI
        minutePicker = findViewById(R.id.minutePicker)
        secondPicker = findViewById(R.id.secondPicker)
        countdownText = findViewById(R.id.countdownText)
        historyText = findViewById(R.id.historyText)
        startButton = findViewById(R.id.startButton)
        pauseButton = findViewById(R.id.pauseButton)
        cancelButton = findViewById(R.id.cancelButton)

        // Configurar NumberPickers
        minutePicker.minValue = 0
        minutePicker.maxValue = 59
        minutePicker.value = 0
        minutePicker.wrapSelectorWheel = true

        secondPicker.minValue = 0
        secondPicker.maxValue = 59
        secondPicker.value = 0
        secondPicker.wrapSelectorWheel = true

        // Inicializar MediaPlayer
        mediaPlayer = MediaPlayer.create(this, android.provider.Settings.System.DEFAULT_ALARM_ALERT_URI) ?: run {
            Toast.makeText(this, "Erro ao carregar som de alarme", Toast.LENGTH_SHORT).show()
            MediaPlayer() // Fallback vazio
        }
        mediaPlayer.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )

        // Configurar volume do canal de alarme
        val audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        audioManager.setStreamVolume(
            AudioManager.STREAM_ALARM,
            (audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM) * 0.7).toInt(),
            0
        )

        // Inicializar histórico
        historyText.text = "Histórico:"

        // Configurar estados iniciais dos botões
        startButton.isEnabled = true
        pauseButton.isEnabled = false
        cancelButton.isEnabled = false

        // Ação do botão Iniciar
        startButton.setOnClickListener {
            if (!isPaused) {
                val minutes = minutePicker.value
                val seconds = secondPicker.value

                if (minutes == 0 && seconds == 0) {
                    Toast.makeText(this, "Selecione um tempo válido", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                totalTime = (minutes * 60 + seconds) * 1000L
                timeRemaining = totalTime
                startCountdown()
            } else {
                isPaused = false
                startCountdown()
            }
        }

        // Ação do botão Pausar
        pauseButton.setOnClickListener {
            countdownTimer?.cancel()
            handler.removeCallbacks(restartRunnable)
            stopAlarm()
            isPaused = true
            startButton.isEnabled = true
            startButton.text = "Retomar"
            pauseButton.isEnabled = false
            cancelButton.isEnabled = true
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) // Desativa a tela sempre ligada
        }

        // Ação do botão Cancelar
        cancelButton.setOnClickListener {
            countdownTimer?.cancel()
            handler.removeCallbacks(restartRunnable)
            stopAlarm()
            countdownText.text = "00:00"
            minutePicker.isEnabled = true
            secondPicker.isEnabled = true
            startButton.isEnabled = true
            startButton.text = "Iniciar"
            pauseButton.isEnabled = false
            cancelButton.isEnabled = false
            isPaused = false
            roundCounter = 0
            timeRemaining = 0
            totalTime = 0
            alarmStartTime = 0
            historyList.clear()
            updateHistoryText()
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) // Desativa a tela sempre ligada
        }
    }

    private fun startCountdown() {
        minutePicker.isEnabled = false
        secondPicker.isEnabled = false
        startButton.isEnabled = false
        pauseButton.isEnabled = true
        cancelButton.isEnabled = true
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) // Mantém a tela ligada

        roundCounter++
        val startTime = timeFormat.format(Date())
        val historyEntry = "${roundCounter}ª. ${formatTime(totalTime)} - Início $startTime"
        historyList.add(0, historyEntry)
        updateHistoryText()

        countdownTimer = object : CountDownTimer(timeRemaining, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                timeRemaining = millisUntilFinished
                countdownText.text = formatTime(millisUntilFinished)
            }

            override fun onFinish() {
                countdownText.text = "00:00"
                Toast.makeText(this@MainActivity, "Temporizador concluído!", Toast.LENGTH_SHORT).show()
                alarmStartTime = System.currentTimeMillis()
                handler.post(alarmRunnable)

                handler.postDelayed(restartRunnable, 7000) // Aumentado para 7 segundos
            }
        }.start()
    }

    private fun stopAlarm() {
        handler.removeCallbacks(alarmRunnable)
        if (mediaPlayer.isPlaying) {
            mediaPlayer.pause()
            mediaPlayer.seekTo(0)
        }
    }

    private fun updateHistoryText() {
        historyText.text = "Histórico:\n${historyList.joinToString("\n")}"
    }

    private fun formatTime(millis: Long): String {
        val minutes = millis / 1000 / 60
        val seconds = (millis / 1000) % 60
        return String.format("%02d:%02d", minutes, seconds)
    }

    override fun onDestroy() {
        super.onDestroy()
        countdownTimer?.cancel()
        handler.removeCallbacks(alarmRunnable)
        handler.removeCallbacks(restartRunnable)
        if (mediaPlayer.isPlaying) {
            mediaPlayer.pause()
            mediaPlayer.seekTo(0)
        }
        mediaPlayer.release()
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) // Desativa a tela sempre ligada
    }
}