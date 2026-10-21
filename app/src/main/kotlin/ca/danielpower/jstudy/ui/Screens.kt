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
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
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

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun App(vm: AppViewModel, screen: Screen) {
    // Kept here so the list keeps its place while a word is open.
    val dictionaryList = rememberLazyListState()
    val theme by vm.theme.collectAsStateWithLifecycle()
    val tab = when (screen) {
        is Screen.Home -> Tab.HOME
        is Screen.Dictionary -> Tab.DICTIONARY
        is Screen.Stories, is Screen.Series -> Tab.STORIES
        is Screen.ReviewTab -> Tab.REVIEW
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
                    is Screen.Home -> HomeScreen(screen.dashboard, vm)
                    is Screen.ReviewTab -> ReviewTabScreen(screen.summary, vm)
                    is Screen.Review -> ReviewScreen(screen, vm)
                    is Screen.Waiting -> WaitingScreen(screen, vm)
                    is Screen.Done -> DoneScreen(screen, vm)
                    is Screen.Settings -> SettingsScreen(
                        screen,
                        theme,
                        vm::saveSettings,
                        vm::openImport,
                        vm::setTheme,
                        vm,
                    )
                    is Screen.Setup -> SetupScreen(screen, vm)
                    is Screen.Kana -> KanaScreen(screen, vm)
                    is Screen.StatsPage -> StatsScreen(screen, vm)
                    is Screen.Dictionary -> DictionaryScreen(screen, vm, dictionaryList)
                    is Screen.WordPage -> WordScreen(screen, vm)
                    is Screen.KanjiPage -> KanjiScreen(screen, vm)
                    is Screen.RenshuuImport -> RenshuuImportScreen(screen.step, vm)
                    is Screen.Stories -> StoriesScreen(screen, vm)
                    is Screen.Series -> SeriesScreen(screen, vm)
                    is Screen.StoryReader -> StoryReaderScreen(screen, vm)
                }
            }
            if (navTab != null) {
                val due by vm.due.collectAsStateWithLifecycle()
                WashiNavBar(navTab, badges = mapOf(Tab.REVIEW to due)) {
                    when (it) {
                        Tab.HOME -> vm.goHome()
                        Tab.STORIES -> vm.openStories()
                        Tab.REVIEW -> vm.openReviewTab()
                        Tab.DICTIONARY -> vm.openDictionary()
                    }
                }
            }
        }
    }
    val limit by vm.limit.collectAsStateWithLifecycle()
    limit?.let { prompt ->
        ModalBottomSheet(onDismissRequest = vm::closeLimit, containerColor = MaterialTheme.colorScheme.surface) {
            LimitSheet(prompt, vm)
        }
    }
}

// ------------------------------------------------------------------ review tab

/** Today's cards, study days, and the way into a review session. */
@Composable
private fun ReviewTabScreen(summary: Summary, vm: AppViewModel) {
    val due = summary.newCards + summary.learning + summary.reviews
    val kanaDue = summary.kana?.due ?: 0
    val answered = summary.reviewedToday.takeIf { it > 0 }?.let { "$it answered so far, ${100 * summary.correctToday / it}% right" }
    // Before any word's been started there's nothing to review yet, rather than nothing left.
    val beforeFirst = due == 0 && !summary.started
    // The button stays pinned at the foot and the rest scrolls, so a small screen never squeezes it.
    Column(Modifier.fillMaxSize().padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 20.dp)) {
        Column(
            modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            Text("Review", style = MaterialTheme.typography.headlineMedium)

            // While any kana isn't clear, and on the day the last one comes clear.
            if (summary.kana != null || summary.kanaClearToday) KanaSection(summary, vm)

            if (beforeFirst) {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Reviews start after your first story",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        "The words you meet in it come back here, just before you'd forget them.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            } else if (due == 0 && kanaDue > 0) {
                Text("No cards due", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else if (due == 0) {
                // Nothing due: the same 済 as Home's Done card.
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    DoneSeal()
                    Text("Done for today", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    answered?.let { Text(it, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    SectionLabel("Today")
                    Text(
                        if (due == 1) "1 card" else "$due cards",
                        fontFamily = Gothic,
                        fontWeight = FontWeight.Bold,
                        fontSize = 44.sp,
                        lineHeight = 48.sp,
                    )
                    answered?.let { Text(it, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }

                Column {
                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface)
                    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                        Count("新 New", summary.newCards, MaterialTheme.colorScheme.primary, Modifier.weight(1f))
                        VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Count("習 Learning", summary.learning, MaterialTheme.colorScheme.tertiary, Modifier.weight(1f).padding(start = 16.dp))
                        VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Count("復 Review", summary.reviews, MaterialTheme.washi.verdigris, Modifier.weight(1f).padding(start = 16.dp))
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SectionLabel("Study days", Modifier.weight(1f))
                    Text(
                        "Stats ›",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable(onClick = vm::openStats).padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
                StudyHeatmap(summary.answersPerDay, summary.readDays, summary.today)
            }

            ComingUp(summary.comingUp, summary.today)
        }

        Spacer(Modifier.height(22.dp))
        if (kanaDue > 0) {
            // Kana first, then the cards, so a beginner always has the one thing to press.
            PrimaryButton(onClick = vm::startStudy, modifier = Modifier.fillMaxWidth().height(58.dp)) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Start studying", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    if (due > 0) Text("Kana first, then ${if (due == 1) "1 card" else "$due cards"}", fontSize = 12.sp)
                }
            }
        } else if (beforeFirst) {
            PrimaryButton(onClick = vm::openStories, modifier = Modifier.fillMaxWidth().height(58.dp)) {
                Text("Read your first story", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
        } else if (due > 0) {
            PrimaryButton(onClick = vm::startReview, modifier = Modifier.fillMaxWidth().height(58.dp)) {
                Text("Start studying", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SecondaryButton(onClick = vm::studyMore, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                    Text("Study more", fontSize = 16.sp)
                }
                Text(
                    buildString {
                        append("Brings tomorrow's reviews forward.")
                        if (summary.daysAhead == 1) append(" You're 1 day ahead.")
                        if (summary.daysAhead > 1) append(" You're ${summary.daysAhead} days ahead.")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/** Kana practice while you learn them: what's due, and how many are clear. Start studying runs it. */
@Composable
private fun KanaSection(summary: Summary, vm: AppViewModel) {
    val kana = summary.kana
    val colors = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionLabel("Kana", Modifier.weight(1f))
            Text(
                "Chart ›",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = colors.primary,
                modifier = Modifier.clickable { vm.openDictionary(kana = true) }.padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
        if (kana == null) {
            // The day the last kana came clear.
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Box(Modifier.size(44.dp).stampGrain().border(2.dp, colors.primary, RoundedCornerShape(4.dp)), contentAlignment = Alignment.Center) {
                    Text("済", fontFamily = Gothic, fontWeight = FontWeight.Bold, fontSize = 26.sp, color = colors.primary)
                }
                Text("All ${summary.kanaLearned} kana clear", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            return@Column
        }
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                listOfNotNull(
                    kana.new.takeIf { it > 0 }?.let { "$it new" },
                    kana.check.takeIf { it > 0 }?.let { "$it to check" },
                ).joinToString(", ").ifEmpty { if (kana.later > 0) "A quick check later today" else "Nothing to practice today" },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            Text("${kana.clear} of ${kana.total} clear", style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
        }
        // Clear in ink, learning in gold, the rest not started.
        Row(Modifier.fillMaxWidth().height(4.dp).background(colors.surfaceVariant)) {
            val total = kana.total.coerceAtLeast(1).toFloat()
            if (kana.clear > 0) Box(Modifier.weight(kana.clear / total).fillMaxHeight().background(colors.onSurface))
            if (kana.learning > 0) Box(Modifier.weight(kana.learning / total).fillMaxHeight().background(colors.tertiary))
            val rest = kana.total - kana.clear - kana.learning
            if (rest > 0) Spacer(Modifier.weight(rest / total))
        }
    }
}

/** A large 済 ("done") seal, grained like the reader's 読了. */
@Composable
private fun DoneSeal() {
    val seal = MaterialTheme.colorScheme.primary
    Box(
        Modifier.size(88.dp).stampGrain().border(3.dp, seal, RoundedCornerShape(6.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Text("済", fontFamily = Gothic, fontWeight = FontWeight.Bold, fontSize = 52.sp, color = seal)
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
    val production = state.current.card.direction == Direction.PRODUCTION
    val revealed = state.revealed
    val noRipple = remember { MutableInteractionSource() }
    // The answer side says the word. A short one has room for the speaker beside it.
    val speakerBeside = revealed && word.display.length <= 3
    val say = { vm.sayWord(word.kana) }

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            WashiIconButton(WashiIcons.Close, "Close", vm::closeReview)
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(18.dp, Alignment.CenterHorizontally)) {
                QueueCount("新", state.counts.new, MaterialTheme.colorScheme.primary)
                QueueCount("習", state.counts.learning, MaterialTheme.colorScheme.tertiary)
                QueueCount("復", state.counts.review, MaterialTheme.washi.verdigris)
            }
            WashiIconButton(WashiIcons.Undo, "Undo", vm::undoLast, enabled = state.canUndo)
        }

        val cardShape = MaterialTheme.shapes.large
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp)
                .clip(cardShape)
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, cardShape)
                .clickable(interactionSource = noRipple, indication = null) {
                    if (!revealed) vm.reveal()
                },
        ) {
            CardTopper(
                stageMark(state.current.card.state) + " " + if (production) {
                    "English → Japanese"
                } else {
                    "Japanese → English"
                },
                production,
            )
            Column(Modifier.verticalScroll(rememberScrollState()).padding(24.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (revealed && !speakerBeside) SayButton(onClick = say)
                    Spacer(Modifier.weight(1f))
                    LevelSeal(levelLabel(word.jlpt), tilted = true)
                }

                if (production) {
                    Box(Modifier.fillMaxWidth().heightIn(min = 160.dp), contentAlignment = Alignment.Center) {
                        Meanings(word.senses, limit = 3, large = true)
                    }
                    if (state.revealed) {
                        HorizontalDivider(Modifier.padding(vertical = 20.dp), color = MaterialTheme.colorScheme.onSurface)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.Bottom) {
                            WordFace(word, showFurigana = true)
                            if (speakerBeside) SayButton(Modifier.padding(start = 8.dp, bottom = 14.dp), onClick = say)
                        }
                        CardRomaji(state.current, revealed = true)
                    }
                } else {
                    Column(
                        Modifier.fillMaxWidth().heightIn(min = 200.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Row(verticalAlignment = Alignment.Bottom) {
                            Box(
                                modifier = Modifier.clickable(
                                    interactionSource = noRipple,
                                    indication = null,
                                    enabled = word.showsKanji && state.current.furigana != FuriganaMode.OFF,
                                ) { vm.toggleFurigana() },
                            ) {
                                WordFace(word, showFurigana = revealed || state.furiganaVisible)
                            }
                            if (speakerBeside) SayButton(Modifier.padding(start = 8.dp, bottom = 14.dp), onClick = say)
                        }
                        CardRomaji(state.current, revealed)
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
                        if (state.current.kanji.isNotEmpty()) {
                            Spacer(Modifier.height(20.dp))
                            KanjiLinks(state.current.kanji, vm::openKanji, compact = true)
                        }
                    }
                }

                if (state.revealed) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text("Mastery", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        MasteryBar(state.current.mastery, Modifier.weight(1f), masteryColor(state.current.mastery))
                        Text("${state.current.mastery}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                    }
                    // The word's page; Back comes back to this card.
                    TextButton(onClick = { vm.openWord(word.id) }, modifier = Modifier.align(Alignment.End)) {
                        Text("Full entry →", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.secondary)
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 20.dp, top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (state.revealed) {
                GradeButton(right = false, onClick = { vm.answer(false) }, modifier = Modifier.weight(1f).height(72.dp)) {
                    GradeLabel("×", "Wrong")
                }
                GradeButton(right = true, onClick = { vm.answer(true) }, modifier = Modifier.weight(1f).height(72.dp)) {
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

/** The word's reading in romaji, for kana you're still learning: on the answer, and on the front for kana not practised yet. */
@Composable
private fun CardRomaji(card: ReviewCard, revealed: Boolean) {
    val romaji = card.romaji ?: return
    if (!revealed && !card.romajiOnFront) return
    Text(
        romaji,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
    )
}

@Composable
private fun GradeLabel(mark: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(mark, fontFamily = Gothic, fontWeight = FontWeight.Bold, fontSize = 26.sp, lineHeight = 28.sp)
        Text(label, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}

/** The band across the top of a review card: indigo when you answer in English, vermilion when you answer in Japanese. */
@Composable
private fun CardTopper(label: String, production: Boolean) {
    val colors = MaterialTheme.colorScheme
    Text(
        label,
        style = MaterialTheme.typography.labelLarge,
        color = if (production) colors.onPrimary else colors.onSecondary,
        modifier = Modifier
            .fillMaxWidth()
            .background(if (production) colors.primary else colors.secondary)
            .padding(horizontal = 24.dp, vertical = 8.dp),
    )
}

/** The 新, 習 or 復 a card shares with the queue counts. */
private fun stageMark(state: CardState): String = when (state) {
    CardState.NEW -> "新"
    CardState.LEARNING, CardState.RELEARNING -> "習"
    CardState.REVIEW -> "復"
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
                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
        Text("Just a moment", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        Text("The next card is due in $minutes min", style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        Spacer(Modifier.height(24.dp))
        SecondaryButton(onClick = vm::goHome) { Text("Home") }
    }
}

/**
 * No cards left. With the rest of the day's plan done too, it's done for
 * today, with the 済; otherwise the reviews are, and the main button goes
 * on to the plan's next step.
 */
@Composable
private fun DoneScreen(state: Screen.Done, vm: AppViewModel) {
    val summary = state.summary
    val dayDone = state.plan.done
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (dayDone) DoneSeal()
        Text(if (dayDone) "Done for today" else "Reviews done", style = MaterialTheme.typography.headlineMedium)
        if (summary.reviewedToday > 0) {
            val accuracy = 100 * summary.correctToday / summary.reviewedToday
            Text(
                "${summary.reviewedToday} answers today, $accuracy% right",
                style = MaterialTheme.typography.bodyLarge,
            )
        }
        Text(
            "Study more brings tomorrow's reviews forward, with any English → Japanese cards waiting.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        PrimaryButton(onClick = { if (dayDone) vm.goHome() else vm.continueToday() }, modifier = Modifier.fillMaxWidth().height(56.dp)) {
            Text(if (dayDone) "Home" else nextStepLabel(state.plan), fontSize = 18.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        SecondaryButton(onClick = vm::studyMore, modifier = Modifier.fillMaxWidth().height(56.dp)) { Text("Study more", fontSize = 18.sp) }
        if (!dayDone) TextButton(onClick = vm::goHome) { Text("Home") }
    }
}

/** Reviews due on each of the next seven days, as bars under their weekdays. */
@Composable
private fun ComingUp(days: List<Int>, today: Long) {
    val verdigris = MaterialTheme.washi.verdigris
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val max = days.maxOrNull()?.coerceAtLeast(1) ?: 1
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionLabel("Coming up", Modifier.weight(1f))
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(color = verdigris, fontWeight = FontWeight.Bold)) { append("${days.firstOrNull() ?: 0}") }
                    append(" tomorrow · ${days.sum()} in 7 days")
                },
                style = MaterialTheme.typography.labelMedium,
                color = muted,
            )
        }
        Row(Modifier.fillMaxWidth().height(36.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            for (n in days) {
                Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.BottomCenter) {
                    // A hairline for an empty day, so it still shows where it is.
                    val bar = if (n == 0) Modifier.height(2.dp) else Modifier.fillMaxHeight(n / max.toFloat())
                    Box(bar.fillMaxWidth().background(if (n == 0) MaterialTheme.colorScheme.outlineVariant else verdigris, RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp)))
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            days.indices.forEach { i ->
                Text(
                    LocalDate.ofEpochDay(today + 1 + i).dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, Locale.ENGLISH),
                    style = MaterialTheme.typography.labelSmall,
                    color = muted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}
