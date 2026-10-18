package ca.danielpower.jstudy.core

/**
 * A word to set in horizontal text: its text, and how wide it is drawn.
 * A [glued] word never starts a line, like closing punctuation (a note's marker).
 */
class FlowWord(val text: String, val width: Float, val glued: Boolean = false)

/** The words of paragraph [paragraph] that make up one line. */
data class TextLine(val paragraph: Int, val words: IntRange)

/** Line and page breaking for horizontal text, with the same rules as [VerticalText]. */
object HorizontalText {
    /**
     * Breaks a paragraph's [words] into lines no wider than [maxWidth]. Rather
     * than start a line with closing punctuation or end one with an opening
     * bracket, the words before the break move down too, as long as some of
     * the line stays behind and the next one still fits. A word wider than a
     * line gets a line of its own.
     * The first line starts [indent] in, and the rest [wrapIndent] in.
     */
    fun lines(words: List<FlowWord>, maxWidth: Float, indent: Float = 0f, wrapIndent: Float = 0f): List<IntRange> {
        val starts = ArrayList<Int>()
        var x = 0f
        words.forEachIndexed { k, word ->
            val width = maxWidth - if (starts.size <= 1) indent else wrapIndent
            if (k == 0) {
                starts += 0
            } else if (x + word.width > width) {
                fun bad(i: Int) = words[i].glued || words[i].text.take(1) in Kinsoku.CLOSING || words[i - 1].text.takeLast(1) in Kinsoku.OPENING
                val lineStart = starts.last()
                var start = k
                while (start > lineStart + 1 && bad(start)) start--
                if (bad(start)) start = k
                // Carrying words down mustn't overflow the next line either.
                fun carried() = (start until k).sumOf { words[it].width.toDouble() }.toFloat()
                if (carried() + word.width > maxWidth - wrapIndent) start = k
                starts += start
                x = carried()
            }
            x += word.width
        }
        return starts.mapIndexed { i, s -> s until (starts.getOrNull(i + 1) ?: words.size) }
    }

    /**
     * Fills pages [pageHeight] tall with the [paragraphs]' lines, each
     * [lineHeight] tall, with [gap] between paragraphs on the same page. A
     * paragraph may run on to the next page. Every page holds at least one line.
     */
    fun pages(paragraphs: List<List<IntRange>>, lineHeight: Float, gap: Float, pageHeight: Float): List<List<TextLine>> {
        val pages = ArrayList<List<TextLine>>()
        var page = ArrayList<TextLine>()
        var y = 0f
        paragraphs.forEachIndexed { p, lines ->
            lines.forEachIndexed { j, words ->
                val top = if (page.isNotEmpty() && j == 0) y + gap else y
                if (page.isNotEmpty() && top + lineHeight > pageHeight) {
                    pages += page
                    page = ArrayList()
                    y = 0f
                } else {
                    y = top
                }
                page += TextLine(p, words)
                y += lineHeight
            }
        }
        if (page.isNotEmpty()) pages += page
        return pages
    }
}
