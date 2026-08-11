package com.portannika.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * A switchgear room at night: dark panels, painted metal, backlit instruments.
 * The palette is deliberately narrow so that colour always means something --
 * amber is a warning, red is a trip, green is on line.
 */
object Pal {
    val bg = Color(0xFF0B0E11)
    val panel = Color(0xFF151B21)
    val panelHigh = Color(0xFF1D262E)
    val panelEdge = Color(0xFF2A353F)
    val bezel = Color(0xFF0A0D10)

    val ink = Color(0xFFE3EAF0)
    val inkDim = Color(0xFF93A3B0)
    val inkFaint = Color(0xFF5F707D)

    val green = Color(0xFF4FBF87)
    val greenGlow = Color(0xFF7FEFB5)
    val amber = Color(0xFFF0AE4A)
    val red = Color(0xFFE05B45)
    val redGlow = Color(0xFFFF8A72)
    val blue = Color(0xFF5FB3D4)
    val violet = Color(0xFF8B8CE0)
    val brass = Color(0xFFC9A24A)

    val needle = Color(0xFFF2E4C4)
    val dialFace = Color(0xFF10161B)
    val dialTick = Color(0xFF6E7F8C)

    /** Colour for a value against a normal band and a limit. */
    fun status(value: Double, warn: Double, trip: Double): Color = when {
        value >= trip -> red
        value >= warn -> amber
        else -> green
    }
}

val Mono = FontFamily.Monospace

private val AppTypography = Typography(
    displaySmall = TextStyle(fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 30.sp),
    headlineSmall = TextStyle(fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 20.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 16.sp),
    titleSmall = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 13.sp, letterSpacing = 0.6.sp),
    bodyMedium = TextStyle(fontSize = 14.sp),
    bodySmall = TextStyle(fontSize = 12.sp),
    labelSmall = TextStyle(fontFamily = Mono, fontSize = 10.sp, letterSpacing = 1.0.sp),
)

private val Scheme = darkColorScheme(
    primary = Pal.blue,
    onPrimary = Pal.bg,
    secondary = Pal.brass,
    background = Pal.bg,
    onBackground = Pal.ink,
    surface = Pal.panel,
    onSurface = Pal.ink,
    surfaceVariant = Pal.panelHigh,
    onSurfaceVariant = Pal.inkDim,
    error = Pal.red,
    outline = Pal.panelEdge,
)

@Composable
fun PortAnnikaTheme(content: @Composable () -> Unit) {
    // The plant is always lit the same way, dark theme or not.
    @Suppress("UNUSED_EXPRESSION") isSystemInDarkTheme()
    MaterialTheme(colorScheme = Scheme, typography = AppTypography, content = content)
}
