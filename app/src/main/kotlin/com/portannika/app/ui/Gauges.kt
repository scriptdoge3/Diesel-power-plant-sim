package com.portannika.app.ui

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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/* ============================================================================
 *  Instruments. Everything here is drawn rather than composed from widgets,
 *  because a panel gauge is a picture of a physical thing and it should read
 *  like one at a glance -- needle position first, number second.
 * ========================================================================== */

private const val START_DEG = 140.0     // sweep runs from lower-left...
private const val SWEEP_DEG = 260.0     // ...clockwise round to lower-right

private fun angleFor(frac: Double) = START_DEG + SWEEP_DEG * frac.coerceIn(0.0, 1.0)

private fun DrawScope.polar(centre: Offset, radius: Float, degrees: Double): Offset {
    val r = degrees * PI / 180.0
    return Offset(centre.x + radius * cos(r).toFloat(), centre.y + radius * sin(r).toFloat())
}

/**
 * A round panel meter.
 *
 * @param bands optional coloured arcs on the scale, as value ranges. This is
 *   how a real instrument tells you where the limits are without you reading.
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
    accent: Color = Pal.blue,
    subtitle: String? = null,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .background(Pal.bezel, CircleShape)
                .border(1.dp, Pal.panelEdge, CircleShape)
                .padding(4.dp),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val c = Offset(size.width / 2f, size.height / 2f)
                val r = min(size.width, size.height) / 2f

                // Dial face with a slight vignette, like glass over paint.
                drawCircle(
                    brush = Brush.radialGradient(
                        listOf(Pal.dialFace, Color(0xFF070A0D)),
                        center = c, radius = r,
                    ),
                    radius = r, center = c,
                )

                // Coloured limit bands on the scale.
                val bandR = r * 0.86f
                for ((from, to, colour) in bands) {
                    val f0 = ((from - min) / (max - min))
                    val f1 = ((to - min) / (max - min))
                    if (!f0.isFinite() || !f1.isFinite()) continue
                    if (f1 <= 0.0 || f0 >= 1.0 || f1 <= f0) continue
                    val a0 = angleFor(f0)
                    val a1 = angleFor(f1)
                    drawArc(
                        color = colour.copy(alpha = 0.55f),
                        startAngle = a0.toFloat(),
                        sweepAngle = (a1 - a0).toFloat(),
                        useCenter = false,
                        topLeft = Offset(c.x - bandR, c.y - bandR),
                        size = Size(bandR * 2, bandR * 2),
                        style = Stroke(width = r * 0.10f),
                    )
                }

                // Ticks.
                val steps = majorTicks.coerceAtLeast(2)
                for (i in 0..steps) {
                    val f = i.toDouble() / steps
                    val a = angleFor(f)
                    val outer = polar(c, r * 0.92f, a)
                    val inner = polar(c, r * 0.76f, a)
                    drawLine(Pal.dialTick, inner, outer, strokeWidth = r * 0.035f)
                    if (i < steps) {
                        for (j in 1..3) {
                            val fm = (i + j / 4.0) / steps
                            val am = angleFor(fm)
                            drawLine(
                                Pal.dialTick.copy(alpha = 0.5f),
                                polar(c, r * 0.84f, am), polar(c, r * 0.92f, am),
                                strokeWidth = r * 0.015f,
                            )
                        }
                    }
                }

                // Needle.
                val raw = if (max > min) (value - min) / (max - min) else 0.0
                val frac = if (raw.isFinite()) raw else 0.0
                val a = angleFor(frac)
                val tip = polar(c, r * 0.80f, a)
                val tail = polar(c, -r * 0.14f, a)
                val side1 = polar(c, r * 0.055f, a + 90)
                val side2 = polar(c, r * 0.055f, a - 90)
                drawPath(
                    Path().apply {
                        moveTo(tip.x, tip.y)
                        lineTo(side1.x, side1.y)
                        lineTo(tail.x, tail.y)
                        lineTo(side2.x, side2.y)
                        close()
                    },
                    color = Pal.needle,
                )
                drawCircle(Pal.panelEdge, radius = r * 0.11f, center = c)
                drawCircle(accent, radius = r * 0.055f, center = c)

                // Off-scale marker: a needle pinned at the stop should be obvious.
                if (frac > 1.0 || frac < 0.0) {
                    drawCircle(Pal.red, radius = r * 0.07f, center = polar(c, r * 0.62f, angleFor(if (frac > 1) 1.0 else 0.0)))
                }
            }

            Column(
                Modifier.padding(top = 26.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    fmt(value, decimals),
                    fontFamily = Mono,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    color = Pal.ink,
                )
                Text(unit, fontFamily = Mono, fontSize = 9.sp, color = Pal.inkFaint)
            }
        }
        Text(
            label.uppercase(),
            fontFamily = Mono,
            fontSize = 9.sp,
            letterSpacing = 1.sp,
            color = Pal.inkDim,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 3.dp),
        )
        if (subtitle != null) {
            Text(subtitle, fontFamily = Mono, fontSize = 8.sp, color = Pal.inkFaint)
        }
    }
}

/**
 * The synchroscope: a rotating pointer showing the angle between the incoming
 * machine and the running bus. Twelve o'clock is in phase. Clockwise means the
 * machine is fast, which is the direction you want when you close, because it
 * picks up load instead of being motored by the bus.
 */
@Composable
fun Synchroscope(
    angleDeg: Double,
    slipHz: Double,
    inWindow: Boolean,
    live: Boolean,
    modifier: Modifier = Modifier,
) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val c = Offset(size.width / 2f, size.height / 2f)
            val r = min(size.width, size.height) / 2f - 2f

            drawCircle(Pal.bezel, radius = r, center = c)
            drawCircle(Pal.panelEdge, radius = r, center = c, style = Stroke(1.5f))

            // "SLOW" on the left half, "FAST" on the right.
            drawArc(
                color = Pal.blue.copy(alpha = 0.16f),
                startAngle = 270f, sweepAngle = 90f, useCenter = true,
                topLeft = Offset(c.x - r, c.y - r), size = Size(r * 2, r * 2),
            )

            // The closing window either side of top dead centre.
            drawArc(
                color = if (live) Pal.green.copy(alpha = 0.30f) else Pal.inkFaint.copy(alpha = 0.12f),
                startAngle = -102f, sweepAngle = 24f, useCenter = true,
                topLeft = Offset(c.x - r, c.y - r), size = Size(r * 2, r * 2),
            )

            for (i in 0 until 24) {
                val a = i * 15.0 - 90.0
                val long = i % 6 == 0
                drawLine(
                    if (long) Pal.dialTick else Pal.dialTick.copy(alpha = 0.4f),
                    polar(c, r * (if (long) 0.80f else 0.88f), a),
                    polar(c, r * 0.96f, a),
                    strokeWidth = if (long) 2.5f else 1.2f,
                )
            }

            // Index mark at the top: this is where you close.
            drawLine(Pal.green, Offset(c.x, c.y - r), Offset(c.x, c.y - r * 0.72f), strokeWidth = 3.5f)

            // Pointer. Angle is measured from top dead centre.
            val a = angleDeg - 90.0
            val tip = polar(c, r * 0.72f, a)
            val tail = polar(c, r * 0.30f, a + 180)
            val col = if (!live) Pal.inkFaint else if (inWindow) Pal.greenGlow else Pal.amber
            drawLine(col, tail, tip, strokeWidth = 4f)
            drawCircle(col, radius = r * 0.07f, center = c)
        }

        Column(
            Modifier.padding(top = 44.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                if (live) "%+.2f Hz".format(slipHz) else "-- --",
                fontFamily = Mono, fontSize = 12.sp,
                color = if (!live) Pal.inkFaint else if (slipHz in 0.02..0.30) Pal.green else Pal.amber,
            )
            Text(
                if (slipHz > 0.005) "FAST" else if (slipHz < -0.005) "SLOW" else "",
                fontFamily = Mono, fontSize = 8.sp, color = Pal.inkFaint,
            )
        }
    }
}

/** A panel indicator lamp with a legend under it. */
@Composable
fun Lamp(label: String, on: Boolean, colour: Color, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(18.dp)
                .background(if (on) colour else colour.copy(alpha = 0.13f), CircleShape)
                .border(1.dp, Pal.panelEdge, CircleShape),
        )
        Text(
            label,
            fontFamily = Mono, fontSize = 8.sp, letterSpacing = 0.4.sp,
            color = if (on) colour else Pal.inkFaint,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

/** A labelled horizontal bar, for things like fuel, wear and load. */
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
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, fontFamily = Mono, fontSize = 10.sp, color = Pal.inkDim)
            Text(text, fontFamily = Mono, fontSize = 10.sp, color = Pal.ink)
        }
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(8.dp)
                .padding(top = 2.dp),
        ) {
            drawRoundRect(
                Pal.bezel,
                size = size,
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(3f, 3f),
            )
            val safe = if (frac.isFinite()) frac.coerceIn(0.0, 1.0) else 0.0
            val w = (size.width * safe).toFloat()
            if (w > 0f) {
                drawRoundRect(
                    colour,
                    size = Size(w, size.height),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(3f, 3f),
                )
            }
            markerFrac?.let {
                if (!it.isFinite()) return@let
                val x = (size.width * it.coerceIn(0.0, 1.0)).toFloat()
                drawLine(Pal.ink, Offset(x, 0f), Offset(x, size.height), strokeWidth = 2f)
            }
        }
    }
}

/**
 * The station load curve: what the town took over the last day, with your
 * share filled underneath it.
 */
@Composable
fun LoadTrace(
    total: List<Float>,
    mine: List<Float>,
    maxKW: Float,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier) {
        val n = total.size
        if (n < 2 || maxKW <= 0f) return@Canvas
        val dx = size.width / (n - 1)
        fun y(v: Float) = size.height * (1f - (v / maxKW).coerceIn(0f, 1f))

        // Horizontal rules every quarter of the scale.
        for (i in 1..3) {
            val yy = size.height * i / 4f
            drawLine(Pal.panelEdge, Offset(0f, yy), Offset(size.width, yy), strokeWidth = 1f)
        }

        fun area(series: List<Float>, colour: Color) {
            val p = Path()
            p.moveTo(0f, size.height)
            series.forEachIndexed { i, v -> p.lineTo(i * dx, y(v)) }
            p.lineTo((n - 1) * dx, size.height)
            p.close()
            drawPath(p, colour)
        }

        area(total, Pal.blue.copy(alpha = 0.20f))
        area(mine, Pal.brass.copy(alpha = 0.55f))

        val line = Path()
        total.forEachIndexed { i, v -> if (i == 0) line.moveTo(0f, y(v)) else line.lineTo(i * dx, y(v)) }
        drawPath(line, Pal.blue, style = Stroke(width = 2f))
    }
}

fun fmt(v: Double, digits: Int): String =
    if (!v.isFinite()) "--" else "%.${digits}f".format(v)

/** Guard for anything fed straight into a Canvas coordinate. */
fun Double.orZero(): Double = if (isFinite()) this else 0.0

/** A raised panel section with a stencilled heading. */
@Composable
fun PanelCard(
    title: String? = null,
    modifier: Modifier = Modifier,
    accent: Color = Pal.panelEdge,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .background(Pal.panel, RoundedCornerShape(10.dp))
            .border(1.dp, accent.copy(alpha = 0.55f), RoundedCornerShape(10.dp))
            .padding(10.dp),
    ) {
        if (title != null) {
            Text(
                title.uppercase(),
                fontFamily = Mono,
                fontSize = 10.sp,
                letterSpacing = 1.4.sp,
                color = Pal.inkDim,
                modifier = Modifier.padding(bottom = 6.dp),
            )
        }
        content()
    }
}

/** A key/value readout row, monospaced so columns line up. */
@Composable
fun Readout(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    colour: Color = Pal.ink,
) {
    Row(
        modifier.fillMaxWidth().padding(vertical = 1.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, fontFamily = Mono, fontSize = 11.sp, color = Pal.inkDim)
        Text(value, fontFamily = Mono, fontSize = 11.sp, color = colour, fontWeight = FontWeight.Medium)
    }
}

internal fun maxOfList(l: List<Float>): Float = l.maxOrNull() ?: 0f
internal fun safeMax(a: Float, b: Float) = max(a, b)
