package com.useconatus.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.useconatus.app.R

val HankenGrotesk = FontFamily(
    listOf(400, 500, 600, 700).map { weight ->
        Font(R.font.hanken_grotesk, FontWeight(weight), variationSettings = FontVariation.Settings(FontVariation.weight(weight)))
    },
)

private fun style(size: Int, line: Int, weight: FontWeight = FontWeight.Normal) =
    TextStyle(fontFamily = HankenGrotesk, fontWeight = weight, fontSize = size.sp, lineHeight = line.sp, letterSpacing = 0.sp)

/** The type scale: the web app's Tailwind sizes, with no letter-spacing. */
val ConatusTypography = Typography(
    displayLarge = style(48, 52, FontWeight.SemiBold),
    displayMedium = style(40, 44, FontWeight.SemiBold),
    displaySmall = style(34, 40, FontWeight.SemiBold),
    headlineLarge = style(30, 36, FontWeight.SemiBold),
    headlineMedium = style(28, 34, FontWeight.SemiBold),
    headlineSmall = style(24, 30, FontWeight.SemiBold),
    titleLarge = style(20, 28, FontWeight.SemiBold),
    titleMedium = style(16, 24, FontWeight.SemiBold),
    titleSmall = style(14, 20, FontWeight.Bold),
    bodyLarge = style(16, 24),
    bodyMedium = style(14, 20),
    bodySmall = style(13, 18),
    labelLarge = style(14, 20, FontWeight.Medium),
    labelMedium = style(12, 16, FontWeight.Medium),
    labelSmall = style(12, 16),
)

/** Buttons and inputs use 8dp, cards 10dp, sheets 14dp: the web's --radius is 10px. */
val ConatusShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(10.dp),
    large = RoundedCornerShape(14.dp),
    extraLarge = RoundedCornerShape(14.dp),
)

val SheetShape = RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp)

val LocalConatusColors = staticCompositionLocalOf { LightConatusColors }

@Composable
fun ConatusTheme(dark: Boolean, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (dark) DarkColors else LightColors,
        typography = ConatusTypography,
        shapes = ConatusShapes,
    ) {
        CompositionLocalProvider(LocalConatusColors provides if (dark) DarkConatusColors else LightConatusColors, content = content)
    }
}

/** Sheets use the web's popover colour: white on light, the card grey on dark. */
@Composable
fun sheetColor() = if (LocalConatusColors.current.dark) MaterialTheme.colorScheme.surfaceContainer else MaterialTheme.colorScheme.surface
