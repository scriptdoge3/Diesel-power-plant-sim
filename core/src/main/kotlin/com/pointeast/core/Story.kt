package com.pointeast.core

/* ============================================================================
 *  The story.
 *
 *  Delivered in three ways: the opening, which you read once; chapter beats
 *  that fire when milestones land; and Cal Renner, who leaves notes on the
 *  board when something happens worth remarking on.
 * ========================================================================== */

const val PLAYER_HOME = "New Denport"
const val BOSS_NAME = "Cal Renner"
const val COMPANY = "Point East Electrical"

/** The opening, shown once on a new career. One screen per page. */
val PROLOGUE: List<String> = listOf(
    """
    You are an electrical engineer four years out of school in New Denport,
    which is a long way from here and getting further every year.

    Dry Green City is not near anything. That is the first thing anyone tells
    you about it and the only thing they all agree on. Eleven days of hard road
    to the nearest place worth naming, and nothing either side of it but the
    flats.

    Nobody in New Denport is hiring engineers. Dry Green is, because Dry Green
    has a grid it built in a hurry and cannot keep standing up. So you took the
    two-year contract, and for two years you have been up poles and down
    manholes putting other people's work right.
    """.trimIndent(),

    """
    The contract ended this morning. There is no renewal and no return ticket
    worth buying, so you go to the bar nearest the depot, because it is the
    nearest bar to the depot.

    Three tables over, a man in a good coat that has been mended twice is
    talking to two men who are not listening. He is talking about Sector E.
    """.trimIndent(),

    """
    Everybody calls it Point East. It is the newest sector in the city and the
    worst one. The dirt out there is dry and dead, so developers will not
    build; because nobody built, nobody ran decent copper; because the copper
    is thin, the sector browns out three or four times a month. And because it
    browns out, developers will not build.

    His pitch is a generating station at the east end. Not a tie-in, not a
    substation. A station. The eighth in a city that has seven, all of them
    privately owned and none of them interested in Sector E.
    """.trimIndent(),

    """
    The investors finish their drinks and leave without putting a number on the
    table.

    You go over anyway. His name is $BOSS_NAME, and as of eleven days ago he is
    $COMPANY, which currently consists of $BOSS_NAME. He is looking for someone
    who knows what a synchroscope is for.

    He has been in Dry Green nineteen years. You ask him what brought him out
    this far and he says, "Distance," and does not add to it. Some people came
    here because everything else fell over. He came here because it is a long
    way from everything else, which is not the same thing, and you decide not
    to ask twice.

    You have nowhere else to be either. You say yes before you have thought
    about it, which is how most of the good decisions get made.
    """.trimIndent(),

    """
    "They didn't bite," he says. "So it comes out of our pockets. Mine mostly,
    what's in them."

    Prime movers are the problem. Nobody has built a new engine or a new motor
    in a long time, and what exists gets nursed along until it doesn't. Out
    here it is worse: everything arrives on a truck that has been driving for
    a week and a half, and the freight on an engine can cost more than the
    engine.

    You tell him you might know where there is one -- a friend from school has
    a 50 kW diesel sitting on a pallet, heavily used, out of an ice plant.

    He counts out the money for it there on the bar. He'll find the rest: the
    pad, the switchgear, the copper, the permits. You find the iron.
    """.trimIndent(),

    """
    So: one tired six-cylinder, a hand rheostat on the exciter, a flyweight
    governor, and a city grid at 90 Hz that will not care whether you live.

    Synchronise it. Sell what it makes. Buy the next thing.

    A megawatt is one thousand kilowatts. You have fifty.
    """.trimIndent(),
)

/**
 * A beat that fires the first time its milestone is completed. These are the
 * only place the game says anything about itself in a voice.
 */
data class Chapter(val milestoneId: String, val title: String, val body: String)

val CHAPTERS: List<Chapter> = listOf(
    Chapter("first_sync", "On The Bus",
        "The needle crosses twelve, you close the breaker, and the ammeter comes off " +
        "the pin. Fifty kilowatts of pre-collapse iron is now part of the Dry Green " +
        "City grid.\n\n" +
        "$BOSS_NAME watches the meter for a while without saying anything. Then: " +
        "\"That's the whole company, right there. Don't break it.\""),

    Chapter("lights_on", "Three Days",
        "Seventy-two hours and Point East has not been dropped once. That has not " +
        "happened out here since the sector was surveyed.\n\n" +
        "Somebody at the east end noticed. A woman came by the fence today and asked, " +
        "specifically and carefully, whether it was going to keep doing that."),

    Chapter("ground_broken", "Ground Broken",
        "There is a concrete truck on Fourth East. Nobody has poured anything out " +
        "there in two years.\n\n" +
        "$BOSS_NAME has started saying \"when\" instead of \"if\", which from him is " +
        "close to celebration. The load out there is climbing and it is climbing " +
        "because of you, which means every kilowatt of it is yours to sell."),

    Chapter("switchgear", "A Station, Not A Shed",
        "Two breaker cubicles, a synchronising bus, and a set of instruments that " +
        "were made before either of us was born and still read true.\n\n" +
        "You can parallel a second machine now. Finding one is the other problem."),

    Chapter("second_unit", "Two Machines",
        "It came in on a low-loader with a tarp over it and half a ton of desert on " +
        "top of that.\n\n" +
        "Two machines is a different job from one. Now there is load sharing, and " +
        "somebody has to decide which engine takes the swing."),

    Chapter("independence", "Off Their Copper",
        "Three single-phase transformers, a pole line, and your own way onto the " +
        "7200 volt primary. Kessler Light & Power has been taking eleven percent of " +
        "everything you sell for the privilege of carrying it on their wire.\n\n" +
        "Not any more. $BOSS_NAME framed the last invoice."),

    Chapter("sector_built", "Sector E",
        "Point East is not the empty end of the city any more. There are streets out " +
        "there with names on them and a load curve with a shape.\n\n" +
        "The other seven stations have started returning $BOSS_NAME's calls."),

    Chapter("baseload", "Baseload",
        "The grid authority has given you the baseload contract: not peaking, not " +
        "standby -- base. The city expects you to be there at four in the morning " +
        "in February.\n\n" +
        "It is the best money in the business and the least forgiving."),

    Chapter("n1", "N Minus One",
        "The inspector walked the plant for two days and signed the certificate: you " +
        "can lose your largest machine at peak and still carry your load.\n\n" +
        "That sentence is the difference between a generating station and a man with " +
        "some generators."),

    Chapter("megawatt", "The First Megawatt",
        "One thousand kilowatts under one owner. There has never been that much " +
        "plant in one place in Dry Green City, not since before.\n\n" +
        "$BOSS_NAME is out on the pad looking at the engine hall with his hands in " +
        "his pockets. Somewhere under the newest machine in it, on a pad poured " +
        "before there was a building, Set 1 is still turning 1800 rpm."),
)

val CHAPTER_BY_MILESTONE: Map<String, Chapter> = CHAPTERS.associateBy { it.milestoneId }

/**
 * Things Cal says when the plant does something notable. Keyed by an event
 * tag the simulation raises; each fires at most once per career.
 */
val RENNER_NOTES: Map<String, String> = mapOf(
    "first_failure" to
        "\"I'm not going to pretend I know what a main bearing costs. Tell me, and " +
        "then tell me how we stop doing that.\"",
    "first_blackout" to
        "\"The whole east end went dark and my phone did not stop. Whatever it takes.\"",
    "broke" to
        "\"We are out of money. Not low. Out. Sell something or run something.\"",
    "first_turbo" to
        "\"You bolted a boat part to our only engine and it makes half again as much " +
        "power. I have decided not to ask questions.\"",
    "tank_dry" to
        "\"We ran the tank dry with the city on the line. That one is on both of us.\"",
    "first_hire" to
        "\"There is a second machine on the pad. That makes us a company with plant, " +
        "which the bank has opinions about.\"",
    "first_freight" to
        "\"Look at what the haulage cost. Nineteen years out here and it still gets me. " +
        "Nothing is expensive in Dry Green. Everything is just far away.\"",
)
