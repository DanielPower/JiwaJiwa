package ca.danielpower.jstudy.ui

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ca.danielpower.jstudy.core.CardState
import ca.danielpower.jstudy.core.FlowWord
import ca.danielpower.jstudy.core.FuriganaMode
import ca.danielpower.jstudy.core.GrammarPoint
import ca.danielpower.jstudy.core.HorizontalText
import ca.danielpower.jstudy.core.MasteryRules
import ca.danielpower.jstudy.core.Narration
import ca.danielpower.jstudy.core.Pace
import ca.danielpower.jstudy.core.PathStory
import ca.danielpower.jstudy.core.ReadingWord
import ca.danielpower.jstudy.core.Speech
import ca.danielpower.jstudy.core.WordKind
import ca.danielpower.jstudy.core.unexplainedConjugation
import ca.danielpower.jstudy.data.ChapterEntry
import ca.danielpower.jstudy.data.LockInfo
import ca.danielpower.jstudy.data.ReaderFont
import ca.danielpower.jstudy.data.ReaderStyle
import ca.danielpower.jstudy.data.ReaderPlace
import ca.danielpower.jstudy.data.SeriesEntry
import ca.danielpower.jstudy.data.SpeechSettings
import ca.danielpower.jstudy.data.StoryReading
import ca.danielpower.jstudy.data.Word
import ca.danielpower.jstudy.data.levelLabel

// ------------------------------------------------------------------ list

@Composable
fun StoriesScreen(state: Screen.Stories, vm: AppViewModel) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Read", style = MaterialTheme.typography.headlineMedium)

        // The stories that come with the app, shown once a chapter is within reach.
        val shown = state.path.filter { it.shown }
        val pace = state.pace
        PathProgress(state.path)
        if (pace != null) PaceMeter(pace)
        if (pace?.nudge == true) {
            // Past today's limit, or behind on reviews: what you can read with nothing new comes first.
            NudgeCard(pace, onReview = vm::startReview)
            if (state.nothingNew.isNotEmpty()) {
                SectionLabel("Nothing new to learn", Modifier.padding(top = 8.dp))
                for (entry in state.nothingNew) NothingNewRow(entry) { vm.openStory(entry.story.id) }
            } else {
                Text(
                    "No stories with nothing new are open right now. Reviews make room for more.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            SectionLabel("Your path", Modifier.padding(top = 8.dp))
            val listed = state.nothingNew.filter { it.series.single }.map { it.series.id }.toSet()
            for (entry in shown.filter { it.series.id !in listed }) {
                Box(Modifier.alpha(0.65f)) { PathRow(entry, state.place) { vm.openPathStory(entry) } }
            }
        } else {
            SectionLabel("Ready to read")
            for (entry in shown) PathRow(entry, state.place) { vm.openPathStory(entry) }
        }
        val waiting = state.path.size - shown.size
        if (waiting > 0) {
            Text(
                (if (waiting == 1) "1 more story opens" else "$waiting more stories open") + " up as you learn.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * A story on the path, by its English title. One chapter shows its first
 * line and what's new in it; more show a bar with a segment per chapter,
 * how far you've read and what's open next, with a seal-red bar down the
 * side. A chapter left part way at [place] fills gold as far as its page.
 */
@Composable
private fun PathRow(entry: SeriesEntry, place: ReaderPlace?, onClick: () -> Unit) {
    val seal = MaterialTheme.colorScheme.primary
    val single = entry.series.single
    val done = entry.readCount == entry.chapters.size
    Row(
        Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            // Clipped so the bar follows the card's corners.
            .clip(WashiShape)
            .background(MaterialTheme.colorScheme.surfaceContainerLow, WashiShape)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, WashiShape)
            .clickable(onClick = onClick),
    ) {
        if (!single) Box(Modifier.width(3.dp).fillMaxHeight().background(seal))
        Column(Modifier.padding(16.dp).alpha(if (done) 0.6f else 1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(entry.series.title, style = MaterialTheme.typography.titleLarge)
            val shownChapter = entry.next ?: entry.chapters.first()
            Text(
                Speech.firstLine(shownChapter.story.body),
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = Gothic,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!single) {
                val gold = MaterialTheme.colorScheme.tertiary
                val line = MaterialTheme.colorScheme.outlineVariant
                Row(Modifier.padding(vertical = 4.dp).fillMaxWidth().height(6.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    for (chapter in entry.chapters) {
                        val partWay = place?.takeIf { !chapter.read && it.storyId == chapter.story.id && it.pages > 0 }
                        Box(Modifier.weight(1f).fillMaxHeight().clip(WashiShape).background(line)) {
                            when {
                                chapter.read -> Box(Modifier.fillMaxSize().background(seal))
                                partWay != null -> Box(
                                    Modifier.fillMaxWidth(((partWay.page + 1f) / partWay.pages).coerceIn(0f, 1f)).fillMaxHeight().background(gold),
                                )
                            }
                        }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (!single && !done) {
                    Text(
                        "${entry.readCount} of ${entry.chapters.size} chapters read",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                val next = entry.next
                Text(
                    when {
                        done -> "Read"
                        next == null -> "More opens up as you learn"
                        !single -> "Chapter ${next.chapter.number} ready"
                        else -> newLabel(next)
                    },
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium,
                    color = when {
                        done || (single && next?.nothingNew == true) -> rightGreen()
                        next == null -> MaterialTheme.colorScheme.onSurfaceVariant
                        else -> seal
                    },
                )
            }
        }
    }
}

/** What's new in a chapter: "3 new words" and its grammar topic, or that nothing is. */
@Composable
private fun newLabel(entry: ChapterEntry): String {
    val words = entry.gap.words.size
    if (words == 0 && entry.newTopic == null) return "Nothing new"
    return listOfNotNull(
        words.takeIf { it > 0 }?.let { if (it == 1) "1 new word" else "$it new words" },
        entry.newTopic?.title,
    ).joinToString(" · ")
}

/**
 * How far along the main path you are: one segment per story, sized by its
 * chapters and filled as far as you've read it. Side stories aren't counted.
 */
@Composable
private fun PathProgress(path: List<SeriesEntry>) {
    val main = path.filter { !it.series.side }
    if (main.isEmpty()) return
    val seal = MaterialTheme.colorScheme.primary
    val line = MaterialTheme.colorScheme.outlineVariant
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            SectionLabel("Your path", Modifier.weight(1f))
            Text(
                "${main.sumOf { it.readCount }} of ${main.sumOf { it.chapters.size }} chapters",
                style = MaterialTheme.typography.labelMedium,
                color = muted,
            )
        }
        Row(Modifier.fillMaxWidth().height(6.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            for (entry in main) {
                Box(Modifier.weight(entry.chapters.size.toFloat()).fillMaxHeight().clip(WashiShape).background(line)) {
                    Box(Modifier.fillMaxWidth(entry.readCount / entry.chapters.size.toFloat()).fillMaxHeight().background(seal))
                }
            }
        }
        val current = main.indexOfFirst { it.readCount < it.chapters.size }
        Text(
            if (current < 0) {
                buildAnnotatedString { append("Every story on the path read") }
            } else {
                buildAnnotatedString {
                    append("Now in ")
                    withStyle(SpanStyle(color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)) { append(main[current].series.title) }
                    append(" · story ${current + 1} of ${main.size}")
                }
            },
            style = MaterialTheme.typography.labelMedium,
            color = muted,
        )
    }
}

/** Today's new words against the daily limit, gold once it's reached. */
@Composable
private fun PaceMeter(pace: Pace) {
    val color = if (pace.atLimit) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            SectionLabel("New vocabulary", Modifier.weight(1f))
            Text(
                "${pace.newToday} of ${pace.limit} words today",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Box(Modifier.fillMaxWidth().height(4.dp).clip(WashiShape).background(MaterialTheme.colorScheme.outlineVariant)) {
            Box(Modifier.fillMaxWidth((pace.newToday / pace.limit.coerceAtLeast(1).toFloat()).coerceIn(0f, 1f)).fillMaxHeight().background(color))
        }
    }
}

/** Why the list leads with stories that have nothing new: reviews piling up, or today's limit reached. */
@Composable
private fun NudgeCard(pace: Pace, onReview: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clip(WashiShape)
            .background(MaterialTheme.colorScheme.surfaceContainerLow, WashiShape)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, WashiShape),
    ) {
        Box(Modifier.width(3.dp).fillMaxHeight().background(MaterialTheme.colorScheme.tertiary))
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (pace.backlog) {
                Text(if (pace.reviewsDue == 1) "1 review waiting" else "${pace.reviewsDue} reviews waiting", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Catch up before adding more, so the words you've started don't slip. Stories with nothing new help too.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                PrimaryButton(onClick = onReview, modifier = Modifier.fillMaxWidth()) { Text("Review now") }
            } else {
                Text("That's ${pace.newToday} new today", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Give them time to settle. These stories use only what you know, and your reviews will lock them in.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (pace.reviewsDue > 0) {
                    SecondaryButton(onClick = onReview, modifier = Modifier.fillMaxWidth()) { Text("Review · ${pace.reviewsDue} due") }
                }
            }
        }
    }
}

/** An open chapter with nothing new to learn: its story, title and first line. */
@Composable
private fun NothingNewRow(entry: ChapterEntry, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerLow, WashiShape)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, WashiShape)
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (!entry.series.single) Tag("${entry.series.title} · chapter ${entry.chapter.number}", MaterialTheme.colorScheme.primary)
        Text(entry.chapter.story.title, style = MaterialTheme.typography.titleLarge)
        Text(
            Speech.firstLine(entry.story.body),
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = Gothic,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            "Nothing new",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
            color = rightGreen(),
        )
    }
}

/** A small capitalised label over a card's title. */
@Composable
private fun Tag(text: String, color: Color) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.sp,
        color = color,
    )
}

// ------------------------------------------------------------------ a story's chapters

/**
 * A story with more than one chapter: what it's about, who you've met, and
 * its chapters, scrolled to the next one to read. A locked chapter opens a
 * sheet saying what it's missing.
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun SeriesScreen(state: Screen.Series, vm: AppViewModel) {
    val entry = state.entry
    val start = (entry.next ?: entry.chapters.firstOrNull { !it.read })?.let { entry.chapters.indexOf(it) }?.coerceAtLeast(0) ?: 0
    val list = rememberLazyListState(initialFirstVisibleItemIndex = start)
    LazyColumn(Modifier.fillMaxSize(), state = list, contentPadding = PaddingValues(start = 24.dp, end = 24.dp, bottom = 24.dp)) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(bottom = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                    WashiIconButton(WashiIcons.Back, "Back", vm::storyBackPressed)
                }
                Text(entry.series.title, style = MaterialTheme.typography.headlineMedium)
                if (entry.series.blurb.isNotEmpty()) {
                    Text(entry.series.blurb, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (entry.cast.isNotEmpty()) {
                    SectionLabel("People so far")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        for (person in entry.cast) CastChip(person.name)
                        if (entry.moreCast) CastChip("？", dashed = true)
                    }
                }
                HorizontalDivider(Modifier.padding(top = 8.dp), color = MaterialTheme.colorScheme.onSurface)
                SectionLabel("Chapters · ${entry.readCount} of ${entry.chapters.size} read")
            }
        }
        items(entry.chapters, key = { it.story.id }) { chapter ->
            ChapterRow(
                chapter,
                next = chapter === entry.next,
                onClick = { if (chapter.ready || chapter.read) vm.openStory(chapter.story.id) else vm.showLock(chapter.chapter.story.id) },
            )
        }
    }
    state.lock?.let { lock ->
        ModalBottomSheet(onDismissRequest = vm::closeLock, containerColor = MaterialTheme.colorScheme.surface) {
            LockSheet(lock, vm)
        }
    }
}

@Composable
private fun CastChip(name: String, dashed: Boolean = false) {
    Text(
        name,
        fontFamily = Gothic,
        style = MaterialTheme.typography.labelLarge,
        color = if (dashed) MaterialTheme.colorScheme.onSurfaceVariant else LocalContentColor.current,
        modifier = Modifier
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(50))
            .background(if (dashed) Color.Transparent else MaterialTheme.colorScheme.surfaceContainerLow, RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

/** One chapter: its number and title, its first line or why it's locked, and a check, what's new, or a lock. */
@Composable
private fun ChapterRow(entry: ChapterEntry, next: Boolean, onClick: () -> Unit) {
    val locked = !entry.ready && !entry.read
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(WashiShape)
                .background(if (next) MaterialTheme.colorScheme.surfaceContainerLow else Color.Transparent)
                .clickable(onClick = onClick)
                .padding(horizontal = 8.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                entry.chapter.number.toString(),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.End,
                modifier = Modifier.width(24.dp),
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    entry.chapter.story.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (locked) MaterialTheme.colorScheme.onSurfaceVariant else LocalContentColor.current,
                )
                if (locked) {
                    Text(lockReason(entry), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    Text(
                        Speech.firstLine(entry.story.body),
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = Gothic,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            when {
                entry.read -> Text("✓", color = rightGreen(), fontWeight = FontWeight.Bold)
                locked -> Icon(WashiIcons.Lock, contentDescription = "Locked", modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                else -> {
                    val words = entry.gap.words.size
                    Text(
                        listOfNotNull(if (words > 0) "$words new" else null, entry.newTopic?.title).joinToString(" · ").ifEmpty { "Ready" },
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
        if (!next) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

/** Why a chapter is locked, in a few words. */
private fun lockReason(entry: ChapterEntry): String {
    val gap = entry.gap
    return when {
        gap.grammar.isNotEmpty() -> "Needs grammar you haven't met yet"
        else -> "${gap.words.size} new words"
    }
}

/** What a locked chapter is missing, and the open chapters that have the most of it. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LockSheet(lock: LockInfo, vm: AppViewModel) {
    val entry = lock.entry
    val gap = entry.gap
    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(start = 24.dp, end = 24.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        SectionLabel("Chapter ${entry.chapter.number} · locked")
        Text(entry.chapter.story.title, style = MaterialTheme.typography.titleLarge)
        for ((topic, teaching) in lock.grammar) {
            val where = teaching.firstOrNull()?.let { " It's taught before ${chapterName(it)}." }.orEmpty()
            Text("It uses ${topic.title} (${inSentence(topic.titleEn)}), which you haven't met yet.$where", style = MaterialTheme.typography.bodyMedium)
        }
        if (gap.words.size > gap.allowance) {
            Text(
                "It has ${gap.words.size} words you haven't learned; a chapter this long opens at ${gap.allowance}." +
                    if (lock.earlierCover) " Reading the chapters before it gives you all of them." else "",
                style = MaterialTheme.typography.bodyMedium,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                for (word in lock.words) {
                    Text(
                        word.display,
                        fontFamily = Gothic,
                        fontSize = 16.sp,
                        modifier = Modifier
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, WashiShape)
                            .background(MaterialTheme.colorScheme.surfaceContainerLow, WashiShape)
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                    )
                }
            }
        }
        if (lock.sources.isNotEmpty()) {
            SectionLabel("Pick them up faster", Modifier.padding(top = 8.dp))
            for ((source, count) in lock.sources) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(WashiShape)
                        .clickable {
                            vm.closeLock()
                            vm.openStory(source.story.id)
                        }
                        .padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(chapterName(source), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    Text(if (count == 1) "1 of them" else "$count of them", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }
}

/** A grammar topic's English title inside a sentence: "past", but "I think, said" keeps its I. */
internal fun inSentence(title: String) =
    if (title.startsWith("I ") || title.startsWith("I,")) title else title.replaceFirstChar { it.lowercase() }

/** A chapter as the lock sheet names it: "Ramen night 3", or a one-chapter story's title. */
private fun chapterName(entry: ChapterEntry) =
    if (entry.series.single) entry.series.title else "${entry.series.title} ${entry.chapter.number}"

/** Past today's limit, a chapter that adds more: something with nothing new first, or read it anyway. */
@Composable
fun LimitSheet(prompt: LimitPrompt, vm: AppViewModel) {
    Column(
        Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        SectionLabel(listOfNotNull(prompt.chapter, "Over today's limit").joinToString(" · "))
        Text(prompt.title, style = MaterialTheme.typography.titleLarge)
        Text(
            "You've started ${prompt.newToday} new words today. This adds ${prompt.adding} more.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        // A suggestion, not a lock: the alternative leads, and reading anyway is one tap.
        PrimaryButton(onClick = vm::readNothingNew, modifier = Modifier.fillMaxWidth().height(52.dp)) {
            Text(if (prompt.nothingNewOpen) "Read something with nothing new" else "Review instead")
        }
        SecondaryButton(onClick = { vm.openStory(prompt.storyId, anyway = true) }, modifier = Modifier.fillMaxWidth()) {
            Text("Read it anyway")
        }
    }
}

// ------------------------------------------------------------------ reader

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StoryReaderScreen(state: Screen.StoryReader, vm: AppViewModel) {
    val reading = state.reading
    val style = state.style
    // The settings menu shares the pane with words and sentences; opening one closes the other.
    var menu by remember { mutableStateOf(false) }
    // What the pane shows: a word, or the sentence around it.
    var open by remember(reading.story.id) { mutableStateOf<Opened?>(null) }
    // Or a chapter's note, by the paragraph whose marker opened it.
    var note by remember(reading.story.id) { mutableStateOf<Int?>(null) }
    // Or the reading-aloud speed, from the playback bar.
    var audio by remember { mutableStateOf(false) }
    val voice by vm.voiceState.collectAsState()
    val speech by vm.speech.collectAsState()
    // The line being read aloud, or the one reading stopped at, in this story.
    val lineIndex = voice.line?.takeIf { voice.key == reading.story.id }
    val line = lineIndex?.let { reading.narration.getOrNull(it) }
    val playing = voice.playing && line != null
    val spoken = line?.let { l -> voice.offset?.let { l.p to l.wordAt(it) } }
    // The first word on the page showing, where reading starts with nothing else to start from.
    var pageFirst by remember(reading.story.id) { mutableStateOf<Pair<Int, Int>?>(null) }
    DisposableEffect(reading.story.id) { onDispose { vm.stopPlayback() } }
    val openWord = open?.let { reading.word(it.p, it.i) }
    val openSentence = open?.takeIf { it.sentence }?.let { reading.sentence(it.p, it.i) }
    // Shaded along with the open word: the rest of its grammar patterns, or the whole sentence.
    val lit = open?.let { o ->
        val indices = if (o.sentence) {
            openSentence?.words?.toList().orEmpty()
        } else {
            openWord?.grammar.orEmpty().flatMap { use -> use.first..use.last }
        }
        indices.map { o.p to it }.toSet()
    }.orEmpty()
    val selected = open?.takeIf { !it.sentence }?.key
    // Washed lightly: the sentence being read aloud, or else the tapped word's, where reading would start.
    val washed = when {
        line != null -> line.words.map { line.p to it }.toSet()
        selected != null -> reading.sentence(selected.first, selected.second)?.words?.map { selected.first to it }?.toSet().orEmpty()
        else -> emptySet()
    }
    val close = {
        open = null
        note = null
        menu = false
        audio = false
    }
    val tap = { key: Pair<Int, Int> ->
        menu = false
        note = null
        audio = false
        open = if (selected == key) null else Opened(key.first, key.second, sentence = false)
        // While reading aloud, a tapped word's sentence is where it carries on: how you go back.
        if (playing) Narration.lineOf(reading.narration, key.first, key.second)?.let(vm::play)
        // Looking a word up means reading it doesn't count towards its mastery.
        val id = reading.word(key.first, key.second)?.takeIf { it.kind == WordKind.WORD }?.wordId
        if (id != null) vm.lookedUp(id)
    }
    val hold = { key: Pair<Int, Int> ->
        menu = false
        note = null
        audio = false
        open = Opened(key.first, key.second, sentence = true)
    }
    val tapNote = { p: Int ->
        menu = false
        audio = false
        open = null
        note = if (note == p) null else p
    }
    val togglePlay = {
        if (playing) {
            vm.pausePlayback()
        } else if (voice.missing && reading.clips.all { it == null }) {
            open = null
            note = null
            menu = false
            audio = true
        } else {
            // From the tapped word's sentence, else where reading stopped, else the page's first.
            val start = selected?.let { Narration.lineOf(reading.narration, it.first, it.second) }
                ?: lineIndex
                ?: pageFirst?.let { Narration.lineOf(reading.narration, it.first, it.second) }
                ?: 0
            vm.play(start)
        }
        Unit
    }
    val pageShown = { page: Int, pages: Int, first: Pair<Int, Int>? ->
        vm.readerAt(page, pages)
        pageFirst = first
        // Turned by hand while stopped: play starts from the new page.
        if (!voice.playing) vm.forgetPlaybackPlace()
    }
    val anyOpen = open != null || note != null || menu || audio
    BackHandler(enabled = anyOpen, onBack = close)

    // A tap in the story that isn't on a word clears the pane.
    val tapToClear = if (anyOpen) Modifier.pointerInput(Unit) { detectTapGestures { close() } } else Modifier
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            WashiIconButton(WashiIcons.Back, "Back", vm::storyBackPressed)
            Box(Modifier.weight(1f)) {
                // A chapter's English title, or a grammar page's pattern, isn't read like the story.
                Text(reading.story.title, style = MaterialTheme.typography.headlineSmall, fontFamily = if (reading.lesson != null) Gothic else null)
            }
            CompositionLocalProvider(LocalContentColor provides if (menu) MaterialTheme.colorScheme.primary else LocalContentColor.current) {
                WashiIconButton(WashiIcons.Settings, "Reader settings", {
                    open = null
                    note = null
                    audio = false
                    menu = !menu
                })
            }
        }
        // Always there, with the story below it, so it never covers the text and nothing moves when it fills.
        Box(Modifier.fillMaxWidth().height(PANE_HEIGHT).background(MaterialTheme.colorScheme.surfaceContainerLow)) {
            val o = open
            val n = note?.let { reading.notes[it] }
            when {
                menu -> ReaderMenu(style, vm::setReaderStyle)
                audio -> AudioMenu(speech, voice.missing && reading.clips.all { it == null }, vm::setSpeechRate)
                n != null -> NoteCard(n)
                o == null -> Text(
                    "Tap a word to see what it means. Hold one to read its sentence in English.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.align(Alignment.Center).padding(horizontal = 32.dp),
                )
                o.sentence -> SentenceCard(openSentence?.translation)
                // Keyed by place, so a grammar point followed in one word's card doesn't carry over to the next word.
                openWord != null -> key(o.p, o.i) {
                    WordCard(
                        word = openWord,
                        reading = reading,
                        forgotten = state.forgotten,
                        onForgot = vm::toggleForgot,
                        onLesson = vm::openGrammar,
                        onEntry = vm::openWord,
                        onSay = vm::sayWord,
                    )
                }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        // A sequel opens in this same screen; it starts on its first page.
        key(reading.story.id) {
            val lesson = reading.lesson
            if (state.impact != null) {
                // Finished: the summary takes the text's place.
                Box(Modifier.weight(1f).fillMaxWidth()) { StoryEnd(state, vm) }
            } else if (lesson != null) {
                LessonPage(
                    state = state,
                    lesson = lesson,
                    size = style.size.toFloat(),
                    family = style.font.family,
                    selected = selected,
                    lit = lit,
                    washed = washed,
                    spoken = spoken,
                    onTap = tap,
                    onHold = hold,
                    onBlank = close,
                    onRead = vm::readAfterLesson,
                    modifier = Modifier.weight(1f).fillMaxWidth().then(tapToClear),
                )
            } else if (style.vertical) {
                VerticalStory(
                    reading = reading,
                    size = style.size.toFloat(),
                    family = style.font.family,
                    selected = selected,
                    lit = lit,
                    washed = washed,
                    spoken = spoken,
                    follow = if (playing) spoken ?: line?.let { it.p to it.words.first } else null,
                    onTap = { p, i -> tap(p to i) },
                    onHold = { p, i -> hold(p to i) },
                    openNote = note,
                    onNote = tapNote,
                    onBlank = close,
                    onFinish = vm::finishStory,
                    startPage = state.startPage,
                    onPageShown = pageShown,
                    modifier = Modifier.weight(1f).fillMaxWidth().then(tapToClear),
                )
            } else {
                HorizontalStory(
                    reading = reading,
                    size = style.size.toFloat(),
                    family = style.font.family,
                    selected = selected,
                    lit = lit,
                    washed = washed,
                    spoken = spoken,
                    follow = if (playing) spoken ?: line?.let { it.p to it.words.first } else null,
                    onTap = { p, i -> tap(p to i) },
                    onHold = { p, i -> hold(p to i) },
                    openNote = note,
                    onNote = tapNote,
                    onBlank = close,
                    onFinish = vm::finishStory,
                    startPage = state.startPage,
                    onPageShown = pageShown,
                    modifier = Modifier.weight(1f).fillMaxWidth().then(tapToClear),
                )
            }
            if (state.impact == null) {
                PlaybackBar(
                    playing = playing,
                    rate = speech.rate,
                    open = audio,
                    onPlay = togglePlay,
                    onMenu = {
                        open = null
                        note = null
                        menu = false
                        audio = !audio
                    },
                )
            }
        }
    }
}

/**
 * The bar under the story for reading it aloud: the speed on the left, which
 * opens its menu in the pane, and play in the middle. Sentences play on by
 * themselves; tapping one moves there.
 */
@Composable
private fun PlaybackBar(playing: Boolean, rate: Float, open: Boolean, onPlay: () -> Unit, onMenu: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val tint = if (open) scheme.primary else scheme.onSurface
    HorizontalDivider(color = scheme.outlineVariant)
    Row(
        Modifier.fillMaxWidth().height(44.dp).background(scheme.surfaceContainerLow).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.weight(1f)) {
            Box(
                Modifier
                    .clip(WashiShape)
                    .border(1.dp, if (open) scheme.primary else scheme.outlineVariant, WashiShape)
                    .clickable(onClick = onMenu)
                    .padding(horizontal = 8.dp, vertical = 3.dp),
            ) {
                Text(rateLabel(rate), style = MaterialTheme.typography.labelLarge, color = tint)
            }
        }
        Box(
            Modifier
                .size(32.dp)
                .clip(WashiShape)
                .background(scheme.primary)
                .clickable(onClick = onPlay),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (playing) WashiIcons.Pause else WashiIcons.Play,
                contentDescription = if (playing) "Pause" else "Read aloud",
                tint = scheme.onPrimary,
                modifier = Modifier.size(16.dp),
            )
        }
        Spacer(Modifier.weight(1f))
    }
}

private fun rateLabel(rate: Float) = String.format(java.util.Locale.ROOT, "%.1f×", rate)

/**
 * Reading aloud's settings, in the pane like the reader's: the speed. With
 * nothing recorded and no Japanese voice on the phone, how to get one.
 */
@Composable
private fun AudioMenu(speech: SpeechSettings, missing: Boolean, onRate: (Float) -> Unit) {
    if (missing) {
        val context = LocalContext.current
        Column(
            Modifier.fillMaxSize().padding(horizontal = 32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "Your phone has no Japanese voice yet. Add one in its text-to-speech settings to hear stories read aloud.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            TextButton(onClick = {
                runCatching { context.startActivity(Intent("com.android.settings.TTS_SETTINGS").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            }) {
                Text("Open speech settings", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.secondary)
            }
        }
        return
    }
    Column(
        Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.SpaceEvenly,
    ) {
        MenuRow("Speed") {
            for (rate in SpeechSettings.RATES) {
                MenuOption(speech.rate == rate, { onRate(rate) }) {
                    Text(rateLabel(rate), style = MaterialTheme.typography.labelLarge, maxLines = 1)
                }
            }
        }
    }
}

/**
 * The story set across the page, a page at a time. Words are measured here so
 * lines and pages can be broken in core, with the same rules as vertical text.
 */
@Composable
private fun HorizontalStory(
    reading: StoryReading,
    size: Float,
    family: FontFamily,
    selected: Pair<Int, Int>?,
    lit: Set<Pair<Int, Int>>,
    washed: Set<Pair<Int, Int>>,
    spoken: Pair<Int, Int>?,
    follow: Pair<Int, Int>?,
    onTap: (p: Int, i: Int) -> Unit,
    onHold: (p: Int, i: Int) -> Unit,
    openNote: Int?,
    onNote: (p: Int) -> Unit,
    onBlank: () -> Unit,
    onFinish: () -> Unit,
    startPage: Int,
    onPageShown: (page: Int, pages: Int, first: Pair<Int, Int>?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    // Words measured as StoryWord draws them: each piece as wide as its text or its furigana, shown or not.
    val rubySize = size * RUBY_RATIO
    // A note's marker, with a little room before it, ends its paragraph and never starts a line.
    val markerWidth = remember(density, size, family) {
        val style = TextStyle(fontSize = (size * MARKER_RATIO).sp, fontFamily = family, fontWeight = FontWeight.Bold)
        measurer.measure("注", style, softWrap = false).size.width + with(density) { (MARKER_PADDING * 2 + MARKER_BORDER * 2).toPx() + (size * 0.4f).sp.toPx() }
    }
    val flows = remember(reading, density, size, family) {
        val base = TextStyle(fontSize = size.sp, fontFamily = family)
        val ruby = TextStyle(fontSize = rubySize.sp, fontFamily = family)
        reading.paragraphs.mapIndexed { p, words ->
            words.map { word ->
                val width = word.ruby.sumOf { piece ->
                    maxOf(
                        measurer.measure(piece.base, base, softWrap = false).size.width,
                        measurer.measure(piece.ruby ?: " ", ruby, softWrap = false).size.width,
                    )
                }
                FlowWord(word.surface, width.toFloat())
            } + listOfNotNull(FlowWord("注", markerWidth, glued = true).takeIf { p in reading.notes })
        }
    }
    // Script style: a line of dialogue starts with its speaker's name, and the
    // quotes in a run of dialogue all start in line, after its widest name.
    // A quote that wraps carries on a character further in.
    // The name keeps the text's line height, so it sits on about the same baseline.
    val nameStyle = TextStyle(fontSize = (size * NAME_RATIO).sp, lineHeight = (size * 1.5f).sp, fontFamily = family)
    val nameColumns = remember(reading, density, size, family) {
        val space = with(density) { (size * NAME_GAP).sp.toPx() }
        Speech.columns(reading.speakers) { measurer.measure(it, nameStyle, softWrap = false).size.width.toFloat() }
            .map { it?.plus(space) }
    }
    val charWidth = with(density) { size.sp.toPx() }
    BoxWithConstraints(modifier) {
        val lineHeight = with(density) { (rubySize * 1.2f).sp.toPx() + (size * 1.5f).sp.toPx() }
        val gap = with(density) { PARAGRAPH_GAP.toPx() }
        // A pixel or two spare, so rounding never pushes a word past the edge.
        val width = constraints.maxWidth - with(density) { (PAGE_PADDING_H * 2 + 2.dp).toPx() }
        val height = constraints.maxHeight - with(density) { (PAGE_PADDING * 2 + PAGE_LABEL_HEIGHT).toPx() }
        val pages = remember(flows, nameColumns, width, height, lineHeight, gap) {
            val lines = flows.mapIndexed { p, flow ->
                val column = nameColumns[p] ?: 0f
                HorizontalText.lines(flow, width, indent = column, wrapIndent = if (column > 0f) column + charWidth else 0f)
            }
            HorizontalText.pages(lines, lineHeight, gap, height)
        }
        val nameColor = speakerColor()
        val lineDp = with(density) { lineHeight.toDp() }
        // The finish stamp goes under the last line, or on a page of its own when that's full.
        val used = pages.lastOrNull()?.withIndex()?.sumOf { (n, line) ->
            (lineHeight + if (n > 0 && line.words.first == 0) gap else 0f).toDouble()
        } ?: 0.0
        val stampPage = pages.isEmpty() || used + with(density) { STAMP_ACROSS_HEIGHT.toPx() } > height
        val count = pages.size + if (stampPage) 1 else 0
        // The page with the word being read aloud, to turn to.
        val followPage = follow?.let { (p, i) -> pages.indexOfFirst { page -> page.any { it.paragraph == p && i in it.words } } }?.takeIf { it >= 0 }
        val shown = { page: Int ->
            onPageShown(page, count, pages.getOrNull(page)?.firstOrNull()?.let { it.paragraph to it.words.first })
        }
        StoryPager(count, reverse = false, startPage, shown, followPage) { page ->
            Column(Modifier.fillMaxSize().padding(horizontal = PAGE_PADDING_H, vertical = PAGE_PADDING)) {
                pages.getOrNull(page).orEmpty().forEachIndexed { n, line ->
                    if (n > 0 && line.words.first == 0) Spacer(Modifier.height(PARAGRAPH_GAP))
                    Row(Modifier.height(lineDp), verticalAlignment = Alignment.Bottom) {
                        val column = nameColumns[line.paragraph]
                        if (column != null) {
                            val columnDp = with(density) { column.toDp() }
                            if (line.words.first == 0) {
                                Text(
                                    reading.speakers[line.paragraph].orEmpty(),
                                    style = nameStyle,
                                    color = nameColor,
                                    softWrap = false,
                                    modifier = Modifier.width(columnDp),
                                )
                            } else {
                                Spacer(Modifier.width(columnDp + with(density) { charWidth.toDp() }))
                            }
                        }
                        for (i in line.words) {
                            val p = line.paragraph
                            val word = reading.paragraphs[p].getOrNull(i)
                            if (word == null) {
                                // Past the paragraph's words: its note's marker.
                                NoteMarker(
                                    textSize = size.sp,
                                    family = family,
                                    open = openNote == p,
                                    onClick = { onNote(p) },
                                    modifier = Modifier.width(with(density) { markerWidth.toDp() }).height(with(density) { (size * 1.5f).sp.toDp() }),
                                )
                                continue
                            }
                            StoryWord(
                                word = word,
                                reading = reading,
                                selected = selected == (p to i),
                                lit = (p to i) in lit,
                                washed = (p to i) in washed,
                                spoken = spoken == (p to i),
                                onTap = { if (word.tappable()) onTap(p, i) else onBlank() },
                                onHold = { onHold(p, i) },
                                size = size.sp,
                                rubySize = rubySize.sp,
                                family = family,
                            )
                        }
                    }
                }
                if (page == count - 1) {
                    FinishStamp(vertical = false, onClick = onFinish, modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 8.dp))
                }
            }
        }
    }
}

/**
 * A grammar topic's page: its English with the Japanese examples between,
 * tapping words and holding for a sentence's English as in a story. It's
 * one page that scrolls, always across, to look back at whenever. Before
 * a chapter, it ends with a button into the chapter.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LessonPage(
    state: Screen.StoryReader,
    lesson: PathStory,
    size: Float,
    family: FontFamily,
    selected: Pair<Int, Int>?,
    lit: Set<Pair<Int, Int>>,
    washed: Set<Pair<Int, Int>>,
    spoken: Pair<Int, Int>?,
    onTap: (Pair<Int, Int>) -> Unit,
    onHold: (Pair<Int, Int>) -> Unit,
    onBlank: () -> Unit,
    onRead: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val reading = state.reading
    val notes = lesson.notes.groupBy { it.before }
    Column(
        modifier.verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Tag("Grammar", MaterialTheme.colorScheme.secondary)
        for (p in 0..reading.paragraphs.size) {
            for (note in notes[p].orEmpty()) Text(note.text, style = MaterialTheme.typography.bodyLarge)
            val words = reading.paragraphs.getOrNull(p) ?: continue
            FlowRow(
                Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceContainerLow, WashiShape)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            ) {
                words.forEachIndexed { i, word ->
                    StoryWord(
                        word = word,
                        reading = reading,
                        selected = selected == (p to i),
                        lit = (p to i) in lit,
                        washed = (p to i) in washed,
                        spoken = spoken == (p to i),
                        onTap = { if (word.tappable()) onTap(p to i) else onBlank() },
                        onHold = { onHold(p to i) },
                        size = size.sp,
                        rubySize = (size * RUBY_RATIO).sp,
                        family = family,
                    )
                }
            }
        }
        // Opened before a chapter: on into it.
        state.then?.let { then ->
            PrimaryButton(onClick = onRead, modifier = Modifier.fillMaxWidth().padding(top = 8.dp).height(52.dp)) {
                Text(then.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/**
 * [count] pages to swipe between, with the page number under them. [reverse]
 * puts the next page on the left. It opens on [startPage], and tells
 * [onPageShown] each page it settles on, so a story can reopen where you were.
 */
@Composable
internal fun StoryPager(
    count: Int,
    reverse: Boolean,
    startPage: Int,
    onPageShown: (Int) -> Unit,
    followPage: Int?,
    page: @Composable (Int) -> Unit,
) {
    val state = rememberPagerState(startPage.coerceIn(0, (count - 1).coerceAtLeast(0))) { count }
    val shown by rememberUpdatedState(onPageShown)
    LaunchedEffect(state) { snapshotFlow { state.settledPage }.collect { shown(it) } }
    // Reading aloud turns the page when it reaches the next one.
    LaunchedEffect(followPage) {
        if (followPage != null && followPage != state.currentPage) state.animateScrollToPage(followPage)
    }
    Column(Modifier.fillMaxSize()) {
        HorizontalPager(state, Modifier.weight(1f).fillMaxWidth(), reverseLayout = reverse, verticalAlignment = Alignment.Top) {
            page(it)
        }
        Box(Modifier.fillMaxWidth().height(PAGE_LABEL_HEIGHT), contentAlignment = Alignment.Center) {
            if (count > 1) {
                Text(
                    "${state.currentPage + 1} / $count",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun StoryReading.isKnown(id: Long?): Boolean {
    val s = id?.let { statuses[it] } ?: return false
    return s.state != CardState.NEW || s.suspended
}

internal fun StoryReading.isUnknown(word: ReadingWord) = word.kind == WordKind.WORD && !isKnown(word.wordId)

/** Furigana follows each word's mastery, as on cards; new words and names always have it. */
internal fun StoryReading.showsRuby(word: ReadingWord): Boolean {
    val mastery = word.wordId?.let { statuses[it]?.mastery } ?: 0
    return isUnknown(word) || word.kind == WordKind.NAME || MasteryRules.furigana(mastery) == FuriganaMode.SHOWN
}

internal fun ReadingWord.tappable() = kind == WordKind.WORD || kind == WordKind.NAME || grammar.isNotEmpty()

/** A tapped word's shading, and the lighter wash on its sentence or the one read aloud. */
internal const val HIGHLIGHT = 0.14f
internal const val WASH = 0.06f

/** Furigana's size against the text it sits on. */
internal const val RUBY_RATIO = 10f / 22f

/** A note's 注 marker against the text it follows. */
internal const val MARKER_RATIO = 0.62f
private val MARKER_PADDING = 2.dp
private val MARKER_BORDER = 1.5.dp

/** A speaker's name against the text of their line: smaller, as a label. */
internal const val NAME_RATIO = 0.8f

/** The space after a run of dialogue's widest name, in characters of the text. */
internal const val NAME_GAP = 0.5f

/** Speakers' names: quieter than the text, and in light theme a little more than onSurfaceVariant. */
@Composable
internal fun speakerColor(): Color {
    val scheme = MaterialTheme.colorScheme
    val light = scheme.surface.luminance() > 0.5f
    return if (light) lerp(scheme.onSurfaceVariant, scheme.surface, 0.19f) else scheme.onSurfaceVariant
}
internal val PAGE_PADDING = 16.dp
private val PAGE_PADDING_H = 24.dp
internal val PAGE_LABEL_HEIGHT = 28.dp
private val PARAGRAPH_GAP = 16.dp
/** Tall enough for a word's first meaning or two and its buttons; the rest scrolls. */
private val PANE_HEIGHT = 160.dp

/** What the reader's pane shows: word [i] of paragraph [p], or with [sentence], the sentence around it. */
private data class Opened(val p: Int, val i: Int, val sentence: Boolean) {
    val key get() = p to i
}

/** The title, its words tappable like the story's, in [style]'s size and weight. */
/**
 * A tap opens a word in the pane, or closes the pane on a word with nothing
 * to show; a long press opens the word's sentence. No ripple: vertical text
 * lays words out a character at a time, so it flashed on one character, and
 * the red selection already shows the tap.
 */
@OptIn(ExperimentalFoundationApi::class)
internal fun Modifier.storyWordGestures(onTap: () -> Unit, onHold: () -> Unit): Modifier =
    combinedClickable(interactionSource = null, indication = null, onLongClick = onHold, onClick = onTap)

@Composable
private fun StoryWord(
    word: ReadingWord,
    reading: StoryReading,
    selected: Boolean,
    lit: Boolean,
    washed: Boolean,
    spoken: Boolean,
    onTap: () -> Unit,
    onHold: () -> Unit,
    size: TextUnit,
    rubySize: TextUnit,
    family: FontFamily,
    weight: FontWeight? = null,
) {
    val unknown = reading.isUnknown(word)
    val showRuby = reading.showsRuby(word)
    val color = if (unknown || selected) MaterialTheme.colorScheme.primary else LocalContentColor.current

    val seal = MaterialTheme.colorScheme.primary
    var modifier: Modifier = Modifier
    if (selected || lit) modifier = modifier.background(seal.copy(alpha = HIGHLIGHT), WashiShape)
    else if (washed) modifier = modifier.background(seal.copy(alpha = WASH), WashiShape)
    // The word being read aloud: a line under it, below any underline it has.
    if (spoken) modifier = modifier.drawBehind {
        val y = this.size.height - 1.dp.toPx()
        drawLine(seal, Offset(0f, y), Offset(this.size.width, y), strokeWidth = 2.dp.toPx())
    }
    modifier = modifier.storyWordGestures(onTap, onHold)
    Box(modifier) {
        Row(verticalAlignment = Alignment.Bottom) {
            for (piece in word.ruby) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = piece.ruby ?: " ",
                        fontSize = rubySize,
                        lineHeight = rubySize * 1.2f,
                        fontFamily = family,
                        maxLines = 1,
                        softWrap = false,
                        color = color.copy(alpha = 0.75f),
                        modifier = if (showRuby && piece.ruby != null) Modifier else Modifier.alpha(0f).clearAndSetSemantics {},
                    )
                    Text(
                        text = piece.base,
                        fontSize = size,
                        lineHeight = size * 1.5f,
                        fontFamily = family,
                        fontWeight = weight,
                        softWrap = false,
                        color = color,
                        textDecoration = if (unknown) TextDecoration.Underline else null,
                    )
                }
            }
        }
    }
}

internal val ReaderFont.family: FontFamily
    get() = when (this) {
        ReaderFont.SANS -> Gothic
        ReaderFont.SERIF -> Mincho
        ReaderFont.ROUNDED -> Maru
    }

private val ReaderFont.label: String
    get() = when (this) {
        ReaderFont.SANS -> "Sans"
        ReaderFont.SERIF -> "Serif"
        ReaderFont.ROUNDED -> "Rounded"
    }

/** The reader's settings, laid out to fit the pane without scrolling. Changes apply as you tap. */
@Composable
private fun ReaderMenu(style: ReaderStyle, onChange: (ReaderStyle) -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        MenuRow("Size") {
            ReaderStyle.SIZES.forEachIndexed { i, size ->
                MenuOption(style.size == size, { onChange(style.copy(size = size)) }) {
                    // Each step a little bigger, as a sample; the real sizes wouldn't fit the buttons.
                    Text("あ", fontFamily = style.font.family, fontSize = (13 + 3 * i).sp)
                }
            }
        }
        MenuRow("Font") {
            for (font in ReaderFont.entries) {
                MenuOption(style.font == font, { onChange(style.copy(font = font)) }) {
                    Text(font.label, fontFamily = font.family, fontSize = 15.sp, maxLines = 1)
                }
            }
        }
        MenuRow("Direction") {
            MenuOption(!style.vertical, { onChange(style.copy(vertical = false)) }) {
                Text("Horizontal", style = MaterialTheme.typography.labelLarge, maxLines = 1)
            }
            MenuOption(style.vertical, { onChange(style.copy(vertical = true)) }) {
                Text("Vertical", style = MaterialTheme.typography.labelLarge, maxLines = 1)
            }
        }
    }
}

@Composable
private fun MenuRow(label: String, options: @Composable RowScope.() -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            modifier = Modifier.width(72.dp),
        )
        options()
    }
}

/** One choice in a menu row: ink when chosen, an ink outline when not, like a chip. */
@Composable
private fun RowScope.MenuOption(selected: Boolean, onClick: () -> Unit, content: @Composable () -> Unit) {
    val ink = MaterialTheme.colorScheme.onSurface
    Box(
        Modifier
            .weight(1f)
            .height(38.dp)
            .clip(WashiShape)
            .background(if (selected) ink else Color.Transparent)
            .border(1.dp, ink, WashiShape)
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton),
        contentAlignment = Alignment.Center,
    ) {
        CompositionLocalProvider(LocalContentColor provides if (selected) MaterialTheme.colorScheme.surface else ink) {
            content()
        }
    }
}

/** A sentence's English, or why there isn't any. Centred in the pane when it fits, from the top when it scrolls. */
@Composable
private fun SentenceCard(translation: String?) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).heightIn(min = maxHeight).padding(horizontal = 20.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                translation ?: "There's no translation saved for this story.",
                fontSize = 17.sp,
                lineHeight = 23.sp,
                color = if (translation == null) MaterialTheme.colorScheme.onSurfaceVariant else LocalContentColor.current,
            )
        }
    }
}

/** A chapter's note, opened from its marker: English the reader might want beside the story. */
@Composable
private fun NoteCard(text: String) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).heightIn(min = maxHeight).padding(horizontal = 20.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
        ) {
            Tag("注 · Note", MaterialTheme.colorScheme.secondary)
            Text(text, fontSize = 16.sp, lineHeight = 22.sp)
        }
    }
}

/**
 * A note's 注 marker, at the end of the paragraph before the note, in
 * indigo so it doesn't read as a new word. It fills [modifier]'s room,
 * which is all tappable, and is shaded while its note is open.
 */
@Composable
internal fun NoteMarker(textSize: TextUnit, family: FontFamily, open: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val indigo = MaterialTheme.colorScheme.secondary
    val shape = RoundedCornerShape(3.dp)
    Box(modifier.clickable(interactionSource = null, indication = null, onClickLabel = "Show note", onClick = onClick), contentAlignment = Alignment.Center) {
        Text(
            "注",
            fontSize = textSize * MARKER_RATIO,
            lineHeight = textSize * MARKER_RATIO * 1.25f,
            fontFamily = family,
            fontWeight = FontWeight.Bold,
            color = indigo,
            softWrap = false,
            modifier = Modifier
                .background(if (open) indigo.copy(alpha = 0.18f) else Color.Transparent, shape)
                .border(MARKER_BORDER, indigo, shape)
                .padding(horizontal = MARKER_PADDING),
        )
    }
}

/**
 * The open word's card: its meanings, conjugation and grammar, and what to do with it.
 * A word's grammar points are named in its conjugation line, and one tapped there
 * takes the card's place until you go back.
 */
@Composable
private fun WordCard(
    word: ReadingWord,
    reading: StoryReading,
    forgotten: Set<Long>,
    onForgot: (Long) -> Unit,
    onLesson: (Long) -> Unit,
    onEntry: (Long) -> Unit,
    onSay: (String) -> Unit,
) {
    val entry = word.wordId?.let { reading.words[it] }
    // A particle or ending on its own: the card explains its grammar and has no dictionary entry.
    val grammarOnly = word.kind == WordKind.GRAMMAR
    val points = word.grammar.map { it.point }.distinctBy { it.id }
    // The topic's page, unless that's the page you're on.
    fun lessonFor(point: GrammarPoint): (() -> Unit)? = reading.lessons[point.id]?.takeIf { it != reading.story.id }?.let { id -> { onLesson(id) } }
    var followed by remember { mutableStateOf<GrammarPoint?>(null) }
    val title = if (grammarOnly) word.surface else entry?.display ?: word.lemma
    followed?.let { point ->
        BackHandler { followed = null }
        GrammarCard(point, back = title, onBack = { followed = null }, onLesson = lessonFor(point))
        return
    }
    if (grammarOnly && points.isNotEmpty()) {
        ParticleCard(points, ::lessonFor)
        return
    }
    // Always from the top, so the title line and its buttons sit in the same place for every word.
    Column(
        Modifier.fillMaxSize().padding(start = 20.dp, end = 4.dp, top = 8.dp, bottom = 4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(title, fontFamily = Gothic, fontWeight = FontWeight.Bold, fontSize = 22.sp, modifier = Modifier.weight(1f, fill = false))
                val kana = entry?.kana ?: word.reading
                if (!grammarOnly && kana != title) {
                    Text(kana, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                // A particle on its own isn't said as it is in a sentence (は is wa), so it has no button.
                if (!grammarOnly) SayButton { onSay(kana) }
            }
            // The word's dictionary page, where there is one.
            if (entry != null) {
                TextButton(onClick = { onEntry(entry.id) }) {
                    Text("Full entry →", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.secondary)
                }
            }
        }
        Column(
            Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(end = 16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (!grammarOnly && points.isNotEmpty()) {
                GrammarLinks(word, title, points, onOpen = { followed = it })
            } else if (word.conjugated) {
                Text(
                    "${word.surface}: ${word.conjugation.joinToString(", ")}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }
            if (entry == null) {
                if (!grammarOnly) Text(
                    if (word.kind == WordKind.NAME) "A name or number" else "Not in the dictionary",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                val senses = entry.senses.take(3)
                senses.forEachIndexed { i, sense ->
                    MeaningLine(if (senses.size > 1) numeral(i) else null, sense.glosses.joinToString("; "), compact = true)
                }
                val status = reading.statuses[entry.id]
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        levelLabel(entry.jlpt) + "  ·  " + when {
                            // A story's words you haven't started join your reviews when you finish it; a grammar page has none.
                            status == null -> "New" + if (reading.lesson == null) ", joins your reviews when you finish" else ""
                            status.suspended -> "Suspended"
                            status.state == CardState.NEW -> "New"
                            else -> "Mastery ${status.mastery}"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    // A word you're reviewing can be marked forgotten: one Wrong review when you finish, never just for tapping it.
                    if (status != null && status.state == CardState.REVIEW && !status.suspended && reading.lesson == null) {
                        Spacer(Modifier.width(8.dp))
                        if (entry.id in forgotten) {
                            Text("✓ Forgot", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            TextButton(onClick = { onForgot(entry.id) }) {
                                Text("Undo", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.secondary)
                            }
                        } else {
                            TextButton(onClick = { onForgot(entry.id) }) {
                                Text("I forgot this", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.secondary)
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * A particle or ending's card: its grammar points, the first in the title's
 * place. What you tapped is already marked in the text, so it isn't repeated.
 */
@Composable
private fun ParticleCard(points: List<GrammarPoint>, lessonFor: (GrammarPoint) -> (() -> Unit)?) {
    val first = points.first()
    Column(
        Modifier.fillMaxSize().padding(start = 20.dp, end = 4.dp, top = 8.dp, bottom = 4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(first.pattern, fontFamily = Gothic, fontWeight = FontWeight.Bold, fontSize = 22.sp, modifier = Modifier.weight(1f))
            lessonFor(first)?.let { onLesson ->
                TextButton(onClick = onLesson) {
                    Text("Grammar lesson →", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.secondary)
                }
            }
        }
        Text(
            "Grammar" + "  ·  " + levelLabel(first.level),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Column(
            Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(end = 16.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            GrammarBody(first)
            for (point in points.drop(1)) GrammarNote(point, lessonFor(point))
        }
    }
}

/** A speaker that says a word's reading. */
@Composable
internal fun SayButton(modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(modifier.size(36.dp).clip(WashiShape).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(WashiIcons.Sound, contentDescription = "Say it", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
    }
}

/**
 * A word's conjugation line naming its grammar points, each a link to its
 * explanation: 食べたい: ～たい want to ›. It takes the place of the word's
 * conjugation labels, except those no point explains, which follow the links:
 * 切れちゃった: ～てしまう … › · past.
 */
@Composable
private fun GrammarLinks(word: ReadingWord, title: String, points: List<GrammarPoint>, onOpen: (GrammarPoint) -> Unit) {
    val link = TextLinkStyles(SpanStyle(color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.Bold))
    Text(
        buildAnnotatedString {
            if (word.surface != title) append("${word.surface}: ")
            points.forEachIndexed { i, point ->
                if (i > 0) append("  ·  ")
                withLink(LinkAnnotation.Clickable(point.id, link) { onOpen(point) }) {
                    append(point.pattern)
                    withStyle(SpanStyle(fontWeight = FontWeight.Normal)) { append(" ${point.meaning} ›") }
                }
            }
            val rest = word.unexplainedConjugation()
            if (rest.isNotEmpty()) append("  ·  " + rest.joinToString(", "))
        },
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.tertiary,
    )
}

/** A grammar point followed from a word's conjugation line, with ‹ [back] to the word. */
@Composable
private fun GrammarCard(point: GrammarPoint, back: String, onBack: () -> Unit, onLesson: (() -> Unit)?) {
    Column(
        Modifier.fillMaxSize().padding(start = 20.dp, end = 4.dp, top = 8.dp, bottom = 4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                "‹ $back",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.secondary,
                maxLines = 1,
                modifier = Modifier.clickable(onClickLabel = "Back to $back", onClick = onBack).padding(vertical = 6.dp),
            )
            Text(point.pattern, fontFamily = Gothic, fontWeight = FontWeight.Bold, fontSize = 22.sp)
            GrammarLevel(point, onLesson)
        }
        Column(
            Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(end = 16.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            GrammarBody(point)
        }
    }
}

/** A particle card's second or later grammar point: the pattern, what it means, an example, and [onLesson] for its topic's page. */
@Composable
private fun GrammarNote(point: GrammarPoint, onLesson: (() -> Unit)?) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.padding(top = 2.dp)) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 4.dp)) {
            Text(point.pattern, fontFamily = Gothic, fontWeight = FontWeight.Bold, fontSize = 17.sp)
            GrammarLevel(point, onLesson)
        }
        GrammarBody(point)
    }
}

/** "Grammar · N5", filling the row, and the Grammar lesson → link when there's a page for it. */
@Composable
private fun RowScope.GrammarLevel(point: GrammarPoint, onLesson: (() -> Unit)?) {
    Text(
        "Grammar" + "  ·  " + levelLabel(point.level),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.weight(1f),
    )
    if (onLesson != null) {
        TextButton(onClick = onLesson) {
            Text("Grammar lesson →", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.secondary)
        }
    }
}

@Composable
private fun GrammarBody(point: GrammarPoint) {
    Text(point.meaning, fontSize = 16.sp, lineHeight = 21.sp)
    Text(point.note, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Text(point.example, fontFamily = Gothic, fontSize = 15.sp, modifier = Modifier.padding(top = 2.dp))
    Text(point.exampleMeaning, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

// ------------------------------------------------------------------ end page

/** Once the story's finished: what you learned from it, and what to read next. */
@Composable
private fun StoryEnd(state: Screen.StoryReader, vm: AppViewModel) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val chapter = state.chapter
        if (chapter != null && !chapter.series.single) SectionLabel("Chapter ${chapter.chapter.number} done")
        Text(if (chapter != null && !chapter.series.single) chapter.chapter.story.title else "The end", style = MaterialTheme.typography.headlineSmall)
        state.impact?.let { WordImpact(it, state.knewNew, state.pace, vm::openWord, vm::toggleKnewNew) }
        state.impact?.topic?.let { topic ->
            SectionLabel("Grammar", Modifier.padding(top = 8.dp))
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(topic.title, fontFamily = Gothic, fontWeight = FontWeight.Bold, fontSize = 20.sp, color = MaterialTheme.colorScheme.secondary)
                Text(topic.titleEn, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        val meets = chapter?.chapter?.meets.orEmpty()
        if (meets.isNotEmpty()) {
            SectionLabel(if (meets.size == 1) "New face" else "New faces", Modifier.padding(top = 8.dp))
            for (person in meets) {
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(person.name, fontFamily = Gothic, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Text(person.about, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        HorizontalDivider(Modifier.padding(top = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)
        val nextStep = state.next
        val pace = state.pace
        if (pace?.nudge == true) {
            // At today's limit or behind on reviews: something with nothing new, or reviews, before the next chapter.
            // The New list shows the meter when there is one.
            pace.takeIf { it.atLimit && state.impact?.seen.isNullOrEmpty() }?.let { PaceMeter(it) }
            val rest = state.rest
            if (rest != null) {
                PrimaryButton(onClick = { vm.openStory(rest.storyId) }, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                    Text(rest.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            if (pace.reviewsDue > 0) {
                if (rest == null) {
                    PrimaryButton(onClick = vm::startReview, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Review · ${pace.reviewsDue} due") }
                } else {
                    SecondaryButton(onClick = vm::startReview, modifier = Modifier.fillMaxWidth()) { Text("Review · ${pace.reviewsDue} due") }
                }
            }
            if (nextStep != null && nextStep.storyId != rest?.storyId) {
                TextButton(onClick = { vm.openStory(nextStep.storyId, anyway = true) }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                    Text(
                        // "Next: 5. Title" or "Next story: Title".
                        nextStep.label.replaceFirst(Regex("^Next( story)?:"), "Next$1 anyway:"),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        } else if (nextStep != null) {
            PrimaryButton(onClick = { vm.openStory(nextStep.storyId) }, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Text(nextStep.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/**
 * What reading a path story did for your words: each word newly seen, or
 * moved up to known or mastered. A new word you say you already know is
 * listed as known too, and stops counting against today's new words.
 */
@Composable
private fun WordImpact(impact: ReadImpact, knewNew: Set<Long>, pace: Pace?, onWord: (Long) -> Unit, onKnewNew: (Long) -> Unit) {
    SectionLabel("Your words", Modifier.padding(top = 8.dp))
    val known = impact.seen.filter { it.id in knewNew } + impact.known
    val groups = listOf(
        Triple("New", impact.seen, masteryColor(0)),
        Triple("Seen → known", known, masteryColor(MasteryRules.HIDE_AT)),
        Triple("Known → mastered", impact.mastered, masteryColor(MasteryRules.OFF_AT)),
    ).filter { it.second.isNotEmpty() }
    if (groups.isEmpty()) {
        Text("No changes to your words this time.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    for ((label, words, color) in groups) {
        val isNew = words === impact.seen
        Column {
            Row(Modifier.fillMaxWidth().padding(bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(label, style = MaterialTheme.typography.bodyLarge, color = color, modifier = Modifier.weight(1f))
                Text((if (isNew) words.count { it.id !in knewNew } else words.size).toString(), style = MaterialTheme.typography.titleMedium)
            }
            if (isNew && pace != null) Box(Modifier.padding(bottom = 6.dp)) { PaceMeter(pace) }
            words.forEachIndexed { i, word ->
                if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                ImpactWord(
                    word = word,
                    onClick = { onWord(word.id) },
                    knew = if (isNew) word.id in knewNew else null,
                    onKnew = { onKnewNew(word.id) },
                )
            }
        }
    }
}

/**
 * One word in the end page's lists: its spelling, its kana if that's in
 * kanji, and its first meaning. Tap for its dictionary page. A new word
 * ([knew] not null) also has "I know this", or "✓ Known" and Undo.
 */
@Composable
private fun ImpactWord(word: Word, onClick: () -> Unit, knew: Boolean? = null, onKnew: () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        val faded = if (knew == true) 0.55f else 1f
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
            Text(word.display, fontFamily = Gothic, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = LocalContentColor.current.copy(alpha = faded))
            if (word.showsKanji) Text(word.kana, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = faded))
            if (knew != true) {
                Text(
                    word.senses.firstOrNull()?.glosses?.joinToString("; ").orEmpty(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = if (knew == null) TextAlign.End else TextAlign.Start,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        if (knew == true) Text("✓ Known", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (knew != null) {
            TextButton(onClick = onKnew) {
                Text(if (knew) "Undo" else "I know this", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.secondary)
            }
        }
    }
}
