package com.jerry.wattnow.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

// Apple System Palette - Dark Mode
private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF0A84FF),      // Apple System Blue (Dark)
    secondary = Color(0xFF30D158),    // Apple System Green (Dark)
    tertiary = Color(0xFFFF9F0A),     // Apple System Orange (Dark)
    background = Color(0xFF000000),   // True OLED Black
    surface = Color(0xFF14161E),      // Elevated Dark Surface
    surfaceVariant = Color(0xFF1C1E26),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = Color(0xFFF5F5F7), // Crisp White
    onSurface = Color(0xFFF5F5F7),
    onSurfaceVariant = Color(0xFF9898A0) // SF Gray
)

// Apple System Palette - Light Mode
private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF007AFF),      // Apple System Blue (Light)
    secondary = Color(0xFF34C759),    // Apple System Green (Light)
    tertiary = Color(0xFFFF9500),     // Apple System Orange (Light)
    background = Color(0xFFF2F2F7),   // Apple System Gray 6
    surface = Color(0xFFFFFFFF),      // Clean Card White
    surfaceVariant = Color(0xFFE5E5EA),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = Color(0xFF000000), // Pure Black
    onSurface = Color(0xFF1C1C1E),
    onSurfaceVariant = Color(0xFF6C6C70) // SF Dark Gray
)

/**
 * 遵循 Apple HIG 规范的 16pt 磨砂玻璃修饰符
 */
fun Modifier.appleFrostedGlass(
    cornerRadius: Dp = 16.dp,
    isDark: Boolean = true
): Modifier = if (isDark) {
    this
        .clip(RoundedCornerShape(cornerRadius))
        .background(
            Brush.verticalGradient(
                colors = listOf(
                    Color(0xFF222530).copy(alpha = 0.72f),
                    Color(0xFF161820).copy(alpha = 0.62f)
                )
            )
        )
        .border(
            width = 1.dp,
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.18f),
                    Color.White.copy(alpha = 0.05f)
                )
            ),
            shape = RoundedCornerShape(cornerRadius)
        )
} else {
    this
        .shadow(
            elevation = 4.dp,
            shape = RoundedCornerShape(cornerRadius),
            ambientColor = Color.Black.copy(alpha = 0.04f),
            spotColor = Color.Black.copy(alpha = 0.06f)
        )
        .clip(RoundedCornerShape(cornerRadius))
        .background(
            Brush.verticalGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.90f),
                    Color.White.copy(alpha = 0.78f)
                )
            )
        )
        .border(
            width = 1.dp,
            color = Color.Black.copy(alpha = 0.06f),
            shape = RoundedCornerShape(cornerRadius)
        )
}

@Composable
fun WattNowTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                window.statusBarColor = android.graphics.Color.TRANSPARENT
                window.navigationBarColor = android.graphics.Color.TRANSPARENT
                val insetsController = WindowCompat.getInsetsController(window, view)
                insetsController.isAppearanceLightStatusBars = !darkTheme
                insetsController.isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
