package com.portannika.core

/* ============================================================================
 *  Upgrade tech tree.
 *
 *  Seven branches uprate Unit 8 itself; the eighth builds the plant around it.
 *  Prerequisites cross branches on purpose -- you cannot fuel an engine you
 *  have not given air to, and you cannot sell 140 kW through a 62 kVA
 *  alternator. Each node carries a [Mods]; buying it stacks those onto the
 *  base machine (see Spec.kt).
 * ========================================================================== */

enum class Flag {
    OIL_COOLER, FAN_ELECTRIC, BRUSHLESS, PMG,
    AVR, VAR_SHARE, EGOV, ISOCH, AUTO_SYNC, LOAD_SHARE, AUTO_START, SCADA,
    BLOCK_HEATER,
    // Plant infrastructure
    SWITCHGEAR, POWERHOUSE, OWN_BUS, STEP_UP, FUEL_FARM, CONTROL_ROOM,
    N1_CERTIFIED, ENGINE_HALL,
}

@kotlinx.serialization.Serializable
data class Turbo(val maxBoostBar: Double, val tau: Double, val eff: Double)

data class Mods(
    val volEffMul: Double = 1.0,
    val filterRestrictMul: Double = 1.0,
    val fuelMaxMul: Double = 1.0,
    val indEffAdd: Double = 0.0,
    val smokeAFRAdd: Double = 0.0,
    val timingAdvance: Double = 0.0,
    val radiatorUAMul: Double = 1.0,
    val waterPumpMul: Double = 1.0,
    val inertiaAdd: Double = 0.0,
    val bmepLimitMul: Double = 1.0,
    val wearMul: Double = 1.0,
    val gasketMul: Double = 1.0,
    val oilPressMul: Double = 1.0,
    val altKVAMul: Double = 1.0,
    val altEffAdd: Double = 0.0,
    val altXsMul: Double = 1.0,
    val fieldTauMul: Double = 1.0,
    val windingCoolK: Double = 0.0,
    val motorStartMul: Double = 1.0,
    val fuelTankAdd: Double = 0.0,
    val droopMinAdd: Double = 0.0,
    val turbo: Turbo? = null,
    val aftercoolerEff: Double? = null,
    val heatRecoveryFrac: Double? = null,
    val economizerFrac: Double? = null,
    val unitSlots: Int = 0,
    val maxUnitKW: Double = 0.0,
    val flags: Set<Flag> = emptySet(),
    val protection: Set<String> = emptySet(),
)

data class Branch(val id: String, val name: String, val colorHex: Long, val blurb: String)

val BRANCHES = listOf(
    Branch("air", "Air & Aspiration", 0xFF5FB3D4, "Get more air in and the fuel has somewhere to go."),
    Branch("fuel", "Fuel System", 0xFFD99A3C, "Pump, injectors, timing. Where the power actually comes from."),
    Branch("cool", "Cooling & Thermal", 0xFF4FBF87, "Heat rejection is the ceiling on continuous rating."),
    Branch("mech", "Bottom End", 0xFFB98B6A, "What the block survives, and how steadily it turns."),
    Branch("elec", "Alternator", 0xFFC46FB0, "The other half of a genset: rating, excitation, fault current."),
    Branch("ctrl", "Controls", 0xFF8B8CE0, "Automate the parts of the job you are tired of doing by hand."),
    Branch("recov", "Recovery", 0xFFCFC45A, "Sell the two thirds of the fuel you currently throw away."),
    Branch("plant", "The Plant", 0xFFE0674F, "Stop being a man with a generator. Become a power station."),
)

data class TechNode(
    val id: String,
    val branch: String,
    val tier: Int,
    val name: String,
    val cost: Double,
    val req: List<String>,
    val desc: String,
    val effect: String,
    val mods: Mods,
)

val NODES: List<TechNode> = listOf(
    // ---------------------------------------------------------------- AIR
    TechNode("air1", "air", 0, "Cyclonic pre-cleaner", 340.0, emptyList(),
        "Spins the salt haze and sawdust out before the paper element ever sees it. The filter stops loading up in a week.",
        "Air filter fouls 60% slower. +2% volumetric efficiency.",
        mods(volEffMul = 1.02, filterRestrictMul = 0.40)),
    TechNode("air2", "air", 1, "Ported head & manifold match", 1450.0, listOf("air1"),
        "Two weeks with a die grinder and a flow bench borrowed from the cannery shop.",
        "+6% volumetric efficiency. Slightly lower EGT at the same fuelling.",
        mods(volEffMul = 1.06)),
    TechNode("air3", "air", 2, "Turbocharger", 4900.0, listOf("air2", "mech2"),
        "A used marine turbo off a scrapped seiner, rebuilt on the bench. The single biggest thing you can do to this engine. Needs the head studs first -- boost on the stock gasket will lift the head.",
        "Up to 0.85 bar boost. Much more air, so much more usable fuel.",
        mods(turbo = Turbo(0.85, 1.5, 0.66))),
    TechNode("air4", "air", 3, "Air-to-air aftercooler", 3100.0, listOf("air3"),
        "Charge air off the turbo leaves around 150 C. Cool it and it gets dense again.",
        "68% effective intercooling. Denser charge, much lower EGT, real headroom.",
        mods(aftercoolerEff = 0.68, smokeAFRAdd = -0.7)),
    TechNode("air5", "air", 4, "Wastegate & boost control", 2200.0, listOf("air4"),
        "Lets you size the turbine for low-speed response without cooking it at full load.",
        "Boost rises 45% faster and is held flat. Reduces turbo wear.",
        mods(turbo = Turbo(1.05, 0.85, 0.70))),
    TechNode("air6", "air", 5, "Variable geometry turbine", 6800.0, listOf("air5", "ctrl4"),
        "Needs the electronic governor to drive the vane actuator. Near-instant response from idle.",
        "Up to 1.35 bar with almost no lag. Another step in the clean fuelling limit.",
        mods(turbo = Turbo(1.35, 0.35, 0.74), smokeAFRAdd = -0.6)),

    // --------------------------------------------------------------- FUEL
    TechNode("fuel1", "fuel", 0, "Injector pop-test & rebuild", 520.0, emptyList(),
        "Three of the six were dribbling. Now they all break at the same pressure and spray a cone instead of a stream.",
        "+1.5% indicated efficiency, cleaner burn, injectors wear slower.",
        mods(indEffAdd = 0.015, smokeAFRAdd = -0.5)),
    TechNode("fuel2", "fuel", 1, "Injection timing advance kit", 780.0, listOf("fuel1"),
        "Reground pump drive key. Starts the burn earlier so peak pressure lands where it should.",
        "+3 deg timing: more power and efficiency, but higher peak cylinder pressure.",
        mods(timingAdvance = 3.0, indEffAdd = 0.012)),
    TechNode("fuel3", "fuel", 2, "High-pressure inline pump", 3600.0, listOf("fuel2"),
        "Larger plungers and a stiffer cam. Meaningfully more fuel per stroke at a higher line pressure.",
        "+32% maximum fuelling, better atomisation.",
        mods(fuelMaxMul = 1.32, indEffAdd = 0.008, smokeAFRAdd = -0.4)),
    TechNode("fuel4", "fuel", 3, "Large-bore injector nozzles", 1900.0, listOf("fuel3", "air3"),
        "Pointless without boost -- all you get is smoke. With the turbo behind it, free power.",
        "+18% maximum fuelling.",
        mods(fuelMaxMul = 1.18)),
    TechNode("fuel5", "fuel", 4, "Electronic unit injectors", 8200.0, listOf("fuel4", "ctrl4"),
        "Cam-driven, solenoid-timed. Timing and quantity become software instead of a key and a shim.",
        "+20% fuelling, +3.5% indicated efficiency, timing optimised continuously.",
        mods(fuelMaxMul = 1.20, indEffAdd = 0.035, smokeAFRAdd = -0.8, timingAdvance = 1.0)),
    TechNode("fuel6", "fuel", 5, "Common rail conversion", 14500.0, listOf("fuel5"),
        "A 1600 bar accumulator and pilot injection. The engine stops sounding like a diesel.",
        "+15% fuelling, +4% indicated efficiency, very clean combustion.",
        mods(fuelMaxMul = 1.15, indEffAdd = 0.040, smokeAFRAdd = -1.2)),

    // ------------------------------------------------------------- COOLING
    TechNode("cool1", "cool", 0, "Rod out & re-core radiator", 420.0, emptyList(),
        "Twenty years of cottonwood fluff and mineral scale. The core was doing maybe two thirds of its job.",
        "+22% radiator capacity, and it fouls more slowly.",
        mods(radiatorUAMul = 1.22)),
    TechNode("cool2", "cool", 1, "High-flow water pump & thermostat", 690.0, listOf("cool1"),
        "Better impeller, and a thermostat that actually opens at its rated temperature.",
        "+15% coolant flow, tighter temperature control, faster warm-up.",
        mods(waterPumpMul = 1.15, radiatorUAMul = 1.06)),
    TechNode("cool3", "cool", 2, "Engine oil cooler", 1150.0, listOf("cool2"),
        "Plate cooler in the jacket circuit. Oil stops thinning out at high load, so the bearings stop complaining.",
        "Oil runs 15-25 C cooler at load. Much slower bearing wear, better oil pressure.",
        mods(oilCoolerFlag = true, wearMul = 0.72, oilPressMul = 1.12)),
    TechNode("cool4", "cool", 3, "Electric fan & shutter control", 2400.0, listOf("cool3"),
        "The belt fan eats nearly two kilowatts whether you need it or not. This one runs when it is needed.",
        "Recovers up to 1.9 kW of parasitic loss. Automatic temperature control.",
        mods(fanElectricFlag = true, radiatorUAMul = 1.10)),
    TechNode("cool5", "cool", 4, "Remote radiator & upsized core", 4600.0, listOf("cool4"),
        "Mounted outside on the seaward wall, twice the frontal area. Cold coastal air is a resource.",
        "+55% radiator capacity. Holds full rating on the warmest day of the year.",
        mods(radiatorUAMul = 1.55)),

    // ---------------------------------------------------------- BOTTOM END
    TechNode("mech1", "mech", 0, "Heavy flywheel", 880.0, emptyList(),
        "Cast iron, 38 kg heavier. Frequency stops diving every time the sawmill starts.",
        "+45% rotating inertia. Much smaller frequency dip on load steps.",
        mods(inertiaAdd = 1.4)),
    TechNode("mech2", "mech", 1, "Head studs & fire-ring gasket", 1250.0, listOf("mech1"),
        "Studs instead of bolts, and a gasket that will hold a real cylinder pressure.",
        "Head gasket survives 2.4x the cylinder pressure. Required before serious boost.",
        mods(gasketMul = 2.4, bmepLimitMul = 1.15)),
    TechNode("mech3", "mech", 2, "Forged pistons & rods", 4200.0, listOf("mech2"),
        "The cast pistons in it now will crack a ring land somewhere north of 90 kW. These will not.",
        "+35% safe BMEP. Piston and ring wear cut by a third.",
        mods(bmepLimitMul = 1.35, wearMul = 0.80)),
    TechNode("mech4", "mech", 3, "Tri-metal bearings & oil galleries", 2700.0, listOf("mech3"),
        "Copper-lead-tin shells and drilled galleries that feed the top end properly.",
        "Bearing wear cut by half, +20% oil pressure, higher load ceiling.",
        mods(wearMul = 0.55, oilPressMul = 1.20, bmepLimitMul = 1.08)),
    TechNode("mech5", "mech", 4, "Nitrided crank & main girdle", 6900.0, listOf("mech4"),
        "A girdle ties the main caps together. The bottom end stops flexing under peak pressure.",
        "+25% safe BMEP. Effectively removes the block as the limiting factor.",
        mods(bmepLimitMul = 1.25, wearMul = 0.85, gasketMul = 1.3)),

    // ---------------------------------------------------------- ALTERNATOR
    TechNode("elec1", "elec", 0, "Clean, dip & bake windings", 380.0, emptyList(),
        "Twenty years of conductive salt film off the end turns, then re-varnished.",
        "Windings run 8 C cooler. +0.6% alternator efficiency.",
        mods(altEffAdd = 0.006, windingCoolK = 8.0)),
    TechNode("elec2", "elec", 1, "Brushless rotating rectifier", 2100.0, listOf("elec1"),
        "No more brushes dusting the inside of the machine, no more slip ring pitting.",
        "Removes brush maintenance, faster field response, +0.4% efficiency.",
        mods(brushlessFlag = true, altEffAdd = 0.004, fieldTauMul = 0.7)),
    TechNode("elec3", "elec", 2, "Stator rewind, larger conductor", 4300.0, listOf("elec2"),
        "Same frame, heavier copper, better slot fill. The iron was never the limit.",
        "+38% kVA rating, lower reactance, +0.8% efficiency.",
        mods(altKVAMul = 1.38, altXsMul = 0.90, altEffAdd = 0.008)),
    TechNode("elec4", "elec", 3, "PMG excitation support", 2600.0, listOf("elec3"),
        "A permanent magnet generator on the shaft feeds the exciter, so the field holds up through a fault or a big motor start.",
        "Sustains 3x rated current for 10 s. Motor starts stop dragging the bus down.",
        mods(pmgFlag = true, motorStartMul = 2.2)),
    TechNode("elec5", "elec", 4, "Larger frame alternator", 9800.0, listOf("elec4", "mech4"),
        "A 190 kVA frame on a fabricated adaptor. Only worth it once the engine can feed it.",
        "190 kVA frame: +2.2% efficiency and much lower reactance.",
        mods(altKVAMul = 2.20, altXsMul = 0.78, altEffAdd = 0.022)),

    // ------------------------------------------------------------ CONTROLS
    TechNode("ctrl1", "ctrl", 0, "Protective relay package", 950.0, emptyList(),
        "Reverse power, over/under frequency and over/under voltage relays wired to the breaker trip coil.",
        "Automatic trips on reverse power (32), frequency (81) and voltage (27/59).",
        mods(protection = setOf("32", "81", "27/59"))),
    TechNode("ctrl2", "ctrl", 1, "Automatic voltage regulator", 1700.0, listOf("ctrl1"),
        "Holds terminal volts without you standing at the rheostat. Set the reference and walk away.",
        "Automatic excitation with adjustable voltage droop for VAR sharing.",
        mods(avrFlag = true)),
    TechNode("ctrl3", "ctrl", 2, "Cross-current VAR compensation", 1300.0, listOf("ctrl2"),
        "Ties your AVR into the station CT loop so your machine takes its fair share of the reactive load.",
        "Automatic kVAR sharing with the other units. No more chasing the field.",
        mods(varShareFlag = true)),
    TechNode("ctrl4", "ctrl", 3, "Electronic governor & actuator", 3900.0, listOf("ctrl2"),
        "Magnetic pickup on the flywheel, PID controller, proportional rack actuator. The flyweights come off.",
        "Droop adjustable down to 0% (isochronous). No hunting, no deadband.",
        mods(egovFlag = true, isochFlag = true, droopMinAdd = -0.025)),
    TechNode("ctrl5", "ctrl", 4, "Automatic synchroniser", 3200.0, listOf("ctrl4"),
        "Matches speed and volts, watches the slip, and closes the breaker at the right instant every time.",
        "One-button synchronising. Cannot close out of phase.",
        mods(autoSyncFlag = true)),
    TechNode("ctrl6", "ctrl", 5, "Digital load-share module", 2900.0, listOf("ctrl5", "ctrl3"),
        "Isochronous load sharing on a two-wire line with the station units. You set a percentage, it holds it.",
        "Set kW as a share of station load and it holds through swings automatically.",
        mods(loadShareFlag = true)),
    TechNode("ctrl7", "ctrl", 6, "Remote start & SCADA link", 5400.0, listOf("ctrl6", "plant3"),
        "The dispatcher can call your plant directly, and you can run it from the house.",
        "Unattended automatic start, sync and load on dispatch. Never miss a call again.",
        mods(autoStartFlag = true, scadaFlag = true)),

    // ------------------------------------------------------------ RECOVERY
    TechNode("recov1", "recov", 0, "Coolant block heater", 310.0, emptyList(),
        "Keeps the jacket at 40 C on shore power so a cold call does not mean a cold start.",
        "Engine starts warm. Far less cold-start wear, much quicker to loading temperature.",
        mods(blockHeaterFlag = true)),
    TechNode("recov2", "recov", 1, "Jacket water heat recovery", 3400.0, listOf("recov1", "cool2"),
        "A plate exchanger ties the jacket circuit into the town district heating loop. They will pay for the heat.",
        "Sells up to 60% of jacket heat, and unloads the radiator while doing it.",
        mods(heatRecoveryFrac = 0.60)),
    TechNode("recov3", "recov", 2, "Exhaust gas economiser", 5200.0, listOf("recov2"),
        "A finned-tube boiler in the stack. Thirty percent of your fuel goes up there; take some of it back.",
        "Sells up to 45% of exhaust heat. Adds a little backpressure.",
        mods(economizerFrac = 0.45, volEffMul = 0.985)),
    TechNode("recov4", "recov", 3, "Day tank & fuel polishing", 2300.0, listOf("recov1"),
        "Centrifuge and a 400 litre heated day tank. Clean warm fuel, and you can take a barge load at once.",
        "+700 L capacity. Fuel filters and injectors last far longer.",
        mods(fuelTankAdd = 700.0, wearMul = 0.92, filterRestrictMul = 0.55)),

    // --------------------------------------------------------------- PLANT
    TechNode("plant1", "plant", 0, "Paralleling switchgear", 6500.0, listOf("ctrl1"),
        "Two breaker cubicles, a synchronising bus and a proper set of instruments. The first thing that makes you a station instead of a machine.",
        "Room for a second generating unit. Unlocks the machinery market.",
        mods(unitSlots = 1, maxUnitKW = 200.0, switchgearFlag = true)),
    TechNode("plant2", "plant", 1, "Powerhouse building", 18000.0, listOf("plant1"),
        "A real building: concrete pads, ventilation, an overhead beam and a floor you can roll an engine across.",
        "Room for four units total. Engines run cleaner and last longer out of the weather.",
        mods(unitSlots = 2, maxUnitKW = 350.0, wearMul = 0.90, powerhouseFlag = true)),
    TechNode("plant3", "plant", 2, "480 V main bus & revenue metering", 12000.0, listOf("plant2"),
        "Your own bus, your own breakers, your own certified revenue meter at the point of delivery.",
        "The co-op stops estimating your output. Higher effective rate, plant-wide alarms.",
        mods(ownBusFlag = true)),
    TechNode("plant4", "plant", 3, "Station step-up transformer bank", 26000.0, listOf("plant3"),
        "Three single-phase 480/7200 V transformers and a pole line to the town feeder. You stop renting the co-op's copper.",
        "Removes the 11% wheeling fee on everything you sell. Forever.",
        mods(stepUpFlag = true)),
    TechNode("plant5", "plant", 4, "Bulk fuel farm", 21000.0, listOf("plant3"),
        "A 40,000 litre tank on a bunded pad with a barge connection and a transfer pump.",
        "+38,000 L storage and barge pricing: fuel costs 22% less.",
        mods(fuelTankAdd = 38000.0, fuelFarmFlag = true)),
    TechNode("plant6", "plant", 5, "Central control room", 15000.0, listOf("plant4"),
        "One board, every unit, an annunciator panel and a chair that does not face a hot engine.",
        "Monitor and command all units from one place. Faster response to everything.",
        mods(controlRoomFlag = true, unitSlots = 1)),
    TechNode("plant7", "plant", 6, "Medium-speed engine hall", 48000.0, listOf("plant6", "plant5"),
        "Deep foundations, a 15 tonne gantry and cooling for machines that come in on their own railcar.",
        "Room for six units, and lifts the per-unit ceiling to 700 kW.",
        mods(unitSlots = 2, maxUnitKW = 700.0, engineHallFlag = true)),
    TechNode("plant8", "plant", 7, "N-1 reserve certification", 22000.0, listOf("plant7"),
        "The co-op's engineer walks the plant and signs off that you can lose your largest unit and still carry the town.",
        "Required for the baseload contract, and the last thing standing between you and the megawatt.",
        mods(n1Flag = true)),
)

// Small helper constructors so the node table above stays readable.
private fun mods(
    volEffMul: Double = 1.0, filterRestrictMul: Double = 1.0, fuelMaxMul: Double = 1.0,
    indEffAdd: Double = 0.0, smokeAFRAdd: Double = 0.0, timingAdvance: Double = 0.0,
    radiatorUAMul: Double = 1.0, waterPumpMul: Double = 1.0, inertiaAdd: Double = 0.0,
    bmepLimitMul: Double = 1.0, wearMul: Double = 1.0, gasketMul: Double = 1.0,
    oilPressMul: Double = 1.0, altKVAMul: Double = 1.0, altEffAdd: Double = 0.0,
    altXsMul: Double = 1.0, fieldTauMul: Double = 1.0, windingCoolK: Double = 0.0,
    motorStartMul: Double = 1.0, fuelTankAdd: Double = 0.0, droopMinAdd: Double = 0.0,
    turbo: Turbo? = null, aftercoolerEff: Double? = null, heatRecoveryFrac: Double? = null,
    economizerFrac: Double? = null, unitSlots: Int = 0, maxUnitKW: Double = 0.0,
    protection: Set<String> = emptySet(),
    oilCoolerFlag: Boolean = false, fanElectricFlag: Boolean = false,
    brushlessFlag: Boolean = false, pmgFlag: Boolean = false, avrFlag: Boolean = false,
    varShareFlag: Boolean = false, egovFlag: Boolean = false, isochFlag: Boolean = false,
    autoSyncFlag: Boolean = false, loadShareFlag: Boolean = false,
    autoStartFlag: Boolean = false, scadaFlag: Boolean = false,
    blockHeaterFlag: Boolean = false, switchgearFlag: Boolean = false,
    powerhouseFlag: Boolean = false, ownBusFlag: Boolean = false, stepUpFlag: Boolean = false,
    fuelFarmFlag: Boolean = false, controlRoomFlag: Boolean = false,
    n1Flag: Boolean = false, engineHallFlag: Boolean = false,
): Mods {
    val f = buildSet {
        if (oilCoolerFlag) add(Flag.OIL_COOLER)
        if (fanElectricFlag) add(Flag.FAN_ELECTRIC)
        if (brushlessFlag) add(Flag.BRUSHLESS)
        if (pmgFlag) add(Flag.PMG)
        if (avrFlag) add(Flag.AVR)
        if (varShareFlag) add(Flag.VAR_SHARE)
        if (egovFlag) add(Flag.EGOV)
        if (isochFlag) add(Flag.ISOCH)
        if (autoSyncFlag) add(Flag.AUTO_SYNC)
        if (loadShareFlag) add(Flag.LOAD_SHARE)
        if (autoStartFlag) add(Flag.AUTO_START)
        if (scadaFlag) add(Flag.SCADA)
        if (blockHeaterFlag) add(Flag.BLOCK_HEATER)
        if (switchgearFlag) add(Flag.SWITCHGEAR)
        if (powerhouseFlag) add(Flag.POWERHOUSE)
        if (ownBusFlag) add(Flag.OWN_BUS)
        if (stepUpFlag) add(Flag.STEP_UP)
        if (fuelFarmFlag) add(Flag.FUEL_FARM)
        if (controlRoomFlag) add(Flag.CONTROL_ROOM)
        if (n1Flag) add(Flag.N1_CERTIFIED)
        if (engineHallFlag) add(Flag.ENGINE_HALL)
    }
    return Mods(
        volEffMul, filterRestrictMul, fuelMaxMul, indEffAdd, smokeAFRAdd, timingAdvance,
        radiatorUAMul, waterPumpMul, inertiaAdd, bmepLimitMul, wearMul, gasketMul,
        oilPressMul, altKVAMul, altEffAdd, altXsMul, fieldTauMul, windingCoolK,
        motorStartMul, fuelTankAdd, droopMinAdd, turbo, aftercoolerEff, heatRecoveryFrac,
        economizerFrac, unitSlots, maxUnitKW, f, protection,
    )
}

val NODE_BY_ID: Map<String, TechNode> = NODES.associateBy { it.id }

fun isUnlockable(node: TechNode, owned: Set<String>) = node.req.all { it in owned }

fun branchNodes(branchId: String) = NODES.filter { it.branch == branchId }.sortedBy { it.tier }
