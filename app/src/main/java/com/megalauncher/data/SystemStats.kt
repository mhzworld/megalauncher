package com.megalauncher.data

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.PowerManager
import android.os.StatFs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import kotlin.math.abs

/** One gauge reading; [fraction] is 0..1, or null when the TV doesn't expose the value. [center] replaces the default "NN%". */
data class StatGauge(val label: String, val fraction: Float?, val detail: String, val center: String? = null)

/** RAM, CPU, temperature, power (or thermal) and storage readings, using only what Android allows a normal app to read. */
class SystemStatsRepository(private val context: Context) {
    private val activityManager = context.getSystemService(ActivityManager::class.java)
    private val batteryManager = context.getSystemService(BatteryManager::class.java)
    private val powerManager = context.getSystemService(PowerManager::class.java)

    private var procStatReadable = true
    // Checked once: TVs without a battery never start reporting power draw.
    private var batteryPowerReadable = true
    private var lastIdle = -1L
    private var lastTotal = -1L

    private val cpuDirs: List<File> by lazy {
        File("/sys/devices/system/cpu").listFiles { f -> f.name.matches(CPU_DIR) }?.toList().orEmpty()
    }
    private val maxFreqs: List<Long> by lazy { cpuDirs.map { readLong(File(it, "cpufreq/cpuinfo_max_freq")) } }
    private val tempSensor: File? by lazy { findTempSensor() }

    private val readLock = Mutex()

    /** Reads only the requested gauges, in the standard order. */
    suspend fun read(kinds: Set<SystemGaugeKind>, fahrenheit: Boolean): List<StatGauge> = readLock.withLock {
        withContext(Dispatchers.IO) {
            SystemGaugeKind.entries.filter { it in kinds }.map { kind ->
                when (kind) {
                    SystemGaugeKind.RAM -> ram()
                    SystemGaugeKind.CPU -> cpu()
                    SystemGaugeKind.TEMP -> temperature(fahrenheit)
                    SystemGaugeKind.POWER -> power()
                    SystemGaugeKind.STORAGE -> storage()
                }
            }
        }
    }

    private fun temperature(fahrenheit: Boolean): StatGauge {
        val celsius = tempSensor?.let { readCelsius(it) } ?: return StatGauge("Temp", null, "Not readable")
        val shown = if (fahrenheit) String.format(Locale.US, "%.0f°F", celsius * 9 / 5 + 32) else String.format(Locale.US, "%.0f°C", celsius)
        return StatGauge("Temp", celsius / MAX_CELSIUS, "SoC", center = shown)
    }

    /** Prefers the CPU/SoC sensor; otherwise the hottest readable thermal zone. */
    private fun findTempSensor(): File? {
        val zones = File("/sys/class/thermal").listFiles { f -> f.name.startsWith("thermal_zone") }.orEmpty()
        val readable = zones.mapNotNull { zone ->
            val sensor = File(zone, "temp")
            val celsius = readCelsius(sensor) ?: return@mapNotNull null
            val type = runCatching { File(zone, "type").readText().trim().lowercase() }.getOrDefault("")
            Triple(sensor, type, celsius)
        }
        val soc = readable.firstOrNull { (_, type, _) -> SOC_SENSOR_TYPES.any { type.contains(it) } }
        return (soc ?: readable.maxByOrNull { it.third })?.first
    }

    // Kernels report either millidegrees (45000) or degrees (45).
    private fun readCelsius(file: File): Float? {
        val raw = runCatching { file.readText().trim().toFloat() }.getOrNull() ?: return null
        val celsius = if (abs(raw) > 1000) raw / 1000 else raw
        return celsius.takeIf { it in 5f..150f }
    }

    private fun ram(): StatGauge {
        val info = ActivityManager.MemoryInfo()
        activityManager?.getMemoryInfo(info)
        if (info.totalMem <= 0) return StatGauge("RAM", null, "Unavailable")
        val used = info.totalMem - info.availMem
        return StatGauge("RAM", used.toFloat() / info.totalMem, "${gb(used)} / ${gb(info.totalMem)}")
    }

    private fun storage(): StatGauge = runCatching {
        val stat = StatFs(Environment.getDataDirectory().path)
        val used = stat.totalBytes - stat.availableBytes
        StatGauge("Storage", used.toFloat() / stat.totalBytes, "${gb(used)} / ${gb(stat.totalBytes)}")
    }.getOrDefault(StatGauge("Storage", null, "Unavailable"))

    private fun cpu(): StatGauge {
        if (procStatReadable) {
            val line = runCatching { File("/proc/stat").bufferedReader().use { it.readLine() } }.getOrNull()
            val values = line?.takeIf { it.startsWith("cpu ") }
                ?.substringAfter("cpu ")?.trim()?.split(WHITESPACE)?.mapNotNull { it.toLongOrNull() }
            if (values != null && values.size >= 4) {
                val idle = values[3] + values.getOrElse(4) { 0L }
                val total = values.sum()
                val load = if (lastTotal >= 0 && total > lastTotal) {
                    1f - (idle - lastIdle).toFloat() / (total - lastTotal)
                } else {
                    null
                }
                lastIdle = idle
                lastTotal = total
                return if (load == null) {
                    StatGauge("CPU", 0f, "Measuring…", center = "…")
                } else {
                    StatGauge("CPU", load.coerceIn(0f, 1f), "Load")
                }
            }
            procStatReadable = false
        }
        // Android 8+ hides /proc/stat from apps; current clock vs maximum clock is the best available hint.
        var cur = 0L
        var max = 0L
        var fastest = 0L
        cpuDirs.forEachIndexed { i, dir ->
            val f = readLong(File(dir, "cpufreq/scaling_cur_freq"))
            val m = maxFreqs.getOrElse(i) { 0L }
            if (f > 0 && m > 0) {
                cur += f
                max += m
                if (f > fastest) fastest = f
            }
        }
        if (max <= 0) return StatGauge("CPU", null, "Unavailable")
        return StatGauge("CPU", cur.toFloat() / max, String.format(Locale.US, "%.1f GHz", fastest / 1_000_000.0))
    }

    private fun power(): StatGauge {
        batteryWatts()?.let { watts ->
            return StatGauge("Power", (watts / MAX_WATTS).toFloat().coerceIn(0f, 1f), String.format(Locale.US, "%.1f W", watts))
        }
        val pm = powerManager ?: return StatGauge("Power", null, "Unavailable")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val headroom = runCatching { pm.getThermalHeadroom(0) }.getOrDefault(Float.NaN)
            if (!headroom.isNaN()) {
                return StatGauge("Thermal", headroom.coerceIn(0f, 1f), "of throttle limit")
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val status = pm.currentThermalStatus
            return StatGauge("Thermal", status / 6f, THERMAL_LABELS.getOrElse(status) { "Unknown" })
        }
        return StatGauge("Power", null, "Not reported by TV")
    }

    /** TVs have no battery fuel gauge, so real watts are usually only available on battery-powered boxes. */
    private fun batteryWatts(): Double? {
        if (!batteryPowerReadable) return null
        val currentUa = runCatching { batteryManager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW) }.getOrNull() ?: 0
        val voltageMv = runCatching {
            context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
                ?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0)
        }.getOrNull() ?: 0
        if (currentUa == 0 || currentUa == Int.MIN_VALUE || voltageMv <= 0) {
            batteryPowerReadable = false
            return null
        }
        return abs(currentUa) / 1_000_000.0 * voltageMv / 1_000.0
    }

    private fun readLong(file: File): Long = runCatching { file.readText().trim().toLong() }.getOrDefault(0L)

    private fun gb(bytes: Long) = String.format(Locale.US, "%.1f GB", bytes / (1024.0 * 1024 * 1024))

    private companion object {
        val CPU_DIR = Regex("cpu\\d+")
        val WHITESPACE = Regex("\\s+")
        const val MAX_WATTS = 15.0
        const val MAX_CELSIUS = 100f
        val SOC_SENSOR_TYPES = listOf("cpu", "soc", "tsens", "core", "cluster", "apss")
        val THERMAL_LABELS = listOf("Normal", "Light", "Moderate", "Severe", "Critical", "Emergency", "Shutdown")
    }
}
