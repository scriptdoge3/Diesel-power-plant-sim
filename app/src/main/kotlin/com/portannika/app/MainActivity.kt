package com.portannika.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Factory
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Upgrade
import com.portannika.app.ui.LedgerScreen
import com.portannika.app.ui.GridScreen
import com.portannika.app.ui.Mono
import com.portannika.app.ui.Pal
import com.portannika.app.ui.PanelScreen
import com.portannika.app.ui.PlantScreen
import com.portannika.app.ui.PortAnnikaTheme
import com.portannika.app.ui.StatusBar
import com.portannika.app.ui.TechScreen
import com.portannika.app.ui.HelpSheet
import com.portannika.app.ui.WinScreen
import kotlinx.coroutines.isActive

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PortAnnikaTheme {
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

private enum class Tab(val label: String, val icon: ImageVector) {
    PANEL("Panel", Icons.Filled.Speed),
    GRID("Grid", Icons.Filled.Hub),
    TECH("Upgrades", Icons.Filled.Upgrade),
    PLANT("Plant", Icons.Filled.Factory),
    OFFICE("Office", Icons.Filled.AccountBalance),
}

@Composable
private fun GameRoot() {
    val host: GameHost = viewModel()
    val context = LocalContext.current
    var tab by rememberSaveable { mutableStateOf(Tab.PANEL) }
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
            host.advance(dt, context)
        }
    }

    // Read the tick so the whole shell recomposes each frame.
    @Suppress("UNUSED_EXPRESSION") host.tick

    Scaffold(
        containerColor = Pal.bg,
        contentWindowInsets = WindowInsets.safeDrawing,
        bottomBar = {
            NavigationBar(containerColor = Pal.panel, tonalElevation = 0.dp) {
                for (t in Tab.entries) {
                    NavigationBarItem(
                        selected = tab == t,
                        onClick = { tab = t },
                        icon = { Icon(t.icon, contentDescription = t.label) },
                        label = { Text(t.label, fontSize = 10.sp) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Pal.bg,
                            selectedTextColor = Pal.blue,
                            indicatorColor = Pal.blue,
                            unselectedIconColor = Pal.inkFaint,
                            unselectedTextColor = Pal.inkFaint,
                        ),
                    )
                }
            }
        },
    ) { pad ->
        Box(Modifier.fillMaxSize().padding(pad)) {
            Column(Modifier.fillMaxSize()) {
                StatusBar(host)
                Box(Modifier.fillMaxSize()) {
                    when (tab) {
                        Tab.PANEL -> PanelScreen(host)
                        Tab.GRID -> GridScreen(host)
                        Tab.TECH -> TechScreen(host)
                        Tab.PLANT -> PlantScreen(host)
                        Tab.OFFICE -> LedgerScreen(host)
                    }
                }
            }

            if (host.sim.gameWon) WinScreen(host)
            if (host.showHelp) HelpSheet(onClose = { host.showHelp = false })

            host.toast?.let { message ->
                Toast(message) { host.toast = null }
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
