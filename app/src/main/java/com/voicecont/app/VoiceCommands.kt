package com.voicecont.app

/**
 * Sesli komut sözlüğü ve metin işleyici.
 * - Gönderme komutları: mesajı gönderir.
 * - Noktalama komutları: söylenen ifadeyi sembole çevirir.
 * Türkçe + İngilizce.
 */
object VoiceCommands {

    /** Mesajı gönderen komutlar (uzun ifadeler önce gelmeli). */
    val SEND_PHRASES = listOf(
        "mesajı gönder", "mesaji gonder", "mesajı gonder", "mesaji gönder",
        "send the message", "send message",
        "gönder", "gonder", "send"
    )

    /**
     * Noktalama / biçim komutları: ifade → sembol.
     * Uzun ifadeler kısa olanlardan ÖNCE eşleştirilir (linkedMap sırası korunur).
     */
    val PUNCTUATION: LinkedHashMap<String, String> = linkedMapOf(
        // --- Türkçe (uzun → kısa) ---
        "yeni paragraf" to "\n\n",
        "noktalı virgül" to ";",
        "soru işareti" to "?",
        "ünlem işareti" to "!",
        "iki nokta" to ":",
        "üç nokta" to "…",
        "nokta koy" to ".",
        "virgül koy" to ",",
        "yeni satır" to "\n",
        "alt satır" to "\n",
        "parantez aç" to "(",
        "parantez kapat" to ")",
        "tırnak aç" to "\"",
        "tırnak kapat" to "\"",
        "soru" to "?",
        "ünlem" to "!",
        "nokta" to ".",
        "virgül" to ",",
        // --- İngilizce (uzun → kısa) ---
        "new paragraph" to "\n\n",
        "new line" to "\n",
        "question mark" to "?",
        "exclamation mark" to "!",
        "exclamation point" to "!",
        "full stop" to ".",
        "open paren" to "(",
        "close paren" to ")",
        "open bracket" to "(",
        "close bracket" to ")",
        "semicolon" to ";",
        "ellipsis" to "…",
        "colon" to ":",
        "comma" to ",",
        "period" to ".",
        "dot" to "."
    )

    /** Sembolden ÖNCE boşluk istemeyen (öncekine yapışan) noktalama. */
    private const val ATTACH_LEFT = ".,?!:;…)\""

    /** Sembolden SONRA gelen kelimenin yapışacağı (boşluk istemeyen) noktalama. */
    private const val ATTACH_RIGHT = "(\n"

    sealed class Token {
        data class Word(val text: String) : Token()
        data class Punct(val symbol: String) : Token()
        object Send : Token()
    }

    /** Tek bir tanıma parçasını işler. */
    data class Result(val isSend: Boolean, val tokens: List<Token>)

    /**
     * Ham metni tokenlara böler. En sonda gönderme komutu varsa isSend=true.
     * Çok kelimeli komutları (3→2→1 pencere) önce dener.
     */
    fun parse(chunk: String): Result {
        val words = chunk.trim().lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
        val originalWords = chunk.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        val tokens = mutableListOf<Token>()
        var i = 0
        var isSend = false

        while (i < words.size) {
            var consumed = 0
            var token: Token? = null

            // 3→2→1 kelimelik pencereyle komut eşleştir
            for (win in 3 downTo 1) {
                if (i + win > words.size) continue
                val phrase = words.subList(i, i + win).joinToString(" ")
                if (i + win == words.size && SEND_PHRASES.contains(phrase)) {
                    token = Token.Send; consumed = win; break
                }
                val sym = PUNCTUATION[phrase]
                if (sym != null) {
                    token = Token.Punct(sym); consumed = win; break
                }
            }

            if (token == null) {
                tokens.add(Token.Word(originalWords[i])); i += 1
            } else if (token is Token.Send) {
                isSend = true; break
            } else {
                tokens.add(token); i += consumed
            }
        }
        return Result(isSend, tokens)
    }

    /**
     * Parçanın SONUNDA gönderme komutu varsa ayıklar.
     * @return (komut hariç ham metin, gönderme komutu var mı)
     */
    fun stripTrailingSend(chunk: String): Pair<String, Boolean> {
        val trimmed = chunk.trim()
        val lower = trimmed.lowercase()
        for (cmd in SEND_PHRASES) {  // uzun ifadeler önce
            if (lower == cmd) return "" to true
            if (lower.endsWith(" $cmd")) {
                return trimmed.substring(0, trimmed.length - cmd.length).trim() to true
            }
        }
        return trimmed to false
    }

    /** Token listesini boşluk kurallarına uygun düz metne çevirir. */
    fun render(tokens: List<Token>): String {
        val sb = StringBuilder()
        for (t in tokens) {
            when (t) {
                is Token.Word -> {
                    if (sb.isNotEmpty()) {
                        val last = sb.last()
                        if (last != ' ' && last !in ATTACH_RIGHT) sb.append(' ')
                    }
                    sb.append(t.text)
                }
                is Token.Punct -> {
                    val sym = t.symbol
                    if (sym.isNotEmpty() && sym[0] in ATTACH_LEFT) {
                        // öncekine yapıştır: sondaki boşlukları sil
                        while (sb.isNotEmpty() && sb.last() == ' ') sb.deleteCharAt(sb.length - 1)
                        sb.append(sym)
                    } else {
                        // açılış parantez / yeni satır vb.
                        if (sb.isNotEmpty() && sb.last() != ' ' && sb.last() != '\n') sb.append(' ')
                        sb.append(sym)
                    }
                }
                Token.Send -> {}
            }
        }
        return sb.toString()
    }
}
