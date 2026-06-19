package com.voicecont.app

import android.content.Context

/** Basit kalıcı ayarlar (SharedPreferences). */
object Prefs {
    private const val FILE = "voice_cont_prefs"
    private const val KEY_AUTO_SEND = "auto_send"
    private const val KEY_LANG = "dictation_lang"

    const val LANG_TR = "tr-TR"
    const val LANG_EN = "en-US"

    private fun prefs(ctx: Context) =
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun isAutoSend(ctx: Context): Boolean =
        prefs(ctx).getBoolean(KEY_AUTO_SEND, true)

    fun setAutoSend(ctx: Context, value: Boolean) =
        prefs(ctx).edit().putBoolean(KEY_AUTO_SEND, value).apply()

    /** Dikte dili: yüzen butona uzun basıp TR/EN seçilir. Varsayılan Türkçe. */
    fun lang(ctx: Context): String =
        prefs(ctx).getString(KEY_LANG, LANG_TR) ?: LANG_TR

    fun setLang(ctx: Context, value: String) =
        prefs(ctx).edit().putString(KEY_LANG, value).apply()
}
