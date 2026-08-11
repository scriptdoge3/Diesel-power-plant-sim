package com.portannika.core

/* ============================================================================
 *  PORT ANNIKA MUNICIPAL POWER -- plant, grid and career constants
 * ----------------------------------------------------------------------------
 *  Port Annika is an isolated coastal town. There is no intertie: whatever the
 *  station makes is what the town gets. The system was built around a 90 Hz
 *  standard, inherited from the original cannery's own plant, which means every
 *  generator here turns 1800 rpm on a 6-pole rotor instead of the 1200 rpm a
 *  60 Hz 6-pole set would.
 *
 *      f = rpm * poles / 120   ->   1800 * 6 / 120 = 90 Hz
 *
 *  Generation is at 480 V three-phase inside the plant. The station step-up
 *  bank takes that to the 7200 V three-phase primary that runs out to the town.
 *  Pole-top distribution transformers drop 7200 V to a 360 V winding with a
 *  grounded center tap, giving customers 180-0-180 split phase: 180 V for
 *  lighting and receptacles, 360 V for ranges, pumps and the cannery motors.
 *
 *  You own Unit 8. The career ends when you own the first megawatt.
 * ========================================================================== */

object Nominal {
    const val FREQ = 90.0            // Hz, system standard
    const val POLES = 6
    const val RPM = 1800.0           // = FREQ * 120 / POLES
    const val GEN_VOLTS = 480.0      // plant bus, 3-phase
    const val LINE_VOLTS = 7200.0    // primary distribution, 3-phase
    const val SERVICE_VOLTS = 360.0  // secondary, end to end
    const val SERVICE_HALF = 180.0   // each leg to grounded center tap

    const val FREQ_BAND = 0.5        // +/- Hz before the quality penalty bites
    const val FREQ_TRIP_HI = 94.5
    const val FREQ_TRIP_LO = 85.5
    const val VOLT_BAND_PCT = 5.0    // +/- % on the 480 V bus
    const val VOLT_TRIP_HI_PCT = 12.0
    const val VOLT_TRIP_LO_PCT = 15.0
}

/**
 * Baseline for Unit 8: a Halvorsen-Marsh HM-6.7.6 litre inline six, naturally
 * aspirated, indirect injection, inline jerk pump, flyweight governor with a
 * hand speeder. No regulator on the exciter -- you set the field with a
 * rheostat and watch the voltmeter. It came out of a cannery ice plant and it
 * shows.
 */
object EngineBase {
    const val NAME = "Halvorsen-Marsh HM-6"
    const val CYLINDERS = 6
    const val DISPLACEMENT_L = 7.6
    const val RATED_KW = 50.0        // electrical, continuous
    const val IDLE_RPM = 750.0
    const val MAX_SAFE_RPM = 2050.0
    const val OVERSPEED_TRIP_RPM = 2160.0
    const val CRANK_RPM = 220.0
    const val FIRE_RPM = 130.0       // above this, with fuel and heat, it lights

    const val INERTIA = 3.1          // kg*m^2, crank + flywheel + rotor
    const val MAX_FUEL_PER_STROKE = 6.15e-5   // kg, per cylinder per firing stroke
    const val LHV = 42.7e6           // J/kg, #2 diesel
    const val FUEL_DENSITY = 0.845   // kg/L

    const val IND_EFF_PEAK = 0.435
    const val SMOKE_AFR = 20.5       // below this the burn goes sooty
    const val VOL_EFF = 0.86

    const val FMEP_A = 0.42          // friction MEP, bar: A + B*(rpm/1000)
    const val FMEP_B = 0.16

    const val FAN_KW = 1.9
    const val WATER_PUMP_KW = 0.6
    const val FIELD_KW = 1.1

    // Thermal
    const val COOLANT_MASS_KG = 46.0
    const val COOLANT_CP = 4180.0
    const val OIL_MASS_KG = 19.0
    const val OIL_CP = 2000.0
    const val THERMOSTAT_OPEN_C = 79.0
    const val THERMOSTAT_FULL_C = 92.0
    const val RADIATOR_UA = 800.0   // W/K, full fan, full open
    const val JACKET_FRAC = 0.28     // of fuel energy into the coolant
    const val EXHAUST_FRAC = 0.30    // of fuel energy out the stack
    const val OIL_COUPLING = 450.0   // W/K, oil circuit to coolant circuit
    const val OIL_FRICTION_FRAC = 0.55

    // Limits before things start dying
    const val COOLANT_WARN_C = 96.0
    const val COOLANT_TRIP_C = 105.0
    const val EGT_WARN_C = 590.0
    const val EGT_LIMIT_C = 680.0
    const val OIL_PRESS_MIN_BAR = 0.9
    const val OIL_TEMP_LIMIT_C = 125.0

    const val OIL_PRESS_RATED_BAR = 3.9
    const val OIL_RELIEF_BAR = 4.3
}

object AlternatorBase {
    const val NAME = "Kestrel Electric 62-6"
    const val RATED_KVA = 62.5
    const val RATED_PF = 0.8
    const val XS = 1.85              // synchronous reactance, per-unit
    const val RA = 0.021
    const val EFF_RATED = 0.917
    const val FIELD_TAU_S = 0.55
    const val RESIDUAL_PU = 0.035    // residual magnetism, enough to self-excite
    const val WINDING_TAU_S = 900.0
    const val WINDING_RISE_K = 85.0  // steady rise at rated current
    const val WINDING_LIMIT_C = 155.0 // class F
}

/**
 * A flyweight governor is a proportional device: it can only hold speed by
 * running a droop. Set it too tight and the linkage hunts.
 */
object GovernorBase {
    const val DROOP_MIN = 0.025      // as tight as the mechanical unit goes
    const val DROOP_MAX = 0.08
    const val DROOP_DEFAULT = 0.04
    const val DEADBAND_PU = 0.0022   // mechanical slop
    const val ACTUATOR_TAU_S = 0.22
    const val HUNT_THRESHOLD = 0.031 // below this droop the linkage hunts
    const val HUNT_GAIN = 0.55
    const val SPEEDER_MIN = 0.93     // no-load speed setting, per-unit
    const val SPEEDER_MAX = 1.10
    const val SPEEDER_RATE_PU_PER_S = 0.012
}

/** A co-op unit the player never touches directly. Simplified droop model. */
data class StationUnitSpec(
    val id: String,
    val name: String,
    val make: String,
    val kW: Double,
    val droop: Double,
    val inertiaH: Double,
    val minKW: Double,
    val startSeconds: Double,
    val bsfc: Double,          // kg/kWh
    val mtbfHours: Double,
    val priority: Int,         // lower runs first
)

val STATION_UNITS = listOf(
    StationUnitSpec("u1", "Unit 1", "Fairbanks-Morse 32E", 120.0, 0.045, 2.4, 30.0, 95.0, 0.262, 1400.0, 3),
    StationUnitSpec("u2", "Unit 2", "Caterpillar D343", 180.0, 0.040, 1.7, 45.0, 55.0, 0.244, 2100.0, 2),
    StationUnitSpec("u3", "Unit 3", "Cummins NT-855", 230.0, 0.038, 1.6, 60.0, 48.0, 0.239, 2400.0, 1),
    StationUnitSpec("u4", "Unit 4", "Detroit 6-71", 90.0, 0.050, 1.2, 22.0, 38.0, 0.288, 900.0, 5),
    StationUnitSpec("u5", "Unit 5", "Caterpillar 3406B", 280.0, 0.036, 1.8, 70.0, 60.0, 0.231, 3000.0, 0),
    StationUnitSpec("u6", "Unit 6", "Lister HR6", 30.0, 0.055, 2.9, 6.0, 120.0, 0.305, 1100.0, 7),
    StationUnitSpec("u7", "Unit 7", "John Deere 6068HF", 75.0, 0.042, 1.3, 18.0, 32.0, 0.255, 2600.0, 4),
)

object Town {
    const val NAME = "Port Annika"
    const val POPULATION = 1180

    /** 24-hour shape, multiplier on the day's base load. Index = hour. */
    val DAILY_SHAPE = doubleArrayOf(
        0.60, 0.55, 0.52, 0.51, 0.54, 0.66, 0.84, 0.97,
        1.00, 0.96, 0.93, 0.94, 0.97, 0.95, 0.92, 0.94,
        1.02, 1.15, 1.24, 1.22, 1.12, 0.98, 0.82, 0.69,
    )

    const val BASE_KW = 300.0              // mid-shoulder, mild weather
    const val HEAT_KW_PER_DEG_C = 9.4      // added load per degree C below 15
    const val COOL_KW_PER_DEG_C = 2.1      // above 22, mostly cannery chillers
    const val LOAD_DAMPING_D = 1.6         // % load change per % frequency
    const val CONST_Z_FRAC = 0.42          // rest of the load is constant kW
    const val PF = 0.87

    /** Long-run demand growth once the town believes the lights will stay on. */
    const val GROWTH_PER_YEAR = 0.06
}

object Weather {
    // Coastal subarctic: cold, damp, not extreme. Monthly means, deg C.
    val MEAN_C = doubleArrayOf(-6.0, -5.0, -2.0, 3.0, 8.0, 12.0, 15.0, 14.0, 10.0, 5.0, -1.0, -5.0)
    const val SWING_C = 7.0                // daily peak-to-trough
    const val NOISE_C = 5.5
    const val BARO_KPA = 101.0
}

object Econ {
    const val STARTING_CASH = 4200.0
    const val BASE_RATE_PER_KWH = 0.63     // paid by the co-op at the bus
    const val FUEL_PRICE_PER_L = 0.94
    const val FUEL_TANK_L = 900.0
    const val HEAT_RATE_PER_KWH_TH = 0.071
    const val RELIABILITY_BONUS = 480.0    // monthly, if you answered dispatch
    const val PENALTY_PER_BLACKOUT_KWH = 3.10
    const val PENALTY_REFUSED_DISPATCH = 220.0
    const val FREQ_QUALITY_PENALTY_PER_MIN = 1.8
    const val REP_START = 0.5

    /** Wheeling fee the co-op charges until you build your own step-up bank. */
    const val WHEELING_FRAC = 0.11

    /** Bulk barge discount once you have the fuel farm. */
    const val BARGE_DISCOUNT = 0.22

    fun ratePerKWh(reputation: Double) = BASE_RATE_PER_KWH * (0.82 + 0.36 * reputation)
}

data class ServiceItem(
    val key: String,
    val name: String,
    val cost: Double,
    val hours: Double,
    val intervalH: Double,
)

val CONSUMABLES = listOf(
    ServiceItem("oil", "Oil & filter change", 145.0, 1.2, 250.0),
    ServiceItem("fuelFilter", "Fuel filter elements", 88.0, 0.6, 500.0),
    ServiceItem("airFilter", "Air cleaner element", 62.0, 0.4, 400.0),
    ServiceItem("valveLash", "Valve lash adjustment", 210.0, 3.0, 1000.0),
    ServiceItem("injectors", "Injector service (pop & clean)", 640.0, 6.0, 2000.0),
    ServiceItem("coolant", "Coolant flush & refill", 190.0, 2.0, 4000.0),
    ServiceItem("radiator", "Radiator core cleaning", 130.0, 1.5, 1500.0),
)

data class RepairItem(
    val key: String,
    val name: String,
    val cost: Double,
    val hours: Double,
)

val REPAIRS = listOf(
    RepairItem("bearings", "Main & rod bearing replacement", 2850.0, 22.0),
    RepairItem("rings", "Top end overhaul (head, valves, rings)", 5400.0, 40.0),
    RepairItem("gasket", "Head gasket & head resurface", 1950.0, 16.0),
    RepairItem("turbo", "Turbocharger cartridge", 1480.0, 5.0),
    RepairItem("alternator", "Alternator rewind", 3300.0, 30.0),
    RepairItem("governor", "Governor linkage rebuild", 470.0, 4.0),
    RepairItem("full", "Full in-frame rebuild", 11800.0, 96.0),
)

/** Time compression steps offered in the UI. */
val TIME_SCALES = intArrayOf(1, 2, 5, 15, 60, 300)

/**
 * Above this compression the fast electromechanical states are solved to their
 * equilibrium each step instead of integrated. The slow thermal, wear and
 * economic states keep running normally, so a fast-forwarded week still costs
 * the right fuel and does the right damage.
 */
const val QUASI_STEADY_ABOVE = 20

/** The number that ends the career. */
const val MEGAWATT_KW = 1000.0
