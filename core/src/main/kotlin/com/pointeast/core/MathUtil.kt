package com.pointeast.core

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.sign

fun Double.clamp(lo: Double, hi: Double): Double = if (this < lo) lo else if (this > hi) hi else this
fun Int.clamp(lo: Int, hi: Int): Int = if (this < lo) lo else if (this > hi) hi else this

fun lerp(a: Double, b: Double, t: Double) = a + (b - a) * t
fun invLerp(a: Double, b: Double, v: Double) = if (b == a) 0.0 else (v - a) / (b - a)
fun remap(v: Double, a: Double, b: Double, c: Double, d: Double) =
    lerp(c, d, invLerp(a, b, v).clamp(0.0, 1.0))

/** First-order lag: move [cur] toward [target] with time constant [tau] over [dt]. */
fun approach(cur: Double, target: Double, tau: Double, dt: Double): Double {
    if (tau <= 0.0) return target
    val k = 1.0 - exp(-dt / tau)
    return cur + (target - cur) * k
}

/** Rate-limited move, units per second. */
fun slew(cur: Double, target: Double, ratePerSec: Double, dt: Double): Double {
    val max = ratePerSec * dt
    val d = target - cur
    return if (abs(d) <= max) target else cur + sign(d) * max
}

/** Wrap an angle to (-180, 180] degrees. */
fun wrapDeg(d: Double): Double {
    var x = (d + 180.0) % 360.0
    if (x < 0) x += 360.0
    return x - 180.0
}

/** Deterministic 32-bit PRNG (mulberry32) so a save file replays the same weather. */
class Rng(seed: Int) {
    private var a: Int = seed

    fun nextDouble(): Double {
        a += 0x6D2B79F5
        var t = a
        t = (t xor (t ushr 15)) * (t or 1)
        t = t xor (t + (t xor (t ushr 7)) * (t or 61))
        return ((t xor (t ushr 14)).toLong() and 0xFFFFFFFFL).toDouble() / 4294967296.0
    }

    fun range(lo: Double, hi: Double) = lo + (hi - lo) * nextDouble()
    fun rangeInt(lo: Int, hi: Int) = lo + floor(nextDouble() * (hi - lo + 1)).toInt()
    fun chance(p: Double) = nextDouble() < p
    fun <T> pick(list: List<T>): T = list[rangeInt(0, list.size - 1)]

    /** Poisson-ish event test for a rate given in events per hour, over dt seconds. */
    fun eventOccurs(ratePerHour: Double, dtSeconds: Double): Boolean {
        if (ratePerHour <= 0.0) return false
        val p = 1.0 - exp(-ratePerHour * dtSeconds / 3600.0)
        return nextDouble() < p
    }

    fun snapshot(): Int = a
    fun restore(state: Int) { a = state }
}

/** Smooth pseudo-noise in [0,1], used for weather drift. */
fun valueNoise(x: Double, seed: Int = 1337): Double {
    val i = floor(x).toInt()
    val f = x - i
    fun h(n: Int): Double {
        var t = n * 374761393 + seed * 668265263
        t = (t xor (t ushr 13)) * 1274126177
        return ((t xor (t ushr 16)).toLong() and 0xFFFFFFFFL).toDouble() / 4294967296.0
    }
    val u = f * f * (3.0 - 2.0 * f)
    return lerp(h(i), h(i + 1), u)
}

// ------------------------------------------------------------------ calendar

private val MONTHS = arrayOf(
    "Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"
)
private val DAYS_IN_MONTH = intArrayOf(31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)

data class GameDate(
    val year: Int,
    val month: Int,
    val monthName: String,
    val day: Int,
    val hour: Int,
    val minute: Int,
    val dayOfYear: Int,
    val totalDays: Int,
    val hourFrac: Double,
) {
    val clock: String get() = "%02d:%02d".format(hour, minute)
    val dateStr: String get() = "$monthName $day, Yr $year"
}

/** Convert elapsed game-seconds since Jan 1 00:00 into a calendar breakdown. */
fun calendarOf(gameSeconds: Double): GameDate {
    val totalDays = floor(gameSeconds / 86400.0).toInt()
    val secOfDay = gameSeconds - totalDays * 86400.0
    val year = 1 + totalDays / 365
    var doy = totalDays % 365
    var month = 0
    while (doy >= DAYS_IN_MONTH[month]) {
        doy -= DAYS_IN_MONTH[month]
        month++
    }
    val hour = floor(secOfDay / 3600.0).toInt()
    val minute = floor((secOfDay % 3600.0) / 60.0).toInt()
    return GameDate(
        year = year, month = month, monthName = MONTHS[month], day = doy + 1,
        hour = hour, minute = minute, dayOfYear = totalDays % 365,
        totalDays = totalDays, hourFrac = secOfDay / 3600.0,
    )
}
