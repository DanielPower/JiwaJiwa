package ca.danielpower.jstudy.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TranslationTest {
    private val reader = Reader { _, _ -> null }

    private fun sentences(text: String): List<String> = sentenceTexts(reader.read(text))

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

    @Test
    fun `reads numbered translations`() {
        assertEquals(listOf("I like cats.", "Dogs too!"), parseTranslations("1. I like cats.\n2) Dogs too!\n", 2))
    }

    @Test
    fun `rejects a reply missing a sentence`() {
        assertNull(parseTranslations("1. I like cats.\n3. Birds?", 3))
    }

    @Test
    fun `numbers the sentences in the request`() {
        assertEquals("1. 猫です。\n2. 犬です。", TranslationPrompts.request(listOf("猫です。", "犬です。")))
    }
}
