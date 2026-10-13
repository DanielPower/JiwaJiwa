package ca.danielpower.jstudy.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ca.danielpower.jstudy.core.Card
import ca.danielpower.jstudy.core.CardState
import ca.danielpower.jstudy.core.Direction
import ca.danielpower.jstudy.core.Kanjis
import ca.danielpower.jstudy.core.MasteryRules
import ca.danielpower.jstudy.core.StudyClock
import ca.danielpower.jstudy.data.levelLabel
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

// ------------------------------------------------------------------ word list

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
        Text(
            ui("Dictionary", "辞書"),
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 20.dp, bottom = 4.dp),
        )
        OutlinedTextField(
            value = state.query,
            onValueChange = vm::search,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            placeholder = { Text("Kana, kanji, romaji or English") },
            leadingIcon = { Icon(WashiIcons.Search, contentDescription = null, modifier = Modifier.size(20.dp)) },
            singleLine = true,
            trailingIcon = {
                if (state.query.isNotEmpty()) WashiIconButton(WashiIcons.Close, "Clear", { vm.search("") })
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
        )
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // 0 is "Other": words off the JLPT lists, shown once searched for or started.
            for (level in listOf(5, 4, 3, 2, 1, 0)) {
                WashiChip(
                    selected = level in state.levels,
                    onClick = { vm.toggleLevel(level) },
                    label = levelLabel(level),
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            for (filter in StatusFilter.entries) {
                WashiChip(
                    selected = state.status == filter,
                    onClick = { vm.setStatusFilter(filter) },
                    label = ui(filter.label),
                )
            }
        }
        val kanji = Kanjis.inText(state.query)
        if (kanji.isNotEmpty()) {
            // Searching with kanji: their own pages, ahead of the words.
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(ui("Kanji", "漢字"), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                for (k in kanji) {
                    Box(
                        Modifier.size(40.dp).border(1.dp, MaterialTheme.colorScheme.outline, WashiShape).clickable {
                            keyboard?.hide()
                            vm.openKanji(k)
                        },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(k, fontFamily = Gothic, fontSize = 22.sp, lineHeight = 26.sp)
                    }
                }
            }
        }
        Text(
            if (state.rows.size == 1) "1 word" else "${state.rows.size} words",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(horizontal = 24.dp))
        LazyColumn(state = listState, modifier = Modifier.weight(1f)) {
            items(state.rows, key = { it.entry.id }) { row ->
                DictionaryRowItem(row) {
                    keyboard?.hide()
                    vm.openWord(row.entry.id)
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(horizontal = 24.dp))
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
            Text(levelLabel(e.jlpt), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            val status = row.status
            when {
                status == null -> {}
                status.suspended -> Text(ui("Suspended", "保留中"), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                status.state == CardState.NEW -> {}
                else -> MasteryMeter(status.mastery, Modifier.padding(top = 4.dp))
            }
        }
    }
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
            WashiIconButton(WashiIcons.Back, "Back", vm::closeWord)
        }
        Column(
            modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                    Box(Modifier.weight(1f)) { WordFace(word, showFurigana = true) }
                    LevelSeal(levelLabel(word.jlpt), Modifier.padding(start = 12.dp, bottom = 12.dp))
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface)
                val others = detail.forms.filter { it != word.display && it != word.kana }
                if (others.isNotEmpty()) {
                    Text(
                        "Also written " + others.joinToString("、"),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SectionLabel(ui("Meanings", "意味"))
                word.senses.forEachIndexed { i, sense ->
                    val pos = detail.partsOfSpeech.getOrNull(i).orEmpty()
                    // Repeat the part of speech only when it changes.
                    if (pos.isNotEmpty() && pos != detail.partsOfSpeech.getOrNull(i - 1)) {
                        Text(
                            pos,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = if (i == 0) 0.dp else 6.dp),
                        )
                    }
                    MeaningLine(if (word.senses.size > 1) numeral(i) else null, sense.glosses.joinToString("; "), sense.info)
                }
            }

            if (detail.kanji.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SectionLabel(ui("Kanji", "漢字"))
                    KanjiLinks(detail.kanji, vm::openKanji)
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SectionLabel(ui("Progress", "学習状況"))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    if (started && !detail.suspended) {
                        Box(
                            Modifier.size(64.dp).border(2.dp, masteryColor(detail.mastery), CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("${detail.mastery}", fontFamily = Gothic, fontWeight = FontWeight.Bold, fontSize = 24.sp, lineHeight = 26.sp)
                                Text("mastery", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        when {
                            detail.suspended -> Text(ui("Suspended", "保留中"), style = MaterialTheme.typography.bodyLarge)
                            recognition == null -> Text(ui("Not started", "未学習"), style = MaterialTheme.typography.bodyLarge)
                            recognition.state == CardState.NEW ->
                                Text("Waiting in the new-word queue", style = MaterialTheme.typography.bodyLarge)
                            else -> {
                                CardLine(ui("Japanese → English", "日本語から英語"), recognition, state.clock)
                                production?.let { CardLine(ui("English → Japanese", "英語から日本語"), it, state.clock) }
                            }
                        }
                    }
                }
                if (detail.history.isNotEmpty()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        for ((_, entry) in detail.history.take(10)) AnswerMark(entry.correct)
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SectionLabel(ui("Actions", "操作"))
                if (!started) {
                    PrimaryButton(onClick = vm::learnNext, modifier = Modifier.fillMaxWidth().height(48.dp)) { Text(ui("Learn next", "次に覚える", "次", "覚える")) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SecondaryButton(onClick = { vm.markKnown(MasteryRules.HIDE_AT) }, modifier = Modifier.weight(1f).height(48.dp)) {
                        Text(if (started) ui("Set as known", "知っている") else ui("Mark known", "知っている"))
                    }
                    SecondaryButton(onClick = { vm.markKnown(MasteryRules.OFF_AT) }, modifier = Modifier.weight(1f).height(48.dp)) {
                        Text(if (started) ui("Set as mastered", "覚えた") else ui("Mark mastered", "覚えた"))
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)) {
                    TextButton(onClick = { vm.setSuspended(!detail.suspended) }) {
                        Text(if (detail.suspended) ui("Unsuspend", "保留を解除する") else ui("Suspend", "保留する"), color = MaterialTheme.colorScheme.primary)
                    }
                    if (detail.cards.isNotEmpty() || detail.mastery > 0) {
                        TextButton(onClick = { confirmReset = true }) {
                            Text(ui("Reset progress", "記録を消す"), color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
                Text(
                    "Suspended words never come up for review or as new words. Reset forgets the word's progress and " +
                        "history, and it comes back as a new word in its usual place.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (detail.history.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionLabel(ui("History", "履歴"))
                    val format = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT)
                    for ((direction, entry) in detail.history.take(50)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
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
                            AnswerMark(entry.correct, size = 22.dp)
                        }
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
                }) { Text(ui("Reset", "消す"), color = MaterialTheme.colorScheme.primary) }
            },
            dismissButton = {
                TextButton(onClick = { confirmReset = false }) { Text(ui("Cancel", "やめる"), color = MaterialTheme.colorScheme.onSurface) }
            },
        )
    }
}

/** A right answer as a red circle, a wrong one as an ink cross. */
@Composable
private fun AnswerMark(correct: Boolean, size: Dp = 26.dp) {
    val color = if (correct) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
    Box(Modifier.size(size).border(2.dp, color, CircleShape), contentAlignment = Alignment.Center) {
        Text(
            if (correct) "○" else "×",
            fontFamily = Gothic,
            fontWeight = FontWeight.Bold,
            fontSize = (size.value * 0.5f).sp,
            lineHeight = (size.value * 0.55f).sp,
            color = color,
        )
    }
}

@Composable
private fun CardLine(label: String, card: Card, clock: StudyClock) {
    val due = when (card.state) {
        CardState.NEW -> "not started"
        CardState.LEARNING, CardState.RELEARNING -> "learning"
        CardState.REVIEW -> dueText(card.dueAt, clock)
    }
    Row(Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(due, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
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
