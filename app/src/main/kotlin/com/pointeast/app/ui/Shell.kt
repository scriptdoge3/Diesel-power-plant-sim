package com.pointeast.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pointeast.app.GameHost
import com.pointeast.core.City
import com.pointeast.core.MEGAWATT_KW
import com.pointeast.core.Nominal
import com.pointeast.core.PROLOGUE
import com.pointeast.core.PointEast
import com.pointeast.core.TIME_SCALES
import kotlin.math.abs

/* ============================================================================
 *  Panel hardware and the two story overlays.
 *
 *  Every control here is at least 44dp on its short side, because this is a
 *  phone and the alternative is missing the breaker button.
 * ========================================================================== */

/** A rectangular panel pushbutton with an engraved cap. */
@Composable
fun PanelButton(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colour: Color = Pal.panelHigh,
    textColour: Color = Pal.ink,
    small: Boolean = false,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(3.dp)
    Box(
        modifier
            .defaultMinSize(minHeight = if (small) 32.dp else 46.dp)
            .background(
                brush = if (enabled) Brush.verticalGradient(
                    listOf(colour, colour.copy(alpha = 0.62f)),
                ) else Brush.verticalGradient(listOf(Pal.panelLow, Pal.panelLow)),
                shape = shape,
            )
            .border(1.dp, if (enabled) textColour.copy(alpha = 0.45f) else Pal.panelEdge, shape)
            .clickable(enabled = enabled) { onClick() }
            .padding(horizontal = if (small) 9.dp else 12.dp, vertical = if (small) 6.dp else 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            fontFamily = Mono,
            fontSize = if (small) 10.sp else 12.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.9.sp,
            color = if (enabled) textColour else Pal.inkFaint,
            maxLines = 1,
        )
    }
}

/** A square nudge key, for fine adjustment. Sized for a thumb. */
@Composable
fun NudgeButton(text: String, enabled: Boolean = true, big: Boolean = false, onClick: () -> Unit) {
    Box(
        Modifier
            .size(if (big) 52.dp else 44.dp)
            .background(
                Brush.verticalGradient(listOf(Pal.panelHigh, Pal.panelLow)),
                RoundedCornerShape(3.dp),
            )
            .border(1.dp, Pal.chromeDark.copy(alpha = 0.6f), RoundedCornerShape(3.dp))
            .clickable(enabled = enabled) { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Text(text, fontFamily = Mono, fontSize = if (big) 17.sp else 14.sp,
            color = if (enabled) Pal.ink else Pal.inkFaint, fontWeight = FontWeight.Bold)
    }
}

/**
 * A bat-handle toggle switch. Up is on. The handle actually moves, which is
 * the only animation in the game and the only one it needs.
 */
@Composable
fun ToggleSwitch(label: String, on: Boolean, modifier: Modifier = Modifier, onToggle: () -> Unit) {
    Column(modifier.clickable { onToggle() }, horizontalAlignment = Alignment.CenterHorizontally) {
        Canvas(Modifier.size(width = 26.dp, height = 44.dp)) {
            val cx = size.width / 2f
            // Chrome escutcheon
            drawRoundRect(
                Pal.chromeDark,
                topLeft = Offset(cx - size.width * 0.34f, size.height * 0.30f),
                size = androidx.compose.ui.geometry.Size(size.width * 0.68f, size.height * 0.40f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f, 4f),
            )
            // Bat handle, thrown up or down
            val baseY = size.height * 0.50f
            val tipY = if (on) size.height * 0.10f else size.height * 0.90f
            drawLine(
                Brush.verticalGradient(listOf(Pal.chrome, Pal.chromeDark)),
                Offset(cx, baseY), Offset(cx, tipY), strokeWidth = size.width * 0.26f,
                cap = androidx.compose.ui.graphics.StrokeCap.Round,
            )
            drawCircle(Pal.chrome, radius = size.width * 0.17f, center = Offset(cx, tipY))
        }
        LegendPlate(label, Modifier.padding(top = 2.dp))
    }
}

/** A tab / selector chip, styled as a small engraved key. */
@Composable
fun Chip(text: String, selected: Boolean, colour: Color = Pal.brass, onClick: () -> Unit) {
    Box(
        Modifier
            .defaultMinSize(minHeight = 34.dp)
            .background(
                if (selected) colour.copy(alpha = 0.26f) else Pal.panelLow,
                RoundedCornerShape(3.dp),
            )
            .border(1.dp, if (selected) colour else Pal.panelEdge, RoundedCornerShape(3.dp))
            .clickable { onClick() }
            .padding(horizontal = 11.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, fontFamily = Mono, fontSize = 10.sp,
            color = if (selected) colour else Pal.inkDim, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

// ---------------------------------------------------------------- status bar

@Composable
fun StatusBar(host: GameHost) {
    val sim = host.sim
    val snap = sim.lastSnapshot
    val date = sim.date
    val freqOk = abs(snap.frequencyHz - Nominal.FREQ) <= Nominal.FREQ_BAND
    val voltPct = (snap.busVoltsPU - 1.0) * 100.0

    Column(
        Modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Pal.panelHigh, Pal.panel)))
            .border(1.dp, Pal.panelEdge)
            .padding(horizontal = 9.dp, vertical = 5.dp),
    ) {
        // Line one: the three numbers you must always be able to see.
        Row(verticalAlignment = Alignment.CenterVertically) {
            BigStat("%.2f".format(snap.frequencyHz), "Hz",
                if (snap.blackout) Pal.red else if (freqOk) Pal.green else Pal.amber)
            Spacer(Modifier.width(12.dp))
            BigStat("%.0f".format(snap.busVolts), "V",
                if (abs(voltPct) <= Nominal.VOLT_BAND_PCT) Pal.green else Pal.amber)
            Spacer(Modifier.width(12.dp))
            BigStat("%.0f".format(snap.playerKW), "kW", Pal.brass)
            Spacer(Modifier.weight(1f))
            Column(horizontalAlignment = Alignment.End) {
                Text(sim.money(sim.cash), fontFamily = Mono, fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (sim.cash < 0) Pal.red else Pal.brass)
                Text("${date.monthName} ${date.day} · ${date.clock}",
                    fontFamily = Mono, fontSize = 9.sp, color = Pal.inkFaint)
            }
        }

        Spacer(Modifier.height(4.dp))

        // Line two: the clock controls, scrollable so they never crowd out.
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PanelButton(
                if (sim.paused) "RUN" else "HOLD", small = true,
                colour = if (sim.paused) Pal.amber.copy(alpha = 0.30f) else Pal.panelHigh,
            ) { sim.togglePause() }
            Spacer(Modifier.width(5.dp))
            for (s in TIME_SCALES) {
                Box(Modifier.padding(end = 3.dp)) {
                    Chip("${s}x", sim.timeScale == s && !sim.paused, Pal.blue) {
                        sim.changeTimeScale(s)
                        if (sim.paused) sim.togglePause()
                    }
                }
            }
            Spacer(Modifier.width(3.dp))
            Text(
                "%.0f°C  ·  %.0f L  ·  rep %.2f".format(
                    sim.grid.ambientC, sim.fuelL, sim.campaign.reputation),
                fontFamily = Mono, fontSize = 9.sp, color = Pal.inkFaint,
            )
            Spacer(Modifier.width(6.dp))
            PanelButton("?", small = true) { host.showHelp = true }
        }

        // Alarm annunciator: the worst thing wrong anywhere in the plant.
        val alarms = sim.activeAlarms()
        if (alarms.isNotEmpty() || snap.blackout || snap.shedKW > 0.5) {
            Spacer(Modifier.height(4.dp))
            val text: String
            val colour: Color
            when {
                snap.blackout -> { text = "CITY IS BLACK -- no generation on the bus"; colour = Pal.red }
                snap.pointEastShedKW > 0.5 ->
                    { text = "POINT EAST SHED -- %.0f kW of Sector E is dark".format(snap.pointEastShedKW); colour = Pal.red }
                snap.shedKW > 0.5 -> { text = "LOAD SHEDDING -- %.0f kW off".format(snap.shedKW); colour = Pal.red }
                else -> {
                    val (unit, a) = alarms.first()
                    text = "$unit: ${a.text}" + if (alarms.size > 1) "  (+${alarms.size - 1})" else ""
                    colour = if (a.critical) Pal.red else Pal.amber
                }
            }
            Box(
                Modifier
                    .fillMaxWidth()
                    .background(colour.copy(alpha = 0.20f), RoundedCornerShape(2.dp))
                    .border(1.dp, colour.copy(alpha = 0.7f), RoundedCornerShape(2.dp))
                    .padding(horizontal = 7.dp, vertical = 4.dp),
            ) {
                Text(text, fontFamily = Mono, fontSize = 10.sp, color = colour,
                    fontWeight = FontWeight.Bold, maxLines = 2)
            }
        }
    }
}

@Composable
private fun BigStat(value: String, unit: String, colour: Color) {
    Row(verticalAlignment = Alignment.Bottom) {
        Text(value, fontFamily = Mono, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = colour)
        Text(unit, fontFamily = Mono, fontSize = 8.sp, color = Pal.inkFaint,
            modifier = Modifier.padding(bottom = 2.dp, start = 1.dp))
    }
}

// ------------------------------------------------------------------ overlays

/** The opening, one page at a time. */
@Composable
fun PrologueSheet(onDone: () -> Unit) {
    var page by remember { mutableIntStateOf(0) }
    val last = page >= PROLOGUE.size - 1

    Box(Modifier.fillMaxSize().background(Color(0xF20E1211)), contentAlignment = Alignment.Center) {
        Column(
            Modifier.padding(20.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            LegendPlate("${City.NAME}  ·  ${PointEast.SECTOR}", wide = true)
            Spacer(Modifier.height(18.dp))
            Box(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                Text(
                    PROLOGUE[page],
                    fontSize = 14.sp, color = Pal.ink, lineHeight = 22.sp,
                )
            }
            Spacer(Modifier.height(18.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${page + 1} / ${PROLOGUE.size}", fontFamily = Mono, fontSize = 10.sp,
                    color = Pal.inkFaint)
                Spacer(Modifier.weight(1f))
                if (!last) {
                    PanelButton("SKIP", small = true) { onDone() }
                    Spacer(Modifier.width(8.dp))
                }
                PanelButton(
                    if (last) "BEGIN" else "NEXT",
                    colour = Pal.brass.copy(alpha = 0.30f), textColour = Pal.brass,
                ) { if (last) onDone() else page++ }
            }
        }
    }
}

/** A chapter beat, shown when a milestone lands. */
@Composable
fun ChapterSheet(title: String, body: String, onDone: () -> Unit) {
    Box(Modifier.fillMaxSize().background(Color(0xEE0E1211)), contentAlignment = Alignment.Center) {
        Column(
            Modifier
                .padding(20.dp)
                .background(Pal.panel, RoundedCornerShape(4.dp))
                .border(1.dp, Pal.brass.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                .padding(16.dp),
        ) {
            LegendPlate(title, wide = true)
            Spacer(Modifier.height(12.dp))
            Box(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                Text(body, fontSize = 13.sp, color = Pal.ink, lineHeight = 20.sp)
            }
            Spacer(Modifier.height(14.dp))
            PanelButton("CLOSE", Modifier.fillMaxWidth(),
                colour = Pal.brass.copy(alpha = 0.28f), textColour = Pal.brass) { onDone() }
        }
    }
}

@Composable
fun HelpSheet(onClose: () -> Unit) {
    Box(Modifier.fillMaxSize().background(Color(0xF00E1211)), contentAlignment = Alignment.Center) {
        Column(
            Modifier
                .padding(16.dp)
                .background(Pal.panel, RoundedCornerShape(4.dp))
                .border(1.dp, Pal.panelEdge, RoundedCornerShape(4.dp))
                .padding(14.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            LegendPlate("Starting and loading a set", wide = true)
            Spacer(Modifier.height(10.dp))
            for ((n, line) in HELP_STEPS.withIndex()) {
                Row(Modifier.padding(bottom = 7.dp)) {
                    Box(
                        Modifier.size(18.dp).background(Pal.panelLow, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("${n + 1}", fontFamily = Mono, fontSize = 9.sp, color = Pal.brass)
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(line, fontSize = 12.sp, color = Pal.ink, lineHeight = 16.sp)
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "${City.NAME} runs at 90 Hz, so a 6-pole machine turns 1800 rpm. You " +
                    "generate at 480 V; the station bank steps that to the 7200 V line, and " +
                    "pole transformers give the customer 180-0-180 split phase.\n\n" +
                    "Point East grows only while your plant is the reason its lights are on. " +
                    "Every hour you carry Sector E cleanly, somebody out there decides to build.",
                fontSize = 11.sp, color = Pal.inkDim, lineHeight = 16.sp,
            )
            Spacer(Modifier.height(12.dp))
            PanelButton("CLOSE", Modifier.fillMaxWidth(),
                colour = Pal.blue.copy(alpha = 0.25f)) { onClose() }
        }
    }
}

private val HELP_STEPS = listOf(
    "Open the fuel valve and press START. A cold engine needs more cranking speed before it fires, so watch the battery.",
    "Let the coolant come up before you load it. Running a cold engine hard is how bearings die young.",
    "Bring up the field with the rheostat until your volts match the bus. Watch the voltmeter.",
    "Set the speeder so the synchroscope creeps CLOCKWISE -- slightly fast. Coming in slow means the bus motors you the instant you close.",
    "Close the breaker as the pointer passes twelve. Closing out of phase will bend something expensive.",
    "Raise the speeder to take load. On the bus the speeder is your kW control, not a speed control.",
    "Watch exhaust temperature and coolant. If it smokes, you are asking for more fuel than you have air for.",
    "To come off: wind the speeder down to near zero kW, THEN open the breaker, then let it idle before stopping it.",
)

@Composable
fun WinScreen(host: GameHost) {
    val sim = host.sim
    Box(Modifier.fillMaxSize().background(Color(0xF60C100F)), contentAlignment = Alignment.Center) {
        Column(
            Modifier.padding(22.dp).verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            LegendPlate("The First Megawatt", wide = true)
            Spacer(Modifier.height(16.dp))
            Text(
                "You came to ${City.NAME} on a two-year contract and stayed for a " +
                    "generating station. Point East Electrical now runs %,.0f kW of plant, " +
                    "certified to lose its largest machine and still carry Sector E.\n\n" +
                    "Somewhere under the newest engine in the hall, Set 1 is still turning."
                        .format(sim.installedKW),
                fontSize = 13.sp, color = Pal.ink, lineHeight = 20.sp,
            )
            Spacer(Modifier.height(16.dp))
            Readout("Installed capacity", "%,.0f kW".format(sim.installedKW), colour = Pal.brass)
            Readout("Largest machine", "%,.0f kW".format(sim.largestUnitKW))
            Readout("Capacity less largest", "%,.0f kW".format(sim.n1CapacityKW))
            Readout("Point East built out", "%.0f %%".format(sim.grid.pointEastConfidence * 100))
            Readout("Point East load", "%,.0f kW".format(sim.grid.pointEastDemandKW))
            Readout("Energy delivered", "%,.0f kWh".format(sim.campaign.totalDeliveredKWh))
            Readout("Peak output", "%,.0f kW".format(sim.campaign.peakDeliveredKW))
            Readout("Days elapsed", "%.0f".format(sim.gameSeconds / 86400.0))
            Readout("Cash", sim.money(sim.cash), colour = Pal.brass)
            Spacer(Modifier.height(8.dp))
            Text("Target was %,.0f kW.".format(MEGAWATT_KW),
                fontFamily = Mono, fontSize = 10.sp, color = Pal.inkFaint)
        }
    }
}
