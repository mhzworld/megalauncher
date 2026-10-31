package com.megalauncher.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.Locale

val AppJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

@Serializable
data class Place(val name: String, val lat: Double, val lon: Double) {
    val key: String get() = String.format(Locale.US, "%.2f,%.2f", lat, lon)
}

@Serializable
enum class GadgetType { WEATHER, PRAYER, APP_WIDGET, SYSTEM, WORLD_CLOCK }

@Serializable
enum class SystemGaugeKind(val label: String) {
    RAM("RAM"),
    CPU("CPU"),
    TEMP("Temperature"),
    POWER("Power / thermal"),
    STORAGE("Storage"),
}

/** [opacity] = card background opacity in %, null = use the row-wide [LauncherConfig.gadgetOpacity]. */
@Serializable
data class Gadget(val type: GadgetType, val widgetId: Int = -1, val opacity: Int? = null) {
    val key: String get() = if (type == GadgetType.APP_WIDGET) "widget-$widgetId" else type.name
}

@Serializable
data class AppRow(val id: String, val title: String, val packages: List<String> = emptyList())

const val DEFAULT_PROFILE_ID = "default"

@Serializable
enum class AvatarKind { DEFAULT, PRESET, IMAGE }

/** [preset] indexes the built-in avatar icons; [version] changes whenever a custom image is imported. */
@Serializable
data class Avatar(val kind: AvatarKind = AvatarKind.DEFAULT, val preset: Int = 0, val version: Long = 0)

@Serializable
data class Profile(val id: String, val name: String, val avatar: Avatar = Avatar())

@Serializable
data class ProfilesState(val profiles: List<Profile>, val activeId: String) {
    val active: Profile get() = profiles.firstOrNull { it.id == activeId } ?: profiles.first()
}

@Serializable
enum class BackgroundType { DEFAULT, SOLID, GRADIENT, IMAGE, VIDEO }

@Serializable
enum class GradientStyle { VERTICAL, HORIZONTAL, CENTER, SQUARE, CIRCLE }

@Serializable
enum class ImageScale { STRETCH, ZOOM }

/** Date line under the clock; [pattern] is a java.text.SimpleDateFormat pattern. */
@Serializable
enum class DateStyle(val pattern: String) {
    SHORT_WEEKDAY("EEE, d MMM yyyy"),
    LONG_WEEKDAY("EEEE, d MMMM yyyy"),
    DAY_MONTH_YEAR("d MMM yyyy"),
    MONTH_DAY("EEE, MMM d"),
    NUMERIC_DMY("dd/MM/yyyy"),
    NUMERIC_MDY("MM/dd/yyyy"),
    ISO("yyyy-MM-dd"),
}

/** Colours are stored as 0xAARRGGBB. */
@Serializable
data class BackgroundConfig(
    val type: BackgroundType = BackgroundType.DEFAULT,
    val color: Long = 0xFF1E3A5F,
    val gradientStyle: GradientStyle = GradientStyle.VERTICAL,
    val gradientStart: Long = 0xFF1E3A5F,
    val gradientEnd: Long = 0xFF000000,
    val imageScale: ImageScale = ImageScale.ZOOM,
    val imageVersion: Long = 0,
    val videoVersion: Long = 0,
    val blur: Int = 0,
    val opacity: Int = 100,
    val oledShift: Boolean = false,
)

@Serializable
data class LauncherConfig(
    val initialized: Boolean = false,
    /** Legacy (pre-profiles) name, only used to name the default profile when migrating. */
    val profileName: String = "",
    val use24Hour: Boolean = true,
    val dateStyle: DateStyle = DateStyle.SHORT_WEEKDAY,
    /** Weather auto-refresh interval; 0 = manual only (cached data is still shown). */
    val weatherRefreshMinutes: Int = 60,
    /** Idle minutes on the launcher before the screensaver starts; 0 = never. */
    val screensaverTimeoutMinutes: Int = 10,
    /** The two cities shown by the world clock gadget. */
    val worldClocks: List<WorldClock> = WorldClocks.defaults,
    /** Gauges shown by the system monitor gadget. */
    val systemGauges: Set<SystemGaugeKind> = SystemGaugeKind.entries.toSet(),
    val pinned: List<String> = emptyList(),
    val gadgets: List<Gadget> = listOf(Gadget(GadgetType.WEATHER), Gadget(GadgetType.PRAYER)),
    val gadgetOpacity: Int = 60,
    val rows: List<AppRow> = emptyList(),
    val useFahrenheit: Boolean = false,
    val weatherApiKey: String = "",
    val autoLocation: Boolean = true,
    val manualLocation: Place? = null,
    val autoPrayerZone: Boolean = true,
    val prayerZone: String = "WLY01",
    val showSyuruk: Boolean = true,
    val highlightCurrentPrayer: Boolean = true,
    val background: BackgroundConfig = BackgroundConfig(),
    /** Dream service component; "" = TV default, null = not chosen yet. */
    val screensaver: String? = null,
) {
    fun hasGadget(type: GadgetType): Boolean = gadgets.any { it.type == type }
}

fun <T> List<T>.moveItem(from: Int, delta: Int): List<T> {
    if (from !in indices) return this
    val to = (from + delta).coerceIn(0, lastIndex)
    if (to == from) return this
    return toMutableList().apply { add(to, removeAt(from)) }
}
