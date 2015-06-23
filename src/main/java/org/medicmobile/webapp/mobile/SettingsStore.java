package org.medicmobile.webapp.mobile;

import java.util.*;
import java.util.regex.*;

public class SettingsStore {
	private static final boolean DEBUG = BuildConfig.DEBUG;

	public static final SettingsStore $ = new SettingsStore();

	private Settings settings;

	private SettingsStore() {
		if(loadFromPreferences()) return;
		if(loadFromBundle()) return;
		if(BuildConfig.DEFAULT_TO_DEMO_APP) if(loadDemo()) return;
	}

	private boolean loadFromPreferences() {
		// TODO implement this
		return false;
	}

	private boolean loadFromBundle() {
		// TODO implement this
		// TODO if read from bundle:
		//   1. save to preferences
		//   2. clear app cache and offline storage
		return false;
	}

	private boolean loadDemo() {
		this.settings = new Settings(
				"https://demo.app.medicmobile.org",
				"demo", "medic");
		return true;
	}

	public String getAppUrl() {
		return settings.appUrl;
	}

	public String getUsername() {
		return settings.username;
	}

	public String getPassword() {
		return settings.password;
	}

	public void save(Settings s) throws IllegalSettingsException {
		s.validate();
		this.settings = s;
	}

	public boolean hasSettings() {
		return this.settings != null;
	}
}

class Settings {
	public static final Pattern URL_PATTERN = Pattern.compile(
			"http[s]?://([^/:]*)(:\\d*)?(.*)");

	public final String appUrl;
	public final String username;
	public final String password;

	public Settings(String appUrl, String username, String password) {
		if(BuildConfig.DEBUG) log("() appUrl=%s; username=%s; password=%s",
				appUrl, username, password);

		this.appUrl = appUrl;
		this.username = username;
		this.password = password;
	}

	public void validate() throws IllegalSettingsException {
		List<IllegalSetting> errors = new LinkedList<IllegalSetting>();

		if(!isSet(appUrl)) {
			errors.add(new IllegalSetting(R.id.txtAppUrl,
					"required"));
		} else if(!URL_PATTERN.matcher(appUrl).matches()) {
			errors.add(new IllegalSetting(R.id.txtAppUrl,
					"must be a valid URL"));
		}

		if(!isSet(username)) {
			errors.add(new IllegalSetting(R.id.txtUsername,
					"required"));
		}

		if(!isSet(password)) {
			errors.add(new IllegalSetting(R.id.txtPassword,
					"required"));
		}

		if(errors.size() > 0) {
			throw new IllegalSettingsException(errors);
		}
	}

	private boolean isSet(String val) {
		return val != null && val.length() > 0;
	}

	private void log(String message, Object...extras) {
		if(BuildConfig.DEBUG) System.err.println("LOG | Settings :: " +
				String.format(message, extras));
	}
}

class IllegalSetting {
	public final int componentId;
	public final String message;

	public IllegalSetting(int componentId, String message) {
		this.componentId = componentId;
		this.message = message;
	}
}

class IllegalSettingsException extends Exception {
	public final List<IllegalSetting> errors;

	public IllegalSettingsException(List<IllegalSetting> errors) {
		this.errors = errors;
	}
}
