package ca.danielpower.jstudy.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class KanaCourseTest {
    @Test
    fun `teaches each sound in both scripts back to back`() {
        val first = KanaCourse.units.take(4).map { it.text }
        assertEquals(listOf("あ", "ア", "い", "イ"), first)
        val ka = KanaCourse.units.first { it.text == "カ" }
        assertEquals("ka", ka.romaji)
        assertEquals("か", ka.twin)
    }

    @Test
    fun `has every unit once with a unique negative id`() {
        val units = KanaCourse.units
        assertEquals(105, units.count { it.script == Script.HIRAGANA })
        assertEquals(121, units.count { it.script == Script.KATAKANA })
        assertEquals(units.size, units.map { it.id }.toSet().size)
        assertEquals(units.size, units.map { it.text }.toSet().size)
        assertTrue(units.all { it.id < 0 && KanaCourse.unit(it.id) == it })
    }

    @Test
    fun `ids come from the characters`() {
        assertEquals(-'あ'.code.toLong(), KanaCourse.idOf("あ"))
        assertEquals(-('き'.code.toLong() * 0x10000 + 'ょ'.code), KanaCourse.idOf("きょ"))
    }

    @Test
    fun `splits text into units, longest first`() {
        assertEquals(listOf("きょ", "う"), KanaCourse.unitsIn("きょう").map { it.text })
        assertEquals(listOf("が", "っ", "こ", "う"), KanaCourse.unitsIn("がっこう").map { it.text })
        assertEquals(listOf("コ", "ー", "ヒ", "ー"), KanaCourse.unitsIn("コーヒー").map { it.text })
        assertEquals(listOf("パ", "ー", "ティ", "ー"), KanaCourse.unitsIn("パーティー").map { it.text })
        // Kanji and untaught characters are skipped.
        assertEquals(listOf("べ", "る"), KanaCourse.unitsIn("食べる").map { it.text })
        assertEquals(listOf("ヴ"), KanaCourse.unitsIn("ヴァ").map { it.text })
    }

    @Test
    fun `can read text once every unit is known or learned`() {
        val learned = setOf(KanaCourse.idOf("か"), KanaCourse.idOf("お"))
        assertTrue(KanaCourse.canRead("かお", KanaKnown.NONE, learned))
        assertFalse(KanaCourse.canRead("あか", KanaKnown.NONE, learned))
        assertFalse(KanaCourse.canRead("テレビ", KanaKnown.HIRAGANA, emptySet()))
        assertTrue(KanaCourse.canRead("たべる", KanaKnown.HIRAGANA, emptySet()))
        assertTrue(KanaCourse.canRead("テレビ", KanaKnown.BOTH, emptySet()))
    }

    @Test
    fun `teaches only the scripts you don't know`() {
        assertEquals(226, KanaCourse.toTeach(KanaKnown.NONE).size)
        assertTrue(KanaCourse.toTeach(KanaKnown.HIRAGANA).all { it.script == Script.KATAKANA })
        assertTrue(KanaCourse.toTeach(KanaKnown.BOTH).isEmpty())
    }

    @Test
    fun `reads a missing setting as knowing both`() {
        assertEquals(KanaKnown.BOTH, KanaKnown.of(null))
        assertEquals(KanaKnown.NONE, KanaKnown.of("none"))
    }
}
