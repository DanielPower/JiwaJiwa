package ca.danielpower.jstudy.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import ca.danielpower.jstudy.core.ImportPlan

@Composable
fun RenshuuImportScreen(step: ImportStep, vm: AppViewModel) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Import from Renshuu", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
            if (step !is ImportStep.Applying) TextButton(onClick = vm::closeImport) { Text("Close") }
        }
        when (step) {
            is ImportStep.EnterKey -> EnterKey(step.error, vm::fetchRenshuu)
            is ImportStep.Fetching -> {
                Text(
                    if (step.pages > 0) "Downloading your words, page ${step.page} of ${step.pages}…" else "Connecting to Renshuu…",
                    style = MaterialTheme.typography.bodyLarge,
                )
                if (step.pages > 0) {
                    LinearProgressIndicator(progress = { step.page.toFloat() / step.pages }, modifier = Modifier.fillMaxWidth())
                } else {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }
            is ImportStep.Preview -> Preview(step.plan, onImport = vm::applyImport, onCancel = vm::closeImport)
            ImportStep.Applying -> Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator()
                Text("Importing…", style = MaterialTheme.typography.bodyLarge)
            }
            is ImportStep.Done -> {
                Text(
                    if (step.updated == 1) "Imported 1 word." else "Imported ${step.updated} words.",
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    "Their first reviews are spread over the coming days and weeks.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                PrimaryButton(onClick = vm::goHome) { Text("Done") }
            }
        }
    }
}

@Composable
private fun EnterKey(error: String?, onSubmit: (String) -> Unit) {
    var key by rememberSaveable { mutableStateOf("") }
    Text(
        "Paste your API key from Renshuu's settings. Jiwa Jiwa uses it once to read the words you've studied " +
            "and their mastery, and doesn't keep it.",
        style = MaterialTheme.typography.bodyMedium,
    )
    OutlinedTextField(
        value = key,
        onValueChange = { key = it },
        label = { Text("Renshuu API key") },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Go),
        keyboardActions = KeyboardActions(onGo = { onSubmit(key) }),
        isError = error != null,
        supportingText = if (error != null) { { Text(error) } } else null,
        modifier = Modifier.fillMaxWidth(),
    )
    PrimaryButton(onClick = { onSubmit(key) }, enabled = key.isNotBlank()) { Text("Check my words") }
    Text(
        "You'll see what would change before anything is imported.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun Preview(plan: ImportPlan, onImport: () -> Unit, onCancel: () -> Unit) {
    val matched = plan.renshuuWords - plan.notInJstudy
    Text(
        "Renshuu has ${plan.renshuuWords} words you've studied. $matched of them are in Jiwa Jiwa's dictionary.",
        style = MaterialTheme.typography.bodyLarge,
    )
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Line("New to Jiwa Jiwa, will start at Renshuu's mastery", plan.newWords)
        Line("Already started, mastery will go up", plan.raised)
        Line("Left as is: Jiwa Jiwa's mastery is as high, or Renshuu's is 0", plan.unchanged)
        Line("Left as is: suspended in Jiwa Jiwa", plan.suspended)
        Line("Not in Jiwa Jiwa's dictionary", plan.notInJstudy)
    }
    if (plan.updates.isEmpty()) {
        Text("There's nothing to import.", style = MaterialTheme.typography.bodyLarge)
        PrimaryButton(onClick = onCancel) { Text("Close") }
    } else {
        Text(
            "Each word is marked known at its mastery, as \"Mark known\" on a word's page does, and gets reviews to match.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PrimaryButton(onClick = onImport) { Text("Import ${plan.updates.size} words") }
            TextButton(onClick = onCancel) { Text("Cancel") }
        }
    }
}

@Composable
private fun Line(label: String, count: Int) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text("$count", style = MaterialTheme.typography.bodyMedium)
    }
}
