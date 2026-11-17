package com.crazestudio.stone.ui.theme

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
import com.crazestudio.stone.R

val StoneOrange = Color(0xFFFF7900)
val StoneOrangeMuted = Color(0x33FF7900)

val SpaceGroteskFontFamily = FontFamily(
    Font(R.font.space_grotesk, FontWeight.Normal),
    Font(R.font.space_grotesk, FontWeight.Medium),
    Font(R.font.space_grotesk, FontWeight.SemiBold),
    Font(R.font.space_grotesk, FontWeight.Bold)
)

val PlusJakartaSansFontFamily = FontFamily(
    Font(R.font.plus_jakarta_sans, FontWeight.Normal),
    Font(R.font.plus_jakarta_sans, FontWeight.Medium),
    Font(R.font.plus_jakarta_sans, FontWeight.SemiBold),
    Font(R.font.plus_jakarta_sans, FontWeight.Bold)
)

val JetBrainsMonoFontFamily = FontFamily(
    Font(R.font.jetbrains_mono, FontWeight.Normal),
    Font(R.font.jetbrains_mono, FontWeight.Medium),
    Font(R.font.jetbrains_mono, FontWeight.Bold)
)

@Immutable
data class StoneColors(
    val bg: Color,
    val panel: Color,
    val panel2: Color,
    val rail: Color,
    val line: Color,
    val text: Color,
    val muted: Color,
    val success: Color,
    val warning: Color,
    val error: Color,
    val isLight: Boolean
)

@Immutable
data class AnsiPalette(
    val defaultFg: Color,
    val defaultBg: Color,
    val standard: List<Color>,
    val bright: List<Color>
)

val DarkStone = StoneColors(
    bg = Color(0xFF0C0C0C),
    panel = Color(0xFF151515),
    panel2 = Color(0xFF1E1E1E),
    rail = Color(0xFF101010),
    line = Color(0xFF2F2F2F),
    text = Color(0xFFF4F4F4),
    muted = Color(0xFF9A9A9A),
    success = Color(0xFF8BD8A5),
    warning = Color(0xFFFFB74D),
    error = Color(0xFFFF6E6E),
    isLight = false
)

val LightStone = StoneColors(
    bg = Color(0xFFF3F3F1),
    panel = Color(0xFFFFFFFF),
    panel2 = Color(0xFFE9E9E6),
    rail = Color(0xFFE4E4E0),
    line = Color(0xFFD0D0CA),
    text = Color(0xFF151515),
    muted = Color(0xFF5F5F5B),
    success = Color(0xFF2E6B40),
    warning = Color(0xFFB26A00),
    error = Color(0xFFC62828),
    isLight = true
)

val ObsidianStone = StoneColors(
    bg = Color(0xFF030303),
    panel = Color(0xFF0E0E0E),
    panel2 = Color(0xFF171717),
    rail = Color(0xFF080808),
    line = Color(0xFF282828),
    text = Color(0xFFF4F4F4),
    muted = Color(0xFF8A8A8A),
    success = Color(0xFF8BD8A5),
    warning = Color(0xFFFFB74D),
    error = Color(0xFFFF6E6E),
    isLight = false
)

val MidnightStone = StoneColors(
    bg = Color(0xFF101218),
    panel = Color(0xFF171A23),
    panel2 = Color(0xFF212531),
    rail = Color(0xFF13151D),
    line = Color(0xFF333847),
    text = Color(0xFFEEF0F5),
    muted = Color(0xFF969CAD),
    success = Color(0xFF8BD8A5),
    warning = Color(0xFFFFB74D),
    error = Color(0xFFFF6E6E),
    isLight = false
)

private fun ansiPaletteFor(colors: StoneColors): AnsiPalette {
    return if (colors.isLight) {
        AnsiPalette(
            defaultFg = colors.text,
            defaultBg = colors.bg,
            standard = listOf(
                Color(0xFF1C1C1C), // 0 Black
                Color(0xFFC62828), // 1 Red
                Color(0xFF2E7D32), // 2 Green
                Color(0xFFAD6800), // 3 Yellow
                Color(0xFF1565C0), // 4 Blue
                Color(0xFF7B1FA2), // 5 Magenta
                Color(0xFF00838F), // 6 Cyan
                Color(0xFF5A5A5A)  // 7 White/Gray
            ),
            bright = listOf(
                Color(0xFF616161), // 8 Bright Black
                Color(0xFFE53935), // 9 Bright Red
                Color(0xFF43A047), // 10 Bright Green
                Color(0xFFD97706), // 11 Bright Yellow
                Color(0xFF1E88E5), // 12 Bright Blue
                Color(0xFF9C27B0), // 13 Bright Magenta
                Color(0xFF0097A7), // 14 Bright Cyan
                Color(0xFF111111)  // 15 Bright White
            )
        )
    } else {
        AnsiPalette(
            defaultFg = colors.text,
            defaultBg = colors.bg,
            standard = listOf(
                Color(0xFF262626), // 0 Black
                Color(0xFFE56B6F), // 1 Red
                Color(0xFF8BD8A5), // 2 Green
                Color(0xFFE9C46A), // 3 Yellow
                Color(0xFF7AA2F7), // 4 Blue
                Color(0xFFBB9AF7), // 5 Magenta
                Color(0xFF7DCFFF), // 6 Cyan
                Color(0xFFD8D8D8)  // 7 White
            ),
            bright = listOf(
                Color(0xFF7A7A7A), // 8 Bright Black
                Color(0xFFFF7A7A), // 9 Bright Red
                Color(0xFFA6F4C5), // 10 Bright Green
                Color(0xFFFFD166), // 11 Bright Yellow
                Color(0xFF9ABDF5), // 12 Bright Blue
                Color(0xFFD4BFFF), // 13 Bright Magenta
                Color(0xFFA4E8FF), // 14 Bright Cyan
                Color(0xFFFFFFFF)  // 15 Bright White
            )
        )
    }
}

val StoneTypography = Typography(
    headlineMedium = TextStyle(
        fontFamily = SpaceGroteskFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        lineHeight = 30.sp,
        letterSpacing = (-0.5).sp
    ),
    titleLarge = TextStyle(
        fontFamily = SpaceGroteskFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 24.sp
    ),
    titleMedium = TextStyle(
        fontFamily = SpaceGroteskFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        lineHeight = 20.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = PlusJakartaSansFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = PlusJakartaSansFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 18.sp
    ),
    bodySmall = TextStyle(
        fontFamily = PlusJakartaSansFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 11.sp,
        lineHeight = 16.sp
    ),
    labelMedium = TextStyle(
        fontFamily = PlusJakartaSansFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp,
        lineHeight = 16.sp
    )
)

val LocalStoneColors = staticCompositionLocalOf { DarkStone }
val LocalAnsiPalette = staticCompositionLocalOf { ansiPaletteFor(DarkStone) }

fun stoneColorsForTheme(themeName: String): StoneColors = when (themeName) {
    "Stone Light" -> LightStone
    "Obsidian" -> ObsidianStone
    "Midnight" -> MidnightStone
    else -> DarkStone
}

@Composable
fun StoneTheme(themeName: String, content: @Composable () -> Unit) {
    val palette = stoneColorsForTheme(themeName)
    val ansi = ansiPaletteFor(palette)
    val colorScheme = if (palette.isLight) {
        lightColorScheme(
            primary = StoneOrange,
            onPrimary = Color.White,
            background = palette.bg,
            onBackground = palette.text,
            surface = palette.panel,
            onSurface = palette.text,
            surfaceVariant = palette.panel2,
            onSurfaceVariant = palette.muted,
            outline = palette.line,
            error = palette.error
        )
    } else {
        darkColorScheme(
            primary = StoneOrange,
            onPrimary = Color.White,
            background = palette.bg,
            onBackground = palette.text,
            surface = palette.panel,
            onSurface = palette.text,
            surfaceVariant = palette.panel2,
            onSurfaceVariant = palette.muted,
            outline = palette.line,
            error = palette.error
        )
    }

    CompositionLocalProvider(
        LocalStoneColors provides palette,
        LocalAnsiPalette provides ansi
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = StoneTypography,
            content = content
        )
    }
}
