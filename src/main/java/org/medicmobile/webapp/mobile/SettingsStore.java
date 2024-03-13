package org.medicmobile.webapp.mobile;

import static org.medicmobile.webapp.mobile.BuildConfig.DEBUG;
import static org.medicmobile.webapp.mobile.BuildConfig.TTL_LAST_URL;
import static org.medicmobile.webapp.mobile.MedicLog.trace;
import static org.medicmobile.webapp.mobile.SimpleJsonClient2.redactUrl;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;

import java.util.LinkedList;
import java.util.List;
import java.util.regex.Pattern;

import dagger.Module;
import dagger.Provides;
import dagger.hilt.InstallIn;
import dagger.hilt.android.components.ActivityComponent;
import dagger.hilt.android.qualifiers.ActivityContext;
import dagger.hilt.android.scopes.ActivityScoped;

@SuppressWarnings("PMD.ShortMethodName")
public abstract class SettingsStore {

	private final SharedPreferences prefs;

	SettingsStore(SharedPreferences prefs) {
		this.prefs = prefs;
	}

	public abstract String getAppUrl();

	public String getUrlToLoad(Uri url) {
		return url != null ? url.toString() : getAppUrl();
	}

	public boolean isRootUrl(String url) {
		if (url == null) {
			return false;
		}

		return getAppUrl().equals(AppUrlVerifier.clean(url));
	}

	public boolean allowCustomHosts() { return false; }

	public abstract boolean hasWebappSettings();

	public abstract boolean allowsConfiguration();

	public abstract void update(SharedPreferences.Editor ed, WebappSettings s);

	public void updateWith(WebappSettings s) throws SettingsException {
		s.validate();

		SharedPreferences.Editor ed = prefs.edit();

		update(ed, s);

		if (!ed.commit()) {
			throw new SettingsException("Failed to save to SharedPreferences.");
		}
	}

	String get(String key) {
		return prefs.getString(key, null);
	}

	/**
	 * Return last visited URL in the app, within TTL_LAST_URL milliseconds.
	 */
	String getLastUrl() {
		long lastUrlTimeMillis = prefs.getLong("last-url-time-ms", 0);
		long lastUrlTimeMillisFromNow = System.currentTimeMillis() - lastUrlTimeMillis;
		if (lastUrlTimeMillisFromNow > TTL_LAST_URL) {
			return null;
		}
		String lastUrl = prefs.getString("last-url", null);
		trace(this, "SettingsStore() :: getting last-url: %s", lastUrl);
		return lastUrl;
	}

	/**
	 * Set last visited URL in the app.
	 */
	void setLastUrl(String lastUrl) throws SettingsException {
		trace(this, "SettingsStore() :: setting last-url: %s", lastUrl);
		SharedPreferences.Editor ed = prefs.edit();
		ed.putString("last-url", lastUrl);
		ed.putLong("last-url-time-ms", System.currentTimeMillis());
		if (!ed.commit()) {
			throw new SettingsException("Failed to save 'last-url' to SharedPreferences.");
		}
	}

	public static SettingsStore in(Context ctx) {
		trace(SettingsStore.class, "Loading settings for context %s...", ctx);

		SharedPreferences prefs = ctx.getSharedPreferences(
				SettingsStore.class.getName(),
				Context.MODE_PRIVATE);

		String appHost = ctx.getResources().getString(R.string.app_host);
		String scheme = ctx.getResources().getString(R.string.scheme);
		if(appHost.length() > 0) {
			return new BrandedSettingsStore(prefs, scheme + "://" + appHost);
		}

		Boolean allowCustomHosts = ctx.getResources().getBoolean(R.bool.allowCustomHosts);
		return new UnbrandedSettingsStore(prefs, allowCustomHosts);
	}

	@Module
	@InstallIn(ActivityComponent.class)
	public static class SettingsStoreModule {
		@Provides
		@ActivityScoped
		public static SettingsStore provideSettingsStore(@ActivityContext Context ctx) {
			return SettingsStore.in(ctx);
		}
	}

	public static class WebappSettings {

		public static final Pattern URL_PATTERN = Pattern.compile("http[s]?://([^/:]*)(:\\d*)?(.*)");

		public final String appUrl;

		public WebappSettings(String appUrl) {
			trace(this, "WebappSettings() :: appUrl: %s", redactUrl(appUrl));
			this.appUrl = appUrl;
		}

		public void validate() throws IllegalSettingsException {
			List<IllegalSetting> errors = new LinkedList<>();

			if (!isSet(appUrl)) {
				errors.add(new IllegalSetting(R.id.txtAppUrl, R.string.errRequired));
			} else if (!URL_PATTERN.matcher(appUrl).matches()) {
				errors.add(new IllegalSetting(R.id.txtAppUrl, R.string.errInvalidUrl));
			}

			if (!errors.isEmpty()) {
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

	public static class IllegalSetting {

		public final int componentId;
		public final int errorStringId;

		public IllegalSetting(int componentId, int errorStringId) {
			this.componentId = componentId;
			this.errorStringId = errorStringId;
		}
	}

	public static class SettingsException extends Exception {
		// See: https://pmd.github.io/pmd-6.36.0/pmd_rules_java_errorprone.html#missingserialversionuid
		public static final long serialVersionUID = -1008287132276329302L;

		public SettingsException(String message) {
			super(message);
		}
	}

	public static class IllegalSettingsException extends SettingsException {

		public final List<IllegalSetting> errors;

		public IllegalSettingsException(List<IllegalSetting> errors) {
			super(createMessage(errors));
			this.errors = errors;
		}

		private static String createMessage(List<IllegalSetting> errors) {
			if (DEBUG) {
				StringBuilder bob = new StringBuilder();
				for (IllegalSetting e : errors) {
					if (bob.length() > 0) {
						bob.append("; ");
					}

					bob.append(String.format("component[%s]: error[%s]", e.componentId, e.errorStringId));
				}
				return bob.toString();
			}
			return null;
		}
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
	private final Boolean allowCustomHosts;

	UnbrandedSettingsStore(SharedPreferences prefs, Boolean allowCustomHosts) {
		super(prefs);

		this.allowCustomHosts = allowCustomHosts;
	}

//> ACCESSORS
	public String getAppUrl() { return get("app-url"); }

	public boolean allowsConfiguration() { return true; }

	public boolean allowCustomHosts() { return this.allowCustomHosts; }

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
