package io.github.bluewind8791.checkinreminder.ui

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import kotlinx.coroutines.launch
import io.github.bluewind8791.checkinreminder.BuildConfig
import io.github.bluewind8791.checkinreminder.alarm.AlarmWork
import io.github.bluewind8791.checkinreminder.data.CookieStore
import io.github.bluewind8791.checkinreminder.data.HiworksClient
import io.github.bluewind8791.checkinreminder.data.Settings
import io.github.bluewind8791.checkinreminder.data.SettingsStore
import io.github.bluewind8791.checkinreminder.domain.AlarmKind
import io.github.bluewind8791.checkinreminder.domain.AlarmPlan
import io.github.bluewind8791.checkinreminder.domain.CheckResult
import io.github.bluewind8791.checkinreminder.domain.ExpectedTime
import io.github.bluewind8791.checkinreminder.domain.checkNow
import io.github.bluewind8791.checkinreminder.ui.theme.AppTheme
import io.github.bluewind8791.checkinreminder.ui.theme.JetBrainsMono
import io.github.bluewind8791.checkinreminder.ui.theme.Palette
import io.github.bluewind8791.checkinreminder.update.Release
import io.github.bluewind8791.checkinreminder.update.UpdateClient
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Main screen: the session in the top bar, a tab (출근 or 설정), and the floating dock
 * with the tabs and 「지금 확인」. The first-run setup takes its place until it is over.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
        )
        setContent { AppTheme { MainScreen() } }
    }
}

private val DATE = DateTimeFormatter.ofPattern("M월 d일 (E)", Locale.KOREAN)
private val TIME = DateTimeFormatter.ofPattern("HH:mm")

@Composable
private fun MainScreen() {
    val context = LocalContext.current
    val store = remember { CookieStore(context) }
    val settingsStore = remember { SettingsStore(context) }
    val settings by settingsStore.settings.collectAsState(initial = Settings())
    val scope = rememberCoroutineScope()
    // null while the saved cookie is being read.
    var loggedIn by remember { mutableStateOf<Boolean?>(null) }
    var result by remember { mutableStateOf<CheckResult?>(null) }
    var checkedAt by remember { mutableStateOf<LocalTime?>(null) }
    var busy by remember { mutableStateOf(false) }
    var confirmLogout by remember { mutableStateOf(false) }
    var editStart by remember { mutableStateOf(false) }
    var editAlerts by remember { mutableStateOf(false) }
    var editEnd by remember { mutableStateOf(false) }
    var editEndAlerts by remember { mutableStateOf(false) }
    val updateClient = remember { UpdateClient.create() }
    var update by remember { mutableStateOf<Release?>(null) }
    var permissions by remember { mutableStateOf(Permissions.read(context)) }
    // Shown when there is no saved session and the setup was never finished.
    var setup by remember { mutableStateOf(false) }
    var tab by rememberSaveable { mutableStateOf(MainTab.Start) }
    var updateCheck by remember { mutableStateOf(UpdateCheck.Idle) }

    fun check() {
        if (busy) return
        busy = true
        scope.launch {
            val sessionId = store.load()
            if (sessionId == null) {
                loggedIn = false
                result = null
            } else {
                val saved = settingsStore.read()
                result = checkNow(HiworksClient.create(sessionId), LocalDate.now(), saved.manualStart, saved.manualEnd)
                checkedAt = LocalTime.now()
            }
            busy = false
        }
    }

    val login = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (it.resultCode == Activity.RESULT_OK) {
            loggedIn = true
            check()
        }
    }
    val openLogin = { login.launch(Intent(context, LoginActivity::class.java)) }

    // The user may change a permission in the system settings and come back.
    LifecycleResumeEffect(Unit) {
        permissions = Permissions.read(context)
        onPauseOrDispose {}
    }

    LaunchedEffect(Unit) {
        val hasSession = store.load() != null
        val setupDone = settingsStore.read().setupDone
        // A user of an older version already set things up.
        if (hasSession && !setupDone) settingsStore.setSetupDone()
        setup = !hasSession && !setupDone
        loggedIn = hasSession
        if (loggedIn == true) {
            check()
            // A force stop drops the alarms.
            AlarmWork.ensureScheduled(context.applicationContext)
        }
    }

    // A debug build has a different key, so a release APK cannot update it.
    if (!BuildConfig.DEBUG) {
        LaunchedEffect(Unit) { update = updateClient.newer(BuildConfig.VERSION_CODE) }
    }

    if (setup) {
        SetupScreen(
            permissions,
            onPermissionsChanged = { permissions = Permissions.read(context) },
            onDone = {
                scope.launch {
                    settingsStore.setSetupDone()
                    setup = false
                    loggedIn = store.load() != null
                    if (loggedIn == true) check()
                }
            },
        )
        return
    }

    Box(Modifier.fillMaxSize().background(Palette.bg)) {
        Column(Modifier.fillMaxSize()) {
            // The latest call that reached Hiworks with the session, from the app or the background.
            val lastOk = listOfNotNull(
                checkedAt?.takeIf { result is CheckResult.Ok }?.let { LocalDate.now().atTime(it) },
                settings.lastCallAt?.takeIf { settings.lastCallResult == "200" },
            ).maxOrNull()
            val (status, statusColor) = when {
                loggedIn != true -> "session: none" to Palette.textFaint
                result == CheckResult.SessionExpired -> "session: expired" to Palette.red
                result == null && settings.lastCallResult == "401" -> "session: expired" to Palette.red
                lastOk != null -> "session: ${lastOk.format(TIME)}" to Palette.green
                else -> "session: --:--" to Palette.textFaint
            }
            TopBar(status, statusColor)
            Column(
                Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Spacer(Modifier.height(8.dp))
                when (tab) {
                    MainTab.Start -> {
                        TabTitle("출근")
                        val today = LocalDate.now()
                        val current = result
                        when {
                            loggedIn == null -> Unit
                            loggedIn == false -> LoggedOutView(openLogin)
                            current == null -> {
                                CommentLine(if (busy) "확인하는 중" else "「지금 확인」을 누르세요")
                                BigTime("--:--", Palette.textDim)
                                NotifyWarning(permissions)
                            }
                            else -> CheckResultView(
                                current,
                                today,
                                checkedAt,
                                settings.manualStart != null,
                                settings.alerts,
                                onRelogin = openLogin,
                                warning = { NotifyWarning(permissions) },
                            )
                        }
                        if (loggedIn == true) {
                            Spacer(Modifier.height(8.dp))
                            AlarmLines(settings, onEditStart = { editStart = true }, onEditAlerts = { editAlerts = true })
                        }
                    }
                    MainTab.End -> {
                        TabTitle("퇴근")
                        when (loggedIn) {
                            null -> Unit
                            false -> LoggedOutView(openLogin)
                            true -> EndView(
                                result,
                                LocalDate.now(),
                                checkedAt,
                                settings,
                                busy,
                                onToggle = {
                                    scope.launch {
                                        val on = !settings.endOn
                                        settingsStore.setEndOn(on)
                                        // A schedule cached by an older version has no end times: fetch it again.
                                        if (on) AlarmWork.keepAlive(context.applicationContext) else AlarmWork.replan(context.applicationContext)
                                    }
                                },
                                onRelogin = openLogin,
                                onEditEnd = { editEnd = true },
                                onEditAlerts = { editEndAlerts = true },
                                warning = { NotifyWarning(permissions) },
                            )
                        }
                    }
                    MainTab.Settings -> SettingsTab(
                        permissions,
                        updateCheck,
                        onCheckUpdate = {
                            updateCheck = UpdateCheck.Busy
                            scope.launch {
                                val release = updateClient.newer(BuildConfig.VERSION_CODE)
                                update = release
                                updateCheck = if (release == null) UpdateCheck.Latest else UpdateCheck.Idle
                            }
                        },
                        onLogout = if (loggedIn == true) ({ confirmLogout = true }) else null,
                    )
                }
                // Room for the dock, so the last line can scroll above it.
                Spacer(Modifier.navigationBarsPadding().height(DOCK_SPACE))
            }
        }
        Dock(
            tab = tab,
            onTab = { tab = it },
            checkedIn = (result as? CheckResult.Ok)?.checkedIn == true,
            permissionMissing = !permissions.notify || permissions.exactAlarm == false || !permissions.batteryOpt,
            busy = busy,
            checkEnabled = loggedIn == true && !busy,
            onCheck = ::check,
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 24.dp),
        )
    }

    if (confirmLogout) {
        AlertDialog(
            onDismissRequest = { confirmLogout = false },
            text = { Text("저장한 세션과 알람을 지웁니다") },
            confirmButton = {
                TextButton(onClick = {
                    confirmLogout = false
                    scope.launch {
                        AlarmWork.logout(context.applicationContext)
                        loggedIn = false
                        result = null
                        checkedAt = null
                    }
                }) { Text("로그아웃", color = Palette.red) }
            },
            dismissButton = {
                TextButton(onClick = { confirmLogout = false }) { Text("취소", color = Palette.textSub) }
            },
            containerColor = Palette.card,
        )
    }

    update?.let { release ->
        UpdateDialog(release, updateClient, onDismiss = { update = null })
    }

    if (editStart) {
        StartTimeDialog(
            initial = settings.manualStart ?: LocalTime.of(9, 0),
            onDismiss = { editStart = false },
            onPick = { time ->
                editStart = false
                scope.launch {
                    settingsStore.setManualStart(time)
                    AlarmWork.replan(context.applicationContext)
                    check()
                }
            },
        )
    }

    if (editAlerts) {
        AlertsDialog(
            AlarmKind.Start,
            current = settings.alerts,
            onDismiss = { editAlerts = false },
            onSave = { alerts ->
                editAlerts = false
                scope.launch {
                    settingsStore.setAlerts(alerts)
                    AlarmWork.replan(context.applicationContext)
                }
            },
        )
    }

    if (editEnd) {
        StartTimeDialog(
            initial = settings.manualEnd ?: LocalTime.of(18, 0),
            onDismiss = { editEnd = false },
            onPick = { time ->
                editEnd = false
                scope.launch {
                    settingsStore.setManualEnd(time)
                    AlarmWork.replan(context.applicationContext)
                    check()
                }
            },
        )
    }

    if (editEndAlerts) {
        AlertsDialog(
            AlarmKind.End,
            current = settings.endAlerts,
            onDismiss = { editEndAlerts = false },
            onSave = { alerts ->
                editEndAlerts = false
                scope.launch {
                    settingsStore.setEndAlerts(alerts)
                    AlarmWork.replan(context.applicationContext)
                }
            },
        )
    }
}

@Composable
private fun LoggedOutView(onLogin: () -> Unit) {
    CommentLine("로그인 전")
    BigTime("--:--", Palette.textDim)
    MessagePanel(
        tag = "LOGIN",
        tagColor = Palette.brand,
        background = Palette.warnBg,
        borderColor = Palette.warnBorder,
        text = "하이웍스에 로그인하면 출퇴근 기록을 확인할 수 있습니다.",
        action = "로그인",
        onAction = onLogin,
    )
}

/**
 * The 퇴근 tab: the next end check, the end time and the check-out record, and the end settings.
 * With the end alerts off, only the end time and the record.
 */
@Composable
private fun EndView(
    result: CheckResult?,
    today: LocalDate,
    checkedAt: LocalTime?,
    settings: Settings,
    busy: Boolean,
    onToggle: () -> Unit,
    onRelogin: () -> Unit,
    onEditEnd: () -> Unit,
    onEditAlerts: () -> Unit,
    warning: @Composable () -> Unit,
) {
    val ok = result as? CheckResult.Ok
    val date = today.format(DATE)
    val dim = "--:--" to Palette.textDim
    val (comment, big) = when {
        !settings.endOn -> "퇴근 알림 꺼짐" to dim
        result == null -> (if (busy) "확인하는 중" else "「지금 확인」을 누르세요") to dim
        ok == null -> "오늘 $date" to dim
        ok.checkedOutAt != null -> "오늘 $date · 퇴근 확인됨" to (ok.checkedOutAt.format(TIME) to Palette.green)
        !ok.usesEndCheck -> "회사에서 퇴근 체크를 쓰지 않습니다" to dim
        ok.expectedEnd == ExpectedTime.NoWork -> "오늘 $date · 휴일 또는 종일 휴가" to dim
        !ok.checkedIn -> "오늘 $date · 출근 기록 없음" to dim
        else -> {
            val end = (ok.expectedEnd as? ExpectedTime.At)?.time
            val now = ok.date.atTime(checkedAt ?: LocalTime.now())
            val next = end?.let { AlarmPlan.nextStage(ok.date, it, settings.endAlerts, now, AlarmKind.End) }
            if (next != null) {
                "다음 확인 · ${endAlertText(next.minutesBefore)}" to (next.at.format(TIME) to Palette.text)
            } else {
                "오늘 $date" to dim
            }
        }
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f)) { CommentLine(comment) }
        EndToggle(settings.endOn, onToggle)
    }
    BigTime(big.first, big.second)
    if (settings.endOn) warning()
    when (result) {
        is CheckResult.Ok -> {
            when (val expected = result.expectedEnd) {
                is ExpectedTime.At -> AssignLine(
                    "end",
                    expected.time.format(TIME),
                    Palette.number,
                    if (settings.manualEnd != null) "직접 입력" else "근무 일정",
                )
                ExpectedTime.NoWork -> AssignLine("end", "none", Palette.string, "휴일·휴가")
                is ExpectedTime.Unknown -> AssignLine("end", "unknown", Palette.red, expected.reason, Palette.red)
            }
            val out = result.checkedOutAt
            if (out != null) {
                AssignLine("checkedOut", out.format(TIME), Palette.number, "하이웍스 기록")
            } else {
                AssignLine("checkedOut", "none", Palette.string)
            }
        }
        null -> Unit
        else -> ProblemPanel(result, checkedAt, onRelogin)
    }
    if (settings.endOn) {
        Spacer(Modifier.height(8.dp))
        val manual = settings.manualEnd
        AssignLine(
            "endTime",
            manual?.format(TIME) ?: "auto",
            if (manual != null) Palette.number else Palette.string,
            if (manual != null) "직접 입력 · 눌러서 변경" else "근무 일정 · 눌러서 변경",
            modifier = Modifier.fillMaxWidth().clickable(onClick = onEditEnd).padding(vertical = 12.dp),
        )
        AssignLine(
            "endAlerts",
            settings.endAlerts.joinToString(", ", "[", "]") { AlarmPlan.label(it) },
            Palette.number,
            "눌러서 변경",
            modifier = Modifier.fillMaxWidth().clickable(onClick = onEditAlerts).padding(vertical = 12.dp),
        )
    } else {
        Text(
            "위의 「퇴근 알림 OFF」를 누르면 다시 켭니다.",
            modifier = Modifier.padding(top = 8.dp),
            fontSize = 12.sp,
            color = Palette.textMuted,
        )
    }
}

/** "퇴근 10분 전", "퇴근 시각", "퇴근 30분 뒤". */
private fun endAlertText(minutesBefore: Long): String = when {
    minutesBefore > 0 -> "퇴근 ${minutesBefore}분 전"
    minutesBefore == 0L -> "퇴근 시각"
    else -> "퇴근 ${-minutesBefore}분 뒤"
}

private fun alertText(kind: AlarmKind, minutesBefore: Long): String = when (kind) {
    AlarmKind.Start -> "출근 ${minutesBefore}분 전"
    AlarmKind.End -> endAlertText(minutesBefore)
}

/** The start time and alert settings. */
@Composable
private fun AlarmLines(settings: Settings, onEditStart: () -> Unit, onEditAlerts: () -> Unit) {
    val manual = settings.manualStart
    AssignLine(
        "startTime",
        manual?.format(TIME) ?: "auto",
        if (manual != null) Palette.number else Palette.string,
        if (manual != null) "직접 입력 · 눌러서 변경" else "근무 일정 · 눌러서 변경",
        modifier = Modifier.fillMaxWidth().clickable(onClick = onEditStart).padding(vertical = 12.dp),
    )
    AssignLine(
        "alerts",
        settings.alerts.joinToString(", ", "[", "]") { "T-$it" },
        Palette.number,
        "눌러서 변경",
        modifier = Modifier.fillMaxWidth().clickable(onClick = onEditAlerts).padding(vertical = 12.dp),
    )
}

/** Without the notification permission, the check alarms still run but nobody sees them. */
@Composable
private fun NotifyWarning(permissions: Permissions) {
    if (permissions.notify) return
    val context = LocalContext.current
    MessagePanel(
        tag = "WARN",
        tagColor = Palette.orange,
        background = Palette.warnBg,
        borderColor = Palette.warnBorder,
        text = "알림 권한이 꺼져 있어 출퇴근 체크를 안 해도 알림이 오지 않습니다.",
        action = "알림 설정 열기",
        onAction = { context.openNotificationSettings() },
    )
}

/**
 * The alerts, in minutes before the start or end time (negative: after the end time).
 * Adds and removes them, and saves them all at once.
 */
@Composable
private fun AlertsDialog(kind: AlarmKind, current: List<Long>, onDismiss: () -> Unit, onSave: (List<Long>) -> Unit) {
    var alerts by remember { mutableStateOf(current.sortedDescending()) }
    // Null while the add field is closed.
    var input by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (kind == AlarmKind.End) "퇴근 알림 시각" else "알림 시각", color = Palette.text) },
        text = {
            Column {
                val question = if (kind == AlarmKind.End) "퇴근 몇 분 전이나 뒤에 확인할지" else "출근 몇 분 전에 확인할지"
                Text("$question · 최대 ${AlarmPlan.MAX_ALERTS}개", fontSize = 12.sp, color = Palette.textMuted)
                Spacer(Modifier.height(8.dp))
                for (minutes in alerts) {
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 48.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            AlarmPlan.label(minutes),
                            modifier = Modifier.width(56.dp),
                            fontFamily = JetBrainsMono,
                            fontWeight = FontWeight.Bold,
                            color = Palette.text,
                        )
                        Text(alertText(kind, minutes), modifier = Modifier.weight(1f), fontSize = 14.sp, color = Palette.textSub)
                        // The last alert stays: at least one is needed.
                        val removable = alerts.size > 1
                        Text(
                            "✕",
                            modifier = Modifier
                                .clickable(enabled = removable) { alerts = alerts - minutes }
                                .padding(12.dp),
                            color = if (removable) Palette.textFaint else Palette.textDim,
                        )
                    }
                }
                val typed = input
                if (typed == null) {
                    val full = alerts.size >= AlarmPlan.MAX_ALERTS
                    Row(
                        Modifier.fillMaxWidth().clickable(enabled = !full) { input = "" }.heightIn(min = 48.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "＋ 알림 추가",
                            modifier = Modifier.weight(1f),
                            fontWeight = FontWeight.Bold,
                            color = if (full) Palette.textDim else Palette.brand,
                        )
                        Text(
                            "${alerts.size}/${AlarmPlan.MAX_ALERTS}" + if (full) " 최대" else "",
                            fontFamily = JetBrainsMono,
                            fontSize = 12.sp,
                            color = Palette.textFaint,
                        )
                    }
                    if (alerts.size == 1) {
                        Text("알림은 1개 이상 있어야 합니다", fontSize = 12.sp, color = Palette.textMuted)
                    }
                } else {
                    AddAlertField(
                        kind,
                        typed,
                        alerts,
                        onChange = { input = it },
                        onAdd = { minutes ->
                            alerts = (alerts + minutes).sortedDescending()
                            input = null
                        },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(alerts) }) { Text("저장", color = Palette.brand) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("취소", color = Palette.textSub) }
        },
        containerColor = Palette.card,
    )
}

/** 「출근 [  ] 분 전 [추가]」 or 「퇴근 [  ] 분 [전|뒤] [추가]」, and why the number cannot be added. */
@Composable
private fun AddAlertField(
    kind: AlarmKind,
    input: String,
    alerts: List<Long>,
    onChange: (String) -> Unit,
    onAdd: (Long) -> Unit,
) {
    val end = kind == AlarmKind.End
    // Only for an end alert: after the end time.
    var after by remember { mutableStateOf(false) }
    val typed = input.toLongOrNull()
    val minutes = typed?.let { if (after) -it else it }
    val error = when {
        minutes == null -> null
        end && minutes !in AlarmPlan.END_MINUTES_RANGE ->
            "전은 ${AlarmPlan.END_MINUTES_RANGE.last}분, 뒤는 ${-AlarmPlan.END_MINUTES_RANGE.first}분까지 입력하세요"
        !end && minutes !in AlarmPlan.MINUTES_RANGE ->
            "${AlarmPlan.MINUTES_RANGE.first}~${AlarmPlan.MINUTES_RANGE.last}분 사이로 입력하세요"
        minutes in alerts -> "이미 있는 알림입니다"
        else -> null
    }
    val add = { if (minutes != null && error == null) onAdd(minutes) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .border(1.5.dp, Palette.brand, RoundedCornerShape(8.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(if (end) "퇴근 " else "출근 ", fontSize = 14.sp, color = Palette.textSub)
            BasicTextField(
                value = input,
                onValueChange = { onChange(it.filter(Char::isDigit).take(if (end) 3 else 2)) },
                modifier = Modifier.width(48.dp).focusRequester(focus),
                textStyle = TextStyle(
                    fontFamily = JetBrainsMono,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = Palette.text,
                    textAlign = TextAlign.Center,
                ),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { add() }),
                singleLine = true,
                cursorBrush = SolidColor(Palette.brand),
                decorationBox = { field ->
                    Column {
                        field()
                        Box(Modifier.fillMaxWidth().height(2.dp).background(Palette.brand))
                    }
                },
            )
            if (end) {
                Text(" 분 ", fontSize = 14.sp, color = Palette.textSub)
                BeforeAfter(after, onChange = { after = it })
                Spacer(Modifier.weight(1f))
            } else {
                Text(" 분 전", modifier = Modifier.weight(1f), fontSize = 14.sp, color = Palette.textSub)
            }
            AppButton("추가", primary = true, onClick = add, enabled = minutes != null && error == null)
        }
        if (error != null) {
            Text(error, fontSize = 12.sp, color = Palette.red)
        }
    }
}

/** Manual start or end time, or back to the work schedule. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun StartTimeDialog(initial: LocalTime, onDismiss: () -> Unit, onPick: (LocalTime?) -> Unit) {
    val state = rememberTimePickerState(initial.hour, initial.minute, is24Hour = true)
    AlertDialog(
        onDismissRequest = onDismiss,
        text = { TimePicker(state) },
        confirmButton = {
            TextButton(onClick = { onPick(LocalTime.of(state.hour, state.minute)) }) {
                Text("직접 입력", color = Palette.brand)
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = { onPick(null) }) { Text("자동", color = Palette.textSub) }
                TextButton(onClick = onDismiss) { Text("취소", color = Palette.textSub) }
            }
        },
        containerColor = Palette.card,
    )
}

/** The start line is the one the check used. */
@Composable
private fun CheckResultView(
    result: CheckResult,
    today: LocalDate,
    checkedAt: LocalTime?,
    manualStart: Boolean,
    alerts: List<Long>,
    onRelogin: () -> Unit,
    warning: @Composable () -> Unit,
) {
    when (result) {
        is CheckResult.Ok -> {
            val date = result.date.format(DATE)
            val checkedInAt = result.checkedInAt?.format(TIME)
            when (val expected = result.expected) {
                is ExpectedTime.At -> {
                    val start = expected.time.format(TIME)
                    if (result.checkedIn) {
                        CommentLine("오늘 $date · 출근 확인됨")
                        BigTime(checkedInAt ?: "--:--", Palette.green)
                        warning()
                    } else {
                        CommentLine("다음 확인 · 오늘 $date")
                        val checkedAtTime = result.date.atTime(checkedAt ?: LocalTime.now())
                        val next = AlarmPlan.nextStage(result.date, expected.time, alerts, checkedAtTime)
                        if (next != null) BigTime(next.at.format(TIME), Palette.text) else BigTime("--:--", Palette.textDim)
                        warning()
                    }
                    AssignLine("start", start, Palette.number, if (manualStart) "직접 입력" else "근무 일정")
                }
                ExpectedTime.NoWork -> {
                    CommentLine("오늘 $date · 휴일 또는 종일 휴가")
                    BigTime("--:--", Palette.textDim)
                    warning()
                    AssignLine("alarms", "none", Palette.string, "휴일·휴가")
                }
                is ExpectedTime.Unknown -> {
                    CommentLine("오늘 $date")
                    BigTime("--:--", Palette.textDim)
                    warning()
                    AssignLine("start", "unknown", Palette.red, expected.reason, Palette.red)
                }
            }
            if (result.checkedIn) {
                AssignLine("checkedIn", checkedInAt ?: "yes", Palette.number, "하이웍스 기록")
            } else {
                AssignLine("checkedIn", "none", Palette.string)
            }
        }
        else -> {
            CommentLine("오늘 ${today.format(DATE)}")
            BigTime("--:--", Palette.textDim)
            ProblemPanel(result, checkedAt, onRelogin)
        }
    }
}

/** A session that expired, or a check that failed. Nothing for a check that worked. */
@Composable
private fun ProblemPanel(result: CheckResult, checkedAt: LocalTime?, onRelogin: () -> Unit) {
    val at = checkedAt?.format(TIME).orEmpty()
    when (result) {
        is CheckResult.Ok -> Unit
        CheckResult.SessionExpired -> MessagePanel(
            tag = "401",
            tagColor = Palette.red,
            background = Palette.errorBg,
            borderColor = Palette.errorBorder,
            text = "$at 확인에서 세션이 만료됐습니다. 다시 로그인해야 출퇴근 기록을 확인할 수 있습니다.",
            note = "회사 밖에서 접속이 제한된 경우에도 이렇게 보일 수 있습니다.",
            action = "다시 로그인",
            onAction = onRelogin,
        )
        is CheckResult.Failed -> MessagePanel(
            tag = "WARN",
            tagColor = Palette.orange,
            background = Palette.warnBg,
            borderColor = Palette.warnBorder,
            text = "$at 하이웍스 기록을 확인하지 못했습니다. 하이웍스 앱에서 직접 확인하세요.",
            note = result.reason,
        )
    }
}

/** 「전」 or 「뒤」 for an end alert. */
@Composable
private fun BeforeAfter(after: Boolean, onChange: (Boolean) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        for ((label, value) in listOf("전" to false, "뒤" to true)) {
            val selected = after == value
            val shape = RoundedCornerShape(6.dp)
            Text(
                label,
                modifier = Modifier
                    .clip(shape)
                    .background(if (selected) Palette.brandTint else Palette.card, shape)
                    .border(if (selected) 1.5.dp else 1.dp, if (selected) Palette.brand else Palette.border, shape)
                    .selectable(selected = selected, role = Role.RadioButton, onClick = { onChange(value) })
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                fontSize = 14.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                color = if (selected) Palette.key else Palette.textSub,
            )
        }
    }
}
