package ca.danielpower.jstudy.core

/**
 * Repairs to tokens the tokenizer gets wrong in everyday speech, before the
 * reader puts them into words. Each is a pattern IPADIC misreads the same way
 * every time, so a story can be written naturally rather than around it.
 * ReadingTest has an example of each.
 */
internal object Tokens {
    /** Verbs a quote goes with. */
    private val SAYING = setOf("言う", "聞く", "思う", "話す", "書く")

    fun repair(tokens: List<Morpheme>): List<Morpheme> {
        val out = ArrayList<Morpheme>(tokens.size + 2)
        var i = 0
        while (i < tokens.size) {
            val split = split(tokens, i)
            if (split != null) {
                out += split.first
                i += split.second
                continue
            }
            out += word(tokens[i], tokens.getOrNull(i - 1), tokens.getOrNull(i + 1), tokens.getOrNull(i - 2))
            i++
        }
        return out
    }

    /** A token read as the wrong word, given the tokens around it. */
    private fun word(t: Morpheme, prev: Morpheme?, next: Morpheme?, before: Morpheme?): Morpheme = when {
        // 行っ(た) is 行く, "went", far more often than 行う, "carried out", which the tokenizer sometimes picks.
        t.base == "行う" && t.surface == "行っ" -> t.copy(base = "行く", reading = "いっ", type = "五段・カ行促音便")
        // 勝っ(たら) is 勝つ, "win", not 勝る, "surpass", which the tokenizer picks opening a sentence.
        t.base == "勝る" && t.surface == "勝っ" -> t.copy(base = "勝つ", reading = "かっ", type = "五段・タ行")
        // 後 after の (仕事の後で) is あと; the tokenizer says のち.
        t.surface == "後" && t.pos2 == "非自立" -> t.copy(reading = "あと")
        // 方 after あの, この, その or どの is a person (あの方), not ～のほう.
        t.surface == "方" && prev?.pos == "連体詞" && prev.base in setOf("あの", "この", "その", "どの") ->
            t.copy(pos2 = "一般", pos3 = "*", reading = "かた")
        // いい standing alone (すごい！いい！) is "good", not the stem of 言う.
        t.surface == "いい" && t.base == "いう" && (prev == null || prev.pos == "記号") && (next == null || next.pos == "記号" || next.pos2 == "終助詞") ->
            Morpheme("いい", "形容詞", "自立", "*", "基本形", "形容詞・イイ", "いい", "いい")
        // よし standing alone (よし、行こう！) is "all right!", not old-fashioned よい.
        t.surface == "よし" && t.pos == "形容詞" && (prev == null || prev.pos == "記号") && (next == null || next.pos == "記号") ->
            Morpheme("よし", "感動詞", "*", "*", "*", "*", "よし", "よし")
        // ない straight after は, も or が (ことはない, お金もない) is the adjective "there isn't", not the negative
        // ending, but ではない and じゃない are "isn't".
        t.pos == "助動詞" && t.base == "ない" && prev?.pos == "助詞" && prev.surface in setOf("は", "も", "が") && before?.base != "で" ->
            t.copy(pos = "形容詞", pos2 = "自立", type = "形容詞・アウオ段")
        // すぎ after a stem (大きすぎ！, 練習しすぎ) is ～すぎる cut short, not the noun 過ぎ.
        t.surface == "すぎ" && t.pos == "名詞" && prev != null &&
            ((prev.pos == "形容詞" && prev.form == "ガル接続") || (prev.pos == "動詞" && prev.form == "連用形")) ->
            Morpheme("すぎ", "動詞", "非自立", "*", "連用形", "一段", "すぎる", "すぎ")
        else -> t
    }

    /** Tokens at [i] the tokenizer split wrong, as they should be, and how many of them that replaces. */
    private fun split(tokens: List<Morpheme>, i: Int): Pair<List<Morpheme>, Int>? {
        val t = tokens[i]
        val next = tokens.getOrNull(i + 1)
        val after = tokens.getOrNull(i + 2)
        return when {
            // ありがとうって is ありがとう and って, not ありがと and 売って.
            t.surface == "ありがと" && next?.surface == "うっ" && next.base == "うる" && after?.surface == "て" ->
                listOf(
                    Morpheme("ありがとう", "感動詞", "*", "*", "*", "*", "ありがとう", "ありがとう"),
                    Morpheme("って", "助詞", "格助詞", "連語", "*", "*", "って", "って"),
                ) to 3
            // ～てたんだ is ～てた and ん, not て and 歎: the い of ～ていた dropped.
            t.pos == "助詞" && t.pos2 == "接続助詞" && t.base in setOf("て", "で") && next?.surface == "たん" && next.pos == "名詞" ->
                listOf(
                    Morpheme(t.surface, "動詞", "非自立", "*", "連用形", "一段", t.surface + "る", t.surface),
                    Morpheme("た", "助動詞", "*", "*", "基本形", "特殊・タ", "た", "た"),
                    Morpheme("ん", "名詞", "非自立", "一般", "*", "*", "ん", "ん"),
                ) to 2
            // 来られる is 来る's "can come" (or passive), not a form of 来る read きたる, "coming".
            t.surface == "来ら" && t.base == "来る" && next?.pos == "動詞" && next.pos2 == "接尾" && next.base == "れる" ->
                listOf(
                    Morpheme("来", "動詞", "自立", "*", "未然形", "カ変・来ル", "来る", "こ"),
                    next.copy(surface = "ら" + next.surface, base = "られる", reading = "ら" + next.readingOrSurface),
                ) to 2
            // ～たって after a past, ending the sentence or before 聞く or 言う, is ～た and the quoting って ("they say it changed").
            t.surface == "たって" && t.pos2 == "接続助詞" && tokens.getOrNull(i - 1)?.form == "連用タ接続" &&
                (next == null || next.pos == "記号" || next.pos2 == "終助詞" || next.base in SAYING) ->
                listOf(
                    Morpheme("た", "助動詞", "*", "*", "基本形", "特殊・タ", "た", "た"),
                    Morpheme("って", "助詞", "格助詞", "連語", "*", "*", "って", "って"),
                ) to 1
            else -> null
        }
    }
}
