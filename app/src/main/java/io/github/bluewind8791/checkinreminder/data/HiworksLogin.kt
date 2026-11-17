package io.github.bluewind8791.checkinreminder.data

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** URLs and cookie parsing for the WebView login. */
object HiworksLogin {
    const val START_URL = "https://office.hiworks.com/"

    /** The cookie for this URL is the one the APIs need. `CookieManager.getCookie` also returns HttpOnly cookies. */
    const val COOKIE_URL = "https://hr-timecheck-api.office.hiworks.com/"

    private const val DASHBOARD_HOST = "dashboard.office.hiworks.com"
    private const val SESSION_COOKIE = "PHPSESSID"

    /** True when the WebView reached the dashboard, which means the login succeeded. */
    fun isLoggedIn(url: String?): Boolean {
        val parsed = url?.toHttpUrlOrNull() ?: return false
        return parsed.isHttps && parsed.host == DASHBOARD_HOST
    }

    /** Finds the PHPSESSID value in a cookie string such as "a=1; PHPSESSID=abc". */
    fun sessionId(cookies: String?): String? =
        cookies?.split(';')
            ?.map { it.trim() }
            ?.firstOrNull { it.substringBefore('=') == SESSION_COOKIE }
            ?.substringAfter('=')
            ?.takeIf { it.isNotEmpty() }
}
