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

data class MasteryRules(
    val gainOnRight: Int = 10,
    val lossOnWrong: Int = 30,
    /** Furigana is hidden by default from this mastery. */
    val hideAt: Int = 40,
    /** Furigana is unavailable from this mastery. */
    val offAt: Int = 70,
    /** The production (English to Japanese) card unlocks at this mastery. */
    val productionAt: Int = 40,
) {
    init {
        require(gainOnRight >= 0 && lossOnWrong >= 0)
        require(hideAt in 0..MAX && offAt in hideAt..MAX && productionAt in 0..MAX)
    }

    fun after(mastery: Int, correct: Boolean): Int =
        (if (correct) mastery + gainOnRight else mastery - lossOnWrong).coerceIn(0, MAX)

    fun furigana(mastery: Int): FuriganaMode = when {
        mastery >= offAt -> FuriganaMode.OFF
        mastery >= hideAt -> FuriganaMode.HIDDEN
        else -> FuriganaMode.SHOWN
    }

    companion object {
        const val MAX = 100
    }
}
