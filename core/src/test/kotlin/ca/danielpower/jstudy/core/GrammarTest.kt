package ca.danielpower.jstudy.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GrammarTest {
    private val reader = Reader { _, _ -> null }

    /** The grammar points in [text], by id, in order. */
    private fun points(text: String): List<String> =
        reader.words(text).flatMap { it.grammar }.map { it.point.id }.distinct()

    @Test
    fun `every point's own example sentence shows that point`() {
        val missing = Grammar.points.filter { it.id !in points(it.example) }
        assertTrue(missing.isEmpty(), "examples without their point: " + missing.joinToString { "${it.id} ${it.example} -> ${points(it.example)}" })
    }

    @Test
    fun `points have an id, a level and some text`() {
        assertEquals(Grammar.points.size, Grammar.points.map { it.id }.toSet().size)
        for (p in Grammar.points) {
            assertTrue(p.level in 1..5, p.id)
            assertTrue(listOf(p.pattern, p.meaning, p.note, p.example, p.exampleMeaning).none { it.isBlank() }, p.id)
        }
    }

    @Test
    fun `a pattern covers its particles rather than explaining them one by one`() {
        assertEquals(listOf("wo", "te-wa-ikenai"), points("写真を撮ってはいけません。"))
        assertEquals(listOf("ta-koto-ga-aru"), points("行ったことがない。"))
        assertEquals(listOf("nakute-mo-ii"), points("行かなくてもいい。"))
        assertEquals(listOf("naide-kudasai"), points("行かないでください。"))
        assertEquals(listOf("tokoro-ing"), points("読んでいるところです。"))
    }

    @Test
    fun `particles are told apart by how they're used`() {
        assertEquals(listOf("kara-from"), points("駅から歩く。"))
        assertEquals(listOf("kara-because"), points("疲れたから寝る。").filter { it != "ta" })
        assertEquals(listOf("te-kara"), points("食べてから出かける。"))
        assertEquals(listOf("ga"), points("猫がいる。"))
        assertEquals(listOf("ga-but"), points("寒いが行く。"))
        assertEquals(listOf("to"), points("友達と話す。"))
        assertEquals(listOf("to-omou"), points("行くと思う。"))
        assertEquals(listOf("to-if"), points("春になると暖かくなる。").filter { it != "naru" })
    }

    @Test
    fun `te links, asks casually, and gives way to the patterns built on it`() {
        assertEquals(listOf("te", "wo"), points("朝起きて、コーヒーを飲む。"))
        assertEquals(listOf("te"), points("ゆっくり食べて。"))
        assertEquals(listOf("te"), points("安くて、うまい。"))
        assertEquals(listOf("te"), points("静かで、いい。"))
        assertEquals(listOf("te-kudasai"), points("待ってください。"))
        assertEquals(listOf("te-iru"), points("食べている。"))
    }

    @Test
    fun `guesses and suggestions are told apart from です and ます`() {
        assertEquals(listOf("deshou"), points("いいでしょ？"))
        assertEquals(listOf("wa", "deshou"), points("明日は雨でしょう。"))
        assertEquals(listOf("deshou"), points("雨だろう。"))
        assertEquals(listOf("desu"), points("雨でした。"))
        assertEquals(listOf("ni", "mashou"), points("一緒に行きましょう。"))
        assertEquals(listOf("mashouka"), points("行きましょうか。"))
        assertEquals(listOf("volitional"), points("行こう。"))
    }

    @Test
    fun `purpose and change are found after the right words`() {
        assertEquals(listOf("wo", "ni-iku"), points("桜を見に行く。"))
        assertEquals(listOf("ni"), points("公園に行く。"))
        assertEquals(listOf("naru"), points("寒くなった。"))
        assertEquals(listOf("naru", "tai"), points("先生になりたい。"))
        assertEquals(listOf("no", "toki"), points("子供のとき"))
    }

    @Test
    fun `casual endings are grammar points`() {
        assertEquals(listOf("na-final"), points("大きいな。"))
        assertEquals(listOf("kana"), points("行くかな。"))
        assertEquals(listOf("jan"), points("いいじゃん。"))
        assertEquals(listOf("desu", "kke"), points("何だっけ？"))
        assertEquals(listOf("tte"), points("それって何？"))
        assertEquals(listOf("no-question"), points("行くの？"))
        assertEquals(listOf("mon"), points("眠いもん。"))
        assertEquals(listOf("volitional", "zo-ze"), points("行こうぜ。"))
    }

    @Test
    fun `an adverb's く is its own point, but not in くて, くない or くなる`() {
        assertEquals(listOf("ku-adverb"), points("早く起きる。"))
        assertEquals(listOf("te"), points("安くて、うまい。"))
        assertEquals(listOf("nai"), points("高くない。"))
        assertEquals(listOf("naru"), points("寒くなる。"))
        assertEquals(listOf("imperative"), points("行け！"))
        assertEquals(listOf("te-kudasai"), points("待ってください。"))
    }

    @Test
    fun `spoken contractions keep their point`() {
        assertEquals(listOf("te-iru"), points("読んでた。"))
        assertEquals(listOf("te-shimau", "no-question"), points("帰っちゃうの？"))
        assertEquals(listOf("kamoshirenai"), points("雨かも。"))
        assertEquals(listOf("nakereba"), points("行かなきゃいけない。"))
        assertEquals(listOf("nakereba"), points("もう行かなきゃ。"))
        assertEquals(listOf("ga", "n-desu"), points("これがいいんじゃない？"))
    }

    @Test
    fun `after a noun, 後で is ato-de and だって is tte`() {
        assertEquals(listOf("no", "ato-de", "wo", "ta"), points("仕事の後で、ラーメンを食べた。"))
        assertEquals(listOf("ga", "tte"), points("ユキが好きだって。"))
        assertEquals(listOf("あと"), reader.words("仕事の後で").filter { it.surface == "後" }.map { it.reading })
    }

    @Test
    fun `a conditional after another point keeps its own point`() {
        assertEquals(listOf("potential", "tara"), points("出られたら、うれしい。"))
        assertEquals(listOf("potential", "nakereba"), points("出られなきゃ。"))
    }

    @Test
    fun `られる on a る verb is can, on a う verb it's the passive`() {
        assertEquals(listOf("potential"), points("食べられる？"))
        assertEquals(listOf("potential"), points("食べれる？"))
        assertEquals(listOf("ni", "passive"), points("先生に呼ばれた。"))
        assertEquals(listOf("causative", "passive"), points("食べさせられた。"))
    }

    @Test
    fun `な before a noun, explaining の, and と after a noun`() {
        assertEquals(listOf("na", "desu", "ne"), points("きれいな部屋だね。"))
        assertEquals(listOf("na", "n-desu"), points("そうなんだ。"))
        assertEquals(listOf("ta", "n-desu", "kana"), points("忘れたのかな。"))
        assertEquals(listOf("no-nominal", "ga"), points("行くのが好き。"))
        assertEquals(listOf("to"), points("友達といる。"))
        assertEquals(listOf("to-quote"), points("「うん」と言った。").filter { it == "to-quote" })
    }

    @Test
    fun `before needs a verb in front of it`() {
        assertEquals(listOf("mae-ni"), points("寝る前に"))
        assertEquals(listOf("no"), points("駅の前に").filter { it != "ni" })
    }

    @Test
    fun `a pattern spans the words it's made of`() {
        val words = reader.words("ここで遊んじゃいけない。")
        val uses = words.flatMap { it.grammar }.filter { it.point.id == "te-wa-ikenai" }.distinct()
        assertEquals(1, uses.size)
        val use = uses[0]
        assertEquals("じゃいけない", words.subList(use.first, use.last + 1).joinToString("") { it.surface })
    }

    @Test
    fun `patterns inside a conjugated word are found too`() {
        val w = reader.words("飲んでしまった").single()
        assertEquals(listOf("te-shimau"), w.grammar.map { it.point.id })
        assertEquals(0, w.grammar[0].first)
        assertEquals(0, w.grammar[0].last)
    }
}
