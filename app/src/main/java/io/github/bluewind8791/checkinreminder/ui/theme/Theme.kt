package io.github.bluewind8791.checkinreminder.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.intl.LocaleList
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.unit.em
import io.github.bluewind8791.checkinreminder.R

/** Light theme matched to the app icon. */
object Palette {
    val bg = Color(0xFFF6F8FA)
    val card = Color(0xFFFFFFFF)
    val surface = Color(0xFFEEF2F6)
    val border = Color(0xFFD8DEE5)
    val divider = Color(0xFFEEF1F4)
    val text = Color(0xFF1B2430)
    val textSub = Color(0xFF4A5562)
    val textMuted = Color(0xFF66717E)
    val textFaint = Color(0xFF8A939E)
    val textDim = Color(0xFFB5BDC6)

    /** Hiworks blue, the app icon's background. The top bar and primary buttons. */
    val brand = Color(0xFF0080DD)
    val onBrand = Color(0xFFFFFFFF)
    val onBrandSub = Color(0xFFCFE6F8)
    /** The app icon's orange, which marks the 10 minutes before the start time. */
    val alarm = Color(0xFFFF9A3D)
    val alarmBg = Color(0xFFFFF4EA)

    /** The current step of the first-run setup. */
    val brandTint = Color(0xFFEEF6FD)

    val green = Color(0xFF1F7A3A)
    val greenBg = Color(0xFFE3F1E7)
    val red = Color(0xFFC4321C)
    val orange = Color(0xFFC25E00)

    // Syntax colors of `key = value` lines.
    val key = Color(0xFF0068B5)
    val number = Color(0xFF1B2430)
    val string = Color(0xFF1F7A3A)
    val keyword = Color(0xFFC25E00)

    val warnBg = Color(0xFFFFF3EC)
    val warnBorder = Color(0xFFF3C9B2)
    val errorBg = Color(0xFFFDEDEA)
    val errorBorder = Color(0xFFF2C4BC)
    val messageText = Color(0xFF5A2E17)
}

/** Numbers and code elements. Has no Hangul. */
val JetBrainsMono = FontFamily(
    Font(R.font.jetbrains_mono_regular, FontWeight.Normal),
    Font(R.font.jetbrains_mono_bold, FontWeight.Bold),
)

/** The default font. Covers Hangul, Latin, and digits. */
val NanumGothicCoding = FontFamily(
    Font(R.font.nanum_gothic_coding_regular, FontWeight.Normal),
    Font(R.font.nanum_gothic_coding_bold, FontWeight.Bold),
)

// Phrase breaking keeps Korean words whole (Android 13 and later). It needs the Korean locale,
// whatever the system language is. Older versions break at any syllable.
private val baseText = TextStyle(
    localeList = LocaleList("ko-KR"),
    fontFamily = NanumGothicCoding,
    color = Palette.text,
    // The font's own line metrics are tight, so lines touch without this.
    lineHeight = 1.5.em,
    lineBreak = LineBreak.Paragraph.copy(wordBreak = LineBreak.WordBreak.Phrase),
)

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Palette.brand,
            onPrimary = Palette.onBrand,
            background = Palette.bg,
            onBackground = Palette.text,
            surface = Palette.surface,
            onSurface = Palette.text,
            error = Palette.red,
        ),
        typography = Typography(
            bodyLarge = baseText,
            bodyMedium = baseText,
            bodySmall = baseText,
            labelLarge = baseText,
            titleLarge = baseText,
            headlineSmall = baseText,
        ),
        content = content,
    )
}
