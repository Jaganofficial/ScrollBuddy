package com.scrollbuddy.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.scrollbuddy.R

// Taken from the character's checked shirt: its brick red for whatever can be pressed, its navy
// for what is chosen or counted, and the brown of his bag for the rest, on warm paper. At night
// the paper turns to the navy of the curtain the nudge draws across the screen.

private val LightColors = lightColorScheme(
    primary = Color(0xFFB0382D),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDAD4),
    onPrimaryContainer = Color(0xFF410001),
    secondary = Color(0xFF2F3E6E),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFDCE1FF),
    onSecondaryContainer = Color(0xFF121C40),
    tertiary = Color(0xFF7A5635),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFDCBE),
    onTertiaryContainer = Color(0xFF2B1700),
    background = Color(0xFFFFF8F4),
    onBackground = Color(0xFF211A18),
    surface = Color(0xFFFFF8F4),
    onSurface = Color(0xFF211A18),
    surfaceVariant = Color(0xFFF2E2DC),
    onSurfaceVariant = Color(0xFF53433F),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFFFF1EB),
    surfaceContainer = Color(0xFFFAEBE4),
    surfaceContainerHigh = Color(0xFFF4E5DE),
    surfaceContainerHighest = Color(0xFFEEDFD8),
    outline = Color(0xFF85736E),
    outlineVariant = Color(0xFFD8C2BC),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFFFB4A8),
    onPrimary = Color(0xFF690004),
    primaryContainer = Color(0xFF8E2018),
    onPrimaryContainer = Color(0xFFFFDAD4),
    secondary = Color(0xFFB7C4FF),
    onSecondary = Color(0xFF16275A),
    secondaryContainer = Color(0xFF2F3E6E),
    onSecondaryContainer = Color(0xFFDCE1FF),
    tertiary = Color(0xFFEBBD92),
    onTertiary = Color(0xFF45290C),
    tertiaryContainer = Color(0xFF5F3F20),
    onTertiaryContainer = Color(0xFFFFDCBE),
    background = Color(0xFF0E1322),
    onBackground = Color(0xFFE3E2EC),
    surface = Color(0xFF0E1322),
    onSurface = Color(0xFFE3E2EC),
    surfaceVariant = Color(0xFF43475A),
    onSurfaceVariant = Color(0xFFC5C6DA),
    surfaceContainerLowest = Color(0xFF090D19),
    surfaceContainerLow = Color(0xFF161B2B),
    surfaceContainer = Color(0xFF1A2031),
    surfaceContainerHigh = Color(0xFF242A3C),
    surfaceContainerHighest = Color(0xFF2F3548),
    outline = Color(0xFF8F90A3),
    outlineVariant = Color(0xFF43475A),
)

// Rounder than the Material defaults throughout
private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

// The app carries its own copy of Roboto, one file that holds every weight. Some phones swap
// the system font for one whose weight the phone decides for itself, and on those every
// heading came out as thin as the body text
@OptIn(ExperimentalTextApi::class)
private fun roboto(weight: FontWeight) = Font(
    resId = R.font.roboto,
    weight = weight,
    variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
)

private val Roboto = FontFamily(
    roboto(FontWeight.Normal),
    roboto(FontWeight.Medium),
    roboto(FontWeight.SemiBold),
    roboto(FontWeight.Bold),
)

private fun TextStyle.inRoboto(weight: FontWeight? = fontWeight) = copy(fontFamily = Roboto, fontWeight = weight)

// Headings carry more weight than the Material defaults; sizes are unchanged
private val AppTypography = Typography().run {
    copy(
        displayLarge = displayLarge.inRoboto(),
        displayMedium = displayMedium.inRoboto(),
        displaySmall = displaySmall.inRoboto(),
        headlineLarge = headlineLarge.inRoboto(FontWeight.Bold),
        headlineMedium = headlineMedium.inRoboto(FontWeight.Bold),
        headlineSmall = headlineSmall.inRoboto(FontWeight.Bold),
        titleLarge = titleLarge.inRoboto(FontWeight.Bold),
        titleMedium = titleMedium.inRoboto(FontWeight.SemiBold),
        titleSmall = titleSmall.inRoboto(FontWeight.SemiBold),
        bodyLarge = bodyLarge.inRoboto(),
        bodyMedium = bodyMedium.inRoboto(),
        bodySmall = bodySmall.inRoboto(),
        labelLarge = labelLarge.inRoboto(FontWeight.SemiBold),
        labelMedium = labelMedium.inRoboto(),
        labelSmall = labelSmall.inRoboto(),
    )
}

/** Used by the activity and by the overlay windows, which have no activity theme behind them. */
@Composable
fun ScrollBuddyTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        shapes = AppShapes,
        typography = AppTypography,
        content = content,
    )
}
