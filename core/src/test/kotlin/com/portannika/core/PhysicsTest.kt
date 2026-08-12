package com.portannika.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * These are calibration tests as much as correctness tests. A diesel genset
 * that burns the wrong amount of fuel or runs the wrong exhaust temperature is
 * a bug in a game that claims to be realistic, so the numbers are pinned to
 * ranges a real 1800 rpm set would actually sit in.
 */
class PhysicsTest {

    private fun runSim(sim: Sim, gameSeconds: Double, scale: Int = 1) {
        sim.changeTimeScale(scale)
        val realDt = 0.05
        val steps = (gameSeconds / (realDt * scale)).toInt()
        repeat(steps) { sim.update(realDt) }
    }

    /** Bring Unit 8 up, on the bus, and loaded. Returns the unit. */
    private fun bringOnLine(sim: Sim, targetKW: Double = 40.0): Genset {
        val u = sim.unit8
        sim.startUnit("u8")
        runSim(sim, 40.0)
        assertTrue("engine should be running, was ${u.runState}", u.isRunning)

        // Warm it before loading, the way you would.
        sim.changeTimeScale(15)
        repeat(4000) { sim.update(0.05) }
        sim.changeTimeScale(1)

        // Match volts, then bring the speed in slightly fast and close.
        u.fieldRheostat = 0.60
        var guard = 0
        while (guard++ < 40000) {
            val c = u.syncCheck(sim.grid.frequencyHz, sim.grid.busVoltPU)
            if (c.voltErrPct > 1.0) u.fieldRheostat -= 0.0015
            if (c.voltErrPct < -1.0) u.fieldRheostat += 0.0015
            u.fieldRheostat = u.fieldRheostat.clamp(0.0, 1.0)
            if (c.slipHz < 0.08) u.speederPU += 0.00004
            if (c.slipHz > 0.22) u.speederPU -= 0.00004
            sim.update(0.05)
            val c2 = u.syncCheck(sim.grid.frequencyHz, sim.grid.busVoltPU)
            if (c2.allOk && c2.directionOk) {
                sim.closeBreaker("u8")
                if (u.onBus) break
            }
        }
        assertTrue("should have synchronised within the guard loop", u.onBus)

        // Take load with the speeder.
        guard = 0
        while (guard++ < 60000 && u.elecKW < targetKW) {
            u.speederPU = (u.speederPU + 0.00002).coerceAtMost(GovernorBase.SPEEDER_MAX)
            sim.update(0.05)
        }
        return u
    }

    // ------------------------------------------------------------ the basics

    @Test
    fun `90 Hz comes from 1800 rpm on six poles`() {
        assertEquals(90.0, Nominal.RPM * Nominal.POLES / 120.0, 1e-9)
        val u = Genset("t", buildUnit8Spec(emptySet()), true)
        u.rpm = 1800.0
        assertEquals(90.0, u.freqHz, 1e-9)
        u.rpm = 1700.0
        assertEquals(85.0, u.freqHz, 1e-9)
    }

    @Test
    fun `stock Unit 8 is a 50 kW machine limited by its alternator`() {
        val spec = buildUnit8Spec(emptySet())
        assertEquals(50.0, spec.ratedKW, 0.5)
        assertEquals(62.5, spec.ratedKVA, 0.01)
        // The engine can make a bit more than the alternator can carry.
        assertTrue("engine limit ${spec.engineKWLimit} should exceed alternator limit",
            spec.engineKWLimit > spec.altKWLimit)
    }

    @Test
    fun `the tech tree roughly triples the machine`() {
        val all = NODES.map { it.id }.toSet()
        val spec = buildUnit8Spec(all)
        assertTrue("fully upgraded rating ${spec.ratedKW} should be over 130 kW", spec.ratedKW > 130.0)
        assertTrue("fully upgraded rating ${spec.ratedKW} should stay under 300 kW", spec.ratedKW < 300.0)
        assertTrue(spec.turbo != null)
        assertTrue(spec.avr && spec.egov && spec.autoSync)
    }

    @Test
    fun `every tech node's prerequisites exist and no cycles`() {
        for (n in NODES) {
            for (r in n.req) assertNotNull("${n.id} requires unknown node $r", NODE_BY_ID[r])
        }
        // A node must be reachable by buying strictly-earlier nodes.
        val owned = mutableSetOf<String>()
        var progress = true
        while (progress) {
            progress = false
            for (n in NODES) if (n.id !in owned && isUnlockable(n, owned)) {
                owned += n.id; progress = true
            }
        }
        assertEquals("all nodes should be reachable", NODES.size, owned.size)
    }

    // -------------------------------------------------------- thermodynamics

    @Test
    fun `at rated load the engine burns and breathes like a real diesel`() {
        val sim = Sim(1234)
        val u = bringOnLine(sim, 42.0)

        // Let it settle thermally.
        sim.changeTimeScale(5)
        repeat(20000) { sim.update(0.05) }

        val bsfc = u.fuelRateKgS * 3600.0 / u.elecKW
        println("load=%.1f kW  bsfc=%.3f kg/kWh  AFR=%.1f  EGT=%.0f C  coolant=%.0f C  oil=%.0f C  oilP=%.2f bar  rack=%.2f"
            .format(u.elecKW, bsfc, u.afr, u.egtC, u.coolantC, u.oilC, u.oilPressureBar, u.rack))

        assertTrue("output should be near the target, was ${u.elecKW}", u.elecKW > 30.0)
        assertTrue("BSFC $bsfc kg/kWh is not plausible for an old IDI diesel", bsfc in 0.24..0.42)
        assertTrue("AFR ${u.afr} should be lean of the smoke limit at rated load", u.afr > u.spec.smokeAFR)
        assertTrue("EGT ${u.egtC} C out of range for a loaded NA diesel", u.egtC in 250.0..600.0)
        assertTrue("coolant ${u.coolantC} C should be at operating temperature", u.coolantC in 70.0..100.0)
        assertTrue("oil pressure ${u.oilPressureBar} bar too low", u.oilPressureBar > 1.5)
        assertTrue("no smoke expected at rated load", u.smokeExcess < 0.05)
    }

    @Test
    fun `overfuelling makes smoke and heat instead of power`() {
        val sim = Sim(77)
        val u = bringOnLine(sim, 40.0)
        val cleanKW = u.elecKW

        // Wind the speeder all the way up: the rack goes to the stop.
        u.speederPU = GovernorBase.SPEEDER_MAX
        sim.changeTimeScale(1)
        repeat(6000) { sim.update(0.05) }

        println("overfuelled: rack=%.2f AFR=%.1f smoke=%.2f EGT=%.0f kW=%.1f (clean was %.1f)"
            .format(u.rack, u.afr, u.smokeExcess, u.egtC, u.elecKW, cleanKW))
        assertTrue("rack should be near the stop", u.rack > 0.9)
        assertTrue("should be making more power than before", u.elecKW > cleanKW)
        // Past the smoke limit the exhaust gets hot and the burn gets dirty.
        assertTrue("EGT should have climbed", u.egtC > 400.0)
    }

    // --------------------------------------------------------------- governor

    @Test
    fun `droop sets the load share between machines`() {
        // Two identical machines at the same speeder setting but different
        // droop should split load inversely to their droop.
        val spec = buildUnit8Spec(emptySet())
        val a = Genset("a", spec, true).apply { droop = 0.03; speederPU = 1.04 }
        val b = Genset("b", spec, true).apply { droop = 0.06; speederPU = 1.04 }
        val f = 90.0
        val pa = spec.ratedKW * (a.speederPU - f / Nominal.FREQ) / a.droop
        val pb = spec.ratedKW * (b.speederPU - f / Nominal.FREQ) / b.droop
        assertEquals("half the droop should take twice the load", 2.0, pa / pb, 1e-6)
    }

    @Test
    fun `raising the speeder takes load, lowering it sheds load`() {
        val sim = Sim(4242)
        val u = bringOnLine(sim, 25.0)
        val before = u.elecKW

        repeat(3000) { sim.update(0.05); u.speederPU = (u.speederPU + 0.000015).coerceAtMost(1.10) }
        val after = u.elecKW
        println("speeder up: %.1f kW -> %.1f kW".format(before, after))
        assertTrue("raising the speeder should pick up load", after > before + 2.0)

        repeat(6000) { sim.update(0.05); u.speederPU = (u.speederPU - 0.000015).coerceAtLeast(0.95) }
        println("speeder down: %.1f kW".format(u.elecKW))
        assertTrue("lowering the speeder should shed load", u.elecKW < after - 2.0)
    }

    @Test
    fun `a mechanical governor set below its droop limit hunts`() {
        val sim = Sim(99)
        val u = bringOnLine(sim, 30.0)
        u.droop = 0.025          // right at the mechanical minimum
        var min = 1e9; var max = -1e9
        repeat(4000) {
            sim.update(0.05)
            min = minOf(min, u.elecKW); max = maxOf(max, u.elecKW)
        }
        println("hunting swing: %.1f kW to %.1f kW".format(min, max))
        assertTrue("a tight mechanical governor should visibly hunt", max - min > 1.5)
    }

    // ---------------------------------------------------------- synchronising

    @Test
    fun `the synchroscope gates a bad close`() {
        val sim = Sim(5150)
        val u = sim.unit8
        sim.startUnit("u8")
        runSim(sim, 45.0)
        u.fieldRheostat = 0.62
        runSim(sim, 20.0)

        // Force a big angle error and try to close.
        u.phaseDeg = 140.0
        val refused = u.closeBreaker(sim.grid.frequencyHz, sim.grid.busVoltPU, force = false)
        assertEquals("should refuse to close 140 degrees out", -1.0, refused, 1e-9)
        assertTrue(!u.onBus)

        // Forcing it through does real damage.
        val bearingsBefore = u.wear.bearings
        val shock = u.closeBreaker(sim.grid.frequencyHz, sim.grid.busVoltPU, force = true)
        println("out-of-phase close shock = %.1f pu".format(shock))
        assertTrue("closing 140 deg out should be violent", shock > 3.0)
        assertTrue("it should have hurt the bearings", u.wear.bearings > bearingsBefore)
    }

    @Test
    fun `a clean close is gentle`() {
        val sim = Sim(2718)
        val u = sim.unit8
        sim.startUnit("u8")
        runSim(sim, 45.0)
        u.fieldRheostat = 0.62
        runSim(sim, 30.0)

        u.phaseDeg = 1.0
        // Match volts to the bus before closing.
        var guard = 0
        while (guard++ < 8000 && abs(u.syncCheck(sim.grid.frequencyHz, sim.grid.busVoltPU).voltErrPct) > 1.0) {
            val c = u.syncCheck(sim.grid.frequencyHz, sim.grid.busVoltPU)
            u.fieldRheostat = (u.fieldRheostat - 0.0008 * c.voltErrPct.coerceIn(-2.0, 2.0)).clamp(0.0, 1.0)
            sim.update(0.05)
            u.phaseDeg = 1.0
        }
        val shock = u.closeBreaker(sim.grid.frequencyHz, sim.grid.busVoltPU, force = true)
        println("clean close shock = %.2f pu".format(shock))
        assertTrue("closing near zero degrees should be gentle, was $shock", shock < 1.5)
    }

    // -------------------------------------------------------------- the grid

    @Test
    fun `the bus solves to a sensible voltage and the town gets 180 volts`() {
        val sim = Sim(31415)
        runSim(sim, 300.0, scale = 5)
        val s = sim.lastSnapshot
        println("f=%.2f Hz  bus=%.0f V  line=%.0f V  service=%.0f V  demand=%.0f kW  gen=%.0f kW"
            .format(s.frequencyHz, s.busVolts, s.lineVolts, s.serviceVolts, s.townDemandKW, s.totalGenKW))
        assertTrue("frequency ${s.frequencyHz} should be near 90 Hz", abs(s.frequencyHz - 90.0) < 2.0)
        assertTrue("bus volts ${s.busVolts} should be near 480", abs(s.busVolts - 480.0) < 60.0)
        // The split-phase service is half of 360 either side of the center tap.
        assertEquals(s.serviceVolts / 2.0, s.busVoltsPU * Nominal.SERVICE_HALF, 0.01)
        assertTrue("the town should be carried", s.totalGenKW > s.townDemandKW * 0.8)
    }

    @Test
    fun `losing generation drops the frequency`() {
        val sim = Sim(161803)
        runSim(sim, 400.0, scale = 5)
        val fBefore = sim.grid.frequencyHz
        // Trip the biggest machine on the bus.
        sim.grid.stationUnits.filter { it.online }.maxByOrNull { it.spec.kW }?.stop()
        repeat(200) { sim.update(0.05) }
        println("f before=%.2f  f after trip=%.2f".format(fBefore, sim.grid.frequencyHz))
        assertTrue("frequency should dip after losing a unit", sim.grid.frequencyHz < fBefore)
    }

    @Test
    fun `under-excitation pulls the bus voltage down`() {
        val sim = Sim(2024)
        val u = bringOnLine(sim, 30.0)
        val vHigh = sim.grid.busVoltPU
        val qHigh = u.kvar

        u.fieldRheostat = 0.15
        repeat(3000) { sim.update(0.05) }
        println("field 0.6 -> 0.15 : V %.3f -> %.3f pu, kvar %.1f -> %.1f".format(vHigh, sim.grid.busVoltPU, qHigh, u.kvar))
        assertTrue("weakening the field should push VARs down", u.kvar < qHigh)
    }

    // ------------------------------------------------------------- protection

    @Test
    fun `overspeed destroys the engine`() {
        val sim = Sim(8080)
        val u = sim.unit8
        sim.startUnit("u8")
        runSim(sim, 45.0)
        u.rpm = EngineBase.OVERSPEED_TRIP_RPM + 60.0
        repeat(20) { sim.update(0.05) }
        assertEquals(RunState.FAILED, u.runState)
        assertTrue(u.failureText!!.contains("Overspeed"))
    }

    @Test
    fun `losing oil pressure shuts the engine down`() {
        val sim = Sim(606)
        val u = sim.unit8
        sim.startUnit("u8")
        runSim(sim, 45.0)
        assertTrue(u.isRunning)
        u.wear.bearings = 0.97        // worn out: no oil pressure left
        u.oilC = 130.0
        repeat(200) { sim.update(0.05) }
        println("oil pressure with worn bearings: %.2f bar, state=%s".format(u.oilPressureBar, u.runState))
        assertTrue("should have tripped or be alarming",
            u.runState != RunState.RUNNING || u.alarms.any { it.code == "OP" })
    }

    @Test
    fun `boost on the stock head gasket over-pressures the cylinder`() {
        // The turbo node requires the head studs precisely because of this.
        val withStuds = buildUnit8Spec(setOf("mech1", "mech2", "air1", "air2", "air3"))
        val withoutStuds = buildUnit8Spec(setOf("air1", "air2"))
        assertTrue("stock gasket limit should be low", withoutStuds.gasketLimitBar < 130.0)
        assertTrue("studded gasket should hold much more", withStuds.gasketLimitBar > 280.0)
        assertTrue("the turbo node must require the head studs", "mech2" in NODE_BY_ID["air3"]!!.req)
    }

    // --------------------------------------------------------------- economy

    @Test
    fun `running at rated load makes money`() {
        val sim = Sim(1000)
        val cash0 = sim.cash
        bringOnLine(sim, 40.0)
        sim.changeTimeScale(15)
        repeat(20000) { sim.update(0.05) }   // several game hours
        val delta = sim.cash - cash0
        val kWh = sim.campaign.totalDeliveredKWh
        // Milestone awards and dispatch fees also land in cash, so judge the
        // operating margin on energy revenue against fuel burnt.
        val revenue = sim.days.sumOf { it.revenue }
        val fuel = sim.days.sumOf { it.fuelCost }
        val margin = (revenue - fuel) / kWh
        println("delivered %.0f kWh, cash change %s, revenue %s, fuel %s, margin %.3f $/kWh"
            .format(kWh, sim.money(delta), sim.money(revenue), sim.money(fuel), margin))
        assertTrue("should have delivered energy", kWh > 20.0)
        assertTrue("a loaded unit should be profitable", delta > 0.0)
        assertTrue("selling energy should beat the fuel it takes", revenue > fuel)
        assertTrue("margin %.3f is not a believable fraction of the tariff".format(margin),
            margin in 0.05..0.55)
    }

    @Test
    fun `fuel burn matches the energy sold`() {
        val sim = Sim(2000)
        val u = bringOnLine(sim, 40.0)
        val fuel0 = u.fuelUsedL
        val kWh0 = sim.campaign.totalDeliveredKWh
        sim.changeTimeScale(15)
        repeat(20000) { sim.update(0.05) }
        val litres = u.fuelUsedL - fuel0
        val kWh = sim.campaign.totalDeliveredKWh - kWh0
        val lPerKWh = litres / kWh
        println("%.1f L for %.1f kWh = %.3f L/kWh".format(litres, kWh, lPerKWh))
        assertTrue("$lPerKWh L/kWh is not a plausible specific consumption", lPerKWh in 0.26..0.50)
    }

    // ------------------------------------------------------------- stability

    @Test
    fun `a fast-forwarded month stays finite and sane`() {
        val sim = Sim(31337)
        val day0 = sim.gameSeconds / 86400.0
        bringOnLine(sim, 35.0)
        sim.changeTimeScale(300)
        repeat(120000) {
            sim.update(0.05)
            // Keep it fuelled, the way a player would.
            if (sim.fuelL < 120.0) sim.fillTank()
        }

        val elapsed = sim.gameSeconds / 86400.0 - day0
        println("ran %.1f days: cash=%s fuel=%.0f L f=%.2f Hz V=%.3f rep=%.2f kWh=%.0f".format(
            elapsed, sim.money(sim.cash), sim.fuelL, sim.grid.frequencyHz, sim.grid.busVoltPU,
            sim.campaign.reputation, sim.campaign.totalDeliveredKWh))
        assertTrue(sim.cash.isFinite())
        assertTrue(sim.grid.frequencyHz.isFinite())
        assertTrue(sim.grid.busVoltPU.isFinite())
        assertTrue(sim.fuelL >= 0.0)
        for (u in sim.units) {
            assertTrue("rpm went non-finite", u.rpm.isFinite())
            assertTrue("coolant went non-finite", u.coolantC.isFinite())
            assertTrue("coolant ${u.coolantC} is absurd", u.coolantC in -60.0..200.0)
            assertTrue("oil temp ${u.oilC} is absurd", u.oilC in -60.0..250.0)
            assertTrue("EGT ${u.egtC} is absurd", u.egtC in -60.0..1000.0)
            assertTrue("winding ${u.windingC} is absurd", u.windingC in -60.0..400.0)
            assertTrue("oil pressure ${u.oilPressureBar} is absurd",
                u.oilPressureBar in 0.0..EngineBase.OIL_RELIEF_BAR + 0.01)
            assertTrue("wear out of range", u.wear.bearings in 0.0..1.0)
        }
        assertTrue("at least three weeks should have passed, got %.1f days".format(elapsed), elapsed > 20.0)
    }

    @Test
    fun `the town is never left dark while the co-op has capacity`() {
        val sim = Sim(555)
        sim.changeTimeScale(60)
        var blackoutTicks = 0
        repeat(30000) {
            sim.update(0.05)
            if (sim.lastSnapshot.blackout) blackoutTicks++
        }
        println("blackout ticks: $blackoutTicks of 30000")
        assertTrue("the co-op should keep the lights on by itself", blackoutTicks < 600)
    }

    // -------------------------------------------------------------- campaign

    @Test
    fun `the megawatt is actually reachable`() {
        // Buy the whole tree, then check the plant can physically hold 1 MW.
        val all = NODES.map { it.id }.toSet()
        val plant = buildPlantSpec(all)
        val unit8 = buildUnit8Spec(all)
        println("slots=${plant.unitSlots} maxUnit=${plant.maxUnitKW} unit8=${unit8.ratedKW}")
        assertTrue("need enough slots", plant.unitSlots >= 6)
        val best = unit8.ratedKW + (plant.unitSlots - 1) * plant.maxUnitKW
        assertTrue("max achievable $best kW must clear a megawatt", best >= MEGAWATT_KW)
        assertTrue(plant.n1Certified)
    }

    @Test
    fun `market listings price and spec sensibly`() {
        val rng = Rng(12345)
        val m = Market(rng)
        m.refresh(0, buildPlantSpec(NODES.map { it.id }.toSet()), 0.5)
        assertTrue(m.listings.isNotEmpty())
        for (l in m.listings) {
            val s = l.toSpec("Test")
            println("%-24s %5.0f kW  cond %.2f  %s  spec-rated %.0f kW"
                .format(l.make, l.kW, l.condition, l.conditionText, s.ratedKW))
            assertTrue("price should be positive", l.totalCost > 0)
            assertEquals("spec rating should match the listing", l.kW, s.ratedKW, 0.01)
            assertTrue("implied efficiency should be plausible", s.indEffPeak in 0.30..0.50)
        }
    }
}
