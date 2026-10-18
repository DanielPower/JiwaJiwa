package ca.danielpower.jstudy.core


/** How a story's words measure up against what the learner knows. */
data class Coverage(
    /** Counted running words (names, numbers, grammar and punctuation aside). */
    val words: Int,
    val knownWords: Int,
    /** Distinct new words in order of first appearance. */
    val newWords: List<ReadingWord>,
)

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
