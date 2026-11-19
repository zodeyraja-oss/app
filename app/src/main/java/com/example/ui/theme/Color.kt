package com.example.ui.theme

import androidx.compose.ui.graphics.Color

// Pure Black (Dark Mode) & Pure White (Light Mode) Canvas
val PureBlackBg = Color(0xFF000000)
val DarkSurface = Color(0xFF0C0D10)
val DarkSurfaceVariant = Color(0xFF16181D)
val DarkSurfaceElevated = Color(0xFF1F2229)

val PureWhiteBg = Color(0xFFFFFFFF)
val LightSurface = Color(0xFFFFFFFF)
val LightSurfaceVariant = Color(0xFFF2F4F7)
val LightSurfaceElevated = Color(0xFFE6E9EF)

val TextPrimaryDark = Color(0xFFF5F7FA)
val TextSecondaryDark = Color(0xFFB3BCC8)
val OutlineDark = Color(0xFF333944)
val HighContrastOutlineDark = Color(0xFF94A3B8)

val TextPrimaryLight = Color(0xFF0B0E14)
val TextSecondaryLight = Color(0xFF424A57)
val OutlineLight = Color(0xFF8A94A6)
val HighContrastOutlineLight = Color(0xFF111827)

val ErrorRed = Color(0xFFFF6B6B)
val OnErrorRed = Color(0xFF3B0004)
val SecurityBadgeGreen = Color(0xFF2EE6A8)
val SecurityBadgeGreenLight = Color(0xFF006C4C)

data class AccentPalette(
    val primaryDark: Color,
    val onPrimaryDark: Color,
    val containerDark: Color,
    val onContainerDark: Color,
    val secondaryDark: Color,
    val primaryLight: Color,
    val onPrimaryLight: Color,
    val containerLight: Color,
    val onContainerLight: Color,
    val secondaryLight: Color
)

val EmeraldAccent = AccentPalette(
    primaryDark = Color(0xFF2EE6A8),
    onPrimaryDark = Color(0xFF002116),
    containerDark = Color(0xFF0A4D38),
    onContainerDark = Color(0xFFA7F9D7),
    secondaryDark = Color(0xFFFFB74D),
    primaryLight = Color(0xFF006C4C),
    onPrimaryLight = Color(0xFFFFFFFF),
    containerLight = Color(0xFFA6F7D5),
    onContainerLight = Color(0xFF002114),
    secondaryLight = Color(0xFF8C5000)
)

val OceanAccent = AccentPalette(
    primaryDark = Color(0xFF4DA3FF),
    onPrimaryDark = Color(0xFF001C3B),
    containerDark = Color(0xFF0D3A6E),
    onContainerDark = Color(0xFFCFE4FF),
    secondaryDark = Color(0xFF38E8E8),
    primaryLight = Color(0xFF005AC1),
    onPrimaryLight = Color(0xFFFFFFFF),
    containerLight = Color(0xFFD6E3FF),
    onContainerLight = Color(0xFF001B3E),
    secondaryLight = Color(0xFF00677F)
)

val VioletAccent = AccentPalette(
    primaryDark = Color(0xFFB388FF),
    onPrimaryDark = Color(0xFF24005A),
    containerDark = Color(0xFF3E1C7A),
    onContainerDark = Color(0xFFEADBFF),
    secondaryDark = Color(0xFFFF80AB),
    primaryLight = Color(0xFF5E35B1),
    onPrimaryLight = Color(0xFFFFFFFF),
    containerLight = Color(0xFFEADDFF),
    onContainerLight = Color(0xFF21005D),
    secondaryLight = Color(0xFF9C2763)
)

val AmberAccent = AccentPalette(
    primaryDark = Color(0xFFFFB74D),
    onPrimaryDark = Color(0xFF2E1500),
    containerDark = Color(0xFF573300),
    onContainerDark = Color(0xFFFFE0B2),
    secondaryDark = Color(0xFF2EE6A8),
    primaryLight = Color(0xFF8C5000),
    onPrimaryLight = Color(0xFFFFFFFF),
    containerLight = Color(0xFFFFDDB3),
    onContainerLight = Color(0xFF2D1600),
    secondaryLight = Color(0xFF006C4C)
)

val RoseAccent = AccentPalette(
    primaryDark = Color(0xFFFF6E91),
    onPrimaryDark = Color(0xFF3B0015),
    containerDark = Color(0xFF6B132C),
    onContainerDark = Color(0xFFFFD9E2),
    secondaryDark = Color(0xFFFFB74D),
    primaryLight = Color(0xFFB31E4A),
    onPrimaryLight = Color(0xFFFFFFFF),
    containerLight = Color(0xFFFFD9E1),
    onContainerLight = Color(0xFF3E0014),
    secondaryLight = Color(0xFF7D4900)
)

val CyanAccent = AccentPalette(
    primaryDark = Color(0xFF38E8E8),
    onPrimaryDark = Color(0xFF002022),
    containerDark = Color(0xFF004F52),
    onContainerDark = Color(0xFFA8F9FA),
    secondaryDark = Color(0xFF4DA3FF),
    primaryLight = Color(0xFF00686B),
    onPrimaryLight = Color(0xFFFFFFFF),
    containerLight = Color(0xFFA3F2F5),
    onContainerLight = Color(0xFF002021),
    secondaryLight = Color(0xFF005AC1)
)
