package com.megalauncher.data

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.provider.Settings
import android.service.dreams.DreamService
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import com.megalauncher.screensaver.NightSkyActivity
import com.megalauncher.screensaver.NightSkyClockDream
import com.megalauncher.screensaver.NightSkyDream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

data class AppInfo(
    val packageName: String,
    val label: String,
    val icon: ImageBitmap,
    val isSystem: Boolean,
    val updatedAt: Long,
)

data class ScreensaverInfo(val component: String, val label: String, val appName: String)

class AppRepository(private val context: Context) {
    private val pm = context.packageManager

    // Sized for the screen density (720p ≈ 1.33x, 1080p 2x, 4K up to 4x), capped to keep RAM in check on 4K.
    private val density = context.resources.displayMetrics.density
    private val iconPx = (80 * density).roundToInt().coerceIn(96, 192)
    private val bannerW = (192 * density).roundToInt().coerceIn(256, 480)
    private val bannerH = bannerW * 9 / 16

    // Banners are only shown in user rows, so they're decoded on demand and kept in a bounded cache.
    private val banners = object : LruCache<String, ImageBitmap>(BANNER_CACHE_BYTES) {
        override fun sizeOf(key: String, value: ImageBitmap) = value.width * value.height * 4
    }
    private val noBanner = HashSet<String>()

    /** Previous results, reused for packages that haven't been updated so icons aren't decoded again. */
    @Volatile
    private var known: Map<String, AppInfo> = emptyMap()

    suspend fun loadApps(): List<AppInfo> = withContext(Dispatchers.IO) {
        val previous = known
        val found = LinkedHashMap<String, AppInfo>()
        for (category in listOf(Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER)) {
            val query = Intent(Intent.ACTION_MAIN).addCategory(category)
            for (ri in pm.queryIntentActivities(query, 0)) {
                val pkg = ri.activityInfo.packageName
                if (pkg == context.packageName || pkg in found) continue
                val updatedAt = runCatching { pm.getPackageInfo(pkg, 0).lastUpdateTime }.getOrDefault(0L)
                val cached = previous[pkg]?.takeIf { it.updatedAt == updatedAt && updatedAt != 0L }
                if (cached == null) synchronized(banners) {
                    banners.remove(pkg)
                    noBanner.remove(pkg)
                }
                found[pkg] = cached ?: runCatching { toAppInfo(ri, updatedAt) }.getOrNull() ?: continue
            }
        }
        known = found
        found.values.sortedBy { it.label.lowercase() }
    }

    private fun toAppInfo(ri: ResolveInfo, updatedAt: Long): AppInfo {
        val ai = ri.activityInfo
        val icon = iconBitmap(ai.loadIcon(pm)).asImageBitmap()
        val isSystem = (ai.applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
        return AppInfo(ai.packageName, ri.loadLabel(pm).toString(), icon, isSystem, updatedAt)
    }

    fun cachedBanner(pkg: String): ImageBitmap? = synchronized(banners) { banners.get(pkg) }

    /** The app's TV banner, or null if it has none. */
    suspend fun banner(pkg: String): ImageBitmap? = withContext(Dispatchers.IO) {
        synchronized(banners) {
            banners.get(pkg)?.let { return@withContext it }
            if (pkg in noBanner) return@withContext null
        }
        val bitmap = runCatching {
            val intent = pm.getLeanbackLaunchIntentForPackage(pkg) ?: pm.getLaunchIntentForPackage(pkg)
            val drawable = intent?.let { pm.getActivityBanner(it) } ?: pm.getApplicationBanner(pkg)
            drawable?.toBitmap(bannerW, bannerH)?.asImageBitmap()
        }.getOrNull()
        synchronized(banners) {
            if (bitmap != null) banners.put(pkg, bitmap) else noBanner.add(pkg)
        }
        bitmap
    }

    /** Adaptive icons are drawn edge-to-edge (no system mask) so they fill round/rounded frames. */
    private fun iconBitmap(drawable: Drawable): Bitmap {
        if (drawable !is AdaptiveIconDrawable) return drawable.toBitmap(iconPx, iconPx)
        val bitmap = Bitmap.createBitmap(iconPx, iconPx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        // Layers are 108dp with the visible icon in the centre 72dp, so draw them at 1.5x.
        val inset = iconPx / 4
        listOfNotNull(drawable.background, drawable.foreground).forEach { layer ->
            layer.setBounds(-inset, -inset, iconPx + inset, iconPx + inset)
            layer.draw(canvas)
        }
        return bitmap
    }

    fun launch(pkg: String): Boolean {
        val intent = pm.getLeanbackLaunchIntentForPackage(pkg)
            ?: pm.getLaunchIntentForPackage(pkg)
            ?: return false
        return start(intent)
    }

    fun uninstall(pkg: String) = start(Intent(Intent.ACTION_DELETE, Uri.parse("package:$pkg")))

    fun openInStore(pkg: String) =
        start(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$pkg"))) ||
            start(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$pkg")))

    fun openAppInfo(pkg: String) =
        start(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$pkg")))

    fun openSystemSettings() = start(Intent(Settings.ACTION_SETTINGS))

    fun startSystemScreensaver() =
        start(Intent(Intent.ACTION_MAIN).setClassName("com.android.systemui", "com.android.systemui.Somnambulator"))

    val nightSkyComponent: String = ComponentName(context, NightSkyDream::class.java).flattenToString()
    val nightSkyClockComponent: String = ComponentName(context, NightSkyClockDream::class.java).flattenToString()

    fun startNightSky(withClock: Boolean) =
        start(Intent(context, NightSkyActivity::class.java).putExtra(NightSkyActivity.EXTRA_CLOCK, withClock))

    fun openScreensaverSettings() = start(Intent(Settings.ACTION_DREAM_SETTINGS))

    /** Other installed screensavers (DreamService implementations), e.g. the TV's ambient mode or Aerial Views. */
    fun screensavers(): List<ScreensaverInfo> = runCatching {
        pm.queryIntentServices(Intent(DreamService.SERVICE_INTERFACE), 0)
            .filter { it.serviceInfo.packageName != context.packageName }
            .map { ri ->
            val si = ri.serviceInfo
            ScreensaverInfo(
                component = ComponentName(si.packageName, si.name).flattenToString(),
                label = ri.loadLabel(pm).toString(),
                appName = si.applicationInfo.loadLabel(pm).toString(),
            )
        }.sortedBy { it.label.lowercase() }
    }.getOrDefault(emptyList())

    /** Requires `adb shell pm grant <pkg> android.permission.WRITE_SECURE_SETTINGS`. */
    fun canSetScreensaver(): Boolean =
        context.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED

    fun applyScreensaver(component: String): Boolean = canSetScreensaver() && runCatching {
        Settings.Secure.putString(context.contentResolver, "screensaver_components", component) &&
            Settings.Secure.putInt(context.contentResolver, "screensaver_enabled", 1)
    }.getOrDefault(false)

    private fun start(intent: Intent): Boolean = try {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (e: ActivityNotFoundException) {
        false
    } catch (e: SecurityException) {
        false
    }

    private companion object {
        const val BANNER_CACHE_BYTES = 8 * 1024 * 1024
    }
}
