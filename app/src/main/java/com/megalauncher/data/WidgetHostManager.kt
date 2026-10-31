package com.megalauncher.data

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.content.pm.PackageManager

class WidgetHostManager(private val context: Context) {
    private val manager: AppWidgetManager? =
        if (context.packageManager.hasSystemFeature(PackageManager.FEATURE_APP_WIDGETS)) {
            runCatching { AppWidgetManager.getInstance(context) }.getOrNull()
        } else {
            null
        }
    private val host: AppWidgetHost? = manager?.let { AppWidgetHost(context, HOST_ID) }

    val isSupported: Boolean get() = manager != null

    fun providers(): List<AppWidgetProviderInfo> =
        runCatching { manager?.installedProviders.orEmpty() }.getOrDefault(emptyList())

    fun allocate(): Int? = runCatching { host?.allocateAppWidgetId() }.getOrNull()

    fun bindIfAllowed(id: Int, info: AppWidgetProviderInfo): Boolean =
        runCatching { manager?.bindAppWidgetIdIfAllowed(id, info.provider) == true }.getOrDefault(false)

    fun delete(id: Int) {
        runCatching { host?.deleteAppWidgetId(id) }
    }

    fun createView(viewContext: Context, id: Int): AppWidgetHostView? {
        val info = manager?.getAppWidgetInfo(id) ?: return null
        return runCatching { host?.createView(viewContext, id, info) }.getOrNull()
    }

    fun widgetLabel(info: AppWidgetProviderInfo): String = info.loadLabel(context.packageManager)

    fun appLabel(info: AppWidgetProviderInfo): String = runCatching {
        val pm = context.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(info.provider.packageName, 0)).toString()
    }.getOrDefault(info.provider.packageName)

    fun startListening() {
        runCatching { host?.startListening() }
    }

    fun stopListening() {
        runCatching { host?.stopListening() }
    }

    private companion object {
        const val HOST_ID = 0x5042
    }
}
