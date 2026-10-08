package com.aioapp.enroll.ui

import android.app.Activity
import android.content.ContextWrapper
import android.view.View
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// AIO coral, as a Material 3 scheme. The same values as option A in the demo page.
private val Light = lightColorScheme(
    primary = Color(0xFFC4432A), onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDAD3), onPrimaryContainer = Color(0xFF3D0700),
    secondary = Color(0xFF775651), onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFDAD3), onSecondaryContainer = Color(0xFF2C1512),
    background = Color(0xFFFFF8F6), onBackground = Color(0xFF231917),
    surface = Color(0xFFFFF8F6), onSurface = Color(0xFF231917),
    surfaceVariant = Color(0xFFF5DDD8), onSurfaceVariant = Color(0xFF53433F),
    surfaceContainerLowest = Color(0xFFFFFFFF), surfaceContainerLow = Color(0xFFFDEEE9),
    surfaceContainer = Color(0xFFF8E5DF), surfaceContainerHigh = Color(0xFFF2DFD9),
    surfaceContainerHighest = Color(0xFFECD9D3),
    outline = Color(0xFFD8C2BC), outlineVariant = Color(0xFFEBD3CD),
    error = Color(0xFFBA1A1A), onError = Color.White,
    errorContainer = Color(0xFFFFDAD6), onErrorContainer = Color(0xFF410002),
)

private val Dark = darkColorScheme(
    primary = Color(0xFFFFB4A5), onPrimary = Color(0xFF5F1505),
    primaryContainer = Color(0xFF7F2B19), onPrimaryContainer = Color(0xFFFFDAD3),
    secondary = Color(0xFFE7BDB6), onSecondary = Color(0xFF442925),
    secondaryContainer = Color(0xFF5D3F3B), onSecondaryContainer = Color(0xFFFFDAD3),
    background = Color(0xFF1A1110), onBackground = Color(0xFFF1DFDB),
    surface = Color(0xFF1A1110), onSurface = Color(0xFFF1DFDB),
    surfaceVariant = Color(0xFF53433F), onSurfaceVariant = Color(0xFFD8C2BC),
    surfaceContainerLowest = Color(0xFF140C0B), surfaceContainerLow = Color(0xFF271D1B),
    surfaceContainer = Color(0xFF32282A), surfaceContainerHigh = Color(0xFF3D3230),
    surfaceContainerHighest = Color(0xFF493D3B),
    outline = Color(0xFF53433F), outlineVariant = Color(0xFF433330),
    error = Color(0xFFFFB4AB), onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A), onErrorContainer = Color(0xFFFFDAD6),
)

/** The two colours Material 3 has no slot for: "this is fine" and "look at this". */
data class Extra(val ok: Color, val okContainer: Color, val warn: Color, val warnContainer: Color)

private val ExtraLight = Extra(Color(0xFF1B6C3C), Color(0xFFB8F1C7), Color(0xFF7A5900), Color(0xFFFFDF99))
private val ExtraDark = Extra(Color(0xFF9CD5AC), Color(0xFF0D5229), Color(0xFFF2C14B), Color(0xFF5C4300))

val LocalExtra = compositionLocalOf { ExtraLight }

@Composable
fun AioTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val view = LocalView.current
    if (!view.isInEditMode) SideEffect {
        val w = view.activity()?.window ?: return@SideEffect
        WindowCompat.getInsetsController(w, view).isAppearanceLightStatusBars = !dark
    }
    CompositionLocalProvider(LocalExtra provides if (dark) ExtraDark else ExtraLight) {
        MaterialTheme(colorScheme = if (dark) Dark else Light, typography = Typography(), content = content)
    }
}

private fun View.activity(): Activity? {
    var c = context
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}
