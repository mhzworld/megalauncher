package com.megalauncher

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import com.megalauncher.ui.LauncherApp
import com.megalauncher.ui.LauncherTheme

class MainActivity : ComponentActivity() {
    private val vm: LauncherViewModel by viewModels()
    private val idleHandler = Handler(Looper.getMainLooper())
    private var lastInteraction = 0L

    // Re-reads the timeout so a changed setting applies; an open dialog (it takes window focus) postpones it.
    private val idleScreensaver: Runnable = Runnable {
        val minutes = vm.config.value.screensaverTimeoutMinutes
        if (minutes <= 0) return@Runnable
        val remaining = lastInteraction + minutes * 60_000L - SystemClock.uptimeMillis()
        when {
            remaining > 0 -> idleHandler.postDelayed(idleScreensaver, remaining)
            hasWindowFocus() -> vm.startScreensaver(fromIdle = true)
            else -> scheduleIdleScreensaver()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            LauncherTheme {
                LauncherApp(vm)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        vm.widgetHost.startListening()
        vm.refreshAll()
    }

    override fun onStop() {
        vm.widgetHost.stopListening()
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        scheduleIdleScreensaver()
    }

    override fun onPause() {
        idleHandler.removeCallbacks(idleScreensaver)
        super.onPause()
    }

    override fun onUserInteraction() {
        super.onUserInteraction()
        scheduleIdleScreensaver()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) scheduleIdleScreensaver()
    }

    private fun scheduleIdleScreensaver() {
        idleHandler.removeCallbacks(idleScreensaver)
        lastInteraction = SystemClock.uptimeMillis()
        val minutes = vm.config.value.screensaverTimeoutMinutes
        if (minutes > 0) idleHandler.postDelayed(idleScreensaver, minutes * 60_000L)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.hasCategory(Intent.CATEGORY_HOME)) vm.goHome()
    }
}
