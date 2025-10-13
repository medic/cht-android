package org.medicmobile.webapp.mobile.util;

import static org.medicmobile.webapp.mobile.MedicLog.log;

import android.content.Context;

import androidx.datastore.core.CorruptionException;
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler;
import androidx.datastore.preferences.core.MutablePreferences;
import androidx.datastore.preferences.core.Preferences;
import androidx.datastore.preferences.rxjava3.RxPreferenceDataStoreBuilder;
import androidx.datastore.rxjava3.RxDataStore;

import io.reactivex.rxjava3.core.Single;
import kotlin.jvm.functions.Function1;

@kotlinx.coroutines.ExperimentalCoroutinesApi
public class AppDataStore {
	private static final String DATASTORE_NAME = "cht_store";
	private final RxDataStore<Preferences> dataStore;
	private static volatile AppDataStore instance;

	private AppDataStore(Context context) {
		dataStore = new RxPreferenceDataStoreBuilder(
				context, DATASTORE_NAME)
				.setCorruptionHandler(new ReplaceFileCorruptionHandler<>(new Function1<CorruptionException, Preferences>() {
					@Override
					public Preferences invoke(CorruptionException e) {
						log(e, "datastore corrupted");
						return new MutablePreferences();
					}
				}))
				.build();
	}

	public static AppDataStore getInstance(Context context) {
		if (instance == null) {
			synchronized (AppDataStore.class) {
				if (instance == null) {
					instance = new AppDataStore(context);
				}
			}
		}
		return instance;
	}


	public Single<Boolean> setValue(Preferences.Key<String> key, String input) {
		return dataStore.updateDataAsync(prefsIn -> {
					MutablePreferences mp = prefsIn.toMutablePreferences();
					mp.set(key, input);
					return Single.just(mp);
				})
				.map(prefs -> true)
				.onErrorReturn(error -> {
					log(error, "Error setting long value");
					return false;
				});
	}

	public Single<Boolean> setLongValue(Preferences.Key<Long> key, long value) {
		return dataStore.updateDataAsync(prefsIn -> {
					MutablePreferences mp = prefsIn.toMutablePreferences();
					mp.set(key, value);
					return Single.just(mp);
				})
				.map(prefs -> true)
				.onErrorReturn(error -> {
					log(error, "Error setting long value");
					return false;
				});
	}

	public Single<String> getValue(Preferences.Key<String> key, String defaultReturnValue) {
		return dataStore.data()
				.firstOrError()
				.map(prefs -> prefs.get(key) != null ? prefs.get(key) : defaultReturnValue)
				.onErrorReturn(error -> {
					log(error, "error getting value");
					return defaultReturnValue;
				});
	}

	public Single<Long> getLongValue(Preferences.Key<Long> key) {
		return dataStore.data()
				.firstOrError()
				.map(prefs -> prefs.get(key) != null ? prefs.get(key) : 0L)
				.onErrorReturn(error -> {
					log(error, "error getting value");
					return 0L;
				});
	}
}
