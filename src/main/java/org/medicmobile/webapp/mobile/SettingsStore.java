package org.medicmobile.webapp.mobile;

import android.content.*;

import java.util.*;
import java.util.regex.*;

import static org.medicmobile.webapp.mobile.BuildConfig.DEBUG;

/**
 * Stores the server url to use. For branded apps, it's immutable.
 */
public abstract class SettingsStore {
	public static SettingsStore in(ContextWrapper ctx) {
		if(DEBUG) log("Loading settings for context %s...", ctx);

		String fixedAppUrl = ctx.getResources().
				getString(R.string.fixed_app_url);
		if(fixedAppUrl.length() > 0) {
			return new BrandedSettingsStore(fixedAppUrl);
		}

		SharedPreferences prefs = ctx.getSharedPreferences(
				SettingsStore.class.getName(),
				Context.MODE_PRIVATE);

		return new UnbrandedSettingsStore(prefs);
	}

	public abstract String getAppUrl();
	public abstract boolean hasSettings();
	public abstract void save(Settings s) throws SettingsException;
	public abstract boolean allowsConfiguration();

	private static void log(String message, Object...extras) {
		if(DEBUG) System.err.println("LOG | SettingsStore :: " +
				String.format(message, extras));
	}
}

class BrandedSettingsStore extends SettingsStore {
	private final String apiUrl;

	BrandedSettingsStore(String apiUrl) {
		this.apiUrl = apiUrl;
	}

	public String getAppUrl() { return apiUrl; }
	public boolean hasSettings() { return true; }
	public boolean allowsConfiguration() { return false; }

	public void save(Settings s) throws SettingsException {
		throw new SettingsException("Cannot save to BrandedSettingsStore.");
	}
}

class UnbrandedSettingsStore extends SettingsStore {
	private final SharedPreferences prefs;

	UnbrandedSettingsStore(SharedPreferences prefs) {
		this.prefs = prefs;
	}

//> ACCESSORS
	public String getAppUrl() { return get("app-url"); }

	private String get(String key) {
		return prefs.getString(key, null);
	}

	public Settings get() {
		Settings s = new Settings(getAppUrl());
		try {
			s.validate();
		} catch(IllegalSettingsException ex) {
			return null;
		}
		return s;
	}

	public boolean allowsConfiguration() { return true; }

	public boolean hasSettings() {
		return get() != null;
	}

	public void save(Settings s) throws SettingsException {
		s.validate();

		SharedPreferences.Editor ed = prefs.edit();
		ed.putString("app-url", s.appUrl);
		if(!ed.commit()) throw new SettingsException(
				"Failed to save to SharedPreferences.");
	}
}

class Settings {
	public static final Pattern URL_PATTERN = Pattern.compile(
			"http[s]?://([^/:]*)(:\\d*)?(.*)");

	public final String appUrl;

	public Settings(String appUrl) {
		if(DEBUG) log("Settings() appUrl=%s", appUrl);
		this.appUrl = appUrl;
	}

	public void validate() throws IllegalSettingsException {
		List<IllegalSetting> errors = new LinkedList<>();

		if(!isSet(appUrl)) {
			errors.add(new IllegalSetting(R.id.txtAppUrl,
					R.string.errRequired));
		} else if(!URL_PATTERN.matcher(appUrl).matches()) {
			errors.add(new IllegalSetting(R.id.txtAppUrl,
					R.string.errInvalidUrl));
		}

		if(errors.size() > 0) {
			throw new IllegalSettingsException(errors);
		}
	}

	private boolean isSet(String val) {
		return val != null && val.length() > 0;
	}

	private void log(String message, Object...extras) {
		if(DEBUG) System.err.println("LOG | Settings :: " +
				String.format(message, extras));
	}
}

class IllegalSetting {
	public final int componentId;
	public final int errorStringId;

	public IllegalSetting(int componentId, int errorStringId) {
		this.componentId = componentId;
		this.errorStringId = errorStringId;
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
						"component[%s]: error[%s]", e.componentId, e.errorStringId));
			}
			return bob.toString();
		}
		return null;
	}
}
