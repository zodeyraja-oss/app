package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.example.data.AppColorTheme
import com.example.data.ChatColorTheme
import com.example.data.ThemeMode

data class KinChatExtendedColors(
    val isDark: Boolean,
    val outgoingBubble: Color,
    val incomingBubble: Color,
    val securityGreen: Color,
    val highContrastMode: Boolean,
    val textScale: Float
)

val LocalKinChatColors = staticCompositionLocalOf {
    KinChatExtendedColors(
        isDark = true,
        outgoingBubble = Color(0xFF0B4D38),
        incomingBubble = DarkSurfaceVariant,
        securityGreen = SecurityBadgeGreen,
        highContrastMode = false,
        textScale = 1.0f
    )
}

fun accentPaletteFor(appColor: AppColorTheme): AccentPalette = when (appColor) {
    AppColorTheme.EMERALD -> EmeraldAccent
    AppColorTheme.OCEAN -> OceanAccent
    AppColorTheme.VIOLET -> VioletAccent
    AppColorTheme.AMBER -> AmberAccent
    AppColorTheme.ROSE -> RoseAccent
    AppColorTheme.CYAN -> CyanAccent
}

fun chatBubbleColorsFor(
    chatColor: ChatColorTheme,
    appColor: AppColorTheme,
    isDark: Boolean
): Pair<Color, Color> {
    val resolvedChat = if (chatColor == ChatColorTheme.MATCH_APP) {
        when (appColor) {
            AppColorTheme.EMERALD -> ChatColorTheme.EMERALD
            AppColorTheme.OCEAN -> ChatColorTheme.COBALT
            AppColorTheme.VIOLET -> ChatColorTheme.PLUM
            AppColorTheme.AMBER -> ChatColorTheme.GOLD
            AppColorTheme.ROSE -> ChatColorTheme.CORAL
            AppColorTheme.CYAN -> ChatColorTheme.COBALT
        }
    } else {
        chatColor
    }
    return if (isDark) {
        val outgoing = when (resolvedChat) {
            ChatColorTheme.MATCH_APP, ChatColorTheme.EMERALD -> Color(0xFF0A4D38)
            ChatColorTheme.COBALT -> Color(0xFF0D3A6E)
            ChatColorTheme.PLUM -> Color(0xFF3E1C7A)
            ChatColorTheme.CORAL -> Color(0xFF6B132C)
            ChatColorTheme.GOLD -> Color(0xFF573300)
        }
        outgoing to DarkSurfaceVariant
    } else {
        val outgoing = when (resolvedChat) {
            ChatColorTheme.MATCH_APP, ChatColorTheme.EMERALD -> Color(0xFFC9F7E4)
            ChatColorTheme.COBALT -> Color(0xFFD6E3FF)
            ChatColorTheme.PLUM -> Color(0xFFEADDFF)
            ChatColorTheme.CORAL -> Color(0xFFFFD9E1)
            ChatColorTheme.GOLD -> Color(0xFFFFE5B8)
        }
        outgoing to LightSurfaceVariant
    }
}

private fun kinChatDarkScheme(
    accent: AccentPalette,
    highContrast: Boolean
) = darkColorScheme(
    primary = accent.primaryDark,
    onPrimary = accent.onPrimaryDark,
    primaryContainer = accent.containerDark,
    onPrimaryContainer = accent.onContainerDark,
    secondary = accent.secondaryDark,
    onSecondary = Color(0xFF141414),
    secondaryContainer = DarkSurfaceElevated,
    onSecondaryContainer = TextPrimaryDark,
    tertiary = Color(0xFF64D2FF),
    onTertiary = Color(0xFF001F2A),
    background = PureBlackBg,
    onBackground = TextPrimaryDark,
    surface = if (highContrast) PureBlackBg else DarkSurface,
    onSurface = TextPrimaryDark,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = TextSecondaryDark,
    surfaceContainerHigh = DarkSurfaceElevated,
    outline = if (highContrast) HighContrastOutlineDark else OutlineDark,
    error = ErrorRed,
    onError = OnErrorRed
)

private fun kinChatLightScheme(
    accent: AccentPalette,
    highContrast: Boolean
) = lightColorScheme(
    primary = accent.primaryLight,
    onPrimary = accent.onPrimaryLight,
    primaryContainer = accent.containerLight,
    onPrimaryContainer = accent.onContainerLight,
    secondary = accent.secondaryLight,
    onSecondary = Color.White,
    secondaryContainer = LightSurfaceElevated,
    onSecondaryContainer = TextPrimaryLight,
    background = PureWhiteBg,
    onBackground = TextPrimaryLight,
    surface = LightSurface,
    onSurface = TextPrimaryLight,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = TextSecondaryLight,
    surfaceContainerHigh = LightSurfaceElevated,
    outline = if (highContrast) HighContrastOutlineLight else OutlineLight,
    error = Color(0xFFB3261E),
    onError = Color.White
)

@Composable
fun resolveIsDarkTheme(themeMode: ThemeMode): Boolean {
    val systemDark = isSystemInDarkTheme()
    return when (themeMode) {
        ThemeMode.SYSTEM -> systemDark
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
    }
}

@Composable
fun KinChatTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    appColor: AppColorTheme = AppColorTheme.EMERALD,
    chatColor: ChatColorTheme = ChatColorTheme.MATCH_APP,
    highContrast: Boolean = false,
    textScale: Float = 1.0f,
    content: @Composable () -> Unit
) {
    val isDark = resolveIsDarkTheme(themeMode)
    val accent = accentPaletteFor(appColor)
    val colorScheme = if (isDark) {
        kinChatDarkScheme(accent, highContrast)
    } else {
        kinChatLightScheme(accent, highContrast)
    }
    val (outgoingBubble, incomingBubble) = chatBubbleColorsFor(chatColor, appColor, isDark)
    val extendedColors = KinChatExtendedColors(
        isDark = isDark,
        outgoingBubble = outgoingBubble,
        incomingBubble = incomingBubble,
        securityGreen = if (isDark) SecurityBadgeGreen else SecurityBadgeGreenLight,
        highContrastMode = highContrast,
        textScale = textScale
    )

    CompositionLocalProvider(LocalKinChatColors provides extendedColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = buildKinChatTypography(textScale),
            content = content
        )
    }
}
