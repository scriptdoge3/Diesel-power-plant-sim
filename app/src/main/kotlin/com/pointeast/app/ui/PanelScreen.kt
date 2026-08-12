package com.pointeast.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pointeast.app.GameHost
import com.pointeast.core.Genset
import com.pointeast.core.GovernorBase
import com.pointeast.core.Nominal
import kotlin.math.abs

/**
 * The switchboard.
 *
 * Electrical instruments and electrical controls, and nothing else. Every
 * meter on this board reads a quantity you can change from this board:
 * frequency and kilowatts with the speeder, volts and reactive with the field,
 * and the synchroscope with both. The engine's own gauges -- coolant, oil,
 * exhaust, boost -- live on the engine board, because that is where they are
 * bolted in a real station and because they are not things you steer with.
 */
@Composable
fun PanelScreen(host: GameHost) {
    val sim = host.sim
    val u = sim.selectedUnit

    UnitSelector(sim)
    UnitHeader(u, sim)
    Spacer(Modifier.height(7.dp))
    PowerMeters(u)
    Spacer(Modifier.height(7.dp))
    VoltageMeters(u)
    Spacer(Modifier.height(7.dp))
    SyncAndBreaker(host, u)
    Spacer(Modifier.height(7.dp))
    GovernorControls(host, u)
    Spacer(Modifier.height(7.dp))
    ExcitationControls(host, u)
    Spacer(Modifier.height(7.dp))
    ElectricalReadout(u)
}

// ------------------------------------------------- what the speeder controls

@Composable
private fun PowerMeters(u: Genset) {
    PanelCard {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AnalogGauge(
                // Expanded scale. 80-100 Hz put the whole in-band region inside
                // one tick and made the meter useless for the job it exists for.
                "Frequency", u.freqHz, 84.0, 96.0, "Hz", Modifier.weight(1f), decimals = 1,
                bands = listOf(
                    Triple(84.0, Nominal.FREQ - Nominal.FREQ_BAND, Pal.amber),
                    Triple(Nominal.FREQ - Nominal.FREQ_BAND, Nominal.FREQ + Nominal.FREQ_BAND, Pal.green),
                    Triple(Nominal.FREQ + Nominal.FREQ_BAND, 96.0, Pal.red),
                ),
                majorTicks = 6,
            )
            AnalogGauge(
                "Real power", u.elecKW, -10.0, u.spec.ratedKW * 1.3, "kW", Modifier.weight(1f),
                bands = listOf(
                    Triple(-10.0, 0.0, Pal.red),
                    Triple(u.spec.ratedKW, u.spec.ratedKW * 1.3, Pal.red),
                ),
                subtitle = "rated %.0f kW".format(u.spec.ratedKW),
            )
        }
    }
}

// --------------------------------------------------- what the field controls

@Composable
private fun VoltageMeters(u: Genset) {
    PanelCard {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AnalogGauge(
                "Volts", u.terminalVoltsPU * Nominal.GEN_VOLTS, 0.0, 600.0, "V",
                Modifier.weight(1f), majorTicks = 6,
                bands = listOf(
                    Triple(456.0, 504.0, Pal.green),
                    Triple(540.0, 600.0, Pal.red),
                ),
                subtitle = "%,.0f V line".format(u.terminalVoltsPU * Nominal.LINE_VOLTS),
            )
            AnalogGauge(
                "Reactive", u.kvar, -u.spec.ratedKVA * 0.6, u.spec.ratedKVA * 0.85, "kVAr",
                Modifier.weight(1f), majorTicks = 6,
                subtitle = "pf %.2f %s".format(
                    abs(u.powerFactor), if (u.kvar >= 0) "lag" else "lead"),
            )
        }
        Spacer(Modifier.height(6.dp))
        BarMeter(
            "Stator current", u.ampsPU, "%.0f%% of rated".format(u.ampsPU * 100),
            when {
                u.ampsPU > 1.05 -> Pal.red
                u.ampsPU > 0.95 -> Pal.amber
                else -> Pal.green
            },
            markerFrac = 1.0,
        )
    }
}

// ------------------------------------------------------- synchronising panel

@Composable
private fun SyncAndBreaker(host: GameHost, u: Genset) {
    val sim = host.sim
    val check = u.syncCheck(sim.grid.frequencyHz, sim.grid.busVoltPU)
    val live = u.isRunning && !u.onBus
    val ready = check.allOk && check.directionOk && live

    PanelCard("Synchronising", accent = if (ready) Pal.green.copy(alpha = 0.8f) else Pal.panelEdge) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(104.dp)) {
                Synchroscope(
                    angleDeg = if (live) u.phaseDeg else 0.0,
                    slipHz = check.slipHz,
                    slipRpm = check.slipRpm,
                    windowDeg = Nominal.SYNC_ANGLE_DEG,
                    inWindow = ready,
                    live = live,
                )
            }
            Spacer(Modifier.width(9.dp))
            Column(Modifier.weight(1f)) {
                SyncRow("SPEED", "%+.1f rpm".format(check.slipRpm),
                    check.slipOk && check.directionOk)
                SyncRow("PHASE", "%+.0f°".format(check.angleDeg), check.angleOk)
                SyncRow("VOLTS", "%+.1f%%".format(check.voltErrPct), check.voltOk)
                Text(
                    "limits  ±%.0f rpm  ±%.0f°".format(
                        Nominal.SYNC_SLIP_RPM, Nominal.SYNC_ANGLE_DEG),
                    fontFamily = Mono, fontSize = 8.sp, color = Pal.inkFaint, maxLines = 1,
                )
                Spacer(Modifier.height(5.dp))
                Text(
                    when {
                        !live && u.onBus -> "On line."
                        !live -> "Start the machine on the engine board first."
                        !check.voltOk -> "Match volts with the field."
                        check.slipHz < Nominal.SYNC_MIN_SLIP_HZ ->
                            "Slow -- raise the speeder until it creeps clockwise."
                        check.slipHz > Nominal.SYNC_SLIP_HZ ->
                            "%.0f rpm fast -- ease the speeder down.".format(check.slipRpm)
                        !check.angleOk -> "Speed is right. Wait for twelve o'clock."
                        else -> "Ready. Close now."
                    },
                    fontFamily = Mono, fontSize = 9.sp, lineHeight = 12.sp,
                    color = if (ready) Pal.greenGlow else Pal.inkDim,
                )
            }
        }

        Spacer(Modifier.height(8.dp))
        if (u.onBus) {
            PanelButton("OPEN BREAKER", Modifier.fillMaxWidth(),
                colour = Pal.amber.copy(alpha = 0.28f), textColour = Pal.amber) {
                sim.openBreaker(u.id)
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                PanelButton(
                    "CLOSE BREAKER", Modifier.weight(1f), enabled = live,
                    colour = if (ready) Pal.green.copy(alpha = 0.34f) else Pal.panelHigh,
                    textColour = if (ready) Pal.greenGlow else Pal.ink,
                ) { host.say(sim.closeBreaker(u.id, force = false)) }
                PanelButton("FORCE", enabled = live,
                    colour = Pal.red.copy(alpha = 0.22f), textColour = Pal.red) {
                    host.say(sim.closeBreaker(u.id, force = true))
                }
            }
        }
    }
}

@Composable
private fun SyncRow(label: String, value: String, ok: Boolean) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp)
            .background(if (ok) Pal.green else Pal.red, RoundedCornerShape(4.dp)))
        Spacer(Modifier.width(7.dp))
        Text(label, fontFamily = Mono, fontSize = 9.sp, color = Pal.inkDim,
            maxLines = 1, softWrap = false, modifier = Modifier.width(52.dp))
        Text(value, fontFamily = Mono, fontSize = 12.sp,
            color = if (ok) Pal.green else Pal.amber, fontWeight = FontWeight.Bold,
            maxLines = 1, softWrap = false)
    }
}

// --------------------------------------------------------- governor controls

@Composable
private fun GovernorControls(host: GameHost, u: Genset) {
    val sim = host.sim
    val isoch = u.spec.isoch && u.droop < 0.001
    PanelCard("Governor  ·  ${if (u.spec.egov) "electronic" else "flyweight"}") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            NudgeButton("−", big = true) { sim.adjustSpeeder(u.id, -0.0035) }
            Spacer(Modifier.width(6.dp))
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    if (u.onBus) "%.1f kW".format(u.elecKW)
                    else "%.2f Hz".format(u.speederPU * Nominal.FREQ),
                    fontFamily = Mono, fontSize = 20.sp, fontWeight = FontWeight.Bold,
                    color = Pal.brass, maxLines = 1,
                )
                Slider(
                    value = u.speederPU.toFloat(),
                    onValueChange = { sim.setSpeeder(u.id, it.toDouble()) },
                    valueRange = GovernorBase.SPEEDER_MIN.toFloat()..GovernorBase.SPEEDER_MAX.toFloat(),
                    colors = SliderDefaults.colors(
                        thumbColor = Pal.chrome, activeTrackColor = Pal.brass.copy(alpha = 0.7f),
                        inactiveTrackColor = Pal.panelLow,
                    ),
                )
                LegendPlate("Speeder  ${"%.3f".format(u.speederPU)} pu", wide = true)
            }
            Spacer(Modifier.width(6.dp))
            NudgeButton("+", big = true) { sim.adjustSpeeder(u.id, 0.0035) }
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp),
            horizontalArrangement = Arrangement.Center) {
            NudgeButton("−−") { sim.adjustSpeeder(u.id, -0.0006) }
            Spacer(Modifier.width(8.dp))
            Text("fine", fontFamily = Mono, fontSize = 9.sp, color = Pal.inkFaint,
                modifier = Modifier.padding(top = 14.dp))
            Spacer(Modifier.width(8.dp))
            NudgeButton("++") { sim.adjustSpeeder(u.id, 0.0006) }
        }
        Text(
            if (u.onBus) "On the bus the speeder is your kilowatt control, not a speed control."
            else "Off the bus it sets the no-load speed.",
            fontFamily = Mono, fontSize = 9.sp, color = Pal.inkFaint,
            modifier = Modifier.padding(top = 5.dp),
        )

        Spacer(Modifier.height(9.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("DROOP", fontFamily = Mono, fontSize = 10.sp, color = Pal.inkDim,
                maxLines = 1, softWrap = false, modifier = Modifier.width(56.dp))
            Slider(
                value = u.droop.toFloat(),
                onValueChange = { sim.setDroop(u.id, it.toDouble()) },
                valueRange = u.spec.droopMin.toFloat()..u.spec.droopMax.toFloat(),
                modifier = Modifier.weight(1f),
                colors = SliderDefaults.colors(
                    thumbColor = Pal.chrome, activeTrackColor = Pal.blue.copy(alpha = 0.7f),
                    inactiveTrackColor = Pal.panelLow,
                ),
            )
            Spacer(Modifier.width(8.dp))
            Text(if (isoch) "ISOCH" else "%.1f%%".format(u.droop * 100),
                fontFamily = Mono, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                color = if (isoch) Pal.violet else Pal.ink, maxLines = 1,
                modifier = Modifier.width(48.dp))
        }
        Text(
            when {
                isoch -> "Isochronous: this machine holds 90 Hz alone and absorbs every swing."
                !u.spec.egov && u.droop < GovernorBase.HUNT_THRESHOLD ->
                    "Below about 3% the flyweights hunt. Listen to it."
                else -> "Less droop takes a bigger share of every load change."
            },
            fontFamily = Mono, fontSize = 9.sp, lineHeight = 12.sp,
            color = if (!u.spec.egov && u.droop < GovernorBase.HUNT_THRESHOLD) Pal.amber
            else Pal.inkFaint,
        )
    }
}

// ------------------------------------------------------- excitation controls

@Composable
private fun ExcitationControls(host: GameHost, u: Genset) {
    val sim = host.sim
    PanelCard("Excitation  ·  ${if (u.spec.avr) "regulator" else "hand rheostat"}") {
        if (u.spec.avr) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("SET V", fontFamily = Mono, fontSize = 10.sp, color = Pal.inkDim,
                    maxLines = 1, softWrap = false, modifier = Modifier.width(56.dp))
                Slider(
                    value = u.avrSetpointPU.toFloat(),
                    onValueChange = { sim.setAvrSetpoint(u.id, it.toDouble()) },
                    valueRange = 0.90f..1.10f,
                    modifier = Modifier.weight(1f),
                    colors = SliderDefaults.colors(
                        thumbColor = Pal.chrome, activeTrackColor = Pal.violet.copy(alpha = 0.7f),
                        inactiveTrackColor = Pal.panelLow,
                    ),
                )
                Spacer(Modifier.width(8.dp))
                Text("%.0f V".format(u.avrSetpointPU * Nominal.GEN_VOLTS),
                    fontFamily = Mono, fontSize = 12.sp, color = Pal.ink,
                    fontWeight = FontWeight.Bold, maxLines = 1, modifier = Modifier.width(50.dp))
            }
            Text(
                if (u.spec.varShare)
                    "Cross-current compensation fitted: it takes its fair share of kVAr."
                else "No cross-current compensation. Set this high and you hog the reactive load.",
                fontFamily = Mono, fontSize = 9.sp, color = Pal.inkFaint, lineHeight = 12.sp,
            )
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                NudgeButton("−") { sim.setField(u.id, u.fieldRheostat - 0.01) }
                Slider(
                    value = u.fieldRheostat.toFloat(),
                    onValueChange = { sim.setField(u.id, it.toDouble()) },
                    valueRange = 0f..1f,
                    modifier = Modifier.weight(1f).padding(horizontal = 6.dp),
                    colors = SliderDefaults.colors(
                        thumbColor = Pal.chrome, activeTrackColor = Pal.brass.copy(alpha = 0.7f),
                        inactiveTrackColor = Pal.panelLow,
                    ),
                )
                NudgeButton("+") { sim.setField(u.id, u.fieldRheostat + 0.01) }
            }
            Row(Modifier.fillMaxWidth().padding(top = 3.dp),
                horizontalArrangement = Arrangement.SpaceBetween) {
                LegendPlate("Field  ${"%.0f".format(u.fieldRheostat * 100)}%")
                Text("open circuit %.0f V".format(u.emfPU * Nominal.GEN_VOLTS),
                    fontFamily = Mono, fontSize = 9.sp, color = Pal.inkFaint, maxLines = 1)
            }
            Text(
                if (u.onBus)
                    "On the bus the field is your kVAr control: too much and you carry everyone's reactive load."
                else "Off the bus the field sets your volts. Match the bus before you close.",
                fontFamily = Mono, fontSize = 9.sp, color = Pal.inkFaint, lineHeight = 12.sp,
                modifier = Modifier.padding(top = 3.dp),
            )
        }
    }
}

// --------------------------------------------------------- electrical detail

@Composable
private fun ElectricalReadout(u: Genset) {
    PanelCard("Metering") {
        Readout("Real power", "%.1f kW".format(u.elecKW), colour = Pal.brass)
        Readout("Reactive", "%.1f kVAr".format(u.kvar), colour = Pal.violet)
        Readout("Apparent", "%.1f kVA of %.0f".format(u.kva, u.spec.ratedKVA),
            colour = if (u.kva > u.spec.ratedKVA) Pal.red else Pal.ink)
        Readout("Power factor", "%.3f %s".format(abs(u.powerFactor),
            if (u.kvar >= 0) "lag" else "lead"))
        Readout("Stator current", "%.0f%% rated".format(u.ampsPU * 100),
            colour = if (u.ampsPU > 1.0) Pal.red else Pal.ink)
        Readout("Frequency", "%.2f Hz".format(u.freqHz))
        Readout("Terminal volts", "%.0f V".format(u.terminalVoltsPU * Nominal.GEN_VOLTS))
        Readout("Primary line", "%,.0f V".format(u.terminalVoltsPU * Nominal.LINE_VOLTS))
        Readout("Customer service", "%.0f-0-%.0f V".format(
            u.terminalVoltsPU * Nominal.SERVICE_HALF, u.terminalVoltsPU * Nominal.SERVICE_HALF))
    }
}
