package com.voicecont.app

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import kotlin.math.abs

/**
 * Ekranda her uygulamanın üstünde duran yüzen mikrofon butonu.
 * Dokunulduğunda ses dinlemeye başlar; sonucu erişilebilirlik servisine iletir.
 */
class OverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var bubble: ImageView
    private lateinit var params: WindowManager.LayoutParams

    // Dil seçici baloncukları (uzun basınca açılır)
    private var trBubble: TextView? = null
    private var enBubble: TextView? = null
    private var pickerVisible = false
    private var longPressed = false
    private val longPressRunnable = Runnable {
        longPressed = true
        showLanguagePicker()
    }

    private var recognizer: SpeechRecognizer? = null
    private var listening = false

    // ---- Oturum durumu (sürekli dinleme) ----
    private var sessionActive = false        // manuel modda dinleme döngüsü açık mı
    private var autoSendMode = true          // bu oturum otomatik gönder mi
    private var seed = ""                    // oturum başındaki gerçek taslak
    private val rawTranscript = StringBuilder()  // söylenen ham kelimeler
    private var lastSpeechAt = 0L            // son konuşmanın zamanı (elapsedRealtime)
    private val relistenWindowMs = 25_000L   // son sözden sonra en az bu kadar dinle

    private val handler = Handler(Looper.getMainLooper())
    private val channelId = "voice_cont_overlay"
    private var pulseSet: AnimatorSet? = null

    private fun startPulse() {
        stopPulse()
        // Pencere sınırında kırpılmaması için ölçek yerine alfa nabzı.
        val a = ObjectAnimator.ofFloat(bubble, "alpha", 1f, 0.5f).apply {
            duration = 650
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
        }
        pulseSet = AnimatorSet().apply { play(a); start() }
    }

    private fun stopPulse() {
        pulseSet?.cancel()
        pulseSet = null
        if (this::bubble.isInitialized) bubble.alpha = 1f
    }

    /** Oturum açıksa bir sonraki döngüde tekrar dinlemeye başla. */
    private fun relisten() = handler.post { if (sessionActive) startListening() }

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
                    longPressed = false
                    handler.postDelayed(
                        longPressRunnable,
                        ViewConfiguration.getLongPressTimeout().toLong()
                    )
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - touchX).toInt()
                    val dy = (event.rawY - touchY).toInt()
                    if (abs(dx) > dp(8) || abs(dy) > dp(8)) {
                        moved = true
                        handler.removeCallbacks(longPressRunnable)
                        if (pickerVisible) hideLanguagePicker()
                    }
                    params.x = initialX + dx
                    params.y = initialY + dy
                    windowManager.updateViewLayout(bubble, params)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    handler.removeCallbacks(longPressRunnable)
                    if (!moved && !longPressed) onBubbleTap()
                    true
                }
                else -> false
            }
        }
    }

    // ---- Dil seçici (uzun basma) ----

    private fun showLanguagePicker() {
        if (pickerVisible) return
        val size = dp(52)
        val gap = dp(10)
        val screenH = resources.displayMetrics.heightPixels

        // Ana butonun altına iki baloncuk; alta sığmazsa üste koy.
        val below = params.y + 2 * (size + gap) + size < screenH
        val y1 = if (below) params.y + (size + gap) else params.y - (size + gap)
        val y2 = if (below) params.y + 2 * (size + gap) else params.y - 2 * (size + gap)

        trBubble = makePickerBubble("TR", Prefs.LANG_TR)
        enBubble = makePickerBubble("EN", Prefs.LANG_EN)
        windowManager.addView(trBubble, pickerParams(params.x, y1, size))
        windowManager.addView(enBubble, pickerParams(params.x, y2, size))
        pickerVisible = true
    }

    private fun hideLanguagePicker() {
        trBubble?.let { runCatching { windowManager.removeView(it) } }
        enBubble?.let { runCatching { windowManager.removeView(it) } }
        trBubble = null
        enBubble = null
        pickerVisible = false
    }

    private fun makePickerBubble(label: String, lang: String): TextView =
        TextView(this).apply {
            text = label
            setTextColor(Color.WHITE)
            textSize = 16f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setBackgroundResource(R.drawable.bubble_bg)
            // Seçili dili vurgula
            alpha = if (Prefs.lang(this@OverlayService) == lang) 1f else 0.6f
            setOnClickListener {
                Prefs.setLang(this@OverlayService, lang)
                toast("Dil: " + if (lang == Prefs.LANG_TR) "Türkçe" else "English")
                hideLanguagePicker()
            }
        }

    private fun pickerParams(x: Int, y: Int, size: Int): WindowManager.LayoutParams =
        WindowManager.LayoutParams(
            size, size,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            this.x = x
            this.y = y
        }

    private fun onBubbleTap() {
        // Dil seçici açıksa → kapat.
        if (pickerVisible) {
            hideLanguagePicker()
            return
        }
        // Oturum açıksa (dinliyorsa) → iptal et / durdur.
        if (sessionActive || listening) {
            endSession()
            return
        }
        if (DictationAccessibilityService.instance == null) {
            toast(getString(R.string.no_accessibility))
            return
        }
        startSession()
    }

    // ---- Oturum yönetimi ----

    private fun startSession() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            toast("Cihazda ses tanıma yok.")
            return
        }
        autoSendMode = Prefs.isAutoSend(this)
        seed = DictationAccessibilityService.instance?.currentFieldText().orEmpty()
        rawTranscript.setLength(0)
        lastSpeechAt = SystemClock.elapsedRealtime()
        sessionActive = true
        toast(getString(R.string.listening))
        startPulse()
        startListening()
    }

    private fun endSession() {
        sessionActive = false
        stopPulse()
        stopListening()
    }

    private fun startListening() {
        // Önceki tanıyıcıyı temizle (sürekli modda BUSY çakışmasını önler).
        recognizer?.destroy()
        recognizer = null

        listening = true
        bubble.setBackgroundResource(R.drawable.bubble_bg_listening)
        recognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
            setRecognitionListener(recognitionListener)
        }
        // Dil: yüzen butona uzun basıp seçilen (TR/EN). Komutlar yine iki dilli algılanır.
        val lang = Prefs.lang(this)
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, lang)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, lang)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            // Konuşma duraklamalarında tanıyıcı erken kapanmasın (ipucu; cihaza göre değişir).
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 3000L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 3000L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 6000L)
        }
        recognizer?.startListening(intent)
    }

    private fun stopListening() {
        listening = false
        bubble.setBackgroundResource(R.drawable.bubble_bg)
        recognizer?.apply {
            runCatching { stopListening() }
            destroy()
        }
        recognizer = null
    }

    /** Geçerli tampondan kutuya yazılacak tam metni üretir ve yazar. */
    private fun renderAndWrite(): Boolean {
        val parsed = VoiceCommands.parse(rawTranscript.toString())
        val body = VoiceCommands.render(parsed.tokens)
        val full = when {
            seed.isBlank() -> body
            body.isBlank() -> seed
            seed.endsWith(" ") || seed.endsWith("\n") -> seed + body
            else -> "$seed $body"
        }
        return DictationAccessibilityService.instance?.writeText(full) ?: false
    }

    private val recognitionListener = object : RecognitionListener {
        override fun onResults(results: Bundle?) {
            val chunk = results
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()
            listening = false

            val svc = DictationAccessibilityService.instance
            if (svc == null) {
                endSession()
                toast(getString(R.string.no_accessibility))
                return
            }

            if (chunk.isNullOrBlank()) {
                onSilence()
                return
            }
            lastSpeechAt = SystemClock.elapsedRealtime()

            // Sondaki "gönder/send" komutunu ayır; kalan ham sözü tampona ekle.
            val (raw, isSend) = VoiceCommands.stripTrailingSend(chunk)
            if (raw.isNotBlank()) {
                if (rawTranscript.isNotEmpty()) rawTranscript.append(' ')
                rawTranscript.append(raw)
            }

            // Tamponu (noktalama dönüştürülmüş haliyle) kutuya yaz.
            renderAndWrite()

            if (autoSendMode || isSend) {
                // Otomatik mod: hep gönder. Manuel mod: komut geldiyse gönder.
                svc.sendDelayed()
                endSession()
            } else if (sessionActive) {
                // Manuel mod, komut yok → dinlemeye devam et (biraz daha).
                relisten()
            }
        }

        override fun onError(error: Int) {
            listening = false
            if (error == SpeechRecognizer.ERROR_NO_MATCH ||
                error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT
            ) {
                onSilence()
            } else {
                endSession()
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

    /**
     * Sessizlik/eşleşmeme: manuel modda son sözden bu yana 25 sn geçmediyse
     * dinlemeye devam et (komut için fırsat ver). Süre dolunca dur (metin kutuda kalır).
     */
    private fun onSilence() {
        if (autoSendMode || !sessionActive) {
            endSession()
            return
        }
        val elapsed = SystemClock.elapsedRealtime() - lastSpeechAt
        if (elapsed <= relistenWindowMs) {
            relisten()
        } else {
            endSession()  // 25 sn boyunca komut gelmedi; metin kutuda kalır
        }
    }

    // ---- Yardımcılar ----

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(longPressRunnable)
        stopPulse()
        recognizer?.destroy()
        hideLanguagePicker()
        if (this::bubble.isInitialized) {
            runCatching { windowManager.removeView(bubble) }
        }
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun toast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }
}
