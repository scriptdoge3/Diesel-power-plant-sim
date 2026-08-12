package com.pointeast.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/* ============================================================================
 *  Panel instruments, drawn rather than composed, because a switchboard meter
 *  is a picture of a physical thing and should read like one: needle position
 *  first, number second, colour only where it means something.
 *
 *  These are 1960s moving-iron meters -- cream paper faces, black scales,
 *  black needles, chrome bezels with visible screws -- not backlit displays.
 * ========================================================================== */

private const val START_DEG = 150.0     // sweep from lower-left...
private const val SWEEP_DEG = 240.0     // ...clockwise to lower-right

private fun angleFor(frac: Double) = START_DEG + SWEEP_DEG * frac.coerceIn(0.0, 1.0)

private fun DrawScope.polar(c: Offset, radius: Float, degrees: Double): Offset {
    val r = degrees * PI / 180.0
    return Offset(c.x + radius * cos(r).toFloat(), c.y + radius * sin(r).toFloat())
}

/** Four fastener heads, as on any panel-mounted instrument. */
private fun DrawScope.bezelScrews(c: Offset, r: Float) {
    for (a in listOf(45.0, 135.0, 225.0, 315.0)) {
        val p = polar(c, r * 0.93f, a)
        drawCircle(Pal.chromeDark, radius = r * 0.045f, center = p)
        drawCircle(Pal.screw, radius = r * 0.030f, center = p)
    }
}

/**
 * A round panel meter.
 *
 * @param bands coloured arcs printed on the scale. A real instrument tells you
 *   where the limits are without you having to read the number.
 */
@Composable
fun AnalogGauge(
    label: String,
    value: Double,
    min: Double,
    max: Double,
    unit: String,
    modifier: Modifier = Modifier,
    decimals: Int = 0,
    bands: List<Triple<Double, Double, Color>> = emptyList(),
    majorTicks: Int = 6,
    accent: Color = Pal.brass,
    subtitle: String? = null,
    compact: Boolean = false,
) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.fillMaxWidth().aspectRatio(1f), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val c = Offset(size.width / 2f, size.height / 2f)
                val outer = min(size.width, size.height) / 2f

                // Chrome bezel ring, lit from the top left like everything else
                // in the room.
                drawCircle(
                    brush = Brush.linearGradient(
                        listOf(Pal.chrome, Pal.chromeDark),
                        start = Offset(0f, 0f), end = Offset(size.width, size.height),
                    ),
                    radius = outer, center = c,
                )
                val r = outer * 0.88f
                drawCircle(Pal.panelEdge, radius = outer * 0.91f, center = c)

                // Cream paper face behind glass, slightly darker at the rim.
                drawCircle(
                    brush = Brush.radialGradient(
                        listOf(Pal.dialFace, Pal.dialFaceEdge),
                        center = Offset(c.x - r * 0.2f, c.y - r * 0.25f), radius = r * 1.5f,
                    ),
                    radius = r, center = c,
                )

                // Printed limit bands, just inside the scale.
                val bandR = r * 0.80f
                for ((from, to, colour) in bands) {
                    val f0 = (from - min) / (max - min)
                    val f1 = (to - min) / (max - min)
                    if (!f0.isFinite() || !f1.isFinite()) continue
                    if (f1 <= 0.0 || f0 >= 1.0 || f1 <= f0) continue
                    val a0 = angleFor(f0)
                    val a1 = angleFor(f1)
                    drawArc(
                        color = colour,
                        startAngle = a0.toFloat(),
                        sweepAngle = (a1 - a0).toFloat(),
                        useCenter = false,
                        topLeft = Offset(c.x - bandR, c.y - bandR),
                        size = Size(bandR * 2, bandR * 2),
                        style = Stroke(width = r * 0.075f),
                    )
                }

                // Scale: heavy majors, light minors, printed in black.
                val steps = majorTicks.coerceAtLeast(2)
                for (i in 0..steps) {
                    val a = angleFor(i.toDouble() / steps)
                    drawLine(Pal.dialInk, polar(c, r * 0.62f, a), polar(c, r * 0.88f, a),
                        strokeWidth = r * 0.040f)
                    if (i < steps) for (j in 1..4) {
                        val am = angleFor((i + j / 5.0) / steps)
                        drawLine(Pal.dialInkFaint, polar(c, r * 0.76f, am), polar(c, r * 0.88f, am),
                            strokeWidth = r * 0.014f)
                    }
                }

                // Needle: a black tapered pointer with a counterweighted tail.
                val raw = if (max > min) (value - min) / (max - min) else 0.0
                val frac = if (raw.isFinite()) raw else 0.0
                val a = angleFor(frac)
                val tip = polar(c, r * 0.84f, a)
                val tail = polar(c, -r * 0.20f, a)
                val s1 = polar(c, r * 0.050f, a + 90)
                val s2 = polar(c, r * 0.050f, a - 90)
                // Shadow on the paper, the way a real needle sits above the face.
                drawPath(
                    Path().apply {
                        moveTo(tip.x + 2f, tip.y + 2f); lineTo(s1.x + 2f, s1.y + 2f)
                        lineTo(tail.x + 2f, tail.y + 2f); lineTo(s2.x + 2f, s2.y + 2f); close()
                    },
                    color = Color(0x22000000),
                )
                drawPath(
                    Path().apply {
                        moveTo(tip.x, tip.y); lineTo(s1.x, s1.y)
                        lineTo(tail.x, tail.y); lineTo(s2.x, s2.y); close()
                    },
                    color = Pal.needle,
                )
                // Brass hub cap.
                drawCircle(Pal.chromeDark, radius = r * 0.105f, center = c)
                drawCircle(accent, radius = r * 0.075f, center = c)

                // Pinned against a stop: unmistakable, as on a real meter.
                if (frac > 1.0 || frac < 0.0) {
                    drawCircle(Pal.needleRed, radius = r * 0.06f,
                        center = polar(c, r * 0.50f, angleFor(if (frac > 1) 1.0 else 0.0)))
                }

                // Mirror band. A precision switchboard meter has a strip of
                // mirror under the scale: you line the needle up with its own
                // reflection so you are reading it square on. It is the detail
                // that says "instrument" more than any other.
                val mirrorR = r * 0.665f
                drawArc(
                    color = Color(0x33FFFFFF),
                    startAngle = START_DEG.toFloat(), sweepAngle = SWEEP_DEG.toFloat(),
                    useCenter = false,
                    topLeft = Offset(c.x - mirrorR, c.y - mirrorR),
                    size = Size(mirrorR * 2, mirrorR * 2),
                    style = Stroke(width = r * 0.055f),
                )
                drawArc(
                    color = Pal.dialInkFaint.copy(alpha = 0.55f),
                    startAngle = START_DEG.toFloat(), sweepAngle = SWEEP_DEG.toFloat(),
                    useCenter = false,
                    topLeft = Offset(c.x - mirrorR, c.y - mirrorR),
                    size = Size(mirrorR * 2, mirrorR * 2),
                    style = Stroke(width = r * 0.008f),
                )

                // Glass: a single soft highlight across the top left.
                drawArc(
                    brush = Brush.linearGradient(
                        listOf(Color(0x24FFFFFF), Color(0x00FFFFFF)),
                        start = Offset(c.x - r, c.y - r), end = Offset(c.x, c.y),
                    ),
                    startAngle = 170f, sweepAngle = 110f, useCenter = true,
                    topLeft = Offset(c.x - r, c.y - r), size = Size(r * 2, r * 2),
                )
                bezelScrews(c, outer)
            }

            // The reading, printed at the bottom of the face where the needle
            // cannot cross it.
            Column(
                Modifier.align(Alignment.BottomCenter)
                    .padding(bottom = if (compact) 9.dp else 15.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    fmt(value, decimals),
                    fontFamily = Mono, fontWeight = FontWeight.Bold,
                    fontSize = if (compact) 12.sp else 17.sp,
                    color = Pal.dialInk, maxLines = 1, softWrap = false,
                )
                Text(unit, fontFamily = Mono, fontSize = if (compact) 7.sp else 9.sp,
                    color = Pal.dialInkFaint, maxLines = 1)
            }
        }

        LegendPlate(label, Modifier.padding(top = 4.dp))
        if (subtitle != null) {
            Text(subtitle, fontFamily = Mono, fontSize = 8.sp, color = Pal.inkFaint,
                textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 1.dp))
        }
    }
}

/** An engraved phenolic legend plate: white letters cut into black laminate. */
@Composable
fun LegendPlate(text: String, modifier: Modifier = Modifier, wide: Boolean = false) {
    Box(
        modifier
            .background(Pal.legendPlate, RoundedCornerShape(2.dp))
            .border(1.dp, Pal.chromeDark.copy(alpha = 0.5f), RoundedCornerShape(2.dp))
            .padding(horizontal = if (wide) 10.dp else 5.dp, vertical = 2.dp),
    ) {
        Text(
            text.uppercase(),
            fontFamily = Mono, fontSize = 8.sp, letterSpacing = 1.1.sp,
            fontWeight = FontWeight.Bold, color = Pal.legendText,
            textAlign = TextAlign.Center, maxLines = 1,
        )
    }
}

/**
 * The synchroscope. The pointer turns at the difference between the incoming
 * machine and the running bus. Twelve o'clock is in phase; clockwise means
 * the machine is running fast, which is the direction you want, because a
 * machine coming in slow gets motored by the bus the instant you close.
 */
@Composable
fun Synchroscope(
    angleDeg: Double,
    slipHz: Double,
    slipRpm: Double,
    windowDeg: Double,
    inWindow: Boolean,
    live: Boolean,
    modifier: Modifier = Modifier,
) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val c = Offset(size.width / 2f, size.height / 2f)
            val outer = min(size.width, size.height) / 2f
            drawCircle(
                brush = Brush.linearGradient(
                    listOf(Pal.chrome, Pal.chromeDark),
                    start = Offset(0f, 0f), end = Offset(size.width, size.height),
                ),
                radius = outer, center = c,
            )
            val r = outer * 0.88f
            drawCircle(Pal.dialFace, radius = r, center = c)

            // SLOW on the left, FAST on the right, as printed on the face.
            drawArc(
                color = Pal.dialInkFaint.copy(alpha = 0.16f),
                startAngle = 90f, sweepAngle = 180f, useCenter = true,
                topLeft = Offset(c.x - r, c.y - r), size = Size(r * 2, r * 2),
            )
            // The closing window either side of top dead centre. This is the
            // real +/- 5 degrees the breaker will accept, not a generous
            // illustration of it.
            drawArc(
                color = if (live) Pal.green.copy(alpha = 0.55f) else Pal.dialInkFaint.copy(alpha = 0.12f),
                startAngle = -90f - windowDeg.toFloat(), sweepAngle = windowDeg.toFloat() * 2f,
                useCenter = true,
                topLeft = Offset(c.x - r, c.y - r), size = Size(r * 2, r * 2),
            )

            for (i in 0 until 24) {
                val a = i * 15.0 - 90.0
                val long = i % 6 == 0
                drawLine(
                    if (long) Pal.dialInk else Pal.dialInkFaint,
                    polar(c, r * (if (long) 0.74f else 0.84f), a),
                    polar(c, r * 0.92f, a),
                    strokeWidth = if (long) 2.6f else 1.2f,
                )
            }
            // Index mark: this is where you close.
            drawLine(Pal.needleRed, Offset(c.x, c.y - r * 0.96f), Offset(c.x, c.y - r * 0.66f),
                strokeWidth = 3.5f)

            val a = angleDeg - 90.0
            val tip = polar(c, r * 0.70f, a)
            val tail = polar(c, r * 0.30f, a + 180)
            val col = if (!live) Pal.dialInkFaint else if (inWindow) Color(0xFF1E6B36) else Pal.needle
            drawLine(col, tail, tip, strokeWidth = 4.5f)
            drawCircle(Pal.chromeDark, radius = r * 0.09f, center = c)
            drawCircle(Pal.brass, radius = r * 0.06f, center = c)

            drawArc(
                brush = Brush.linearGradient(
                    listOf(Color(0x22FFFFFF), Color(0x00FFFFFF)),
                    start = Offset(c.x - r, c.y - r), end = Offset(c.x, c.y),
                ),
                startAngle = 170f, sweepAngle = 110f, useCenter = true,
                topLeft = Offset(c.x - r, c.y - r), size = Size(r * 2, r * 2),
            )
            bezelScrews(c, outer)
        }

        Column(
            Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                if (live) "%+.1f".format(slipRpm) else "--",
                fontFamily = Mono, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                color = if (!live) Pal.dialInkFaint
                else if (inWindow) Color(0xFF1E6B36) else Pal.needleRed,
            )
            Text(
                if (!live) "RPM" else if (slipRpm > 0.1) "RPM FAST"
                else if (slipRpm < -0.1) "RPM SLOW" else "RPM",
                fontFamily = Mono, fontSize = 7.sp, color = Pal.dialInkFaint, maxLines = 1,
            )
        }
    }
}

/**
 * An indicator lamp: coloured glass in a chrome bezel. Dark when off, because
 * an incandescent lamp with no filament current is dark, not dim.
 */
@Composable
fun Lamp(label: String, on: Boolean, colour: Color, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(17.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val c = Offset(size.width / 2f, size.height / 2f)
                val r = size.width / 2f
                drawCircle(Pal.chromeDark, radius = r, center = c)
                if (on) {
                    // Hot filament: bright centre falling off to the lens colour.
                    drawCircle(
                        brush = Brush.radialGradient(
                            listOf(Color.White.copy(alpha = 0.85f), colour, colour),
                            center = Offset(c.x - r * 0.2f, c.y - r * 0.25f), radius = r * 1.3f,
                        ),
                        radius = r * 0.80f, center = c,
                    )
                } else {
                    drawCircle(colour.copy(alpha = 0.13f), radius = r * 0.80f, center = c)
                    drawCircle(Pal.lampOff, radius = r * 0.55f, center = c)
                }
            }
        }
        Text(
            label,
            fontFamily = Mono, fontSize = 7.sp, letterSpacing = 0.3.sp,
            color = if (on) colour else Pal.inkFaint,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

/** A labelled horizontal bar, for fuel, wear, load and the like. */
@Composable
fun BarMeter(
    label: String,
    frac: Double,
    text: String,
    colour: Color,
    modifier: Modifier = Modifier,
    markerFrac: Double? = null,
) {
    Column(modifier) {
        if (label.isNotEmpty() || text.isNotEmpty()) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(label, fontFamily = Mono, fontSize = 9.sp, color = Pal.inkDim)
                Text(text, fontFamily = Mono, fontSize = 9.sp, color = Pal.ink)
            }
        }
        Canvas(Modifier.fillMaxWidth().height(9.dp).padding(top = 2.dp)) {
            drawRoundRect(Pal.panelEdge, size = size, cornerRadius = CornerRadius(2f, 2f))
            val safe = if (frac.isFinite()) frac.coerceIn(0.0, 1.0) else 0.0
            val w = (size.width * safe).toFloat()
            if (w > 0f) {
                drawRoundRect(colour, size = Size(w, size.height), cornerRadius = CornerRadius(2f, 2f))
            }
            markerFrac?.let {
                if (!it.isFinite()) return@let
                val x = (size.width * it.coerceIn(0.0, 1.0)).toFloat()
                drawLine(Pal.chrome, Offset(x, 0f), Offset(x, size.height), strokeWidth = 2f)
            }
        }
    }
}

/** The recorder chart: city load over the last day with your share filled in. */
@Composable
fun LoadTrace(
    total: List<Float>,
    mine: List<Float>,
    maxKW: Float,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier) {
        drawRect(Pal.dialFace)
        val n = total.size
        if (n < 2 || maxKW <= 0f) return@Canvas
        val dx = size.width / (n - 1)
        fun y(v: Float) = size.height * (1f - (v / maxKW).coerceIn(0f, 1f))

        // Chart paper: a printed grid, not screen rules.
        for (i in 1..4) {
            val yy = size.height * i / 5f
            drawLine(Pal.dialInkFaint.copy(alpha = 0.35f), Offset(0f, yy), Offset(size.width, yy), 1f)
        }
        for (i in 1..5) {
            val xx = size.width * i / 6f
            drawLine(Pal.dialInkFaint.copy(alpha = 0.22f), Offset(xx, 0f), Offset(xx, size.height), 1f)
        }

        fun area(series: List<Float>, colour: Color) {
            val p = Path()
            p.moveTo(0f, size.height)
            series.forEachIndexed { i, v -> p.lineTo(i * dx, y(v)) }
            p.lineTo((n - 1) * dx, size.height)
            p.close()
            drawPath(p, colour)
        }

        area(total, Pal.dialInkFaint.copy(alpha = 0.22f))
        area(mine, Pal.brass.copy(alpha = 0.55f))

        val line = Path()
        total.forEachIndexed { i, v -> if (i == 0) line.moveTo(0f, y(v)) else line.lineTo(i * dx, y(v)) }
        drawPath(line, Pal.dialInk, style = Stroke(width = 1.8f))
    }
}

fun fmt(v: Double, digits: Int): String =
    if (!v.isFinite()) "--" else "%.${digits}f".format(v)

/** A raised panel section with an engraved heading plate. */
@Composable
fun PanelCard(
    title: String? = null,
    modifier: Modifier = Modifier,
    accent: Color = Pal.panelEdge,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(3.dp)
    Box(modifier) {
        Column(
            Modifier
                // Wrinkle-finish paint: lit from above, darker toward the
                // bottom of the pressing, with a hard seam all round.
                .background(
                    Brush.verticalGradient(listOf(Pal.panelHigh, Pal.panel, Pal.panelLow)),
                    shape,
                )
                .border(1.dp, accent.copy(alpha = 0.9f), shape)
                .padding(horizontal = 11.dp, vertical = 10.dp),
        ) {
            if (title != null) {
                LegendPlate(title, Modifier.padding(bottom = 7.dp), wide = true)
            }
            content()
        }
        // Four fasteners holding the pressing to the frame.
        Canvas(Modifier.matchParentSize()) {
            val inset = 5.dp.toPx()
            val rad = 1.9.dp.toPx()
            for (x in listOf(inset, size.width - inset)) {
                for (y in listOf(inset, size.height - inset)) {
                    drawCircle(Pal.panelEdge.copy(alpha = 0.7f), radius = rad * 1.35f,
                        center = Offset(x, y))
                    drawCircle(Pal.screw.copy(alpha = 0.65f), radius = rad, center = Offset(x, y))
                    drawLine(
                        Pal.panelEdge.copy(alpha = 0.8f),
                        Offset(x - rad * 0.7f, y - rad * 0.7f),
                        Offset(x + rad * 0.7f, y + rad * 0.7f),
                        strokeWidth = 1f,
                    )
                }
            }
        }
    }
}

/** A key/value readout row, monospaced so the columns line up. */
/**
 * A key/value row. The value takes the width it needs and the label gives way,
 * because a truncated label is readable and a number broken one digit per line
 * is not.
 */
@Composable
fun Readout(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    colour: Color = Pal.ink,
) {
    Row(
        modifier.fillMaxWidth().padding(vertical = 1.5.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label, fontFamily = Mono, fontSize = 10.sp, color = Pal.inkDim,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(end = 8.dp),
        )
        Text(
            value, fontFamily = Mono, fontSize = 10.sp, color = colour,
            fontWeight = FontWeight.Medium, maxLines = 1, softWrap = false,
        )
    }
}
