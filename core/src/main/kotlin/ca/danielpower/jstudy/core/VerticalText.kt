package ca.danielpower.jstudy.core

/**
 * Characters set together in vertical text, such as a kanji run with its
 * furigana, which shouldn't break across columns. A paragraph's first run
 * says where the paragraph starts: [indent] cells down its first column, and
 * [wrapIndent] cells down the columns it runs on to. A [glued] run never
 * starts a column, like closing punctuation (a note's marker).
 */
class VerticalRun(
    val chars: List<String>,
    val paragraphStart: Boolean = false,
    val indent: Int = 1,
    val wrapIndent: Int = 0,
    val glued: Boolean = false,
)

/** A run's column (0 is the first, rightmost one) and the row its first character sits in. */
data class VerticalSpot(val col: Int, val row: Int)

/** Line breaking for vertical text, following the usual Japanese rules (kinsoku shori). */
object VerticalText {
    /**
     * Where each run goes, given how many cells fit down a column. One more
     * cell below that is kept for a hanging 、 or 。.
     */
    fun place(runs: List<VerticalRun>, rows: Int): List<VerticalSpot> {
        val spots = ArrayList<VerticalSpot>(runs.size)
        var col = 0
        var row = 0
        var wrapRow = 0
        runs.forEachIndexed { k, run ->
            if (run.paragraphStart) {
                if (k > 0) col++
                // Paragraphs start one cell down, as in print, unless they say otherwise.
                row = run.indent
                wrapRow = run.wrapIndent
            }
            val n = run.chars.size
            val hangs = n == 1 && run.chars[0] in Kinsoku.HANGING && row == rows
            if (row + n > rows && !hangs && row > wrapRow) {
                // Rather than start a column with closing punctuation or end
                // one with an opening bracket, carry the runs before the break
                // over too (oidashi), as long as some of the column stays behind.
                var start = k
                while (
                    start > 0 && spots[start - 1].col == col &&
                    (runs[start].glued || runs[start].chars.first() in Kinsoku.CLOSING || runs[start - 1].chars.last() in Kinsoku.OPENING)
                ) {
                    start--
                }
                if (start < k && (start == 0 || spots[start - 1].col != col)) start = k
                // Nor overflow the next column with what it carries.
                val carried = (start until k).sumOf { runs[it].chars.size }
                val end = wrapRow + carried
                if (start < k && end + n > rows && !(n == 1 && run.chars[0] in Kinsoku.HANGING && end == rows)) start = k
                col++
                row = wrapRow
                for (j in start until k) {
                    spots[j] = VerticalSpot(col, row)
                    row += runs[j].chars.size
                }
            }
            spots.add(VerticalSpot(col, row))
            row += n
        }
        return spots
    }
}
