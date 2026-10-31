package com.megalauncher.data

import android.content.Context
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import org.json.JSONObject
import java.io.File
import java.io.IOException

@Serializable
private data class CachedPlace(val place: Place, val fetchedAt: Long)

class LocationRepository(context: Context) {
    private val file = File(context.filesDir, "ip_location.json")
    private val mutex = Mutex()

    /** Manual location from settings, otherwise an IP-based lookup cached for [IP_TTL_MS]. */
    suspend fun resolve(config: LauncherConfig, online: Boolean): Place? {
        if (!config.autoLocation) return config.manualLocation
        return mutex.withLock {
            val cached = FileCache.read<CachedPlace>(file)
            val fresh = cached != null && System.currentTimeMillis() - cached.fetchedAt < IP_TTL_MS
            if (fresh || !online) return@withLock cached?.place
            try {
                lookupByIp().also { FileCache.write(file, CachedPlace(it, System.currentTimeMillis())) }
            } catch (e: IOException) {
                cached?.place
            } catch (e: org.json.JSONException) {
                cached?.place
            }
        }
    }

    suspend fun search(query: String): List<Place> {
        val url = "https://geocoding-api.open-meteo.com/v1/search?name=${Net.encode(query)}&count=10&language=en&format=json"
        val results = JSONObject(Net.get(url)).optJSONArray("results") ?: return emptyList()
        return (0 until results.length()).map { i ->
            val o = results.getJSONObject(i)
            val name = listOf(o.optString("name"), o.optString("admin1"), o.optString("country"))
                .filter { it.isNotBlank() }.distinct().joinToString(", ")
            Place(name, o.getDouble("latitude"), o.getDouble("longitude"))
        }
    }

    private suspend fun lookupByIp(): Place {
        val json = JSONObject(Net.get("https://ipwho.is/"))
        if (!json.optBoolean("success", false)) throw IOException(json.optString("message", "IP lookup failed"))
        val name = json.optString("city").ifBlank { json.optString("region") }.ifBlank { "Current location" }
        return Place(name, json.getDouble("latitude"), json.getDouble("longitude"))
    }

    private companion object {
        const val IP_TTL_MS = 12L * 60 * 60 * 1000
    }
}
