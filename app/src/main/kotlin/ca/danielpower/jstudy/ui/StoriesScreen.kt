package ca.danielpower.jstudy.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.font.FontWeight
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
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Stories", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
            TextButton(onClick = vm::goHome) { Text("Home") }
        }

        if (!state.hasClaudeKey) {
            Text(
                "Stories are written by Claude. Add your Claude API key in Settings to start.",
                style = MaterialTheme.typography.bodyLarge,
            )
            OutlinedButton(onClick = vm::openSettings) { Text("Settings") }
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
                    FilterChip(
                        selected = draft.length == length,
                        onClick = { vm.editDraft { it.copy(length = length) } },
                        label = { Text(length.label) },
                        enabled = !writing,
                    )
                }
            }
            Text("Difficulty", style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (difficulty in StoryDifficulty.entries) {
                    FilterChip(
                        selected = draft.difficulty == difficulty,
                        onClick = { vm.editDraft { it.copy(difficulty = difficulty) } },
                        label = { Text(difficulty.label) },
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
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 3.dp)
                    Text(state.writing.orEmpty() + "…", style = MaterialTheme.typography.bodyLarge)
                }
            } else {
                Button(onClick = vm::writeStory, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                    Text("Write a story", fontSize = 18.sp)
                }
            }
            state.error?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
            }
        }

        if (state.stories.isNotEmpty()) {
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            for (story in state.stories) StoryRow(story) { vm.openStory(story.id) }
        }
    }
}

@Composable
private fun StoryRow(story: SavedStory, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(story.title.ifBlank { "Untitled" }, style = MaterialTheme.typography.titleMedium)
            Text(
                story.body.lineSequence().firstOrNull { it.isNotBlank() }.orEmpty(),
                style = MaterialTheme.typography.bodyMedium,
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
    // The word whose popover is open, as (paragraph, index).
    var open by remember { mutableStateOf<Pair<Int, Int>?>(null) }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = vm::openStories) { Text("Back") }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { confirmDelete = true }) { Text("Delete") }
        }
        Column(
            modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(reading.story.title.ifBlank { "Untitled" }, style = MaterialTheme.typography.headlineSmall)
            val cov = reading.coverage
            Text(
                buildString {
                    append("${(cov.knownShare * 100).roundToInt()}% known")
                    val n = cov.newWords.size
                    append(if (n == 1) "  ·  1 new word" else "  ·  $n new words")
                    if (state.queued != null && state.queued > 0) {
                        append(
                            if (state.queued == 1) "\nIts new word is now first in your new-word queue."
                            else "\nIts ${state.queued} new words are now first in your new-word queue.",
                        )
                    }
                },
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            reading.paragraphs.forEachIndexed { p, words ->
                FlowRow(modifier = Modifier.fillMaxWidth()) {
                    words.forEachIndexed { i, word ->
                        StoryWord(
                            word = word,
                            reading = reading,
                            expanded = open == (p to i),
                            onOpen = { open = p to i },
                            onClose = { open = null },
                            onKnow = { id ->
                                open = null
                                vm.knowStoryWord(id)
                            },
                        )
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
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
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

private fun StoryReading.isKnown(id: Long?): Boolean {
    val s = id?.let { statuses[it] } ?: return false
    return s.state != CardState.NEW || s.suspended
}

private const val TEXT_SIZE = 22
private const val RUBY_SIZE = 11

@Composable
private fun StoryWord(
    word: ReadingWord,
    reading: StoryReading,
    expanded: Boolean,
    onOpen: () -> Unit,
    onClose: () -> Unit,
    onKnow: (Long) -> Unit,
) {
    val tappable = word.kind == WordKind.WORD || word.kind == WordKind.NAME
    val unknown = word.kind == WordKind.WORD && !reading.isKnown(word.wordId)
    val mastery = word.wordId?.let { reading.statuses[it]?.mastery } ?: 0
    // Furigana follows each word's mastery, as on cards; new words always have it.
    val showRuby = unknown || word.kind == WordKind.NAME || reading.rules.furigana(mastery) == FuriganaMode.SHOWN
    val color = if (unknown) MaterialTheme.colorScheme.primary else LocalContentColor.current

    Box {
        Row(
            modifier = if (tappable) Modifier.clickable(onClick = onOpen) else Modifier,
            verticalAlignment = Alignment.Bottom,
        ) {
            for (piece in word.ruby) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = piece.ruby ?: " ",
                        fontSize = RUBY_SIZE.sp,
                        lineHeight = (RUBY_SIZE * 1.2).sp,
                        maxLines = 1,
                        softWrap = false,
                        color = color.copy(alpha = 0.8f),
                        modifier = Modifier.alpha(if (showRuby && piece.ruby != null) 1f else 0f),
                    )
                    Text(
                        text = piece.base,
                        fontSize = TEXT_SIZE.sp,
                        lineHeight = (TEXT_SIZE * 1.4).sp,
                        softWrap = false,
                        color = color,
                        fontWeight = if (unknown) FontWeight.Medium else null,
                    )
                }
            }
        }
        if (tappable) {
            DropdownMenu(expanded = expanded, onDismissRequest = onClose) {
                WordPopover(word, reading, unknown, onKnow)
            }
        }
    }
}

@Composable
private fun WordPopover(word: ReadingWord, reading: StoryReading, unknown: Boolean, onKnow: (Long) -> Unit) {
    val entry = word.wordId?.let { reading.words[it] }
    Column(
        modifier = Modifier.widthIn(min = 200.dp, max = 320.dp).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(entry?.display ?: word.lemma, style = MaterialTheme.typography.headlineSmall)
            val kana = entry?.kana ?: word.reading
            if (kana != (entry?.display ?: word.lemma)) {
                Text(kana, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
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
                val text = sense.glosses.joinToString("; ")
                Text(if (senses.size > 1) "${i + 1}. $text" else text, style = MaterialTheme.typography.bodyLarge)
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
            if (unknown) {
                TextButton(onClick = { onKnow(entry.id) }, modifier = Modifier.padding(top = 2.dp)) {
                    Text("I know this")
                }
            }
        }
    }
}
