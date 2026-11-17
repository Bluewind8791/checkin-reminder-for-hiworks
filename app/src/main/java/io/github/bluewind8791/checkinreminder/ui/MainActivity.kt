package io.github.bluewind8791.checkinreminder.ui

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import io.github.bluewind8791.checkinreminder.alarm.AlarmWork
import io.github.bluewind8791.checkinreminder.data.CookieStore
import io.github.bluewind8791.checkinreminder.data.HiworksClient
import io.github.bluewind8791.checkinreminder.data.Settings
import io.github.bluewind8791.checkinreminder.data.SettingsStore
import io.github.bluewind8791.checkinreminder.domain.AlarmPlan
import io.github.bluewind8791.checkinreminder.domain.CheckResult
import io.github.bluewind8791.checkinreminder.domain.ExpectedStart
import io.github.bluewind8791.checkinreminder.domain.checkNow
import io.github.bluewind8791.checkinreminder.ui.theme.AppTheme
import io.github.bluewind8791.checkinreminder.ui.theme.Palette
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Main screen: status, the 「지금 확인」 result, the start time setting,
 * the next alarm, the last background call, and logout.
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
private val SHORT_DATE = DateTimeFormatter.ofPattern("M/d (E)", Locale.KOREAN)

/** Minutes before the start time of the first check alarm. */
private const val FIRST_ALARM_MINUTES = 10L

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
    var editFirst by remember { mutableStateOf(false) }

    fun check() {
        if (busy) return
        busy = true
        scope.launch {
            val sessionId = store.load()
            if (sessionId == null) {
                loggedIn = false
                result = null
            } else {
                result = checkNow(HiworksClient.create(sessionId), LocalDate.now(), settingsStore.read().manualStart)
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

    val notifyPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notifyPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        loggedIn = store.load() != null
        if (loggedIn == true) {
            check()
            // A force stop drops the alarms.
            AlarmWork.ensureScheduled(context.applicationContext)
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Palette.bg)
            .navigationBarsPadding(),
    ) {
        val (status, statusColor) = when {
            loggedIn != true -> "session: none" to Palette.textFaint
            result == CheckResult.SessionExpired -> "session: expired" to Palette.red
            result == null && settings.lastCallResult == "401" -> "session: expired" to Palette.red
            else -> "session: alive" to Palette.green
        }
        TopBar(status, statusColor)
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Spacer(Modifier.height(8.dp))
            val today = LocalDate.now()
            val current = result
            when {
                loggedIn == null -> Unit
                loggedIn == false -> {
                    CommentLine("로그인 전")
                    BigTime("--:--", Palette.textDim)
                    MessagePanel(
                        tag = "LOGIN",
                        tagColor = Palette.brand,
                        background = Palette.warnBg,
                        borderColor = Palette.warnBorder,
                        text = "하이웍스에 로그인하면 출근 여부를 확인할 수 있습니다.",
                        action = "로그인",
                        onAction = openLogin,
                    )
                }
                current == null -> {
                    CommentLine(if (busy) "확인하는 중" else "「지금 확인」을 누르세요")
                    BigTime("--:--", Palette.textDim)
                }
                else -> CheckResultView(current, today, checkedAt, settings.manualStart != null, onRelogin = openLogin)
            }
            checkedAt?.let { CommentLine("마지막 확인 ${it.format(DateTimeFormatter.ofPattern("HH:mm:ss"))}", Palette.textFaint) }
            if (loggedIn == true) {
                Spacer(Modifier.height(8.dp))
                AlarmLines(settings, onEditStart = { editStart = true }, onEditFirst = { editFirst = true })
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AppButton(
                text = if (busy) "확인하는 중" else "▶ 지금 확인",
                primary = true,
                onClick = ::check,
                enabled = loggedIn == true && !busy,
                modifier = Modifier.weight(2f),
            )
            AppButton(
                text = "로그아웃",
                primary = false,
                onClick = { confirmLogout = true },
                enabled = loggedIn == true && !busy,
                modifier = Modifier.weight(1f),
            )
        }
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

    if (editFirst) {
        FirstMinutesDialog(
            current = settings.firstMinutes,
            onDismiss = { editFirst = false },
            onPick = { minutes ->
                editFirst = false
                scope.launch {
                    settingsStore.setFirstMinutes(minutes)
                    AlarmWork.replan(context.applicationContext)
                }
            },
        )
    }
}

/**
 * The start time and first alarm settings, the next check alarm, and the last background call.
 */
@Composable
private fun AlarmLines(settings: Settings, onEditStart: () -> Unit, onEditFirst: () -> Unit) {
    val manual = settings.manualStart
    AssignLine(
        "startTime",
        manual?.format(TIME) ?: "auto",
        if (manual != null) Palette.number else Palette.string,
        if (manual != null) "직접 입력 · 눌러서 변경" else "근무 일정 · 눌러서 변경",
        modifier = Modifier.fillMaxWidth().clickable(onClick = onEditStart).padding(vertical = 12.dp),
    )
    AssignLine(
        "firstAlert",
        "T-${settings.firstMinutes}",
        Palette.number,
        "첫 알림 · 눌러서 변경",
        modifier = Modifier.fillMaxWidth().clickable(onClick = onEditFirst).padding(vertical = 12.dp),
    )
    val next = settings.nextCheck
    if (next != null) {
        AssignLine("next", "${next.date.format(SHORT_DATE)} ${next.at.format(TIME)}", Palette.number, "T-${next.minutesBefore}")
    } else {
        AssignLine("next", "none", Palette.string)
    }
    val callAt = settings.lastCallAt
    val callResult = settings.lastCallResult
    if (callAt != null && callResult != null) {
        val ok = callResult == "200"
        // A failure stores its reason, such as "network: SocketTimeoutException".
        val code = if (ok || callResult == "401") callResult else "fail"
        AssignLine(
            "keepalive",
            code,
            if (ok) Palette.green else Palette.red,
            if (ok || callResult == "401") callAt.format(TIME) else "${callAt.format(TIME)} · $callResult",
        )
    }
}

/** Minutes before the start time of the first check alarm. */
@Composable
private fun FirstMinutesDialog(current: Long, onDismiss: () -> Unit, onPick: (Long) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("첫 알림 (이후 5분, 1분 전)", color = Palette.text) },
        text = {
            Column {
                for (minutes in AlarmPlan.FIRST_MINUTES_CHOICES) {
                    Text(
                        "출근 ${minutes}분 전",
                        modifier = Modifier.fillMaxWidth().clickable { onPick(minutes) }.padding(vertical = 12.dp),
                        color = if (minutes == current) Palette.brand else Palette.textSub,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("취소", color = Palette.textSub) }
        },
        containerColor = Palette.card,
    )
}

/** Manual start time, or back to the work schedule. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StartTimeDialog(initial: LocalTime, onDismiss: () -> Unit, onPick: (LocalTime?) -> Unit) {
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
    onRelogin: () -> Unit,
) {
    val at = checkedAt?.format(TIME).orEmpty()
    when (result) {
        is CheckResult.Ok -> {
            val date = result.date.format(DATE)
            val checkedInAt = result.checkedInAt?.format(TIME)
            when (val expected = result.expected) {
                is ExpectedStart.At -> {
                    val start = expected.time.format(TIME)
                    if (result.checkedIn) {
                        CommentLine("오늘 $date · 출근 확인됨")
                        BigTime(checkedInAt ?: "--:--", Palette.green)
                    } else {
                        CommentLine("다음 확인 · 오늘 $date")
                        BigTime(expected.time.minusMinutes(FIRST_ALARM_MINUTES).format(TIME), Palette.text)
                    }
                    AssignLine("start", start, Palette.number, if (manualStart) "직접 입력" else "근무 일정")
                }
                ExpectedStart.NoWork -> {
                    CommentLine("오늘 $date · 휴일 또는 종일 휴가")
                    BigTime("--:--", Palette.textDim)
                    AssignLine("alarms", "none", Palette.string, "휴일·휴가")
                }
                is ExpectedStart.Unknown -> {
                    CommentLine("오늘 $date")
                    BigTime("--:--", Palette.textDim)
                    AssignLine("start", "unknown", Palette.red, expected.reason, Palette.red)
                }
            }
            if (result.checkedIn) {
                AssignLine("checkedIn", checkedInAt ?: "yes", Palette.number, "하이웍스 기록")
            } else {
                AssignLine("checkedIn", "none", Palette.string)
            }
        }
        CheckResult.SessionExpired -> {
            CommentLine("오늘 ${today.format(DATE)}")
            BigTime("--:--", Palette.textDim)
            MessagePanel(
                tag = "401",
                tagColor = Palette.red,
                background = Palette.errorBg,
                borderColor = Palette.errorBorder,
                text = "$at 확인에서 세션이 만료됐습니다. 다시 로그인해야 출근 여부를 확인할 수 있습니다.",
                note = "회사 밖에서 접속이 제한된 경우에도 이렇게 보일 수 있습니다.",
                action = "다시 로그인",
                onAction = onRelogin,
            )
        }
        is CheckResult.Failed -> {
            CommentLine("오늘 ${today.format(DATE)}")
            BigTime("--:--", Palette.textDim)
            MessagePanel(
                tag = "WARN",
                tagColor = Palette.orange,
                background = Palette.warnBg,
                borderColor = Palette.warnBorder,
                text = "$at 출근 여부를 확인하지 못했습니다. 하이웍스 앱에서 직접 확인하세요.",
                note = result.reason,
            )
        }
    }
}
