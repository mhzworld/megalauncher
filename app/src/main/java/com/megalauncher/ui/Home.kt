package com.megalauncher.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import com.megalauncher.LauncherViewModel
import com.megalauncher.Screen
import com.megalauncher.data.AppInfo
import com.megalauncher.data.AppRow
import com.megalauncher.data.Gadget
import com.megalauncher.data.GadgetType
import com.megalauncher.data.WorldClocks
import kotlinx.coroutines.delay

private const val PINNED = "pinned"
private const val GADGETS = "gadgets"

private data class MoveTarget(val section: String, val key: String)

private sealed interface HomeDialog {
    data class AppMenu(val section: String, val pkg: String) : HomeDialog
    data object AddFavourites : HomeDialog
    data class AddToRow(val rowId: String) : HomeDialog
    data class RowMenu(val rowId: String) : HomeDialog
    data class RenameRow(val rowId: String) : HomeDialog
    data object NewRow : HomeDialog
    data class GadgetMenu(val gadget: Gadget) : HomeDialog
    data class GadgetOpacity(val key: String) : HomeDialog
    data object AddGadget : HomeDialog
    data object WidgetBindHelp : HomeDialog
    data object CitySearch : HomeDialog
    data object ZonePicker : HomeDialog
    data class WorldClockCity(val index: Int) : HomeDialog
    data object SystemGauges : HomeDialog
}

@Composable
fun HomeScreen(vm: LauncherViewModel) {
    val config by vm.config.collectAsStateWithLifecycle()
    val apps by vm.apps.collectAsStateWithLifecycle()
    val homeToken by vm.homeToken.collectAsStateWithLifecycle()

    val appMap = remember(apps) { apps.associateBy { it.packageName } }
    val scroll = rememberScrollState()
    val firstFocus = remember { FocusRequester() }
    var moving by remember { mutableStateOf<MoveTarget?>(null) }
    var dialog by remember { mutableStateOf<HomeDialog?>(null) }
    val addWidget = rememberWidgetAdder(vm) { dialog = HomeDialog.WidgetBindHelp }

    LaunchedEffect(homeToken) {
        scroll.animateScrollTo(0)
        delay(80)
        firstFocus.safeRequestFocus()
    }
    BackHandler(enabled = moving != null) { moving = null }

    Column(Modifier.fillMaxSize().verticalScroll(scroll).padding(bottom = 48.dp)) {
        if (moving != null) {
            Text(
                "Moving: use ◀ ▶ to reorder, press OK when done",
                fontSize = 14.sp,
                color = LauncherColors.Moving,
                modifier = Modifier.padding(start = 8.dp, top = 4.dp),
            )
        }

        // Favourites
        SectionTitle("Favourites")
        val pinnedApps = config.pinned.mapNotNull { appMap[it] }
        ScrollRow(spacing = 8.dp) {
            pinnedApps.forEachIndexed { index, app ->
                key(app.packageName) {
                    val isMoving = moving == MoveTarget(PINNED, app.packageName)
                    RoundAppIcon(
                        app = app,
                        moving = isMoving,
                        modifier = Modifier
                            .then(if (index == 0) Modifier.focusRequester(firstFocus) else Modifier)
                            .movable(isMoving, { vm.movePinned(app.packageName, it) }, { moving = null }),
                        onClick = { vm.launch(app.packageName) },
                        onLongClick = { dialog = HomeDialog.AppMenu(PINNED, app.packageName) },
                    )
                }
            }
            RoundAction(
                LauncherIcons.Apps,
                "All apps",
                if (pinnedApps.isEmpty()) Modifier.focusRequester(firstFocus) else Modifier,
            ) { vm.setScreen(Screen.APPS) }
            RoundAction(Icons.Default.Add, "Add favourite") { dialog = HomeDialog.AddFavourites }
        }

        // Gadgets
        SectionTitle("Gadgets")
        ScrollRow {
            config.gadgets.forEach { gadget ->
                key(gadget.key) {
                    val isMoving = moving == MoveTarget(GADGETS, gadget.key)
                    val modifier = Modifier.movable(isMoving, { vm.moveGadget(gadget.key, it) }, { moving = null })
                    val width = when (gadget.type) {
                        GadgetType.WEATHER -> 280.dp
                        GadgetType.PRAYER -> 540.dp
                        GadgetType.APP_WIDGET -> 380.dp
                        GadgetType.SYSTEM -> systemGadgetWidth(config.systemGauges.size)
                        GadgetType.WORLD_CLOCK -> 360.dp
                    }
                    val alpha = (gadget.opacity ?: config.gadgetOpacity) / 100f
                    FocusSurface(
                        onClick = {
                            when (gadget.type) {
                                GadgetType.WEATHER -> vm.refreshWeather(force = true)
                                GadgetType.PRAYER -> vm.refreshPrayer(force = true)
                                GadgetType.APP_WIDGET, GadgetType.SYSTEM, GadgetType.WORLD_CLOCK -> Unit
                            }
                        },
                        onLongClick = { dialog = HomeDialog.GadgetMenu(gadget) },
                        modifier = modifier.size(width, 200.dp),
                        shape = RoundedCornerShape(18.dp),
                        highlighted = isMoving,
                        focusedScale = 1.03f,
                        containerColor = LauncherColors.Card.copy(alpha = alpha),
                        focusedContainerColor = LauncherColors.Card.copy(alpha = alpha),
                    ) {
                        when (gadget.type) {
                            GadgetType.WEATHER -> WeatherGadgetHost(vm, config.useFahrenheit, config.use24Hour)
                            GadgetType.PRAYER -> PrayerGadgetHost(vm, config)
                            GadgetType.APP_WIDGET -> AppWidgetGadget(vm, gadget.widgetId)
                            GadgetType.SYSTEM -> SystemGadgetHost(vm, config.systemGauges)
                            GadgetType.WORLD_CLOCK -> WorldClockGadgetHost(vm, config.worldClocks, config.use24Hour)
                        }
                    }
                }
            }
            ActionTile(Icons.Default.Add, "Add gadget", Modifier.padding(top = 46.dp)) { dialog = HomeDialog.AddGadget }
        }

        // User-defined app rows
        config.rows.forEach { row ->
            key(row.id) {
                AppsRow(
                    row = row,
                    appMap = appMap,
                    moving = moving,
                    onMoveEnd = { moving = null },
                    vm = vm,
                    onAppLongClick = { pkg -> dialog = HomeDialog.AppMenu(row.id, pkg) },
                    onAddApps = { dialog = HomeDialog.AddToRow(row.id) },
                    onEditRow = { dialog = HomeDialog.RowMenu(row.id) },
                )
            }
        }

        Spacer(Modifier.height(12.dp))
        Row(Modifier.padding(start = 8.dp)) {
            PillButton("Add row", Icons.Default.Add) { dialog = HomeDialog.NewRow }
        }
    }

    HomeDialogs(
        vm = vm,
        dialog = dialog,
        setDialog = { dialog = it },
        appMap = appMap,
        apps = apps,
        onMove = { moving = it },
        addWidget = addWidget,
    )
}

@Composable
private fun ScrollRow(spacing: Dp = 18.dp, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalBringIntoViewSpec provides EdgeBringIntoViewSpec) {
        Row(
            Modifier
                .fillMaxWidth()
                .focusRestorer()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(spacing),
        ) {
            content()
        }
    }
}

@Composable
private fun AppsRow(
    row: AppRow,
    appMap: Map<String, AppInfo>,
    moving: MoveTarget?,
    onMoveEnd: () -> Unit,
    vm: LauncherViewModel,
    onAppLongClick: (String) -> Unit,
    onAddApps: () -> Unit,
    onEditRow: () -> Unit,
) {
    Column {
        SectionTitle(row.title)
        ScrollRow {
            row.packages.mapNotNull { appMap[it] }.forEach { app ->
                key(app.packageName) {
                    val isMoving = moving == MoveTarget(row.id, app.packageName)
                    val banner by produceState(vm.cachedBanner(app.packageName), app.packageName, app.updatedAt) {
                        value = vm.banner(app.packageName)
                    }
                    BannerCard(
                        app = app,
                        banner = banner,
                        moving = isMoving,
                        modifier = Modifier.movable(isMoving, { vm.moveAppInRow(row.id, app.packageName, it) }, onMoveEnd),
                        onClick = { vm.launch(app.packageName) },
                        onLongClick = { onAppLongClick(app.packageName) },
                    )
                }
            }
            ActionTile(Icons.Default.Add, "Add apps", onClick = onAddApps)
            ActionTile(Icons.Default.Edit, "Edit row", onClick = onEditRow)
        }
    }
}

@Composable
private fun HomeDialogs(
    vm: LauncherViewModel,
    dialog: HomeDialog?,
    setDialog: (HomeDialog?) -> Unit,
    appMap: Map<String, AppInfo>,
    apps: List<AppInfo>,
    onMove: (MoveTarget) -> Unit,
    addWidget: (android.appwidget.AppWidgetProviderInfo) -> Unit,
) {
    val config by vm.config.collectAsStateWithLifecycle()
    val dismiss = { setDialog(null) }

    when (dialog) {
        null -> Unit

        is HomeDialog.AppMenu -> appMap[dialog.pkg]?.let { app ->
            val inFavourites = dialog.section == PINNED
            ActionMenu(
                title = app.label,
                icon = app.icon,
                onDismiss = dismiss,
                actions = buildList {
                    add(MenuAction("Open") { vm.launch(app.packageName) })
                    add(MenuAction("Move") { onMove(MoveTarget(dialog.section, app.packageName)) })
                    if (inFavourites) {
                        add(MenuAction("Remove from favourites") { vm.togglePinned(app.packageName) })
                    } else {
                        add(MenuAction("Remove from this row") { vm.toggleAppInRow(dialog.section, app.packageName) })
                        if (app.packageName !in config.pinned) {
                            add(MenuAction("Add to favourites") { vm.togglePinned(app.packageName) })
                        }
                    }
                    addAll(commonAppActions(vm, app))
                },
            )
        }

        HomeDialog.AddFavourites -> AppPickerDialog(
            title = "Favourites",
            apps = apps,
            selected = config.pinned.toSet(),
            onToggle = vm::togglePinned,
            onDismiss = dismiss,
        )

        is HomeDialog.AddToRow -> config.rows.find { it.id == dialog.rowId }?.let { row ->
            AppPickerDialog(
                title = row.title,
                apps = apps,
                selected = row.packages.toSet(),
                onToggle = { vm.toggleAppInRow(row.id, it) },
                onDismiss = dismiss,
            )
        }

        is HomeDialog.RowMenu -> config.rows.find { it.id == dialog.rowId }?.let { row ->
            val index = config.rows.indexOf(row)
            ActionMenu(
                title = row.title,
                onDismiss = dismiss,
                actions = buildList {
                    add(MenuAction("Add or remove apps") { setDialog(HomeDialog.AddToRow(row.id)) })
                    add(MenuAction("Rename row") { setDialog(HomeDialog.RenameRow(row.id)) })
                    if (index > 0) add(MenuAction("Move row up") { vm.moveRow(row.id, -1) })
                    if (index < config.rows.lastIndex) add(MenuAction("Move row down") { vm.moveRow(row.id, 1) })
                    add(MenuAction("Delete row") { vm.deleteRow(row.id) })
                },
            )
        }

        is HomeDialog.RenameRow -> config.rows.find { it.id == dialog.rowId }?.let { row ->
            TextInputDialog(
                title = "Rename row",
                initial = row.title,
                onConfirm = { vm.renameRow(row.id, it) },
                onDismiss = dismiss,
            )
        }

        HomeDialog.NewRow -> TextInputDialog(
            title = "New row",
            initial = "",
            placeholder = "Row name, e.g. Movies",
            onConfirm = { vm.addRow(it) },
            onDismiss = dismiss,
        )

        is HomeDialog.GadgetMenu -> {
            val gadget = dialog.gadget
            val title = when (gadget.type) {
                GadgetType.WEATHER -> "Weather"
                GadgetType.PRAYER -> "Prayer times"
                GadgetType.APP_WIDGET -> "Widget"
                GadgetType.SYSTEM -> "System monitor"
                GadgetType.WORLD_CLOCK -> "World clock"
            }
            ActionMenu(
                title = title,
                onDismiss = dismiss,
                actions = buildList {
                    when (gadget.type) {
                        GadgetType.WEATHER -> {
                            add(MenuAction("Refresh now") { vm.refreshWeather(force = true) })
                            add(
                                MenuAction(if (config.useFahrenheit) "Show in Celsius (°C)" else "Show in Fahrenheit (°F)") {
                                    vm.setFahrenheit(!config.useFahrenheit)
                                },
                            )
                            add(MenuAction("Change location…") { setDialog(HomeDialog.CitySearch) })
                            if (!config.autoLocation) add(MenuAction("Use automatic location") { vm.useAutoLocation() })
                        }
                        GadgetType.PRAYER -> {
                            add(MenuAction("Refresh now") { vm.refreshPrayer(force = true) })
                            add(MenuAction("Change zone…") { setDialog(HomeDialog.ZonePicker) })
                        }
                        GadgetType.WORLD_CLOCK -> config.worldClocks.take(2).forEachIndexed { index, clock ->
                            add(MenuAction("Change clock ${index + 1} (${clock.city})…") { setDialog(HomeDialog.WorldClockCity(index)) })
                        }
                        GadgetType.SYSTEM -> add(MenuAction("Show / hide gauges…") { setDialog(HomeDialog.SystemGauges) })
                        GadgetType.APP_WIDGET -> Unit
                    }
                    add(MenuAction("Background opacity…") { setDialog(HomeDialog.GadgetOpacity(gadget.key)) })
                    add(MenuAction("Move") { onMove(MoveTarget(GADGETS, gadget.key)) })
                    add(MenuAction("Remove") { vm.removeGadget(gadget) })
                },
            )
        }

        is HomeDialog.GadgetOpacity -> config.gadgets.find { it.key == dialog.key }?.let { gadget ->
            GadgetOpacityDialog(
                opacity = gadget.opacity,
                rowOpacity = config.gadgetOpacity,
                onChange = { vm.setGadgetOpacity(gadget.key, it) },
                onDismiss = dismiss,
            )
        }

        HomeDialog.AddGadget -> AddGadgetDialog(vm, config.gadgets, addWidget, dismiss)

        HomeDialog.WidgetBindHelp -> InfoDialog(
            title = "Allow Mega Launcher to add widgets",
            message = "Android TV only lets the built-in launcher add app widgets, and has no screen to allow " +
                "other launchers. Grant it once in Launcher settings → Permissions (with Shizuku, no PC needed), " +
                "or from a PC with adb, then add the gadget again:",
            code = "adb shell appwidget grantbind --package com.megalauncher --user 0",
            onDismiss = dismiss,
        )

        HomeDialog.CitySearch -> CitySearchDialog(vm, dismiss)

        HomeDialog.ZonePicker -> ZonePickerDialog(
            current = if (config.autoPrayerZone) null else config.prayerZone,
            onPick = vm::setPrayerZone,
            onDismiss = dismiss,
        )

        is HomeDialog.WorldClockCity -> WorldClockCityDialog(
            title = "World clock ${dialog.index + 1}",
            current = config.worldClocks.getOrElse(dialog.index) { WorldClocks.defaults[dialog.index] },
            onPick = { vm.setWorldClock(dialog.index, it) },
            onDismiss = dismiss,
        )

        // Closes by itself once hiding the last gauge removes the gadget.
        HomeDialog.SystemGauges -> if (config.hasGadget(GadgetType.SYSTEM)) {
            SystemGaugesDialog(config.systemGauges, vm::setSystemGaugeVisible, dismiss)
        } else {
            LaunchedEffect(Unit) { dismiss() }
        }
    }
}
