package ca.danielpower.jstudy.core

private const val ENDERS = "。！？!?"
private const val OPENERS = "「『（(【〔"
private const val CLOSERS = "」』）)】〕"

/**
 * The sentences of a paragraph, as ranges of word indices. A sentence ends at
 * 。！？ outside brackets, taking any closing brackets after it, so a quote
 * stays with the words around it: 夫は「青がいい。」と言った。 is one sentence.
 * A quote that ends in 。！？ and is followed straight away by another quote
 * is a sentence of its own: 「あれは何？」「本です。」 is two.
 */
fun sentenceRanges(words: List<ReadingWord>): List<IntRange> {
    val out = ArrayList<IntRange>()
    var start = 0
    var depth = 0
    var ended = false
    var quoteEnded = false
    var previous = ' '
    words.forEachIndexed { i, word ->
        val punctuation = word.kind == WordKind.PUNCTUATION
        val opens = punctuation && word.surface.any { it in OPENERS }
        if ((ended && !(punctuation && !opens)) || (quoteEnded && opens)) {
            out += start until i
            start = i
            ended = false
        }
        quoteEnded = false
        if (punctuation) {
            for (c in word.surface) {
                when (c) {
                    in OPENERS -> depth++
                    in CLOSERS -> {
                        depth = (depth - 1).coerceAtLeast(0)
                        if (depth == 0 && previous in ENDERS) quoteEnded = true
                    }
                    in ENDERS -> if (depth == 0) ended = true
                }
                previous = c
            }
        } else {
            previous = word.surface.lastOrNull() ?: ' '
        }
    }
    if (start < words.size) out += start until words.size
    return out
}

/** The text of each sentence in [paragraphs], in order. */
fun sentenceTexts(paragraphs: List<List<ReadingWord>>): List<String> =
    paragraphs.flatMap { words -> sentenceRanges(words).map { r -> r.joinToString("") { words[it].surface } } }

object TranslationPrompts {
    val system = """
        You translate Japanese sentences from a graded reader into natural English for the learner reading it.
        Translate each numbered sentence on its own line, keeping its number: "1. ...". Keep the meaning
        close to the Japanese. Write nothing else.
    """.trimIndent()

    fun request(sentences: List<String>): String =
        sentences.mapIndexed { i, s -> "${i + 1}. $s" }.joinToString("\n")
}

/** English for each of [count] sentences from a numbered reply, or null if any is missing. */
fun parseTranslations(reply: String, count: Int): List<String>? {
    val numbered = Regex("""^\s*(\d+)[.)．]\s*(.+)$""")
    val byNumber = HashMap<Int, String>()
    for (line in reply.lines()) {
        val m = numbered.find(line) ?: continue
        byNumber.putIfAbsent(m.groupValues[1].toInt(), m.groupValues[2].trim())
    }
    return (1..count).map { byNumber[it] ?: return null }
}

/** Translates a story's sentences with one request. */
class StoryTranslator(private val model: StoryModel) {
    /** English for each of [sentences], or null when the reply doesn't match them one to one. */
    fun translate(sentences: List<String>): List<String>? {
        if (sentences.isEmpty()) return emptyList()
        return parseTranslations(model.start(TranslationPrompts.system, TranslationPrompts.request(sentences)), sentences.size)
    }
}
