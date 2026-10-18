package ca.danielpower.jstudy.core

import kotlin.random.Random

enum class Direction(val value: Int) {
    /** Japanese on the front, meaning on the back. */
    RECOGNITION(0),

    /** Meaning on the front, Japanese on the back. */
    PRODUCTION(1),
    ;

    companion object {
        fun of(value: Int) = entries.first { it.value == value }
    }
}

enum class CardState(val value: Int) {
    NEW(0),
    LEARNING(1),
    REVIEW(2),
    RELEARNING(3),
    ;

    val inSteps get() = this == LEARNING || this == RELEARNING

    companion object {
        fun of(value: Int) = entries.first { it.value == value }
    }
}

/**
 * How a word was started. Only [STORY] words count against the daily limit,
 * and imported or level-filled words stay out of Home's Recent row.
 * Stored in the app's database: don't renumber.
 */
enum class StartSource(val value: Int) {
    /** Introduced by finishing a story. */
    STORY(0),

    /** Marked known by hand. */
    KNOWN(1),

    /** Imported from Renshuu. */
    IMPORT(2),

    /** Filled in from the level you can already read. */
    LEVEL(3),
    ;

    /** Shown among the week's new words on Home. */
    val recent get() = this == STORY || this == KNOWN

    companion object {
        fun of(value: Int) = entries.first { it.value == value }
    }
}

data class Card(
    val id: Long,
    val wordId: Long,
    val direction: Direction,
    val state: CardState = CardState.NEW,
    val step: Int? = null,
    val stability: Double? = null,
    val difficulty: Double? = null,
    /** When the card is next due. For review cards this is the start of a study day. */
    val dueAt: Long = 0,
    val lastReviewAt: Long? = null,
    val reps: Int = 0,
    val lapses: Int = 0,
    /** Study day the word was started, whichever way ([startSource]). */
    val introducedDay: Long? = null,
    val startSource: StartSource = StartSource.STORY,
)

data class WordProgress(
    val wordId: Long,
    /**
     * Derived from the recognition card's stability by [MasteryRules.fromStability]
     * and kept here so lists can sort and filter by it.
     */
    val mastery: Int = 0,
    val productionUnlocked: Boolean = false,
)

data class ReviewLog(
    val cardId: Long,
    val reviewedAt: Long,
    val day: Long,
    val correct: Boolean,
    val stateBefore: CardState,
    val stateAfter: CardState,
    /** Whole study days since the previous review; null for a new card. */
    val elapsedDays: Int?,
    val stabilityAfter: Double,
    val difficultyAfter: Double,
    val dueAt: Long,
    val masteryBefore: Int,
    val masteryAfter: Int,
    /** A due word read in a story, not an answer: it moves mastery, but stays out of review stats and the review cap. */
    val read: Boolean = false,
)

data class Answer(
    val card: Card,
    val progress: WordProgress,
    /** True when this answer unlocked the word's production card. */
    val unlockedProduction: Boolean,
    val log: ReviewLog,
)

/** Applies one Wrong or Right answer: FSRS scheduling plus mastery. */
class Reviewer(
    private val fsrs: Fsrs,
    private val clock: StudyClock,
) {
    fun answer(card: Card, progress: WordProgress, correct: Boolean, now: Long, fuzz: Random? = null): Answer =
        grade(card, progress, if (correct) Rating.GOOD else Rating.AGAIN, now, fuzz)

    /**
     * A due review word read in a story without looking it up. Context helps
     * in a story, so it's worth [READ_SHARE] of a Right answer's stability
     * gain, and leaves difficulty alone.
     */
    fun read(card: Card, progress: WordProgress, now: Long, fuzz: Random? = null): Answer {
        require(card.state == CardState.REVIEW)
        return grade(card, progress, Rating.GOOD, now, fuzz, read = true)
    }

    private fun grade(card: Card, progress: WordProgress, rating: Rating, now: Long, fuzz: Random?, read: Boolean = false): Answer {
        require(card.wordId == progress.wordId)
        val correct = rating != Rating.AGAIN
        val today = clock.dayOf(now)
        val memory = if (card.state == CardState.NEW) {
            FsrsMemory.NEW
        } else {
            FsrsMemory(
                state = when (card.state) {
                    CardState.REVIEW -> FsrsState.REVIEW
                    CardState.RELEARNING -> FsrsState.RELEARNING
                    else -> FsrsState.LEARNING
                },
                step = card.step,
                stability = card.stability,
                difficulty = card.difficulty,
            )
        }
        val elapsed = card.lastReviewAt?.let { (today - clock.dayOf(it)).toInt().coerceAtLeast(0) }
        val result = if (read) fsrs.read(memory, elapsed, READ_SHARE, fuzz) else fsrs.review(memory, rating, elapsed, fuzz)

        val dueAt = when (val interval = result.interval) {
            is FsrsInterval.Minutes -> now + (interval.minutes * 60_000).toLong()
            is FsrsInterval.Days -> clock.startOf(today + interval.days)
        }
        val state = when (result.memory.state) {
            FsrsState.LEARNING -> CardState.LEARNING
            FsrsState.REVIEW -> CardState.REVIEW
            FsrsState.RELEARNING -> CardState.RELEARNING
        }

        // Mastery follows the card you see the word itself on. Production
        // reviews schedule their own card but leave it alone.
        val newMastery = if (card.direction == Direction.PRODUCTION) {
            progress.mastery
        } else {
            MasteryRules.fromStability(result.memory.stability)
        }
        val unlock = card.direction == Direction.RECOGNITION &&
            !progress.productionUnlocked &&
            newMastery >= MasteryRules.PRODUCTION_AT

        val updated = card.copy(
            state = state,
            step = result.memory.step,
            stability = result.memory.stability,
            difficulty = result.memory.difficulty,
            dueAt = dueAt,
            lastReviewAt = now,
            reps = card.reps + 1,
            lapses = card.lapses + if (card.state == CardState.REVIEW && !correct) 1 else 0,
            // A word marked known starts as a review, and never counts as a new word,
            // even once a lapse has put it back through relearning.
            introducedDay = card.introducedDay ?: today.takeIf { card.state == CardState.NEW || card.state == CardState.LEARNING },
        )
        return Answer(
            card = updated,
            progress = progress.copy(
                mastery = newMastery,
                productionUnlocked = progress.productionUnlocked || unlock,
            ),
            unlockedProduction = unlock,
            log = ReviewLog(
                cardId = card.id,
                reviewedAt = now,
                day = today,
                correct = correct,
                stateBefore = card.state,
                stateAfter = state,
                elapsedDays = elapsed,
                stabilityAfter = result.memory.stability!!,
                difficultyAfter = result.memory.difficulty!!,
                dueAt = dueAt,
                masteryBefore = progress.mastery,
                masteryAfter = newMastery,
                read = read,
            ),
        )
    }

    companion object {
        /** Share of a Right answer's stability gain that reading a due word gives. */
        const val READ_SHARE = 0.15
    }

    /**
     * First sight of a new word you don't know yet. Nothing is graded: the
     * card enters its first learning step, and its first FSRS review is the
     * next time you see it.
     */
    fun introduce(card: Card, now: Long): Card {
        require(card.state == CardState.NEW)
        val firstStep = fsrs.learningStepsMinutes.firstOrNull() ?: 0.0
        return card.copy(
            state = CardState.LEARNING,
            step = 0,
            stability = null,
            difficulty = null,
            dueAt = now + (firstStep * 60_000).toLong(),
            introducedDay = clock.dayOf(now),
        )
    }

    /**
     * Mark a word you already know. The card skips learning and gets a
     * review schedule to match the chosen mastery, so it still comes back
     * for occasional checks. The first review lands on a random day within
     * that interval, so marking many words at once doesn't bunch them up.
     *
     * A word that wasn't started yet, or was started today, counts as started
     * today from [source], so it's in the words graph from today but never
     * against the daily limit. One started on an earlier day keeps that day.
     */
    fun markKnown(card: Card, progress: WordProgress, mastery: Int, now: Long, random: Random, source: StartSource = StartSource.KNOWN): Known {
        val newMastery = mastery.coerceIn(0, MasteryRules.MAX)
        val days = MasteryRules.stabilityDays(newMastery)
        return known(card, progress, newMastery, days, days, now, random, source)
    }

    /**
     * Mark a word known with a stability of [days], as the reading level
     * does. The first review lands within [firstDueWithin] days, which can
     * be longer than [days] to spread out a large batch.
     */
    fun markKnownFor(card: Card, progress: WordProgress, days: Int, firstDueWithin: Int, now: Long, random: Random): Known =
        known(card, progress, MasteryRules.fromStability(days.toDouble()), days, maxOf(days, firstDueWithin), now, random, StartSource.LEVEL)

    private fun known(card: Card, progress: WordProgress, newMastery: Int, days: Int, firstDueWithin: Int, now: Long, random: Random, source: StartSource): Known {
        require(card.wordId == progress.wordId)
        val today = clock.dayOf(now)
        val startsToday = card.state == CardState.NEW || card.introducedDay == today
        val firstDue = 1 + random.nextInt(firstDueWithin)
        val unlock = card.direction == Direction.RECOGNITION &&
            !progress.productionUnlocked &&
            newMastery >= MasteryRules.PRODUCTION_AT
        return Known(
            card = card.copy(
                state = CardState.REVIEW,
                step = null,
                // At 90% target retention the FSRS interval equals the stability.
                stability = days.toDouble(),
                difficulty = fsrs.startingDifficulty(Rating.GOOD),
                dueAt = clock.startOf(today + firstDue),
                lastReviewAt = now,
                introducedDay = if (startsToday) today else card.introducedDay,
                startSource = if (startsToday) source else card.startSource,
            ),
            progress = progress.copy(mastery = newMastery, productionUnlocked = progress.productionUnlocked || unlock),
            unlockedProduction = unlock,
        )
    }
}

data class Known(
    val card: Card,
    val progress: WordProgress,
    val unlockedProduction: Boolean,
)
