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
    val lastCallAt: LocalDateTime? = null,
    /** "200", "401", or the failure reason of the last background call. */
    val lastCallResult: String? = null,
    /** The first-run setup is over, or the app was already in use when the setup came. */
    val setupDone: Boolean = false,
    /** The end alerts are on. Off until the user turns them on. */
    val endOn: Boolean = false,
    /** The end time the user typed. Null means the work schedule's `plan_end_at`. */
    val manualEnd: LocalTime? = null,
    /** Minutes before the end time of each end alarm, largest first. Negative is after the end time. */
    val endAlerts: List<Long> = AlarmPlan.DEFAULT_END_ALERTS,
    /** The last date whose end alarms are over. */
    val endDoneDate: LocalDate? = null,
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

    suspend fun setEndOn(on: Boolean) = dataStore.edit { it[END_ON] = on }

    suspend fun setManualEnd(time: LocalTime?) = dataStore.edit {
        if (time == null) it.remove(MANUAL_END) else it[MANUAL_END] = time.toString()
    }

    suspend fun setEndAlerts(alerts: List<Long>) = dataStore.edit {
        it[END_ALERTS] = alerts.sortedDescending().joinToString(",")
    }

    suspend fun setEndDoneDate(date: LocalDate) = dataStore.edit { it[END_DONE_DATE] = date.toString() }

    suspend fun setDays(days: List<CalendarDay>) = dataStore.edit {
        it[DAYS] = json.encodeToString(DAYS_SERIALIZER, days)
    }

    suspend fun setDoneDate(date: LocalDate) = dataStore.edit { it[DONE_DATE] = date.toString() }

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
        val endOn = prefs[END_ON]
        val manualEnd = prefs[MANUAL_END]
        val endAlerts = prefs[END_ALERTS]
        prefs.clear()
        if (manualStart != null) prefs[MANUAL_START] = manualStart
        if (alerts != null) prefs[ALERTS] = alerts
        if (firstMinutes != null) prefs[FIRST_MINUTES] = firstMinutes
        if (setupDone != null) prefs[SETUP_DONE] = setupDone
        if (endOn != null) prefs[END_ON] = endOn
        if (manualEnd != null) prefs[MANUAL_END] = manualEnd
        if (endAlerts != null) prefs[END_ALERTS] = endAlerts
    }

    private fun Preferences.toSettings() = Settings(
        manualStart = parse(this[MANUAL_START], LocalTime::parse),
        alerts = alertsFrom(this[ALERTS], this[FIRST_MINUTES]),
        days = parse(this[DAYS]) { json.decodeFromString(DAYS_SERIALIZER, it) }.orEmpty(),
        doneDate = parse(this[DONE_DATE], LocalDate::parse),
        lastCallAt = parse(this[LAST_CALL_AT], LocalDateTime::parse),
        lastCallResult = this[LAST_CALL_RESULT],
        setupDone = this[SETUP_DONE] ?: false,
        endOn = this[END_ON] ?: false,
        manualEnd = parse(this[MANUAL_END], LocalTime::parse),
        endAlerts = this[END_ALERTS]?.split(",")?.mapNotNull { it.toLongOrNull() }.orEmpty()
            .ifEmpty { AlarmPlan.DEFAULT_END_ALERTS },
        endDoneDate = parse(this[END_DONE_DATE], LocalDate::parse),
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
        val LAST_CALL_AT = stringPreferencesKey("last_call_at")
        val LAST_CALL_RESULT = stringPreferencesKey("last_call_result")
        val SETUP_DONE = booleanPreferencesKey("setup_done")
        val END_ON = booleanPreferencesKey("end_on")
        val MANUAL_END = stringPreferencesKey("manual_end")
        val END_ALERTS = stringPreferencesKey("end_alerts")
        val END_DONE_DATE = stringPreferencesKey("end_done_date")

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
