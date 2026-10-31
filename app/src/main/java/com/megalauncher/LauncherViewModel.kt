package com.megalauncher

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.Uri
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.megalauncher.data.AppInfo
import com.megalauncher.data.AppRepository
import com.megalauncher.data.AppRow
import com.megalauncher.data.Avatar
import com.megalauncher.data.AvatarKind
import com.megalauncher.data.BackgroundConfig
import com.megalauncher.data.BackgroundRepository
import com.megalauncher.data.BackgroundType
import com.megalauncher.data.ConfigStore
import com.megalauncher.data.DEFAULT_PROFILE_ID
import com.megalauncher.data.DateStyle
import com.megalauncher.data.Gadget
import com.megalauncher.data.GadgetType
import com.megalauncher.data.LauncherConfig
import com.megalauncher.data.LocationRepository
import com.megalauncher.data.Net
import com.megalauncher.data.Place
import com.megalauncher.data.PrayerDay
import com.megalauncher.data.PrayerRepository
import com.megalauncher.data.PrayerZone
import com.megalauncher.data.PrayerZones
import com.megalauncher.data.Profile
import com.megalauncher.data.ProfileRepository
import com.megalauncher.data.ProfilesState
import com.megalauncher.data.ScreensaverInfo
import com.megalauncher.data.ShizukuHelper
import com.megalauncher.data.ShizukuStatus
import com.megalauncher.data.SystemGaugeKind
import com.megalauncher.data.SystemStatsRepository
import com.megalauncher.data.WeatherData
import com.megalauncher.data.WeatherRepository
import com.megalauncher.data.WidgetHostManager
import com.megalauncher.data.WorldClock
import com.megalauncher.data.WorldClocks
import com.megalauncher.data.moveItem
import com.megalauncher.screensaver.NIGHT_SKY_NAME
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import rikka.shizuku.Shizuku
import java.time.LocalDate
import java.util.UUID

enum class Screen { HOME, APPS, SEARCH, SETTINGS }

data class WeatherState(val data: WeatherData? = null, val loading: Boolean = false, val error: String? = null)

data class PrayerState(
    val zone: PrayerZone,
    val day: PrayerDay? = null,
    val loading: Boolean = false,
    val error: String? = null,
)

class LauncherViewModel(app: Application) : AndroidViewModel(app) {
    private val store = ConfigStore(app)
    private val profileRepo = ProfileRepository(app)
    private val appRepo = AppRepository(app)
    private val weatherRepo = WeatherRepository(app)
    private val prayerRepo = PrayerRepository(app)
    private val locationRepo = LocationRepository(app)
    // Only created once the system monitor gadget is first shown.
    private val statsRepo by lazy { SystemStatsRepository(app) }
    val backgroundRepo = BackgroundRepository(app)
    val widgetHost = WidgetHostManager(app)

    private val _profiles = MutableStateFlow(loadProfiles())
    val profiles: StateFlow<ProfilesState> = _profiles.asStateFlow()
    private val activeId: String get() = _profiles.value.activeId

    init {
        backgroundRepo.profileId = activeId
    }

    private val _config = MutableStateFlow(store.load(activeId) ?: LauncherConfig())
    val config: StateFlow<LauncherConfig> = _config.asStateFlow()

    private val _apps = MutableStateFlow<List<AppInfo>>(emptyList())
    val apps: StateFlow<List<AppInfo>> = _apps.asStateFlow()

    private val _screen = MutableStateFlow(Screen.HOME)
    val screen: StateFlow<Screen> = _screen.asStateFlow()

    private val _homeToken = MutableStateFlow(0)
    val homeToken: StateFlow<Int> = _homeToken.asStateFlow()

    private val _now = MutableStateFlow(System.currentTimeMillis())
    val now: StateFlow<Long> = _now.asStateFlow()

    private val _online = MutableStateFlow(Net.isOnline(app))
    val online: StateFlow<Boolean> = _online.asStateFlow()

    private val _weather = MutableStateFlow(WeatherState())
    val weather: StateFlow<WeatherState> = _weather.asStateFlow()

    private val _prayer = MutableStateFlow(PrayerState(PrayerZones.byCode(_config.value.prayerZone)))
    val prayer: StateFlow<PrayerState> = _prayer.asStateFlow()

    /** Whether the adb-only permissions (widget binding, screensaver switching) have been granted. */
    private val _permissionsGranted = MutableStateFlow(appRepo.canSetScreensaver())
    val permissionsGranted: StateFlow<Boolean> = _permissionsGranted.asStateFlow()

    private val shizukuListener = Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
        if (requestCode != ShizukuHelper.REQUEST_CODE) return@OnRequestPermissionResultListener
        if (grantResult == PackageManager.PERMISSION_GRANTED) {
            grantPermissionsWithShizuku()
        } else {
            toast("Shizuku access was denied")
        }
    }

    private var weatherJob: Job? = null
    private var prayerJob: Job? = null
    private var reloadJob: Job? = null
    private var lastWeatherAttempt = 0L
    // Location/API key used for the weather currently shown, to skip redundant refreshes.
    private var weatherSettingsKey: String? = null
    private var prayerSettingsKey: String? = null

    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val pkg = intent.data?.schemeSpecificPart
            val replacing = intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)
            if (intent.action == Intent.ACTION_PACKAGE_REMOVED && !replacing && pkg != null) forgetPackage(pkg)
            // App updates send bursts of broadcasts; reload once they settle.
            reloadApps(delayMs = PACKAGE_RELOAD_DELAY_MS)
        }
    }

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            viewModelScope.launch {
                _online.value = true
                delay(1_500)
                refreshAll()
            }
        }

        override fun onLost(network: Network) {
            viewModelScope.launch { _online.value = false }
        }
    }

    init {
        reloadApps()
        startClock()
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addDataScheme("package")
        }
        ContextCompat.registerReceiver(app, packageReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        runCatching {
            app.getSystemService(ConnectivityManager::class.java)?.registerDefaultNetworkCallback(networkCallback)
        }
        runCatching { Shizuku.addRequestPermissionResultListener(shizukuListener) }
    }

    override fun onCleared() {
        val app = getApplication<Application>()
        runCatching { app.unregisterReceiver(packageReceiver) }
        runCatching { app.getSystemService(ConnectivityManager::class.java)?.unregisterNetworkCallback(networkCallback) }
        runCatching { Shizuku.removeRequestPermissionResultListener(shizukuListener) }
    }

    // region Permissions via Shizuku

    fun shizukuStatus(): ShizukuStatus = ShizukuHelper.status(getApplication())

    fun refreshPermissions() {
        _permissionsGranted.value = appRepo.canSetScreensaver()
    }

    fun requestShizukuPermission() {
        runCatching { ShizukuHelper.requestPermission() }.onFailure { toast("Shizuku is not running") }
    }

    fun grantPermissionsWithShizuku() {
        viewModelScope.launch {
            val ok = runCatching {
                ShizukuHelper.grantLauncherPermissions(getApplication<Application>().packageName)
            }.getOrDefault(false)
            _permissionsGranted.value = appRepo.canSetScreensaver()
            toast(
                if (ok) "Permissions granted – app widgets and screensaver switching are enabled" else "Shizuku could not grant the permissions",
                long = true,
            )
        }
    }

    fun openShizuku() {
        if (!appRepo.launch(ShizukuHelper.PACKAGE)) toast("Unable to open Shizuku")
    }

    fun openShizukuInStore() {
        if (!appRepo.openInStore(ShizukuHelper.PACKAGE)) toast("No app store available")
    }

    // endregion

    // region Apps

    fun reloadApps(delayMs: Long = 0) {
        reloadJob?.cancel()
        reloadJob = viewModelScope.launch {
            delay(delayMs)
            val list = appRepo.loadApps()
            _apps.value = list
            if (!_config.value.initialized) seedDefaults(list)
            // Warm the banner cache for apps shown in rows so they don't pop in later.
            _config.value.rows.flatMap { it.packages }.distinct().forEach { appRepo.banner(it) }
        }
    }

    fun cachedBanner(pkg: String) = appRepo.cachedBanner(pkg)

    suspend fun banner(pkg: String) = appRepo.banner(pkg)

    private fun seedDefaults(list: List<AppInfo>) {
        val pkgs = list.map { it.packageName }
        updateConfig {
            it.copy(
                initialized = true,
                pinned = pkgs.take(8),
                rows = listOf(AppRow(newId(), "Highlights", pkgs.drop(8).take(10))),
            )
        }
    }

    private fun forgetPackage(pkg: String) = updateConfig { c ->
        c.copy(pinned = c.pinned - pkg, rows = c.rows.map { it.copy(packages = it.packages - pkg) })
    }

    fun launch(pkg: String) {
        if (!appRepo.launch(pkg)) toast("Unable to open app")
    }

    fun uninstall(pkg: String) {
        if (!appRepo.uninstall(pkg)) toast("Uninstall is not available")
    }

    fun openInStore(pkg: String) {
        if (!appRepo.openInStore(pkg)) toast("No app store available")
    }

    fun openAppInfo(pkg: String) {
        appRepo.openAppInfo(pkg)
    }

    fun openSystemSettings() {
        if (!appRepo.openSystemSettings()) toast("Settings unavailable")
    }

    /** [fromIdle]: started by the inactivity timer, so never open settings screens or show messages. */
    fun startScreensaver(fromIdle: Boolean = false) {
        val chosen = _config.value.screensaver
        // Not chosen yet = the built-in Night Sky with clock.
        if (chosen == null || chosen == appRepo.nightSkyComponent || chosen == appRepo.nightSkyClockComponent) {
            // With the optional permission, also make it the TV's idle screensaver.
            if (chosen != null) appRepo.applyScreensaver(chosen)
            if (!appRepo.startNightSky(withClock = chosen != appRepo.nightSkyComponent) && !fromIdle) {
                toast("Screensaver unavailable")
            }
            return
        }
        if (chosen.isNotEmpty() && !appRepo.applyScreensaver(chosen) && !fromIdle) {
            toast(
                "Using the TV's current screensaver. To let Mega Launcher switch it, grant the permission in " +
                    "Launcher settings → Permissions.",
                long = true,
            )
        }
        if (appRepo.startSystemScreensaver()) return
        // This TV has no "start screensaver now" shortcut.
        when {
            fromIdle -> appRepo.startNightSky(withClock = true)
            !appRepo.openScreensaverSettings() -> toast("Screensaver unavailable")
        }
    }

    val nightSkyComponent: String get() = appRepo.nightSkyComponent
    val nightSkyClockComponent: String get() = appRepo.nightSkyClockComponent

    fun screensaverLabel(component: String?): String = when (component) {
        appRepo.nightSkyComponent -> NIGHT_SKY_NAME
        null, appRepo.nightSkyClockComponent -> "$NIGHT_SKY_NAME with clock"
        "" -> "TV default"
        else -> appRepo.screensavers().firstOrNull { it.component == component }?.label ?: "TV default"
    }

    fun setScreensaverTimeout(minutes: Int) = updateConfig { it.copy(screensaverTimeoutMinutes = minutes) }

    fun screensavers(): List<ScreensaverInfo> = appRepo.screensavers()

    fun canSetScreensaver(): Boolean = appRepo.canSetScreensaver()

    fun setScreensaver(component: String) = updateConfig { it.copy(screensaver = component) }

    fun openScreensaverSettings() {
        if (!appRepo.openScreensaverSettings()) toast("Screensaver settings unavailable")
    }

    // endregion

    // region Navigation

    fun setScreen(screen: Screen) {
        _screen.value = screen
    }

    fun goHome() {
        _screen.value = Screen.HOME
        _homeToken.update { it + 1 }
    }

    // endregion

    // region Layout editing

    private fun updateConfig(transform: (LauncherConfig) -> LauncherConfig) {
        _config.update(transform)
        store.save(activeId, _config.value)
    }

    fun togglePinned(pkg: String) = updateConfig { c ->
        c.copy(pinned = if (pkg in c.pinned) c.pinned - pkg else c.pinned + pkg)
    }

    fun movePinned(pkg: String, delta: Int) = updateConfig { c ->
        c.copy(pinned = c.pinned.moveItem(c.pinned.indexOf(pkg), delta))
    }

    fun addGadget(type: GadgetType) = updateConfig { it.copy(gadgets = it.gadgets + Gadget(type)) }

    suspend fun systemStats(kinds: Set<SystemGaugeKind>) = statsRepo.read(kinds, _config.value.useFahrenheit)

    /** Hiding the last gauge turns the gadget off; it comes back with every gauge shown. */
    fun setSystemGaugeVisible(kind: SystemGaugeKind, visible: Boolean) {
        val gauges = _config.value.systemGauges.let { if (visible) it + kind else it - kind }
        if (gauges.isNotEmpty()) {
            updateConfig { it.copy(systemGauges = gauges) }
            return
        }
        updateConfig { c ->
            c.copy(
                systemGauges = SystemGaugeKind.entries.toSet(),
                gadgets = c.gadgets.filterNot { it.type == GadgetType.SYSTEM },
            )
        }
        toast("System monitor turned off – all gauges were hidden")
    }

    fun addAppWidget(widgetId: Int) = updateConfig {
        it.copy(gadgets = it.gadgets + Gadget(GadgetType.APP_WIDGET, widgetId))
    }

    fun removeGadget(gadget: Gadget) {
        if (gadget.type == GadgetType.APP_WIDGET) widgetHost.delete(gadget.widgetId)
        updateConfig { c -> c.copy(gadgets = c.gadgets.filterNot { it.key == gadget.key }) }
    }

    fun moveGadget(key: String, delta: Int) = updateConfig { c ->
        c.copy(gadgets = c.gadgets.moveItem(c.gadgets.indexOfFirst { it.key == key }, delta))
    }

    fun setGadgetRowOpacity(percent: Int) = updateConfig { it.copy(gadgetOpacity = percent.coerceIn(0, 100)) }

    /** null = follow the row-wide opacity. */
    fun setGadgetOpacity(key: String, percent: Int?) = updateConfig { c ->
        c.copy(gadgets = c.gadgets.map { if (it.key == key) it.copy(opacity = percent?.coerceIn(0, 100)) else it })
    }

    fun addRow(title: String, packages: List<String> = emptyList()) = updateConfig {
        it.copy(rows = it.rows + AppRow(newId(), title.trim().ifBlank { "New row" }, packages))
    }

    fun renameRow(rowId: String, title: String) = updateRow(rowId) { it.copy(title = title.trim().ifBlank { it.title }) }

    fun deleteRow(rowId: String) = updateConfig { c -> c.copy(rows = c.rows.filterNot { it.id == rowId }) }

    fun moveRow(rowId: String, delta: Int) = updateConfig { c ->
        c.copy(rows = c.rows.moveItem(c.rows.indexOfFirst { it.id == rowId }, delta))
    }

    fun toggleAppInRow(rowId: String, pkg: String) = updateRow(rowId) { r ->
        r.copy(packages = if (pkg in r.packages) r.packages - pkg else r.packages + pkg)
    }

    fun addAppToRow(rowId: String, pkg: String) = updateRow(rowId) { r ->
        if (pkg in r.packages) r else r.copy(packages = r.packages + pkg)
    }

    fun moveAppInRow(rowId: String, pkg: String, delta: Int) = updateRow(rowId) { r ->
        r.copy(packages = r.packages.moveItem(r.packages.indexOf(pkg), delta))
    }

    private fun updateRow(rowId: String, transform: (AppRow) -> AppRow) = updateConfig { c ->
        c.copy(rows = c.rows.map { if (it.id == rowId) transform(it) else it })
    }

    // endregion

    // region Profiles

    /** First run after the profiles update: wrap the existing settings into a "default" profile. */
    private fun loadProfiles(): ProfilesState = profileRepo.load() ?: ProfilesState(
        profiles = listOf(Profile(DEFAULT_PROFILE_ID, store.load(DEFAULT_PROFILE_ID)?.profileName.orEmpty())),
        activeId = DEFAULT_PROFILE_ID,
    ).also { profileRepo.save(it) }

    private fun updateProfiles(transform: (ProfilesState) -> ProfilesState) {
        _profiles.update(transform)
        profileRepo.save(_profiles.value)
    }

    private fun updateProfile(id: String, transform: (Profile) -> Profile) = updateProfiles { s ->
        s.copy(profiles = s.profiles.map { if (it.id == id) transform(it) else it })
    }

    fun switchProfile(id: String) {
        val state = _profiles.value
        if (id == state.activeId || state.profiles.none { it.id == id }) return
        updateProfiles { it.copy(activeId = id) }
        backgroundRepo.profileId = id
        _config.value = store.load(id) ?: LauncherConfig()
        if (!_config.value.initialized && _apps.value.isNotEmpty()) seedDefaults(_apps.value)
        goHome()
        refreshAll()
    }

    fun addProfile(name: String) {
        val id = newId()
        val count = _profiles.value.profiles.size + 1
        updateProfiles { it.copy(profiles = it.profiles + Profile(id, name.trim().ifBlank { "Profile $count" })) }
        switchProfile(id)
    }

    fun deleteProfile(id: String) {
        val state = _profiles.value
        if (state.profiles.size <= 1) return
        if (id == state.activeId) switchProfile(state.profiles.first { it.id != id }.id)
        store.load(id)?.gadgets?.filter { it.type == GadgetType.APP_WIDGET }?.forEach { widgetHost.delete(it.widgetId) }
        store.delete(id)
        profileRepo.deleteAvatar(id)
        backgroundRepo.deleteFilesFor(id)
        updateProfiles { s -> s.copy(profiles = s.profiles.filterNot { it.id == id }) }
    }

    fun renameProfile(name: String) = updateProfile(activeId) { it.copy(name = name.trim()) }

    fun setAvatarPreset(index: Int) {
        profileRepo.deleteAvatar(activeId)
        updateProfile(activeId) { it.copy(avatar = Avatar(AvatarKind.PRESET, preset = index)) }
    }

    fun setAvatarDefault() {
        profileRepo.deleteAvatar(activeId)
        updateProfile(activeId) { it.copy(avatar = Avatar()) }
    }

    fun importAvatar(uri: Uri) {
        val id = activeId
        viewModelScope.launch {
            try {
                profileRepo.importAvatar(id, uri)
                updateProfile(id) { it.copy(avatar = Avatar(AvatarKind.IMAGE, version = System.currentTimeMillis())) }
                toast("Profile picture updated")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                toast("Could not use this image (${e.message})")
            }
        }
    }

    suspend fun loadAvatar(id: String) = profileRepo.loadAvatar(id)

    // endregion

    // region Settings

    fun setUse24Hour(enabled: Boolean) = updateConfig { it.copy(use24Hour = enabled) }

    fun setDateStyle(style: DateStyle) = updateConfig { it.copy(dateStyle = style) }

    fun setWeatherRefreshMinutes(minutes: Int) {
        updateConfig { it.copy(weatherRefreshMinutes = minutes) }
        refreshWeather()
    }

    fun setFahrenheit(enabled: Boolean) = updateConfig { it.copy(useFahrenheit = enabled) }

    fun setWeatherEnabled(enabled: Boolean) = setGadgetEnabled(GadgetType.WEATHER, enabled) { refreshWeather() }

    fun setPrayerEnabled(enabled: Boolean) = setGadgetEnabled(GadgetType.PRAYER, enabled) { refreshPrayer() }

    fun setShowSyuruk(enabled: Boolean) = updateConfig { it.copy(showSyuruk = enabled) }

    fun setHighlightCurrentPrayer(enabled: Boolean) = updateConfig { it.copy(highlightCurrentPrayer = enabled) }

    fun setSystemMonitorEnabled(enabled: Boolean) = setGadgetEnabled(GadgetType.SYSTEM, enabled) {}

    fun setWorldClockEnabled(enabled: Boolean) = setGadgetEnabled(GadgetType.WORLD_CLOCK, enabled) {}

    fun setWorldClock(index: Int, clock: WorldClock) = updateConfig { c ->
        val clocks = List(2) { i -> c.worldClocks.getOrElse(i) { WorldClocks.defaults[i] } }
        c.copy(worldClocks = clocks.mapIndexed { i, old -> if (i == index) clock else old })
    }

    private fun setGadgetEnabled(type: GadgetType, enabled: Boolean, onEnabled: () -> Unit) {
        if (enabled == _config.value.hasGadget(type)) return
        if (enabled) {
            addGadget(type)
            onEnabled()
        } else {
            updateConfig { c -> c.copy(gadgets = c.gadgets.filterNot { it.type == type }) }
        }
    }

    fun setWeatherApiKey(key: String) {
        updateConfig { it.copy(weatherApiKey = key.trim()) }
        refreshWeather(force = true)
    }

    fun setManualLocation(place: Place) {
        updateConfig { it.copy(autoLocation = false, manualLocation = place) }
        refreshWeather(force = true)
        refreshPrayer()
    }

    fun useAutoLocation() {
        updateConfig { it.copy(autoLocation = true) }
        refreshWeather(force = true)
        refreshPrayer()
    }

    /** null = pick the zone automatically from the current location. */
    fun setPrayerZone(code: String?) {
        updateConfig { if (code == null) it.copy(autoPrayerZone = true) else it.copy(autoPrayerZone = false, prayerZone = code) }
        refreshPrayer()
    }

    suspend fun searchCity(query: String): List<Place> =
        if (query.isBlank()) emptyList() else runCatching { locationRepo.search(query.trim()) }.getOrDefault(emptyList())

    fun setBackground(transform: (BackgroundConfig) -> BackgroundConfig) =
        updateConfig { it.copy(background = transform(it.background)) }

    fun importBackgroundFromUri(uri: Uri, video: Boolean) {
        importBackground(video) { backgroundRepo.importFromUri(uri, video) }
    }

    fun importBackgroundFromUrl(url: String, video: Boolean) {
        val link = url.trim()
        if (!link.startsWith("https://", ignoreCase = true)) {
            toast("Only https:// links are supported")
            return
        }
        importBackground(video) { backgroundRepo.importFromUrl(link, video) }
    }

    private fun importBackground(video: Boolean, block: suspend () -> Unit) {
        val kind = if (video) "video" else "image"
        viewModelScope.launch {
            toast("Loading $kind…")
            try {
                block()
                val version = System.currentTimeMillis()
                setBackground {
                    if (video) {
                        it.copy(type = BackgroundType.VIDEO, videoVersion = version)
                    } else {
                        it.copy(type = BackgroundType.IMAGE, imageVersion = version)
                    }
                }
                toast("Background updated")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                toast("Could not use this $kind (${e.message})")
            }
        }
    }

    // endregion

    // region Weather & prayer (cache first, refresh from network when stale and online)

    fun refreshAll(force: Boolean = false) {
        refreshWeather(force)
        refreshPrayer(force)
    }

    fun refreshWeather(force: Boolean = false) {
        if (weatherJob?.isActive == true) {
            if (!force) return
            weatherJob?.cancel()
        }
        if (!_config.value.hasGadget(GadgetType.WEATHER)) return
        val settingsKey = _config.value.let { "${it.autoLocation}|${it.manualLocation?.key}|${it.weatherApiKey}" }
        val shown = _weather.value.data
        val refreshMs = _config.value.weatherRefreshMinutes * 60_000L
        if (!force && shown != null && settingsKey == weatherSettingsKey &&
            (refreshMs <= 0 || System.currentTimeMillis() - shown.fetchedAt < refreshMs)
        ) {
            return
        }
        weatherJob = viewModelScope.launch {
            val cfg = _config.value
            val online = Net.isOnline(getApplication()).also { _online.value = it }
            val place = locationRepo.resolve(cfg, online)
            if (place == null) {
                val msg = if (online) "Set your location in Settings" else "No internet connection"
                _weather.update { it.copy(loading = false, error = msg) }
                return@launch
            }

            val cached = weatherRepo.cached()?.takeIf { it.locationKey == place.key }
            if (cached != null) {
                _weather.value = WeatherState(cached)
                weatherSettingsKey = settingsKey
            } else if (_weather.value.data?.locationKey != place.key) {
                _weather.value = WeatherState()
            }

            val interval = cfg.weatherRefreshMinutes * 60_000L
            val fresh = cached != null && (interval <= 0 || System.currentTimeMillis() - cached.fetchedAt < interval)
            if ((fresh && !force) || !online) {
                if (cached == null) _weather.update { it.copy(error = "No internet connection") }
                return@launch
            }

            _weather.update { it.copy(loading = true, error = null) }
            lastWeatherAttempt = System.currentTimeMillis()
            try {
                _weather.value = WeatherState(weatherRepo.fetch(place, cfg.weatherApiKey))
                weatherSettingsKey = settingsKey
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _weather.update { s ->
                    s.copy(loading = false, error = if (s.data == null) "Weather unavailable (${e.message})" else null)
                }
            }
        }
    }

    fun refreshPrayer(force: Boolean = false) {
        if (prayerJob?.isActive == true) {
            if (!force) return
            prayerJob?.cancel()
        }
        if (!_config.value.hasGadget(GadgetType.PRAYER)) return
        // Location is part of the key because the automatic zone follows it.
        val settingsKey = _config.value.let { "${it.autoPrayerZone}|${it.prayerZone}|${it.autoLocation}|${it.manualLocation?.key}" }
        if (!force && settingsKey == prayerSettingsKey && _prayer.value.day?.date == LocalDate.now().toString()) return
        prayerJob = viewModelScope.launch {
            val cfg = _config.value
            val online = Net.isOnline(getApplication()).also { _online.value = it }
            val zone = if (cfg.autoPrayerZone) {
                locationRepo.resolve(cfg, online)?.let { PrayerZones.nearest(it.lat, it.lon) }
                    ?: PrayerZones.byCode(cfg.prayerZone)
            } else {
                PrayerZones.byCode(cfg.prayerZone)
            }
            _prayer.update { it.copy(zone = zone, loading = true) }
            val result = prayerRepo.today(zone.code, online, force)
            _prayer.value = PrayerState(zone, result.day, loading = false, error = result.error)
            if (result.day != null) prayerSettingsKey = settingsKey
        }
    }

    private fun startClock() {
        viewModelScope.launch {
            var date = LocalDate.now()
            while (isActive) {
                val t = System.currentTimeMillis()
                _now.value = t
                delay(60_000 - t % 60_000 + 50)
                val today = LocalDate.now()
                if (today != date) {
                    date = today
                    refreshPrayer()
                }
                val nowMs = System.currentTimeMillis()
                val fetchedAt = _weather.value.data?.fetchedAt ?: 0L
                val interval = _config.value.weatherRefreshMinutes * 60_000L
                if (interval > 0 && _online.value && nowMs - fetchedAt > interval && nowMs - lastWeatherAttempt > WEATHER_RETRY_MS) {
                    refreshWeather()
                }
            }
        }
    }

    // endregion

    fun toast(message: String, long: Boolean = false) {
        Toast.makeText(getApplication(), message, if (long) Toast.LENGTH_LONG else Toast.LENGTH_SHORT).show()
    }

    private fun newId() = UUID.randomUUID().toString()

    private companion object {
        const val WEATHER_RETRY_MS = 5L * 60 * 1000
        const val PACKAGE_RELOAD_DELAY_MS = 1_500L
    }
}
