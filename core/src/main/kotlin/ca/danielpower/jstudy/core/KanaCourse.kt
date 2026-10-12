package ca.danielpower.jstudy.core

enum class Script { HIRAGANA, KATAKANA }

/** Which kana you could already read when you started, so they're never taught. */
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
        fun of(value: String?) = entries.firstOrNull { it.value == value } ?: BOTH
    }
}

/**
 * One kana taught as a card: a single character, a combination such as きゃ,
 * or a mark such as っ and ー.
 */
data class KanaUnit(
    /**
     * Stands in for a word id on the unit's card. Always negative, so it
     * never meets a dictionary word, and taken from the characters, so it
     * stays put if the teaching order changes.
     */
    val id: Long,
    val text: String,
    val script: Script,
    /** How it's read, or a short name for a mark. */
    val romaji: String,
    /** The same sound in the other script, if it has one. */
    val twin: String?,
    val note: String? = null,
)

/**
 * Hiragana and katakana in teaching order. Each sound is taught in both
 * scripts back to back (あ then ア), so the pair is learned as one sound.
 */
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
        "わ" to "wa", "を" to "wo", "ん" to "n",
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
        "っ" to "Doubles the next consonant: きって is kitte.",
        "ッ" to "Doubles the next consonant: ベッド is beddo.",
        "ー" to "Makes the vowel before it long: コーヒー is koohii.",
        "ヲ" to "Said \"o\". Hardly ever seen.",
        "ヂ" to "Sounds like ジ. Very rare.",
        "ヅ" to "Sounds like ズ. Very rare.",
    )

    /** Every unit, in the order they're taught. */
    val units: List<KanaUnit> = buildList {
        for (group in listOf(basic, voiced, combined)) {
            for ((hiragana, romaji) in group) {
                val katakana = Kana.toKatakana(hiragana)
                add(unit(hiragana, Script.HIRAGANA, romaji, katakana))
                add(unit(katakana, Script.KATAKANA, romaji, hiragana))
            }
        }
        add(unit("っ", Script.HIRAGANA, "small tsu", "ッ"))
        add(unit("ッ", Script.KATAKANA, "small tsu", "っ"))
        add(unit("ー", Script.KATAKANA, "long vowel", null))
        for ((katakana, romaji) in loanSounds) add(unit(katakana, Script.KATAKANA, romaji, null))
    }

    private val byId = units.associateBy { it.id }
    private val byText = units.associateBy { it.text }

    private fun unit(text: String, script: Script, romaji: String, twin: String?) =
        KanaUnit(idOf(text), text, script, romaji, twin, notes[text])

    /** A unit's id from its characters: minus the code points packed into one number. */
    fun idOf(text: String): Long {
        require(text.length in 1..2)
        return -text.fold(0L) { acc, c -> acc * 0x10000 + c.code }
    }

    fun isKanaId(id: Long) = id < 0

    fun unit(id: Long): KanaUnit? = byId[id]

    /**
     * The units it takes to read [text], longest match first, so きょう needs
     * きょ and う. Kanji and anything not taught (small ぁ on its own, ゐ, ・)
     * are left out.
     */
    fun unitsIn(text: String): List<KanaUnit> {
        val out = ArrayList<KanaUnit>()
        var i = 0
        while (i < text.length) {
            val pair = if (i + 1 < text.length) byText[text.substring(i, i + 2)] else null
            if (pair != null) {
                out.add(pair)
                i += 2
                continue
            }
            byText[text.substring(i, i + 1)]?.let(out::add)
            i++
        }
        return out
    }

    /**
     * Whether all of [text]'s kana can be read, given what you knew at the
     * start and the units you've learned (their ids).
     */
    fun canRead(text: String, known: KanaKnown, learned: Set<Long>): Boolean =
        unitsIn(text).all { known.covers(it.script) || it.id in learned }

    /** The units still to teach, in order. */
    fun toTeach(known: KanaKnown): List<KanaUnit> = units.filter { !known.covers(it.script) }
}
