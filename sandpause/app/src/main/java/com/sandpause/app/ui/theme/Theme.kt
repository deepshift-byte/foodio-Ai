package com.sandpause.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.sandpause.app.R

/** Nunito, a rounded typeface, bundled in res/font (SIL Open Font License). */
val Nunito = FontFamily(
    Font(R.font.nunito_regular, FontWeight.Normal),
    Font(R.font.nunito_bold, FontWeight.Bold),
    Font(R.font.nunito_extrabold, FontWeight.ExtraBold),
)

private val AppTypography = Typography(
    displayLarge = TextStyle(fontFamily = Nunito, fontWeight = FontWeight.ExtraBold, fontSize = 56.sp),
    headlineMedium = TextStyle(fontFamily = Nunito, fontWeight = FontWeight.ExtraBold, fontSize = 28.sp),
    titleLarge = TextStyle(fontFamily = Nunito, fontWeight = FontWeight.Bold, fontSize = 22.sp),
    titleMedium = TextStyle(fontFamily = Nunito, fontWeight = FontWeight.Bold, fontSize = 17.sp),
    bodyLarge = TextStyle(fontFamily = Nunito, fontWeight = FontWeight.Normal, fontSize = 17.sp),
    bodyMedium = TextStyle(fontFamily = Nunito, fontWeight = FontWeight.Normal, fontSize = 15.sp),
    labelLarge = TextStyle(fontFamily = Nunito, fontWeight = FontWeight.Bold, fontSize = 16.sp),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF1F6F78),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFCBEFF2),
    onPrimaryContainer = Color(0xFF002025),
    secondary = Color(0xFFB7791F),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFE7B3),
    onSecondaryContainer = Color(0xFF2B1B00),
    tertiary = Color(0xFFC62828),
    error = Color(0xFFB3261E),
    background = Color(0xFFF7F9FA),
    onBackground = Color(0xFF1A1C1E),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1A1C1E),
    surfaceVariant = Color(0xFFE3ECEE),
    onSurfaceVariant = Color(0xFF41484A),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF8ED1D9),
    onPrimary = Color(0xFF00363C),
    primaryContainer = Color(0xFF0B4F57),
    onPrimaryContainer = Color(0xFFCBEFF2),
    secondary = Color(0xFFFFCF70),
    onSecondary = Color(0xFF442C00),
    secondaryContainer = Color(0xFF5F4100),
    onSecondaryContainer = Color(0xFFFFE7B3),
    tertiary = Color(0xFFFF8A80),
    error = Color(0xFFFFB4AB),
    background = Color(0xFF101416),
    onBackground = Color(0xFFE0E3E5),
    surface = Color(0xFF181C1E),
    onSurface = Color(0xFFE0E3E5),
    surfaceVariant = Color(0xFF2B3437),
    onSurfaceVariant = Color(0xFFC0C8CB),
)

@Composable
fun SandPauseTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = AppTypography,
        content = content,
    )
}

/** Colours of the child lock screen. They do not follow dark mode: the child screen is always bright. */
object ChildPalette {
    val YellowTop = Color(0xFFFFF6C9)
    val YellowBottom = Color(0xFFFFD95A)
    val YellowGlow = Color(0xFFFFE082)
    val YellowSand = Color(0xFFE0A526)
    val YellowText = Color(0xFF3B2F00)

    val RedTop = Color(0xFFFFD6D6)
    val RedBottom = Color(0xFFFF8A80)
    val RedGlow = Color(0xFFFF5252)
    val RedSand = Color(0xFFC62828)
    val RedText = Color(0xFF4A0000)

    val GreenTop = Color(0xFFD7F8D0)
    val GreenBottom = Color(0xFF7ED957)
    val GreenGlow = Color(0xFF66BB6A)
    val GreenSand = Color(0xFF2E7D32)
    val GreenText = Color(0xFF0B3D0B)

    val Glass = Color(0xFF5D4037)
    val Card = Color(0xFFFFFFFF)
    val Emergency = Color(0xFFD32F2F)
}
