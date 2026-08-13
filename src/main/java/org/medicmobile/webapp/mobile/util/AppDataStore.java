package org.medicmobile.webapp.mobile.util;

import static org.medicmobile.webapp.mobile.MedicLog.log;

import android.content.Context;

import androidx.annotation.Nullable;
import androidx.annotation.OptIn;
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler;
import androidx.datastore.preferences.core.MutablePreferences;
import androidx.datastore.preferences.core.Preferences;
import androidx.datastore.preferences.core.Preferences.Key;
import androidx.datastore.preferences.core.PreferencesKeys;
import androidx.datastore.preferences.rxjava3.RxPreferenceDataStoreBuilder;
import androidx.datastore.rxjava3.RxDataStore;

import org.medicmobile.webapp.mobile.AppNotificationManager;

import java.util.concurrent.TimeUnit;

import io.reactivex.rxjava3.core.Single;

@OptIn(markerClass = kotlinx.coroutines.ExperimentalCoroutinesApi.class)
public class AppDataStore {
	private static final String DATASTORE_NAME = "cht_datastore";
	private static final long WRITE_TIMEOUT_SECONDS = 5;
	private static AppDataStore instance;
	private final RxDataStore<Preferences> dataStore;

	private AppDataStore(Context context) {
		ReplaceFileCorruptionHandler<Preferences> corruptionHandler = new ReplaceFileCorruptionHandler<>(corruptionException -> {
			log(corruptionException, "Data store corrupted");
			return new MutablePreferences();
		});
		dataStore = new RxPreferenceDataStoreBuilder(context, DATASTORE_NAME)
			.setCorruptionHandler(corruptionHandler)
			.build();
	}

	public static synchronized AppDataStore getInstance(Context context) {
		if (instance == null) {
			instance = new AppDataStore(context);
		}
		return instance;
	}

	private <T> Single<Preferences> save(Key<T> prefKey, T value) {
		return dataStore.updateDataAsync(preferences -> {
			MutablePreferences mutablePreferences = preferences.toMutablePreferences();
			mutablePreferences.set(prefKey, value);
			return Single.just(mutablePreferences);
		});
	}

	public void saveString(String key, String value) {
		save(PreferencesKeys.stringKey(key), value);
	}

	public void saveLong(String key, Long value) {
		save(PreferencesKeys.longKey(key), value);
	}

	public void saveLongBlocking(String key, Long value) {
		Single<Preferences> updateResult = save(PreferencesKeys.longKey(key), value);
		Preferences ignored = updateResult.blockingGet(); // NOSONAR
	}

	public void saveStringBlocking(String key, String value) {
		Single<Preferences> updateResult = save(PreferencesKeys.stringKey(key), value);
		Preferences ignored = updateResult.blockingGet(); // NOSONAR
	}

	private <T> T getBlocking(Key<T> key, @Nullable T defaultValue) {
		return dataStore
			.data()
			.map(preferences -> {
				T value = preferences.get(key);
				return value != null ? value : defaultValue;
			})
			.blockingFirst();
	}

	public String getStringBlocking(String key, @Nullable String defaultValue) {
		return getBlocking(PreferencesKeys.stringKey(key), defaultValue);
	}

	public long getLongBlocking(String key, @Nullable Long defaultValue) {
		return getBlocking(PreferencesKeys.longKey(key), defaultValue);
	}

	/**
	 * Persists the task-notifications, settings and max-count in a single atomic transaction.
	 */
	public void saveTaskNotificationSettingsBlocking(String settings, String notifications) {
		try {
			Preferences ignored = dataStore // NOSONAR
				.updateDataAsync(preferences -> {
					MutablePreferences mutablePreferences = preferences.toMutablePreferences();
					mutablePreferences.set(PreferencesKeys
						.stringKey(AppNotificationManager.TASK_NOTIFICATION_SETTINGS_KEY), settings);
					mutablePreferences.set(PreferencesKeys
						.stringKey(AppNotificationManager.TASK_NOTIFICATIONS_KEY), notifications);
					return Single.just(mutablePreferences);
				})
				.timeout(WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
				.blockingGet();
		} catch (Exception e) {
			log(e, "AppDataStore :: saving task notification settings failed/timed out");
		}
	}
}
