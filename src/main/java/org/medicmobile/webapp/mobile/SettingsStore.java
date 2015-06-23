package org.medicmobile.webapp.mobile;

import android.content.*;

import java.util.*;
import java.util.regex.*;

import static org.medicmobile.webapp.mobile.BuildConfig.DEBUG;

public class SettingsStore {
	private final SharedPreferences prefs;
	private static SettingsStore instance;

	private SettingsStore(SharedPreferences prefs) {
		this.prefs = prefs;
	}

	public synchronized static SettingsStore in(ContextWrapper ctx) {
		if(DEBUG) log("Loading settings for %s...", ctx);

		if(instance != null) return instance; // TODO this is a heck to help diagnose the NPE when loading from EmbeddedBrowserActivity

		SharedPreferences prefs = ctx.getSharedPreferences(
				SettingsStore.class.getName(),
				Context.MODE_PRIVATE);
		instance = new SettingsStore(prefs); // TODO more NPE hack

		if(ctx instanceof SettingsLoader) {
			if(DEBUG) log("Checking for settings in SharedPreferences...");
			if(instance.hasSettings()) return instance;

			if(DEBUG) log("Checking for settings in bundle...");
			if(instance.loadFromBundle()) return instance;

			if(BuildConfig.DEFAULT_TO_DEMO_APP) {
				if(DEBUG) log("Loading demo settings...");
				if(instance.loadDemo()) return instance;
			} else if(DEBUG) log("Not checking settings.");
		}

		return instance;
	}

//> ACCESSORS
	public String getAppUrl() { return get("app-url"); }

	public String getUsername() { return get("username"); }

	public String getPassword() { return get("password"); }

	private String get(String key) {
		return prefs.getString(key, null);
	}

	public boolean hasSettings() {
		try {
			new Settings(getAppUrl(), getUsername(), getPassword())
					.validate();
		} catch(IllegalSettingsException ex) {
			log("hasSettings() :: false");
			return false;
		}
		log("hasSettings() :: true");
		return true;
	}

	public boolean save(String appUrl, String username, String password) {
		try {
			save(new Settings(appUrl, username, password));
		} catch(SettingsException ex) {
			if(DEBUG) ex.printStackTrace();
			return false;
		}
		return true;
	}

	public void save(Settings s) throws SettingsException {
		s.validate();

		SharedPreferences.Editor ed = prefs.edit();
		ed.putString("app-url", s.appUrl);
		ed.putString("username", s.username);
		ed.putString("password", s.password);
		if(!ed.commit()) throw new SettingsException(
				"Failed to save to SharedPreferences.");
	}

//> LOADERS
	private boolean loadFromBundle() {
		// TODO implement this
		// TODO if read from bundle:
		//   1. save to preferences
		//   2. clear app cache and offline storage
		return false;
	}

	private boolean loadDemo() {
		return save("https://demo.app.medicmobile.org", "demo", "medic");
	}

	private static void log(String message, Object...extras) {
		if(BuildConfig.DEBUG) System.err.println("LOG | SettingsStore :: " +
				String.format(message, extras));
	}
}

class Settings {
	public static final Pattern URL_PATTERN = Pattern.compile(
			"http[s]?://([^/:]*)(:\\d*)?(.*)");

	public final String appUrl;
	public final String username;
	public final String password;

	public Settings(String appUrl, String username, String password) {
		if(BuildConfig.DEBUG) log("Settings() appUrl=%s; username=%s; password=%s",
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

class SettingsException extends Exception {
	public SettingsException(String message) {
		super(message);
	}
}

class IllegalSettingsException extends SettingsException {
	public final List<IllegalSetting> errors;

	public IllegalSettingsException(List<IllegalSetting> errors) {
		super(createMessage(errors));
		this.errors = errors;
	}

	private static String createMessage(List<IllegalSetting> errors) {
		if(DEBUG) {
			StringBuilder bob = new StringBuilder();
			for(IllegalSetting e : errors) {
				if(bob.length() > 0) bob.append("; ");
				bob.append(String.format(
						"%s: %s", e.componentId, e.message));
			}
			return bob.toString();
		}
		return null;
	}
}

interface SettingsLoader {}
