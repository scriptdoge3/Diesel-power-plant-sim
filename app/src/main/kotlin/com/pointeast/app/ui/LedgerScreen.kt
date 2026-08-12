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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pointeast.app.GameHost
import com.pointeast.core.LogLevel
import com.pointeast.core.MILESTONES
import com.pointeast.core.calendarOf

// ------------------------------------------------------------------ career

@Composable
fun CareerTab(host: GameHost) {
    val sim = host.sim
    val context = LocalContext.current
    val done = sim.campaign.completed

    PanelCard("The first megawatt", accent = Pal.brass) {
        BarMeter(
            "Milestones", sim.campaign.progressFraction(),
            "${done.size} of ${MILESTONES.size}", Pal.brass,
        )
        Spacer(Modifier.height(8.dp))
        Readout("Energy delivered", "%,.0f kWh".format(sim.campaign.totalDeliveredKWh))
        Readout("Peak output", "%.0f kW".format(sim.campaign.peakDeliveredKW))
        Readout("Installed capacity", "%.0f kW".format(sim.installedKW), colour = Pal.brass)
        Readout("Point East built", "%.0f %%".format(sim.grid.pointEastConfidence * 100),
            colour = Pal.brass)
        Readout("Reputation", "%.2f".format(sim.campaign.reputation),
            colour = if (sim.campaign.reputation > 0.6) Pal.green else Pal.amber)
        Readout("Tariff", "%s / kWh".format(sim.money(sim.ratePerKWh)))
        Readout("Days elapsed", "%.0f".format(sim.gameSeconds / 86400.0))
    }

    Spacer(Modifier.height(8.dp))

    for (m in MILESTONES) {
        val complete = m.id in done
        val isNext = !complete && sim.campaign.nextMilestone?.id == m.id
        Row(
            Modifier
                .fillMaxWidth()
                .padding(bottom = 6.dp)
                .background(
                    if (complete) Pal.green.copy(alpha = 0.07f)
                    else if (isNext) Pal.brass.copy(alpha = 0.09f) else Pal.panel,
                    RoundedCornerShape(8.dp),
                )
                .padding(10.dp),
        ) {
            Box(
                Modifier.size(20.dp).background(
                    if (complete) Pal.green else if (isNext) Pal.brass else Pal.panelHigh,
                    CircleShape,
                ),
                contentAlignment = Alignment.Center,
            ) {
                Text(if (complete) "✓" else "", fontFamily = Mono, fontSize = 11.sp, color = Pal.bg)
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    m.title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                    color = if (complete) Pal.green else if (isNext) Pal.brass else Pal.inkDim,
                )
                Text(m.subtitle, fontSize = 11.sp, color = Pal.inkFaint, lineHeight = 15.sp)
            }
            if (m.reward > 0 && !complete) {
                Text(sim.money(m.reward), fontFamily = Mono, fontSize = 10.sp, color = Pal.inkFaint)
            }
        }
    }

    Spacer(Modifier.height(10.dp))
    PanelCard("Game") {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            PanelButton("SAVE NOW", Modifier.weight(1f)) {
                host.save(context)
                host.say("Saved")
            }
            PanelButton("RESTART", Modifier.weight(1f), colour = Pal.red.copy(alpha = 0.18f),
                textColour = Pal.red) {
                host.newGame(context)
                host.say("New career started")
            }
        }
        Text("The game saves itself every game-hour and whenever you leave the app.",
            fontFamily = Mono, fontSize = 9.sp, color = Pal.inkFaint,
            modifier = Modifier.padding(top = 5.dp))
    }
}

// ------------------------------------------------------------------ ledger

@Composable
fun LedgerTab(host: GameHost) {
    val sim = host.sim
    val recent = sim.days.takeLast(14)

    PanelCard("Trading") {
        val today = sim.days.lastOrNull()
        if (today != null) {
            Readout("Revenue today", sim.money(today.revenue), colour = Pal.green)
            Readout("Fuel burnt today", sim.money(-today.fuelCost), colour = Pal.red)
            Readout("Maintenance today", sim.money(-today.maintenance), colour = Pal.red)
            Readout("Capital today", sim.money(-today.capital), colour = Pal.red)
            Readout("Net today", sim.money(today.net),
                colour = if (today.net >= 0) Pal.green else Pal.red)
            Readout("Energy today", "%.0f kWh".format(today.kWh))
            Readout("Machine hours today", "%.1f h".format(today.runHours))
        }
        Spacer(Modifier.height(6.dp))
        val totalRev = sim.days.sumOf { it.revenue }
        val totalFuel = sim.days.sumOf { it.fuelCost }
        val totalMaint = sim.days.sumOf { it.maintenance }
        val totalCap = sim.days.sumOf { it.capital }
        Readout("Lifetime revenue", sim.money(totalRev))
        Readout("Lifetime fuel", sim.money(-totalFuel))
        Readout("Lifetime maintenance", sim.money(-totalMaint))
        Readout("Lifetime capital", sim.money(-totalCap))
        Readout("Lifetime net", sim.money(totalRev - totalFuel - totalMaint - totalCap),
            colour = if (totalRev - totalFuel - totalMaint - totalCap >= 0) Pal.green else Pal.red)
    }

    Spacer(Modifier.height(8.dp))

    if (recent.size >= 2) {
        PanelCard("Daily net, last ${recent.size} days") {
            val peak = recent.maxOf { kotlin.math.abs(it.net) }.coerceAtLeast(1.0)
            Row(
                Modifier.fillMaxWidth().height(70.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                for (d in recent) {
                    val frac = (kotlin.math.abs(d.net) / peak).coerceIn(0.02, 1.0)
                    Column(
                        Modifier.weight(1f).height(70.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height((frac * 60).dp)
                                .background(
                                    if (d.net >= 0) Pal.green else Pal.red,
                                    RoundedCornerShape(2.dp),
                                ),
                        )
                    }
                }
            }
            Text(
                "best %s   worst %s".format(
                    sim.money(recent.maxOf { it.net }), sim.money(recent.minOf { it.net })),
                fontFamily = Mono, fontSize = 9.sp, color = Pal.inkFaint,
            )
        }
        Spacer(Modifier.height(8.dp))
    }

    PanelCard("Entries") {
        val entries = sim.ledger.toList().takeLast(60).reversed()
        if (entries.isEmpty()) {
            Text("Nothing yet.", fontFamily = Mono, fontSize = 10.sp, color = Pal.inkFaint)
        }
        for (e in entries) {
            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                Text(
                    calendarOf(e.gameSeconds).let { "${it.monthName} ${it.day}" },
                    fontFamily = Mono, fontSize = 9.sp, color = Pal.inkFaint,
                    modifier = Modifier.width(46.dp),
                )
                Text(e.text, fontFamily = Mono, fontSize = 10.sp, color = Pal.inkDim,
                    modifier = Modifier.weight(1f))
                Text(sim.money(e.amount), fontFamily = Mono, fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (e.amount >= 0) Pal.green else Pal.red)
            }
        }
    }
}

// --------------------------------------------------------------------- log

@Composable
fun LogTab(host: GameHost) {
    val sim = host.sim
    PanelCard("Station log") {
        val entries = sim.log.toList().reversed()
        if (entries.isEmpty()) {
            Text("Nothing logged yet.", fontFamily = Mono, fontSize = 10.sp, color = Pal.inkFaint)
        }
        for (e in entries) {
            val colour = when (e.level) {
                LogLevel.GOOD -> Pal.green
                LogLevel.WARN -> Pal.amber
                LogLevel.ALARM -> Pal.red
                LogLevel.INFO -> Pal.inkDim
            }
            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                Text(e.stamp, fontFamily = Mono, fontSize = 9.sp, color = Pal.inkFaint,
                    modifier = Modifier.width(76.dp))
                Text(e.text, fontFamily = Mono, fontSize = 10.sp, color = colour,
                    lineHeight = 14.sp, modifier = Modifier.weight(1f))
            }
        }
    }
}
