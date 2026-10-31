package com.megalauncher.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import java.io.File
import java.io.IOException
import kotlin.math.max
import kotlin.math.min

/** Profile list (name, avatar, active profile) and custom avatar images. */
class ProfileRepository(private val context: Context) {
    private val prefs = context.getSharedPreferences("launcher", Context.MODE_PRIVATE)

    fun load(): ProfilesState? = prefs.getString(KEY, null)?.let {
        runCatching { AppJson.decodeFromString<ProfilesState>(it) }.getOrNull()?.takeIf { s -> s.profiles.isNotEmpty() }
    }

    fun save(state: ProfilesState) {
        prefs.edit().putString(KEY, AppJson.encodeToString(state)).apply()
    }

    private fun avatarFile(id: String) = File(context.filesDir, "avatar_$id.png")

    fun deleteAvatar(id: String) {
        avatarFile(id).delete()
    }

    /** Center-crops the picture to a square and stores a 256px PNG. */
    suspend fun importAvatar(id: String, uri: Uri) = withContext(Dispatchers.IO) {
        val decoded = decode(uri) ?: throw IOException("Not a supported image")
        val side = min(decoded.width, decoded.height)
        val square = Bitmap.createBitmap(decoded, (decoded.width - side) / 2, (decoded.height - side) / 2, side, side)
        val scaled = Bitmap.createScaledBitmap(square, AVATAR_PX, AVATAR_PX, true)
        avatarFile(id).outputStream().use { scaled.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    suspend fun loadAvatar(id: String): ImageBitmap? = withContext(Dispatchers.IO) {
        val file = avatarFile(id)
        if (!file.exists()) return@withContext null
        BitmapFactory.decodeFile(file.path)?.asImageBitmap()
    }

    private fun decode(uri: Uri): Bitmap? {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth > 0 && bounds.outHeight > 0) {
            var sample = 1
            while (min(bounds.outWidth, bounds.outHeight) / (sample * 2) >= AVATAR_PX) sample *= 2
            resolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
            }?.let { return it }
        }
        // Formats BitmapFactory can't read (e.g. AVIF).
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return null
        return runCatching {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(resolver, uri)) { decoder, info, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                val scale = AVATAR_PX.toFloat() / min(info.size.width, info.size.height)
                if (scale < 1f) {
                    decoder.setTargetSize(max(1, (info.size.width * scale).toInt()), max(1, (info.size.height * scale).toInt()))
                }
            }
        }.getOrNull()
    }

    private companion object {
        const val KEY = "profiles"
        const val AVATAR_PX = 256
    }
}
