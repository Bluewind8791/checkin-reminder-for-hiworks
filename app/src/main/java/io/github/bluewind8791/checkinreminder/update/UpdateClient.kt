package io.github.bluewind8791.checkinreminder.update

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.content.pm.PackageInstaller
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

/** `version.json` on the install page: the latest release. */
@Serializable
data class Release(
    val versionCode: Int,
    val versionName: String,
    /** APK file name, next to `version.json`. */
    val apk: String,
    /** What changed, one short line each. */
    val notes: List<String> = emptyList(),
)

/** What [UpdateClient.newer] found on the install page. */
sealed interface UpdateResult {
    data class Newer(val release: Release) : UpdateResult
    data object Latest : UpdateResult
    /** The page could not be read, or did not hold a usable release. */
    data object Failed : UpdateResult
}

/**
 * Checks the install page for a newer release, and installs it.
 * Android installs the APK only when it is signed with the same key as this app.
 */
class UpdateClient(
    private val http: OkHttpClient,
    private val base: HttpUrl = BASE,
) {
    /** Whether the install page has a release with a higher versionCode than [currentCode]. */
    suspend fun newer(currentCode: Int): UpdateResult = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(base.resolve(VERSION_FILE)!!).build()
        try {
            http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext UpdateResult.Failed
                val release = json.decodeFromString<Release>(response.body?.string().orEmpty())
                when {
                    release.versionCode <= currentCode -> UpdateResult.Latest
                    !APK_NAME.matches(release.apk) -> UpdateResult.Failed
                    else -> UpdateResult.Newer(release)
                }
            }
        } catch (e: IOException) {
            UpdateResult.Failed
        } catch (e: IllegalArgumentException) {
            // kotlinx.serialization.SerializationException is an IllegalArgumentException.
            UpdateResult.Failed
        }
    }

    /**
     * Streams the APK of [release] into a PackageInstaller session and commits it.
     * The system then asks the user to confirm, through [InstallReceiver].
     * [onProgress] gets the percent done, when the size is known.
     */
    suspend fun install(context: Context, release: Release, onProgress: (Int) -> Unit) = withContext(Dispatchers.IO) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
            .apply { setAppPackageName(context.packageName) }
        val sessionId = installer.createSession(params)
        installer.openSession(sessionId).use { session ->
            try {
                val request = Request.Builder().url(base.resolve(release.apk)!!).build()
                http.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
                    val body = response.body ?: throw IOException("empty body")
                    val total = body.contentLength()
                    session.openWrite("base.apk", 0, total).use { out ->
                        val buffer = ByteArray(64 * 1024)
                        var done = 0L
                        var percent = -1
                        body.byteStream().use { input ->
                            while (true) {
                                val read = input.read(buffer)
                                if (read < 0) break
                                out.write(buffer, 0, read)
                                done += read
                                if (total > 0 && (done * 100 / total).toInt() != percent) {
                                    percent = (done * 100 / total).toInt()
                                    onProgress(percent)
                                }
                            }
                        }
                        session.fsync(out)
                    }
                }
                session.commit(statusReceiver(context, sessionId))
            } catch (e: IOException) {
                session.abandon()
                throw e
            }
        }
    }

    private fun statusReceiver(context: Context, sessionId: Int): IntentSender {
        // The system adds the status extras, so the PendingIntent must be mutable.
        val mutable = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
        return PendingIntent.getBroadcast(
            context,
            sessionId,
            Intent(context, InstallReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or mutable,
        ).intentSender
    }

    companion object {
        val BASE = "https://checkin.bluewind91.com/".toHttpUrl()
        private const val VERSION_FILE = "version.json"

        /** A plain file name, so the APK always comes from [BASE]. */
        private val APK_NAME = Regex("[A-Za-z0-9._-]+\\.apk")

        private val json = Json { ignoreUnknownKeys = true }

        fun create(): UpdateClient = UpdateClient(
            OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .followRedirects(false)
                .build(),
        )
    }
}
