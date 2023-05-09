package org.medicmobile.webapp.mobile;

import static org.medicmobile.webapp.mobile.MedicLog.trace;
import static org.medicmobile.webapp.mobile.R.string.errAppUrl_apiNotReady;
import static org.medicmobile.webapp.mobile.R.string.errAppUrl_appNotFound;
import static org.medicmobile.webapp.mobile.R.string.errAppUrl_serverNotFound;
import static org.medicmobile.webapp.mobile.R.string.errInvalidUrl;
import static org.medicmobile.webapp.mobile.SimpleJsonClient2.redactUrl;

import org.json.JSONException;
import org.json.JSONObject;
import org.medicmobile.webapp.mobile.AppUrlVerifier.AppUrlVerification;

import java.io.IOException;
import java.net.MalformedURLException;
import java.util.concurrent.Callable;

public class AppUrlVerifier implements Callable<AppUrlVerification> {

	private final SimpleJsonClient2 jsonClient;
	private final String appUrl;

	AppUrlVerifier(SimpleJsonClient2 jsonClient, String appUrl) {
		if (Utils.isDebug() && (appUrl == null || appUrl.trim().isEmpty())) {
			throw new RuntimeException("AppUrlVerifier :: Cannot verify APP URL because it is not defined.");
		}

		this.jsonClient = jsonClient;
		this.appUrl = appUrl;
	}

	public AppUrlVerifier(String appUrl) {
		this(new SimpleJsonClient2(), appUrl);
	}

	/**
	 * Verify the string passed is a valid CHT-Core URL.
	 */
	public AppUrlVerification call() {
		String appUrl = clean(this.appUrl);

		try {
			JSONObject json = jsonClient.get(appUrl + "/setup/poll");

			if (!json.getString("handler").equals("medic-api")) {
				return AppUrlVerification.failure(appUrl, errAppUrl_appNotFound);
			}

			if (!json.getBoolean("ready")) {
				return AppUrlVerification.failure(appUrl, errAppUrl_apiNotReady);
			}

			return AppUrlVerification.ok(appUrl);

		} catch(MalformedURLException ex) {
			// seems unlikely, as we should have verified this already
			return AppUrlVerification.failure(appUrl, errInvalidUrl);
		} catch(JSONException ex) {
			return AppUrlVerification.failure(appUrl, errAppUrl_appNotFound);
		} catch(IOException ex) {
			trace(ex, "Exception caught trying to verify url: %s", redactUrl(appUrl));
			return AppUrlVerification.failure(appUrl, errAppUrl_serverNotFound);
		}
	}

	/**
	 * Clean-up the URL passed, removing leading and trailing spaces, and trailing "/" char
	 * that the user may input by mistake.
	 */
	public static String clean(String appUrl) {
		appUrl = appUrl.trim();
		if (appUrl.endsWith("/")) {
			return appUrl.substring(0, appUrl.length()-1);
		}
		return appUrl;
	}

	@SuppressWarnings("PMD.ShortMethodName")
	public static class AppUrlVerification {
		public final String appUrl;
		public final boolean isOk;
		public final int failure;

		private AppUrlVerification(String appUrl, boolean isOk, int failure) {
			this.appUrl = appUrl;
			this.isOk = isOk;
			this.failure = failure;
		}

		//> FACTORIES
		public static AppUrlVerification ok(String appUrl) {
			return new AppUrlVerification(appUrl, true, 0);
		}

		public static AppUrlVerification failure(String appUrl, int failure) {
			return new AppUrlVerification(appUrl, false, failure);
		}
	}
}
