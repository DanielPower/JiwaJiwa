package ca.danielpower.jstudy.core

enum class Script { HIRAGANA, KATAKANA }

/** Which kana you could already read when you started (setup's Kana page), so they're never taught. */
enum class KanaKnown(val value: String) {
    BOTH("both"),
    HIRAGANA("hiragana"),
    NONE("none"),
    ;

    fun covers(script: Script) = when (this) {
        BOTH -> true
        HIRAGANA -> script == Script.HIRAGANA
        NONE -> false
    }

    companion object {
        /** Unset is both: installs from before kana came back already read them. */
        fun of(value: String?) = entries.firstOrNull { it.value == value } ?: BOTH
    }
}

/**
 * One kana as it's taught and read: a single character, a combination such
 * as きゃ, or a mark such as っ and ー, whose sound depends on its neighbours.
 */
data class KanaUnit(
    val text: String,
    val script: Script,
    /** How it's read, in Hepburn romaji. For a mark, what it does. */
    val romaji: String,
    val mark: Boolean = false,
    val note: String? = null,
)

/** Every kana unit, how a text splits into them, and the order the stories need them in. */
object KanaCourse {
    private val basic = listOf(
        "あ" to "a", "い" to "i", "う" to "u", "え" to "e", "お" to "o",
        "か" to "ka", "き" to "ki", "く" to "ku", "け" to "ke", "こ" to "ko",
        "さ" to "sa", "し" to "shi", "す" to "su", "せ" to "se", "そ" to "so",
        "た" to "ta", "ち" to "chi", "つ" to "tsu", "て" to "te", "と" to "to",
        "な" to "na", "に" to "ni", "ぬ" to "nu", "ね" to "ne", "の" to "no",
        "は" to "ha", "ひ" to "hi", "ふ" to "fu", "へ" to "he", "ほ" to "ho",
        "ま" to "ma", "み" to "mi", "む" to "mu", "め" to "me", "も" to "mo",
        "や" to "ya", "ゆ" to "yu", "よ" to "yo",
        "ら" to "ra", "り" to "ri", "る" to "ru", "れ" to "re", "ろ" to "ro",
        "わ" to "wa", "を" to "o", "ん" to "n",
    )

    private val voiced = listOf(
        "が" to "ga", "ぎ" to "gi", "ぐ" to "gu", "げ" to "ge", "ご" to "go",
        "ざ" to "za", "じ" to "ji", "ず" to "zu", "ぜ" to "ze", "ぞ" to "zo",
        "だ" to "da", "ぢ" to "ji", "づ" to "zu", "で" to "de", "ど" to "do",
        "ば" to "ba", "び" to "bi", "ぶ" to "bu", "べ" to "be", "ぼ" to "bo",
        "ぱ" to "pa", "ぴ" to "pi", "ぷ" to "pu", "ぺ" to "pe", "ぽ" to "po",
    )

    private val combined = listOf(
        "きゃ" to "kya", "きゅ" to "kyu", "きょ" to "kyo",
        "しゃ" to "sha", "しゅ" to "shu", "しょ" to "sho",
        "ちゃ" to "cha", "ちゅ" to "chu", "ちょ" to "cho",
        "にゃ" to "nya", "にゅ" to "nyu", "にょ" to "nyo",
        "ひゃ" to "hya", "ひゅ" to "hyu", "ひょ" to "hyo",
        "みゃ" to "mya", "みゅ" to "myu", "みょ" to "myo",
        "りゃ" to "rya", "りゅ" to "ryu", "りょ" to "ryo",
        "ぎゃ" to "gya", "ぎゅ" to "gyu", "ぎょ" to "gyo",
        "じゃ" to "ja", "じゅ" to "ju", "じょ" to "jo",
        "びゃ" to "bya", "びゅ" to "byu", "びょ" to "byo",
        "ぴゃ" to "pya", "ぴゅ" to "pyu", "ぴょ" to "pyo",
    )

    /** Katakana spellings for sounds Japanese borrowed, with no hiragana twin. */
    private val loanSounds = listOf(
        "ティ" to "ti", "ディ" to "di", "トゥ" to "tu", "ドゥ" to "du",
        "ファ" to "fa", "フィ" to "fi", "フェ" to "fe", "フォ" to "fo",
        "ウィ" to "wi", "ウェ" to "we", "ウォ" to "wo",
        "シェ" to "she", "チェ" to "che", "ジェ" to "je",
        "ヴ" to "vu",
    )

    private val notes = mapOf(
        "ぢ" to "Sounds like じ. Rare: mostly in words like はなぢ.",
        "づ" to "Sounds like ず. Rare: mostly in words like つづく.",
        "を" to "Said \"o\". Only used to mark an object.",
        "ん" to "Said \"n\", or \"m\" before b, m and p.",
        "は" to "Said \"wa\" when it marks the topic: わたしは.",
        "へ" to "Said \"e\" when it means \"to\": にほんへ.",
        "っ" to "A small つ doubles the next sound: きって is kitte.",
        "ッ" to "A small ツ doubles the next sound: ベッド is beddo.",
        "ー" to "Makes the vowel before it long: コーヒー is koohii.",
        "ヲ" to "Said \"o\". Hardly ever seen.",
        "ヂ" to "Sounds like ジ. Very rare.",
        "ヅ" to "Sounds like ズ. Very rare.",
    )

    /** Every unit, in the order of the kana chart: hiragana, then katakana. */
    val units: List<KanaUnit> = buildList {
        for (script in Script.entries) {
            for (group in listOf(basic, voiced, combined)) {
                for ((hiragana, romaji) in group) {
                    val text = if (script == Script.HIRAGANA) hiragana else Kana.toKatakana(hiragana)
                    add(KanaUnit(text, script, romaji, note = notes[text]))
                }
            }
            if (script == Script.HIRAGANA) {
                add(KanaUnit("っ", script, "small tsu", mark = true, note = notes["っ"]))
            } else {
                add(KanaUnit("ッ", script, "small tsu", mark = true, note = notes["ッ"]))
                add(KanaUnit("ー", script, "long vowel", mark = true, note = notes["ー"]))
                for ((text, romaji) in loanSounds) add(KanaUnit(text, script, romaji))
            }
        }
    }

    private val byText = units.associateBy { it.text }
    private val chartIndex = units.withIndex().associate { (i, u) -> u.text to i }

    fun unit(text: String): KanaUnit? = byText[text]

    /** Where [text] sits on the chart, for keeping ties in chart order. */
    fun chartIndex(text: String): Int = chartIndex[text] ?: Int.MAX_VALUE

    /**
     * [text] split into kana units, longest first so きょう is きょ and う,
     * with anything else (kanji, punctuation, a lone small ぁ) as its own
     * one-character piece.
     */
    fun segments(text: String): List<String> {
        val out = ArrayList<String>()
        var i = 0
        while (i < text.length) {
            if (i + 1 < text.length && text.substring(i, i + 2) in byText) {
                out.add(text.substring(i, i + 2))
                i += 2
            } else {
                out.add(text.substring(i, i + 1))
                i++
            }
        }
        return out
    }

    /** The units it takes to read [text]. */
    fun unitsIn(text: String): List<KanaUnit> = segments(text).mapNotNull { byText[it] }

    /**
     * The order to teach kana in, from the text the stories show, chapter by
     * chapter along the path: the ones most used in the first [window]
     * chapters first, so romaji leaves the early stories as fast as it can,
     * then the rest in the order the stories meet them, then the rest of the
     * chart.
     */
    fun order(chapters: List<String>, window: Int = 40): List<String> {
        val counts = HashMap<String, Int>()
        val first = HashMap<String, Int>()
        chapters.forEachIndexed { n, text ->
            for (unit in unitsIn(text)) {
                if (n < window) counts.merge(unit.text, 1, Int::plus)
                first.putIfAbsent(unit.text, n)
            }
        }
        return units.map { it.text }.sortedWith(
            compareByDescending<String> { counts[it] ?: 0 }
                .thenBy { first[it] ?: Int.MAX_VALUE }
                .thenBy { chartIndex(it) },
        )
    }

    /**
     * [order] in sets of [size] to learn together, keeping kana that look
     * alike in different sets: learned side by side they get muddled, so
     * each waits for a later set.
     */
    fun sets(order: List<String>, size: Int = SET_SIZE): List<List<String>> {
        val left = order.toMutableList()
        val out = ArrayList<List<String>>()
        while (left.isNotEmpty()) {
            val set = ArrayList<String>()
            val it = left.iterator()
            while (it.hasNext() && set.size < size) {
                val text = it.next()
                if (set.none { KanaLooks.alike(it, text) }) {
                    set.add(text)
                    it.remove()
                }
            }
            out.add(set)
        }
        return out
    }

    const val SET_SIZE = 5
}

/** Kana that are easy to mistake for each other, for choosing wrong answers and keeping them apart when teaching. */
object KanaLooks {
    private val groups = listOf(
        "さちき", "ぬめの", "ねれわ", "るろ", "はほけ", "いり", "こにた", "あおめ", "うつら", "しつ", "そて", "くへ",
        "まも", "ゆよ", "んそ", "みよ", "なた", "ふう",
        "シツ", "ソンリノ", "クケタワ", "フヌス", "ウワラフ", "コユロ", "アマヤ", "チテナ", "セヒ", "ノメ", "オホ",
        "エニコ", "ルレ", "ヲラヨ", "キチモ", "ハル", "ヘハ", "ヨユ", "ミシ",
    )

    private val alikeOf: Map<String, Set<String>> = buildMap<String, MutableSet<String>> {
        for (group in groups) for (a in group) for (b in group) if (a != b) getOrPut(a.toString()) { mutableSetOf() }.add(b.toString())
    }

    fun alike(a: String, b: String): Boolean = alikeOf[a]?.contains(b) == true || dakuten(a) == b || dakuten(b) == a

    fun lookalikes(text: String): Set<String> = alikeOf[text].orEmpty()

    /** か for が, は for ぱ: the kana without its voicing marks, if it has them. */
    fun dakuten(text: String): String? {
        if (text.length != 1) return null
        val decomposed = java.text.Normalizer.normalize(text, java.text.Normalizer.Form.NFD)
        return decomposed.takeIf { it.length > 1 }?.substring(0, 1)
    }
}
