package com.pointeast.app.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pointeast.app.GameHost
import com.pointeast.core.EngineBase
import com.pointeast.core.Genset
import com.pointeast.core.RunState
import com.pointeast.core.Sim

/** The selector along the top of both boards, once there is more than one set. */
@Composable
fun UnitSelector(sim: Sim) {
    if (sim.units.size <= 1) return
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(bottom = 7.dp),
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

/**
 * The nameplate and annunciator strip, carried at the top of both the
 * switchboard and the engine board so you always know which machine you are
 * looking at and whether anything is shouting.
 */
@Composable
fun UnitHeader(u: Genset, sim: Sim) {
    PanelCard(accent = if (u.onBus) Pal.green.copy(alpha = 0.7f) else Pal.panelEdge) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(u.spec.name, fontFamily = Mono, fontSize = 15.sp,
                    fontWeight = FontWeight.Bold, color = Pal.ink, maxLines = 1)
                Text(u.spec.make, fontFamily = Mono, fontSize = 9.sp, color = Pal.inkFaint,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("%.0f kW  ·  %.0f kVA".format(u.spec.ratedKW, u.spec.ratedKVA),
                    fontFamily = Mono, fontSize = 9.sp, color = Pal.inkFaint, maxLines = 1)
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
                    fontWeight = FontWeight.Bold, color = colour, letterSpacing = 1.sp,
                    maxLines = 1, softWrap = false)
                Text("%,.0f h".format(u.runHours), fontFamily = Mono, fontSize = 9.sp,
                    color = Pal.inkFaint, maxLines = 1)
            }
        }
        u.failureText?.let {
            Spacer(Modifier.height(4.dp))
            Text(it, fontFamily = Mono, fontSize = 10.sp, color = Pal.red)
            Text("Repair it under Machines.", fontFamily = Mono, fontSize = 9.sp,
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
