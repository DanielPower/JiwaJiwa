package ca.danielpower.jstudy.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import kotlin.math.max
import kotlin.math.roundToInt

private val GAP = 6.dp
private val MARGIN = 12.dp
private val MAX_WIDTH = 360.dp
private val MAX_HEIGHT = 520.dp

/**
 * A popover next to the word it explains: below the word, or above it when
 * there's more room there, so it never covers the word. Call it inside the
 * word's Box. [content] gets the most height it can use and stay clear of
 * the word. A tap outside it calls [onDismiss] and still reaches the page,
 * so tapping another word opens that one.
 */
@Composable
internal fun BoxScope.WordPopover(onDismiss: () -> Unit, content: @Composable (maxHeight: Dp) -> Unit) {
    var anchor by remember { mutableStateOf<Rect?>(null) }
    Spacer(Modifier.matchParentSize().onGloballyPositioned { anchor = it.boundsInWindow() })
    val word = anchor ?: return

    val density = LocalDensity.current
    val insets = WindowInsets.systemBars
    val top = insets.getTop(density)
    val bottom = insets.getBottom(density)
    val gap = with(density) { GAP.roundToPx() }
    val margin = with(density) { MARGIN.roundToPx() }
    val windowHeight = LocalView.current.rootView.height
    val above = word.top - gap - top - margin
    val below = windowHeight - bottom - margin - word.bottom - gap
    val maxHeight = with(density) { max(above, below).roundToInt().toDp() }.coerceIn(160.dp, MAX_HEIGHT)
    val width = min(LocalConfiguration.current.screenWidthDp.dp - MARGIN * 2, MAX_WIDTH)

    Popup(
        popupPositionProvider = remember(gap, margin, top, bottom) { BesideWord(gap, margin, top, bottom) },
        onDismissRequest = onDismiss,
        // Not focusable, so a tap outside dismisses it and also lands on the page.
        properties = PopupProperties(focusable = false, dismissOnClickOutside = true),
    ) {
        Surface(
            modifier = Modifier.width(width),
            shape = WashiShape,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.onSurface),
            shadowElevation = 6.dp,
        ) {
            content(maxHeight)
        }
    }
}

/** Below the word if the popover fits there, else above it, else on the roomier side. */
private class BesideWord(val gap: Int, val margin: Int, val top: Int, val bottom: Int) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val height = popupContentSize.height
        val floor = windowSize.height - bottom - margin
        val ceiling = top + margin
        val below = anchorBounds.bottom + gap
        val above = anchorBounds.top - gap - height
        val y = when {
            below + height <= floor -> below
            above >= ceiling -> above
            anchorBounds.top - ceiling > floor - anchorBounds.bottom -> max(above, ceiling)
            else -> below
        }
        val x = (anchorBounds.center.x - popupContentSize.width / 2)
            .coerceAtMost(windowSize.width - margin - popupContentSize.width)
            .coerceAtLeast(margin)
        return IntOffset(x, y)
    }
}
