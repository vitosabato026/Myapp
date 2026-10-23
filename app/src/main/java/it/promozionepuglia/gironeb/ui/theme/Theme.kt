package it.promozionepuglia.gironeb.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import it.promozionepuglia.gironeb.R
import it.promozionepuglia.gironeb.data.ThemeMode

/** Colori dell'app, uguali alle anteprime approvate. */
@Immutable
data class AppColors(
    val bg: Color,
    val surface: Color,
    val nav: Color,
    val line: Color,
    val divider: Color,
    val text: Color,
    val text2: Color,
    val muted: Color,
    val accent: Color,
    val onAccent: Color,
    val accent2: Color,
    val isDark: Boolean,
) {
    val win = Color(0xFF1F7A45)
    val draw = Color(0xFF6B7A90)
    val loss = Color(0xFFC0392B)
    val zonePromotion = Color(0xFF2E9E5B)
    val zonePlayoff get() = accent2
    val zonePlayout get() = accent
    val zoneRelegation = Color(0xFFE5484D)
}

val LightAppColors = AppColors(
    bg = Color(0xFFF4F6FA), surface = Color(0xFFFFFFFF), nav = Color(0xFFFFFFFF),
    line = Color(0xFFDCE3EE), divider = Color(0xFFE8EDF4),
    text = Color(0xFF142033), text2 = Color(0xFF34445C), muted = Color(0xFF5A6B82),
    accent = Color(0xFFA65F00), onAccent = Color(0xFFFFFFFF), accent2 = Color(0xFF1F6FB2),
    isDark = false,
)

val DarkAppColors = AppColors(
    bg = Color(0xFF0F1A2B), surface = Color(0xFF172741), nav = Color(0xFF131F33),
    line = Color(0xFF24385A), divider = Color(0xFF1C2D49),
    text = Color(0xFFEEF2F8), text2 = Color(0xFFC9D4E3), muted = Color(0xFF9FB0C8),
    accent = Color(0xFFF2B134), onAccent = Color(0xFF0F1A2B), accent2 = Color(0xFF5AB0FF),
    isDark = true,
)

val LocalAppColors = staticCompositionLocalOf { LightAppColors }

val GoogleSans = FontFamily(
    Font(R.font.google_sans_regular, FontWeight.Normal),
    Font(R.font.google_sans_medium, FontWeight.Medium),
    Font(R.font.google_sans_bold, FontWeight.Bold),
)

private val AppTypography = Typography().run {
    fun TextStyle.gs() = copy(fontFamily = GoogleSans)
    Typography(
        displayLarge = displayLarge.gs(), displayMedium = displayMedium.gs(), displaySmall = displaySmall.gs(),
        headlineLarge = headlineLarge.gs(), headlineMedium = headlineMedium.gs(),
        headlineSmall = headlineSmall.gs().copy(fontWeight = FontWeight.Bold),
        titleLarge = titleLarge.gs().copy(fontWeight = FontWeight.Bold),
        titleMedium = titleMedium.gs().copy(fontWeight = FontWeight.Bold),
        titleSmall = titleSmall.gs(),
        bodyLarge = bodyLarge.gs(), bodyMedium = bodyMedium.gs(), bodySmall = bodySmall.gs(),
        labelLarge = labelLarge.gs(), labelMedium = labelMedium.gs(),
        labelSmall = labelSmall.gs().copy(fontSize = 11.sp),
    )
}

@Composable
fun PromozioneTheme(mode: ThemeMode, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val c = if (dark) DarkAppColors else LightAppColors
    val scheme = if (dark) {
        darkColorScheme(
            primary = c.accent, onPrimary = c.onAccent, secondary = c.accent2,
            background = c.bg, onBackground = c.text, surface = c.bg, onSurface = c.text,
            surfaceContainer = c.surface, surfaceVariant = c.surface, onSurfaceVariant = c.muted,
            outline = c.line, outlineVariant = c.divider,
            secondaryContainer = c.line, onSecondaryContainer = c.text,
        )
    } else {
        lightColorScheme(
            primary = c.accent, onPrimary = c.onAccent, secondary = c.accent2,
            background = c.bg, onBackground = c.text, surface = c.bg, onSurface = c.text,
            surfaceContainer = c.surface, surfaceVariant = c.surface, onSurfaceVariant = c.muted,
            outline = c.line, outlineVariant = c.divider,
            secondaryContainer = c.line, onSecondaryContainer = c.text,
        )
    }
    CompositionLocalProvider(LocalAppColors provides c) {
        MaterialTheme(colorScheme = scheme, typography = AppTypography) {
            CompositionLocalProvider(LocalContentColor provides c.text, content = content)
        }
    }
}

object AppTheme {
    val colors: AppColors @Composable get() = LocalAppColors.current
}
