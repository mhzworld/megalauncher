package com.megalauncher.screensaver

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.Typeface
import android.os.SystemClock
import android.view.View
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * "MHz Night Sky": twinkling stars drifting in parallax layers, a slowly travelling moon and the
 * occasional shooting star on black. Everything keeps moving, so nothing burns into OLED panels.
 * With [showClock], a digital clock with seconds moves to a random spot every 60 seconds.
 */
class NightSkyView(context: Context, private val showClock: Boolean, private val use24Hour: Boolean) : View(context) {

    private class Star(
        var x: Float,
        var y: Float,
        val radius: Float,
        val depth: Float,
        val alpha: Float,
        val twinkleSpeed: Float,
        val phase: Float,
        val color: Int,
    )

    private class Meteor(val vx: Float, val vy: Float, val length: Float, val duration: Float, var x: Float, var y: Float) {
        var age = 0f
    }

    private val density = resources.displayMetrics.density
    private val random = Random(SystemClock.uptimeMillis())
    private val stars = ArrayList<Star>()
    private var meteor: Meteor? = null
    private var nextMeteorIn = 3f

    private val starPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val moonPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFF4EED8.toInt() }
    private val earthshinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF14161E.toInt() }
    private val craterPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFDDD4B8.toInt() }
    private val meteorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.ROUND }
    private val clockPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFE8ECF5.toInt()
        typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
        textAlign = Paint.Align.CENTER
    }
    private val datePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF9AA4B8.toInt()
        textAlign = Paint.Align.CENTER
    }

    private var moonX = 0f
    private var moonY = 0f
    private var moonBaseY = 0f
    private var moonRadius = 0f
    private val litPath = Path()
    private val discPath = Path()

    private var clockX = 0f
    private var clockY = 0f
    private var clockMovedAt = 0f
    private var clockSecond = -1L
    private var clockText = ""
    private var dateText = ""
    private val timeFormat = SimpleDateFormat(if (use24Hour) "HH:mm:ss" else "h:mm:ss a", Locale.getDefault())
    private val dateFormat = SimpleDateFormat("EEE, d MMM", Locale.getDefault())

    private var time = 0f
    private var lastFrame = 0L

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        lastFrame = 0L
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        stars.clear()
        val count = (w * h / (density * density * 2400f)).toInt().coerceIn(120, 420)
        repeat(count) {
            val depth = when (random.nextInt(10)) {
                in 0..5 -> 0.35f
                in 6..8 -> 0.65f
                else -> 1f
            }
            stars += Star(
                x = random.nextFloat() * w,
                y = random.nextFloat() * h,
                radius = (0.45f + depth * 0.9f + random.nextFloat() * 0.35f) * density,
                depth = depth,
                alpha = 0.35f + 0.65f * depth * (0.6f + 0.4f * random.nextFloat()),
                twinkleSpeed = 0.5f + random.nextFloat() * 2.2f,
                phase = random.nextFloat() * TWO_PI,
                color = STAR_COLORS[random.nextInt(STAR_COLORS.size)],
            )
        }
        moonRadius = h * 0.065f
        glowPaint.shader = RadialGradient(
            0f, 0f, moonRadius * GLOW_SCALE,
            intArrayOf(0x38FFF2CC, 0x12FFF2CC, 0x00FFF2CC),
            floatArrayOf(0f, 0.4f, 1f),
            Shader.TileMode.CLAMP,
        )
        resetMoon(initial = true)
        clockPaint.textSize = h * 0.13f
        datePaint.textSize = h * 0.04f
        moveClock()
    }

    private fun resetMoon(initial: Boolean) {
        moonX = if (initial) width * (0.35f + 0.5f * random.nextFloat()) else width + moonRadius * GLOW_SCALE
        moonBaseY = height * (0.14f + 0.3f * random.nextFloat())
        // A new phase each pass: full moon, crescent or gibbous.
        val r = moonRadius
        discPath.reset()
        discPath.addCircle(0f, 0f, r, Path.Direction.CW)
        litPath.set(discPath)
        if (random.nextInt(4) != 0) {
            val shadow = Path().apply {
                addCircle((0.45f + random.nextFloat() * 0.9f) * r, -0.12f * r, r * 1.05f, Path.Direction.CW)
            }
            litPath.op(shadow, Path.Op.DIFFERENCE)
        }
    }

    private fun moveClock() {
        val textW = clockPaint.measureText(if (use24Hour) "00:00:00" else "00:00:00 PM")
        val marginX = width * 0.05f + textW / 2
        val top = height * 0.06f + clockPaint.textSize
        val bottom = height * 0.92f - datePaint.textSize * 1.6f
        clockX = marginX + random.nextFloat() * max(1f, width - 2 * marginX)
        clockY = top + random.nextFloat() * max(1f, bottom - top)
        clockMovedAt = time
    }

    private fun update(dt: Float) {
        val w = width.toFloat()
        val h = height.toFloat()

        // Mostly leftwards, with the direction slowly swaying over time.
        val angle = PI.toFloat() + 0.12f + 0.25f * sin(time / 70f)
        val dirX = cos(angle)
        val dirY = sin(angle)
        val margin = 10 * density
        for (s in stars) {
            val speed = (3f + 10f * s.depth) * density
            s.x += dirX * speed * dt
            s.y += dirY * speed * dt
            if (s.x < -margin) s.x += w + 2 * margin else if (s.x > w + margin) s.x -= w + 2 * margin
            if (s.y < -margin) s.y += h + 2 * margin else if (s.y > h + margin) s.y -= h + 2 * margin
        }

        moonX -= 7f * density * dt
        moonY = moonBaseY + sin(time * 0.04f) * h * 0.04f
        if (moonX < -moonRadius * GLOW_SCALE) resetMoon(initial = false)

        nextMeteorIn -= dt
        if (meteor == null && nextMeteorIn <= 0f) {
            val speed = (650f + random.nextFloat() * 450f) * density
            val a = (PI * (0.68 + random.nextDouble() * 0.14)).toFloat()
            meteor = Meteor(
                vx = cos(a) * speed,
                vy = sin(a) * speed,
                length = (90f + random.nextFloat() * 90f) * density,
                duration = 0.7f + random.nextFloat() * 0.6f,
                x = w * (0.35f + 0.7f * random.nextFloat()),
                y = h * (0.02f + 0.35f * random.nextFloat()),
            )
            nextMeteorIn = 5f + random.nextFloat() * 10f
        }
        meteor?.let {
            it.age += dt
            it.x += it.vx * dt
            it.y += it.vy * dt
            if (it.age >= it.duration) meteor = null
        }

        if (showClock && time - clockMovedAt >= CLOCK_MOVE_SECONDS) moveClock()
    }

    override fun onDraw(canvas: Canvas) {
        val now = SystemClock.uptimeMillis()
        val dt = if (lastFrame == 0L) 0f else min(0.1f, (now - lastFrame) / 1000f)
        lastFrame = now
        time += dt
        if (width > 0 && height > 0) update(dt)

        canvas.drawColor(Color.BLACK)
        val fade = min(1f, time / 1.5f)
        drawStars(canvas, fade)
        drawMoon(canvas, fade)
        drawMeteor(canvas)
        if (showClock) drawClock(canvas, fade)

        // ~30 fps is plenty for this slow motion and halves the work on weak TV chips.
        postInvalidateDelayed(FRAME_MS)
    }

    private fun drawStars(canvas: Canvas, fade: Float) {
        for (s in stars) {
            val twinkle = 0.6f + 0.4f * sin(time * s.twinkleSpeed + s.phase)
            val a = (s.alpha * twinkle * fade * 255).toInt().coerceIn(0, 255)
            starPaint.color = s.color
            starPaint.alpha = a
            canvas.drawCircle(s.x, s.y, s.radius, starPaint)
            if (s.depth == 1f) {
                starPaint.alpha = a / 6
                canvas.drawCircle(s.x, s.y, s.radius * 3f, starPaint)
            }
        }
    }

    private fun drawMoon(canvas: Canvas, fade: Float) {
        val a = (255 * fade).toInt()
        canvas.save()
        canvas.translate(moonX, moonY)
        glowPaint.alpha = a
        canvas.drawCircle(0f, 0f, moonRadius * GLOW_SCALE, glowPaint)
        // The unlit part still hides the stars behind it, with a faint earthshine.
        earthshinePaint.alpha = a
        canvas.drawPath(discPath, earthshinePaint)
        moonPaint.alpha = a
        canvas.drawPath(litPath, moonPaint)
        canvas.clipPath(litPath)
        craterPaint.alpha = a
        for (i in CRATERS.indices step 3) {
            canvas.drawCircle(CRATERS[i] * moonRadius, CRATERS[i + 1] * moonRadius, CRATERS[i + 2] * moonRadius, craterPaint)
        }
        canvas.restore()
    }

    private fun drawMeteor(canvas: Canvas) {
        val m = meteor ?: return
        val strength = sin(m.age / m.duration * PI).toFloat().coerceAtLeast(0f)
        val speed = hypot(m.vx, m.vy)
        val tailX = m.x - m.vx / speed * m.length
        val tailY = m.y - m.vy / speed * m.length
        meteorPaint.shader = LinearGradient(
            m.x, m.y, tailX, tailY,
            Color.argb((230 * strength).toInt(), 255, 255, 255), Color.TRANSPARENT,
            Shader.TileMode.CLAMP,
        )
        meteorPaint.strokeWidth = 2f * density
        canvas.drawLine(m.x, m.y, tailX, tailY, meteorPaint)
    }

    private fun drawClock(canvas: Canvas, fade: Float) {
        val nowMs = System.currentTimeMillis()
        val second = nowMs / 1000
        if (second != clockSecond) {
            clockSecond = second
            clockText = timeFormat.format(Date(nowMs)).uppercase(Locale.getDefault())
            dateText = dateFormat.format(Date(nowMs))
        }
        // Fade out just before each jump and back in after it.
        val sinceMove = time - clockMovedAt
        val untilMove = CLOCK_MOVE_SECONDS - sinceMove
        val visible = (min(sinceMove, untilMove) / CLOCK_FADE_SECONDS).coerceIn(0f, 1f) * fade
        clockPaint.alpha = (235 * visible).toInt()
        datePaint.alpha = (200 * visible).toInt()
        canvas.drawText(clockText, clockX, clockY, clockPaint)
        canvas.drawText(dateText, clockX, clockY + datePaint.textSize * 1.6f, datePaint)
    }

    private companion object {
        const val TWO_PI = 6.2831855f
        const val FRAME_MS = 33L
        const val GLOW_SCALE = 3.5f
        const val CLOCK_MOVE_SECONDS = 60f
        const val CLOCK_FADE_SECONDS = 0.4f
        val STAR_COLORS = intArrayOf(
            0xFFFFFFFF.toInt(), 0xFFFFFFFF.toInt(), 0xFFFFFFFF.toInt(),
            0xFFD6E4FF.toInt(), 0xFFFFF1D6.toInt(), 0xFFE6ECFF.toInt(),
        )

        // Crater spots as (dx, dy, radius) in moon radii.
        val CRATERS = floatArrayOf(
            -0.35f, -0.2f, 0.18f,
            0.25f, 0.1f, 0.14f,
            -0.05f, 0.45f, 0.12f,
            0.3f, -0.4f, 0.09f,
            -0.4f, 0.3f, 0.08f,
        )
    }
}
