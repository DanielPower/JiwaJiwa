package ca.danielpower.jstudy.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
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
 * The story set vertically. It opens at the right, where reading starts, and
 * scrolls sideways. The open word's [popover] sits beside the whole word, so
 * it doesn't cover the rest of the word down the column.
 */
@Composable
fun VerticalStory(
    reading: StoryReading,
    selected: Pair<Int, Int>?,
    lit: Set<Pair<Int, Int>>,
    onOpen: (p: Int, i: Int) -> Unit,
    popover: @Composable (Dp) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val pieces = remember(reading) { pieces(reading) }
    val selectedPieces = pieces.indices.filter { (pieces[it].p to pieces[it].i) == selected }
    BoxWithConstraints(modifier) {
        val density = LocalDensity.current
        val cell = with(density) { (TEXT_SIZE * 1.15f).sp.toPx() }
        val rubyCell = with(density) { (RUBY_SIZE * 1.15f).sp.toPx() }
        val gap = cell * 0.75f
        val colWidth = cell + rubyCell
        // Keep one cell spare at the foot for hanging punctuation.
        val rows = ((constraints.maxHeight / cell).toInt() - 1).coerceAtLeast(4)
        val spots = remember(pieces, rows) { VerticalText.place(pieces.map { VerticalRun(it.chars, it.paragraphStart) }, rows) }
        val cols = (spots.maxOfOrNull { it.col } ?: 0) + 1
        val width = cols * colWidth + (cols - 1) * gap

        val cellDp = with(density) { cell.toDp() }
        val rubyDp = with(density) { rubyCell.toDp() }
        val ink = LocalContentColor.current
        val seal = MaterialTheme.colorScheme.primary
        val highlight = seal.copy(alpha = 0.14f)

        Box(Modifier.fillMaxHeight().horizontalScroll(rememberScrollState(), reverseScrolling = true)) {
            Layout(
                content = {
                    pieces.forEach { piece ->
                        val word = reading.paragraphs[piece.p][piece.i]
                        val isSelected = selected == (piece.p to piece.i)
                        val unknown = reading.isUnknown(word)
                        PieceView(
                            piece = piece,
                            cell = cellDp,
                            rubyCell = rubyDp,
                            color = if (unknown || isSelected) seal else ink,
                            sideline = unknown,
                            background = if (isSelected || (piece.p to piece.i) in lit) highlight else Color.Transparent,
                            onClick = if (word.tappable()) {
                                { onOpen(piece.p, piece.i) }
                            } else {
                                null
                            },
                        )
                    }
                    // Spans the open word, to anchor its popover.
                    if (selectedPieces.isNotEmpty()) Box { WordPopover(onDismiss, popover) }
                },
            ) { measurables, constraints ->
                fun left(spot: VerticalSpot) = width - (spot.col + 1) * colWidth - spot.col * gap
                val placeables = pieces.indices.map { k ->
                    measurables[k].measure(Constraints.fixed(colWidth.roundToInt(), (pieces[k].chars.size * cell).roundToInt()))
                }
                // A word split across columns spans both of them.
                val anchor = if (selectedPieces.isEmpty()) {
                    null
                } else {
                    val x0 = selectedPieces.minOf { left(spots[it]) }
                    val x1 = selectedPieces.maxOf { left(spots[it]) + colWidth }
                    val y0 = selectedPieces.minOf { spots[it].row * cell }
                    val y1 = selectedPieces.maxOf { (spots[it].row + pieces[it].chars.size) * cell }
                    val size = Constraints.fixed((x1 - x0).roundToInt(), (y1 - y0).roundToInt())
                    Triple(measurables[pieces.size].measure(size), x0, y0)
                }
                layout(width.roundToInt(), constraints.maxHeight) {
                    placeables.forEachIndexed { k, placeable ->
                        val spot = spots[k]
                        placeable.place(left(spot).roundToInt(), (spot.row * cell).roundToInt())
                    }
                    anchor?.let { (placeable, x, y) -> placeable.place(x.roundToInt(), y.roundToInt()) }
                }
            }
        }
    }
}

@Composable
private fun PieceView(
    piece: Piece,
    cell: Dp,
    rubyCell: Dp,
    color: Color,
    sideline: Boolean,
    background: Color,
    onClick: (() -> Unit)?,
) {
    var modifier = Modifier.background(background)
    if (onClick != null) modifier = modifier.clickable(onClick = onClick)
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
                Glyph(c, TEXT_SIZE, cell, color)
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
                    Glyph(c, RUBY_SIZE, rubyCell, color.copy(alpha = 0.75f))
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
private fun Glyph(c: String, size: Int, cell: Dp, color: Color) {
    Box(Modifier.size(cell), contentAlignment = Alignment.Center) {
        Text(
            c,
            modifier = Modifier.wrapContentHeight(unbounded = true),
            fontSize = size.sp,
            fontFamily = Gothic,
            style = Vertical,
            color = color,
            textAlign = TextAlign.Center,
            overflow = TextOverflow.Visible,
            softWrap = false,
        )
    }
}
