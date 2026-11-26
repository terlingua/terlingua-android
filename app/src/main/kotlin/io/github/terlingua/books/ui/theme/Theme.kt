package io.github.terlingua.books.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import io.github.terlingua.books.domain.ThemeMode

@Immutable
data class CodeColors(
    val background: Color,
    val plain: Color,
    val keyword: Color,
    val string: Color,
    val comment: Color,
    val number: Color,
    val function: Color,
    val type: Color,
    val annotation: Color,
    val operator: Color,
) {
    fun forKind(kind: String): Color = when (kind) {
        "kw" -> keyword
        "str" -> string
        "com" -> comment
        "num", "lit" -> number
        "fn" -> function
        "type", "tag" -> type
        "ann", "attr", "var" -> annotation
        "op" -> operator
        else -> plain
    }
}

@Immutable
data class ReaderColors(val success: Color, val onSuccess: Color, val successContainer: Color, val onSuccessContainer: Color)

val LocalCodeColors = staticCompositionLocalOf { LightCode }
val LocalReaderColors = staticCompositionLocalOf { LightReader }
val LocalIsDarkTheme = staticCompositionLocalOf { false }

@Composable
fun TerlinguaTheme(mode: ThemeMode, content: @Composable () -> Unit) {
    val systemDark = isSystemInDarkTheme()
    val context = LocalContext.current
    val dark = when (mode) {
        ThemeMode.System -> systemDark
        ThemeMode.Dark, ThemeMode.SolarizedDark, ThemeMode.HighContrast -> true
        ThemeMode.Light, ThemeMode.Sepia, ThemeMode.SolarizedLight -> false
    }
    val scheme: ColorScheme = when (mode) {
        ThemeMode.System -> when {
            Build.VERSION.SDK_INT >= 31 && systemDark -> dynamicDarkColorScheme(context)
            Build.VERSION.SDK_INT >= 31 -> dynamicLightColorScheme(context)
            systemDark -> DarkScheme
            else -> LightScheme
        }
        ThemeMode.Light -> LightScheme
        ThemeMode.Dark -> DarkScheme
        ThemeMode.Sepia -> SepiaScheme
        ThemeMode.SolarizedLight -> SolarizedLightScheme
        ThemeMode.SolarizedDark -> SolarizedDarkScheme
        ThemeMode.HighContrast -> HighContrastScheme
    }
    val code = when (mode) {
        ThemeMode.SolarizedLight -> SolarizedLightCode
        ThemeMode.SolarizedDark -> SolarizedDarkCode
        ThemeMode.Sepia -> SepiaCode
        ThemeMode.HighContrast -> HighContrastCode
        else -> if (dark) DarkCode else LightCode
    }
    CompositionLocalProvider(
        LocalCodeColors provides code,
        LocalReaderColors provides if (dark) DarkReader else LightReader,
        LocalIsDarkTheme provides dark,
    ) {
        MaterialTheme(colorScheme = scheme, typography = TerlinguaTypography, content = content)
    }
}

private val LightScheme = lightColorScheme(
    primary = Color(0xFF00696B), onPrimary = Color.White,
    primaryContainer = Color(0xFF9CF1F2), onPrimaryContainer = Color(0xFF002020),
    secondary = Color(0xFF4A6363), secondaryContainer = Color(0xFFCCE8E8),
    tertiary = Color(0xFF4B607C), tertiaryContainer = Color(0xFFD3E4FF),
    background = Color(0xFFFAFDFC), surface = Color(0xFFFAFDFC),
    surfaceVariant = Color(0xFFDAE5E4), onSurfaceVariant = Color(0xFF3F4948),
    outline = Color(0xFF6F7979),
)

private val DarkScheme = darkColorScheme(
    primary = Color(0xFF80D4D5), onPrimary = Color(0xFF003737),
    primaryContainer = Color(0xFF004F50), onPrimaryContainer = Color(0xFF9CF1F2),
    secondary = Color(0xFFB0CCCB), secondaryContainer = Color(0xFF324B4B),
    tertiary = Color(0xFFB3C8E8), tertiaryContainer = Color(0xFF334863),
    background = Color(0xFF191C1C), surface = Color(0xFF191C1C),
    surfaceVariant = Color(0xFF3F4948), onSurfaceVariant = Color(0xFFBEC9C8),
    outline = Color(0xFF889392),
)

private val SepiaScheme = lightColorScheme(
    primary = Color(0xFF8B5A2B), onPrimary = Color.White,
    primaryContainer = Color(0xFFF1D9BC), onPrimaryContainer = Color(0xFF2E1500),
    secondary = Color(0xFF725A42), secondaryContainer = Color(0xFFEADCC6),
    tertiary = Color(0xFF5A6237), tertiaryContainer = Color(0xFFDEE7B1),
    background = Color(0xFFF4ECD8), onBackground = Color(0xFF433422),
    surface = Color(0xFFF8F1E1), onSurface = Color(0xFF433422),
    surfaceVariant = Color(0xFFE9DDC5), onSurfaceVariant = Color(0xFF5B4A36),
    surfaceContainer = Color(0xFFF0E6D0), surfaceContainerHigh = Color(0xFFEBE0C8),
    surfaceContainerLow = Color(0xFFF6EEDB), surfaceContainerHighest = Color(0xFFE5D9BF),
    outline = Color(0xFF8C7A63),
)

private val SolarizedLightScheme = lightColorScheme(
    primary = Color(0xFF268BD2), onPrimary = Color(0xFFFDF6E3),
    primaryContainer = Color(0xFFD5E7F4), onPrimaryContainer = Color(0xFF073642),
    secondary = Color(0xFF2AA198), secondaryContainer = Color(0xFFD7EEEC),
    tertiary = Color(0xFF6C71C4), tertiaryContainer = Color(0xFFE2E2F5),
    error = Color(0xFFDC322F),
    background = Color(0xFFFDF6E3), onBackground = Color(0xFF586E75),
    surface = Color(0xFFFDF6E3), onSurface = Color(0xFF586E75),
    surfaceVariant = Color(0xFFEEE8D5), onSurfaceVariant = Color(0xFF657B83),
    surfaceContainer = Color(0xFFF5EFDC), surfaceContainerHigh = Color(0xFFEEE8D5),
    surfaceContainerLow = Color(0xFFFAF3E0), surfaceContainerHighest = Color(0xFFE8E2CF),
    outline = Color(0xFF93A1A1),
)

private val SolarizedDarkScheme = darkColorScheme(
    primary = Color(0xFF268BD2), onPrimary = Color(0xFFFDF6E3),
    primaryContainer = Color(0xFF0B4A6F), onPrimaryContainer = Color(0xFFEEE8D5),
    secondary = Color(0xFF2AA198), secondaryContainer = Color(0xFF0E4A47),
    tertiary = Color(0xFF6C71C4), tertiaryContainer = Color(0xFF2E3163),
    error = Color(0xFFDC322F),
    background = Color(0xFF002B36), onBackground = Color(0xFF93A1A1),
    surface = Color(0xFF002B36), onSurface = Color(0xFF93A1A1),
    surfaceVariant = Color(0xFF073642), onSurfaceVariant = Color(0xFF839496),
    surfaceContainer = Color(0xFF04313D), surfaceContainerHigh = Color(0xFF073642),
    surfaceContainerLow = Color(0xFF012E39), surfaceContainerHighest = Color(0xFF0B3B48),
    outline = Color(0xFF586E75),
)

private val HighContrastScheme = darkColorScheme(
    primary = Color(0xFFFFE500), onPrimary = Color.Black,
    primaryContainer = Color(0xFFFFE500), onPrimaryContainer = Color.Black,
    secondary = Color(0xFF66FFFF), onSecondary = Color.Black,
    secondaryContainer = Color(0xFF003C3C), onSecondaryContainer = Color.White,
    tertiary = Color(0xFFFF9EFF), onTertiary = Color.Black,
    error = Color(0xFFFF6B6B), onError = Color.Black,
    background = Color.Black, onBackground = Color.White,
    surface = Color.Black, onSurface = Color.White,
    surfaceVariant = Color(0xFF1A1A1A), onSurfaceVariant = Color.White,
    surfaceContainer = Color(0xFF0D0D0D), surfaceContainerHigh = Color(0xFF141414),
    surfaceContainerLow = Color(0xFF050505), surfaceContainerHighest = Color(0xFF1F1F1F),
    outline = Color.White, outlineVariant = Color(0xFFBDBDBD),
)

private val LightCode = CodeColors(
    background = Color(0xFFF3F6F6), plain = Color(0xFF1F2328), keyword = Color(0xFFCF222E),
    string = Color(0xFF0A3069), comment = Color(0xFF6E7781), number = Color(0xFF0550AE),
    function = Color(0xFF8250DF), type = Color(0xFF953800), annotation = Color(0xFF116329), operator = Color(0xFF57606A),
)
private val DarkCode = CodeColors(
    background = Color(0xFF0F1414), plain = Color(0xFFE6EDF3), keyword = Color(0xFFFF7B72),
    string = Color(0xFFA5D6FF), comment = Color(0xFF8B949E), number = Color(0xFF79C0FF),
    function = Color(0xFFD2A8FF), type = Color(0xFFFFA657), annotation = Color(0xFF7EE787), operator = Color(0xFFB1BAC4),
)
private val SepiaCode = CodeColors(
    background = Color(0xFFEDE3CC), plain = Color(0xFF433422), keyword = Color(0xFF9C3D10),
    string = Color(0xFF5F6B1F), comment = Color(0xFF8C7A63), number = Color(0xFF7A4E9C),
    function = Color(0xFF1E5E7A), type = Color(0xFF8B5A2B), annotation = Color(0xFF3E6B41), operator = Color(0xFF6B5843),
)
private val SolarizedLightCode = CodeColors(
    background = Color(0xFFEEE8D5), plain = Color(0xFF586E75), keyword = Color(0xFF859900),
    string = Color(0xFF2AA198), comment = Color(0xFF93A1A1), number = Color(0xFFD33682),
    function = Color(0xFF268BD2), type = Color(0xFFB58900), annotation = Color(0xFFCB4B16), operator = Color(0xFF657B83),
)
private val SolarizedDarkCode = SolarizedLightCode.copy(background = Color(0xFF073642), plain = Color(0xFF93A1A1), comment = Color(0xFF586E75), operator = Color(0xFF839496))
private val HighContrastCode = CodeColors(
    background = Color(0xFF111111), plain = Color.White, keyword = Color(0xFFFFE500),
    string = Color(0xFF66FFFF), comment = Color(0xFFBDBDBD), number = Color(0xFFFF9EFF),
    function = Color(0xFF8DFF8D), type = Color(0xFFFFB86B), annotation = Color(0xFF8DFF8D), operator = Color.White,
)

private val LightReader = ReaderColors(Color(0xFF1B6E33), Color.White, Color(0xFFC9F2CF), Color(0xFF00210B))
private val DarkReader = ReaderColors(Color(0xFF8BD89A), Color(0xFF00391A), Color(0xFF0F5226), Color(0xFFA6F5B4))
