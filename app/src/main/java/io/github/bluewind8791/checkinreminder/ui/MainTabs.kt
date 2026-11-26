package io.github.bluewind8791.checkinreminder.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.bluewind8791.checkinreminder.BuildConfig
import io.github.bluewind8791.checkinreminder.ui.theme.JetBrainsMono
import io.github.bluewind8791.checkinreminder.ui.theme.Palette

enum class MainTab { Start, End, Settings }

enum class UpdateCheck { Idle, Busy, Latest, Failed }

/** Space the floating dock covers at the bottom of the screen. */
val DOCK_SPACE = 112.dp

@Composable
fun TabTitle(text: String) {
    Text(text, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Palette.text)
}

/** Floats over the bottom of the screen: the tabs, then 「지금 확인」. */
@Composable
fun Dock(
    tab: MainTab,
    onTab: (MainTab) -> Unit,
    checkedIn: Boolean,
    permissionMissing: Boolean,
    busy: Boolean,
    checkEnabled: Boolean,
    onCheck: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(34.dp)
    Row(
        modifier
            .shadow(12.dp, shape)
            .background(Palette.card, shape)
            .border(1.dp, Palette.border, shape)
            .padding(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            DockTab(
                AppIcons.checkIn,
                "출근",
                selected = tab == MainTab.Start,
                badge = if (checkedIn) Palette.green to "출근 확인됨" else null,
                onClick = { onTab(MainTab.Start) },
            )
            DockTab(
                AppIcons.logOut,
                "퇴근",
                selected = tab == MainTab.End,
                badge = null,
                onClick = { onTab(MainTab.End) },
            )
            DockTab(
                AppIcons.settings,
                "설정",
                selected = tab == MainTab.Settings,
                badge = if (permissionMissing) Palette.orange to "확인할 권한 있음" else null,
                onClick = { onTab(MainTab.Settings) },
            )
        }
        Box(Modifier.padding(horizontal = 8.dp).width(1.dp).height(32.dp).background(Palette.divider))
        val buttonShape = RoundedCornerShape(28.dp)
        Row(
            Modifier
                .height(56.dp)
                .clip(buttonShape)
                .background(if (checkEnabled) Palette.brand else Palette.surface, buttonShape)
                .clickable(enabled = checkEnabled, onClick = onCheck)
                .padding(start = 16.dp, end = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val color = if (checkEnabled) Palette.onBrand else Palette.textFaint
            Icon(AppIcons.refresh, contentDescription = null, modifier = Modifier.size(18.dp), tint = color)
            Spacer(Modifier.width(8.dp))
            Text(if (busy) "확인하는 중" else "지금 확인", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = color)
        }
    }
}

/** A badge is a small dot with what it means for screen readers. */
@Composable
private fun DockTab(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    badge: Pair<Color, String>?,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(28.dp)
    val color = if (selected) Palette.key else Palette.textMuted
    Box(
        Modifier
            .size(width = 58.dp, height = 56.dp)
            .clip(shape)
            .background(if (selected) Palette.brandTint else Color.Transparent, shape)
            .selectable(selected = selected, role = Role.Tab, onClick = onClick),
    ) {
        Column(
            Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp), tint = color)
            Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = color)
        }
        if (badge != null) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 6.dp, end = 10.dp)
                    .size(8.dp)
                    .background(badge.first, CircleShape)
                    .border(2.dp, Palette.card, CircleShape)
                    .semantics { contentDescription = badge.second },
            )
        }
    }
}

/** 「퇴근 알림 ON」 or 「퇴근 알림 OFF」. Tapping it turns the end alerts on or off. */
@Composable
fun EndToggle(on: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    val color = if (on) Palette.key else Palette.textSub
    Row(
        Modifier
            .height(40.dp)
            .clip(shape)
            .background(if (on) Palette.brandTint else Palette.card, shape)
            .border(if (on) 1.5.dp else 1.dp, if (on) Palette.brand else Palette.border, shape)
            .toggleable(value = on, role = Role.Switch, onValueChange = { onClick() })
            .padding(start = 12.dp, end = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(if (on) AppIcons.bell else AppIcons.bellOff, contentDescription = null, modifier = Modifier.size(16.dp), tint = color)
        Spacer(Modifier.width(6.dp))
        Text("퇴근 알림 ", fontSize = 13.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.Normal, color = color)
        Text(
            if (on) "ON" else "OFF",
            fontFamily = JetBrainsMono,
            fontSize = 13.sp,
            fontWeight = if (on) FontWeight.Bold else FontWeight.Normal,
            color = color,
        )
    }
}

/** Permissions, the version and the update check, and logout (null when logged out). */
@Composable
fun SettingsTab(
    permissions: Permissions,
    updateCheck: UpdateCheck,
    onCheckUpdate: () -> Unit,
    onLogout: (() -> Unit)?,
) {
    val context = LocalContext.current
    TabTitle("설정")
    SectionLabel("권한")
    SettingsCard {
        PermissionRow("알림 권한", null, permissions.notify, Palette.red) { context.openNotificationSettings() }
        permissions.exactAlarm?.let {
            CardDivider()
            PermissionRow("정확한 알람", null, it, Palette.red) { context.openExactAlarmSettings() }
        }
        CardDivider()
        // Recommended only: the alarms ran on time for six days without it (Galaxy, 2026-11).
        PermissionRow("배터리 최적화 예외", "권장 · 알림이 늦게 오면 켜세요", permissions.batteryOpt, Palette.orange) {
            context.requestBatteryExemption()
        }
    }
    SectionLabel("앱")
    SettingsCard {
        Row(Modifier.fillMaxWidth().heightIn(min = 52.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("버전 ", fontSize = 14.sp, color = Palette.text)
            Text("v${BuildConfig.VERSION_NAME}", fontFamily = JetBrainsMono, fontSize = 14.sp, color = Palette.textSub)
            Spacer(Modifier.weight(1f))
            val canCheck = updateCheck == UpdateCheck.Idle || updateCheck == UpdateCheck.Failed
            Text(
                when (updateCheck) {
                    UpdateCheck.Idle -> "새 버전 확인"
                    UpdateCheck.Busy -> "확인하는 중"
                    UpdateCheck.Latest -> "최신 버전입니다"
                    UpdateCheck.Failed -> "확인 실패 · 다시 확인"
                },
                modifier = Modifier
                    .heightIn(min = 44.dp)
                    .clickable(enabled = canCheck, onClick = onCheckUpdate)
                    .padding(horizontal = 4.dp, vertical = 12.dp),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = if (canCheck) Palette.brand else Palette.textMuted,
            )
        }
        if (onLogout != null) {
            CardDivider()
            Row(
                Modifier.fillMaxWidth().heightIn(min = 52.dp).clickable(onClick = onLogout),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(AppIcons.logOut, contentDescription = null, modifier = Modifier.size(18.dp), tint = Palette.red)
                Spacer(Modifier.width(10.dp))
                Text("로그아웃", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Palette.red)
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp), fontSize = 12.sp, color = Palette.textMuted)
}

@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(10.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .background(Palette.card, shape)
            .border(1.dp, Palette.border, shape)
            .padding(horizontal = 14.dp),
        content = content,
    )
}

@Composable
private fun CardDivider() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(Palette.divider))
}

/** A missing permission opens its settings screen. */
@Composable
private fun PermissionRow(label: String, note: String?, granted: Boolean, deniedColor: Color, onOpen: () -> Unit) {
    val color = if (granted) Palette.green else deniedColor
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = if (note != null) 60.dp else 52.dp)
            .clickable(enabled = !granted, onClick = onOpen),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, fontSize = 14.sp, color = Palette.text)
            if (note != null) Text(note, fontSize = 12.sp, color = Palette.textMuted)
        }
        Box(Modifier.size(6.dp).background(color, CircleShape))
        Spacer(Modifier.width(6.dp))
        Text(if (granted) "허용" else "꺼짐", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = color)
        if (!granted) {
            Spacer(Modifier.width(8.dp))
            Text("›", fontSize = 14.sp, color = Palette.textMuted)
        }
    }
}
