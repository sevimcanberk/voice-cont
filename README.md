# Voice Cont.

Telefonda ekranda açık olan **herhangi bir sohbete**, konuştuğun mesajı sesle dikte edip
otomatik **Gönder**'e basan Android uygulaması.

## Nasıl çalışır

1. Ekranda yüzen bir mikrofon butonu durur (her uygulamanın üstünde).
2. Butona dokun → konuş.
3. Uygulama konuşmanı metne çevirir (Google `SpeechRecognizer`, Türkçe).
4. **Erişilebilirlik servisi** o an açık olan sohbet uygulamasının yazı kutusunu bulur,
   metni yazar ve "Gönder" butonuna basar.

WhatsApp ve Telegram öncelikli hedef; diğer sohbet uygulamalarında da çoğunlukla çalışır
(gönder butonu otomatik tespit edilir).

---

## APK'yı üretme (GitHub Actions — makineye Android Studio kurmadan)

Makinende Java/Android SDK yok; bu yüzden APK'yı bulutta üretiyoruz.

### 1. Kodu GitHub'a koy

GitHub'da boş bir repo aç (örn. `voice-cont`), sonra bu klasörde:

```bash
cd "C:/Users/canbe/Downloads/Agent-OS/Projects/Active/Voice Cont"
git init
git add .
git commit -m "Voice Cont. ilk sürüm"
git branch -M main
git remote add origin https://github.com/<KULLANICI_ADIN>/voice-cont.git
git push -u origin main
```

### 2. APK otomatik üretilir

Push olur olmaz **Actions** sekmesinde "Build APK" iş akışı çalışır (~3-5 dk).
Bittiğinde çalışmanın altındaki **Artifacts → `voice-cont-debug-apk`**'yı indir.
İçinde `app-debug.apk` var.

> Elle de tetikleyebilirsin: Actions → Build APK → "Run workflow".

### 3. Telefona kur (sideload)

1. `app-debug.apk`'yı telefona aktar (Drive, USB, Telegram "Kaydedilenler", vb.).
2. Dosyaya dokun → "Bilinmeyen kaynaklara izin ver" çıkarsa aç → Kur.

---

## İlk açılışta verilecek izinler (uygulama içi ekran yönlendirir)

| İzin | Niçin |
|---|---|
| **Mikrofon** | Konuşmanı kaydetmek için |
| **Diğer uygulamaların üstünde göster** | Yüzen butonu çizmek için |
| **Erişilebilirlik servisi** (Voice Cont.) | Açık sohbete yazıp Gönder'e basmak için |

Üçü de verilince ana ekrandaki **"Yüzen butonu başlat"**'a bas. Bitti.

> Türkçe sesli yazma çevrimdışı çalışsın istersen: Android Ayarlar → Sistem → Diller →
> Sesle yazma → Türkçe dil paketini indir.

---

## Bilinen sınırlar (v1)

- Tetikleyici tek dokunuş gerektirir (tam eller-serbest "hotword" faz 2).
- Gönder butonu nadir bazı uygulamalarda otomatik bulunamayabilir → o durumda Enter/IME
  aksiyonu denenir; yine olmazsa mesaj kutuya yazılır ama gönderilmez (elle gönderirsin).

## Yapı

```
app/src/main/java/com/voicecont/app/
  MainActivity.kt                    # Kurulum/izin ekranı
  OverlayService.kt                  # Yüzen buton + ses dinleme akışı
  DictationAccessibilityService.kt   # Metni yaz + Gönder'e bas
  SendButtonFinder.kt                # Gönder butonu tespiti (çok dilli)
.github/workflows/build.yml          # Bulut APK derleme
```
