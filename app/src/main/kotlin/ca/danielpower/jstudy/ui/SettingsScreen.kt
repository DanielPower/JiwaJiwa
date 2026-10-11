package ca.danielpower.jstudy.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ca.danielpower.jstudy.core.StudySettings
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(
    state: Screen.Settings,
    onChange: (StudySettings) -> Unit,
    onImport: () -> Unit,
    onBack: () -> Unit,
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
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Settings", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
            TextButton(onClick = onBack) { Text("Done") }
        }

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

        Section("Import")
        Text(
            "Bring in the mastery of words you've studied on Renshuu.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedButton(onClick = onImport) { Text("Import from Renshuu") }

        Section("About")
        Text(
            "Word list and dictionary data:\n" + state.attribution.split("; ").joinToString("\n") { "• $it" },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun Section(title: String) {
    HorizontalDivider(Modifier.padding(top = 16.dp))
    Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
}

@Composable
private fun Stepper(label: String, value: String, onStep: (Int) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        OutlinedButton(onClick = { onStep(-1) }) { Text("−") }
        Text(value, modifier = Modifier.widthIn(min = 64.dp), textAlign = TextAlign.Center)
        OutlinedButton(onClick = { onStep(1) }) { Text("+") }
    }
}
