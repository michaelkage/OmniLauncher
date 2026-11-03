package dev.omni.launcher

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

@Composable
fun OmniTheme(
    mode: String,
    palette: String,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val dark = when (mode) {
        "light" -> false
        "dark" -> true
        else -> isSystemInDarkTheme()
    }

    val colors = if (palette == "dynamic" && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else {
        when (palette) {
            "ocean" -> if (dark) darkColorScheme(
                primary = Color(0xFF8CCBFF), secondary = Color(0xFFB4C9E8),
                tertiary = Color(0xFF70D8D0), background = Color(0xFF0D1B26),
                surface = Color(0xFF132633)
            ) else lightColorScheme(
                primary = Color(0xFF006493), secondary = Color(0xFF4D6078),
                tertiary = Color(0xFF006B65), background = Color(0xFFF5FAFF),
                surface = Color(0xFFF5FAFF)
            )
            "forest" -> if (dark) darkColorScheme(
                primary = Color(0xFFA4D0A2), secondary = Color(0xFFBECBB5),
                tertiary = Color(0xFFE0C28C), background = Color(0xFF151E16),
                surface = Color(0xFF1D2A1E)
            ) else lightColorScheme(
                primary = Color(0xFF386A3A), secondary = Color(0xFF53634D),
                tertiary = Color(0xFF765A20), background = Color(0xFFF7FAF2),
                surface = Color(0xFFF7FAF2)
            )
            "plum" -> if (dark) darkColorScheme(
                primary = Color(0xFFE0B9FF), secondary = Color(0xFFD2C2DC),
                tertiary = Color(0xFFFFB4A8), background = Color(0xFF211629),
                surface = Color(0xFF2C2035)
            ) else lightColorScheme(
                primary = Color(0xFF70418C), secondary = Color(0xFF665A70),
                tertiary = Color(0xFF98534A), background = Color(0xFFFFF7FF),
                surface = Color(0xFFFFF7FF)
            )
            else -> if (dark) darkColorScheme() else lightColorScheme()
        }
    }

    MaterialTheme(colorScheme = colors, content = content)
}
