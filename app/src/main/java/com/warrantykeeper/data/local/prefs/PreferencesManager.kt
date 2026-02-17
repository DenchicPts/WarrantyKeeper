package com.warrantykeeper.data.local.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

@Singleton
class PreferencesManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val dataStore = context.dataStore

    companion object {
        val KEY_IS_LOGGED_IN = booleanPreferencesKey("is_logged_in")
        val KEY_USER_EMAIL = stringPreferencesKey("user_email")
        val KEY_USER_NAME = stringPreferencesKey("user_name")
        val KEY_USER_PHOTO_URL = stringPreferencesKey("user_photo_url")
        val KEY_NOTIFICATIONS_ENABLED = booleanPreferencesKey("notifications_enabled")
        val KEY_NOTIFICATION_DAYS_BEFORE = intPreferencesKey("notification_days_before")
        val KEY_NOTIFICATION_TIME_HOUR = intPreferencesKey("notification_time_hour")
        val KEY_NOTIFICATION_TIME_MINUTE = intPreferencesKey("notification_time_minute")
    }

    val isLoggedIn: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[KEY_IS_LOGGED_IN] ?: false
    }

    val userEmail: Flow<String?> = dataStore.data.map { preferences ->
        preferences[KEY_USER_EMAIL]
    }

    val userName: Flow<String?> = dataStore.data.map { preferences ->
        preferences[KEY_USER_NAME]
    }

    val userPhotoUrl: Flow<String?> = dataStore.data.map { preferences ->
        preferences[KEY_USER_PHOTO_URL]
    }

    val notificationsEnabled: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[KEY_NOTIFICATIONS_ENABLED] ?: true
    }

    val notificationDaysBefore: Flow<Int> = dataStore.data.map { preferences ->
        preferences[KEY_NOTIFICATION_DAYS_BEFORE] ?: 7
    }

    val notificationTimeHour: Flow<Int> = dataStore.data.map { preferences ->
        preferences[KEY_NOTIFICATION_TIME_HOUR] ?: 10
    }

    val notificationTimeMinute: Flow<Int> = dataStore.data.map { preferences ->
        preferences[KEY_NOTIFICATION_TIME_MINUTE] ?: 0
    }

    suspend fun setLoginStatus(isLoggedIn: Boolean) {
        dataStore.edit { preferences ->
            preferences[KEY_IS_LOGGED_IN] = isLoggedIn
        }
    }

    suspend fun setUserData(email: String?, name: String?, photoUrl: String?) {
        dataStore.edit { preferences ->
            email?.let { preferences[KEY_USER_EMAIL] = it }
            name?.let { preferences[KEY_USER_NAME] = it }
            photoUrl?.let { preferences[KEY_USER_PHOTO_URL] = it }
        }
    }

    suspend fun setNotificationsEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[KEY_NOTIFICATIONS_ENABLED] = enabled
        }
    }

    suspend fun setNotificationDaysBefore(days: Int) {
        dataStore.edit { preferences ->
            preferences[KEY_NOTIFICATION_DAYS_BEFORE] = days
        }
    }

    suspend fun setNotificationTime(hour: Int, minute: Int) {
        dataStore.edit { preferences ->
            preferences[KEY_NOTIFICATION_TIME_HOUR] = hour
            preferences[KEY_NOTIFICATION_TIME_MINUTE] = minute
        }
    }

    suspend fun clearUserData() {
        dataStore.edit { preferences ->
            preferences.clear()
        }
    }
}
