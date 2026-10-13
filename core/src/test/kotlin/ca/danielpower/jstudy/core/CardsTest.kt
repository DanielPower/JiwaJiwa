package ca.danielpower.jstudy.core

import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CardsTest {
    private val zone = ZoneId.of("America/St_Johns")
    private val clock = StudyClock(zone, rolloverHour = 4)
    private val reviewer = Reviewer(Fsrs(), clock)

    private fun at(text: String) = LocalDateTime.parse(text).atZone(zone).toInstant().toEpochMilli()

    @Test
    fun `stored values map back to the same enum`() {
        for (d in Direction.entries) assertEquals(d, Direction.of(d.value))
        for (s in CardState.entries) assertEquals(s, CardState.of(s.value))
        assertFailsWith<NoSuchElementException> { Direction.of(9) }
        assertFailsWith<NoSuchElementException> { CardState.of(9) }
        assertEquals(listOf(CardState.LEARNING, CardState.RELEARNING), CardState.entries.filter { it.inSteps })
    }

    @Test
    fun `mastery rises with stability and the inverse agrees`() {
        var last = 0
        var s = 0.5
        while (s < 200) {
            val m = MasteryRules.fromStability(s)
            assertTrue(m >= last, "mastery fell at stability $s")
            assertTrue(m in 0..MasteryRules.MAX)
            last = m
            s += 0.25
        }
        var lastDays = 0
        for (m in -5..105) {
            val days = MasteryRules.stabilityDays(m)
            assertTrue(days >= lastDays, "days fell at mastery $m")
            lastDays = days
        }
        assertEquals(MasteryRules.stabilityDays(0), MasteryRules.stabilityDays(-5))
        assertEquals(MasteryRules.stabilityDays(100), MasteryRules.stabilityDays(105))
    }

    @Test
    fun `answers count reps and keep the first study day`() {
        val now = at("2026-10-10T20:00")
        val card = Card(id = 1, wordId = 7, direction = Direction.RECOGNITION)
        val first = reviewer.answer(card, WordProgress(7), correct = true, now = now)
        val second = reviewer.answer(first.card, first.progress, correct = true, now = at("2026-10-11T20:00"))
        assertEquals(2, second.card.reps)
        assertEquals(clock.dayOf(now), second.card.introducedDay)
        assertEquals(at("2026-10-11T20:00"), second.card.lastReviewAt)
    }

    @Test
    fun `only a miss on a review card counts as a lapse`() {
        val now = at("2026-10-10T20:00")
        val learning = reviewer.answer(Card(id = 1, wordId = 7, direction = Direction.RECOGNITION), WordProgress(7), correct = false, now = now)
        assertEquals(CardState.LEARNING, learning.card.state)
        assertEquals(0, learning.card.lapses)
        assertFalse(learning.log.correct)
        assertEquals(CardState.NEW, learning.log.stateBefore)
        assertEquals(CardState.LEARNING, learning.log.stateAfter)
    }

    @Test
    fun `answers check the card belongs to the word`() {
        val card = Card(id = 1, wordId = 7, direction = Direction.RECOGNITION)
        assertFailsWith<IllegalArgumentException> { reviewer.answer(card, WordProgress(8), true, 0) }
        assertFailsWith<IllegalArgumentException> { reviewer.markKnown(card, WordProgress(8), 50, 0, Random(1)) }
        assertFailsWith<IllegalArgumentException> { reviewer.introduce(card.copy(state = CardState.REVIEW), 0) }
    }

    @Test
    fun `kana cards earn mastery but never unlock a production card`() {
        val card = Card(
            id = 1, wordId = KanaCourse.units.first().id, direction = Direction.KANA, state = CardState.REVIEW,
            stability = 20.0, difficulty = 5.0, lastReviewAt = at("2026-10-01T09:00"),
        )
        val answer = reviewer.answer(card, WordProgress(card.wordId), correct = true, now = at("2026-10-10T09:00"))
        assertTrue(answer.progress.mastery >= 40)
        assertFalse(answer.unlockedProduction)
        assertFalse(answer.progress.productionUnlocked)
    }

    @Test
    fun `marking a word known below the production threshold keeps it locked`() {
        val now = at("2026-10-10T20:00")
        val card = Card(id = 1, wordId = 7, direction = Direction.RECOGNITION)
        val low = reviewer.markKnown(card, WordProgress(7), 20, now, Random(1))
        assertFalse(low.unlockedProduction)
        assertEquals(20, low.progress.mastery)

        val capped = reviewer.markKnown(card, WordProgress(7), 150, now, Random(1))
        assertEquals(100, capped.progress.mastery)
        assertEquals(90, capped.intervalDays)
        assertEquals(90.0, capped.card.stability)

        val already = reviewer.markKnown(card, WordProgress(7, productionUnlocked = true), 80, now, Random(1))
        assertFalse(already.unlockedProduction, "reports only a new unlock")
        assertTrue(already.progress.productionUnlocked)
    }
}
