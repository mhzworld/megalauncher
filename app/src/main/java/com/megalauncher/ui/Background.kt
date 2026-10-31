package com.megalauncher.ui

import android.graphics.Bitmap
import android.graphics.RenderEffect
import android.graphics.Shader
import android.graphics.drawable.Animatable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.view.View
import android.widget.ImageView
import android.widget.Toast
import androidx.compose.animation.core.Animatable as AnimatableValue
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.tv.material3.Text
import com.megalauncher.data.BackgroundConfig
import com.megalauncher.data.BackgroundRepository
import com.megalauncher.data.BackgroundType
import com.megalauncher.data.DeviceMedia
import com.megalauncher.data.GradientStyle
import com.megalauncher.data.ImageScale
import com.megalauncher.data.blurRadius
import kotlinx.coroutines.delay
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

val BackgroundPalette: List<Long> = listOf(
    0xFF000000, 0xFF1C1C1E, 0xFF3A3A3C, 0xFF5E5E63, 0xFFB0B5BD, 0xFFF2F2F7,
    0xFF0D1B2A, 0xFF1E3A5F, 0xFF1565C0, 0xFF0288D1, 0xFF00838F, 0xFF00695C,
    0xFF2E7D32, 0xFF558B2F, 0xFF827717, 0xFFF9A825, 0xFFEF6C00, 0xFFD84315,
    0xFFB71C1C, 0xFF880E4F, 0xFFAD1457, 0xFF6A1B9A, 0xFF4527A0, 0xFF4E342E,
)

fun colorHex(color: Long): String = String.format("#%06X", color and 0xFFFFFF)

val BackgroundType.label: String
    get() = when (this) {
        BackgroundType.DEFAULT -> "Default (black)"
        BackgroundType.SOLID -> "Solid colour"
        BackgroundType.GRADIENT -> "Gradient"
        BackgroundType.IMAGE -> "Image / GIF / WebP"
        BackgroundType.VIDEO -> "Video (MP4 / WebM)"
    }

val GradientStyle.label: String
    get() = when (this) {
        GradientStyle.VERTICAL -> "Vertical"
        GradientStyle.HORIZONTAL -> "Horizontal"
        GradientStyle.CENTER -> "Center"
        GradientStyle.SQUARE -> "Square"
        GradientStyle.CIRCLE -> "Circle"
    }

val ImageScale.label: String
    get() = when (this) {
        ImageScale.STRETCH -> "Stretch to fill screen"
        ImageScale.ZOOM -> "Zoom to fill (keep aspect ratio)"
    }

@Composable
fun LauncherBackground(background: BackgroundConfig, repo: BackgroundRepository) {
    val shift = if (background.oledShift) rememberBurnInShift() else null
    // Own layer: scrolling the UI on top doesn't redraw the background, and the OLED drift is a cheap transform.
    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer {
                if (shift != null) {
                    scaleX = OLED_SCALE
                    scaleY = OLED_SCALE
                    translationX = shift.value.x * size.width
                    translationY = shift.value.y * size.height
                }
            },
    ) {
        when (background.type) {
            BackgroundType.DEFAULT -> Box(Modifier.fillMaxSize().background(Color.Black))
            BackgroundType.SOLID -> SolidBackground(background)
            BackgroundType.GRADIENT -> {
                val bitmap = remember(background.gradientStyle, background.gradientStart, background.gradientEnd) {
                    gradientBitmap(background.gradientStyle, Color(background.gradientStart), Color(background.gradientEnd))
                }
                Image(
                    bitmap,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.FillBounds,
                    filterQuality = FilterQuality.High,
                )
            }
            BackgroundType.IMAGE -> if (repo.hasImage) ImageBackground(background, repo) else SolidBackground(background)
            BackgroundType.VIDEO -> if (repo.hasVideo) VideoBackground(background, repo) else SolidBackground(background)
        }
    }
}

@Composable
private fun SolidBackground(background: BackgroundConfig) {
    Box(Modifier.fillMaxSize().background(Color(background.color)))
}

private const val OLED_SCALE = 1.08f
private const val OLED_MAX_SHIFT = 0.03f

/** Every 20 s, glides to the next point on a slow Lissajous path (fraction of screen size). */
@Composable
private fun rememberBurnInShift(): State<Offset> {
    val offset = remember { AnimatableValue(Offset.Zero, Offset.VectorConverter) }
    LaunchedEffect(Unit) {
        var step = 0
        while (true) {
            delay(20_000)
            step++
            val target = Offset(sin(step * 0.7f) * OLED_MAX_SHIFT, cos(step * 0.9f) * OLED_MAX_SHIFT)
            offset.animateTo(target, tween(durationMillis = 4_000, easing = LinearEasing))
        }
    }
    return offset.asState()
}

/** Small bitmap upscaled with filtering; supports shapes Compose brushes can't (e.g. square). */
private fun gradientBitmap(style: GradientStyle, start: Color, end: Color, w: Int = 192, h: Int = 108): ImageBitmap {
    val aspect = w.toFloat() / h
    val pixels = IntArray(w * h)
    for (y in 0 until h) {
        for (x in 0 until w) {
            val nx = (x + 0.5f) / w * 2 - 1
            val ny = (y + 0.5f) / h * 2 - 1
            val t = when (style) {
                GradientStyle.VERTICAL -> (ny + 1) / 2
                GradientStyle.HORIZONTAL -> (nx + 1) / 2
                GradientStyle.CENTER -> sqrt(nx * nx + ny * ny) / sqrt(2f)
                GradientStyle.SQUARE -> max(abs(nx * aspect), abs(ny)) / aspect
                GradientStyle.CIRCLE -> sqrt(nx * nx * aspect * aspect + ny * ny) / sqrt(aspect * aspect + 1)
            }
            pixels[y * w + x] = lerp(start, end, t.coerceIn(0f, 1f)).toArgb()
        }
    }
    return Bitmap.createBitmap(pixels, w, h, Bitmap.Config.ARGB_8888).asImageBitmap()
}

@Composable
private fun ImageBackground(background: BackgroundConfig, repo: BackgroundRepository) {
    val metrics = LocalContext.current.resources.displayMetrics
    val screenW = max(metrics.widthPixels, metrics.heightPixels)
    val screenH = min(metrics.widthPixels, metrics.heightPixels)

    // Static images are blurred once while decoding; a live RenderEffect blur is re-run on every frame
    // and makes scrolling stutter, so it's only used for animated images.
    val drawable by produceState<Drawable?>(null, repo.imagePath, background.imageVersion, background.blur) {
        value = repo.loadDrawable(screenW, screenH, background.blur)
    }
    val current = drawable
    val lifecycleOwner = LocalLifecycleOwner.current
    // Pause animated images while another app is in front.
    DisposableEffect(current, lifecycleOwner) {
        val animatable = current as? Animatable
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> animatable?.start()
                Lifecycle.Event.ON_STOP -> animatable?.stop()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            animatable?.stop()
        }
    }

    Box(Modifier.fillMaxSize().background(Color(background.color))) {
        if (current != null) {
            AndroidView(
                factory = { ImageView(it) },
                update = { view ->
                    if (view.drawable !== current) {
                        view.setImageDrawable(current)
                        (current as? Animatable)?.start()
                    }
                    view.scaleType = if (background.imageScale == ImageScale.STRETCH) {
                        ImageView.ScaleType.FIT_XY
                    } else {
                        ImageView.ScaleType.CENTER_CROP
                    }
                    view.alpha = background.opacity / 100f
                    applyBlur(view, if (current is Animatable) background.blur else 0)
                },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun VideoBackground(background: BackgroundConfig, repo: BackgroundRepository) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val viewRef = remember { AtomicReference<LoopingVideoView?>(null) }
    // Pause the video while another app is in front.
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> viewRef.get()?.setPlaying(true)
                Lifecycle.Event.ON_STOP -> viewRef.get()?.setPlaying(false)
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Box(Modifier.fillMaxSize().background(Color(background.color))) {
        AndroidView(
            factory = { context ->
                LoopingVideoView(context).also { view ->
                    viewRef.set(view)
                    view.onError = { message -> Toast.makeText(context, message, Toast.LENGTH_LONG).show() }
                }
            },
            update = { view ->
                view.setSource(repo.videoPath, "${repo.videoPath}:${background.videoVersion}")
                view.setZoom(background.imageScale == ImageScale.ZOOM)
                view.alpha = background.opacity / 100f
                applyBlur(view, background.blur)
            },
            onRelease = { view ->
                view.release()
                viewRef.set(null)
            },
            modifier = Modifier.fillMaxSize(),
        )
    }
}

private fun applyBlur(view: View, level: Int) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        // Live GIF/video blur is Gaussian; a disc of radius R looks like sigma ≈ R/2, and Android uses sigma ≈ 0.577 × radius.
        val radius = blurRadius(level) * 0.87f
        view.setRenderEffect(
            if (radius > 0f) RenderEffect.createBlurEffect(radius, radius, Shader.TileMode.CLAMP) else null,
        )
    }
}

@Composable
fun ColorPickerDialog(title: String, current: Long, onPick: (Long) -> Unit, onDismiss: () -> Unit) {
    val first = remember { FocusRequester() }
    val focusIndex = BackgroundPalette.indexOf(current).coerceAtLeast(0)
    Dialog(onDismissRequest = onDismiss) {
        Column(Modifier.ignoreHeldSelectKey().width(520.dp).background(LauncherColors.Panel, RoundedCornerShape(20.dp)).padding(24.dp)) {
            Text(title, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(12.dp))
            BackgroundPalette.chunked(6).forEachIndexed { rowIndex, colors ->
                Row(Modifier.padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    colors.forEachIndexed { colIndex, value ->
                        val color = Color(value)
                        FocusSurface(
                            onClick = {
                                onPick(value)
                                onDismiss()
                            },
                            modifier = Modifier
                                .size(56.dp)
                                .then(if (rowIndex * 6 + colIndex == focusIndex) Modifier.focusRequester(first) else Modifier),
                            shape = CircleShape,
                            focusedScale = 1.15f,
                            containerColor = color,
                            focusedContainerColor = color,
                        ) {
                            if (value == current) {
                                TvIcon(
                                    Icons.Default.Check,
                                    "Selected",
                                    Modifier.size(24.dp).align(Alignment.Center),
                                    tint = if (color.luminance() > 0.5f) Color.Black else Color.White,
                                )
                            }
                        }
                    }
                }
            }
        }
        LaunchedEffect(Unit) {
            delay(50)
            first.safeRequestFocus()
        }
    }
}

@Composable
fun DeviceMediaPickerDialog(repo: BackgroundRepository, video: Boolean, onPick: (Uri) -> Unit, onDismiss: () -> Unit) {
    val items by produceState<List<DeviceMedia>?>(null, video) { value = repo.deviceMedia(video) }
    val first = remember { FocusRequester() }
    FullDialog(onDismiss) {
        Column(Modifier.fillMaxSize().padding(24.dp)) {
            Text(
                if (video) "Choose background video" else "Choose background image",
                fontSize = 22.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                if (video) "MP4 and WebM videos play muted and loop" else "GIF and animated WebP files play as live backgrounds",
                fontSize = 13.sp,
                color = LauncherColors.TextSecondary,
            )
            Spacer(Modifier.height(12.dp))
            val list = items
            when {
                list == null -> Text("Loading…", fontSize = 16.sp, color = LauncherColors.TextSecondary)
                list.isEmpty() -> Text(
                    "Nothing found on this TV. Copy files to the Movies, Pictures or Download folder, or use a web link.",
                    fontSize = 16.sp,
                    color = LauncherColors.TextSecondary,
                )
                else -> LazyVerticalGrid(
                    columns = GridCells.Adaptive(204.dp),
                    contentPadding = PaddingValues(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    itemsIndexed(list, key = { _, media -> media.uri.toString() }) { index, media ->
                        MediaThumbnail(
                            repo = repo,
                            media = media,
                            video = video,
                            modifier = if (index == 0) Modifier.focusRequester(first) else Modifier,
                        ) {
                            onPick(media.uri)
                            onDismiss()
                        }
                    }
                }
            }
        }
        LaunchedEffect(items) {
            if (!items.isNullOrEmpty()) {
                delay(80)
                first.safeRequestFocus()
            }
        }
    }
}

@Composable
private fun MediaThumbnail(
    repo: BackgroundRepository,
    media: DeviceMedia,
    video: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val thumb by produceState<ImageBitmap?>(null, media.uri) { value = repo.thumbnail(media.uri, video) }
    FocusSurface(onClick = onClick, modifier = modifier.size(192.dp, 108.dp), shape = RoundedCornerShape(10.dp)) {
        val bitmap = thumb
        if (bitmap != null) {
            Image(bitmap, media.name, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            Text(media.name, fontSize = 12.sp, maxLines = 2, modifier = Modifier.align(Alignment.Center).padding(8.dp))
        }
    }
}
