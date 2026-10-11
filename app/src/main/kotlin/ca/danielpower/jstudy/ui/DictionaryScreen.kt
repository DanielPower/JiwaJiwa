package ca.danielpower.jstudy.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ca.danielpower.jstudy.core.Card
import ca.danielpower.jstudy.core.CardState
import ca.danielpower.jstudy.core.Direction
import ca.danielpower.jstudy.core.StudyClock
import ca.danielpower.jstudy.data.WordStatus
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

// ------------------------------------------------------------------ word list

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DictionaryScreen(state: Screen.Dictionary, vm: AppViewModel, listState: LazyListState) {
    val keyboard = LocalSoftwareKeyboardController.current
    // Show new results from the top, but keep the place when coming back from a word.
    var shownRows by remember { mutableStateOf(state.rows) }
    LaunchedEffect(state.rows) {
        if (state.rows !== shownRows) listState.scrollToItem(0)
        shownRows = state.rows
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 8.dp, top = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Dictionary", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
            TextButton(onClick = vm::goHome) { Text("Done") }
        }
        OutlinedTextField(
            value = state.query,
            onValueChange = vm::search,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            placeholder = { Text("Kana, kanji, romaji or English") },
            singleLine = true,
            trailingIcon = {
                if (state.query.isNotEmpty()) TextButton(onClick = { vm.search("") }) { Text("Clear") }
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
        )
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            for (level in 5 downTo 1) {
                FilterChip(
                    selected = level in state.levels,
                    onClick = { vm.toggleLevel(level) },
                    label = { Text("N$level") },
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            for (filter in StatusFilter.entries) {
                FilterChip(
                    selected = state.status == filter,
                    onClick = { vm.setStatusFilter(filter) },
                    label = { Text(filter.label) },
                )
            }
        }
        Text(
            if (state.rows.size == 1) "1 word" else "${state.rows.size} words",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
        )
        HorizontalDivider()
        LazyColumn(state = listState, modifier = Modifier.weight(1f)) {
            items(state.rows, key = { it.entry.id }) { row ->
                DictionaryRowItem(row) {
                    keyboard?.hide()
                    vm.openWord(row.entry.id)
                }
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun DictionaryRowItem(row: DictionaryRow, onClick: () -> Unit) {
    val e = row.entry
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(e.display, style = MaterialTheme.typography.titleLarge)
                if (e.secondary != null) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        e.secondary,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Text(e.gloss, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(12.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text("N${e.jlpt}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            statusLabel(row.status)?.let {
                Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

private fun statusLabel(status: WordStatus?): String? = when {
    status == null -> null
    status.suspended -> "Suspended"
    status.state == CardState.NEW -> null
    else -> "Mastery ${status.mastery}"
}

// ------------------------------------------------------------------ word page

@Composable
fun WordScreen(state: Screen.WordPage, vm: AppViewModel) {
    val detail = state.detail
    val word = detail.word
    val recognition = detail.cards.firstOrNull { it.direction == Direction.RECOGNITION }
    val production = detail.cards.firstOrNull { it.direction == Direction.PRODUCTION }
    val started = recognition != null && recognition.state != CardState.NEW
    var confirmReset by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = vm::closeWord) { Text("Back") }
        }
        Column(
            modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                WordFace(word, showFurigana = true)
                Text(
                    "N${word.jlpt}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            val others = detail.forms.filter { it != word.display && it != word.kana }
            if (others.isNotEmpty()) {
                Text(
                    "Also written " + others.joinToString("、"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Heading("Meanings")
            word.senses.forEachIndexed { i, sense ->
                Column {
                    val pos = detail.partsOfSpeech.getOrNull(i).orEmpty()
                    // Repeat the part of speech only when it changes.
                    if (pos.isNotEmpty() && pos != detail.partsOfSpeech.getOrNull(i - 1)) {
                        Text(pos, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    }
                    Text("${i + 1}. " + sense.glosses.joinToString("; "), style = MaterialTheme.typography.bodyLarge)
                    sense.info?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            Heading("Progress")
            Text(
                when {
                    detail.suspended -> "Suspended"
                    recognition == null -> "Not started"
                    recognition.state == CardState.NEW -> "Waiting in the new-word queue"
                    else -> "Mastery ${detail.mastery}"
                },
                style = MaterialTheme.typography.bodyLarge,
            )
            if (started) {
                CardLine("Japanese → English", recognition!!, state.clock)
                production?.let { CardLine("English → Japanese", it, state.clock) }
            }

            Heading("Actions")
            if (!started) {
                Button(onClick = vm::learnNext, modifier = Modifier.fillMaxWidth()) { Text("Learn next") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                val verb = if (started) "Set as" else "Mark"
                OutlinedButton(onClick = { vm.markKnown(state.rules.hideAt) }, modifier = Modifier.weight(1f)) {
                    Text("$verb known")
                }
                OutlinedButton(onClick = { vm.markKnown(state.rules.offAt) }, modifier = Modifier.weight(1f)) {
                    Text("$verb mastered")
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = { vm.setSuspended(!detail.suspended) }, modifier = Modifier.weight(1f)) {
                    Text(if (detail.suspended) "Unsuspend" else "Suspend")
                }
                if (detail.cards.isNotEmpty() || detail.mastery > 0) {
                    OutlinedButton(onClick = { confirmReset = true }, modifier = Modifier.weight(1f)) { Text("Reset") }
                }
            }
            Text(
                "Suspended words never come up for review or as new words. Reset forgets the word's progress and " +
                    "history, and it comes back as a new word in its usual place.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (detail.history.isNotEmpty()) {
                Heading("History")
                val format = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT)
                for ((direction, entry) in detail.history.take(50)) {
                    Row(Modifier.fillMaxWidth()) {
                        Text(
                            Instant.ofEpochMilli(entry.at).atZone(ZoneId.systemDefault()).format(format),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            if (direction == Direction.RECOGNITION) "JP→EN" else "EN→JP",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            if (entry.correct) "Right" else "Wrong",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = if (entry.correct) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                            modifier = Modifier.width(56.dp),
                        )
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("Reset ${word.display}?") },
            text = { Text("This forgets its mastery, schedule and review history.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmReset = false
                    vm.resetWord()
                }) { Text("Reset") }
            },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun Heading(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 12.dp))
}

@Composable
private fun CardLine(label: String, card: Card, clock: StudyClock) {
    val due = when (card.state) {
        CardState.NEW -> "not started"
        CardState.LEARNING, CardState.RELEARNING -> "learning"
        CardState.REVIEW -> dueText(card.dueAt, clock)
    }
    Text(
        "$label: $due · ${card.reps} reviews, ${card.lapses} lapses",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

private fun dueText(dueAt: Long, clock: StudyClock): String {
    val days = clock.dayOf(dueAt) - clock.dayOf(System.currentTimeMillis())
    return when {
        days <= 0 -> "due today"
        days == 1L -> "due tomorrow"
        days < 60 -> "due in $days days"
        else -> "due " + LocalDate.ofEpochDay(clock.dayOf(dueAt)).format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
    }
}
