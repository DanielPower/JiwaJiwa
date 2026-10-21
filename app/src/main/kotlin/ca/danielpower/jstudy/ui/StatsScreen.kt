package ca.danielpower.jstudy.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ca.danielpower.jstudy.core.MasteryRules
import ca.danielpower.jstudy.data.LevelStats
import ca.danielpower.jstudy.data.Stats
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val BAR_TOP = RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp)
private val DAY = DateTimeFormatter.ofPattern("MMM d")

@Composable
fun StatsScreen(state: Screen.StatsPage, vm: AppViewModel) {
    val s = state.stats
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            WashiIconButton(WashiIcons.Back, "Back", vm::openReviewTab)
            Text("Stats", style = MaterialTheme.typography.headlineMedium)
        }
        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                Tile("Words started", "${s.wordsStarted}", Modifier.weight(1f))
                Tile("Recall, 30 days", percent(s.recall30) ?: "–", Modifier.weight(1f))
            }
            Row(Modifier.fillMaxWidth()) {
                Tile("Study days", "${s.studyDays}", Modifier.weight(1f))
                Tile("Answers", "${s.totalAnswers}", Modifier.weight(1f))
            }
            Text(
                "Recall is how often you got a card right when it came back for review, not counting words " +
                    "you were still learning. The schedule aims for 90%." +
                    (percent(s.recallAll)?.let { " All time: $it." } ?: ""),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Heading("Reviews due, next 30 days")
            var dueDay by remember { mutableStateOf<Int?>(null) }
            Bars(s.forecast.map { it to it }, MaterialTheme.washi.verdigris, dueDay) { dueDay = if (dueDay == it) null else it }
            val dueIndex = dueDay ?: 0
            Caption(
                count(s.forecast[dueIndex], "review") + " " + dayName(s.today, dueIndex) +
                    if (dueDay == null) " · ${s.forecast.sum()} in all" else "",
            )

            Heading("Answers, last 30 days")
            var answerDay by remember { mutableStateOf<Int?>(null) }
            Bars(s.answers, MaterialTheme.washi.pine, answerDay, restColor = MaterialTheme.colorScheme.error) { answerDay = if (answerDay == it) null else it }
            Legend(listOf("Right" to MaterialTheme.washi.pine, "Wrong" to MaterialTheme.colorScheme.error))
            val answerIndex = answerDay ?: s.answers.lastIndex
            val (total, right) = s.answers[answerIndex]
            Caption(
                count(total, "answer") + " " + dayName(s.today, answerIndex - s.answers.lastIndex) +
                    if (total > 0) ", $right right" else "",
            )

            Heading("Words by level")
            for (level in s.levels) LevelRow(level)
            Legend(listOf("Mastered" to masteryColor(MasteryRules.OFF_AT), "Known" to masteryColor(MasteryRules.HIDE_AT), "Seen" to masteryColor(0)))
            Spacer(Modifier.height(24.dp))
        }
    }
}

private fun percent(p: Pair<Int, Int>): String? =
    if (p.second == 0) null else "${(100.0 * p.first / p.second).toInt()}%"

private fun count(n: Int, noun: String) = if (n == 1) "1 $noun" else "$n ${noun}s"

/** "today", "tomorrow", "yesterday" or a date, for a day [offset] days from today. */
private fun dayName(today: Long, offset: Int): String = when (offset) {
    0 -> "today"
    1 -> "tomorrow"
    -1 -> "yesterday"
    else -> "on " + LocalDate.ofEpochDay(today + offset).format(DAY)
}

@Composable
private fun Heading(text: String) {
    Column(Modifier.padding(top = 12.dp)) {
        HorizontalDivider(color = MaterialTheme.colorScheme.onSurface)
        SectionLabel(text, Modifier.padding(top = 4.dp))
    }
}

@Composable
private fun Caption(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun Tile(label: String, value: String, modifier: Modifier) {
    Column(modifier) {
        Text(value, fontFamily = Gothic, fontWeight = FontWeight.Bold, fontSize = 30.sp)
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * One bar per day. Each value is (total, part): the part is in [color] and
 * the rest above it in [restColor]. Tap a bar to select it; the others fade.
 */
@Composable
private fun Bars(values: List<Pair<Int, Int>>, color: Color, selected: Int?, restColor: Color = color, onSelect: (Int) -> Unit) {
    val max = values.maxOf { it.first }.coerceAtLeast(1)
    val track = MaterialTheme.colorScheme.surfaceVariant
    Row(Modifier.fillMaxWidth().height(110.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        values.forEachIndexed { i, (total, part) ->
            val dim = selected != null && selected != i
            Box(
                Modifier.weight(1f).fillMaxHeight().clickable { onSelect(i) },
                contentAlignment = Alignment.BottomCenter,
            ) {
                if (total == 0) {
                    // A hairline so empty days still show where they are.
                    Box(Modifier.fillMaxWidth().height(2.dp).background(track))
                } else {
                    val alpha = if (dim) 0.45f else 1f
                    Column(Modifier.fillMaxWidth().fillMaxHeight(total.toFloat() / max)) {
                        val rest = total - part
                        if (rest > 0) {
                            Box(
                                Modifier.fillMaxWidth().weight(rest.toFloat())
                                    .background(restColor.copy(alpha = alpha), BAR_TOP),
                            )
                        }
                        if (part > 0) {
                            Box(
                                Modifier.fillMaxWidth().weight(part.toFloat())
                                    .background(color.copy(alpha = alpha), if (rest > 0) RoundedCornerShape(0.dp) else BAR_TOP),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LevelRow(level: LevelStats) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(level.label, fontFamily = Gothic, fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.width(44.dp))
            Text(
                "${level.started} of ${level.total} started · ${level.known} known · ${level.mastered} mastered",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row(
            Modifier.fillMaxWidth().height(10.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(2.dp)),
        ) {
            val total = level.total.coerceAtLeast(1).toFloat()
            val parts = listOf(
                level.mastered to masteryColor(MasteryRules.OFF_AT),
                level.known - level.mastered to masteryColor(MasteryRules.HIDE_AT),
                level.started - level.known to masteryColor(0),
                level.total - level.started to Color.Transparent,
            )
            for ((n, color) in parts) {
                if (n <= 0) continue
                Box(Modifier.weight(n / total).fillMaxHeight().background(color))
            }
        }
    }
}

@Composable
private fun Legend(items: List<Pair<String, Color>>) {
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
        for ((label, color) in items) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).background(color, RoundedCornerShape(2.dp)))
                Spacer(Modifier.width(6.dp))
                Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
