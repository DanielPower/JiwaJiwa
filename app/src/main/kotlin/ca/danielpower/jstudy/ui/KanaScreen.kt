package ca.danielpower.jstudy.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ca.danielpower.jstudy.core.Kana
import ca.danielpower.jstudy.core.KanaCourse
import ca.danielpower.jstudy.core.KanaLookNote
import ca.danielpower.jstudy.core.KanaQuestion
import ca.danielpower.jstudy.core.KanaStage
import ca.danielpower.jstudy.core.KanaUnit
import ca.danielpower.jstudy.core.Script
import ca.danielpower.jstudy.data.KanaExample
import kotlinx.coroutines.delay

/** Kana practice: a new set's lesson and intro, the questions, and the chart at the end. */
@Composable
fun KanaScreen(state: Screen.Kana, vm: AppViewModel) {
    when (val step = state.step) {
        is KanaStep.Lesson -> KanaLessonPage(step, vm)
        is KanaStep.Intro -> KanaIntro(step, vm)
        is KanaStep.Ask -> KanaAsk(state, step, vm)
        is KanaStep.Done -> KanaDone(step, vm)
    }
}

// ------------------------------------------------------------------ intro

@Composable
private fun KanaIntro(step: KanaStep.Intro, vm: AppViewModel) {
    Column(Modifier.fillMaxSize()) {
        NewKanaBar(step.set, step.sets, vm::closeKana)
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                step.line,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            for (unit in step.units) IntroRow(unit, step.examples[unit.text]) { vm.sayKana(unit.text) }
            if (step.looks.isNotEmpty()) LookAlike(step.looks, vm::sayKana)
        }
        Row(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 20.dp, top = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Quiet, like a grammar lesson's: a quick check of each, once.
            TextButton(onClick = { vm.practiseKana(know = true) }) {
                Text("I know these", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            PrimaryButton(onClick = { vm.practiseKana() }, modifier = Modifier.weight(1f).height(58.dp)) {
                Text("Practice", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

/** The close button and "New kana · set 2 of 47", over a set's lesson and its intro. */
@Composable
internal fun NewKanaBar(set: Int, sets: Int, onClose: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        WashiIconButton(WashiIcons.Close, "Close", onClose)
        Text("New kana · set $set of $sets", style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun IntroRow(unit: KanaUnit, example: KanaExample?, onSay: () -> Unit) {
    // How a voiced or combined kana is built on one met already: "フ with ゛ · fu → bu".
    val lines = listOfNotNull(KanaCourse.built(unit.text), unit.note ?: example?.let { exampleLine(unit.text, it) })
    KanaRow(unit.text, soundLabel(unit), lines, onSay.takeIf { !unit.mark })
}

/** "in 来た (きた), “came”": a word with [text] in, with its reading when the word spells it in kanji. */
internal fun exampleLine(text: String, e: KanaExample): String {
    val word = if (text in e.word) e.word else "${e.word} (${e.reading})"
    return "in $word, “${e.meaning}”"
}

/** A kana large, its [label] and [lines] beside it, and a speaker if it has a sound: tap anywhere on it to hear it. */
@Composable
internal fun KanaRow(text: String, label: String, lines: List<String>, onSay: (() -> Unit)?) {
    Row(
        Modifier.fillMaxWidth().clip(WashiShape).clickable(enabled = onSay != null) { onSay?.invoke() }.padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // A combined kana (しゃ) is two glyphs wide, too wide for this column at 44sp.
        Text(text, fontFamily = Gothic, fontSize = if (text.length > 1) 34.sp else 44.sp, lineHeight = 50.sp, modifier = Modifier.width(76.dp), textAlign = TextAlign.Center)
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            for (line in lines) Text(line, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (onSay != null) SayButton(onClick = onSay)
    }
}

/**
 * The set's worst lookalikes beside the kana they're taken for, met before or
 * in this set, each with what tells them apart. Tap a kana to hear it.
 */
@Composable
private fun LookAlike(notes: List<KanaLookNote>, onSay: (String) -> Unit) {
    val colors = MaterialTheme.colorScheme
    Column(
        Modifier
            .padding(top = 8.dp)
            .fillMaxWidth()
            .border(1.dp, colors.outlineVariant, WashiShape)
            .padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SectionLabel("Look alike")
        for (note in notes) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Row {
                    for (text in listOf(note.kana) + note.like) {
                        Column(
                            Modifier.width(44.dp).clip(WashiShape).clickable { onSay(text) },
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(text, fontFamily = Gothic, fontSize = 34.sp, lineHeight = 42.sp)
                            Text(KanaCourse.unit(text)?.romaji.orEmpty(), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                        }
                    }
                }
                Text(note.tell, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant, modifier = Modifier.weight(1f))
            }
        }
        Text("They come up as wrong answers for each other.", style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
    }
}

/** A kana's sound as taught; a mark says what it does. */
private fun soundLabel(unit: KanaUnit) = when (unit.text) {
    "っ", "ッ" -> "doubles the next sound"
    "ー" -> "makes a vowel long"
    else -> unit.romaji
}

// ------------------------------------------------------------------ questions

@Composable
private fun KanaAsk(state: Screen.Kana, step: KanaStep.Ask, vm: AppViewModel) {
    val q = step.question
    val result = step.result
    val noRipple = remember { MutableInteractionSource() }
    // A right answer moves on by itself once its sound has had time to play out; a wrong one waits for a tap.
    LaunchedEffect(step) {
        if (result?.correct == true) {
            delay(if (q.unit.mark) MARK_PAUSE else RIGHT_PAUSE)
            vm.awaitKanaSound(SOUND_WAIT)
            vm.nextKana()
        }
    }
    val wrong = result != null && !result.correct
    Column(
        Modifier.fillMaxSize().clickable(interactionSource = noRipple, indication = null, enabled = wrong, onClickLabel = "Go on", role = if (wrong) Role.Button else null) { vm.nextKana() },
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            WashiIconButton(WashiIcons.Close, "Close", vm::closeKana)
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(18.dp, Alignment.CenterHorizontally)) {
                Text("新 ${state.newLeft}", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                Text("習 ${state.checkLeft}", color = MaterialTheme.colorScheme.tertiary, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            }
            // Balances the close button, so the counts sit in the middle.
            Spacer(Modifier.size(48.dp))
        }
        ProgressLine(state.progress, Modifier.padding(horizontal = 20.dp))

        val cardShape = MaterialTheme.shapes.large
        val colors = MaterialTheme.colorScheme
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp)
                .clip(cardShape)
                .background(colors.surfaceContainerLow)
                .border(1.dp, colors.outlineVariant, cardShape),
        ) {
            // Indigo when you answer with a sound, vermilion when you answer with a kana, as on the word cards.
            Text(
                (if (step.fresh) "新 " else "習 ") + if (q.listen) "Sound → kana" else if (q.unit.mark) "Word → sound" else "Kana → sound",
                style = MaterialTheme.typography.labelLarge,
                color = if (q.listen) colors.onPrimary else colors.onSecondary,
                modifier = Modifier.fillMaxWidth().background(if (q.listen) colors.primary else colors.secondary).padding(horizontal = 24.dp, vertical = 8.dp),
            )
            Box(Modifier.weight(1f).fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                if (wrong) {
                    Feedback(q, step, vm)
                } else if (q.listen) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(
                            Modifier.size(96.dp).clip(WashiShape).clickable { vm.sayKana(q.unit.text) },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(WashiIcons.Sound, contentDescription = "Play it again", tint = colors.primary, modifier = Modifier.size(64.dp))
                        }
                        Text("Pick the kana you hear", style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                    }
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(q.prompt, fontFamily = Gothic, fontSize = if (q.unit.mark) 56.sp else 112.sp, lineHeight = if (q.unit.mark) 64.sp else 124.sp)
                        if (q.unit.mark) Text("How is it said?", style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                    }
                }
            }
            if (wrong) {
                Text(
                    "Tap to go on",
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                )
            }
        }

        Options(q, step, vm, Modifier.padding(start = 20.dp, end = 20.dp, bottom = 20.dp, top = 4.dp))
    }
}

/** A wrong answer: the right kana beside the one you picked, each with its sound. */
@Composable
private fun Feedback(q: KanaQuestion, step: KanaStep.Ask, vm: AppViewModel) {
    val picked = step.result?.picked?.let { KanaCourse.unit(it) }
    if (q.unit.mark) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(q.prompt, fontFamily = Gothic, fontSize = 48.sp, lineHeight = 56.sp)
            Text(q.answer, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.washi.pine)
            Text(soundLabel(q.unit).replaceFirstChar { it.uppercase() } + ".", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    Row(horizontalArrangement = Arrangement.spacedBy(28.dp), verticalAlignment = Alignment.Top) {
        FeedbackKana(q.unit, MaterialTheme.washi.pine) { vm.sayKana(q.unit.text) }
        if (picked != null) FeedbackKana(picked, MaterialTheme.colorScheme.error) { vm.sayKana(picked.text) }
    }
}

@Composable
private fun FeedbackKana(unit: KanaUnit, color: Color, onSay: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(unit.text, fontFamily = Gothic, fontSize = 72.sp, lineHeight = 82.sp)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(unit.romaji, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = color)
            SayButton(onClick = onSay)
        }
    }
}

/** The four answers, two by two. Once answered, the right one goes green and a wrong pick red. */
@Composable
private fun Options(q: KanaQuestion, step: KanaStep.Ask, vm: AppViewModel, modifier: Modifier) {
    val answered = step.result != null
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        for (row in q.options.chunked(2)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                for (option in row) {
                    val right = option == q.answer
                    val colors = MaterialTheme.colorScheme
                    val (container, content) = when {
                        answered && right -> MaterialTheme.washi.pine to colors.surface
                        answered && option == step.picked -> colors.error to colors.onError
                        else -> colors.surfaceContainerLow to colors.onSurface.copy(alpha = if (answered) 0.4f else 1f)
                    }
                    // Said as well as coloured, for TalkBack.
                    val state = when {
                        answered && right -> "Right"
                        answered && option == step.picked -> "Wrong"
                        else -> null
                    }
                    Box(
                        Modifier
                            .weight(1f)
                            .height(72.dp)
                            .clip(WashiShape)
                            .background(container)
                            .border(1.dp, if (answered) container else colors.outline, WashiShape)
                            .semantics { if (state != null) stateDescription = state }
                            .clickable(enabled = !answered, role = Role.Button) { vm.answerKana(option) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            option,
                            fontFamily = if (q.listen) Gothic else null,
                            fontSize = if (q.listen) 34.sp else 22.sp,
                            fontWeight = if (q.listen) null else FontWeight.Bold,
                            color = content,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ProgressLine(done: Float, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(3.dp).background(MaterialTheme.colorScheme.surfaceVariant)) {
        Box(Modifier.fillMaxWidth(done.coerceIn(0f, 1f)).height(3.dp).background(MaterialTheme.colorScheme.onSurface))
    }
}

// How long a right answer stays up, its sound playing, before the next question: at least RIGHT_PAUSE
// (MARK_PAUSE for a mark's word, which the phone's voice is slower to say), then on until its
// recording ends, but no more than SOUND_WAIT longer.
private const val RIGHT_PAUSE = 600L
private const val MARK_PAUSE = 1200L
private const val SOUND_WAIT = 2500L

// ------------------------------------------------------------------ done

@Composable
private fun KanaDone(step: KanaStep.Done, vm: AppViewModel) {
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            WashiIconButton(WashiIcons.Close, "Close", vm::closeKana)
        }
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Kana practice done", style = MaterialTheme.typography.headlineMedium)
            Text(
                listOfNotNull(
                    listOfNotNull(
                        step.learned.takeIf { it > 0 }?.let { if (it == 1) "1 new kana" else "$it new kana" },
                        step.checked.takeIf { it > 0 }?.let { "$it checked" },
                    ).joinToString(", ").takeIf { it.isNotEmpty() }?.let { "$it." },
                    "One more quick check of the new kana later today.".takeIf { step.later > 0 },
                ).joinToString(" "),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            KanaChartView(step.chart, vm::sayKana, legend = true, modifier = Modifier.padding(top = 8.dp))
        }
        Column(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 20.dp, top = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (step.cardsNext > 0) {
                PrimaryButton(onClick = vm::kanaThenReview, modifier = Modifier.fillMaxWidth().height(58.dp)) {
                    Text(if (step.cardsNext == 1) "Now 1 card" else "Now ${step.cardsNext} cards", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (step.more > 0) {
                    SecondaryButton(onClick = vm::moreKana, modifier = Modifier.weight(1f).height(56.dp)) { Text("${step.more} more", fontSize = 16.sp) }
                }
                if (step.cardsNext > 0) {
                    SecondaryButton(onClick = vm::closeKana, modifier = Modifier.weight(1f).height(56.dp)) { Text("Done", fontSize = 16.sp) }
                } else {
                    PrimaryButton(onClick = vm::closeKana, modifier = Modifier.weight(1f).height(56.dp)) {
                        Text("Done", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------------ chart

/** The basic kana by row of the chart, a vowel to a column; null leaves a gap. */
private val BASIC_ROWS = listOf(
    "あいうえお", "かきくけこ", "さしすせそ", "たちつてと", "なにぬねの",
    "はひふへほ", "まみむめも", "や ゆ よ", "らりるれろ", "わ   を", "ん    ",
).map { row -> row.map { c -> c.toString().takeIf { c != ' ' } } }

private val VOICED_ROWS = listOf("がぎぐげご", "ざじずぜぞ", "だぢづでど", "ばびぶべぼ", "ぱぴぷぺぽ")
    .map { row -> row.map { it.toString() } }

private val COMBINED_ROWS = "きしちにひみりぎじびぴ".map { c -> "ゃゅょ".map { small -> "$c$small" } }

/**
 * Every kana with its sound, hiragana or katakana, coloured by where you
 * are: clear plain, still learning gold, met today red, not started faded.
 * Tap one to hear it.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun KanaChartView(chart: KanaChart, onSay: (String) -> Unit, modifier: Modifier = Modifier, legend: Boolean = false) {
    val state = chart.state
    // Opens on the script you're learning: katakana once you read hiragana.
    var script by rememberSaveable {
        mutableStateOf(if (state.toLearn.isNotEmpty() && state.toLearn.none { KanaCourse.unit(it)?.script == Script.HIRAGANA }) Script.KATAKANA else Script.HIRAGANA)
    }
    fun spell(text: String) = if (script == Script.KATAKANA) Kana.toKatakana(text) else text
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            WashiChip(selected = script == Script.HIRAGANA, onClick = { script = Script.HIRAGANA }, label = "Hiragana")
            WashiChip(selected = script == Script.KATAKANA, onClick = { script = Script.KATAKANA }, label = "Katakana")
            Spacer(Modifier.weight(1f))
            Text("Tap to hear", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        val learning = state.toLearn.filter { KanaCourse.unit(it)?.script == script }
        if (learning.isNotEmpty()) {
            val name = if (script == Script.HIRAGANA) "Hiragana" else "Katakana"
            SectionLabel("$name · ${learning.count { state.stage(it) == KanaStage.CLEAR }} of ${learning.size} clear")
        }
        ChartGrid(BASIC_ROWS.map { row -> row.map { it?.let(::spell) } }, chart, onSay)
        SectionLabel("Voiced ゛ ゜", Modifier.padding(top = 6.dp))
        ChartGrid(VOICED_ROWS.map { row -> row.map(::spell) }, chart, onSay)
        SectionLabel("Combined", Modifier.padding(top = 6.dp))
        ChartGrid(COMBINED_ROWS.map { row -> row.map(::spell) }, chart, onSay, columns = 3)
        // The marks, and katakana's spellings for borrowed sounds.
        val others = KanaCourse.units.filter { u ->
            u.script == script && (u.mark || (script == Script.KATAKANA && u.text.length == 2 && u.text[1] !in "ャュョ") || u.text == "ヴ")
        }
        if (others.isNotEmpty()) {
            SectionLabel(if (script == Script.KATAKANA) "Marks and borrowed sounds" else "Marks", Modifier.padding(top = 6.dp))
            ChartGrid(others.map { it.text }.chunked(5), chart, onSay)
        }
        if (legend) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.padding(top = 4.dp)) {
                LegendItem(MaterialTheme.colorScheme.primary, "new today")
                LegendItem(MaterialTheme.colorScheme.tertiary, "learning")
                LegendItem(MaterialTheme.colorScheme.onSurface, "clear")
                LegendItem(MaterialTheme.colorScheme.onSurfaceVariant, "not started")
            }
        }
    }
}

@Composable
private fun ChartGrid(rows: List<List<String?>>, chart: KanaChart, onSay: (String) -> Unit, columns: Int = 5) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        for (row in rows) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                for (n in 0 until columns) {
                    val unit = row.getOrNull(n)?.let { KanaCourse.unit(it) }
                    Box(Modifier.weight(1f)) { if (unit != null) ChartCell(unit, chart, onSay) }
                }
            }
        }
    }
}

@Composable
private fun ChartCell(unit: KanaUnit, chart: KanaChart, onSay: (String) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val stage = chart.state.stage(unit.text)
    val today = chart.state.progress[unit.text]?.introducedDay == chart.today && stage != KanaStage.CLEAR
    // Not started is muted rather than faded, so its small romaji stays readable.
    val (background, ink) = when {
        today -> colors.primary.copy(alpha = 0.12f) to colors.primary
        stage == KanaStage.LEARNING -> colors.tertiary.copy(alpha = 0.16f) to colors.onSurface
        stage == KanaStage.NEW -> Color.Transparent to colors.onSurfaceVariant
        else -> Color.Transparent to colors.onSurface
    }
    val said = when {
        today -> "new today"
        stage == KanaStage.LEARNING -> "learning"
        stage == KanaStage.NEW -> "not started"
        else -> "clear"
    }
    Column(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clip(WashiShape)
            .background(background)
            .border(1.dp, colors.outlineVariant, WashiShape)
            .semantics { stateDescription = said }
            .clickable(enabled = !unit.mark) { onSay(unit.text) }
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(unit.text, fontFamily = Gothic, fontSize = 24.sp, lineHeight = 28.sp, color = ink)
        Text(
            if (unit.mark) "" else unit.romaji,
            style = MaterialTheme.typography.labelSmall,
            color = colors.onSurfaceVariant,
        )
    }
}

@Composable
private fun LegendItem(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(10.dp).background(color, WashiShape))
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
