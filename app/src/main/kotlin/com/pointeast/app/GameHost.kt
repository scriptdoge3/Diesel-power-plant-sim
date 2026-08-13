package com.pointeast.app

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.pointeast.core.Sim
import com.pointeast.core.decodeSave
import com.pointeast.core.encode
import com.pointeast.core.toSave

/**
 * The needle clock.
 *
 * Bumped once per rendered frame, and read *only* from inside draw lambdas.
 * A snapshot read taken during the draw phase invalidates drawing alone, so an
 * instrument that reads this redraws its pointer at the full frame rate without
 * costing a single recomposition. It is a global rather than a parameter
 * because every instrument in the station wants it and none of them should have
 * to be handed the game host to get it.
 */
object FrameClock {
    var frame by mutableIntStateOf(0)
        internal set
}

/**
 * Owns the simulation and drives it from the frame clock.
 *
 * The sim itself is plain mutable Kotlin rather than a tree of Compose state,
 * because it changes hundreds of values twenty times a second and snapshotting
 * all of that individually would cost more than the physics does. Instead the
 * host bumps [tick]; a composable that reads [tick] recomposes, and then reads
 * whatever it needs straight off the sim.
 *
 * There are two clocks, and the split is the whole reason the board is quick:
 *
 *  * [FrameClock] runs at the display rate and moves the needles.
 *  * [tick] runs at [UI_HZ] and rebuilds the text. Numbers printed sixty times
 *    a second are not readable and are not free -- every one of them is a
 *    recomposition of the whole board -- so they are printed fifteen times a
 *    second instead, which is faster than anyone can read a four-digit number
 *    and four times cheaper.
 *
 * The physics is untouched by either: [advance] steps the sim every frame
 * regardless, so frequency and phase are integrated as finely as they ever
 * were.
 */
class GameHost : ViewModel() {

    var seed: Int = DEFAULT_SEED
        private set
    var sim: Sim = Sim(DEFAULT_SEED)
        private set

    /** Bumped [UI_HZ] times a second to drive recomposition of the text. */
    var tick by mutableIntStateOf(0)
        private set

    /**
     * Bumped [SLOW_HZ] times a second, for the boards that show money and
     * hardware rather than electricity. Nothing on the R&D side of the station
     * changes faster than a bank balance, and redrawing forty-odd tech nodes
     * fifteen times a second to watch a number that moves once a minute is the
     * most expensive thing the app could possibly do.
     */
    var slowTick by mutableIntStateOf(0)
        private set

    var toast by mutableStateOf<String?>(null)
    var showHelp by mutableStateOf(false)

    // A day of load history for the trace on the grid screen. The chart reads
    // the two published lists; they are rebuilt when a sample lands, once every
    // six game-minutes, rather than copied out of the deques on every frame.
    private val historyTotal = ArrayDeque<Float>()
    private val historyMine = ArrayDeque<Float>()
    private var lastSampleSeconds = 0.0
    var traceTotal: List<Float> = emptyList()
        private set
    var traceMine: List<Float> = emptyList()
        private set

    private var lastSaveSeconds = 0.0
    private var realSeconds = 0.0
    private var lastUiSeconds = 0.0
    private var lastSlowSeconds = 0.0

    /**
     * One display frame.
     *
     * [stepWorld] is false while a story beat is on the screen: the plant is
     * held, but the clocks still turn, because the panel behind the page is
     * still Compose's only reason to redraw and the page itself has to be
     * dismissable.
     */
    fun advance(realDtSeconds: Double, context: Context?, stepWorld: Boolean = true) {
        val dt = realDtSeconds.coerceIn(0.0, 0.25)   // never integrate a stall
        if (stepWorld) {
            sim.update(dt)
            sampleHistory()
        }

        // Needles every frame, numbers fifteen times a second.
        FrameClock.frame++
        realSeconds += dt
        if (realSeconds - lastUiSeconds >= 1.0 / UI_HZ) {
            lastUiSeconds = realSeconds
            tick++
        }
        if (realSeconds - lastSlowSeconds >= 1.0 / SLOW_HZ) {
            lastSlowSeconds = realSeconds
            slowTick++
        }

        // Autosave every game-hour of simulated time.
        if (stepWorld && context != null && sim.gameSeconds - lastSaveSeconds > 3600.0) {
            lastSaveSeconds = sim.gameSeconds
            save(context)
        }
    }

    private fun sampleHistory() {
        // One sample every six game-minutes gives 240 points across a day.
        if (sim.gameSeconds - lastSampleSeconds < 360.0) return
        lastSampleSeconds = sim.gameSeconds
        historyTotal.addLast(sim.lastSnapshot.cityDemandKW.toFloat())
        historyMine.addLast(sim.lastSnapshot.playerKW.toFloat())
        while (historyTotal.size > 240) historyTotal.removeFirst()
        while (historyMine.size > 240) historyMine.removeFirst()
        traceTotal = historyTotal.toList()
        traceMine = historyMine.toList()
    }

    private fun clearHistory() {
        historyTotal.clear()
        historyMine.clear()
        traceTotal = emptyList()
        traceMine = emptyList()
    }

    fun say(message: String?) {
        if (!message.isNullOrBlank()) toast = message
        poke()
    }

    /**
     * Refresh every board now.
     *
     * The sim is not Compose state, so an action the player takes changes
     * nothing Compose is watching. On the fast boards the next tick covers it
     * within a frame or two; on the slow ones half a second of a button that
     * appears not to have worked is half a second too long, so anything that
     * acts on the world calls this and the board redraws under their thumb.
     */
    fun poke() {
        tick++
        slowTick++
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
            clearHistory()
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
        clearHistory()
        lastSampleSeconds = sim.gameSeconds
        lastSaveSeconds = sim.gameSeconds
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(KEY_SAVE).apply()
        tick++
    }

    companion object {
        private const val PREFS = "port_annika"
        private const val KEY_SAVE = "save_v1"
        const val DEFAULT_SEED = 20260811

        /** How often the printed numbers are refreshed, in hertz. */
        const val UI_HZ = 15.0

        /** How often the money-and-hardware boards are refreshed, in hertz. */
        const val SLOW_HZ = 2.0
    }
}
