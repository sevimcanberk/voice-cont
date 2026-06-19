package com.voicecont.app

import android.accessibilityservice.AccessibilityService
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast

/**
 * Asıl iş: dikte edilen metni o an açık olan sohbetin yazı kutusuna yazar
 * ve "Gönder" butonuna basar.
 */
class DictationAccessibilityService : AccessibilityService() {

    companion object {
        @Volatile
        var instance: DictationAccessibilityService? = null
            private set
    }

    private val handler = Handler(Looper.getMainLooper())

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        instance = null
        return super.onUnbind(intent)
    }

    // İstemci olarak çalışıyoruz; olay akışını dinlememize gerek yok.
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onInterrupt() {}

    /** Otomatik mod: metni yaz, kısa gecikme sonrası gönder. */
    fun dictateAndSend(text: String) {
        if (writeText(text)) {
            // Metnin işlenmesi + Gönder butonunun etkinleşmesi için kısa bekleme.
            handler.postDelayed({ performSend() }, 350)
        }
    }

    /** Manuel mod: sadece metni kutuya yaz, gönderme. */
    fun dictateOnly(text: String) {
        writeText(text)
    }

    /** Manuel mod: "gönder/send" komutu gelince yalnızca Gönder'e bas. */
    fun sendOnly() {
        performSend()
    }

    /**
     * Metni aktif penceredeki yazı kutusuna yazar (varsa taslağın sonuna ekler).
     * @return kutu bulunup yazıldıysa true.
     */
    private fun writeText(text: String): Boolean {
        val field = findEditableNode(rootInActiveWindow)
        if (field == null) {
            toast(getString(R.string.no_field))
            return false
        }

        // Boş kutuda bazı uygulamalar (WhatsApp gibi) ipucu metnini ("Message")
        // text olarak döndürür → onu mevcut metin sanma.
        val hint = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            field.hintText?.toString() else null
        val raw = field.text?.toString().orEmpty()
        val existing = if (raw.isBlank() || raw == hint) "" else raw

        val combined = if (existing.isBlank()) text
        else if (existing.endsWith(" ")) existing + text
        else "$existing $text"

        val args = Bundle().apply {
            putCharSequence(
                AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                combined
            )
        }
        return field.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
    }

    private fun performSend() {
        val root = rootInActiveWindow ?: return
        val sendButton = SendButtonFinder.find(root)

        if (sendButton != null) {
            sendButton.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            return
        }

        // Yedek: bazı uygulamalar Enter/IME aksiyonuyla gönderir.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val field = findEditableNode(root)
            if (field != null) {
                val ok = field.performAction(
                    AccessibilityNodeInfo.AccessibilityAction.ACTION_IME_ENTER.id
                )
                if (ok) return
            }
        }

        toast(getString(R.string.not_sent))
    }

    /** Önce odaklı giriş alanını, yoksa görünür ilk editable düğümü bulur. */
    private fun findEditableNode(root: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (root == null) return null

        root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)?.let {
            if (it.isEditable) return it
        }

        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            if (node.isEditable && node.isVisibleToUser) return node
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { queue.add(it) }
            }
        }
        return null
    }

    private fun toast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }
}
