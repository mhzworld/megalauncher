package com.megalauncher.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.tv.material3.Text
import com.megalauncher.LauncherViewModel
import com.megalauncher.data.StatGauge
import com.megalauncher.data.SystemGaugeKind
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

private const val REFRESH_MS = 2_000L
private const val ARC_START = 150f
private const val ARC_SWEEP = 240f

/** Polls only the visible gauges, and only while the home screen is visible and the launcher is in front. */
@Composable
fun SystemGadgetHost(vm: LauncherViewModel, kinds: Set<SystemGaugeKind>) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var stats by remember { mutableStateOf<List<StatGauge>?>(null) }
    LaunchedEffect(lifecycle, kinds) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                stats = vm.systemStats(kinds)
                delay(REFRESH_MS)
            }
        }
    }
    SystemGadget(stats)
}

@Composable
fun SystemGadget(stats: List<StatGauge>?) {
    Column(Modifier.fillMaxSize().padding(14.dp)) {
        Text("System monitor", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        if (stats == null) {
            Spacer(Modifier.weight(1f))
            Text("Reading…", fontSize = 15.sp, color = LauncherColors.TextSecondary)
            Spacer(Modifier.weight(1f))
        } else {
            Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.SpaceEvenly) {
                stats.forEach { Gauge(it) }
            }
        }
    }
}

/** Gadget width for [count] gauges. */
fun systemGadgetWidth(count: Int): Dp = (48 + 112 * count).coerceAtLeast(260).dp

@Composable
private fun RowScope.Gauge(gauge: StatGauge) {
    val fraction = gauge.fraction
    val animated by animateFloatAsState(fraction ?: 0f, tween(600), label = gauge.label)
    val track = Color.White.copy(alpha = 0.15f)
    val color = levelColor(fraction ?: 0f)
    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(84.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val width = 8.dp.toPx()
                val topLeft = Offset(width / 2, width / 2)
                val arcSize = Size(size.width - width, size.height - width)
                val stroke = Stroke(width, cap = StrokeCap.Round)
                drawArc(track, ARC_START, ARC_SWEEP, false, topLeft, arcSize, style = stroke)
                if (fraction != null && animated > 0f) {
                    drawArc(color, ARC_START, ARC_SWEEP * animated.coerceIn(0f, 1f), false, topLeft, arcSize, style = stroke)
                }
            }
            Text(
                when {
                    fraction == null -> "N/A"
                    gauge.center != null -> gauge.center
                    else -> "${(fraction * 100).roundToInt()}%"
                },
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        Text(gauge.label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        Text(
            gauge.detail,
            fontSize = 11.sp,
            color = LauncherColors.TextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun levelColor(fraction: Float): Color = when {
    fraction < 0.6f -> Color(0xFF4CAF50)
    fraction < 0.85f -> Color(0xFFFFB300)
    else -> Color(0xFFE53935)
}
