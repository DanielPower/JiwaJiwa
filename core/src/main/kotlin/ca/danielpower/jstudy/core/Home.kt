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

// ------------------------------------------------------------ words over time

/** How many words had been seen (started), known and mastered by the end of [day]. Each count includes the next. */
data class WordDay(val day: Long, val seen: Int, val known: Int, val mastered: Int)

/** A recognition review's mastery change, on the study day it happened. */
data class MasteryChange(val wordId: Long, val day: Long, val before: Int, val after: Int)

object WordHistory {
    /**
     * One [WordDay] for each day from [from] to [to], replaying [changes]
     * (oldest first). [started] is each started word's first day, null when
     * it has none (a word marked known or imported); [mastery] is each one's
     * mastery now, for words with no reviews. Words with no first day or
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
