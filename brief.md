# Voice Cont. — Proje Özeti

## Amaç
Telefonda, ekranda açık olan **herhangi bir sohbete** sesli komutla mesaj dikte edip
otomatik **Gönder**'e basan Android uygulaması. Eller (neredeyse) serbest mesajlaşma.

## Çözüm Mimarisi
| Katman | Teknoloji |
|---|---|
| Tetikleyici | Yüzen buton (overlay) — her uygulamanın üstünde |
| Ses → Metin | Android `SpeechRecognizer`, `tr-TR` (ücretsiz, çevrimdışı paketle offline) |
| Yaz + Gönder | `AccessibilityService` — açık sohbetin kutusunu bulur, yazar, Gönder'e basar |

## Kararlar
- **Platform:** Android (iOS sandbox'ı bu otomasyona izin vermiyor — elenmişti).
- **Yapı:** Kendi APK'm; kodu Claude yazdı.
- **Derleme:** GitHub Actions (makinede Java/SDK/Studio yok → bulutta APK üretilir).
- **v1 tetikleyici:** yüzen buton. **Faz 2:** hotword (Vosk/Porcupine).

## Durum
- Kaynak kod **tam** (4 Kotlin sınıfı + res + manifest + Actions workflow).
- **Henüz cihazda derlenip test edilmedi.**

## Sonraki Adım
GitHub'a push → APK indir → telefona sideload → 3 izin → WhatsApp/Telegram test.
Detay: `README.md`.

## Bilinen Sınırlar
- Gönder butonu egzotik uygulamada bulunamazsa Enter/IME denenir; olmazsa elle gönderilir.
- v1 her mesajda butona bir kez dokunmayı gerektirir (tam eller-serbest faz 2).
