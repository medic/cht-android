package org.medicmobile.webapp.mobile.util;

import android.content.Context;
import android.util.Log;

import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler;
import androidx.datastore.preferences.core.MutablePreferences;
import androidx.datastore.preferences.core.Preferences;
import androidx.datastore.preferences.core.PreferencesKeys;
import androidx.datastore.preferences.rxjava3.RxPreferenceDataStoreBuilder;
import androidx.datastore.rxjava3.RxDataStore;

import io.reactivex.rxjava3.annotations.NonNull;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.observers.DisposableSingleObserver;
import io.reactivex.rxjava3.schedulers.Schedulers;

public class AppDataStore {
	private static final String DATASTORE_NAME = "cht_store";
	private final RxDataStore<Preferences> dataStore;
	private static volatile AppDataStore instance;

	private AppDataStore(Context context) {
		dataStore = new RxPreferenceDataStoreBuilder(context, DATASTORE_NAME)
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

	public Single<Preferences> setValue(Preferences.Key<String> key, String input) {
		return dataStore.updateDataAsync(prefsIn -> {
			MutablePreferences mp = prefsIn.toMutablePreferences();
			mp.set(key, input);
			return Single.just(mp);
		});
	}

	public Single<Preferences> setLongValue(Preferences.Key<Long> key, long value) {
		return dataStore.updateDataAsync(prefsIn -> {
			MutablePreferences mp = prefsIn.toMutablePreferences();
			mp.set(key, value);
			return Single.just(mp);
		});
	}

	public Single<String> getValue(Preferences.Key<String> key, String defaultReturnValue) {
		return dataStore.data()
				.map(prefs -> prefs.get(key))
				.first(defaultReturnValue)
				.subscribeOn(Schedulers.io());
	}

	public Single<Long> getLongValue(Preferences.Key<Long> key) {
		return dataStore.data()
				.map(preferences -> preferences.get(key))
				.first(0L)
				.subscribeOn(Schedulers.io());
	}


}
