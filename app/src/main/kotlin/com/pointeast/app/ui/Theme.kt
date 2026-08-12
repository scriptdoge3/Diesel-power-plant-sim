package com.pointeast.app.ui

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
 * A 1960s generating station control room, because that is what survived.
 *
 * Nothing here glows except the lamps. Panels are painted steel -- the
 * grey-green that every switchboard in the world was painted in 1962 --
 * with visible screw heads and engraved phenolic legend plates. Meters have
 * cream paper faces behind glass with black scales and black needles, which
 * is the opposite of a backlit digital instrument and reads far better in a
 * photograph of a real plant.
 *
 * Colour is scarce on purpose. Red, amber and green mean exactly one thing
 * each, and they are the only saturated things on the screen.
 */
object Pal {
    // Painted steel cabinet
    val bg = Color(0xFF23292A)          // shadow between panels
    val panel = Color(0xFF3A4442)       // machinery grey-green
    val panelHigh = Color(0xFF47524F)   // raised section
    val panelLow = Color(0xFF2C3433)    // recessed section
    val panelEdge = Color(0xFF1B2120)   // panel seam
    val screw = Color(0xFF8D9691)       // fastener head

    // Chrome and brass hardware
    val chrome = Color(0xFFB9C2BF)
    val chromeDark = Color(0xFF6E7876)
    val brass = Color(0xFFC2A05A)

    // Engraved legend plates: white letters cut into black laminate
    val legendPlate = Color(0xFF15191A)
    val legendText = Color(0xFFD8DEDA)

    // Meter faces: cream paper, black print
    val dialFace = Color(0xFFE8E2D0)
    val dialFaceEdge = Color(0xFFCFC7B2)
    val dialInk = Color(0xFF1A1A18)
    val dialInkFaint = Color(0xFF6A665C)
    val needle = Color(0xFF141414)
    val needleRed = Color(0xFF8E2C22)

    // Text on the cabinet itself
    val ink = Color(0xFFE2E7E3)
    val inkDim = Color(0xFFA8B2AD)
    val inkFaint = Color(0xFF737E7A)

    // Indicator lamps. Incandescent behind coloured glass, so they are warm
    // and they are dark when off, not merely dimmer.
    val green = Color(0xFF56C07A)
    val greenGlow = Color(0xFF8FE8AB)
    val amber = Color(0xFFE39A2B)
    val amberGlow = Color(0xFFFFCC55)
    val red = Color(0xFFCE3B2C)
    val redGlow = Color(0xFFFF6E58)
    val blue = Color(0xFF6FA6C4)
    val violet = Color(0xFF9A8FD0)
    val lampOff = Color(0xFF2A302F)

    /** Colour for a value against a normal band and a limit. */
    fun status(value: Double, warn: Double, trip: Double): Color = when {
        value >= trip -> red
        value >= warn -> amber
        else -> green
    }
}

/**
 * Everything numeric is monospaced. On a real panel the numbers are stencilled
 * or on a counter drum, and either way they are fixed pitch, which also stops
 * readouts jittering as digits change.
 */
val Mono = FontFamily.Monospace

private val AppTypography = Typography(
    displaySmall = TextStyle(fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 30.sp),
    headlineSmall = TextStyle(fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 20.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 16.sp),
    titleSmall = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 13.sp, letterSpacing = 0.8.sp),
    bodyMedium = TextStyle(fontSize = 14.sp),
    bodySmall = TextStyle(fontSize = 12.sp),
    labelSmall = TextStyle(fontFamily = Mono, fontSize = 10.sp, letterSpacing = 1.2.sp),
)

private val Scheme = darkColorScheme(
    primary = Pal.brass,
    onPrimary = Pal.legendPlate,
    secondary = Pal.chrome,
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
fun PointEastTheme(content: @Composable () -> Unit) {
    // The control room is lit the same way whatever the phone thinks.
    MaterialTheme(colorScheme = Scheme, typography = AppTypography, content = content)
}
