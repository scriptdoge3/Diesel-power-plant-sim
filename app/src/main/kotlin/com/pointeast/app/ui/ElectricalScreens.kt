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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pointeast.app.GameHost
import com.pointeast.core.Nominal
import com.pointeast.core.OrderStatus
import com.pointeast.core.PointEast
import com.pointeast.core.calendarOf
import kotlin.math.abs
import kotlin.math.max

/* ============================================================================
 *  The ELECTRICAL board: the system you sell into.
 *
 *  Split into three because a phone cannot hold the whole grid on one screen
 *  without becoming a scroll nobody reads to the bottom of.
 * ========================================================================== */

// ------------------------------------------------------------------- the grid

@Composable
fun GridSystemTab(host: GameHost) {
    val sim = host.sim
    val snap = sim.lastSnapshot

    PanelCard("Dry Green City grid") {
        Readout("Frequency", "%.2f Hz".format(snap.frequencyHz),
            colour = if (abs(snap.frequencyHz - Nominal.FREQ) < Nominal.FREQ_BAND) Pal.green
            else Pal.amber)
        Readout("Generator bus", "%.0f V".format(snap.busVolts))
        Readout("Primary line", "%,.0f V".format(snap.lineVolts))
        Readout("Service", "%.0f-0-%.0f V".format(snap.serviceVolts / 2, snap.serviceVolts / 2))
        Readout("Ambient", "%.1f °C".format(sim.grid.ambientC))
        Spacer(Modifier.height(6.dp))
        Readout("City demand", "%.0f kW".format(snap.cityDemandKW))
        Readout("Other stations", "%.0f kW".format(snap.stationKW))
        Readout("Your plant", "%.0f kW".format(snap.playerKW), colour = Pal.brass)
        Readout("Spinning reserve", "%.0f kW".format(snap.reserveKW),
            colour = if (snap.reserveKW < 40) Pal.red else Pal.green)
        Readout("Load shed", "%.0f kW".format(snap.shedKW),
            colour = if (snap.shedKW > 0.5) Pal.red else Pal.inkFaint)
    }

    Spacer(Modifier.height(8.dp))

    PanelCard("Last 24 hours") {
        val total = host.traceTotal
        val mine = host.traceMine
        if (total.size < 3) {
            Box(Modifier.fillMaxWidth().height(84.dp), contentAlignment = Alignment.Center) {
                Text("Chart recorder warming up...", fontFamily = Mono, fontSize = 10.sp,
                    color = Pal.inkFaint)
            }
        } else {
            val peak = max(total.maxOrNull() ?: 1f, 1f) * 1.15f
            LoadTrace(total, mine, peak, Modifier.fillMaxWidth().height(96.dp))
            Row(Modifier.fillMaxWidth().padding(top = 3.dp),
                horizontalArrangement = Arrangement.SpaceBetween) {
                Text("24 h ago", fontFamily = Mono, fontSize = 8.sp, color = Pal.inkFaint)
                Text("peak %.0f kW".format(peak / 1.15f), fontFamily = Mono, fontSize = 8.sp,
                    color = Pal.ink)
                Text("now", fontFamily = Mono, fontSize = 8.sp, color = Pal.inkFaint)
            }
            Row(Modifier.padding(top = 3.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                LegendDot("City demand", Pal.dialInkFaint)
                LegendDot("Your output", Pal.brass)
            }
        }
    }

    Spacer(Modifier.height(8.dp))

    PanelCard("The other seven stations") {
        for (u in sim.grid.stations) {
            val colour = when {
                u.failed -> Pal.red
                u.starting -> Pal.amber
                u.online -> Pal.green
                else -> Pal.inkFaint
            }
            Row(
                Modifier.fillMaxWidth().padding(vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.width(6.dp).height(24.dp)
                    .background(colour, RoundedCornerShape(1.dp)))
                Spacer(Modifier.width(7.dp))
                Column(Modifier.weight(1f)) {
                    Text(u.spec.company, fontFamily = Mono, fontSize = 11.sp, color = Pal.ink,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${u.spec.sector} · %.0f kW".format(u.spec.kW),
                        fontFamily = Mono, fontSize = 8.sp, color = Pal.inkFaint, maxLines = 1)
                }
                Spacer(Modifier.width(6.dp))
                Text(
                    when {
                        u.failed -> "DOWN"
                        u.starting -> "start"
                        u.online -> "%.0f kW".format(u.outputKW)
                        else -> "standby"
                    },
                    fontFamily = Mono, fontSize = 11.sp, color = colour,
                    fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false,
                )
            }
        }
        Spacer(Modifier.height(5.dp))
        Readout("Their capacity", "%.0f kW".format(sim.grid.cityStationCapacityKW()))
        Readout("Yours", "%.0f kW".format(sim.installedKW), colour = Pal.brass)
    }
}

// ------------------------------------------------------------------ sector E

@Composable
fun PointEastTab(host: GameHost) {
    val sim = host.sim
    val snap = sim.lastSnapshot
    val carrying = snap.playerKW >= snap.pointEastDemandKW * 0.90 &&
        snap.pointEastShedKW < 0.5 && !snap.blackout

    PanelCard("Point East  ·  ${PointEast.SECTOR}", accent = Pal.brass.copy(alpha = 0.7f)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text("%.0f".format(snap.pointEastConfidence * 100), fontFamily = Mono,
                fontSize = 30.sp, fontWeight = FontWeight.Bold, color = Pal.brass)
            Text("% built out", fontFamily = Mono, fontSize = 10.sp, color = Pal.inkFaint,
                modifier = Modifier.padding(bottom = 5.dp, start = 3.dp))
            Spacer(Modifier.weight(1f))
            Text("%.0f kW".format(snap.pointEastDemandKW), fontFamily = Mono,
                fontSize = 18.sp, fontWeight = FontWeight.Bold,
                color = if (snap.pointEastShedKW > 0.5) Pal.red else Pal.ink)
        }
        Spacer(Modifier.height(5.dp))
        BarMeter("Sector development", snap.pointEastConfidence,
            "%.0f of %.0f kW".format(snap.pointEastDemandKW, PointEast.DEVELOPED_KW), Pal.brass)
        Spacer(Modifier.height(7.dp))
        Text(
            when {
                snap.pointEastShedKW > 0.5 ->
                    "Sector E is being shed right now. Every hour dark costs about a day " +
                        "of confidence -- this is the fastest way to lose ground you have earned."
                carrying ->
                    "You are carrying Sector E. Somebody out there is deciding to build, and " +
                        "every kilowatt they add is yours to sell."
                else ->
                    "The other stations are carrying Sector E. It stays lit, but nobody " +
                        "breaks ground on their promises. Put %.0f kW on the bus and that changes."
                            .format(snap.pointEastDemandKW)
            },
            fontFamily = Mono, fontSize = 10.sp, lineHeight = 14.sp,
            color = when {
                snap.pointEastShedKW > 0.5 -> Pal.red
                carrying -> Pal.green
                else -> Pal.inkDim
            },
        )
    }

    Spacer(Modifier.height(8.dp))

    PanelCard("The record") {
        Readout("Hours carried by you", "%,.0f h".format(sim.grid.pointEastLitHours))
        Readout("Hours dark", "%,.0f h".format(sim.grid.pointEastDarkHours),
            colour = if (sim.grid.pointEastDarkHours > 1) Pal.amber else Pal.inkFaint)
        Readout("Load today", "%.0f kW".format(snap.pointEastDemandKW))
        Readout("Shed right now", "%.0f kW".format(snap.pointEastShedKW),
            colour = if (snap.pointEastShedKW > 0.5) Pal.red else Pal.inkFaint)
        Readout("If fully built", "%.0f kW".format(PointEast.DEVELOPED_KW), colour = Pal.brass)
    }

    Spacer(Modifier.height(8.dp))

    PanelCard("Why this is the whole business") {
        Text(
            "The dirt out here is dry and dead, so developers will not build. Because " +
                "nobody built, nobody ran decent copper. Because the copper is thin, the " +
                "sector browns out. And because it browns out, developers will not build.\n\n" +
                "That circle runs the other way too. Sector E is the only load in Dry Green " +
                "City that grows because of what you personally do — and all of it is load " +
                "the other seven stations never bothered to want.",
            fontSize = 11.sp, color = Pal.inkDim, lineHeight = 16.sp,
        )
    }
}

// ------------------------------------------------------------------ dispatch

@Composable
fun DispatchTab(host: GameHost) {
    val sim = host.sim
    val snap = sim.lastSnapshot
    val offered = sim.campaign.offeredOrder
    val active = sim.campaign.activeOrder

    if (offered != null || active != null) {
        val order = offered ?: active!!
        val isOffer = offered != null
        PanelCard(
            if (isOffer) "The dispatcher is on the line" else "Order running",
            accent = if (isOffer) Pal.amber else Pal.green,
        ) {
            Text("\"${order.reason}.\"", fontSize = 13.sp, color = Pal.ink, lineHeight = 18.sp)
            Spacer(Modifier.height(6.dp))
            Readout("Wanted", "%.0f kW".format(order.targetKW), colour = Pal.brass)
            Readout("From", calendarOf(order.startAt).let {
                "${it.monthName} ${it.day} ${it.clock}"
            })
            Readout("Until", calendarOf(order.endAt).let {
                "${it.monthName} ${it.day} ${it.clock}"
            })
            Readout("Standby fee", sim.money(order.standbyFee), colour = Pal.brass)
            if (!isOffer) {
                Spacer(Modifier.height(6.dp))
                BarMeter("Compliance", order.complianceFrac,
                    "%.0f%%".format(order.complianceFrac * 100),
                    if (order.complianceFrac > 0.9) Pal.green else Pal.amber)
                Spacer(Modifier.height(4.dp))
                Text(
                    if (snap.playerKW >= order.targetKW * 0.95) "Meeting it."
                    else "Short by %.0f kW.".format(order.targetKW - snap.playerKW),
                    fontFamily = Mono, fontSize = 10.sp,
                    color = if (snap.playerKW >= order.targetKW * 0.95) Pal.green else Pal.amber,
                )
            }
            if (isOffer) {
                Spacer(Modifier.height(9.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    PanelButton("ACCEPT", Modifier.weight(1f),
                        colour = Pal.green.copy(alpha = 0.30f), textColour = Pal.greenGlow) {
                        sim.acceptOrder(order.id)
                    }
                    PanelButton("DECLINE", Modifier.weight(1f)) { sim.declineOrder(order.id) }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    } else {
        PanelCard("No orders") {
            Text(
                "Nothing on the wire. The grid authority calls when a station goes down or " +
                    "the forecast turns, and answering is most of how your reputation gets " +
                    "built.",
                fontSize = 12.sp, color = Pal.inkDim, lineHeight = 16.sp,
            )
        }
        Spacer(Modifier.height(8.dp))
    }

    PanelCard("Standing") {
        Readout("Reputation", "%.2f".format(sim.campaign.reputation),
            colour = if (sim.campaign.reputation > 0.6) Pal.green else Pal.amber)
        Readout("Tariff", "%s / kWh".format(sim.money(sim.ratePerKWh)))
        Readout("Orders answered", "${sim.campaign.ordersAnswered}", colour = Pal.green)
        Readout("Orders missed", "${sim.campaign.ordersMissed}",
            colour = if (sim.campaign.ordersMissed > 0) Pal.amber else Pal.inkFaint)
        if (sim.campaign.baseloadContract) {
            Readout("Baseload contract", "held", colour = Pal.brass)
        }
    }

    val past = sim.campaign.orders.filter {
        it.status != OrderStatus.OFFERED && it.status != OrderStatus.ACTIVE
    }.takeLast(10).reversed()
    if (past.isNotEmpty()) {
        Spacer(Modifier.height(8.dp))
        PanelCard("Recent") {
            for (o in past) {
                Readout(
                    "%s  ·  %.0f kW".format(
                        calendarOf(o.startAt).let { "${it.monthName} ${it.day}" }, o.targetKW),
                    o.status.name.lowercase(),
                    colour = when (o.status) {
                        OrderStatus.COMPLETED -> Pal.green
                        OrderStatus.FAILED, OrderStatus.EXPIRED -> Pal.red
                        else -> Pal.inkFaint
                    },
                )
            }
        }
    }
}

@Composable
private fun LegendDot(label: String, colour: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(9.dp).height(9.dp).background(colour, RoundedCornerShape(1.dp)))
        Spacer(Modifier.width(4.dp))
        Text(label, fontFamily = Mono, fontSize = 8.sp, color = Pal.inkDim, maxLines = 1)
    }
}
