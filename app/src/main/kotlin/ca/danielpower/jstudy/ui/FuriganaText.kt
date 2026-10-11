package ca.danielpower.jstudy.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import ca.danielpower.jstudy.data.FuriganaSegment

/**
 * A word with its reading set above each kanji run. The reading row always
 * takes up its space, so showing or hiding it doesn't move the word.
 */
@Composable
fun FuriganaText(
    segments: List<FuriganaSegment>,
    showFurigana: Boolean,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 44.sp,
) {
    val rubySize = fontSize * 0.42f
    Row(modifier = modifier, horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.Bottom) {
        for (segment in segments) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = segment.ruby ?: " ",
                    fontSize = rubySize,
                    lineHeight = rubySize * 1.15f,
                    maxLines = 1,
                    softWrap = false,
                    textAlign = TextAlign.Center,
                    color = LocalContentColor.current.copy(alpha = 0.8f),
                    modifier = Modifier.alpha(if (showFurigana && segment.ruby != null) 1f else 0f),
                )
                Text(text = segment.base, fontSize = fontSize, lineHeight = fontSize * 1.2f, softWrap = false)
            }
        }
    }
}
