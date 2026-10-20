package ca.danielpower.jstudy.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ca.danielpower.jstudy.core.Candidate
import ca.danielpower.jstudy.core.NextUp
import ca.danielpower.jstudy.core.ReadKind
import ca.danielpower.jstudy.core.Recent
import ca.danielpower.jstudy.core.Streak
import ca.danielpower.jstudy.core.WordDay
import ca.danielpower.jstudy.data.ChapterEntry
import ca.danielpower.jstudy.data.Dashboard
import ca.danielpower.jstudy.data.RecentItem
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Home: what to do next, then how far you've come. The next step is one
 * card with a quieter second option under it; below it the week's streak,
 * the words graph, and the words and grammar met this week. The sections
 * spread out to fill the screen, within limits, before it has to scroll.
 */
@Composable
fun HomeScreen(d: Dashboard, vm: AppViewModel) {
    // At least a screen tall, so the sections can spread out to fill it; past that it scrolls.
    BoxWithConstraints(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .heightIn(min = maxHeight)
                .padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 8.dp),
            verticalArrangement = FillSpacing(min = 18.dp, max = 36.dp),
        ) {
            HomeHeader(d.today, vm::openSettings)
            NextCard(d, vm)
            StreakWeek(d.activeDays, d.streak, d.today)
            WordsSection(d.words)
            // The version keeps close to Recent rather than taking a gap of its own.
            Column {
                RecentSection(d, vm)
                VersionLine()
            }
        }
    }
}

/** The installed build's version, small at the foot of Home. */
@Composable
private fun VersionLine() {
    val context = LocalContext.current
    val version = remember { context.packageManager.getPackageInfo(context.packageName, 0).versionName } ?: return
    Text(
        "v$version",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
    )
}

/**
 * Even gaps between items that grow to fill the column, from [min] up to
 * [max]. The column asks for [min] gaps, so when that doesn't fit the screen
 * it scrolls; past [max] the leftover space goes to the bottom.
 */
private class FillSpacing(private val min: Dp, private val max: Dp) : Arrangement.Vertical {
    override val spacing = min

    override fun Density.arrange(totalSize: Int, sizes: IntArray, outPositions: IntArray) {
        val free = totalSize - sizes.sum()
        val gap = if (sizes.size < 2) 0 else (free / (sizes.size - 1)).coerceIn(min.roundToPx(), max.roundToPx())
        var y = 0
        sizes.forEachIndexed { i, size ->
            outPositions[i] = y
            y += size + gap
        }
    }
}

@Composable
private fun HomeHeader(today: Long, onSettings: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(
            Modifier.size(36.dp).background(MaterialTheme.colorScheme.primary, WashiShape),
            contentAlignment = Alignment.Center,
        ) {
            Text("学", fontFamily = Gothic, fontWeight = FontWeight.Bold, fontSize = 22.sp, color = MaterialTheme.colorScheme.onPrimary)
        }
        Text("Jiwa Jiwa", fontFamily = Gothic, fontWeight = FontWeight.Bold, fontSize = 22.sp, modifier = Modifier.weight(1f))
        val day = LocalDate.ofEpochDay(today)
        Text(
            day.format(DateTimeFormatter.ofPattern("M月d日（E）", Locale.JAPANESE)),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        // Settings are rarely needed, so they're a gear here rather than a tab.
        Box(Modifier.padding(start = 4.dp)) { WashiIconButton(WashiIcons.Settings, "Settings", onSettings) }
    }
}

// ------------------------------------------------------------------ next step

/** What the next-step card says, and what tapping it and its second line do. */
private class NextContent(
    val tag: String,
    val tagColor: Color,
    val title: String,
    val sub: String,
    /** Null for a card with nothing to do, which shows a 済 stamp instead of an arrow. */
    val onClick: (() -> Unit)?,
    val alt: String? = null,
    val onAlt: () -> Unit = {},
    /** How far through a chapter you left part way, from 0 to 1, shown as a bar along the card's foot. */
    val progress: Float? = null,
)

@Composable
private fun NextCard(d: Dashboard, vm: AppViewModel) {
    val story = nextContent(d.next, d, vm)
    // While kana are due, practising them comes first, with the story on the "or" line.
    val kana = d.kana?.takeIf { it.due > 0 }
    val c = if (kana == null) story else NextContent(
        "Kana practice",
        MaterialTheme.colorScheme.secondary,
        listOfNotNull(
            kana.new.takeIf { it > 0 }?.let { "$it new kana" },
            kana.check.takeIf { it > 0 }?.let { "$it to check" },
        ).joinToString(", "),
        "About ${kana.minutes} minute" + if (kana.minutes == 1) "" else "s",
        vm::startKana,
        (d.next as? NextUp.Read)?.let { "read ${shortName(it.chapter.item)}" },
        { (d.next as? NextUp.Read)?.let { vm.openStory(it.chapter.item.story.id) } },
    )
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        val seal = MaterialTheme.colorScheme.primary
        Row(
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceContainerLow, WashiShape)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, WashiShape)
                .let { if (c.onClick != null) it.clickable(onClick = c.onClick) else it }
                .drawBehind {
                    c.progress?.let { done ->
                        val bar = 3.dp.toPx()
                        val inset = 1.dp.toPx()
                        drawRect(seal, Offset(inset, size.height - inset - bar), Size((size.width - 2 * inset) * done, bar))
                    }
                }
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    c.tag.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    color = c.tagColor,
                )
                Text(c.title, style = MaterialTheme.typography.titleMedium)
                if (c.sub.isNotEmpty()) {
                    Text(c.sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (c.onClick != null) {
                Box(
                    Modifier.size(44.dp).background(MaterialTheme.colorScheme.onSurface, WashiShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(WashiIcons.Forward, contentDescription = null, tint = MaterialTheme.colorScheme.surface, modifier = Modifier.size(22.dp))
                }
            } else {
                DoneStamp()
            }
        }
        if (c.alt != null) {
            Text(
                buildAnnotatedString {
                    append("or ")
                    withStyle(SpanStyle(color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.Bold)) { append(c.alt) }
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.clickable(onClick = c.onAlt).padding(horizontal = 2.dp, vertical = 4.dp),
            )
        }
    }
}

@Composable
private fun nextContent(next: NextUp<ChapterEntry>, d: Dashboard, vm: AppViewModel): NextContent {
    val colors = MaterialTheme.colorScheme
    val green = rightGreen()
    val newToday = d.pace.newToday
    fun reviewAlt(due: Int) = if (due > 0) "review $due due" else null
    return when (next) {
        is NextUp.Read -> {
            val c = next.chapter
            val e = c.item
            val open: () -> Unit = { vm.openStory(e.story.id) }
            when (next.kind) {
                ReadKind.START -> NextContent("Start here", colors.primary, chapterName(e), seriesLine(e, c), open, reviewAlt(next.orReview), vm::startReview)
                ReadKind.CONTINUE -> NextContent(
                    "Continue · page ${c.resumePage + 1}" + if (c.pages > 0) " of ${c.pages}" else "",
                    colors.primary,
                    chapterName(e),
                    seriesLine(e, c),
                    open,
                    reviewAlt(next.orReview),
                    vm::startReview,
                    progress = if (c.pages > 0) (c.resumePage + 1f) / c.pages else null,
                )
                ReadKind.LESSON -> NextContent(
                    "Next · grammar lesson",
                    colors.secondary,
                    "${e.newTopic?.title ?: ""}, then ${shortName(e)}",
                    seriesLine(e, c),
                    open,
                    reviewAlt(next.orReview),
                    vm::startReview,
                )
                ReadKind.NEXT -> NextContent("Next", colors.primary, chapterName(e), seriesLine(e, c), open, reviewAlt(next.orReview), vm::startReview)
                ReadKind.SIDE -> NextContent(
                    "Nothing new to learn",
                    green,
                    chapterName(e),
                    "${newWords(newToday)} today · side story",
                    open,
                    reviewAlt(next.orReview),
                    vm::startReview,
                )
            }
        }
        is NextUp.Review -> if (next.backlog) {
            NextContent(
                "Reviews waiting",
                colors.tertiary,
                "Catch up on reviews",
                if (d.reviewsLater > 0) "${next.due} today · ${d.reviewsLater} more after that" else "${next.due} due today",
                vm::startReview,
                next.orRead?.let { "keep reading ${shortName(it.item)}" },
                { next.orRead?.let { vm.openStory(it.item.story.id) } },
            )
        } else {
            NextContent("${newWords(newToday)} today", colors.secondary, "Review ${next.due} due", "Today's new words are in", vm::startReview)
        }
        is NextUp.Done -> NextContent(
            "Done for today",
            green,
            next.tomorrow?.let { "${shortName(it.item)} tomorrow" } ?: "See you tomorrow",
            "${newWords(newToday)}, every review done",
            null,
        )
        NextUp.AllRead -> d.readAgain?.let { again ->
            val e = again.entry
            NextContent(
                "Read again",
                colors.primary,
                chapterName(e),
                listOfNotNull(
                    e.series.title.takeIf { !e.series.single },
                    when (again.due) {
                        0 -> null
                        1 -> "1 word to review"
                        else -> "${again.due} words to review"
                    },
                ).joinToString(" · "),
                { vm.openStory(e.story.id) },
            )
        } ?: NextContent("All read", green, "You've read every story", "More stories are on the way", null)
    }
}

/** "Ch. 5 · The interview", or a one-chapter story's title. */
private fun chapterName(e: ChapterEntry) =
    if (e.series.single) e.series.title else "Ch. ${e.chapter.number} · ${e.chapter.story.title}"

/** "Ch. 5", or a one-chapter story's title. */
private fun shortName(e: ChapterEntry) = if (e.series.single) e.series.title else "Ch. ${e.chapter.number}"

/** The story it's in and what's new in it: "The Share House · 4 new words". */
private fun seriesLine(e: ChapterEntry, c: Candidate<ChapterEntry>) = listOfNotNull(
    e.series.title.takeIf { !e.series.single },
    when (c.newWords) {
        0 -> "nothing new"
        1 -> "1 new word"
        else -> "${c.newWords} new words"
    },
).joinToString(" · ")

/** A 済 ("done") seal, grained like the reader's 読了, where the card's arrow would be. */
@Composable
private fun DoneStamp() {
    val seal = MaterialTheme.colorScheme.primary
    Text(
        "済",
        modifier = Modifier.stampGrain().border(2.dp, seal, RoundedCornerShape(4.dp)).padding(horizontal = 8.dp, vertical = 1.dp),
        fontFamily = Gothic,
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp,
        color = seal,
    )
}

// ------------------------------------------------------------------ streak

/**
 * A week, Monday first, with each day you reviewed or finished a chapter
 * filled in, and the streak so far. Arrows step back to earlier weeks, as
 * far as the first study day.
 */
@Composable
private fun StreakWeek(days: Set<Long>, streak: Int, today: Long) {
    val thisWeek = Streak.weekOf(today)
    val firstWeek = days.minOrNull()?.let(Streak::weekOf) ?: thisWeek
    var week by rememberSaveable(today) { mutableLongStateOf(thisWeek) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        // The week and its arrows sit centred in the label row, so the week needs no line of its own.
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            SectionLabel("Streak", Modifier.align(Alignment.CenterStart))
            Row(verticalAlignment = Alignment.CenterVertically) {
                WeekArrow(WashiIcons.Back, "Previous week", enabled = week > firstWeek) { week -= 7 }
                Text(weekRange(week), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                WeekArrow(WashiIcons.Next, "Next week", enabled = week < thisWeek) { week += 7 }
            }
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(color = MaterialTheme.colorScheme.primary)) { append("$streak") }
                    append(if (streak == 1) " day" else " days")
                },
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.align(Alignment.CenterEnd),
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            for (i in 0..6) {
                val day = week + i
                DayDot(DAY_LETTERS[i], done = day in days, isToday = day == today, future = day > today)
            }
        }
    }
}

/** A small arrow for stepping between weeks, dimmed when there's no week to step to. */
@Composable
private fun WeekArrow(icon: ImageVector, description: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(32.dp).clickable(enabled = enabled, onClick = onClick).alpha(if (enabled) 1f else 0.3f),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = description, modifier = Modifier.size(18.dp))
    }
}

private const val DAY_LETTERS = "MTWTFSS"

@Composable
private fun DayDot(letter: Char, done: Boolean, isToday: Boolean, future: Boolean) {
    val seal = MaterialTheme.colorScheme.primary
    val line = MaterialTheme.colorScheme.outlineVariant
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        val dot = Modifier.size(26.dp)
        Box(
            when {
                done -> dot.background(seal, CircleShape)
                // Today stays open until something's done; it doesn't break the streak yet.
                isToday -> dot.dashedOutline(seal, 13.dp)
                future -> dot.alpha(0.5f).dashedOutline(line, 13.dp, dash = 2f)
                else -> dot.border(1.5.dp, line, CircleShape)
            },
            contentAlignment = Alignment.Center,
        ) {
            if (done) Text("✓", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimary)
        }
        Text(
            letter.toString(),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
            color = if (isToday) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** "Oct 13 – 19", or "Sep 29 – Oct 5" across months. */
private fun weekRange(monday: Long): String {
    val start = LocalDate.ofEpochDay(monday)
    val end = start.plusDays(6)
    val first = start.format(DateTimeFormatter.ofPattern("MMM d"))
    val last = end.format(DateTimeFormatter.ofPattern(if (end.month == start.month) "d" else "MMM d"))
    return "$first – $last"
}

/** A dashed outline with rounded corners of [radius]; [dash] is the dash length in dp. */
internal fun Modifier.dashedOutline(color: Color, radius: Dp, dash: Float = 4f): Modifier = drawBehind {
    val width = 1.5.dp.toPx()
    drawRoundRect(
        color = color,
        topLeft = Offset(width / 2, width / 2),
        size = size.copy(width = size.width - width, height = size.height - width),
        cornerRadius = CornerRadius(radius.toPx()),
        style = Stroke(width, pathEffect = PathEffect.dashPathEffect(floatArrayOf(dash.dp.toPx(), 3.dp.toPx()))),
    )
}

// ------------------------------------------------------------------ words graph

@Composable
private fun WordsSection(words: List<WordDay>) {
    var all by rememberSaveable { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionLabel("Words", Modifier.weight(1f))
            RangeToggle(all) { all = it }
        }
        val last = words.lastOrNull()
        if (last == null || last.seen == 0) {
            Text(
                "Your words will show here once you've read your first story.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().dashedOutline(MaterialTheme.colorScheme.outlineVariant, 4.dp).padding(20.dp),
            )
            return@Column
        }
        val shown = if (all) words else words.takeLast(GRAPH_DAYS)
        WordsChart(shown)
        // This week's gains, against the end of the day a week ago.
        val weekAgo = words.getOrNull(words.size - 8) ?: WordDay(0, 0, 0, 0)
        Row(Modifier.fillMaxWidth()) {
            Total(last.seen, "seen", last.seen - weekAgo.seen, MaterialTheme.colorScheme.tertiary, Modifier.weight(1f))
            Total(last.known, "known", last.known - weekAgo.known, MaterialTheme.colorScheme.secondary, Modifier.weight(1f))
            Total(last.mastered, "mastered", last.mastered - weekAgo.mastered, rightGreen(), Modifier.weight(1f))
        }
    }
}

private const val GRAPH_DAYS = 30

@Composable
private fun RangeToggle(all: Boolean, onChange: (Boolean) -> Unit) {
    val ink = MaterialTheme.colorScheme.onSurface
    Row(Modifier.border(1.dp, MaterialTheme.colorScheme.onSurfaceVariant, WashiShape)) {
        for ((label, value) in listOf("30 days" to false, "All" to true)) {
            val on = all == value
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = if (on) MaterialTheme.colorScheme.surface else ink,
                modifier = Modifier
                    .background(if (on) ink else Color.Transparent, WashiShape)
                    .clickable { onChange(value) }
                    .padding(horizontal = 10.dp, vertical = 3.dp),
            )
        }
    }
}

@Composable
private fun Total(value: Int, label: String, gain: Int, color: Color, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("$value", fontFamily = Gothic, fontWeight = FontWeight.Bold, fontSize = 20.sp, color = color, modifier = Modifier.alignByBaseline())
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.alignByBaseline())
        }
        if (gain != 0) {
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(color = color, fontWeight = FontWeight.Bold)) { append(if (gain > 0) "+$gain" else "$gain") }
                    append(" this week")
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Words seen, known and mastered over time as nested bands: every mastered
 * word is known, and every known word seen, so each band sits inside the
 * one before. Colours follow the furigana stages, as mastery does elsewhere.
 * Dragging across it shows the day under your finger and its three counts.
 */
@Composable
private fun WordsChart(days: List<WordDay>) {
    val measurer = rememberTextMeasurer()
    val axis = TextStyle(fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    val grid = MaterialTheme.colorScheme.outlineVariant
    val bands = listOf(
        MaterialTheme.colorScheme.tertiary to { d: WordDay -> d.seen },
        MaterialTheme.colorScheme.secondary to { d: WordDay -> d.known },
        rightGreen() to { d: WordDay -> d.mastered },
    )
    val top = niceCeiling(days.maxOf { it.seen })
    val ink = MaterialTheme.colorScheme.onSurface
    val tipBackground = MaterialTheme.colorScheme.surfaceContainerLow
    val tipStyle = TextStyle(fontSize = 11.sp, color = ink)
    // Where a finger is on the graph, while it's dragging across.
    var touch by remember(days) { mutableStateOf<Float?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        val drag = Modifier.pointerInput(days) {
            detectHorizontalDragGestures(
                onDragStart = { touch = it.x },
                onDragEnd = { touch = null },
                onDragCancel = { touch = null },
            ) { change, _ ->
                change.consume()
                touch = change.position.x
            }
        }
        Canvas(Modifier.fillMaxWidth().height(140.dp).then(drag)) {
            val labelWidth = measurer.measure("$top", axis).size.width + 6.dp.toPx()
            val left = labelWidth
            val width = size.width - left
            val height = size.height - 6.dp.toPx()
            val y0 = 3.dp.toPx()
            fun x(i: Int) = left + if (days.size == 1) width else width * i / (days.size - 1)
            fun y(v: Int) = y0 + height * (1 - v / top.toFloat())
            for (v in listOf(0, top / 2, top)) {
                drawLine(grid, Offset(left, y(v)), Offset(size.width, y(v)), 1.dp.toPx())
                val text = measurer.measure("$v", axis)
                drawText(text, topLeft = Offset(left - text.size.width - 6.dp.toPx(), y(v) - text.size.height / 2))
            }
            for ((color, value) in bands) {
                val line = Path().apply {
                    days.forEachIndexed { i, d -> if (i == 0) moveTo(x(i), y(value(d))) else lineTo(x(i), y(value(d))) }
                }
                val area = Path().apply {
                    addPath(line)
                    lineTo(x(days.size - 1), y(0))
                    lineTo(x(0), y(0))
                    close()
                }
                drawPath(area, color.copy(alpha = 0.3f))
                drawPath(line, color, style = Stroke(1.5.dp.toPx()))
            }
            val at = touch?.let { tx ->
                if (days.size == 1) 0 else ((tx - left) / width * (days.size - 1)).roundToInt().coerceIn(0, days.size - 1)
            }
            if (at == null) {
                drawCircle(bands[0].first, 3.dp.toPx(), Offset(x(days.size - 1), y(days.last().seen)))
            } else {
                val day = days[at]
                val dash = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 3.dp.toPx()))
                drawLine(ink, Offset(x(at), y0), Offset(x(at), y(0)), 1.dp.toPx(), pathEffect = dash)
                for ((color, value) in bands) drawCircle(color, 3.dp.toPx(), Offset(x(at), y(value(day))))
                // The day and its counts in a box beside the line, on whichever side has room.
                val lines = listOf(
                    measurer.measure(LocalDate.ofEpochDay(day.day).format(DateTimeFormatter.ofPattern("MMM d")), tipStyle.copy(fontWeight = FontWeight.Bold)),
                ) + bands.zip(listOf("seen", "known", "mastered")).map { (band, label) ->
                    measurer.measure("${band.second(day)} $label", tipStyle.copy(color = band.first))
                }
                val pad = 6.dp.toPx()
                val boxWidth = lines.maxOf { it.size.width } + 2 * pad
                val boxHeight = lines.sumOf { it.size.height } + 2 * pad
                val gapX = 8.dp.toPx()
                val boxLeft = if (x(at) + gapX + boxWidth <= size.width) x(at) + gapX else x(at) - gapX - boxWidth
                drawRoundRect(tipBackground, Offset(boxLeft, y0), Size(boxWidth, boxHeight), CornerRadius(3.dp.toPx()))
                drawRoundRect(grid, Offset(boxLeft, y0), Size(boxWidth, boxHeight), CornerRadius(3.dp.toPx()), style = Stroke(1.dp.toPx()))
                var lineTop = y0 + pad
                for (text in lines) {
                    drawText(text, topLeft = Offset(boxLeft + pad, lineTop))
                    lineTop += text.size.height
                }
            }
        }
        Row(Modifier.fillMaxWidth()) {
            val format = DateTimeFormatter.ofPattern("MMM d")
            Text(LocalDate.ofEpochDay(days.first().day).format(format), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
            Text(LocalDate.ofEpochDay(days.last().day).format(format), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun newWords(n: Int) = if (n == 1) "1 new word" else "$n new words"

/** The next round number at or above [n]: 1, 2 or 5 times a power of ten, and even, so its half is whole. */
private fun niceCeiling(n: Int): Int {
    if (n <= 2) return 2
    val magnitude = 10.0.pow(ceil(log10(n.toDouble())) - 1)
    val step = listOf(1.0, 2.0, 5.0, 10.0).first { it * magnitude >= n } * magnitude
    return ceil(step).toInt().let { if (it % 2 == 0) it else it + 1 }
}

// ------------------------------------------------------------------ recent

/** The week's new words (gold) and grammar (indigo) together. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RecentSection(d: Dashboard, vm: AppViewModel) {
    val gold = MaterialTheme.colorScheme.tertiary
    val indigo = MaterialTheme.colorScheme.secondary
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionLabel("Recent", Modifier.weight(1f))
            if (d.recent.isNotEmpty()) {
                KeySwatch(gold, "words")
                Spacer(Modifier.width(10.dp))
                KeySwatch(indigo, "grammar")
            }
        }
        if (d.recent.isEmpty()) {
            Text(
                "Nothing new this week. Words and grammar you meet in stories show up here.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            // Two lines at most, then +N, which opens the row in place to show the rest of the week.
            var expanded by rememberSaveable { mutableStateOf(false) }
            val muted = MaterialTheme.colorScheme.onSurfaceVariant
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val measurer = rememberTextMeasurer()
                val chipText = MaterialTheme.typography.bodyMedium.copy(fontFamily = Gothic)
                val density = LocalDensity.current
                val shown = if (expanded) {
                    d.recent.size
                } else {
                    with(density) {
                        // A chip is its text plus padding and border; a dp spare each so rounding never wraps a third line.
                        val chrome = (2 * 8 + 2 + 1).dp.toPx()
                        fun width(text: String) = measurer.measure(text, chipText).size.width + chrome
                        Recent.fitting(
                            d.recent.map { width(it.label) },
                            maxWidth = constraints.maxWidth.toFloat(),
                            gap = 6.dp.toPx(),
                            lines = RECENT_LINES,
                        ) { hidden -> width("+$hidden") }
                    }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (item in d.recent.take(shown)) when (item) {
                        is RecentItem.NewWord -> Chip(item.word.display, gold, gold.copy(alpha = 0.4f)) { vm.openWord(item.word.id) }
                        is RecentItem.NewGrammar -> Chip(
                            item.topic.title,
                            indigo,
                            indigo.copy(alpha = 0.45f),
                            indigo.copy(alpha = 0.08f).compositeOver(MaterialTheme.colorScheme.surfaceContainerLow),
                        ) { vm.openGrammar(item.pageId) }
                    }
                    if (shown < d.recent.size) Chip("+${d.recent.size - shown}", gold, gold.copy(alpha = 0.4f)) { expanded = true }
                    if (expanded) Chip("Show less", muted, MaterialTheme.colorScheme.outlineVariant) { expanded = false }
                }
            }
        }
    }
}

/** What a recent item's chip says. */
private val RecentItem.label
    get() = when (this) {
        is RecentItem.NewWord -> word.display
        is RecentItem.NewGrammar -> topic.title
    }

private const val RECENT_LINES = 2

@Composable
private fun KeySwatch(color: Color, label: String) {
    Box(Modifier.size(8.dp).background(color, RoundedCornerShape(2.dp)))
    Spacer(Modifier.width(4.dp))
    Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun Chip(
    text: String,
    color: Color,
    border: Color,
    background: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    onClick: () -> Unit,
) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        fontFamily = Gothic,
        color = color,
        modifier = Modifier
            .background(background, WashiShape)
            .border(1.dp, border, WashiShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}
