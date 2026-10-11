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
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
                    )
                    is Screen.Dictionary -> DictionaryScreen(screen, vm, dictionaryList)
                    is Screen.WordPage -> WordScreen(screen, vm)
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
                Text("学", fontFamily = Mincho, fontWeight = FontWeight.Bold, fontSize = 22.sp, color = MaterialTheme.colorScheme.onPrimary)
            }
            Text("jstudy", fontFamily = Mincho, fontWeight = FontWeight.Bold, fontSize = 22.sp, modifier = Modifier.weight(1f))
            val day = LocalDate.ofEpochDay(summary.today)
            Text(
                day.dayOfWeek.getDisplayName(java.time.format.TextStyle.FULL, Locale.JAPANESE) + " · " +
                    day.format(DateTimeFormatter.ofPattern("MMM d")),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            SectionLabel("Today")
            Text(
                when (due) {
                    0 -> "All done"
                    1 -> "1 card"
                    else -> "$due cards"
                },
                fontFamily = Mincho,
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
                Count("新", "New", summary.newCards, MaterialTheme.colorScheme.primary, Modifier.weight(1f))
                VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Count("習", "Learning", summary.learning, MaterialTheme.colorScheme.tertiary, Modifier.weight(1f).padding(start = 16.dp))
                VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Count("復", "Review", summary.reviews, MaterialTheme.colorScheme.secondary, Modifier.weight(1f).padding(start = 16.dp))
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SectionLabel("Study days", Modifier.weight(1f))
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
        }

        Spacer(Modifier.weight(1f))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (summary.anythingDue) {
                PrimaryButton(onClick = vm::startReview, modifier = Modifier.fillMaxWidth().height(58.dp)) {
                    Text("Study  ·  始める", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
            } else {
                PrimaryButton(onClick = vm::studyMore, modifier = Modifier.fillMaxWidth().height(58.dp)) {
                    Text("Study more", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
            }
            SecondaryButton(onClick = vm::openStories, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Text("Read a story", fontSize = 16.sp)
            }
        }
    }
}

@Composable
private fun Count(kanji: String, label: String, value: Int, color: Color, modifier: Modifier) {
    Column(modifier.padding(vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text("$value", fontFamily = Mincho, fontWeight = FontWeight.Bold, fontSize = 30.sp, color = color)
        Text("$kanji $label", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                        "新 New word",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f),
                    )
                } else {
                    Spacer(Modifier.weight(1f))
                }
                LevelSeal(levelLabel(word.jlpt), tilted = true)
            }

            if (production) {
                Box(Modifier.fillMaxWidth().heightIn(min = 160.dp), contentAlignment = Alignment.Center) {
                    Meanings(word.senses, limit = 3, large = true)
                }
                if (state.revealed) {
                    HorizontalDivider(Modifier.padding(vertical = 20.dp), color = MaterialTheme.colorScheme.onSurface)
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        WordFace(word, showFurigana = true)
                    }
                }
            } else {
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
                            "Tap the word for its reading",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                }
                if (revealed) {
                    HorizontalDivider(Modifier.padding(vertical = 20.dp), color = MaterialTheme.colorScheme.onSurface)
                    Meanings(word.senses, limit = 3, large = false)
                }
            }

            if (intro) {
                Text(
                    "How well do you know this word?",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 24.dp),
                )
            } else if (state.revealed) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text("Mastery", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    MasteryBar(state.current.mastery, Modifier.weight(1f))
                    Text("${state.current.mastery}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 20.dp, top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (intro) {
                val rules = state.rules
                SecondaryButton(onClick = { vm.introduce(0) }, modifier = Modifier.weight(1f).height(64.dp)) {
                    Text("Unknown")
                }
                SecondaryButton(onClick = { vm.introduce(rules.hideAt) }, modifier = Modifier.weight(1f).height(64.dp)) {
                    Text("Known")
                }
                SecondaryButton(onClick = { vm.introduce(rules.offAt) }, modifier = Modifier.weight(1f).height(64.dp)) {
                    Text("Mastered")
                }
            } else if (state.revealed) {
                SecondaryButton(onClick = { vm.answer(false) }, modifier = Modifier.weight(1f).height(72.dp)) {
                    GradeLabel("×", "Wrong")
                }
                SealButton(onClick = { vm.answer(true) }, modifier = Modifier.weight(1f).height(72.dp)) {
                    GradeLabel("○", "Right")
                }
            } else {
                SecondaryButton(onClick = vm::reveal, modifier = Modifier.fillMaxWidth().height(72.dp)) {
                    Text("Show answer", fontSize = 18.sp)
                }
            }
        }
    }
}

@Composable
private fun GradeLabel(mark: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(mark, fontFamily = Mincho, fontWeight = FontWeight.Bold, fontSize = 26.sp, lineHeight = 28.sp)
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
            Text(word.kana, fontSize = size, lineHeight = size * 1.2f, fontFamily = Mincho, fontWeight = FontWeight.Bold)
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
internal fun MeaningLine(numeral: String?, text: String, info: String? = null) {
    Row {
        if (numeral != null) {
            Text(
                numeral,
                fontFamily = Mincho,
                fontWeight = FontWeight.Bold,
                fontSize = 19.sp,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.width(30.dp),
            )
        }
        Column(Modifier.weight(1f)) {
            Text(text, fontSize = 19.sp, lineHeight = 26.sp)
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
        Text("少々お待ちください", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        Text("The next card is due in $minutes min", style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        Spacer(Modifier.height(24.dp))
        SecondaryButton(onClick = vm::goHome) { Text("Home") }
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
            Text("済", fontFamily = Mincho, fontWeight = FontWeight.Bold, fontSize = 52.sp, color = MaterialTheme.colorScheme.primary)
        }
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
        PrimaryButton(onClick = onStudyMore, modifier = Modifier.fillMaxWidth().height(56.dp)) { Text("Study more", fontSize = 18.sp) }
        SecondaryButton(onClick = onHome, modifier = Modifier.fillMaxWidth().height(56.dp)) { Text("Home", fontSize = 18.sp) }
    }
}
