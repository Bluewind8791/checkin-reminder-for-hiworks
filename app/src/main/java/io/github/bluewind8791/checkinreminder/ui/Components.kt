package io.github.bluewind8791.checkinreminder.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import io.github.bluewind8791.checkinreminder.BuildConfig
import io.github.bluewind8791.checkinreminder.ui.theme.JetBrainsMono
import io.github.bluewind8791.checkinreminder.ui.theme.NanumGothicCoding
import io.github.bluewind8791.checkinreminder.ui.theme.Palette

/** The blue bar at the top, under the status bar too. */
@Composable
fun BrandBar(content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Palette.brand)
            .statusBarsPadding()
            .heightIn(min = 56.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

/** App name, version, and the status pill. */
@Composable
fun TopBar(status: String, statusColor: Color) {
    BrandBar {
        Spacer(Modifier.width(16.dp))
        Text("출퇴근 알림", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Palette.onBrand)
        Spacer(Modifier.width(8.dp))
        Text("v${BuildConfig.VERSION_NAME}", fontFamily = JetBrainsMono, fontSize = 11.sp, color = Palette.onBrandSub)
        Spacer(Modifier.weight(1f))
        Row(
            modifier = Modifier
                .background(Palette.card, RoundedCornerShape(4.dp))
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(6.dp).background(statusColor, CircleShape))
            Spacer(Modifier.width(6.dp))
            Text(status, fontFamily = JetBrainsMono, fontSize = 11.sp, color = statusColor)
        }
        Spacer(Modifier.width(16.dp))
    }
}

/** A `// …` line. Uses the Hangul font because comments mix Hangul and code. */
@Composable
fun CommentLine(text: String, color: Color = Palette.textMuted) {
    Text("// $text", fontSize = 12.sp, color = color)
}

@Composable
fun BigTime(text: String, color: Color) {
    Text(
        text,
        fontFamily = JetBrainsMono,
        fontWeight = FontWeight.Bold,
        fontSize = 84.sp,
        letterSpacing = (-0.06).em,
        color = color,
    )
}

/** `key = value // comment`. */
@Composable
fun AssignLine(
    key: String,
    value: String,
    valueColor: Color,
    comment: String? = null,
    commentColor: Color = Palette.textMuted,
    modifier: Modifier = Modifier,
) {
    Text(
        buildAnnotatedString {
            withStyle(SpanStyle(fontFamily = JetBrainsMono, color = Palette.key)) { append(key) }
            withStyle(SpanStyle(fontFamily = JetBrainsMono, color = Palette.textSub)) { append(" = ") }
            withStyle(SpanStyle(fontFamily = JetBrainsMono, color = valueColor)) { append(value) }
            if (comment != null) {
                withStyle(SpanStyle(fontFamily = NanumGothicCoding, color = commentColor)) { append("  // $comment") }
            }
        },
        modifier = modifier,
        fontSize = 14.sp,
    )
}

/** Tag + sentence (+ small note) (+ button). `WARN`, `401` and similar. */
@Composable
fun MessagePanel(
    tag: String,
    tagColor: Color,
    background: Color,
    borderColor: Color,
    text: String,
    note: String? = null,
    action: String? = null,
    onAction: () -> Unit = {},
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(background, RoundedCornerShape(6.dp))
            .border(1.dp, borderColor, RoundedCornerShape(6.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row {
            Text(
                tag,
                modifier = Modifier
                    .background(tagColor, RoundedCornerShape(3.dp))
                    .padding(horizontal = 5.dp, vertical = 1.dp),
                fontFamily = JetBrainsMono,
                fontWeight = FontWeight.Bold,
                fontSize = 10.sp,
                color = Palette.onBrand,
            )
            Spacer(Modifier.width(8.dp))
            Text(text, fontSize = 13.sp, color = Palette.messageText, lineHeight = 19.sp)
        }
        if (note != null) {
            Text(note, fontSize = 11.sp, color = Palette.textMuted, lineHeight = 16.sp)
        }
        if (action != null) {
            AppButton(action, primary = true, color = tagColor, onClick = onAction, modifier = Modifier.fillMaxWidth())
        }
    }
}

/** 48dp button. The primary one is filled, the secondary one has a border. */
@Composable
fun AppButton(
    text: String,
    primary: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    color: Color = Palette.brand,
) {
    val shape = RoundedCornerShape(6.dp)
    val base = modifier.heightIn(min = 48.dp).clip(shape)
    val styled = if (primary) {
        base.background(if (enabled) color else Palette.surface, shape)
    } else {
        base.background(Palette.card, shape).border(1.dp, Palette.border, shape)
    }
    Box(
        modifier = styled.clickable(enabled = enabled, onClick = onClick).padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            fontSize = if (primary) 14.sp else 13.sp,
            fontWeight = if (primary) FontWeight.Bold else FontWeight.Normal,
            color = when {
                !enabled -> Palette.textFaint
                primary -> Palette.onBrand
                else -> Palette.textSub
            },
        )
    }
}
