package com.megalauncher.data

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.drawable.AnimatedImageDrawable
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** Lens-blur disc radius per level 0–10, in pixels on a 1920 px wide image. Level 1 is a gentle but visible softening. */
private val BLUR_RADIUS = floatArrayOf(0f, 2f, 4f, 6f, 8f, 11f, 14f, 17f, 21f, 25f, 30f)

fun blurRadius(level: Int): Float = BLUR_RADIUS[level.coerceIn(0, BLUR_RADIUS.lastIndex)]

// sRGB (gamma 2.2) <-> 16-bit linear light, so blurring mixes light like a real lens.
private val SRGB_TO_LINEAR by lazy { IntArray(256) { ((it / 255.0).pow(2.2) * 65535).roundToInt() } }
private val LINEAR_TO_SRGB by lazy { IntArray(65536) { ((it / 65535.0).pow(1 / 2.2) * 255).roundToInt() } }

data class DeviceMedia(val uri: Uri, val name: String)

/** Stores one imported background image (static/animated) and one video per profile in private app storage. */
class BackgroundRepository(private val context: Context) {
    /** Set when the active profile changes; the default profile keeps the original file names. */
    var profileId: String = DEFAULT_PROFILE_ID

    private val imageFile get() = fileFor("background_image", profileId)
    private val videoFile get() = fileFor("background_video", profileId)

    private fun fileFor(base: String, id: String) =
        File(context.filesDir, if (id == DEFAULT_PROFILE_ID) base else "${base}_$id")

    fun deleteFilesFor(id: String) {
        fileFor("background_image", id).delete()
        fileFor("background_video", id).delete()
    }

    val hasImage: Boolean get() = imageFile.exists()
    val hasVideo: Boolean get() = videoFile.exists()
    val imagePath: String get() = imageFile.path
    val videoPath: String get() = videoFile.path

    // Resolve the target first so switching profile mid-import can't write into the other profile.
    suspend fun importFromUri(uri: Uri, video: Boolean) = withContext(Dispatchers.IO) {
        val target = if (video) videoFile else imageFile
        val tmp = tempFile()
        try {
            val input = context.contentResolver.openInputStream(uri) ?: throw IOException("Cannot open file")
            input.use { Net.copyLimited(it, tmp, maxBytes(video)) }
            commit(tmp, video, target)
        } finally {
            tmp.delete()
        }
    }

    suspend fun importFromUrl(url: String, video: Boolean) = withContext(Dispatchers.IO) {
        val target = if (video) videoFile else imageFile
        val tmp = tempFile()
        try {
            Net.download(url, tmp, maxBytes(video))
            commit(tmp, video, target)
        } finally {
            tmp.delete()
        }
    }

    private fun tempFile() = File(context.cacheDir, "background_import.tmp")

    private fun maxBytes(video: Boolean) = if (video) MAX_VIDEO_BYTES else MAX_IMAGE_BYTES

    private fun commit(tmp: File, video: Boolean, target: File) {
        if (video) {
            val retriever = MediaMetadataRetriever()
            val hasVideo = try {
                retriever.setDataSource(tmp.path)
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO) == "yes"
            } catch (e: RuntimeException) {
                false
            } finally {
                retriever.release()
            }
            if (!hasVideo) throw IOException("Not a supported video")
            tmp.copyTo(target, overwrite = true)
        } else {
            if (!isDecodableImage(tmp)) throw IOException("Not a supported image")
            tmp.copyTo(target, overwrite = true)
        }
    }

    private fun isDecodableImage(file: File): Boolean {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        if (bounds.outWidth > 0 && bounds.outHeight > 0) return true
        // ImageDecoder covers formats BitmapFactory may not (e.g. AVIF, some HEIF variants).
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return false
        return runCatching {
            ImageDecoder.decodeDrawable(ImageDecoder.createSource(file)) { decoder, _, _ -> decoder.setTargetSize(16, 16) }
        }.isSuccess
    }

    /**
     * Decodes the background image scaled down to roughly cover [maxW] x [maxH].
     * [softwareBlur] > 0 pre-blurs static images once (cheaper than a per-frame RenderEffect).
     */
    suspend fun loadDrawable(screenW: Int, screenH: Int, softwareBlur: Int): Drawable? = withContext(Dispatchers.IO) {
        if (!imageFile.exists()) return@withContext null
        // Software bitmaps (blur, Android 8) live on the app heap: a blurred 4K image would need ~100 MB, so cap at 1080p.
        // Hardware bitmaps (Android 9+, no blur) keep full 4K sharpness in GPU memory.
        val software = softwareBlur > 0 || Build.VERSION.SDK_INT < Build.VERSION_CODES.P
        val cap = if (software) min(1f, min(SOFTWARE_MAX_W.toFloat() / screenW, SOFTWARE_MAX_H.toFloat() / screenH)) else 1f
        val maxW = max(1, (screenW * cap).roundToInt())
        val maxH = max(1, (screenH * cap).roundToInt())
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val drawable = ImageDecoder.decodeDrawable(ImageDecoder.createSource(imageFile)) { decoder, info, _ ->
                    val w = info.size.width
                    val h = info.size.height
                    val scale = max(maxW.toFloat() / w, maxH.toFloat() / h)
                    if (scale < 1f) {
                        decoder.setTargetSize(max(1, (w * scale).roundToInt()), max(1, (h * scale).roundToInt()))
                    }
                    if (softwareBlur > 0) decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                }
                if (drawable is AnimatedImageDrawable) {
                    drawable.repeatCount = AnimatedImageDrawable.REPEAT_INFINITE
                    drawable
                } else {
                    softBlur(drawable, softwareBlur)
                }
            } else {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(imageFile.path, bounds)
                var sample = 1
                while (bounds.outWidth / (sample * 2) >= maxW && bounds.outHeight / (sample * 2) >= maxH) sample *= 2
                val bitmap = BitmapFactory.decodeFile(imageFile.path, BitmapFactory.Options().apply { inSampleSize = sample })
                    ?: return@runCatching null
                softBlur(BitmapDrawable(context.resources, bitmap), softwareBlur)
            }
        }.getOrNull()
    }

    /**
     * Camera-style lens (bokeh) blur, done once at load time: each pixel becomes the average of a disc,
     * computed in linear light so highlights bloom like a defocused lens instead of turning grey.
     */
    private fun softBlur(drawable: Drawable, level: Int): Drawable {
        if (level <= 0 || drawable !is BitmapDrawable) return drawable
        val src = drawable.bitmap
        val radius = blurRadius(level) * max(src.width, src.height) / 1920f
        // Large discs are computed on a smaller copy; their output has no fine detail, so the upscale is invisible.
        val scale = ceil(radius / MAX_WORK_RADIUS).toInt().coerceAtLeast(1)
        val w = max(1, src.width / scale)
        val h = max(1, src.height / scale)
        val work = if (scale == 1) src else Bitmap.createScaledBitmap(src, w, h, true)
        val pixels = IntArray(w * h)
        work.getPixels(pixels, 0, w, 0, 0, w, h)
        discBlur(pixels, w, h, radius / scale, src.hasAlpha())
        val blurred = Bitmap.createBitmap(pixels, w, h, Bitmap.Config.ARGB_8888)
        return BitmapDrawable(context.resources, blurred).apply { isFilterBitmap = true }
    }

    /**
     * Disc-kernel average using per-row prefix sums (O(2r+1) per pixel), one channel at a time.
     * Works in place: each channel's prefix sums are built before that channel is overwritten.
     */
    private fun discBlur(pixels: IntArray, w: Int, h: Int, radius: Float, hasAlpha: Boolean) {
        val r = radius.toInt().coerceAtLeast(1)
        val halfWidth = IntArray(2 * r + 1) { i ->
            val dy = (i - r).toFloat()
            sqrt(max(0f, radius * radius - dy * dy)).roundToInt()
        }
        if (!hasAlpha) {
            for (i in pixels.indices) pixels[i] = pixels[i] or (0xFF shl 24)
        }
        val prefix = IntArray((w + 1) * h)
        val shifts = if (hasAlpha) intArrayOf(24, 16, 8, 0) else intArrayOf(16, 8, 0)
        for (shift in shifts) {
            val linear = shift != 24
            val keep = (0xFF shl shift).inv()
            for (y in 0 until h) {
                val row = y * w
                val p = y * (w + 1)
                prefix[p] = 0
                for (x in 0 until w) {
                    val v = (pixels[row + x] ushr shift) and 0xFF
                    prefix[p + x + 1] = prefix[p + x] + if (linear) SRGB_TO_LINEAR[v] else v
                }
            }
            for (y in 0 until h) {
                for (x in 0 until w) {
                    var sum = 0
                    var count = 0
                    for (i in 0..2 * r) {
                        val yy = y + i - r
                        if (yy < 0 || yy >= h) continue
                        val hw = halfWidth[i]
                        val x0 = max(0, x - hw)
                        val x1 = min(w - 1, x + hw)
                        val p = yy * (w + 1)
                        sum += prefix[p + x1 + 1] - prefix[p + x0]
                        count += x1 - x0 + 1
                    }
                    val avg = sum / count
                    val v = if (linear) LINEAR_TO_SRGB[avg] else avg
                    val i = y * w + x
                    pixels[i] = (pixels[i] and keep) or (v shl shift)
                }
            }
        }
    }

    /**
     * Images/videos from MediaStore plus a direct scan of common folders, because files copied in via
     * adb, USB or the emulator are often not indexed by MediaStore yet.
     */
    suspend fun deviceMedia(video: Boolean): List<DeviceMedia> = withContext(Dispatchers.IO) {
        val found = LinkedHashMap<String, Pair<DeviceMedia, Long>>() // key: file path (or uri) -> media, modified
        queryMediaStore(video).forEach { (key, entry) -> found.putIfAbsent(key, entry) }
        scanFolders(video).forEach { file ->
            found.putIfAbsent(file.path, DeviceMedia(Uri.fromFile(file), file.name) to file.lastModified())
        }
        found.values.sortedByDescending { it.second }.take(MAX_LISTED).map { it.first }
    }

    private fun queryMediaStore(video: Boolean): List<Pair<String, Pair<DeviceMedia, Long>>> {
        val collection = if (video) MediaStore.Video.Media.EXTERNAL_CONTENT_URI else MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        @Suppress("DEPRECATION") // DATA is still readable and lets us de-duplicate against the folder scan.
        val projection = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.DATA,
            MediaStore.MediaColumns.DATE_MODIFIED,
        )
        return runCatching {
            context.contentResolver.query(collection, projection, null, null, null)?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
                @Suppress("DEPRECATION")
                val dataCol = cursor.getColumnIndex(MediaStore.MediaColumns.DATA)
                val modifiedCol = cursor.getColumnIndex(MediaStore.MediaColumns.DATE_MODIFIED)
                buildList {
                    while (cursor.moveToNext()) {
                        val uri = ContentUris.withAppendedId(collection, cursor.getLong(idCol))
                        val path = if (dataCol >= 0) cursor.getString(dataCol) else null
                        val modified = if (modifiedCol >= 0) cursor.getLong(modifiedCol) * 1000 else 0L
                        add((path ?: uri.toString()) to (DeviceMedia(uri, cursor.getString(nameCol) ?: "") to modified))
                    }
                }
            }
        }.getOrNull().orEmpty()
    }

    private fun scanFolders(video: Boolean): List<File> {
        val root = Environment.getExternalStorageDirectory()
        val extensions = if (video) VIDEO_EXTENSIONS else IMAGE_EXTENSIONS
        return SCAN_FOLDERS.flatMap { name ->
            runCatching {
                File(root, name).walkTopDown()
                    .maxDepth(4)
                    .filter { it.isFile && it.extension.lowercase() in extensions }
                    .take(MAX_LISTED)
                    .toList()
            }.getOrDefault(emptyList())
        }
    }

    // A few at a time: the gallery grid asks for a dozen at once, and video frames are full-size before scaling.
    private val thumbnailSlots = Semaphore(3)

    suspend fun thumbnail(uri: Uri, video: Boolean, size: Int = 256): ImageBitmap? = thumbnailSlots.withPermit {
        withContext(Dispatchers.IO) { loadThumbnail(uri, video, size) }
    }

    private fun loadThumbnail(uri: Uri, video: Boolean, size: Int): ImageBitmap? {
        val systemThumb = if (uri.scheme == "content" && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            runCatching { context.contentResolver.loadThumbnail(uri, Size(size, size), null) }.getOrNull()
        } else {
            null
        }
        return runCatching {
            val bitmap = systemThumb ?: if (video) videoFrame(uri, size) else sampledImage(uri, size)
            bitmap?.asImageBitmap()
        }.getOrNull()
    }

    private fun videoFrame(uri: Uri, size: Int): Bitmap? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                return retriever.getScaledFrameAtTime(-1, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, size, size)
            }
            retriever.frameAtTime?.let { frame ->
                val scale = size.toFloat() / max(frame.width, frame.height)
                Bitmap.createScaledBitmap(frame, max(1, (frame.width * scale).roundToInt()), max(1, (frame.height * scale).roundToInt()), true)
                    .also { if (it !== frame) frame.recycle() }
            }
        } finally {
            retriever.release()
        }
    }

    private fun sampledImage(uri: Uri, size: Int): Bitmap? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            runCatching {
                val source = ImageDecoder.createSource(context.contentResolver, uri)
                return ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                    val scale = size.toFloat() / max(info.size.width, info.size.height)
                    if (scale < 1f) {
                        decoder.setTargetSize(max(1, (info.size.width * scale).roundToInt()), max(1, (info.size.height * scale).roundToInt()))
                    }
                }
            }
        }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= size && bounds.outHeight / (sample * 2) >= size) sample *= 2
        return context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        }
    }

    private companion object {
        const val MAX_IMAGE_BYTES = 40L * 1024 * 1024
        const val MAX_VIDEO_BYTES = 300L * 1024 * 1024
        const val MAX_LISTED = 300
        const val SOFTWARE_MAX_W = 1920
        const val SOFTWARE_MAX_H = 1080
        // Largest disc radius computed directly; bigger blurs run on a proportionally smaller copy.
        const val MAX_WORK_RADIUS = 6f
        val SCAN_FOLDERS = listOf(
            Environment.DIRECTORY_PICTURES,
            Environment.DIRECTORY_DOWNLOADS,
            Environment.DIRECTORY_DCIM,
            Environment.DIRECTORY_MOVIES,
        )
        val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "jpe", "png", "webp", "gif", "bmp", "heic", "heif", "avif")
        val VIDEO_EXTENSIONS = setOf("mp4", "m4v", "webm", "mkv", "3gp", "mov")
    }
}
