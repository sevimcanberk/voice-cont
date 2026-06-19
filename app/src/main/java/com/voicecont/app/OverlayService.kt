package com.voicecont.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.Toast
import java.util.Locale
import kotlin.math.abs

/**
 * Ekranda her uygulamanın üstünde duran yüzen mikrofon butonu.
 * Dokunulduğunda ses dinlemeye başlar; sonucu erişilebilirlik servisine iletir.
 */
class OverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var bubble: ImageView
    private lateinit var params: WindowManager.LayoutParams

    private var recognizer: SpeechRecognizer? = null
    private var listening = false

    private val channelId = "voice_cont_overlay"

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startAsForeground()
        addBubble()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    // ---- Foreground bildirimi ----

    private fun startAsForeground() {
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                getString(R.string.notif_channel),
                NotificationManager.IMPORTANCE_LOW
            )
            nm.createNotificationChannel(channel)
        }
        val notification: Notification = Notification.Builder(this, channelId)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.notif_text))
            .setSmallIcon(R.drawable.ic_mic)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(1, notification)
        }
    }

    // ---- Yüzen buton ----

    private fun addBubble() {
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        bubble = ImageView(this).apply {
            setImageResource(R.drawable.ic_mic)
            setBackgroundResource(R.drawable.bubble_bg)
            val pad = dp(14)
            setPadding(pad, pad, pad, pad)
        }

        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            // NOT_FOCUSABLE: sohbet uygulamasının yazı kutusu odağını kaybetmesin.
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = dp(16)
            y = dp(200)
        }

        bubble.setOnTouchListener(makeTouchListener())
        windowManager.addView(bubble, params)
    }

    /** Sürükleme ile dokunmayı ayırır: küçük hareket = dokunma → dinle. */
    private fun makeTouchListener(): View.OnTouchListener {
        var initialX = 0
        var initialY = 0
        var touchX = 0f
        var touchY = 0f
        var moved = false

        return View.OnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    touchX = event.rawX
                    touchY = event.rawY
                    moved = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - touchX).toInt()
                    val dy = (event.rawY - touchY).toInt()
                    if (abs(dx) > dp(8) || abs(dy) > dp(8)) moved = true
                    params.x = initialX + dx
                    params.y = initialY + dy
                    windowManager.updateViewLayout(bubble, params)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!moved) onBubbleTap()
                    true
                }
                else -> false
            }
        }
    }

    private fun onBubbleTap() {
        if (listening) {
            stopListening()
            return
        }
        if (DictationAccessibilityService.instance == null) {
            toast(getString(R.string.no_accessibility))
            return
        }
        startListening()
    }

    // ---- Ses tanıma ----

    private fun startListening() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            toast("Cihazda ses tanıma yok.")
            return
        }
        listening = true
        bubble.setBackgroundResource(R.drawable.bubble_bg_listening)
        toast(getString(R.string.listening))

        recognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
            setRecognitionListener(recognitionListener)
        }
        // Dikte dili: ayardan; boşsa cihazın varsayılan dili.
        val lang = Prefs.lang(this).ifBlank { Locale.getDefault().toLanguageTag() }
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, lang)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, lang)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        recognizer?.startListening(intent)
    }

    private fun stopListening() {
        listening = false
        bubble.setBackgroundResource(R.drawable.bubble_bg)
        recognizer?.apply {
            stopListening()
            destroy()
        }
        recognizer = null
    }

    private val recognitionListener = object : RecognitionListener {
        override fun onResults(results: Bundle?) {
            val text = results
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()
            stopListening()
            if (text.isNullOrBlank()) return

            val svc = DictationAccessibilityService.instance
            if (svc == null) {
                toast(getString(R.string.no_accessibility))
                return
            }

            if (Prefs.isAutoSend(this@OverlayService)) {
                // Otomatik mod: yaz ve gönder.
                svc.dictateAndSend(text)
            } else {
                // Manuel mod: "gönder/send" komutu ise gönder, değilse kutuya yaz.
                if (isSendCommand(text)) svc.sendOnly() else svc.dictateOnly(text)
            }
        }

        override fun onError(error: Int) {
            stopListening()
            if (error != SpeechRecognizer.ERROR_NO_MATCH &&
                error != SpeechRecognizer.ERROR_SPEECH_TIMEOUT
            ) {
                toast("Ses tanıma hatası ($error)")
            }
        }

        override fun onReadyForSpeech(params: Bundle?) {}
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {}
        override fun onPartialResults(partialResults: Bundle?) {}
        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    // ---- Yardımcılar ----

    override fun onDestroy() {
        super.onDestroy()
        recognizer?.destroy()
        if (this::bubble.isInitialized) {
            runCatching { windowManager.removeView(bubble) }
        }
    }

    // Yalnızca "gönder" / "send" (tek başına, sonda nokta/ünlem olabilir) → gönder komutu.
    private val sendCommand = Regex("^(gönder|gonder|send)[.!?\\s]*$", RegexOption.IGNORE_CASE)

    private fun isSendCommand(text: String): Boolean =
        sendCommand.matches(text.trim())

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun toast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }
}
