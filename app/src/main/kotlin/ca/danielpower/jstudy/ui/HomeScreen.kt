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
import androidx.compose.material3.HorizontalDivider
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
import ca.danielpower.jstudy.core.PlanAlt
import ca.danielpower.jstudy.core.PlanStep
import ca.danielpower.jstudy.core.ReadKind
import ca.danielpower.jstudy.core.Recent
import ca.danielpower.jstudy.core.StepKind
import ca.danielpower.jstudy.core.StepState
import ca.danielpower.jstudy.core.Streak
import ca.danielpower.jstudy.core.TodayPlan
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
 * Home: the day's plan, then how far you've come. The plan is one card with
 * a quieter second option under it; below it the week's streak, the words
 * graph, and the words and grammar met this week. The sections spread out
 * to fill the screen, within limits, before it has to scroll.
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
            TodayCard(d, vm)
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

// ------------------------------------------------------------------ today

/**
 * The day's plan: kana while you're learning them, a chapter, then
 * reviews, each ticked off once done. The step to do now has the arrow,
 * and tapping it starts that step. Once all are done, a 済 instead.
 */
@Composable
private fun TodayCard(d: Dashboard, vm: AppViewModel) {
    val plan = d.plan
    val colors = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        val card = Modifier
            .fillMaxWidth()
            .background(colors.surfaceContainerLow, WashiShape)
            .border(1.dp, colors.outlineVariant, WashiShape)
        if (plan.done) {
            Row(
                card.padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    CardTag("Done for today", rightGreen())
                    Text("See you tomorrow", style = MaterialTheme.typography.titleMedium)
                    Text(daySummary(d), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                }
                DoneStamp()
            }
        } else {
            Column(card.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp)) {
                CardTag("Today", colors.primary)
                plan.steps.forEachIndexed { i, step ->
                    // Under the text, clear of the markers.
                    if (i > 0) HorizontalDivider(Modifier.padding(start = 34.dp), color = colors.outlineVariant)
                    StepRow(i + 1, step, d, vm)
                }
            }
        }
        altLine(d, vm)?.let { (text, onClick) ->
            Text(
                buildAnnotatedString {
                    append("or ")
                    withStyle(SpanStyle(color = colors.secondary, fontWeight = FontWeight.Bold)) { append(text) }
                },
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
                modifier = Modifier.clickable(onClick = onClick).padding(horizontal = 2.dp, vertical = 4.dp),
            )
        }
    }
}

@Composable
private fun CardTag(text: String, color: Color) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.sp,
        color = color,
    )
}

/** One step of the plan: its marker, what it is, and for the step to do now, the arrow. */
@Composable
private fun StepRow(number: Int, step: PlanStep<ChapterEntry>, d: Dashboard, vm: AppViewModel) {
    val colors = MaterialTheme.colorScheme
    val now = step.state == StepState.NOW
    val text = stepText(step, d)
    val seal = colors.primary
    // How far through a chapter you left part way, as a bar along the row's foot.
    val progress = step.read?.takeIf { now && step.readKind == ReadKind.CONTINUE && it.pages > 0 }?.let { (it.resumePage + 1f) / it.pages }
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .let { if (now) it.clickable(onClick = vm::continueToday) else it }
            .drawBehind {
                progress?.let { done ->
                    val bar = 3.dp.toPx()
                    drawRect(seal, Offset(0f, size.height - bar), Size(size.width * done, bar))
                }
            }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        StepMarker(number, step.state)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            if (now) {
                Text(text.title, style = MaterialTheme.typography.titleMedium)
            } else {
                Text(text.title, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
            }
            if (text.sub.isNotEmpty()) {
                Text(text.sub, style = MaterialTheme.typography.bodySmall, color = text.subColor ?: colors.onSurfaceVariant)
            }
        }
        if (now) {
            Box(
                Modifier.size(44.dp).background(colors.onSurface, WashiShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(WashiIcons.Forward, contentDescription = null, tint = colors.surface, modifier = Modifier.size(22.dp))
            }
        }
    }
}

/** A step's number in a circle: ink for the one to do now, an outline for later ones, and a green tick once done. */
@Composable
private fun StepMarker(number: Int, state: StepState) {
    val colors = MaterialTheme.colorScheme
    val circle = Modifier.size(22.dp)
    Box(
        when (state) {
            StepState.NOW -> circle.background(colors.onSurface, CircleShape)
            StepState.DONE -> circle.background(rightGreen(), CircleShape)
            StepState.LATER -> circle.border(1.5.dp, colors.outlineVariant, CircleShape)
        },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            if (state == StepState.DONE) "✓" else "$number",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = if (state == StepState.LATER) colors.onSurfaceVariant else colors.surface,
        )
    }
}

/** What a step's row says; [subColor] is null for the usual muted line. */
private class StepText(val title: String, val sub: String, val subColor: Color? = null)

@Composable
private fun stepText(step: PlanStep<ChapterEntry>, d: Dashboard): StepText {
    val done = step.state == StepState.DONE
    return when (step.kind) {
        StepKind.KANA -> {
            val kana = d.kana
            when {
                // The day the last kana came clear.
                kana == null -> StepText("All kana clear", "Stories drop the romaji")
                done -> StepText("Kana practice", if (step.count == 1) "1 kana practiced" else "${step.count} kana practiced")
                else -> StepText(
                    "Kana practice",
                    listOfNotNull(
                        kana.new.takeIf { it > 0 }?.let { "$it new" },
                        kana.check.takeIf { it > 0 }?.let { "$it to check" },
                    ).joinToString(", ") + " · ${kana.minutes} min",
                )
            }
        }
        StepKind.READ -> {
            val c = step.read
            when {
                step.full -> StepText("Today's new words are in", newWords(d.pace.newToday))
                done || c == null -> StepText(
                    d.readToday?.takeIf { step.count == 1 }?.let { "Read ${it.label}" } ?: "Read ${chapters(step.count)}",
                    wordCount(d.pace.newToday).sentence(),
                )
                else -> readText(c, step.readKind, rightGreen())
            }
        }
        StepKind.REVIEW -> when {
            done -> {
                val s = d.plan.soFar
                StepText("Review", if (s.answers > 0) "${answers(s.answers)}, ${100 * s.right / s.answers}% right" else "Nothing was due")
            }
            step.backlog -> StepText(
                "Catch up on reviews",
                if (d.reviewsLater > 0) "${step.count} today · ${d.reviewsLater} more after that" else "${step.count} due today",
            )
            step.count == 0 -> StepText(
                "Review",
                if ((d.words.lastOrNull()?.seen ?: 0) == 0) "The words from your story come back here" else "Today's new words come back here",
            )
            else -> StepText("Review ${cards(step.count)}", reviewSub(step, d))
        }
    }
}

/** A chapter to read, by how Home suggests it. */
private fun readText(c: Candidate<ChapterEntry>, kind: ReadKind?, green: Color): StepText {
    val e = c.item
    return when (kind) {
        ReadKind.CONTINUE -> StepText("Continue ${chapterTitle(e)}", "Page ${c.resumePage + 1}" + if (c.pages > 0) " of ${c.pages}" else "")
        ReadKind.SIDE -> StepText(
            "Read ${chapterTitle(e)}",
            "Nothing new to learn" + if (e.series.side) " · side story" else "",
            green,
        )
        ReadKind.START -> StepText(
            "Read ${chapterTitle(e)}",
            listOfNotNull("a short grammar lesson first".takeIf { c.teachesGrammar }, wordCount(c.newWords)).joinToString(" · ").sentence(),
        )
        else -> StepText(
            "Read ${chapterTitle(e)}",
            listOfNotNull(e.newTopic?.let { "grammar: ${it.title}" }, wordCount(c.newWords)).joinToString(" · ").sentence(),
        )
    }
}

/** What's in the reviews: the words read today and the ones from before, or the chapter's still to come. */
private fun reviewSub(step: PlanStep<ChapterEntry>, d: Dashboard): String {
    val ahead = d.plan.steps.firstOrNull { it.kind == StepKind.READ && it.state != StepState.DONE }?.read
    if (ahead != null && ahead.newWords > 0) return "Plus the new words from ${ahead.item.label}"
    val today = d.newWordsDue.coerceAtMost(step.count)
    return when {
        today == 0 -> ""
        today == step.count -> "Today's new words"
        else -> "Today's new words and ${step.count - today} from before"
    }
}

/** The Done card's line: what the day held. */
private fun daySummary(d: Dashboard): String {
    val s = d.plan.soFar
    val parts = listOfNotNull(
        "kana".takeIf { s.kana > 0 },
        s.chapters.takeIf { it > 0 }?.let { chapters(it) },
        s.answers.takeIf { it > 0 }?.let { if (it == 1) "1 review" else "$it reviews" },
    )
    return when {
        parts.isEmpty() -> if (d.plan.steps.none { it.kind == StepKind.READ }) "You've read every story" else "Nothing was due today"
        parts.size == 1 -> parts[0].sentence()
        else -> (parts.dropLast(1).joinToString(", ") + " and " + parts.last()).sentence()
    }
}

/** The line under the card, and what tapping it does. */
private fun altLine(d: Dashboard, vm: AppViewModel): Pair<String, () -> Unit>? =
    when (val alt = d.plan.alt) {
        is PlanAlt.KanaCheck -> "a quick kana check · ${alt.count}" to { vm.startKana() }
        is PlanAlt.KeepReading -> {
            val e = alt.chapter.item
            (if (alt.kind == ReadKind.CONTINUE) "continue ${e.label}" else "keep reading ${e.label}") to { vm.openStory(e.story.id) }
        }
        is PlanAlt.ReadAgain -> d.readAgain?.let { again -> "read ${again.entry.label} again" to { vm.openStory(again.entry.story.id) } }
        null -> null
    }

/**
 * The main button at the end of kana practice, a chapter or reviews: on to
 * the day's next step ("Next: review 6 cards"), or "Done for today".
 */
fun nextStepLabel(plan: TodayPlan<ChapterEntry>): String {
    val step = plan.now ?: return "Done for today"
    return when (step.kind) {
        StepKind.KANA -> "Next: kana practice"
        StepKind.READ -> {
            val name = step.read?.item?.label
            when {
                name == null -> "Next: read"
                step.readKind == ReadKind.CONTINUE -> "Next: continue $name"
                else -> "Next: read $name"
            }
        }
        StepKind.REVIEW -> "Next: review ${cards(step.count)}"
    }
}

/** "Arrival 1 · I'm here", or a one-chapter story's title. */
private fun chapterTitle(e: ChapterEntry) = if (e.series.single) e.series.title else "${e.label} · ${e.chapter.story.title}"

private fun wordCount(n: Int) = if (n == 0) "nothing new" else newWords(n)

private fun cards(n: Int) = if (n == 1) "1 card" else "$n cards"

private fun chapters(n: Int) = if (n == 1) "1 chapter" else "$n chapters"

private fun answers(n: Int) = if (n == 1) "1 answer" else "$n answers"

/** Sentence case: the first letter up. */
private fun String.sentence() = replaceFirstChar { it.uppercase() }

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
private fun Modifier.dashedOutline(color: Color, radius: Dp, dash: Float = 4f): Modifier = drawBehind {
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
