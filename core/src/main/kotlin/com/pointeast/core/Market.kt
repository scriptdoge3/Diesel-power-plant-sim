package com.pointeast.core

import kotlin.math.roundToInt

/* ============================================================================
 *  The machinery market.
 *
 *  Once you have paralleling switchgear you can own more than one machine.
 *
 *  Dry Green is eleven days of hard road from anywhere worth naming, which
 *  shapes this whole screen. Nothing new ever gets built and nothing new ever
 *  arrives, so everything here is second-hand:
 *  cannery sets, ex-military standby plant, a tug's auxiliary engine, whatever
 *  came off the last barge. Stock rotates when the barge calls.
 * ========================================================================== */

@kotlinx.serialization.Serializable
data class MarketListing(
    val id: String,
    val name: String,
    val make: String,
    val kW: Double,
    val displacementL: Double,
    val cylinders: Int,
    val turbocharged: Boolean,
    val aftercooled: Boolean,
    val bsfc: Double,
    val condition: Double,     // 0..1, 1 = as new
    val hoursOnClock: Double,
    val price: Double,
    val freight: Double,
    val blurb: String,
) {
    val totalCost: Double get() = price + freight
    val conditionText: String get() = when {
        condition > 0.85 -> "Excellent"
        condition > 0.68 -> "Good"
        condition > 0.50 -> "Serviceable"
        condition > 0.32 -> "Rough"
        else -> "Project"
    }

    fun toSpec(unitName: String): GensetSpec =
        marketSpec(unitName, make, kW, displacementL, cylinders, turbocharged, aftercooled, bsfc, condition)
}

private data class Chassis(
    val make: String,
    val kW: Double,
    val displacementL: Double,
    val cylinders: Int,
    val turbo: Boolean,
    val aftercooled: Boolean,
    val bsfc: Double,
    val blurb: String,
)

private val CHASSIS = listOf(
    Chassis("Lister-Petter HR4", 26.0, 3.8, 4, false, false, 0.302,
        "Air-cooled, indestructible, and it will still be running when you are not."),
    Chassis("Deutz F6L912", 42.0, 5.7, 6, false, false, 0.291,
        "Air-cooled Deutz out of a gravel crusher. Loud enough to hear across the inlet."),
    Chassis("Perkins 6.354", 55.0, 5.8, 6, false, false, 0.283,
        "Ex-fishing boat auxiliary. Salt in every crevice but the bottom end is tight."),
    Chassis("John Deere 6068TF", 88.0, 6.8, 6, true, false, 0.256,
        "Ex-irrigation set from the valley. Low hours, clean, boring in the best way."),
    Chassis("Detroit 6-71N", 110.0, 7.0, 6, false, false, 0.297,
        "Two-stroke, blower-scavenged, drinks fuel and announces itself. Parts everywhere."),
    Chassis("Caterpillar 3306B", 145.0, 10.5, 6, true, false, 0.246,
        "The workhorse. If you only buy one second machine, buy this one."),
    Chassis("Cummins NTA-855", 210.0, 14.0, 6, true, true, 0.234,
        "Big-cam Cummins off a decommissioned pipeline compressor station."),
    Chassis("Caterpillar 3412", 340.0, 27.0, 12, true, true, 0.228,
        "V12. Needs a real foundation and a real crane to put it on one."),
    Chassis("Wartsila Vasa 4R22", 520.0, 41.0, 4, true, true, 0.203,
        "Medium-speed, heavy-fuel capable, came out of a coastal freighter. A serious machine."),
    Chassis("Mirrlees Blackstone ESL8", 680.0, 68.0, 8, true, true, 0.199,
        "Eight cylinders of British marine iron. It arrives on its own railcar."),
)

private val PROVENANCE = listOf(
    "Cannery standby set, ran one week a year",
    "Ex-military mobile plant, canvas-wrapped since the base closed",
    "Off a tug that lost its argument with a reef",
    "Pulled from a mine site that ran out of ore",
    "Hospital standby, replaced by a newer set",
    "Sat on a pallet in Seward for six years",
    "Sawmill prime power until they went on the city grid",
    "Barge auxiliary, well maintained by an owner who cared",
)

class Market(private val rng: Rng) {
    var listings = mutableListOf<MarketListing>()
    var nextRefreshDay = 0
    private var counter = 0

    fun refresh(currentDay: Int, plant: PlantSpec, reputation: Double) {
        listings.clear()
        val count = rng.rangeInt(3, 5)
        val affordableCeiling = plant.maxUnitKW
        val pool = CHASSIS.filter { it.kW <= affordableCeiling * 1.35 }
            .ifEmpty { CHASSIS.take(3) }

        repeat(count) {
            val c = rng.pick(pool)
            val condition = rng.range(0.28, 0.94)
            val hours = lerp(48000.0, 900.0, condition) * rng.range(0.7, 1.3)
            // Newer, cleaner, bigger machines cost more per kW.
            val perKW = lerp(120.0, 430.0, condition) * (if (c.turbo) 1.12 else 1.0) *
                (if (c.aftercooled) 1.08 else 1.0)
            val price = (c.kW * perKW * rng.range(0.88, 1.14)).roundToInt().toDouble()
            // Eleven days of road. The freight on an engine can run to a
            // third of what the engine costs, and there is no way around it.
            val freight = (900.0 + c.kW * 26.0 * rng.range(0.8, 1.25)).roundToInt().toDouble()
            counter++
            listings += MarketListing(
                id = "lst$currentDay-$counter",
                name = c.make,
                make = c.make,
                kW = c.kW,
                displacementL = c.displacementL,
                cylinders = c.cylinders,
                turbocharged = c.turbo,
                aftercooled = c.aftercooled,
                bsfc = c.bsfc * lerp(1.14, 0.99, condition),
                condition = condition,
                hoursOnClock = hours,
                price = price * (1.0 - 0.06 * reputation),
                freight = freight,
                blurb = c.blurb + " " + rng.pick(PROVENANCE) + ".",
            )
        }
        nextRefreshDay = currentDay + rng.rangeInt(9, 16)
    }

    fun remove(id: String) { listings.removeAll { it.id == id } }
}
