package com.pointeast.core

/* ============================================================================
 *  The career.
 *
 *  You start as a man with one tired 50 kW set in a shed, selling into someone
 *  else's station. The last milestone is the first megawatt of plant Port
 *  Annika has ever had under one owner -- yours.
 * ========================================================================== */

data class Milestone(
    val id: String,
    val title: String,
    val subtitle: String,
    val reward: Double,
    val repReward: Double,
)

val MILESTONES = listOf(
    Milestone("first_sync", "Shakedown",
        "Synchronise Set 1 to the city bus and deliver 100 kWh.", 250.0, 0.03),
    Milestone("lights_on", "The Lights Stay On",
        "Carry Point East for 72 hours without the sector being dropped.", 600.0, 0.06),
    Milestone("journeyman", "Journeyman",
        "Deliver 10,000 kWh into the Dry Green City grid.", 900.0, 0.05),
    Milestone("uprate_75", "Uprated",
        "Get Set 1 to a continuous rating of 75 kW.", 700.0, 0.04),
    Milestone("ground_broken", "Ground Broken",
        "Give Point East enough reliable power that developers start building: 20% built out.",
        1400.0, 0.07),
    Milestone("switchgear", "A Station, Not a Shed",
        "Install paralleling switchgear.", 0.0, 0.05),
    Milestone("second_unit", "Two Machines",
        "Own and run a second generating set.", 1200.0, 0.06),
    Milestone("uprate_130", "Everything the Block Will Take",
        "Get Set 1 to a continuous rating of 130 kW.", 2000.0, 0.05),
    Milestone("own_bus", "Your Own Bus",
        "Build a 480 V main bus with revenue metering.", 1500.0, 0.06),
    Milestone("independence", "Off Their Copper",
        "Build your own step-up bank and feeder. No more wheeling fee.", 2500.0, 0.08),
    Milestone("sector_built", "Sector E",
        "Build Point East out to 60% of what the sector could be.", 5000.0, 0.10),
    Milestone("half_meg", "Five Hundred",
        "Reach 500 kW of installed capacity.", 4000.0, 0.08),
    Milestone("baseload", "Baseload Contract",
        "Reach 350 kW installed with a reputation of 0.75 to win the city baseload contract.",
        6000.0, 0.10),
    Milestone("fuel_farm", "Bulk Pricing",
        "Build the bulk fuel farm.", 2000.0, 0.04),
    Milestone("n1", "N-1",
        "Get the plant certified to lose its largest machine and still carry its load.",
        5000.0, 0.12),
    Milestone("megawatt", "The First Megawatt",
        "One thousand kilowatts of your own plant, certified, carrying Point East.",
        25000.0, 0.20),
)

val MILESTONE_BY_ID = MILESTONES.associateBy { it.id }

enum class OrderStatus { OFFERED, ACCEPTED, DECLINED, ACTIVE, COMPLETED, FAILED, EXPIRED }

/**
 * A call from the grid dispatcher: be on the bus carrying at least this much
 * between these hours. Answering the phone is most of the reputation game.
 */
@kotlinx.serialization.Serializable
data class DispatchOrder(
    val id: String,
    val issuedAt: Double,
    val startAt: Double,
    val endAt: Double,
    val targetKW: Double,
    val standbyFee: Double,
    val reason: String,
    var status: OrderStatus = OrderStatus.OFFERED,
    var compliedSeconds: Double = 0.0,
    var totalSeconds: Double = 0.0,
) {
    val durationHours: Double get() = (endAt - startAt) / 3600.0
    val complianceFrac: Double get() = if (totalSeconds < 1.0) 0.0 else compliedSeconds / totalSeconds
}

/** Tracks milestones, dispatch orders and reputation. */
class Campaign {
    val completed = mutableSetOf<String>()
    val orders = mutableListOf<DispatchOrder>()
    var reputation = Econ.REP_START
    var baseloadContract = false
    var peakDeliveredKW = 0.0
    var totalDeliveredKWh = 0.0
    var ordersAnswered = 0
    var ordersMissed = 0
    private var orderCounter = 0

    // Diagnostics: where reputation actually goes over a career.
    var nFull = 0; var nPartial = 0; var nFailed = 0; var nExpired = 0; var nDeclined = 0
    var repUp = 0.0; var repDown = 0.0; var repClippedAtZero = 0.0

    /** Apply a reputation change, recording what was lost to the floor. */
    fun adjustRep(delta: Double) {
        val raw = reputation + delta
        if (raw < 0.0) repClippedAtZero += -raw
        if (delta > 0) repUp += delta else repDown += -delta
        reputation = raw.clamp(0.0, 1.0)
    }

    val nextMilestone: Milestone? get() = MILESTONES.firstOrNull { it.id !in completed }

    fun progressFraction(): Double = completed.size.toDouble() / MILESTONES.size

    /** Returns milestones newly completed this tick. */
    fun checkMilestones(
        units: List<Genset>,
        plant: PlantSpec,
        installedKW: Double,
        pointEastConfidence: Double,
        pointEastLitHours: Double,
    ): List<Milestone> {
        val newly = mutableListOf<Milestone>()
        fun award(id: String, condition: Boolean) {
            if (condition && id !in completed && MILESTONE_BY_ID.containsKey(id)) {
                completed += id
                newly += MILESTONE_BY_ID.getValue(id)
            }
        }
        val foundingSet = units.firstOrNull { it.isFoundingSet }

        award("first_sync", totalDeliveredKWh >= 100.0)
        award("lights_on", pointEastLitHours >= 72.0)
        award("journeyman", totalDeliveredKWh >= 10_000.0)
        award("uprate_75", (foundingSet?.spec?.ratedKW ?: 0.0) >= 75.0)
        award("ground_broken", pointEastConfidence >= 0.20)
        award("switchgear", plant.hasSwitchgear)
        award("second_unit", units.size >= 2)
        award("uprate_130", (foundingSet?.spec?.ratedKW ?: 0.0) >= 130.0)
        award("own_bus", plant.hasOwnBus)
        award("independence", plant.hasStepUp)
        award("sector_built", pointEastConfidence >= 0.60)
        award("half_meg", installedKW >= 500.0)
        award("baseload", installedKW >= 350.0 && reputation >= 0.75)
        award("fuel_farm", plant.hasFuelFarm)
        award("n1", plant.n1Certified)
        award("megawatt", installedKW >= MEGAWATT_KW && plant.n1Certified && peakDeliveredKW >= 700.0)

        if ("baseload" in completed) baseloadContract = true
        return newly
    }

    // ------------------------------------------------------------- dispatch

    fun maybeIssueOrder(rng: Rng, gameSeconds: Double, dt: Double, plant: PlantSpec, installedKW: Double) {
        // Roughly one call a day, more if you have proven you answer them.
        val rate = 0.045 * (0.6 + reputation)
        if (!rng.eventOccurs(rate, dt)) return
        if (orders.any { it.status == OrderStatus.OFFERED || it.status == OrderStatus.ACTIVE }) return

        orderCounter++
        val lead = rng.range(3600.0, 21600.0)
        val duration = rng.range(3.0, 11.0) * 3600.0
        val target = (installedKW * rng.range(0.30, 0.70)).coerceAtLeast(12.0)
        val fee = target * duration / 3600.0 * rng.range(0.06, 0.13) + 60.0
        val reason = rng.pick(listOf(
            "Unit 3 is down for a head gasket",
            "Cold snap forecast, we want the reserve",
            "Cannery is running a night shift",
            "Annual inspection on Unit 5",
            "Fuel barge is late, we are spreading the load",
            "Sawmill is running the big saw all evening",
            "Unit 1 is throwing metal in the oil",
        ))
        orders += DispatchOrder(
            id = "ord$orderCounter",
            issuedAt = gameSeconds,
            startAt = gameSeconds + lead,
            endAt = gameSeconds + lead + duration,
            targetKW = target,
            standbyFee = fee,
            reason = reason,
        )
    }

    /**
     * Track compliance on the live order and settle finished ones.
     * Returns a list of (message, cashDelta) results to post to the ledger.
     */
    fun stepOrders(
        gameSeconds: Double,
        dt: Double,
        playerDeliveredKW: Double,
    ): List<Pair<String, Double>> {
        val results = mutableListOf<Pair<String, Double>>()
        for (o in orders) {
            when (o.status) {
                OrderStatus.OFFERED -> {
                    if (gameSeconds > o.startAt) {
                        o.status = OrderStatus.EXPIRED
                        ordersMissed++; nExpired++
                        adjustRep(-0.012)
                        results += "Dispatch order expired without an answer" to 0.0
                    }
                }
                OrderStatus.ACCEPTED -> {
                    if (gameSeconds >= o.startAt) o.status = OrderStatus.ACTIVE
                }
                OrderStatus.ACTIVE -> {
                    o.totalSeconds += dt
                    if (playerDeliveredKW >= o.targetKW * 0.95) o.compliedSeconds += dt
                    if (gameSeconds >= o.endAt) {
                        val c = o.complianceFrac
                        if (c >= 0.85) {
                            o.status = OrderStatus.COMPLETED
                            ordersAnswered++; nFull++
                            adjustRep(0.035)
                            results += "Dispatch order completed (%.0f%%)".format(c * 100) to o.standbyFee
                        } else if (c >= 0.45) {
                            o.status = OrderStatus.COMPLETED
                            ordersAnswered++; nPartial++
                            adjustRep(0.012)
                            results += "Dispatch order partly met (%.0f%%)".format(c * 100) to o.standbyFee * c
                        } else {
                            o.status = OrderStatus.FAILED
                            ordersMissed++; nFailed++
                            adjustRep(-0.045)
                            // Scale the penalty to the size of the job taken on.
                            val penalty = (o.standbyFee * 0.75)
                                .coerceIn(40.0, Econ.PENALTY_REFUSED_DISPATCH * 4)
                            results += "Dispatch order failed (%.0f%%)".format(c * 100) to -penalty
                        }
                    }
                }
                else -> {}
            }
        }
        // Keep the list from growing without bound.
        if (orders.size > 30) {
            val done = orders.filter { it.status != OrderStatus.OFFERED && it.status != OrderStatus.ACTIVE && it.status != OrderStatus.ACCEPTED }
            orders.removeAll(done.take(done.size - 12).toSet())
        }
        return results
    }

    fun accept(id: String) { orders.find { it.id == id }?.let { if (it.status == OrderStatus.OFFERED) it.status = OrderStatus.ACCEPTED } }

    /** Turning work down costs a little; taking it and failing costs far more. */
    fun decline(id: String) {
        orders.find { it.id == id }?.let {
            if (it.status == OrderStatus.OFFERED) {
                it.status = OrderStatus.DECLINED
                nDeclined++; adjustRep(-0.015)
            }
        }
    }

    val activeOrder: DispatchOrder? get() = orders.firstOrNull { it.status == OrderStatus.ACTIVE }
    val offeredOrder: DispatchOrder? get() = orders.firstOrNull { it.status == OrderStatus.OFFERED }
}
