package ca.danielpower.jstudy.core

import kotlin.test.Test
import kotlin.test.assertEquals

class HorizontalTextTest {
    /** One word per character, each 1 wide; lines read back as text. */
    private fun lines(text: String, width: Int): List<String> {
        val words = text.map { FlowWord(it.toString(), 1f) }
        return HorizontalText.lines(words, width.toFloat()).map { r -> r.joinToString("") { words[it].text } }
    }

    @Test
    fun `fills each line before starting the next`() {
        assertEquals(listOf("あいう", "えお"), lines("あいうえお", 3))
    }

    @Test
    fun `carries a word over rather than start a line with punctuation`() {
        assertEquals(listOf("あい", "う。え"), lines("あいう。え", 3))
        assertEquals(listOf("あい", "う。」"), lines("あいう。」", 3))
    }

    @Test
    fun `never ends a line with an opening bracket`() {
        assertEquals(listOf("あい", "「う"), lines("あい「う", 3))
    }

    @Test
    fun `leaves room for a first line's indent and the wrapped lines' own`() {
        val words = "あいうえおかき".map { FlowWord(it.toString(), 1f) }
        val lines = HorizontalText.lines(words, 5f, indent = 2f, wrapIndent = 3f)
        assertEquals(listOf("あいう", "えお", "かき"), lines.map { r -> r.joinToString("") { words[it].text } })
    }

    @Test
    fun `gives up on the rule rather than empty a line`() {
        assertEquals(listOf("あ", "。", "。"), lines("あ。。", 1))
    }

    @Test
    fun `puts a word wider than the line on its own line`() {
        val words = listOf(FlowWord("あ", 1f), FlowWord("長い", 5f), FlowWord("い", 1f))
        assertEquals(listOf(0..0, 1..1, 2..2), HorizontalText.lines(words, 3f))
    }

    @Test
    fun `fills pages with whole lines and gaps between paragraphs`() {
        // Lines 10 tall, gaps 5, pages 30: two paragraphs of two lines need 45.
        val pages = HorizontalText.pages(listOf(listOf(0..0, 1..1), listOf(0..0, 1..1)), 10f, 5f, 30f)
        assertEquals(
            listOf(
                listOf(TextLine(0, 0..0), TextLine(0, 1..1)),
                listOf(TextLine(1, 0..0), TextLine(1, 1..1)),
            ),
            pages,
        )
    }

    @Test
    fun `runs a long paragraph on to the next page`() {
        val pages = HorizontalText.pages(listOf(listOf(0..0, 1..1, 2..2)), 10f, 5f, 25f)
        assertEquals(listOf(listOf(TextLine(0, 0..0), TextLine(0, 1..1)), listOf(TextLine(0, 2..2))), pages)
    }

    @Test
    fun `puts a line on every page even when it doesn't fit`() {
        assertEquals(listOf(listOf(TextLine(0, 0..0)), listOf(TextLine(0, 1..1))), HorizontalText.pages(listOf(listOf(0..0, 1..1)), 10f, 0f, 5f))
    }
}
