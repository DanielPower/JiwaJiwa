package ca.danielpower.jstudy.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ca.danielpower.jstudy.core.Kanji
import ca.danielpower.jstudy.data.Entry
import ca.danielpower.jstudy.data.KanjiWordRow
import ca.danielpower.jstudy.data.levelLabel

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun KanjiScreen(state: Screen.KanjiPage, vm: AppViewModel) {
    val detail = state.detail
    val kanji = detail.kanji
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            WashiIconButton(WashiIcons.Back, "Back", vm::goBack)
        }
        Column(
            modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    if (kanji.strokes.isNotEmpty()) {
                        StrokeDiagram(kanji.strokes, Modifier.size(120.dp))
                    } else {
                        Text(kanji.literal, fontFamily = Gothic, fontSize = 88.sp, lineHeight = 100.sp)
                    }
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            kanji.meanings.take(4).joinToString(", "),
                            style = MaterialTheme.typography.titleLarge,
                        )
                        Text(
                            listOfNotNull(
                                if (kanji.strokeCount == 1) "1 stroke" else "${kanji.strokeCount} strokes",
                                gradeLabel(kanji.grade),
                            ).joinToString(" · "),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (kanji.jlpt > 0) LevelSeal(levelLabel(kanji.jlpt), Modifier.padding(start = 12.dp))
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface)
            }

            if (kanji.onyomi.isNotEmpty() || kanji.kunyomi.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SectionLabel(ui("Readings", "読み方"))
                    if (kanji.onyomi.isNotEmpty()) ReadingLine("On", kanji.onyomi, detail.readingsMet)
                    if (kanji.kunyomi.isNotEmpty()) ReadingLine("Kun", kanji.kunyomi, detail.readingsMet)
                    if (detail.readingsMet.isNotEmpty()) {
                        Text(
                            "Readings in red are ones your words use.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            if (kanji.strokes.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SectionLabel(ui("Stroke order", "書き順"))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        for (i in kanji.strokes.indices) StrokeStep(kanji.strokes, i, Modifier.size(52.dp))
                    }
                }
            }

            if (detail.yourWords.isNotEmpty()) {
                Column {
                    SectionLabel(ui("Your words", "あなたの単語"), Modifier.padding(bottom = 4.dp))
                    for (row in detail.yourWords) {
                        KanjiWordItem(row.entry, kanji.literal, row) { vm.openWord(row.entry.id) }
                    }
                }
            }

            if (detail.moreWords.isNotEmpty()) {
                Column {
                    SectionLabel(
                        if (detail.yourWords.isEmpty()) ui("Words", "単語") else ui("More words", "他の単語"),
                        Modifier.padding(bottom = 4.dp),
                    )
                    for (entry in detail.moreWords) {
                        KanjiWordItem(entry, kanji.literal, null) { vm.openWord(entry.id) }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

private fun gradeLabel(grade: Int?): String? = when (grade) {
    in 1..6 -> "Jōyō, grade $grade"
    8 -> "Jōyō, secondary school"
    9, 10 -> "Name kanji"
    else -> null
}

/** On'yomi or kun'yomi, with okurigana dimmed and the readings your words use in red. */
@Composable
private fun ReadingLine(label: String, readings: List<String>, met: Set<String>) {
    val dim = MaterialTheme.colorScheme.onSurfaceVariant
    val red = MaterialTheme.colorScheme.primary
    Row {
        Text(label, style = MaterialTheme.typography.labelLarge, color = dim, modifier = Modifier.width(44.dp).padding(top = 3.dp))
        Text(
            buildAnnotatedString {
                readings.forEachIndexed { i, reading ->
                    if (i > 0) append("、")
                    val used = reading in met
                    withStyle(SpanStyle(color = if (used) red else Color.Unspecified, fontWeight = if (used) FontWeight.Bold else null)) {
                        append(reading.substringBefore('.'))
                    }
                    if ('.' in reading) withStyle(SpanStyle(color = if (used) red.copy(alpha = 0.6f) else dim)) { append(reading.substringAfter('.')) }
                }
            },
            fontFamily = Gothic,
            fontSize = 19.sp,
            lineHeight = 28.sp,
            modifier = Modifier.weight(1f),
        )
    }
}

/** A word on a kanji's page, with the kanji in red, and its reading and mastery when it's one of yours. */
@Composable
private fun KanjiWordItem(entry: Entry, kanji: String, mine: KanjiWordRow?, onClick: () -> Unit) {
    val red = MaterialTheme.colorScheme.primary
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    buildAnnotatedString {
                        for (c in entry.display) {
                            if (c.toString() == kanji) withStyle(SpanStyle(color = red)) { append(c) } else append(c)
                        }
                    },
                    fontFamily = Gothic,
                    style = MaterialTheme.typography.titleLarge,
                )
                if (entry.secondary != null) {
                    Spacer(Modifier.width(8.dp))
                    Text(entry.secondary, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Text(entry.gloss, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(12.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(levelLabel(entry.jlpt), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (mine != null) {
                Text("Mastery ${mine.mastery}", style = MaterialTheme.typography.labelMedium, color = red)
            }
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

// ------------------------------------------------------------------ strokes

/** KanjiVG draws on a 109-unit square. */
private const val KANJIVG_SIZE = 109f

private const val STROKE_MILLIS = 450

private fun parseStrokes(strokes: List<String>): List<Path> = strokes.map { PathParser().parsePathString(it).toPath() }

/** The kanji drawn a stroke at a time. Tap to draw it again. */
@Composable
fun StrokeDiagram(strokes: List<String>, modifier: Modifier = Modifier) {
    val paths = remember(strokes) { parseStrokes(strokes) }
    var replays by remember { mutableIntStateOf(0) }
    val progress = remember(strokes) { Animatable(0f) }
    LaunchedEffect(strokes, replays) {
        progress.snapTo(0f)
        progress.animateTo(paths.size.toFloat(), tween(paths.size * STROKE_MILLIS, easing = LinearEasing))
    }
    val ink = MaterialTheme.colorScheme.onSurface
    val guide = MaterialTheme.colorScheme.outlineVariant
    Canvas(
        modifier
            .border(1.dp, guide, WashiShape)
            .clickable { replays++ },
    ) {
        guides(guide)
        val done = progress.value.toInt()
        val measure = PathMeasure()
        inKanjiSpace {
            paths.forEachIndexed { i, path ->
                when {
                    i < done -> drawPath(path, ink, style = INK)
                    i == done -> {
                        measure.setPath(path, false)
                        val part = Path()
                        measure.getSegment(0f, measure.length * (progress.value - done), part, true)
                        drawPath(part, ink, style = INK)
                    }
                }
            }
        }
    }
}

/** The strokes up to [index], the last of them in red. */
@Composable
private fun StrokeStep(strokes: List<String>, index: Int, modifier: Modifier) {
    val paths = remember(strokes) { parseStrokes(strokes) }
    val ink = MaterialTheme.colorScheme.onSurface
    val red = MaterialTheme.colorScheme.primary
    val guide = MaterialTheme.colorScheme.outlineVariant
    Canvas(modifier.border(1.dp, guide, WashiShape)) {
        guides(guide)
        inKanjiSpace {
            for (i in 0 until index) drawPath(paths[i], ink.copy(alpha = 0.35f), style = INK)
            drawPath(paths[index], red, style = INK)
        }
    }
}

private val INK = Stroke(width = 4f, cap = StrokeCap.Round, join = StrokeJoin.Round)

/** Dashed centre lines, as on practice paper. */
private fun DrawScope.guides(color: Color) {
    val dash = PathEffect.dashPathEffect(floatArrayOf(6f, 6f))
    drawLine(color, Offset(size.width / 2, 0f), Offset(size.width / 2, size.height), pathEffect = dash)
    drawLine(color, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), pathEffect = dash)
}

private fun DrawScope.inKanjiSpace(block: DrawScope.() -> Unit) {
    val factor = minOf(size.width, size.height) / KANJIVG_SIZE
    withTransform({ scale(factor, factor, pivot = Offset.Zero) }, block)
}

// ------------------------------------------------------------------ links

/** The kanji in a word, each with its first meanings, opening its page when tapped. */
@Composable
fun KanjiLinks(kanji: List<Kanji>, onOpen: (String) -> Unit, compact: Boolean = false) {
    val size: Dp = if (compact) 36.dp else 44.dp
    Column(verticalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 6.dp)) {
        for (k in kanji) {
            Row(
                Modifier.fillMaxWidth().clickable { onOpen(k.literal) }.padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(size).border(1.dp, MaterialTheme.colorScheme.outlineVariant, WashiShape), contentAlignment = Alignment.Center) {
                    Text(k.literal, fontFamily = Gothic, fontSize = if (compact) 22.sp else 26.sp, lineHeight = if (compact) 26.sp else 30.sp)
                }
                Spacer(Modifier.width(12.dp))
                Text(
                    k.meanings.take(3).joinToString(", "),
                    style = if (compact) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodyLarge,
                    color = if (compact) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}
