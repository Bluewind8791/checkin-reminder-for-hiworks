package io.github.bluewind8791.checkinreminder.ui

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.launch
import io.github.bluewind8791.checkinreminder.alarm.AlarmWork
import io.github.bluewind8791.checkinreminder.data.ApiResult
import io.github.bluewind8791.checkinreminder.data.CookieStore
import io.github.bluewind8791.checkinreminder.data.HiworksClient
import io.github.bluewind8791.checkinreminder.data.HiworksLogin
import io.github.bluewind8791.checkinreminder.ui.theme.AppTheme
import io.github.bluewind8791.checkinreminder.ui.theme.JetBrainsMono
import io.github.bluewind8791.checkinreminder.ui.theme.Palette
import java.security.GeneralSecurityException

/** WebView login. The user types the password into the Hiworks page only. */
class LoginActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
        )
        setContent {
            AppTheme {
                LoginScreen(
                    onLoggedIn = {
                        setResult(RESULT_OK)
                        finish()
                    },
                    onClose = ::finish,
                )
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun LoginScreen(onLoggedIn: () -> Unit, onClose: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var address by remember { mutableStateOf(HiworksLogin.START_URL) }
    var verifying by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var webView by remember { mutableStateOf<WebView?>(null) }

    val onDashboard: (WebView) -> Unit = { view ->
        if (!verifying && error == null) {
            verifying = true
            view.stopLoading()
            scope.launch {
                val message = saveSession(CookieStore(context))
                // The keepalive call plans both alarms.
                if (message == null) AlarmWork.keepAlive(context.applicationContext)
                verifying = false
                if (message == null) onLoggedIn() else error = message
            }
        }
    }

    BackHandler {
        val view = webView
        if (view != null && view.canGoBack()) view.goBack() else onClose()
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Palette.bg)
            .navigationBarsPadding()
            .imePadding(),
    ) {
        AddressBar(address, onBack = onClose)
        Box(Modifier.weight(1f).fillMaxWidth()) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    WebView(ctx).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.allowFileAccess = false
                        settings.allowContentAccess = false
                        webViewClient = object : WebViewClient() {
                            // Only HTTPS pages may load.
                            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
                                request.url.scheme != "https"

                            override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                                address = url.orEmpty()
                                if (HiworksLogin.isLoggedIn(url)) onDashboard(view)
                            }
                        }
                        loadUrl(HiworksLogin.START_URL)
                        webView = this
                    }
                },
                onRelease = { it.destroy() },
            )
            if (verifying) {
                Box(Modifier.fillMaxSize().background(Palette.bg), contentAlignment = Alignment.Center) {
                    Text("// 세션을 확인하는 중", fontSize = 13.sp, color = Palette.textMuted)
                }
            }
        }
        Column(Modifier.padding(16.dp)) {
            val message = error
            if (message != null) {
                MessagePanel(
                    tag = "WARN",
                    tagColor = Palette.orange,
                    background = Palette.warnBg,
                    borderColor = Palette.warnBorder,
                    text = message,
                    action = "다시 로그인",
                    onAction = {
                        error = null
                        webView?.loadUrl(HiworksLogin.START_URL)
                    },
                )
            } else {
                CommentLine("비밀번호는 하이웍스 페이지에만 입력합니다")
                CommentLine("앱은 비밀번호를 저장하지 않습니다")
                CommentLine("로그인 뒤 세션 쿠키만 기기에 암호화해 둡니다")
            }
        }
    }
}

/** `‹` and the scheme + host of the current page. */
@Composable
private fun AddressBar(url: String, onBack: () -> Unit) {
    val uri = Uri.parse(url)
    val secure = uri.scheme == "https"
    BrandBar {
        Box(Modifier.size(48.dp).clickable(onClick = onBack), contentAlignment = Alignment.Center) {
            Text("‹", fontFamily = JetBrainsMono, fontSize = 22.sp, color = Palette.onBrand)
        }
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(color = if (secure) Palette.green else Palette.red)) { append(uri.scheme.orEmpty()) }
                withStyle(SpanStyle(color = Palette.textFaint)) { append("://") }
                withStyle(SpanStyle(color = Palette.text)) { append(uri.host.orEmpty()) }
            },
            modifier = Modifier
                .weight(1f)
                .background(Palette.card, RoundedCornerShape(6.dp))
                .padding(horizontal = 12.dp, vertical = 8.dp),
            fontFamily = JetBrainsMono,
            fontSize = 13.sp,
            maxLines = 1,
        )
        Spacer(Modifier.width(16.dp))
    }
}

/**
 * Takes PHPSESSID from the WebView, checks it with one API call, and saves it.
 * Returns null on success, or the message to show.
 */
private suspend fun saveSession(store: CookieStore): String? {
    val cookies = CookieManager.getInstance()
    val sessionId = HiworksLogin.sessionId(cookies.getCookie(HiworksLogin.COOKIE_URL))
    val message = if (sessionId == null) {
        "세션 쿠키를 찾지 못했습니다."
    } else {
        when (val result = HiworksClient.create(sessionId).workInfo()) {
            is ApiResult.Success -> try {
                store.save(sessionId)
                null
            } catch (e: GeneralSecurityException) {
                "세션을 기기에 저장하지 못했습니다."
            }
            ApiResult.SessionExpired ->
                "로그인을 확인하지 못했습니다 (401). 회사 밖에서 접속이 제한된 경우에도 이렇게 될 수 있습니다."
            is ApiResult.Failed -> when {
                result.reason.startsWith("network") -> "네트워크 오류로 로그인을 확인하지 못했습니다."
                result.reason.startsWith("HTTP 5") -> "하이웍스 서버 오류로 로그인을 확인하지 못했습니다."
                // A company without the attendance service fails here.
                else -> "이 회사는 하이웍스 근태관리를 쓰지 않는 것 같습니다."
            }
        }
    }
    // Keep only one copy of the cookie. On a failure this also restarts the login.
    cookies.removeAllCookies(null)
    cookies.flush()
    return message
}
