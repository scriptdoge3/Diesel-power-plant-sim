package com.pointeast.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
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
import com.pointeast.core.CONSUMABLES
import com.pointeast.core.Genset
import com.pointeast.core.MEGAWATT_KW
import com.pointeast.core.REPAIRS
import com.pointeast.core.RunState
import com.pointeast.core.Sim

private enum class PlantTab { PLANT, MACHINES, MARKET }

@Composable
fun PlantScreen(host: GameHost) {
    val sim = host.sim
    var tab by rememberSaveable { mutableStateOf(PlantTab.PLANT) }

    Column(Modifier.padding(horizontal = 10.dp).padding(top = 8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Chip("Plant", tab == PlantTab.PLANT) { tab = PlantTab.PLANT }
            Chip("Machines", tab == PlantTab.MACHINES) { tab = PlantTab.MACHINES }
            Chip("Market", tab == PlantTab.MARKET, Pal.brass) { tab = PlantTab.MARKET }
        }
        Spacer(Modifier.height(8.dp))
        Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = 16.dp)) {
            when (tab) {
                PlantTab.PLANT -> PlantOverview(host)
                PlantTab.MACHINES -> MachineList(host)
                PlantTab.MARKET -> MarketList(host)
            }
        }
    }
}

// ------------------------------------------------------------------- plant

@Composable
private fun PlantOverview(host: GameHost) {
    val sim = host.sim
    val p = sim.plant

    PanelCard("Toward the megawatt", accent = Pal.brass) {
        val frac = (sim.installedKW / MEGAWATT_KW).coerceIn(0.0, 1.0)
        Row(verticalAlignment = Alignment.Bottom) {
            Text("%,.0f".format(sim.installedKW), fontFamily = Mono, fontSize = 30.sp,
                fontWeight = FontWeight.Bold, color = Pal.brass)
            Text(" kW of %,.0f".format(MEGAWATT_KW), fontFamily = Mono, fontSize = 12.sp,
                color = Pal.inkFaint, modifier = Modifier.padding(bottom = 5.dp))
        }
        Spacer(Modifier.height(4.dp))
        BarMeter("Installed capacity", frac, "%.0f%%".format(frac * 100), Pal.brass)
        Spacer(Modifier.height(6.dp))
        Readout("Units owned", "${sim.units.size} of ${p.unitSlots} slots")
        Readout("Largest unit", "%.0f kW".format(sim.largestUnitKW))
        Readout("Capacity less largest (N-1)", "%.0f kW".format(sim.n1CapacityKW),
            colour = if (sim.n1CapacityKW >= sim.lastSnapshot.cityDemandKW) Pal.green else Pal.amber)
        Readout("Per-unit ceiling here", "%.0f kW".format(p.maxUnitKW))
        Readout("N-1 certified", if (p.n1Certified) "yes" else "no",
            colour = if (p.n1Certified) Pal.green else Pal.inkFaint)
        sim.campaign.nextMilestone?.let {
            Spacer(Modifier.height(6.dp))
            Text("NEXT: ${it.title}", fontFamily = Mono, fontSize = 10.sp,
                color = Pal.brass, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
            Text(it.subtitle, fontSize = 11.sp, color = Pal.inkDim, lineHeight = 15.sp)
        }
    }

    Spacer(Modifier.height(8.dp))

    PanelCard("Infrastructure") {
        InfraRow("Paralleling switchgear", p.hasSwitchgear, "Lets you own more than one machine")
        InfraRow("Powerhouse building", p.hasPowerhouse, "Four unit slots, machines out of the weather")
        InfraRow("480 V bus & revenue metering", p.hasOwnBus, "Metered properly, better rate")
        InfraRow("Step-up bank & feeder", p.hasStepUp,
            if (p.hasStepUp) "No wheeling fee" else "Co-op keeps %.0f%% of what you sell".format(p.wheelingFrac * 100))
        InfraRow("Bulk fuel farm", p.hasFuelFarm,
            if (p.hasFuelFarm) "Barge pricing" else "Retail fuel pricing")
        InfraRow("Central control room", p.hasControlRoom, "Command every unit from one board")
        InfraRow("Medium-speed engine hall", p.hasEngineHall, "Six slots, 700 kW per unit")
        InfraRow("N-1 certification", p.n1Certified, "Required for the baseload contract")
        Spacer(Modifier.height(4.dp))
        Text("Buy these on the Upgrades screen, under The Plant.",
            fontFamily = Mono, fontSize = 9.sp, color = Pal.inkFaint)
    }

    Spacer(Modifier.height(8.dp))

    PanelCard("Fuel") {
        BarMeter(
            "Tank", sim.fuelL / p.fuelTankL,
            "%,.0f / %,.0f L".format(sim.fuelL, p.fuelTankL),
            if (sim.fuelL / p.fuelTankL > 0.15) Pal.blue else Pal.red,
        )
        Spacer(Modifier.height(6.dp))
        Readout("Price today", "%s / L".format(sim.money(sim.effectiveFuelPrice)))
        Readout("Cost to fill", sim.money((p.fuelTankL - sim.fuelL) * sim.effectiveFuelPrice))
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            PanelButton("+200 L", Modifier.weight(1f)) { host.say(sim.buyFuel(200.0)) }
            PanelButton("+1000 L", Modifier.weight(1f)) { host.say(sim.buyFuel(1000.0)) }
            PanelButton("FILL", Modifier.weight(1f), colour = Pal.blue.copy(alpha = 0.25f),
                textColour = Pal.blue) { host.say(sim.fillTank()) }
        }
    }

    if (sim.plant.scada) {
        Spacer(Modifier.height(8.dp))
        PanelCard("Automation", accent = Pal.violet) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Chip(if (sim.autoPlant) "AUTO ON" else "AUTO OFF", sim.autoPlant, Pal.violet) {
                    sim.autoPlant = !sim.autoPlant
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    "The plant answers dispatch by itself: starts, synchronises and loads.",
                    fontFamily = Mono, fontSize = 9.sp, color = Pal.inkFaint,
                )
            }
        }
    }
}

@Composable
private fun InfraRow(name: String, have: Boolean, note: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(7.dp).height(14.dp)
            .background(if (have) Pal.green else Pal.panelHigh, RoundedCornerShape(2.dp)))
        Spacer(Modifier.width(7.dp))
        Column(Modifier.weight(1f)) {
            Text(name, fontFamily = Mono, fontSize = 11.sp,
                color = if (have) Pal.ink else Pal.inkFaint)
            Text(note, fontFamily = Mono, fontSize = 8.sp, color = Pal.inkFaint)
        }
    }
}

// ---------------------------------------------------------------- machines

@Composable
private fun MachineList(host: GameHost) {
    val sim = host.sim
    for (u in sim.units) {
        MachineCard(host, sim, u)
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun MachineCard(host: GameHost, sim: Sim, u: Genset) {
    var expanded by rememberSaveable(u.id) { mutableStateOf(u.isFoundingSet) }
    val worst = u.wear.worst()

    PanelCard(accent = if (u.runState == RunState.FAILED) Pal.red else Pal.panelEdge) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(u.spec.name, fontFamily = Mono, fontSize = 13.sp,
                    fontWeight = FontWeight.Bold, color = Pal.ink)
                Text("${u.spec.make} · %.0f kW · %,.0f h".format(u.spec.ratedKW, u.runHours),
                    fontFamily = Mono, fontSize = 9.sp, color = Pal.inkFaint)
            }
            PanelButton(if (expanded) "HIDE" else "OPEN", small = true) { expanded = !expanded }
        }

        Spacer(Modifier.height(6.dp))
        BarMeter(
            "Worst component: ${worst.first}", worst.second,
            "%.0f%% worn".format(worst.second * 100),
            when {
                worst.second > 0.8 -> Pal.red
                worst.second > 0.55 -> Pal.amber
                else -> Pal.green
            },
        )

        if (expanded) {
            Spacer(Modifier.height(8.dp))
            Text("CONDITION", fontFamily = Mono, fontSize = 9.sp, letterSpacing = 1.sp, color = Pal.inkDim)
            Spacer(Modifier.height(3.dp))
            WearRow("Main & rod bearings", u.wear.bearings)
            WearRow("Rings & liners", u.wear.rings)
            WearRow("Injectors", u.wear.injectors)
            WearRow("Head gasket", u.wear.gasket)
            if (u.spec.turbo != null) WearRow("Turbocharger", u.wear.turbo)
            WearRow("Alternator windings", u.wear.alternator)
            WearRow("Governor linkage", u.wear.governor)
            Spacer(Modifier.height(3.dp))
            WearRow("Air filter", u.airFilterFouling, "fouled")
            WearRow("Fuel filter", u.fuelFilterFouling, "fouled")
            WearRow("Radiator core", u.radiatorFouling, "fouled")
            WearRow("Oil condition", u.oilCondition, "degraded")

            Spacer(Modifier.height(8.dp))
            Text("SCHEDULED SERVICE", fontFamily = Mono, fontSize = 9.sp,
                letterSpacing = 1.sp, color = Pal.inkDim)
            Spacer(Modifier.height(3.dp))
            for (item in CONSUMABLES) {
                val hours = u.service.get(item.key)
                val overdue = hours > item.intervalH
                Row(Modifier.fillMaxWidth().padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(item.name, fontFamily = Mono, fontSize = 10.sp,
                            color = if (overdue) Pal.amber else Pal.ink)
                        Text(
                            "%.0f h of %.0f  ·  %.1f h work".format(hours, item.intervalH, item.hours),
                            fontFamily = Mono, fontSize = 8.sp,
                            color = if (overdue) Pal.amber else Pal.inkFaint,
                        )
                    }
                    PanelButton(
                        sim.money(item.cost), small = true,
                        enabled = !u.isRunning,
                        colour = if (overdue) Pal.amber.copy(alpha = 0.2f) else Pal.panelHigh,
                    ) { host.say(sim.doService(u.id, item.key)) }
                }
            }

            Spacer(Modifier.height(8.dp))
            Text("REPAIRS", fontFamily = Mono, fontSize = 9.sp, letterSpacing = 1.sp, color = Pal.inkDim)
            Spacer(Modifier.height(3.dp))
            for (r in REPAIRS) {
                if (r.key == "turbo" && u.spec.turbo == null) continue
                Row(Modifier.fillMaxWidth().padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(r.name, fontFamily = Mono, fontSize = 10.sp, color = Pal.ink)
                        Text("%.0f h out of service".format(r.hours),
                            fontFamily = Mono, fontSize = 8.sp, color = Pal.inkFaint)
                    }
                    PanelButton(sim.money(r.cost), small = true, enabled = !u.isRunning) {
                        host.say(sim.doRepair(u.id, r.key))
                    }
                }
            }

            if (!u.isFoundingSet) {
                Spacer(Modifier.height(8.dp))
                PanelButton("SELL THIS MACHINE", Modifier.fillMaxWidth(),
                    enabled = !u.isRunning, colour = Pal.red.copy(alpha = 0.18f),
                    textColour = Pal.red) { host.say(sim.sellMachine(u.id)) }
            }

            if (u.isRunning) {
                Text("Shut the machine down before working on it.",
                    fontFamily = Mono, fontSize = 9.sp, color = Pal.amber,
                    modifier = Modifier.padding(top = 6.dp))
            }
        }
    }
}

@Composable
private fun WearRow(label: String, value: Double, verb: String = "worn") {
    Row(Modifier.fillMaxWidth().padding(vertical = 1.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontFamily = Mono, fontSize = 10.sp, color = Pal.inkDim, modifier = Modifier.weight(1f))
        Box(Modifier.width(90.dp)) {
            BarMeter("", value, "", when {
                value > 0.8 -> Pal.red
                value > 0.55 -> Pal.amber
                else -> Pal.green
            })
        }
        Spacer(Modifier.width(6.dp))
        Text("%3.0f%%".format(value * 100), fontFamily = Mono, fontSize = 10.sp,
            color = Pal.ink, modifier = Modifier.width(38.dp))
    }
}

// ------------------------------------------------------------------ market

@Composable
private fun MarketList(host: GameHost) {
    val sim = host.sim
    if (!sim.plant.hasSwitchgear) {
        PanelCard("Machinery market") {
            Text(
                "Nobody will sell you a second generator until you have somewhere to " +
                    "put it and a way to parallel it. Buy the paralleling switchgear on " +
                    "the Upgrades screen, under The Plant.",
                fontSize = 12.sp, color = Pal.inkDim, lineHeight = 17.sp,
            )
        }
        return
    }

    PanelCard("On the board") {
        Readout("Slots used", "${sim.units.size} of ${sim.plant.unitSlots}")
        Readout("Largest machine this plant can take", "%.0f kW".format(sim.plant.maxUnitKW))
        Readout("Stock rotates", "day ${sim.market.nextRefreshDay}")
    }
    Spacer(Modifier.height(8.dp))

    if (sim.market.listings.isEmpty()) {
        PanelCard { Text("Nothing on the board. Wait for the barge.",
            fontFamily = Mono, fontSize = 11.sp, color = Pal.inkFaint) }
        return
    }

    for (l in sim.market.listings) {
        val tooBig = l.kW > sim.plant.maxUnitKW
        val noRoom = sim.units.size >= sim.plant.unitSlots
        val affordable = l.totalCost <= sim.cash

        PanelCard(accent = if (tooBig || noRoom) Pal.panelEdge else Pal.brass) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(l.make, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Pal.ink)
                    Text(
                        "%.0f kW · %.1f L · %d cyl · %s%s".format(
                            l.kW, l.displacementL, l.cylinders,
                            if (l.turbocharged) "turbo" else "naturally aspirated",
                            if (l.aftercooled) ", aftercooled" else "",
                        ),
                        fontFamily = Mono, fontSize = 9.sp, color = Pal.inkFaint,
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(sim.money(l.totalCost), fontFamily = Mono, fontSize = 13.sp,
                        fontWeight = FontWeight.Bold, color = if (affordable) Pal.brass else Pal.inkFaint)
                    Text("inc. %s freight".format(sim.money(l.freight)),
                        fontFamily = Mono, fontSize = 8.sp, color = Pal.inkFaint)
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(l.blurb, fontSize = 11.sp, color = Pal.inkDim, lineHeight = 15.sp)
            Spacer(Modifier.height(6.dp))
            Row {
                Column(Modifier.weight(1f)) {
                    Readout("Condition", l.conditionText, colour = when {
                        l.condition > 0.68 -> Pal.green
                        l.condition > 0.45 -> Pal.amber
                        else -> Pal.red
                    })
                    Readout("Hours", "%,.0f".format(l.hoursOnClock))
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Readout("Fuel", "%.3f kg/kWh".format(l.bsfc))
                    Readout("Cost per kW", sim.money(l.totalCost / l.kW))
                }
            }
            Spacer(Modifier.height(8.dp))
            val label = when {
                noRoom -> "NO FREE UNIT SLOT"
                tooBig -> "TOO BIG FOR THIS PLANT"
                !affordable -> "NEED ${sim.money(l.totalCost - sim.cash)} MORE"
                else -> "BUY IT"
            }
            PanelButton(
                label, Modifier.fillMaxWidth(),
                enabled = !noRoom && !tooBig && affordable,
                colour = if (!noRoom && !tooBig && affordable) Pal.brass.copy(alpha = 0.25f) else Pal.panelHigh,
                textColour = if (!noRoom && !tooBig && affordable) Pal.brass else Pal.inkFaint,
            ) { host.say(sim.buyMachine(l.id)) }
        }
        Spacer(Modifier.height(8.dp))
    }
}
