package ca.danielpower.jstudy.core

/**
 * A sentence to read aloud: words [words] of paragraph [p], as [text], said
 * by [speaker] (null for narration). [starts] has where each word begins in
 * the text, so the engine's place in it can be turned back into a word.
 */
data class NarrationLine(val p: Int, val words: IntRange, val text: String, val starts: List<Int>, val speaker: String?) {
    /** The word being said at [offset] in the text. */
    fun wordAt(offset: Int): Int {
        val k = starts.indexOfLast { it <= offset }.coerceAtLeast(0)
        return words.first + k
    }
}

/** A story's text as sentences to read aloud, in order. */
object Narration {
    fun lines(paragraphs: List<List<ReadingWord>>, speakers: List<String?>): List<NarrationLine> = buildList {
        paragraphs.forEachIndexed { p, words ->
            for (range in sentenceRanges(words)) {
                val text = StringBuilder()
                val starts = ArrayList<Int>()
                for (i in range) {
                    starts += text.length
                    text.append(spoken(words[i]))
                }
                if (text.any(::sayable)) add(NarrationLine(p, range, text.toString(), starts, speakers.getOrNull(p)))
            }
        }
    }

    /** The line holding word [i] of paragraph [p]. */
    fun lineOf(lines: List<NarrationLine>, p: Int, i: Int): Int? =
        lines.indexOfFirst { it.p == p && i in it.words }.takeIf { it >= 0 }
            // A word in a sentence with nothing to say, like 「…」: the next line after it.
            ?: lines.indexOfFirst { it.p > p || (it.p == p && it.words.first > i) }.takeIf { it >= 0 }

    /**
     * A word as the engine should get it: as written, except where engines
     * often choose the wrong reading, which get the reading the story was
     * checked with. Names written in kanji are among them.
     */
    fun spoken(word: ReadingWord): String {
        if (word.surface.none(Kanjis::isKanji)) return word.surface
        val ambiguous = word.kind == WordKind.NAME || word.lemma in AMBIGUOUS || word.surface in AMBIGUOUS
        return if (ambiguous) word.reading else word.surface
    }

    // Written alike, read differently by context: 今日 きょう or こんにち, 何 なに or なん.
    private val AMBIGUOUS = setOf(
        "今日", "明日", "昨日", "何", "何時", "何人", "何日", "一日", "一人", "二人", "大人", "上手", "下手",
        "方", "日本", "描く", "弾く", "弦", "行く", "来る", "生", "上", "下", "間", "中", "後", "前",
    )

    private fun sayable(c: Char) = c.isLetterOrDigit()
}
