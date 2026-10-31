package com.megalauncher.ui

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.Text
import com.megalauncher.BuildConfig
import com.megalauncher.LauncherViewModel
import com.megalauncher.Screen
import com.megalauncher.data.AppInfo
import com.megalauncher.data.AvatarKind
import com.megalauncher.data.BackgroundType
import com.megalauncher.data.DateStyle
import com.megalauncher.data.GadgetType
import com.megalauncher.data.GradientStyle
import com.megalauncher.data.ImageScale
import com.megalauncher.data.LauncherConfig
import com.megalauncher.data.Profile
import com.megalauncher.data.ShizukuStatus
import com.megalauncher.data.SystemGaugeKind
import com.megalauncher.data.WorldClocks
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun LauncherApp(vm: LauncherViewModel) {
    val screen by vm.screen.collectAsStateWithLifecycle()
    val config by vm.config.collectAsStateWithLifecycle()
    val profiles by vm.profiles.collectAsStateWithLifecycle()
    var profileSwitcher by remember { mutableStateOf(false) }
    var addProfile by remember { mutableStateOf(false) }

    // A home launcher must never finish on Back.
    BackHandler { if (screen != Screen.HOME) vm.goHome() }

    Box(Modifier.fillMaxSize()) {
        LauncherBackground(config.background, vm.backgroundRepo)
        Column(
            Modifier
                .fillMaxSize()
                .padding(start = 40.dp, end = 40.dp, top = 20.dp),
        ) {
            TopBar(
                vm = vm,
                screen = screen,
                config = config,
                profile = profiles.active,
                onProfile = { profileSwitcher = true },
                onScreensaver = { vm.startScreensaver() },
            )
            Spacer(Modifier.height(6.dp))
            CompositionLocalProvider(LocalBringIntoViewSpec provides PivotBringIntoViewSpec) {
                Box(Modifier.fillMaxSize()) {
                    when (screen) {
                        Screen.HOME -> HomeScreen(vm)
                        Screen.APPS -> AppsScreen(vm)
                        Screen.SEARCH -> SearchScreen(vm)
                        Screen.SETTINGS -> SettingsScreen(vm)
                    }
                }
            }
        }
    }

    if (profileSwitcher) {
        ProfileSwitcherDialog(
            vm = vm,
            onAdd = { addProfile = true },
            onManage = { vm.setScreen(Screen.SETTINGS) },
            onDismiss = { profileSwitcher = false },
        )
    }
    if (addProfile) {
        TextInputDialog(
            title = "New profile name",
            initial = "",
            placeholder = "e.g. Kids",
            onConfirm = vm::addProfile,
            onDismiss = { addProfile = false },
        )
    }
}

@Composable
private fun TopBar(
    vm: LauncherViewModel,
    screen: Screen,
    config: LauncherConfig,
    profile: Profile,
    onProfile: () -> Unit,
    onScreensaver: () -> Unit,
) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        FocusSurface(
            onClick = onProfile,
            modifier = Modifier.size(44.dp),
            shape = CircleShape,
            containerColor = Color.Transparent,
            focusedContainerColor = Color.Transparent,
        ) {
            AvatarImage(profile, vm, Modifier.fillMaxSize())
        }
        Spacer(Modifier.width(16.dp))
        PillButton(null, Icons.Default.Search, selected = screen == Screen.SEARCH) { vm.setScreen(Screen.SEARCH) }
        Spacer(Modifier.width(4.dp))
        PillButton("Home", selected = screen == Screen.HOME) { vm.goHome() }
        Spacer(Modifier.width(4.dp))
        PillButton("Apps", selected = screen == Screen.APPS) { vm.setScreen(Screen.APPS) }

        Spacer(Modifier.weight(1f))

        PillButton(null, LauncherIcons.Moon, onClick = onScreensaver)
        Spacer(Modifier.width(4.dp))
        PillButton(null, LauncherIcons.Tune, selected = screen == Screen.SETTINGS) { vm.setScreen(Screen.SETTINGS) }
        Spacer(Modifier.width(4.dp))
        PillButton(null, Icons.Default.Settings) { vm.openSystemSettings() }
        Spacer(Modifier.width(20.dp))
        Clock(vm, config)
    }
}

fun formatClockTime(millis: Long, use24Hour: Boolean): String =
    SimpleDateFormat(if (use24Hour) "HH:mm" else "h:mm a", Locale.getDefault()).format(Date(millis)).uppercase(Locale.getDefault())

fun formatClockDate(millis: Long, style: DateStyle): String =
    SimpleDateFormat(style.pattern, Locale.getDefault()).format(Date(millis))

/** Two-line clock: time at 12pt above the date at 8pt, in the profile's chosen formats. */
@Composable
private fun Clock(vm: LauncherViewModel, config: LauncherConfig) {
    val now by vm.now.collectAsStateWithLifecycle()
    val time = remember(now, config.use24Hour) { formatClockTime(now, config.use24Hour) }
    val day = remember(now, config.dateStyle) { formatClockDate(now, config.dateStyle) }
    Column(horizontalAlignment = Alignment.End) {
        Text(time, fontSize = pt(12), lineHeight = pt(13), fontWeight = FontWeight.SemiBold)
        Text(day, fontSize = pt(8), lineHeight = pt(9), color = LauncherColors.TextSecondary)
    }
}

@Composable
fun AppsScreen(vm: LauncherViewModel) {
    val apps by vm.apps.collectAsStateWithLifecycle()
    val config by vm.config.collectAsStateWithLifecycle()
    var menuFor by remember { mutableStateOf<AppInfo?>(null) }
    var rowChooserFor by remember { mutableStateOf<AppInfo?>(null) }
    val first = remember { FocusRequester() }

    Column(Modifier.fillMaxSize()) {
        SectionTitle("All apps (${apps.size})")
        AppGrid(
            apps = apps,
            firstFocus = first,
            onClick = { vm.launch(it.packageName) },
            onLongClick = { menuFor = it },
        )
    }
    LaunchedEffect(apps.isNotEmpty()) {
        if (apps.isNotEmpty()) {
            delay(80)
            first.safeRequestFocus()
        }
    }

    menuFor?.let { app ->
        val pinned = app.packageName in config.pinned
        ActionMenu(
            title = app.label,
            icon = app.icon,
            onDismiss = { menuFor = null },
            actions = buildList {
                add(MenuAction("Open") { vm.launch(app.packageName) })
                add(MenuAction(if (pinned) "Remove from favourites" else "Add to favourites") { vm.togglePinned(app.packageName) })
                add(MenuAction("Add to row…") { rowChooserFor = app })
                addAll(commonAppActions(vm, app))
            },
        )
    }
    rowChooserFor?.let { app ->
        RowChooserDialog(
            rows = config.rows,
            appLabel = app.label,
            onPick = { vm.addAppToRow(it, app.packageName) },
            onNewRow = { vm.addRow(it, listOf(app.packageName)) },
            onDismiss = { rowChooserFor = null },
        )
    }
}

@Composable
fun SearchScreen(vm: LauncherViewModel) {
    val apps by vm.apps.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    val results = remember(apps, query) {
        val q = query.trim()
        if (q.isEmpty()) apps else apps.filter { it.label.contains(q, ignoreCase = true) || it.packageName.contains(q, ignoreCase = true) }
    }
    val field = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current

    LaunchedEffect(Unit) {
        delay(80)
        field.safeRequestFocus()
        keyboard?.show()
    }

    Column(Modifier.fillMaxSize()) {
        Spacer(Modifier.height(8.dp))
        TvTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(0.6f).padding(start = 8.dp).focusRequester(field),
            placeholder = "Search installed apps",
            onDone = { keyboard?.hide() },
        )
        Spacer(Modifier.height(8.dp))
        if (results.isEmpty()) {
            Text("No apps match \"$query\"", fontSize = 16.sp, color = LauncherColors.TextSecondary, modifier = Modifier.padding(12.dp))
        } else {
            AppGrid(apps = results, onClick = { vm.launch(it.packageName) })
        }
    }
}

private enum class SettingsDialog {
    NAME, AVATAR, SWITCH_PROFILE, ADD_PROFILE, DELETE_PROFILE, DATE_FORMAT, WEATHER_REFRESH,
    CITY, API_KEY, ZONE, SCREENSAVER, SCREENSAVER_TIMEOUT, SHIZUKU_INSTALL, SHIZUKU_START, WORLD_CLOCK_1, WORLD_CLOCK_2,
    BG_TYPE, BG_COLOR, BG_GRADIENT_STYLE, BG_GRADIENT_START, BG_GRADIENT_END, BG_MEDIA_SOURCE, BG_MEDIA_URL, BG_GALLERY,
}

@Composable
fun SettingsScreen(vm: LauncherViewModel) {
    val config by vm.config.collectAsStateWithLifecycle()
    val profiles by vm.profiles.collectAsStateWithLifecycle()
    val now by vm.now.collectAsStateWithLifecycle()
    val weather by vm.weather.collectAsStateWithLifecycle()
    val prayer by vm.prayer.collectAsStateWithLifecycle()
    val permissionsGranted by vm.permissionsGranted.collectAsStateWithLifecycle()
    var dialog by remember { mutableStateOf<SettingsDialog?>(null) }
    // Whether the media source/gallery/link dialogs are for a video (true) or an image (false).
    var mediaIsVideo by remember { mutableStateOf(false) }
    // When true, picked images become the profile picture instead of the background.
    var pickingAvatar by remember { mutableStateOf(false) }
    val first = remember { FocusRequester() }
    val context = LocalContext.current

    val pickMedia = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { if (pickingAvatar) vm.importAvatar(it) else vm.importBackgroundFromUri(it, mediaIsVideo) }
    }
    val requestMediaPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            dialog = SettingsDialog.BG_GALLERY
        } else {
            vm.toast("Storage permission is needed to browse files")
        }
    }
    val mediaPermission = when {
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU -> Manifest.permission.READ_EXTERNAL_STORAGE
        mediaIsVideo -> Manifest.permission.READ_MEDIA_VIDEO
        else -> Manifest.permission.READ_MEDIA_IMAGES
    }
    val screensaverLabel = remember(config.screensaver) { vm.screensaverLabel(config.screensaver) }

    LaunchedEffect(Unit) {
        vm.refreshPermissions()
        delay(80)
        first.safeRequestFocus()
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 8.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text("Launcher settings", fontSize = 22.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 8.dp))

        SectionTitle("Profile")
        val profile = profiles.active
        SettingRow("Name", profile.name.ifBlank { "Not set" }, Modifier.focusRequester(first)) { dialog = SettingsDialog.NAME }
        SettingRow(
            "Picture",
            when (profile.avatar.kind) {
                AvatarKind.DEFAULT -> "Default"
                AvatarKind.PRESET -> "Preset icon"
                AvatarKind.IMAGE -> "Custom image"
            },
        ) { dialog = SettingsDialog.AVATAR }
        SettingRow("Switch profile", "${profiles.profiles.size} profile(s)") { dialog = SettingsDialog.SWITCH_PROFILE }
        SettingRow("Add profile", "Each profile has its own layout, gadgets & background") { dialog = SettingsDialog.ADD_PROFILE }
        if (profiles.profiles.size > 1) {
            SettingRow("Delete this profile", "") { dialog = SettingsDialog.DELETE_PROFILE }
        }

        SectionTitle("Clock")
        SettingSwitch("24-hour time (${formatClockTime(now, config.use24Hour)})", config.use24Hour) { vm.setUse24Hour(it) }
        SettingRow("Date format", formatClockDate(now, config.dateStyle)) { dialog = SettingsDialog.DATE_FORMAT }

        SectionTitle("Weather")
        val weatherOn = config.hasGadget(GadgetType.WEATHER)
        SettingSwitch("Show weather forecast", weatherOn) { vm.setWeatherEnabled(it) }
        if (weatherOn) {
            SettingSwitch("Use Fahrenheit (°F)", config.useFahrenheit) { vm.setFahrenheit(it) }
            val location = if (config.autoLocation) {
                "Automatic (by IP)" + (weather.data?.placeName?.let { " – $it" } ?: "")
            } else {
                config.manualLocation?.name ?: "Not set"
            }
            SettingRow("Location", location) { dialog = SettingsDialog.CITY }
            val keyLabel = if (config.weatherApiKey.isBlank()) {
                "Not set – using Open-Meteo"
            } else {
                "••••" + config.weatherApiKey.takeLast(4)
            }
            SettingRow("Weather.com API key", keyLabel) { dialog = SettingsDialog.API_KEY }
            SettingRow("Auto-refresh", refreshLabel(config.weatherRefreshMinutes)) { dialog = SettingsDialog.WEATHER_REFRESH }
            SettingRow("Refresh weather now", weather.data?.source?.let { "Source: $it" } ?: "") { vm.refreshWeather(force = true) }
        }

        SectionTitle("Prayer times (JAKIM e-Solat)")
        val prayerOn = config.hasGadget(GadgetType.PRAYER)
        SettingSwitch("Show prayer times", prayerOn) { vm.setPrayerEnabled(it) }
        if (prayerOn) {
            SettingSwitch("Automatic zone (from location)", config.autoPrayerZone) { auto ->
                if (auto) {
                    vm.setPrayerZone(null)
                } else {
                    dialog = SettingsDialog.ZONE
                }
            }
            val zone = "${prayer.zone.code} ${prayer.zone.areas}"
            SettingRow("Zone", zone) { dialog = SettingsDialog.ZONE }
            SettingSwitch("Highlight current prayer", config.highlightCurrentPrayer) { vm.setHighlightCurrentPrayer(it) }
            SettingSwitch("Show Syuruk", config.showSyuruk) { vm.setShowSyuruk(it) }
            SettingRow("Refresh prayer times now", "Cached for 1 month") { vm.refreshPrayer(force = true) }
        }

        SectionTitle("Background")
        val bg = config.background
        SettingRow("Background", bg.type.label) { dialog = SettingsDialog.BG_TYPE }
        when (bg.type) {
            BackgroundType.DEFAULT -> Unit
            BackgroundType.SOLID -> SettingRow("Colour", colorHex(bg.color), swatch = Color(bg.color)) {
                dialog = SettingsDialog.BG_COLOR
            }
            BackgroundType.GRADIENT -> {
                SettingRow("Gradient style", bg.gradientStyle.label) { dialog = SettingsDialog.BG_GRADIENT_STYLE }
                SettingRow("First colour", colorHex(bg.gradientStart), swatch = Color(bg.gradientStart)) {
                    dialog = SettingsDialog.BG_GRADIENT_START
                }
                SettingRow("Second colour", colorHex(bg.gradientEnd), swatch = Color(bg.gradientEnd)) {
                    dialog = SettingsDialog.BG_GRADIENT_END
                }
            }
            BackgroundType.IMAGE, BackgroundType.VIDEO -> {
                val isVideo = bg.type == BackgroundType.VIDEO
                val hasMedia = if (isVideo) vm.backgroundRepo.hasVideo else vm.backgroundRepo.hasImage
                SettingRow(
                    if (isVideo) "Video" else "Image",
                    if (hasMedia) "Change…" else "Not set – showing solid colour",
                ) {
                    mediaIsVideo = isVideo
                    dialog = SettingsDialog.BG_MEDIA_SOURCE
                }
                SettingRow("Fit", bg.imageScale.label) {
                    vm.setBackground {
                        it.copy(imageScale = if (it.imageScale == ImageScale.ZOOM) ImageScale.STRETCH else ImageScale.ZOOM)
                    }
                }
                SettingStepper(
                    "Blur",
                    if (bg.blur == 0) "Off" else "${bg.blur}",
                    onDecrease = { vm.setBackground { it.copy(blur = (it.blur - 1).coerceAtLeast(0)) } },
                    onIncrease = { vm.setBackground { it.copy(blur = (it.blur + 1).coerceAtMost(10)) } },
                )
                SettingStepper(
                    "Opacity",
                    "${bg.opacity}%",
                    onDecrease = { vm.setBackground { it.copy(opacity = (it.opacity - 10).coerceAtLeast(10)) } },
                    onIncrease = { vm.setBackground { it.copy(opacity = (it.opacity + 10).coerceAtMost(100)) } },
                )
            }
        }
        SettingSwitch("OLED TV – slowly move background (burn-in protection)", bg.oledShift) { enabled ->
            vm.setBackground { it.copy(oledShift = enabled) }
        }

        SectionTitle("World clock")
        val worldClockOn = config.hasGadget(GadgetType.WORLD_CLOCK)
        SettingSwitch("Show world clock", worldClockOn) { vm.setWorldClockEnabled(it) }
        if (worldClockOn) {
            repeat(2) { index ->
                val clock = config.worldClocks.getOrElse(index) { WorldClocks.defaults[index] }
                SettingRow("Clock ${index + 1}", "${clock.city} (${WorldClocks.utcOffset(clock)})") {
                    dialog = if (index == 0) SettingsDialog.WORLD_CLOCK_1 else SettingsDialog.WORLD_CLOCK_2
                }
            }
        }

        SectionTitle("Gadgets")
        SettingSwitch("Show system monitor (uses a little CPU while visible)", config.hasGadget(GadgetType.SYSTEM)) {
            vm.setSystemMonitorEnabled(it)
        }
        if (config.hasGadget(GadgetType.SYSTEM)) {
            SystemGaugeKind.entries.forEach { kind ->
                SettingSwitch(" Show ${kind.label} gauge", kind in config.systemGauges) { vm.setSystemGaugeVisible(kind, it) }
            }
        }
        SettingStepper(
            "Gadget background opacity (all)",
            "${config.gadgetOpacity}%",
            onDecrease = { vm.setGadgetRowOpacity(config.gadgetOpacity - 10) },
            onIncrease = { vm.setGadgetRowOpacity(config.gadgetOpacity + 10) },
        )
        Text(
            "Hold OK on a gadget → Background opacity to set it for that gadget only.",
            fontSize = 13.sp,
            color = LauncherColors.TextSecondary,
            modifier = Modifier.padding(start = 8.dp),
        )

        SectionTitle("Screensaver")
        SettingRow("Screensaver", screensaverLabel) { dialog = SettingsDialog.SCREENSAVER }
        SettingRow("Start screensaver after", timeoutLabel(config.screensaverTimeoutMinutes)) {
            dialog = SettingsDialog.SCREENSAVER_TIMEOUT
        }
        SettingRow("Start screensaver now", "") { vm.startScreensaver() }

        SectionTitle("Permissions")
        SettingRow(
            "Allow app widgets & screensaver switching",
            if (permissionsGranted) "Granted" else "Grant with Shizuku (no PC needed)",
        ) {
            when (vm.shizukuStatus()) {
                ShizukuStatus.NOT_INSTALLED -> {
                    dialog = SettingsDialog.SHIZUKU_INSTALL
                }
                ShizukuStatus.NOT_RUNNING -> {
                    dialog = SettingsDialog.SHIZUKU_START
                }
                ShizukuStatus.NEEDS_PERMISSION -> vm.requestShizukuPermission()
                ShizukuStatus.READY -> vm.grantPermissionsWithShizuku()
            }
        }

        SectionTitle("System")
        SettingRow("All Android TV settings", "") { vm.openSystemSettings() }

        SectionTitle("About")
        SettingRow("Mega Launcher version", "${BuildConfig.VERSION_NAME} (build ${BuildConfig.VERSION_CODE})") {}
        SettingRow("Built with AI", "GitHub Copilot · Claude Opus 5.5") {}
        SettingRow("Source code", PROJECT_URL) {
            try {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(PROJECT_URL)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } catch (e: ActivityNotFoundException) {
                vm.toast("No web browser on this TV – visit $PROJECT_URL", long = true)
            }
        }
        SettingRow("License", "MIT License © 2026 mhzworld – free and open source") {}
    }

    when (dialog) {
        null -> Unit
        SettingsDialog.NAME -> TextInputDialog(
            title = "Profile name",
            initial = profiles.active.name,
            onConfirm = vm::renameProfile,
            onDismiss = { dialog = null },
        )
        SettingsDialog.AVATAR -> AvatarPickerDialog(
            vm = vm,
            onBrowse = {
                pickingAvatar = true
                mediaIsVideo = false
                val permission = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                    Manifest.permission.READ_EXTERNAL_STORAGE
                } else {
                    Manifest.permission.READ_MEDIA_IMAGES
                }
                if (ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED) {
                    dialog = SettingsDialog.BG_GALLERY
                } else {
                    requestMediaPermission.launch(permission)
                }
            },
            onSystemPicker = {
                pickingAvatar = true
                try {
                    pickMedia.launch("image/*")
                } catch (e: ActivityNotFoundException) {
                    vm.toast("No file picker on this TV – use \"Browse\" instead")
                }
            },
            onDismiss = { dialog = null },
        )
        SettingsDialog.SWITCH_PROFILE -> ProfileSwitcherDialog(
            vm = vm,
            onAdd = { dialog = SettingsDialog.ADD_PROFILE },
            onManage = {},
            onDismiss = { dialog = null },
        )
        SettingsDialog.ADD_PROFILE -> TextInputDialog(
            title = "New profile name",
            initial = "",
            placeholder = "e.g. Kids",
            onConfirm = vm::addProfile,
            onDismiss = { dialog = null },
        )
        SettingsDialog.DELETE_PROFILE -> InfoDialog(
            title = "Delete \"${profiles.active.name.ifBlank { "this profile" }}\"?",
            message = "Its layout, gadgets, background and picture will be removed. This can't be undone.",
            actionLabel = "Delete",
            onAction = { vm.deleteProfile(profiles.activeId) },
            onDismiss = { dialog = null },
        )
        SettingsDialog.DATE_FORMAT -> ActionMenu(
            title = "Date format",
            onDismiss = { dialog = null },
            actions = DateStyle.entries.map { style ->
                val sample = formatClockDate(now, style)
                MenuAction(if (style == config.dateStyle) "✓  $sample" else sample) { vm.setDateStyle(style) }
            },
        )
        SettingsDialog.WEATHER_REFRESH -> ActionMenu(
            title = "Weather auto-refresh",
            onDismiss = { dialog = null },
            actions = WEATHER_REFRESH_OPTIONS.map { minutes ->
                val label = refreshLabel(minutes)
                MenuAction(if (minutes == config.weatherRefreshMinutes) "✓  $label" else label) {
                    vm.setWeatherRefreshMinutes(minutes)
                }
            },
        )
        SettingsDialog.CITY -> CitySearchDialog(vm) { dialog = null }
        SettingsDialog.API_KEY -> TextInputDialog(
            title = "Weather.com (The Weather Company) API key",
            initial = config.weatherApiKey,
            placeholder = "Leave empty to use Open-Meteo",
            secret = true,
            onConfirm = vm::setWeatherApiKey,
            onDismiss = { dialog = null },
        )
        SettingsDialog.ZONE -> ZonePickerDialog(
            current = if (config.autoPrayerZone) null else config.prayerZone,
            onPick = vm::setPrayerZone,
            onDismiss = { dialog = null },
        )
        SettingsDialog.BG_TYPE -> ActionMenu(
            title = "Background",
            onDismiss = { dialog = null },
            actions = BackgroundType.entries.map { type ->
                MenuAction(type.label) {
                    val needsMedia = (type == BackgroundType.IMAGE && !vm.backgroundRepo.hasImage) ||
                        (type == BackgroundType.VIDEO && !vm.backgroundRepo.hasVideo)
                    if (needsMedia) {
                        // The type switches only once a file has been imported successfully.
                        mediaIsVideo = type == BackgroundType.VIDEO
                        dialog = SettingsDialog.BG_MEDIA_SOURCE
                    } else {
                        vm.setBackground { it.copy(type = type) }
                    }
                }
            },
        )
        SettingsDialog.BG_COLOR -> ColorPickerDialog("Background colour", config.background.color, { color ->
            vm.setBackground { it.copy(color = color) }
        }) { dialog = null }
        SettingsDialog.BG_GRADIENT_START -> ColorPickerDialog("First colour", config.background.gradientStart, { color ->
            vm.setBackground { it.copy(gradientStart = color) }
        }) { dialog = null }
        SettingsDialog.BG_GRADIENT_END -> ColorPickerDialog("Second colour", config.background.gradientEnd, { color ->
            vm.setBackground { it.copy(gradientEnd = color) }
        }) { dialog = null }
        SettingsDialog.BG_GRADIENT_STYLE -> ActionMenu(
            title = "Gradient style",
            onDismiss = { dialog = null },
            actions = GradientStyle.entries.map { style ->
                MenuAction(style.label) { vm.setBackground { it.copy(gradientStyle = style) } }
            },
        )
        SettingsDialog.BG_MEDIA_SOURCE -> ActionMenu(
            title = if (mediaIsVideo) "Background video" else "Background image",
            onDismiss = { dialog = null },
            actions = listOf(
                MenuAction(if (mediaIsVideo) "Browse videos on this TV" else "Browse images on this TV") {
                    pickingAvatar = false
                    if (ContextCompat.checkSelfPermission(context, mediaPermission) == PackageManager.PERMISSION_GRANTED) {
                        dialog = SettingsDialog.BG_GALLERY
                    } else {
                        requestMediaPermission.launch(mediaPermission)
                    }
                },
                MenuAction("Use system file picker") {
                    pickingAvatar = false
                    try {
                        pickMedia.launch(if (mediaIsVideo) "video/*" else "image/*")
                    } catch (e: ActivityNotFoundException) {
                        vm.toast("No file picker on this TV – use \"Browse\" instead")
                    }
                },
                MenuAction("Download from web link (https)") { dialog = SettingsDialog.BG_MEDIA_URL },
            ),
        )
        SettingsDialog.BG_MEDIA_URL -> TextInputDialog(
            title = if (mediaIsVideo) "Video link (MP4 / WebM)" else "Image or GIF link",
            initial = "",
            placeholder = "https://…",
            onConfirm = { vm.importBackgroundFromUrl(it, mediaIsVideo) },
            onDismiss = { dialog = null },
        )
        SettingsDialog.BG_GALLERY -> DeviceMediaPickerDialog(
            repo = vm.backgroundRepo,
            video = mediaIsVideo,
            onPick = { if (pickingAvatar) vm.importAvatar(it) else vm.importBackgroundFromUri(it, mediaIsVideo) },
            onDismiss = { dialog = null },
        )
        SettingsDialog.WORLD_CLOCK_1, SettingsDialog.WORLD_CLOCK_2 -> {
            val index = if (dialog == SettingsDialog.WORLD_CLOCK_1) 0 else 1
            WorldClockCityDialog(
                title = "World clock ${index + 1}",
                current = config.worldClocks.getOrElse(index) { WorldClocks.defaults[index] },
                onPick = { vm.setWorldClock(index, it) },
                onDismiss = { dialog = null },
            )
        }
        SettingsDialog.SCREENSAVER_TIMEOUT -> ActionMenu(
            title = "Start screensaver after",
            onDismiss = { dialog = null },
            actions = SCREENSAVER_TIMEOUT_OPTIONS.map { minutes ->
                val label = timeoutLabel(minutes)
                MenuAction(if (minutes == config.screensaverTimeoutMinutes) "✓  $label" else label) {
                    vm.setScreensaverTimeout(minutes)
                }
            },
        )
        SettingsDialog.SCREENSAVER -> ScreensaverPickerDialog(
            vm = vm,
            current = config.screensaver,
            onPicked = {},
            onDismiss = { dialog = null },
        )
        SettingsDialog.SHIZUKU_INSTALL -> InfoDialog(
            title = "Install Shizuku",
            message = "Shizuku is a free, open-source helper that runs the one-time permission commands on the TV " +
                "itself – no root and no PC. Install it from the Play Store, or if it isn't listed for your TV, " +
                "from github.com/RikkaApps/Shizuku/releases. Then come back and press this button again.",
            actionLabel = "Open Play Store",
            onAction = vm::openShizukuInStore,
            onDismiss = { dialog = null },
        )
        SettingsDialog.SHIZUKU_START -> InfoDialog(
            title = "Start Shizuku",
            message = "Shizuku is installed but not running. Turn on Settings → System → Developer options → " +
                "Wireless debugging (Android 11+), then open Shizuku and choose \"Start via Wireless debugging\". " +
                "When it shows as running, come back and press this button again.",
            actionLabel = "Open Shizuku",
            onAction = vm::openShizuku,
            onDismiss = { dialog = null },
        )
    }
}

private const val PROJECT_URL = "https://github.com/mhzworld/megalauncher"

private val WEATHER_REFRESH_OPTIONS = listOf(15, 30, 60, 120, 180, 360, 0)

private val SCREENSAVER_TIMEOUT_OPTIONS = listOf(1, 5, 10, 15, 30, 60, 0)

private fun timeoutLabel(minutes: Int): String = when {
    minutes <= 0 -> "Never"
    minutes == 1 -> "1 minute of inactivity"
    minutes == 60 -> "1 hour of inactivity"
    else -> "$minutes minutes of inactivity"
}

private fun refreshLabel(minutes: Int): String = when {
    minutes <= 0 -> "Manual only (press the gadget)"
    minutes == 60 -> "Every hour"
    minutes < 60 -> "Every $minutes minutes"
    else -> "Every ${minutes / 60} hours"
}

/** D-pad left/right adjusts the value while focused. */
@Composable
internal fun SettingStepper(
    title: String,
    value: String,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
    modifier: Modifier = Modifier.fillMaxWidth(0.7f),
) {
    FocusSurface(
        onClick = onIncrease,
        modifier = modifier
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.DirectionLeft -> {
                        onDecrease()
                        true
                    }
                    Key.DirectionRight -> {
                        onIncrease()
                        true
                    }
                    else -> false
                }
            },
        focusedScale = 1.02f,
        showFocusBorder = false,
        focusedContainerColor = Color.White,
        focusedContentColor = Color.Black,
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(Modifier.padding(horizontal = 18.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(title, fontSize = 16.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
            Text("◀   $value   ▶", fontSize = 14.sp, color = LocalContentColor.current.copy(alpha = 0.7f))
        }
    }
}

@Composable
private fun SettingSwitch(title: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    FocusSurface(
        onClick = { onChange(!checked) },
        modifier = Modifier.fillMaxWidth(0.7f),
        focusedScale = 1.02f,
        showFocusBorder = false,
        focusedContainerColor = Color.White,
        focusedContentColor = Color.Black,
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(Modifier.padding(horizontal = 18.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(title, fontSize = 16.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
            Text(
                if (checked) "On" else "Off",
                fontSize = 14.sp,
                color = LocalContentColor.current.copy(alpha = 0.7f),
            )
            Spacer(Modifier.width(12.dp))
            Box(
                Modifier
                    .size(width = 44.dp, height = 24.dp)
                    .background(if (checked) LauncherColors.PrayerActive else Color.Gray, RoundedCornerShape(50)),
                contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart,
            ) {
                Box(Modifier.padding(3.dp).size(18.dp).background(Color.White, CircleShape))
            }
        }
    }
}

@Composable
private fun SettingRow(
    title: String,
    value: String,
    modifier: Modifier = Modifier,
    swatch: Color? = null,
    onClick: () -> Unit,
) {
    FocusSurface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(0.7f),
        focusedScale = 1.02f,
        showFocusBorder = false,
        focusedContainerColor = Color.White,
        focusedContentColor = Color.Black,
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(Modifier.padding(horizontal = 18.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(title, fontSize = 16.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
            if (swatch != null) {
                Box(
                    Modifier
                        .size(20.dp)
                        .background(swatch, CircleShape)
                        .border(1.dp, Color.Gray, CircleShape),
                )
                Spacer(Modifier.width(10.dp))
            }
            Text(
                value,
                fontSize = 14.sp,
                color = LocalContentColor.current.copy(alpha = 0.7f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1.4f, fill = false),
            )
        }
    }
}
