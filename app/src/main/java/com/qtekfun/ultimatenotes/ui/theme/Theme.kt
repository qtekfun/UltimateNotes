// SPDX-FileCopyrightText: 2026 UltimateNotes contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatenotes.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.qtekfun.ultimatenotes.data.settings.ThemeMode

internal val LightColors = lightColorScheme(
    primary = Blue40,
    secondary = Orange40,
    tertiary = Red40
)

internal val DarkColors = darkColorScheme(
    primary = Blue80,
    secondary = Orange80,
    tertiary = Red80
)

/**
 * The color scheme for [options]. [dynamicLight] and [dynamicDark] are the wallpaper colors
 * (Android 12+), or null where they do not exist.
 */
fun colorSchemeFor(
    options: ThemeOptions,
    systemDark: Boolean,
    dynamicLight: ColorScheme?,
    dynamicDark: ColorScheme?
): ColorScheme {
    val dark = when (options.mode) {
        ThemeMode.SYSTEM -> systemDark
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val dynamic = if (dark) dynamicDark else dynamicLight
    val scheme =
        (if (options.dynamicColor) dynamic else null) ?: if (dark) DarkColors else LightColors
    return if (dark && options.amoled) scheme.toAmoled() else scheme
}

/** Pure black behind everything, for OLED screens; containers stay just visible. */
internal fun ColorScheme.toAmoled() = copy(
    background = Color.Black,
    surface = Color.Black,
    surfaceDim = Color.Black,
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = AmoledLow,
    surfaceContainer = AmoledContainer,
    surfaceContainerHigh = AmoledHigh,
    surfaceContainerHighest = AmoledHighest,
    surfaceBright = AmoledHighest
)

/** Material 3 theme: wallpaper colors where the system provides them, dark and AMOLED options. */
@Composable
fun UltimateNotesTheme(options: ThemeOptions = ThemeOptions(), content: @Composable () -> Unit) {
    val context = LocalContext.current
    val dynamic = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val colorScheme = colorSchemeFor(
        options = options,
        systemDark = isSystemInDarkTheme(),
        dynamicLight = if (dynamic) dynamicLightColorScheme(context) else null,
        dynamicDark = if (dynamic) dynamicDarkColorScheme(context) else null
    )
    SystemBarIcons(light = colorScheme.background.luminance() > LIGHT_BACKGROUND)
    MaterialTheme(colorScheme = colorScheme, content = content)
}

/**
 * Dark icons over a light app, light icons over a dark one. The edge-to-edge default follows the
 * system theme, so a light app on a dark system (or the reverse) had invisible clock and battery.
 */
@Composable
private fun SystemBarIcons(light: Boolean) {
    val view = LocalView.current
    if (view.isInEditMode) return
    SideEffect {
        val window = (view.context as? Activity)?.window ?: return@SideEffect
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = light
            isAppearanceLightNavigationBars = light
        }
    }
}

private const val LIGHT_BACKGROUND = 0.5f
