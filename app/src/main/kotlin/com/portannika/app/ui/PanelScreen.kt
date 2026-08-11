package com.portannika.app.ui

import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.portannika.app.GameHost
import com.portannika.core.EngineBase
import com.portannika.core.GovernorBase
import com.portannika.core.Genset
import com.portannika.core.Nominal
import com.portannika.core.RunState
import kotlin.math.abs

@Composable
fun PanelScreen(host: GameHost) {
    val sim = host.sim
    val u = sim.selectedUnit
    val snap = sim.lastSnapshot
    val scroll = rememberScrollState()

    Column(
        Modifier
            .verticalScroll(scroll)
            .padding(horizontal = 10.dp)
            .padding(top = 8.dp, bottom = 16.dp),
    ) {
        if (sim.units.size > 1) {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                for (unit in sim.units) {
                    Chip(
                        "${unit.spec.name}  ${"%.0f".format(unit.elecKW)}kW",
                        unit.id == sim.selectedUnitId,
                        colour = when {
                            unit.runState == RunState.FAILED -> Pal.red
                            unit.onBus -> Pal.green
                            unit.isRunning -> Pal.amber
                            else -> Pal.blue
                        },
                    ) { sim.selectUnit(unit.id) }
                }
            }
        }

        UnitHeader(u, sim)
        Spacer(Modifier.height(8.dp))
        GaugeCluster(u)
        Spacer(Modifier.height(8.dp))
        SyncAndBreaker(host, u)
        Spacer(Modifier.height(8.dp))
        EngineControls(host, u)
        Spacer(Modifier.height(8.dp))
        GovernorControls(host, u)
        Spacer(Modifier.height(8.dp))
        ExcitationControls(host, u)
        Spacer(Modifier.height(8.dp))
        MachineDetail(u, snap.frequencyHz)
    }
}

// ------------------------------------------------------------------- header

@Composable
private fun UnitHeader(u: Genset, sim: com.portannika.core.Sim) {
    PanelCard(accent = if (u.onBus) Pal.green else Pal.panelEdge) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(u.spec.name, fontFamily = Mono, fontSize = 15.sp,
                    fontWeight = FontWeight.Bold, color = Pal.ink)
                Text(
                    "${u.spec.make}  ·  %.1f L  ·  %.0f kW / %.0f kVA"
                        .format(u.spec.displacementL, u.spec.ratedKW, u.spec.ratedKVA),
                    fontFamily = Mono, fontSize = 9.sp, color = Pal.inkFaint,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                val (label, colour) = when (u.runState) {
                    RunState.FAILED -> "FAILED" to Pal.red
                    RunState.RUNNING -> (if (u.onBus) "ON LINE" else "RUNNING") to (if (u.onBus) Pal.green else Pal.amber)
                    RunState.CRANKING -> "CRANKING" to Pal.amber
                    RunState.PRELUBE -> "PRELUBE" to Pal.blue
                    RunState.STARTING -> "STARTING" to Pal.amber
                    RunState.COOLDOWN -> "COOLING" to Pal.blue
                    RunState.STOPPED -> "STOPPED" to Pal.inkFaint
                }
                Text(label, fontFamily = Mono, fontSize = 13.sp,
                    fontWeight = FontWeight.Bold, color = colour, letterSpacing = 1.sp)
                Text("%.0f h on the clock".format(u.runHours),
                    fontFamily = Mono, fontSize = 9.sp, color = Pal.inkFaint)
            }
        }
        u.failureText?.let {
            Spacer(Modifier.height(4.dp))
            Text(it, fontFamily = Mono, fontSize = 10.sp, color = Pal.red)
            Text("Repair it on the Plant screen.", fontFamily = Mono, fontSize = 9.sp, color = Pal.inkFaint)
        }
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Lamp("RUN", u.isRunning, Pal.green)
            Lamp("BKR", u.breakerClosed, Pal.green)
            Lamp("SMOKE", u.smokeExcess > 0.12, Pal.amber)
            Lamp("HI EGT", u.egtC > EngineBase.EGT_WARN_C, Pal.amber)
            Lamp("HI TEMP", u.coolantC > EngineBase.COOLANT_WARN_C, Pal.red)
            Lamp("LO OIL", u.isRunning && u.oilPressureBar < EngineBase.OIL_PRESS_MIN_BAR, Pal.red)
            Lamp("O/L", u.kva > u.spec.ratedKVA * 1.02, Pal.red)
            Lamp("AUTO", sim.autoPlant && u.spec.autoStart, Pal.violet)
        }
    }
}

// ------------------------------------------------------------------- gauges

@Composable
private fun GaugeCluster(u: Genset) {
    val fMax = 100.0
    PanelCard {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            AnalogGauge(
                "Frequency", u.freqHz, 70.0, fMax, "Hz", Modifier.weight(1f), decimals = 1,
                bands = listOf(
                    Triple(70.0, Nominal.FREQ - Nominal.FREQ_BAND, Pal.amber),
                    Triple(Nominal.FREQ - Nominal.FREQ_BAND, Nominal.FREQ + Nominal.FREQ_BAND, Pal.green),
                    Triple(Nominal.FREQ + Nominal.FREQ_BAND, fMax, Pal.red),
                ),
                subtitle = "%.0f rpm".format(u.rpm),
            )
            AnalogGauge(
                "Real power", u.elecKW, -10.0, u.spec.ratedKW * 1.35, "kW", Modifier.weight(1f),
                bands = listOf(
                    Triple(-10.0, 0.0, Pal.red),
                    Triple(u.spec.ratedKW, u.spec.ratedKW * 1.35, Pal.red),
                ),
                accent = Pal.brass,
                subtitle = "rated %.0f".format(u.spec.ratedKW),
            )
            AnalogGauge(
                "Reactive", u.kvar, -u.spec.ratedKVA * 0.6, u.spec.ratedKVA * 0.85, "kVAr",
                Modifier.weight(1f), accent = Pal.violet,
                subtitle = "pf %.2f".format(abs(u.powerFactor)),
            )
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            AnalogGauge(
                "Volts", u.terminalVoltsPU * Nominal.GEN_VOLTS, 0.0, 600.0, "V", Modifier.weight(1f),
                bands = listOf(
                    Triple(456.0, 504.0, Pal.green),
                    Triple(540.0, 600.0, Pal.red),
                ),
                subtitle = "%.0f V line".format(u.terminalVoltsPU * Nominal.LINE_VOLTS),
            )
            AnalogGauge(
                "Coolant", u.coolantC, 0.0, 120.0, "°C", Modifier.weight(1f),
                bands = listOf(
                    Triple(EngineBase.THERMOSTAT_OPEN_C, EngineBase.COOLANT_WARN_C, Pal.green),
                    Triple(EngineBase.COOLANT_WARN_C, EngineBase.COOLANT_TRIP_C, Pal.amber),
                    Triple(EngineBase.COOLANT_TRIP_C, 120.0, Pal.red),
                ),
                accent = Pal.green,
                subtitle = "oil %.0f°C".format(u.oilC),
            )
            AnalogGauge(
                "Oil press", u.oilPressureBar, 0.0, 5.0, "bar", Modifier.weight(1f), decimals = 1,
                bands = listOf(
                    Triple(0.0, EngineBase.OIL_PRESS_MIN_BAR, Pal.red),
                    Triple(EngineBase.OIL_PRESS_MIN_BAR, 1.6, Pal.amber),
                ),
                accent = Pal.amber,
            )
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            AnalogGauge(
                "Exhaust", u.egtC, 0.0, 800.0, "°C", Modifier.weight(1f),
                bands = listOf(
                    Triple(EngineBase.EGT_WARN_C, EngineBase.EGT_LIMIT_C, Pal.amber),
                    Triple(EngineBase.EGT_LIMIT_C, 800.0, Pal.red),
                ),
                accent = Pal.red,
                subtitle = "AFR %.0f".format(u.afr.coerceAtMost(99.0)),
            )
            if (u.spec.turbo != null) {
                AnalogGauge(
                    "Boost", u.boostBar, 0.0, (u.spec.turbo!!.maxBoostBar * 1.2), "bar",
                    Modifier.weight(1f), decimals = 2, accent = Pal.blue,
                    subtitle = "man %.0f°C".format(u.manifoldC),
                )
            } else {
                AnalogGauge(
                    "Fuel rack", u.rack * 100.0, 0.0, 100.0, "%", Modifier.weight(1f),
                    bands = listOf(Triple(92.0, 100.0, Pal.red)),
                    accent = Pal.brass,
                    subtitle = "%.1f L/h".format(u.fuelRateKgS * 3600.0 / EngineBase.FUEL_DENSITY),
                )
            }
            AnalogGauge(
                "Stator temp", u.windingC, 0.0, 200.0, "°C", Modifier.weight(1f),
                bands = listOf(
                    Triple(u.spec.windingLimitC, 200.0, Pal.red),
                    Triple(u.spec.windingLimitC - 25.0, u.spec.windingLimitC, Pal.amber),
                ),
                accent = Pal.violet,
                subtitle = "%.0f%% load".format(u.loadFraction * 100),
            )
        }
    }
}

// ------------------------------------------------------- synchronising panel

@Composable
private fun SyncAndBreaker(host: GameHost, u: Genset) {
    val sim = host.sim
    val busF = sim.grid.frequencyHz
    val busV = sim.grid.busVoltPU
    val check = u.syncCheck(busF, busV)
    val live = u.isRunning && !u.onBus

    PanelCard("Synchronising", accent = if (check.allOk && live) Pal.green else Pal.panelEdge) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(112.dp)) {
                Synchroscope(
                    angleDeg = if (live) u.phaseDeg else 0.0,
                    slipHz = check.slipHz,
                    inWindow = check.allOk && check.directionOk,
                    live = live,
                )
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                SyncRow("Slip", "%+.3f Hz".format(check.slipHz), check.slipOk,
                    if (check.directionOk) "coming in fast" else if (check.slipHz < 0) "SLOW -- bus will motor you" else "too fast")
                SyncRow("Angle", "%+.0f°".format(check.angleDeg), check.angleOk, "want within 12° of top")
                SyncRow("Volts", "%+.1f %%".format(check.voltErrPct), check.voltOk, "match the bus before closing")
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Lamp("BUS LIVE", sim.grid.busVoltPU > 0.5, Pal.green)
                    Lamp("READY", check.allOk && check.directionOk && live, Pal.greenGlow)
                    Lamp("ON LINE", u.onBus, Pal.green)
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (u.onBus) {
                PanelButton("OPEN BREAKER", Modifier.weight(1f), colour = Pal.amber.copy(alpha = 0.25f),
                    textColour = Pal.amber) {
                    sim.openBreaker(u.id)
                }
            } else {
                PanelButton(
                    "CLOSE BREAKER", Modifier.weight(1f),
                    enabled = live,
                    colour = if (check.allOk && check.directionOk) Pal.green.copy(alpha = 0.3f) else Pal.panelHigh,
                    textColour = if (check.allOk && check.directionOk) Pal.greenGlow else Pal.ink,
                ) { host.say(sim.closeBreaker(u.id, force = false)) }
                PanelButton("FORCE", enabled = live, colour = Pal.red.copy(alpha = 0.22f),
                    textColour = Pal.red) { host.say(sim.closeBreaker(u.id, force = true)) }
            }
            if (u.spec.autoSync && !u.onBus) {
                PanelButton("AUTO SYNC", colour = Pal.violet.copy(alpha = 0.25f), textColour = Pal.violet,
                    enabled = live) {
                    sim.autoPlant = true
                    host.say("Automatic synchroniser armed")
                }
            }
        }
        if (!u.spec.autoSync) {
            Text(
                "Bring it in slightly FAST and close as the pointer passes twelve.",
                fontFamily = Mono, fontSize = 9.sp, color = Pal.inkFaint,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

@Composable
private fun SyncRow(label: String, value: String, ok: Boolean, hint: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 1.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(7.dp).padding(end = 0.dp)) {
            androidx.compose.foundation.Canvas(Modifier.size(7.dp)) {
                drawCircle(if (ok) Pal.green else Pal.red)
            }
        }
        Spacer(Modifier.width(6.dp))
        Text(label, fontFamily = Mono, fontSize = 10.sp, color = Pal.inkDim, modifier = Modifier.width(42.dp))
        Text(value, fontFamily = Mono, fontSize = 11.sp, color = if (ok) Pal.green else Pal.amber,
            fontWeight = FontWeight.Bold, modifier = Modifier.width(74.dp))
        Text(hint, fontFamily = Mono, fontSize = 8.sp, color = Pal.inkFaint)
    }
}

// ----------------------------------------------------------- engine controls

@Composable
private fun EngineControls(host: GameHost, u: Genset) {
    val sim = host.sim
    PanelCard("Engine") {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            PanelButton(
                "START", Modifier.weight(1f),
                enabled = !u.isRunning && u.runState != RunState.FAILED && u.runState != RunState.CRANKING,
                colour = Pal.green.copy(alpha = 0.25f), textColour = Pal.greenGlow,
            ) { sim.startUnit(u.id) }
            PanelButton(
                "STOP", Modifier.weight(1f), enabled = u.isRunning,
                colour = Pal.panelHigh,
            ) { sim.stopUnit(u.id) }
            PanelButton(
                "E-STOP", Modifier.weight(1f),
                colour = Pal.red.copy(alpha = 0.3f), textColour = Pal.redGlow,
            ) { sim.emergencyStop(u.id) }
        }
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Chip("PRELUBE", u.prelubeRunning) { sim.setPrelube(u.id, !u.prelubeRunning) }
            Spacer(Modifier.width(8.dp))
            Text(
                if (u.prelubeRunning) "Oil pumped up before cranking. Slower start, much less wear."
                else "Dry start. Fast, and it costs the bearings.",
                fontFamily = Mono, fontSize = 8.sp, color = Pal.inkFaint,
            )
        }
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            BarMeter("Battery", u.batterySoC, "%.0f%%".format(u.batterySoC * 100),
                if (u.batterySoC > 0.3) Pal.green else Pal.red, Modifier.weight(1f))
            BarMeter("Fuel rack", u.rack, "%.0f%%".format(u.rack * 100),
                if (u.smokeExcess > 0.1) Pal.amber else Pal.brass, Modifier.weight(1f))
        }
        Spacer(Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            BarMeter("Tank", (sim.fuelL / sim.plant.fuelTankL), "%.0f / %.0f L".format(sim.fuelL, sim.plant.fuelTankL),
                if (sim.fuelL / sim.plant.fuelTankL > 0.15) Pal.blue else Pal.red, Modifier.weight(1f))
            BarMeter("Cyl. pressure", (u.peakCylBar / u.spec.gasketLimitBar),
                "%.0f / %.0f bar".format(u.peakCylBar, u.spec.gasketLimitBar),
                if (u.peakCylBar > u.spec.gasketLimitBar) Pal.red else Pal.green, Modifier.weight(1f))
        }
    }
}

// --------------------------------------------------------- governor controls

@Composable
private fun GovernorControls(host: GameHost, u: Genset) {
    val sim = host.sim
    val isoch = u.spec.isoch && u.droop < 0.001
    PanelCard("Governor  ·  ${if (u.spec.egov) "electronic" else "mechanical flyweight"}") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("SPEEDER", fontFamily = Mono, fontSize = 10.sp, color = Pal.inkDim,
                modifier = Modifier.width(62.dp))
            NudgeButton("−−") { sim.adjustSpeeder(u.id, -0.004) }
            Spacer(Modifier.width(4.dp))
            NudgeButton("−") { sim.adjustSpeeder(u.id, -0.0007) }
            Slider(
                value = u.speederPU.toFloat(),
                onValueChange = { sim.setSpeeder(u.id, it.toDouble()) },
                valueRange = GovernorBase.SPEEDER_MIN.toFloat()..GovernorBase.SPEEDER_MAX.toFloat(),
                modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
                colors = SliderDefaults.colors(
                    thumbColor = Pal.brass, activeTrackColor = Pal.brass.copy(alpha = 0.6f),
                    inactiveTrackColor = Pal.panelHigh,
                ),
            )
            NudgeButton("+") { sim.adjustSpeeder(u.id, 0.0007) }
            Spacer(Modifier.width(4.dp))
            NudgeButton("++") { sim.adjustSpeeder(u.id, 0.004) }
        }
        Text(
            if (u.onBus) "On the bus the speeder sets your kW, not your speed. No-load setting %.3f pu (%.1f Hz)"
                .format(u.speederPU, u.speederPU * Nominal.FREQ)
            else "No-load speed setting %.3f pu = %.1f Hz".format(u.speederPU, u.speederPU * Nominal.FREQ),
            fontFamily = Mono, fontSize = 8.sp, color = Pal.inkFaint,
        )

        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("DROOP", fontFamily = Mono, fontSize = 10.sp, color = Pal.inkDim,
                modifier = Modifier.width(62.dp))
            Slider(
                value = u.droop.toFloat(),
                onValueChange = { sim.setDroop(u.id, it.toDouble()) },
                valueRange = u.spec.droopMin.toFloat()..u.spec.droopMax.toFloat(),
                modifier = Modifier.weight(1f),
                colors = SliderDefaults.colors(
                    thumbColor = Pal.blue, activeTrackColor = Pal.blue.copy(alpha = 0.6f),
                    inactiveTrackColor = Pal.panelHigh,
                ),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                if (isoch) "ISOCH" else "%.1f%%".format(u.droop * 100),
                fontFamily = Mono, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                color = if (isoch) Pal.violet else Pal.ink, modifier = Modifier.width(52.dp),
            )
        }
        Text(
            when {
                isoch -> "Isochronous: this machine holds 90 Hz on its own and absorbs every load swing."
                !u.spec.egov && u.droop < GovernorBase.HUNT_THRESHOLD ->
                    "Below about 3% the flyweights start hunting. Listen to it."
                else -> "Less droop takes a bigger share of load changes. Machines on the same bus must all have some."
            },
            fontFamily = Mono, fontSize = 8.sp,
            color = if (!u.spec.egov && u.droop < GovernorBase.HUNT_THRESHOLD) Pal.amber else Pal.inkFaint,
        )
    }
}

// ------------------------------------------------------- excitation controls

@Composable
private fun ExcitationControls(host: GameHost, u: Genset) {
    val sim = host.sim
    PanelCard("Excitation  ·  ${if (u.spec.avr) "automatic regulator" else "hand rheostat"}") {
        if (u.spec.avr) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("SET V", fontFamily = Mono, fontSize = 10.sp, color = Pal.inkDim,
                    modifier = Modifier.width(56.dp))
                Slider(
                    value = u.avrSetpointPU.toFloat(),
                    onValueChange = { sim.setAvrSetpoint(u.id, it.toDouble()) },
                    valueRange = 0.90f..1.10f,
                    modifier = Modifier.weight(1f),
                    colors = SliderDefaults.colors(
                        thumbColor = Pal.violet, activeTrackColor = Pal.violet.copy(alpha = 0.6f),
                        inactiveTrackColor = Pal.panelHigh,
                    ),
                )
                Spacer(Modifier.width(8.dp))
                Text("%.0f V".format(u.avrSetpointPU * Nominal.GEN_VOLTS),
                    fontFamily = Mono, fontSize = 12.sp, color = Pal.ink,
                    fontWeight = FontWeight.Bold, modifier = Modifier.width(52.dp))
            }
            Text(
                if (u.spec.varShare)
                    "Cross-current compensation is fitted: the machine takes its fair share of kVAr automatically."
                else "No cross-current compensation. Set this above the others and you will hog the reactive load.",
                fontFamily = Mono, fontSize = 8.sp, color = Pal.inkFaint,
            )
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("FIELD", fontFamily = Mono, fontSize = 10.sp, color = Pal.inkDim,
                    modifier = Modifier.width(56.dp))
                NudgeButton("−") { sim.setField(u.id, u.fieldRheostat - 0.01) }
                Slider(
                    value = u.fieldRheostat.toFloat(),
                    onValueChange = { sim.setField(u.id, it.toDouble()) },
                    valueRange = 0f..1f,
                    modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
                    colors = SliderDefaults.colors(
                        thumbColor = Pal.brass, activeTrackColor = Pal.brass.copy(alpha = 0.6f),
                        inactiveTrackColor = Pal.panelHigh,
                    ),
                )
                NudgeButton("+") { sim.setField(u.id, u.fieldRheostat + 0.01) }
            }
            Text(
                "Rheostat %.0f%%  ·  field %.2f pu  ·  open-circuit volts %.0f V"
                    .format(u.fieldRheostat * 100, u.fieldPU, u.emfPU * Nominal.GEN_VOLTS),
                fontFamily = Mono, fontSize = 8.sp, color = Pal.inkFaint,
            )
            Text(
                if (u.onBus)
                    "On the bus, the field is your kVAr control. Too much and you carry everyone's reactive load; too little and you absorb theirs."
                else "Off the bus, the field sets your terminal volts. Match the bus before you close.",
                fontFamily = Mono, fontSize = 8.sp, color = Pal.inkFaint,
            )
        }
    }
}

// -------------------------------------------------------------- the numbers

@Composable
private fun MachineDetail(u: Genset, busFreq: Double) {
    PanelCard("Instrumentation") {
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Column(Modifier.weight(1f)) {
                Readout("Real power", "%.1f kW".format(u.elecKW), colour = Pal.brass)
                Readout("Reactive", "%.1f kVAr".format(u.kvar), colour = Pal.violet)
                Readout("Apparent", "%.1f kVA".format(u.kva))
                Readout("Power factor", "%.3f %s".format(abs(u.powerFactor),
                    if (u.kvar >= 0) "lag" else "lead"))
                Readout("Stator current", "%.0f %% rated".format(u.ampsPU * 100),
                    colour = if (u.ampsPU > 1.0) Pal.red else Pal.ink)
                Readout("Terminal volts", "%.0f V".format(u.terminalVoltsPU * Nominal.GEN_VOLTS))
                Readout("Service volts", "%.0f-0-%.0f V".format(
                    u.terminalVoltsPU * Nominal.SERVICE_HALF, u.terminalVoltsPU * Nominal.SERVICE_HALF))
            }
            Column(Modifier.weight(1f)) {
                Readout("Shaft power", "%.1f kW".format(u.brakeKW))
                Readout("Fuel rate", "%.2f L/h".format(u.fuelRateKgS * 3600.0 / EngineBase.FUEL_DENSITY))
                Readout(
                    "Specific fuel",
                    if (u.elecKW > 1.0) "%.3f kg/kWh".format(u.fuelRateKgS * 3600.0 / u.elecKW) else "--",
                )
                Readout("Air/fuel ratio", if (u.afr < 98) "%.1f : 1".format(u.afr) else "--",
                    colour = if (u.smokeExcess > 0.05) Pal.amber else Pal.ink)
                Readout("Manifold", "%.0f °C".format(u.manifoldC))
                Readout("Jacket heat", "%.1f kW".format(u.jacketHeatKW))
                Readout("Exhaust heat", "%.1f kW".format(u.exhaustHeatKW))
                if (u.recoveredHeatKW > 0.01) {
                    Readout("Heat sold", "%.1f kW".format(u.recoveredHeatKW), colour = Pal.brass)
                }
            }
        }
    }
}
