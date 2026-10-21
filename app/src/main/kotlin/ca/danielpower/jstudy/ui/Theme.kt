package ca.danielpower.jstudy.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ca.danielpower.jstudy.R

// "Washi": paper and sumi ink, with one vermilion seal colour. New, learning
// and review counts use primary (vermilion), tertiary (ochre) and verdigris;
// mastery's stages are ochre, verdigris and pine (see masteryColor).

/** Zen Kaku Gothic New, a clean sans serif, for Japanese and UI text alike. */
val Gothic = FontFamily(
    Font(R.font.zen_kaku_gothic_new_regular, FontWeight.Normal),
    Font(R.font.zen_kaku_gothic_new_bold, FontWeight.Bold),
)

/** Zen Old Mincho, a serif, offered only for stories. Regular only: a note's bold 注 is in [Gothic]. */
val Mincho = FontFamily(Font(R.font.zen_old_mincho_regular, FontWeight.Normal))

/** Zen Maru Gothic, a rounded sans serif, offered only for stories. Regular only, like [Mincho]. */
val Maru = FontFamily(Font(R.font.zen_maru_gothic_regular, FontWeight.Normal))

private val Paper = Color(0xFFF6ECDA)
private val PaperRaised = Color(0xFFFCF6EA)
private val Ink = Color(0xFF2A1F17)

private val Light = lightColorScheme(
    primary = Color(0xFFB5381C),
    onPrimary = PaperRaised,
    primaryContainer = Color(0xFFF5D8C8),
    onPrimaryContainer = Color(0xFF5A1A0C),
    secondary = Color(0xFF2E5B9E),
    onSecondary = PaperRaised,
    secondaryContainer = Color(0xFFDDE1E6),
    onSecondaryContainer = Color(0xFF14263B),
    tertiary = Color(0xFF77560F),
    onTertiary = PaperRaised,
    tertiaryContainer = Color(0xFFF1E0BC),
    onTertiaryContainer = Color(0xFF3A2A05),
    error = Color(0xFF8E2643),
    onError = PaperRaised,
    errorContainer = Color(0xFFF3D6DC),
    onErrorContainer = Color(0xFF4A1020),
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
    error = Color(0xFFF1859A),
    onError = InkDark,
    errorContainer = Color(0xFF4E1A26),
    onErrorContainer = Color(0xFFF8D9DF),
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

/** The Washi colours Material's scheme has no slot for. Read them as `MaterialTheme.washi`. */
@Immutable
class WashiColors(
    /** Known words (mastery 40 to 69), the 復 review count and reviews due ahead. */
    val verdigris: Color,
    /** Mastered words (70 and up), Right, and finished. */
    val pine: Color,
    /** Notes: the 注 marker and its tag. */
    val plum: Color,
)

private val LightWashi = WashiColors(
    verdigris = Color(0xFF356160),
    pine = Color(0xFF3D6936),
    plum = Color(0xFF7A3A70),
)

private val DarkWashi = WashiColors(
    verdigris = Color(0xFF7CA9A5),
    pine = Color(0xFF97C485),
    plum = Color(0xFFD0A0CB),
)

private val LocalWashiColors = staticCompositionLocalOf { LightWashi }

val MaterialTheme.washi: WashiColors
    @Composable
    @ReadOnlyComposable
    get() = LocalWashiColors.current

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
    val dark = mode.isDark()
    CompositionLocalProvider(LocalWashiColors provides if (dark) DarkWashi else LightWashi) {
        MaterialTheme(
            colorScheme = if (dark) Dark else Light,
            typography = WashiTypography,
            shapes = WashiShapes,
            content = content,
        )
    }
}
