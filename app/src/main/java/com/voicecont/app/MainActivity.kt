package com.voicecont.app

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var btnMic: Button
    private lateinit var btnOverlay: Button
    private lateinit var btnAccessibility: Button
    private lateinit var btnAutoSend: Button
    private lateinit var btnLang: Button
    private lateinit var btnStart: Button
    private lateinit var tvStatus: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        btnMic = findViewById(R.id.btnMic)
        btnOverlay = findViewById(R.id.btnOverlay)
        btnAccessibility = findViewById(R.id.btnAccessibility)
        btnAutoSend = findViewById(R.id.btnAutoSend)
        btnLang = findViewById(R.id.btnLang)
        btnStart = findViewById(R.id.btnStart)
        tvStatus = findViewById(R.id.tvStatus)

        btnMic.setOnClickListener {
            ActivityCompat.requestPermissions(
                this, arrayOf(Manifest.permission.RECORD_AUDIO), 100
            )
        }

        btnOverlay.setOnClickListener {
            startActivity(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
            )
        }

        btnAccessibility.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        btnAutoSend.setOnClickListener {
            Prefs.setAutoSend(this, !Prefs.isAutoSend(this))
            updateSettingLabels()
        }

        btnLang.setOnClickListener {
            // Türkçe → English → Cihaz dili → ...
            val next = when (Prefs.lang(this)) {
                Prefs.LANG_TR -> Prefs.LANG_EN
                Prefs.LANG_EN -> Prefs.LANG_DEVICE
                else -> Prefs.LANG_TR
            }
            Prefs.setLang(this, next)
            updateSettingLabels()
        }

        btnStart.setOnClickListener { toggleOverlay() }
        updateSettingLabels()
    }

    private fun updateSettingLabels() {
        btnAutoSend.text = if (Prefs.isAutoSend(this))
            "Otomatik gönder: AÇIK (konuş → yaz + gönder)"
        else
            "Otomatik gönder: KAPALI (yaz, sonra \"gönder/send\" de)"

        val langName = when (Prefs.lang(this)) {
            Prefs.LANG_TR -> "Türkçe"
            Prefs.LANG_EN -> "English"
            else -> "Cihaz dili"
        }
        btnLang.text = "Dikte dili: $langName"
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
    }

    private fun toggleOverlay() {
        if (!allGranted()) {
            tvStatus.text = "Önce yukarıdaki 3 izni tamamla."
            return
        }
        startForegroundService(Intent(this, OverlayService::class.java))
        tvStatus.text = "Yüzen buton başlatıldı. Bir sohbet aç, butona dokun, konuş."
    }

    private fun refreshStatus() {
        val mic = hasMic()
        val overlay = Settings.canDrawOverlays(this)
        val acc = isAccessibilityEnabled()

        btnMic.text = "1. Mikrofon izni — " + mark(mic)
        btnOverlay.text = "2. Üstte gösterme izni — " + mark(overlay)
        btnAccessibility.text = "3. Erişilebilirlik servisi — " + mark(acc)

        btnStart.isEnabled = mic && overlay && acc
        val durum = if (mic && overlay && acc)
            "Hazır. 'Yüzen butonu başlat'a bas."
        else
            "Eksik izinleri tamamla (sırayla 1-2-3)."
        tvStatus.text = "Sürüm: v${appVersion()}\n$durum"
    }

    private fun appVersion(): String =
        runCatching {
            packageManager.getPackageInfo(packageName, 0).versionName ?: "?"
        }.getOrDefault("?")

    private fun allGranted(): Boolean =
        hasMic() && Settings.canDrawOverlays(this) && isAccessibilityEnabled()

    private fun hasMic(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    private fun mark(ok: Boolean): String =
        if (ok) getString(R.string.status_granted) else getString(R.string.status_missing)

    /** Erişilebilirlik servisimiz açık mı? */
    private fun isAccessibilityEnabled(): Boolean {
        val expected = "$packageName/$packageName.DictationAccessibilityService"
        val enabled = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false

        val splitter = TextUtils.SimpleStringSplitter(':')
        splitter.setString(enabled)
        for (service in splitter) {
            if (service.equals(expected, ignoreCase = true)) return true
            // Bazı sürümler tam sınıf adıyla yazar:
            if (service.contains("DictationAccessibilityService", ignoreCase = true)) return true
        }
        return false
    }
}
