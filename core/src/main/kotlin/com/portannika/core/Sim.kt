package com.portannika.core

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.roundToInt

/* ============================================================================
 *  The world. Owns the clock, the money, the machines and the grid, and
 *  exposes a small command surface for the UI to drive.
 * ========================================================================== */

enum class LogLevel { INFO, GOOD, WARN, ALARM }

data class LogEntry(val gameSeconds: Double, val text: String, val level: LogLevel) {
    val stamp: String get() = calendarOf(gameSeconds).let { "${it.monthName} ${it.day} ${it.clock}" }
}

data class LedgerEntry(val gameSeconds: Double, val text: String, val amount: Double, val category: String)

data class DaySummary(
    val day: Int,
    var revenue: Double = 0.0,
    var fuelCost: Double = 0.0,
    var maintenance: Double = 0.0,
    var capital: Double = 0.0,
    var kWh: Double = 0.0,
    var runHours: Double = 0.0,
) {
    val net: Double get() = revenue - fuelCost - maintenance - capital
}

class Sim(seed: Int = 20260811) {

    val rng = Rng(seed)
    val grid = Grid(rng)
    val market = Market(rng)
    val campaign = Campaign()

    var gameSeconds = 4.0 * 86400.0 + 6.0 * 3600.0   // start Jan 5th, 06:00
        private set
    var timeScale = 1
        private set
    var paused = false

    var cash = Econ.STARTING_CASH
        private set
    var fuelL = 560.0
        private set
    var fuelPricePerL = Econ.FUEL_PRICE_PER_L
        private set

    var ownedTech = mutableSetOf<String>()
        private set
    var plant: PlantSpec = buildPlantSpec(emptySet())
        private set

    val units = mutableListOf<Genset>()
    var selectedUnitId: String = "u8"

    val log = ArrayDeque<LogEntry>()
    val ledger = ArrayDeque<LedgerEntry>()
    val days = mutableListOf<DaySummary>()

    var autoPlant = false
    var lastSnapshot: GridSnapshot = GridSnapshot(
        Nominal.FREQ, 1.0, 480.0, 7200.0, 360.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, false, false
    )
        private set

    var gameWon = false
        private set

    // Accumulators reset each in-game day.
    private var lastDay = -1
    private var qualityPenaltyAccum = 0.0
    private var monthlyBonusDay = -1

    init {
        val u8 = Genset("u8", buildUnit8Spec(emptySet()), isUnit8 = true)
        u8.coolantC = 6.0; u8.oilC = 6.0; u8.windingC = 6.0
        units += u8
        grid.updateWeather(gameSeconds)
        market.refresh(currentDay(), plant, campaign.reputation)
        logMsg("Port Annika Municipal Power. Unit 8 is yours; the other seven are the co-op's.", LogLevel.INFO)
        logMsg("Sync to the station bus and start selling. The goal is a megawatt of your own.", LogLevel.INFO)
    }

    // ------------------------------------------------------------ accessors

    val selectedUnit: Genset get() = units.find { it.id == selectedUnitId } ?: units.first()
    val unit8: Genset get() = units.first { it.isUnit8 }
    val installedKW: Double get() = units.sumOf { it.spec.ratedKW }
    val onlineKW: Double get() = units.filter { it.onBus }.sumOf { it.elecKW }
    val date: GameDate get() = calendarOf(gameSeconds)
    fun currentDay() = (gameSeconds / 86400.0).toInt()
    val ratePerKWh: Double
        get() = Econ.ratePerKWh(campaign.reputation) *
            (if (campaign.baseloadContract) 1.10 else 1.0) *
            (if (plant.hasOwnBus) 1.04 else 1.0)
    val effectiveFuelPrice: Double get() = fuelPricePerL * plant.fuelPriceMul

    /** Largest single unit -- what N-1 has to survive. */
    val largestUnitKW: Double get() = units.maxOfOrNull { it.spec.ratedKW } ?: 0.0
    val n1CapacityKW: Double get() = installedKW - largestUnitKW

    // --------------------------------------------------------------- update

    fun update(realDtSeconds: Double) {
        if (paused || gameWon) return
        val gameDt = realDtSeconds * timeScale
        if (gameDt <= 0.0) return

        val quasi = timeScale > QUASI_STEADY_ABOVE
        // One second, not more: the synchroscope pointer sweeps 36 degrees a
        // second at a typical closing slip, and a longer stride steps straight
        // over the window you are trying to close in.
        val dtMax = if (quasi) 1.0 else 0.05
        val n = ceil(gameDt / dtMax).toInt().clamp(1, 150)
        val dt = gameDt / n
        repeat(n) { stepOnce(dt, quasi) }
    }

    private fun stepOnce(dt: Double, quasiSteady: Boolean) {
        gameSeconds += dt
        grid.updateWeather(gameSeconds)
        grid.spawnRandomEvents(dt, gameSeconds)

        val env = Env(grid.ambientC, Weather.BARO_KPA * 1000.0)
        val growthYears = (gameSeconds / 86400.0 / 365.0)

        // ---- phase one: prime movers -----------------------------------
        for (u in units) u.preStep(dt, env, grid.frequencyHz)

        // ---- dispatch and automation -----------------------------------
        val demandNow = grid.baseDemandKW(gameSeconds, growthYears)
        // Credit the player only for power actually flowing, and discount it:
        // the co-op will not shed its own reserve on the strength of a machine
        // that might open its breaker in the next minute.
        val playerFirm = units.filter { it.onBus }.sumOf { it.elecKW } * 0.7
        grid.dispatchStation(demandNow, playerFirm)
        if (autoPlant) runPlantController(dt)

        // ---- phase two: the bus ----------------------------------------
        val snap = grid.step(dt, gameSeconds, growthYears, units, quasiSteady, env)
        lastSnapshot = snap

        for (u in units) {
            val assigned = if (u.onBus) u.brakeKW * u.spec.altEff else 0.0
            u.postStep(dt, env, grid.frequencyHz, grid.busVoltPU, assigned, u.kvar, quasiSteady)
        }

        // Consume fuel from the shared tank.
        var kgThisStep = 0.0
        for (u in units) kgThisStep += u.fuelRateKgS * dt
        val litres = kgThisStep / EngineBase.FUEL_DENSITY
        if (litres > 0.0) {
            if (fuelL >= litres) {
                fuelL -= litres
                for (u in units) u.fuelUsedL += (u.fuelRateKgS * dt / EngineBase.FUEL_DENSITY)
            } else {
                fuelL = 0.0
                for (u in units) if (u.isRunning) {
                    u.requestStop()
                    logMsg("${u.spec.name} ran the tank dry and shut down.", LogLevel.ALARM)
                }
            }
        }

        settleMoney(dt, snap)
        campaign.maybeIssueOrder(rng, gameSeconds, dt, plant, installedKW)
        for ((msg, amount) in campaign.stepOrders(gameSeconds, dt, snap.playerKW)) {
            if (amount != 0.0) postLedger(msg, amount, "dispatch")
            logMsg(msg, if (amount >= 0) LogLevel.GOOD else LogLevel.WARN)
        }

        campaign.peakDeliveredKW = maxOf(campaign.peakDeliveredKW, snap.playerKW)
        for (m in campaign.checkMilestones(units, plant, installedKW)) {
            cash += m.reward
            campaign.reputation = (campaign.reputation + m.repReward).clamp(0.0, 1.0)
            if (m.reward > 0) postLedger("Milestone: ${m.title}", m.reward, "milestone")
            logMsg("MILESTONE -- ${m.title}. ${m.subtitle}", LogLevel.GOOD)
            if (m.id == "megawatt") {
                gameWon = true
                logMsg("Port Annika has its first megawatt, and it is yours.", LogLevel.GOOD)
            }
        }

        collectAlarms()
        rolloverDay()
    }

    // --------------------------------------------------------------- money

    private fun settleMoney(dt: Double, snap: GridSnapshot) {
        val hours = dt / 3600.0
        val today = daySummary()

        if (!snap.blackout) {
            val kWh = snap.playerKW.coerceAtLeast(0.0) * hours
            if (kWh > 0.0) {
                val gross = kWh * ratePerKWh
                val net = gross * (1.0 - plant.wheelingFrac)
                cash += net
                today.revenue += net
                today.kWh += kWh
                campaign.totalDeliveredKWh += kWh
            }
        }

        // Heat sold into the district loop.
        var heatKW = 0.0
        for (u in units) if (u.isRunning) {
            heatKW += u.recoveredHeatKW
            if (u.spec.economizerFrac > 0.0) heatKW += u.exhaustHeatKW * u.spec.economizerFrac
        }
        if (heatKW > 0.0) {
            val v = heatKW * hours * Econ.HEAT_RATE_PER_KWH_TH
            cash += v
            today.revenue += v
        }

        // Fuel is an expense at the moment it is burnt, valued at what it cost.
        var kg = 0.0
        for (u in units) kg += u.fuelRateKgS * dt
        if (kg > 0.0) today.fuelCost += kg / EngineBase.FUEL_DENSITY * effectiveFuelPrice

        for (u in units) if (u.isRunning) today.runHours += hours

        // Power quality: the co-op docks you while you are on the bus and the
        // frequency is outside the band. Your droop and your speeder decide it.
        val anyOnBus = units.any { it.onBus }
        if (anyOnBus && !snap.blackout) {
            val err = abs(snap.frequencyHz - Nominal.FREQ)
            if (err > Nominal.FREQ_BAND) {
                val p = Econ.FREQ_QUALITY_PENALTY_PER_MIN * (dt / 60.0) *
                    ((err - Nominal.FREQ_BAND) / Nominal.FREQ_BAND).coerceAtMost(4.0)
                cash -= p
                qualityPenaltyAccum += p
                today.maintenance += p
            }
        }

        // If you took a job and are not doing it while the town is short, you pay.
        val active = campaign.activeOrder
        if (active != null && (snap.shedKW > 0.0 || snap.blackout) && snap.playerKW < active.targetKW * 0.8) {
            val p = snap.shedKW.coerceAtLeast(10.0) * hours * Econ.PENALTY_PER_BLACKOUT_KWH
            cash -= p
            today.maintenance += p
        }
    }

    private fun daySummary(): DaySummary {
        val d = currentDay()
        if (days.isEmpty() || days.last().day != d) days += DaySummary(d)
        return days.last()
    }

    private fun rolloverDay() {
        val d = currentDay()
        if (d == lastDay) return
        lastDay = d

        // Fuel price drifts with the barge schedule.
        fuelPricePerL = (Econ.FUEL_PRICE_PER_L *
            (0.88 + 0.30 * valueNoise(d / 22.0, 991))).clamp(0.62, 1.55)

        if (d >= market.nextRefreshDay) {
            market.refresh(d, plant, campaign.reputation)
            if (plant.hasSwitchgear) logMsg("The barge called. New machinery listings on the board.", LogLevel.INFO)
        }

        if (qualityPenaltyAccum > 1.0) {
            postLedger("Power quality penalties", -qualityPenaltyAccum, "penalty")
        }
        qualityPenaltyAccum = 0.0

        // Monthly reliability bonus.
        val cal = calendarOf(gameSeconds)
        if (cal.day == 1 && monthlyBonusDay != d) {
            monthlyBonusDay = d
            if (campaign.reputation > 0.55 && campaign.ordersAnswered > campaign.ordersMissed) {
                val bonus = Econ.RELIABILITY_BONUS * campaign.reputation
                cash += bonus
                postLedger("Monthly availability bonus", bonus, "bonus")
                logMsg("Availability bonus paid: %s".format(money(bonus)), LogLevel.GOOD)
            }
        }
        if (days.size > 400) days.removeAt(0)
    }

    // ---------------------------------------------------------- automation

    /** Runs the plant hands-off once you own the automation to do it. */
    private fun runPlantController(dt: Double) {
        val order = campaign.activeOrder
        val target = order?.targetKW ?: 0.0
        for (u in units) {
            if (!u.spec.autoStart) continue
            if (target > 0.0) {
                if (!u.isRunning) u.requestStart()
                else if (!u.onBus && u.spec.autoSync) {
                    val c = u.syncCheck(grid.frequencyHz, grid.busVoltPU)
                    // Walk the speeder until the slip is right, then close.
                    u.speederPU = if (c.slipHz < 0.05) (u.speederPU + 0.0006).coerceAtMost(GovernorBase.SPEEDER_MAX)
                    else if (c.slipHz > 0.25) (u.speederPU - 0.0006).coerceAtLeast(GovernorBase.SPEEDER_MIN)
                    else u.speederPU
                    if (c.allOk && c.directionOk) u.closeBreaker(grid.frequencyHz, grid.busVoltPU, false)
                } else if (u.onBus) {
                    val share = target / units.count { it.onBus }.coerceAtLeast(1)
                    val err = share - u.elecKW
                    u.speederPU = (u.speederPU + err * 0.00004 * dt * 60.0)
                        .clamp(GovernorBase.SPEEDER_MIN, GovernorBase.SPEEDER_MAX)
                }
            } else if (u.onBus && u.elecKW < 2.0) {
                u.openBreaker("Automatic unload")
            }
        }
    }

    /**
     * Time spent with the spanners is time the rest of the plant keeps running.
     * Advancing the world properly means the other machines go on making money
     * and burning fuel while one of them is stripped down.
     */
    private fun fastForwardHours(hours: Double) {
        if (hours <= 0.0) return
        val total = hours * 3600.0
        val dt = 60.0
        var elapsed = 0.0
        var guard = 0
        while (elapsed < total && guard++ < 40000) {
            stepOnce(dt, quasiSteady = true)
            elapsed += dt
        }
    }

    // ------------------------------------------------------------- commands

    fun changeTimeScale(s: Int) { timeScale = s.clamp(1, 1000) }
    fun togglePause() { paused = !paused }
    fun selectUnit(id: String) { if (units.any { it.id == id }) selectedUnitId = id }

    fun startUnit(id: String) {
        val u = units.find { it.id == id } ?: return
        if (fuelL < 5.0) { logMsg("No fuel in the tank.", LogLevel.WARN); return }
        u.requestStart()
        logMsg("${u.spec.name}: cranking.", LogLevel.INFO)
    }

    fun stopUnit(id: String) {
        val u = units.find { it.id == id } ?: return
        if (u.onBus) { logMsg("${u.spec.name}: open the breaker before shutting down.", LogLevel.WARN); return }
        u.requestStop()
        logMsg("${u.spec.name}: fuel off, cooling down.", LogLevel.INFO)
    }

    fun emergencyStop(id: String) {
        units.find { it.id == id }?.let {
            it.emergencyStop("Operator emergency stop")
            logMsg("${it.spec.name}: EMERGENCY STOP.", LogLevel.ALARM)
        }
    }

    fun closeBreaker(id: String, force: Boolean = false): String {
        val u = units.find { it.id == id } ?: return "No such unit"
        if (!u.isRunning) return "Unit is not running"
        val shock = u.closeBreaker(grid.frequencyHz, grid.busVoltPU, force)
        return when {
            shock < 0 -> "Synchroscope is not in the window -- check slip, angle and volts"
            shock < 0.6 -> { logMsg("${u.spec.name} on line. Clean close.", LogLevel.GOOD); "On line, clean close" }
            shock < 1.5 -> { logMsg("${u.spec.name} on line with a bump.", LogLevel.INFO); "On line, slight bump" }
            shock < 3.0 -> { logMsg("${u.spec.name} closed out of phase -- hard bump.", LogLevel.WARN); "Hard close -- that hurt something" }
            else -> { logMsg("${u.spec.name} closed badly out of phase.", LogLevel.ALARM); "Violent close -- serious damage" }
        }
    }

    fun openBreaker(id: String) {
        val u = units.find { it.id == id } ?: return
        if (u.elecKW > u.spec.ratedKW * 0.12) {
            logMsg("${u.spec.name}: unload with the speeder before opening the breaker.", LogLevel.WARN)
        }
        u.openBreaker("Operator opened the breaker")
        logMsg("${u.spec.name} off line.", LogLevel.INFO)
    }

    fun adjustSpeeder(id: String, delta: Double) {
        val u = units.find { it.id == id } ?: return
        u.speederPU = (u.speederPU + delta).clamp(GovernorBase.SPEEDER_MIN, GovernorBase.SPEEDER_MAX)
    }

    fun setSpeeder(id: String, value: Double) {
        units.find { it.id == id }?.speederPU = value.clamp(GovernorBase.SPEEDER_MIN, GovernorBase.SPEEDER_MAX)
    }

    fun setDroop(id: String, value: Double) {
        val u = units.find { it.id == id } ?: return
        u.droop = value.clamp(u.spec.droopMin, u.spec.droopMax)
    }

    fun setField(id: String, value: Double) { units.find { it.id == id }?.fieldRheostat = value.clamp(0.0, 1.0) }
    fun setAvrSetpoint(id: String, value: Double) { units.find { it.id == id }?.avrSetpointPU = value.clamp(0.90, 1.10) }
    fun setPrelube(id: String, on: Boolean) { units.find { it.id == id }?.prelubeRunning = on }

    // -------------------------------------------------------------- economy

    fun buyFuel(litres: Double): String {
        val space = plant.fuelTankL - fuelL
        val take = litres.coerceAtMost(space)
        if (take <= 1.0) return "Tank is full"
        val cost = take * effectiveFuelPrice
        if (cost > cash) return "Not enough cash"
        cash -= cost
        fuelL += take
        postLedger("Fuel, %.0f L at %s/L".format(take, money(effectiveFuelPrice)), -cost, "fuel")
        daySummary().fuelCost += 0.0   // fuel is expensed as it burns
        return "Took %.0f L".format(take)
    }

    fun fillTank(): String = buyFuel(plant.fuelTankL - fuelL)

    fun buyTech(id: String): String {
        val node = NODE_BY_ID[id] ?: return "Unknown upgrade"
        if (id in ownedTech) return "Already fitted"
        if (!isUnlockable(node, ownedTech)) return "Prerequisites not met"
        if (node.cost > cash) return "Not enough cash"

        // Work on the machine means the machine is down.
        val u8 = unit8
        if (node.branch !in listOf("ctrl", "plant", "recov") && u8.isRunning) {
            return "Shut Unit 8 down before working on it"
        }

        cash -= node.cost
        postLedger("Upgrade: ${node.name}", -node.cost, "capital")
        daySummary().capital += node.cost
        fastForwardHours(installHours(node))
        ownedTech.add(id)
        rebuildSpecs()
        logMsg("Fitted: ${node.name}. ${node.effect}", LogLevel.GOOD)
        return "Fitted ${node.name}"
    }

    /** How long a job is, roughly: a rheostat is an afternoon, a rebuild is weeks. */
    fun installHours(node: TechNode): Double =
        (node.cost / 320.0).clamp(2.0, 90.0)

    private fun rebuildSpecs() {
        plant = buildPlantSpec(ownedTech)
        for (u in units) {
            u.spec = if (u.isUnit8) buildUnit8Spec(ownedTech).applyPlantControls(ownedTech, plant)
            else u.spec.applyPlantControls(ownedTech, plant)
            u.droop = u.droop.coerceAtLeast(u.spec.droopMin)
        }
    }

    fun buyMachine(listingId: String): String {
        val l = market.listings.find { it.id == listingId } ?: return "Listing is gone"
        if (!plant.hasSwitchgear) return "You need paralleling switchgear before a second machine"
        if (units.size >= plant.unitSlots) return "No room -- the plant has ${plant.unitSlots} unit slots"
        if (l.kW > plant.maxUnitKW) return "Too big for this plant (limit %.0f kW)".format(plant.maxUnitKW)
        if (l.totalCost > cash) return "Not enough cash"

        cash -= l.totalCost
        val n = 8 + units.size
        val id = "u${n + 1}"
        val spec = l.toSpec("Unit ${n + 1}").applyPlantControls(ownedTech, plant)
        val g = Genset(id, spec, isUnit8 = false)
        // A used machine arrives with the wear its hours imply.
        val w = (1.0 - l.condition)
        g.wear = Wear(
            bearings = w * 0.55, rings = w * 0.60, injectors = w * 0.65,
            gasket = w * 0.35, turbo = if (l.turbocharged) w * 0.50 else 0.0,
            alternator = w * 0.40, governor = w * 0.45,
        )
        g.runHours = l.hoursOnClock
        g.coolantC = grid.ambientC
        g.oilC = grid.ambientC
        g.windingC = grid.ambientC
        units += g
        market.remove(listingId)
        postLedger("Purchased ${l.make} (${l.kW.roundToInt()} kW)", -l.totalCost, "capital")
        daySummary().capital += l.totalCost
        logMsg("Unit ${n + 1} landed: ${l.make}, ${l.kW.roundToInt()} kW, ${l.conditionText.lowercase()}.", LogLevel.GOOD)
        return "Bought ${l.make}"
    }

    fun sellMachine(id: String): String {
        val u = units.find { it.id == id } ?: return "No such unit"
        if (u.isUnit8) return "Unit 8 is not for sale. It is where you started."
        if (u.isRunning) return "Shut it down first"
        val cond = 1.0 - u.wear.worst().second
        val value = u.spec.ratedKW * lerp(90.0, 260.0, cond.clamp(0.0, 1.0))
        cash += value
        units.remove(u)
        if (selectedUnitId == id) selectedUnitId = "u8"
        postLedger("Sold ${u.spec.name}", value, "capital")
        logMsg("Sold ${u.spec.name} for ${money(value)}.", LogLevel.INFO)
        return "Sold for ${money(value)}"
    }

    fun doService(unitId: String, key: String): String {
        val u = units.find { it.id == unitId } ?: return "No such unit"
        val item = CONSUMABLES.find { it.key == key } ?: return "Unknown service"
        if (u.isRunning) return "Shut the unit down first"
        val cost = item.cost * (0.85 + 0.35 * (u.spec.ratedKW / 50.0).clamp(0.6, 4.0))
        if (cost > cash) return "Not enough cash"
        cash -= cost
        fastForwardHours(item.hours)
        u.performService(key)
        postLedger("${item.name} -- ${u.spec.name}", -cost, "maintenance")
        daySummary().maintenance += cost
        logMsg("${u.spec.name}: ${item.name} done (${item.hours} h).", LogLevel.INFO)
        return "Done"
    }

    fun doRepair(unitId: String, key: String): String {
        val u = units.find { it.id == unitId } ?: return "No such unit"
        val item = REPAIRS.find { it.key == key } ?: return "Unknown repair"
        if (u.isRunning) return "Shut the unit down first"
        val cost = item.cost * (0.8 + 0.4 * (u.spec.ratedKW / 50.0).clamp(0.6, 5.0))
        if (cost > cash) return "Not enough cash"
        cash -= cost
        fastForwardHours(item.hours)
        u.performRepair(key)
        postLedger("${item.name} -- ${u.spec.name}", -cost, "maintenance")
        daySummary().maintenance += cost
        logMsg("${u.spec.name}: ${item.name} complete.", LogLevel.GOOD)
        return "Done"
    }

    fun acceptOrder(id: String) {
        campaign.accept(id)
        logMsg("Accepted dispatch order.", LogLevel.INFO)
    }

    fun declineOrder(id: String) {
        campaign.decline(id)
        logMsg("Declined dispatch order.", LogLevel.WARN)
    }

    // ------------------------------------------------------------ logging

    private val seenAlarms = mutableSetOf<String>()

    private fun collectAlarms() {
        val now = mutableSetOf<String>()
        for (u in units) for (a in u.alarms) {
            val k = "${u.id}:${a.code}"
            now += k
            if (k !in seenAlarms) {
                logMsg("${u.spec.name}: ${a.text}", if (a.critical) LogLevel.ALARM else LogLevel.WARN)
            }
        }
        for (u in units) u.lastTripReason?.let {
            val k = "${u.id}:trip:$it"
            if (k !in seenAlarms) { logMsg("${u.spec.name}: $it", LogLevel.ALARM); now += k }
            else now += k
        }
        seenAlarms.retainAll(now)
        seenAlarms.addAll(now)
    }

    fun logMsg(text: String, level: LogLevel) {
        log.addLast(LogEntry(gameSeconds, text, level))
        while (log.size > 300) log.removeFirst()
    }

    private fun postLedger(text: String, amount: Double, category: String) {
        ledger.addLast(LedgerEntry(gameSeconds, text, amount, category))
        while (ledger.size > 300) ledger.removeFirst()
    }

    // -------------------------------------------------------------- helpers

    fun money(v: Double): String {
        val neg = v < 0
        val a = kotlin.math.abs(v)
        return (if (neg) "-$" else "$") + if (a >= 10000) "%,.0f".format(a) else "%,.2f".format(a)
    }

    /** Everything the alarm banner needs, worst first. */
    fun activeAlarms(): List<Pair<String, Alarm>> =
        units.flatMap { u -> u.alarms.map { u.spec.name to it } }
            .sortedByDescending { it.second.critical }

    // ---------------------------------------------------------------- saving

    /** Overwrite this world with a saved one. See Save.kt. */
    fun applySave(s: SaveState) {
        rng.restore(s.rngState)
        gameSeconds = s.gameSeconds
        timeScale = s.timeScale
        cash = s.cash
        fuelL = s.fuelL
        fuelPricePerL = s.fuelPricePerL
        ownedTech = s.ownedTech.toMutableSet()
        plant = buildPlantSpec(ownedTech)
        autoPlant = s.autoPlant
        gameWon = s.gameWon

        units.clear()
        for (us in s.units) {
            val g = Genset(us.id, us.spec, us.isUnit8)
            g.runState = runCatching { RunState.valueOf(us.runState) }.getOrDefault(RunState.STOPPED)
            g.rpm = us.rpm; g.rack = us.rack; g.fieldPU = us.fieldPU; g.emfPU = us.emfPU
            g.boostBar = us.boostBar
            g.coolantC = us.coolantC; g.oilC = us.oilC; g.egtC = us.egtC; g.windingC = us.windingC
            g.batterySoC = us.batterySoC; g.phaseDeg = us.phaseDeg
            g.breakerClosed = us.breakerClosed; g.onBus = us.onBus
            g.fuelValveOpen = us.fuelValveOpen
            g.speederPU = us.speederPU; g.droop = us.droop
            g.fieldRheostat = us.fieldRheostat
            g.avrSetpointPU = us.avrSetpointPU; g.avrVoltDroop = us.avrVoltDroop
            g.runHours = us.runHours; g.lifetimeKWh = us.lifetimeKWh; g.fuelUsedL = us.fuelUsedL
            g.starts = us.starts
            g.wear = us.wear.copy(); g.service = us.service.copy()
            g.airFilterFouling = us.airFilterFouling
            g.fuelFilterFouling = us.fuelFilterFouling
            g.radiatorFouling = us.radiatorFouling
            g.oilCondition = us.oilCondition
            g.failureText = us.failureText
            units += g
        }
        if (units.none { it.isUnit8 }) {
            units.add(0, Genset("u8", buildUnit8Spec(ownedTech), true))
        }
        selectedUnitId = if (units.any { it.id == s.selectedUnitId }) s.selectedUnitId else units.first().id

        for (ss in s.station) {
            val u = grid.stationUnits.find { it.spec.id == ss.id } ?: continue
            u.online = ss.online; u.starting = ss.starting; u.startTimer = ss.startTimer
            u.speedSetPU = ss.speedSetPU; u.outputKW = ss.outputKW; u.emfPU = ss.emfPU
            u.runHours = ss.runHours; u.failed = ss.failed; u.failedFor = ss.failedFor
        }
        grid.frequencyHz = s.frequencyHz
        grid.busVoltPU = s.busVoltPU
        grid.unservedKWh = s.unservedKWh
        grid.updateWeather(gameSeconds)

        market.listings = s.listings.toMutableList()
        market.nextRefreshDay = s.nextRefreshDay

        campaign.completed.clear(); campaign.completed += s.completed
        campaign.orders.clear(); campaign.orders += s.orders
        campaign.reputation = s.reputation
        campaign.peakDeliveredKW = s.peakDeliveredKW
        campaign.totalDeliveredKWh = s.totalDeliveredKWh
        campaign.ordersAnswered = s.ordersAnswered
        campaign.ordersMissed = s.ordersMissed
        campaign.baseloadContract = "baseload" in campaign.completed

        log.clear()
        for (l in s.log) {
            log.addLast(LogEntry(l.t, l.text, runCatching { LogLevel.valueOf(l.level) }.getOrDefault(LogLevel.INFO)))
        }
        ledger.clear()
        for (l in s.ledger) ledger.addLast(LedgerEntry(l.t, l.text, l.amount, l.category))
        days.clear()
        for (d in s.days) days += DaySummary(d.day, d.revenue, d.fuelCost, d.maintenance, d.capital, d.kWh, d.runHours)
        lastDay = currentDay()
    }
}
