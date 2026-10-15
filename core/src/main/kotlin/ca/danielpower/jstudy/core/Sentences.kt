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
