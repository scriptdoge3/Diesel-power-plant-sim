package com.pointeast.core

import org.junit.Test

/**
 * How long the plant takes to answer the operator, in seconds of real time at
 * 1x. Everything the player does is a press followed by a wait, and this is a
 * measurement of the wait.
 */
class ResponseTest {

    private fun warmSim(): Sim {
        val sim = Sim(7)
        sim.changeTimeScale(1)
        val u = sim.selectedUnit
        u.batterySoC = 1.0
        sim.startUnit(u.id)
        repeat(60_000) { sim.update(1.0 / 60.0) }      // let it start and warm
        return sim
    }

    /** Run at 1x until [done], and report the wall-clock seconds it took. */
    private fun secondsUntil(sim: Sim, limit: Double = 1200.0, done: () -> Boolean): Double {
        val t0 = sim.gameSeconds
        var guard = 0
        while (!done() && sim.gameSeconds - t0 < limit && guard++ < 200_000) {
            sim.update(1.0 / 60.0)
        }
        return sim.gameSeconds - t0
    }

    private fun report(what: String, seconds: Double) {
        println("%-46s %7.1f s".format(what, seconds))
    }

    @Test
    fun `how long the operator waits at 1x`() {
        println("\n---- operator wait at 1x, in real seconds ----")

        // ---- cold start, the very first thing anyone does ----
        run {
            val sim = Sim(7)
            sim.changeTimeScale(1)
            val u = sim.selectedUnit
            report("cold block starts at", u.coolantC)
            sim.chargeBattery(u.id)
            sim.setPrelube(u.id, true)
            sim.startUnit(u.id)
            report("START to first fire", secondsUntil(sim) { u.runState == RunState.RUNNING })
            report("  ... then to 55 C, warm enough for load",
                secondsUntil(sim, limit = 3600.0) { u.coolantC >= 55.0 })
            report("  ... then to a settled 88 C",
                secondsUntil(sim, limit = 7200.0) { u.coolantC >= 85.0 })
        }

        // ---- warming up the way you actually do it: on load ----
        run {
            val sim = Sim(7)
            sim.changeTimeScale(1)
            val u = sim.selectedUnit
            u.batterySoC = 1.0
            sim.setPrelube(u.id, true)
            sim.startUnit(u.id)
            secondsUntil(sim) { u.runState == RunState.RUNNING }
            // Sync the way a person does: look at the scope about once a
            // second, ease the speeder, close when it sits in the window.
            var guard = 0
            var sinceLook = 0.0
            val t0 = sim.gameSeconds
            var lastFail = ""
            while (!u.onBus && guard++ < 200_000) {
                sinceLook += 1.0 / 60.0
                val c = u.syncCheck(sim.grid.frequencyHz, sim.grid.busVoltPU)
                if (sinceLook > 1.0) {
                    sinceLook = 0.0
                    lastFail = listOfNotNull(
                        if (!c.slipOk) "slip" else null,
                        if (!c.directionOk) "direction" else null,
                        if (!c.voltOk) "volts" else null,
                    ).joinToString("+").ifEmpty { "angle" }
                    if (!c.slipOk || !c.directionOk) {
                        sim.adjustSpeeder(u.id, (if (c.slipRpm < 0) 1 else -1) * 0.0006)
                    }
                    if (!c.voltOk) {
                        sim.setField(u.id, (u.fieldRheostat + if (c.voltErrPct < 0) 0.02 else -0.02))
                    }
                }
                if (c.slipOk && c.angleOk && c.voltOk && c.directionOk) sim.closeBreaker(u.id)
                sim.update(1.0 / 60.0)
            }
            if (!u.onBus) println("      never synced; last blocked on $lastFail")
            report("running to synchronised and closed", sim.gameSeconds - t0)
            repeat(40) { sim.adjustSpeeder(u.id, 0.002) }
            report("on load, then to 55 C", secondsUntil(sim, limit = 3600.0) { u.coolantC >= 55.0 })
            report("on load, then to 85 C", secondsUntil(sim, limit = 7200.0) { u.coolantC >= 85.0 })
            report("  (carrying kW)", u.elecKW)
        }

        // ---- the speeder, the control used most ----
        run {
            val sim = warmSim()
            val u = sim.selectedUnit
            sim.setSpeeder(u.id, 1.0)
            repeat(600) { sim.update(1.0 / 60.0) }
            val before = u.freqHz
            sim.adjustSpeeder(u.id, 0.0035)               // one press of the nudge key
            report("speeder nudge to half its effect on Hz",
                secondsUntil(sim, limit = 120.0) {
                    kotlin.math.abs(u.freqHz - before) > 0.0035 * Nominal.FREQ * 0.5
                })
        }

        // ---- stopping ----
        run {
            val sim = warmSim()
            val u = sim.selectedUnit
            sim.stopUnit(u.id)
            report("STOP to actually stopped", secondsUntil(sim, limit = 900.0) {
                u.runState == RunState.STOPPED
            })
        }

        // ---- restart while it is still turning ----
        run {
            val sim = warmSim()
            val u = sim.selectedUnit
            sim.stopUnit(u.id)
            repeat(300) { sim.update(1.0 / 60.0) }        // five seconds into the cooldown
            sim.startUnit(u.id)
            report("restart during cooldown", secondsUntil(sim, limit = 120.0) {
                u.runState == RunState.RUNNING
            })
        }
        println("---------------------------------------------\n")
    }
}
