package com.pointeast.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pointeast.app.GameHost
import com.pointeast.core.EngineBase
import com.pointeast.core.Genset
import com.pointeast.core.RunState
import kotlin.math.abs

/**
 * The engine gauge board.
 *
 * In a real station this is not on the switchboard at all -- it is a separate
 * panel bolted to the engine, because the things on it are the engine's
 * business and not the grid's. Coolant, oil, exhaust and boost tell you
 * whether the machine is well; volts and kilowatts tell you what it is doing
 * for the city. Keeping them apart is how the job is actually organised.
 */
@Composable
fun EngineBoard(host: GameHost) {
    val sim = host.sim
    val u = sim.selectedUnit

    UnitSelector(sim)
    UnitHeader(u, sim)
    Spacer(Modifier.height(7.dp))
    StartingPanel(host, u)
    Spacer(Modifier.height(7.dp))
    EngineGauges(u)
    Spacer(Modifier.height(7.dp))
    Consumables(host, u)
    Spacer(Modifier.height(7.dp))
    Combustion(u)
}

// ------------------------------------------------------------------ starting

@Composable
private fun StartingPanel(host: GameHost, u: Genset) {
    val sim = host.sim
    val cold = u.coolantC < 15.0
    PanelCard("Starting") {
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

        Spacer(Modifier.height(9.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            ToggleSwitch("PRELUBE", u.prelubeRunning) { sim.setPrelube(u.id, !u.prelubeRunning) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                BarMeter("Battery", u.batterySoC, "%.0f%%".format(u.batterySoC * 100),
                    when {
                        u.batterySoC > 0.5 -> Pal.green
                        u.batterySoC > 0.2 -> Pal.amber
                        else -> Pal.red
                    })
                Spacer(Modifier.height(5.dp))
                PanelButton(
                    "PUT BATTERY ON CHARGE", Modifier.fillMaxWidth(), small = true,
                    enabled = !u.isRunning && u.batterySoC < 0.95,
                ) { host.say(sim.chargeBattery(u.id)) }
            }
        }

        Spacer(Modifier.height(7.dp))
        Text(
            when {
                u.runState == RunState.CRANKING && u.tooColdToFire ->
                    "Turning, but not fast enough to light. A cold block pulls the heat " +
                        "straight out of the charge."
                u.runState == RunState.CRANKING -> "Cranking."
                cold && !u.isRunning ->
                    "Block is at %.0f °C. Cold, and it will fight you: an indirect " +
                        "injection engine has only compression heat to light on. " +
                        "Prelube first, and expect to use the battery."
                            .format(u.coolantC)
                !u.isRunning -> "Block at %.0f °C. It will start.".format(u.coolantC)
                u.coolantC < 55.0 ->
                    "Running but cold. Let it come up before you put load on it."
                else -> "Warm. Ready for load."
            },
            fontFamily = Mono, fontSize = 9.sp, lineHeight = 12.sp,
            color = when {
                u.tooColdToFire -> Pal.amber
                cold && !u.isRunning -> Pal.amber
                else -> Pal.inkFaint
            },
        )
    }
}

// -------------------------------------------------------------- engine gauges

@Composable
private fun EngineGauges(u: Genset) {
    PanelCard("Engine") {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AnalogGauge(
                "Coolant", u.coolantC, -20.0, 120.0, "°C", Modifier.weight(1f),
                bands = listOf(
                    Triple(EngineBase.THERMOSTAT_OPEN_C, EngineBase.COOLANT_WARN_C, Pal.green),
                    Triple(EngineBase.COOLANT_WARN_C, EngineBase.COOLANT_TRIP_C, Pal.amber),
                    Triple(EngineBase.COOLANT_TRIP_C, 120.0, Pal.red),
                ),
                majorTicks = 7,
                subtitle = "oil %.0f °C".format(u.oilC),
            )
            AnalogGauge(
                "Oil pressure", u.oilPressureBar, 0.0, 5.0, "bar", Modifier.weight(1f),
                decimals = 1, majorTicks = 5,
                bands = listOf(
                    Triple(0.0, EngineBase.OIL_PRESS_MIN_BAR, Pal.red),
                    Triple(EngineBase.OIL_PRESS_MIN_BAR, 1.6, Pal.amber),
                ),
            )
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            AnalogGauge(
                "Exhaust", u.egtC, 0.0, 800.0, "°C", Modifier.weight(1f),
                compact = true, majorTicks = 4,
                bands = listOf(
                    Triple(EngineBase.EGT_WARN_C, EngineBase.EGT_LIMIT_C, Pal.amber),
                    Triple(EngineBase.EGT_LIMIT_C, 800.0, Pal.red),
                ),
            )
            AnalogGauge(
                "Speed", u.rpm, 0.0, 2200.0, "rpm", Modifier.weight(1f),
                compact = true, majorTicks = 4,
                bands = listOf(Triple(EngineBase.MAX_SAFE_RPM, 2200.0, Pal.red)),
            )
            if (u.spec.turbo != null) {
                AnalogGauge(
                    "Boost", u.boostBar, 0.0, u.spec.turbo!!.maxBoostBar * 1.2, "bar",
                    Modifier.weight(1f), decimals = 2, compact = true, majorTicks = 4,
                )
            } else {
                AnalogGauge(
                    "Fuel rack", u.rack * 100.0, 0.0, 100.0, "%", Modifier.weight(1f),
                    compact = true, majorTicks = 4,
                    bands = listOf(Triple(92.0, 100.0, Pal.red)),
                )
            }
            AnalogGauge(
                "Stator", u.windingC, 0.0, 200.0, "°C", Modifier.weight(1f),
                compact = true, majorTicks = 4,
                bands = listOf(
                    Triple(u.spec.windingLimitC - 25.0, u.spec.windingLimitC, Pal.amber),
                    Triple(u.spec.windingLimitC, 200.0, Pal.red),
                ),
            )
        }
    }
}

// ---------------------------------------------------------------- consumables

@Composable
private fun Consumables(host: GameHost, u: Genset) {
    val sim = host.sim
    PanelCard("Fuel") {
        BarMeter("Day tank", sim.fuelL / sim.plant.fuelTankL,
            "%,.0f / %,.0f L".format(sim.fuelL, sim.plant.fuelTankL),
            if (sim.fuelL / sim.plant.fuelTankL > 0.15) Pal.blue else Pal.red)
        Spacer(Modifier.height(4.dp))
        BarMeter("Fuel rack", u.rack, "%.0f%%".format(u.rack * 100),
            if (u.smokeExcess > 0.1) Pal.amber else Pal.brass)
        Spacer(Modifier.height(6.dp))
        Readout("Burning", "%.2f L/h".format(u.fuelRateKgS * 3600.0 / EngineBase.FUEL_DENSITY))
        Readout("Price", "%s / L".format(sim.money(sim.effectiveFuelPrice)))
        Spacer(Modifier.height(7.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            PanelButton("+200 L", Modifier.weight(1f), small = true) {
                host.say(sim.buyFuel(200.0))
            }
            PanelButton("+1000 L", Modifier.weight(1f), small = true) {
                host.say(sim.buyFuel(1000.0))
            }
            PanelButton("FILL", Modifier.weight(1f), small = true,
                colour = Pal.blue.copy(alpha = 0.25f)) { host.say(sim.fillTank()) }
        }
    }
}

// ---------------------------------------------------------------- combustion

@Composable
private fun Combustion(u: Genset) {
    PanelCard("Combustion & heat") {
        Readout("Shaft power", "%.1f kW".format(u.brakeKW))
        Readout(
            "Specific fuel",
            if (u.elecKW > 1.0) "%.3f kg/kWh".format(u.fuelRateKgS * 3600.0 / u.elecKW) else "--",
        )
        Readout("Air/fuel ratio", if (u.afr < 98) "%.1f : 1".format(u.afr) else "--",
            colour = if (u.smokeExcess > 0.05) Pal.amber else Pal.ink)
        Readout("Smoke limit", "%.1f : 1".format(u.spec.smokeAFR))
        Readout("Manifold", "%.0f °C".format(u.manifoldC))
        Readout("Peak cylinder", "%.0f of %.0f bar".format(u.peakCylBar, u.spec.gasketLimitBar),
            colour = if (u.peakCylBar > u.spec.gasketLimitBar) Pal.red else Pal.ink)
        Readout("Jacket heat", "%.1f kW".format(u.jacketHeatKW))
        Readout("Exhaust heat", "%.1f kW".format(u.exhaustHeatKW))
        if (u.recoveredHeatKW > 0.01) {
            Readout("Heat sold", "%.1f kW".format(u.recoveredHeatKW), colour = Pal.brass)
        }
        if (u.smokeExcess > 0.02) {
            Spacer(Modifier.height(5.dp))
            Text(
                "Asking for more fuel than there is air to burn. The extra is going " +
                    "out of the stack as soot and heat, not into the shaft.",
                fontFamily = Mono, fontSize = 9.sp, color = Pal.amber, lineHeight = 12.sp,
            )
        }
    }
}
