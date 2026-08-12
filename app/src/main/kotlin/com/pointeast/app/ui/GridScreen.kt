package com.pointeast.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pointeast.app.GameHost
import com.pointeast.core.Nominal
import com.pointeast.core.PointEast
import com.pointeast.core.OrderStatus
import com.pointeast.core.calendarOf
import kotlin.math.abs
import kotlin.math.max

@Composable
fun GridScreen(host: GameHost) {
    val sim = host.sim
    val snap = sim.lastSnapshot

    Column(
        Modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 10.dp)
            .padding(top = 8.dp, bottom = 16.dp),
    ) {
        // ---------------------------------------------------------- the system
        PanelCard("Dry Green City grid") {
            Row {
                Column(Modifier.weight(1f)) {
                    Readout("Frequency", "%.2f Hz".format(snap.frequencyHz),
                        colour = if (abs(snap.frequencyHz - Nominal.FREQ) < Nominal.FREQ_BAND) Pal.green else Pal.amber)
                    Readout("Generator bus", "%.0f V".format(snap.busVolts))
                    Readout("Primary line", "%,.0f V".format(snap.lineVolts))
                    Readout("Service", "%.0f-0-%.0f V".format(snap.serviceVolts / 2, snap.serviceVolts / 2))
                    Readout("Ambient", "%.1f °C".format(sim.grid.ambientC))
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Readout("Town demand", "%.0f kW".format(snap.cityDemandKW))
                    Readout("Other stations", "%.0f kW".format(snap.stationKW))
                    Readout("Your plant", "%.0f kW".format(snap.playerKW), colour = Pal.brass)
                    Readout("Spinning reserve", "%.0f kW".format(snap.reserveKW),
                        colour = if (snap.reserveKW < 40) Pal.red else Pal.green)
                    Readout("Load shed", "%.0f kW".format(snap.shedKW),
                        colour = if (snap.shedKW > 0.5) Pal.red else Pal.inkFaint)
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        // --------------------------------------------------------- Point East
        PanelCard("Point East  ·  ${PointEast.SECTOR}", accent = Pal.brass.copy(alpha = 0.7f)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text("%.0f".format(snap.pointEastConfidence * 100), fontFamily = Mono,
                    fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Pal.brass)
                Text("% built out", fontFamily = Mono, fontSize = 10.sp, color = Pal.inkFaint,
                    modifier = Modifier.padding(bottom = 4.dp, start = 2.dp))
                Spacer(Modifier.weight(1f))
                Text("%.0f kW".format(snap.pointEastDemandKW), fontFamily = Mono,
                    fontSize = 16.sp, fontWeight = FontWeight.Bold,
                    color = if (snap.pointEastShedKW > 0.5) Pal.red else Pal.ink)
            }
            Spacer(Modifier.height(4.dp))
            BarMeter(
                "Sector development", snap.pointEastConfidence,
                "%.0f of %.0f kW".format(snap.pointEastDemandKW, PointEast.DEVELOPED_KW),
                Pal.brass,
            )
            Spacer(Modifier.height(5.dp))
            val carrying = snap.playerKW >= snap.pointEastDemandKW * 0.90 &&
                snap.pointEastShedKW < 0.5 && !snap.blackout
            Text(
                when {
                    snap.pointEastShedKW > 0.5 ->
                        "Sector E is being shed. Every hour dark costs weeks of confidence."
                    carrying ->
                        "You are carrying Sector E. Somebody out there is deciding to build."
                    else ->
                        "The other stations are carrying Sector E. It stays lit, but nobody " +
                            "breaks ground on their promises -- put %.0f kW on the bus and they will."
                                .format(snap.pointEastDemandKW)
                },
                fontFamily = Mono, fontSize = 9.sp, lineHeight = 12.sp,
                color = when {
                    snap.pointEastShedKW > 0.5 -> Pal.red
                    carrying -> Pal.green
                    else -> Pal.inkDim
                },
            )
            Spacer(Modifier.height(4.dp))
            Readout("Hours carried by you", "%,.0f h".format(sim.grid.pointEastLitHours))
            Readout("Hours dark", "%,.0f h".format(sim.grid.pointEastDarkHours),
                colour = if (sim.grid.pointEastDarkHours > 1) Pal.amber else Pal.inkFaint)
        }

        Spacer(Modifier.height(8.dp))

        // ------------------------------------------------------- the load curve
        PanelCard("Last 24 hours") {
            val total = host.traceTotal
            val mine = host.traceMine
            if (total.size < 3) {
                Box(Modifier.fillMaxWidth().height(90.dp), contentAlignment = Alignment.Center) {
                    Text("Collecting...", fontFamily = Mono, fontSize = 10.sp, color = Pal.inkFaint)
                }
            } else {
                val peak = max(total.maxOrNull() ?: 1f, 1f) * 1.15f
                LoadTrace(total, mine, peak, Modifier.fillMaxWidth().height(96.dp))
                Row(Modifier.fillMaxWidth().padding(top = 3.dp),
                    horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("24 h ago", fontFamily = Mono, fontSize = 8.sp, color = Pal.inkFaint)
                    Text("peak %.0f kW".format(peak / 1.15f), fontFamily = Mono, fontSize = 8.sp, color = Pal.blue)
                    Text("now", fontFamily = Mono, fontSize = 8.sp, color = Pal.inkFaint)
                }
                Row(Modifier.padding(top = 2.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    LegendDot("Town demand", Pal.blue)
                    LegendDot("Your output", Pal.brass)
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        // ------------------------------------------------------------ dispatch
        val offered = sim.campaign.offeredOrder
        val active = sim.campaign.activeOrder
        if (offered != null || active != null) {
            val order = offered ?: active!!
            val isOffer = offered != null
            PanelCard(
                if (isOffer) "Dispatcher on the line" else "Dispatch order running",
                accent = if (isOffer) Pal.amber else Pal.green,
            ) {
                Text("\"${order.reason}.\"", fontSize = 12.sp, color = Pal.ink)
                Spacer(Modifier.height(4.dp))
                Readout("Wanted", "%.0f kW".format(order.targetKW), colour = Pal.brass)
                Readout("Window", "%s to %s".format(
                    calendarOf(order.startAt).let { "${it.monthName} ${it.day} ${it.clock}" },
                    calendarOf(order.endAt).clock))
                Readout("Standby fee", sim.money(order.standbyFee), colour = Pal.brass)
                if (!isOffer) {
                    Spacer(Modifier.height(4.dp))
                    BarMeter(
                        "Compliance", order.complianceFrac,
                        "%.0f%%".format(order.complianceFrac * 100),
                        if (order.complianceFrac > 0.9) Pal.green else Pal.amber,
                    )
                    Text(
                        if (snap.playerKW >= order.targetKW * 0.95) "Meeting it right now."
                        else "You are %.0f kW short.".format(order.targetKW - snap.playerKW),
                        fontFamily = Mono, fontSize = 9.sp,
                        color = if (snap.playerKW >= order.targetKW * 0.95) Pal.green else Pal.amber,
                    )
                }
                if (isOffer) {
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        PanelButton("ACCEPT", Modifier.weight(1f),
                            colour = Pal.green.copy(alpha = 0.25f), textColour = Pal.greenGlow) {
                            sim.acceptOrder(order.id)
                        }
                        PanelButton("DECLINE", Modifier.weight(1f)) { sim.declineOrder(order.id) }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }

        // -------------------------------------------------------- grid authority units
        PanelCard("The other seven stations") {
            for (u in sim.grid.stations) {
                val colour = when {
                    u.failed -> Pal.red
                    u.starting -> Pal.amber
                    u.online -> Pal.green
                    else -> Pal.inkFaint
                }
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier
                            .width(7.dp).height(20.dp)
                            .background(colour, RoundedCornerShape(2.dp)),
                    )
                    Spacer(Modifier.width(7.dp))
                    Column(Modifier.weight(1f)) {
                        Text(u.spec.company, fontFamily = Mono, fontSize = 11.sp, color = Pal.ink)
                        Text("${u.spec.sector}  ·  ${u.spec.make}", fontFamily = Mono, fontSize = 8.sp, color = Pal.inkFaint)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            when {
                                u.failed -> "DOWN"
                                u.starting -> "starting %.0fs".format(u.startTimer)
                                u.online -> "%.0f kW".format(u.outputKW)
                                else -> "standby"
                            },
                            fontFamily = Mono, fontSize = 11.sp, color = colour,
                            fontWeight = FontWeight.Bold,
                        )
                        Text("%.0f kW rated".format(u.spec.kW),
                            fontFamily = Mono, fontSize = 8.sp, color = Pal.inkFaint)
                    }
                    if (u.online) {
                        Spacer(Modifier.width(8.dp))
                        Box(Modifier.width(48.dp)) {
                            BarMeter("", (u.outputKW / u.spec.kW), "", Pal.green)
                        }
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            Readout("Their combined capacity", "%.0f kW".format(sim.grid.cityStationCapacityKW()))
            Readout("Your installed", "%.0f kW".format(sim.installedKW), colour = Pal.brass)
        }

        Spacer(Modifier.height(8.dp))

        // ------------------------------------------------------------- events
        if (sim.grid.events.isNotEmpty()) {
            PanelCard("On the system right now") {
                for (e in sim.grid.events.take(8)) {
                    Readout(e.name, "%.0f kW  %.0f kVAr".format(e.currentKW(), e.currentKvar()))
                }
            }
            Spacer(Modifier.height(8.dp))
        }

        // -------------------------------------------------------- order history
        val past = sim.campaign.orders.filter {
            it.status != OrderStatus.OFFERED && it.status != OrderStatus.ACTIVE
        }.takeLast(6).reversed()
        if (past.isNotEmpty()) {
            PanelCard("Recent dispatch") {
                for (o in past) {
                    val c = when (o.status) {
                        OrderStatus.COMPLETED -> Pal.green
                        OrderStatus.FAILED, OrderStatus.EXPIRED -> Pal.red
                        else -> Pal.inkFaint
                    }
                    Readout(
                        "%s  %.0f kW".format(calendarOf(o.startAt).let { "${it.monthName} ${it.day}" }, o.targetKW),
                        o.status.name.lowercase(), colour = c,
                    )
                }
                Readout("Answered / missed",
                    "${sim.campaign.ordersAnswered} / ${sim.campaign.ordersMissed}")
            }
        }
    }
}

@Composable
private fun LegendDot(label: String, colour: androidx.compose.ui.graphics.Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(9.dp).height(9.dp).background(colour, RoundedCornerShape(2.dp)))
        Spacer(Modifier.width(4.dp))
        Text(label, fontFamily = Mono, fontSize = 8.sp, color = Pal.inkDim)
    }
}
