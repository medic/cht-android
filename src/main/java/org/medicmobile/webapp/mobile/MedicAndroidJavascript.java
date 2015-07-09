package org.medicmobile.webapp.mobile;

import android.webkit.*;

public class MedicAndroidJavascript {

	private final SettingsStore settings;

	public MedicAndroidJavascript(SettingsStore settings) {
		this.settings = settings;
	}

	@JavascriptInterface
	public int getAppVersion() {
		return BuildConfig.VERSION_CODE;
	}

	@JavascriptInterface
	public String getCouchDbUrl() {
		return settings.getAppUrl();
	}

	@JavascriptInterface
	public String getCouchDbUser() {
		return settings.getUsername();
	}

	@JavascriptInterface
	public String getCouchDbPass() {
		return settings.getPassword();
	}
}
