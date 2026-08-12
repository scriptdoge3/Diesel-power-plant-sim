package com.pointeast.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pointeast.app.ui.ControlRoomBoard
import com.pointeast.app.ui.ElectricalBoard
import com.pointeast.app.ui.RndBoard
import com.pointeast.app.ui.Mono
import com.pointeast.app.ui.Pal
import com.pointeast.app.ui.PointEastTheme
import com.pointeast.app.ui.StatusBar
import com.pointeast.app.ui.ChapterSheet
import com.pointeast.app.ui.HelpSheet
import com.pointeast.app.ui.PrologueSheet
import com.pointeast.app.ui.WinScreen
import kotlinx.coroutines.isActive

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PointEastTheme {
                GameRoot()
            }
        }
    }

    override fun onPause() {
        super.onPause()
        // The activity may not come back; write the world out now.
        runCatching { hostRef?.save(applicationContext) }
    }

    companion object {
        internal var hostRef: GameHost? = null
    }
}

private enum class Tab(val label: String) {
    CONTROL("Control Room"),
    ELECTRICAL("Electrical"),
    RND("R & D"),
}

@Composable
private fun GameRoot() {
    val host: GameHost = viewModel()
    val context = LocalContext.current
    var tab by rememberSaveable { mutableStateOf(Tab.CONTROL) }
    var loaded by remember { mutableStateOf(false) }

    // Handed to the activity so onPause can flush the world to disk.
    SideEffect { MainActivity.hostRef = host }

    LaunchedEffect(Unit) {
        if (!loaded) {
            host.load(context)
            loaded = true
        }
        // Frame-clock game loop. Real elapsed time in, game time out; the sim
        // decides internally how finely to integrate at the current speed.
        var last = withFrameNanos { it }
        while (isActive) {
            val now = withFrameNanos { it }
            val dt = (now - last) / 1_000_000_000.0
            last = now
            // The world holds while you are reading. A story beat should not
            // cost you the frequency.
            if (host.sim.prologueSeen && host.sim.pendingChapters.isEmpty()) {
                host.advance(dt, context)
            }
        }
    }

    // Read the tick so the whole shell recomposes each frame.
    @Suppress("UNUSED_EXPRESSION") host.tick

    Scaffold(
        containerColor = Pal.bg,
        contentWindowInsets = WindowInsets.safeDrawing,
        bottomBar = { BoardSelector(tab) { tab = it } },
    ) { pad ->
        Box(Modifier.fillMaxSize().padding(pad)) {
            Column(Modifier.fillMaxSize()) {
                StatusBar(host)
                Box(Modifier.fillMaxSize()) {
                    when (tab) {
                        Tab.CONTROL -> ControlRoomBoard(host)
                        Tab.ELECTRICAL -> ElectricalBoard(host)
                        Tab.RND -> RndBoard(host)
                    }
                }
            }

            when {
                !host.sim.prologueSeen -> PrologueSheet {
                    host.sim.prologueSeen = true
                    host.save(context)
                }
                host.sim.pendingChapters.isNotEmpty() -> {
                    val ch = host.sim.pendingChapters.first()
                    ChapterSheet(ch.title, ch.body) {
                        host.sim.pendingChapters.removeFirst()
                        host.save(context)
                    }
                }
                host.sim.gameWon -> WinScreen(host)
            }
            if (host.showHelp) HelpSheet(onClose = { host.showHelp = false })

            host.toast?.let { message ->
                Toast(message) { host.toast = null }
            }
        }
    }
}

/**
 * The board selector: three engraved keys along the bottom of the cabinet,
 * each with a lamp above it showing which board you are stood at.
 */
@Composable
private fun BoardSelector(selected: Tab, onSelect: (Tab) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Pal.panel, Pal.panelLow)))
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = 6.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        for (t in Tab.entries) {
            val on = t == selected
            Column(
                Modifier
                    .weight(1f)
                    .background(
                        Brush.verticalGradient(
                            if (on) listOf(Pal.panelHigh, Pal.panel)
                            else listOf(Pal.panelLow, Pal.panelEdge),
                        ),
                        RoundedCornerShape(3.dp),
                    )
                    .border(
                        1.dp,
                        if (on) Pal.brass.copy(alpha = 0.8f) else Pal.panelEdge,
                        RoundedCornerShape(3.dp),
                    )
                    .clickable { onSelect(t) }
                    .padding(vertical = 7.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // Indicator lamp above the key.
                Box(
                    Modifier
                        .size(7.dp)
                        .background(
                            if (on) Pal.brass else Pal.lampOff,
                            RoundedCornerShape(4.dp),
                        ),
                )
                Spacer(Modifier.height(5.dp))
                Text(
                    t.label.uppercase(),
                    fontFamily = Mono,
                    fontSize = 9.sp,
                    letterSpacing = 0.8.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (on) Pal.legendText else Pal.inkFaint,
                    maxLines = 1,
                    softWrap = false,
                )
            }
        }
    }
}

@Composable
private fun Toast(message: String, onDismiss: () -> Unit) {
    LaunchedEffect(message) {
        val start = System.currentTimeMillis()
        while (System.currentTimeMillis() - start < 2600) withFrameNanos { it }
        onDismiss()
    }
    Box(
        Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .windowInsetsPadding(WindowInsets.safeDrawing),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Row(
            Modifier
                .background(Pal.panelHigh, RoundedCornerShape(8.dp))
                .clickable { onDismiss() }
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Text(
                message,
                fontFamily = Mono,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = Pal.ink,
            )
        }
    }
}
