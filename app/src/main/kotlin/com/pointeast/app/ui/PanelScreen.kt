package com.pointeast.app.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pointeast.app.GameHost
import com.pointeast.core.EngineBase
import com.pointeast.core.Genset
import com.pointeast.core.GovernorBase
import com.pointeast.core.Nominal
import com.pointeast.core.RunState
import com.pointeast.core.Sim
import kotlin.math.abs

/**
 * The control panel for one machine, laid out for a phone held in one hand.
 *
 * Two large meters carry the reading you take constantly -- frequency and
 * kilowatts -- and everything else is a compact secondary row. Controls are
 * below the instruments, in reach of a thumb, in the order you actually touch
 * them: start, synchronise, load, excite.
 */
@Composable
fun PanelScreen(host: GameHost) {
    val sim = host.sim
    val u = sim.selectedUnit

    Column(
        Modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 8.dp)
            .padding(top = 7.dp, bottom = 18.dp),
    ) {
        if (sim.units.size > 1) {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                    .padding(bottom = 7.dp),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                for (unit in sim.units) {
                    Chip(
                        "${unit.spec.name}  ${"%.0f".format(unit.elecKW)}kW",
                        unit.id == sim.selectedUnitId,
                        colour = when {
                            unit.runState == RunState.FAILED -> Pal.red
                            unit.onBus -> Pal.green
                            unit.isRunning -> Pal.amber
                            else -> Pal.chrome
                        },
                    ) { sim.selectUnit(unit.id) }
                }
            }
        }

        UnitHeader(u, sim)
        Spacer(Modifier.height(7.dp))
        MainMeters(u)
        Spacer(Modifier.height(7.dp))
        SecondaryMeters(u)
        Spacer(Modifier.height(7.dp))
        EngineControls(host, u)
        Spacer(Modifier.height(7.dp))
        SyncAndBreaker(host, u)
        Spacer(Modifier.height(7.dp))
        GovernorControls(host, u)
        Spacer(Modifier.height(7.dp))
        ExcitationControls(host, u)
        Spacer(Modifier.height(7.dp))
        Detail(u)
    }
}

// ------------------------------------------------------------------- header

@Composable
private fun UnitHeader(u: Genset, sim: Sim) {
    PanelCard(accent = if (u.onBus) Pal.green.copy(alpha = 0.7f) else Pal.panelEdge) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(u.spec.name, fontFamily = Mono, fontSize = 15.sp,
                    fontWeight = FontWeight.Bold, color = Pal.ink)
                Text("${u.spec.make}  ·  %.0f kW / %.0f kVA".format(u.spec.ratedKW, u.spec.ratedKVA),
                    fontFamily = Mono, fontSize = 9.sp, color = Pal.inkFaint)
            }
            val (label, colour) = when (u.runState) {
                RunState.FAILED -> "FAILED" to Pal.red
                RunState.RUNNING -> (if (u.onBus) "ON LINE" else "RUNNING") to
                    (if (u.onBus) Pal.green else Pal.amber)
                RunState.CRANKING -> "CRANKING" to Pal.amber
                RunState.PRELUBE -> "PRELUBE" to Pal.blue
                RunState.STARTING -> "STARTING" to Pal.amber
                RunState.COOLDOWN -> "COOLING" to Pal.blue
                RunState.STOPPED -> "STOPPED" to Pal.inkFaint
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(label, fontFamily = Mono, fontSize = 13.sp,
                    fontWeight = FontWeight.Bold, color = colour, letterSpacing = 1.sp)
                Text("%,.0f h".format(u.runHours), fontFamily = Mono, fontSize = 9.sp,
                    color = Pal.inkFaint)
            }
        }
        u.failureText?.let {
            Spacer(Modifier.height(4.dp))
            Text(it, fontFamily = Mono, fontSize = 10.sp, color = Pal.red)
            Text("Repair it on the Plant screen.", fontFamily = Mono, fontSize = 9.sp,
                color = Pal.inkFaint)
        }

        Spacer(Modifier.height(7.dp))
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            Lamp("RUN", u.isRunning, Pal.green)
            Lamp("BKR", u.breakerClosed, Pal.green)
            Lamp("SMOKE", u.smokeExcess > 0.12, Pal.amber)
            Lamp("HI EGT", u.egtC > EngineBase.EGT_WARN_C, Pal.amber)
            Lamp("HI TMP", u.coolantC > EngineBase.COOLANT_WARN_C, Pal.red)
            Lamp("LO OIL", u.isRunning && u.oilPressureBar < EngineBase.OIL_PRESS_MIN_BAR, Pal.red)
            Lamp("O/L", u.kva > u.spec.ratedKVA * 1.02, Pal.red)
            Lamp("AUTO", sim.autoPlant && u.spec.autoStart, Pal.violet)
        }
    }
}

// ------------------------------------------------------------- the two meters

@Composable
private fun MainMeters(u: Genset) {
    PanelCard {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AnalogGauge(
                "Frequency", u.freqHz, 80.0, 100.0, "Hz", Modifier.weight(1f), decimals = 1,
                bands = listOf(
                    Triple(80.0, Nominal.FREQ - Nominal.FREQ_BAND, Pal.amber),
                    Triple(Nominal.FREQ - Nominal.FREQ_BAND, Nominal.FREQ + Nominal.FREQ_BAND, Pal.green),
                    Triple(Nominal.FREQ + Nominal.FREQ_BAND, 100.0, Pal.red),
                ),
                majorTicks = 5,
                subtitle = "%.0f rpm".format(u.rpm),
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

@Composable
private fun SecondaryMeters(u: Genset) {
    PanelCard {
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            AnalogGauge(
                "Volts", u.terminalVoltsPU * Nominal.GEN_VOLTS, 0.0, 600.0, "V",
                Modifier.weight(1f), compact = true, majorTicks = 6,
                bands = listOf(Triple(456.0, 504.0, Pal.green), Triple(540.0, 600.0, Pal.red)),
            )
            AnalogGauge(
                "Coolant", u.coolantC, 0.0, 120.0, "°C", Modifier.weight(1f), compact = true,
                bands = listOf(
                    Triple(EngineBase.THERMOSTAT_OPEN_C, EngineBase.COOLANT_WARN_C, Pal.green),
                    Triple(EngineBase.COOLANT_WARN_C, EngineBase.COOLANT_TRIP_C, Pal.amber),
                    Triple(EngineBase.COOLANT_TRIP_C, 120.0, Pal.red),
                ),
            )
            AnalogGauge(
                "Oil", u.oilPressureBar, 0.0, 5.0, "bar", Modifier.weight(1f),
                decimals = 1, compact = true, majorTicks = 5,
                bands = listOf(
                    Triple(0.0, EngineBase.OIL_PRESS_MIN_BAR, Pal.red),
                    Triple(EngineBase.OIL_PRESS_MIN_BAR, 1.6, Pal.amber),
                ),
            )
            if (u.spec.turbo != null) {
                AnalogGauge(
                    "Boost", u.boostBar, 0.0, u.spec.turbo!!.maxBoostBar * 1.2, "bar",
                    Modifier.weight(1f), decimals = 2, compact = true, majorTicks = 4,
                )
            } else {
                AnalogGauge(
                    "Exhaust", u.egtC, 0.0, 800.0, "°C", Modifier.weight(1f),
                    compact = true, majorTicks = 4,
                    bands = listOf(
                        Triple(EngineBase.EGT_WARN_C, EngineBase.EGT_LIMIT_C, Pal.amber),
                        Triple(EngineBase.EGT_LIMIT_C, 800.0, Pal.red),
                    ),
                )
            }
        }
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
                enabled = !u.isRunning && u.runState != RunState.FAILED &&
                    u.runState != RunState.CRANKING,
                colour = Pal.green.copy(alpha = 0.30f), textColour = Pal.greenGlow,
            ) { sim.startUnit(u.id) }
            PanelButton("STOP", Modifier.weight(1f), enabled = u.isRunning) { sim.stopUnit(u.id) }
            PanelButton("E-STOP", Modifier.weight(1f),
                colour = Pal.red.copy(alpha = 0.34f), textColour = Pal.redGlow,
            ) { sim.emergencyStop(u.id) }
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            ToggleSwitch("PRELUBE", u.prelubeRunning) { sim.setPrelube(u.id, !u.prelubeRunning) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    if (u.prelubeRunning) "Oil pumped up before cranking. Slower start, far less wear."
                    else "Dry start. Fast, and it costs the bearings.",
                    fontFamily = Mono, fontSize = 9.sp, color = Pal.inkFaint, lineHeight = 12.sp,
                )
                Spacer(Modifier.height(5.dp))
                BarMeter("Battery", u.batterySoC, "%.0f%%".format(u.batterySoC * 100),
                    if (u.batterySoC > 0.3) Pal.green else Pal.red)
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            BarMeter("Fuel rack", u.rack, "%.0f%%".format(u.rack * 100),
                if (u.smokeExcess > 0.1) Pal.amber else Pal.brass, Modifier.weight(1f))
            BarMeter("Tank", sim.fuelL / sim.plant.fuelTankL,
                "%.0f L".format(sim.fuelL),
                if (sim.fuelL / sim.plant.fuelTankL > 0.15) Pal.blue else Pal.red,
                Modifier.weight(1f))
        }
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
                    inWindow = ready,
                    live = live,
                )
            }
            Spacer(Modifier.width(9.dp))
            Column(Modifier.weight(1f)) {
                SyncRow("SLIP", "%+.2f Hz".format(check.slipHz), check.slipOk && check.directionOk)
                SyncRow("ANGLE", "%+.0f°".format(check.angleDeg), check.angleOk)
                SyncRow("VOLTS", "%+.1f%%".format(check.voltErrPct), check.voltOk)
                Spacer(Modifier.height(5.dp))
                Text(
                    when {
                        !live && u.onBus -> "On line."
                        !live -> "Start the machine first."
                        !check.voltOk -> "Match volts with the field."
                        check.slipHz < 0.02 -> "Too slow -- raise the speeder."
                        check.slipHz > 0.30 -> "Too fast -- lower the speeder."
                        !check.angleOk -> "Wait for twelve o'clock."
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
                PanelButton("FORCE", enabled = live, small = false,
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
        Box(
            Modifier.size(8.dp)
                .background(if (ok) Pal.green else Pal.red, RoundedCornerShape(4.dp)),
        )
        Spacer(Modifier.width(7.dp))
        Text(label, fontFamily = Mono, fontSize = 9.sp, color = Pal.inkDim,
            modifier = Modifier.width(44.dp))
        Text(value, fontFamily = Mono, fontSize = 12.sp,
            color = if (ok) Pal.green else Pal.amber, fontWeight = FontWeight.Bold)
    }
}

// --------------------------------------------------------- governor controls

@Composable
private fun GovernorControls(host: GameHost, u: Genset) {
    val sim = host.sim
    val isoch = u.spec.isoch && u.droop < 0.001
    PanelCard("Governor  ·  ${if (u.spec.egov) "electronic" else "flyweight"}") {
        // Big thumb keys either side of the handwheel: this is the control the
        // player touches more than any other.
        Row(verticalAlignment = Alignment.CenterVertically) {
            NudgeButton("−", big = true) { sim.adjustSpeeder(u.id, -0.0035) }
            Spacer(Modifier.width(6.dp))
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    if (u.onBus) "%.1f kW".format(u.elecKW)
                    else "%.2f Hz".format(u.speederPU * Nominal.FREQ),
                    fontFamily = Mono, fontSize = 20.sp, fontWeight = FontWeight.Bold,
                    color = Pal.brass,
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
                modifier = Modifier.width(52.dp))
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
                color = if (isoch) Pal.violet else Pal.ink, modifier = Modifier.width(48.dp))
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
                    modifier = Modifier.width(50.dp))
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
                    fontWeight = FontWeight.Bold, modifier = Modifier.width(50.dp))
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
                    fontFamily = Mono, fontSize = 9.sp, color = Pal.inkFaint)
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

// ---------------------------------------------------------- the full numbers

@Composable
private fun Detail(u: Genset) {
    var open by rememberSaveable { mutableStateOf(false) }
    PanelCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            LegendPlate("Instrumentation", Modifier.weight(1f), wide = true)
            PanelButton(if (open) "HIDE" else "SHOW", small = true) { open = !open }
        }
        if (open) {
            Spacer(Modifier.height(7.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f)) {
                    Readout("Real power", "%.1f kW".format(u.elecKW), colour = Pal.brass)
                    Readout("Reactive", "%.1f kVAr".format(u.kvar), colour = Pal.violet)
                    Readout("Apparent", "%.1f kVA".format(u.kva))
                    Readout("Power factor", "%.3f %s".format(abs(u.powerFactor),
                        if (u.kvar >= 0) "lag" else "lead"))
                    Readout("Stator current", "%.0f%% rated".format(u.ampsPU * 100),
                        colour = if (u.ampsPU > 1.0) Pal.red else Pal.ink)
                    Readout("Terminal volts", "%.0f V".format(u.terminalVoltsPU * Nominal.GEN_VOLTS))
                    Readout("Line volts", "%,.0f V".format(u.terminalVoltsPU * Nominal.LINE_VOLTS))
                    Readout("Service", "%.0f-0-%.0f V".format(
                        u.terminalVoltsPU * Nominal.SERVICE_HALF,
                        u.terminalVoltsPU * Nominal.SERVICE_HALF))
                }
                Column(Modifier.weight(1f)) {
                    Readout("Shaft power", "%.1f kW".format(u.brakeKW))
                    Readout("Fuel rate", "%.2f L/h".format(
                        u.fuelRateKgS * 3600.0 / EngineBase.FUEL_DENSITY))
                    Readout("Specific fuel",
                        if (u.elecKW > 1.0) "%.3f kg/kWh".format(u.fuelRateKgS * 3600.0 / u.elecKW)
                        else "--")
                    Readout("Air/fuel", if (u.afr < 98) "%.1f : 1".format(u.afr) else "--",
                        colour = if (u.smokeExcess > 0.05) Pal.amber else Pal.ink)
                    Readout("Exhaust", "%.0f °C".format(u.egtC))
                    Readout("Oil temp", "%.0f °C".format(u.oilC))
                    Readout("Stator temp", "%.0f °C".format(u.windingC),
                        colour = if (u.windingC > u.spec.windingLimitC) Pal.red else Pal.ink)
                    Readout("Peak cylinder", "%.0f bar".format(u.peakCylBar),
                        colour = if (u.peakCylBar > u.spec.gasketLimitBar) Pal.red else Pal.ink)
                    if (u.recoveredHeatKW > 0.01) {
                        Readout("Heat sold", "%.1f kW".format(u.recoveredHeatKW), colour = Pal.brass)
                    }
                }
            }
        }
    }
}
