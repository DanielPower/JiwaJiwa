package ca.danielpower.jstudy.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UiLabelsTest {
    private val ids = mapOf("答え" to 1L, "見る" to 2L, "勉強" to 3L, "始める" to 4L, "インポート" to 5L, "設定" to 6L)
    private val lexicon = Lexicon { text, reading -> ids[text]?.let { LexiconMatch(it, reading.orEmpty()) } }
    private val reader = Reader(lexicon)

    private fun words(label: UiLabel) = UiLabels.words(label, reader, lexicon)

    @Test
    fun `a label needs its content words, not its particles`() {
        assertEquals(listOf(1L, 2L), words(UiLabel("Show answer", "答えを見る")))
        assertEquals(listOf(3L, 4L), words(UiLabel("Start studying", "勉強を始める")))
    }

    @Test
    fun `a conjugated word counts as its dictionary form`() {
        assertEquals(listOf(2L), words(UiLabel("Saw", "見ました")))
    }

    @Test
    fun `a label with a word the dictionary lacks stays English`() {
        assertNull(words(UiLabel("Read a book", "本を読む")))
    }

    @Test
    fun `a label with a loanword the dictionary lacks stays English`() {
        assertNull(words(UiLabel("Open the app", "アプリを開く")))
    }

    @Test
    fun `a label with no vocabulary stays English`() {
        assertNull(words(UiLabel("Of", "の")))
    }

    @Test
    fun `named words replace the automatic split`() {
        assertEquals(listOf(5L), words(UiLabel("Import from Renshuu", "Renshuuからインポート", listOf("インポート"))))
        assertNull(words(UiLabel("Import", "インポート", listOf("輸入"))))
    }

    @Test
    fun `a label turns Japanese once every word is mastered`() {
        val label = UiLabel("Show answer", "答えを見る")
        val words = mapOf(label.key to words(label))
        assertEquals("Show answer", UiLanguage(words, setOf(1L)).text(label))
        assertEquals("答えを見る", UiLanguage(words, setOf(1L, 2L, 9L)).text(label))
    }

    @Test
    fun `labels not yet looked up or that can't switch stay English`() {
        val settings = UiLabel("Settings", "設定")
        val book = UiLabel("Read a book", "本を読む")
        val language = UiLanguage(mapOf(book.key to null), setOf(6L))
        assertFalse(language.knows(settings))
        assertEquals("Settings", language.text(settings))
        assertTrue(language.knows(book))
        assertEquals("Read a book", language.text(book))
        assertEquals("設定", language.withWords(mapOf(settings.key to listOf(6L))).text(settings))
    }

    @Test
    fun `naming words changes the cache key`() {
        assertEquals("設定", UiLabel("Settings", "設定").key)
        assertEquals("設定|設定", UiLabel("Settings", "設定", listOf("設定")).key)
    }
}
