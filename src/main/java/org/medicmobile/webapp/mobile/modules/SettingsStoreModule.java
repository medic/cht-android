package org.medicmobile.webapp.mobile.modules;

import android.content.Context;

import org.medicmobile.webapp.mobile.SettingsStore;

import dagger.Module;
import dagger.Provides;
import dagger.hilt.InstallIn;
import dagger.hilt.android.components.ActivityComponent;
import dagger.hilt.android.qualifiers.ActivityContext;

@Module
@InstallIn(ActivityComponent.class)
public class SettingsStoreModule {
	@Provides
	public static SettingsStore provideAnalyticsService(@ActivityContext Context ctx) {
		return SettingsStore.in(ctx);
	}
}
