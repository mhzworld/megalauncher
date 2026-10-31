package com.megalauncher.ui

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ActivityNotFoundException
import android.content.Intent
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.tv.material3.Text
import com.megalauncher.LauncherViewModel
import com.megalauncher.PrayerState
import com.megalauncher.WeatherState
import com.megalauncher.data.LauncherConfig
import com.megalauncher.data.PrayerDay
import com.megalauncher.data.PrayerSlot
import com.megalauncher.data.WeatherData
import com.megalauncher.data.currentSlot
import com.megalauncher.data.slots
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

private fun formatTemp(celsius: Double, fahrenheit: Boolean, withUnit: Boolean = false): String {
    val value = if (fahrenheit) celsius * 9 / 5 + 32 else celsius
    val unit = if (withUnit) (if (fahrenheit) "F" else "C") else ""
    return "${value.roundToInt()}°$unit"
}

// Hosts collect their own flows so per-minute clock ticks only recompose the gadget, not the whole home screen.
@Composable
fun WeatherGadgetHost(vm: LauncherViewModel, fahrenheit: Boolean, is24: Boolean) {
    val weather by vm.weather.collectAsStateWithLifecycle()
    val now by vm.now.collectAsStateWithLifecycle()
    val online by vm.online.collectAsStateWithLifecycle()
    WeatherGadget(weather, fahrenheit, now, online, is24)
}

@Composable
fun PrayerGadgetHost(vm: LauncherViewModel, config: LauncherConfig) {
    val prayer by vm.prayer.collectAsStateWithLifecycle()
    val now by vm.now.collectAsStateWithLifecycle()
    PrayerGadget(prayer, now, config.autoPrayerZone, config.showSyuruk, config.highlightCurrentPrayer, config.use24Hour)
}

@Composable
fun WeatherGadget(state: WeatherState, fahrenheit: Boolean, now: Long, online: Boolean, is24: Boolean) {
    val data = state.data
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                data?.placeName ?: "Weather",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            val status = when {
                state.loading -> "Updating…"
                data != null -> {
                    val time = SimpleDateFormat(if (is24) "HH:mm" else "h:mm a", Locale.getDefault())
                        .format(Date(data.fetchedAt)).uppercase(Locale.getDefault())
                    "Updated $time" + if (online) "" else " · offline"
                }
                else -> ""
            }
            Text(status, fontSize = 11.sp, color = LauncherColors.TextSecondary)
        }

        if (data == null) {
            Spacer(Modifier.weight(1f))
            Text(state.error ?: "Loading weather…", fontSize = 15.sp, color = LauncherColors.TextSecondary)
            Spacer(Modifier.weight(1f))
        } else {
            WeatherDetails(data, fahrenheit, now, is24)
        }
    }
}

@Composable
private fun ColumnScope.WeatherDetails(data: WeatherData, fahrenheit: Boolean, now: Long, is24: Boolean) {
    // If the cached observation is old, show the forecast for the current hour instead.
    val nowSec = now / 1000
    val observationIsOld = now - data.fetchedAt > 90 * 60 * 1000L
    val currentHour = if (observationIsOld) data.hourly.lastOrNull { it.epochSec <= nowSec } else null

    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
        Text(currentHour?.icon ?: data.icon, fontSize = 32.sp)
        Spacer(Modifier.width(6.dp))
        Text(formatTemp(currentHour?.tempC ?: data.tempC, fahrenheit, withUnit = true), fontSize = 34.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            if (currentHour == null) {
                Text(data.description, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                data.feelsLikeC?.let {
                    Text("Feels ${formatTemp(it, fahrenheit)}", fontSize = 12.sp, color = LauncherColors.TextSecondary, maxLines = 1)
                }
                data.humidity?.let {
                    Text("Humidity $it%", fontSize = 12.sp, color = LauncherColors.TextSecondary, maxLines = 1)
                }
            } else {
                Text("Forecast", fontSize = 13.sp, color = LauncherColors.TextSecondary)
            }
        }
    }

    Spacer(Modifier.weight(1f))
    val upcoming = data.hourly.filter { it.epochSec > nowSec }.take(5)
    if (upcoming.isEmpty()) {
        Text("Hourly forecast unavailable", fontSize = 12.sp, color = LauncherColors.TextSecondary)
    } else {
        val hourFormat = SimpleDateFormat(if (is24) "HH:mm" else "h a", Locale.getDefault())
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            upcoming.forEach { hour ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        hourFormat.format(Date(hour.epochSec * 1000)).uppercase(Locale.getDefault()),
                        fontSize = 12.sp,
                        color = LauncherColors.TextSecondary,
                    )
                    Text(hour.icon, fontSize = 18.sp)
                    Text(formatTemp(hour.tempC, fahrenheit), fontSize = 14.sp, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

private val HIJRI_MONTHS = listOf(
    "Muharram", "Safar", "Rabiulawal", "Rabiulakhir", "Jamadilawal", "Jamadilakhir",
    "Rejab", "Syaaban", "Ramadan", "Syawal", "Zulkaedah", "Zulhijjah",
)

private fun formatHijri(value: String): String {
    val parts = value.split("-")
    if (parts.size != 3) return value
    val month = parts[1].toIntOrNull()?.let { HIJRI_MONTHS.getOrNull(it - 1) } ?: return value
    return "${parts[2].toIntOrNull() ?: parts[2]} $month ${parts[0]}H"
}

private fun formatPrayerTime(hhmm: String, is24: Boolean): String = runCatching {
    LocalTime.parse(hhmm).format(DateTimeFormatter.ofPattern(if (is24) "HH:mm" else "h:mm a", Locale.getDefault()))
        .uppercase(Locale.getDefault())
}.getOrDefault(hhmm)

@Composable
fun PrayerGadget(
    state: PrayerState,
    now: Long,
    autoZone: Boolean,
    showSyuruk: Boolean,
    highlightCurrent: Boolean,
    is24: Boolean,
) {
    val day = state.day
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Waktu Solat", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Text(
                "  ${state.zone.code}${if (autoZone) " (auto)" else ""}",
                fontSize = 12.sp,
                color = LauncherColors.TextSecondary,
            )
            Spacer(Modifier.weight(1f))
            if (day != null && day.hijri.isNotBlank()) {
                Text(formatHijri(day.hijri), fontSize = 12.sp, color = LauncherColors.TextSecondary)
            }
        }
        Text(
            state.zone.areas,
            fontSize = 11.sp,
            color = LauncherColors.TextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        if (day == null) {
            Spacer(Modifier.weight(1f))
            Text(
                if (state.loading) "Loading prayer times…" else state.error ?: "Prayer times unavailable",
                fontSize = 15.sp,
                color = LauncherColors.TextSecondary,
            )
            Spacer(Modifier.weight(1f))
        } else {
            Spacer(Modifier.height(10.dp))
            PrayerTimesRow(day, now, is24, showSyuruk, highlightCurrent, Modifier.fillMaxWidth().weight(1f))
        }
    }
}

@Composable
private fun PrayerTimesRow(
    day: PrayerDay,
    now: Long,
    is24: Boolean,
    showSyuruk: Boolean,
    highlightCurrent: Boolean,
    modifier: Modifier,
) {
    val time = Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault()).toLocalTime()
    val current = if (highlightCurrent) day.currentSlot(time) else null
    Row(
        modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        day.slots().filter { showSyuruk || it.first != PrayerSlot.SYURUK }.forEach { (slot, value) ->
            val active = slot == current
            Column(
                Modifier
                    .weight(if (active) 1.6f else 1f)
                    .fillMaxHeight(if (active) 1f else 0.8f)
                    .background(
                        if (active) LauncherColors.PrayerActive else Color.White.copy(alpha = 0.06f),
                        RoundedCornerShape(10.dp),
                    )
                    .padding(vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    slot.label,
                    fontSize = if (active) 17.sp else 12.sp,
                    fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                    color = if (slot == PrayerSlot.SYURUK && !active) LauncherColors.TextSecondary else Color.White,
                    maxLines = 1,
                )
                Text(
                    formatPrayerTime(value, is24),
                    fontSize = if (active) 24.sp else 14.sp,
                    fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
fun AppWidgetGadget(vm: LauncherViewModel, widgetId: Int) {
    AndroidView(
        factory = { ctx ->
            val view: View = vm.widgetHost.createView(ctx, widgetId)?.apply {
                // D-pad focus stays on the launcher card; widget content is display-only.
                descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
            } ?: TextView(ctx).apply {
                text = "Widget unavailable"
                setTextColor(android.graphics.Color.WHITE)
                gravity = Gravity.CENTER
            }
            view
        },
        modifier = Modifier.fillMaxSize().padding(8.dp),
    )
}

/**
 * Bind (with user consent if needed) and configure an app widget, then add it as a gadget.
 * [onBindBlocked] is called when the TV refuses to let the launcher bind widgets.
 */
@Composable
fun rememberWidgetAdder(vm: LauncherViewModel, onBindBlocked: () -> Unit): (AppWidgetProviderInfo) -> Unit {
    var pendingId by remember { mutableStateOf<Int?>(null) }
    var pendingInfo by remember { mutableStateOf<AppWidgetProviderInfo?>(null) }

    fun finish(add: Boolean) {
        val id = pendingId ?: return
        if (add) vm.addAppWidget(id) else vm.widgetHost.delete(id)
        pendingId = null
        pendingInfo = null
    }

    val configureLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        finish(it.resultCode == Activity.RESULT_OK)
    }

    fun configureOrAdd() {
        val id = pendingId ?: return
        val configure = pendingInfo?.configure ?: return finish(true)
        try {
            configureLauncher.launch(
                Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE)
                    .setComponent(configure)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id),
            )
        } catch (e: ActivityNotFoundException) {
            finish(true)
        } catch (e: SecurityException) {
            finish(true)
        }
    }

    val bindLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (it.resultCode == Activity.RESULT_OK) {
            configureOrAdd()
        } else {
            finish(false)
            onBindBlocked()
        }
    }

    return { info ->
        val id = vm.widgetHost.allocate()
        if (id == null) {
            vm.toast("App widgets are not supported on this device")
        } else {
            pendingId = id
            pendingInfo = info
            if (vm.widgetHost.bindIfAllowed(id, info)) {
                configureOrAdd()
            } else {
                try {
                    bindLauncher.launch(
                        Intent(AppWidgetManager.ACTION_APPWIDGET_BIND)
                            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, info.provider),
                    )
                } catch (e: ActivityNotFoundException) {
                    finish(false)
                    onBindBlocked()
                }
            }
        }
    }
}
