package ca.danielpower.jstudy.core

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class HomeTest {
    private fun pace(newToday: Int = 0, limit: Int = 20, reviewsDue: Int = 0, nudge: Int = 50) =
        Pace(newToday, limit, reviewsDue, nudge)

    private val ch5 = Candidate("ch5", newWords = 4, teachesGrammar = false)
    private val side = Candidate("side", newWords = 0, teachesGrammar = false)

    // ------------------------------------------------------------ suggestion

    @Test
    fun `the next chapter comes first, with due reviews as the other option`() {
        assertEquals(NextUp.Read(ch5, ReadKind.NEXT, 14), Suggest.next(pace(reviewsDue = 14), 14, ch5, side, anyRead = true))
    }

    @Test
    fun `reviews come first once they reach the nudge, with reading as the other option`() {
        assertEquals(NextUp.Review(64, backlog = true, orRead = ch5), Suggest.next(pace(reviewsDue = 64), 64, ch5, side, anyRead = true))
    }

    @Test
    fun `a nudge of 0 never puts reviews first`() {
        assertEquals(NextUp.Read(ch5, ReadKind.NEXT, 300), Suggest.next(pace(reviewsDue = 300, nudge = 0), 300, ch5, null, anyRead = true))
    }

    @Test
    fun `past today's limit, a chapter with nothing new comes first`() {
        assertEquals(NextUp.Read(side, ReadKind.SIDE, 14), Suggest.next(pace(newToday = 18), 14, ch5, side, anyRead = true))
    }

    @Test
    fun `past the limit with nothing new open, reviews are the only suggestion`() {
        assertEquals(NextUp.Review(14, backlog = false, orRead = null), Suggest.next(pace(newToday = 20), 14, ch5, null, anyRead = true))
    }

    @Test
    fun `past the limit with nothing open or due, it's done for today`() {
        assertEquals(NextUp.Done(ch5), Suggest.next(pace(newToday = 20), 0, ch5, null, anyRead = true))
    }

    @Test
    fun `with every chapter read and nothing due, everything is read`() {
        assertEquals(NextUp.AllRead, Suggest.next(pace(), 0, null, null, anyRead = true))
    }

    @Test
    fun `a chapter with nothing new fits even past the limit`() {
        val quiet = Candidate("ch6", newWords = 0, teachesGrammar = false)
        assertEquals(NextUp.Read(quiet, ReadKind.NEXT, 0), Suggest.next(pace(newToday = 25), 0, quiet, side, anyRead = true))
    }

    @Test
    fun `the first chapter starts, a half-read one continues, and grammar comes first`() {
        assertEquals(ReadKind.START, (Suggest.next(pace(), 0, ch5, null, anyRead = false) as NextUp.Read).kind)
        val half = ch5.copy(resumePage = 3)
        assertEquals(ReadKind.CONTINUE, (Suggest.next(pace(), 0, half, null, anyRead = false) as NextUp.Read).kind)
        val lesson = ch5.copy(teachesGrammar = true)
        assertEquals(ReadKind.LESSON, (Suggest.next(pace(), 0, lesson, null, anyRead = true) as NextUp.Read).kind)
    }

    // ------------------------------------------------------------ word history

    @Test
    fun `words count as seen from their first day and known or mastered from the review that got them there`() {
        val started = mapOf(1L to 10L, 2L to 11L)
        val changes = listOf(
            MasteryChange(1, 12, 0, 45),
            MasteryChange(1, 14, 45, 72),
            MasteryChange(2, 13, 0, 20),
        )
        val days = WordHistory.days(started, emptyMap(), changes, from = 10, to = 15)
        assertEquals(listOf(1, 2, 2, 2, 2, 2), days.map { it.seen })
        assertEquals(listOf(0, 0, 1, 1, 1, 1), days.map { it.known })
        assertEquals(listOf(0, 0, 0, 0, 1, 1), days.map { it.mastered })
    }

    @Test
    fun `a missed review takes a word back out of known`() {
        val changes = listOf(MasteryChange(1, 11, 0, 50), MasteryChange(1, 13, 50, 30))
        val days = WordHistory.days(mapOf(1L to 10L), emptyMap(), changes, from = 10, to = 14)
        assertEquals(listOf(0, 1, 1, 0, 0), days.map { it.known })
    }

    @Test
    fun `words with no first day count from the start at their mastery, until a review changes it`() {
        val started = mapOf(1L to null, 2L to null)
        val mastery = mapOf(1L to 75, 2L to 50)
        val changes = listOf(MasteryChange(2, 12, 50, 72))
        val days = WordHistory.days(started, mastery, changes, from = 10, to = 13)
        assertEquals(listOf(2, 2, 2, 2), days.map { it.seen })
        assertEquals(listOf(2, 2, 2, 2), days.map { it.known })
        assertEquals(listOf(1, 1, 2, 2), days.map { it.mastered })
    }

    @Test
    fun `words started before the range count on its first day`() {
        val days = WordHistory.days(mapOf(1L to 2L), emptyMap(), listOf(MasteryChange(1, 5, 0, 40)), from = 10, to = 11)
        assertEquals(listOf(WordDay(10, 1, 1, 0), WordDay(11, 1, 1, 0)), days)
    }

    // ------------------------------------------------------------ streak

    @Test
    fun `a streak runs to today, or to yesterday while today is empty`() {
        assertEquals(3, Streak.current(setOf(8L, 9L, 10L), today = 10))
        assertEquals(3, Streak.current(setOf(7L, 8L, 9L), today = 10))
        assertEquals(0, Streak.current(setOf(7L, 8L), today = 10))
        assertEquals(1, Streak.current(setOf(8L, 10L), today = 10))
    }

    @Test
    fun `weeks start on Monday`() {
        val saturday = LocalDate.parse("2026-10-17").toEpochDay()
        assertEquals(LocalDate.parse("2026-10-12").toEpochDay(), Streak.weekOf(saturday))
        assertEquals(LocalDate.parse("2026-10-12").toEpochDay(), Streak.weekOf(LocalDate.parse("2026-10-12").toEpochDay()))
    }
}
