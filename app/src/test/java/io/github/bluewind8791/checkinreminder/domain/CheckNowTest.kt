package io.github.bluewind8791.checkinreminder.domain

import kotlinx.coroutines.runBlocking
import io.github.bluewind8791.checkinreminder.data.HiworksClient
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class CheckNowTest {
    private lateinit var server: MockWebServer
    private lateinit var client: HiworksClient
    private val day = LocalDate.of(2026, 11, 5)

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
        client = HiworksClient(
            http = OkHttpClient(),
            sessionId = "test-session",
            workApiBase = server.url("/"),
            timecheckApiBase = server.url("/"),
        )
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun fixture(name: String): String =
        javaClass.getResource("/fixtures/$name")!!.readText()

    @Test
    fun `a checked-in day gives the start and check-in times`() = runBlocking {
        server.enqueue(MockResponse().setBody(fixture("calendar-normal.json")))
        server.enqueue(MockResponse().setBody(fixture("work-info-checked-in.json")))

        val result = checkNow(client, day)

        assertEquals(
            CheckResult.Ok(
                date = day,
                expected = ExpectedTime.At(LocalTime.of(9, 0)),
                checkedIn = true,
                checkedInAt = LocalTime.of(8, 48, 24),
                expectedEnd = ExpectedTime.At(LocalTime.of(18, 0)),
                checkedOutAt = null,
                usesEndCheck = true,
            ),
            result,
        )
        assertTrue(server.takeRequest().path!!.startsWith("/v4/my-work-data-calendar"))
        assertEquals("/v4/web/user-work-info", server.takeRequest().path)
    }

    @Test
    fun `a day before the check-in is not checked in`() = runBlocking {
        server.enqueue(MockResponse().setBody(fixture("calendar-normal.json")))
        server.enqueue(MockResponse().setBody(fixture("work-info-not-checked-in.json")))

        val result = checkNow(client, day) as CheckResult.Ok

        assertEquals(false, result.checkedIn)
        assertEquals(null, result.checkedInAt)
    }

    /**
     * work-info-checked-out.json is a live response taken after a check-out on 2026-11-26, with the
     * date, times, ids and place names replaced. The check-out also adds a `details` entry of type
     * `end`; only `end_at` is read.
     */
    @Test
    fun `a checked-out day gives the check-out time`() = runBlocking {
        server.enqueue(MockResponse().setBody(fixture("calendar-normal.json")))
        server.enqueue(MockResponse().setBody(fixture("work-info-checked-out.json")))

        val result = checkNow(client, day) as CheckResult.Ok

        assertEquals(LocalTime.of(18, 1, 10), result.checkedOutAt)
        assertEquals(true, result.usesEndCheck)
    }

    @Test
    fun `a manual end time wins over the schedule`() = runBlocking {
        server.enqueue(MockResponse().setBody(fixture("calendar-normal.json")))
        server.enqueue(MockResponse().setBody(fixture("work-info-checked-in.json")))

        val result = checkNow(client, day, manualEnd = LocalTime.of(19, 30)) as CheckResult.Ok

        assertEquals(ExpectedTime.At(LocalTime.of(19, 30)), result.expectedEnd)
    }

    @Test
    fun `401 on the calendar is session expired and skips the second call`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(401))

        assertEquals(CheckResult.SessionExpired, checkNow(client, day))
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `401 on the work info is session expired`() = runBlocking {
        server.enqueue(MockResponse().setBody(fixture("calendar-normal.json")))
        server.enqueue(MockResponse().setResponseCode(401))

        assertEquals(CheckResult.SessionExpired, checkNow(client, day))
    }

    @Test
    fun `a server error fails`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(500))

        assertEquals(CheckResult.Failed("HTTP 500"), checkNow(client, day))
    }

    @Test
    fun `a calendar without today fails`() = runBlocking {
        server.enqueue(MockResponse().setBody(fixture("calendar-normal.json")))

        val result = checkNow(client, day.plusDays(1))

        assertTrue(result is CheckResult.Failed)
        assertEquals(1, server.requestCount)
    }
}
