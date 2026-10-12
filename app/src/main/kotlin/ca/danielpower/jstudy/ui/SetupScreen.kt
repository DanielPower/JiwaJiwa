package ca.danielpower.jstudy.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** First run: pick a starting level, and say how well you know the levels below it. */
@Composable
fun SetupScreen(state: Screen.Setup, vm: AppViewModel) {
    val below = (5 downTo state.startLevel + 1).toList()
    val belowCount = below.sumOf { state.levelCounts[it] ?: 0 }
    val belowNames = below.joinToString(" and ") { "N$it" }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("ようこそ", fontFamily = Mincho, fontWeight = FontWeight.Bold, fontSize = 36.sp)
        Text(
            "Where do you want to start? New words come from this level first, most common first, " +
                "then the harder levels, then any easier ones.",
            style = MaterialTheme.typography.bodyLarge,
        )

        SectionLabel("Starting level", Modifier.padding(top = 8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (level in 5 downTo 1) {
                WashiChip(
                    selected = state.startLevel == level,
                    onClick = { vm.editSetup { it.copy(startLevel = level) } },
                    label = "N$level",
                )
            }
        }
        Text(
            "${state.levelCounts[state.startLevel] ?: 0} words at N${state.startLevel}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (below.isNotEmpty()) {
            SectionLabel("The $belowCount words in $belowNames", Modifier.padding(top = 8.dp))
            val options = listOf(
                Triple(0, "Learn later", "They come after the harder levels, as new words."),
                Triple(state.rules.hideAt, "Known", "Reviewed over the next week, furigana hidden but tappable."),
                Triple(state.rules.offAt, "Mastered", "Reviewed over the next month, no furigana."),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for ((mastery, label, _) in options) {
                    WashiChip(
                        selected = state.lowerMastery == mastery,
                        onClick = { vm.editSetup { it.copy(lowerMastery = mastery) } },
                        label = label,
                    )
                }
            }
            Text(
                options.first { it.first == state.lowerMastery }.third +
                    if (state.lowerMastery > 0) " Any you miss go back to learning." else "",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Text(
            "You can change the starting level later in Settings, and mark single words from the dictionary.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
        PrimaryButton(onClick = vm::finishSetup, modifier = Modifier.fillMaxWidth().padding(top = 16.dp).height(58.dp)) {
            Text("Start", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
    }
}
