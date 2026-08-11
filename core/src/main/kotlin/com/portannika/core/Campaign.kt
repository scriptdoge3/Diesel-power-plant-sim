package com.portannika.core

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
        "Synchronise Unit 8 to the station bus and deliver 100 kWh.", 250.0, 0.03),
    Milestone("journeyman", "Journeyman",
        "Deliver 10,000 kWh to the town.", 900.0, 0.05),
    Milestone("uprate_75", "Uprated",
        "Get Unit 8 to a continuous rating of 75 kW.", 700.0, 0.04),
    Milestone("switchgear", "A Station, Not a Shed",
        "Install paralleling switchgear.", 0.0, 0.05),
    Milestone("second_unit", "Two Machines",
        "Own and run a second generating unit.", 1200.0, 0.06),
    Milestone("uprate_130", "Everything the Block Will Take",
        "Get Unit 8 to a continuous rating of 130 kW.", 2000.0, 0.05),
    Milestone("own_bus", "Your Own Bus",
        "Build a 480 V main bus with revenue metering.", 1500.0, 0.06),
    Milestone("independence", "Off Their Copper",
        "Build your own step-up bank and feeder. No more wheeling fee.", 2500.0, 0.08),
    Milestone("half_meg", "Five Hundred",
        "Reach 500 kW of installed capacity.", 4000.0, 0.08),
    Milestone("baseload", "Baseload Contract",
        "Reach 350 kW installed with a reputation of 0.75 to win the co-op's baseload contract.", 6000.0, 0.10),
    Milestone("fuel_farm", "Barge Pricing",
        "Build the bulk fuel farm.", 2000.0, 0.04),
    Milestone("n1", "N-1",
        "Get the plant certified to lose its largest unit and still carry the town.", 5000.0, 0.12),
    Milestone("megawatt", "The First Megawatt",
        "One thousand kilowatts of your own plant, certified, carrying Port Annika.", 25000.0, 0.20),
)

val MILESTONE_BY_ID = MILESTONES.associateBy { it.id }

enum class OrderStatus { OFFERED, ACCEPTED, DECLINED, ACTIVE, COMPLETED, FAILED, EXPIRED }

/**
 * A call from the co-op dispatcher: be on the bus carrying at least this much
 * between these hours. Answering the phone is most of the reputation game.
 */
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

    val nextMilestone: Milestone? get() = MILESTONES.firstOrNull { it.id !in completed }

    fun progressFraction(): Double = completed.size.toDouble() / MILESTONES.size

    /** Returns milestones newly completed this tick. */
    fun checkMilestones(
        units: List<Genset>,
        plant: PlantSpec,
        installedKW: Double,
    ): List<Milestone> {
        val newly = mutableListOf<Milestone>()
        fun award(id: String, condition: Boolean) {
            if (condition && id !in completed && MILESTONE_BY_ID.containsKey(id)) {
                completed += id
                newly += MILESTONE_BY_ID.getValue(id)
            }
        }
        val unit8 = units.firstOrNull { it.isUnit8 }

        award("first_sync", totalDeliveredKWh >= 100.0)
        award("journeyman", totalDeliveredKWh >= 10_000.0)
        award("uprate_75", (unit8?.spec?.ratedKW ?: 0.0) >= 75.0)
        award("switchgear", plant.hasSwitchgear)
        award("second_unit", units.size >= 2)
        award("uprate_130", (unit8?.spec?.ratedKW ?: 0.0) >= 130.0)
        award("own_bus", plant.hasOwnBus)
        award("independence", plant.hasStepUp)
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
        val lead = rng.range(1800.0, 10800.0)
        val duration = rng.range(3.0, 11.0) * 3600.0
        val target = (installedKW * rng.range(0.35, 0.85)).coerceAtLeast(15.0)
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
                        ordersMissed++
                        reputation = (reputation - 0.02).clamp(0.0, 1.0)
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
                        if (c >= 0.90) {
                            o.status = OrderStatus.COMPLETED
                            ordersAnswered++
                            reputation = (reputation + 0.035).clamp(0.0, 1.0)
                            results += "Dispatch order completed (%.0f%%)".format(c * 100) to o.standbyFee
                        } else if (c >= 0.55) {
                            o.status = OrderStatus.COMPLETED
                            ordersAnswered++
                            reputation = (reputation + 0.005).clamp(0.0, 1.0)
                            results += "Dispatch order partly met (%.0f%%)".format(c * 100) to o.standbyFee * c
                        } else {
                            o.status = OrderStatus.FAILED
                            ordersMissed++
                            reputation = (reputation - 0.06).clamp(0.0, 1.0)
                            results += "Dispatch order failed (%.0f%%)".format(c * 100) to -Econ.PENALTY_REFUSED_DISPATCH
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

    fun decline(id: String) {
        orders.find { it.id == id }?.let {
            if (it.status == OrderStatus.OFFERED) {
                it.status = OrderStatus.DECLINED
                reputation = (reputation - 0.015).clamp(0.0, 1.0)
            }
        }
    }

    val activeOrder: DispatchOrder? get() = orders.firstOrNull { it.status == OrderStatus.ACTIVE }
    val offeredOrder: DispatchOrder? get() = orders.firstOrNull { it.status == OrderStatus.OFFERED }
}
