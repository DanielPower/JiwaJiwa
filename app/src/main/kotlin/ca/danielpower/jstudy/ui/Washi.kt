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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

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

/** The seal colour, for "Right". */
@Composable
fun SealButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        shape = WashiShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
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
fun LevelSeal(label: String, modifier: Modifier = Modifier, tilted: Boolean = false, size: Dp = 40.dp) {
    val color = MaterialTheme.colorScheme.primary
    Box(
        modifier = modifier
            .then(if (tilted) Modifier.rotate(-6f) else Modifier)
            .size(size)
            .border(2.dp, color, WashiShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            fontFamily = Mincho,
            fontWeight = FontWeight.Bold,
            fontSize = if (label.length > 2) 11.sp else 14.sp,
            color = color,
        )
    }
}

/** A thin bar for mastery, 0 to 100. */
@Composable
fun MasteryBar(mastery: Int, modifier: Modifier = Modifier) {
    Box(modifier.height(4.dp).background(MaterialTheme.colorScheme.surfaceVariant)) {
        Box(
            Modifier
                .fillMaxWidth((mastery / 100f).coerceIn(0f, 1f))
                .height(4.dp)
                .background(MaterialTheme.colorScheme.onSurface),
        )
    }
}

// ------------------------------------------------------------------ navigation

enum class Tab(val label: String, val icon: ImageVector) {
    HOME("Home", WashiIcons.Home),
    DICTIONARY("Dictionary", WashiIcons.Search),
    STORIES("Stories", WashiIcons.Book),
    SETTINGS("Settings", WashiIcons.Settings),
}

@Composable
fun WashiNavBar(current: Tab, onSelect: (Tab) -> Unit) {
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
                    Icon(tab.icon, contentDescription = null, tint = color, modifier = Modifier.size(22.dp))
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
    private fun icon(name: String, vararg paths: String): ImageVector {
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
                fill = null,
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
    val Settings = icon(
        "settings",
        "M9 12a3 3 0 1 0 6 0a3 3 0 1 0 -6 0",
        "M12 3v3M12 18v3M3 12h3M18 12h3M5.6 5.6l2.1 2.1M16.3 16.3l2.1 2.1M5.6 18.4l2.1-2.1M16.3 7.7l2.1-2.1",
    )
    val Close = icon("close", "M6 6l12 12M18 6L6 18")
    val Back = icon("back", "M15 5l-7 7 7 7")
    val Undo = icon("undo", "M9 14L4 9l5-5", "M4 9h10a6 6 0 0 1 0 12h-3")
    val Delete = icon("delete", "M5 7h14M10 7V4h4v3M7 7l1 13h8l1-13")
}
