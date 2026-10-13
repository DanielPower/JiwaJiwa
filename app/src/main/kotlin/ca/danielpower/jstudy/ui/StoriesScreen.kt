package ca.danielpower.jstudy.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
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
            for (story in state.stories) StoryRow(story) { vm.openStory(story.id) }
        }
    }
}

@Composable
private fun StoryRow(story: SavedStory, onClick: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerLow, WashiShape)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, WashiShape)
            .clickable(onClick = onClick),
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
    var confirmDelete by remember { mutableStateOf(false) }
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
    val focus = open?.key
    // Where the open word is on screen, so the pane can stay clear of it.
    var focusBounds by remember { mutableStateOf<Rect?>(null) }
    val close = { open = null }
    val tap = { key: Pair<Int, Int> -> open = if (selected == key) null else Opened(key.first, key.second, sentence = false) }
    val hold = { key: Pair<Int, Int> -> open = Opened(key.first, key.second, sentence = true) }
    val onFocus = { bounds: Rect -> focusBounds = bounds }
    BackHandler(enabled = open != null) { open = null }

    // A tap anywhere that isn't a word or a button closes the pane.
    val tapToClose = if (open != null) Modifier.pointerInput(Unit) { detectTapGestures { open = null } } else Modifier
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val paneMaxHeight = maxHeight * 0.45f
        var top by remember { mutableFloatStateOf(0f) }
        var paneHeight by remember { mutableIntStateOf(0) }
        Column(Modifier.fillMaxSize().onGloballyPositioned { top = it.boundsInRoot().top }.then(tapToClose)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                WashiIconButton(WashiIcons.Back, "Back", vm::openStories)
                val cov = reading.coverage
                val n = cov.newWords.size
                Text(
                    "${(cov.knownShare * 100).roundToInt()}% known  ·  " + if (n == 1) "1 new word" else "$n new words",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                // Names the layout it switches to.
                SecondaryButton(
                    onClick = { vm.setStoryVertical(!state.vertical) },
                    modifier = Modifier.height(40.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp),
                ) {
                    Text(if (state.vertical) ui("Horizontal", "横書き", "横", "書く") else ui("Vertical", "縦書き", "縦", "書く"), fontSize = 14.sp)
                }
                WashiIconButton(WashiIcons.Delete, "Delete story", { confirmDelete = true })
            }
            val title: @Composable (TextStyle) -> Unit = { style ->
                StoryTitle(reading, style, selected, lit, focus, tap, hold, close, onFocus)
            }
            if (state.vertical) {
                Column(
                    Modifier.padding(horizontal = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    title(MaterialTheme.typography.headlineSmall)
                }
                VerticalStory(
                    reading = reading,
                    selected = selected,
                    lit = lit,
                    focus = focus,
                    onTap = { p, i -> tap(p to i) },
                    onHold = { p, i -> hold(p to i) },
                    onBlank = close,
                    onFocus = onFocus,
                    modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp),
                )
            } else Column(
                modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                title(MaterialTheme.typography.headlineMedium)
                reading.paragraphs.forEachIndexed { p, words ->
                    FlowRow(modifier = Modifier.fillMaxWidth()) {
                        words.forEachIndexed { i, word ->
                            StoryWord(
                                word = word,
                                reading = reading,
                                selected = selected == (p to i),
                                lit = (p to i) in lit,
                                onTap = { if (word.tappable()) tap(p to i) else close() },
                                onHold = { hold(p to i) },
                                onFocus = if (focus == (p to i)) onFocus else null,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }

        val o = open
        if (o != null) {
            // The pane sits at the top, like a header, unless the open word is under it there.
            val margin = with(LocalDensity.current) { PANE_MARGIN.toPx() }
            val covered = focusBounds?.let { it.top < top + margin + paneHeight } == true
            Surface(
                modifier = Modifier
                    .align(if (covered) Alignment.BottomCenter else Alignment.TopCenter)
                    .padding(PANE_MARGIN)
                    .fillMaxWidth()
                    .onSizeChanged { paneHeight = it.height },
                shape = WashiShape,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.onSurface),
                shadowElevation = 6.dp,
            ) {
                if (o.sentence) {
                    SentenceCard(openSentence?.translation, paneMaxHeight, onClose = close)
                } else if (openWord != null) {
                    WordCard(
                        word = openWord,
                        reading = reading,
                        maxHeight = paneMaxHeight,
                        onAdd = vm::addStoryWord,
                        onKnow = { id ->
                            open = null
                            vm.knowStoryWord(id)
                        },
                        onClose = close,
                    )
                }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this story?") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    vm.deleteStory(reading.story.id)
                }) { Text(ui("Delete", "削除"), color = MaterialTheme.colorScheme.primary) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text(ui("Cancel", "やめる"), color = MaterialTheme.colorScheme.onSurface) }
            },
        )
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
private val PANE_MARGIN = 8.dp

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
    focus: Pair<Int, Int>?,
    onTap: (Pair<Int, Int>) -> Unit,
    onHold: (Pair<Int, Int>) -> Unit,
    onBlank: () -> Unit,
    onFocus: (Rect) -> Unit,
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
                onFocus = if (focus == key) onFocus else null,
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
    /** Set on the open word, to report where it is. */
    onFocus: ((Rect) -> Unit)?,
    size: TextUnit = TEXT_SIZE.sp,
    weight: FontWeight? = null,
) {
    val unknown = reading.isUnknown(word)
    val showRuby = reading.showsRuby(word)
    val color = if (unknown || selected) MaterialTheme.colorScheme.primary else LocalContentColor.current

    var modifier: Modifier = Modifier
    if (selected || lit) modifier = modifier.background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f), WashiShape)
    modifier = modifier.storyWordGestures(word.tappable(), onTap, onHold)
    if (onFocus != null) modifier = modifier.onGloballyPositioned { onFocus(it.boundsInRoot()) }
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
private fun SentenceCard(translation: String?, maxHeight: Dp, onClose: () -> Unit) {
    Row(
        Modifier.heightIn(max = maxHeight).padding(start = 20.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            translation ?: "There's no translation saved for this story.",
            fontSize = 19.sp,
            lineHeight = 26.sp,
            color = if (translation == null) MaterialTheme.colorScheme.onSurfaceVariant else LocalContentColor.current,
            modifier = Modifier.weight(1f).padding(vertical = 12.dp).verticalScroll(rememberScrollState()),
        )
        WashiIconButton(WashiIcons.Close, "Close", onClose)
    }
}

/** The open word's card: its meanings, conjugation and grammar, and what to do with it. */
@Composable
private fun WordCard(
    word: ReadingWord,
    reading: StoryReading,
    maxHeight: Dp,
    onAdd: (Long) -> Unit,
    onKnow: (Long) -> Unit,
    onClose: () -> Unit,
) {
    val entry = word.wordId?.let { reading.words[it] }
    // A particle or ending on its own: the card explains its grammar and has no dictionary entry.
    val grammarOnly = word.kind == WordKind.GRAMMAR
    Column(
        Modifier.heightIn(max = maxHeight).padding(start = 20.dp, end = 4.dp, top = 4.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            val title = if (grammarOnly) word.surface else entry?.display ?: word.lemma
            Text(title, fontFamily = Gothic, fontWeight = FontWeight.Bold, fontSize = 28.sp, modifier = Modifier.weight(1f, fill = false))
            val kana = entry?.kana ?: word.reading
            if (!grammarOnly && kana != title) {
                Text(kana, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.weight(1f))
            WashiIconButton(WashiIcons.Close, "Close", onClose)
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
