package com.megalauncher.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.media.MediaPlayer
import android.view.Surface
import android.view.TextureView
import java.io.FileInputStream

/** Muted, looping video on a TextureView (supports alpha, transforms and RenderEffect). */
@SuppressLint("ViewConstructor")
class LoopingVideoView(context: Context) : TextureView(context), TextureView.SurfaceTextureListener {
    /** Called (on the main thread) when the video can't be played, with a short user-facing reason. */
    var onError: ((String) -> Unit)? = null

    private var player: MediaPlayer? = null
    private var surface: Surface? = null
    private var path: String? = null
    private var sourceKey: String? = null
    private var prepared = false
    private var playing = true
    private var zoom = true
    private var videoW = 0
    private var videoH = 0
    private var restarts = 0
    // Set when the file itself can't be played, so returning to the launcher doesn't retry and re-report it.
    private var unplayable = false
    private val restart = Runnable { startPlayer() }

    init {
        surfaceTextureListener = this
        isOpaque = false
    }

    /** [key] changes whenever the file is replaced, forcing a reload of the same path. */
    fun setSource(newPath: String, key: String) {
        if (key == sourceKey) return
        sourceKey = key
        path = newPath
        restarts = 0
        unplayable = false
        startPlayer()
    }

    fun setZoom(enabled: Boolean) {
        if (zoom == enabled) return
        zoom = enabled
        updateTransform()
    }

    fun setPlaying(play: Boolean) {
        playing = play
        // A player that failed earlier is retried when the launcher comes back to the front.
        if (play && player == null && surface != null && !unplayable) {
            restarts = 0
            startPlayer()
            return
        }
        val p = player ?: return
        if (!prepared) return
        runCatching { if (play) p.start() else p.pause() }
    }

    fun release() {
        prepared = false
        player?.let { runCatching { it.release() } }
        player = null
    }

    private fun startPlayer() {
        removeCallbacks(restart)
        release()
        val s = surface ?: return
        val file = path ?: return
        val mp = MediaPlayer()
        player = mp
        mp.setSurface(s)
        mp.isLooping = true
        mp.setVolume(0f, 0f)
        mp.setOnVideoSizeChangedListener { _, w, h ->
            videoW = w
            videoH = h
            updateTransform()
        }
        mp.setOnPreparedListener {
            if (player !== it) return@setOnPreparedListener
            prepared = true
            if (playing) it.start()
        }
        mp.setOnErrorListener { failed, what, extra ->
            if (player !== failed) return@setOnErrorListener true
            release()
            // The TV's media service restarted (often after another app used the decoder): try again.
            if (what == MediaPlayer.MEDIA_ERROR_SERVER_DIED && restarts < MAX_RESTARTS) {
                restarts++
                postDelayed(restart, RESTART_DELAY_MS)
            } else {
                unplayable = what != MediaPlayer.MEDIA_ERROR_SERVER_DIED
                onError?.invoke(describe(what, extra))
            }
            true
        }
        try {
            // Opened here (not by path) so the media service never needs access to the app's private files.
            FileInputStream(file).use { mp.setDataSource(it.fd) }
            mp.prepareAsync()
        } catch (e: Exception) {
            release()
            unplayable = true
            onError?.invoke("The video file can't be opened (${e.message})")
        }
    }

    private fun describe(what: Int, extra: Int): String = when {
        extra == MediaPlayer.MEDIA_ERROR_UNSUPPORTED || extra == MediaPlayer.MEDIA_ERROR_MALFORMED ->
            "This TV can't decode this video. Try an H.264 MP4 at 1080p."
        extra == MediaPlayer.MEDIA_ERROR_IO -> "The video file can't be read."
        what == MediaPlayer.MEDIA_ERROR_SERVER_DIED -> "The TV's video player stopped. Re-open the launcher to retry."
        else -> "The video can't be played on this TV (error $what/$extra). Try an H.264 MP4 at 1080p."
    }

    // TextureView stretches video to the view; scaling one axis back up crops instead.
    private fun updateTransform() {
        if (videoW == 0 || videoH == 0 || width == 0 || height == 0) return
        val matrix = Matrix()
        if (zoom) {
            val viewRatio = width.toFloat() / height
            val videoRatio = videoW.toFloat() / videoH
            if (videoRatio > viewRatio) {
                matrix.setScale(videoRatio / viewRatio, 1f, width / 2f, height / 2f)
            } else {
                matrix.setScale(1f, viewRatio / videoRatio, width / 2f, height / 2f)
            }
        }
        setTransform(matrix)
    }

    override fun onSurfaceTextureAvailable(texture: SurfaceTexture, width: Int, height: Int) {
        surface = Surface(texture)
        startPlayer()
    }

    override fun onSurfaceTextureSizeChanged(texture: SurfaceTexture, width: Int, height: Int) = updateTransform()

    override fun onSurfaceTextureDestroyed(texture: SurfaceTexture): Boolean {
        release()
        surface?.release()
        surface = null
        return true
    }

    override fun onSurfaceTextureUpdated(texture: SurfaceTexture) = Unit

    override fun onDetachedFromWindow() {
        removeCallbacks(restart)
        super.onDetachedFromWindow()
    }

    private companion object {
        const val MAX_RESTARTS = 2
        const val RESTART_DELAY_MS = 1_000L
    }
}
