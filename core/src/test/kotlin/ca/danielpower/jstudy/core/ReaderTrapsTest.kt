package ca.danielpower.jstudy.core

import org.junit.jupiter.api.Assumptions.assumeTrue
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Everyday Japanese the tokenizer once misread, read with the real
 * dictionary, so a story can say it without a pin or a reword. Needs the
 * dictionary built (python3 tools/dictionary/build_dictionary.py), so it's
 * skipped where it isn't.
 */
class ReaderTrapsTest {
    /** In [text], the word written [surface] has dictionary form [lemma] and reading [reading], and is the entry read [kana]. */
    private data class Case(val text: String, val surface: String, val lemma: String, val reading: String, val kana: String?, val grammar: String? = null)

    private val cases = listOf(
        Case("何時に起きた？", "何時", "何時", "なんじ", "なんじ"),
        Case("マンガを描いた。", "描いた", "描く", "かいた", "えがく"),
        Case("弦が切れた。", "弦", "弦", "げん", "げん"),
        Case("ギターを弾きたい。", "弾きたい", "弾く", "ひきたい", "ひく"),
        Case("ギターを弾きました。", "弾きました", "弾く", "ひきました", "ひく"),
        Case("ギターが弾けない。", "弾けない", "弾く", "ひけない", "ひく", "potential"),
        Case("ギターが弾ける人。", "弾ける", "弾く", "ひける", "ひく", "potential"),
        Case("明日は休めない。", "休めない", "休む", "やすめない", "やすむ", "potential"),
        Case("歌が聞けた。", "聞けた", "聞く", "きけた", "きく", "potential"),
        Case("絵が描けた。", "描けた", "描く", "かけた", "えがく", "potential"),
        Case("明日、来られますか？", "来られます", "来る", "こられます", "くる", "potential"),
        Case("勝ったら、ラーメンだ！", "勝ったら", "勝つ", "かったら", "かつ"),
        Case("ありがとうって言った。", "ありがとう", "ありがとう", "ありがとう", "ありがとう"),
        Case("よし、行こう！", "よし", "よし", "よし", "よし"),
        Case("いつか、行きたい。", "いつか", "いつか", "いつか", "いつか"),
        Case("すごい！いい！", "いい", "いい", "いい", "いい"),
        Case("はい！いいですよ！", "いいです", "いい", "いいです", "いい"),
        Case("会社に行ってたんだって。", "ん", "ん", "ん", null, "n-desu"),
        // One tap, the whole of ～かもしれない.
        Case("雨かもしれません。", "かもしれません", "かも", "かもしれません", null, "kamoshirenai"),
        Case("駅まで送りに行く。", "送り", "送る", "おくり", "おくる", "ni-iku"),
        Case("祭りに行く。", "祭り", "祭り", "まつり", "まつり"),
        Case("もう、終わり？", "終わり", "終わり", "おわり", "おわり"),
        Case("あの方は先生です。", "方", "方", "かた", "かた"),
        Case("日本に来た。", "日本", "日本", "にほん", "にほん"),
        Case("毎日、聞いてる。", "毎日", "毎日", "まいにち", "まいにち"),
        Case("「です、ます」は大事。", "ます", "ます", "ます", null, "masu"),
        Case("ご、ごめん。", "ご", "ご", "ご", null),
        Case("スマホを見た。", "スマホ", "スマホ", "すまほ", "スマホ"),
        Case("スマホで、写真を撮った。", "スマホ", "スマホ", "すまほ", "スマホ"),
        Case("スマホで、写真を撮った。", "で", "で", "で", null, "de"),
        // 何 said なん is still the word 何 (なに).
        Case("これは何ですか？", "何", "何", "なん", "なに"),
        Case("何の仕事？", "何", "何", "なん", "なに"),
        Case("何でもない。", "何", "何", "なん", "なに"),
        Case("何を食べる？", "何", "何", "なに", "なに"),
        Case("何か食べる？", "何", "何", "なに", "なに"),
        Case("アレックス、ゲーム好き？", "好き", "好き", "すき", "すき"),
        Case("行ったことはない。", "ことは", "こと", "ことは", null, "ta-koto-ga-aru"),
        Case("大きすぎ！", "すぎ", "すぎる", "すぎ", null, "sugiru"),
        Case("練習しすぎ。", "すぎ", "すぎる", "すぎ", null, "sugiru"),
        Case("部屋に入っちゃだめだよ。", "ちゃ", "ちゃ", "ちゃ", null, "te-wa-ikenai"),
        Case("静かに歩いた。", "に", "に", "に", null, "ku-adverb"),
        Case("楽しそうに歌った。", "に", "に", "に", null, "ku-adverb"),
        Case("ソラの笑う声。", "の", "の", "の", null, "no"),
        Case("店が変わったって聞いた。", "変わった", "変わる", "かわった", "かわる", "ta"),
        Case("店が変わったって聞いた。", "って", "って", "って", null, "tte"),
        Case("天気がよさそう。", "よさそう", "よい", "よさそう", "よい", "sou-looks"),
        Case("ビール、飲んじゃう。", "飲んじゃう", "飲む", "のんじゃう", "のむ", "te-shimau"),
    )

    @Test
    fun `the reader reads everyday Japanese it once got wrong`() {
        val root = File("..")
        assumeTrue(StoryTools.dictionary(root).exists(), "dictionary not built")
        StoryTools.open(root).use { tools ->
            val wrong = ArrayList<String>()
            for (c in cases) {
                val words = tools.reader.words(c.text)
                val w = words.firstOrNull { it.surface == c.surface } ?: fail("${c.text}: no word ${c.surface} in ${words.map { it.surface }}")
                val kana = w.wordId?.let { tools.lexicon.entry(it)?.kana }
                val got = "${w.lemma}/${w.reading}/$kana"
                if (got != "${c.lemma}/${c.reading}/${c.kana}") wrong += "${c.text} ${c.surface}: $got, wanted ${c.lemma}/${c.reading}/${c.kana}"
                if (c.grammar != null && w.grammar.none { it.point.id == c.grammar }) wrong += "${c.text} ${c.surface}: no ${c.grammar} in ${w.grammar.map { it.point.id }}"
                if (c.kana == null && w.kind == WordKind.WORD) wrong += "${c.text} ${c.surface}: counted as a word"
                if (c.kana != null && w.kind != WordKind.WORD) wrong += "${c.text} ${c.surface}: read as ${w.kind}, not a word"
                if (w.kind == WordKind.GRAMMAR) assertTrue(w.grammar.isNotEmpty(), "${c.text} ${c.surface}: no grammar point explains it")
            }
            assertEquals(emptyList(), wrong)
        }
    }

    @Test
    fun `the recorded voice says 何 the way the reader reads it`() {
        val root = File("..")
        assumeTrue(StoryTools.dictionary(root).exists(), "dictionary not built")
        StoryTools.open(root).use { tools ->
            fun said(text: String) = Narration.lines(listOf(tools.reader.words(text)), listOf(null)).single().text
            assertEquals("これはなんですか？", said("これは何ですか？"))
            assertEquals("なにを食べる？", said("何を食べる？"))
        }
    }
}
