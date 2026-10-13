package ca.danielpower.jstudy.core

/** A kanji's dictionary entry: KANJIDIC2's meanings and readings, its JLPT level, and its KanjiVG strokes. */
data class Kanji(
    val literal: String,
    val meanings: List<String>,
    /** On'yomi, in katakana. */
    val onyomi: List<String>,
    /** Kun'yomi, in hiragana. A dot marks where okurigana starts (い.きる); a dash marks a prefix or suffix. */
    val kunyomi: List<String>,
    val strokeCount: Int,
    /** School grade 1 to 6, 8 for the rest of the jōyō kanji, 9 or 10 for name kanji, or null. */
    val grade: Int?,
    /** 5 to 1, or 0 when it isn't on the JLPT lists. */
    val jlpt: Int,
    /** SVG path data for each stroke in order, on a 109-unit square; empty when KanjiVG doesn't have it. */
    val strokes: List<String>,
)

/**
 * A kanji in a word's spelling and the reading it takes there. The reading
 * is null when it belongs to several kanji together (今日, きょう). [after]
 * is the rest of the spelling, which picks out the okurigana.
 */
data class KanjiInWord(val kanji: String, val reading: String?, val after: String)

object Kanjis {
    /** True for a CJK ideograph. 々 isn't one: it repeats the kanji before it. */
    fun isKanji(c: Char): Boolean = c in '一'..'鿿' || c in '㐀'..'䶿'

    /** The different kanji in [text], in order. */
    fun inText(text: String): List<String> = text.filter(::isKanji).map(Char::toString).distinct()

    /** Each kanji in a spelling, from its furigana segments (text, reading over it). */
    fun inWord(segments: List<Pair<String, String?>>): List<KanjiInWord> = buildList {
        segments.forEachIndexed { i, (base, ruby) ->
            val kanji = base.filter(::isKanji)
            if (kanji.isEmpty()) return@forEachIndexed
            val after = segments.drop(i + 1).joinToString("") { it.first }
            val alone = kanji.length == 1 && base.length == 1
            for (c in kanji) add(KanjiInWord(c.toString(), if (alone) ruby else null, after))
        }
    }

    /**
     * Which of the kanji's dictionary readings [reading] is, or null when
     * none fits. Compounds change sounds, so a voiced first sound (かわ in
     * 川, がわ in 小川) and a small っ at the end (がく in 学, がっ in 学校)
     * still match, as does a verb's stem with its okurigana folded in (うけ
     * in 受付, from う.ける). Among kun'yomi, the one whose okurigana follows
     * the kanji in [after] wins (う.まれる for 生まれる).
     */
    fun dictionaryReading(kanji: Kanji, reading: String, after: String = ""): String? {
        val r = Kana.toHiragana(reading)
        data class Candidate(val reading: String, val exact: Boolean, val okurigana: Int, val plain: Boolean)
        val candidates = buildList {
            for (on in kanji.onyomi) {
                val base = Kana.toHiragana(on)
                if (r in soundChanges(base)) add(Candidate(on, r == base, 1, true))
            }
            for (kun in kanji.kunyomi) {
                val stem = kun.substringBefore('.').replace("-", "")
                val okurigana = if ('.' in kun) kun.substringAfter('.').replace("-", "") else ""
                if (stem.isEmpty()) continue
                val folded = r !in soundChanges(stem)
                if (folded && stemForms(stem, okurigana).none { r in soundChanges(it) }) continue
                val fit = when {
                    folded || okurigana.isEmpty() -> 1
                    after.startsWith(okurigana) -> 3
                    // Conjugated: 生きて for 生きる.
                    after.startsWith(okurigana.first()) -> 2
                    // Spellings that leave the okurigana out still match, last.
                    else -> 0
                }
                add(Candidate(kun, r == stem, fit, '-' !in kun))
            }
        }
        return candidates.maxWithOrNull(
            compareBy<Candidate>({ it.exact }, { it.okurigana }, { it.plain }),
        )?.reading
    }

    /** A verb's stem as compounds write it under the kanji: あげ for あ.げる, ひき for ひ.く. */
    private fun stemForms(stem: String, okurigana: String): List<String> {
        if (okurigana.isEmpty()) return emptyList()
        val head = okurigana.dropLast(1)
        return listOfNotNull(
            (stem + head).takeIf { okurigana.endsWith('る') && head.isNotEmpty() },
            I_ROW[okurigana.last()]?.let { stem + head + it },
        )
    }

    /** A reading and the forms it takes inside compounds. */
    private fun soundChanges(reading: String): Set<String> {
        val forms = mutableSetOf(reading)
        VOICED[reading.first()]?.forEach { forms += it + reading.drop(1) }
        if (reading.length > 1 && reading.last() in "つくちきり") {
            for (f in forms.toList()) forms += f.dropLast(1) + "っ"
        }
        return forms
    }

    private val I_ROW = "うくぐすつぬぶむる".zip("いきぎしちにびみり").toMap()

    private val VOICED: Map<Char, List<Char>> = buildMap {
        val plain = "かきくけこさしすせそたちつてとはひふへほ"
        val voiced = "がぎぐげござじずぜぞだぢづでどばびぶべぼ"
        val half = "ぱぴぷぺぽ"
        plain.forEachIndexed { i, c -> put(c, listOfNotNull(voiced[i], half.getOrNull(i - 15))) }
        // ち and つ usually voice to じ and ず (鼻血, 三日月).
        put('ち', listOf('ぢ', 'じ'))
        put('つ', listOf('づ', 'ず'))
    }
}
