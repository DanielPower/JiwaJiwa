package ca.danielpower.jstudy.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TodayTest {
    private fun pace(newToday: Int = 0, limit: Int = 20, reviewsDue: Int = 0, nudge: Int = 50) =
        Pace(newToday, limit, reviewsDue, nudge)

    private val ch1 = Candidate("ch1", newWords = 3, teachesGrammar = true)
    private val ch2 = Candidate("ch2", newWords = 3, teachesGrammar = false)
    private val side = Candidate("side", newWords = 0, teachesGrammar = false)

    /** Five new kana a day while learning them; [check] more to check, [late] of them today's second look. */
    private fun kana(new: Int = 5, check: Int = 0, late: Int = 0) =
        KanaCounts(new = new, check = check, clear = 0, total = 226, late = late)

    private fun plan(
        pace: Pace = pace(),
        current: Candidate<String>? = ch2,
        side: Candidate<String>? = null,
        anyRead: Boolean = true,
        kana: KanaCounts? = null,
        kanaClearToday: Boolean = false,
        soFar: DoneToday = DoneToday(),
    ) = Today.plan(pace, current, side, anyRead, kana, kanaClearToday, soFar)

    private fun TodayPlan<String>.shape() = steps.map { it.kind to it.state }

    @Test
    fun `a kana learner's day is kana, then a chapter, then reviews`() {
        val first = plan(current = ch1, anyRead = false, kana = kana())
        assertEquals(
            listOf(StepKind.KANA to StepState.NOW, StepKind.READ to StepState.LATER, StepKind.REVIEW to StepState.LATER),
            first.shape(),
        )
        assertEquals(ch1, first.steps[1].read)
        assertEquals(ReadKind.START, first.steps[1].readKind)
        assertEquals(5, first.now?.count)

        // Kana done: on to the chapter.
        val kanaDone = plan(current = ch1, anyRead = false, kana = kana(new = 0), soFar = DoneToday(kana = 5))
        assertEquals(StepKind.READ, kanaDone.now?.kind)
        assertEquals(5, kanaDone.steps[0].count)

        // The chapter read: its new words are due now.
        val read = plan(pace(newToday = 3, reviewsDue = 3), current = ch2, kana = kana(new = 0), soFar = DoneToday(kana = 5, chapters = 1))
        assertEquals(
            listOf(StepKind.KANA to StepState.DONE, StepKind.READ to StepState.DONE, StepKind.REVIEW to StepState.NOW),
            read.shape(),
        )
        assertEquals(3, read.now?.count)
    }

    @Test
    fun `past the nudge, reviews come before the chapter`() {
        val p = plan(pace(reviewsDue = 64), current = ch2, kana = kana())
        assertEquals(listOf(StepKind.KANA, StepKind.REVIEW, StepKind.READ), p.steps.map { it.kind })
        assertTrue(p.steps[1].backlog)
        assertEquals(64, p.steps[1].count)
        assertEquals(ch2, p.steps[2].read)

        val kanaDone = plan(pace(reviewsDue = 64), current = ch2, kana = kana(new = 0), soFar = DoneToday(kana = 5))
        assertEquals(StepKind.REVIEW, kanaDone.now?.kind)
    }

    @Test
    fun `the late kana check doesn't reopen a finished day`() {
        val p = plan(current = ch2, kana = kana(new = 0, check = 5, late = 5), soFar = DoneToday(kana = 5, chapters = 1, answers = 6))
        assertTrue(p.done)
        assertEquals(PlanAlt.KanaCheck(5), p.alt)
        assertTrue(p.left.isEmpty())
    }

    @Test
    fun `kana still to check from earlier days keep the kana step open`() {
        val p = plan(current = ch2, kana = kana(new = 0, check = 8, late = 5), soFar = DoneToday(kana = 5, chapters = 1))
        assertEquals(StepKind.KANA to StepState.NOW, p.shape().first())
    }

    @Test
    fun `past the limit with nothing open, reading counts as done`() {
        val p = plan(pace(newToday = 21, reviewsDue = 6), current = ch2, soFar = DoneToday(chapters = 4))
        val read = p.steps.single { it.kind == StepKind.READ }
        assertEquals(StepState.DONE, read.state)
        assertTrue(read.full)
        assertEquals(StepKind.REVIEW, p.now?.kind)
        // Nothing else fits, so there's no keeping on.
        assertNull(p.alt)
    }

    @Test
    fun `past the limit, a chapter with nothing new is today's reading`() {
        val p = plan(pace(newToday = 21), current = ch2, side = side)
        assertEquals(side, p.now?.read)
        assertEquals(ReadKind.SIDE, p.now?.readKind)
        // It brings no new words, so there's nothing to review after it.
        assertEquals(StepKind.REVIEW to StepState.DONE, p.shape().last())
    }

    @Test
    fun `a day with nothing due and a chapter read is done`() {
        val p = plan(pace(newToday = 3), current = ch2, soFar = DoneToday(chapters = 1, answers = 6, right = 5))
        assertTrue(p.done)
        assertNull(p.now)
        assertEquals(6, p.steps.last().count)
        // Another chapter fits, as the other option.
        assertEquals(PlanAlt.KeepReading(ch2, ReadKind.NEXT), p.alt)
    }

    @Test
    fun `doing steps out of order just ticks them off`() {
        // Reviews answered first, from the Review tab: the chapter is still to read.
        val p = plan(current = ch2, soFar = DoneToday(answers = 4))
        assertEquals(listOf(StepKind.READ to StepState.NOW, StepKind.REVIEW to StepState.LATER), p.shape())

        // A chapter read before the kana: kana are still the step to do.
        val q = plan(current = ch2, kana = kana(), soFar = DoneToday(chapters = 1))
        assertEquals(
            listOf(StepKind.KANA to StepState.NOW, StepKind.READ to StepState.DONE, StepKind.REVIEW to StepState.DONE),
            q.shape(),
        )
    }

    @Test
    fun `reviews aren't done while a chapter with new words is still to read`() {
        val p = plan(current = ch1, anyRead = false)
        assertEquals(StepKind.REVIEW to StepState.LATER, p.shape().last())
        assertEquals(0, p.steps.last().count)
        // Nothing waits on the chapter yet, so it's the only step left.
        assertEquals(listOf(StepKind.READ), p.left.map { it.kind })
    }

    @Test
    fun `a chapter left part way is continued`() {
        val half = ch2.copy(resumePage = 2, pages = 5)
        val p = plan(current = half)
        assertEquals(ReadKind.CONTINUE, p.now?.readKind)
        assertEquals(half, p.now?.read)
    }

    @Test
    fun `with every chapter read, there's no reading step and one can be read again`() {
        val p = plan(pace(reviewsDue = 4), current = null)
        assertEquals(listOf(StepKind.REVIEW to StepState.NOW), p.shape())
        assertEquals(PlanAlt.ReadAgain, p.alt)
    }

    @Test
    fun `kana stay on the plan the day the last one comes clear`() {
        val p = plan(current = ch2, kana = null, kanaClearToday = true, soFar = DoneToday(kana = 21))
        assertEquals(StepKind.KANA to StepState.DONE, p.shape().first())
        assertEquals(21, p.steps.first().count)
        assertEquals(listOf(StepKind.READ, StepKind.REVIEW), plan(current = ch2).steps.map { it.kind })
    }

    @Test
    fun `today counts once anything's been done`() {
        assertFalse(DoneToday().any)
        assertTrue(DoneToday(kana = 3).any)
        assertTrue(DoneToday(chapters = 1).any)
        assertTrue(DoneToday(answers = 1).any)
    }
}
