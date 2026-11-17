package io.github.bluewind8791.checkinreminder.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
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
    /** Minutes before the start time of the first check alarm. */
    val firstMinutes: Long = AlarmPlan.DEFAULT_FIRST_MINUTES,
    /** The work schedule from the last call that returned one. */
    val days: List<CalendarDay> = emptyList(),
    /** The last date whose check alarms are over. */
    val doneDate: LocalDate? = null,
    val nextCheck: CheckAlarm? = null,
    val lastCallAt: LocalDateTime? = null,
    /** "200", "401", or the failure reason of the last background call. */
    val lastCallResult: String? = null,
)

/** Settings and alarm state. Holds no secret: the cookie is in [CookieStore]. */
class SettingsStore(context: Context) {
    private val dataStore = context.applicationContext.settingsDataStore

    val settings: Flow<Settings> = dataStore.data.map { it.toSettings() }

    suspend fun read(): Settings = settings.first()

    suspend fun setManualStart(time: LocalTime?) = dataStore.edit {
        if (time == null) it.remove(MANUAL_START) else it[MANUAL_START] = time.toString()
    }

    suspend fun setFirstMinutes(minutes: Long) = dataStore.edit { it[FIRST_MINUTES] = minutes }

    suspend fun setDays(days: List<CalendarDay>) = dataStore.edit {
        it[DAYS] = json.encodeToString(DAYS_SERIALIZER, days)
    }

    suspend fun setDoneDate(date: LocalDate) = dataStore.edit { it[DONE_DATE] = date.toString() }

    suspend fun setNextCheck(alarm: CheckAlarm?) = dataStore.edit {
        if (alarm == null) {
            it.remove(NEXT_DATE)
            it.remove(NEXT_STAGE)
            it.remove(NEXT_START)
            it.remove(NEXT_FIRST_MINUTES)
        } else {
            it[NEXT_DATE] = alarm.date.toString()
            it[NEXT_STAGE] = alarm.stage
            it[NEXT_START] = alarm.start.toString()
            it[NEXT_FIRST_MINUTES] = alarm.firstMinutes
        }
    }

    suspend fun setLastCall(at: LocalDateTime, result: String) = dataStore.edit {
        it[LAST_CALL_AT] = at.toString()
        it[LAST_CALL_RESULT] = result
    }

    /** Clears everything except the user's own settings. Used by logout. */
    suspend fun clearSession() = dataStore.edit { prefs ->
        val manualStart = prefs[MANUAL_START]
        val firstMinutes = prefs[FIRST_MINUTES]
        prefs.clear()
        if (manualStart != null) prefs[MANUAL_START] = manualStart
        if (firstMinutes != null) prefs[FIRST_MINUTES] = firstMinutes
    }

    private fun Preferences.toSettings() = Settings(
        manualStart = parse(this[MANUAL_START], LocalTime::parse),
        firstMinutes = this[FIRST_MINUTES] ?: AlarmPlan.DEFAULT_FIRST_MINUTES,
        days = parse(this[DAYS]) { json.decodeFromString(DAYS_SERIALIZER, it) }.orEmpty(),
        doneDate = parse(this[DONE_DATE], LocalDate::parse),
        nextCheck = parse(this[NEXT_DATE], LocalDate::parse)?.let { date ->
            val stage = this[NEXT_STAGE] ?: return@let null
            val start = parse(this[NEXT_START], LocalTime::parse) ?: return@let null
            CheckAlarm(date, stage, start, this[NEXT_FIRST_MINUTES] ?: AlarmPlan.DEFAULT_FIRST_MINUTES)
        },
        lastCallAt = parse(this[LAST_CALL_AT], LocalDateTime::parse),
        lastCallResult = this[LAST_CALL_RESULT],
    )

    /** A value this class wrote. Null when it is missing or does not parse, so a bad value never crashes the app. */
    private fun <T> parse(value: String?, parser: (String) -> T): T? =
        value?.let { runCatching { parser(it) }.getOrNull() }

    private companion object {
        val MANUAL_START = stringPreferencesKey("manual_start")
        val FIRST_MINUTES = longPreferencesKey("first_minutes")
        val DAYS = stringPreferencesKey("days")
        val DONE_DATE = stringPreferencesKey("done_date")
        val NEXT_DATE = stringPreferencesKey("next_date")
        val NEXT_STAGE = intPreferencesKey("next_stage")
        val NEXT_START = stringPreferencesKey("next_start")
        val NEXT_FIRST_MINUTES = longPreferencesKey("next_first_minutes")
        val LAST_CALL_AT = stringPreferencesKey("last_call_at")
        val LAST_CALL_RESULT = stringPreferencesKey("last_call_result")

        val DAYS_SERIALIZER = ListSerializer(CalendarDay.serializer())
        val json = Json { ignoreUnknownKeys = true }
    }
}
