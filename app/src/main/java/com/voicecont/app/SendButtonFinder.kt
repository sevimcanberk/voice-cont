package com.voicecont.app

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Açık sohbet uygulamasının ekran ağacında "Gönder" butonunu bulur.
 * WhatsApp/Telegram/Instagram gibi uygulamalar butonu çoğunlukla
 * contentDescription ile etiketler ("Send", "Gönder"...). Çok dilli eşleştirme yapar.
 */
object SendButtonFinder {

    // Yaygın "gönder" sözcükleri (büyük/küçük harf duyarsız).
    private val SEND_PATTERN = Regex(
        "(send|gönder|gonder|enviar|envoyer|senden|invia|送信|발송|보내기|отправить|إرسال)",
        RegexOption.IGNORE_CASE
    )

    fun find(root: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (root == null) return null

        val candidates = mutableListOf<AccessibilityNodeInfo>()
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)

        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()

            val label = buildString {
                node.contentDescription?.let { append(it).append(' ') }
                node.text?.let { append(it) }
            }

            if (label.isNotBlank() && SEND_PATTERN.containsMatchIn(label)) {
                // Etiketi taşıyan düğüm tıklanabilir değilse, tıklanabilir bir ata bul.
                var clickable: AccessibilityNodeInfo? = node
                while (clickable != null && !(clickable.isClickable && clickable.isEnabled)) {
                    clickable = clickable.parent
                }
                if (clickable != null) candidates.add(clickable)
            }

            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { queue.add(it) }
            }
        }

        // Gönder butonu tipik olarak en altta/sağda olur → onu seç.
        return candidates.maxByOrNull {
            val r = Rect()
            it.getBoundsInScreen(r)
            r.bottom.toLong() * 100_000L + r.right
        }
    }
}
