package com.swordfish.lemuroid.app.mobile.shared.compose.ui

import android.content.SharedPreferences
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.swordfish.lemuroid.R
import com.swordfish.lemuroid.lib.preferences.SharedPreferencesHelper

enum class FrontendTheme(val key: String) {
    DEFAULT("default"),
    LOCADORA("locadora"),
    NETFLIX("netflix"),
    CONSOLE("console"),
    TV("tv"),
    RETRO_80_90("retro_80_90"),
    ROCKSTAR("rockstar"),
    THRASH_METAL("thrash_metal"),
    THREE_D("three_d"),
    ;

    companion object {
        fun fromKey(key: String?): FrontendTheme = entries.firstOrNull { it.key == key } ?: DEFAULT
    }
}

val LocalFrontendTheme = compositionLocalOf { FrontendTheme.DEFAULT }

/**
 * Observes the selected [FrontendTheme] from shared preferences and recomposes immediately
 * whenever the preference value changes.
 */
@Composable
fun rememberFrontendTheme(): FrontendTheme {
    val context = LocalContext.current
    val prefKey = stringResource(R.string.pref_key_frontend_theme)
    val prefs = remember(context) { SharedPreferencesHelper.getSharedPreferences(context) }
    var theme by remember(prefs, prefKey) {
        mutableStateOf(FrontendTheme.fromKey(prefs.getString(prefKey, null)))
    }

    DisposableEffect(prefs, prefKey) {
        val listener =
            SharedPreferences.OnSharedPreferenceChangeListener { sharedPreferences, key ->
                if (key == prefKey || key == null) {
                    theme = FrontendTheme.fromKey(sharedPreferences.getString(prefKey, null))
                }
            }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose {
            prefs.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }

    return theme
}

/**
 * Returns the custom Material3 [ColorScheme] for this [FrontendTheme], or `null` for [FrontendTheme.DEFAULT]
 * so the app falls back to its standard dynamic/default Material3 palette.
 */
fun FrontendTheme.colorScheme(): ColorScheme? =
    when (this) {
        FrontendTheme.DEFAULT -> null
        FrontendTheme.LOCADORA ->
            darkColorScheme(
                primary = Color(0xFFFFC107),
                onPrimary = Color(0xFF1A1200),
                primaryContainer = Color(0xFF5C4200),
                onPrimaryContainer = Color(0xFFFFE599),
                inversePrimary = Color(0xFF8C6400),
                secondary = Color(0xFF29B6F6),
                onSecondary = Color(0xFF001E2E),
                secondaryContainer = Color(0xFF004C6D),
                onSecondaryContainer = Color(0xFFC2EBFF),
                tertiary = Color(0xFFFF7043),
                onTertiary = Color(0xFF2B0B00),
                tertiaryContainer = Color(0xFF63210B),
                onTertiaryContainer = Color(0xFFFFDBD0),
                background = Color(0xFF16100C),
                onBackground = Color(0xFFF7EFE6),
                surface = Color(0xFF1F1712),
                onSurface = Color(0xFFF7EFE6),
                surfaceVariant = Color(0xFF35281F),
                onSurfaceVariant = Color(0xFFD9C7B6),
                surfaceTint = Color(0xFFFFC107),
                surfaceContainerLowest = Color(0xFF110C09),
                surfaceContainerLow = Color(0xFF1C140F),
                surfaceContainer = Color(0xFF241B15),
                surfaceContainerHigh = Color(0xFF2F231B),
                surfaceContainerHighest = Color(0xFF3B2D23),
                inverseSurface = Color(0xFFF2E6DA),
                inverseOnSurface = Color(0xFF1E1610),
                outline = Color(0xFF947E6A),
                outlineVariant = Color(0xFF4F3E30),
            )
        FrontendTheme.NETFLIX ->
            darkColorScheme(
                primary = Color(0xFFE50914),
                onPrimary = Color(0xFFFFFFFF),
                primaryContainer = Color(0xFF730008),
                onPrimaryContainer = Color(0xFFFFDAD6),
                inversePrimary = Color(0xFFB80710),
                secondary = Color(0xFFD4D4D4),
                onSecondary = Color(0xFF121212),
                secondaryContainer = Color(0xFF303030),
                onSecondaryContainer = Color(0xFFF0F0F0),
                tertiary = Color(0xFFFF4D5A),
                onTertiary = Color(0xFF2B0004),
                tertiaryContainer = Color(0xFF5C0A12),
                onTertiaryContainer = Color(0xFFFFD9DC),
                background = Color(0xFF0F0F0F),
                onBackground = Color(0xFFF8F8F8),
                surface = Color(0xFF161616),
                onSurface = Color(0xFFF8F8F8),
                surfaceVariant = Color(0xFF282828),
                onSurfaceVariant = Color(0xFFBDBDBD),
                surfaceTint = Color(0xFFE50914),
                surfaceContainerLowest = Color(0xFF0A0A0A),
                surfaceContainerLow = Color(0xFF141414),
                surfaceContainer = Color(0xFF1C1C1C),
                surfaceContainerHigh = Color(0xFF242424),
                surfaceContainerHighest = Color(0xFF2E2E2E),
                inverseSurface = Color(0xFFF2F2F2),
                inverseOnSurface = Color(0xFF141414),
                outline = Color(0xFF636363),
                outlineVariant = Color(0xFF333333),
            )
        FrontendTheme.CONSOLE ->
            darkColorScheme(
                primary = Color(0xFF298DFF),
                onPrimary = Color(0xFF001936),
                primaryContainer = Color(0xFF003E7E),
                onPrimaryContainer = Color(0xFFD6E9FF),
                inversePrimary = Color(0xFF005BB5),
                secondary = Color(0xFF00D4FF),
                onSecondary = Color(0xFF001F29),
                secondaryContainer = Color(0xFF004D66),
                onSecondaryContainer = Color(0xFFC2F2FF),
                tertiary = Color(0xFF7B9CFF),
                onTertiary = Color(0xFF091840),
                tertiaryContainer = Color(0xFF1E326B),
                onTertiaryContainer = Color(0xFFDBE4FF),
                background = Color(0xFF080D18),
                onBackground = Color(0xFFEDF4FF),
                surface = Color(0xFF0F1727),
                onSurface = Color(0xFFEDF4FF),
                surfaceVariant = Color(0xFF1B2740),
                onSurfaceVariant = Color(0xFFB5C7E3),
                surfaceTint = Color(0xFF298DFF),
                surfaceContainerLowest = Color(0xFF060A12),
                surfaceContainerLow = Color(0xFF0D1422),
                surfaceContainer = Color(0xFF131D30),
                surfaceContainerHigh = Color(0xFF1A2740),
                surfaceContainerHighest = Color(0xFF233352),
                inverseSurface = Color(0xFFE6F0FF),
                inverseOnSurface = Color(0xFF0C1424),
                outline = Color(0xFF4F678F),
                outlineVariant = Color(0xFF253654),
            )
        FrontendTheme.TV ->
            darkColorScheme(
                primary = Color(0xFF39FF14),
                onPrimary = Color(0xFF041C00),
                primaryContainer = Color(0xFF124F06),
                onPrimaryContainer = Color(0xFFBEFFAA),
                inversePrimary = Color(0xFF1A7A08),
                secondary = Color(0xFFFFB72B),
                onSecondary = Color(0xFF261700),
                secondaryContainer = Color(0xFF5C3E00),
                onSecondaryContainer = Color(0xFFFFE2A8),
                tertiary = Color(0xFF00E6A8),
                onTertiary = Color(0xFF002117),
                tertiaryContainer = Color(0xFF004F39),
                onTertiaryContainer = Color(0xFFA8FFE4),
                background = Color(0xFF080E09),
                onBackground = Color(0xFFDCF7DE),
                surface = Color(0xFF0E1810),
                onSurface = Color(0xFFDCF7DE),
                surfaceVariant = Color(0xFF1A2B1D),
                onSurfaceVariant = Color(0xFF9EC9A3),
                surfaceTint = Color(0xFF39FF14),
                surfaceContainerLowest = Color(0xFF050A06),
                surfaceContainerLow = Color(0xFF0C150E),
                surfaceContainer = Color(0xFF121F14),
                surfaceContainerHigh = Color(0xFF182A1B),
                surfaceContainerHighest = Color(0xFF213825),
                inverseSurface = Color(0xFFD8F2DA),
                inverseOnSurface = Color(0xFF0C170E),
                outline = Color(0xFF437048),
                outlineVariant = Color(0xFF223D26),
            )
        FrontendTheme.RETRO_80_90 ->
            darkColorScheme(
                primary = Color(0xFFFF2E88),
                onPrimary = Color(0xFFFFFFFF),
                primaryContainer = Color(0xFF750039),
                onPrimaryContainer = Color(0xFFFFD6E7),
                inversePrimary = Color(0xFFC2005B),
                secondary = Color(0xFF00F0FF),
                onSecondary = Color(0xFF002024),
                secondaryContainer = Color(0xFF00525C),
                onSecondaryContainer = Color(0xFFB8FBFF),
                tertiary = Color(0xFFFFD600),
                onTertiary = Color(0xFF261E00),
                tertiaryContainer = Color(0xFF5C4C00),
                onTertiaryContainer = Color(0xFFFFF0A6),
                background = Color(0xFF0B021C),
                onBackground = Color(0xFFF9EEFF),
                surface = Color(0xFF14052E),
                onSurface = Color(0xFFF9EEFF),
                surfaceVariant = Color(0xFF260D52),
                onSurfaceVariant = Color(0xFFD4BDF2),
                surfaceTint = Color(0xFFFF2E88),
                surfaceContainerLowest = Color(0xFF070114),
                surfaceContainerLow = Color(0xFF120429),
                surfaceContainer = Color(0xFF1A073B),
                surfaceContainerHigh = Color(0xFF240B4F),
                surfaceContainerHighest = Color(0xFF301066),
                inverseSurface = Color(0xFFF3E6FF),
                inverseOnSurface = Color(0xFF170633),
                outline = Color(0xFF7849B0),
                outlineVariant = Color(0xFF3E1A73),
            )
        FrontendTheme.ROCKSTAR ->
            darkColorScheme(
                primary = Color(0xFFFFB800),
                onPrimary = Color(0xFF140E00),
                primaryContainer = Color(0xFF5E4300),
                onPrimaryContainer = Color(0xFFFFE5A3),
                inversePrimary = Color(0xFF8A6300),
                secondary = Color(0xFFE6E6E6),
                onSecondary = Color(0xFF111111),
                secondaryContainer = Color(0xFF363636),
                onSecondaryContainer = Color(0xFFF5F5F5),
                tertiary = Color(0xFFFF7A00),
                onTertiary = Color(0xFF261000),
                tertiaryContainer = Color(0xFF5E2B00),
                onTertiaryContainer = Color(0xFFFFDCC2),
                background = Color(0xFF0B0B0B),
                onBackground = Color(0xFFF7F7F7),
                surface = Color(0xFF141414),
                onSurface = Color(0xFFF7F7F7),
                surfaceVariant = Color(0xFF262626),
                onSurfaceVariant = Color(0xFFC7C7C7),
                surfaceTint = Color(0xFFFFB800),
                surfaceContainerLowest = Color(0xFF070707),
                surfaceContainerLow = Color(0xFF121212),
                surfaceContainer = Color(0xFF1B1B1B),
                surfaceContainerHigh = Color(0xFF242424),
                surfaceContainerHighest = Color(0xFF2F2F2F),
                inverseSurface = Color(0xFFF2F2F2),
                inverseOnSurface = Color(0xFF121212),
                outline = Color(0xFF6E6E6E),
                outlineVariant = Color(0xFF363636),
            )
        FrontendTheme.THRASH_METAL ->
            darkColorScheme(
                primary = Color(0xFFD61A1A),
                onPrimary = Color(0xFFFFFFFF),
                primaryContainer = Color(0xFF630606),
                onPrimaryContainer = Color(0xFFFFD6D6),
                inversePrimary = Color(0xFF9E0B0B),
                secondary = Color(0xFFC2CAD2),
                onSecondary = Color(0xFF101215),
                secondaryContainer = Color(0xFF303740),
                onSecondaryContainer = Color(0xFFE6EBF0),
                tertiary = Color(0xFFEB6300),
                onTertiary = Color(0xFF260E00),
                tertiaryContainer = Color(0xFF5E2600),
                onTertiaryContainer = Color(0xFFFFDBC2),
                background = Color(0xFF09090B),
                onBackground = Color(0xFFF0F2F5),
                surface = Color(0xFF131418),
                onSurface = Color(0xFFF0F2F5),
                surfaceVariant = Color(0xFF22252C),
                onSurfaceVariant = Color(0xFFB5BDC7),
                surfaceTint = Color(0xFFC41212),
                surfaceContainerLowest = Color(0xFF060608),
                surfaceContainerLow = Color(0xFF111216),
                surfaceContainer = Color(0xFF191B21),
                surfaceContainerHigh = Color(0xFF22252D),
                surfaceContainerHighest = Color(0xFF2C303A),
                inverseSurface = Color(0xFFE8ECF0),
                inverseOnSurface = Color(0xFF121417),
                outline = Color(0xFF5D6570),
                outlineVariant = Color(0xFF2F333C),
            )
        FrontendTheme.THREE_D ->
            darkColorScheme(
                primary = Color(0xFF4DA6FF),
                onPrimary = Color(0xFF001B36),
                primaryContainer = Color(0xFF004985),
                onPrimaryContainer = Color(0xFFD6EBFF),
                inversePrimary = Color(0xFF0062B8),
                secondary = Color(0xFFFFA245),
                onSecondary = Color(0xFF2B1500),
                secondaryContainer = Color(0xFF633600),
                onSecondaryContainer = Color(0xFFFFDFC0),
                tertiary = Color(0xFF5CE1E6),
                onTertiary = Color(0xFF002022),
                tertiaryContainer = Color(0xFF004F52),
                onTertiaryContainer = Color(0xFFC4F9FB),
                background = Color(0xFF0B0E15),
                onBackground = Color(0xFFECF2FA),
                surface = Color(0xFF131823),
                onSurface = Color(0xFFECF2FA),
                surfaceVariant = Color(0xFF20283A),
                onSurfaceVariant = Color(0xFFAEBAD1),
                surfaceTint = Color(0xFF4DA6FF),
                surfaceContainerLowest = Color(0xFF070A10),
                surfaceContainerLow = Color(0xFF101520),
                surfaceContainer = Color(0xFF171E2D),
                surfaceContainerHigh = Color(0xFF1F283B),
                surfaceContainerHighest = Color(0xFF29344C),
                inverseSurface = Color(0xFFE8EEF7),
                inverseOnSurface = Color(0xFF111622),
                outline = Color(0xFF51607D),
                outlineVariant = Color(0xFF2B354A),
            )
    }
