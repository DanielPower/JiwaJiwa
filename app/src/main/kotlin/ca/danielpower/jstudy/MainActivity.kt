package ca.danielpower.jstudy

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ca.danielpower.jstudy.ui.App
import ca.danielpower.jstudy.ui.AppViewModel
import ca.danielpower.jstudy.ui.JStudyTheme
import ca.danielpower.jstudy.ui.Screen
import ca.danielpower.jstudy.ui.isDark

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val theme by vm.theme.collectAsStateWithLifecycle()
            val dark = theme.isDark()
            // Status and navigation bar icons follow the app's theme, not just the system's.
            LaunchedEffect(dark) {
                val style = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark }
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
            }
            JStudyTheme(theme) {
                val screen by vm.screen.collectAsStateWithLifecycle()
                val firstPage = (screen as? Screen.Setup)?.let { it.page == it.pages.first() } == true
                BackHandler(enabled = screen !is Screen.Home && screen !is Screen.Loading && !firstPage) { vm.back() }
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
