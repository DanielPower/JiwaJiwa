package ca.danielpower.jstudy.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlin.math.ceil

private val CELL = 14.dp
private val GAP = 3.dp
private val SHAPE = RoundedCornerShape(3.dp)

/**
 * Cards answered each study day, one column per week ending with this week,
 * Sunday at the top. Tap a day to see its count.
 */
@Composable
fun StudyHeatmap(answersPerDay: Map<Long, Int>, today: Long) {
    var selected by remember(today) { mutableStateOf<Long?>(null) }
    val max = answersPerDay.values.maxOrNull() ?: 0
    val empty = MaterialTheme.colorScheme.surfaceVariant
    val full = MaterialTheme.colorScheme.primary

    fun color(count: Int): Color {
        if (count <= 0 || max == 0) return empty
        // Four steps, relative to the busiest day shown.
        val level = ceil(4.0 * count / max).toInt().coerceIn(1, 4)
        return full.copy(alpha = level / 4f)
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val weeks = ((maxWidth + GAP) / (CELL + GAP)).toInt().coerceIn(1, 53)
            // Sunday = 0, so today's row is its day of week.
            val todayRow = LocalDate.ofEpochDay(today).dayOfWeek.value % 7
            val firstDay = today - todayRow - 7L * (weeks - 1)
            Row(horizontalArrangement = Arrangement.spacedBy(GAP)) {
                for (week in 0 until weeks) {
                    Column(verticalArrangement = Arrangement.spacedBy(GAP)) {
                        for (row in 0 until 7) {
                            val day = firstDay + week * 7L + row
                            if (day > today) {
                                Spacer(Modifier.size(CELL))
                            } else {
                                Cell(
                                    color = color(answersPerDay[day] ?: 0),
                                    outlined = day == selected || (selected == null && day == today),
                                    size = CELL,
                                    onClick = { selected = if (selected == day) null else day },
                                )
                            }
                        }
                    }
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            val day = selected ?: today
            val count = answersPerDay[day] ?: 0
            val date = if (day == today) {
                "today"
            } else {
                "on " + LocalDate.ofEpochDay(day).format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
            }
            Text(
                (if (count == 1) "1 card" else "$count cards") + " $date",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Text("Less", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(4.dp))
            for (level in 0..4) {
                Cell(if (level == 0) empty else full.copy(alpha = level / 4f), outlined = false, size = 10.dp)
                Spacer(Modifier.width(2.dp))
            }
            Spacer(Modifier.width(2.dp))
            Text("More", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun Cell(color: Color, outlined: Boolean, size: Dp, onClick: (() -> Unit)? = null) {
    var modifier = Modifier.size(size).background(color, SHAPE)
    if (outlined) modifier = modifier.border(1.5.dp, MaterialTheme.colorScheme.onSurface, SHAPE)
    if (onClick != null) modifier = modifier.clickable(onClick = onClick)
    Spacer(modifier)
}
