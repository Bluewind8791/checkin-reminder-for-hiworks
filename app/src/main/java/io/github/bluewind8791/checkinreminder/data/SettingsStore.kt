package io.github.bluewind8791.checkinreminder.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import io.github.bluewind8791.checkinreminder.domain.AlarmPlan
import io.github.bluewind8791.checkinreminder.domain.CheckAlarm
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

private val Context.settingsDataStore by preferencesDataStore(name = "settings")

data class Settings(
    /** The start time the user typed. Null means the work schedule's `plan_start_at`. */
    val manualStart: LocalTime? = null,
    /** Minutes before the start time of each check alarm, largest first. */
    val alerts: List<Long> = AlarmPlan.DEFAULT_ALERTS,
    /** The work schedule from the last call that returned one. */
    val days: List<CalendarDay> = emptyList(),
    /** The last date whose check alarms are over. */
    val doneDate: LocalDate? = null,
    val nextCheck: CheckAlarm? = null,
    val lastCallAt: LocalDateTime? = null,
    /** "200", "401", or the failure reason of the last background call. */
    val lastCallResult: String? = null,
    /** The first-run setup is over, or the app was already in use when the setup came. */
    val setupDone: Boolean = false,
)

/** Settings and alarm state. Holds no secret: the cookie is in [CookieStore]. */
class SettingsStore(context: Context) {
    private val dataStore = context.applicationContext.settingsDataStore

    val settings: Flow<Settings> = dataStore.data.map { it.toSettings() }

    suspend fun read(): Settings = settings.first()

    suspend fun setManualStart(time: LocalTime?) = dataStore.edit {
        if (time == null) it.remove(MANUAL_START) else it[MANUAL_START] = time.toString()
    }

    suspend fun setAlerts(alerts: List<Long>) = dataStore.edit {
        it[ALERTS] = alerts.sortedDescending().joinToString(",")
        it.remove(FIRST_MINUTES)
    }

    suspend fun setDays(days: List<CalendarDay>) = dataStore.edit {
        it[DAYS] = json.encodeToString(DAYS_SERIALIZER, days)
    }

    suspend fun setDoneDate(date: LocalDate) = dataStore.edit { it[DONE_DATE] = date.toString() }

    suspend fun setNextCheck(alarm: CheckAlarm?) = dataStore.edit {
        if (alarm == null) {
            it.remove(NEXT_DATE)
            it.remove(NEXT_START)
            it.remove(NEXT_MINUTES)
        } else {
            it[NEXT_DATE] = alarm.date.toString()
            it[NEXT_START] = alarm.start.toString()
            it[NEXT_MINUTES] = alarm.minutesBefore
        }
    }

    suspend fun setLastCall(at: LocalDateTime, result: String) = dataStore.edit {
        it[LAST_CALL_AT] = at.toString()
        it[LAST_CALL_RESULT] = result
    }

    suspend fun setSetupDone() = dataStore.edit { it[SETUP_DONE] = true }

    /** Clears everything except the user's own settings. Used by logout. */
    suspend fun clearSession() = dataStore.edit { prefs ->
        val manualStart = prefs[MANUAL_START]
        val alerts = prefs[ALERTS]
        val firstMinutes = prefs[FIRST_MINUTES]
        val setupDone = prefs[SETUP_DONE]
        prefs.clear()
        if (manualStart != null) prefs[MANUAL_START] = manualStart
        if (alerts != null) prefs[ALERTS] = alerts
        if (firstMinutes != null) prefs[FIRST_MINUTES] = firstMinutes
        if (setupDone != null) prefs[SETUP_DONE] = setupDone
    }

    private fun Preferences.toSettings() = Settings(
        manualStart = parse(this[MANUAL_START], LocalTime::parse),
        alerts = alertsFrom(this[ALERTS], this[FIRST_MINUTES]),
        days = parse(this[DAYS]) { json.decodeFromString(DAYS_SERIALIZER, it) }.orEmpty(),
        doneDate = parse(this[DONE_DATE], LocalDate::parse),
        nextCheck = parse(this[NEXT_DATE], LocalDate::parse)?.let { date ->
            val start = parse(this[NEXT_START], LocalTime::parse) ?: return@let null
            val minutes = this[NEXT_MINUTES] ?: return@let null
            CheckAlarm(date, start, minutes)
        },
        lastCallAt = parse(this[LAST_CALL_AT], LocalDateTime::parse),
        lastCallResult = this[LAST_CALL_RESULT],
        setupDone = this[SETUP_DONE] ?: false,
    )

    /** A value this class wrote. Null when it is missing or does not parse, so a bad value never crashes the app. */
    private fun <T> parse(value: String?, parser: (String) -> T): T? =
        value?.let { runCatching { parser(it) }.getOrNull() }

    private companion object {
        val MANUAL_START = stringPreferencesKey("manual_start")
        val ALERTS = stringPreferencesKey("alerts")
        /** The first alert of versions before [ALERTS], whose later alerts were always 5 and 1 minutes. */
        val FIRST_MINUTES = longPreferencesKey("first_minutes")
        val DAYS = stringPreferencesKey("days")
        val DONE_DATE = stringPreferencesKey("done_date")
        val NEXT_DATE = stringPreferencesKey("next_date")
        val NEXT_START = stringPreferencesKey("next_start")
        val NEXT_MINUTES = longPreferencesKey("next_minutes")
        val LAST_CALL_AT = stringPreferencesKey("last_call_at")
        val LAST_CALL_RESULT = stringPreferencesKey("last_call_result")
        val SETUP_DONE = booleanPreferencesKey("setup_done")

        val DAYS_SERIALIZER = ListSerializer(CalendarDay.serializer())
        val json = Json { ignoreUnknownKeys = true }
    }
}

/**
 * The saved alerts, such as "10,5,1". Without them, the first alert [legacyFirst] of an older version
 * followed by its fixed 5 and 1 minutes. The defaults when neither is there or the saved value does not parse.
 */
internal fun alertsFrom(saved: String?, legacyFirst: Long?): List<Long> {
    if (saved != null) {
        return saved.split(",").mapNotNull { it.toLongOrNull() }.ifEmpty { AlarmPlan.DEFAULT_ALERTS }
    }
    return if (legacyFirst != null) listOf(legacyFirst, 5L, 1L) else AlarmPlan.DEFAULT_ALERTS
}
