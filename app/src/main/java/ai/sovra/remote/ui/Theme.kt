/*
 * Sovra design tokens, ported from the Sovra Harness design system
 * (`crates/sovra-ui/src/design/palette.rs` and `tokens.rs`) by way of the Expo app's
 * `src/theme/tokens.ts`. Raw colours live only here. Mobile adaptations: 44 dp touch
 * targets and 16 sp body text.
 */
package ai.sovra.remote.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private object Brand {
    val violet = Color(0xFF7C3AED)
    val violetDeep = Color(0xFF6D28D9)
    val violetLight = Color(0xFF9F67FF)
    val lilac = Color(0xFFB98CF0)
    /** Slightly deeper than brand indigo so white labels pass 4.5:1. */
    val indigoFill = Color(0xFF5566E6)
}

@Immutable
data class Palette(
    val dark: Boolean,
    val canvas: Color,
    val surface: Color,
    val hover: Color,
    val text: Color,
    val secondaryText: Color,
    val mutedText: Color,
    val link: Color,
    val border: Color,
    val controlBorder: Color,
    val danger: Color,
    val success: Color,
    val warning: Color,
    val ring: Color,
    val primary: Color,
    val primaryForeground: Color,
    val selection: Color,
    val composerBackground: Color,
    val composerBorder: Color,
    val composerFocusBorder: Color,
    val sendReadyBackground: Color,
    val sendReadyForeground: Color,
    val stopBackground: Color,
    val userBubble: Color,
    val codeBackground: Color,
) {
    /** 102° violet → indigo, the desktop's `button_primary`. */
    val primaryGradient: Brush get() = Brush.linearGradient(listOf(Brand.violet, Brand.indigoFill))
}

val DarkPalette = Palette(
    dark = true,
    canvas = Color(0xFF111216), surface = Color(0xFF1B1C22), hover = Color(0xFF262730),
    text = Color(0xFFEFF0F5), secondaryText = Color(0xFFB5B6C3), mutedText = Color(0xFFA0A2B2), link = Brand.lilac,
    border = Color.White.copy(alpha = 0.12f), controlBorder = Color(0xFF7D7F91),
    danger = Color(0xFFEF4444), success = Color(0xFF22C55E), warning = Color(0xFFF59E0B),
    ring = Brand.violetLight, primary = Brand.violet, primaryForeground = Color.White,
    selection = Brand.violet.copy(alpha = 0.24f),
    composerBackground = Color(0xFF1B1C22), composerBorder = Color.White.copy(alpha = 0.12f), composerFocusBorder = Brand.violet.copy(alpha = 0.6f),
    sendReadyBackground = Color.White, sendReadyForeground = Color(0xFF0A0A0F), stopBackground = Color(0xFFDC2626),
    userBubble = Color(0xFF262730), codeBackground = Color(0xFF0B0C0F),
)

val LightPalette = Palette(
    dark = false,
    canvas = Color(0xFFFCFCFE), surface = Color.White, hover = Color(0xFFEFEFF5),
    text = Color(0xFF22232C), secondaryText = Color(0xFF595B6B), mutedText = Color(0xFF666878), link = Brand.violetDeep,
    border = Brand.violet.copy(alpha = 0.10f), controlBorder = Color(0xFF868798),
    danger = Color(0xFFDC2626), success = Color(0xFF15803D), warning = Color(0xFFB45309),
    ring = Brand.violetDeep, primary = Brand.violet, primaryForeground = Color.White,
    selection = Brand.violet.copy(alpha = 0.10f),
    composerBackground = Color.White, composerBorder = Color(0x294C1D95), composerFocusBorder = Brand.violet.copy(alpha = 0.6f),
    sendReadyBackground = Color(0xFF0A0A0F), sendReadyForeground = Color.White, stopBackground = Color(0xFFDC2626),
    userBubble = Color(0xFFEFEFF5), codeBackground = Color(0xFFF4F4F8),
)

/** 2/4/6/8/16/24/32/48 — the desktop `SpaceTokens`. */
object Space {
    val hair = 2.dp
    val compact = 4.dp
    val snug = 6.dp
    val small = 8.dp
    val medium = 16.dp
    val large = 24.dp
    val extra = 32.dp
}

/** 6/10/10/14/18 — the desktop `RadiusTokens`. */
object Radius {
    val small = 6.dp
    val button = 10.dp
    val card = 14.dp
    val panel = 18.dp
}

object Layout {
    /** Minimum touch target (desktop uses 36 px). */
    val control = 44.dp
    val compactControl = 36.dp
}

/** Desktop roles: 36 welcome, 28 page title, 16 section title, body, 12 caption. */
object Type {
    val display = TextStyle(fontSize = 36.sp, lineHeight = 42.sp, fontWeight = FontWeight.Bold)
    val pageTitle = TextStyle(fontSize = 28.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold)
    val sectionTitle = TextStyle(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold)
    val body = TextStyle(fontSize = 16.sp, lineHeight = 24.sp)
    val label = TextStyle(fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold)
    val caption = TextStyle(fontSize = 13.sp, lineHeight = 18.sp)
}

val LocalPalette = staticCompositionLocalOf { DarkPalette }

@Composable
fun SovraTheme(appearance: ai.sovra.remote.data.Appearance, content: @Composable () -> Unit) {
    val dark = when (appearance) {
        ai.sovra.remote.data.Appearance.System -> isSystemInDarkTheme()
        ai.sovra.remote.data.Appearance.Dark -> true
        ai.sovra.remote.data.Appearance.Light -> false
    }
    val palette = if (dark) DarkPalette else LightPalette
    val scheme = (if (dark) darkColorScheme() else lightColorScheme()).copy(
        primary = palette.primary, onPrimary = palette.primaryForeground, background = palette.canvas, onBackground = palette.text,
        surface = palette.surface, onSurface = palette.text, error = palette.danger, outline = palette.controlBorder,
    )
    CompositionLocalProvider(LocalPalette provides palette) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
