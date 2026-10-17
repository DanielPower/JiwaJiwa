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
    fun aPinnedReadingPicksTheWord() {
        // 方 is ほう (direction, ～のほうが) or かた (person); the tokenizer only knows ほう, as grammar.
        val hou = LexiconMatch(30, "ほう")
        val kata = LexiconMatch(31, "かた")
        val reader = Reader { text, reading -> if (text == "方") (if (reading == "かた") kata else hou) else null }
        assertEquals(WordKind.GRAMMAR, reader.words("あの方").first { it.surface == "方" }.kind)
        val w = reader.words("あの方", mapOf("方" to "かた")).first { it.surface == "方" }
        assertEquals(WordKind.WORD, w.kind)
        assertEquals(31L, w.wordId)
        assertEquals(listOf(Ruby("方", "かた")), w.ruby)
        // A reading the dictionary doesn't have for the word changes nothing.
        assertEquals(WordKind.GRAMMAR, reader.words("あの方", mapOf("方" to "がた")).first { it.surface == "方" }.kind)
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
    fun namesAndDigitsAreNotVocabulary() {
        val words = reader.words("タロウさんは3匹の猫と東京へ行く")
        val kinds = words.associate { it.surface to it.kind }
        assertEquals(WordKind.NAME, kinds["タロウ"])
        assertEquals(WordKind.NAME, kinds["さん"])
        assertEquals(WordKind.NAME, kinds["3"])
        assertEquals(WordKind.NAME, kinds["東京"])
        assertEquals(WordKind.WORD, kinds["猫"])
        assertNull(words.first { it.surface == "タロウ" }.wordId)
    }

    @Test
    fun numbersInKanjiAndCountersAreWords() {
        // 二人 is taught as a word (a JLPT entry); 三時 isn't, so it's 三 and 時.
        val dictionary = mapOf("三" to LexiconMatch(30, "さん", 5), "時" to LexiconMatch(31, "じ"), "二人" to LexiconMatch(32, "ふたり", 5), "三時" to LexiconMatch(33, "さんじ"))
        val reader = Reader { text, _ -> dictionary[text] }
        assertEquals(listOf("三" to 30L, "時" to 31L), reader.words("三時に来る").take(2).map { it.surface to it.wordId })
        val pair = reader.words("二人").single()
        assertEquals(32L, pair.wordId)
        assertEquals(listOf(Ruby("二人", "ふたり")), pair.ruby)
        assertEquals(WordKind.WORD, reader.words("三匹").first().kind)
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
