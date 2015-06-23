package org.medicmobile.webapp.mobile;

public class SettingsStore {
	private static final boolean DEBUG = BuildConfig.DEBUG;

	public static final SettingsStore $ = new SettingsStore();

	private SettingsStore() {}

	public String getUsername() {
		return "demo";
	}

	public String getPassword() {
		return "medic";
	}

	public String getAppUrl() {
		return "https://demo.app.medicmobile.org";
	}
}
