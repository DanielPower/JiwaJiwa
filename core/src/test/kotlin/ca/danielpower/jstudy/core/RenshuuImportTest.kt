package ca.danielpower.jstudy.core

import kotlin.test.Test
import kotlin.test.assertEquals

class RenshuuImportTest {
    private val jstudy = setOf(1L, 2L, 3L, 4L, 5L, 6L)

    private fun plan(terms: List<RenshuuTerm>, local: Map<Long, LocalWord> = emptyMap()) =
        RenshuuImport.plan(terms, { it in jstudy }, local)

    @Test
    fun `takes the higher mastery`() {
        val p = plan(
            listOf(RenshuuTerm(1, 80), RenshuuTerm(2, 30), RenshuuTerm(3, 55)),
            mapOf(
                2L to LocalWord(50, started = true, suspended = false),
                3L to LocalWord(20, started = true, suspended = false),
            ),
        )
        assertEquals(mapOf(1L to 80, 3L to 55), p.updates)
        assertEquals(1, p.newWords)
        assertEquals(1, p.raised)
        assertEquals(1, p.unchanged)
    }

    @Test
    fun `skips words jstudy doesn't have`() {
        val p = plan(listOf(RenshuuTerm(1, 50), RenshuuTerm(99, 50), RenshuuTerm(null, 50)))
        assertEquals(mapOf(1L to 50), p.updates)
        assertEquals(3, p.renshuuWords)
        assertEquals(2, p.notInJstudy)
    }

    @Test
    fun `kana without an entry number go to jstudy's kana cards`() {
        val ka = KanaCourse.idOf("か")
        val kya = KanaCourse.idOf("キャ")
        val p = plan(
            listOf(
                RenshuuTerm(null, 44, "か"),
                RenshuuTerm(null, 41, "キャ"),
                RenshuuTerm(null, 30, "勉強する"),
                // A word with an entry number is never taken for a kana, even one jstudy lacks.
                RenshuuTerm(99, 50, "か"),
            ),
            mapOf(ka to LocalWord(60, started = true, suspended = false)),
        )
        assertEquals(mapOf(kya to 41), p.updates)
        assertEquals(1, p.unchanged)
        assertEquals(2, p.notInJstudy)
    }

    @Test
    fun `leaves suspended words and zero mastery alone`() {
        val p = plan(
            listOf(RenshuuTerm(4, 90), RenshuuTerm(5, 0)),
            mapOf(4L to LocalWord(0, started = false, suspended = true)),
        )
        assertEquals(emptyMap(), p.updates)
        assertEquals(1, p.suspended)
        assertEquals(1, p.unchanged)
    }

    @Test
    fun `a word listed twice counts at its highest mastery`() {
        val p = plan(listOf(RenshuuTerm(6, 40), RenshuuTerm(6, 75), RenshuuTerm(1, 150)))
        assertEquals(mapOf(6L to 75, 1L to 100), p.updates)
    }
}
