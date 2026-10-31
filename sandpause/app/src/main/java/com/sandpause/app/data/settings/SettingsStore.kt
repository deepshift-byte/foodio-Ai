package com.sandpause.app.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.sandpause.app.di.ApplicationScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * All settings are kept in DataStore, in device-protected storage, so the lock
 * can be restored at LOCKED_BOOT_COMPLETED before the user unlocks the phone.
 */
@Singleton
class SettingsStore @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    @ApplicationScope scope: CoroutineScope,
) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private val settingsKey = stringPreferencesKey("settings_json")
    private val lockKey = stringPreferencesKey("lock_json")
    private val dataKey = stringPreferencesKey("data_json")

    val settings: StateFlow<SettingsDto> = dataStore.data
        .map { decode<SettingsDto>(it[settingsKey], SettingsDto()) }
        .distinctUntilChanged()
        .stateIn(scope, SharingStarted.Eagerly, SettingsDto())

    val lock: Flow<LockSnapshotDto> = dataStore.data
        .map { decode<LockSnapshotDto>(it[lockKey], LockSnapshotDto()) }
        .distinctUntilChanged()

    val dataState: Flow<DataStateDto> = dataStore.data
        .map { decode<DataStateDto>(it[dataKey], DataStateDto()) }
        .distinctUntilChanged()

    /** Reads from DataStore directly, so it is correct even at early boot before the StateFlow has loaded. */
    suspend fun currentSettings(): SettingsDto =
        decode(dataStore.data.first()[settingsKey], SettingsDto())

    suspend fun currentLock(): LockSnapshotDto =
        decode(dataStore.data.first()[lockKey], LockSnapshotDto())

    suspend fun currentData(): DataStateDto =
        decode(dataStore.data.first()[dataKey], DataStateDto())

    suspend fun updateSettings(transform: (SettingsDto) -> SettingsDto) {
        dataStore.edit { prefs ->
            val current = decode<SettingsDto>(prefs[settingsKey], SettingsDto())
            prefs[settingsKey] = json.encodeToString(transform(current))
        }
    }

    suspend fun updateLock(transform: (LockSnapshotDto) -> LockSnapshotDto) {
        dataStore.edit { prefs ->
            val current = decode<LockSnapshotDto>(prefs[lockKey], LockSnapshotDto())
            prefs[lockKey] = json.encodeToString(transform(current))
        }
    }

    suspend fun updateData(transform: (DataStateDto) -> DataStateDto) {
        dataStore.edit { prefs ->
            val current = decode<DataStateDto>(prefs[dataKey], DataStateDto())
            prefs[dataKey] = json.encodeToString(transform(current))
        }
    }

    private inline fun <reified T> decode(raw: String?, default: T): T {
        if (raw.isNullOrBlank()) return default
        return runCatching { json.decodeFromString<T>(raw) }.getOrDefault(default)
    }
}
