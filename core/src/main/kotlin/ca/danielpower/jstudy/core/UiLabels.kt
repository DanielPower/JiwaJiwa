package ca.danielpower.jstudy.core

/**
 * A piece of the app's own text, in English and in Japanese. It shows in
 * English until you've mastered every word the Japanese uses, then in
 * Japanese, so the app turns Japanese as you learn.
 *
 * The words are found by reading [japanese] like a story. Name them in
 * [words] (dictionary spellings) only where that gets it wrong, such as
 * text with a name in it.
 */
data class UiLabel(val english: String, val japanese: String, val words: List<String> = emptyList()) {
    /** Identifies the label's vocabulary for caching: the same Japanese and words need the same words mastered. */
    val key: String get() = if (words.isEmpty()) japanese else japanese + "|" + words.joinToString(",")
}

object UiLabels {
    /**
     * The dictionary words [label] uses, or null when the label can't switch
     * to Japanese: a word or name isn't in the dictionary, or there's no
     * vocabulary in it at all. Particles, endings, suffixes and numbers
     * don't count; they aren't cards.
     */
    fun words(label: UiLabel, reader: Reader, lexicon: Lexicon): List<Long>? {
        val ids = if (label.words.isNotEmpty()) {
            label.words.map { lexicon.find(it, null)?.wordId ?: return null }
        } else {
            val words = reader.read(label.japanese).flatten()
            // A loanword or name the dictionary lacks may be one you can't read yet.
            if (words.any { it.kind == WordKind.NAME && it.wordId == null }) return null
            words.filter { it.kind == WordKind.WORD }.map { it.wordId ?: return null }
        }
        return ids.distinct().ifEmpty { null }
    }
}

/** Which labels show in Japanese: what each label's words are, and which words you've mastered. */
class UiLanguage(
    /** Each label's words by [UiLabel.key]; null for a label that stays English. Missing until looked up. */
    private val words: Map<String, List<Long>?>,
    private val mastered: Set<Long>,
) {
    /** Whether [label]'s words have been looked up yet. */
    fun knows(label: UiLabel): Boolean = label.key in words

    fun isJapanese(label: UiLabel): Boolean = words[label.key]?.let { mastered.containsAll(it) } == true

    fun text(label: UiLabel): String = if (isJapanese(label)) label.japanese else label.english

    fun withWords(more: Map<String, List<Long>?>) = UiLanguage(words + more, mastered)

    fun withMastered(mastered: Set<Long>) = UiLanguage(words, mastered)

    companion object {
        val ENGLISH = UiLanguage(emptyMap(), emptySet())
    }
}
