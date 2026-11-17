package io.github.bluewind8791.checkinreminder.data

import io.github.bluewind8791.checkinreminder.domain.AlarmPlan
import org.junit.Assert.assertEquals
import org.junit.Test

class AlertsFromTest {
    @Test
    fun `saved alerts are read`() {
        assertEquals(listOf(60L, 30L, 15L, 3L, 2L), alertsFrom("60,30,15,3,2", null))
        assertEquals(listOf(15L), alertsFrom("15", null))
    }

    @Test
    fun `saved alerts win over the old first alert`() {
        assertEquals(listOf(20L), alertsFrom("20", 30))
    }

    @Test
    fun `the old first alert becomes the first of three`() {
        assertEquals(listOf(30L, 5L, 1L), alertsFrom(null, 30))
    }

    @Test
    fun `nothing saved gives the defaults`() {
        assertEquals(AlarmPlan.DEFAULT_ALERTS, alertsFrom(null, null))
    }

    @Test
    fun `a bad saved value gives the defaults`() {
        assertEquals(AlarmPlan.DEFAULT_ALERTS, alertsFrom("", null))
        assertEquals(AlarmPlan.DEFAULT_ALERTS, alertsFrom("x,y", null))
    }
}
