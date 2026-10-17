package ca.danielpower.jstudy.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ca.danielpower.jstudy.core.MasteryRules
import ca.danielpower.jstudy.core.ReadLevel

/** First run, a page at a time: welcome, your level, reminder. */
@Composable
fun SetupScreen(state: Screen.Setup, vm: AppViewModel) {
    val pages = SetupPage.entries
    val index = state.page.ordinal
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
                SetupPage.LEVEL -> LevelPage(state, vm)
                SetupPage.REMINDER -> ReminderPage(state, vm)
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, bottom = 20.dp, top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (index > 0) {
                SecondaryButton(onClick = vm::setupBack, modifier = Modifier.weight(1f).height(58.dp)) {
                    Text(ui("Back", "戻る"), fontSize = 18.sp)
                }
            }
            PrimaryButton(
                onClick = if (last) vm::finishSetup else vm::setupNext,
                modifier = Modifier.weight(1f).height(58.dp),
            ) {
                Text(if (last) ui("Start", "始める") else ui("Next", "次へ"), fontSize = 18.sp, fontWeight = FontWeight.Bold)
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
    Title(ui("Welcome", "ようこそ"))
    Body(
        "Jiwa Jiwa teaches Japanese through stories written for learners. Each one brings a few new words " +
            "and some grammar, and the words you meet come back as flashcards, just before you'd forget them.",
    )
    Body("A couple of questions first, so it starts at the right place for you.")
}

@Composable
private fun ColumnScope.LevelPage(state: Screen.Setup, vm: AppViewModel) {
    Title(ui("Your level", "レベル"))
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
        WashiChip(selected = selected == ReadLevel.NONE, onClick = { onSelect(ReadLevel.NONE) }, label = ui("None", "なし"))
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
private fun ColumnScope.ReminderPage(state: Screen.Setup, vm: AppViewModel) {
    Title(ui("Daily reminder", "毎日の通知"))
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
            Text(if (reminder == null) ui("Choose a time", "時間を選ぶ") else ui("Change time", "時間を変える"))
        }
        if (reminder != null) {
            SecondaryButton(onClick = { vm.editSetup { it.copy(reminderMinute = null) } }) { Text(ui("No reminder", "通知しない")) }
        }
    }
    Help(
        "You can change your level and the reminder later in Settings, and mark single " +
            "words from the dictionary.",
    )
}
