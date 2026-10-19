package ca.danielpower.jstudy.core

import kotlin.random.Random

data class StudySettings(
    /**
     * New words a day before the app suggests reading something with
     * nothing new, or reviewing. A suggestion, never a lock. Unlocked
     * production cards in reviews fill what's left of it.
     */
    val newPerDay: Int = 20,
    /** Reviews due before the app suggests catching up; 0 for never. */
    val reviewNudge: Int = 50,
    val reviewCap: Int = 200,
    val rolloverHour: Int = 4,
) {
    init {
        require(newPerDay >= 0 && reviewNudge >= 0 && reviewCap >= 0)
        require(rolloverHour in 0..23)
    }
}

/**
 * Today's new words against the daily limit, and the reviews
 * waiting. It only shapes suggestions: nothing is locked by it.
 */
data class Pace(val newToday: Int, val limit: Int, val reviewsDue: Int, val reviewNudge: Int) {
    val atLimit get() = newToday >= limit
    val backlog get() = reviewNudge > 0 && reviewsDue >= reviewNudge
    val nudge get() = atLimit || backlog

    /** Whether learning [adding] more new words now would take you past the limit. */
    fun over(adding: Int) = adding > 0 && newToday + adding > limit
}

/**
 * "Study more" state. Each use pulls every schedule one more day forward
 * ([daysAhead] is never reset) and adds one more day's worth of new cards
 * and review allowance for the rest of today ([extraDaysToday]).
 */
data class StudyAhead(
    val daysAhead: Int = 0,
    /** Study day [extraDaysToday] belongs to. */
    val day: Long = 0,
    val extraDaysToday: Int = 0,
) {
    fun extraOn(today: Long) = if (day == today) extraDaysToday else 0

    fun studyMore(today: Long) = StudyAhead(daysAhead + 1, today, extraOn(today) + 1)
}

/** What today's queue may contain. */
data class DailyLimits(
    /** Review cards are due when due before this instant. */
    val reviewDueBefore: Long,
    val newCards: Int,
    val reviews: Int,
    /**
     * A card answered since this instant (the start of today) isn't pulled
     * ahead by Study more: it's due only if it's due before [endOfToday].
     * Otherwise a card with a short interval would come back as soon as it
     * was answered, all day.
     */
    val answeredSince: Long = 0,
    val endOfToday: Long = reviewDueBefore,
) {
    /** Whether a review card due at [dueAt], last answered at [lastReviewAt], is in today's queue. */
    fun isDue(dueAt: Long, lastReviewAt: Long?): Boolean =
        dueAt < endOfToday || (dueAt < reviewDueBefore && (lastReviewAt == null || lastReviewAt < answeredSince))

    companion object {
        fun of(
            settings: StudySettings,
            ahead: StudyAhead,
            clock: StudyClock,
            now: Long,
            newIntroducedToday: Int,
            reviewsDoneToday: Int,
        ): DailyLimits {
            val today = clock.dayOf(now)
            val days = 1 + ahead.extraOn(today)
            return DailyLimits(
                reviewDueBefore = clock.startOf(today + 1 + ahead.daysAhead),
                newCards = (settings.newPerDay * days - newIntroducedToday).coerceAtLeast(0),
                reviews = (settings.reviewCap * days - reviewsDoneToday).coerceAtLeast(0),
                answeredSince = clock.startOf(today),
                endOfToday = clock.startOf(today + 1),
            )
        }
    }
}

/**
 * The order cards are shown in during one sitting.
 *
 * Learning cards come first once their step is due. Otherwise reviews and
 * new cards alternate, spread evenly. When only learning cards remain, the
 * next one is shown early if it is due within [learnAheadMillis], as Anki does.
 *
 * With [random], reviews and new words come in a shuffled order, so cards
 * aren't always met in the same order or next to the same neighbours.
 */
class StudySession(
    reviews: List<Card>,
    newCards: List<Card>,
    learning: List<Card>,
    private val learnAheadMillis: Long = 20 * 60_000L,
    random: Random? = null,
) {
    private val main = ArrayDeque(
        if (random == null) {
            interleave(reviews, newCards)
        } else {
            interleave(reviews.shuffled(random), newCards.shuffled(random))
        },
    )
    private val learning = learning.sortedBy { it.dueAt }.toMutableList()
    private var lastShownId: Long? = null

    val newRemaining get() = main.count { it.state == CardState.NEW }
    val reviewRemaining get() = main.count { it.state == CardState.REVIEW }
    val learningRemaining get() = learning.size

    /** When the next learning card is due, if any are waiting. */
    val nextLearningDue: Long? get() = learning.firstOrNull()?.dueAt

    fun isEmpty() = main.isEmpty() && learning.isEmpty()

    /** The card to show at [now], or null when nothing is due yet. */
    fun next(now: Long): Card? {
        val dueLearning = learning.firstOrNull { it.dueAt <= now && (it.id != lastShownId || main.isEmpty()) }
        val card = dueLearning
            ?: main.firstOrNull()
            ?: learning.firstOrNull { it.dueAt <= now + learnAheadMillis }
            ?: return null
        lastShownId = card.id
        return card
    }

    /** Record the answered card. Steps that land before [endOfDay] come back this sitting. */
    fun answered(updated: Card, endOfDay: Long) {
        remove(updated.id)
        if (updated.state.inSteps && updated.dueAt < endOfDay) {
            learning.add(updated)
            learning.sortBy { it.dueAt }
        }
    }

    /** Put a card back as it was before an undone answer. */
    fun restore(card: Card) {
        remove(card.id)
        if (card.state.inSteps) {
            learning.add(card)
            learning.sortBy { it.dueAt }
        } else {
            main.addFirst(card)
        }
        lastShownId = null
    }

    private fun remove(id: Long) {
        main.removeAll { it.id == id }
        learning.removeAll { it.id == id }
    }

    companion object {
        /** Spread [b] evenly through [a], keeping each list's order. */
        fun <T> interleave(a: List<T>, b: List<T>): List<T> {
            val out = ArrayList<T>(a.size + b.size)
            var i = 0
            var j = 0
            while (i < a.size || j < b.size) {
                val posA = (i + 1).toDouble() / (a.size + 1)
                val posB = (j + 1).toDouble() / (b.size + 1)
                if (j >= b.size || (i < a.size && posA <= posB)) out.add(a[i++]) else out.add(b[j++])
            }
            return out
        }
    }
}
