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

	public boolean hasSettings() {
		return isSet(getAppUrl()) &&
				isSet(getUsername()) &&
				isSet(getPassword());
	}

	private boolean isSet(String val) {
		return val != null && val.length() > 0;
	}
}
