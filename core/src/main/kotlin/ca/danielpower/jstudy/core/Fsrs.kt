package ca.danielpower.jstudy.core

import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.round
import kotlin.random.Random

/**
 * FSRS-6, ported from the reference implementation py-fsrs 6.3.2
 * (https://github.com/open-spaced-repetition/py-fsrs, MIT licence).
 *
 * The one deliberate difference: elapsed days are passed in by the caller
 * instead of being derived from timestamps, so the app can count them in
 * study days (with a rollover hour) the way Anki does. Fuzz picks a uniform
 * whole number of days in the same range py-fsrs uses.
 */
enum class Rating(val value: Int) { AGAIN(1), HARD(2), GOOD(3), EASY(4) }

/** FSRS memory states. New cards are the app's concern; FSRS starts at [LEARNING]. */
enum class FsrsState(val value: Int) { LEARNING(1), REVIEW(2), RELEARNING(3) }

data class FsrsMemory(
    val state: FsrsState,
    /** Index into the learning or relearning steps; null in [FsrsState.REVIEW]. */
    val step: Int?,
    /** Null until the first review. */
    val stability: Double?,
    val difficulty: Double?,
) {
    companion object {
        val NEW = FsrsMemory(FsrsState.LEARNING, 0, null, null)
    }
}

/** When the card is next due, relative to the review. */
sealed interface FsrsInterval {
    data class Minutes(val minutes: Double) : FsrsInterval
    data class Days(val days: Int) : FsrsInterval
}

data class FsrsResult(val memory: FsrsMemory, val interval: FsrsInterval)

class Fsrs(
    val parameters: List<Double> = DEFAULT_PARAMETERS,
    val desiredRetention: Double = 0.9,
    val learningStepsMinutes: List<Double> = listOf(1.0, 10.0),
    val relearningStepsMinutes: List<Double> = listOf(10.0),
    val maximumInterval: Int = 36500,
) {
    init {
        require(parameters.size == 21) { "FSRS-6 needs 21 parameters, got ${parameters.size}" }
        require(desiredRetention > 0.0 && desiredRetention < 1.0)
    }

    private val w = parameters
    private val decay = -w[20]
    private val factor = 0.9.pow(1.0 / decay) - 1.0

    /** Probability of recall after [elapsedDays] for a memory with [stability]. */
    fun retrievability(stability: Double, elapsedDays: Int): Double =
        (1.0 + factor * max(0, elapsedDays) / stability).pow(decay)

    /**
     * Review a card. [elapsedDays] is the number of whole days since the last
     * review, or null for the first review. [fuzz] spreads review intervals
     * of 3 days or more; pass null for deterministic results.
     */
    fun review(memory: FsrsMemory, rating: Rating, elapsedDays: Int?, fuzz: Random? = null): FsrsResult {
        val result = when (memory.state) {
            FsrsState.LEARNING -> stepped(memory, rating, elapsedDays, learningStepsMinutes)
            FsrsState.RELEARNING -> stepped(memory, rating, elapsedDays, relearningStepsMinutes)
            FsrsState.REVIEW -> review(memory, rating, elapsedDays)
        }
        val interval = result.interval
        return if (fuzz != null && result.memory.state == FsrsState.REVIEW && interval is FsrsInterval.Days) {
            result.copy(interval = FsrsInterval.Days(fuzzed(interval.days, fuzz)))
        } else {
            result
        }
    }

    private fun updated(memory: FsrsMemory, rating: Rating, elapsedDays: Int?): Pair<Double, Double> {
        val s = memory.stability
        val d = memory.difficulty
        if (s == null || d == null) {
            return initialStability(rating) to initialDifficulty(rating, clamp = true)
        }
        return if (elapsedDays != null && elapsedDays < 1) {
            shortTermStability(s, rating) to nextDifficulty(d, rating)
        } else {
            nextStability(d, s, retrievability(s, elapsedDays ?: 0), rating) to nextDifficulty(d, rating)
        }
    }

    private fun stepped(memory: FsrsMemory, rating: Rating, elapsedDays: Int?, steps: List<Double>): FsrsResult {
        val (s, d) = updated(memory, rating, elapsedDays)
        val step = memory.step ?: 0
        fun graduate() = FsrsResult(FsrsMemory(FsrsState.REVIEW, null, s, d), FsrsInterval.Days(nextInterval(s)))
        fun stay(newStep: Int, minutes: Double) =
            FsrsResult(FsrsMemory(memory.state, newStep, s, d), FsrsInterval.Minutes(minutes))

        if (steps.isEmpty() || (step >= steps.size && rating != Rating.AGAIN)) return graduate()
        return when (rating) {
            Rating.AGAIN -> stay(0, steps[0])
            Rating.HARD -> when {
                step == 0 && steps.size == 1 -> stay(step, steps[0] * 1.5)
                step == 0 -> stay(step, (steps[0] + steps[1]) / 2.0)
                else -> stay(step, steps[step])
            }
            Rating.GOOD -> if (step + 1 == steps.size) graduate() else stay(step + 1, steps[step + 1])
            Rating.EASY -> graduate()
        }
    }

    private fun review(memory: FsrsMemory, rating: Rating, elapsedDays: Int?): FsrsResult {
        val s0 = requireNotNull(memory.stability)
        val d0 = requireNotNull(memory.difficulty)
        val s = if (elapsedDays != null && elapsedDays < 1) {
            shortTermStability(s0, rating)
        } else {
            nextStability(d0, s0, retrievability(s0, elapsedDays ?: 0), rating)
        }
        val d = nextDifficulty(d0, rating)
        if (rating == Rating.AGAIN && relearningStepsMinutes.isNotEmpty()) {
            return FsrsResult(
                FsrsMemory(FsrsState.RELEARNING, 0, s, d),
                FsrsInterval.Minutes(relearningStepsMinutes[0]),
            )
        }
        return FsrsResult(FsrsMemory(FsrsState.REVIEW, null, s, d), FsrsInterval.Days(nextInterval(s)))
    }

    private fun clampStability(s: Double) = max(s, STABILITY_MIN)

    private fun clampDifficulty(d: Double) = d.coerceIn(1.0, 10.0)

    private fun initialStability(rating: Rating) = clampStability(w[rating.value - 1])

    private fun initialDifficulty(rating: Rating, clamp: Boolean): Double {
        val d = w[4] - exp(w[5] * (rating.value - 1)) + 1.0
        return if (clamp) clampDifficulty(d) else d
    }

    fun nextInterval(stability: Double): Int {
        val raw = (stability / factor) * (desiredRetention.pow(1.0 / decay) - 1.0)
        // Python's round() rounds halves to even; kotlin.math.round does the same.
        return round(raw).toInt().coerceAtLeast(1).coerceAtMost(maximumInterval)
    }

    private fun shortTermStability(s: Double, rating: Rating): Double {
        var increase = exp(w[17] * (rating.value - 3 + w[18])) * s.pow(-w[19])
        if (rating != Rating.AGAIN) increase = max(increase, 1.0)
        return clampStability(s * increase)
    }

    private fun nextDifficulty(d: Double, rating: Rating): Double {
        val delta = -(w[6] * (rating.value - 3))
        val damped = d + (10.0 - d) * delta / 9.0
        val reverted = w[7] * initialDifficulty(Rating.EASY, clamp = false) + (1 - w[7]) * damped
        return clampDifficulty(reverted)
    }

    private fun nextStability(d: Double, s: Double, r: Double, rating: Rating): Double {
        val next = if (rating == Rating.AGAIN) {
            val longTerm = w[11] * d.pow(-w[12]) * ((s + 1).pow(w[13]) - 1) * exp((1 - r) * w[14])
            val shortTerm = s / exp(w[17] * w[18])
            min(longTerm, shortTerm)
        } else {
            val hardPenalty = if (rating == Rating.HARD) w[15] else 1.0
            val easyBonus = if (rating == Rating.EASY) w[16] else 1.0
            s * (1 + exp(w[8]) * (11 - d) * s.pow(-w[9]) * (exp((1 - r) * w[10]) - 1) * hardPenalty * easyBonus)
        }
        return clampStability(next)
    }

    /** Inclusive range of days a fuzzed interval may land on. */
    fun fuzzRange(days: Int): IntRange {
        if (days < 2.5) return days..days
        var delta = 1.0
        for ((start, end, f) in FUZZ_RANGES) {
            delta += f * max(min(days.toDouble(), end) - start, 0.0)
        }
        var lo = max(2, round(days - delta).toInt())
        val hi = min(round(days + delta).toInt(), maximumInterval)
        lo = min(lo, hi)
        return lo..hi
    }

    private fun fuzzed(days: Int, random: Random): Int {
        val range = fuzzRange(days)
        if (range.first == range.last) return days
        return range.first + floor(random.nextDouble() * (range.last - range.first + 1)).toInt()
    }

    companion object {
        const val STABILITY_MIN = 0.001

        val DEFAULT_PARAMETERS = listOf(
            0.212, 1.2931, 2.3065, 8.2956, 6.4133, 0.8334, 3.0194, 0.001, 1.8722, 0.1666, 0.796,
            1.4835, 0.0614, 0.2629, 1.6483, 0.6014, 1.8729, 0.5425, 0.0912, 0.0658, 0.1542,
        )

        private val FUZZ_RANGES = listOf(
            Triple(2.5, 7.0, 0.15),
            Triple(7.0, 20.0, 0.1),
            Triple(20.0, Double.POSITIVE_INFINITY, 0.05),
        )
    }
}
