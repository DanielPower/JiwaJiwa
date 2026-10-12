package ca.danielpower.jstudy.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import ca.danielpower.jstudy.core.UiLabel
import ca.danielpower.jstudy.core.UiLanguage

/** The app's language, and how to look up a label seen for the first time. */
class AppLanguage(val language: UiLanguage, val lookUp: (UiLabel) -> Unit)

val LocalAppLanguage = compositionLocalOf { AppLanguage(UiLanguage.ENGLISH) {} }

/**
 * [label] in English, or in Japanese once you've mastered every word in it.
 * Write UI text through this, so the app turns Japanese as you learn.
 */
@Composable
fun ui(label: UiLabel): String {
    val app = LocalAppLanguage.current
    if (!app.language.knows(label)) SideEffect { app.lookUp(label) }
    return app.language.text(label)
}

/** UI text in English and Japanese. Name [words] only where reading the Japanese gets them wrong. */
@Composable
fun ui(english: String, japanese: String, vararg words: String): String = ui(UiLabel(english, japanese, words.asList()))
