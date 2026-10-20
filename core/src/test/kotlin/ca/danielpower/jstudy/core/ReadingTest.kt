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
        // 方 is ほう (direction, ～のほうが) or かた (person); after a noun the tokenizer only knows ほう, as grammar.
        val hou = LexiconMatch(30, "ほう")
        val kata = LexiconMatch(31, "かた")
        val reader = Reader { text, reading -> if (text == "方") (if (reading == "かた") kata else hou) else null }
        assertEquals(WordKind.GRAMMAR, reader.words("東の方").first { it.surface == "方" }.kind)
        val w = reader.words("東の方", mapOf("方" to "かた")).first { it.surface == "方" }
        assertEquals(WordKind.WORD, w.kind)
        assertEquals(31L, w.wordId)
        assertEquals(listOf(Ruby("方", "かた")), w.ruby)
        // A reading the dictionary doesn't have for the word changes nothing.
        assertEquals(WordKind.GRAMMAR, reader.words("東の方", mapOf("方" to "がた")).first { it.surface == "方" }.kind)
        // After あの it's a person without a pin.
        assertEquals(31L, reader.words("あの方").first { it.surface == "方" }.wordId)
    }

    @Test
    fun aPinnedDictionaryFormPinsItsConjugations() {
        // 描く is えがく or かく; the tokenizer reads it えがく.
        val kaku = LexiconMatch(50, "かく")
        val reader = Reader { text, reading -> if (text == "描く") (if (reading == "かく") kaku else LexiconMatch(51, "えがく")) else null }
        val w = reader.words("絵を描いた。", mapOf("描く" to "かく")).first { it.lemma == "描く" }
        assertEquals("描いた", w.surface)
        assertEquals(50L, w.wordId)
        assertEquals("かいた", w.reading)
        assertEquals(listOf(Ruby("描", "か"), Ruby("いた", null)), w.ruby)
    }

    @Test
    fun aCastNameIsANameNotAWord() {
        val reader = Reader { text, _ -> if (text == "リン") LexiconMatch(60, "りん") else null }
        assertEquals(WordKind.WORD, reader.words("リンが来た。").first { it.surface == "リン" }.kind)
        val rin = reader.words("リンが来た。", names = setOf("リン")).first { it.surface == "リン" }
        assertEquals(WordKind.NAME, rin.kind)
        assertEquals(null, rin.wordId)
    }

    @Test
    fun wentIsGoNotCarryOut() {
        val reader = Reader { _, _ -> null }
        val w = reader.words("電車で行った。").first { it.surface == "行った" }
        assertEquals("行く", w.lemma)
        assertEquals("いった", w.reading)
    }

    @Test
    fun aConjunctionOpeningASentenceIsAWord() {
        val reader = Reader { text, reading -> if (text == "でも") LexiconMatch(40, "でも") else null }
        val demo = reader.words("うるさい！でも、いい！").first { it.surface == "でも" }
        assertEquals(WordKind.WORD, demo.kind)
        assertEquals(40L, demo.wordId)
        assertEquals(WordKind.GRAMMAR, reader.words("コーヒーでも飲む？").first { it.surface == "でも" }.kind)
    }

    @Test
    fun aFormNoGrammarExplainsIsASetPhrase() {
        val reader = Reader { text, _ -> mapOf("いらっしゃる" to 41L, "いらっしゃいませ" to 42L)[text]?.let { LexiconMatch(it, "いらっしゃいませ") } }
        val w = reader.words("いらっしゃいませ！").first()
        assertEquals(42L, w.wordId)
        assertEquals("いらっしゃいませ", w.lemma)
        assertTrue(w.conjugation.isEmpty())
    }

    @Test
    fun aPotentialIsItsVerbThatCan() {
        fun verb(id: Long, kana: String, godan: Boolean, usuallyKana: Boolean = false, potential: Boolean = false) =
            VerbEntry(id, kana, godan, usuallyKana, potential)
        val verbs = mapOf(
            "話す" to verb(50, "はなす", true), "帰る" to verb(51, "かえる", true), "行く" to verb(52, "いく", true),
            "売る" to verb(53, "うる", true), "来る" to verb(54, "くる", false),
            "弾く" to verb(55, "ひく", true), "歌う" to verb(56, "うたう", true),
            // The dictionary's own entries for some of the forms.
            "話せる" to verb(60, "はなせる", false, potential = true), "行ける" to verb(61, "いける", false, usuallyKana = true),
            "売れる" to verb(62, "うれる", false),
        )
        val reader = Reader(object : Lexicon {
            override fun find(text: String, reading: String?) = verbs[text]?.let { LexiconMatch(it.id, it.kana) }
            override fun verbs(text: String) = listOfNotNull(verbs[text])
        })
        fun read(text: String) = reader.words(text).first()
        for ((text, id) in listOf("話せる" to 50L, "帰れない" to 51L, "行ける" to 52L, "来れる" to 54L)) {
            val w = read(text)
            assertEquals(id, w.wordId, text)
            assertEquals("potential", w.conjugation.first(), text)
            assertEquals(listOf("potential"), w.grammar.map { it.point.id }.filter { it == "potential" }, text)
        }
        // A う verb's potential is one token: what it shares with 話す is the word, the rest the point.
        assertEquals(1..2, read("話せる").grammarChars(0, setOf("potential")))
        // It stops where the word's next point starts: け in 弾けて (て is ～て), え in 歌えなかった.
        assertEquals(1..1, read("弾けて").grammarChars(0, setOf("potential")))
        assertEquals(2..2, read("弾けて").grammarChars(0, setOf("te")))
        assertEquals(1..1, read("歌えなかった").grammarChars(0, setOf("potential")))
        // 売れる sells well: a verb of its own, not "can sell".
        assertEquals(62L, read("売れる").wordId)
        assertTrue(read("売れる").grammar.isEmpty())
    }

    @Test
    fun aStemJoinsThePointAfterIt() {
        val mi = reader.words("見に行く").first()
        assertEquals(7L, mi.wordId)
        assertEquals(listOf("ni-iku"), mi.grammar.map { it.point.id })
        assertEquals(listOf("naru"), reader.words("高くなる").first().grammar.map { it.point.id })
    }

    @Test
    fun aGreetingWithItsPoliteEndingIsOneWord() {
        val reader = Reader { text, _ -> mapOf("ありがとう" to 70L, "ありがとうございます" to 71L)[text]?.let { LexiconMatch(it, "ありがとうございます") } }
        assertEquals(listOf(71L), reader.words("ありがとうございます。").filter { it.kind == WordKind.WORD }.map { it.wordId })
    }

    @Test
    fun aSetPhraseIsOneWordWithNoGrammarInside() {
        val reader = Reader { text, _ -> mapOf("ただいま" to 90L, "いってきます" to 91L, "来る" to 92L)[text]?.let { LexiconMatch(it, text) } }
        val tadaima = reader.words("ただいま！").first()
        assertEquals(90L, tadaima.wordId)
        val ittekimasu = reader.words("いってきます。").first()
        assertEquals(91L, ittekimasu.wordId)
        assertTrue(ittekimasu.grammar.isEmpty())
        // ～ないで来た is "came without", so 来た is its own word.
        assertEquals(92L, reader.words("食べないで来た。").first { it.surface == "来た" }.wordId)
    }

    @Test
    fun aTimeNounNoPointExplainsIsAWord() {
        val reader = Reader { text, _ -> mapOf("休み" to 80L, "日" to 81L)[text]?.let { LexiconMatch(it, "ひ") } }
        val hi = reader.words("休みの日").first { it.surface == "日" }
        assertEquals(WordKind.WORD, hi.kind)
        assertEquals(81L, hi.wordId)
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
        assertEquals(listOf("causative", "passive", "past"), w.conjugation)
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
        assertEquals(listOf("probably"), word("高かろう").conjugation)
    }

    @Test
    fun `a bare なきゃ is must at the end of a clause and if-not before more of it`() {
        assertEquals(listOf("must"), reader.words("もう行かなきゃ。")[1].conjugation)
        assertEquals(listOf("must"), reader.words("行かなきゃね")[0].conjugation)
        assertEquals(listOf("must"), reader.words("行かなきゃだめだ")[0].conjugation)
        assertEquals(listOf("negative", "conditional (if)"), reader.words("行かなきゃわからない")[0].conjugation)
        assertEquals(listOf("must"), reader.words("行かなきゃいけない")[0].conjugation)
        assertEquals(listOf("must"), reader.words("行かなきゃならない")[0].conjugation)
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
        // 二人 isn't read に・にん, so it's a word; 三時 is just さん・じ, so it's 三 and 時.
        val dictionary = mapOf("三" to LexiconMatch(30, "さん"), "時" to LexiconMatch(31, "じ"), "二人" to LexiconMatch(32, "ふたり"), "三時" to LexiconMatch(33, "さんじ"), "一回" to LexiconMatch(34, "いっかい"))
        val reader = Reader { text, _ -> dictionary[text] }
        assertEquals(listOf("三" to 30L, "時" to 31L), reader.words("三時に来る").take(2).map { it.surface to it.wordId })
        val pair = reader.words("二人").single()
        assertEquals(32L, pair.wordId)
        assertEquals(listOf(Ruby("二人", "ふたり")), pair.ruby)
        assertEquals(34L, reader.words("一回").single().wordId)
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
    fun grammarPiecesOfOnePointAreOneTap() {
        val words = reader.words("雨でした。").filter { it.kind != WordKind.PUNCTUATION }
        assertEquals(listOf("雨", "でした"), words.map { it.surface })
        assertEquals(listOf(GrammarUse(grammarPoint("desu")!!, 1, 1)), words[1].grammar)
        // Different points stay apart, and the points after a joined piece still find their words.
        val apart = reader.words("学生だったんです。").filter { it.kind != WordKind.PUNCTUATION }
        assertEquals(listOf("学生", "だった", "んです"), apart.map { it.surface })
        assertEquals(listOf(2 to 2), apart[2].grammar.map { it.first to it.last })
        assertEquals(listOf("だっ", "たら"), reader.words("雨だったら").drop(1).map { it.surface })
        // A pattern spread over a word and a particle still spans both.
        val go = reader.words("食べに行く")
        assertEquals(listOf(0 to 2), go[1].grammar.map { it.first to it.last })
    }

    @Test
    fun paragraphs() {
        assertEquals(2, reader.read("猫が好きです。\n\n本を読む。").size)
    }

    @Test
    fun aPointKnowsWhereItStartsInsideItsWord() {
        val words = reader.words("雨が降ったら。")
        val i = words.indexOfFirst { it.surface == "降ったら" }
        // The verb is the word; only たら is ～たら.
        assertEquals(2..3, words[i].grammarChars(i, setOf("tara")))
        assertNull(words[i].grammarChars(i, setOf("te-iru")))
        // A particle on its own is all grammar.
        val k = words.indexOfFirst { it.surface == "が" }
        assertEquals(0..0, words[k].grammarChars(k, setOf("ga")))
        // And the app reads back what the tools wrote.
        assertEquals(listOf(words), StoryText.parse(StoryText.write(mapOf("a/b" to listOf(words))).values.single()).getValue("a/b"))
    }
}
