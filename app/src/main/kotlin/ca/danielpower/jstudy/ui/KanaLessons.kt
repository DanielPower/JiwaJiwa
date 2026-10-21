package ca.danielpower.jstudy.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ca.danielpower.jstudy.core.Kana
import ca.danielpower.jstudy.core.KanaCourse
import ca.danielpower.jstudy.core.KanaLesson

/** The borrowed sounds the lesson before them shows, each with a word from the stories where there is one. */
internal val BORROWED_ROWS = listOf("ファ", "フィ", "ティ", "ディ", "ウィ", "シェ", "ヴ")

/**
 * A page on what's new in a set, before its intro: the vowels before the
 * first row, ゛ before the first voiced row, and so on. Until the set's
 * practice starts, it shows each time the set comes up.
 */
@Composable
fun KanaLessonPage(step: KanaStep.Lesson, vm: AppViewModel) {
    Column(Modifier.fillMaxSize()) {
        NewKanaBar(step.intro.set, step.intro.sets, vm::closeKana)
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            when (step.lesson) {
                KanaLesson.VOWELS -> Vowels(vm)
                KanaLesson.DAKUTEN -> Dakuten(vm)
                KanaLesson.HANDAKUTEN -> Handakuten(vm)
                KanaLesson.SMALL_YA -> SmallYa(vm)
                KanaLesson.SMALL_TSU -> SmallTsu(vm)
                KanaLesson.KATAKANA -> Katakana(vm)
                KanaLesson.LONG_VOWEL -> LongVowel(vm)
                KanaLesson.BORROWED -> Borrowed(step, vm)
            }
        }
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 20.dp, top = 4.dp)) {
            PrimaryButton(onClick = vm::kanaLessonDone, modifier = Modifier.weight(1f).height(58.dp)) {
                Text("Continue", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

// ------------------------------------------------------------------ lessons

@Composable
private fun Vowels(vm: AppViewModel) {
    Title("Five vowels")
    Para("Japanese has just five vowels, and each one always sounds the same. Tap one to hear it.", bottom = 4.dp)
    for ((kana, like) in listOf(
        "あ" to "as in “father”",
        "い" to "as in “ski”",
        "う" to "as in “food”, lips relaxed",
        "え" to "as in “get”",
        "お" to "as in “go”, but short",
    )) {
        KanaRow(kana, KanaCourse.unit(kana)!!.romaji, listOf(like), onSay = { vm.sayKana(kana) })
    }
    Heading("Every other kana adds a consonant")
    Para("か is k + a, き is k + i, and so on. So the kana chart is a grid: a column for each vowel, a row for each consonant. You’ll learn it a row a day.")
    SoundChart()
    Para("In red, the few that bend the pattern. Each row’s page points them out. ん, the last kana, is just n.", top = 8.dp, small = true)
}

@Composable
private fun Dakuten(vm: AppViewModel) {
    Title("Voiced kana")
    Para("Two small strokes, ゛, called dakuten, voice a kana’s consonant: your mouth does the same thing, but your throat buzzes. Feel it with a finger on your throat as you say “k”, then “g”.")
    PairRow("か", "が", "k → g", "ka → ga", vm)
    PairRow("さ", "ざ", "s → z", "sa → za", vm)
    PairRow("た", "だ", "t → d", "ta → da", vm)
    PairRow("は", "ば", "h → b", "ha → ba", vm)
    Para("Only these four rows take ゛, and the whole row changes the same way, odd ones too: し shi → じ ji, つ tsu → づ zu, ふ fu → ぶ bu.", top = 8.dp)
    Para("h → b is the strange pair: long ago, は was said “pa”.", top = 8.dp, small = true)
    Para("ぢ and づ sound just like じ and ず. They’re rare: you’ll nearly always see じ and ず.", small = true)
}

@Composable
private fun Handakuten(vm: AppViewModel) {
    Title("From h to p")
    Para("A small circle, ゜, called handakuten, goes only on the は row, and turns h into p.")
    for ((plain, marked) in listOf("は" to "ぱ", "ひ" to "ぴ", "ふ" to "ぷ", "へ" to "ぺ", "ほ" to "ぽ")) {
        PairRow(plain, marked, "${KanaCourse.unit(plain)!!.romaji} → ${KanaCourse.unit(marked)!!.romaji}", null, vm)
    }
    Para("ふ is fu, but ぷ is pu, just as ぶ is bu.", top = 8.dp, small = true)
}

@Composable
private fun SmallYa(vm: AppViewModel) {
    Title("Small ゃ ゅ ょ")
    Para("Written small after a kana that ends in i, ゃ ゅ ょ blend with it into one quick sound: き + ゃ is kya, not ki-ya.")
    for (kana in listOf("きゃ", "きゅ", "きょ")) KanaRow(kana, KanaCourse.unit(kana)!!.romaji, listOf("き + small ${kana.substring(1)}"), onSay = { vm.sayKana(kana) })
    Para("The size matters:", top = 8.dp)
    Words(listOf(Word("びょういん", "byouin", "hospital"), Word("びよういん", "biyouin", "beauty salon")), vm)
    Para("Any kana that ends in i takes them: き し ち に ひ み り, and ぎ じ び ぴ. し, ち and じ lose the y: しゃ is sha, ちゃ is cha, じゃ is ja.", top = 12.dp)
}

@Composable
private fun SmallTsu(vm: AppViewModel) {
    Title("Small っ")
    Para("A small っ doubles the consonant after it: you pause for a beat, then say it. It has no sound of its own.")
    Para("Hear the difference:", top = 8.dp)
    Words(listOf(Word("きて", "kite", "come"), Word("きって", "kitte", "stamp")), vm)
}

@Composable
private fun Katakana(vm: AppViewModel) {
    Title("Katakana")
    Para("Katakana spells the same sounds as hiragana, in sharper shapes. You’ll see it in words from other languages, like コーヒー, “coffee”, and in foreign names, like アレックス.")
    Para("Everything else carries over: ゛ and ゜, small ャ ュ ョ and small ッ work just as they do in hiragana.", top = 8.dp)
    Heading("Some look like their hiragana")
    Column(Modifier.padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for (row in listOf("か", "き", "へ", "り", "も", "や").chunked(3)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (hiragana in row) {
                    val katakana = Kana.toKatakana(hiragana)
                    Column(
                        Modifier
                            .weight(1f)
                            .height(64.dp)
                            .clip(WashiShape)
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, WashiShape)
                            .clickable { vm.sayKana(katakana) },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text("$hiragana$katakana", fontFamily = Gothic, fontSize = 28.sp, lineHeight = 34.sp, letterSpacing = 2.sp)
                        Text(KanaCourse.unit(hiragana)!!.romaji, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
    Para("Again, a row a day, starting with アイウエオ.", top = 12.dp)
}

@Composable
private fun LongVowel(vm: AppViewModel) {
    Title("ー and ッ")
    Para("In katakana, a dash holds the vowel before it for an extra beat: コーヒー is koohii. Hiragana writes the same thing with a vowel, as in おかあさん.")
    Para("ッ is small ツ, and doubles the next sound, just like っ.", top = 8.dp)
    Words(listOf(Word("コーヒー", "koohii", "coffee"), Word("ケーキ", "keeki", "cake"), Word("ベッド", "beddo", "bed")), vm, top = 8.dp)
}

@Composable
private fun Borrowed(step: KanaStep.Lesson, vm: AppViewModel) {
    Title("Borrowed sounds")
    Para("Japanese took in a few sounds from other languages, and katakana spells them with a small ァ ィ ゥ ェ ォ: フ + small ァ is fa, テ + small ィ is ti. ヴ, ウ with ゛, is v.", bottom = 4.dp)
    for (kana in BORROWED_ROWS) {
        val lines = listOfNotNull(KanaCourse.built(kana), step.examples[kana]?.let { exampleLine(kana, it) })
        KanaRow(kana, KanaCourse.unit(kana)!!.romaji, lines, onSay = { vm.sayKana(kana) })
    }
}

// ------------------------------------------------------------------ parts

@Composable
private fun Title(text: String) {
    Text(text, style = MaterialTheme.typography.headlineSmall)
}

@Composable
private fun Heading(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 12.dp))
}

/** A paragraph; [small] for an aside, muted. */
@Composable
private fun Para(text: String, top: Dp = 0.dp, bottom: Dp = 0.dp, small: Boolean = false) {
    Text(
        text,
        style = if (small) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodyLarge,
        color = if (small) MaterialTheme.colorScheme.onSurfaceVariant else Color.Unspecified,
        modifier = Modifier.padding(top = top, bottom = bottom),
    )
}

/** A kana and the same kana with its mark, "か → が": tap it to hear both, one after the other. */
@Composable
private fun PairRow(plain: String, marked: String, label: String, line: String?, vm: AppViewModel) {
    val say = { vm.sayKanas(listOf(plain, marked)) }
    Row(
        Modifier.fillMaxWidth().clip(WashiShape).clickable(onClick = say).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(
            Modifier.width(112.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        ) {
            Text(plain, fontFamily = Gothic, fontSize = 34.sp, lineHeight = 50.sp)
            Text("→", fontSize = 18.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(marked, fontFamily = Gothic, fontSize = 34.sp, lineHeight = 50.sp)
        }
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            if (line != null) Text(line, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        SayButton(onClick = say)
    }
}

private data class Word(val text: String, val romaji: String, val meaning: String)

/** Words to hear side by side, in the phone's voice, as a word card says them. */
@Composable
private fun Words(words: List<Word>, vm: AppViewModel, top: Dp = 4.dp) {
    Column(
        Modifier
            .padding(top = top)
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, WashiShape)
            .padding(start = 16.dp, end = 12.dp, top = 2.dp, bottom = 2.dp),
    ) {
        words.forEachIndexed { i, word ->
            if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(
                Modifier.fillMaxWidth().clickable { vm.sayKana(word.text) }.padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(word.text, fontFamily = Gothic, fontSize = 22.sp, lineHeight = 30.sp)
                Column(Modifier.weight(1f)) {
                    Text(word.romaji, style = MaterialTheme.typography.titleSmall)
                    Text(word.meaning, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                SayButton { vm.sayKana(word.text) }
            }
        }
    }
}

/**
 * The chart's sounds, a row for each consonant and a column for each
 * vowel, the vowels bold and the few that bend the pattern red.
 */
private val SOUNDS = listOf(
    "" to listOf("a", "i", "u", "e", "o"),
    "k" to listOf("ka", "ki", "ku", "ke", "ko"),
    "s" to listOf("sa", "!shi", "su", "se", "so"),
    "t" to listOf("ta", "!chi", "!tsu", "te", "to"),
    "n" to listOf("na", "ni", "nu", "ne", "no"),
    "h" to listOf("ha", "hi", "!fu", "he", "ho"),
    "m" to listOf("ma", "mi", "mu", "me", "mo"),
    "y" to listOf("ya", "", "yu", "", "yo"),
    "r" to listOf("ra", "ri", "ru", "re", "ro"),
    "w" to listOf("wa", "", "", "", "!o"),
    "" to listOf("n", "", "", "", ""),
)

@Composable
private fun SoundChart() {
    val colors = MaterialTheme.colorScheme
    Column(Modifier.padding(top = 4.dp)) {
        SOUNDS.forEachIndexed { i, (consonant, cells) ->
            if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(colors.outlineVariant))
            Row(Modifier.height(24.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(consonant, style = MaterialTheme.typography.bodyMedium, fontSize = 13.sp, color = colors.onSurfaceVariant, modifier = Modifier.width(28.dp))
                for (cell in cells) {
                    val odd = cell.startsWith("!")
                    Text(
                        cell.removePrefix("!"),
                        style = MaterialTheme.typography.bodyMedium,
                        fontSize = 15.sp,
                        fontWeight = if (odd || i == 0) FontWeight.Bold else null,
                        color = if (odd) colors.primary else Color.Unspecified,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}
