package ca.danielpower.jstudy.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.material3.LocalContentColor
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
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import ca.danielpower.jstudy.core.Card
import ca.danielpower.jstudy.core.CardState
import ca.danielpower.jstudy.core.Direction
import ca.danielpower.jstudy.core.Kanjis
import ca.danielpower.jstudy.core.StudyClock
import ca.danielpower.jstudy.data.GrammarRow
import ca.danielpower.jstudy.data.StoryUse
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
            "Dictionary",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 20.dp, bottom = 4.dp),
        )
        Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            WashiChip(selected = state.grammar == null && state.kana == null, onClick = { vm.showGrammar(false) }, label = "Words")
            WashiChip(selected = state.grammar != null, onClick = { vm.showGrammar(true) }, label = "Grammar")
            WashiChip(selected = state.kana != null, onClick = { vm.showKana(true) }, label = "Kana")
        }
        val grammar = state.grammar
        val kana = state.kana
        if (kana != null) {
            KanaChartView(
                kana,
                vm::sayKana,
                modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp),
            )
        } else if (grammar != null) {
            GrammarList(grammar, vm)
        } else {
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
                        label = filter.label,
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
                    Text("Kanji", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
}

/**
 * Every grammar topic: the ones you've met, newest first, each with where
 * it's taught, then the rest greyed. A topic you've met opens its page.
 */
@Composable
private fun ColumnScope.GrammarList(rows: List<GrammarRow>, vm: AppViewModel) {
    val met = rows.count { it.seen }
    LazyColumn(Modifier.weight(1f)) {
        item {
            SectionLabel("Met · $met of ${rows.size}", Modifier.padding(start = 24.dp, end = 24.dp, top = 12.dp, bottom = 4.dp))
        }
        items(rows, key = { it.topic.id }) { row ->
            if (!row.seen && rows.indexOf(row) == met) {
                SectionLabel("Not yet", Modifier.padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 4.dp))
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable(enabled = row.seen) { vm.openGrammar(row.pageId) }
                    .padding(horizontal = 24.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    row.topic.title,
                    fontFamily = Gothic,
                    fontWeight = if (row.seen) FontWeight.Bold else FontWeight.Normal,
                    fontSize = 18.sp,
                    color = if (row.seen) LocalContentColor.current else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                val where = row.taughtIn?.let { c -> if (c.series.single) c.series.title else "${c.series.title} ${c.chapter.number}" }
                Text(
                    listOfNotNull(where.takeIf { row.seen }, inSentence(row.topic.titleEn)).joinToString(" · "),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.End,
                )
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(horizontal = 24.dp))
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
                status.suspended -> Text("Suspended", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
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
                    Box(Modifier.weight(1f, fill = false)) { WordFace(word, showFurigana = true) }
                    SayButton(Modifier.padding(start = 8.dp, bottom = 14.dp)) { vm.sayWord(word.kana) }
                    Spacer(Modifier.weight(1f))
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
                SectionLabel("Meanings")
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
                    SectionLabel("Kanji")
                    KanjiLinks(detail.kanji, vm::openKanji)
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SectionLabel("Progress")
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
                            detail.suspended -> Text("Suspended", style = MaterialTheme.typography.bodyLarge)
                            recognition == null || recognition.state == CardState.NEW ->
                                Text("Not started", style = MaterialTheme.typography.bodyLarge)
                            else -> {
                                CardLine("Japanese → English", recognition, state.clock)
                                production?.let { CardLine("English → Japanese", it, state.clock) }
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

            if (detail.stories.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    SectionLabel("In your stories")
                    for (use in detail.stories) StoryUseRow(use, onOpen = { vm.openStoryAt(use) }, onPlay = { vm.playStoryUse(use) })
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SectionLabel("Actions")
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)) {
                    TextButton(onClick = { vm.setSuspended(!detail.suspended) }) {
                        Text(if (detail.suspended) "Unsuspend" else "Suspend", color = MaterialTheme.colorScheme.primary)
                    }
                    if (detail.cards.isNotEmpty() || detail.mastery > 0) {
                        TextButton(onClick = { confirmReset = true }) {
                            Text("Reset progress", color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
                Text(
                    "Suspended words never come up for review, and count as known in stories. Reset forgets the " +
                        "word's progress and history.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (detail.history.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionLabel("History")
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
                }) { Text("Reset", color = MaterialTheme.colorScheme.primary) }
            },
            dismissButton = {
                TextButton(onClick = { confirmReset = false }) { Text("Cancel", color = MaterialTheme.colorScheme.onSurface) }
            },
        )
    }
}

/**
 * A sentence with the word from a chapter you've read: the chapter, the
 * sentence with the word marked, and its English. Tapping it opens the
 * chapter there; the speaker plays it.
 */
@Composable
private fun StoryUseRow(use: StoryUse, onOpen: () -> Unit, onPlay: () -> Unit) {
    val seal = MaterialTheme.colorScheme.primary
    val nameColor = speakerColor()
    Row(Modifier.fillMaxWidth().clickable(onClick = onOpen), verticalAlignment = Alignment.Top) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                if (use.series.single) use.series.title else "${use.series.title}, ch. ${use.chapter.number}",
                style = MaterialTheme.typography.labelMedium,
                color = seal,
            )
            Text(
                buildAnnotatedString {
                    // Script style, as in the reader: the speaker's name, smaller, before their line.
                    use.speaker?.let { name ->
                        withStyle(SpanStyle(fontSize = NAME_RATIO.em, color = nameColor)) { append(name) }
                        append(" ")
                    }
                    use.words.forEachIndexed { i, word ->
                        if (i in use.marked) {
                            withStyle(SpanStyle(color = seal, background = seal.copy(alpha = HIGHLIGHT))) { append(word.surface) }
                        } else {
                            append(word.surface)
                        }
                    }
                },
                style = MaterialTheme.typography.bodyLarge,
            )
            use.translation?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (use.line != null) SayButton(Modifier.padding(start = 8.dp), onClick = onPlay)
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
