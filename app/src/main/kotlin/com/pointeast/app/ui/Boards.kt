package com.pointeast.app.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pointeast.app.GameHost

/* ============================================================================
 *  Three boards, the way a station is actually divided:
 *
 *    CONTROL ROOM  -- the machines. Running them, keeping them alive, the log.
 *    ELECTRICAL    -- the system outside the fence, and what it wants of you.
 *    R & D         -- everything about becoming bigger than you are.
 *
 *  Each board carries its own row of selector keys. Two levels of navigation
 *  beats one level with eight destinations on a screen this size.
 * ========================================================================== */

@Composable
private fun BoardTabs(
    labels: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
) {
    androidx.compose.foundation.layout.Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        labels.forEachIndexed { i, label ->
            Chip(label, i == selected) { onSelect(i) }
        }
    }
}

@Composable
private fun BoardScroll(content: @Composable () -> Unit) {
    Column(
        Modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 8.dp)
            .padding(bottom = 20.dp),
    ) {
        content()
        Spacer(Modifier.height(4.dp))
    }
}

// ---------------------------------------------------------------- control room

@Composable
fun ControlRoomBoard(host: GameHost) {
    var tab by rememberSaveable { mutableStateOf(0) }
    Column {
        BoardTabs(listOf("Panel", "Machines", "Log"), tab) { tab = it }
        when (tab) {
            0 -> PanelScreen(host)              // brings its own scroll
            1 -> BoardScroll { MachineList(host) }
            else -> BoardScroll { LogTab(host) }
        }
    }
}

// ------------------------------------------------------------------ electrical

@Composable
fun ElectricalBoard(host: GameHost) {
    var tab by rememberSaveable { mutableStateOf(0) }
    // A waiting order is the one thing on this board that is time-critical, so
    // the key says so rather than making the player go and look.
    val pending = host.sim.campaign.offeredOrder != null
    Column {
        BoardTabs(
            listOf("Grid", "Point East", if (pending) "Dispatch !" else "Dispatch"),
            tab,
        ) { tab = it }
        BoardScroll {
            when (tab) {
                0 -> GridSystemTab(host)
                1 -> PointEastTab(host)
                else -> DispatchTab(host)
            }
        }
    }
}

// ------------------------------------------------------------------------ R&D

@Composable
fun RndBoard(host: GameHost) {
    var tab by rememberSaveable { mutableStateOf(0) }
    Column {
        BoardTabs(listOf("Upgrades", "Plant", "Market", "Books"), tab) { tab = it }
        when (tab) {
            0 -> TechScreen(host)               // brings its own scroll
            1 -> BoardScroll { PlantOverview(host) }
            2 -> BoardScroll { MarketList(host) }
            else -> BoardScroll {
                CareerTab(host)
                Spacer(Modifier.height(8.dp))
                LedgerTab(host)
            }
        }
    }
}
