package com.megalauncher.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Stores one [LauncherConfig] per profile; the default profile keeps the original "config" key. */
class ConfigStore(context: Context) {
    private val prefs = context.getSharedPreferences("launcher", Context.MODE_PRIVATE)

    fun load(profileId: String): LauncherConfig? = prefs.getString(key(profileId), null)?.let {
        runCatching { AppJson.decodeFromString<LauncherConfig>(it) }.getOrNull()
    }

    fun save(profileId: String, config: LauncherConfig) {
        prefs.edit().putString(key(profileId), AppJson.encodeToString(config)).apply()
    }

    fun delete(profileId: String) {
        prefs.edit().remove(key(profileId)).apply()
    }

    private fun key(profileId: String) = if (profileId == DEFAULT_PROFILE_ID) "config" else "config_$profileId"
}

object FileCache {
    suspend inline fun <reified T> read(file: File): T? = withContext(Dispatchers.IO) {
        if (!file.exists()) return@withContext null
        runCatching { AppJson.decodeFromString<T>(file.readText()) }.getOrNull()
    }

    suspend inline fun <reified T> write(file: File, value: T) = withContext(Dispatchers.IO) {
        runCatching {
            val tmp = File(file.parentFile, file.name + ".tmp")
            tmp.writeText(AppJson.encodeToString(value))
            tmp.renameTo(file)
        }
        Unit
    }
}

object Net {
    private const val USER_AGENT = "MegaLauncher/1.0 (Android TV)"

    suspend fun get(url: String): String = request(url, null)

    suspend fun postForm(url: String, form: Map<String, String>): String =
        request(url, form.entries.joinToString("&") { "${encode(it.key)}=${encode(it.value)}" })

    fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")

    suspend fun download(url: String, dest: File, maxBytes: Long) = withContext(Dispatchers.IO) {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 10_000
            conn.readTimeout = 30_000
            conn.setRequestProperty("User-Agent", USER_AGENT)
            val code = conn.responseCode
            if (code !in 200..299) throw IOException("HTTP $code")
            if (conn.contentLengthLong > maxBytes) throw IOException("File is too large")
            conn.inputStream.use { copyLimited(it, dest, maxBytes) }
        } finally {
            conn.disconnect()
        }
    }

    fun copyLimited(input: InputStream, dest: File, maxBytes: Long) {
        dest.outputStream().use { out ->
            val buffer = ByteArray(64 * 1024)
            var total = 0L
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                total += n
                if (total > maxBytes) throw IOException("File is too large")
                out.write(buffer, 0, n)
            }
        }
    }

    /** Only checks for an internet-capable network; "validated" relies on Google's connectivity check. */
    fun isOnline(context: Context): Boolean {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return false
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private suspend fun request(url: String, formBody: String?): String = withContext(Dispatchers.IO) {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 10_000
            conn.readTimeout = 15_000
            conn.setRequestProperty("User-Agent", USER_AGENT)
            conn.setRequestProperty("Accept", "application/json")
            if (formBody != null) {
                conn.requestMethod = "POST"
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                conn.outputStream.use { it.write(formBody.toByteArray()) }
            }
            val code = conn.responseCode
            if (code !in 200..299) throw IOException("HTTP $code")
            if (conn.contentLengthLong > MAX_RESPONSE_BYTES) throw IOException("Response is too large")
            conn.inputStream.use { input ->
                val out = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(16 * 1024)
                while (true) {
                    val n = input.read(buffer)
                    if (n < 0) break
                    if (out.size() + n > MAX_RESPONSE_BYTES) throw IOException("Response is too large")
                    out.write(buffer, 0, n)
                }
                out.toString("UTF-8")
            }
        } finally {
            conn.disconnect()
        }
    }

    // API responses (weather, prayer times, geocoding) are small; this guards against runaway servers.
    private const val MAX_RESPONSE_BYTES = 4 * 1024 * 1024
}
