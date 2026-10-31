package com.megalauncher.data

import android.content.Context
import kotlinx.serialization.Serializable
import org.json.JSONObject
import java.io.File

@Serializable
data class HourForecast(val epochSec: Long, val tempC: Double, val icon: String, val precipChance: Int? = null)

@Serializable
data class WeatherData(
    val placeName: String,
    val locationKey: String,
    val tempC: Double,
    val feelsLikeC: Double? = null,
    val description: String,
    val icon: String,
    val humidity: Int? = null,
    val hourly: List<HourForecast> = emptyList(),
    val source: String,
    val fetchedAt: Long,
)

class WeatherRepository(context: Context) {
    private val file = File(context.filesDir, "weather_cache.json")

    suspend fun cached(): WeatherData? = FileCache.read(file)

    /** Uses The Weather Company (weather.com) when an API key is set, otherwise Open-Meteo. */
    suspend fun fetch(place: Place, apiKey: String): WeatherData {
        val data = if (apiKey.isNotBlank()) fetchWeatherCom(place, apiKey.trim()) else fetchOpenMeteo(place)
        FileCache.write(file, data)
        return data
    }

    private suspend fun fetchWeatherCom(place: Place, apiKey: String): WeatherData {
        val query = "geocode=${place.lat},${place.lon}&units=m&language=en-US&format=json&apiKey=${Net.encode(apiKey)}"
        val current = JSONObject(Net.get("https://api.weather.com/v3/wx/observations/current?$query"))
        val hourly = runCatching {
            val h = JSONObject(Net.get("https://api.weather.com/v3/wx/forecast/hourly/2day?$query"))
            val times = h.getJSONArray("validTimeUtc")
            val temps = h.getJSONArray("temperature")
            val icons = h.getJSONArray("iconCode")
            val precip = h.optJSONArray("precipChance")
            (0 until times.length()).map { i ->
                HourForecast(times.getLong(i), temps.getDouble(i), WeatherIcons.weatherCom(icons.optInt(i, 44)), precip?.optInt(i))
            }
        }.getOrDefault(emptyList())
        // Some weather.com key tiers exclude hourly forecasts; fill those from Open-Meteo.
        val hours = hourly.ifEmpty { runCatching { fetchOpenMeteo(place).hourly }.getOrDefault(emptyList()) }
        return WeatherData(
            placeName = place.name,
            locationKey = place.key,
            tempC = current.getDouble("temperature"),
            feelsLikeC = current.optDouble("temperatureFeelsLike").takeIf { !it.isNaN() },
            description = current.optString("wxPhraseLong"),
            icon = WeatherIcons.weatherCom(current.optInt("iconCode", 44)),
            humidity = current.optInt("relativeHumidity", -1).takeIf { it >= 0 },
            hourly = hours,
            source = "weather.com",
            fetchedAt = System.currentTimeMillis(),
        )
    }

    private suspend fun fetchOpenMeteo(place: Place): WeatherData {
        val url = "https://api.open-meteo.com/v1/forecast?latitude=${place.lat}&longitude=${place.lon}" +
            "&current=temperature_2m,apparent_temperature,relative_humidity_2m,weather_code,is_day" +
            "&hourly=temperature_2m,weather_code,precipitation_probability,is_day" +
            "&forecast_days=2&timezone=auto&timeformat=unixtime"
        val json = JSONObject(Net.get(url))
        val c = json.getJSONObject("current")
        val h = json.getJSONObject("hourly")
        val times = h.getJSONArray("time")
        val temps = h.getJSONArray("temperature_2m")
        val codes = h.getJSONArray("weather_code")
        val precip = h.optJSONArray("precipitation_probability")
        val isDay = h.optJSONArray("is_day")
        val hours = (0 until times.length()).map { i ->
            HourForecast(
                times.getLong(i),
                temps.getDouble(i),
                WeatherIcons.wmo(codes.optInt(i), isDay?.optInt(i, 1) != 0).first,
                precip?.optInt(i),
            )
        }
        val (icon, description) = WeatherIcons.wmo(c.optInt("weather_code"), c.optInt("is_day", 1) != 0)
        return WeatherData(
            placeName = place.name,
            locationKey = place.key,
            tempC = c.getDouble("temperature_2m"),
            feelsLikeC = c.optDouble("apparent_temperature").takeIf { !it.isNaN() },
            description = description,
            icon = icon,
            humidity = c.optInt("relative_humidity_2m", -1).takeIf { it >= 0 },
            hourly = hours,
            source = "Open-Meteo",
            fetchedAt = System.currentTimeMillis(),
        )
    }
}

object WeatherIcons {
    fun weatherCom(code: Int): String = when (code) {
        0, 1, 2, 3, 4, 37, 38, 47 -> "⛈️"
        5, 6, 7, 8, 9, 10, 11, 12, 35, 40 -> "🌧️"
        39, 45 -> "🌦️"
        13, 14, 15, 16, 17, 18, 41, 42, 43, 46 -> "❄️"
        19, 20, 21, 22 -> "🌫️"
        23, 24 -> "💨"
        26, 27, 28 -> "☁️"
        29, 31, 33 -> "🌙"
        30, 34 -> "🌤️"
        32, 36 -> "☀️"
        else -> "🌡️"
    }

    fun wmo(code: Int, isDay: Boolean): Pair<String, String> = when (code) {
        0 -> (if (isDay) "☀️" else "🌙") to "Clear"
        1 -> (if (isDay) "🌤️" else "🌙") to "Mainly clear"
        2 -> (if (isDay) "⛅" else "☁️") to "Partly cloudy"
        3 -> "☁️" to "Overcast"
        45, 48 -> "🌫️" to "Fog"
        in 51..57 -> "🌦️" to "Drizzle"
        in 61..67 -> "🌧️" to "Rain"
        in 71..77 -> "❄️" to "Snow"
        in 80..82 -> "🌧️" to "Rain showers"
        85, 86 -> "🌨️" to "Snow showers"
        in 95..99 -> "⛈️" to "Thunderstorm"
        else -> "🌡️" to "—"
    }
}
