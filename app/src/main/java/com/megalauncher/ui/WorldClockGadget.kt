package com.megalauncher.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import com.megalauncher.LauncherViewModel
import com.megalauncher.data.WorldClock
import com.megalauncher.data.WorldClocks
import java.time.Instant
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

// Uses the launcher's per-minute tick, so the gadget recomposes only once a minute.
@Composable
fun WorldClockGadgetHost(vm: LauncherViewModel, clocks: List<WorldClock>, use24Hour: Boolean) {
    val now by vm.now.collectAsStateWithLifecycle()
    Row(
        Modifier.fillMaxSize().padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        clocks.take(2).forEach { WorldClockFace(it, now, use24Hour) }
    }
}

@Composable
private fun RowScope.WorldClockFace(clock: WorldClock, now: Long, use24Hour: Boolean) {
    val time = remember(clock.zoneId, now) { ZonedDateTime.ofInstant(Instant.ofEpochMilli(now), WorldClocks.zone(clock)) }
    val digital = remember(time, use24Hour) {
        time.format(DateTimeFormatter.ofPattern(if (use24Hour) "HH:mm" else "h:mm a", Locale.getDefault())).uppercase(Locale.getDefault())
    }
    val date = remember(time) { time.format(DateTimeFormatter.ofPattern("EEE, d MMM", Locale.getDefault())) }
    val isDay = time.hour in 6..17

    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(clock.city, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(4.dp))
        AnalogFace(time.hour % 12, time.minute, isDay, 84.dp)
        Spacer(Modifier.height(4.dp))
        Text(digital, fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        Text(date, fontSize = 10.sp, color = LauncherColors.TextSecondary, maxLines = 1)
    }
}

/** Light face during the day (6:00–17:59), dark at night. */
@Composable
private fun AnalogFace(hour: Int, minute: Int, isDay: Boolean, size: Dp) {
    val face = if (isDay) Color(0xFFE8ECF5) else Color(0xFF2A2F38)
    val ink = if (isDay) Color(0xFF1B1E24) else Color.White
    Canvas(Modifier.size(size)) {
        val r = this.size.minDimension / 2
        val c = center
        drawCircle(face, r)
        drawCircle(ink.copy(alpha = 0.25f), r - 1.dp.toPx(), style = Stroke(1.5.dp.toPx()))
        for (i in 0 until 12) {
            val major = i % 3 == 0
            val angle = i * 30.0
            drawLine(
                ink.copy(alpha = if (major) 0.9f else 0.45f),
                point(c, angle, r * if (major) 0.76f else 0.82f),
                point(c, angle, r * 0.9f),
                strokeWidth = (if (major) 2.5.dp else 1.5.dp).toPx(),
                cap = StrokeCap.Round,
            )
        }
        hand(c, (hour + minute / 60.0) * 30.0, r * 0.5f, 4.dp.toPx(), ink)
        hand(c, minute * 6.0, r * 0.74f, 2.5.dp.toPx(), ink)
        drawCircle(LauncherColors.Accent, 3.5.dp.toPx(), c)
    }
}

private fun DrawScope.hand(c: Offset, degrees: Double, length: Float, width: Float, color: Color) {
    drawLine(color, c, point(c, degrees, length), strokeWidth = width, cap = StrokeCap.Round)
}

// 0° points to 12 o'clock and increases clockwise.
private fun point(c: Offset, degrees: Double, radius: Float): Offset {
    val rad = (degrees - 90) * PI / 180
    return Offset(c.x + (cos(rad) * radius).toFloat(), c.y + (sin(rad) * radius).toFloat())
}
