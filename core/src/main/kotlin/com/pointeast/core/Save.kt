package com.pointeast.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/* ============================================================================
 *  Save and restore. A career runs for game-months, so the state that matters
 *  is everything an operator would care about coming back to: the clock, the
 *  money, what is fitted, what is worn out and what is still turning.
 * ========================================================================== */

const val SAVE_VERSION = 1

@Serializable
data class UnitSave(
    val id: String,
    val isFoundingSet: Boolean,
    val spec: GensetSpec,
    val runState: String,
    val rpm: Double,
    val rack: Double,
    val fieldPU: Double,
    val emfPU: Double,
    val boostBar: Double,
    val coolantC: Double,
    val oilC: Double,
    val egtC: Double,
    val windingC: Double,
    val batterySoC: Double,
    val phaseDeg: Double,
    val breakerClosed: Boolean,
    val onBus: Boolean,
    val fuelValveOpen: Boolean,
    val speederPU: Double,
    val droop: Double,
    val fieldRheostat: Double,
    val avrSetpointPU: Double,
    val avrVoltDroop: Double,
    val runHours: Double,
    val lifetimeKWh: Double,
    val fuelUsedL: Double,
    val starts: Int,
    val wear: Wear,
    val service: ServiceHours,
    val airFilterFouling: Double,
    val fuelFilterFouling: Double,
    val radiatorFouling: Double,
    val oilCondition: Double,
    val failureText: String?,
)

@Serializable
data class StationSave(
    val id: String,
    val online: Boolean,
    val starting: Boolean,
    val startTimer: Double,
    val speedSetPU: Double,
    val outputKW: Double,
    val emfPU: Double,
    val runHours: Double,
    val failed: Boolean,
    val failedFor: Double,
)

@Serializable
data class LogSave(val t: Double, val text: String, val level: String)

@Serializable
data class LedgerSave(val t: Double, val text: String, val amount: Double, val category: String)

@Serializable
data class DaySave(
    val day: Int, val revenue: Double, val fuelCost: Double,
    val maintenance: Double, val capital: Double, val kWh: Double, val runHours: Double,
)

@Serializable
data class SaveState(
    val version: Int = SAVE_VERSION,
    val seed: Int,
    val rngState: Int,
    val gameSeconds: Double,
    val timeScale: Int,
    val cash: Double,
    val fuelL: Double,
    val fuelPricePerL: Double,
    val ownedTech: List<String>,
    val selectedUnitId: String,
    val autoPlant: Boolean,
    val gameWon: Boolean,
    val units: List<UnitSave>,
    val station: List<StationSave>,
    val frequencyHz: Double,
    val busVoltPU: Double,
    val unservedKWh: Double,
    val listings: List<MarketListing>,
    val nextRefreshDay: Int,
    val completed: List<String>,
    val orders: List<DispatchOrder>,
    val reputation: Double,
    val peakDeliveredKW: Double,
    val totalDeliveredKWh: Double,
    val ordersAnswered: Int,
    val ordersMissed: Int,
    val log: List<LogSave>,
    val ledger: List<LedgerSave>,
    val days: List<DaySave>,
    val prologueSeen: Boolean = false,
    val seenNotes: List<String> = emptyList(),
    val pointEastConfidence: Double = 0.0,
    val pointEastLitHours: Double = 0.0,
    val pointEastDarkHours: Double = 0.0,
)

private val json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

fun Sim.toSave(seed: Int): SaveState = SaveState(
    seed = seed,
    rngState = rng.snapshot(),
    gameSeconds = gameSeconds,
    timeScale = timeScale,
    cash = cash,
    fuelL = fuelL,
    fuelPricePerL = fuelPricePerL,
    ownedTech = ownedTech.toList(),
    selectedUnitId = selectedUnitId,
    autoPlant = autoPlant,
    gameWon = gameWon,
    units = units.map { u ->
        UnitSave(
            id = u.id, isFoundingSet = u.isFoundingSet, spec = u.spec,
            runState = u.runState.name, rpm = u.rpm, rack = u.rack,
            fieldPU = u.fieldPU, emfPU = u.emfPU, boostBar = u.boostBar,
            coolantC = u.coolantC, oilC = u.oilC, egtC = u.egtC, windingC = u.windingC,
            batterySoC = u.batterySoC, phaseDeg = u.phaseDeg,
            breakerClosed = u.breakerClosed, onBus = u.onBus, fuelValveOpen = u.fuelValveOpen,
            speederPU = u.speederPU, droop = u.droop, fieldRheostat = u.fieldRheostat,
            avrSetpointPU = u.avrSetpointPU, avrVoltDroop = u.avrVoltDroop,
            runHours = u.runHours, lifetimeKWh = u.lifetimeKWh, fuelUsedL = u.fuelUsedL,
            starts = u.starts, wear = u.wear, service = u.service,
            airFilterFouling = u.airFilterFouling, fuelFilterFouling = u.fuelFilterFouling,
            radiatorFouling = u.radiatorFouling, oilCondition = u.oilCondition,
            failureText = u.failureText,
        )
    },
    station = grid.stations.map {
        StationSave(it.spec.id, it.online, it.starting, it.startTimer, it.speedSetPU,
            it.outputKW, it.emfPU, it.runHours, it.failed, it.failedFor)
    },
    frequencyHz = grid.frequencyHz,
    busVoltPU = grid.busVoltPU,
    unservedKWh = grid.unservedKWh,
    listings = market.listings.toList(),
    nextRefreshDay = market.nextRefreshDay,
    completed = campaign.completed.toList(),
    orders = campaign.orders.toList(),
    reputation = campaign.reputation,
    peakDeliveredKW = campaign.peakDeliveredKW,
    totalDeliveredKWh = campaign.totalDeliveredKWh,
    ordersAnswered = campaign.ordersAnswered,
    ordersMissed = campaign.ordersMissed,
    log = log.map { LogSave(it.gameSeconds, it.text, it.level.name) },
    ledger = ledger.map { LedgerSave(it.gameSeconds, it.text, it.amount, it.category) },
    days = days.map { DaySave(it.day, it.revenue, it.fuelCost, it.maintenance, it.capital, it.kWh, it.runHours) },
    prologueSeen = prologueSeen,
    seenNotes = seenNotes.toList(),
    pointEastConfidence = grid.pointEastConfidence,
    pointEastLitHours = grid.pointEastLitHours,
    pointEastDarkHours = grid.pointEastDarkHours,
)

fun SaveState.encode(): String = json.encodeToString(SaveState.serializer(), this)

fun decodeSave(text: String): SaveState? = try {
    val s = json.decodeFromString(SaveState.serializer(), text)
    if (s.version == SAVE_VERSION) s else null
} catch (_: Exception) {
    null
}

/** Rebuild a [Sim] from a save. Returns null if the save cannot be applied. */
fun restoreSim(s: SaveState): Sim? = try {
    val sim = Sim(s.seed)
    sim.applySave(s)
    sim
} catch (_: Exception) {
    null
}
