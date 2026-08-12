package com.pointeast.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pointeast.app.GameHost
import com.pointeast.core.BRANCHES
import com.pointeast.core.NODE_BY_ID
import com.pointeast.core.branchNodes
import com.pointeast.core.buildFoundingSpec
import com.pointeast.core.NODES
import com.pointeast.core.isUnlockable

/** Constant, so it is not refolded on every frame. */
private val FULLY_DEVELOPED = buildFoundingSpec(NODES.map { it.id }.toSet())

@Composable
fun TechScreen(host: GameHost) {
    val sim = host.sim
    var branchId by rememberSaveable { mutableStateOf(BRANCHES.first().id) }
    val branch = BRANCHES.first { it.id == branchId }
    val accent = Color(branch.colorHex.toInt())

    Column(Modifier.padding(horizontal = 10.dp).padding(top = 8.dp)) {

        // Where Set 1 stands today, and where the money goes next.
        val spec = sim.foundingSet.spec
        PanelCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("UNIT 8 CONTINUOUS RATING", fontFamily = Mono, fontSize = 9.sp,
                        letterSpacing = 1.sp, color = Pal.inkDim)
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text("%.0f".format(spec.ratedKW), fontFamily = Mono, fontSize = 26.sp,
                            fontWeight = FontWeight.Bold, color = Pal.brass)
                        Text(" kW", fontFamily = Mono, fontSize = 11.sp, color = Pal.inkFaint,
                            modifier = Modifier.padding(bottom = 4.dp))
                        Text("   from 50 kW stock", fontFamily = Mono, fontSize = 9.sp,
                            color = Pal.inkFaint, modifier = Modifier.padding(bottom = 4.dp))
                    }
                }
                Column(horizontalAlignment = Alignment.End) {
                    Readout("Engine limit", "%.0f kW".format(spec.engineKWLimit))
                    Readout("Alternator limit", "%.0f kW".format(spec.altKWLimit))
                    Text(
                        if (spec.engineKWLimit < spec.altKWLimit) "engine is the limit"
                        else "alternator is the limit",
                        fontFamily = Mono, fontSize = 8.sp, color = Pal.amber,
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            for (b in BRANCHES) {
                val owned = branchNodes(b.id).count { it.id in sim.ownedTech }
                val total = branchNodes(b.id).size
                Chip("${b.name}  $owned/$total", b.id == branchId, Color(b.colorHex.toInt())) {
                    branchId = b.id
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = 16.dp)) {
            Text(branch.blurb, fontSize = 11.sp, color = Pal.inkDim,
                modifier = Modifier.padding(bottom = 8.dp))

            for (node in branchNodes(branchId)) {
                val ownedNode = node.id in sim.ownedTech
                val unlockable = isUnlockable(node, sim.ownedTech)
                val affordable = node.cost <= sim.cash
                val blockedByRunning = node.branch !in listOf("ctrl", "plant", "recov") && sim.foundingSet.isRunning

                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                        .background(
                            if (ownedNode) accent.copy(alpha = 0.09f) else Pal.panel,
                            RoundedCornerShape(10.dp),
                        )
                        .border(
                            1.dp,
                            if (ownedNode) accent.copy(alpha = 0.7f) else Pal.panelEdge,
                            RoundedCornerShape(10.dp),
                        )
                        .padding(10.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(22.dp).background(
                                if (ownedNode) accent else Pal.panelHigh, CircleShape,
                            ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                if (ownedNode) "✓" else "${node.tier + 1}",
                                fontFamily = Mono, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                                color = if (ownedNode) Pal.bg else Pal.inkDim,
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(
                            node.name, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                            color = if (ownedNode) accent else Pal.ink,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            if (ownedNode) "FITTED" else sim.money(node.cost),
                            fontFamily = Mono, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                            color = when {
                                ownedNode -> accent
                                affordable && unlockable -> Pal.brass
                                else -> Pal.inkFaint
                            },
                        )
                    }

                    Spacer(Modifier.height(5.dp))
                    Text(node.desc, fontSize = 11.sp, color = Pal.inkDim, lineHeight = 15.sp)
                    Spacer(Modifier.height(4.dp))
                    Text(node.effect, fontFamily = Mono, fontSize = 10.sp, color = accent)
                    if (!ownedNode) {
                        Text(
                            "%.0f hours of work%s".format(
                                sim.installHours(node),
                                if (node.branch !in listOf("ctrl", "plant", "recov"))
                                    " with Set 1 shut down" else "",
                            ),
                            fontFamily = Mono, fontSize = 9.sp, color = Pal.inkFaint,
                        )
                    }

                    if (node.req.isNotEmpty()) {
                        Spacer(Modifier.height(4.dp))
                        Row(Modifier.horizontalScroll(rememberScrollState())) {
                            Text("needs: ", fontFamily = Mono, fontSize = 9.sp, color = Pal.inkFaint)
                            for (r in node.req) {
                                val met = r in sim.ownedTech
                                Text(
                                    NODE_BY_ID[r]?.name ?: r,
                                    fontFamily = Mono, fontSize = 9.sp,
                                    color = if (met) Pal.green else Pal.amber,
                                    textDecoration = if (met) TextDecoration.LineThrough else null,
                                    modifier = Modifier.padding(end = 8.dp),
                                )
                            }
                        }
                    }

                    if (!ownedNode) {
                        Spacer(Modifier.height(8.dp))
                        val label = when {
                            !unlockable -> "PREREQUISITES NOT MET"
                            blockedByRunning -> "SHUT UNIT 8 DOWN FIRST"
                            !affordable -> "NEED ${sim.money(node.cost - sim.cash)} MORE"
                            else -> "FIT IT  ·  ${sim.money(node.cost)}"
                        }
                        PanelButton(
                            label,
                            Modifier.fillMaxWidth(),
                            enabled = unlockable && affordable && !blockedByRunning,
                            colour = if (unlockable && affordable && !blockedByRunning)
                                accent.copy(alpha = 0.25f) else Pal.panelHigh,
                            textColour = if (unlockable && affordable && !blockedByRunning) accent else Pal.inkFaint,
                        ) { host.say(sim.buyTech(node.id)) }
                    }
                }
            }

            // Show what the whole tree would be worth, as a target to aim at.
            if (branchId != "plant") {
                val full = FULLY_DEVELOPED
                Text(
                    "Fully developed, Set 1 makes %.0f kW continuous.".format(full.ratedKW),
                    fontFamily = Mono, fontSize = 9.sp, color = Pal.inkFaint,
                    modifier = Modifier.padding(top = 4.dp, bottom = 20.dp),
                )
            }
        }
    }
}
