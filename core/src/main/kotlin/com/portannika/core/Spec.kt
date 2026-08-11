package com.portannika.core

import kotlin.math.min

/**
 * The "effective machine" a genset is built from. The simulation reads only
 * this; it never looks at the tech tree. Purchased second-hand units get a
 * [GensetSpec] straight from their market listing instead.
 */
data class GensetSpec(
    val name: String,
    val make: String,
    // engine
    val cylinders: Int,
    val displacementL: Double,
    val volEff: Double,
    val filterRestrictMul: Double,
    val maxFuelPerStroke: Double,
    val indEffPeak: Double,
    val smokeAFR: Double,
    val timingAdvance: Double,
    val inertia: Double,
    val bmepLimitBar: Double,
    val gasketLimitBar: Double,
    val wearMul: Double,
    val oilPressMul: Double,
    val turbo: Turbo?,
    val aftercoolerEff: Double,
    val idleRPM: Double,
    val ratedRPM: Double,
    val overspeedTripRPM: Double,
    // cooling
    val radiatorUA: Double,
    val waterPumpMul: Double,
    val oilCooler: Boolean,
    val fanKW: Double,
    val coolantMassKg: Double,
    val oilMassKg: Double,
    // alternator
    val ratedKVA: Double,
    val altEff: Double,
    val xs: Double,
    val fieldTauS: Double,
    val windingRiseK: Double,
    val windingLimitC: Double,
    val brushless: Boolean,
    val pmg: Boolean,
    val motorStartMul: Double,
    // controls
    val avr: Boolean,
    val varShare: Boolean,
    val egov: Boolean,
    val isoch: Boolean,
    val autoSync: Boolean,
    val loadShare: Boolean,
    val autoStart: Boolean,
    val blockHeater: Boolean,
    val protection: Set<String>,
    val droopMin: Double,
    val droopMax: Double,
    // recovery
    val heatRecoveryFrac: Double,
    val economizerFrac: Double,
    // headline ratings
    val ratedKW: Double,
    val engineKWLimit: Double,
    val altKWLimit: Double,
) {
    /** Displacement swept per crank revolution for a 4-stroke, in m^3. */
    val sweptPerRevM3: Double get() = displacementL * 1e-3 / 2.0
}

/** Plant-wide capabilities, folded from the "plant" and "ctrl" branches. */
data class PlantSpec(
    val unitSlots: Int,
    val maxUnitKW: Double,
    val fuelTankL: Double,
    val hasSwitchgear: Boolean,
    val hasPowerhouse: Boolean,
    val hasOwnBus: Boolean,
    val hasStepUp: Boolean,
    val hasFuelFarm: Boolean,
    val hasControlRoom: Boolean,
    val n1Certified: Boolean,
    val hasEngineHall: Boolean,
    val scada: Boolean,
) {
    /** Fraction of revenue the co-op keeps for moving your power on their line. */
    val wheelingFrac: Double get() = if (hasStepUp) 0.0 else Econ.WHEELING_FRAC
    val fuelPriceMul: Double get() = if (hasFuelFarm) 1.0 - Econ.BARGE_DISCOUNT else 1.0
}

private fun foldMods(owned: Set<String>): Mods {
    var volEffMul = 1.0; var filterRestrictMul = 1.0; var fuelMaxMul = 1.0
    var indEffAdd = 0.0; var smokeAFRAdd = 0.0; var timingAdvance = 0.0
    var radiatorUAMul = 1.0; var waterPumpMul = 1.0; var inertiaAdd = 0.0
    var bmepLimitMul = 1.0; var wearMul = 1.0; var gasketMul = 1.0; var oilPressMul = 1.0
    var altKVAMul = 1.0; var altEffAdd = 0.0; var altXsMul = 1.0; var fieldTauMul = 1.0
    var windingCoolK = 0.0; var motorStartMul = 1.0; var fuelTankAdd = 0.0
    var droopMinAdd = 0.0; var unitSlots = 0; var maxUnitKW = 0.0
    var turbo: Turbo? = null
    var aftercoolerEff: Double? = null
    var heatRecoveryFrac: Double? = null
    var economizerFrac: Double? = null
    val flags = mutableSetOf<Flag>()
    val protection = mutableSetOf<String>()

    for (id in owned) {
        val m = NODE_BY_ID[id]?.mods ?: continue
        volEffMul *= m.volEffMul
        filterRestrictMul *= m.filterRestrictMul
        fuelMaxMul *= m.fuelMaxMul
        indEffAdd += m.indEffAdd
        smokeAFRAdd += m.smokeAFRAdd
        timingAdvance += m.timingAdvance
        radiatorUAMul *= m.radiatorUAMul
        waterPumpMul *= m.waterPumpMul
        inertiaAdd += m.inertiaAdd
        bmepLimitMul *= m.bmepLimitMul
        wearMul *= m.wearMul
        gasketMul *= m.gasketMul
        oilPressMul *= m.oilPressMul
        altKVAMul *= m.altKVAMul
        altEffAdd += m.altEffAdd
        altXsMul *= m.altXsMul
        fieldTauMul *= m.fieldTauMul
        windingCoolK += m.windingCoolK
        motorStartMul *= m.motorStartMul
        fuelTankAdd += m.fuelTankAdd
        droopMinAdd += m.droopMinAdd
        unitSlots += m.unitSlots
        if (m.maxUnitKW > maxUnitKW) maxUnitKW = m.maxUnitKW
        // Later turbo / cooler nodes replace earlier ones outright.
        m.turbo?.let { turbo = it }
        m.aftercoolerEff?.let { aftercoolerEff = it }
        m.heatRecoveryFrac?.let { heatRecoveryFrac = it }
        m.economizerFrac?.let { economizerFrac = it }
        flags += m.flags
        protection += m.protection
    }

    return Mods(
        volEffMul, filterRestrictMul, fuelMaxMul, indEffAdd, smokeAFRAdd, timingAdvance,
        radiatorUAMul, waterPumpMul, inertiaAdd, bmepLimitMul, wearMul, gasketMul,
        oilPressMul, altKVAMul, altEffAdd, altXsMul, fieldTauMul, windingCoolK,
        motorStartMul, fuelTankAdd, droopMinAdd, turbo, aftercoolerEff,
        heatRecoveryFrac, economizerFrac, unitSlots, maxUnitKW, flags, protection,
    )
}

/** Build Unit 8's effective spec from the owned tech-tree nodes. */
fun buildUnit8Spec(owned: Set<String>): GensetSpec {
    val m = foldMods(owned)
    val ratedKVA = AlternatorBase.RATED_KVA * m.altKVAMul
    val altKWLimit = ratedKVA * AlternatorBase.RATED_PF

    // What the engine can safely make: BMEP limit at rated speed.
    val bmepLimit = 6.2 * m.bmepLimitMul
    val sweptPerRev = EngineBase.DISPLACEMENT_L * 1e-3 / 2.0
    val revPerSec = Nominal.RPM / 60.0
    // P = BMEP * Vswept_per_rev * rev/s   (bar -> Pa is 1e5)
    val engineShaftKW = bmepLimit * 1e5 * sweptPerRev * revPerSec / 1000.0
    val altEff = AlternatorBase.EFF_RATED + m.altEffAdd
    val engineKWLimit = engineShaftKW * altEff

    return GensetSpec(
        name = "Unit 8",
        make = EngineBase.NAME,
        cylinders = EngineBase.CYLINDERS,
        displacementL = EngineBase.DISPLACEMENT_L,
        volEff = EngineBase.VOL_EFF * m.volEffMul,
        filterRestrictMul = m.filterRestrictMul,
        maxFuelPerStroke = EngineBase.MAX_FUEL_PER_STROKE * m.fuelMaxMul,
        indEffPeak = EngineBase.IND_EFF_PEAK + m.indEffAdd,
        smokeAFR = maxOf(16.5, EngineBase.SMOKE_AFR + m.smokeAFRAdd),
        timingAdvance = m.timingAdvance,
        inertia = EngineBase.INERTIA + m.inertiaAdd,
        bmepLimitBar = bmepLimit,
        gasketLimitBar = 125.0 * m.gasketMul,
        wearMul = m.wearMul,
        oilPressMul = m.oilPressMul,
        turbo = m.turbo,
        aftercoolerEff = m.aftercoolerEff ?: 0.0,
        idleRPM = EngineBase.IDLE_RPM,
        ratedRPM = Nominal.RPM,
        overspeedTripRPM = EngineBase.OVERSPEED_TRIP_RPM,
        radiatorUA = EngineBase.RADIATOR_UA * m.radiatorUAMul,
        waterPumpMul = m.waterPumpMul,
        oilCooler = Flag.OIL_COOLER in m.flags,
        fanKW = if (Flag.FAN_ELECTRIC in m.flags) 0.35 else EngineBase.FAN_KW,
        coolantMassKg = EngineBase.COOLANT_MASS_KG,
        oilMassKg = EngineBase.OIL_MASS_KG,
        ratedKVA = ratedKVA,
        altEff = altEff,
        xs = AlternatorBase.XS * m.altXsMul,
        fieldTauS = AlternatorBase.FIELD_TAU_S * m.fieldTauMul,
        windingRiseK = maxOf(30.0, AlternatorBase.WINDING_RISE_K - m.windingCoolK),
        windingLimitC = AlternatorBase.WINDING_LIMIT_C,
        brushless = Flag.BRUSHLESS in m.flags,
        pmg = Flag.PMG in m.flags,
        motorStartMul = m.motorStartMul,
        avr = Flag.AVR in m.flags,
        varShare = Flag.VAR_SHARE in m.flags,
        egov = Flag.EGOV in m.flags,
        isoch = Flag.ISOCH in m.flags,
        autoSync = Flag.AUTO_SYNC in m.flags,
        loadShare = Flag.LOAD_SHARE in m.flags,
        autoStart = Flag.AUTO_START in m.flags,
        blockHeater = Flag.BLOCK_HEATER in m.flags,
        protection = m.protection,
        droopMin = maxOf(0.0, GovernorBase.DROOP_MIN + m.droopMinAdd),
        droopMax = GovernorBase.DROOP_MAX,
        heatRecoveryFrac = m.heatRecoveryFrac ?: 0.0,
        economizerFrac = m.economizerFrac ?: 0.0,
        ratedKW = min(engineKWLimit, altKWLimit),
        engineKWLimit = engineKWLimit,
        altKWLimit = altKWLimit,
    )
}

fun buildPlantSpec(owned: Set<String>): PlantSpec {
    val m = foldMods(owned)
    return PlantSpec(
        unitSlots = 1 + m.unitSlots,
        maxUnitKW = maxOf(120.0, m.maxUnitKW),
        fuelTankL = Econ.FUEL_TANK_L + m.fuelTankAdd,
        hasSwitchgear = Flag.SWITCHGEAR in m.flags,
        hasPowerhouse = Flag.POWERHOUSE in m.flags,
        hasOwnBus = Flag.OWN_BUS in m.flags,
        hasStepUp = Flag.STEP_UP in m.flags,
        hasFuelFarm = Flag.FUEL_FARM in m.flags,
        hasControlRoom = Flag.CONTROL_ROOM in m.flags,
        n1Certified = Flag.N1_CERTIFIED in m.flags,
        hasEngineHall = Flag.ENGINE_HALL in m.flags,
        scada = Flag.SCADA in m.flags,
    )
}

/**
 * Second-hand machines bought from the market. They arrive at a fixed
 * specification -- you cannot put the Unit 8 tech tree into them -- but
 * plant-wide control upgrades still reach them through [applyPlantControls].
 */
fun marketSpec(
    name: String,
    make: String,
    kW: Double,
    displacementL: Double,
    cylinders: Int,
    turbocharged: Boolean,
    aftercooled: Boolean,
    bsfcKgPerKWh: Double,
    condition: Double,
): GensetSpec {
    val turbo = if (turbocharged) Turbo(if (aftercooled) 1.10 else 0.80, 1.1, 0.68) else null
    val sweptPerRev = displacementL * 1e-3 / 2.0
    val revPerSec = Nominal.RPM / 60.0
    val altEff = 0.925 + (kW / 4000.0).clamp(0.0, 0.03)
    val shaftKW = kW / altEff
    // Back out the BMEP this machine actually runs at, then allow 12% headroom.
    val bmep = shaftKW * 1000.0 / (1e5 * sweptPerRev * revPerSec)
    // Indicated efficiency implied by the quoted fuel consumption.
    val indEff = (3600.0 / (bsfcKgPerKWh * EngineBase.LHV / 1000.0) * 1.16).clamp(0.30, 0.50)
    val kVA = kW / 0.8

    return GensetSpec(
        name = name, make = make,
        cylinders = cylinders,
        displacementL = displacementL,
        volEff = (if (turbocharged) 0.90 else 0.86) * lerp(0.93, 1.0, condition),
        filterRestrictMul = 1.0,
        // Fuel per stroke that yields the rated shaft power at the rated point.
        maxFuelPerStroke = shaftKW * 1000.0 / (indEff * EngineBase.LHV) /
            (cylinders * revPerSec / 2.0) * 1.28,
        indEffPeak = indEff,
        smokeAFR = if (turbocharged) 19.0 else 20.5,
        timingAdvance = 0.0,
        inertia = 3.1 * (kW / 50.0).coerceAtLeast(0.6),
        bmepLimitBar = bmep * 1.12,
        gasketLimitBar = if (turbocharged) 190.0 else 130.0,
        wearMul = lerp(1.9, 0.85, condition),
        oilPressMul = lerp(0.72, 1.05, condition),
        turbo = turbo,
        aftercoolerEff = if (aftercooled) 0.66 else 0.0,
        idleRPM = 700.0,
        ratedRPM = Nominal.RPM,
        overspeedTripRPM = Nominal.RPM * 1.20,
        radiatorUA = EngineBase.RADIATOR_UA * (kW / 50.0) * lerp(0.80, 1.05, condition),
        waterPumpMul = 1.0,
        oilCooler = kW >= 150.0,
        fanKW = EngineBase.FAN_KW * (kW / 50.0).coerceAtMost(4.0),
        coolantMassKg = EngineBase.COOLANT_MASS_KG * (kW / 50.0).coerceAtLeast(0.7),
        oilMassKg = EngineBase.OIL_MASS_KG * (kW / 50.0).coerceAtLeast(0.7),
        ratedKVA = kVA,
        altEff = altEff,
        xs = 1.7,
        fieldTauS = 0.4,
        windingRiseK = 80.0,
        windingLimitC = AlternatorBase.WINDING_LIMIT_C,
        brushless = kW >= 120.0,
        pmg = false,
        motorStartMul = 1.0,
        avr = kW >= 100.0,
        varShare = false,
        egov = false, isoch = false, autoSync = false, loadShare = false, autoStart = false,
        blockHeater = false,
        protection = if (kW >= 100.0) setOf("32", "81") else emptySet(),
        droopMin = GovernorBase.DROOP_MIN,
        droopMax = GovernorBase.DROOP_MAX,
        heatRecoveryFrac = 0.0,
        economizerFrac = 0.0,
        ratedKW = kW,
        engineKWLimit = kW * 1.12,
        altKWLimit = kVA * 0.8,
    )
}

/**
 * Plant-wide control upgrades reach every unit on the bus once the switchgear
 * is in, so a purchased machine inherits AVR, auto-sync, load sharing and so on
 * from the control room rather than needing its own.
 */
fun GensetSpec.applyPlantControls(owned: Set<String>, plant: PlantSpec): GensetSpec {
    if (!plant.hasSwitchgear) return this
    val m = foldMods(owned)
    return copy(
        avr = avr || Flag.AVR in m.flags,
        varShare = varShare || Flag.VAR_SHARE in m.flags,
        egov = egov || Flag.EGOV in m.flags,
        isoch = isoch || Flag.ISOCH in m.flags,
        autoSync = autoSync || Flag.AUTO_SYNC in m.flags,
        loadShare = loadShare || Flag.LOAD_SHARE in m.flags,
        autoStart = autoStart || Flag.AUTO_START in m.flags,
        blockHeater = blockHeater || Flag.BLOCK_HEATER in m.flags,
        protection = protection + m.protection,
        droopMin = minOf(droopMin, maxOf(0.0, GovernorBase.DROOP_MIN + m.droopMinAdd)),
    )
}
