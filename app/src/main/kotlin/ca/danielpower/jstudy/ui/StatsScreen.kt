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
            WashiIconButton(WashiIcons.Back, "Back", vm::goHome)
            Text(ui("Stats", "統計"), style = MaterialTheme.typography.headlineMedium)
        }
        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                Tile(ui("Words started", "始めた単語"), "${s.wordsStarted}", Modifier.weight(1f))
                Tile("Recall, 30 days", percent(s.recall30) ?: "–", Modifier.weight(1f))
            }
            Row(Modifier.fillMaxWidth()) {
                Tile(ui("Study days", "勉強した日"), "${s.studyDays}", Modifier.weight(1f))
                Tile(ui("Answers", "回答"), "${s.totalAnswers}", Modifier.weight(1f))
            }
            Text(
                "Recall is how often you got a card right when it came back for review, not counting words " +
                    "you were still learning. Your target is set in Settings." +
                    (percent(s.recallAll)?.let { " All time: $it." } ?: ""),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Heading("Reviews due, next 30 days")
            var dueDay by remember { mutableStateOf<Int?>(null) }
            Bars(s.forecast.map { it to it }, dueDay) { dueDay = if (dueDay == it) null else it }
            val dueIndex = dueDay ?: 0
            Caption(
                count(s.forecast[dueIndex], "review") + " " + dayName(s.today, dueIndex) +
                    if (dueDay == null) " · ${s.forecast.sum()} in all" else "",
            )

            Heading("Answers, last 30 days")
            var answerDay by remember { mutableStateOf<Int?>(null) }
            Bars(s.answers, answerDay) { answerDay = if (answerDay == it) null else it }
            Legend(listOf(ui("Right", "正解") to 1f, ui("Wrong", "間違い") to WRONG_ALPHA))
            val answerIndex = answerDay ?: s.answers.lastIndex
            val (total, right) = s.answers[answerIndex]
            Caption(
                count(total, "answer") + " " + dayName(s.today, answerIndex - s.answers.lastIndex) +
                    if (total > 0) ", $right right" else "",
            )

            if (s.kana.isNotEmpty()) {
                Heading(ui("Kana", "かな", "仮名"))
                for (script in s.kana) LevelRow(script)
                Legend(listOf(ui("Mastered", "覚えた") to 1f, ui("Known", "知っている") to 0.6f, ui("Started", "始めた") to 0.3f))
            }

            Heading(ui("Words by level", "レベル別の単語"))
            for (level in s.levels) LevelRow(level)
            Legend(listOf(ui("Mastered", "覚えた") to 1f, ui("Known", "知っている") to 0.6f, ui("Started", "始めた") to 0.3f))
            Spacer(Modifier.height(24.dp))
        }
    }
}

private const val WRONG_ALPHA = 0.3f

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
 * One bar per day. Each value is (total, highlighted part): the highlighted
 * part is solid and the rest faint. Tap a bar to select it.
 */
@Composable
private fun Bars(values: List<Pair<Int, Int>>, selected: Int?, onSelect: (Int) -> Unit) {
    val max = values.maxOf { it.first }.coerceAtLeast(1)
    val ink = MaterialTheme.colorScheme.primary
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
                    Column(Modifier.fillMaxWidth().fillMaxHeight(total.toFloat() / max)) {
                        val rest = total - part
                        if (rest > 0) {
                            Box(
                                Modifier.fillMaxWidth().weight(rest.toFloat())
                                    .background(ink.copy(alpha = WRONG_ALPHA * if (dim) 0.5f else 1f), BAR_TOP),
                            )
                        }
                        if (part > 0) {
                            Box(
                                Modifier.fillMaxWidth().weight(part.toFloat())
                                    .background(ink.copy(alpha = if (dim) 0.45f else 1f), if (rest > 0) RoundedCornerShape(0.dp) else BAR_TOP),
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
    val ink = MaterialTheme.colorScheme.primary
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
                level.mastered to 1f,
                level.known - level.mastered to 0.6f,
                level.started - level.known to 0.3f,
                level.total - level.started to 0f,
            )
            for ((n, alpha) in parts) {
                if (n <= 0) continue
                Box(
                    Modifier.weight(n / total).fillMaxHeight()
                        .background(if (alpha == 0f) Color.Transparent else ink.copy(alpha = alpha)),
                )
            }
        }
    }
}

@Composable
private fun Legend(items: List<Pair<String, Float>>) {
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
        for ((label, alpha) in items) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).background(MaterialTheme.colorScheme.primary.copy(alpha = alpha), RoundedCornerShape(2.dp)))
                Spacer(Modifier.width(6.dp))
                Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
