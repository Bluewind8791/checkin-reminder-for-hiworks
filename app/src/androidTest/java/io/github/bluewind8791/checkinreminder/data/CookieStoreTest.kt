package io.github.bluewind8791.checkinreminder.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

/** Runs on a device because it needs Android Keystore. */
@RunWith(AndroidJUnit4::class)
class CookieStoreTest {
    private val store = CookieStore(InstrumentationRegistry.getInstrumentation().targetContext)

    @After
    fun tearDown() = runBlocking {
        store.clear()
    }

    @Test
    fun savedValueComesBack() = runBlocking {
        store.save("test-session-value")
        assertEquals("test-session-value", store.load())
    }

    @Test
    fun aNewSaveReplacesTheOldValue() = runBlocking {
        store.save("first")
        store.save("second")
        assertEquals("second", store.load())
    }

    @Test
    fun nothingAfterClear() = runBlocking {
        store.save("test-session-value")
        store.clear()
        assertNull(store.load())
    }
}
