package app.corkboard.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import app.corkboard.R
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import app.corkboard.data.ThemeMode

// Fallback palette for Android 10/11, where there is no wallpaper-derived scheme: cork and pin red.
private val LightFallback = lightColorScheme(
    primary = Color(0xFF8B5000),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDCBE),
    onPrimaryContainer = Color(0xFF2C1600),
    secondary = Color(0xFF725A42),
    secondaryContainer = Color(0xFFFEDDBE),
    onSecondaryContainer = Color(0xFF291806),
    tertiary = Color(0xFFB3261E),
    tertiaryContainer = Color(0xFFF9DEDC),
    onTertiaryContainer = Color(0xFF410E0B),
    background = Color(0xFFFFF8F4),
    surface = Color(0xFFFFF8F4),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFFEF1E7),
    surfaceContainer = Color(0xFFF9ECE1),
    surfaceContainerHigh = Color(0xFFF3E6DB),
    surfaceContainerHighest = Color(0xFFEDE0D6),
    onSurface = Color(0xFF201A15),
    onSurfaceVariant = Color(0xFF51443A),
    outline = Color(0xFF837468),
    outlineVariant = Color(0xFFD5C3B5),
)

private val DarkFallback = darkColorScheme(
    primary = Color(0xFFFFB870),
    onPrimary = Color(0xFF4A2800),
    primaryContainer = Color(0xFF693C00),
    onPrimaryContainer = Color(0xFFFFDCBE),
    secondary = Color(0xFFE1C1A4),
    secondaryContainer = Color(0xFF59422C),
    onSecondaryContainer = Color(0xFFFEDDBE),
    tertiary = Color(0xFFF2B8B5),
    tertiaryContainer = Color(0xFF8C1D18),
    onTertiaryContainer = Color(0xFFF9DEDC),
    background = Color(0xFF17130F),
    surface = Color(0xFF17130F),
    surfaceContainerLowest = Color(0xFF120D0A),
    surfaceContainerLow = Color(0xFF201A15),
    surfaceContainer = Color(0xFF241E19),
    surfaceContainerHigh = Color(0xFF2F2923),
    surfaceContainerHighest = Color(0xFF3A332D),
    onSurface = Color(0xFFEDE0D6),
    onSurfaceVariant = Color(0xFFD5C3B5),
    outline = Color(0xFF9D8E81),
    outlineVariant = Color(0xFF51443A),
)

@OptIn(ExperimentalTextApi::class)
private fun flex(weight: Int, round: Float): FontFamily = FontFamily(
    Font(
        R.font.google_sans_flex,
        weight = FontWeight(weight),
        variationSettings = FontVariation.Settings(
            FontVariation.weight(weight),
            // Roundness axis: 100 is the soft, friendly cut Material 3 Expressive uses for big type.
            FontVariation.Setting("ROND", round),
        ),
    ),
)

private val DisplayRegular = flex(400, 100f)
private val DisplayMedium = flex(500, 100f)
private val TextRegular = flex(400, 0f)
private val TextMedium = flex(500, 0f)

/** Prices and other figures that should stand out: heavy, round, and the same in every screen. */
val PriceFont: FontFamily = flex(650, 100f)

/** One typeface throughout: round for headings and titles, plain for reading text. */
private fun typography(): Typography {
    val base = Typography()
    fun TextStyle.display(medium: Boolean = false) = copy(fontFamily = if (medium) DisplayMedium else DisplayRegular, fontWeight = FontWeight(if (medium) 500 else 400))
    fun TextStyle.text(medium: Boolean = false) = copy(fontFamily = if (medium) TextMedium else TextRegular, fontWeight = FontWeight(if (medium) 500 else 400))
    return Typography(
        displayLarge = base.displayLarge.display(),
        displayMedium = base.displayMedium.display(),
        displaySmall = base.displaySmall.display(),
        headlineLarge = base.headlineLarge.display(),
        headlineMedium = base.headlineMedium.display(),
        headlineSmall = base.headlineSmall.display(),
        titleLarge = base.titleLarge.display(),
        titleMedium = base.titleMedium.display(medium = true),
        titleSmall = base.titleSmall.display(medium = true),
        bodyLarge = base.bodyLarge.text(),
        bodyMedium = base.bodyMedium.text(),
        bodySmall = base.bodySmall.text(),
        labelLarge = base.labelLarge.text(medium = true),
        labelMedium = base.labelMedium.text(medium = true),
        labelSmall = base.labelSmall.text(medium = true),
    )
}

@Composable
fun isDark(mode: ThemeMode): Boolean = when (mode) {
    ThemeMode.System -> isSystemInDarkTheme()
    ThemeMode.Light -> false
    ThemeMode.Dark -> true
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun CorkboardTheme(dark: Boolean, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val scheme: ColorScheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> DarkFallback
        else -> LightFallback
    }
    MaterialExpressiveTheme(colorScheme = scheme, motionScheme = MotionScheme.expressive(), typography = remember { typography() }, content = content)
}
