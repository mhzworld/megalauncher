package com.megalauncher.screensaver

import android.app.Activity
import android.content.Context
import android.os.Bundle
import android.os.SystemClock
import android.service.dreams.DreamService
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.WindowManager
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.megalauncher.data.ConfigStore
import com.megalauncher.data.DEFAULT_PROFILE_ID
import com.megalauncher.data.ProfileRepository

const val NIGHT_SKY_NAME = "MHz Night Sky"

/** Follows the active profile's 12/24-hour setting so the screensaver clock matches the launcher. */
internal fun launcherUses24Hour(context: Context): Boolean {
    val profileId = ProfileRepository(context).load()?.activeId ?: DEFAULT_PROFILE_ID
    return ConfigStore(context).load(profileId)?.use24Hour ?: true
}

/** Runs Night Sky from the launcher; needs no permission, unlike switching the TV's system screensaver. */
class NightSkyActivity : Activity() {
    private var startedAt = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContentView(NightSkyView(this, intent.getBooleanExtra(EXTRA_CLOCK, false), launcherUses24Hour(this)))
        WindowCompat.getInsetsController(window, window.decorView).hide(WindowInsetsCompat.Type.systemBars())
        startedAt = SystemClock.uptimeMillis()
    }

    // Any button closes it; the short grace period stops the press that opened it from closing it.
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_UP && SystemClock.uptimeMillis() - startedAt > WAKE_GRACE_MS) finish()
        return true
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (ev.action == MotionEvent.ACTION_UP) finish()
        return true
    }

    override fun onStop() {
        super.onStop()
        finish()
    }

    companion object {
        const val EXTRA_CLOCK = "clock"
        private const val WAKE_GRACE_MS = 600L
    }
}

/** Night Sky as a system screensaver, selectable in the TV's own screensaver settings. */
open class NightSkyDream : DreamService() {
    protected open val showClock = false

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        isInteractive = false
        isFullscreen = true
        setContentView(NightSkyView(this, showClock, launcherUses24Hour(this)))
    }
}

class NightSkyClockDream : NightSkyDream() {
    override val showClock = true
}
