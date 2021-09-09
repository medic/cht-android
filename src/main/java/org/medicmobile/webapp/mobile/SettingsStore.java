package org.medicmobile.webapp.mobile;

import android.content.*;

import java.util.*;
import java.util.regex.*;

import static org.medicmobile.webapp.mobile.BuildConfig.DEBUG;
import static org.medicmobile.webapp.mobile.SimpleJsonClient2.redactUrl;
import static org.medicmobile.webapp.mobile.MedicLog.trace;

@SuppressWarnings("PMD.ShortMethodName")
public abstract class SettingsStore {
	private final SharedPreferences prefs;

	SettingsStore(SharedPreferences prefs) {
		this.prefs = prefs;
	}

	public abstract String getAppUrl();
	public abstract boolean hasWebappSettings();

	public abstract boolean allowsConfiguration();
	public abstract void update(SharedPreferences.Editor ed, WebappSettings s);

	String getUnlockCode() {
		return get("unlock-code");
	}

	void updateWith(WebappSettings s) throws SettingsException {
		s.validate();

		SharedPreferences.Editor ed = prefs.edit();

		update(ed, s);

		if(!ed.commit()) throw new SettingsException(
				"Failed to save to SharedPreferences.");
	}

	void updateWithUnlockCode(String unlockCode) throws SettingsException {
		SharedPreferences.Editor ed = prefs.edit();

		ed.putString("unlock-code", unlockCode);

		if(!ed.commit()) throw new SettingsException(
				"Failed to save 'unlock-code' to SharedPreferences.");
	}

	String get(String key) {
		return prefs.getString(key, null);
	}

	/**
	 * Returns true if the user has denied to provide its geolocation data.
	 * The rejection is taken from the first view with the "prominent" disclosure
	 * about the location data, not from the native dialog displayed by Android.
	 */
	boolean hasUserDeniedGeolocation() {
		return prefs.getBoolean("denied-geolocation", false);
	}

	/**
	 * @see #hasUserDeniedGeolocation()
	 */
	void setUserDeniedGeolocation() throws SettingsException {
		SharedPreferences.Editor ed = prefs.edit();
		ed.putBoolean("denied-geolocation", true);
		if(!ed.commit()) throw new SettingsException(
				"Failed to save 'denied-geolocation' to SharedPreferences.");
	}

	static SettingsStore in(Context ctx) {
		trace(SettingsStore.class, "Loading settings for context %s...", ctx);

		SharedPreferences prefs = ctx.getSharedPreferences(
				SettingsStore.class.getName(),
				Context.MODE_PRIVATE);

		String appHost = ctx.getResources().getString(R.string.app_host);
		String scheme = ctx.getResources().getString(R.string.scheme);
		if(appHost.length() > 0) {
			return new BrandedSettingsStore(prefs, scheme + "://" + appHost);
		}

		return new UnbrandedSettingsStore(prefs);
	}
}

@SuppressWarnings("PMD.CallSuperInConstructor")
class BrandedSettingsStore extends SettingsStore {
	private final String apiUrl;

	BrandedSettingsStore(SharedPreferences prefs, String apiUrl) {
		super(prefs);
		this.apiUrl = apiUrl;
	}

	public String getAppUrl() { return apiUrl; }
	public boolean hasWebappSettings() { return true; }
	public boolean allowsConfiguration() { return false; }

	public void update(SharedPreferences.Editor ed, WebappSettings s) { /* nothing to save */ }
}

@SuppressWarnings("PMD.CallSuperInConstructor")
class UnbrandedSettingsStore extends SettingsStore {
	UnbrandedSettingsStore(SharedPreferences prefs) {
		super(prefs);
	}

//> ACCESSORS
	public String getAppUrl() { return get("app-url"); }

	public boolean allowsConfiguration() { return true; }

	public boolean hasWebappSettings() {
		WebappSettings s = new WebappSettings(getAppUrl());
		try {
			s.validate();
			return true;
		} catch(IllegalSettingsException ex) {
			return false;
		}
	}

	public void update(SharedPreferences.Editor ed, WebappSettings s) {
		ed.putString("app-url", s.appUrl);
	}
}

class WebappSettings {
	public static final Pattern URL_PATTERN = Pattern.compile(
			"http[s]?://([^/:]*)(:\\d*)?(.*)");

	public final String appUrl;

	public WebappSettings(String appUrl) {
		if(DEBUG) trace(this, "WebappSettings() :: appUrl: %s", redactUrl(appUrl));
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

		if(!errors.isEmpty()) {
			throw new IllegalSettingsException(errors);
		}
	}

	public void update(SharedPreferences.Editor ed, WebappSettings s) {
		ed.putString("app-url", s.appUrl);
	}

	private boolean isSet(String val) {
		return val != null && val.length() > 0;
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
