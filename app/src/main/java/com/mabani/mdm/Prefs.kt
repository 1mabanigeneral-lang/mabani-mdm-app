package com.mabani.mdm

import android.content.Context

/**
 * Tiny SharedPreferences wrapper holding this device's enrollment state.
 */
object Prefs {
    private const val FILE = "mdm_prefs"
    private const val KEY_ENROLL_TOKEN = "enroll_token"
    private const val KEY_API_KEY = "api_key"
    private const val KEY_SERVER = "server_base_url"

    // Change this if the MDM backend ever moves to a different domain.
    const val DEFAULT_SERVER = "https://mdm.1mabanihr.com"

    fun serverBaseUrl(ctx: Context): String =
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString(KEY_SERVER, DEFAULT_SERVER)
            ?: DEFAULT_SERVER

    fun enrollToken(ctx: Context): String? =
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString(KEY_ENROLL_TOKEN, null)

    fun saveEnrollToken(ctx: Context, token: String) {
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
            .putString(KEY_ENROLL_TOKEN, token).apply()
    }

    fun apiKey(ctx: Context): String? =
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString(KEY_API_KEY, null)

    fun saveApiKey(ctx: Context, key: String) {
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
            .putString(KEY_API_KEY, key).apply()
    }

    fun isEnrolled(ctx: Context): Boolean = apiKey(ctx) != null
}
