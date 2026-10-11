package ca.danielpower.jstudy

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ca.danielpower.jstudy.ui.App
import ca.danielpower.jstudy.ui.AppViewModel
import ca.danielpower.jstudy.ui.JStudyTheme
import ca.danielpower.jstudy.ui.Screen

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            JStudyTheme {
                val screen by vm.screen.collectAsStateWithLifecycle()
                BackHandler(enabled = screen !is Screen.Home && screen !is Screen.Loading) { vm.goHome() }
                App(vm, screen)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Counts and due cards change with the clock, e.g. after the day rolls over.
        if (vm.screen.value is Screen.Home) vm.goHome()
    }
}
