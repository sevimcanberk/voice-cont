package com.voicecont.app

import android.content.Context

/** Basit kalıcı ayarlar (SharedPreferences). */
object Prefs {
    private const val FILE = "voice_cont_prefs"
    private const val KEY_AUTO_SEND = "auto_send"

    private fun prefs(ctx: Context) =
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun isAutoSend(ctx: Context): Boolean =
        prefs(ctx).getBoolean(KEY_AUTO_SEND, true)

    fun setAutoSend(ctx: Context, value: Boolean) =
        prefs(ctx).edit().putBoolean(KEY_AUTO_SEND, value).apply()
}
