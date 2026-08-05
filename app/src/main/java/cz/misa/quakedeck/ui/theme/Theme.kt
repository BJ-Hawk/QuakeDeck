package cz.misa.quakedeck.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF66B7FF),
    onPrimary = Color(0xFF003355),
    primaryContainer = Color(0xFF174A67),
    onPrimaryContainer = Color(0xFFD3ECFF),
    secondary = Color(0xFFFFB45C),
    onSecondary = Color(0xFF4D2600),
    secondaryContainer = Color(0xFF3A2918),
    onSecondaryContainer = Color(0xFFFFDDB6),
    background = Color(0xFF0B0F14),
    onBackground = Color(0xFFF1F5F9),
    surface = Color(0xFF111820),
    onSurface = Color(0xFFF1F5F9),
    surfaceVariant = Color(0xFF18222D),
    onSurfaceVariant = Color(0xFF9FB0BF),
    outline = Color(0xFF687B8C),
    outlineVariant = Color(0xFF2C3A47),
    error = Color(0xFFFF625A),
    errorContainer = Color(0xFF5A1718),
    onErrorContainer = Color(0xFFFFD8D5)
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF00679A),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFCDEBFF),
    onPrimaryContainer = Color(0xFF002F49),
    secondary = Color(0xFF9A5200),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFDDB7),
    onSecondaryContainer = Color(0xFF301800),
    background = Color(0xFFF4F7FA),
    onBackground = Color(0xFF17212A),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF17212A),
    surfaceVariant = Color(0xFFE5EDF3),
    onSurfaceVariant = Color(0xFF4E626F),
    outline = Color(0xFF71838F),
    outlineVariant = Color(0xFFC7D3DB),
    error = Color(0xFFBA1A1A),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002)
)

/** Colours that are specific to QuakeDeck's map and compact controls. */
@Immutable
data class QuakeDeckExtraColors(
    val isDark: Boolean,
    val mapBackground: Color,
    val mapLand: Color,
    val mapContextLand: Color,
    val mapBoundary: Color,
    val mapContextBoundary: Color,
    val mapRegionBoundary: Color,
    val mapCoastBackdrop: Color,
    val mapLabelFill: Color,
    val mapLabelOutline: Color,
    val mapCityMajor: Color,
    val mapCityMinor: Color,
    val mapStationJma: Color,
    val mapStationNied: Color,
    val mapStationOther: Color,
    val mapStationOutline: Color,
    val mapControlSurface: Color,
    val mapControlForeground: Color,
    val mapBranding: Color,
    val handleTrack: Color,
    val handleProgress: Color,
    val epicenterFocusedOutline: Color,
    val epicenterUnfocusedOutline: Color
)

private val DarkExtraColors = QuakeDeckExtraColors(
    isDark = true,
    mapBackground = Color(0xFF081018),
    mapLand = Color(0xFF6D7780),
    mapContextLand = Color(0xFF2E373E),
    mapBoundary = Color(0xFFDCE4E9),
    mapContextBoundary = Color(0xFF6F7C85),
    mapRegionBoundary = Color(0x96E0E7EC),
    mapCoastBackdrop = Color(0xFF080D12),
    mapLabelFill = Color(0xFFECF2F6),
    mapLabelOutline = Color(0xDC050B10),
    mapCityMajor = Color(0xFFDDE7ED),
    mapCityMinor = Color(0xFFAFC0CB),
    mapStationJma = Color(0xFFB5C6D2),
    mapStationNied = Color(0xFF7FA8D8),
    mapStationOther = Color(0xFF7D909D),
    mapStationOutline = Color(0xDD10151A),
    mapControlSurface = Color(0xDD101820),
    mapControlForeground = Color(0xFFE4EEF4),
    mapBranding = Color(0xFF91A4B7),
    handleTrack = Color(0xFF5C6F82),
    handleProgress = Color(0xFF70D7FF),
    epicenterFocusedOutline = Color(0xFF70D7FF),
    epicenterUnfocusedOutline = Color.White
)

private val LightExtraColors = QuakeDeckExtraColors(
    isDark = false,
    mapBackground = Color(0xFFE7F1F6),
    mapLand = Color(0xFFB5C2CA),
    mapContextLand = Color(0xFFD3DDE3),
    mapBoundary = Color(0xFF687985),
    mapContextBoundary = Color(0xFF7D8D97),
    mapRegionBoundary = Color(0xA0667884),
    mapCoastBackdrop = Color(0xFF263741),
    mapLabelFill = Color(0xFF182832),
    mapLabelOutline = Color(0xEFFFFFFF),
    mapCityMajor = Color(0xFF314752),
    mapCityMinor = Color(0xFF667A86),
    mapStationJma = Color(0xFF526B79),
    mapStationNied = Color(0xFF356EAA),
    mapStationOther = Color(0xFF687D89),
    mapStationOutline = Color(0xE6FFFFFF),
    mapControlSurface = Color(0xECFFFFFF),
    mapControlForeground = Color(0xFF20343F),
    mapBranding = Color(0xFF4F6877),
    handleTrack = Color(0xFF95A6B1),
    handleProgress = Color(0xFF0077AD),
    epicenterFocusedOutline = Color(0xFF007DB7),
    epicenterUnfocusedOutline = Color(0xFF263B47)
)

val LocalQuakeDeckExtraColors = staticCompositionLocalOf { DarkExtraColors }

@Composable
fun QuakeDeckTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val extraColors = if (darkTheme) DarkExtraColors else LightExtraColors

    androidx.compose.runtime.CompositionLocalProvider(
        LocalQuakeDeckExtraColors provides extraColors
    ) {
        MaterialTheme(colorScheme = colorScheme) {
            ConfigureSystemBars(darkTheme)
            content()
        }
    }
}

@Composable
private fun ConfigureSystemBars(darkTheme: Boolean) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val activity = view.context as? Activity ?: return@SideEffect
            val window = activity.window
            // MainActivity enables edge-to-edge drawing. Keep three-button
            // navigation free of the automatic contrast scrim where supported;
            // system-bar icon colors continue to follow the selected app theme.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                window.isNavigationBarContrastEnforced = false
            }
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }
}
