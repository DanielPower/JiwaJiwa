package ca.danielpower.jstudy.core

import kotlin.test.Test
import kotlin.test.assertEquals

class VerticalTextTest {
    /** Sets [text] one character per run and reads it back column by column. */
    private fun columns(text: String, rows: Int, runs: List<VerticalRun> = text.map { VerticalRun(listOf(it.toString())) }): List<String> {
        val spots = VerticalText.place(runs, rows)
        return spots.indices.groupBy { spots[it].col }.toSortedMap().values.map { ks -> ks.joinToString("") { runs[it].chars.joinToString("") } }
    }

    @Test
    fun `starts a paragraph and its wrapped columns where it says`() {
        val runs = "あいうえおか".mapIndexed { i, c -> VerticalRun(listOf(c.toString()), paragraphStart = i == 0, indent = 3, wrapIndent = 4) }
        val spots = VerticalText.place(runs, 6)
        assertEquals(listOf(VerticalSpot(0, 3), VerticalSpot(0, 4), VerticalSpot(0, 5), VerticalSpot(1, 4), VerticalSpot(1, 5), VerticalSpot(2, 4)), spots)
    }

    @Test
    fun `fills each column before starting the next`() {
        assertEquals(listOf("あいう", "えお"), columns("あいうえお", 3))
    }

    @Test
    fun `lets a comma or full stop hang below a full column`() {
        assertEquals(listOf("あいう。", "えお"), columns("あいう。えお", 3))
    }

    @Test
    fun `carries the last character over rather than start a column with a closing bracket`() {
        assertEquals(listOf("あい", "う」え"), columns("あいう」え", 3))
    }

    @Test
    fun `keeps a closing bracket with the punctuation hanging before it`() {
        assertEquals(listOf("あい", "う。」"), columns("あいう。」", 3))
        assertEquals(listOf("あい", "う！」"), columns("あいう！」", 3))
    }

    @Test
    fun `carries the last character over rather than start a column with a glued run`() {
        val runs = "あいう".map { VerticalRun(listOf(it.toString())) } + VerticalRun(listOf("注"), glued = true)
        assertEquals(listOf("あい", "う注"), columns("", 3, runs))
    }

    @Test
    fun `never ends a column with an opening bracket`() {
        assertEquals(listOf("あい", "「う"), columns("あい「う", 3))
    }

    @Test
    fun `keeps a kanji run with its furigana in one column`() {
        val runs = listOf(VerticalRun(listOf("あ")), VerticalRun(listOf("あ")), VerticalRun(listOf("靴", "下")))
        assertEquals(listOf(VerticalSpot(0, 0), VerticalSpot(0, 1), VerticalSpot(1, 0)), VerticalText.place(runs, 3))
    }

    @Test
    fun `starts paragraphs in a new column one cell down`() {
        val runs = listOf(VerticalRun(listOf("あ"), paragraphStart = true), VerticalRun(listOf("い"), paragraphStart = true))
        assertEquals(listOf(VerticalSpot(0, 1), VerticalSpot(1, 1)), VerticalText.place(runs, 3))
    }

    @Test
    fun `leaves a column whole when there is nothing to carry over`() {
        assertEquals(listOf("」」」", "」"), columns("」」」」", 3))
    }
}
