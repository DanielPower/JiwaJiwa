package ca.danielpower.jstudy.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ca.danielpower.jstudy.core.CardState
import ca.danielpower.jstudy.core.FuriganaMode
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
        Text("Stories", style = MaterialTheme.typography.headlineMedium)

        if (!state.hasClaudeKey) {
            Text(
                "Stories are written by Claude. Add your Claude API key in Settings to start.",
                style = MaterialTheme.typography.bodyLarge,
            )
            SecondaryButton(onClick = vm::openSettings) { Text("Settings") }
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

            Text("Length", style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (length in StoryLength.entries) {
                    WashiChip(
                        selected = draft.length == length,
                        onClick = { vm.editDraft { it.copy(length = length) } },
                        label = length.label,
                        enabled = !writing,
                    )
                }
            }
            Text("Difficulty", style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (difficulty in StoryDifficulty.entries) {
                    WashiChip(
                        selected = draft.difficulty == difficulty,
                        onClick = { vm.editDraft { it.copy(difficulty = difficulty) } },
                        label = difficulty.label,
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
                    Text("Write a story", fontSize = 18.sp)
                }
            }
            state.error?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
            }
        }

        if (state.stories.isNotEmpty()) {
            HorizontalDivider(Modifier.padding(top = 12.dp), color = MaterialTheme.colorScheme.onSurface)
            SectionLabel("Your stories")
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
            Text(story.title.ifBlank { "Untitled" }, style = MaterialTheme.typography.titleLarge)
            Text(
                story.body.lineSequence().firstOrNull { it.isNotBlank() }.orEmpty(),
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = Mincho,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                listOfNotNull(
                    DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(story.createdAt)),
                    StoryLength.entries.firstOrNull { it.name == story.length }?.label,
                    StoryDifficulty.entries.firstOrNull { it.name == story.difficulty }?.label,
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
    // The word shown in the sheet, as (paragraph, index).
    var open by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    val openWord = open?.let { (p, i) -> reading.paragraphs.getOrNull(p)?.getOrNull(i) }
    // In vertical text a word low on the page would sit under the sheet, so it moves to the top.
    var sheetAtTop by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
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
                val switchTo = if (state.vertical) "Horizontal text" else "Vertical text"
                SecondaryButton(
                    onClick = { vm.setStoryVertical(!state.vertical) },
                    modifier = Modifier.size(40.dp).semantics { contentDescription = switchTo },
                    contentPadding = PaddingValues(0.dp),
                ) {
                    Text(if (state.vertical) "横" else "縦", fontFamily = Mincho, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                }
                WashiIconButton(WashiIcons.Delete, "Delete story", { confirmDelete = true })
            }
            if (state.vertical) {
                Column(
                    Modifier.padding(horizontal = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(reading.story.title.ifBlank { "Untitled" }, style = MaterialTheme.typography.headlineSmall)
                    QueuedNote(state.queued)
                }
                VerticalStory(
                    reading = reading,
                    selected = open,
                    onOpen = { p, i, low ->
                        if (open == (p to i)) {
                            open = null
                        } else {
                            open = p to i
                            sheetAtTop = low
                        }
                    },
                    modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp),
                )
            } else Column(
                modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(reading.story.title.ifBlank { "Untitled" }, style = MaterialTheme.typography.headlineMedium)
                QueuedNote(state.queued)
                reading.paragraphs.forEachIndexed { p, words ->
                    FlowRow(modifier = Modifier.fillMaxWidth()) {
                        words.forEachIndexed { i, word ->
                            StoryWord(
                                word = word,
                                reading = reading,
                                selected = open == (p to i),
                                onOpen = {
                                    open = if (open == (p to i)) null else (p to i)
                                    sheetAtTop = false
                                },
                            )
                        }
                    }
                }
                // Room to scroll the last lines out from under the sheet.
                Spacer(Modifier.height(if (openWord != null) 300.dp else 24.dp))
            }
        }
        if (openWord != null) {
            WordSheet(
                word = openWord,
                reading = reading,
                onKnow = { id ->
                    open = null
                    vm.knowStoryWord(id)
                },
                onClose = { open = null },
                atTop = sheetAtTop,
                modifier = Modifier.align(if (sheetAtTop) Alignment.TopCenter else Alignment.BottomCenter),
            )
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
                }) { Text("Delete", color = MaterialTheme.colorScheme.primary) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Cancel", color = MaterialTheme.colorScheme.onSurface) }
            },
        )
    }
}

@Composable
private fun QueuedNote(queued: Int?) {
    if (queued != null && queued > 0) {
        Text(
            if (queued == 1) "Its new word is now first in your new-word queue."
            else "Its $queued new words are now first in your new-word queue.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
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
    return isUnknown(word) || word.kind == WordKind.NAME || rules.furigana(mastery) == FuriganaMode.SHOWN
}

internal fun ReadingWord.tappable() = kind == WordKind.WORD || kind == WordKind.NAME

internal const val TEXT_SIZE = 22
internal const val RUBY_SIZE = 10

@Composable
private fun StoryWord(word: ReadingWord, reading: StoryReading, selected: Boolean, onOpen: () -> Unit) {
    val tappable = word.tappable()
    val unknown = reading.isUnknown(word)
    val showRuby = reading.showsRuby(word)
    val color = if (unknown || selected) MaterialTheme.colorScheme.primary else LocalContentColor.current

    var modifier: Modifier = Modifier
    if (selected) modifier = modifier.background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f), WashiShape)
    if (tappable) modifier = modifier.clickable(onClick = onOpen)
    Row(modifier = modifier, verticalAlignment = Alignment.Bottom) {
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
                    fontSize = TEXT_SIZE.sp,
                    lineHeight = (TEXT_SIZE * 1.5).sp,
                    fontFamily = Mincho,
                    softWrap = false,
                    color = color,
                    textDecoration = if (unknown) TextDecoration.Underline else null,
                )
            }
        }
    }
}

/** The tapped word, in a sheet along the bottom of the reader. */
@Composable
private fun WordSheet(
    word: ReadingWord,
    reading: StoryReading,
    onKnow: (Long) -> Unit,
    onClose: () -> Unit,
    atTop: Boolean,
    modifier: Modifier = Modifier,
) {
    val entry = word.wordId?.let { reading.words[it] }
    val noRipple = remember { MutableInteractionSource() }
    Column(
        modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            // Taps on the sheet stay on the sheet.
            .clickable(interactionSource = noRipple, indication = null) {},
    ) {
        if (!atTop) HorizontalDivider(thickness = 2.dp, color = MaterialTheme.colorScheme.onSurface)
        Column(
            Modifier.padding(start = 24.dp, end = 8.dp, top = 8.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(entry?.display ?: word.lemma, fontFamily = Mincho, fontWeight = FontWeight.Bold, fontSize = 30.sp)
                val kana = entry?.kana ?: word.reading
                if (kana != (entry?.display ?: word.lemma)) {
                    Text(kana, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.weight(1f))
                WashiIconButton(WashiIcons.Close, "Close", onClose)
            }
            Column(Modifier.padding(end = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (word.conjugated) {
                    Text(
                        "${word.surface}: ${word.conjugation.joinToString(", ")}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                }
                if (entry == null) {
                    Text(
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
                            status == null -> "New"
                            status.suspended -> "Suspended"
                            status.state == CardState.NEW -> "New, in your new-word queue"
                            else -> "Mastery ${status.mastery}"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (reading.isUnknown(word)) {
                        SecondaryButton(
                            onClick = { onKnow(entry.id) },
                            modifier = Modifier.fillMaxWidth().height(48.dp).padding(top = 4.dp),
                        ) { Text("I know this") }
                    }
                }
            }
        }
        if (atTop) HorizontalDivider(thickness = 2.dp, color = MaterialTheme.colorScheme.onSurface)
    }
}
