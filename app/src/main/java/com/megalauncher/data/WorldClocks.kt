package com.megalauncher.data

import kotlinx.serialization.Serializable
import java.time.DateTimeException
import java.time.Instant
import java.time.ZoneId

@Serializable
data class WorldClock(val city: String, val zoneId: String)

object WorldClocks {
    val defaults = listOf(WorldClock("London", "Europe/London"), WorldClock("Los Angeles", "America/Los_Angeles"))

    /** Shown before the user searches; includes common names that aren't time zone IDs (e.g. Mecca, Beijing). */
    val popular = listOf(
        WorldClock("Kuala Lumpur", "Asia/Kuala_Lumpur"),
        WorldClock("Singapore", "Asia/Singapore"),
        WorldClock("Jakarta", "Asia/Jakarta"),
        WorldClock("Bangkok", "Asia/Bangkok"),
        WorldClock("Manila", "Asia/Manila"),
        WorldClock("Hong Kong", "Asia/Hong_Kong"),
        WorldClock("Beijing", "Asia/Shanghai"),
        WorldClock("Seoul", "Asia/Seoul"),
        WorldClock("Tokyo", "Asia/Tokyo"),
        WorldClock("Sydney", "Australia/Sydney"),
        WorldClock("Perth", "Australia/Perth"),
        WorldClock("Auckland", "Pacific/Auckland"),
        WorldClock("New Delhi", "Asia/Kolkata"),
        WorldClock("Dhaka", "Asia/Dhaka"),
        WorldClock("Karachi", "Asia/Karachi"),
        WorldClock("Dubai", "Asia/Dubai"),
        WorldClock("Mecca", "Asia/Riyadh"),
        WorldClock("Medina", "Asia/Riyadh"),
        WorldClock("Istanbul", "Europe/Istanbul"),
        WorldClock("Cairo", "Africa/Cairo"),
        WorldClock("Johannesburg", "Africa/Johannesburg"),
        WorldClock("Moscow", "Europe/Moscow"),
        WorldClock("Berlin", "Europe/Berlin"),
        WorldClock("Paris", "Europe/Paris"),
        WorldClock("London", "Europe/London"),
        WorldClock("New York", "America/New_York"),
        WorldClock("Washington DC", "America/New_York"),
        WorldClock("Toronto", "America/Toronto"),
        WorldClock("Chicago", "America/Chicago"),
        WorldClock("Los Angeles", "America/Los_Angeles"),
        WorldClock("San Francisco", "America/Los_Angeles"),
        WorldClock("Mexico City", "America/Mexico_City"),
        WorldClock("São Paulo", "America/Sao_Paulo"),
        WorldClock("Honolulu", "Pacific/Honolulu"),
    )

    /** Popular cities plus every region/city time zone the TV knows, e.g. "Asia/Kuching" -> "Kuching". */
    val all: List<WorldClock> by lazy {
        val fromZones = ZoneId.getAvailableZoneIds()
            .filter { '/' in it && !it.startsWith("Etc/") && !it.startsWith("SystemV/") }
            .map { WorldClock(it.substringAfterLast('/').replace('_', ' '), it) }
        (popular + fromZones).distinctBy { it.city.lowercase() }.sortedBy { it.city.lowercase() }
    }

    fun zone(clock: WorldClock): ZoneId = try {
        ZoneId.of(clock.zoneId)
    } catch (e: DateTimeException) {
        ZoneId.systemDefault()
    }

    /** e.g. "UTC+8", "UTC+5:30", "UTC−3". */
    fun utcOffset(clock: WorldClock): String {
        val seconds = zone(clock).rules.getOffset(Instant.now()).totalSeconds
        val sign = if (seconds < 0) "−" else "+"
        val abs = kotlin.math.abs(seconds)
        val hours = abs / 3600
        val minutes = abs % 3600 / 60
        return if (minutes == 0) "UTC$sign$hours" else "UTC$sign$hours:${minutes.toString().padStart(2, '0')}"
    }
}
