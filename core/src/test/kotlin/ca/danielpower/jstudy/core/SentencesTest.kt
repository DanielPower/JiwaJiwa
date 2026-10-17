package ca.danielpower.jstudy.core

import kotlin.test.Test
import kotlin.test.assertEquals

class SentencesTest {
    private val reader = Reader { _, _ -> null }

    private fun sentences(text: String): List<String> =
        reader.read(text).flatMap { words -> sentenceRanges(words).map { r -> r.joinToString("") { words[it].surface } } }

    @Test
    fun `splits a paragraph after each full stop`() {
        assertEquals(listOf("猫が好きです。", "犬も好きです！", "鳥は？"), sentences("猫が好きです。犬も好きです！鳥は？"))
    }

    @Test
    fun `keeps a quote with the words around it`() {
        assertEquals(
            listOf("そして、夫は妻に「青がいいと思うよ。」と、言った。", "妻は笑った。"),
            sentences("そして、夫は妻に「青がいいと思うよ。」と、言った。妻は笑った。"),
        )
    }

    @Test
    fun `ends a sentence after a quote that closes it`() {
        assertEquals(listOf("「あれは何？」", "「本です。」"), sentences("「あれは何？」「本です。」"))
    }

    @Test
    fun `keeps text without a full stop as one sentence`() {
        assertEquals(listOf("猫の本"), sentences("猫の本"))
        assertEquals(listOf("猫です。", "犬"), sentences("猫です。犬"))
    }

    @Test
    fun `counts sentences paragraph by paragraph`() {
        assertEquals(listOf("猫です。", "犬です。", "鳥です。"), sentences("猫です。犬です。\n鳥です。"))
    }
}
