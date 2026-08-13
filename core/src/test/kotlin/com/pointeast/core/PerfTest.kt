package com.pointeast.core

import org.junit.Test

/**
 * How much game time the simulation can produce per second of real time.
 *
 * The phone has to run this inside a 16 ms frame alongside laying out and
 * drawing the panel, so the number that matters is game-seconds per real
 * second: at 300x time compression the sim must deliver 300 of them.
 */
class PerfTest {

    private fun benchmark(units: Int, scale: Int, label: String) {
        val quasi = scale > QUASI_STEADY_ABOVE
        val sim = Sim(4242)
        sim.setCashForTest(2_000_000.0)
        // Build a late-game plant: several machines, all running and loaded.
        for (id in listOf("ctrl1", "plant1", "plant2", "plant3", "plant6", "plant5", "plant7")) {
            sim.buyTech(id)
        }
        var guard = 0
        while (sim.units.size < units && guard++ < 40) {
            val l = sim.market.listings.firstOrNull { it.kW <= sim.plant.maxUnitKW }
            if (l == null) { sim.market.refresh(sim.currentDay() + 99, sim.plant, 0.5); continue }
            if (!sim.buyMachine(l.id).startsWith("Bought")) sim.market.remove(l.id)
        }
        for (u in sim.units) {
            u.batterySoC = 1.0; u.coolantC = 80.0; u.oilC = 85.0
            sim.startUnit(u.id)
        }
        sim.changeTimeScale(scale)
        repeat(4000) { sim.update(0.05) }     // settle and warm

        // Now time it, one display frame at a time.
        val dt = 1.0 / 60.0
        val reps = 20_000
        val t0 = System.nanoTime()
        repeat(reps) { sim.update(dt) }
        val elapsed = (System.nanoTime() - t0) / 1e9
        val gameSeconds = reps * dt * sim.timeScale
        val ratio = gameSeconds / elapsed

        println(
            "%-26s %d units, %4dx %-5s: %,.0fx real time achievable (%.3f ms per frame)"
                .format(label, sim.units.size, scale, if (quasi) "quasi" else "full",
                    ratio, elapsed / reps * 1000.0)
        )
    }

    @Test
    fun `simulation throughput`() {
        benchmark(1, 1, "one machine, real time")
        benchmark(1, DEFAULT_SCALE, "one machine, default")
        benchmark(6, DEFAULT_SCALE, "six machines, default")
        benchmark(6, 300, "six machines, fast")
        benchmark(6, TIME_SCALES.last(), "six machines, flat out")
    }
}
