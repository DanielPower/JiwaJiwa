package ca.danielpower.jstudy.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ca.danielpower.jstudy.core.KanaKnown
import ca.danielpower.jstudy.core.MasteryRules
import ca.danielpower.jstudy.core.ReadLevel

/** First run, a page at a time: welcome, kana, your level, reading direction, reminder. */
@Composable
fun SetupScreen(state: Screen.Setup, vm: AppViewModel) {
    val pages = state.pages
    val index = pages.indexOf(state.page)
    val last = index == pages.lastIndex

    Column(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                "${index + 1} / ${pages.size}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            when (state.page) {
                SetupPage.WELCOME -> WelcomePage()
                SetupPage.KANA -> KanaPage(state, vm)
                SetupPage.LEVEL -> LevelPage(state, vm)
                SetupPage.DIRECTION -> DirectionPage(state, vm)
                SetupPage.REMINDER -> ReminderPage(state, vm)
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, bottom = 20.dp, top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (index > 0) {
                SecondaryButton(onClick = vm::setupBack, modifier = Modifier.weight(1f).height(58.dp)) {
                    Text("Back", fontSize = 18.sp)
                }
            }
            PrimaryButton(
                onClick = if (last) vm::finishSetup else vm::setupNext,
                modifier = Modifier.weight(1f).height(58.dp),
            ) {
                Text(if (last) "Start" else "Next", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun Title(text: String) {
    Text(text, fontFamily = Gothic, fontWeight = FontWeight.Bold, fontSize = 32.sp, lineHeight = 40.sp)
}

@Composable
private fun Body(text: String) {
    Text(text, style = MaterialTheme.typography.bodyLarge)
}

@Composable
private fun Help(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** A script or level with an example: the Japanese large on the left, what it is on the right. */
@Composable
private fun Example(japanese: String, name: String, description: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            japanese,
            fontFamily = Gothic,
            fontWeight = FontWeight.Bold,
            fontSize = 26.sp,
            modifier = Modifier.width(76.dp),
        )
        Column(Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Help(description)
        }
    }
}

@Composable
private fun ColumnScope.WelcomePage() {
    Title("Welcome")
    Body(
        "Jiwa Jiwa teaches Japanese through stories written for learners. Each one brings a few new words " +
            "and some grammar, and the words you meet come back as flashcards, just before you'd forget them.",
    )
    Body("A couple of questions first, so it starts at the right place for you.")
}

@Composable
private fun ColumnScope.KanaPage(state: Screen.Setup, vm: AppViewModel) {
    Title("Kana")
    Help(
        "Japanese is written with two sets of sound letters, hiragana and katakana, plus kanji. " +
            "Stories use all three from the start.",
    )
    Example("あい", "Hiragana", "Endings, small words, most of a sentence.")
    Example("アイ", "Katakana", "Names and words from other languages.")

    SectionLabel("Which can you already read?", Modifier.padding(top = 8.dp))
    KanaKnownChips(state.kana) { known -> vm.editSetup { it.copy(kana = known) } }
    if (state.kana != KanaKnown.BOTH) {
        Help(
            "You'll learn them five at a time, a few minutes a day. Until you've practiced a kana, stories " +
                "write its sound above it in English letters, called romaji.",
        )
        RomajiExample()
    }
}

/** Both, Hiragana only, Neither. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun KanaKnownChips(selected: KanaKnown, onSelect: (KanaKnown) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for (known in KanaKnown.entries) WashiChip(selected = selected == known, onClick = { onSelect(known) }, label = known.label)
    }
}

/** つい に with its romaji over it while new, then plain once practised. Romaji takes its word's colour, as in stories. */
@Composable
private fun RomajiExample() {
    val word = listOf("つ" to "tsu", "い" to "i", "に" to "ni")
    Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Row { for ((kana, sound) in word) RomajiGlyph(kana, sound) }
            Help("new kana")
        }
        Text("→", fontSize = 22.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 26.dp))
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Row { for ((kana, _) in word) RomajiGlyph(kana, null) }
            Help("once practiced")
        }
    }
}

@Composable
private fun RomajiGlyph(kana: String, sound: String?) {
    Column(Modifier.width(34.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(sound ?: " ", fontSize = 12.sp, lineHeight = 14.sp, fontWeight = FontWeight.Bold)
        Text(kana, fontSize = 28.sp, lineHeight = 34.sp, fontFamily = Mincho)
    }
}

@Composable
private fun ColumnScope.LevelPage(state: Screen.Setup, vm: AppViewModel) {
    Title("Your level")
    Help(
        "Words are grouped by the levels of the JLPT, the Japanese-Language Proficiency Test. " +
            "N5 is the first level and N1 the last.",
    )
    Example("N5", "Beginner", "Everyday basics, like 水 (water) and 行く (to go).")
    Example("N3", "Intermediate", "Ordinary conversation and simple reading.")

    SectionLabel("Which level can you already read?", Modifier.padding(top = 8.dp))
    ReadLevelChips(state.readLevel) { level -> vm.editSetup { it.copy(readLevel = level) } }
    if (state.readLevel == ReadLevel.NONE) {
        Help("You start from the first story, and every word is new.")
    } else {
        ReadLevelSummary(state.readLevel, state.levelCounts)
        Help("They come back for review now and then, spread out. Stories start where you are.")
    }
}

/** None, then N5 to N1. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ReadLevelChips(selected: Int, onSelect: (Int) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        WashiChip(selected = selected == ReadLevel.NONE, onClick = { onSelect(ReadLevel.NONE) }, label = "None")
        for (level in 5 downTo 1) {
            WashiChip(selected = selected == level, onClick = { onSelect(level) }, label = "N$level")
        }
    }
}

/** What each level's words start as, for someone who reads at [readLevel]: hardest first. */
@Composable
private fun ReadLevelSummary(readLevel: Int, levelCounts: Map<Int, Int>) {
    Column {
        for (level in readLevel..5) {
            val days = ReadLevel.stabilityDays(readLevel, level) ?: continue
            val mastery = MasteryRules.fromStability(days.toDouble())
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("N$level · ${levelCounts[level] ?: 0} words", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Help(if (mastery >= MasteryRules.OFF_AT) "Mastered, no furigana" else "Known, furigana on tap")
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}

@Composable
private fun ColumnScope.DirectionPage(state: Screen.Setup, vm: AppViewModel) {
    Title("Reading direction")
    Text(
        buildAnnotatedString {
            append("Japanese can be written two ways. ")
            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append("Vertical") }
            append(
                " writing, 縦書き (tategaki), runs top to bottom, with columns from right to left. Novels, manga " +
                    "and newspapers are set this way, and books open from what feels like the back.",
            )
        },
        style = MaterialTheme.typography.bodyLarge,
    )
    Text(
        buildAnnotatedString {
            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append("Horizontal") }
            append(" writing, 横書き (yokogaki), runs left to right like English. It's common on phones, websites and in textbooks.")
        },
        style = MaterialTheme.typography.bodyLarge,
    )
    Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        DirectionOption(
            selected = !state.vertical,
            onClick = { vm.editSetup { it.copy(vertical = false) } },
            label = "Horizontal",
            description = "Left to right",
            modifier = Modifier.weight(1f),
        ) { HorizontalSample() }
        DirectionOption(
            selected = state.vertical,
            onClick = { vm.editSetup { it.copy(vertical = true) } },
            label = "Vertical",
            description = "Columns right to left",
            modifier = Modifier.weight(1f),
        ) { VerticalSample() }
    }
    Help(
        "Both are ordinary Japanese, so pick whichever is easier to start with. Vertical is good practice " +
            "for real books. You can switch any time from the reader's settings.",
    )
}

/** A tappable card holding a small page set one way, with its name under it. */
@Composable
private fun DirectionOption(
    selected: Boolean,
    onClick: () -> Unit,
    label: String,
    description: String,
    modifier: Modifier = Modifier,
    sample: @Composable BoxScope.() -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val card = RoundedCornerShape(14.dp)
    val page = RoundedCornerShape(6.dp)
    Column(
        modifier = modifier
            .clip(card)
            .background(if (selected) colors.primaryContainer else colors.surfaceContainerLow)
            .border(if (selected) 2.5.dp else 1.5.dp, if (selected) colors.primary else colors.outlineVariant, card)
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
            .padding(start = 4.dp, end = 4.dp, top = 4.dp, bottom = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(220.dp)
                .clip(page)
                .background(colors.surfaceContainerLowest)
                .border(1.dp, colors.outlineVariant, page)
                .padding(10.dp),
            content = sample,
        )
        Text(label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
        Text(
            description,
            style = MaterialTheme.typography.labelMedium,
            color = colors.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

/** The sample sentence, a character (or a kanji with its reading) at a time. */
private val Sample = listOf(
    "ユ" to null, "キ" to null, "は" to null, "駅" to "えき", "で" to null, "電" to "でん", "車" to "しゃ",
    "を" to null, "待" to "ま", "っ" to null, "て" to null, "い" to null, "た" to null, "。" to null,
    "雨" to "あめ", "が" to null, "少" to "すこ", "し" to null, "降" to "ふ", "っ" to null, "て" to null,
    "い" to null, "る" to null, "。" to null,
)

private val SampleSize = 20.sp

/** Fills lines of [perLine] characters; a full stop may hang past the end rather than start a line. */
private fun sampleLines(perLine: Int): List<List<Pair<String, String?>>> {
    val lines = mutableListOf<MutableList<Pair<String, String?>>>()
    for (cell in Sample) {
        val line = lines.lastOrNull()
        if (line == null || (line.size >= perLine && cell.first != "。")) lines += mutableListOf(cell) else line += cell
    }
    return lines
}

@Composable
private fun HorizontalSample() {
    val em = with(LocalDensity.current) { SampleSize.toDp() }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        Column(verticalArrangement = Arrangement.spacedBy(em * 0.3f)) {
            for (line in sampleLines((this@BoxWithConstraints.maxWidth / em).toInt().coerceAtLeast(1))) {
                Row {
                    for ((base, ruby) in line) {
                        Column(Modifier.width(em), horizontalAlignment = Alignment.CenterHorizontally) {
                            SampleGlyph(ruby ?: "", SampleSize * 0.5f, Modifier.size(em, em * 0.5f), ruby = true)
                            SampleGlyph(base, SampleSize, Modifier.size(em))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun VerticalSample() {
    val em = with(LocalDensity.current) { SampleSize.toDp() }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        // Columns run right to left, so the first is drawn at the end of the row.
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(em * 0.3f, Alignment.End)) {
            for (column in sampleLines((this@BoxWithConstraints.maxHeight / em).toInt().coerceAtLeast(1)).asReversed()) {
                Column {
                    for ((base, ruby) in column) {
                        Row {
                            // The vertical form of the full stop sits top right, as in a book.
                            SampleGlyph(if (base == "。") "︒" else base, SampleSize, Modifier.size(em))
                            Column(Modifier.width(em * 0.5f)) {
                                for (kana in ruby.orEmpty()) SampleGlyph(kana.toString(), SampleSize * 0.5f, Modifier.size(em * 0.5f), ruby = true)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** One character centred in its square, in the reader's serif; readings in the gothic, as in the reader. */
@Composable
private fun SampleGlyph(text: String, size: TextUnit, modifier: Modifier, ruby: Boolean = false) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Text(
            text,
            fontSize = size,
            lineHeight = size,
            fontFamily = if (ruby) Gothic else Mincho,
            color = if (ruby) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            softWrap = false,
        )
    }
}

@Composable
private fun ColumnScope.ReminderPage(state: Screen.Setup, vm: AppViewModel) {
    Title("Daily reminder")
    val pickReminder = rememberReminderPicker { minute -> vm.editSetup { it.copy(reminderMinute = minute) } }
    val reminder = state.reminderMinute
    Body(
        if (reminder == null) {
            "A little every day works better than a lot now and then. Get a notification when cards are " +
                "waiting, at a time you choose."
        } else {
            "Every day at ${reminderTime(reminder)}, if cards are waiting."
        },
    )
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        SecondaryButton(onClick = { pickReminder(reminder) }) {
            Text(if (reminder == null) "Choose a time" else "Change time")
        }
        if (reminder != null) {
            SecondaryButton(onClick = { vm.editSetup { it.copy(reminderMinute = null) } }) { Text("No reminder") }
        }
    }
    Help("You can change the kana you know, your level and the reminder later in Settings.")
}

/** How [KanaKnown] reads on its chips. */
internal val KanaKnown.label: String
    get() = when (this) {
        KanaKnown.BOTH -> "Both"
        KanaKnown.HIRAGANA -> "Hiragana only"
        KanaKnown.NONE -> "Neither"
    }
