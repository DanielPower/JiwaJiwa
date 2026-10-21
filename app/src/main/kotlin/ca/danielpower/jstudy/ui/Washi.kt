package ca.danielpower.jstudy.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ca.danielpower.jstudy.core.MasteryRules

// ------------------------------------------------------------------ buttons

/** The main action on a screen: solid ink. */
@Composable
fun PrimaryButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = WashiShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.onSurface,
            contentColor = MaterialTheme.colorScheme.surface,
        ),
        content = content,
    )
}

/** The grade buttons on a revealed card: pine for "Right", crimson for "Wrong". */
@Composable
fun GradeButton(
    right: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val container = if (right) MaterialTheme.washi.pine else colors.error
    Button(
        onClick = onClick,
        modifier = modifier,
        shape = WashiShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = container,
            contentColor = if (right) colors.surface else colors.onError,
        ),
        content = content,
    )
}

/** A secondary action: an ink outline. */
@Composable
fun SecondaryButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    content: @Composable RowScope.() -> Unit,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = WashiShape,
        border = BorderStroke(
            1.dp,
            if (enabled) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.outlineVariant,
        ),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
        contentPadding = contentPadding,
        content = content,
    )
}

@Composable
fun WashiIconButton(icon: ImageVector, description: String, onClick: () -> Unit, enabled: Boolean = true) {
    IconButton(onClick = onClick, enabled = enabled) {
        Icon(icon, contentDescription = description, modifier = Modifier.size(22.dp))
    }
}

/** A filter: ink when on, an ink outline when off. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WashiChip(selected: Boolean, onClick: () -> Unit, label: String, enabled: Boolean = true) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        enabled = enabled,
        colors = FilterChipDefaults.filterChipColors(
            labelColor = MaterialTheme.colorScheme.onSurface,
            selectedContainerColor = MaterialTheme.colorScheme.onSurface,
            selectedLabelColor = MaterialTheme.colorScheme.surface,
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = enabled,
            selected = selected,
            borderColor = MaterialTheme.colorScheme.outline,
        ),
    )
}

// ------------------------------------------------------------------ labels

/** A small spaced-out heading over a section. */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        letterSpacing = 1.5.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}

/** The JLPT level, stamped like a seal. */
@Composable
fun LevelSeal(label: String, modifier: Modifier = Modifier, tilted: Boolean = false) {
    val color = MaterialTheme.colorScheme.primary
    Box(
        modifier = modifier
            .then(if (tilted) Modifier.rotate(-6f) else Modifier)
            .size(40.dp)
            .border(2.dp, color, WashiShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            fontFamily = Gothic,
            fontWeight = FontWeight.Bold,
            fontSize = if (label.length > 2) 11.sp else 14.sp,
            color = color,
        )
    }
}

/** A thin bar for mastery, 0 to 100. */
@Composable
fun MasteryBar(mastery: Int, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.onSurface) {
    Box(modifier.height(4.dp).background(MaterialTheme.colorScheme.surfaceVariant)) {
        Box(
            Modifier
                .fillMaxWidth((mastery / 100f).coerceIn(0f, 1f))
                .height(4.dp)
                .background(color),
        )
    }
}

/**
 * Mastery's colour by stage, the same stages as furigana: ochre while a word
 * is seen (furigana shows), verdigris once it's known (tap to show), pine
 * once it's mastered (off).
 */
@Composable
fun masteryColor(mastery: Int): Color = when {
    mastery >= MasteryRules.OFF_AT -> MaterialTheme.washi.pine
    mastery >= MasteryRules.HIDE_AT -> MaterialTheme.washi.verdigris
    else -> MaterialTheme.colorScheme.tertiary
}

/** A short mastery bar with its number, for lists. */
@Composable
fun MasteryMeter(mastery: Int, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        MasteryBar(mastery, Modifier.width(44.dp), masteryColor(mastery))
        Text("$mastery", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
    }
}

// ------------------------------------------------------------------ navigation

enum class Tab(val label: String, val icon: ImageVector) {
    HOME("Home", WashiIcons.Home),
    STORIES("Read", WashiIcons.Book),
    REVIEW("Review", WashiIcons.Cards),
    DICTIONARY("Dictionary", WashiIcons.Search),
}

@Composable
fun WashiNavBar(current: Tab, badges: Map<Tab, Int> = emptyMap(), onSelect: (Tab) -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars)
                .height(64.dp),
        ) {
            for (tab in Tab.entries) {
                val selected = tab == current
                val color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .height(64.dp)
                        .clickable(role = Role.Tab) { if (!selected) onSelect(tab) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
                ) {
                    Box {
                        Icon(tab.icon, contentDescription = null, tint = color, modifier = Modifier.size(22.dp))
                        val count = badges[tab] ?: 0
                        if (count > 0) {
                            Text(
                                if (count > 99) "99+" else "$count",
                                fontSize = 9.sp,
                                lineHeight = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .offset(x = 10.dp, y = (-4).dp)
                                    .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(6.dp))
                                    .padding(horizontal = 4.dp),
                            )
                        }
                    }
                    Text(
                        tab.label,
                        fontSize = 12.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        color = color,
                    )
                }
            }
        }
    }
}

// ------------------------------------------------------------------ icons

/** Line icons drawn on a 24 unit grid, tinted by whoever shows them. */
object WashiIcons {
    private fun icon(name: String, vararg paths: String): ImageVector = icon(name, filled = false, *paths)

    private fun icon(name: String, filled: Boolean, vararg paths: String): ImageVector {
        val builder = ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        )
        for (path in paths) {
            builder.addPath(
                pathData = addPathNodes(path),
                fill = if (filled) SolidColor(Color.Black) else null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.8f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
        }
        return builder.build()
    }

    val Home = icon("home", "M3 11l9-7 9 7v9a1 1 0 0 1-1 1h-5v-6H9v6H4a1 1 0 0 1-1-1z")
    val Search = icon("search", "M5 11a6 6 0 1 0 12 0a6 6 0 1 0 -12 0", "M20 20l-4.5-4.5")
    val Book = icon("book", "M4 5h6a2 2 0 0 1 2 2v12a2 2 0 0 0-2-2H4z", "M20 5h-6a2 2 0 0 0-2 2v12a2 2 0 0 1 2-2h6z")
    // A toothed cog, so it reads as settings rather than a sun. Its outline is Lucide's "settings" (ISC licence).
    val Settings = icon(
        "settings",
        "M12.22 2h-.44a2 2 0 0 0-2 2v.18a2 2 0 0 1-1 1.73l-.43.25a2 2 0 0 1-2 0l-.15-.08a2 2 0 0 0-2.73.73l-.22.38a2 2 0 0 0 .73 2.73l.15.1a2 2 0 0 1 1 1.72v.51a2 2 0 0 1-1 1.74l-.15.09a2 2 0 0 0-.73 2.73l.22.38a2 2 0 0 0 2.73.73l.15-.08a2 2 0 0 1 2 0l.43.25a2 2 0 0 1 1 1.73V20a2 2 0 0 0 2 2h.44a2 2 0 0 0 2-2v-.18a2 2 0 0 1 1-1.73l.43-.25a2 2 0 0 1 2 0l.15.08a2 2 0 0 0 2.73-.73l.22-.39a2 2 0 0 0-.73-2.73l-.15-.08a2 2 0 0 1-1-1.74v-.5a2 2 0 0 1 1-1.74l.15-.09a2 2 0 0 0 .73-2.73l-.22-.38a2 2 0 0 0-2.73-.73l-.15.08a2 2 0 0 1-2 0l-.43-.25a2 2 0 0 1-1-1.73V4a2 2 0 0 0-2-2z",
        "M9 12a3 3 0 1 0 6 0a3 3 0 1 0 -6 0",
    )
    /** Two cards, one behind the other, for reviews. */
    val Cards = icon("cards", "M4 8h12v12H4z", "M8 4h12v12")
    val Forward = icon("forward", "M5 12h14", "M13 6l6 6-6 6")
    val Close = icon("close", "M6 6l12 12M18 6L6 18")
    val Lock = icon("lock", "M7 11V8a5 5 0 0 1 10 0v3", "M5 11h14v10H5z")
    val Back = icon("back", "M15 5l-7 7 7 7")
    val Next = icon("next", "M9 5l7 7-7 7")
    val Up = icon("up", "M6 15l6-6 6 6")
    val Down = icon("down", "M6 9l6 6 6-6")
    val Sound = icon("sound", "M4 9h4l5-4v14l-5-4H4z", "M16.5 8.5a5 5 0 0 1 0 7", "M19 6a8.5 8.5 0 0 1 0 12")
    val Play = icon("play", filled = true, "M8 5l11 7-11 7z")
    val Pause = icon("pause", filled = true, "M7 5h3v14H7z", "M14 5h3v14h-3z")
    val Undo = icon("undo", "M9 14L4 9l5-5", "M4 9h10a6 6 0 0 1 0 12h-3")
}
