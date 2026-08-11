package com.portannika.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.portannika.app.GameHost
import com.portannika.core.MEGAWATT_KW
import com.portannika.core.Nominal
import com.portannika.core.TIME_SCALES
import kotlin.math.abs

/* ============================================================================
 *  Shared chrome: the status strip that never leaves the screen, the buttons
 *  everything else is built from, and the two full-screen overlays.
 * ========================================================================== */

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
    Box(
        modifier
            .background(
                if (enabled) colour else Pal.panelHigh.copy(alpha = 0.4f),
                RoundedCornerShape(6.dp),
            )
            .border(
                1.dp,
                if (enabled) textColour.copy(alpha = 0.35f) else Pal.panelEdge,
                RoundedCornerShape(6.dp),
            )
            .clickable(enabled = enabled) { onClick() }
            .padding(horizontal = if (small) 8.dp else 12.dp, vertical = if (small) 5.dp else 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            fontFamily = Mono,
            fontSize = if (small) 10.sp else 12.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.6.sp,
            color = if (enabled) textColour else Pal.inkFaint,
        )
    }
}

/** A small square nudge control, for fine adjustment of a continuous setting. */
@Composable
fun NudgeButton(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        Modifier
            .size(34.dp)
            .background(Pal.panelHigh, RoundedCornerShape(6.dp))
            .border(1.dp, Pal.panelEdge, RoundedCornerShape(6.dp))
            .clickable(enabled = enabled) { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Text(text, fontFamily = Mono, fontSize = 13.sp,
            color = if (enabled) Pal.ink else Pal.inkFaint, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun Chip(text: String, selected: Boolean, colour: Color = Pal.blue, onClick: () -> Unit) {
    Box(
        Modifier
            .background(if (selected) colour.copy(alpha = 0.22f) else Pal.panel, RoundedCornerShape(14.dp))
            .border(1.dp, if (selected) colour else Pal.panelEdge, RoundedCornerShape(14.dp))
            .clickable { onClick() }
            .padding(horizontal = 11.dp, vertical = 5.dp),
    ) {
        Text(text, fontFamily = Mono, fontSize = 10.sp,
            color = if (selected) colour else Pal.inkDim, fontWeight = FontWeight.Bold)
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
            .background(Pal.panel)
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    "${date.dateStr}  ${date.clock}",
                    fontFamily = Mono, fontSize = 11.sp, color = Pal.ink,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    "%.0f C   fuel %.0f L @ %s/L".format(
                        sim.grid.ambientC, sim.fuelL, sim.money(sim.effectiveFuelPrice)),
                    fontFamily = Mono, fontSize = 9.sp, color = Pal.inkFaint,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    sim.money(sim.cash),
                    fontFamily = Mono, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                    color = if (sim.cash < 0) Pal.red else Pal.brass,
                )
                Text(
                    "rep %.2f   %s/kWh".format(sim.campaign.reputation, sim.money(sim.ratePerKWh)),
                    fontFamily = Mono, fontSize = 9.sp, color = Pal.inkFaint,
                )
            }
        }

        Spacer(Modifier.height(5.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            // The two numbers that matter most, always visible.
            BigStat(
                "%.2f".format(snap.frequencyHz), "Hz",
                if (snap.blackout) Pal.red else if (freqOk) Pal.green else Pal.amber,
            )
            Spacer(Modifier.width(10.dp))
            BigStat(
                "%.0f".format(snap.busVolts), "V BUS",
                if (abs(voltPct) <= Nominal.VOLT_BAND_PCT) Pal.green else Pal.amber,
            )
            Spacer(Modifier.width(10.dp))
            BigStat("%.0f".format(snap.playerKW), "kW MINE", Pal.brass)
            Spacer(Modifier.weight(1f))

            // Speed control. Pause is a real control here: the plant keeps
            // running in your head while you think about the next move.
            Row(verticalAlignment = Alignment.CenterVertically) {
                PanelButton(
                    if (sim.paused) "▶" else "II",
                    small = true,
                    colour = if (sim.paused) Pal.amber.copy(alpha = 0.25f) else Pal.panelHigh,
                ) { sim.togglePause() }
                Spacer(Modifier.width(4.dp))
                for (s in TIME_SCALES) {
                    Box(Modifier.padding(horizontal = 1.dp)) {
                        Chip("${s}x", sim.timeScale == s && !sim.paused) {
                            sim.changeTimeScale(s)
                            if (sim.paused) sim.togglePause()
                        }
                    }
                }
                Spacer(Modifier.width(4.dp))
                PanelButton("?", small = true) { host.showHelp = true }
            }
        }

        // Alarm strip: the worst active alarm across the whole plant.
        val alarms = sim.activeAlarms()
        if (alarms.isNotEmpty() || snap.blackout || snap.shedKW > 0.5) {
            Spacer(Modifier.height(5.dp))
            val text: String
            val colour: Color
            when {
                snap.blackout -> { text = "TOWN IS BLACK -- no generation on the bus"; colour = Pal.red }
                snap.shedKW > 0.5 -> { text = "LOAD SHEDDING -- %.0f kW of the town is off".format(snap.shedKW); colour = Pal.red }
                else -> {
                    val (unit, a) = alarms.first()
                    text = "$unit: ${a.text}" + if (alarms.size > 1) "   (+${alarms.size - 1} more)" else ""
                    colour = if (a.critical) Pal.red else Pal.amber
                }
            }
            Box(
                Modifier
                    .fillMaxWidth()
                    .background(colour.copy(alpha = 0.18f), RoundedCornerShape(5.dp))
                    .border(1.dp, colour.copy(alpha = 0.6f), RoundedCornerShape(5.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            ) {
                Text(text, fontFamily = Mono, fontSize = 10.sp, color = colour, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun BigStat(value: String, unit: String, colour: Color) {
    Row(verticalAlignment = Alignment.Bottom) {
        Text(value, fontFamily = Mono, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = colour)
        Text(" $unit", fontFamily = Mono, fontSize = 8.sp, color = Pal.inkFaint,
            modifier = Modifier.padding(bottom = 2.dp))
    }
}

// ------------------------------------------------------------------ overlays

@Composable
fun HelpSheet(onClose: () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xE6060809))
            .clickable { onClose() },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .padding(18.dp)
                .background(Pal.panel, RoundedCornerShape(12.dp))
                .border(1.dp, Pal.panelEdge, RoundedCornerShape(12.dp))
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Text("HOW TO RUN THE UNIT", fontFamily = Mono, fontSize = 13.sp,
                fontWeight = FontWeight.Bold, color = Pal.brass, letterSpacing = 1.sp)
            Spacer(Modifier.height(10.dp))
            for ((n, line) in HELP_STEPS.withIndex()) {
                Row(Modifier.padding(bottom = 7.dp)) {
                    Box(
                        Modifier.size(18.dp).background(Pal.panelHigh, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("${n + 1}", fontFamily = Mono, fontSize = 9.sp, color = Pal.blue)
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(line, fontSize = 12.sp, color = Pal.ink, lineHeight = 16.sp)
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "Port Annika runs at 90 Hz because the old cannery plant did, so a " +
                    "6-pole machine turns 1800 rpm. You generate at 480 V; the station " +
                    "bank steps that to the 7200 V line, and pole transformers give the " +
                    "town 180-0-180 split phase.",
                fontSize = 11.sp, color = Pal.inkDim, lineHeight = 15.sp,
            )
            Spacer(Modifier.height(12.dp))
            PanelButton("CLOSE", colour = Pal.blue.copy(alpha = 0.25f)) { onClose() }
        }
    }
}

private val HELP_STEPS = listOf(
    "Open the fuel valve and press START. The starter cranks; a cold engine needs more speed before it fires, so watch the battery.",
    "Let the coolant come up before you load it. Running a cold engine hard is how bearings die young.",
    "Bring up the field with the rheostat until your volts match the bus. Watch the voltmeter, not the clock.",
    "Set the speeder so the synchroscope creeps CLOCKWISE -- slightly fast. Coming in slow means the bus motors you the instant you close.",
    "Close the breaker as the pointer passes twelve o'clock. Closing out of phase will bend something expensive.",
    "Raise the speeder to take load. On droop, the speeder is your kW control; it is not a speed control once you are paralleled.",
    "Watch exhaust temperature and coolant. If the engine smokes, you are asking for more fuel than you have air for.",
    "To come off: wind the speeder down until you are near zero kW, THEN open the breaker, then let it idle before you stop it.",
)

@Composable
fun WinScreen(host: GameHost) {
    val sim = host.sim
    Box(
        Modifier.fillMaxSize().background(Color(0xF2070A0C)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("THE FIRST MEGAWATT", fontFamily = Mono, fontSize = 22.sp,
                fontWeight = FontWeight.Bold, color = Pal.brass, letterSpacing = 2.sp)
            Spacer(Modifier.height(14.dp))
            Text(
                "You arrived with a 50 kW cannery engine and a hand rheostat. " +
                    "Port Annika now takes its power from %,.0f kW of plant that is yours, " +
                    "certified to lose its largest machine and still carry the town."
                        .format(sim.installedKW),
                fontSize = 13.sp, color = Pal.ink, lineHeight = 19.sp,
            )
            Spacer(Modifier.height(16.dp))
            Readout("Installed capacity", "%,.0f kW".format(sim.installedKW))
            Readout("Largest unit", "%,.0f kW".format(sim.largestUnitKW))
            Readout("Capacity less largest unit", "%,.0f kW".format(sim.n1CapacityKW))
            Readout("Energy delivered", "%,.0f kWh".format(sim.campaign.totalDeliveredKWh))
            Readout("Peak output", "%,.0f kW".format(sim.campaign.peakDeliveredKW))
            Readout("Days elapsed", "%.0f".format(sim.gameSeconds / 86400.0))
            Readout("Cash", sim.money(sim.cash), colour = Pal.brass)
            Spacer(Modifier.height(6.dp))
            Text(
                "Target was %,.0f kW.".format(MEGAWATT_KW),
                fontFamily = Mono, fontSize = 10.sp, color = Pal.inkFaint,
            )
        }
    }
}
