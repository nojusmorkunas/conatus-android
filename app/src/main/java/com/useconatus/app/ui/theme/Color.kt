package com.useconatus.app.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// Colour tokens from the web app's globals.css (Tailwind v4 neutral).

private object Neutral {
    val N0 = Color(0xFFFFFFFF)
    val N50 = Color(0xFFFAFAFA)
    val N100 = Color(0xFFF5F5F5)
    val N200 = Color(0xFFE5E5E5)
    val N300 = Color(0xFFD4D4D4)
    val N400 = Color(0xFFA1A1A1)
    val N500 = Color(0xFF737373)
    val N600 = Color(0xFF525252)
    val N700 = Color(0xFF404040)
    val N800 = Color(0xFF262626)
    val N900 = Color(0xFF171717)
    val N950 = Color(0xFF0A0A0A)
}

val LightColors: ColorScheme = lightColorScheme(
    primary = Neutral.N900, onPrimary = Neutral.N50,
    primaryContainer = Neutral.N200, onPrimaryContainer = Neutral.N900,
    secondary = Neutral.N700, onSecondary = Neutral.N50,
    secondaryContainer = Neutral.N100, onSecondaryContainer = Neutral.N900,
    tertiary = Neutral.N700, onTertiary = Neutral.N50,
    background = Neutral.N0, onBackground = Neutral.N950,
    surface = Neutral.N0, onSurface = Neutral.N950,
    surfaceVariant = Neutral.N100, onSurfaceVariant = Neutral.N500,
    surfaceContainerLowest = Neutral.N0, surfaceContainerLow = Neutral.N50,
    surfaceContainer = Neutral.N100, surfaceContainerHigh = Color(0xFFEEEEEE), surfaceContainerHighest = Neutral.N200,
    surfaceBright = Neutral.N0, surfaceDim = Neutral.N200,
    inverseSurface = Neutral.N900, inverseOnSurface = Neutral.N50, inversePrimary = Neutral.N200,
    outline = Neutral.N400, outlineVariant = Neutral.N200,
    error = Color(0xFFE7000B), onError = Neutral.N0,
    scrim = Color.Black,
)

val DarkColors: ColorScheme = darkColorScheme(
    primary = Neutral.N200, onPrimary = Neutral.N900,
    primaryContainer = Neutral.N800, onPrimaryContainer = Neutral.N50,
    secondary = Neutral.N300, onSecondary = Neutral.N900,
    secondaryContainer = Neutral.N800, onSecondaryContainer = Neutral.N50,
    tertiary = Neutral.N300, onTertiary = Neutral.N900,
    background = Neutral.N950, onBackground = Neutral.N50,
    surface = Neutral.N950, onSurface = Neutral.N50,
    surfaceVariant = Neutral.N800, onSurfaceVariant = Neutral.N400,
    surfaceContainerLowest = Color.Black, surfaceContainerLow = Color(0xFF111111),
    surfaceContainer = Neutral.N900, surfaceContainerHigh = Color(0xFF1F1F1F), surfaceContainerHighest = Neutral.N800,
    surfaceBright = Neutral.N800, surfaceDim = Neutral.N950,
    inverseSurface = Neutral.N50, inverseOnSurface = Neutral.N900, inversePrimary = Neutral.N900,
    outline = Neutral.N600, outlineVariant = Color(0xFF2A2A2A),
    error = Color(0xFFFF6467), onError = Neutral.N950,
    scrim = Color.Black,
)

/** Colours with a meaning that Material has no role for. Text meets 4.5:1, strokes 3:1. */
data class ConatusColors(
    val overdue: Color,
    val today: Color,
    val tomorrow: Color,
    val muted: Color,
    val dark: Boolean,
)

val LightConatusColors = ConatusColors(Color(0xFFE7000B), Color(0xFF008236), Color(0xFFCA3500), Neutral.N500, dark = false)
val DarkConatusColors = ConatusColors(Color(0xFFFF6467), Color(0xFF05DF72), Color(0xFFFF8904), Neutral.N400, dark = true)

/**
 * Priority stroke colour, 1 (most urgent) to 4 (none). The web uses Tailwind 500s; orange-500
 * and the faded P4 grey fall below 3:1 on white, so light mode uses orange-600 and a darker grey.
 */
fun priorityColor(priority: Int, dark: Boolean): Color = when (priority) {
    1 -> Color(0xFFFB2C36)
    2 -> if (dark) Color(0xFFFF6900) else Color(0xFFF54900)
    3 -> Color(0xFF2B7FFF)
    else -> if (dark) Color(0xFF6B6B6B) else Color(0xFF949494)
}

/** The server's 13 project and label colours. */
enum class Hue(val dot: Color, private val lightText: Color, private val darkText: Color) {
    Gray(Color(0xFF9F9FA9), Color(0xFF52525C), Color(0xFF9F9FA9)),
    Red(Color(0xFFFB2C36), Color(0xFFC10007), Color(0xFFFF6467)),
    Orange(Color(0xFFFF6900), Color(0xFFCA3500), Color(0xFFFF8904)),
    Amber(Color(0xFFFE9A00), Color(0xFFBB4D00), Color(0xFFFFB900)),
    Yellow(Color(0xFFFDC700), Color(0xFFA65F00), Color(0xFFFDC700)),
    Lime(Color(0xFF7CCF00), Color(0xFF497D00), Color(0xFF9AE600)),
    Green(Color(0xFF00C950), Color(0xFF008236), Color(0xFF05DF72)),
    Teal(Color(0xFF00BBA7), Color(0xFF00786F), Color(0xFF00D5BE)),
    Cyan(Color(0xFF00B8DB), Color(0xFF007595), Color(0xFF00D3F2)),
    Blue(Color(0xFF2B7FFF), Color(0xFF1447E6), Color(0xFF51A2FF)),
    Indigo(Color(0xFF615FFF), Color(0xFF432DD7), Color(0xFF7C86FF)),
    Purple(Color(0xFFAD46FF), Color(0xFF8200DB), Color(0xFFC27AFF)),
    Pink(Color(0xFFF6339A), Color(0xFFC6005C), Color(0xFFFB64B6)),
    ;

    /** Coloured text: the 700 shade on light and the 400 shade on dark, so it reads at 4.5:1. */
    fun text(dark: Boolean) = if (dark) darkText else lightText

    companion object {
        /** Unknown names, from a newer server, fall back to grey. */
        fun of(name: String?): Hue = entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: Gray
    }
}
