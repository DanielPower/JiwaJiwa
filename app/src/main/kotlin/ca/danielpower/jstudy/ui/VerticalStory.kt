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
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

private class Spot(val col: Int, val row: Int)

/** Vertical forms of punctuation, brackets and the long vowel mark. */
private val Vertical = TextStyle(fontFeatureSettings = "vert")

/** May hang below the end of a column rather than start the next one. */
private val HANGING = setOf("、", "。", "，", "．", "」", "』", "）", "】", "〕", "！", "？", "!", "?")

/** Never end a column. */
private val OPENING = setOf("「", "『", "（", "【", "〔")

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

/** Where each piece goes, given how many cells fit down a column. */
private fun place(pieces: List<Piece>, rows: Int): List<Spot> {
    var col = 0
    var row = 0
    var first = true
    return pieces.map { piece ->
        if (piece.paragraphStart) {
            if (!first) col++
            // Paragraphs start one cell down, as in print.
            row = 1
        }
        first = false
        val n = piece.chars.size
        val hangs = n == 1 && piece.chars[0] in HANGING && row == rows
        if (row + n > rows && !hangs && row > 0) {
            col++
            row = 0
        } else if (n == 1 && piece.chars[0] in OPENING && row == rows - 1) {
            col++
            row = 0
        }
        Spot(col, row).also { row += n }
    }
}

/**
 * The story set vertically. It opens at the right, where reading starts, and
 * scrolls sideways. The open word's [popover] hangs off its first piece.
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
    val firstPiece = pieces.indexOfFirst { (it.p to it.i) == selected }
    BoxWithConstraints(modifier) {
        val density = LocalDensity.current
        val cell = with(density) { (TEXT_SIZE * 1.15f).sp.toPx() }
        val rubyCell = with(density) { (RUBY_SIZE * 1.15f).sp.toPx() }
        val gap = cell * 0.75f
        val colWidth = cell + rubyCell
        // Keep one cell spare at the foot for hanging punctuation.
        val rows = ((constraints.maxHeight / cell).toInt() - 1).coerceAtLeast(4)
        val spots = remember(pieces, rows) { place(pieces, rows) }
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
                    pieces.forEachIndexed { k, piece ->
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
                            popover = if (k == firstPiece) popover else null,
                            onDismiss = onDismiss,
                        )
                    }
                },
            ) { measurables, constraints ->
                val placeables = measurables.mapIndexed { k, m ->
                    m.measure(Constraints.fixed(colWidth.roundToInt(), (pieces[k].chars.size * cell).roundToInt()))
                }
                layout(width.roundToInt(), constraints.maxHeight) {
                    placeables.forEachIndexed { k, placeable ->
                        val spot = spots[k]
                        val x = width - (spot.col + 1) * colWidth - spot.col * gap
                        placeable.place(x.roundToInt(), (spot.row * cell).roundToInt())
                    }
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
    popover: (@Composable (Dp) -> Unit)?,
    onDismiss: () -> Unit,
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
                Box(Modifier.size(cell), contentAlignment = Alignment.Center) {
                    Text(
                        c,
                        fontSize = TEXT_SIZE.sp,
                        lineHeight = TEXT_SIZE.sp,
                        fontFamily = Gothic,
                        style = Vertical,
                        color = color,
                        textAlign = TextAlign.Center,
                    )
                }
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
                    Box(Modifier.size(rubyCell), contentAlignment = Alignment.Center) {
                        Text(
                            c,
                            fontSize = RUBY_SIZE.sp,
                            lineHeight = RUBY_SIZE.sp,
                            fontFamily = Gothic,
                            style = Vertical,
                            color = color.copy(alpha = 0.75f),
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }
        if (popover != null) WordPopover(onDismiss, popover)
    }
}
