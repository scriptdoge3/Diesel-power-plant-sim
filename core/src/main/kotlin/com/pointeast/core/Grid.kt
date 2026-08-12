package com.pointeast.core

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sign
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/* ============================================================================
 *  The Dry Green City system.
 *
 *  Every machine on the bus turns at the same speed, so there is one system
 *  frequency and it is set by the balance between what the prime movers make
 *  and what the town takes. Each governor's droop decides how that machine
 *  responds; nobody assigns anyone a load.
 *
 *  Bus voltage is solved rather than assumed. With small angles each machine
 *  pushes reactive power  Q_i = V (E_i - V) / X_i , so summing over the
 *  machines and setting the total equal to the load's reactive demand gives a
 *  quadratic in V. That is why an under-excited machine drags the whole town's
 *  voltage down with it.
 * ========================================================================== */

const val SYSTEM_BASE_KVA = 1000.0

/**
 * How far the frequency is allowed to wander before anyone reaches for a
 * speeder. Inside this the grid just sits where the droop characteristics put
 * it, which is what makes load changes visible on the meter.
 */
const val AGC_DEADBAND_HZ = 0.20

/** One of the other stations. machines. Simplified: no thermodynamics, just droop. */
class CityStation(val spec: StationSpec) {
    var online = false
    var starting = false
    var startTimer = 0.0
    var speedSetPU = 1.0 + spec.droop * 0.5
    var outputKW = 0.0
    var commandKW = 0.0
    var emfPU = 1.05
    var kvar = 0.0
    var runHours = 0.0
    var failed = false
    var failedFor = 0.0

    val ratedKVA: Double get() = spec.kW / 0.8

    /** Droop characteristic evaluated at the current system frequency. */
    fun availableKW(systemFreq: Double): Double {
        if (!online) return 0.0
        val fPU = systemFreq / Nominal.FREQ
        val demand = spec.kW * (speedSetPU - fPU) / spec.droop
        return demand.clamp(-spec.kW * 0.15, spec.kW * 1.10)
    }

    fun step(dt: Double, systemFreq: Double, rng: Rng) {
        if (failed) {
            failedFor -= dt
            outputKW = 0.0
            if (failedFor <= 0.0) { failed = false; online = false }
            return
        }
        if (starting) {
            startTimer -= dt
            if (startTimer <= 0.0) { starting = false; online = true }
            return
        }
        if (!online) { outputKW = 0.0; return }

        runHours += dt / 3600.0
        // Governor lag on the way to the droop demand.
        commandKW = availableKW(systemFreq)
        outputKW = approach(outputKW, commandKW, 1.1, dt)

        // Old machines break. Loaded ones break more often.
        val loadFactor = 0.5 + (outputKW / spec.kW).clamp(0.0, 1.3)
        if (rng.eventOccurs(loadFactor / spec.mtbfHours, dt)) {
            failed = true
            failedFor = rng.range(1800.0, 14400.0)
            online = false
            outputKW = 0.0
        }
    }

    fun start() { if (!online && !starting && !failed) { starting = true; startTimer = spec.startSeconds } }
    fun stop() { online = false; starting = false; outputKW = 0.0 }

    fun fuelKgPerSec(): Double =
        if (online && outputKW > 0) outputKW * spec.bsfc / 3600.0 * (1.0 + 0.35 * (1.0 - (outputKW / spec.kW).clamp(0.05, 1.0))) else 0.0
}

/** A transient the town throws at you: a motor start, a fault, a mill shift. */
data class LoadEvent(
    val name: String,
    var remaining: Double,
    val kW: Double,
    val kvar: Double,
    val inrushSeconds: Double,
    val inrushMul: Double,
) {
    fun currentKW(): Double {
        val t = inrushSeconds - remaining.coerceAtLeast(0.0)
        return if (remaining > 0 && t < inrushSeconds) kW * inrushMul else kW
    }
    fun currentKvar(): Double {
        val t = inrushSeconds - remaining.coerceAtLeast(0.0)
        return if (remaining > 0 && t < inrushSeconds) kvar * inrushMul * 2.2 else kvar
    }
}

data class GridSnapshot(
    val frequencyHz: Double,
    val busVoltsPU: Double,
    val busVolts: Double,
    val lineVolts: Double,
    val serviceVolts: Double,
    val cityDemandKW: Double,
    val servedKW: Double,
    val shedKW: Double,
    val totalGenKW: Double,
    val playerKW: Double,
    val stationKW: Double,
    val reserveKW: Double,
    val blackout: Boolean,
    val voltageCollapse: Boolean,
    val pointEastDemandKW: Double,
    val pointEastShedKW: Double,
    val pointEastConfidence: Double,
)

class Grid(private val rng: Rng) {

    val stations = CITY_STATIONS.map { CityStation(it) }
    val events = mutableListOf<LoadEvent>()

    var frequencyHz = Nominal.FREQ
    var busVoltPU = 1.0
    var blackout = false
    var voltageCollapse = false
    var shedKW = 0.0
    var unservedKWh = 0.0
    var cityDemandKW = 0.0
    var servedKW = 0.0
    var ambientC = 8.0

    /**
     * How far Point East has been built out, 0 to 1. Every hour the sector
     * gets clean power from your plant this creeps up and somebody breaks
     * ground; every hour it sits browned out, it falls back much faster.
     * This is the only load in the city that answers to what you do.
     */
    var pointEastConfidence = 0.0
    var pointEastDemandKW = 0.0
    var pointEastShedKW = 0.0
    var pointEastLitHours = 0.0
    var pointEastDarkHours = 0.0

    /** Secondary control: the grid authority operator trimming speeders to hold 90 Hz. */
    private var agcBias = 0.0
    private var shedSteps = 0

    /**
     * Dry Green City was running before you turned up.
     *
     * Booting with every station cold means the first seconds of a new career
     * are a frequency collapse, the shedding relays latch, and the player opens
     * the game to a quarter of the city dark and Point East already losing
     * confidence -- none of which they did. So the other stations start already
     * synchronised, loaded, and trimmed to hold 90 Hz.
     */
    fun primeAtStart(gameSeconds: Double, growthYears: Double) {
        updateWeather(gameSeconds)
        pointEastDemandKW = pointEastBaseKW(gameSeconds)
        val demand = baseDemandKW(gameSeconds, growthYears) + pointEastDemandKW

        var rated = 0.0
        for (u in stations.sortedBy { it.spec.priority }) {
            if (rated >= demand * 1.25) break
            u.online = true
            u.starting = false
            u.startTimer = 0.0
            rated += u.spec.kW
        }
        val online = stations.filter { it.online }

        // The secondary trim that puts the combined droop characteristic
        // through (demand, 90 Hz):
        //     P_i = kW_i * (0.55 + bias / droop_i)
        val sumKW = online.sumOf { it.spec.kW }
        val sumKWoverDroop = online.sumOf { it.spec.kW / it.spec.droop }
        agcBias = if (sumKWoverDroop > 0.0)
            ((demand - 0.55 * sumKW) / sumKWoverDroop).clamp(-0.06, 0.09) else 0.0

        frequencyHz = Nominal.FREQ
        busVoltPU = 1.0
        for (u in online) {
            u.speedSetPU = 1.0 + u.spec.droop * 0.55 + agcBias
            u.outputKW = u.availableKW(Nominal.FREQ)
            u.emfPU = 1.45
        }
        shedSteps = 0
        restoreTimer = 0.0
        blackout = false
        voltageCollapse = false
        shedKW = 0.0
        pointEastShedKW = 0.0
    }

    // ------------------------------------------------------------- weather

    fun updateWeather(gameSeconds: Double) {
        val d = calendarOf(gameSeconds)
        val monthly = Weather.MEAN_C[d.month]
        val nextMonth = Weather.MEAN_C[(d.month + 1) % 12]
        val monthBlend = lerp(monthly, nextMonth, (d.day / 30.0).clamp(0.0, 1.0))
        // Daily swing, coldest just before dawn.
        val daily = -cos((d.hourFrac - 4.0) / 24.0 * 2 * PI) * (Weather.SWING_C / 2.0)
        // Slow synoptic drift over several days.
        val synoptic = (valueNoise(gameSeconds / 86400.0 / 2.4, 4711) - 0.5) * 2.0 * Weather.NOISE_C
        ambientC = monthBlend + daily + synoptic
    }

    // ---------------------------------------------------------- town demand

    /**
     * Base demand before frequency and voltage effects: a daily shape, a
     * weather-driven heating term, and slow growth as the town gains
     * confidence in its power supply.
     */
    /** What Point East is drawing today, given how much of it has been built. */
    fun pointEastBaseKW(gameSeconds: Double): Double {
        val d = calendarOf(gameSeconds)
        val h = d.hourFrac
        val i0 = h.toInt().clamp(0, 23)
        val i1 = (i0 + 1) % 24
        val shape = lerp(City.DAILY_SHAPE[i0], City.DAILY_SHAPE[i1], h - i0)
        val built = lerp(PointEast.BASE_KW, PointEast.DEVELOPED_KW, pointEastConfidence)
        var kW = built * shape
        // Out on the flats it is hotter by day and colder by night than the
        // rest of the city, and the new blocks are all electric.
        if (ambientC < 15.0) kW += (15.0 - ambientC) * 1.4 * (0.3 + pointEastConfidence)
        if (ambientC > 24.0) kW += (ambientC - 24.0) * 2.2 * (0.3 + pointEastConfidence)
        return kW
    }

    /** The seven sectors the other stations already serve. */
    fun baseDemandKW(gameSeconds: Double, growthYears: Double): Double {
        val d = calendarOf(gameSeconds)
        val h = d.hourFrac
        val i0 = h.toInt().clamp(0, 23)
        val i1 = (i0 + 1) % 24
        val shape = lerp(City.DAILY_SHAPE[i0], City.DAILY_SHAPE[i1], h - i0)

        var kW = City.BASE_KW * shape
        if (ambientC < 15.0) kW += (15.0 - ambientC) * City.HEAT_KW_PER_DEG_C
        if (ambientC > 22.0) kW += (ambientC - 22.0) * City.COOL_KW_PER_DEG_C

        // The cannery runs hard in summer, the sawmill in winter.
        val season = 1.0 + 0.10 * sin((d.dayOfYear - 172) / 365.0 * 2 * PI)
        kW *= season
        kW *= (1.0 + City.GROWTH_PER_YEAR * growthYears)
        return kW
    }

    fun spawnRandomEvents(dt: Double, gameSeconds: Double) {
        val d = calendarOf(gameSeconds)
        // The sawmill's 60 hp main motor, weekdays in working hours.
        if (d.hour in 7..17 && rng.eventOccurs(1.4, dt)) {
            events += LoadEvent("Sawmill main motor start", 6.0, 44.0, 26.0, 4.5, 3.4)
        }
        // Cannery refrigeration compressor cycling.
        if (rng.eventOccurs(2.2, dt)) {
            events += LoadEvent("Cannery compressor", rng.range(180.0, 900.0), rng.range(18.0, 34.0), 14.0, 3.0, 2.9)
        }
        // Water treatment lift pumps.
        if (rng.eventOccurs(1.1, dt)) {
            events += LoadEvent("Water plant lift pump", rng.range(300.0, 1500.0), rng.range(9.0, 17.0), 7.0, 2.5, 3.1)
        }
        // Winter cold snaps push everyone's baseboard heat on at once.
        if (ambientC < -8.0 && rng.eventOccurs(0.35, dt)) {
            events += LoadEvent("Cold snap heating surge", rng.range(1800.0, 7200.0), rng.range(30.0, 80.0), 12.0, 1.0, 1.0)
        }
    }

    private fun stepEvents(dt: Double) {
        for (e in events) e.remaining -= dt
        events.removeAll { it.remaining <= 0.0 }
    }

    // ---------------------------------------------------------- the dispatch

    /**
     * The grid authority's dispatcher: keep enough capacity online to carry the load
     * with a reserve margin, cheapest and largest machines first.
     */
    fun dispatchStation(demandKW: Double, playerFirmKW: Double) {
        val target = demandKW * 1.20 + 25.0
        val available = stations.filter { !it.failed }.sortedBy { it.spec.priority }
        var online = playerFirmKW + available.filter { it.online || it.starting }.sumOf { it.spec.kW }

        if (online < target) {
            for (u in available) {
                if (!u.online && !u.starting) {
                    u.start()
                    online += u.spec.kW
                    if (online >= target) break
                }
            }
        } else {
            // Shut down the most expensive surplus machine, keeping a margin.
            val runningDesc = available.filter { it.online }.sortedByDescending { it.spec.priority }
            for (u in runningDesc) {
                if (online - u.spec.kW > demandKW * 1.15 + 15.0) {
                    u.stop()
                    online -= u.spec.kW
                } else break
            }
        }
    }

    // ------------------------------------------------------------ the solve

    /**
     * Advance the system by [dt]. [playerUnits] are the machines the player
     * owns; only those with the breaker closed take part.
     */
    fun step(
        dt: Double,
        gameSeconds: Double,
        growthYears: Double,
        playerUnits: List<Genset>,
        quasiSteady: Boolean,
        env: Env,
    ): GridSnapshot {
        stepEvents(dt)

        val onBusPlayer = playerUnits.filter { it.onBus && it.isRunning }

        // --- demand -------------------------------------------------------
        val cityKW = baseDemandKW(gameSeconds, growthYears)
        pointEastDemandKW = pointEastBaseKW(gameSeconds)
        var demand = cityKW + pointEastDemandKW
        var demandKvar = demand * tan(kotlin.math.acos(City.PF))
        for (e in events) { demand += e.currentKW(); demandKvar += e.currentKvar() }
        cityDemandKW = demand

        // Load is partly constant impedance, so it falls when volts fall, and
        // partly motors, so it falls when frequency falls.
        val vFactor = City.CONST_Z_FRAC * busVoltPU * busVoltPU + (1.0 - City.CONST_Z_FRAC)
        val fFactor = 1.0 + City.LOAD_DAMPING_D * (frequencyHz / Nominal.FREQ - 1.0)
        var effectiveDemand = demand * vFactor * fFactor
        // The voltage dependence of the reactive load is applied inside the
        // voltage solve, which needs to evaluate it at trial voltages.
        var nominalKvar = demandKvar

        // Under-frequency load shedding: the town's relays drop blocks of
        // customers rather than let the whole system collapse.
        shedKW = 0.0
        if (frequencyHz < 87.0) shedSteps = maxOf(shedSteps, 1)
        if (frequencyHz < 85.5) shedSteps = maxOf(shedSteps, 2)
        if (frequencyHz < 84.0) shedSteps = maxOf(shedSteps, 3)
        if (frequencyHz > 89.4 && shedSteps > 0) {
            restoreTimer += dt
            if (restoreTimer > 45.0) { shedSteps--; restoreTimer = 0.0 }
        } else restoreTimer = 0.0

        pointEastShedKW = 0.0
        if (shedSteps > 0) {
            val frac = (0.18 * shedSteps).coerceAtMost(0.60)
            shedKW = effectiveDemand * frac
            effectiveDemand -= shedKW
            nominalKvar *= (1.0 - frac)
            // Point East hangs off the end of the worst feeder in the city, so
            // when the grid runs short it goes dark before anywhere else does.
            val peShare = if (demand > 1.0) pointEastDemandKW / demand else 0.0
            pointEastShedKW = (shedKW * peShare * PointEast.SHED_PRIORITY)
                .coerceAtMost(pointEastDemandKW)
        }

        // --- generation ---------------------------------------------------
        for (u in stations) u.step(dt, frequencyHz, rng)
        val stationKW = stations.sumOf { it.outputKW }
        val playerKW = onBusPlayer.sumOf { (it.brakeKW * it.spec.altEff) }
        val totalGen = stationKW + playerKW

        // --- frequency ----------------------------------------------------
        // Equivalent rotating inertia of everything locked to the bus.
        val omegaRated = Nominal.RPM * 2 * PI / 60.0
        var jTotal = onBusPlayer.sumOf { it.spec.inertia }
        for (u in stations) if (u.online) {
            jTotal += 2.0 * u.spec.inertiaH * (u.ratedKVA * 1000.0) / (omegaRated * omegaRated)
        }

        if (jTotal < 1e-6) {
            // Nothing on the bus at all: the town is dark. Reset the
            // controllers so they do not wind up against a dead bus.
            blackout = true
            frequencyHz = 0.0
            agcBias = 0.0
            shedSteps = 0
            restoreTimer = 0.0
            busVoltPU = 0.0
            servedKW = 0.0
            unservedKWh += effectiveDemand * dt / 3600.0
            return snapshot(demand, 0.0, 0.0, 0.0)
        }

        if (quasiSteady) {
            // Solve for the frequency where droop response meets demand rather
            // than integrating the swing equation at a huge timestep.
            frequencyHz = solveSteadyFrequency(effectiveDemand, onBusPlayer, env)
        } else {
            // Swing equation: J omega d(omega)/dt = P_mech - P_elec
            val omega = ((frequencyHz / Nominal.FREQ) * omegaRated).coerceAtLeast(20.0)
            val accel = (totalGen - effectiveDemand) * 1000.0 / (jTotal * omega)
            val omegaNew = (omega + accel * dt).coerceAtLeast(0.0)
            frequencyHz = omegaNew / omegaRated * Nominal.FREQ
        }
        frequencyHz = frequencyHz.clamp(0.0, Nominal.FREQ * 1.35)

        // --- secondary control (the grid authority operator trimming speeders) ------
        val onlineStation = stations.filter { it.online }
        if (onlineStation.isNotEmpty()) {
            // Only integrate against a frequency that means something. Letting
            // this wind up while the bus is dead leaves every speeder on its
            // stop when the machines do come back.
            // These are seven private stations trimmed by hand, not a control
            // computer. Nobody is stood at the board every second, so the
            // frequency is allowed to ride with the load inside a band and only
            // gets pulled back when it leaves one. That is why an isolated grid
            // built in a hurry never sits exactly on its nameplate, and why you
            // have to chase the bus a little when you synchronise.
            if (frequencyHz > 70.0) {
                val err = (Nominal.FREQ - frequencyHz).clamp(-3.0, 3.0)
                val outsideBand = abs(err) - AGC_DEADBAND_HZ
                if (outsideBand > 0.0) {
                    agcBias = (agcBias + sign(err) * outsideBand / Nominal.FREQ * dt * 0.055)
                        .clamp(-0.06, 0.09)
                }
            }
            for (u in onlineStation) {
                u.speedSetPU = 1.0 + u.spec.droop * 0.55 + agcBias
            }
        }

        // --- voltage ------------------------------------------------------
        solveVoltage(onBusPlayer, nominalKvar, dt)

        // --- distribute real and reactive power ---------------------------
        servedKW = totalGen.coerceAtLeast(0.0)
        blackout = frequencyHz < 78.0 || busVoltPU < 0.55
        if (blackout) unservedKWh += effectiveDemand * dt / 3600.0
        else if (shedKW > 0) unservedKWh += shedKW * dt / 3600.0

        updatePointEast(dt, playerKW)

        val reserve = stations.filter { it.online }.sumOf { it.spec.kW } +
            onBusPlayer.sumOf { it.spec.ratedKW } - effectiveDemand

        return snapshot(demand, playerKW, stationKW, reserve)
    }

    private var restoreTimer = 0.0

    /**
     * Point East grows when your plant is the reason its lights are on, and
     * only then. Power arriving from the other stations keeps the sector
     * alive but does not persuade anybody to build there -- they have been
     * promised that before. Being dropped costs far more confidence than
     * being carried earns, which is why one bad night undoes a good week.
     */
    private fun updatePointEast(dt: Double, playerKW: Double) {
        val hours = dt / 3600.0
        val dark = blackout || pointEastShedKW > pointEastDemandKW * 0.05
        val carriedByYou = !dark && playerKW >= pointEastDemandKW * 0.90 &&
            abs(frequencyHz - Nominal.FREQ) < 1.5 && busVoltPU > 0.92

        val perHour = 1.0 / (PointEast.DEVELOPMENT_DAYS * 24.0)
        when {
            dark -> {
                pointEastDarkHours += hours
                pointEastConfidence -= hours * perHour * PointEast.SETBACK_MULTIPLIER
            }
            carriedByYou -> {
                pointEastLitHours += hours
                pointEastConfidence += hours * perHour
            }
        }
        pointEastConfidence = pointEastConfidence.clamp(0.0, 1.0)
    }

    /**
     * Where the combined droop characteristics cross the demand line.
     *
     * The player's machines are evaluated through their own engine model
     * rather than an idealised straight droop line. Those two do not agree --
     * a real engine's output is not linear in rack -- and using the idealised
     * one here hands the machine a frequency its governor cannot support, so
     * the bus motors it the moment the breaker closes.
     */
    private fun solveSteadyFrequency(
        demandKW: Double,
        playerUnits: List<Genset>,
        env: Env,
    ): Double {
        var lo = Nominal.FREQ * 0.80
        var hi = Nominal.FREQ * 1.08
        repeat(28) {
            val mid = (lo + hi) / 2.0
            var gen = stations.filter { it.online }.sumOf { it.availableKW(mid) }
            for (g in playerUnits) gen += g.electricalKWAtFrequency(mid, env)
            if (gen > demandKW) lo = mid else hi = mid
        }
        return (lo + hi) / 2.0
    }

    /**
     * Bus voltage from the reactive balance.
     *
     *  Machines with an automatic voltage regulator do not sit passively behind
     *  their synchronous reactance -- they move their field until the terminal
     *  volts match a reference, and that reference falls with the VARs they are
     *  carrying so that parallel machines share reactive load instead of
     *  fighting. So the regulated machines set the bus:
     *
     *      V = 1 - droop * (Q_regulated / Q_capability)
     *
     *  A machine on a hand rheostat has no such loop. It injects whatever its
     *  field produces, Q = V (E - V) / X, and the regulated machines pick up
     *  whatever is left over. Over-excite it and you hog VARs off the others;
     *  under-excite it and you make them carry yours. Both are real effects an
     *  operator on a manual exciter has to manage, and both fall out of this.
     */
    private fun solveVoltage(playerUnits: List<Genset>, loadKvarNominal: Double, dt: Double) {
        val avrStation = stations.filter { it.online }
        val avrPlayer = playerUnits.filter { it.spec.avr && it.spec.varShare }
        // An AVR without cross-current compensation regulates to its own
        // setpoint, so it behaves as an injector, not a sharer.
        val injectors = playerUnits.filter { !it.spec.avr || !it.spec.varShare }

        // Reactive capability at rated power factor: Q = S * sin(acos(0.8)).
        val qCap = avrStation.sumOf { it.ratedKVA * 0.6 } + avrPlayer.sumOf { it.spec.ratedKVA * 0.6 }

        if (qCap < 1.0) {
            solveVoltageUnregulated(injectors, loadKvarNominal)
            return
        }

        fun injectedKvar(v: Double): Double {
            var q = 0.0
            for (g in injectors) {
                q += if (g.spec.avr) {
                    // Regulating to its own reference against the bus.
                    val cap = g.spec.ratedKVA * 0.6
                    (cap * (g.avrSetpointPU - v) / g.avrVoltDroop.coerceAtLeast(0.01))
                        .clamp(-cap * 1.6, cap * 1.6)
                } else {
                    val x = g.xsOnSystemBase(SYSTEM_BASE_KVA)
                    v * (g.emfPU - v) / x * SYSTEM_BASE_KVA
                }
            }
            return q
        }

        fun loadKvarAt(v: Double) =
            loadKvarNominal * (City.CONST_Z_FRAC * v * v + (1.0 - City.CONST_Z_FRAC))

        fun regulatedTarget(v: Double): Double {
            val r = (loadKvarAt(v) - injectedKvar(v)) / qCap
            // Past about 110% of reactive capability the regulators are at
            // ceiling and the voltage falls away rather than drooping gently.
            val over = (r - 1.10).coerceAtLeast(0.0)
            return 1.0 - 0.045 * r - 0.35 * over * over
        }

        // residual(v) = v - target(v) rises monotonically with v, so bisect.
        var lo = 0.20
        var hi = 1.50
        repeat(40) {
            val mid = (lo + hi) / 2.0
            if (mid - regulatedTarget(mid) < 0.0) lo = mid else hi = mid
        }
        val v = ((lo + hi) / 2.0).clamp(0.0, 1.6)
        busVoltPU = v

        val qRegulated = loadKvarAt(v) - injectedKvar(v)
        voltageCollapse = qRegulated > qCap * 1.10 || v < 0.80

        // Hand out the reactive power each machine is actually carrying.
        for (g in injectors) {
            g.kvar = if (g.spec.avr) {
                val cap = g.spec.ratedKVA * 0.6
                (cap * (g.avrSetpointPU - v) / g.avrVoltDroop.coerceAtLeast(0.01))
                    .clamp(-cap * 1.6, cap * 1.6)
            } else {
                val x = g.xsOnSystemBase(SYSTEM_BASE_KVA)
                v * (g.emfPU - v) / x * SYSTEM_BASE_KVA
            }
        }
        for (g in avrPlayer) g.kvar = qRegulated * (g.spec.ratedKVA * 0.6) / qCap
        for (u in avrStation) {
            u.kvar = qRegulated * (u.ratedKVA * 0.6) / qCap
            // Keep the grid authority machines' internal EMF consistent with the VARs
            // they are carrying, so the synchroscope has something honest to
            // compare against and a machine coming off the bus is at the right
            // open-circuit volts.
            val x = 1.7 * (SYSTEM_BASE_KVA / u.ratedKVA)
            u.emfPU = (v + (u.kvar / SYSTEM_BASE_KVA) * x / v.coerceAtLeast(0.4)).clamp(0.4, 2.6)
        }
    }

    /**
     * No regulator anywhere on the bus: every machine is an EMF behind a
     * reactance, and the bus lands where their combined output meets the load.
     *
     *     sum V (E_i - V) / X_i = Q_load   ->   A V^2 - B V + Q = 0
     */
    private fun solveVoltageUnregulated(sources: List<Genset>, loadKvarNominal: Double) {
        if (sources.isEmpty()) { busVoltPU = 0.0; voltageCollapse = true; return }
        var a = 0.0
        var b = 0.0
        for (g in sources) {
            val x = g.xsOnSystemBase(SYSTEM_BASE_KVA)
            a += 1.0 / x
            b += g.emfPU / x
        }
        // The load's reactive demand still falls with voltage; use the last
        // solution to evaluate it, which is close enough for one step.
        val qLoadPU = loadKvarNominal *
            (City.CONST_Z_FRAC * busVoltPU * busVoltPU + (1.0 - City.CONST_Z_FRAC)) / SYSTEM_BASE_KVA
        val disc = b * b - 4.0 * a * qLoadPU
        voltageCollapse = disc < 0.0
        val v = if (disc < 0.0) b / (2.0 * a) else (b + sqrt(disc)) / (2.0 * a)
        busVoltPU = v.clamp(0.0, 1.6)
        for (g in sources) {
            val x = g.xsOnSystemBase(SYSTEM_BASE_KVA)
            g.kvar = busVoltPU * (g.emfPU - busVoltPU) / x * SYSTEM_BASE_KVA
        }
    }

    private fun snapshot(demand: Double, playerKW: Double, stationKW: Double, reserve: Double) =
        GridSnapshot(
            frequencyHz = frequencyHz,
            busVoltsPU = busVoltPU,
            busVolts = busVoltPU * Nominal.GEN_VOLTS,
            lineVolts = busVoltPU * Nominal.LINE_VOLTS,
            serviceVolts = busVoltPU * Nominal.SERVICE_VOLTS,
            cityDemandKW = demand,
            servedKW = servedKW,
            shedKW = shedKW,
            totalGenKW = playerKW + stationKW,
            playerKW = playerKW,
            stationKW = stationKW,
            reserveKW = reserve,
            blackout = blackout,
            voltageCollapse = voltageCollapse,
            pointEastDemandKW = pointEastDemandKW,
            pointEastShedKW = pointEastShedKW,
            pointEastConfidence = pointEastConfidence,
        )

    /** Total grid authority capacity that is running or could be started right now. */
    fun cityStationCapacityKW() = stations.filter { !it.failed }.sumOf { it.spec.kW }
    fun cityStationOnlineKW() = stations.filter { it.online }.sumOf { it.spec.kW }
}
