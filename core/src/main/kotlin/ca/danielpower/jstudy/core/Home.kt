package ca.danielpower.jstudy.core

import java.time.DayOfWeek
import java.time.LocalDate

// ------------------------------------------------------------ what to do next

/** A chapter Home could suggest, with what reading it would bring. */
data class Candidate<out T>(
    val item: T,
    val newWords: Int,
    /** Whether it opens with a grammar topic's page. */
    val teachesGrammar: Boolean,
    /** The page you stopped on last time, or 0. */
    val resumePage: Int = 0,
    /** How many pages the chapter had then, or 0 when unknown. */
    val pages: Int = 0,
)

enum class ReadKind {
    /** Nothing read yet: the first chapter. */
    START,

    /** A chapter you left part way through. */
    CONTINUE,

    /** The next chapter, which starts with its grammar's page. */
    LESSON,

    /** The next chapter. */
    NEXT,

    /** Past today's limit: a chapter with nothing new instead. */
    SIDE,
}

/** What Home suggests for this session. */
sealed interface NextUp<out T> {
    /** Review [due] cards; [orRead] is a chapter to read instead, if there is one. */
    data class Review<T>(val due: Int, val backlog: Boolean, val orRead: Candidate<T>?) : NextUp<T>

    /** Read [chapter]; [orReview] is how many reviews could be done instead. */
    data class Read<T>(val chapter: Candidate<T>, val kind: ReadKind, val orReview: Int) : NextUp<T>

    /** Today's new words are in and nothing is due; [tomorrow] is the chapter waiting. */
    data class Done<T>(val tomorrow: Candidate<T>?) : NextUp<T>

    /** Every chapter has been read and nothing is due. */
    data object AllRead : NextUp<Nothing>
}

object Suggest {
    /**
     * Reviews first once they pile up past the nudge, then the chapter you're
     * on ([current]), unless it would take you past today's new-word limit:
     * then a chapter with nothing new ([side]), then reviews, then nothing.
     * [due] is what a review session would show now; [anyRead] is whether
     * any chapter has been read, for the very first suggestion.
     */
    fun <T> next(pace: Pace, due: Int, current: Candidate<T>?, side: Candidate<T>?, anyRead: Boolean): NextUp<T> {
        val fits = current?.takeIf { !pace.over(it.newWords) }
        if (pace.backlog && due > 0) return NextUp.Review(due, backlog = true, orRead = fits ?: side)
        if (fits != null) return NextUp.Read(fits, kind(fits, anyRead), due)
        if (side != null) return NextUp.Read(side, ReadKind.SIDE, due)
        if (due > 0) return NextUp.Review(due, backlog = false, orRead = null)
        return if (current == null) NextUp.AllRead else NextUp.Done(current)
    }

    private fun kind(c: Candidate<*>, anyRead: Boolean) = when {
        !anyRead && c.resumePage == 0 -> ReadKind.START
        c.resumePage > 0 -> ReadKind.CONTINUE
        c.teachesGrammar -> ReadKind.LESSON
        else -> ReadKind.NEXT
    }
}

// ------------------------------------------------------------ today's plan

enum class StepKind { KANA, READ, REVIEW }

enum class StepState { DONE, NOW, LATER }

/** One step of the day's plan. */
data class PlanStep<out T>(
    val kind: StepKind,
    val state: StepState,
    /** Reading: the chapter to read, and how Home words it; null once it's done. */
    val read: Candidate<T>? = null,
    val readKind: ReadKind? = null,
    /** Kana or cards waiting; once done, the kana practised, chapters finished or answers given today. */
    val count: Int = 0,
    /** Reviews: past the nudge, so they come before reading. */
    val backlog: Boolean = false,
    /** Reading, done: past today's new-word limit with nothing new open, rather than for a chapter read. */
    val full: Boolean = false,
)

/** The quiet second option under the plan. */
sealed interface PlanAlt<out T> {
    /** The day's done, and today's new kana have their second look due. */
    data class KanaCheck(val count: Int) : PlanAlt<Nothing>

    /** Today's chapter is read, and another fits. */
    data class KeepReading<T>(val chapter: Candidate<T>, val kind: ReadKind) : PlanAlt<T>

    /** Every chapter's read: one to read again. */
    data object ReadAgain : PlanAlt<Nothing>
}

/** What's been done today: kana practised, chapters finished, and review answers and how many were right. */
data class DoneToday(val kana: Int = 0, val chapters: Int = 0, val answers: Int = 0, val right: Int = 0) {
    /** Whether today counts towards the streak yet. */
    val any get() = kana > 0 || chapters > 0 || answers > 0
}

data class TodayPlan<out T>(val steps: List<PlanStep<T>>, val alt: PlanAlt<T>?, val soFar: DoneToday) {
    val done get() = steps.all { it.state == StepState.DONE }

    /** The step to do next, until the day's done. */
    val now get() = steps.firstOrNull { it.state == StepState.NOW }

    /** The steps with something to do now: reviews waiting on today's chapter have nothing yet. */
    val left get() = steps.filter { it.state != StepState.DONE && (it.kind != StepKind.REVIEW || it.count > 0) }
}

object Today {
    /**
     * The day as a short plan: kana while you're learning them, one
     * chapter, then the reviews, which by then include the words the
     * chapter started. It keeps [Suggest]'s order: past the nudge, reviews
     * come before reading, and past the new-word limit, reading is a
     * chapter with nothing new, or done if none is open. The first step
     * that isn't done is the one to do; steps done out of order just tick
     * off. Takes [Suggest.next]'s inputs, plus kana practice ([kana], null
     * once every kana is clear), whether the last kana came clear today,
     * and what's been done today ([soFar]).
     */
    fun <T> plan(
        pace: Pace,
        current: Candidate<T>?,
        side: Candidate<T>?,
        anyRead: Boolean,
        kana: KanaCounts?,
        kanaClearToday: Boolean,
        soFar: DoneToday,
    ): TodayPlan<T> {
        val first = Suggest.next(pace, pace.reviewsDue, current, side, anyRead)
        val backlog = first is NextUp.Review && first.backlog
        // What Suggest would have you read if reviews weren't piling up.
        val reading = Suggest.next(pace.copy(reviewNudge = 0), pace.reviewsDue, current, side, anyRead) as? NextUp.Read

        // The late check never reopens kana: once the rest is done, it's the second option.
        val kanaStep: PlanStep<T>? = when {
            kana != null && kana.due - kana.late > 0 -> PlanStep(StepKind.KANA, StepState.LATER, count = kana.due)
            kana != null || kanaClearToday -> PlanStep(StepKind.KANA, StepState.DONE, count = soFar.kana)
            else -> null
        }

        // With every chapter read there's no reading step.
        val readStep: PlanStep<T>? = when {
            current == null && side == null -> null
            reading == null -> PlanStep(StepKind.READ, StepState.DONE, count = soFar.chapters, full = true)
            soFar.chapters > 0 -> PlanStep(StepKind.READ, StepState.DONE, count = soFar.chapters)
            else -> PlanStep(StepKind.READ, StepState.LATER, read = reading.chapter, readKind = reading.kind)
        }

        val due = pace.reviewsDue
        val reviewStep: PlanStep<T> = when {
            due > 0 -> PlanStep(StepKind.REVIEW, StepState.LATER, count = due, backlog = backlog)
            // A chapter still to read brings new words to review, so reviews aren't done before it.
            (readStep?.read?.newWords ?: 0) > 0 -> PlanStep(StepKind.REVIEW, StepState.LATER)
            else -> PlanStep(StepKind.REVIEW, StepState.DONE, count = soFar.answers)
        }

        val ordered = listOfNotNull(kanaStep, if (backlog) reviewStep else readStep, if (backlog) readStep else reviewStep)
        val nowAt = ordered.indexOfFirst { it.state != StepState.DONE }
        val steps = ordered.mapIndexed { i, step -> if (i == nowAt) step.copy(state = StepState.NOW) else step }

        val done = nowAt < 0
        val alt = when {
            done && kana != null && kana.late > 0 -> PlanAlt.KanaCheck(kana.late)
            readStep?.state == StepState.DONE && reading != null -> PlanAlt.KeepReading(reading.chapter, reading.kind)
            readStep == null -> PlanAlt.ReadAgain
            else -> null
        }
        return TodayPlan(steps, alt, soFar)
    }
}

// ------------------------------------------------------------ words over time

/** How many words had been seen (started), known and mastered by the end of [day]. Each count includes the next. */
data class WordDay(val day: Long, val seen: Int, val known: Int, val mastered: Int)

/** A recognition review's mastery change, on the study day it happened. */
data class MasteryChange(val wordId: Long, val day: Long, val before: Int, val after: Int)

object WordHistory {
    /**
     * One [WordDay] for each day from [from] to [to], replaying [changes]
     * (oldest first): answers, reads and marked-known changes. [started] is
     * each started word's first day, null when it has none; [mastery] is each
     * one's mastery now, for words with no changes logged. Words with no first day or
     * review count from the start, since there's no telling when they came.
     * Mastery can drop after a missed review, so known and mastered can too.
     */
    fun days(started: Map<Long, Long?>, mastery: Map<Long, Int>, changes: List<MasteryChange>, from: Long, to: Long): List<WordDay> {
        if (to < from) return emptyList()
        val size = (to - from + 1).toInt()
        val seen = IntArray(size + 1)
        val known = IntArray(size + 1)
        val mastered = IntArray(size + 1)
        // Days before the range land on its first day; the last slot is past the end.
        fun index(day: Long) = when {
            day <= from -> 0
            day > to -> size
            else -> (day - from).toInt()
        }
        val byWord = changes.groupBy { it.wordId }
        for ((word, firstDay) in started) {
            val own = byWord[word].orEmpty()
            val start = firstDay ?: Long.MIN_VALUE
            seen[index(start)]++
            // The stage each day ends on, as (day, mastery) steps.
            var level = own.firstOrNull()?.before ?: mastery[word] ?: 0
            var since = start
            fun close(until: Long) {
                if (until <= since) return
                if (level >= MasteryRules.HIDE_AT) { known[index(since)]++; known[index(until)]-- }
                if (level >= MasteryRules.OFF_AT) { mastered[index(since)]++; mastered[index(until)]-- }
            }
            for (change in own) {
                if (change.day > since) {
                    close(change.day)
                    since = change.day
                }
                level = change.after
            }
            close(Long.MAX_VALUE)
        }
        var s = 0
        var k = 0
        var m = 0
        return List(size) { i ->
            s += seen[i]
            k += known[i]
            m += mastered[i]
            WordDay(from + i, s, k, m)
        }
    }
}

// ------------------------------------------------------------ recent

object Recent {
    /**
     * Home's recent words and grammar, newest day first, at most [shown] in
     * all, and how many words were left out. Every grammar topic stays, since
     * there are few; words fill what's left. On the same day a topic comes
     * before the words, as its page comes before the chapter.
     */
    fun <T> pick(words: List<Pair<T, Long>>, grammar: List<Pair<T, Long>>, shown: Int): Pair<List<T>, Int> {
        val room = (shown - grammar.size).coerceAtLeast(0)
        val kept = words.sortedByDescending { it.second }.take(room)
        val items = (grammar.map { Triple(it.first, it.second, 0) } + kept.map { Triple(it.first, it.second, 1) })
            .sortedWith(compareByDescending<Triple<T, Long, Int>> { it.second }.thenBy { it.third })
            .map { it.first }
        return items to words.size - kept.size
    }

    /**
     * How many of the chips, [widths] wide in order, fit in [lines] rows of
     * [maxWidth] with [gap] between them, leaving room at the end for a "+N"
     * chip [more] wide when some are left out ([more] gets how many).
     */
    fun fitting(widths: List<Float>, maxWidth: Float, gap: Float, lines: Int, more: (Int) -> Float): Int {
        fun fits(chips: List<Float>): Boolean {
            var rows = 1
            var x = 0f
            for (w in chips) {
                if (x > 0f && x + gap + w > maxWidth) {
                    rows++
                    x = w
                } else {
                    x += if (x > 0f) gap + w else w
                }
            }
            return rows <= lines
        }
        if (fits(widths)) return widths.size
        for (shown in widths.size - 1 downTo 0) {
            if (fits(widths.take(shown) + more(widths.size - shown))) return shown
        }
        return 0
    }
}

// ------------------------------------------------------------ streak

object Streak {
    /** Days in a row with something done, ending today, or yesterday while today is still empty. */
    fun current(days: Set<Long>, today: Long): Int {
        var day = if (today in days) today else today - 1
        var count = 0
        while (day in days) {
            count++
            day--
        }
        return count
    }

    /** The Monday starting the week [day] is in. */
    fun weekOf(day: Long): Long {
        val date = LocalDate.ofEpochDay(day)
        return day - (date.dayOfWeek.value - DayOfWeek.MONDAY.value)
    }
}
