package ca.danielpower.jstudy.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ca.danielpower.jstudy.core.CardState
import ca.danielpower.jstudy.core.Direction
import ca.danielpower.jstudy.core.FuriganaMode
import ca.danielpower.jstudy.data.Summary
import ca.danielpower.jstudy.data.Word
import kotlinx.coroutines.delay

@Composable
fun App(vm: AppViewModel, screen: Screen) {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(modifier = Modifier.safeDrawingPadding()) {
            when (screen) {
                Screen.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                is Screen.Home -> HomeScreen(screen.summary, vm::startReview, vm::studyMore, vm::openSettings)
                is Screen.Review -> ReviewScreen(screen, vm)
                is Screen.Waiting -> WaitingScreen(screen, vm)
                is Screen.Done -> DoneScreen(screen.summary, vm::studyMore, vm::goHome)
                is Screen.Settings -> SettingsScreen(screen, vm::saveSettings, vm::goHome)
            }
        }
    }
}

// ------------------------------------------------------------------ home

@Composable
private fun HomeScreen(summary: Summary, onStudy: () -> Unit, onStudyMore: () -> Unit, onSettings: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("jstudy", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
            TextButton(onClick = onSettings) { Text("Settings") }
        }
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Today", style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Count("New", summary.newCards, MaterialTheme.colorScheme.primary)
                    Count("Learning", summary.learning, MaterialTheme.colorScheme.tertiary)
                    Count("Review", summary.reviews, MaterialTheme.colorScheme.secondary)
                }
            }
        }
        Text(
            buildString {
                append(if (summary.streak == 1) "1 day streak" else "${summary.streak} day streak")
                append("  ·  ${summary.wordsStarted} of ${summary.wordsTotal} words started")
                if (summary.daysAhead > 0) {
                    append("\n")
                    append(if (summary.daysAhead == 1) "Studying 1 day ahead" else "Studying ${summary.daysAhead} days ahead")
                }
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.weight(1f))
        if (summary.anythingDue) {
            Button(onClick = onStudy, modifier = Modifier.fillMaxWidth().height(56.dp)) { Text("Study", fontSize = 18.sp) }
        } else {
            Text("All done for today.", style = MaterialTheme.typography.titleMedium)
            Button(onClick = onStudyMore, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                Text("Study more", fontSize = 18.sp)
            }
        }
    }
}

@Composable
private fun Count(label: String, value: Int, color: androidx.compose.ui.graphics.Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("$value", style = MaterialTheme.typography.headlineMedium, color = color, fontWeight = FontWeight.SemiBold)
        Text(label, style = MaterialTheme.typography.labelLarge)
    }
}

// ------------------------------------------------------------------ review

@Composable
private fun ReviewScreen(state: Screen.Review, vm: AppViewModel) {
    val word = state.current.word
    val production = state.current.card.direction == Direction.PRODUCTION
    // First sight of a new word: show everything and ask how well it's known.
    val intro = !production && state.current.card.state == CardState.NEW
    val revealed = state.revealed || intro
    val noRipple = remember { MutableInteractionSource() }

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = vm::goHome) { Text("Close") }
            Spacer(Modifier.weight(1f))
            QueueCount(state.counts.new, MaterialTheme.colorScheme.primary)
            QueueCount(state.counts.learning, MaterialTheme.colorScheme.tertiary)
            QueueCount(state.counts.review, MaterialTheme.colorScheme.secondary)
            Spacer(Modifier.weight(1f))
            TextButton(onClick = vm::undoLast, enabled = state.canUndo) { Text("Undo") }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .clickable(interactionSource = noRipple, indication = null) {
                    if (!revealed) vm.reveal()
                }
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (intro) {
                Text(
                    "New word",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            } else {
                Spacer(Modifier.height(32.dp))
            }
            if (production) {
                Meanings(word, limit = 3, large = true)
                if (state.revealed) {
                    HorizontalDivider(Modifier.padding(vertical = 24.dp))
                    WordFace(word, showFurigana = true)
                }
            } else {
                Box(
                    modifier = Modifier.clickable(
                        interactionSource = noRipple,
                        indication = null,
                        enabled = word.showsKanji && state.current.furigana != FuriganaMode.OFF,
                    ) { vm.toggleFurigana() },
                ) {
                    WordFace(word, showFurigana = revealed || state.furiganaVisible)
                }
                if (!revealed && word.showsKanji && state.current.furigana == FuriganaMode.HIDDEN) {
                    Text(
                        "Tap the word for its reading",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                if (revealed) {
                    HorizontalDivider(Modifier.padding(vertical = 24.dp))
                    Meanings(word, limit = 3, large = false)
                }
            }
            if (intro) {
                Text(
                    "N${word.jlpt}  ·  How well do you know this word?",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 24.dp),
                )
            } else if (revealed) {
                Text(
                    "N${word.jlpt}  ·  mastery ${state.current.mastery}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 24.dp),
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (intro) {
                val rules = state.rules
                OutlinedButton(onClick = { vm.introduce(0) }, modifier = Modifier.weight(1f).height(64.dp)) {
                    Text("Unknown")
                }
                OutlinedButton(onClick = { vm.introduce(rules.hideAt) }, modifier = Modifier.weight(1f).height(64.dp)) {
                    Text("Known")
                }
                OutlinedButton(onClick = { vm.introduce(rules.offAt) }, modifier = Modifier.weight(1f).height(64.dp)) {
                    Text("Mastered")
                }
            } else if (state.revealed) {
                Button(
                    onClick = { vm.answer(false) },
                    modifier = Modifier.weight(1f).height(64.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    ),
                ) { Text("Wrong", fontSize = 18.sp) }
                Button(
                    onClick = { vm.answer(true) },
                    modifier = Modifier.weight(1f).height(64.dp),
                ) { Text("Right", fontSize = 18.sp) }
            } else {
                OutlinedButton(onClick = vm::reveal, modifier = Modifier.fillMaxWidth().height(64.dp)) {
                    Text("Show answer", fontSize = 18.sp)
                }
            }
        }
    }
}

@Composable
private fun QueueCount(value: Int, color: androidx.compose.ui.graphics.Color) {
    Text(
        "$value",
        color = color,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(horizontal = 10.dp),
        style = MaterialTheme.typography.titleMedium,
    )
}

@Composable
private fun WordFace(word: Word, showFurigana: Boolean) {
    val length = word.display.length
    val size = when {
        length <= 4 -> 48.sp
        length <= 6 -> 40.sp
        length <= 8 -> 32.sp
        else -> 26.sp
    }
    val segments = word.furigana
    if (word.showsKanji && segments != null) {
        FuriganaText(segments, showFurigana, fontSize = size)
    } else {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            // Keep the same height as a word with a reading row.
            Text(" ", fontSize = size * 0.42f)
            Text(word.kana, fontSize = size)
            if (word.usuallyKana && word.kanji != null) {
                // Usually written in kana, but the kanji spelling turns up too.
                Text(
                    "sometimes ${word.kanji}",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun Meanings(word: Word, limit: Int, large: Boolean) {
    val senses = word.senses.take(limit)
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = if (large) Alignment.CenterHorizontally else Alignment.Start,
    ) {
        senses.forEachIndexed { i, sense ->
            val text = sense.glosses.joinToString("; ")
            Text(
                if (senses.size > 1) "${i + 1}. $text" else text,
                style = if (large) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleMedium,
                textAlign = if (large) TextAlign.Center else TextAlign.Start,
            )
        }
    }
}

// ------------------------------------------------------------------ waiting and done

@Composable
private fun WaitingScreen(state: Screen.Waiting, vm: AppViewModel) {
    LaunchedEffect(state.dueAt) {
        delay((state.dueAt - System.currentTimeMillis()).coerceAtLeast(0) + 500)
        vm.refresh()
    }
    val minutes = ((state.dueAt - System.currentTimeMillis()) / 60_000 + 1).coerceAtLeast(1)
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("The next card is due in $minutes min", style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Spacer(Modifier.height(24.dp))
        OutlinedButton(onClick = vm::goHome) { Text("Home") }
    }
}

@Composable
private fun DoneScreen(summary: Summary, onStudyMore: () -> Unit, onHome: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Done for today", style = MaterialTheme.typography.headlineMedium)
        if (summary.reviewedToday > 0) {
            val accuracy = 100 * summary.correctToday / summary.reviewedToday
            Text(
                "${summary.reviewedToday} answers today, $accuracy% right",
                style = MaterialTheme.typography.bodyLarge,
            )
        }
        Text(
            "Study more brings tomorrow's reviews forward and adds the next new words.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Button(onClick = onStudyMore, modifier = Modifier.fillMaxWidth().height(56.dp)) { Text("Study more", fontSize = 18.sp) }
        OutlinedButton(onClick = onHome, modifier = Modifier.fillMaxWidth().height(56.dp)) { Text("Home", fontSize = 18.sp) }
        Spacer(Modifier.width(1.dp))
    }
}
