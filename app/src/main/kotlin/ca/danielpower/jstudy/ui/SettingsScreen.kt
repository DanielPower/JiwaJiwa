package ca.danielpower.jstudy.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.TextButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ca.danielpower.jstudy.core.KanaKnown
import ca.danielpower.jstudy.core.MasteryRules
import ca.danielpower.jstudy.core.StudySettings
import ca.danielpower.jstudy.data.Reminder
import java.time.LocalDate
import java.util.Locale

@Composable
fun SettingsScreen(
    state: Screen.Settings,
    theme: ThemeMode,
    onChange: (StudySettings) -> Unit,
    onSaveClaudeKey: (String) -> Unit,
    onImport: () -> Unit,
    onTheme: (ThemeMode) -> Unit,
    vm: AppViewModel,
) {
    val s = state.settings
    fun update(block: () -> StudySettings) {
        runCatching(block).onSuccess(onChange)
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(ui("Settings", "設定"), style = MaterialTheme.typography.headlineMedium)

        Section(ui("Appearance", "見た目", "見る", "目"))
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(ui("Theme", "テーマ"), modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (mode in ThemeMode.entries) {
                    WashiChip(selected = theme == mode, onClick = { onTheme(mode) }, label = ui(mode.label))
                }
            }
        }

        Section(ui("Kana", "かな", "仮名"))
        Text("Kana you could read already", style = MaterialTheme.typography.bodyLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for ((known, label) in KANA_CHOICES) {
                WashiChip(selected = state.kanaKnown == known, onClick = { vm.setKanaKnown(known) }, label = ui(label))
            }
        }
        Text(
            kanaHelp(state.kanaKnown),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (state.kanaKnown != KanaKnown.BOTH) {
            Stepper("New kana per day", "${s.newKanaPerDay}") { d ->
                update { s.copy(newKanaPerDay = (s.newKanaPerDay + d * 2).coerceIn(0, 60)) }
            }
        }

        Section(ui("Daily study", "毎日の勉強"))
        Text("Start new words from", style = MaterialTheme.typography.bodyLarge)
        // Moving past N5 asks what to do with the easier words, as first-run setup does.
        var pendingLevel by remember { mutableStateOf<Int?>(null) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (level in 5 downTo 1) {
                WashiChip(
                    selected = state.startLevel == level,
                    onClick = {
                        when {
                            level == state.startLevel -> {}
                            level < 5 -> pendingLevel = level
                            else -> vm.setStartLevel(level)
                        }
                    },
                    label = "N$level",
                )
            }
        }
        pendingLevel?.let { level ->
            val easier = (5 downTo level + 1).joinToString(" and ") { "N$it" }
            val choose = { mastery: Int ->
                pendingLevel = null
                vm.setStartLevel(level, mastery)
            }
            AlertDialog(
                onDismissRequest = { pendingLevel = null },
                title = { Text("Start from N$level") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("What about the words in $easier you haven't started yet? Words you've started keep their progress.")
                        TextButton(onClick = { choose(0) }) { Text("Learn them later, after the harder levels") }
                        TextButton(onClick = { choose(MasteryRules.HIDE_AT) }) { Text("Known: review over the next week") }
                        TextButton(onClick = { choose(MasteryRules.OFF_AT) }) { Text("Mastered: review over the next month") }
                    }
                },
                confirmButton = {},
                dismissButton = { TextButton(onClick = { pendingLevel = null }) { Text(ui("Cancel", "やめる")) } },
            )
        }
        Text(
            "New words come from this level first, then the harder ones, then any easier ones.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Stepper(ui("New words per day", "一日の新しい単語"), "${s.newPerDay}") { d ->
            update { s.copy(newPerDay = (s.newPerDay + d).coerceIn(0, 100)) }
        }
        Stepper("Review limit per day", "${s.reviewCap}") { d ->
            update { s.copy(reviewCap = (s.reviewCap + d * 50).coerceIn(50, 2000)) }
        }
        Stepper("New day starts at", String.format(Locale.ROOT, "%02d:00", s.rolloverHour)) { d ->
            update { s.copy(rolloverHour = (s.rolloverHour + d + 24) % 24) }
        }

        Section(ui("Reminder", "通知"))
        val r = state.reminder
        val pickReminder = rememberReminderPicker { vm.setReminder(Reminder(enabled = true, minuteOfDay = it)) }
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(ui("Daily reminder", "毎日の通知"), style = MaterialTheme.typography.bodyLarge)
                Text(
                    "A notification when cards are waiting. Nothing if you're done for the day.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = r.enabled,
                onCheckedChange = { on -> if (on) pickReminder(r.minuteOfDay) else vm.setReminder(r.copy(enabled = false)) },
            )
        }
        if (r.enabled && r.minuteOfDay != null) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(ui("Remind me at", "時間"), modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                SecondaryButton(onClick = { pickReminder(r.minuteOfDay) }) { Text(reminderTime(r.minuteOfDay)) }
            }
        }

        Section(ui("Stories", "物語"))
        Text(
            "Stories are written by Claude using your own API key from console.anthropic.com, " +
                "and billed to it.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        var key by remember(state.claudeKey) { mutableStateOf(state.claudeKey) }
        OutlinedTextField(
            value = key,
            onValueChange = { key = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Claude API key") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        )
        if (key.trim() != state.claudeKey) {
            SecondaryButton(onClick = { onSaveClaudeKey(key) }) { Text(ui("Save key", "保存")) }
        } else if (state.claudeKey.isNotEmpty()) {
            Text(ui("Saved", "保存しました"), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        }

        Section(ui("Import", "インポート"))
        Text(
            "Bring in the mastery of words you've studied on Renshuu.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SecondaryButton(onClick = onImport) { Text(ui("Import from Renshuu", "Renshuuからインポート", "インポート")) }

        Section(ui("Backup", "バックアップ"))
        Text(
            "Save your progress, settings and stories to a file, or restore them from one. " +
                "Your Claude API key isn't included.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
            if (uri != null) vm.exportBackup(uri)
        }
        var restoring by remember { mutableStateOf(false) }
        val restore = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) vm.importBackup(uri)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SecondaryButton(onClick = { export.launch("jiwajiwa-${LocalDate.now()}.jstudy") }, modifier = Modifier.weight(1f)) {
                Text(ui("Save backup", "バックアップを保存"))
            }
            SecondaryButton(onClick = { restoring = true }, modifier = Modifier.weight(1f)) { Text(ui("Restore", "戻す")) }
        }
        state.backupMessage?.let {
            Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        }
        if (restoring) {
            AlertDialog(
                onDismissRequest = { restoring = false },
                title = { Text("Restore a backup?") },
                text = { Text("Everything in the backup replaces your current progress, settings and stories.") },
                confirmButton = {
                    TextButton(onClick = {
                        restoring = false
                        restore.launch(arrayOf("*/*"))
                    }) { Text(ui("Choose file", "ファイルを選ぶ")) }
                },
                dismissButton = { TextButton(onClick = { restoring = false }) { Text(ui("Cancel", "やめる")) } },
            )
        }

        Section(ui("Reset", "消す"))
        Text(
            "Erase all your progress, settings, stories and your Claude API key, " +
                "and start again from first-run setup.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        var resetting by remember { mutableStateOf(false) }
        SecondaryButton(onClick = { resetting = true }) {
            Text(ui("Reset everything", "全部消す"), color = MaterialTheme.colorScheme.error)
        }
        if (resetting) {
            AlertDialog(
                onDismissRequest = { resetting = false },
                title = { Text("Reset everything?") },
                text = {
                    Text(
                        "All your cards, mastery, review history, settings, stories and your Claude API key " +
                            "will be deleted. This can't be undone. Save a backup first if you might want them back.",
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        resetting = false
                        vm.resetAll()
                    }) { Text(ui("Reset", "消す"), color = MaterialTheme.colorScheme.error) }
                },
                dismissButton = { TextButton(onClick = { resetting = false }) { Text(ui("Cancel", "やめる")) } },
            )
        }

        Section(ui("About", "このアプリについて", "この", "アプリ"))
        Text(
            "Word list and dictionary data:\n" + state.attribution.split("; ").joinToString("\n") { "• $it" } +
                "\n\nThis app uses the JMdict/EDICT and KANJIDIC dictionary files. These files are the property of " +
                "the Electronic Dictionary Research and Development Group, and are used in conformance with the " +
                "Group's licence (https://www.edrdg.org/edrdg/licence.html). See " +
                "https://www.edrdg.org/wiki/index.php/JMdict-EDICT_Dictionary_Project and " +
                "https://www.edrdg.org/wiki/index.php/KANJIDIC_Project." +
                "\n\nThe app's dictionary database is adapted from the sources above (entries are merged, " +
                "filtered and ranked) and is licensed under CC BY-SA 4.0 (https://creativecommons.org/licenses/by-sa/4.0/)." +
                "\n\nFonts: Zen Kaku Gothic New, Zen Old Mincho and Zen Maru Gothic, under the SIL Open Font License." +
                "\n\nSoftware: Kuromoji (Atilika) with the IPADIC dictionary (NAIST), AndroidX, Jetpack Compose, Kotlin and " +
                "kotlinx.serialization, under the Apache License 2.0.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun Section(title: String) {
    HorizontalDivider(Modifier.padding(top = 16.dp), color = MaterialTheme.colorScheme.onSurface)
    SectionLabel(title, Modifier.padding(top = 4.dp, bottom = 4.dp))
}

@Composable
private fun Stepper(label: String, value: String, onStep: (Int) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        SecondaryButton(onClick = { onStep(-1) }, modifier = Modifier.size(44.dp), contentPadding = PaddingValues(0.dp)) {
            Text("−", fontSize = 18.sp)
        }
        Text(
            value,
            modifier = Modifier.widthIn(min = 64.dp),
            textAlign = TextAlign.Center,
            fontFamily = Gothic,
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
        )
        SecondaryButton(onClick = { onStep(1) }, modifier = Modifier.size(44.dp), contentPadding = PaddingValues(0.dp)) {
            Text("+", fontSize = 18.sp)
        }
    }
}
