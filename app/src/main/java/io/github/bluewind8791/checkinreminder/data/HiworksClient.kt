package io.github.bluewind8791.checkinreminder.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.time.LocalDate
import java.util.concurrent.TimeUnit

sealed interface ApiResult<out T> {
    data class Success<T>(val value: T) : ApiResult<T>

    /** The server answered 401. The user must log in again. */
    data object SessionExpired : ApiResult<Nothing>

    /** Network error, other HTTP error, or a response that cannot be parsed. */
    data class Failed(val reason: String) : ApiResult<Nothing>
}

/**
 * Calls the two read-only Hiworks APIs with the PHPSESSID cookie.
 * Never logs the cookie or a response body.
 */
class HiworksClient(
    private val http: OkHttpClient,
    private val sessionId: String,
    private val workApiBase: HttpUrl = WORK_API_BASE,
    private val timecheckApiBase: HttpUrl = TIMECHECK_API_BASE,
) {
    suspend fun workInfo(): ApiResult<WorkInfo> {
        val url = timecheckApiBase.newBuilder()
            .addPathSegments("v4/web/user-work-info")
            .build()
        return get(url) { json.decodeFromString<WorkInfoResponse>(it).data }
    }

    suspend fun calendar(from: LocalDate, to: LocalDate): ApiResult<List<CalendarDay>> {
        val url = workApiBase.newBuilder()
            .addPathSegments("v4/my-work-data-calendar")
            .addQueryParameter("filter[work_date][gte]", from.toString())
            .addQueryParameter("filter[work_date][lte]", to.toString())
            .addQueryParameter("page[limit]", "25")
            .addQueryParameter("page[offset]", "0")
            .build()
        return get(url) { json.decodeFromString<CalendarResponse>(it).data.userWorkData }
    }

    private suspend fun <T> get(url: HttpUrl, parse: (String) -> T): ApiResult<T> =
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url(url)
                .header("Cookie", "PHPSESSID=$sessionId")
                .build()
            try {
                http.newCall(request).execute().use { response ->
                    when {
                        response.code == 401 -> ApiResult.SessionExpired
                        !response.isSuccessful -> ApiResult.Failed("HTTP ${response.code}")
                        else -> ApiResult.Success(parse(response.body?.string().orEmpty()))
                    }
                }
            } catch (e: IOException) {
                ApiResult.Failed("network: ${e.javaClass.simpleName}")
            } catch (e: IllegalArgumentException) {
                // kotlinx.serialization.SerializationException is an IllegalArgumentException.
                ApiResult.Failed("parse: ${e.javaClass.simpleName}")
            }
        }

    companion object {
        val WORK_API_BASE = "https://hr-work-api.office.hiworks.com/".toHttpUrl()
        val TIMECHECK_API_BASE = "https://hr-timecheck-api.office.hiworks.com/".toHttpUrl()
        private const val TIMEOUT_SECONDS = 5L

        private val json = Json {
            ignoreUnknownKeys = true
            coerceInputValues = true
        }

        /** @param callTimeoutSeconds limit for a whole call, used by alarms. 0 means none. */
        fun create(sessionId: String, callTimeoutSeconds: Long = 0): HiworksClient = HiworksClient(
            http = OkHttpClient.Builder()
                .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .callTimeout(callTimeoutSeconds, TimeUnit.SECONDS)
                .followRedirects(false)
                .addInterceptor(HostAllowlistInterceptor())
                .build(),
            sessionId = sessionId,
        )
    }
}

/** Blocks every request that is not HTTPS to a host ending in ".office.hiworks.com". */
class HostAllowlistInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val url = chain.request().url
        if (!url.isHttps || !url.host.endsWith(ALLOWED_SUFFIX)) {
            throw IOException("blocked host: ${url.host}")
        }
        return chain.proceed(chain.request())
    }

    private companion object {
        const val ALLOWED_SUFFIX = ".office.hiworks.com"
    }
}
