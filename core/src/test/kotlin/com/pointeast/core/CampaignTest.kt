package com.pointeast.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The order a competent operator would buy things in. Cheap efficiency
 * first, then whichever component is actually capping the machine, then
 * the plant that lets it grow past one engine. Anything not listed is
 * appended, so the plan always terminates.
 */
private val BUILD_ORDER: List<String> = (listOf(
    "cool1", "fuel1", "air1", "elec1", "recov1",
    "mech1", "ctrl1", "elec2", "mech2", "air2",
    "elec3",                       // the alternator is the first ceiling
    "fuel2", "cool2", "air3", "fuel3", "cool3", "mech3",
    "plant1",                      // switchgear: a second machine
    "elec4", "mech4", "elec5",     // 190 kVA frame
    "fuel4", "air4", "mech5", "cool4",
    "ctrl2", "ctrl3", "ctrl4", "ctrl5",
    "plant2", "plant3", "plant4", "plant5", "plant6", "plant7", "plant8",
    "recov2", "recov4", "air5", "fuel5", "ctrl6", "ctrl7",
    "cool5", "recov3", "air6", "fuel6",
) + NODES.map { it.id }).distinct()

/**
 * Drives the simulation through the same command surface the app's screens
 * call, with a scripted operator standing in for the player.
 */
class CampaignTest {

    /**
     * A competent operator: keeps fuel in the tank, keeps machines running and
     * synchronised, trims the field, answers dispatch it can carry, services
     * what is overdue, and works down a build order: keeps fuel in the tank, keeps
     * machines running and synchronised, answers dispatch, services what is
     * saving for the expensive unlocks rather than frittering the money.
     */
    private class Operator(val sim: Sim) {
        var techBought = 0
        var machinesBought = 0
        var servicesDone = 0
        var repairsDone = 0
        var syncAttempts = 0
        var failures = 0
        var firstFailureDay = -1.0
        private var wasFailed = false
        val wearTrace = mutableListOf<String>()
        val repTrace = mutableListOf<String>()
        var onBusTicks = 0
        var runTicks = 0
        private var n = 0
        /** Set while deliberately holding Set 1 down for a job. */
        private var pendingJob: String? = null

        fun tick() {
            n++
            // Operating the machines is a per-instant job; shopping and
            // paperwork are not, and scanning the whole catalogue every tick
            // costs far more than the physics does.
            for (u in sim.units.toList()) operate(u)
            if (sim.units.any { it.onBus }) onBusTicks++
            if (sim.units.any { it.isRunning }) runTicks++
            if (n % 40 == 0) { keepFuelled(); answerDispatch() }
            if (n % 400 == 0) spend()
            if (n % 12000 == 0) {
                val c = sim.campaign
                repTrace += "day %4.0f rep=%.3f full=%d part=%d fail=%d exp=%d dec=%d ms=%d".format(
                    sim.gameSeconds / 86400.0, c.reputation, c.nFull, c.nPartial,
                    c.nFailed, c.nExpired, c.nDeclined, c.completed.size)
            }
        }

        private fun keepFuelled() {
            if (sim.fuelL < sim.plant.fuelTankL * 0.20) {
                sim.buyFuel(sim.plant.fuelTankL * 0.6)
            }
        }

        private fun answerDispatch() {
            val o = sim.campaign.offeredOrder ?: return
            // Only take work the plant can actually carry; a failed order costs
            // money and reputation.
            val capable = sim.units.filter { it.runState != RunState.FAILED }
                .sumOf { it.spec.ratedKW } * 0.85
            if (o.targetKW <= capable) sim.acceptOrder(o.id) else sim.declineOrder(o.id)
        }

        private fun operate(u: Genset) {
            when {
                u.runState == RunState.FAILED -> {
                    if (u.isFoundingSet && !wasFailed) {
                        failures++
                        wasFailed = true
                        if (firstFailureDay < 0) firstFailureDay = sim.gameSeconds / 86400.0
                        wearTrace += "day %.0f (%,.0f h): %s  [oil %.2f bar, %.0f C, cond %.2f]".format(
                            sim.gameSeconds / 86400.0, u.runHours, u.failureText,
                            u.oilPressureBar, u.oilC, u.oilCondition)
                    }
                    val key = worstComponentKey(u)
                    if (sim.doRepair(u.id, key) == "Done") repairsDone++
                }
                !u.isRunning -> {
                    if (u.isFoundingSet) wasFailed = false
                    // Do overdue maintenance while it is already stopped. Oil is
                    // cheap and skipping it is what kills bearings, so it is not
                    // gated on having spare cash.
                    val overdue = CONSUMABLES.firstOrNull { u.service.get(it.key) > it.intervalH }
                    if (overdue != null && (overdue.key == "oil" || sim.cash > 3000)) {
                        if (sim.doService(u.id, overdue.key) == "Done") servicesDone++
                    } else if (u.isFoundingSet && pendingJob != null) {
                        // Held down on purpose: fit the upgrade now.
                        if (sim.buyTech(pendingJob!!).startsWith("Fitted")) techBought++
                        pendingJob = null
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
                    // Hold the slip in the middle of the closing window. It is
                    // only three rpm wide, so this has to be proportional.
                    val want = Nominal.SYNC_SLIP_HZ * 0.55
                    u.speederPU = (u.speederPU + ((want - c.slipHz) * 0.05)
                        .clamp(-0.0010, 0.0010)).clamp(0.93, 1.10)
                    if (c.allOk && c.directionOk) {
                        syncAttempts++
                        sim.closeBreaker(u.id, force = false)
                        if (u.onBus) {
                            // Wind load on straight away. A machine left sitting
                            // at no load on a live bus gets motored.
                            val d = u.droop.coerceAtLeast(u.spec.droopMin)
                            u.speederPU = (sim.grid.frequencyHz / Nominal.FREQ + d * 0.45)
                                .clamp(0.95, 1.10)
                        }
                    }
                }
                else -> {
                    // Hold a load target with proportional control on the error,
                    // so the response does not depend on the tick rate.
                    val hot = u.coolantC > EngineBase.COOLANT_WARN_C - 4 ||
                        u.egtC > EngineBase.EGT_WARN_C - 20 || u.smokeExcess > 0.05
                    val overKva = u.kva > u.spec.ratedKVA * 0.98
                    val target = when {
                        hot || overKva -> u.elecKW * 0.90
                        else -> u.spec.ratedKW * 0.82
                    }
                    val err = (target - u.elecKW) / u.spec.ratedKW
                    u.speederPU = (u.speederPU + (err * 0.02).clamp(-0.004, 0.004))
                        .clamp(0.95, 1.10)

                    // Trim the field to hold something near rated power factor.
                    // On a hand rheostat this is a standing job: leave it where
                    // it was at synchronising and the machine quietly ends up
                    // carrying everyone else's reactive load.
                    if (!u.spec.avr) {
                        val wantKvar = u.elecKW * 0.5
                        val e = (u.kvar - wantKvar) / u.spec.ratedKVA
                        u.fieldRheostat = (u.fieldRheostat - (e * 0.06).clamp(-0.008, 0.008))
                            .clamp(0.0, 1.0)
                    }
                }
            }
        }

        /** Repair whichever destroyed component actually stopped the machine. */
        private fun worstComponentKey(u: Genset): String = when {
            u.wear.bearings >= 0.99 -> "bearings"
            u.wear.rings >= 0.99 -> "rings"
            u.wear.gasket >= 0.99 -> "gasket"
            u.wear.alternator >= 0.99 -> "alternator"
            u.wear.turbo >= 0.99 -> "turbo"
            else -> "bearings"
        }

        /**
         * Spend on whatever is cheapest and available, keeping a working float.
         * Machines get bought whenever there is a slot and the money.
         */
        private fun spend() {
            // Never spend the plant into a state where it cannot repair itself.
            val float = 4500.0

            // A machine is worth more than any upgrade once there is a slot.
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

            // Work down the build order. If the next thing on the list is not
            // affordable yet, save for it rather than frittering the money on
            // whatever happens to be cheap -- otherwise the plant never gets
            // past the first alternator rewind.
            val next = BUILD_ORDER
                .asSequence()
                .mapNotNull { NODE_BY_ID[it] }
                .firstOrNull { it.id !in sim.ownedTech && isUnlockable(it, sim.ownedTech) }
                ?: return
            if (next.cost + float > sim.cash) return

            // Machine work needs the machine down; stop it, buy, restart next tick.
            val needsShutdown = next.branch !in listOf("ctrl", "plant", "recov")
            if (needsShutdown && sim.foundingSet.isRunning) {
                pendingJob = next.id
                if (sim.foundingSet.onBus) sim.openBreaker("g1")
                sim.stopUnit("g1")
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
    fun `a long career grows the plant many times over`() {
        val sim = Sim(1971)
        val op = play(sim, gameDays = 400.0, scale = 300)

        println(
            "end: %.0f days  cash=%s  installed=%.0f kW  units=%d  tech=%d/%d  rep=%.2f".format(
                sim.gameSeconds / 86400.0, sim.money(sim.cash), sim.installedKW,
                sim.units.size, sim.ownedTech.size, NODES.size, sim.campaign.reputation,
            )
        )
        println("   foundingSet rating %.0f kW, peak delivered %.0f kW, %,.0f kWh lifetime".format(
            sim.foundingSet.spec.ratedKW, sim.campaign.peakDeliveredKW, sim.campaign.totalDeliveredKWh))
        println("   on bus %.0f%% / running %.0f%% of the time; %d failures, %d repairs, %d services, %d syncs"
            .format(op.onBusTicks * 100.0 / (op.runTicks + 1), op.runTicks * 100.0 / 480000.0,
                op.failures, op.repairsDone, op.servicesDone, op.syncAttempts))
        println("   revenue %s  fuel %s  maint+penalty %s  capital %s".format(
            sim.money(sim.days.sumOf { it.revenue }), sim.money(-sim.days.sumOf { it.fuelCost }),
            sim.money(-sim.days.sumOf { it.maintenance }), sim.money(-sim.days.sumOf { it.capital })))
        println("   orders answered ${sim.campaign.ordersAnswered} missed ${sim.campaign.ordersMissed}")
        val c = sim.campaign
        println("   rep ledger: full=${c.nFull} partial=${c.nPartial} failed=${c.nFailed} " +
            "expired=${c.nExpired} declined=${c.nDeclined}")
        println("   rep up=%.2f down=%.2f lost to the floor=%.2f  final=%.2f"
            .format(c.repUp, c.repDown, c.repClippedAtZero, c.reputation))
        op.repTrace.forEach { println("   $it") }
        println("   first failure: day %.0f".format(op.firstFailureDay))
        for (t in op.wearTrace.take(6)) println("      $t")
        val tally = sim.log.groupingBy { it.text.take(46) }.eachCount()
            .entries.sortedByDescending { it.value }.take(10)
        println("   most common log lines:")
        for ((text, count) in tally) println("      %5d  %s".format(count, text))
        println("   ledger by category: " +
            sim.ledger.groupBy { it.category }.mapValues { e -> e.value.sumOf { it.amount }.toInt() })
        println("   milestones ${sim.campaign.completed.size}/${MILESTONES.size}: " +
            MILESTONES.filter { it.id in sim.campaign.completed }.joinToString { it.title })
        for (u in sim.units) {
            println("   %-8s %6.0f kW  %,7.0f h  worst %s %.0f%%".format(
                u.spec.name, u.spec.ratedKW, u.runHours, u.wear.worst().first, u.wear.worst().second * 100))
        }

        assertTrue("the plant should have grown several times over, got %.0f kW"
            .format(sim.installedKW), sim.installedKW > 150.0)
        // How many machines it ends up with depends on what the barge happens
        // to bring, so the meaningful claim is that it outgrew the one it
        // started with -- the capacity assertion above carries the weight.
        assertTrue("should have bought machines, got ${sim.units.size}", sim.units.size >= 2)
        assertTrue("should have most of the tech tree, got ${sim.ownedTech.size}/${NODES.size}",
            sim.ownedTech.size > NODES.size / 2)
        assertTrue("Set 1 should be uprated near its ceiling, got %.0f kW"
            .format(sim.foundingSet.spec.ratedKW), sim.foundingSet.spec.ratedKW > 120.0)
        assertTrue("should have cleared most milestones, got ${sim.campaign.completed.size}",
            sim.campaign.completed.size >= 6)
        // A well-run plant should not be destroying engines. Wear is supposed
        // to be a maintenance schedule, not a countdown to a spun bearing.
        assertTrue("a maintained plant should not be failing engines, got ${op.failures}",
            op.failures <= 2)
        for (u in sim.units) {
            assertTrue("${u.spec.name} temperatures must stay physical",
                u.coolantC in -60.0..200.0 && u.oilC in -60.0..250.0)
        }
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
        sim.stopUnit("g1"); sim.openBreaker("g1"); sim.closeBreaker("g1")
        sim.emergencyStop("g1"); sim.adjustSpeeder("g1", 5.0); sim.setDroop("g1", -1.0)
        sim.setField("g1", 9.0); sim.setAvrSetpoint("g1", -3.0); sim.setPrelube("g1", true)
        sim.selectUnit("nope"); sim.stopUnit("nope"); sim.openBreaker("nope")
        sim.buyTech("nonexistent"); sim.buyMachine("nonexistent")
        sim.doService("nope", "oil"); sim.doService("g1", "nope")
        sim.doRepair("nope", "bearings"); sim.doRepair("g1", "nope")
        sim.sellMachine("g1"); sim.acceptOrder("nope"); sim.declineOrder("nope")
        sim.buyFuel(-50.0); sim.buyFuel(1e9); sim.changeTimeScale(99999)
        repeat(200) { sim.update(0.05) }

        assertTrue(sim.cash.isFinite())
        assertEquals("Set 1 must survive all of that", 1, sim.units.size)
        assertTrue(sim.foundingSet.speederPU <= GovernorBase.SPEEDER_MAX)
        assertTrue(sim.foundingSet.droop >= sim.foundingSet.spec.droopMin)
        assertTrue(sim.foundingSet.fieldRheostat in 0.0..1.0)
        assertTrue("fuel must not exceed the tank", sim.fuelL <= sim.plant.fuelTankL + 1e-6)
        assertTrue("cash must not have been spent on nothing", sim.cash <= Econ.STARTING_CASH)
    }

    @Test
    fun `a new career does not open with the city already collapsing`() {
        val sim = Sim(3)
        sim.changeTimeScale(1)
        repeat(300) { sim.update(0.05) }      // the first few seconds on screen
        val s = sim.lastSnapshot
        println("boot: f=%.2f Hz  V=%.3f pu  demand=%.0f kW  gen=%.0f kW  shed=%.0f  PE shed=%.0f"
            .format(s.frequencyHz, s.busVoltsPU, s.cityDemandKW, s.totalGenKW,
                s.shedKW, s.pointEastShedKW))
        assertTrue("frequency should be in band at boot, was %.2f".format(s.frequencyHz),
            kotlin.math.abs(s.frequencyHz - 90.0) < 1.0)
        assertTrue("bus volts should be up at boot, were %.3f".format(s.busVoltsPU),
            s.busVoltsPU > 0.93)
        assertTrue("nothing should be shed at boot, was %.0f kW".format(s.shedKW), s.shedKW < 1.0)
        assertTrue("Point East must not be dark before the player has done anything",
            s.pointEastShedKW < 1.0)
        assertTrue("the other stations should already be carrying the city",
            s.stationKW > s.cityDemandKW * 0.85)
        assertTrue("and the player starts with nothing on the bus", s.playerKW < 0.01)
    }

    @Test
    fun `a big tank must not stop you buying a small amount of fuel`() {
        val sim = Sim(77)
        // Simulate the late game: a fuel farm sized tank and very little cash.
        sim.setCashForTest(200_000.0)
        for (id in listOf("ctrl1", "plant1", "plant2", "plant3", "plant5")) {
            val r = sim.buyTech(id)
            assertTrue("could not fit $id: $r", r.startsWith("Fitted"))
        }
        assertTrue("the fuel farm should be fitted", sim.plant.hasFuelFarm)
        assertTrue("and the tank should be big", sim.plant.fuelTankL > 30_000.0)

        sim.drainCashForTest()
        sim.setCashForTest(400.0)
        val before = sim.fuelL
        val msg = sim.buyFuel(sim.plant.fuelTankL)   // ask to fill it right up
        println("with \$400 and a %.0f L tank: %s".format(sim.plant.fuelTankL, msg))

        val bought = sim.fuelL - before
        assertTrue("four hundred dollars must buy some diesel, got %.0f L".format(bought),
            bought > 100.0)
        assertTrue("and it must not spend money it does not have", sim.cash >= -0.01)
        assertTrue("and it should have spent nearly all of it", sim.cash < 5.0)
    }

    @Test
    fun `buying a machine requires switchgear, a slot and the money`() {
        val sim = Sim(21)
        val listing = sim.market.listings.first()
        assertTrue(sim.buyMachine(listing.id).contains("switchgear"))
        assertEquals(1, sim.units.size)
    }
}
