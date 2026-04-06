package com.example.blockerop.ui.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val AppColorScheme = darkColorScheme(
    primary              = Indigo,
    onPrimary            = Color.White,
    primaryContainer     = IndigoDim,
    onPrimaryContainer   = IndigoLight,

    secondary            = Emerald,
    onSecondary          = Color.White,
    secondaryContainer   = EmeraldDim,
    onSecondaryContainer = Emerald,

    tertiary             = Amber,
    onTertiary           = Color.White,
    tertiaryContainer    = AmberDim,
    onTertiaryContainer  = Amber,

    error                = Rose,
    onError              = Color.White,
    errorContainer       = RoseDim,
    onErrorContainer     = Rose,

    background           = BgDeep,
    onBackground         = TextHigh,

    surface              = BgCard,
    onSurface            = TextHigh,
    surfaceVariant       = BgElevated,
    onSurfaceVariant     = TextMid,

    outline              = BorderSubtle,
    outlineVariant       = BorderMid,

    scrim                = Color(0xCC000000),
)

@Composable
fun BlockerOPTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = AppColorScheme,
        typography  = Typography,
        content     = content
    )
}
