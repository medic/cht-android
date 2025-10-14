package org.medicmobile.webapp.mobile.util;

import android.content.Context;
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.medicmobile.webapp.mobile.MedicLog.log

private const val DATASTORE_NAME = "cht_datastore"
val Context.dataStore by preferencesDataStore(
    name = DATASTORE_NAME,
    corruptionHandler = ReplaceFileCorruptionHandler { corruptionException ->
        log(corruptionException, "Data store corrupted")
        emptyPreferences()
    })

class AppDataStore(private val context: Context) {

    private val coroutineScope = CoroutineScope(Dispatchers.IO + Job())

    fun saveString(key: String, value: String) {
        coroutineScope.launch {
            try {
                val key = stringPreferencesKey(key)
                context.dataStore.edit { prefs -> prefs[key] = value }
            } catch (e: Exception) {
                log(e, "error saving string");
            }
        }
    }

    suspend fun getStringOnce(key: String, defaultValue: String = ""): String {
        val prefsKey = stringPreferencesKey(key)
        return context.dataStore.data.map { prefs -> prefs[prefsKey] ?: defaultValue }.first()
    }

    fun getStringBlocking(key: String, defaultValue: String): String {
        return runBlocking {
            getStringOnce(key, defaultValue)
        }
    }

    suspend fun saveLongSuspending(key: String, value: Long) {
            try {
                val prefsKey = longPreferencesKey(key)
                context.dataStore.edit { prefs -> prefs[prefsKey] = value }
            } catch (e: Exception) {
                log(e, "error saving long")
            }
    }

    fun saveLong(key: String, value: Long) {
        coroutineScope.launch { saveLongSuspending(key, value) }
    }

    fun saveLongBlocking(key: String, value: Long) {
        runBlocking {
            saveLongSuspending(key, value)
        }
    }

    suspend fun getLongOnce(key: String, defaultValue: Long = 0L): Long {
        val prefsKey = longPreferencesKey(key)
        return context.dataStore.data.map { prefs -> prefs[prefsKey] ?: defaultValue }.first()
    }

    fun getLongBlocking(key: String, defaultValue: Long): Long {
        return runBlocking {
            getLongOnce(key, defaultValue)
        }
    }
}
