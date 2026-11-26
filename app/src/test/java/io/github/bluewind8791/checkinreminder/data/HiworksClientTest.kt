package io.github.bluewind8791.checkinreminder.data

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import java.util.concurrent.TimeUnit

class HiworksClientTest {
    private lateinit var server: MockWebServer
    private lateinit var client: HiworksClient

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
        client = clientWith(OkHttpClient.Builder().readTimeout(1, TimeUnit.SECONDS).build())
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun clientWith(http: OkHttpClient) = HiworksClient(
        http = http,
        sessionId = "test-session",
        workApiBase = server.url("/"),
        timecheckApiBase = server.url("/"),
    )

    private fun fixture(name: String): String =
        javaClass.getResource("/fixtures/$name")!!.readText()

    @Test
    fun `workInfo parses a checked-in response and sends only the session cookie`() = runBlocking {
        server.enqueue(MockResponse().setBody(fixture("work-info-checked-in.json")))

        val result = client.workInfo()

        val info = (result as ApiResult.Success).value
        assertEquals("2026-11-06 08:48:24", info.startAt)
        assertEquals("0000-00-00 00:00:00", info.endAt)
        assertEquals("Y", info.useEndCheck)
        assertEquals("start", info.details.single().type)
        val request = server.takeRequest()
        assertEquals("/v4/web/user-work-info", request.path)
        assertEquals("PHPSESSID=test-session", request.getHeader("Cookie"))
    }

    @Test
    fun `workInfo parses the not-checked-in response`() = runBlocking {
        server.enqueue(MockResponse().setBody(fixture("work-info-not-checked-in.json")))

        val info = (client.workInfo() as ApiResult.Success).value

        assertEquals("0000-00-00 00:00:00", info.startAt)
        assertTrue(info.details.isEmpty())
    }

    @Test
    fun `calendar sends the date filter and parses the days`() = runBlocking {
        server.enqueue(MockResponse().setBody(fixture("calendar-normal.json")))

        val result = client.calendar(LocalDate.of(2026, 11, 5), LocalDate.of(2026, 11, 6))

        val day = (result as ApiResult.Success).value.single()
        assertEquals("2026-11-05", day.workDate)
        assertEquals(0, day.dayOffType)
        assertEquals("09:00", day.planStartAt)
        assertEquals("18:00", day.planEndAt)
        assertTrue(day.vacationData.isEmpty())
        val url = server.takeRequest().requestUrl!!
        assertEquals("/v4/my-work-data-calendar", url.encodedPath)
        assertEquals("2026-11-05", url.queryParameter("filter[work_date][gte]"))
        assertEquals("2026-11-06", url.queryParameter("filter[work_date][lte]"))
        assertEquals("25", url.queryParameter("page[limit]"))
        assertEquals("0", url.queryParameter("page[offset]"))
    }

    @Test
    fun `calendar treats a null vacation list as empty`() = runBlocking {
        server.enqueue(
            MockResponse().setBody("""{"data":{"user_work_data":[{"work_date":"2026-11-05","vacation_data":null}]}}"""),
        )

        val result = client.calendar(LocalDate.of(2026, 11, 5), LocalDate.of(2026, 11, 5))

        assertTrue((result as ApiResult.Success).value.single().vacationData.isEmpty())
    }

    @Test
    fun `401 means the session expired`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(401))

        assertEquals(ApiResult.SessionExpired, client.workInfo())
    }

    @Test
    fun `other HTTP errors fail`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(500))

        assertEquals(ApiResult.Failed("HTTP 500"), client.workInfo())
    }

    @Test
    fun `a body that cannot be parsed fails`() = runBlocking {
        server.enqueue(MockResponse().setBody("<html>login</html>"))

        assertTrue(client.workInfo() is ApiResult.Failed)
    }

    @Test
    fun `a body without the expected fields fails`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"result":"ok"}"""))

        assertTrue(client.workInfo() is ApiResult.Failed)
    }

    @Test
    fun `a timeout fails`() = runBlocking {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))

        assertTrue(client.workInfo() is ApiResult.Failed)
    }

    @Test
    fun `the allowlist blocks hosts other than office hiworks`() = runBlocking {
        server.enqueue(MockResponse().setBody(fixture("work-info-checked-in.json")))
        val guarded = clientWith(OkHttpClient.Builder().addInterceptor(HostAllowlistInterceptor()).build())

        val result = guarded.workInfo()

        assertEquals(ApiResult.Failed("network: IOException"), result)
        assertEquals(0, server.requestCount)
    }
}
