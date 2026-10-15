package ca.danielpower.jstudy.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ca.danielpower.jstudy.R
import ca.danielpower.jstudy.core.CardState
import ca.danielpower.jstudy.core.FlowWord
import ca.danielpower.jstudy.core.FuriganaMode
import ca.danielpower.jstudy.core.GrammarPoint
import ca.danielpower.jstudy.core.GrammarTopic
import ca.danielpower.jstudy.core.HorizontalText
import ca.danielpower.jstudy.core.MasteryRules
import ca.danielpower.jstudy.core.Pace
import ca.danielpower.jstudy.core.PathStory
import ca.danielpower.jstudy.core.ReadingWord
import ca.danielpower.jstudy.core.StoryDifficulty
import ca.danielpower.jstudy.core.StoryLength
import ca.danielpower.jstudy.core.StoryPath
import ca.danielpower.jstudy.core.WordKind
import ca.danielpower.jstudy.core.partTitle
import ca.danielpower.jstudy.core.readStats
import ca.danielpower.jstudy.data.ChapterEntry
import ca.danielpower.jstudy.data.LockInfo
import ca.danielpower.jstudy.data.ReaderFont
import ca.danielpower.jstudy.data.ReaderStyle
import ca.danielpower.jstudy.data.Repository
import ca.danielpower.jstudy.data.SavedStory
import ca.danielpower.jstudy.data.SeriesEntry
import ca.danielpower.jstudy.data.StoryReading
import ca.danielpower.jstudy.data.Word
import ca.danielpower.jstudy.data.levelLabel
import java.text.DateFormat
import java.util.Date
import kotlin.math.roundToInt

// ------------------------------------------------------------------ list and form

@Composable
fun StoriesScreen(state: Screen.Stories, vm: AppViewModel) {
    // The story waiting on "Delete this story?".
    var deleting by remember { mutableStateOf<SavedStory?>(null) }
    val draft = state.draft
    val writing = state.writing != null
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(ui("Stories", "物語"), style = MaterialTheme.typography.headlineMedium)

        // The stories that come with the app, shown once a chapter is within reach.
        val shown = state.path.filter { it.shown }
        val pace = state.pace
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
            SectionLabel(ui("Your path", "あなたの道", "あなた", "道"), Modifier.padding(top = 8.dp))
            val listed = state.nothingNew.filter { it.series.single }.map { it.series.id }.toSet()
            for (entry in shown.filter { it.series.id !in listed }) {
                Box(Modifier.alpha(0.65f)) { PathRow(entry) { vm.openPathStory(entry) } }
            }
        } else {
            SectionLabel(ui("Ready to read", "読める物語", "読める", "物語"))
            for (entry in shown) PathRow(entry) { vm.openPathStory(entry) }
        }
        val waiting = state.path.size - shown.size
        if (waiting > 0) {
            Text(
                (if (waiting == 1) "1 more story opens" else "$waiting more stories open") + " up as you learn.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        // Claude writes stories once you have enough words for them to work with.
        if (state.knownWords >= Repository.WRITING_OPENS_AT) {
            HorizontalDivider(Modifier.padding(top = 12.dp), color = MaterialTheme.colorScheme.onSurface)
            SectionLabel(ui("Write your own", "自分で書く", "自分", "書く"))

            if (!state.signedIn) {
                StorySeal()
                Text(
                    "Claude writes short stories using mostly words you know, with a few new ones to learn.",
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text("Sign in with Google to start.", style = MaterialTheme.typography.bodyLarge)
                SignInWithGoogleButton(vm, Modifier.fillMaxWidth().height(52.dp))
                Text(
                    "Your Google account is only used to keep each person to a fair number of stories a day. " +
                        "Your progress stays on this phone.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                state.error?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                }
            } else {
                OutlinedTextField(
                    value = draft.prompt,
                    onValueChange = { text -> vm.editDraft { it.copy(prompt = text) } },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("What should it be about? (optional)") },
                    placeholder = { Text("Slice of life, a cat who runs a café…") },
                    enabled = !writing,
                )
                val recent = state.stories.map { it.prompt }.filter { it.isNotBlank() }.distinct().take(5)
                if (recent.isNotEmpty()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        for (prompt in recent) {
                            SuggestionChip(
                                onClick = { vm.editDraft { it.copy(prompt = prompt) } },
                                label = { Text(prompt, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                enabled = !writing,
                            )
                        }
                    }
                }

                Text(ui("Length", "長さ"), style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (length in StoryLength.entries) {
                        WashiChip(
                            selected = draft.length == length,
                            onClick = { vm.editDraft { it.copy(length = length) } },
                            label = ui(length.label),
                            enabled = !writing,
                        )
                    }
                }
                Text(ui("Difficulty", "難しさ"), style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (difficulty in StoryDifficulty.entries) {
                        WashiChip(
                            selected = draft.difficulty == difficulty,
                            onClick = { vm.editDraft { it.copy(difficulty = difficulty) } },
                            label = ui(difficulty.label),
                            enabled = !writing,
                        )
                    }
                }
                Text(
                    "${(draft.difficulty.knownShare * 100).roundToInt()}% of the words will be ones you know. " +
                        "New words go to the front of your reviews.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                if (writing) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 3.dp, color = MaterialTheme.colorScheme.primary)
                        Text(state.writing.orEmpty() + "…", style = MaterialTheme.typography.bodyLarge)
                    }
                } else {
                    PrimaryButton(onClick = vm::writeStory, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                        Text(ui("Write a story", "物語を書く"), fontSize = 18.sp)
                    }
                }
                state.error?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                }
            }
        }

        if (state.stories.isNotEmpty()) {
            HorizontalDivider(Modifier.padding(top = 12.dp), color = MaterialTheme.colorScheme.onSurface)
            SectionLabel(ui("Your stories", "あなたの物語"))
            Text("Hold a story to delete it.", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            for (story in state.stories) StoryRow(story, onClick = { vm.openStory(story.id) }, onHold = { deleting = story })
        }
    }

    deleting?.let { story ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete this story?") },
            text = { Text(storyTitle(story), fontFamily = Gothic) },
            confirmButton = {
                TextButton(onClick = {
                    deleting = null
                    vm.deleteStory(story.id)
                }) { Text(ui("Delete", "削除"), color = MaterialTheme.colorScheme.primary) }
            },
            dismissButton = {
                TextButton(onClick = { deleting = null }) { Text(ui("Cancel", "やめる"), color = MaterialTheme.colorScheme.onSurface) }
            },
        )
    }
}

/** 物語 stamped like a seal, over the sign-in prompt. */
@Composable
private fun StorySeal() {
    val color = MaterialTheme.colorScheme.primary
    Box(
        Modifier
            .padding(vertical = 12.dp)
            .rotate(-4f)
            .size(64.dp)
            .border(2.dp, color, WashiShape),
        contentAlignment = Alignment.Center,
    ) {
        Text("物\n語", fontFamily = Gothic, fontWeight = FontWeight.Bold, fontSize = 18.sp, lineHeight = 20.sp, color = color, textAlign = TextAlign.Center)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun StoryRow(story: SavedStory, onClick: () -> Unit, onHold: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerLow, WashiShape)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, WashiShape)
            .combinedClickable(onLongClick = onHold, onClick = onClick),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(storyTitle(story), style = MaterialTheme.typography.titleLarge)
            Text(
                story.body.lineSequence().firstOrNull { it.isNotBlank() }.orEmpty(),
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = Gothic,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                listOfNotNull(
                    DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(story.createdAt)),
                    StoryLength.entries.firstOrNull { it.name == story.length }?.let { ui(it.label) },
                    StoryDifficulty.entries.firstOrNull { it.name == story.difficulty }?.let { ui(it.label) },
                    story.readAt?.let { ui("Read", "読んだ", "読む") },
                ).joinToString("  ·  "),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * A story on the path, by its English title. One chapter shows its first
 * line and what's new in it; more show a chapter count, how far you've read
 * and what's open next, with a seal-red bar down the side.
 */
@Composable
private fun PathRow(entry: SeriesEntry, onClick: () -> Unit) {
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
            if (!single) Tag("${entry.chapters.size} chapters", seal)
            Text(entry.series.title, style = MaterialTheme.typography.titleLarge)
            val shownChapter = entry.next ?: entry.chapters.first()
            Text(
                shownChapter.story.body.lineSequence().firstOrNull { it.isNotBlank() }.orEmpty(),
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = Gothic,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!single) {
                Box(Modifier.padding(vertical = 4.dp).fillMaxWidth().height(4.dp).clip(WashiShape).background(MaterialTheme.colorScheme.outlineVariant)) {
                    Box(Modifier.fillMaxWidth(entry.readCount / entry.chapters.size.toFloat()).fillMaxHeight().background(seal))
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (!single && !done) {
                    Text(
                        "${entry.readCount} of ${entry.chapters.size} read",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                val next = entry.next
                Text(
                    when {
                        done -> ui("Read", "読んだ", "読む")
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

/** What's new in a chapter: "3 new words · 2 new kana", or that nothing is. */
@Composable
private fun newLabel(entry: ChapterEntry): String {
    val words = entry.gap.words.size
    val kana = entry.gap.kana.size
    if (words == 0 && kana == 0 && entry.newTopic == null) return ui("Nothing new", "新しい言葉なし", "新しい", "言葉")
    return listOfNotNull(
        words.takeIf { it > 0 }?.let { if (it == 1) "1 new word" else "$it new words" },
        kana.takeIf { it > 0 }?.let { "$it new kana" },
        entry.newTopic?.title,
    ).joinToString(" · ")
}

/** Today's new words and kana against the daily limit, gold once it's reached. */
@Composable
private fun PaceMeter(pace: Pace) {
    val color = if (pace.atLimit) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth()) {
            Text(ui("Today", "今日"), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
            Text(
                "${pace.newToday} of ${pace.limit} new",
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
                Text("${pace.reviewsDue} reviews waiting", style = MaterialTheme.typography.titleMedium)
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
            entry.story.body.lineSequence().firstOrNull { it.isNotBlank() }.orEmpty(),
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = Gothic,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            ui("Nothing new", "新しい言葉なし", "新しい", "言葉"),
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
                    SectionLabel(ui("People so far", "登場人物", "登場", "人物"))
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
                        entry.story.body.lineSequence().firstOrNull { it.isNotBlank() }.orEmpty(),
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
                    val words = entry.gap.words.size + entry.gap.kana.size
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
        gap.words.size > gap.allowance -> "${gap.words.size} new words"
        else -> "${gap.kana.size} new kana"
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
            Text("It uses ${topic.title} (${topic.titleEn.lowercase()}), which you haven't met yet.$where", style = MaterialTheme.typography.bodyMedium)
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
        if (gap.kana.size > StoryPath.MAX_KANA) {
            Text(
                "It has ${gap.kana.size} kana you haven't learned: ${lock.kana.joinToString(" ") { it.text }}.",
                style = MaterialTheme.typography.bodyMedium,
            )
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

/** A chapter as the lock sheet names it: "Ramen night 3", or a one-chapter story's title. */
private fun chapterName(entry: ChapterEntry) =
    if (entry.series.single) entry.series.title else "${entry.series.title} ${entry.chapter.number}"

/** A chapter's new grammar, kana and words, to see before reading it, or to mark known. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BeforeReadingScreen(state: Screen.BeforeReading, vm: AppViewModel) {
    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        WashiIconButton(WashiIcons.Back, "Back", vm::storyBackPressed)
        val chapter = state.chapter
        if (chapter != null && !chapter.series.single) {
            SectionLabel("${chapter.series.title} · chapter ${chapter.chapter.number} of ${chapter.series.chapters.size}")
        }
        Text(state.story.title, style = MaterialTheme.typography.headlineSmall)
        val count = state.words.size + state.kana.size
        val what = listOfNotNull(
            state.topic?.let { "the grammar" },
            state.kana.takeIf { it.isNotEmpty() }?.let { "kana" },
            state.words.takeIf { it.isNotEmpty() }?.let { "words" },
        )
        val list = if (what.size > 1) what.dropLast(1).joinToString(", ") + " and " + what.last() else what.firstOrNull().orEmpty()
        Text(
            "Before you read, learn ${if (state.topic == null) "the " else ""}$list new in this chapter.",
            style = MaterialTheme.typography.bodyLarge,
        )
        val pace = state.pace
        val over = pace?.over(count) == true
        if (over) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceContainerLow, WashiShape)
                    .border(1.dp, MaterialTheme.colorScheme.tertiary, WashiShape)
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text("Over today's limit", style = MaterialTheme.typography.titleSmall)
                Text(
                    "You've started ${pace!!.newToday} new words and kana today. This chapter adds $count more.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            state.topic?.let { topic ->
                SectionLabel(ui("Grammar", "文法"))
                TopicCard(topic, onMore = { state.topicPage?.let(vm::openGrammar) })
            }
            if (state.kana.isNotEmpty()) {
                SectionLabel(ui("Kana", "仮名"), if (state.topic != null) Modifier.padding(top = 12.dp) else Modifier)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (unit in state.kana) {
                        Column(
                            Modifier
                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, WashiShape)
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(unit.text, fontFamily = Gothic, fontWeight = FontWeight.Bold, fontSize = 22.sp)
                            Text(unit.romaji, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            if (state.words.isNotEmpty()) {
                if (state.kana.isNotEmpty() || state.topic != null) SectionLabel(ui("Words", "言葉"), Modifier.padding(top = 12.dp))
                for (word in state.words) {
                    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        // Furigana above the word, as in the reader.
                        Column(Modifier.weight(1f)) {
                            if (word.kana != word.display) {
                                Text(word.kana, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text(word.display, fontFamily = Gothic, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                        }
                        Text(
                            word.senses.firstOrNull()?.glosses?.take(2)?.joinToString("; ").orEmpty(),
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.End,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
        if (over) {
            // A suggestion, not a lock: the alternative leads, and learning anyway is one tap.
            PrimaryButton(onClick = vm::readNothingNew, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                Text(if (state.nothingNewOpen) "Read something with nothing new" else "Review instead", fontSize = 18.sp)
            }
            SecondaryButton(onClick = vm::learnStoryWords, modifier = Modifier.fillMaxWidth()) { Text("Learn anyway") }
        } else {
        // Grammar on its own needs no cards: read on once it's explained.
        PrimaryButton(onClick = vm::learnStoryWords, modifier = Modifier.fillMaxWidth().height(56.dp)) {
            Text(
                when (count) {
                    0 -> ui("Start reading", "読み始める", "読む", "始める")
                    1 -> "Learn this"
                    else -> "Learn these"
                },
                fontSize = 18.sp,
            )
        }
        }
        if (count > 0) {
            TextButton(onClick = vm::knowStoryWords, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Text(
                    if (count == 1 && state.topic == null) "I know this already" else "I know these already",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** A grammar topic before a chapter: the pattern, what it does, an example, and a link to its full page. */
@Composable
private fun TopicCard(topic: GrammarTopic, onMore: () -> Unit) {
    val indigo = MaterialTheme.colorScheme.secondary
    Row(
        Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clip(WashiShape)
            .background(MaterialTheme.colorScheme.surfaceContainerLow, WashiShape)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, WashiShape),
    ) {
        Box(Modifier.width(3.dp).fillMaxHeight().background(indigo))
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(topic.title, fontFamily = Gothic, fontWeight = FontWeight.Bold, fontSize = 20.sp, color = indigo)
                Text(topic.titleEn, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(topic.summary, style = MaterialTheme.typography.bodyMedium)
            topic.example?.let { (jp, en) ->
                Text(jp, fontFamily = Gothic, fontSize = 18.sp)
                Text(en, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(
                "Full explanation",
                style = MaterialTheme.typography.labelLarge,
                color = indigo,
                textDecoration = TextDecoration.Underline,
                modifier = Modifier.clickable(onClick = onMore).padding(vertical = 2.dp),
            )
        }
    }
}

@Composable
private fun storyTitle(story: SavedStory) = partTitle(story.title.ifBlank { ui("Untitled", "無題", "題") }, story.part)

// ------------------------------------------------------------------ reader

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StoryReaderScreen(state: Screen.StoryReader, vm: AppViewModel) {
    val reading = state.reading
    val style = state.style
    // The settings menu shares the pane with words and sentences; opening one closes the other.
    var menu by remember { mutableStateOf(false) }
    // What the pane shows: a word, or the sentence around it. The title is paragraph TITLE.
    var open by remember(reading.story.id) { mutableStateOf<Opened?>(null) }
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
    val close = {
        open = null
        menu = false
    }
    val tap = { key: Pair<Int, Int> ->
        menu = false
        open = if (selected == key) null else Opened(key.first, key.second, sentence = false)
        // Looking a word up means reading it doesn't count towards its mastery.
        val id = reading.word(key.first, key.second)?.takeIf { it.kind == WordKind.WORD }?.wordId
        if (id != null) vm.lookedUp(id)
    }
    val hold = { key: Pair<Int, Int> ->
        menu = false
        open = Opened(key.first, key.second, sentence = true)
    }
    BackHandler(enabled = open != null || menu, onBack = close)

    // A tap in the story that isn't on a word clears the pane.
    val tapToClear = if (open != null || menu) Modifier.pointerInput(Unit) { detectTapGestures { close() } } else Modifier
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            WashiIconButton(WashiIcons.Back, "Back", vm::storyBackPressed)
            Box(Modifier.weight(1f)) {
                StoryTitle(reading, MaterialTheme.typography.headlineSmall, style.font.family, selected, lit, tap, hold, close)
            }
            CompositionLocalProvider(LocalContentColor provides if (menu) MaterialTheme.colorScheme.primary else LocalContentColor.current) {
                WashiIconButton(WashiIcons.Settings, "Reader settings", {
                    open = null
                    menu = !menu
                })
            }
        }
        // Always there, with the story below it, so it never covers the text and nothing moves when it fills.
        Box(Modifier.fillMaxWidth().height(PANE_HEIGHT).background(MaterialTheme.colorScheme.surfaceContainerLow)) {
            val o = open
            when {
                menu -> ReaderMenu(style, vm::setReaderStyle)
                o == null -> Text(
                    "Tap a word to see what it means. Hold one to read its sentence in English.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.align(Alignment.Center).padding(horizontal = 32.dp),
                )
                o.sentence -> SentenceCard(openSentence?.translation)
                openWord != null -> WordCard(
                    word = openWord,
                    reading = reading,
                    onAdd = vm::addStoryWord,
                    onKnow = { id ->
                        open = null
                        vm.knowStoryWord(id)
                    },
                )
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        val end = @Composable { StoryEnd(state, vm, onWord = tap) }
        // A sequel opens in this same screen; it starts on its first page.
        key(reading.story.id) {
            val lesson = reading.lesson
            if (lesson != null) {
                LessonPage(
                    state = state,
                    lesson = lesson,
                    size = style.size.toFloat(),
                    family = style.font.family,
                    selected = selected,
                    lit = lit,
                    onTap = tap,
                    onHold = hold,
                    onBlank = close,
                    modifier = Modifier.weight(1f).fillMaxWidth().then(tapToClear),
                )
            } else if (style.vertical) {
                VerticalStory(
                    reading = reading,
                    size = style.size.toFloat(),
                    family = style.font.family,
                    selected = selected,
                    lit = lit,
                    onTap = { p, i -> tap(p to i) },
                    onHold = { p, i -> hold(p to i) },
                    onBlank = close,
                    end = end,
                    onEnd = vm::finishStory,
                    modifier = Modifier.weight(1f).fillMaxWidth().then(tapToClear),
                )
            } else {
                HorizontalStory(
                    reading = reading,
                    size = style.size.toFloat(),
                    family = style.font.family,
                    selected = selected,
                    lit = lit,
                    onTap = { p, i -> tap(p to i) },
                    onHold = { p, i -> hold(p to i) },
                    onBlank = close,
                    end = end,
                    onEnd = vm::finishStory,
                    modifier = Modifier.weight(1f).fillMaxWidth().then(tapToClear),
                )
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
    onTap: (p: Int, i: Int) -> Unit,
    onHold: (p: Int, i: Int) -> Unit,
    onBlank: () -> Unit,
    end: @Composable () -> Unit,
    onEnd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    // Words measured as StoryWord draws them: each piece as wide as its text or its furigana, shown or not.
    val rubySize = size * RUBY_RATIO
    val flows = remember(reading, density, size, family) {
        val base = TextStyle(fontSize = size.sp, fontFamily = family)
        val ruby = TextStyle(fontSize = rubySize.sp, fontFamily = family)
        reading.paragraphs.map { words ->
            words.map { word ->
                val width = word.ruby.sumOf { piece ->
                    maxOf(
                        measurer.measure(piece.base, base, softWrap = false).size.width,
                        measurer.measure(piece.ruby ?: " ", ruby, softWrap = false).size.width,
                    )
                }
                FlowWord(word.surface, width.toFloat())
            }
        }
    }
    BoxWithConstraints(modifier) {
        val lineHeight = with(density) { (rubySize * 1.2f).sp.toPx() + (size * 1.5f).sp.toPx() }
        val gap = with(density) { PARAGRAPH_GAP.toPx() }
        // A pixel or two spare, so rounding never pushes a word past the edge.
        val width = constraints.maxWidth - with(density) { (PAGE_PADDING_H * 2 + 2.dp).toPx() }
        val height = constraints.maxHeight - with(density) { (PAGE_PADDING * 2 + PAGE_LABEL_HEIGHT).toPx() }
        val pages = remember(flows, width, height, lineHeight, gap) {
            HorizontalText.pages(flows.map { HorizontalText.lines(it, width) }, lineHeight, gap, height)
        }
        val lineDp = with(density) { lineHeight.toDp() }
        StoryPager(pages.size, reverse = false, end, onEnd) { page ->
            Column(Modifier.fillMaxSize().padding(horizontal = PAGE_PADDING_H, vertical = PAGE_PADDING)) {
                pages[page].forEachIndexed { n, line ->
                    if (n > 0 && line.words.first == 0) Spacer(Modifier.height(PARAGRAPH_GAP))
                    Row(Modifier.height(lineDp), verticalAlignment = Alignment.Bottom) {
                        for (i in line.words) {
                            val p = line.paragraph
                            val word = reading.paragraphs[p][i]
                            StoryWord(
                                word = word,
                                reading = reading,
                                selected = selected == (p to i),
                                lit = (p to i) in lit,
                                onTap = { if (word.tappable()) onTap(p, i) else onBlank() },
                                onHold = { onHold(p, i) },
                                size = size.sp,
                                rubySize = rubySize.sp,
                                family = family,
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * A grammar topic's page: its English with the Japanese examples between,
 * tapping words and holding for a sentence's English as in a story. It's
 * one page that scrolls, always across, to look back at whenever.
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
    onTap: (Pair<Int, Int>) -> Unit,
    onHold: (Pair<Int, Int>) -> Unit,
    onBlank: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val reading = state.reading
    val notes = lesson.notes.groupBy { it.before }
    Column(
        modifier.verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Tag(ui("Grammar", "文法"), MaterialTheme.colorScheme.secondary)
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
                        onTap = { if (word.tappable()) onTap(p to i) else onBlank() },
                        onHold = { onHold(p to i) },
                        size = size.sp,
                        rubySize = (size * RUBY_RATIO).sp,
                        family = family,
                    )
                }
            }
        }
    }
}

/**
 * [count] pages to swipe between, with the page number under them, then the
 * [end] page, which calls [onEnd] when it's reached. [reverse] puts the next
 * page on the left.
 */
@Composable
internal fun StoryPager(
    count: Int,
    reverse: Boolean,
    end: @Composable () -> Unit,
    onEnd: () -> Unit,
    page: @Composable (Int) -> Unit,
) {
    val state = rememberPagerState { count + 1 }
    val atEnd = state.settledPage == count
    LaunchedEffect(atEnd) { if (atEnd) onEnd() }
    Column(Modifier.fillMaxSize()) {
        HorizontalPager(state, Modifier.weight(1f).fillMaxWidth(), reverseLayout = reverse, verticalAlignment = Alignment.Top) {
            if (it < count) page(it) else end()
        }
        Box(Modifier.fillMaxWidth().height(PAGE_LABEL_HEIGHT), contentAlignment = Alignment.Center) {
            if (count > 1 && state.currentPage < count) {
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

/** Furigana's size against the text it sits on. */
internal const val RUBY_RATIO = 10f / 22f
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
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StoryTitle(
    reading: StoryReading,
    style: TextStyle,
    family: FontFamily,
    selected: Pair<Int, Int>?,
    lit: Set<Pair<Int, Int>>,
    onTap: (Pair<Int, Int>) -> Unit,
    onHold: (Pair<Int, Int>) -> Unit,
    onBlank: () -> Unit,
) {
    if (reading.title.isEmpty()) {
        // A bundled chapter's English title, or a grammar page's pattern, isn't read like the story.
        val title = reading.story.title.takeIf { reading.story.pathId != null } ?: partTitle(ui("Untitled", "無題", "題"), reading.story.part)
        Text(title, style = style, fontFamily = if (reading.lesson != null) Gothic else null)
        return
    }
    FlowRow(modifier = Modifier.fillMaxWidth()) {
        reading.title.forEachIndexed { i, word ->
            val key = StoryReading.TITLE to i
            StoryWord(
                word = word,
                reading = reading,
                selected = selected == key,
                lit = key in lit,
                onTap = { if (word.tappable()) onTap(key) else onBlank() },
                onHold = { onHold(key) },
                size = style.fontSize,
                rubySize = 10.sp,
                family = family,
                weight = style.fontWeight,
            )
        }
        reading.story.part?.let { part ->
            Text(partTitle("", part), style = style, fontFamily = family, modifier = Modifier.align(Alignment.Bottom))
        }
    }
}

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

    var modifier: Modifier = Modifier
    if (selected || lit) modifier = modifier.background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f), WashiShape)
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
                        modifier = Modifier.alpha(if (showRuby && piece.ruby != null) 1f else 0f),
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
        MenuRow(ui("Size", "大きさ")) {
            ReaderStyle.SIZES.forEachIndexed { i, size ->
                MenuOption(style.size == size, { onChange(style.copy(size = size)) }) {
                    // Each step a little bigger, as a sample; the real sizes wouldn't fit the buttons.
                    Text("あ", fontFamily = style.font.family, fontSize = (13 + 3 * i).sp)
                }
            }
        }
        MenuRow(ui("Font", "文字")) {
            for (font in ReaderFont.entries) {
                MenuOption(style.font == font, { onChange(style.copy(font = font)) }) {
                    Text(font.label, fontFamily = font.family, fontSize = 15.sp, maxLines = 1)
                }
            }
        }
        MenuRow(ui("Direction", "向き")) {
            MenuOption(!style.vertical, { onChange(style.copy(vertical = false)) }) {
                Text(ui("Horizontal", "横書き", "横", "書く"), style = MaterialTheme.typography.labelLarge, maxLines = 1)
            }
            MenuOption(style.vertical, { onChange(style.copy(vertical = true)) }) {
                Text(ui("Vertical", "縦書き", "縦", "書く"), style = MaterialTheme.typography.labelLarge, maxLines = 1)
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

/** The open word's card: its meanings, conjugation and grammar, and what to do with it. */
@Composable
private fun WordCard(
    word: ReadingWord,
    reading: StoryReading,
    onAdd: (Long) -> Unit,
    onKnow: (Long) -> Unit,
) {
    val entry = word.wordId?.let { reading.words[it] }
    // A particle or ending on its own: the card explains its grammar and has no dictionary entry.
    val grammarOnly = word.kind == WordKind.GRAMMAR
    // Centred when it all fits. When the meanings scroll they take the spare room, so the card fills the pane from the top.
    Column(
        Modifier.fillMaxSize().padding(start = 20.dp, end = 4.dp, top = 8.dp, bottom = 4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            val title = if (grammarOnly) word.surface else entry?.display ?: word.lemma
            Text(title, fontFamily = Gothic, fontWeight = FontWeight.Bold, fontSize = 22.sp, modifier = Modifier.weight(1f, fill = false))
            val kana = entry?.kana ?: word.reading
            if (!grammarOnly && kana != title) {
                Text(kana, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Column(
            Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(end = 16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (word.conjugated) {
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
                Text(
                    levelLabel(entry.jlpt) + "  ·  " + when {
                        status == null -> ui("New", "新しい")
                        status.suspended -> "Suspended"
                        status.state == CardState.NEW -> "New, in your new-word queue"
                        else -> "Mastery ${status.mastery}"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            for (use in word.grammar.distinctBy { it.point.id }) GrammarNote(use.point)
        }
        if (entry != null && reading.isUnknown(word)) {
            // Words aren't queued just for being read; "Add to reviews" is the main action, "I know this" the quiet one.
            Row(
                Modifier.padding(end = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (reading.statuses[entry.id] == null) {
                    PrimaryButton(onClick = { onAdd(entry.id) }, modifier = Modifier.weight(1f).height(40.dp)) {
                        Text(ui("Add to reviews", "復習に追加"))
                    }
                } else {
                    Spacer(Modifier.weight(1f))
                }
                TextButton(onClick = { onKnow(entry.id) }) {
                    Text(ui("I know this", "知っている"), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

/** One grammar point in the word card: the pattern, what it means, and an example. */
@Composable
private fun GrammarNote(point: GrammarPoint) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.padding(top = 2.dp)) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 4.dp)) {
            Text(point.pattern, fontFamily = Gothic, fontWeight = FontWeight.Bold, fontSize = 17.sp)
            Text(
                ui("Grammar", "文法") + "  ·  " + levelLabel(point.level),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(point.meaning, fontSize = 16.sp, lineHeight = 21.sp)
        Text(point.note, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(point.example, fontFamily = Gothic, fontSize = 15.sp, modifier = Modifier.padding(top = 2.dp))
        Text(point.exampleMeaning, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// ------------------------------------------------------------------ end page

/**
 * After the last page: how the read went, the new words in it to add to your
 * reviews, and the next part. Tapping a word opens it in the pane, as in the story.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StoryEnd(state: Screen.StoryReader, vm: AppViewModel, onWord: (Pair<Int, Int>) -> Unit) {
    val reading = state.reading
    val stats = readStats(reading.paragraphs, reading.coverage, reading.story.readMillis)
    val newWords = reading.newWords
    val unadded = newWords.map { it.second.id }.filter { reading.statuses[it] == null }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val chapter = state.chapter
        if (chapter != null && !chapter.series.single) SectionLabel("Chapter ${chapter.chapter.number} done")
        Text(if (chapter != null && !chapter.series.single) chapter.chapter.story.title else ui("The end", "終わり"), style = MaterialTheme.typography.headlineSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            EndStat(stats.characters.toString(), ui("Characters", "文字"))
            EndStat("${(stats.knownShare * 100).roundToInt()}%", ui("Seen", "見た", "見る"))
            EndStat(stats.millis?.let(::duration) ?: "–", ui("Time", "時間"))
            EndStat(stats.charactersPerMinute?.let { "$it/min" } ?: "–", ui("Speed", "速さ"))
        }

        val onPath = reading.story.pathId != null
        if (onPath) {
            state.impact?.let { WordImpact(it) }
            state.impact?.topic?.let { topic ->
                SectionLabel(ui("Grammar", "文法"), Modifier.padding(top = 8.dp))
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
        } else {
            SectionLabel(ui("New words", "新しい言葉"), Modifier.padding(top = 8.dp))
        }
        if (onPath) {
            // A path story's new words were seen before reading it.
        } else if (newWords.isEmpty()) {
            Text("You knew every word in this one.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            Text("Tap a word for more.", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            for ((at, word) in newWords) NewWordRow(word, reading, onClick = { onWord(at) }, onAdd = { vm.addStoryWord(word.id) })
            if (unadded.size > 1) {
                SecondaryButton(onClick = { vm.addStoryWords(unadded) }, modifier = Modifier.fillMaxWidth()) {
                    Text(ui("Add all to reviews", "全部復習に追加", "全部", "復習", "追加"))
                }
            }
        }

        HorizontalDivider(Modifier.padding(top = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)
        val next = reading.next
        val nextStep = state.next
        when {
            onPath -> {
                val pace = state.pace
                if (pace?.nudge == true) {
                    // At today's limit or behind on reviews: something with nothing new, or reviews, before the next chapter.
                    pace.takeIf { it.atLimit }?.let { PaceMeter(it) }
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
                        TextButton(onClick = { vm.openStory(nextStep.storyId) }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                            Text(
                                nextStep.label.replaceFirst("Next", "Next anyway"),
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
                if (chapter != null && !chapter.series.single) {
                    SecondaryButton(onClick = { vm.openSeries(chapter.series.id) }, modifier = Modifier.fillMaxWidth()) {
                        Text("Back to chapters")
                    }
                } else {
                    SecondaryButton(onClick = vm::openStories, modifier = Modifier.fillMaxWidth()) {
                        Text(ui("Back to stories", "物語に戻る", "物語", "戻る"))
                    }
                }
            }
            next != null -> PrimaryButton(onClick = { vm.openStory(next) }, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Text(ui("Read the next part", "続きを読む", "続き", "読む"))
            }
            !state.signedIn -> {}
            state.writing != null -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 3.dp, color = MaterialTheme.colorScheme.primary)
                Text(state.writing + "…", style = MaterialTheme.typography.bodyLarge)
            }
            else -> {
                PrimaryButton(onClick = vm::writeSequel, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                    Text(ui("Write a sequel", "続きを書く", "続き", "書く"))
                }
                Text(
                    "Claude writes the next part from the story so far, with the same length and difficulty.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        state.error?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
        }

        // Google Play asks for a way to flag offensive AI-written content from inside the app.
        if (!onPath) {
            val context = LocalContext.current
            TextButton(onClick = { reportStory(context, reading.story) }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Text(
                    ui("Report this story", "この話を報告", "話", "報告"),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** What reading a path story did for your words: seen for it, and moved up to known or mastered. */
@Composable
private fun WordImpact(impact: ReadImpact) {
    SectionLabel(ui("Your words", "あなたの言葉", "あなた", "言葉"), Modifier.padding(top = 8.dp))
    ImpactRow("New, now seen", impact.seen.size, masteryColor(0))
    ImpactRow("Seen → known", impact.known.size, masteryColor(MasteryRules.HIDE_AT))
    ImpactRow("Known → mastered", impact.mastered.size, masteryColor(MasteryRules.OFF_AT))
    val moved = listOfNotNull(
        impact.known.takeIf { it.isNotEmpty() }?.let { w -> w.joinToString("、") { it.display } + " moved to known." },
        impact.mastered.takeIf { it.isNotEmpty() }?.let { w -> w.joinToString("、") { it.display } + if (w.size == 1) " is mastered." else " are mastered." },
    )
    if (moved.isNotEmpty()) {
        Text(moved.joinToString(" "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ImpactRow(label: String, count: Int, color: Color) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyLarge, color = color, modifier = Modifier.weight(1f))
        Text(count.toString(), style = MaterialTheme.typography.titleMedium)
    }
}

/** Opens an email to the developer with the story in it, for a story that's offensive or broken. */
private fun reportStory(context: Context, story: SavedStory) {
    val body = "What's wrong with this story?\n\n\n" +
        "---\nTitle: ${story.title}\nPrompt: ${story.prompt}\n" +
        "Length: ${story.length}, difficulty: ${story.difficulty}\n\n${story.body}"
    val uri = Uri.parse(
        "mailto:${context.getString(R.string.report_email)}" +
            "?subject=${Uri.encode("Jiwa Jiwa story report")}&body=${Uri.encode(body)}",
    )
    try {
        context.startActivity(Intent(Intent.ACTION_SENDTO, uri))
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, "No email app to send the report with.", Toast.LENGTH_LONG).show()
    }
}

@Composable
private fun RowScope.EndStat(value: String, label: String) {
    Column(
        Modifier
            .weight(1f)
            .background(MaterialTheme.colorScheme.surfaceContainerLow, WashiShape)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, WashiShape)
            .padding(vertical = 10.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(value, style = MaterialTheme.typography.titleMedium, maxLines = 1)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
    }
}

/** 45s, or 4:05 for minutes and seconds. */
private fun duration(millis: Long): String {
    val seconds = millis / 1000
    return if (seconds < 60) "${seconds}s" else "%d:%02d".format(seconds / 60, seconds % 60)
}

/** A new word on the end page: what it is, and a button to add it if it isn't queued yet. */
@Composable
private fun NewWordRow(word: Word, reading: StoryReading, onClick: () -> Unit, onAdd: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(WashiShape).clickable(onClick = onClick).padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(word.display, fontFamily = Gothic, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                if (word.kana != word.display) {
                    Text(word.kana, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Text(
                word.senses.firstOrNull()?.glosses?.joinToString("; ").orEmpty(),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (reading.statuses[word.id] == null) {
            PrimaryButton(onClick = onAdd, modifier = Modifier.height(40.dp)) { Text(ui("Add", "追加")) }
        } else {
            Text(ui("In reviews", "復習中", "復習"), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
