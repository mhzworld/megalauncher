package com.megalauncher.data

import android.content.Context
import android.content.pm.PackageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku

enum class ShizukuStatus { NOT_INSTALLED, NOT_RUNNING, NEEDS_PERMISSION, READY }

/** Runs the launcher's one-time adb permission grants on the TV itself via Shizuku (no root, no PC). */
object ShizukuHelper {
    const val PACKAGE = "moe.shizuku.privileged.api"
    const val REQUEST_CODE = 0x5A1

    fun status(context: Context): ShizukuStatus {
        val installed = runCatching { context.packageManager.getPackageInfo(PACKAGE, 0) }.isSuccess
        if (!installed) return ShizukuStatus.NOT_INSTALLED
        val running = runCatching { Shizuku.pingBinder() && !Shizuku.isPreV11() }.getOrDefault(false)
        if (!running) return ShizukuStatus.NOT_RUNNING
        return if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
            ShizukuStatus.READY
        } else {
            ShizukuStatus.NEEDS_PERMISSION
        }
    }

    fun requestPermission() = Shizuku.requestPermission(REQUEST_CODE)

    suspend fun grantLauncherPermissions(packageName: String): Boolean = withContext(Dispatchers.IO) {
        val userId = android.os.Process.myUid() / 100_000
        listOf(
            "appwidget grantbind --package $packageName --user $userId",
            "pm grant $packageName android.permission.WRITE_SECURE_SETTINGS",
        ).all { runShell(it) == 0 }
    }

    // Shizuku 13 hides newProcess(); it is still the simplest way to run a one-off shell command.
    private fun runShell(command: String): Int {
        val newProcess = Shizuku::class.java.getDeclaredMethod(
            "newProcess",
            Array<String>::class.java,
            Array<String>::class.java,
            String::class.java,
        ).apply { isAccessible = true }
        val process = newProcess.invoke(null, arrayOf("sh", "-c", command), null, null) as Process
        return process.waitFor()
    }
}
