package ca.danielpower.jstudy.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ca.danielpower.jstudy.core.CardState
import ca.danielpower.jstudy.core.Direction
import ca.danielpower.jstudy.core.FuriganaMode
import ca.danielpower.jstudy.core.KanaUnit
import ca.danielpower.jstudy.core.MasteryRules
import ca.danielpower.jstudy.core.Script
import ca.danielpower.jstudy.data.Sense
import ca.danielpower.jstudy.data.Summary
import ca.danielpower.jstudy.data.Word
import ca.danielpower.jstudy.data.levelLabel
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.delay

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun App(vm: AppViewModel, screen: Screen) {
    // Kept here so the list keeps its place while a word is open.
    val dictionaryList = rememberLazyListState()
    val theme by vm.theme.collectAsStateWithLifecycle()
    val tab = when (screen) {
        is Screen.Home -> Tab.HOME
        is Screen.Dictionary -> Tab.DICTIONARY
        is Screen.Stories -> Tab.STORIES
        is Screen.Settings -> Tab.SETTINGS
        else -> null
    }
    // The bar steps aside while typing, so the keyboard doesn't squeeze the screen.
    val navTab = tab?.takeIf { !WindowInsets.isImeVisible }
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize()) {
            val insets = if (navTab != null) {
                WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)
            } else {
                WindowInsets.safeDrawing
            }
            Box(Modifier.weight(1f).fillMaxWidth().windowInsetsPadding(insets)) {
                when (screen) {
                    Screen.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    }
                    is Screen.Home -> HomeScreen(screen.summary, vm)
                    is Screen.Review -> ReviewScreen(screen, vm)
                    is Screen.Waiting -> WaitingScreen(screen, vm)
                    is Screen.Done -> DoneScreen(screen.summary, vm::studyMore, vm::goHome)
                    is Screen.Settings -> SettingsScreen(
                        screen,
                        theme,
                        vm::saveSettings,
                        vm::saveClaudeKey,
                        vm::openImport,
                        vm::setStoryVertical,
                        vm::setTheme,
                        vm,
                    )
                    is Screen.Setup -> SetupScreen(screen, vm)
                    is Screen.StatsPage -> StatsScreen(screen, vm)
                    is Screen.Dictionary -> DictionaryScreen(screen, vm, dictionaryList)
                    is Screen.WordPage -> WordScreen(screen, vm)
                    is Screen.KanjiPage -> KanjiScreen(screen, vm)
                    is Screen.RenshuuImport -> RenshuuImportScreen(screen.step, vm)
                    is Screen.Stories -> StoriesScreen(screen, vm)
                    is Screen.StoryReader -> StoryReaderScreen(screen, vm)
                }
            }
            if (navTab != null) {
                WashiNavBar(navTab) {
                    when (it) {
                        Tab.HOME -> vm.goHome()
                        Tab.DICTIONARY -> vm.openDictionary()
                        Tab.STORIES -> vm.openStories()
                        Tab.SETTINGS -> vm.openSettings()
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------------ home

@Composable
private fun HomeScreen(summary: Summary, vm: AppViewModel) {
    val due = summary.newCards + summary.learning + summary.reviews
    Column(
        modifier = Modifier.fillMaxSize().padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                Modifier.size(36.dp).background(MaterialTheme.colorScheme.primary, WashiShape),
                contentAlignment = Alignment.Center,
            ) {
                Text("学", fontFamily = Gothic, fontWeight = FontWeight.Bold, fontSize = 22.sp, color = MaterialTheme.colorScheme.onPrimary)
            }
            Text("jstudy", fontFamily = Gothic, fontWeight = FontWeight.Bold, fontSize = 22.sp, modifier = Modifier.weight(1f))
            val day = LocalDate.ofEpochDay(summary.today)
            Text(
                day.dayOfWeek.getDisplayName(java.time.format.TextStyle.FULL, Locale.JAPANESE) + " · " +
                    day.format(DateTimeFormatter.ofPattern("MMM d")),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            SectionLabel(ui("Today", "今日"))
            Text(
                when (due) {
                    0 -> ui("All done", "終わり")
                    1 -> "1 card"
                    else -> "$due cards"
                },
                fontFamily = Gothic,
                fontWeight = FontWeight.Bold,
                fontSize = 44.sp,
                lineHeight = 48.sp,
            )
            if (summary.reviewedToday > 0) {
                val accuracy = 100 * summary.correctToday / summary.reviewedToday
                Text(
                    "${summary.reviewedToday} answered so far, $accuracy% right",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Column {
            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface)
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                Count(ui("新 New", "新しい"), summary.newCards, MaterialTheme.colorScheme.primary, Modifier.weight(1f))
                VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Count(ui("習 Learning", "学習中"), summary.learning, MaterialTheme.colorScheme.tertiary, Modifier.weight(1f).padding(start = 16.dp))
                VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Count(ui("復 Review", "復習"), summary.reviews, MaterialTheme.colorScheme.secondary, Modifier.weight(1f).padding(start = 16.dp))
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SectionLabel(ui("Study days", "勉強した日"), Modifier.weight(1f))
                Text(
                    ui("Stats", "統計"),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable(onClick = vm::openStats).padding(horizontal = 8.dp, vertical = 4.dp),
                )
                Text(
                    if (summary.streak == 1) "1 day streak" else "${summary.streak} day streak",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
            StudyHeatmap(summary.answersPerDay, summary.today)
            Text(
                buildString {
                    append("${summary.wordsStarted} of ${summary.wordsTotal} words started")
                    if (summary.daysAhead > 0) {
                        append(if (summary.daysAhead == 1) "  ·  1 day ahead" else "  ·  ${summary.daysAhead} days ahead")
                    }
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (summary.kanaLearned < summary.kanaTotal) {
                Text(
                    "${summary.kanaLearned} of ${summary.kanaTotal} kana learned. New words join once you can read them.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(Modifier.weight(1f))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (summary.anythingDue) {
                PrimaryButton(onClick = vm::startReview, modifier = Modifier.fillMaxWidth().height(58.dp)) {
                    Text(ui("Start studying", "勉強を始める"), fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
            } else {
                PrimaryButton(onClick = vm::studyMore, modifier = Modifier.fillMaxWidth().height(58.dp)) {
                    Text(ui("Study more", "もっと勉強する"), fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
            }
            SecondaryButton(onClick = vm::openStories, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Text(ui("Read a story", "物語を読む"), fontSize = 16.sp)
            }
        }
    }
}

@Composable
private fun Count(label: String, value: Int, color: Color, modifier: Modifier) {
    Column(modifier.padding(vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text("$value", fontFamily = Gothic, fontWeight = FontWeight.Bold, fontSize = 30.sp, color = color)
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// ------------------------------------------------------------------ review

@Composable
private fun ReviewScreen(state: Screen.Review, vm: AppViewModel) {
    val word = state.current.word
    val kana = state.current.kana
    val production = state.current.card.direction == Direction.PRODUCTION
    // First sight of a new word: show everything, with Continue to start learning it.
    val intro = !production && state.current.card.state == CardState.NEW
    val revealed = state.revealed || intro
    val noRipple = remember { MutableInteractionSource() }

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            WashiIconButton(WashiIcons.Close, "Close", vm::goHome)
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(18.dp, Alignment.CenterHorizontally)) {
                QueueCount("新", state.counts.new, MaterialTheme.colorScheme.primary)
                QueueCount("習", state.counts.learning, MaterialTheme.colorScheme.tertiary)
                QueueCount("復", state.counts.review, MaterialTheme.colorScheme.secondary)
            }
            WashiIconButton(WashiIcons.Undo, "Undo", vm::undoLast, enabled = state.canUndo)
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp)
                .background(MaterialTheme.colorScheme.surfaceContainerLow, WashiShape)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, WashiShape)
                .clickable(interactionSource = noRipple, indication = null) {
                    if (!revealed) vm.reveal()
                }
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (intro) {
                    Text(
                        if (kana != null) ui("新 New kana", "新しいかな", "新しい", "仮名") else ui("新 New word", "新しい単語"),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f),
                    )
                } else {
                    Spacer(Modifier.weight(1f))
                }
                if (word != null) LevelSeal(levelLabel(word.jlpt), tilted = true)
                if (kana != null) LevelSeal(if (kana.script == Script.HIRAGANA) "ひら" else "カナ", tilted = true)
            }

            if (kana != null) {
                KanaFace(kana, revealed)
            } else if (word != null && production) {
                Box(Modifier.fillMaxWidth().heightIn(min = 160.dp), contentAlignment = Alignment.Center) {
                    Meanings(word.senses, limit = 3, large = true)
                }
                if (state.revealed) {
                    HorizontalDivider(Modifier.padding(vertical = 20.dp), color = MaterialTheme.colorScheme.onSurface)
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        WordFace(word, showFurigana = true)
                    }
                }
            } else if (word != null) {
                Column(
                    Modifier.fillMaxWidth().heightIn(min = 200.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
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
                            ui("Tap the word for its reading", "押して読み方を見る", "押す", "読む", "見る"),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                }
                if (revealed) {
                    HorizontalDivider(Modifier.padding(vertical = 20.dp), color = MaterialTheme.colorScheme.onSurface)
                    Meanings(word.senses, limit = 3, large = false)
                    if (state.current.kanji.isNotEmpty()) {
                        Spacer(Modifier.height(20.dp))
                        KanjiLinks(state.current.kanji, vm::openKanji, compact = true)
                    }
                }
            }

            if (!intro && state.revealed) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(ui("Mastery", "習熟度"), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    MasteryBar(state.current.mastery, Modifier.weight(1f))
                    Text("${state.current.mastery}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                }
            }
        }

        if (intro) {
            IntroActions(state.current.card.id, MasteryRules.HIDE_AT, MasteryRules.OFF_AT, vm)
            return@Column
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 20.dp, top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (state.revealed) {
                GradeButton(right = false, onClick = { vm.answer(false) }, modifier = Modifier.weight(1f).height(72.dp)) {
                    GradeLabel("×", ui("Wrong", "間違い"))
                }
                GradeButton(right = true, onClick = { vm.answer(true) }, modifier = Modifier.weight(1f).height(72.dp)) {
                    GradeLabel("○", ui("Right", "正解"))
                }
            } else {
                SecondaryButton(onClick = vm::reveal, modifier = Modifier.fillMaxWidth().height(72.dp)) {
                    Text(ui("Show answer", "答えを見る"), fontSize = 18.sp)
                }
            }
        }
    }
}

/**
 * The first sight of a new word or kana. Most of the time it's new to the
 * learner, so "Continue" is the main action. "I know this" is a quiet link
 * that opens the two ways to skip it as already known.
 */
@Composable
private fun IntroActions(cardId: Long, knownAt: Int, masteredAt: Int, vm: AppViewModel) {
    var knows by remember(cardId) { mutableStateOf(false) }
    Column(
        modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 20.dp, top = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (knows) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SecondaryButton(onClick = { vm.introduce(knownAt) }, modifier = Modifier.weight(1f).height(48.dp)) {
                    Text(ui("I know it well", "よく知っている"))
                }
                SecondaryButton(onClick = { vm.introduce(masteredAt) }, modifier = Modifier.weight(1f).height(48.dp)) {
                    Text(ui("Mastered", "覚えた"))
                }
            }
            TextButton(onClick = { knows = false }) {
                Text(ui("Never mind", "やめる"), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            TextButton(onClick = { knows = true }) {
                Text(ui("I know this", "知っている"), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        PrimaryButton(onClick = { vm.introduce(0) }, modifier = Modifier.fillMaxWidth().height(72.dp)) {
            Text(ui("Continue", "続ける"), fontSize = 18.sp)
        }
    }
}

/** A kana card: the kana, then its sound and its twin in the other script. */
@Composable
private fun KanaFace(kana: KanaUnit, revealed: Boolean) {
    Box(Modifier.fillMaxWidth().heightIn(min = 200.dp), contentAlignment = Alignment.Center) {
        Text(kana.text, fontFamily = Gothic, fontWeight = FontWeight.Bold, fontSize = 96.sp, lineHeight = 112.sp)
    }
    if (revealed) {
        HorizontalDivider(Modifier.padding(vertical = 20.dp), color = MaterialTheme.colorScheme.onSurface)
        Column(
            Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(kana.romaji, fontSize = 34.sp, fontWeight = FontWeight.Bold)
            val twin = kana.twin
            if (twin != null) {
                Text(
                    (if (kana.script == Script.HIRAGANA) ui("Katakana", "カタカナ") else ui("Hiragana", "ひらがな", "ひらがな")) + " " + twin,
                    fontFamily = Gothic,
                    fontSize = 22.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            val note = kana.note
            if (note != null) {
                Text(
                    note,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun GradeLabel(mark: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(mark, fontFamily = Gothic, fontWeight = FontWeight.Bold, fontSize = 26.sp, lineHeight = 28.sp)
        Text(label, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun QueueCount(kanji: String, value: Int, color: Color) {
    Text("$kanji $value", color = color, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
}

@Composable
internal fun WordFace(word: Word, showFurigana: Boolean) {
    val length = word.display.length
    val size = when {
        length <= 4 -> 60.sp
        length <= 6 -> 48.sp
        length <= 8 -> 38.sp
        else -> 30.sp
    }
    val segments = word.furigana
    if (word.showsKanji && segments != null) {
        FuriganaText(segments, showFurigana, fontSize = size)
    } else {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            // Keep the same height as a word with a reading row.
            val ruby = size * RUBY_SCALE
            Text(" ", fontSize = ruby, lineHeight = ruby * 1.15f)
            Text(word.kana, fontSize = size, lineHeight = size * 1.2f, fontFamily = Gothic, fontWeight = FontWeight.Bold)
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

private val NUMERALS = listOf("一", "二", "三", "四", "五", "六", "七", "八", "九", "十")

/** Kanji numerals for numbered meanings. */
internal fun numeral(i: Int) = NUMERALS.getOrElse(i) { "${i + 1}" }

/** Meanings, numbered in red when there are several. */
@Composable
internal fun Meanings(senses: List<Sense>, limit: Int, large: Boolean) {
    val shown = senses.take(limit)
    if (large) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            for (sense in shown) {
                Text(
                    sense.glosses.joinToString("; "),
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center,
                )
            }
        }
    } else {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            shown.forEachIndexed { i, sense ->
                MeaningLine(if (shown.size > 1) numeral(i) else null, sense.glosses.joinToString("; "))
            }
        }
    }
}

@Composable
internal fun MeaningLine(numeral: String?, text: String, info: String? = null, compact: Boolean = false) {
    // Compact for the story reader's pane, where space is short.
    val size = if (compact) 16.sp else 19.sp
    Row {
        if (numeral != null) {
            Text(
                numeral,
                fontFamily = Gothic,
                fontWeight = FontWeight.Bold,
                fontSize = size,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.width(if (compact) 24.dp else 30.dp),
            )
        }
        Column(Modifier.weight(1f)) {
            Text(text, fontSize = size, lineHeight = if (compact) 21.sp else 26.sp)
            if (info != null) {
                Text(info, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
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
        Text(ui("Just a moment", "少々お待ちください", "少々", "待つ"), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        Text("The next card is due in $minutes min", style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        Spacer(Modifier.height(24.dp))
        SecondaryButton(onClick = vm::goHome) { Text(ui("Home", "ホーム")) }
    }
}

@Composable
private fun DoneScreen(summary: Summary, onStudyMore: () -> Unit, onHome: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(88.dp).border(3.dp, MaterialTheme.colorScheme.primary, WashiShape),
            contentAlignment = Alignment.Center,
        ) {
            Text("済", fontFamily = Gothic, fontWeight = FontWeight.Bold, fontSize = 52.sp, color = MaterialTheme.colorScheme.primary)
        }
        Text(ui("Done for today", "今日はここまで"), style = MaterialTheme.typography.headlineMedium)
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
        PrimaryButton(onClick = onStudyMore, modifier = Modifier.fillMaxWidth().height(56.dp)) { Text(ui("Study more", "もっと勉強する"), fontSize = 18.sp) }
        SecondaryButton(onClick = onHome, modifier = Modifier.fillMaxWidth().height(56.dp)) { Text(ui("Home", "ホーム"), fontSize = 18.sp) }
    }
}
