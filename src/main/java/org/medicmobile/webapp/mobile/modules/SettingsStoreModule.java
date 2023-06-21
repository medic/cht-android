package org.medicmobile.webapp.mobile.modules;

import android.content.Context;

import org.medicmobile.webapp.mobile.SettingsStore;

import dagger.Module;
import dagger.Provides;
import dagger.hilt.InstallIn;
import dagger.hilt.android.components.ActivityComponent;
import dagger.hilt.android.qualifiers.ActivityContext;
import dagger.hilt.android.scopes.ActivityScoped;

@Module
@InstallIn(ActivityComponent.class)
public class SettingsStoreModule {
	@Provides
	@ActivityScoped
	public static SettingsStore provideSettingsStore(@ActivityContext Context ctx) {
		return SettingsStore.in(ctx);
	}
}
