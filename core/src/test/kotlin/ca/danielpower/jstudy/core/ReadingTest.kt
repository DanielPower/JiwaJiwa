package ca.danielpower.jstudy.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReadingTest {
    private val ids = mapOf(
        "食べる" to 1L, "読む" to 2L, "高い" to 3L, "勉強" to 4L, "行く" to 5L, "来る" to 6L, "見る" to 7L,
        "本" to 8L, "部屋" to 9L, "静か" to 10L, "図書館" to 11L, "猫" to 12L, "好き" to 13L, "魔導士" to 14L,
        "きれい" to 15L, "言う" to 16L, "飲む" to 17L, "お茶" to 18L, "持つ" to 19L, "雨" to 20L, "降る" to 21L,
    )
    private val readings = mapOf(14L to "まどうし")
    private val reader = Reader { text, reading -> ids[text]?.let { LexiconMatch(it, readings[it] ?: reading.orEmpty()) } }

    private fun word(text: String): ReadingWord {
        val words = reader.words(text).filter { it.kind != WordKind.PUNCTUATION }
        assertEquals(1, words.size, "one word in $text: $words")
        return words[0]
    }

    @Test
    fun aSpeakersNameIsntPartOfTheText() {
        assertEquals(Speech("ユキ", "「でしょ？」"), Speech.of("ユキ|「でしょ？」"))
        assertEquals(Speech(null, "ラーメンが来た。"), Speech.of("ラーメンが来た。"))
        assertEquals("ユキ「でしょ？」", Speech.of("ユキ|「でしょ？」").toString())
        assertEquals("「でしょ？」", reader.read("ユキ|「でしょ？」").single().joinToString("") { it.surface })
    }

    @Test
    fun aRunOfDialogueLinesUpOnItsWidestName() {
        val speakers = listOf("アレックス", "ユキ", null, "ユキ", "ケンタ", null)
        assertEquals(listOf(5f, 5f, null, 3f, 3f, null), Speech.columns(speakers) { it.length.toFloat() })
    }

    @Test
    fun conjugationsAttachToTheirVerb() {
        val w = word("食べさせられた")
        assertEquals("食べる", w.lemma)
        assertEquals(1L, w.wordId)
        assertEquals(listOf("causative", "passive or potential", "past"), w.conjugation)
        assertEquals(listOf(Ruby("食", "た"), Ruby("べ", null), Ruby("させ", null), Ruby("られ", null), Ruby("た", null)), w.ruby)
    }

    @Test
    fun labels() {
        assertEquals(listOf("past"), word("読んだ").conjugation)
        assertEquals(listOf("past"), word("高かった").conjugation)
        assertEquals(listOf("polite", "negative", "past"), word("言われませんでした").conjugation.drop(1))
        assertEquals(listOf("passive"), word("言われませんでした").conjugation.take(1))
        assertEquals(listOf("want to", "negative", "past"), word("行きたくなかった").conjugation)
        assertEquals(listOf("must"), word("行かなければならない").conjugation)
        assertEquals(listOf("polite", "past"), word("来ました").conjugation)
        assertEquals(listOf("please"), word("見てください").conjugation)
        assertEquals(listOf("completely or regrettably", "past"), word("飲んでしまった").conjugation)
        assertEquals(listOf("conditional (if/when)"), word("行ったら").conjugation)
        assertEquals(listOf("volitional (let's)"), word("行こう").conjugation)
        assertEquals(listOf("past"), word("きれいだった").conjugation)
        assertEquals(listOf("probably"), word("食べるだろう").conjugation)
        assertEquals(listOf("polite", "probably"), word("行くでしょう").conjugation)
    }

    @Test
    fun ongoingVerbAndSuruNoun() {
        val w = word("勉強しています")
        assertEquals("勉強する", w.lemma)
        assertEquals(4L, w.wordId)
        assertEquals(listOf("ongoing or resulting state", "polite"), w.conjugation)
    }

    @Test
    fun plainWordsAndParticles() {
        val words = reader.words("静かな部屋で本を読んでいる。")
        assertEquals(listOf("静か", "な", "部屋", "で", "本", "を", "読んでいる", "。"), words.map { it.surface })
        assertEquals(WordKind.GRAMMAR, words[1].kind)
        assertEquals(WordKind.PUNCTUATION, words.last().kind)
        assertTrue(words[0].conjugation.isEmpty())
        assertEquals(listOf("ongoing or resulting state"), words[6].conjugation)
    }

    @Test
    fun compoundNounsFromTheDictionary() {
        val words = reader.words("魔導士は図書館に行った")
        assertEquals("魔導士", words[0].surface)
        assertEquals(14L, words[0].wordId)
        assertEquals(listOf(Ruby("魔導士", "まどうし")), words[0].ruby)
    }

    @Test
    fun namesAndNumbersAreNotVocabulary() {
        val words = reader.words("タロウさんは三匹の猫と東京へ行く")
        val kinds = words.associate { it.surface to it.kind }
        assertEquals(WordKind.NAME, kinds["タロウ"])
        assertEquals(WordKind.NAME, kinds["三"])
        assertEquals(WordKind.NAME, kinds["東京"])
        assertEquals(WordKind.WORD, kinds["猫"])
        assertNull(words.first { it.surface == "タロウ" }.wordId)
    }

    @Test
    fun rubyMatchesKanaInsideWords() {
        assertEquals(
            listOf(Ruby("取", "と"), Ruby("り", null), Ruby("扱", "あつか"), Ruby("い", null)),
            Reader.ruby("取り扱い", "とりあつかい"),
        )
        assertEquals(listOf(Ruby("ねこ", null)), Reader.ruby("ねこ", "ねこ"))
        assertEquals(listOf(Ruby("今日", "きょう")), Reader.ruby("今日", "キョウ"))
    }

    @Test
    fun paragraphs() {
        assertEquals(2, reader.read("猫が好きです。\n\n本を読む。").size)
    }
}
