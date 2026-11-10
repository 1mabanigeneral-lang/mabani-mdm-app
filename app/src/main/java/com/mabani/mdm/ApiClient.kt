package com.mabani.mdm

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import javax.net.ssl.HttpsURLConnection

/**
 * Minimal JSON/HTTP client for talking to the MDM backend
 * (mdm.1mabanihr.com, the api folder's PHP endpoints). No external
 * libraries — just HttpURLConnection, to keep the build small and
 * dependency-free.
 */
object ApiClient {
    private const val TAG = "MdmApi"

    data class Result(val ok: Boolean, val json: JSONObject?, val error: String? = null)

    private fun post(ctx: Context, path: String, body: JSONObject, apiKey: String? = null): Result {
        return try {
            val url = URL(Prefs.serverBaseUrl(ctx) + path)
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.connectTimeout = 15000
            conn.readTimeout = 15000
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            if (apiKey != null) conn.setRequestProperty("X-API-Key", apiKey)
            if (conn is HttpsURLConnection) { /* standard system trust store is fine, Let's Encrypt is trusted */ }

            conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }

            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() } ?: "{}"
            conn.disconnect()

            val json = JSONObject(text)
            Result(json.optBoolean("ok", false), json)
        } catch (e: Exception) {
            Log.w(TAG, "request to $path failed: ${e.message}")
            Result(false, null, e.message)
        }
    }

    fun enroll(ctx: Context, enrollToken: String, model: String, androidVersion: String): Result {
        val body = JSONObject()
            .put("enroll_token", enrollToken)
            .put("model", model)
            .put("android_version", androidVersion)
        return post(ctx, "/api/enroll.php", body)
    }

    fun heartbeat(ctx: Context, apiKey: String, lat: Double?, lng: Double?, batteryPct: Int?): Result {
        val body = JSONObject()
        if (lat != null) body.put("lat", lat)
        if (lng != null) body.put("lng", lng)
        if (batteryPct != null) body.put("battery_pct", batteryPct)
        return post(ctx, "/api/heartbeat.php", body, apiKey)
    }

    fun ack(ctx: Context, apiKey: String, commandId: Int, result: String): Result {
        val body = JSONObject().put("command_id", commandId).put("result", result)
        return post(ctx, "/api/ack.php", body, apiKey)
    }

    /** Pulls out the pending-commands array from a heartbeat response. */
    fun commandsFrom(json: JSONObject?): List<JSONObject> {
        val arr: JSONArray = json?.optJSONArray("commands") ?: return emptyList()
        return (0 until arr.length()).map { arr.getJSONObject(it) }
    }
}
