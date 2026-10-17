package ca.danielpower.jstudy.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ReadLevelTest {
    @Test
    fun `the level you read is known, easier levels further out`() {
        fun mastery(word: Int) = ReadLevel.stabilityDays(3, word)?.let { MasteryRules.fromStability(it.toDouble()) }
        assertEquals(ReadLevel.AT_LEVEL, mastery(3))
        assertEquals(MasteryRules.OFF_AT, mastery(4))
        assertEquals(MasteryRules.MAX, mastery(5))
        assertEquals(365, ReadLevel.stabilityDays(1, 4))
    }

    @Test
    fun `nothing is filled in above the level, off the lists, or with no level`() {
        assertNull(ReadLevel.stabilityDays(4, 3))
        assertNull(ReadLevel.stabilityDays(4, 0))
        assertNull(ReadLevel.stabilityDays(ReadLevel.NONE, 5))
    }

    @Test
    fun `a big batch spreads its first reviews to about 100 a day`() {
        assertEquals(15, ReadLevel.firstDueWithin(15, 667))
        assertEquals(31, ReadLevel.firstDueWithin(15, 3056))
        assertEquals(90, ReadLevel.firstDueWithin(90, 3056))
    }
}
