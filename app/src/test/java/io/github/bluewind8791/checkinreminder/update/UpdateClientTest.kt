package io.github.bluewind8791.checkinreminder.update

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class UpdateClientTest {
    private lateinit var server: MockWebServer
    private lateinit var client: UpdateClient

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
        client = UpdateClient(OkHttpClient(), server.url("/"))
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun version(code: Int, apk: String = "checkin-reminder-0.2.0.apk") = MockResponse().setBody(
        """{"versionCode": $code, "versionName": "0.2.0", "apk": "$apk", "notes": ["알림 문구 수정", "버그 수정"], "extra": 1}""",
    )

    @Test
    fun `newer returns a release with a higher versionCode`() = runBlocking {
        server.enqueue(version(2))

        val result = client.newer(currentCode = 1)

        assertEquals(
            UpdateResult.Newer(Release(2, "0.2.0", "checkin-reminder-0.2.0.apk", listOf("알림 문구 수정", "버그 수정"))),
            result,
        )
        assertEquals("/version.json", server.takeRequest().path)
    }

    @Test
    fun `newer returns latest for the same versionCode`() = runBlocking {
        server.enqueue(version(1))

        assertEquals(UpdateResult.Latest, client.newer(currentCode = 1))
    }

    @Test
    fun `newer fails when the apk is not a plain file name`() = runBlocking {
        server.enqueue(version(2, apk = "https://example.com/other.apk"))

        assertEquals(UpdateResult.Failed, client.newer(currentCode = 1))
    }

    @Test
    fun `newer fails on an HTTP error`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(404))

        assertEquals(UpdateResult.Failed, client.newer(currentCode = 1))
    }

    @Test
    fun `newer fails on a body that is not a release`() = runBlocking {
        server.enqueue(MockResponse().setBody("<html></html>"))

        assertEquals(UpdateResult.Failed, client.newer(currentCode = 1))
    }

    @Test
    fun `newer fails when the server is not reachable`() = runBlocking {
        server.shutdown()

        assertEquals(UpdateResult.Failed, client.newer(currentCode = 1))
    }

    @Test
    fun `notes are optional`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"versionCode": 2, "versionName": "0.2.0", "apk": "a.apk"}"""))

        assertEquals(emptyList<String>(), (client.newer(currentCode = 1) as UpdateResult.Newer).release.notes)
    }
}
