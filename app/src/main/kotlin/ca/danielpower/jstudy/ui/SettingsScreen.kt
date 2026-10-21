package ca.danielpower.jstudy.ui

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.TextButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import ca.danielpower.jstudy.core.ReadLevel
import ca.danielpower.jstudy.core.StudySettings
import ca.danielpower.jstudy.data.Reminder
import ca.danielpower.jstudy.reminder.DailyReminder
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

@Composable
fun SettingsScreen(
    state: Screen.Settings,
    theme: ThemeMode,
    onChange: (StudySettings) -> Unit,
    onImport: () -> Unit,
    onTheme: (ThemeMode) -> Unit,
    vm: AppViewModel,
) {
    val s = state.settings
    fun update(block: () -> StudySettings) {
        runCatching(block).onSuccess(onChange)
    }

    // Opened from the gear on Home, so it has a way back instead of the tab bar.
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            WashiIconButton(WashiIcons.Back, "Back", vm::goHome)
            Text("Settings", style = MaterialTheme.typography.headlineMedium)
        }
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Section("Appearance")
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Theme", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (mode in ThemeMode.entries) {
                        WashiChip(selected = theme == mode, onClick = { onTheme(mode) }, label = mode.label)
                    }
                }
            }

            Section("Daily study")
            Text("Kana you can already read", style = MaterialTheme.typography.bodyLarge)
            KanaKnownChips(state.kana, vm::setKanaKnown)
            Text("Level you can already read", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 8.dp))
            // Raising the level fills in mastery, so it asks first; lowering it changes nothing.
            var pendingLevel by remember { mutableStateOf<Int?>(null) }
            ReadLevelChips(state.readLevel) { level ->
                val raises = level != ReadLevel.NONE && (state.readLevel == ReadLevel.NONE || level < state.readLevel)
                if (raises) pendingLevel = level else vm.setReadLevel(level)
            }
            pendingLevel?.let { level ->
                AlertDialog(
                    onDismissRequest = { pendingLevel = null },
                    title = { Text("Read at N$level") },
                    text = {
                        Text(
                            "N$level's ${state.levelCounts[level] ?: 0} words become known, and easier levels become mastered. " +
                                "Words you've studied keep their progress when it's higher.",
                        )
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            pendingLevel = null
                            vm.setReadLevel(level)
                        }) { Text("Fill in") }
                    },
                    dismissButton = { TextButton(onClick = { pendingLevel = null }) { Text("Cancel", color = MaterialTheme.colorScheme.onSurface) } },
                )
            }
            Text(
                "Raising it fills in the words of that level and the easier ones. Lowering it changes nothing else.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Stepper("New words per day", "${s.newPerDay}") { d ->
                update { s.copy(newPerDay = (s.newPerDay + d * 5).coerceIn(5, 100)) }
            }
            Text(
                "A suggestion, never a lock. Past it, stories with nothing new come first, and you can always learn more.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Stepper("Suggest catching up at", if (s.reviewNudge == 0) "Off" else "${s.reviewNudge} due") { d ->
                update { s.copy(reviewNudge = (s.reviewNudge + d * 10).coerceIn(0, 500)) }
            }
            Text(
                "Past this many due reviews, Home and Read put reviews first.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Stepper("Review limit per day", "${s.reviewCap}") { d ->
                update { s.copy(reviewCap = (s.reviewCap + d * 50).coerceIn(50, 2000)) }
            }
            Text(
                "Reviews past this wait for the next day, oldest first.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Stepper("New day starts at", String.format(Locale.ROOT, "%02d:00", s.rolloverHour)) { d ->
                update { s.copy(rolloverHour = (s.rolloverHour + d + 24) % 24) }
            }

            Section("Reminder")
            val r = state.reminder
            // Checked again on coming back, as from the phone's notification settings.
            val context = LocalContext.current
            var notificationsOn by remember { mutableStateOf(DailyReminder.notificationsOn(context)) }
            LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { notificationsOn = DailyReminder.notificationsOn(context) }
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
            if (r.enabled && !notificationsOn) {
                Text(
                    "Notifications are off for Jiwa Jiwa, so the reminder can't show.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    "Open notification settings ›",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier
                        .clickable {
                            context.startActivity(
                                Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                    .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName),
                            )
                        }
                        .padding(vertical = 4.dp),
                )
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
                "Save your progress, settings and stories to a file, or restore them from one.",
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
                    Text("Save backup")
                }
                SecondaryButton(onClick = { restoring = true }, modifier = Modifier.weight(1f)) { Text("Restore") }
            }
            state.lastBackup?.let {
                Text(
                    "Last backup: ${backupDate(it)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            state.backupMessage?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (state.backupFailed) MaterialTheme.colorScheme.error else MaterialTheme.washi.pine,
                )
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
                    dismissButton = { TextButton(onClick = { restoring = false }) { Text("Cancel", color = MaterialTheme.colorScheme.onSurface) } },
                )
            }

            Section("Reset")
            Text(
                "Erase all your progress, settings and stories, " +
                    "and start again from first-run setup.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            var resetting by remember { mutableStateOf(false) }
            SecondaryButton(onClick = { resetting = true }) {
                Text("Reset everything", color = MaterialTheme.colorScheme.error)
            }
            if (resetting) {
                AlertDialog(
                    onDismissRequest = { resetting = false },
                    title = { Text("Reset everything?") },
                    text = {
                        Text(
                            "All your cards, mastery, review history, settings and stories " +
                                "will be deleted. This can't be undone. Save a backup first if you might want them back.",
                        )
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            resetting = false
                            vm.resetAll()
                        }) { Text("Reset", color = MaterialTheme.colorScheme.error) }
                    },
                    dismissButton = { TextButton(onClick = { resetting = false }) { Text("Cancel", color = MaterialTheme.colorScheme.onSurface) } },
                )
            }

            Section("About")
            Text(
                "Word list and dictionary data:\n" + state.attribution.split("; ").joinToString("\n") { "• $it" } +
                    "\n\nThis app uses the JMdict/EDICT and KANJIDIC dictionary files. These files are the property of " +
                    "the Electronic Dictionary Research and Development Group, and are used in conformance with the " +
                    "Group's licence (https://www.edrdg.org/edrdg/licence.html). See " +
                    "https://www.edrdg.org/wiki/index.php/JMdict-EDICT_Dictionary_Project and " +
                    "https://www.edrdg.org/wiki/index.php/KANJIDIC_Project." +
                    "\n\nThe app's dictionary database is adapted from the sources above (entries are merged, " +
                    "filtered and ranked) and is licensed under CC BY-SA 4.0 (https://creativecommons.org/licenses/by-sa/4.0/)." +
                    "\n\nIcons: the settings cog is from Lucide, under the ISC License." +
                    "\n\nFonts: Zen Kaku Gothic New, Zen Old Mincho and Zen Maru Gothic, under the SIL Open Font License." +
                    "\n\nSoftware: AndroidX, Jetpack Compose, Media3 ExoPlayer, Kotlin and kotlinx.serialization, under the " +
                    "Apache License 2.0. Scheduling is a port of py-fsrs (Open Spaced Repetition), under the MIT License. " +
                    "The stories were split into words with Kuromoji (Atilika) and the IPADIC dictionary (NAIST)." +
                    "\n\nStory audio: AI voices, recorded with Fish Audio (https://fish.audio).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** "Oct 3 (16 days ago)", with the year when it isn't this one. */
private fun backupDate(at: Long): String {
    val zone = ZoneId.systemDefault()
    val date = Instant.ofEpochMilli(at).atZone(zone).toLocalDate()
    val today = LocalDate.now(zone)
    val pattern = if (date.year == today.year) "MMM d" else "MMM d, yyyy"
    val ago = when (val days = ChronoUnit.DAYS.between(date, today)) {
        0L -> "today"
        1L -> "yesterday"
        else -> "$days days ago"
    }
    return "${date.format(DateTimeFormatter.ofPattern(pattern))} ($ago)"
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
