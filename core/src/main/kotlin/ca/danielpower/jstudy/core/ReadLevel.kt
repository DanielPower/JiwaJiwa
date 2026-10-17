package ca.danielpower.jstudy.core

import kotlin.math.ceil

/**
 * The JLPT level you can already read, chosen at setup or in Settings.
 * Its words, and those of every easier level, start as known instead of
 * new: the level itself with furigana behind a tap, since some of its
 * words won't be known, and the easier ones further out the easier they
 * are. Reviews then check the guesses.
 */
object ReadLevel {
    /** No level: every word starts new. Levels are 5 (N5) to 1 (N1). */
    const val NONE = 0

    /** First reviews from filling in a level are spread so they add about this many a day at most. */
    const val FIRST_REVIEWS_PER_DAY = 100

    /** Mastery for words of the level you read at: known, furigana on tap, back within about two weeks. */
    const val AT_LEVEL = 50

    /**
     * The stability in days a word of [wordLevel] starts at for someone who
     * reads at [readLevel], or null when it isn't filled in: a level of
     * NONE, a word harder than it, or a word not on the JLPT lists.
     */
    fun stabilityDays(readLevel: Int, wordLevel: Int): Int? {
        if (readLevel !in 1..5 || wordLevel !in readLevel..5) return null
        return when (wordLevel - readLevel) {
            0 -> MasteryRules.stabilityDays(AT_LEVEL)
            1 -> MasteryRules.stabilityDays(MasteryRules.OFF_AT)
            2 -> 90
            else -> 365
        }
    }

    /** How many days the first reviews of [total] filled-in words spread over, at least [days]. */
    fun firstDueWithin(days: Int, total: Int): Int = maxOf(days, ceil(total.toDouble() / FIRST_REVIEWS_PER_DAY).toInt())
}
