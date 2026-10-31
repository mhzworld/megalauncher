package com.megalauncher.ui

import android.appwidget.AppWidgetProviderInfo
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.tv.material3.Text
import com.megalauncher.LauncherViewModel
import com.megalauncher.data.AppRow
import com.megalauncher.data.Gadget
import com.megalauncher.data.GadgetType
import com.megalauncher.data.Place
import com.megalauncher.data.PrayerZones
import com.megalauncher.data.SystemGaugeKind
import com.megalauncher.data.WorldClock
import com.megalauncher.data.WorldClocks
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Per-gadget background opacity; changes apply live. [opacity] null = follow the row setting. */
@Composable
fun GadgetOpacityDialog(opacity: Int?, rowOpacity: Int, onChange: (Int?) -> Unit, onDismiss: () -> Unit) {
    val stepper = remember { FocusRequester() }
    val current = opacity ?: rowOpacity
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .ignoreHeldSelectKey()
                .width(520.dp)
                .background(LauncherColors.Panel, RoundedCornerShape(20.dp))
                .padding(24.dp),
        ) {
            Text("Gadget background opacity", fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            Text("Press ◀ ▶ to adjust", fontSize = 13.sp, color = LauncherColors.TextSecondary)
            Spacer(Modifier.height(16.dp))
            SettingStepper(
                "Opacity",
                if (opacity == null) "$current% (all gadgets)" else "$current%",
                onDecrease = { onChange((current - 10).coerceAtLeast(0)) },
                onIncrease = { onChange((current + 10).coerceAtMost(100)) },
                modifier = Modifier.fillMaxWidth().focusRequester(stepper),
            )
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PillButton("Done", onClick = onDismiss)
                if (opacity != null) PillButton("Use setting for all gadgets") { onChange(null) }
            }
        }
        LaunchedEffect(Unit) {
            delay(50)
            stepper.safeRequestFocus()
        }
    }
}

/** Toggle which system monitor gauges are shown; hiding all of them turns the gadget off. */
@Composable
fun SystemGaugesDialog(visible: Set<SystemGaugeKind>, onToggle: (SystemGaugeKind, Boolean) -> Unit, onDismiss: () -> Unit) {
    val first = remember { FocusRequester() }
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .ignoreHeldSelectKey()
                .width(460.dp)
                .background(LauncherColors.Panel, RoundedCornerShape(20.dp))
                .padding(24.dp),
        ) {
            Text("System monitor gauges", fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            Text("Hiding every gauge turns the gadget off.", fontSize = 13.sp, color = LauncherColors.TextSecondary)
            Spacer(Modifier.height(14.dp))
            SystemGaugeKind.entries.forEachIndexed { index, kind ->
                MenuButton(
                    kind.label,
                    if (index == 0) Modifier.focusRequester(first) else Modifier,
                    selected = kind in visible,
                ) { onToggle(kind, kind !in visible) }
                Spacer(Modifier.height(6.dp))
            }
            Spacer(Modifier.height(8.dp))
            PillButton("Done", onClick = onDismiss)
        }
        LaunchedEffect(Unit) {
            delay(50)
            first.safeRequestFocus()
        }
    }
}

/** Lets the user pick one of the TV's installed screensavers (DreamServices); "" = TV default. */
@Composable
fun ScreensaverPickerDialog(
    vm: LauncherViewModel,
    current: String?,
    onPicked: () -> Unit,
    onDismiss: () -> Unit,
) {
    val options = remember { vm.screensavers() }
    val canSet = remember { vm.canSetScreensaver() }
    val first = remember { FocusRequester() }
    val pick: (String) -> Unit = { component ->
        vm.setScreensaver(component)
        onDismiss()
        onPicked()
    }
    FullDialog(onDismiss) {
        Column(Modifier.fillMaxSize().padding(24.dp)) {
            Text("Choose screensaver", fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
            Text(
                if (canSet) {
                    "MHz Night Sky is built in. Other screensavers are also set as the TV's own screensaver."
                } else {
                    "MHz Night Sky and the TV default work without any permission. To use another installed " +
                        "screensaver, grant the permission once in Launcher settings → Permissions."
                },
                fontSize = 13.sp,
                color = LauncherColors.TextSecondary,
            )
            Spacer(Modifier.height(12.dp))
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                MenuButton(
                    "MHz Night Sky with clock (built-in)",
                    Modifier.focusRequester(first),
                    selected = current == null || current == vm.nightSkyClockComponent,
                ) { pick(vm.nightSkyClockComponent) }
                MenuButton(
                    "MHz Night Sky (built-in)",
                    selected = current == vm.nightSkyComponent,
                ) { pick(vm.nightSkyComponent) }
                MenuButton("TV default screensaver", selected = current == "") { pick("") }
                options.forEach { option ->
                    MenuButton(
                        if (option.label == option.appName) option.label else "${option.label} – ${option.appName}",
                        selected = current == option.component,
                    ) { pick(option.component) }
                }
                MenuButton("Open Android screensaver settings") {
                    onDismiss()
                    vm.openScreensaverSettings()
                }
            }
        }
        LaunchedEffect(Unit) {
            delay(80)
            first.safeRequestFocus()
        }
    }
}

@Composable
fun AddGadgetDialog(
    vm: LauncherViewModel,
    existing: List<Gadget>,
    onPickWidget: (AppWidgetProviderInfo) -> Unit,
    onDismiss: () -> Unit,
) {
    val providers = remember { vm.widgetHost.providers() }
    val entries = buildList {
        if (existing.none { it.type == GadgetType.WEATHER }) {
            add(MenuAction("Weather forecast") { vm.addGadget(GadgetType.WEATHER) })
        }
        if (existing.none { it.type == GadgetType.PRAYER }) {
            add(MenuAction("Prayer times (JAKIM)") { vm.addGadget(GadgetType.PRAYER) })
        }
        if (existing.none { it.type == GadgetType.SYSTEM }) {
            add(MenuAction("System monitor (RAM, CPU, temperature, power, storage)") { vm.addGadget(GadgetType.SYSTEM) })
        }
        if (existing.none { it.type == GadgetType.WORLD_CLOCK }) {
            add(MenuAction("World clock (2 cities)") { vm.addGadget(GadgetType.WORLD_CLOCK) })
        }
        providers.forEach { info ->
            add(MenuAction("${vm.widgetHost.appLabel(info)} – ${vm.widgetHost.widgetLabel(info)}") { onPickWidget(info) })
        }
    }
    val first = remember { FocusRequester() }
    FullDialog(onDismiss) {
        Column(Modifier.fillMaxSize().padding(24.dp)) {
            Text("Add gadget", fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
            if (!vm.widgetHost.isSupported) {
                Text("This TV does not support Android app widgets; only built-in gadgets are available.", fontSize = 13.sp, color = LauncherColors.TextSecondary)
            }
            Spacer(Modifier.height(12.dp))
            if (entries.isEmpty()) {
                Text("Nothing else to add.", fontSize = 15.sp, color = LauncherColors.TextSecondary)
                Spacer(Modifier.height(12.dp))
                PillButton("Close", modifier = Modifier.focusRequester(first), onClick = onDismiss)
            } else {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    entries.forEachIndexed { index, entry ->
                        MenuButton(entry.label, if (index == 0) Modifier.focusRequester(first) else Modifier) {
                            onDismiss()
                            entry.onClick()
                        }
                    }
                }
            }
        }
        LaunchedEffect(Unit) {
            delay(80)
            first.safeRequestFocus()
        }
    }
}

/** Popular cities first; typing searches every time zone the TV knows. */
@Composable
fun WorldClockCityDialog(title: String, current: WorldClock, onPick: (WorldClock) -> Unit, onDismiss: () -> Unit) {
    var query by remember { mutableStateOf("") }
    val results = remember(query) {
        val q = query.trim()
        if (q.isEmpty()) {
            WorldClocks.popular
        } else {
            WorldClocks.all.filter { it.city.contains(q, ignoreCase = true) || it.zoneId.contains(q, ignoreCase = true) }.take(100)
        }
    }
    val first = remember { FocusRequester() }
    FullDialog(onDismiss) {
        Column(Modifier.fillMaxSize().padding(24.dp)) {
            Text(title, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
            Text("Pick a city, or press ▲ to search all time zones", fontSize = 13.sp, color = LauncherColors.TextSecondary)
            Spacer(Modifier.height(12.dp))
            TvTextField(query, { query = it }, Modifier.fillMaxWidth(0.6f), "Search city, e.g. Kuching")
            Spacer(Modifier.height(12.dp))
            if (results.isEmpty()) {
                Text("No cities match \"$query\"", fontSize = 15.sp, color = LauncherColors.TextSecondary)
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    itemsIndexed(results, key = { _, c -> c.city + "|" + c.zoneId }) { index, clock ->
                        MenuButton(
                            "${clock.city}   ·   ${WorldClocks.utcOffset(clock)}",
                            if (index == 0) Modifier.focusRequester(first) else Modifier,
                            selected = clock == current,
                        ) {
                            onPick(clock)
                            onDismiss()
                        }
                    }
                }
            }
        }
        LaunchedEffect(Unit) {
            delay(80)
            first.safeRequestFocus()
        }
    }
}

@Composable
fun CitySearchDialog(vm: LauncherViewModel, onDismiss: () -> Unit) {
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<Place>>(emptyList()) }
    var status by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val field = remember { FocusRequester() }

    val search: () -> Unit = {
        scope.launch {
            status = "Searching…"
            results = vm.searchCity(query)
            status = if (results.isEmpty()) "No places found (check internet connection)" else null
        }
    }

    FullDialog(onDismiss) {
        Column(Modifier.fillMaxSize().padding(24.dp)) {
            Text("Weather location", fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TvTextField(query, { query = it }, Modifier.weight(1f).focusRequester(field), "City name, e.g. Shah Alam", onDone = search)
                PillButton("Search", onClick = search)
                PillButton("Use automatic") {
                    vm.useAutoLocation()
                    onDismiss()
                }
            }
            Spacer(Modifier.height(10.dp))
            status?.let { Text(it, fontSize = 14.sp, color = LauncherColors.TextSecondary) }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(results) { place ->
                    MenuButton(place.name) {
                        vm.setManualLocation(place)
                        onDismiss()
                    }
                }
            }
        }
        LaunchedEffect(Unit) {
            delay(80)
            field.safeRequestFocus()
        }
    }
}

@Composable
fun ZonePickerDialog(current: String?, onPick: (String?) -> Unit, onDismiss: () -> Unit) {
    val first = remember { FocusRequester() }
    FullDialog(onDismiss) {
        Column(Modifier.fillMaxSize().padding(24.dp)) {
            Text("Prayer time zone (JAKIM e-Solat)", fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(12.dp))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                item {
                    MenuButton("Automatic (nearest zone to your location)", Modifier.focusRequester(first), selected = current == null) {
                        onPick(null)
                        onDismiss()
                    }
                }
                items(PrayerZones.all, key = { it.code }) { zone ->
                    MenuButton("${zone.code} · ${zone.state} — ${zone.areas}", selected = zone.code == current) {
                        onPick(zone.code)
                        onDismiss()
                    }
                }
            }
        }
        LaunchedEffect(Unit) {
            delay(80)
            first.safeRequestFocus()
        }
    }
}

@Composable
fun RowChooserDialog(
    rows: List<AppRow>,
    appLabel: String,
    onPick: (String) -> Unit,
    onNewRow: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var creating by remember { mutableStateOf(false) }
    var closed by remember { mutableStateOf(false) }
    if (creating) {
        TextInputDialog(
            title = "New row",
            initial = "",
            placeholder = "Row name",
            onConfirm = onNewRow,
            onDismiss = onDismiss,
        )
        return
    }
    if (closed) {
        LaunchedEffect(Unit) { onDismiss() }
        return
    }
    ActionMenu(
        title = "Add \"$appLabel\" to row",
        actions = rows.map { row -> MenuAction(row.title) { onPick(row.id) } } +
            MenuAction("New row…") { creating = true },
        onDismiss = { closed = true },
    )
}
