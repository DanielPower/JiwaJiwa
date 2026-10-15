package ca.danielpower.jstudy.core

import kotlin.math.roundToInt

/** How a story's words measure up against what the learner knows. */
data class Coverage(
    /** Counted running words (names, numbers, grammar and punctuation aside). */
    val words: Int,
    val knownWords: Int,
    /** Distinct new words in order of first appearance. */
    val newWords: List<ReadingWord>,
) {
    val knownShare get() = if (words == 0) 1.0 else knownWords.toDouble() / words
}

/** [isKnown] says whether a dictionary word counts as known; words with no entry never do. */
fun coverage(paragraphs: List<List<ReadingWord>>, isKnown: (Long) -> Boolean): Coverage {
    var words = 0
    var known = 0
    val new = LinkedHashMap<String, ReadingWord>()
    for (w in paragraphs.flatten()) {
        if (w.kind != WordKind.WORD) continue
        words++
        val id = w.wordId
        if (id != null && isKnown(id)) {
            known++
        } else {
            new.putIfAbsent(w.key, w)
        }
    }
    return Coverage(words, known, new.values.toList())
}

/** How a finished read went. [millis] is how long the reading took, if it was timed. */
data class ReadStats(val characters: Int, val words: Int, val knownWords: Int, val millis: Long?) {
    val knownShare get() = if (words == 0) 1.0 else knownWords.toDouble() / words

    /** Reading speed, left out for a read too short to say much. */
    val charactersPerMinute: Int?
        get() = millis?.takeIf { it >= MIN_TIMED }?.let { (characters * 60_000.0 / it).roundToInt() }

    companion object {
        const val MIN_TIMED = 15_000L
    }
}

/** Characters count letters, digits, kana and kanji, not punctuation or spaces, as reading speeds usually do. */
fun readStats(paragraphs: List<List<ReadingWord>>, coverage: Coverage, millis: Long?) = ReadStats(
    characters = paragraphs.flatten().sumOf { w -> w.surface.count { it.isLetterOrDigit() } },
    words = coverage.words,
    knownWords = coverage.knownWords,
    millis = millis,
)
