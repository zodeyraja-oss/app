package com.example.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.R

val DisplayFontFamily = FontFamily(
    Font(R.font.plus_jakarta_sans, FontWeight.Normal),
    Font(R.font.plus_jakarta_sans, FontWeight.SemiBold),
    Font(R.font.plus_jakarta_sans, FontWeight.Bold)
)

val BodyFontFamily = FontFamily(
    Font(R.font.atkinson_hyperlegible, FontWeight.Normal),
    Font(R.font.atkinson_hyperlegible, FontWeight.Bold)
)

val MonoFontFamily = FontFamily(
    Font(R.font.jetbrains_mono, FontWeight.Normal),
    Font(R.font.jetbrains_mono, FontWeight.Bold)
)

fun buildKinChatTypography(scale: Float = 1.0f): Typography {
    val s = scale.coerceIn(0.9f, 1.55f)
    return Typography(
        displaySmall = TextStyle(
            fontFamily = DisplayFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = (30f * s).sp,
            lineHeight = (38f * s).sp,
            letterSpacing = (-0.25).sp
        ),
        headlineMedium = TextStyle(
            fontFamily = DisplayFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = (24f * s).sp,
            lineHeight = (32f * s).sp
        ),
        headlineSmall = TextStyle(
            fontFamily = DisplayFontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = (20f * s).sp,
            lineHeight = (28f * s).sp
        ),
        titleLarge = TextStyle(
            fontFamily = DisplayFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = (19f * s).sp,
            lineHeight = (26f * s).sp
        ),
        titleMedium = TextStyle(
            fontFamily = DisplayFontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = (16f * s).sp,
            lineHeight = (22f * s).sp,
            letterSpacing = 0.15.sp
        ),
        bodyLarge = TextStyle(
            fontFamily = BodyFontFamily,
            fontWeight = FontWeight.Normal,
            fontSize = (16.5f * s).sp,
            lineHeight = (24f * s).sp,
            letterSpacing = 0.25.sp
        ),
        bodyMedium = TextStyle(
            fontFamily = BodyFontFamily,
            fontWeight = FontWeight.Normal,
            fontSize = (14.5f * s).sp,
            lineHeight = (21f * s).sp,
            letterSpacing = 0.2.sp
        ),
        labelLarge = TextStyle(
            fontFamily = DisplayFontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = (14.5f * s).sp,
            lineHeight = (20f * s).sp,
            letterSpacing = 0.1.sp
        ),
        labelMedium = TextStyle(
            fontFamily = BodyFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = (12.5f * s).sp,
            lineHeight = (16f * s).sp,
            letterSpacing = 0.4.sp
        ),
        labelSmall = TextStyle(
            fontFamily = BodyFontFamily,
            fontWeight = FontWeight.Normal,
            fontSize = (11.5f * s).sp,
            lineHeight = (16f * s).sp,
            letterSpacing = 0.4.sp
        )
    )
}

val Typography = buildKinChatTypography(1.0f)
