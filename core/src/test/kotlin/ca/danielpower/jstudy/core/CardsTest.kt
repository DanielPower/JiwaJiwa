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
    fun `reading a due word in a story raises mastery less than a right answer`() {
        val now = at("2026-10-14T12:00")
        val card = Card(1, 7, Direction.RECOGNITION, CardState.REVIEW, stability = 10.0, difficulty = 5.0,
            dueAt = now - 1, lastReviewAt = at("2026-10-04T12:00"), reps = 3)
        val progress = WordProgress(7, MasteryRules.fromStability(10.0))
        val read = reviewer.read(card, progress, now)
        val right = reviewer.answer(card, progress, true, now)
        assertTrue(read.log.correct)
        assertTrue(read.progress.mastery > progress.mastery)
        assertTrue(read.progress.mastery < right.progress.mastery)
        assertTrue(read.card.dueAt < right.card.dueAt)
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
    fun `a word filled in by reading level starts that day, and relearning it doesn't change how`() {
        val now = at("2026-10-10T20:00")
        val known = reviewer.markKnownFor(Card(id = 1, wordId = 7, direction = Direction.RECOGNITION), WordProgress(7), 15, 40, now, Random(1))
        val missed = reviewer.answer(known.card, known.progress, correct = false, now = known.card.dueAt)
        assertEquals(CardState.RELEARNING, missed.card.state)
        val relearned = reviewer.answer(missed.card, missed.progress, correct = true, now = missed.card.dueAt)
        assertEquals(clock.dayOf(now), relearned.card.introducedDay)
        assertEquals(StartSource.LEVEL, relearned.card.startSource)
    }

    @Test
    fun `marking a word known starts it today, apart from the day's new words`() {
        val now = at("2026-10-10T20:00")
        val today = clock.dayOf(now)
        val fresh = Card(id = 1, wordId = 7, direction = Direction.RECOGNITION)
        val known = reviewer.markKnown(fresh, WordProgress(7), 50, now, Random(1))
        assertEquals(today, known.card.introducedDay)
        assertEquals(StartSource.KNOWN, known.card.startSource)
        val reviewed = reviewer.answer(known.card, known.progress, correct = true, now = known.card.dueAt)
        assertEquals(today, reviewed.card.introducedDay)
        assertEquals(StartSource.KNOWN, reviewed.card.startSource)

        // Started by a story today, then known: still today's, no longer one of its new words.
        val startedToday = reviewer.introduce(fresh, now)
        assertEquals(StartSource.KNOWN, reviewer.markKnown(startedToday, WordProgress(7), 50, now, Random(1)).card.startSource)

        // Started on an earlier day: that day stays, as one of its new words.
        val earlier = reviewer.introduce(fresh, at("2026-10-01T20:00"))
        val later = reviewer.markKnown(earlier, WordProgress(7), 50, now, Random(1)).card
        assertEquals(earlier.introducedDay, later.introducedDay)
        assertEquals(StartSource.STORY, later.startSource)

        // Imported: dated today too, as an import.
        val imported = reviewer.markKnown(fresh, WordProgress(7), 50, now, Random(1), StartSource.IMPORT).card
        assertEquals(today, imported.introducedDay)
        assertEquals(StartSource.IMPORT, imported.startSource)
        // Imported again a week later: the first import's day stays.
        val again = reviewer.markKnown(imported, WordProgress(7), 70, at("2026-10-17T20:00"), Random(1), StartSource.IMPORT).card
        assertEquals(today, again.introducedDay)
    }

    @Test
    fun `stored start sources map back and only stories and known words are recent`() {
        for (s in StartSource.entries) assertEquals(s, StartSource.of(s.value))
        assertEquals(listOf(StartSource.STORY, StartSource.KNOWN), StartSource.entries.filter { it.recent })
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
    fun `marking known for a stability spreads the first review over the window`() {
        val now = at("2026-10-10T20:00")
        val card = Card(id = 1, wordId = 7, direction = Direction.RECOGNITION)
        val dues = (1..50).map { seed ->
            reviewer.markKnownFor(card, WordProgress(7), 15, 40, now, Random(seed)).card.dueAt
        }
        val known = reviewer.markKnownFor(card, WordProgress(7), 15, 40, now, Random(1))
        assertEquals(15.0, known.card.stability)
        assertEquals(50, known.progress.mastery)
        assertTrue(dues.all { it > now && it <= now + 41 * 86_400_000L })
        assertTrue(dues.any { it > now + 16 * 86_400_000L })
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
        assertEquals(90.0, capped.card.stability)

        val already = reviewer.markKnown(card, WordProgress(7, productionUnlocked = true), 80, now, Random(1))
        assertFalse(already.unlockedProduction, "reports only a new unlock")
        assertTrue(already.progress.productionUnlocked)
    }
}
