package ca.danielpower.jstudy.core

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SessionTest {
    private val zone = ZoneId.of("America/St_Johns")
    private val clock = StudyClock(zone, rolloverHour = 4)

    private fun at(text: String) = LocalDateTime.parse(text).atZone(zone).toInstant().toEpochMilli()

    private fun newCard(id: Long) = Card(id = id, wordId = id, direction = Direction.RECOGNITION)

    private fun review(id: Long) = Card(
        id = id, wordId = id, direction = Direction.RECOGNITION, state = CardState.REVIEW,
        stability = 5.0, difficulty = 5.0, lastReviewAt = at("2026-10-05T09:00"),
    )

    // ------------------------------------------------------------ clock

    @Test
    fun `every study day starts and ends where dayOf says, through daylight saving changes`() {
        // St. John's springs forward on 2026-03-08 and falls back on 2026-11-01, both at 2am.
        val first = LocalDate.parse("2026-01-01").toEpochDay()
        val last = LocalDate.parse("2026-12-31").toEpochDay()
        for (hour in 0..23) {
            val c = StudyClock(zone, rolloverHour = hour)
            for (day in first..last) {
                val start = c.startOf(day)
                assertEquals(day, c.dayOf(start), "rollover $hour, day ${LocalDate.ofEpochDay(day)}")
                assertEquals(day - 1, c.dayOf(start - 1), "rollover $hour, just before ${LocalDate.ofEpochDay(day)}")
            }
        }
    }

    @Test
    fun `the day the clocks fall back is 25 hours long`() {
        val day = LocalDate.parse("2026-11-01").toEpochDay()
        assertEquals(25 * 3_600_000L, clock.startOf(day) - clock.startOf(day - 1))
        assertEquals(24 * 3_600_000L, clock.startOf(day + 1) - clock.startOf(day))
    }

    @Test
    fun `midnight rollover follows the calendar`() {
        val midnight = StudyClock(zone, rolloverHour = 0)
        assertEquals(LocalDate.parse("2026-10-10").toEpochDay(), midnight.dayOf(at("2026-10-10T00:00")))
        assertEquals(LocalDate.parse("2026-10-09").toEpochDay(), midnight.dayOf(at("2026-10-09T23:59")))
    }

    @Test
    fun `settings reject values the app can't use`() {
        assertFailsWith<IllegalArgumentException> { StudyClock(zone, rolloverHour = 24) }
        assertFailsWith<IllegalArgumentException> { StudySettings(newPerDay = -1) }
        assertFailsWith<IllegalArgumentException> { StudySettings(newKanaPerDay = -1) }
        assertFailsWith<IllegalArgumentException> { StudySettings(reviewCap = -1) }
        assertFailsWith<IllegalArgumentException> { StudySettings(rolloverHour = -1) }
        StudySettings(newPerDay = 0, newKanaPerDay = 0, reviewCap = 0, rolloverHour = 0)
    }

    // ------------------------------------------------------------ daily limits

    @Test
    fun `limits never go negative`() {
        val now = at("2026-10-10T20:00")
        val limits = DailyLimits.of(StudySettings(), StudyAhead(), clock, now, newIntroducedToday = 25, reviewsDoneToday = 500, newKanaIntroducedToday = 12)
        assertEquals(0, limits.newCards)
        assertEquals(0, limits.reviews)
        assertEquals(0, limits.newKana)
    }

    @Test
    fun `kana have their own daily allowance and study more adds to it`() {
        val now = at("2026-10-10T20:00")
        val today = clock.dayOf(now)
        val settings = StudySettings(newPerDay = 5, newKanaPerDay = 8)
        val normal = DailyLimits.of(settings, StudyAhead(), clock, now, 0, 0, newKanaIntroducedToday = 3)
        assertEquals(5, normal.newCards)
        assertEquals(5, normal.newKana)
        val more = DailyLimits.of(settings, StudyAhead().studyMore(today), clock, now, 0, 0, newKanaIntroducedToday = 3)
        assertEquals(10, more.newCards)
        assertEquals(13, more.newKana)
    }

    @Test
    fun `study more stacks within a day and the shift carries over`() {
        val today = clock.dayOf(at("2026-10-10T20:00"))
        val twice = StudyAhead().studyMore(today).studyMore(today)
        assertEquals(2, twice.daysAhead)
        assertEquals(2, twice.extraOn(today))
        assertEquals(0, twice.extraOn(today + 1))

        val tomorrow = twice.studyMore(today + 1)
        assertEquals(3, tomorrow.daysAhead)
        assertEquals(1, tomorrow.extraOn(today + 1), "yesterday's extra days don't carry over")
    }

    // ------------------------------------------------------------ session

    @Test
    fun `interleave handles empty lists`() {
        assertEquals(emptyList(), StudySession.interleave(emptyList<Int>(), emptyList()))
        assertEquals(listOf(1, 2), StudySession.interleave(listOf(1, 2), emptyList()))
        // A tie goes to the first list, so one review lands in the middle of three new cards.
        assertEquals(listOf("n1", "r1", "n2", "n3"), StudySession.interleave(listOf("r1"), listOf("n1", "n2", "n3")))
    }

    private fun order(session: StudySession): List<Long> = buildList {
        while (true) {
            val card = session.next(at("2026-10-10T20:00")) ?: break
            add(card.id)
            session.answered(card.copy(state = CardState.REVIEW), 0)
        }
    }

    @Test
    fun `a shuffled sitting mixes up reviews and new words but keeps new kana in order`() {
        val kana = (101L..104L).map { Card(id = it, wordId = -it, direction = Direction.KANA) }
        val reviews = (1L..20L).map(::review)
        val words = (21L..30L).map(::newCard)
        val orders = (1..5).map { seed ->
            order(StudySession(reviews, kana + words, emptyList(), random = kotlin.random.Random(seed)))
        }
        for (o in orders) {
            assertEquals((reviews + kana + words).map { it.id }.toSet(), o.toSet())
            assertEquals(kana.map { it.id }, o.filter { it > 100 })
        }
        assertTrue(orders.any { o -> o.filter { it <= 20 } != reviews.map { it.id } })
        assertTrue(orders.any { o -> o.filter { it in 21..30 } != words.map { it.id } })
        assertTrue(orders.distinct().size > 1)
    }

    @Test
    fun `counts what's left in the sitting`() {
        val now = at("2026-10-10T20:00")
        val learning = Card(id = 9, wordId = 9, direction = Direction.RECOGNITION, state = CardState.LEARNING, step = 0, dueAt = now + 5 * 60_000)
        val session = StudySession(listOf(review(1), review(2)), listOf(newCard(3)), listOf(learning))
        assertEquals(2, session.reviewRemaining)
        assertEquals(1, session.newRemaining)
        assertEquals(1, session.learningRemaining)
        assertEquals(learning.dueAt, session.nextLearningDue)
    }

    @Test
    fun `waits when the only learning card is due later than the learn-ahead window`() {
        val now = at("2026-10-10T20:00")
        val later = Card(id = 9, wordId = 9, direction = Direction.RECOGNITION, state = CardState.LEARNING, step = 0, dueAt = now + 30 * 60_000)
        val session = StudySession(emptyList(), emptyList(), listOf(later))
        assertNull(session.next(now))
        assertEquals(later.dueAt, session.nextLearningDue)
        assertEquals(9, session.next(now + 11 * 60_000)!!.id)
    }

    @Test
    fun `a learning card isn't shown twice in a row while other cards wait`() {
        val now = at("2026-10-10T20:00")
        val endOfDay = clock.startOf(clock.dayOf(now) + 1)
        val step = Card(id = 9, wordId = 9, direction = Direction.RECOGNITION, state = CardState.LEARNING, step = 0, dueAt = now - 1)
        val session = StudySession(emptyList(), listOf(newCard(1)), listOf(step))

        assertEquals(9, session.next(now)!!.id)
        session.answered(step.copy(dueAt = now - 1), endOfDay)
        assertEquals(1, session.next(now)!!.id)
        session.answered(newCard(1).copy(state = CardState.REVIEW, dueAt = endOfDay), endOfDay)
        // Nothing else is left, so the same card may come back.
        assertEquals(9, session.next(now)!!.id)
    }

    @Test
    fun `steps that land after the end of the day leave the sitting`() {
        val now = at("2026-10-11T03:50")
        val endOfDay = clock.startOf(clock.dayOf(now) + 1)
        val card = newCard(1)
        val session = StudySession(emptyList(), listOf(card), emptyList())
        session.next(now)
        session.answered(card.copy(state = CardState.LEARNING, step = 0, dueAt = endOfDay + 60_000), endOfDay)
        assertEquals(0, session.learningRemaining)
        assertTrue(session.isEmpty())
    }

    @Test
    fun `undo puts a learning card back with the learning cards`() {
        val now = at("2026-10-10T20:00")
        val step = Card(id = 9, wordId = 9, direction = Direction.RECOGNITION, state = CardState.LEARNING, step = 0, dueAt = now)
        val session = StudySession(listOf(review(1)), emptyList(), listOf(step))
        val shown = session.next(now)!!
        assertEquals(9, shown.id)
        session.answered(shown.copy(state = CardState.REVIEW, dueAt = now + 86_400_000L), Long.MAX_VALUE)
        assertEquals(0, session.learningRemaining)

        session.restore(shown)
        assertEquals(1, session.learningRemaining)
        assertEquals(1, session.reviewRemaining)
        assertEquals(9, session.next(now)!!.id, "the restored card is due, so it comes first")
    }
}
