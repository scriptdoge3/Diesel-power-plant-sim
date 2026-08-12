package com.pointeast.core

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sign
import kotlin.math.sin
import kotlin.math.sqrt

/* ============================================================================
 *  One generating set: diesel engine, governor, alternator, breaker.
 *
 *  The engine is modelled through its air path. Air mass flow sets how much
 *  fuel can burn cleanly; fuel burnt sets indicated power; indicated minus
 *  friction and parasitics is brake power; brake power minus what the bus
 *  takes accelerates the flywheel. Heat that does not leave as work leaves as
 *  coolant heat or exhaust heat, and both of those have somewhere to go.
 *
 *  Nothing here knows about the bus solution. The Grid drives this class in
 *  two phases per tick so every machine sees the same instant.
 * ========================================================================== */

enum class RunState { STOPPED, PRELUBE, CRANKING, STARTING, RUNNING, COOLDOWN, FAILED }

/** Component condition, 0.0 = new, 1.0 = destroyed. */
@kotlinx.serialization.Serializable
data class Wear(
    var bearings: Double = 0.14,
    var rings: Double = 0.19,
    var injectors: Double = 0.31,
    var gasket: Double = 0.12,
    var turbo: Double = 0.0,
    var alternator: Double = 0.16,
    var governor: Double = 0.22,
) {
    fun worst(): Pair<String, Double> = listOf(
        "Bearings" to bearings, "Rings & liners" to rings, "Injectors" to injectors,
        "Head gasket" to gasket, "Turbocharger" to turbo, "Alternator" to alternator,
        "Governor linkage" to governor,
    ).maxBy { it.second }
}

/** Hours-since-service counters that drive the maintenance screen. */
@kotlinx.serialization.Serializable
data class ServiceHours(
    var oil: Double = 180.0,
    var fuelFilter: Double = 310.0,
    var airFilter: Double = 260.0,
    var valveLash: Double = 640.0,
    var injectors: Double = 1450.0,
    var coolant: Double = 2100.0,
    var radiator: Double = 980.0,
) {
    fun get(key: String) = when (key) {
        "oil" -> oil; "fuelFilter" -> fuelFilter; "airFilter" -> airFilter
        "valveLash" -> valveLash; "injectors" -> injectors; "coolant" -> coolant
        "radiator" -> radiator; else -> 0.0
    }
    fun reset(key: String) {
        when (key) {
            "oil" -> oil = 0.0; "fuelFilter" -> fuelFilter = 0.0; "airFilter" -> airFilter = 0.0
            "valveLash" -> valveLash = 0.0; "injectors" -> injectors = 0.0
            "coolant" -> coolant = 0.0; "radiator" -> radiator = 0.0
        }
    }
    fun addAll(h: Double) {
        oil += h; fuelFilter += h; airFilter += h; valveLash += h
        injectors += h; coolant += h; radiator += h
    }
}

data class Alarm(val code: String, val text: String, val critical: Boolean)

/** Ambient conditions handed to every machine each tick. */
data class Env(val ambientC: Double, val baroPa: Double)

class Genset(
    val id: String,
    var spec: GensetSpec,
    /** Set 1 is the one the tech tree modifies; purchased machines are fixed. */
    val isFoundingSet: Boolean,
) {
    // ------------------------------------------------------------- controls
    var fuelValveOpen = false
    var starterEngaged = false
    var prelubeRunning = false
    var speederPU = 1.0                  // governor no-load speed setting, per-unit
    var droop = GovernorBase.DROOP_DEFAULT
    var fieldRheostat = 0.62             // manual excitation, 0..1
    var avrSetpointPU = 1.0              // AVR reference, per-unit volts
    var avrVoltDroop = 0.04              // voltage droop for VAR sharing
    var breakerClosed = false
    var autoMode = false                 // let the plant controller run this unit
    var loadSharePercent = 0.0           // target share of station load, 0..1
    var manualFanOn = true

    // --------------------------------------------------------------- states
    var runState = RunState.STOPPED
    var rpm = 0.0
    var rack = 0.0                       // fuel rack position, 0..1
    var fieldPU = 0.0                    // exciter field, per-unit
    var emfPU = 0.0                      // internal EMF behind synchronous reactance
    var boostBar = 0.0
    var manifoldC = 15.0
    var coolantC = 12.0
    var oilC = 12.0
    var egtC = 20.0
    var windingC = 15.0
    var batterySoC = 0.86
    var phaseDeg = 0.0                   // generator angle relative to the bus
    private var prevPhaseDeg = 0.0
    var huntPhase = 0.0

    // ------------------------------------------------------------- outputs
    var brakeKW = 0.0
    var elecKW = 0.0                     // real power actually delivered
    var kvar = 0.0
    var terminalVoltsPU = 0.0
    var ampsPU = 0.0
    var fuelRateKgS = 0.0
    var afr = 99.0
    var smokeExcess = 0.0                // 0 = clean, 1 = solid black
    var peakCylBar = 0.0
    var jacketHeatKW = 0.0
    var exhaustHeatKW = 0.0
    var recoveredHeatKW = 0.0

    // ------------------------------------------------------------ bookkeeping
    var runHours = 0.0
    var lifetimeKWh = 0.0
    var fuelUsedL = 0.0
    var starts = 0
    var wear = Wear()
    var service = ServiceHours()
    var airFilterFouling = 0.30          // 0..1
    var fuelFilterFouling = 0.24
    var radiatorFouling = 0.22
    var oilCondition = 0.28              // 0 = fresh, 1 = sludge
    var failureText: String? = null
    var lastTripReason: String? = null
    var cooldownRemaining = 0.0
    var startAttemptTime = 0.0
    var syncShockPU = 0.0

    val alarms = mutableListOf<Alarm>()

    // Bus-facing values the Grid fills in.
    var onBus = false
    var busFreqHz = Nominal.FREQ

    val freqHz: Double get() = rpm * Nominal.POLES / 120.0
    val speedPU: Double get() = rpm / spec.ratedRPM
    val loadFraction: Double get() = if (spec.ratedKW > 0) elecKW / spec.ratedKW else 0.0
    val kva: Double get() = sqrt(elecKW * elecKW + kvar * kvar)
    val powerFactor: Double get() = if (kva > 0.1) (elecKW / kva).clamp(-1.0, 1.0) else 1.0
    val isRunning: Boolean get() = runState == RunState.RUNNING || runState == RunState.COOLDOWN

    // =========================================================== phase one

    /**
     * Advance everything that does not need the bus solution: the governor,
     * the air path, combustion and the resulting brake power. Returns the
     * electrical power this machine can put on the bus right now.
     */
    fun preStep(dt: Double, env: Env, systemFreq: Double): Double {
        alarms.clear()
        if (runState == RunState.FAILED) {
            rack = 0.0; brakeKW = 0.0; elecKW = 0.0; fuelRateKgS = 0.0
            failureText?.let { alarms += Alarm("FAIL", it, true) }
            return 0.0
        }

        stepStartStop(dt, env)
        stepGovernor(dt, if (onBus) systemFreq else freqHz)
        computeAirPath(dt, env)
        computeCombustion(env)
        return if (isRunning) (brakeKW * spec.altEff).coerceAtLeast(-8.0) else 0.0
    }

    // =========================================================== phase two

    /**
     * Apply the bus solution: take the assigned speed and electrical loading,
     * then advance the slow states -- temperatures, wear, fuel, protection.
     */
    fun postStep(
        dt: Double,
        env: Env,
        systemFreq: Double,
        busVoltPU: Double,
        assignedElecKW: Double,
        assignedKvar: Double,
        quasiSteady: Boolean,
    ) {
        if (runState == RunState.FAILED) {
            coastDown(dt); stepThermalOff(dt, env); return
        }

        if (onBus) {
            // Locked to the bus: every machine turns at the system speed.
            rpm = systemFreq * 120.0 / Nominal.POLES
            elecKW = assignedElecKW
            kvar = assignedKvar
            terminalVoltsPU = busVoltPU
            phaseDeg = 0.0
        } else {
            elecKW = 0.0
            kvar = 0.0
            integrateFreeSpeed(dt, quasiSteady)
            terminalVoltsPU = emfPU
            // Track the angle against the bus for the synchroscope.
            prevPhaseDeg = phaseDeg
            phaseDeg = wrapDeg(phaseDeg + (freqHz - systemFreq) * 360.0 * dt)
        }

        stepExcitation(dt, busVoltPU)
        stepThermal(dt, env)
        stepWearAndFouling(dt)
        stepBattery(dt)
        checkProtection(dt, systemFreq, busVoltPU)

        if (isRunning) {
            val hrs = dt / 3600.0
            runHours += hrs
            service.addAll(hrs)
            lifetimeKWh += elecKW * hrs
        }
        ampsPU = if (terminalVoltsPU > 0.05) kva / spec.ratedKVA / terminalVoltsPU else 0.0
    }

    // ------------------------------------------------------------ start/stop

    private fun stepStartStop(dt: Double, env: Env) {
        when (runState) {
            RunState.PRELUBE -> {
                startAttemptTime += dt
                if (startAttemptTime > 8.0) { runState = RunState.CRANKING; startAttemptTime = 0.0 }
            }
            RunState.CRANKING -> {
                startAttemptTime += dt
                if (batterySoC < 0.06) {
                    alarms += Alarm("BATT", "Battery flat -- will not crank", true)
                    starterEngaged = false; runState = RunState.STOPPED; return
                }
                // Starter torque falls off with speed and with a weak battery.
                val starterTorque = 420.0 * (batterySoC / 0.8).clamp(0.15, 1.15) *
                    (1.0 - (rpm / (EngineBase.CRANK_RPM * 1.6)).clamp(0.0, 0.95))
                val drag = frictionTorque() + compressionDragTorque()
                val omega = rpm * 2 * PI / 60.0
                val alpha = (starterTorque - drag) / spec.inertia
                rpm = (rpm + alpha * dt * 60.0 / (2 * PI)).coerceAtLeast(0.0)
                batterySoC = (batterySoC - dt * 0.0075).coerceAtLeast(0.0)

                if (fuelValveOpen && rpm > EngineBase.FIRE_RPM * coldStartPenalty(env)) {
                    runState = RunState.STARTING; starts++; startAttemptTime = 0.0
                    // Most bearing wear happens in the seconds before oil
                    // pressure comes up. Pre-lubing, or a warm engine, avoids it.
                    val cold = remap(oilC, -10.0, 60.0, 1.0, 0.25)
                    val dry = if (prelubeRunning) 0.15 else 1.0
                    wear.bearings = (wear.bearings + 2.2e-4 * cold * dry * spec.wearMul)
                        .coerceAtMost(1.0)
                }
                if (startAttemptTime > 18.0) {
                    alarms += Alarm("START", "Cranked 18 s without a start", false)
                    starterEngaged = false
                    runState = RunState.STOPPED
                }
                if (omega.isNaN()) rpm = 0.0
            }
            RunState.STARTING -> {
                starterEngaged = false
                startAttemptTime += dt
                if (rpm > spec.idleRPM * 0.85) { runState = RunState.RUNNING; startAttemptTime = 0.0 }
                if (!fuelValveOpen) runState = RunState.STOPPED
                if (startAttemptTime > 12.0) runState = RunState.RUNNING
            }
            RunState.RUNNING -> {
                if (!fuelValveOpen) {
                    runState = if (coolantC > 70.0 && runHours > 0.05) RunState.COOLDOWN else RunState.STOPPED
                    cooldownRemaining = 180.0
                }
            }
            RunState.COOLDOWN -> {
                cooldownRemaining -= dt
                if (cooldownRemaining <= 0.0 || rpm < 40.0) runState = RunState.STOPPED
            }
            RunState.STOPPED -> {
                if (starterEngaged) {
                    runState = if (prelubeRunning) RunState.PRELUBE else RunState.CRANKING
                    startAttemptTime = 0.0
                }
            }
            RunState.FAILED -> {}
        }
    }

    /** Cold engines need more speed before they will fire. */
    private fun coldStartPenalty(env: Env): Double {
        val t = if (spec.blockHeater) maxOf(coolantC, 38.0) else coolantC
        return when {
            t > 30.0 -> 0.82
            t > 5.0 -> remap(t, 5.0, 30.0, 1.05, 0.82)
            t > -12.0 -> remap(t, -12.0, 5.0, 1.55, 1.05)
            else -> 1.9
        }
    }

    fun requestStart() {
        if (runState == RunState.STOPPED || runState == RunState.COOLDOWN) {
            fuelValveOpen = true
            starterEngaged = true
            runState = if (prelubeRunning) RunState.PRELUBE else RunState.CRANKING
            startAttemptTime = 0.0
        }
    }

    fun requestStop() {
        fuelValveOpen = false
        starterEngaged = false
    }

    /** Emergency stop: fuel off, breaker open, no cooldown. */
    fun emergencyStop(reason: String) {
        fuelValveOpen = false
        starterEngaged = false
        breakerClosed = false
        onBus = false
        runState = RunState.STOPPED
        lastTripReason = reason
    }

    // ------------------------------------------------------------- governor

    private fun stepGovernor(dt: Double, sensedFreq: Double) {
        if (!isRunning && runState != RunState.STARTING) {
            if (runState == RunState.CRANKING || runState == RunState.PRELUBE) rack = 0.0
            else rack = approach(rack, 0.0, 0.3, dt)
            return
        }

        val speedPUNow = (sensedFreq / Nominal.FREQ)
        var err = speederPU - speedPUNow

        if (!spec.egov) {
            // Mechanical flyweights: deadband from linkage slop, made worse by wear.
            val db = GovernorBase.DEADBAND_PU * (1.0 + 3.0 * wear.governor)
            err = when {
                err > db -> err - db
                err < -db -> err + db
                else -> 0.0
            }
        }

        val effDroop = droop.coerceAtLeast(spec.droopMin)
        var target = if (effDroop <= 1e-4) {
            // Isochronous: integral action holds speed dead on.
            (rack + err * 9.0 * dt * 60.0).clamp(0.0, 1.0)
        } else {
            (err / effDroop).clamp(0.0, 1.0)
        }

        // A mechanical governor set too tight will hunt: the linkage overshoots
        // and the engine chases itself.
        if (!spec.egov && effDroop < GovernorBase.HUNT_THRESHOLD) {
            val severity = invLerp(GovernorBase.HUNT_THRESHOLD, spec.droopMin, effDroop).clamp(0.0, 1.0)
            huntPhase += dt * 2.0 * PI * (1.1 + 0.6 * severity)
            target += sin(huntPhase) * GovernorBase.HUNT_GAIN * severity * (0.25 + 0.75 * rack)
        } else {
            huntPhase = 0.0
        }

        // While starting, the governor runs the rack up to get it lit.
        if (runState == RunState.STARTING) target = 0.55

        val tau = if (spec.egov) 0.06 else GovernorBase.ACTUATOR_TAU_S * (1.0 + wear.governor)
        rack = approach(rack, target.clamp(0.0, 1.0), tau, dt).clamp(0.0, 1.0)
    }

    // ------------------------------------------------------------- air path

    private var massAirKgS = 0.0

    /** Air mass flow and charge temperature at a given speed and boost. */
    private fun airFlowAt(rpmV: Double, boost: Double, env: Env): Pair<Double, Double> {
        if (rpmV < 20.0) return 0.0 to env.ambientC
        val revPerSec = rpmV / 60.0
        val flowRatio = (rpmV / spec.ratedRPM) * (1.0 + boost)
        val filterDropPa = 2600.0 * airFilterFouling * spec.filterRestrictMul * flowRatio * flowRatio
        val manifoldPa = env.baroPa - filterDropPa + boost * 1e5
        val ambientK = env.ambientC + 273.15

        var chargeK = ambientK + 12.0
        if (spec.turbo != null && boost > 0.001) {
            val pr = manifoldPa / (env.baroPa - filterDropPa).coerceAtLeast(40000.0)
            val ideal = ambientK * pr.pow(0.2857)
            chargeK = ambientK + (ideal - ambientK) / spec.turbo!!.eff
            if (spec.aftercoolerEff > 0.0) {
                chargeK -= spec.aftercoolerEff * (chargeK - (ambientK + 6.0))
            }
        }
        chargeK += (coolantC - env.ambientC).coerceAtLeast(0.0) * 0.06

        val density = manifoldPa / (287.05 * chargeK)
        val ve = spec.volEff * (1.0 - 0.18 * wear.rings) *
            (1.0 - 0.10 * (service.valveLash / 2200.0).clamp(0.0, 1.0))
        return ve * spec.sweptPerRevM3 * revPerSec * density to (chargeK - 273.15)
    }

    /** Brake power, kW, for a rack position and speed. The engine in one line. */
    private fun brakeKWAt(rackV: Double, rpmV: Double, env: Env): Double {
        if (rpmV < 20.0) return 0.0
        val revPerSec = rpmV / 60.0
        val boost = if (spec.turbo != null) {
            val drive = (rackV * (rpmV / spec.ratedRPM).pow(1.25)).clamp(0.0, 1.4)
            spec.turbo!!.maxBoostBar * (drive * drive).clamp(0.0, 1.0) * (1.0 - 0.35 * wear.turbo)
        } else 0.0
        val (air, _) = airFlowAt(rpmV, boost, env)

        val fuelAvail = (1.0 - 0.55 * fuelFilterFouling * fuelFilterFouling).clamp(0.35, 1.0)
        val maxRate = spec.maxFuelPerStroke * spec.cylinders * revPerSec / 2.0
        val fuel = (rackV * maxRate * fuelAvail).coerceAtLeast(0.0)
        if (maxRate <= 1e-12) return 0.0

        val afrV = if (fuel > 1e-7) air / fuel else 99.0
        val smoke = ((spec.smokeAFR - afrV) / spec.smokeAFR).clamp(0.0, 1.0)
        val loadFrac = (fuel / maxRate).clamp(0.0, 1.2)
        val shape = (0.55 + 0.75 * loadFrac - 0.28 * loadFrac * loadFrac).clamp(0.30, 1.0)
        val etaInd = (spec.indEffPeak * shape * (1.0 + 0.010 * spec.timingAdvance) *
            (1.0 - 0.16 * wear.injectors) * (1.0 - 1.10 * smoke) *
            (1.0 - 0.22 * abs(rpmV / spec.ratedRPM - 1.0))).clamp(0.0, 0.52)

        val indicatedW = fuel * EngineBase.LHV * etaInd
        val frictionW = frictionTorqueAt(rpmV) * (rpmV * 2 * PI / 60.0)
        val parasiticW = parasiticKWAt(rpmV) * 1000.0
        return (indicatedW - frictionW - parasiticW) / 1000.0
    }

    /**
     * What this machine would settle at if the bus were running at [f].
     * The grid's steady-state solve uses this so that the frequency it finds
     * agrees with the power the engine will actually make there -- otherwise a
     * machine gets handed a frequency its governor cannot support and is
     * motored by the bus the instant its breaker closes.
     */
    fun electricalKWAtFrequency(f: Double, env: Env): Double {
        if (!isRunning) return 0.0
        val d = droop.coerceAtLeast(spec.droopMin)
        val rackAt = if (d <= 1e-4) rack
        else ((speederPU - f / Nominal.FREQ) / d).clamp(0.0, 1.0)
        val rpmAt = f * 120.0 / Nominal.POLES
        return brakeKWAt(rackAt, rpmAt, env) * spec.altEff
    }

    private fun computeAirPath(dt: Double, env: Env) {
        if (rpm < 20.0) { massAirKgS = 0.0; boostBar = 0.0; return }

        val revPerSec = rpm / 60.0
        // Air cleaner pressure drop rises with the square of flow.
        val flowRatio = (rpm / spec.ratedRPM) * (1.0 + boostBar)
        val filterDropPa = 2600.0 * airFilterFouling * spec.filterRestrictMul * flowRatio * flowRatio

        // Turbocharger: boost tracks the exhaust energy available, which in
        // practice tracks fuelling and speed. Lag models the rotor inertia.
        if (spec.turbo != null) {
            val t = spec.turbo!!
            val drive = (rack * (rpm / spec.ratedRPM).pow(1.25)).clamp(0.0, 1.4)
            val targetBoost = t.maxBoostBar * (drive * drive).clamp(0.0, 1.0) *
                (1.0 - 0.35 * wear.turbo)
            boostBar = approach(boostBar, targetBoost, t.tau, dt)
        } else {
            boostBar = 0.0
        }

        val manifoldPa = env.baroPa - filterDropPa + boostBar * 1e5
        val ambientK = env.ambientC + 273.15

        // Compression heating through the turbo, then the aftercooler.
        var chargeK = ambientK + 12.0
        if (spec.turbo != null && boostBar > 0.001) {
            val pr = manifoldPa / (env.baroPa - filterDropPa).coerceAtLeast(40000.0)
            val ideal = ambientK * pr.pow(0.2857)
            chargeK = ambientK + (ideal - ambientK) / spec.turbo!!.eff
            if (spec.aftercoolerEff > 0.0) {
                chargeK -= spec.aftercoolerEff * (chargeK - (ambientK + 6.0))
            }
        }
        // The block warms the incoming charge a little.
        chargeK += (coolantC - env.ambientC).coerceAtLeast(0.0) * 0.06
        manifoldC = chargeK - 273.15

        val density = manifoldPa / (287.05 * chargeK)
        // Worn rings and tight valves both cost volumetric efficiency.
        val ve = spec.volEff * (1.0 - 0.18 * wear.rings) *
            (1.0 - 0.10 * (service.valveLash / 2200.0).clamp(0.0, 1.0))
        massAirKgS = ve * spec.sweptPerRevM3 * revPerSec * density
    }

    // ---------------------------------------------------------- combustion

    private fun computeCombustion(env: Env) {
        if (!isRunning && runState != RunState.STARTING) {
            fuelRateKgS = 0.0; brakeKW = 0.0; afr = 99.0; smokeExcess = 0.0
            jacketHeatKW = 0.0; exhaustHeatKW = 0.0; egtTarget = env.ambientC; return
        }

        val revPerSec = rpm / 60.0
        // A clogged fuel filter starves the pump at high rack.
        val fuelAvail = (1.0 - 0.55 * fuelFilterFouling * fuelFilterFouling).clamp(0.35, 1.0)
        val maxRate = spec.maxFuelPerStroke * spec.cylinders * revPerSec / 2.0
        fuelRateKgS = (rack * maxRate * fuelAvail).coerceAtLeast(0.0)

        afr = if (fuelRateKgS > 1e-7) massAirKgS / fuelRateKgS else 99.0
        smokeExcess = ((spec.smokeAFR - afr) / spec.smokeAFR).clamp(0.0, 1.0)

        val qFuel = fuelRateKgS * EngineBase.LHV      // W

        // Indicated efficiency: a shape term for load, a penalty for a sooty
        // burn, a bonus for timing, and a penalty for worn injectors.
        val loadFrac = if (maxRate > 1e-9) (fuelRateKgS / maxRate).clamp(0.0, 1.2) else 0.0
        val shape = (0.55 + 0.75 * loadFrac - 0.28 * loadFrac * loadFrac).clamp(0.30, 1.0)
        val timingGain = 1.0 + 0.010 * spec.timingAdvance
        val injectorLoss = 1.0 - 0.16 * wear.injectors
        val smokeLoss = 1.0 - 1.10 * smokeExcess
        val speedLoss = 1.0 - 0.22 * abs(rpm / spec.ratedRPM - 1.0)
        val etaInd = (spec.indEffPeak * shape * timingGain * injectorLoss *
            smokeLoss * speedLoss).clamp(0.0, 0.52)

        val indicatedW = qFuel * etaInd
        val frictionW = frictionTorque() * (rpm * 2 * PI / 60.0)
        val parasiticW = parasiticKW() * 1000.0

        brakeKW = (indicatedW - frictionW - parasiticW) / 1000.0

        // Peak cylinder pressure -- what actually kills head gaskets.
        val manifoldBar = (env.baroPa + boostBar * 1e5) / 1e5
        peakCylBar = manifoldBar * 48.5 * (1.0 + 0.55 * loadFrac + 0.030 * spec.timingAdvance)

        // Heat split.
        jacketHeatKW = (qFuel * EngineBase.JACKET_FRAC +
            frictionW * (1.0 - EngineBase.OIL_FRICTION_FRAC)) / 1000.0
        val exhaustW = qFuel * EngineBase.EXHAUST_FRAC * (1.0 + 0.9 * smokeExcess)
        exhaustHeatKW = exhaustW / 1000.0

        val mdotExh = massAirKgS + fuelRateKgS
        egtTarget = if (mdotExh > 1e-5) {
            manifoldC + exhaustW / (mdotExh * 1150.0)
        } else env.ambientC
    }

    private var egtTarget = 20.0

    private fun frictionTorque(): Double = frictionTorqueAt(rpm)

    private fun frictionTorqueAt(rpmV: Double): Double {
        // FMEP in bar, converted to a torque through the swept volume.
        val visc = viscosityFactor()
        val fmepBar = (EngineBase.FMEP_A + EngineBase.FMEP_B * (rpmV / 1000.0)) *
            (0.90 + 0.20 * visc) * (1.0 + 0.55 * wear.bearings + 0.35 * wear.rings)
        // Work per revolution = FMEP * swept volume; torque = work / 2*pi.
        return fmepBar * 1e5 * spec.sweptPerRevM3 / (2 * PI)
    }

    /** Extra drag from pumping against compression while cranking. */
    private fun compressionDragTorque(): Double =
        if (rpm < EngineBase.CRANK_RPM * 2) 34.0 * (1.0 - rpm / (EngineBase.CRANK_RPM * 3)) else 0.0

    private fun parasiticKW(): Double = parasiticKWAt(rpm)

    private fun parasiticKWAt(rpmV: Double): Double {
        val fanFrac = if (spec.fanKW <= 0.5) {
            if (coolantC > 82.0) 1.0 else 0.0            // electric fan, on demand
        } else (rpmV / spec.ratedRPM).pow(3).clamp(0.0, 1.3)  // belt fan, cubed with speed
        val pumpKW = EngineBase.WATER_PUMP_KW * spec.waterPumpMul * (rpmV / spec.ratedRPM).pow(2)
        return spec.fanKW * fanFrac + pumpKW + EngineBase.FIELD_KW * fieldPU * fieldPU
    }

    private fun viscosityFactor(): Double = (1.9 - 0.009 * oilC).clamp(0.55, 1.8)

    // --------------------------------------------------------------- speed

    /** Off the bus, the machine's own inertia sets its speed. */
    private fun integrateFreeSpeed(dt: Double, quasiSteady: Boolean) {
        if (runState == RunState.CRANKING || runState == RunState.PRELUBE) return
        if (!isRunning && runState != RunState.STARTING) { coastDown(dt); return }

        if (quasiSteady) {
            // Solve the droop characteristic directly instead of integrating.
            val effDroop = droop.coerceAtLeast(spec.droopMin)
            rpm = if (effDroop <= 1e-4) speederPU * spec.ratedRPM
            else (speederPU * spec.ratedRPM).clamp(spec.idleRPM, spec.overspeedTripRPM)
            return
        }

        val omega = (rpm * 2 * PI / 60.0).coerceAtLeast(1.0)
        // No electrical load off the bus, so all brake power accelerates it.
        val netTorque = brakeKW * 1000.0 / omega
        val alpha = netTorque / spec.inertia
        rpm = (rpm + alpha * dt * 60.0 / (2 * PI)).coerceIn(0.0, spec.overspeedTripRPM * 1.3)
    }

    private fun coastDown(dt: Double) {
        if (rpm <= 0.0) return
        val omega = (rpm * 2 * PI / 60.0).coerceAtLeast(0.5)
        val alpha = -(frictionTorque() + parasiticKW() * 1000.0 / omega) / spec.inertia
        rpm = (rpm + alpha * dt * 60.0 / (2 * PI)).coerceAtLeast(0.0)
    }

    // ---------------------------------------------------------- excitation

    private fun stepExcitation(dt: Double, busVoltPU: Double) {
        if (rpm < 120.0) {
            fieldPU = approach(fieldPU, 0.0, 0.6, dt)
            emfPU = 0.0
            return
        }

        val target = if (spec.avr) {
            // AVR with voltage droop: the reference falls as the machine picks
            // up VARs, which is what makes reactive load share between units.
            val measured = if (onBus) busVoltPU else emfPU
            val ref = avrSetpointPU - if (spec.varShare || onBus) avrVoltDroop * (kvar / spec.ratedKVA) else 0.0
            val err = ref - measured
            // Bounded integral step: stable at any timestep, including the
            // one-second steps used when the game is fast-forwarded.
            val step = (err * 5.5 * dt).clamp(-0.15, 0.15)
            (fieldPU + step).clamp(0.0, 2.0)
        } else {
            // Manual rheostat: the handle position is the field demand.
            fieldRheostat * 1.7
        }

        val tau = if (spec.avr) spec.fieldTauS * 0.5 else spec.fieldTauS
        fieldPU = approach(fieldPU, target.clamp(0.0, 2.0), tau, dt)

        // Open-circuit saturation curve, normalised so 1.0 pu field = 1.0 pu volts.
        val ifld = fieldPU + AlternatorBase.RESIDUAL_PU
        val sat = ifld * 1.18 / (1.0 + 0.18 * ifld.pow(1.9))
        emfPU = (sat * speedPU * (1.0 - 0.12 * wear.alternator)).coerceAtLeast(0.0)
    }

    /** Reactance on a common system base, used by the bus voltage solve. */
    fun xsOnSystemBase(systemBaseKVA: Double): Double =
        spec.xs * (systemBaseKVA / spec.ratedKVA)

    // ------------------------------------------------------------- thermal

    private fun stepThermal(dt: Double, env: Env) {
        // Exhaust gas temperature follows the combustion calculation quickly.
        egtC = approach(egtC, if (isRunning) egtTarget else env.ambientC, if (isRunning) 3.5 else 90.0, dt)

        val thermostat = if (coolantC < EngineBase.THERMOSTAT_OPEN_C) 0.06
        else remap(coolantC, EngineBase.THERMOSTAT_OPEN_C, EngineBase.THERMOSTAT_FULL_C, 0.06, 1.0)

        val fanFrac = if (spec.fanKW <= 0.5) {
            if (coolantC > 82.0) 1.0 else 0.12
        } else (0.25 + 0.75 * (rpm / spec.ratedRPM).clamp(0.0, 1.15))

        val uaEff = spec.radiatorUA * thermostat * fanFrac *
            (1.0 - 0.45 * radiatorFouling) * spec.waterPumpMul

        // Heat sold into the district loop leaves the jacket before the radiator.
        recoveredHeatKW = if (spec.heatRecoveryFrac > 0.0 && isRunning)
            jacketHeatKW * spec.heatRecoveryFrac else 0.0
        val qJacketW = (jacketHeatKW - recoveredHeatKW) * 1000.0
        val heaterW = if (spec.blockHeater && !isRunning && coolantC < 42.0) 2400.0 else 0.0

        // Both circuits are first-order lags toward an equilibrium temperature.
        // Solving them in that form rather than stepping the heat balance keeps
        // them stable at any timestep -- the oil circuit's time constant is
        // under half a minute with a cooler fitted, so an explicit step will
        // happily run it to infinity when the game is fast-forwarded.
        val coolantMassCp = spec.coolantMassKg * EngineBase.COOLANT_CP
        if (uaEff > 1.0) {
            val tauCool = coolantMassCp / uaEff
            val coolantInf = env.ambientC + (qJacketW + heaterW) / uaEff
            coolantC = approach(coolantC, coolantInf, tauCool, dt)
        } else {
            coolantC += (qJacketW + heaterW) / coolantMassCp * dt
        }

        // Oil: friction heat in, coupled to the jacket, helped by an oil cooler.
        val qOilInW = frictionTorque() * (rpm * 2 * PI / 60.0) * EngineBase.OIL_FRICTION_FRAC +
            fuelRateKgS * EngineBase.LHV * 0.025
        val couple = EngineBase.OIL_COUPLING * (if (spec.oilCooler) 3.2 else 1.0)
        val tauOil = (spec.oilMassKg * EngineBase.OIL_CP) / couple
        val oilInf = coolantC + qOilInW / couple
        oilC = approach(oilC, oilInf, tauOil, dt)

        // Alternator windings: I^2 R heating against a long thermal time constant.
        val iPU = ampsPU
        val targetWinding = env.ambientC + 12.0 + spec.windingRiseK * iPU * iPU *
            (1.0 + 0.25 * wear.alternator)
        windingC = approach(windingC, targetWinding, AlternatorBase.WINDING_TAU_S, dt)

        if (!isRunning) stepThermalOff(dt, env)

        // A guard, not a model: if anything ever does go non-finite, the game
        // should show a broken engine rather than a broken number.
        if (!coolantC.isFinite()) coolantC = env.ambientC
        if (!oilC.isFinite()) oilC = env.ambientC
        if (!windingC.isFinite()) windingC = env.ambientC
        if (!egtC.isFinite()) egtC = env.ambientC
        coolantC = coolantC.clamp(-60.0, 400.0)
        oilC = oilC.clamp(-60.0, 400.0)
        windingC = windingC.clamp(-60.0, 500.0)
        egtC = egtC.clamp(-60.0, 1400.0)
    }

    private fun stepThermalOff(dt: Double, env: Env) {
        // Soaking back to ambient once everything stops.
        coolantC = approach(coolantC, env.ambientC, 1800.0, dt)
        oilC = approach(oilC, env.ambientC, 2570.0, dt)
        windingC = approach(windingC, env.ambientC, 1125.0, dt)
    }

    /**
     * What the pump delivers before the bearings' clearance is accounted for:
     * a function of speed, oil viscosity and how dirty the oil is.
     */
    private val supplyPressureBar: Double
        get() {
            if (rpm < 30.0) return 0.0
            return (EngineBase.OIL_PRESS_RATED_BAR * (rpm / spec.ratedRPM).pow(0.85) *
                viscosityFactor() * spec.oilPressMul * (1.0 - 0.30 * oilCondition))
                .coerceAtMost(EngineBase.OIL_RELIEF_BAR)
        }

    /**
     * Gallery pressure as the gauge reads it. Clearance opens slowly and then
     * all at once, so the quartic keeps normal wear undramatic and makes a
     * failing bottom end unmistakable long before it lets go.
     */
    val oilPressureBar: Double
        get() {
            if (rpm < 30.0) return 0.0
            val p = supplyPressureBar /
                (1.0 + 1.4 * wear.bearings + 6.0 * wear.bearings.pow(4))
            return p.coerceAtMost(EngineBase.OIL_RELIEF_BAR)
        }

    // ------------------------------------------------------- wear & fouling

    private fun stepWearAndFouling(dt: Double) {
        if (!isRunning) return
        val h = dt / 3600.0
        val lf = (brakeKW / (spec.ratedKW / spec.altEff)).clamp(0.0, 2.0)
        val w = spec.wearMul

        // Bearings hate low oil pressure, hot thin oil and dirty oil.
        //
        // The pressure used here deliberately excludes the loss caused by the
        // bearings' own clearance. Worn bearings do drop the gauge, but
        // feeding that back in as a wear multiplier double-counts the same
        // clearance and turns ordinary wear into a runaway that eats an engine
        // in a few hundred hours. What actually thins the oil film is hot,
        // dirty, low-viscosity oil and a starved pump -- so that is what
        // drives the rate.
        val pressFactor = if (supplyPressureBar < 1.4)
            (1.4 / supplyPressureBar.coerceAtLeast(0.15)).pow(1.6).coerceAtMost(3.0) else 1.0
        val oilFactor = 1.0 + 1.8 * oilCondition + 0.015 * (oilC - 95.0).coerceAtLeast(0.0)
        wear.bearings += h * 4.5e-5 * w * lf.pow(1.6) * pressFactor * oilFactor

        // Rings and liners: heat and soot.
        val egtFactor = 1.0 + 0.035 * (egtC - EngineBase.EGT_WARN_C).coerceAtLeast(0.0)
        wear.rings += h * 2.1e-5 * w * lf.pow(1.5) * egtFactor * (1.0 + 2.0 * smokeExcess)

        // Injectors: dirty fuel and hours.
        wear.injectors += h * 3.4e-5 * w * (1.0 + 1.8 * fuelFilterFouling) * (0.5 + lf)

        // Head gasket: fine until peak pressure passes what it can hold, then fast.
        if (peakCylBar > spec.gasketLimitBar) {
            val over = (peakCylBar - spec.gasketLimitBar) / spec.gasketLimitBar
            wear.gasket += h * (0.010 + 0.85 * over * over)
        } else {
            wear.gasket += h * 6.0e-6 * w * lf
        }

        // Turbo: bearing and seal life is mostly an EGT story.
        if (spec.turbo != null) {
            wear.turbo += h * 1.7e-5 * (1.0 + 0.05 * (egtC - 560.0).coerceAtLeast(0.0)) *
                (0.4 + boostBar)
        }

        // Alternator insulation: Arrhenius-ish, life halves every 10 K over class.
        val over = (windingC - 120.0).coerceAtLeast(0.0)
        wear.alternator += h * 1.1e-5 * 2.0.pow(over / 10.0)

        wear.governor += h * 1.3e-5 * (1.0 + 3.0 * abs(sin(huntPhase)))

        // Fouling and oil degradation.
        airFilterFouling = (airFilterFouling + h * 9.0e-4 * spec.filterRestrictMul *
            (rpm / spec.ratedRPM)).coerceAtMost(1.0)
        fuelFilterFouling = (fuelFilterFouling + h * 5.5e-4 * spec.filterRestrictMul).coerceAtMost(1.0)
        radiatorFouling = (radiatorFouling + h * 2.2e-4).coerceAtMost(1.0)
        oilCondition = (oilCondition + h * (1.1e-3 + 0.7e-3 * lf) *
            (1.0 + 1.5 * wear.rings)).coerceAtMost(1.0)

        clampWear()
    }

    private fun clampWear() {
        wear.bearings = wear.bearings.clamp(0.0, 1.0)
        wear.rings = wear.rings.clamp(0.0, 1.0)
        wear.injectors = wear.injectors.clamp(0.0, 1.0)
        wear.gasket = wear.gasket.clamp(0.0, 1.0)
        wear.turbo = wear.turbo.clamp(0.0, 1.0)
        wear.alternator = wear.alternator.clamp(0.0, 1.0)
        wear.governor = wear.governor.clamp(0.0, 1.0)
    }

    private fun stepBattery(dt: Double) {
        if (isRunning && rpm > 400.0) {
            batterySoC = (batterySoC + dt * 0.00022).coerceAtMost(1.0)
        } else if (!isRunning) {
            batterySoC = (batterySoC - dt * 2.0e-7).coerceAtLeast(0.0)
        }
    }

    // ----------------------------------------------------------- protection

    private fun checkProtection(dt: Double, systemFreq: Double, busVoltPU: Double) {
        if (runState == RunState.FAILED) return

        // --- always fitted: mechanical overspeed and the destruction checks
        if (rpm > spec.overspeedTripRPM) {
            fail("Overspeed -- the engine ran away and threw a rod")
            return
        }
        if (wear.gasket >= 1.0) { fail("Head gasket failed -- coolant into the cylinders"); return }
        if (wear.bearings >= 1.0) { fail("Spun a main bearing"); return }
        if (wear.rings >= 1.0) { fail("Ring and liner failure -- no compression left"); return }
        if (wear.alternator >= 1.0) { fail("Alternator winding failure -- insulation broke down"); return }
        if (spec.turbo != null && wear.turbo >= 1.0) {
            alarms += Alarm("TURBO", "Turbocharger failed -- no boost", true)
            spec = spec.copy(turbo = null)
            wear.turbo = 0.999
        }

        if (isRunning) {
            val op = oilPressureBar
            if (op < EngineBase.OIL_PRESS_MIN_BAR) {
                alarms += Alarm("OP", "LOW OIL PRESSURE %.1f bar".format(op), true)
                if (op < 0.45) { emergencyStop("Low oil pressure shutdown"); return }
            }
            if (coolantC > EngineBase.COOLANT_TRIP_C) {
                alarms += Alarm("HT", "HIGH COOLANT TEMP %.0f C".format(coolantC), true)
                emergencyStop("High coolant temperature shutdown"); return
            } else if (coolantC > EngineBase.COOLANT_WARN_C) {
                alarms += Alarm("HT", "Coolant temperature high", false)
            }
            if (egtC > EngineBase.EGT_LIMIT_C) {
                alarms += Alarm("EGT", "EXHAUST TEMP %.0f C -- burning valves".format(egtC), true)
            } else if (egtC > EngineBase.EGT_WARN_C) {
                alarms += Alarm("EGT", "Exhaust temperature high", false)
            }
            if (oilC > EngineBase.OIL_TEMP_LIMIT_C) {
                alarms += Alarm("OT", "Oil temperature %.0f C".format(oilC), true)
            }
            if (windingC > spec.windingLimitC) {
                alarms += Alarm("STA", "Stator over temperature %.0f C".format(windingC), true)
            }
            if (smokeExcess > 0.25) {
                alarms += Alarm("SMK", "Overfuelling -- heavy smoke", false)
            }
            if (peakCylBar > spec.gasketLimitBar) {
                alarms += Alarm("PCP", "Peak cylinder pressure over limit", true)
            }
            if (kva > spec.ratedKVA * 1.02) {
                alarms += Alarm("OL", "Overload %.0f kVA of %.0f".format(kva, spec.ratedKVA), kva > spec.ratedKVA * 1.15)
            }
        }

        // --- relay package, only if bought
        if (onBus && "32" in spec.protection && elecKW < -spec.ratedKW * 0.08) {
            reversePowerTimer += dt
            alarms += Alarm("32", "REVERSE POWER -- motoring at %.1f kW".format(elecKW), true)
            if (reversePowerTimer > 12.0) {
                openBreaker("Reverse power relay (32)"); reversePowerTimer = 0.0
            }
        } else reversePowerTimer = 0.0

        if (onBus && "81" in spec.protection) {
            if (systemFreq > Nominal.FREQ_TRIP_HI || systemFreq < Nominal.FREQ_TRIP_LO) {
                openBreaker("Frequency relay (81) at %.1f Hz".format(systemFreq))
            }
        }
        if (onBus && "27/59" in spec.protection) {
            val pct = (busVoltPU - 1.0) * 100.0
            if (pct > Nominal.VOLT_TRIP_HI_PCT || pct < -Nominal.VOLT_TRIP_LO_PCT) {
                openBreaker("Voltage relay (27/59) at %.0f V".format(busVoltPU * Nominal.GEN_VOLTS))
            }
        }

        // Thermal-magnetic breaker: always fitted, inverse-time overload.
        if (onBus && kva > spec.ratedKVA * 1.18) {
            overloadIntegral += dt * ((kva / spec.ratedKVA) - 1.0).pow(2)
            if (overloadIntegral > 55.0) {
                openBreaker("Overcurrent -- main breaker tripped")
                overloadIntegral = 0.0
            }
        } else {
            overloadIntegral = (overloadIntegral - dt * 0.35).coerceAtLeast(0.0)
        }

        if (!isRunning && breakerClosed) openBreaker("Unit stopped while on line")
    }

    private var reversePowerTimer = 0.0
    private var overloadIntegral = 0.0

    private fun fail(reason: String) {
        runState = RunState.FAILED
        failureText = reason
        lastTripReason = reason
        breakerClosed = false
        onBus = false
        fuelValveOpen = false
        rack = 0.0
    }

    fun openBreaker(reason: String?) {
        if (breakerClosed) lastTripReason = reason
        breakerClosed = false
        onBus = false
        elecKW = 0.0
        kvar = 0.0
    }

    // -------------------------------------------------------- synchronising

    data class SyncCheck(
        val slipHz: Double,
        val angleDeg: Double,
        val voltErrPct: Double,
        val slipOk: Boolean,
        val angleOk: Boolean,
        val voltOk: Boolean,
        val directionOk: Boolean,
    ) {
        val allOk: Boolean get() = slipOk && angleOk && voltOk
    }

    fun syncCheck(busFreq: Double, busVoltPU: Double): SyncCheck {
        val slip = freqHz - busFreq
        val vErr = if (busVoltPU > 0.05) (emfPU - busVoltPU) / busVoltPU * 100.0 else 100.0
        // The pointer can cross top dead centre entirely within one step when
        // the game is running fast. Closing "as it passes twelve" has to mean
        // passing, not being sampled there.
        val sweptThroughZero = prevPhaseDeg * phaseDeg < 0.0 &&
            abs(prevPhaseDeg) + abs(phaseDeg) < 120.0
        return SyncCheck(
            slipHz = slip,
            angleDeg = phaseDeg,
            voltErrPct = vErr,
            slipOk = abs(slip) < 0.30,
            angleOk = abs(phaseDeg) < 12.0 || sweptThroughZero,
            voltOk = abs(vErr) < 5.0,
            // Best practice: come in very slightly fast so you pick up load,
            // not so the bus motors you.
            directionOk = slip in 0.02..0.30,
        )
    }

    /**
     * Close the main breaker. Returns the per-unit torque shock: under about
     * 1.5 pu it is a bump, above 3 pu you are bending things.
     */
    fun closeBreaker(busFreq: Double, busVoltPU: Double, force: Boolean): Double {
        if (!isRunning) return 0.0
        val c = syncCheck(busFreq, busVoltPU)
        if (!force && !c.allOk) return -1.0

        // If the pointer swept through zero during the step, the breaker
        // closes at the crossing, not at where the angle happened to land.
        val sweptThroughZero = prevPhaseDeg * phaseDeg < 0.0 &&
            abs(prevPhaseDeg) + abs(phaseDeg) < 120.0
        val effectiveAngle = if (!force && sweptThroughZero) 0.0 else phaseDeg
        val angleRad = effectiveAngle * PI / 180.0
        // Current surge on closing out of phase, in per-unit of rated.
        val xTotal = (spec.xs * 0.16 + 0.10)     // subtransient plus system
        val shock = abs(2.0 * sin(angleRad / 2.0)) / xTotal +
            abs(c.voltErrPct) / 100.0 / xTotal +
            abs(c.slipHz) * 0.6
        syncShockPU = shock

        breakerClosed = true
        onBus = true
        phaseDeg = 0.0

        // Damage scales hard once you are past a gentle close.
        if (shock > 1.5) {
            val sev = (shock - 1.5) / 4.0
            wear.bearings += sev * 0.10
            wear.alternator += sev * 0.14
            wear.governor += sev * 0.05
            if (shock > 6.0) fail("Out-of-phase closure -- sheared the coupling")
        }
        return shock
    }

    // ------------------------------------------------------------- servicing

    fun performService(key: String) {
        service.reset(key)
        when (key) {
            "oil" -> { oilCondition = 0.02 }
            "fuelFilter" -> fuelFilterFouling = 0.0
            "airFilter" -> airFilterFouling = 0.0
            "valveLash" -> {}
            "injectors" -> wear.injectors = (wear.injectors * 0.25).coerceAtMost(0.20)
            "coolant" -> {}
            "radiator" -> radiatorFouling = 0.0
        }
    }

    fun performRepair(key: String) {
        when (key) {
            "bearings" -> wear.bearings = 0.01
            "rings" -> { wear.rings = 0.01; wear.gasket = 0.01 }
            "gasket" -> wear.gasket = 0.01
            "turbo" -> wear.turbo = 0.01
            "alternator" -> wear.alternator = 0.01
            "governor" -> wear.governor = 0.01
            "full" -> {
                wear = Wear(0.01, 0.01, 0.02, 0.01, if (spec.turbo != null) 0.02 else 0.0, 0.02, 0.02)
                oilCondition = 0.0
            }
        }
        if (runState == RunState.FAILED) {
            runState = RunState.STOPPED
            failureText = null
            rpm = 0.0
        }
    }
}
