package com.voicecont.app

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

/** Uygulamayı tanıtan ve komut listesini gösteren kılavuz ekranı. */
class HelpActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_help)
        supportActionBar?.apply {
            title = "Kılavuz"
            setDisplayHomeAsUpEnabled(true)
        }
        findViewById<TextView>(R.id.tvGuide).text = guideText()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun guideText(): String = """
VOICE CONT. — KILAVUZ

▌ Ne işe yarar?
Ekranda hangi sohbet açıksa (WhatsApp, Telegram, Instagram, Chrome...),
konuştuğun mesajı oraya yazar ve istersen otomatik gönderir. Eller (neredeyse)
serbest mesajlaşma.

▌ Kurulum (tek seferlik)
Ana ekrandaki 3 izni sırayla ver:
  1) Mikrofon
  2) Diğer uygulamaların üstünde göster
  3) Erişilebilirlik servisi (listede "Voice Cont."u aç)
Sonra "Yüzen butonu başlat"a bas. Ekranda mor mikrofon butonu belirir.
Butonu sürükleyerek istediğin yere taşıyabilirsin.

▌ Nasıl kullanılır?
1) Bir sohbet aç.
2) Yüzen mikrofon butonuna BİR KEZ dokun.
3) Mesajını konuş.
Butona tekrar dokunmak = dinlemeyi iptal et (yazılan kutuda kalır).

▌ İki çalışma modu (ana ekrandan seçilir)
• Otomatik gönder: AÇIK
  Konuş → mesaj yazılır ve hemen gönderilir. (Tek seferlik, hızlı.)

• Otomatik gönder: KAPALI
  Konuş → mesaj kutuya yazılır, GÖNDERİLMEZ. Uygulama seni biraz daha
  dinlemeye devam eder; eklemek istediğini söyleyebilirsin. Bitince
  "gönder" / "send" dediğinde mesaj gider. Tekrar butona dokunmana gerek yok.

▌ DİL
Dikte dili cihazının diline göre otomatik ayarlanır. İngilizce dikte için
telefonda İngilizce sesle-yazma paketini kur:
Ayarlar → Genel yönetim → Klavye → Google ile sesle yazma → İngilizce indir.

──────────────────────────────
SESLİ KOMUTLAR
──────────────────────────────

▌ Gönderme (mesajı gönderir, manuel modda)
  "gönder"            "send"
  "mesajı gönder"     "send the message" / "send message"

▌ Noktalama
  "nokta" / "nokta koy"        "period" / "dot" / "full stop"   →  .
  "virgül" / "virgül koy"      "comma"                          →  ,
  "soru" / "soru işareti"      "question mark"                  →  ?
  "ünlem" / "ünlem işareti"    "exclamation mark/point"         →  !
  "iki nokta"                  "colon"                          →  :
  "noktalı virgül"             "semicolon"                      →  ;
  "üç nokta"                   "ellipsis"                       →  …
  "yeni satır" / "alt satır"   "new line"                       →  (alt satır)
  "yeni paragraf"              "new paragraph"                  →  (boş satır)
  "parantez aç / kapat"        "open / close paren"             →  ( )
  "tırnak aç / kapat"          —                                →  "

Örnek (KAPALI mod):
  Söyle:  "merhaba nasılsın soru işareti yarın görüşürüz nokta gönder"
  Yazılır: merhaba nasılsın? yarın görüşürüz.
  ...ve mesaj gönderilir.

──────────────────────────────
İPUÇLARI
──────────────────────────────
• Gönder komutu cümlenin sonunda söylenmeli (tek başına ya da en sonda).
• Küfür/argo yıldızlanıyorsa bu cihaz ayarıdır: Google ile sesle yazma →
  "Uygunsuz kelimeleri gizle" seçeneğini kapat.
• Bir uygulamada gönderilmiyorsa kutuya yine yazılır; o uygulamanın adını
  geliştiriciye bildir, gönder butonu tanımına eklensin.
""".trimIndent()
}
