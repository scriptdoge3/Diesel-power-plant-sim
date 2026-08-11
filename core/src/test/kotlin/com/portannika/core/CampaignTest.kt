package com.portannika.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Drives the simulation through the same command surface the app's screens
 * call, with a scripted operator standing in for the player. If a career can
 * be played to the megawatt here, it can be played on the phone.
 */
class CampaignTest {

    /**
     * A competent but unimaginative operator: keeps fuel in the tank, keeps
     * machines running and synchronised, answers dispatch, services what is
     * overdue, and spends spare cash on the cheapest thing it can fit.
     */
    private class Operator(val sim: Sim) {
        var techBought = 0
        var machinesBought = 0
        var servicesDone = 0
        var repairsDone = 0
        var syncAttempts = 0
        private var n = 0

        fun tick() {
            n++
            // Operating the machines is a per-instant job; shopping and
            // paperwork are not, and scanning the whole catalogue every tick
            // costs far more than the physics does.
            for (u in sim.units.toList()) operate(u)
            if (n % 40 == 0) { keepFuelled(); answerDispatch() }
            if (n % 400 == 0) spend()
        }

        private fun keepFuelled() {
            if (sim.fuelL < sim.plant.fuelTankL * 0.20) {
                sim.buyFuel(sim.plant.fuelTankL * 0.6)
            }
        }

        private fun answerDispatch() {
            sim.campaign.offeredOrder?.let { sim.acceptOrder(it.id) }
        }

        private fun operate(u: Genset) {
            when {
                u.runState == RunState.FAILED -> {
                    // Fix the cheapest thing that will bring it back.
                    val key = worstComponentKey(u)
                    if (sim.doRepair(u.id, key) == "Done") repairsDone++
                }
                !u.isRunning -> {
                    // Do overdue maintenance while it is already stopped.
                    val overdue = CONSUMABLES.firstOrNull { u.service.get(it.key) > it.intervalH }
                    if (overdue != null && sim.cash > 3000) {
                        if (sim.doService(u.id, overdue.key) == "Done") servicesDone++
                    } else if (sim.fuelL > 100) {
                        sim.startUnit(u.id)
                    }
                }
                !u.onBus -> {
                    // Warm it, match volts, come in fast, close on the window.
                    if (u.coolantC < 55.0) return
                    if (!u.spec.avr) {
                        val c = u.syncCheck(sim.grid.frequencyHz, sim.grid.busVoltPU)
                        u.fieldRheostat = (u.fieldRheostat - 0.0012 * c.voltErrPct.coerceIn(-3.0, 3.0))
                            .clamp(0.0, 1.0)
                    }
                    val c = u.syncCheck(sim.grid.frequencyHz, sim.grid.busVoltPU)
                    if (c.slipHz < 0.10) u.speederPU = (u.speederPU + 0.00030).coerceAtMost(1.10)
                    if (c.slipHz > 0.24) u.speederPU = (u.speederPU - 0.00030).coerceAtLeast(0.93)
                    if (c.allOk && c.directionOk) {
                        syncAttempts++
                        sim.closeBreaker(u.id, force = false)
                    }
                }
                else -> {
                    // Load it up, but stop short of the rating and the smoke limit.
                    val target = u.spec.ratedKW * 0.86
                    val hot = u.coolantC > EngineBase.COOLANT_WARN_C - 4 ||
                        u.egtC > EngineBase.EGT_WARN_C - 20 || u.smokeExcess > 0.05
                    val step = if (u.elecKW < target && !hot) 0.00012 else -0.00012
                    u.speederPU = (u.speederPU + step).clamp(0.95, 1.10)
                }
            }
        }

        private fun worstComponentKey(u: Genset): String = when (u.wear.worst().first) {
            "Bearings" -> "bearings"
            "Rings & liners" -> "rings"
            "Head gasket" -> "gasket"
            "Turbocharger" -> "turbo"
            "Alternator" -> "alternator"
            "Governor linkage" -> "governor"
            else -> "full"
        }

        /**
         * Spend on whatever is cheapest and available, keeping a working float.
         * Machines get bought whenever there is a slot and the money.
         */
        private fun spend() {
            val float = 2500.0

            // A machine is usually worth more than the next small upgrade.
            if (sim.plant.hasSwitchgear && sim.units.size < sim.plant.unitSlots) {
                val best = sim.market.listings
                    .filter { it.kW <= sim.plant.maxUnitKW && it.condition > 0.45 }
                    .filter { it.totalCost + float < sim.cash }
                    .maxByOrNull { it.kW }
                if (best != null && sim.buyMachine(best.id).startsWith("Bought")) {
                    machinesBought++
                    return
                }
            }

            val next = NODES
                .filter { it.id !in sim.ownedTech && isUnlockable(it, sim.ownedTech) }
                .filter { it.cost + float < sim.cash }
                .minByOrNull { it.cost } ?: return

            // Machine work needs the machine down; stop it, buy, restart next tick.
            val needsShutdown = next.branch !in listOf("ctrl", "plant", "recov")
            if (needsShutdown && sim.unit8.isRunning) {
                if (sim.unit8.onBus) sim.openBreaker("u8")
                sim.stopUnit("u8")
                return
            }
            if (sim.buyTech(next.id).startsWith("Fitted")) techBought++
        }
    }

    private fun play(sim: Sim, gameDays: Double, scale: Int = 300): Operator {
        val op = Operator(sim)
        sim.changeTimeScale(scale)
        val perStep = 0.05 * scale
        val steps = (gameDays * 86400.0 / perStep).toInt()
        repeat(steps) {
            sim.update(0.05)
            op.tick()
        }
        return op
    }

    // ------------------------------------------------------------------ tests

    @Test
    fun `a scripted operator gets the plant off the ground`() {
        val sim = Sim(4242)
        val op = play(sim, gameDays = 60.0)

        println(
            "60 days: cash=%s  installed=%.0f kW  units=%d  tech=%d  rep=%.2f  kWh=%,.0f".format(
                sim.money(sim.cash), sim.installedKW, sim.units.size,
                sim.ownedTech.size, sim.campaign.reputation, sim.campaign.totalDeliveredKWh,
            )
        )
        println("   milestones: ${sim.campaign.completed.sorted()}")
        println("   synced $op.syncAttempts times, ${op.servicesDone} services, ${op.repairsDone} repairs")

        assertTrue("should have synchronised at least once", op.syncAttempts > 0)
        assertTrue("should have delivered real energy", sim.campaign.totalDeliveredKWh > 2000.0)
        assertTrue("should have bought some upgrades", sim.ownedTech.isNotEmpty())
        assertTrue("should have cleared the first milestones",
            "first_sync" in sim.campaign.completed)
        assertTrue("should not have gone broke", sim.cash > 0.0)
    }

    @Test
    fun `the megawatt can actually be reached by playing`() {
        val sim = Sim(1971)
        val op = play(sim, gameDays = 900.0, scale = 300)

        println(
            "end: %.0f days  cash=%s  installed=%.0f kW  units=%d  tech=%d/%d  rep=%.2f".format(
                sim.gameSeconds / 86400.0, sim.money(sim.cash), sim.installedKW,
                sim.units.size, sim.ownedTech.size, NODES.size, sim.campaign.reputation,
            )
        )
        println("   unit8 rating %.0f kW, peak delivered %.0f kW, %,.0f kWh lifetime".format(
            sim.unit8.spec.ratedKW, sim.campaign.peakDeliveredKW, sim.campaign.totalDeliveredKWh))
        println("   milestones ${sim.campaign.completed.size}/${MILESTONES.size}: " +
            MILESTONES.filter { it.id in sim.campaign.completed }.joinToString { it.title })
        for (u in sim.units) {
            println("   %-8s %6.0f kW  %,7.0f h  worst %s %.0f%%".format(
                u.spec.name, u.spec.ratedKW, u.runHours, u.wear.worst().first, u.wear.worst().second * 100))
        }

        assertTrue("the plant should have grown well past its 50 kW start, got %.0f"
            .format(sim.installedKW), sim.installedKW > 400.0)
        assertTrue("should own several machines", sim.units.size >= 3)
        assertTrue("should have most of the tech tree", sim.ownedTech.size > NODES.size / 2)
        assertTrue("should have cleared most milestones, got ${sim.campaign.completed.size}",
            sim.campaign.completed.size >= 8)
    }

    // ------------------------------------------------------------------ saves

    @Test
    fun `a save round-trips`() {
        val sim = Sim(31337)
        play(sim, gameDays = 25.0)

        val encoded = sim.toSave(31337).encode()
        val decoded = decodeSave(encoded)
        assertNotNull("save should decode", decoded)

        val restored = restoreSim(decoded!!)
        assertNotNull("save should restore", restored)
        requireNotNull(restored)

        assertEquals(sim.gameSeconds, restored.gameSeconds, 1e-6)
        assertEquals(sim.cash, restored.cash, 1e-6)
        assertEquals(sim.fuelL, restored.fuelL, 1e-6)
        assertEquals(sim.ownedTech, restored.ownedTech)
        assertEquals(sim.units.size, restored.units.size)
        assertEquals(sim.campaign.completed, restored.campaign.completed)
        assertEquals(sim.campaign.totalDeliveredKWh, restored.campaign.totalDeliveredKWh, 1e-6)
        assertEquals(sim.installedKW, restored.installedKW, 1e-6)
        for (i in sim.units.indices) {
            assertEquals(sim.units[i].id, restored.units[i].id)
            assertEquals(sim.units[i].rpm, restored.units[i].rpm, 1e-6)
            assertEquals(sim.units[i].coolantC, restored.units[i].coolantC, 1e-6)
            assertEquals(sim.units[i].runHours, restored.units[i].runHours, 1e-6)
            assertEquals(sim.units[i].wear.bearings, restored.units[i].wear.bearings, 1e-9)
            assertEquals(sim.units[i].onBus, restored.units[i].onBus)
        }

        // And it must keep running from where it left off.
        restored.changeTimeScale(15)
        repeat(2000) { restored.update(0.05) }
        assertTrue(restored.cash.isFinite())
        assertTrue(restored.grid.frequencyHz.isFinite())
        println("restored and ran on: %.2f Hz, %s".format(restored.grid.frequencyHz, restored.money(restored.cash)))
    }

    @Test
    fun `a fresh save round-trips before anything has happened`() {
        val sim = Sim(7)
        val restored = restoreSim(decodeSave(sim.toSave(7).encode())!!)
        assertNotNull(restored)
        assertEquals(1, restored!!.units.size)
        assertEquals(sim.cash, restored.cash, 1e-9)
    }

    @Test
    fun `garbage in the save slot is refused rather than crashing`() {
        assertEquals(null, decodeSave("not json at all"))
        assertEquals(null, decodeSave("{}"))
        assertEquals(null, decodeSave(""))
    }

    // ------------------------------------------------- command surface safety

    @Test
    fun `every command is safe to call at any time`() {
        val sim = Sim(11)
        // Fire everything at a stopped, cold, unupgraded plant.
        sim.stopUnit("u8"); sim.openBreaker("u8"); sim.closeBreaker("u8")
        sim.emergencyStop("u8"); sim.adjustSpeeder("u8", 5.0); sim.setDroop("u8", -1.0)
        sim.setField("u8", 9.0); sim.setAvrSetpoint("u8", -3.0); sim.setPrelube("u8", true)
        sim.selectUnit("nope"); sim.stopUnit("nope"); sim.openBreaker("nope")
        sim.buyTech("nonexistent"); sim.buyMachine("nonexistent")
        sim.doService("nope", "oil"); sim.doService("u8", "nope")
        sim.doRepair("nope", "bearings"); sim.doRepair("u8", "nope")
        sim.sellMachine("u8"); sim.acceptOrder("nope"); sim.declineOrder("nope")
        sim.buyFuel(-50.0); sim.buyFuel(1e9); sim.changeTimeScale(99999)
        repeat(200) { sim.update(0.05) }

        assertTrue(sim.cash.isFinite())
        assertEquals("Unit 8 must survive all of that", 1, sim.units.size)
        assertTrue(sim.unit8.speederPU <= GovernorBase.SPEEDER_MAX)
        assertTrue(sim.unit8.droop >= sim.unit8.spec.droopMin)
        assertTrue(sim.unit8.fieldRheostat in 0.0..1.0)
        assertTrue("fuel must not exceed the tank", sim.fuelL <= sim.plant.fuelTankL + 1e-6)
        assertTrue("cash must not have been spent on nothing", sim.cash <= Econ.STARTING_CASH)
    }

    @Test
    fun `buying a machine requires switchgear, a slot and the money`() {
        val sim = Sim(21)
        val listing = sim.market.listings.first()
        assertTrue(sim.buyMachine(listing.id).contains("switchgear"))
        assertEquals(1, sim.units.size)
    }
}
