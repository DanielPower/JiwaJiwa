package ca.danielpower.jstudy.ui

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
import androidx.compose.material3.HorizontalDivider
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
import ca.danielpower.jstudy.core.StudySettings
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(
    state: Screen.Settings,
    onChange: (StudySettings) -> Unit,
    onSaveClaudeKey: (String) -> Unit,
    onImport: () -> Unit,
    onStoryVertical: (Boolean) -> Unit,
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

        Section("Daily study")
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
