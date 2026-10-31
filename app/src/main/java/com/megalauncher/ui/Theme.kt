package com.megalauncher.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme

object LauncherColors {
    val Background = Color.Black
    val Panel = Color(0xFF1B1E24)
    val Card = Color(0xFF262A31)
    val CardFocused = Color(0xFF3A3F48)
    val Accent = Color(0xFF8AB4F8)
    val PrayerActive = Color(0xFF1F7A55)
    val TextSecondary = Color(0xFFB0B5BD)
    val Moving = Color(0xFFFFC857)
}

/** Typographic points -> sp (1pt = 1/72", 1sp = 1/160" at default font scale). */
fun pt(value: Int): TextUnit = (value * 160f / 72f).sp

@Composable
fun LauncherTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = LauncherColors.Accent,
            background = LauncherColors.Background,
            surface = LauncherColors.Card,
            onSurface = Color.White,
        ),
    ) {
        CompositionLocalProvider(LocalContentColor provides Color.White, content = content)
    }
}

object LauncherIcons {
    val Moon: ImageVector by lazy {
        vector("M12.34,2.02C6.59,1.82 2,6.42 2,12c0,5.52 4.48,10 10,10c3.71,0 6.93,-2.02 8.66,-5.02C13.15,16.73 8.57,8.55 12.34,2.02z")
    }
    val Apps: ImageVector by lazy {
        vector(
            "M4,4h4v4H4zM10,4h4v4h-4zM16,4h4v4h-4zM4,10h4v4H4zM10,10h4v4h-4zM16,10h4v4h-4z" +
                "M4,16h4v4H4zM10,16h4v4h-4zM16,16h4v4h-4z",
        )
    }

    val Tune: ImageVector by lazy {
        vector(
            "M3,17v2h6v-2H3zM3,5v2h10V5H3zM13,21v-2h8v-2h-8v-2h-2v6h2zM7,9v2H3v2h4v2h2V9H7z" +
                "M21,13v-2H11v2h10zM15,9h2V7h4V5h-4V3h-2v6z",
        )
    }

    private fun vector(path: String) = ImageVector.Builder(
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).addPath(pathData = addPathNodes(path), fill = SolidColor(Color.White)).build()
}
