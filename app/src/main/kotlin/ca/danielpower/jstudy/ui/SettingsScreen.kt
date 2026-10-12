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
import ca.danielpower.jstudy.core.StudySettings
import ca.danielpower.jstudy.data.Reminder
import java.time.LocalDate
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(
    state: Screen.Settings,
    theme: ThemeMode,
    onChange: (StudySettings) -> Unit,
    onSaveClaudeKey: (String) -> Unit,
    onImport: () -> Unit,
    onStoryVertical: (Boolean) -> Unit,
    onTheme: (ThemeMode) -> Unit,
    vm: AppViewModel,
) {
    val s = state.settings
    val m = s.mastery
    fun update(block: () -> StudySettings) {
        runCatching(block).onSuccess(onChange)
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineMedium)

        Section("Appearance")
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Theme", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (mode in ThemeMode.entries) {
                    WashiChip(selected = theme == mode, onClick = { onTheme(mode) }, label = mode.label)
                }
            }
        }

        Section("Kana")
        Text("Kana you could read already", style = MaterialTheme.typography.bodyLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for ((known, label) in KANA_CHOICES) {
                WashiChip(selected = state.kanaKnown == known, onClick = { vm.setKanaKnown(known) }, label = label)
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

        Section("Daily study")
        Text("Start new words from", style = MaterialTheme.typography.bodyLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (level in 5 downTo 1) {
                WashiChip(selected = state.startLevel == level, onClick = { vm.setStartLevel(level) }, label = "N$level")
            }
        }
        Text(
            "New words come from this level first, then the harder ones, then any easier ones.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Stepper("New words per day", "${s.newPerDay}") { d ->
            update { s.copy(newPerDay = (s.newPerDay + d).coerceIn(0, 100)) }
        }
        Stepper("Review limit per day", "${s.reviewCap}") { d ->
            update { s.copy(reviewCap = (s.reviewCap + d * 50).coerceIn(50, 2000)) }
        }
        Stepper("Target retention", "${(s.desiredRetention * 100).roundToInt()}%") { d ->
            update { s.copy(desiredRetention = ((s.desiredRetention * 100).roundToInt() + d).coerceIn(80, 95) / 100.0) }
        }
        Stepper("New day starts at", String.format(Locale.ROOT, "%02d:00", s.rolloverHour)) { d ->
            update { s.copy(rolloverHour = (s.rolloverHour + d + 24) % 24) }
        }

        Section("Mastery")
        Stepper("Gain when right", "+${m.gainOnRight}") { d ->
            update { s.copy(mastery = m.copy(gainOnRight = (m.gainOnRight + d).coerceIn(1, 50))) }
        }
        Stepper("Loss when wrong", "−${m.lossOnWrong}") { d ->
            update { s.copy(mastery = m.copy(lossOnWrong = (m.lossOnWrong + d * 5).coerceIn(0, 100))) }
        }
        Stepper("Hide furigana from", "${m.hideAt}") { d ->
            update { s.copy(mastery = m.copy(hideAt = (m.hideAt + d * 5).coerceIn(0, m.offAt))) }
        }
        Stepper("Turn furigana off from", "${m.offAt}") { d ->
            update { s.copy(mastery = m.copy(offAt = (m.offAt + d * 5).coerceIn(m.hideAt, 100))) }
        }
        Stepper("English to Japanese cards from", "${m.productionAt}") { d ->
            update { s.copy(mastery = m.copy(productionAt = (m.productionAt + d * 5).coerceIn(0, 100))) }
        }

        Section("Reminder")
        val r = state.reminder
        val pickReminder = rememberReminderPicker { vm.setReminder(Reminder(enabled = true, minuteOfDay = it)) }
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Daily reminder", style = MaterialTheme.typography.bodyLarge)
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
                Text("Remind me at", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                SecondaryButton(onClick = { pickReminder(r.minuteOfDay) }) { Text(reminderTime(r.minuteOfDay)) }
            }
        }

        Section("Stories")
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
            SecondaryButton(onClick = { onSaveClaudeKey(key) }) { Text("Save key") }
        } else if (state.claudeKey.isNotEmpty()) {
            Text("Saved", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        }

        Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Vertical text", style = MaterialTheme.typography.bodyLarge)
                Text(
                    "縦書き: top to bottom, right to left, like a Japanese book.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = state.storyVertical, onCheckedChange = onStoryVertical)
        }

        Section("Import")
        Text(
            "Bring in the mastery of words you've studied on Renshuu.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SecondaryButton(onClick = onImport) { Text("Import from Renshuu") }

        Section("Backup")
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
            SecondaryButton(onClick = { export.launch("jstudy-${LocalDate.now()}.jstudy") }, modifier = Modifier.weight(1f)) {
                Text("Save backup")
            }
            SecondaryButton(onClick = { restoring = true }, modifier = Modifier.weight(1f)) { Text("Restore") }
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
                    }) { Text("Choose file") }
                },
                dismissButton = { TextButton(onClick = { restoring = false }) { Text("Cancel") } },
            )
        }

        Section("About")
        Text(
            "Word list and dictionary data:\n" + state.attribution.split("; ").joinToString("\n") { "• $it" } +
                "\n\nFonts: Shippori Mincho and Zen Kaku Gothic New, under the SIL Open Font License.",
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
            fontFamily = Mincho,
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
        )
        SecondaryButton(onClick = { onStep(1) }, modifier = Modifier.size(44.dp), contentPadding = PaddingValues(0.dp)) {
            Text("+", fontSize = 18.sp)
        }
    }
}
