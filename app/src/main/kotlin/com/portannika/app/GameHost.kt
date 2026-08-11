package com.portannika.app

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.portannika.core.Sim
import com.portannika.core.decodeSave
import com.portannika.core.encode
import com.portannika.core.toSave

/**
 * Owns the simulation and drives it from the frame clock.
 *
 * The sim itself is plain mutable Kotlin rather than a tree of Compose state,
 * because it changes hundreds of values twenty times a second and snapshotting
 * all of that individually would cost more than the physics does. Instead the
 * host bumps [tick] once per frame; a composable that reads [tick] recomposes,
 * and then reads whatever it needs straight off the sim.
 */
class GameHost : ViewModel() {

    var seed: Int = DEFAULT_SEED
        private set
    var sim: Sim = Sim(DEFAULT_SEED)
        private set

    /** Bumped once per rendered frame to drive recomposition. */
    var tick by mutableIntStateOf(0)
        private set

    var toast by mutableStateOf<String?>(null)
    var showHelp by mutableStateOf(false)

    // A day of load history for the trace on the grid screen.
    private val historyTotal = ArrayDeque<Float>()
    private val historyMine = ArrayDeque<Float>()
    private var lastSampleSeconds = 0.0
    val traceTotal: List<Float> get() = historyTotal.toList()
    val traceMine: List<Float> get() = historyMine.toList()

    private var lastSaveSeconds = 0.0

    fun advance(realDtSeconds: Double, context: Context?) {
        val dt = realDtSeconds.coerceIn(0.0, 0.25)   // never integrate a stall
        sim.update(dt)
        sampleHistory()
        tick++

        // Autosave every game-hour of simulated time.
        if (context != null && sim.gameSeconds - lastSaveSeconds > 3600.0) {
            lastSaveSeconds = sim.gameSeconds
            save(context)
        }
    }

    private fun sampleHistory() {
        // One sample every six game-minutes gives 240 points across a day.
        if (sim.gameSeconds - lastSampleSeconds < 360.0) return
        lastSampleSeconds = sim.gameSeconds
        historyTotal.addLast(sim.lastSnapshot.townDemandKW.toFloat())
        historyMine.addLast(sim.lastSnapshot.playerKW.toFloat())
        while (historyTotal.size > 240) historyTotal.removeFirst()
        while (historyMine.size > 240) historyMine.removeFirst()
    }

    fun say(message: String?) {
        if (!message.isNullOrBlank()) toast = message
    }

    // ------------------------------------------------------------ persistence

    fun save(context: Context) {
        runCatching {
            val text = sim.toSave(seed).encode()
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putString(KEY_SAVE, text).apply()
        }
    }

    fun load(context: Context): Boolean {
        val text = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_SAVE, null) ?: return false
        val state = decodeSave(text) ?: return false
        seed = state.seed
        val fresh = Sim(state.seed)
        return runCatching {
            fresh.applySave(state)
            sim = fresh
            historyTotal.clear(); historyMine.clear()
            lastSampleSeconds = sim.gameSeconds
            lastSaveSeconds = sim.gameSeconds
            tick++
            true
        }.getOrDefault(false)
    }

    fun hasSave(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).contains(KEY_SAVE)

    fun newGame(context: Context, newSeed: Int = System.currentTimeMillis().toInt()) {
        seed = newSeed
        sim = Sim(newSeed)
        historyTotal.clear(); historyMine.clear()
        lastSampleSeconds = sim.gameSeconds
        lastSaveSeconds = sim.gameSeconds
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(KEY_SAVE).apply()
        tick++
    }

    companion object {
        private const val PREFS = "port_annika"
        private const val KEY_SAVE = "save_v1"
        const val DEFAULT_SEED = 20260811
    }
}
