package ca.danielpower.jstudy.core

import kotlin.math.abs
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

class FsrsTest {
    private val fsrs = Fsrs()

    private data class Row(
        val case: Int,
        val rating: Int,
        val elapsedDays: Int?,
        val state: Int,
        val step: Int?,
        val stability: Double,
        val difficulty: Double,
        val intervalSeconds: Double,
    )

    private fun vectors(): List<Row> =
        javaClass.getResource("/fsrs_vectors.tsv")!!.readText().lines()
            .filter { it.isNotBlank() && !it.startsWith("#") }
            .map { line ->
                val f = line.split('\t')
                Row(
                    case = f[0].toInt(),
                    rating = f[1].toInt(),
                    elapsedDays = f[2].toInt().takeIf { it >= 0 },
                    state = f[3].toInt(),
                    step = f[4].toInt().takeIf { it >= 0 },
                    stability = f[5].toDouble(),
                    difficulty = f[6].toDouble(),
                    intervalSeconds = f[7].toDouble(),
                )
            }

    private fun close(a: Double, b: Double) = abs(a - b) <= 1e-9 * maxOf(1.0, abs(b))

    @Test
    fun `matches py-fsrs reference vectors`() {
        val rows = vectors()
        assertTrue(rows.size > 800)
        for ((case, reviews) in rows.groupBy { it.case }) {
            var memory = FsrsMemory.NEW
            reviews.forEachIndexed { i, r ->
                val result = fsrs.review(memory, Rating.entries.first { it.value == r.rating }, r.elapsedDays)
                val m = result.memory
                val seconds = when (val iv = result.interval) {
                    is FsrsInterval.Minutes -> iv.minutes * 60
                    is FsrsInterval.Days -> iv.days * 86400.0
                }
                val where = "case $case review $i"
                assertEquals(r.state, m.state.value, "$where state")
                assertEquals(r.step, m.step, "$where step")
                if (!close(m.stability!!, r.stability)) fail("$where stability ${m.stability} != ${r.stability}")
                if (!close(m.difficulty!!, r.difficulty)) fail("$where difficulty ${m.difficulty} != ${r.difficulty}")
                assertEquals(r.intervalSeconds, seconds, "$where interval")
                memory = m
            }
        }
    }

    @Test
    fun `new card answered right twice graduates to one day or more`() {
        val first = fsrs.review(FsrsMemory.NEW, Rating.GOOD, null)
        assertEquals(FsrsInterval.Minutes(10.0), first.interval)
        val second = fsrs.review(first.memory, Rating.GOOD, 0)
        assertEquals(FsrsState.REVIEW, second.memory.state)
        assertTrue((second.interval as FsrsInterval.Days).days >= 1)
    }

    @Test
    fun `fuzz stays inside the reference range`() {
        val memory = FsrsMemory(FsrsState.REVIEW, null, 40.0, 5.0)
        val plain = fsrs.review(memory, Rating.GOOD, 40).interval as FsrsInterval.Days
        val range = fsrs.fuzzRange(plain.days)
        val random = Random(1)
        val seen = (1..500).map { (fsrs.review(memory, Rating.GOOD, 40, random).interval as FsrsInterval.Days).days }.toSet()
        assertTrue(seen.all { it in range }, "$seen outside $range")
        assertEquals(range.toSet(), seen)
    }

    @Test
    fun `short intervals are never fuzzed`() {
        assertEquals(2..2, fsrs.fuzzRange(2))
        assertEquals(1..1, fsrs.fuzzRange(1))
    }
}
