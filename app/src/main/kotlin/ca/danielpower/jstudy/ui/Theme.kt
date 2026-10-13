package ca.danielpower.jstudy.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ca.danielpower.jstudy.R
import ca.danielpower.jstudy.core.UiLabel

// "Washi": paper and sumi ink, with one vermilion seal colour. New, learning
// and review counts use primary, tertiary and secondary.

/** Zen Kaku Gothic New, a clean sans serif, for Japanese and UI text alike. */
val Gothic = FontFamily(
    Font(R.font.zen_kaku_gothic_new_regular, FontWeight.Normal),
    Font(R.font.zen_kaku_gothic_new_bold, FontWeight.Bold),
)

/** Zen Old Mincho, a serif, offered only for stories. */
val Mincho = FontFamily(
    Font(R.font.zen_old_mincho_regular, FontWeight.Normal),
    Font(R.font.zen_old_mincho_bold, FontWeight.Bold),
)

/** Zen Maru Gothic, a rounded sans serif, offered only for stories. */
val Maru = FontFamily(
    Font(R.font.zen_maru_gothic_regular, FontWeight.Normal),
    Font(R.font.zen_maru_gothic_bold, FontWeight.Bold),
)

private val Paper = Color(0xFFF6ECDA)
private val PaperRaised = Color(0xFFFCF6EA)
private val Ink = Color(0xFF2A1F17)

private val Light = lightColorScheme(
    primary = Color(0xFFB5381C),
    onPrimary = PaperRaised,
    primaryContainer = Color(0xFFF5D8C8),
    onPrimaryContainer = Color(0xFF5A1A0C),
    secondary = Color(0xFF2D4866),
    onSecondary = PaperRaised,
    secondaryContainer = Color(0xFFDDE1E6),
    onSecondaryContainer = Color(0xFF14263B),
    tertiary = Color(0xFF77560F),
    onTertiary = PaperRaised,
    tertiaryContainer = Color(0xFFF1E0BC),
    onTertiaryContainer = Color(0xFF3A2A05),
    error = Color(0xFF9A2912),
    onError = PaperRaised,
    errorContainer = Color(0xFFF5D8C8),
    onErrorContainer = Color(0xFF5A1A0C),
    background = Paper,
    onBackground = Ink,
    surface = Paper,
    onSurface = Ink,
    surfaceVariant = Color(0xFFECDDC4),
    onSurfaceVariant = Color(0xFF6A5848),
    surfaceTint = Color.Transparent,
    inverseSurface = Ink,
    inverseOnSurface = PaperRaised,
    inversePrimary = Color(0xFFE8704F),
    outline = Ink,
    outlineVariant = Color(0xFFE3D3B9),
    surfaceBright = PaperRaised,
    surfaceDim = Color(0xFFECE1CD),
    surfaceContainerLowest = Color(0xFFFFFBF3),
    surfaceContainerLow = PaperRaised,
    surfaceContainer = PaperRaised,
    surfaceContainerHigh = Color(0xFFF2E7D4),
    surfaceContainerHighest = Color(0xFFECE1CD),
)

private val InkDark = Color(0xFF1E1711)
private val InkRaised = Color(0xFF2A2119)
private val PaperText = Color(0xFFF1E5CF)

private val Dark = darkColorScheme(
    primary = Color(0xFFE8704F),
    onPrimary = InkDark,
    primaryContainer = Color(0xFF52200F),
    onPrimaryContainer = Color(0xFFF7D2C2),
    secondary = Color(0xFF93B2D3),
    onSecondary = InkDark,
    secondaryContainer = Color(0xFF263447),
    onSecondaryContainer = Color(0xFFD6E3F2),
    tertiary = Color(0xFFDDB862),
    onTertiary = InkDark,
    tertiaryContainer = Color(0xFF433416),
    onTertiaryContainer = Color(0xFFF3E2B6),
    error = Color(0xFFF2906F),
    onError = InkDark,
    errorContainer = Color(0xFF52200F),
    onErrorContainer = Color(0xFFF7D2C2),
    background = InkDark,
    onBackground = PaperText,
    surface = InkDark,
    onSurface = PaperText,
    surfaceVariant = Color(0xFF372B21),
    onSurfaceVariant = Color(0xFFB8A68F),
    surfaceTint = Color.Transparent,
    inverseSurface = PaperText,
    inverseOnSurface = InkDark,
    inversePrimary = Color(0xFFB5381C),
    outline = Color(0xFFD4C6B0),
    outlineVariant = Color(0xFF433629),
    surfaceBright = Color(0xFF33291F),
    surfaceDim = InkDark,
    surfaceContainerLowest = Color(0xFF18120D),
    surfaceContainerLow = InkRaised,
    surfaceContainer = InkRaised,
    surfaceContainerHigh = Color(0xFF30261D),
    surfaceContainerHighest = Color(0xFF382D23),
)

private fun Typography.withFonts(): Typography {
    fun TextStyle.heading() = copy(fontFamily = Gothic, fontWeight = FontWeight.Bold)
    fun TextStyle.gothic() = copy(fontFamily = Gothic)
    return copy(
        displayLarge = displayLarge.heading(),
        displayMedium = displayMedium.heading(),
        displaySmall = displaySmall.heading(),
        headlineLarge = headlineLarge.heading(),
        headlineMedium = headlineMedium.heading(),
        headlineSmall = headlineSmall.heading(),
        titleLarge = titleLarge.heading(),
        titleMedium = titleMedium.gothic().copy(fontWeight = FontWeight.Bold),
        titleSmall = titleSmall.gothic().copy(fontWeight = FontWeight.Bold),
        bodyLarge = bodyLarge.gothic(),
        bodyMedium = bodyMedium.gothic(),
        bodySmall = bodySmall.gothic(),
        labelLarge = labelLarge.gothic().copy(fontWeight = FontWeight.Bold),
        labelMedium = labelMedium.gothic(),
        labelSmall = labelSmall.gothic(),
    )
}

private val WashiTypography = Typography().withFonts()

/** Small, square-ish corners, like cut paper. */
val WashiShape = RoundedCornerShape(4.dp)

private val WashiShapes = Shapes(
    extraSmall = RoundedCornerShape(2.dp),
    small = WashiShape,
    medium = WashiShape,
    large = RoundedCornerShape(8.dp),
    extraLarge = RoundedCornerShape(8.dp),
)

enum class ThemeMode(val label: UiLabel) {
    AUTO(UiLabel("Auto", "自動")),
    LIGHT(UiLabel("Light", "明るい")),
    DARK(UiLabel("Dark", "暗い")),
}

@Composable
fun ThemeMode.isDark(): Boolean = when (this) {
    ThemeMode.AUTO -> isSystemInDarkTheme()
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

@Composable
fun JStudyTheme(mode: ThemeMode, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (mode.isDark()) Dark else Light,
        typography = WashiTypography,
        shapes = WashiShapes,
        content = content,
    )
}
