package ca.danielpower.jstudy.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ca.danielpower.jstudy.core.VerticalRun
import ca.danielpower.jstudy.core.VerticalSpot
import ca.danielpower.jstudy.core.VerticalText
import ca.danielpower.jstudy.data.StoryReading
import kotlin.math.roundToInt

// Compose has no vertical writing mode, so stories are set here by hand: each
// character in its own square cell, columns running top to bottom and right
// to left, furigana to the right of its kanji. The fonts' "vert" feature
// turns punctuation, brackets and ー to their vertical forms.

/**
 * A run of characters set together: a kanji run with its furigana, which
 * shouldn't break across columns, or a single character.
 */
private class Piece(val chars: List<String>, val ruby: List<String>?, val p: Int, val i: Int, val paragraphStart: Boolean)

/** Vertical forms of punctuation, brackets and the long vowel mark. */
private val Vertical = TextStyle(fontFeatureSettings = "vert")

private fun String.characters(): List<String> = buildList {
    var k = 0
    while (k < length) {
        val n = Character.charCount(codePointAt(k))
        add(substring(k, k + n))
        k += n
    }
}

private fun pieces(reading: StoryReading): List<Piece> = buildList {
    reading.paragraphs.forEachIndexed { p, words ->
        var start = true
        words.forEachIndexed { i, word ->
            val showRuby = reading.showsRuby(word)
            for (part in word.ruby) {
                val chars = part.base.characters().filter { it.isNotBlank() }
                if (chars.isEmpty()) continue
                val furigana = part.ruby
                if (showRuby && furigana != null) {
                    add(Piece(chars, furigana.characters(), p, i, start))
                    start = false
                } else {
                    for (c in chars) {
                        add(Piece(listOf(c), null, p, i, start))
                        start = false
                    }
                }
            }
        }
    }
}

/**
 * The story set vertically, a page at a time. The first page is at the right,
 * where reading starts, and the next page comes in from the left, as in a
 * Japanese book. Each page's columns are centred across it.
 */
@Composable
fun VerticalStory(
    reading: StoryReading,
    size: Float,
    family: FontFamily,
    selected: Pair<Int, Int>?,
    lit: Set<Pair<Int, Int>>,
    onTap: (p: Int, i: Int) -> Unit,
    onHold: (p: Int, i: Int) -> Unit,
    onBlank: () -> Unit,
    end: @Composable () -> Unit,
    onEnd: () -> Unit,
    startPage: Int,
    onPageShown: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val pieces = remember(reading) { pieces(reading) }
    BoxWithConstraints(modifier) {
        val density = LocalDensity.current
        val rubySize = size * RUBY_RATIO
        val cell = with(density) { (size * 1.15f).sp.toPx() }
        val rubyCell = with(density) { (rubySize * 1.15f).sp.toPx() }
        // The space past each column's furigana: about as tight as a paperback, fitting an extra column.
        val gap = cell * 0.45f
        val colWidth = cell + rubyCell
        val pad = with(density) { PAGE_PADDING.toPx() }
        val width = constraints.maxWidth - 2 * pad
        val height = constraints.maxHeight - 2 * pad - with(density) { PAGE_LABEL_HEIGHT.toPx() }
        // Keep one cell spare at the foot for hanging punctuation.
        val rows = ((height / cell).toInt() - 1).coerceAtLeast(4)
        val perPage = ((width + gap) / (colWidth + gap)).toInt().coerceAtLeast(1)
        val spots = remember(pieces, rows) { VerticalText.place(pieces.map { VerticalRun(it.chars, it.paragraphStart) }, rows) }
        // The pieces on each page, by index.
        val pages = remember(spots, perPage) {
            val count = (spots.maxOfOrNull { it.col } ?: 0) / perPage + 1
            List(count) { page -> spots.indices.filter { spots[it].col / perPage == page } }
        }
        // Every page's columns sit where a full page's would, so they don't shift as you turn pages.
        val fullWidth = perPage * colWidth + (perPage - 1) * gap
        val right = (width + fullWidth) / 2

        val cellDp = with(density) { cell.toDp() }
        val rubyDp = with(density) { rubyCell.toDp() }
        val ink = LocalContentColor.current
        val seal = MaterialTheme.colorScheme.primary
        val highlight = seal.copy(alpha = 0.14f)

        StoryPager(pages.size, reverse = true, end, onEnd, startPage, onPageShown) { page ->
            val onPage = pages[page]
            Layout(
                modifier = Modifier.fillMaxSize().padding(PAGE_PADDING),
                content = {
                    onPage.forEach { k ->
                        val piece = pieces[k]
                        val word = reading.paragraphs[piece.p][piece.i]
                        val isSelected = selected == (piece.p to piece.i)
                        val unknown = reading.isUnknown(word)
                        PieceView(
                            piece = piece,
                            textSize = size.sp,
                            rubySize = rubySize.sp,
                            family = family,
                            cell = cellDp,
                            rubyCell = rubyDp,
                            color = if (unknown || isSelected) seal else ink,
                            sideline = unknown,
                            background = if (isSelected || (piece.p to piece.i) in lit) highlight else Color.Transparent,
                            gestures = Modifier.storyWordGestures(
                                onTap = { if (word.tappable()) onTap(piece.p, piece.i) else onBlank() },
                                onHold = { onHold(piece.p, piece.i) },
                            ),
                        )
                    }
                },
            ) { measurables, constraints ->
                fun left(spot: VerticalSpot): Float {
                    val col = spot.col % perPage
                    return right - (col + 1) * colWidth - col * gap
                }
                // Round each piece's top and foot, not its height, so a word's
                // pieces meet exactly: rounding the height on its own left
                // highlighted words with a pixel of overlap or gap between them.
                fun top(row: Int) = (row * cell).roundToInt()
                val placeables = measurables.mapIndexed { n, m ->
                    val row = spots[onPage[n]].row
                    val height = top(row + pieces[onPage[n]].chars.size) - top(row)
                    m.measure(Constraints.fixed(colWidth.roundToInt(), height))
                }
                layout(constraints.maxWidth, constraints.maxHeight) {
                    placeables.forEachIndexed { n, placeable ->
                        val spot = spots[onPage[n]]
                        placeable.place(left(spot).roundToInt(), top(spot.row))
                    }
                }
            }
        }
    }
}

@Composable
private fun PieceView(
    piece: Piece,
    textSize: TextUnit,
    rubySize: TextUnit,
    family: FontFamily,
    cell: Dp,
    rubyCell: Dp,
    color: Color,
    sideline: Boolean,
    background: Color,
    gestures: Modifier,
) {
    var modifier = Modifier.background(background).then(gestures)
    if (sideline) {
        // New words get a line down their left side, where underlines go in vertical text.
        modifier = modifier.drawBehind {
            val x = 1.dp.toPx()
            drawLine(color, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1.dp.toPx())
        }
    }
    Box(modifier) {
        Column(Modifier.width(cell)) {
            for (c in piece.chars) {
                Glyph(c, textSize, family, cell, color)
            }
        }
        val ruby = piece.ruby
        if (ruby != null) {
            // Long readings run past their kanji, centred on them.
            Column(
                Modifier
                    .align(Alignment.CenterEnd)
                    .width(rubyCell)
                    .requiredHeight(rubyCell * ruby.size),
            ) {
                for (c in ruby) {
                    Glyph(c, rubySize, family, rubyCell, color.copy(alpha = 0.75f))
                }
            }
        }
    }
}

/**
 * One character centred in its square cell. The text keeps the font's own
 * line height, which is taller than the cell, and is left free to overflow
 * it: squeezing the line to the cell clipped the feet of some kanji.
 */
@Composable
private fun Glyph(c: String, size: TextUnit, family: FontFamily, cell: Dp, color: Color) {
    Box(Modifier.size(cell), contentAlignment = Alignment.Center) {
        Text(
            c,
            modifier = Modifier.wrapContentHeight(unbounded = true),
            fontSize = size,
            fontFamily = family,
            style = Vertical,
            color = color,
            textAlign = TextAlign.Center,
            overflow = TextOverflow.Visible,
            softWrap = false,
        )
    }
}
