package ca.danielpower.jstudy.core

/** How furigana appears on the front of a card. The answer side always shows it. */
enum class FuriganaMode {
    /** Shown, and can be hidden with a tap. */
    SHOWN,

    /** Hidden, and can be shown with a tap. */
    HIDDEN,

    /** Not shown and not available. */
    OFF,
}

/**
 * Mastery is a 0-100 view of a word's FSRS stability: about how many days
 * you can go before your chance of recalling it drops to 90%. It isn't
 * scored separately, so a miss on a word you've known for months costs less
 * than a miss on a new one, just as it does in the scheduler.
 */
object MasteryRules {
    const val MAX = 100

    /** Furigana is hidden by default from this mastery; marking a word "Known" sets it. */
    const val HIDE_AT = 40

    /** Furigana is unavailable from this mastery; marking a word "Mastered" sets it. */
    const val OFF_AT = 70

    /** The production (English to Japanese) card unlocks at this mastery. */
    const val PRODUCTION_AT = 40

    fun furigana(mastery: Int): FuriganaMode = when {
        mastery >= OFF_AT -> FuriganaMode.OFF
        mastery >= HIDE_AT -> FuriganaMode.HIDDEN
        else -> FuriganaMode.SHOWN
    }

    /** Mastery against stability in days: a week is 40, a month 70, three months 100. */
    private val POINTS = listOf(0 to 1.0, 40 to 7.0, 70 to 30.0, 100 to 90.0)

    /** Mastery for a card with this FSRS [stability] in days; 0 before its first review. */
    fun fromStability(stability: Double?): Int {
        if (stability == null || stability <= POINTS.first().second) return 0
        if (stability >= POINTS.last().second) return MAX
        val (lo, hi) = POINTS.zipWithNext().first { (_, b) -> stability <= b.second }
        val t = (stability - lo.second) / (hi.second - lo.second)
        return kotlin.math.round(lo.first + t * (hi.first - lo.first)).toInt()
    }

    /** Stability in whole days that gives about this [mastery]; the inverse of [fromStability]. */
    fun stabilityDays(mastery: Int): Int {
        val m = mastery.coerceIn(0, MAX)
        val (lo, hi) = POINTS.zipWithNext().first { (_, b) -> m <= b.first }
        val t = (m - lo.first).toDouble() / (hi.first - lo.first)
        return kotlin.math.round(lo.second + t * (hi.second - lo.second)).toInt().coerceAtLeast(1)
    }
}
