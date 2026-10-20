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

/**
 * A page before a set, on what's new in it, shown once as the set comes in:
 * the vowels before the first row, ゛ before the first voiced row, and so on.
 */
enum class KanaLesson { VOWELS, DAKUTEN, HANDAKUTEN, SMALL_YA, SMALL_TSU, KATAKANA, LONG_VOWEL, BORROWED }

/** Kana learned together: a row of the chart, or two rows of combinations, after its [lesson] if it has one. */
data class KanaSet(val kana: List<String>, val script: Script, val lesson: KanaLesson? = null)

/** Every kana unit, how a text splits into them, and the course that teaches them. */
object KanaCourse {
    /** The chart's rows: the vowels, then a consonant before each. */
    private val basic = listOf(
        listOf("あ" to "a", "い" to "i", "う" to "u", "え" to "e", "お" to "o"),
        listOf("か" to "ka", "き" to "ki", "く" to "ku", "け" to "ke", "こ" to "ko"),
        listOf("さ" to "sa", "し" to "shi", "す" to "su", "せ" to "se", "そ" to "so"),
        listOf("た" to "ta", "ち" to "chi", "つ" to "tsu", "て" to "te", "と" to "to"),
        listOf("な" to "na", "に" to "ni", "ぬ" to "nu", "ね" to "ne", "の" to "no"),
        listOf("は" to "ha", "ひ" to "hi", "ふ" to "fu", "へ" to "he", "ほ" to "ho"),
        listOf("ま" to "ma", "み" to "mi", "む" to "mu", "め" to "me", "も" to "mo"),
        listOf("や" to "ya", "ゆ" to "yu", "よ" to "yo"),
        listOf("ら" to "ra", "り" to "ri", "る" to "ru", "れ" to "re", "ろ" to "ro"),
        listOf("わ" to "wa", "を" to "o", "ん" to "n"),
    )

    /** The rows that take ゛, then the one that takes ゜. */
    private val voiced = listOf(
        listOf("が" to "ga", "ぎ" to "gi", "ぐ" to "gu", "げ" to "ge", "ご" to "go"),
        listOf("ざ" to "za", "じ" to "ji", "ず" to "zu", "ぜ" to "ze", "ぞ" to "zo"),
        listOf("だ" to "da", "ぢ" to "ji", "づ" to "zu", "で" to "de", "ど" to "do"),
        listOf("ば" to "ba", "び" to "bi", "ぶ" to "bu", "べ" to "be", "ぼ" to "bo"),
        listOf("ぱ" to "pa", "ぴ" to "pi", "ぷ" to "pu", "ぺ" to "pe", "ぽ" to "po"),
    )

    /** Each kana that ends in i, with small ゃ ゅ ょ. */
    private val combined = listOf(
        listOf("きゃ" to "kya", "きゅ" to "kyu", "きょ" to "kyo"),
        listOf("しゃ" to "sha", "しゅ" to "shu", "しょ" to "sho"),
        listOf("ちゃ" to "cha", "ちゅ" to "chu", "ちょ" to "cho"),
        listOf("にゃ" to "nya", "にゅ" to "nyu", "にょ" to "nyo"),
        listOf("ひゃ" to "hya", "ひゅ" to "hyu", "ひょ" to "hyo"),
        listOf("みゃ" to "mya", "みゅ" to "myu", "みょ" to "myo"),
        listOf("りゃ" to "rya", "りゅ" to "ryu", "りょ" to "ryo"),
        listOf("ぎゃ" to "gya", "ぎゅ" to "gyu", "ぎょ" to "gyo"),
        listOf("じゃ" to "ja", "じゅ" to "ju", "じょ" to "jo"),
        listOf("びゃ" to "bya", "びゅ" to "byu", "びょ" to "byo"),
        listOf("ぴゃ" to "pya", "ぴゅ" to "pyu", "ぴょ" to "pyo"),
    )

    /** Katakana spellings for sounds Japanese borrowed, with no hiragana twin, a set a row. */
    private val loanSounds = listOf(
        listOf("ファ" to "fa", "フィ" to "fi", "フェ" to "fe", "フォ" to "fo", "ヴ" to "vu"),
        listOf("ティ" to "ti", "ディ" to "di", "トゥ" to "tu", "ドゥ" to "du"),
        listOf("シェ" to "she", "チェ" to "che", "ジェ" to "je", "ウィ" to "wi", "ウェ" to "we", "ウォ" to "wo"),
    )

    private val borrowed = loanSounds.flatten().map { it.first }.toSet()

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

    /**
     * The course, a set a day: hiragana's rows, then its ゛ and ゜ rows, so a
     * voiced kana is learned as a plain one with a mark, not a new shape;
     * then its combinations, two rows a set, then っ. Then katakana the same
     * way, then ッ and ー, then its spellings for borrowed sounds. Each new
     * idea comes after a lesson.
     */
    val sets: List<KanaSet> = buildList {
        for (script in Script.entries) {
            val hiragana = script == Script.HIRAGANA
            fun set(kana: List<Pair<String, String>>, lesson: KanaLesson?) =
                add(KanaSet(kana.map { (text, _) -> if (hiragana) text else Kana.toKatakana(text) }, script, lesson))
            basic.forEachIndexed { i, row -> set(row, if (i > 0) null else if (hiragana) KanaLesson.VOWELS else KanaLesson.KATAKANA) }
            voiced.forEachIndexed { i, row ->
                set(row, if (!hiragana) null else if (i == 0) KanaLesson.DAKUTEN else if (i == voiced.lastIndex) KanaLesson.HANDAKUTEN else null)
            }
            combined.chunked(2).forEachIndexed { i, rows -> set(rows.flatten(), if (hiragana && i == 0) KanaLesson.SMALL_YA else null) }
            if (hiragana) {
                add(KanaSet(listOf("っ"), script, KanaLesson.SMALL_TSU))
            } else {
                add(KanaSet(listOf("ッ", "ー"), script, KanaLesson.LONG_VOWEL))
                loanSounds.forEachIndexed { i, row -> add(KanaSet(row.map { it.first }, script, if (i == 0) KanaLesson.BORROWED else null)) }
            }
        }
    }

    /** Every unit, in the course's order, which is the chart's: hiragana, then katakana. */
    val units: List<KanaUnit> = buildList {
        for (script in Script.entries) {
            for (group in listOf(basic, voiced, combined)) {
                for ((hiragana, romaji) in group.flatten()) {
                    val text = if (script == Script.HIRAGANA) hiragana else Kana.toKatakana(hiragana)
                    add(KanaUnit(text, script, romaji, note = notes[text]))
                }
            }
            if (script == Script.HIRAGANA) {
                add(KanaUnit("っ", script, "small tsu", mark = true, note = notes["っ"]))
            } else {
                add(KanaUnit("ッ", script, "small tsu", mark = true, note = notes["ッ"]))
                add(KanaUnit("ー", script, "long vowel", mark = true, note = notes["ー"]))
                for ((text, romaji) in loanSounds.flatten()) add(KanaUnit(text, script, romaji))
            }
        }
    }

    private val byText = units.associateBy { it.text }

    fun unit(text: String): KanaUnit? = byText[text]

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

    /** The kana [text] is built on: か for が, は for ぱ, き for きゃ, ぎ for ぎゃ, フ for ファ; null for one that isn't. */
    fun base(text: String): String? {
        val unit = byText[text] ?: return null
        if (unit.mark) return null
        return if (text.length == 2) text.substring(0, 1) else KanaLooks.dakuten(text)
    }

    /**
     * How a voiced or combined kana is built, in a line for its intro:
     * "フ with ゛ · fu → bu", "き + small ゃ · kya". Null for the rest.
     */
    fun built(text: String): String? {
        val unit = byText[text] ?: return null
        val base = base(text)?.let { byText[it] } ?: return null
        if (text.length == 2) return "${base.text} + small ${text.substring(1)} · ${unit.romaji}"
        return "${base.text} with ${mark(text)} · ${base.romaji} → ${unit.romaji}"
    }

    /** ゜ for a kana that has one (ぱ), else ゛. */
    private fun mark(text: String) =
        if (java.text.Normalizer.normalize(text, java.text.Normalizer.Form.NFD).endsWith("\u309A")) "゜" else "゛"

    /**
     * A set's first line on its intro: its row and the rule the row follows,
     * worked out from the chart, so a kana that bends it is named: "The k
     * row: k + each vowel.", "The s row: s + each vowel, except し, said
     * shi.", "The k row with ゛: k becomes g.", "き and し with small ゃ ゅ ょ."
     */
    fun introLine(set: KanaSet): String {
        val units = set.kana.mapNotNull { byText[it] }
        if (units.all { it.mark }) {
            return units.joinToString(" and ") { if (it.text == "ー") "the dash ー" else "small ${it.text}" }.replaceFirstChar { it.uppercase() } + "."
        }
        val first = units.first()
        val base = base(first.text)?.let { byText[it] }
        val line = when {
            units.any { it.text in borrowed } -> "Sounds from other languages"
            first.text.length == 2 ->
                "${units.map { it.text.substring(0, 1) }.distinct().joinToString(" and ")} with small ${units.map { it.text.substring(1) }.distinct().joinToString(" ")}"
            base != null -> {
                val from = base.romaji.dropLast(1)
                val to = first.romaji.dropLast(1)
                "The $from row with ${mark(first.text)}: $from becomes $to" + except(units.filter { it.romaji != to + byText.getValue(base(it.text)!!).romaji.last() })
            }
            first.romaji.length == 1 -> "The five vowels"
            else -> {
                val consonant = first.romaji.dropLast(1)
                val n = units.find { it.romaji == "n" }
                val row = units.filter { it != n }
                val odd = except(row.filter { it.romaji != consonant + it.romaji.last() })
                when {
                    n != null -> "The $consonant row, which keeps only ${row.joinToString(" and ") { it.text }}, then ${n.text}"
                    row.size == 5 -> "The $consonant row: $consonant + each vowel$odd"
                    else -> "The $consonant row: $consonant + ${row.map { it.romaji.last() }.let { v -> v.dropLast(1).joinToString(", ") + " and " + v.last() }}$odd"
                }
            }
        }
        return "$line. Tap one to hear it."
    }

    /** ", except し, said shi", for the kana in a row that don't follow it. */
    private fun except(odd: List<KanaUnit>) =
        if (odd.isEmpty()) "" else ", except " + odd.joinToString(", and ") { "${it.text}, said ${it.romaji}" }
}

/** A kana easy to take for [like], met already or in the same set, and how to tell them apart. */
data class KanaLookNote(val kana: String, val like: List<String>, val tell: String)

/** Kana that are easy to mistake for each other, for choosing wrong answers, and the worst of them pointed out as they come in. */
object KanaLooks {
    /**
     * The worst lookalikes, each told apart on the intro of the set that
     * brings in the later one, whatever row the other is in (Daniel,
     * 2026-10-20: ぬ and め differ only in a loop, ね and れ, る and ろ).
     */
    private val notes = listOf(
        KanaLookNote("お", listOf("あ"), "お has a short stroke at the top right; あ doesn't."),
        KanaLookNote("さ", listOf("き"), "き has two crossbars; さ has one."),
        KanaLookNote("ち", listOf("さ"), "Their curves face opposite ways: さ's opens to the right, ち's to the left."),
        KanaLookNote("め", listOf("ぬ"), "ぬ ends in a loop; め doesn't."),
        KanaLookNote("り", listOf("い"), "In い, the left stroke is the longer; in り, the right one."),
        KanaLookNote("れ", listOf("ね"), "ね ends in a loop; れ kicks out to the right."),
        KanaLookNote("ろ", listOf("る"), "る ends in a loop; ろ doesn't."),
        KanaLookNote("わ", listOf("ね", "れ"), "ね ends in a loop, れ kicks out, and わ curves round."),
        KanaLookNote("ツ", listOf("シ"), "ツ's short strokes sit on top, and its long one sweeps down; シ's sit on the left, and its long one sweeps up."),
        KanaLookNote("タ", listOf("ク"), "タ is ク with one more stroke, across the middle."),
        KanaLookNote("ヌ", listOf("ス"), "ヌ's short stroke cuts across the long one; ス's grows out of it."),
        KanaLookNote("マ", listOf("ア"), "ア ends in a long stroke down to the left; マ, in a short tick to the right."),
        KanaLookNote("ユ", listOf("コ"), "ユ's bottom line runs out past its side; コ's stops at the corner."),
        KanaLookNote("ン", listOf("ソ"), "ソ's long stroke sweeps down from the top; ン's sweeps up from the bottom."),
        KanaLookNote("ワ", listOf("ウ"), "ウ has a short stroke on top; ワ doesn't."),
    )

    /** The notes for the new kana in [texts], in their order. */
    fun notesFor(texts: List<String>): List<KanaLookNote> = notes.filter { it.kana in texts }.sortedBy { texts.indexOf(it.kana) }

    private val groups = listOf(
        "さちき", "ぬめの", "ねれわ", "ぬね", "るろ", "はほけ", "いり", "こにた", "あおめ", "うつら", "しつ", "そて", "くへ",
        "まも", "ゆよ", "んそ", "みよ", "なた", "ふう",
        "シツソン", "ソンリノ", "クケタワ", "フヌス", "ウワラフ", "コユロ", "アマヤ", "チテナ", "セヒ", "ノメ", "オホ",
        "エニコ", "ルレ", "ヲラヨ", "キチモ", "ハル", "ヘハ", "ヨユ", "ミシ",
    )

    private val alikeOf: Map<String, Set<String>> = buildMap<String, MutableSet<String>> {
        for (group in groups) for (a in group) for (b in group) if (a != b) getOrPut(a.toString()) { mutableSetOf() }.add(b.toString())
    }

    /**
     * Whether [a] and [b] are easy to mix up: a lookalike pair, a kana and
     * itself with ゛ or ゜ (は and ば), the two marked forms (ば and ぱ), or
     * two combinations whose kana are alike that way (しゃ and じゃ, きゅ and きょ).
     */
    fun alike(a: String, b: String): Boolean {
        if (a == b) return false
        if (a.length == 2 && b.length == 2) return sameOrAlike(a.substring(0, 1), b.substring(0, 1)) && sameOrAlike(full(a[1]), full(b[1]))
        return single(a, b)
    }

    private fun single(a: String, b: String): Boolean {
        val baseA = dakuten(a)
        val baseB = dakuten(b)
        return alikeOf[a]?.contains(b) == true || baseA == b || baseB == a || (baseA != null && baseA == baseB)
    }

    private fun sameOrAlike(a: String, b: String) = a == b || single(a, b)

    /** A small kana's full size (ゅ is ゆ), for comparing combinations. */
    private fun full(c: Char): String = (SMALL.indexOf(c).takeIf { it >= 0 }?.let { FULL[it] } ?: c).toString()

    private const val SMALL = "ぁぃぅぇぉゃゅょァィゥェォャュョ"
    private const val FULL = "あいうえおやゆよアイウエオヤユヨ"

    fun lookalikes(text: String): Set<String> = alikeOf[text].orEmpty()

    /** か for が, は for ぱ: the kana without its voicing marks, if it has them. */
    fun dakuten(text: String): String? {
        if (text.length != 1) return null
        val decomposed = java.text.Normalizer.normalize(text, java.text.Normalizer.Form.NFD)
        return decomposed.takeIf { it.length > 1 }?.substring(0, 1)
    }
}
