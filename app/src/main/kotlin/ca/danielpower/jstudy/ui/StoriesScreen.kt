package ca.danielpower.jstudy.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import ca.danielpower.jstudy.core.FlowWord
import ca.danielpower.jstudy.core.HorizontalText
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ca.danielpower.jstudy.core.CardState
import ca.danielpower.jstudy.core.FuriganaMode
import ca.danielpower.jstudy.core.GrammarPoint
import ca.danielpower.jstudy.core.MasteryRules
import ca.danielpower.jstudy.core.ReadingWord
import ca.danielpower.jstudy.core.StoryDifficulty
import ca.danielpower.jstudy.core.StoryLength
import ca.danielpower.jstudy.core.WordKind
import ca.danielpower.jstudy.data.SavedStory
import ca.danielpower.jstudy.data.StoryReading
import ca.danielpower.jstudy.data.levelLabel
import java.text.DateFormat
import java.util.Date
import kotlin.math.roundToInt

/** Below this many started words, stories have little to work with. */
private const val FEW_WORDS = 300

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

        if (!state.hasClaudeKey) {
            Text(
                "Stories are written by Claude. Add your Claude API key in Settings to start.",
                style = MaterialTheme.typography.bodyLarge,
            )
            SecondaryButton(onClick = vm::openSettings) { Text(ui("Settings", "設定")) }
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
            if (state.knownWords < FEW_WORDS) {
                Text(
                    "You've started ${state.knownWords} words. Stories read best once you know a few hundred, " +
                        "so early ones will have more new words.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

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
            text = { Text(story.title.ifBlank { ui("Untitled", "無題", "題") }, fontFamily = Gothic) },
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
            Text(story.title.ifBlank { ui("Untitled", "無題", "題") }, style = MaterialTheme.typography.titleLarge)
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
                ).joinToString("  ·  "),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ------------------------------------------------------------------ reader

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StoryReaderScreen(state: Screen.StoryReader, vm: AppViewModel) {
    val reading = state.reading
    // What the pane shows: a word, or the sentence around it. The title is paragraph TITLE.
    var open by remember { mutableStateOf<Opened?>(null) }
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
    val close = { open = null }
    val tap = { key: Pair<Int, Int> -> open = if (selected == key) null else Opened(key.first, key.second, sentence = false) }
    val hold = { key: Pair<Int, Int> -> open = Opened(key.first, key.second, sentence = true) }
    BackHandler(enabled = open != null) { open = null }

    // A tap in the story that isn't on a word clears the pane.
    val tapToClear = if (open != null) Modifier.pointerInput(Unit) { detectTapGestures { open = null } } else Modifier
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(start = 8.dp, end = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            WashiIconButton(WashiIcons.Back, "Back", vm::openStories)
            Box(Modifier.weight(1f)) {
                StoryTitle(reading, MaterialTheme.typography.headlineSmall, selected, lit, tap, hold, close)
            }
        }
        // Always there, with the story below it, so it never covers the text and nothing moves when it fills.
        Box(Modifier.fillMaxWidth().height(PANE_HEIGHT).background(MaterialTheme.colorScheme.surfaceContainerLow)) {
            val o = open
            when {
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
        if (state.vertical) {
            VerticalStory(
                reading = reading,
                selected = selected,
                lit = lit,
                onTap = { p, i -> tap(p to i) },
                onHold = { p, i -> hold(p to i) },
                onBlank = close,
                modifier = Modifier.weight(1f).fillMaxWidth().then(tapToClear),
            )
        } else {
            HorizontalStory(
                reading = reading,
                selected = selected,
                lit = lit,
                onTap = { p, i -> tap(p to i) },
                onHold = { p, i -> hold(p to i) },
                onBlank = close,
                modifier = Modifier.weight(1f).fillMaxWidth().then(tapToClear),
            )
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
    selected: Pair<Int, Int>?,
    lit: Set<Pair<Int, Int>>,
    onTap: (p: Int, i: Int) -> Unit,
    onHold: (p: Int, i: Int) -> Unit,
    onBlank: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    // Words measured as StoryWord draws them: each piece as wide as its text or its furigana, shown or not.
    val flows = remember(reading, density) {
        val base = TextStyle(fontSize = TEXT_SIZE.sp, fontFamily = Gothic)
        val ruby = TextStyle(fontSize = RUBY_SIZE.sp, fontFamily = Gothic)
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
        val lineHeight = with(density) { (RUBY_SIZE * 1.2f).sp.toPx() + (TEXT_SIZE * 1.5f).sp.toPx() }
        val gap = with(density) { PARAGRAPH_GAP.toPx() }
        // A pixel or two spare, so rounding never pushes a word past the edge.
        val width = constraints.maxWidth - with(density) { (2 * PAGE_PADDING_H + 2.dp).toPx() }
        val height = constraints.maxHeight - with(density) { (2 * PAGE_PADDING + PAGE_LABEL_HEIGHT).toPx() }
        val pages = remember(flows, width, height, lineHeight, gap) {
            HorizontalText.pages(flows.map { HorizontalText.lines(it, width) }, lineHeight, gap, height)
        }
        val lineDp = with(density) { lineHeight.toDp() }
        StoryPager(pages.size, reverse = false) { page ->
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
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Pages to swipe between, with the page number under them. [reverse] puts the next page on the left. */
@Composable
internal fun StoryPager(count: Int, reverse: Boolean, page: @Composable (Int) -> Unit) {
    val state = rememberPagerState { count }
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

internal const val TEXT_SIZE = 22
internal const val RUBY_SIZE = 10
internal val PAGE_PADDING = 16.dp
private val PAGE_PADDING_H = 24.dp
internal val PAGE_LABEL_HEIGHT = 28.dp
private val PARAGRAPH_GAP = 16.dp
/** Tall enough for a word's first meanings and its buttons; the rest scrolls. */
private val PANE_HEIGHT = 210.dp

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
    selected: Pair<Int, Int>?,
    lit: Set<Pair<Int, Int>>,
    onTap: (Pair<Int, Int>) -> Unit,
    onHold: (Pair<Int, Int>) -> Unit,
    onBlank: () -> Unit,
) {
    if (reading.title.isEmpty()) {
        Text(ui("Untitled", "無題", "題"), style = style)
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
                weight = style.fontWeight,
            )
        }
    }
}

/**
 * A tap opens a word in the pane, or closes the pane on a word with nothing
 * to show; a long press opens the word's sentence.
 */
@OptIn(ExperimentalFoundationApi::class)
internal fun Modifier.storyWordGestures(tappable: Boolean, onTap: () -> Unit, onHold: () -> Unit): Modifier =
    if (tappable) {
        combinedClickable(onLongClick = onHold, onClick = onTap)
    } else {
        combinedClickable(interactionSource = null, indication = null, onLongClick = onHold, onClick = onTap)
    }

@Composable
private fun StoryWord(
    word: ReadingWord,
    reading: StoryReading,
    selected: Boolean,
    lit: Boolean,
    onTap: () -> Unit,
    onHold: () -> Unit,
    size: TextUnit = TEXT_SIZE.sp,
    weight: FontWeight? = null,
) {
    val unknown = reading.isUnknown(word)
    val showRuby = reading.showsRuby(word)
    val color = if (unknown || selected) MaterialTheme.colorScheme.primary else LocalContentColor.current

    var modifier: Modifier = Modifier
    if (selected || lit) modifier = modifier.background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f), WashiShape)
    modifier = modifier.storyWordGestures(word.tappable(), onTap, onHold)
    Box(modifier) {
        Row(verticalAlignment = Alignment.Bottom) {
            for (piece in word.ruby) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = piece.ruby ?: " ",
                        fontSize = RUBY_SIZE.sp,
                        lineHeight = (RUBY_SIZE * 1.2).sp,
                        fontFamily = Gothic,
                        maxLines = 1,
                        softWrap = false,
                        color = color.copy(alpha = 0.75f),
                        modifier = Modifier.alpha(if (showRuby && piece.ruby != null) 1f else 0f),
                    )
                    Text(
                        text = piece.base,
                        fontSize = size,
                        lineHeight = size * 1.5f,
                        fontFamily = Gothic,
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

/** A sentence's English, or why there isn't any. */
@Composable
private fun SentenceCard(translation: String?) {
    Text(
        translation ?: "There's no translation saved for this story.",
        fontSize = 19.sp,
        lineHeight = 26.sp,
        color = if (translation == null) MaterialTheme.colorScheme.onSurfaceVariant else LocalContentColor.current,
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp),
    )
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
    Column(
        Modifier.fillMaxSize().padding(start = 20.dp, end = 4.dp, top = 12.dp, bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            val title = if (grammarOnly) word.surface else entry?.display ?: word.lemma
            Text(title, fontFamily = Gothic, fontWeight = FontWeight.Bold, fontSize = 28.sp, modifier = Modifier.weight(1f, fill = false))
            val kana = entry?.kana ?: word.reading
            if (!grammarOnly && kana != title) {
                Text(kana, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Column(
            Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(end = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
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
                    MeaningLine(if (senses.size > 1) numeral(i) else null, sense.glosses.joinToString("; "))
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
                    PrimaryButton(onClick = { onAdd(entry.id) }, modifier = Modifier.weight(1f).height(44.dp)) {
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
    Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(top = 4.dp)) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 4.dp)) {
            Text(point.pattern, fontFamily = Gothic, fontWeight = FontWeight.Bold, fontSize = 20.sp)
            Text(
                ui("Grammar", "文法") + "  ·  " + levelLabel(point.level),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(point.meaning, fontSize = 19.sp, lineHeight = 26.sp)
        Text(point.note, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(point.example, fontFamily = Gothic, fontSize = 17.sp, modifier = Modifier.padding(top = 2.dp))
        Text(point.exampleMeaning, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
