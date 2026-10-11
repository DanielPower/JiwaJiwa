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

// "Washi": paper and sumi ink, with one vermilion seal colour. New, learning
// and review counts use primary, tertiary and secondary.

/** Shippori Mincho, for Japanese words and headings. */
val Mincho = FontFamily(
    Font(R.font.shippori_mincho_regular, FontWeight.Normal),
    Font(R.font.shippori_mincho_bold, FontWeight.Bold),
)

/** Zen Kaku Gothic New, for everything else. */
val Gothic = FontFamily(
    Font(R.font.zen_kaku_gothic_new_regular, FontWeight.Normal),
    Font(R.font.zen_kaku_gothic_new_bold, FontWeight.Bold),
)

private val Paper = Color(0xFFF4EFE4)
private val PaperRaised = Color(0xFFFBF8F1)
private val Ink = Color(0xFF1F1A16)

private val Light = lightColorScheme(
    primary = Color(0xFFB8381E),
    onPrimary = PaperRaised,
    primaryContainer = Color(0xFFF3D9CF),
    onPrimaryContainer = Color(0xFF5A1A0C),
    secondary = Color(0xFF2E4A6B),
    onSecondary = PaperRaised,
    secondaryContainer = Color(0xFFDCE3EC),
    onSecondaryContainer = Color(0xFF14263B),
    tertiary = Color(0xFF7A5A12),
    onTertiary = PaperRaised,
    tertiaryContainer = Color(0xFFEFE2C2),
    onTertiaryContainer = Color(0xFF3A2A05),
    error = Color(0xFF9C2A14),
    onError = PaperRaised,
    errorContainer = Color(0xFFF3D9CF),
    onErrorContainer = Color(0xFF5A1A0C),
    background = Paper,
    onBackground = Ink,
    surface = Paper,
    onSurface = Ink,
    surfaceVariant = Color(0xFFE6DDCC),
    onSurfaceVariant = Color(0xFF6B6259),
    surfaceTint = Color.Transparent,
    inverseSurface = Ink,
    inverseOnSurface = PaperRaised,
    inversePrimary = Color(0xFFE0644A),
    outline = Ink,
    outlineVariant = Color(0xFFDDD3C2),
    surfaceBright = PaperRaised,
    surfaceDim = Color(0xFFE9E2D4),
    surfaceContainerLowest = Color(0xFFFFFDF8),
    surfaceContainerLow = PaperRaised,
    surfaceContainer = PaperRaised,
    surfaceContainerHigh = Color(0xFFF0E9DC),
    surfaceContainerHighest = Color(0xFFE9E2D4),
)

private val InkDark = Color(0xFF171412)
private val InkRaised = Color(0xFF211D19)
private val PaperText = Color(0xFFEDE6D8)

private val Dark = darkColorScheme(
    primary = Color(0xFFE0644A),
    onPrimary = InkDark,
    primaryContainer = Color(0xFF4A1C11),
    onPrimaryContainer = Color(0xFFF6CFC3),
    secondary = Color(0xFF8FB0D6),
    onSecondary = InkDark,
    secondaryContainer = Color(0xFF233448),
    onSecondaryContainer = Color(0xFFD6E3F2),
    tertiary = Color(0xFFD6B25E),
    onTertiary = InkDark,
    tertiaryContainer = Color(0xFF3F3214),
    onTertiaryContainer = Color(0xFFF1E1B8),
    error = Color(0xFFF08A72),
    onError = InkDark,
    errorContainer = Color(0xFF4A1C11),
    onErrorContainer = Color(0xFFF6CFC3),
    background = InkDark,
    onBackground = PaperText,
    surface = InkDark,
    onSurface = PaperText,
    surfaceVariant = Color(0xFF2E2823),
    onSurfaceVariant = Color(0xFFA89E90),
    surfaceTint = Color.Transparent,
    inverseSurface = PaperText,
    inverseOnSurface = InkDark,
    inversePrimary = Color(0xFFB8381E),
    outline = Color(0xFFCFC6B8),
    outlineVariant = Color(0xFF3A332D),
    surfaceBright = Color(0xFF2A2520),
    surfaceDim = InkDark,
    surfaceContainerLowest = Color(0xFF120F0D),
    surfaceContainerLow = InkRaised,
    surfaceContainer = InkRaised,
    surfaceContainerHigh = Color(0xFF29241F),
    surfaceContainerHighest = Color(0xFF312B25),
)

private fun Typography.withFonts(): Typography {
    fun TextStyle.mincho() = copy(fontFamily = Mincho, fontWeight = FontWeight.Bold)
    fun TextStyle.gothic() = copy(fontFamily = Gothic)
    return copy(
        displayLarge = displayLarge.mincho(),
        displayMedium = displayMedium.mincho(),
        displaySmall = displaySmall.mincho(),
        headlineLarge = headlineLarge.mincho(),
        headlineMedium = headlineMedium.mincho(),
        headlineSmall = headlineSmall.mincho(),
        titleLarge = titleLarge.mincho(),
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

enum class ThemeMode(val label: String) {
    AUTO("Auto"),
    LIGHT("Light"),
    DARK("Dark"),
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
