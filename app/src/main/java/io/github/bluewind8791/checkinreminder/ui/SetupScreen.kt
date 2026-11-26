package io.github.bluewind8791.checkinreminder.ui

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import io.github.bluewind8791.checkinreminder.alarm.AlarmWork
import io.github.bluewind8791.checkinreminder.data.CookieStore
import io.github.bluewind8791.checkinreminder.data.HiworksClient
import io.github.bluewind8791.checkinreminder.data.Settings
import io.github.bluewind8791.checkinreminder.data.SettingsStore
import io.github.bluewind8791.checkinreminder.domain.CheckResult
import io.github.bluewind8791.checkinreminder.domain.ExpectedTime
import io.github.bluewind8791.checkinreminder.domain.checkNow
import io.github.bluewind8791.checkinreminder.ui.theme.JetBrainsMono
import io.github.bluewind8791.checkinreminder.ui.theme.Palette
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private enum class StepState { Done, Later, Todo }

private fun stateOf(done: Boolean, later: Boolean) = when {
    done -> StepState.Done
    later -> StepState.Later
    else -> StepState.Todo
}

private val DAY = DateTimeFormatter.ofPattern("M/d (E)", Locale.KOREAN)
private val TIME = DateTimeFormatter.ofPattern("HH:mm")

/**
 * First run, in four steps: notifications, the battery exemption, login, and the work schedule.
 * Only the login cannot be put off. A step put off stays as `later`, and the main screen shows its permission.
 */
@Composable
fun SetupScreen(permissions: Permissions, onPermissionsChanged: () -> Unit, onDone: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = remember { CookieStore(context) }
    val settingsStore = remember { SettingsStore(context) }
    val settings by settingsStore.settings.collectAsState(initial = Settings())
    var notifyLater by rememberSaveable { mutableStateOf(false) }
    var batteryLater by rememberSaveable { mutableStateOf(false) }
    var loggedIn by remember { mutableStateOf(false) }
    // Null while the work schedule is being read.
    var result by remember { mutableStateOf<CheckResult?>(null) }
    var checkedAt by remember { mutableStateOf<LocalTime?>(null) }
    var editStart by remember { mutableStateOf(false) }

    fun check() {
        result = null
        scope.launch {
            val sessionId = store.load()
            val checked = sessionId?.let {
                checkNow(HiworksClient.create(it), LocalDate.now(), settingsStore.read().manualStart)
            }
            if (checked == null || checked == CheckResult.SessionExpired) {
                loggedIn = false
            } else {
                result = checked
                checkedAt = LocalTime.now()
            }
        }
    }

    val notifyRequest = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        onPermissionsChanged()
        if (!granted) notifyLater = true
    }
    val login = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (it.resultCode == Activity.RESULT_OK) {
            loggedIn = true
            check()
        }
    }

    val expected = (result as? CheckResult.Ok)?.expected
    val known = expected is ExpectedTime.At || expected == ExpectedTime.NoWork
    val states = listOf(
        stateOf(permissions.notify, notifyLater),
        stateOf(permissions.batteryOpt, batteryLater),
        stateOf(loggedIn, false),
        stateOf(known, false),
    )
    val current = states.indexOf(StepState.Todo).let { if (it < 0) states.lastIndex else it }

    Column(
        Modifier
            .fillMaxSize()
            .background(Palette.bg)
            .navigationBarsPadding(),
    ) {
        // The latest call that reached Hiworks with the session, as on the main screen.
        // The login's keepalive call is one of them.
        val lastOk = listOfNotNull(
            checkedAt?.takeIf { result is CheckResult.Ok }?.let { LocalDate.now().atTime(it) },
            settings.lastCallAt?.takeIf { settings.lastCallResult == "200" },
        ).maxOrNull()
        when {
            !loggedIn -> TopBar("setup ${current + 1}/4", Palette.orange)
            lastOk != null -> TopBar("session: ${lastOk.format(TIME)}", Palette.green)
            else -> TopBar("session: --:--", Palette.textFaint)
        }
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Spacer(Modifier.height(8.dp))
            CommentLine("처음 설정")
            Text(
                "알림이 제때 오도록\n네 가지를 설정합니다",
                modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                lineHeight = 32.sp,
            )
            StepPanel(states, current) { step ->
                when (step) {
                    0 -> StepDetail("출근 체크를 안 했을 때 알림을 보냅니다. 꺼 두면 알림이 오지 않습니다.")
                    1 -> StepDetail(
                        "기기가 오래 잠들어 있으면 알림이 늦어질 수 있습니다." +
                            if (isSamsung) " 삼성 기기는 설정 › 배터리 › 백그라운드 사용 한도 › 절전 예외 앱에도 추가하세요." else "",
                    )
                    2 -> StepDetail("하이웍스 페이지에서 로그인합니다. 앱은 비밀번호를 저장하지 않고, 세션 쿠키만 기기에 암호화해 둡니다.")
                    else -> ScheduleDetail(result, settings.manualStart != null)
                }
            }
        }
        when (current) {
            0 -> SetupButtons(
                "알림 허용",
                onClick = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                        context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                    ) {
                        notifyRequest.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        context.openNotificationSettings()
                    }
                },
                onLater = { notifyLater = true },
            )
            1 -> SetupButtons("설정 열기", onClick = { context.requestBatteryExemption() }, onLater = { batteryLater = true })
            2 -> SetupButtons("로그인", onClick = { login.launch(Intent(context, LoginActivity::class.java)) })
            else -> when {
                result == null -> SetupButtons("확인하는 중", onClick = {}, enabled = false)
                known -> SetupButtons("시작하기", onClick = onDone)
                expected is ExpectedTime.Unknown -> SetupButtons("출근 시각 입력", onClick = { editStart = true }, onLater = onDone)
                else -> SetupButtons("다시 확인", onClick = ::check, onLater = onDone)
            }
        }
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
}

/** The four steps. The current one is tinted and shows its [detail]. */
@Composable
private fun StepPanel(states: List<StepState>, current: Int, detail: @Composable (Int) -> Unit) {
    val titles = listOf("알림 권한", "배터리 최적화 예외", "하이웍스 로그인", "근무 일정 확인")
    val shape = RoundedCornerShape(6.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Palette.card)
            .border(1.dp, Palette.border, shape),
    ) {
        Row(Modifier.fillMaxWidth().background(Palette.surface).padding(horizontal = 12.dp, vertical = 7.dp)) {
            Text("setup", modifier = Modifier.weight(1f), fontFamily = JetBrainsMono, fontSize = 11.sp, color = Palette.textSub)
            Text("${current + 1}/4", fontFamily = JetBrainsMono, fontSize = 11.sp, color = Palette.textSub)
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(Palette.border))
        states.forEachIndexed { i, state ->
            if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(Palette.divider))
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(if (i == current) Palette.brandTint else Color.Transparent)
                    .heightIn(min = 52.dp)
                    .padding(top = 15.dp, end = 12.dp, bottom = 13.dp),
            ) {
                Text(
                    "${i + 1}",
                    modifier = Modifier.width(30.dp).padding(top = 2.dp, end = 12.dp),
                    fontFamily = JetBrainsMono,
                    fontSize = 11.sp,
                    color = Palette.textFaint,
                    textAlign = TextAlign.End,
                )
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        buildAnnotatedString {
                            append(titles[i])
                            if (i == 1) withStyle(SpanStyle(fontSize = 11.sp, color = Palette.textFaint)) { append("  권장") }
                        },
                        fontSize = 14.sp,
                    )
                    if (i == current) detail(i)
                }
                StateTag(state, i == current)
            }
        }
    }
}

@Composable
private fun StateTag(state: StepState, current: Boolean) {
    val shape = RoundedCornerShape(3.dp)
    val (text, color, background) = when {
        state == StepState.Done -> Triple("done", Palette.green, Palette.greenBg)
        state == StepState.Later -> Triple("later", Palette.orange, Palette.warnBg)
        current -> Triple("now", Palette.onBrand, Palette.brand)
        else -> Triple("todo", Palette.textFaint, Palette.card)
    }
    val borderColor = when {
        state == StepState.Later -> Palette.warnBorder
        state == StepState.Todo && !current -> Palette.border
        else -> background
    }
    Text(
        text,
        modifier = Modifier
            .padding(start = 10.dp)
            .background(background, shape)
            .border(1.dp, borderColor, shape)
            .padding(horizontal = 6.dp, vertical = 1.dp),
        fontFamily = JetBrainsMono,
        fontWeight = FontWeight.Bold,
        fontSize = 10.sp,
        color = color,
    )
}

@Composable
private fun StepDetail(text: String) {
    Text(text, fontSize = 12.sp, lineHeight = 19.sp, color = Palette.textMuted)
}

/** The start time the work schedule gave, or why there is none. */
@Composable
private fun ScheduleDetail(result: CheckResult?, manualStart: Boolean) {
    when (result) {
        null -> StepDetail("근무 일정에서 출근 시각을 가져오는 중입니다.")
        is CheckResult.Ok -> when (val expected = result.expected) {
            is ExpectedTime.At -> {
                StepDetail(if (manualStart) "직접 입력한 출근 시각을 씁니다." else "근무 일정에서 출근 시각을 가져왔습니다.")
                AssignLine("start", expected.time.format(DateTimeFormatter.ofPattern("HH:mm")), Palette.number, "오늘 ${result.date.format(DAY)}")
            }
            ExpectedTime.NoWork -> StepDetail("오늘은 휴일 또는 종일 휴가입니다. 다음 근무일부터 알림이 옵니다.")
            is ExpectedTime.Unknown -> {
                StepDetail("근무 일정에 출근 시각이 없습니다. 출근 시각을 직접 입력하세요.")
                AssignLine("start", "unknown", Palette.red)
            }
        }
        CheckResult.SessionExpired -> Unit
        is CheckResult.Failed -> StepDetail("근무 일정을 가져오지 못했습니다. ${result.reason}")
    }
}

/** The primary button, and 「나중에」 when the step can be put off. */
@Composable
private fun SetupButtons(text: String, onClick: () -> Unit, onLater: (() -> Unit)? = null, enabled: Boolean = true) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        AppButton(text, primary = true, onClick = onClick, enabled = enabled, modifier = Modifier.weight(2f))
        if (onLater != null) {
            AppButton("나중에", primary = false, onClick = onLater, modifier = Modifier.weight(1f))
        }
    }
}
