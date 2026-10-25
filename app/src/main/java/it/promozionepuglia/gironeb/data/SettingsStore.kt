package it.promozionepuglia.gironeb.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** Per quali partite notificare i gol: nessuna, tutto il girone o una sola squadra. */
sealed interface GoalAlerts {
    data object Off : GoalAlerts
    data object All : GoalAlerts
    data class Team(val key: String) : GoalAlerts

    fun covers(m: Match): Boolean = when (this) {
        Off -> false
        All -> true
        is Team -> m.involves(key)
    }
}

private val Context.dataStore by preferencesDataStore(name = "settings")

class SettingsStore(private val context: Context) {
    private val themeKey = stringPreferencesKey("theme_mode")
    private val goalAlertsKey = stringPreferencesKey("goal_alerts")
    private val permissionAskedKey = booleanPreferencesKey("notification_permission_asked")

    val themeMode: Flow<ThemeMode> = context.dataStore.data.map { prefs ->
        prefs[themeKey]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.SYSTEM
    }

    /** Di serie i gol di tutto il girone. */
    val goalAlerts: Flow<GoalAlerts> = context.dataStore.data.map { prefs ->
        when (val v = prefs[goalAlertsKey]) {
            null, ALL -> GoalAlerts.All
            OFF -> GoalAlerts.Off
            else -> GoalAlerts.Team(v)
        }
    }

    /** Se il permesso per le notifiche è già stato chiesto una volta all'avvio. */
    val notificationPermissionAsked: Flow<Boolean> = context.dataStore.data.map { it[permissionAskedKey] == true }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { it[themeKey] = mode.name }
    }

    suspend fun setGoalAlerts(alerts: GoalAlerts) {
        context.dataStore.edit {
            it[goalAlertsKey] = when (alerts) {
                GoalAlerts.Off -> OFF
                GoalAlerts.All -> ALL
                is GoalAlerts.Team -> alerts.key
            }
        }
    }

    suspend fun setNotificationPermissionAsked() {
        context.dataStore.edit { it[permissionAskedKey] = true }
    }

    private companion object {
        const val OFF = "off"
        const val ALL = "all"
    }
}
