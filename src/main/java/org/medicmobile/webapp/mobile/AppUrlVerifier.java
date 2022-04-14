package org.medicmobile.webapp.mobile;

import java.io.IOException;
import java.net.MalformedURLException;
import java.util.concurrent.Callable;

import org.json.JSONException;
import org.json.JSONObject;

import static org.medicmobile.webapp.mobile.BuildConfig.DEBUG;
import static org.medicmobile.webapp.mobile.MedicLog.trace;
import static org.medicmobile.webapp.mobile.R.string.errAppUrl_apiNotReady;
import static org.medicmobile.webapp.mobile.R.string.errAppUrl_appNotFound;
import static org.medicmobile.webapp.mobile.R.string.errAppUrl_serverNotFound;
import static org.medicmobile.webapp.mobile.R.string.errInvalidUrl;
import static org.medicmobile.webapp.mobile.SimpleJsonClient2.redactUrl;

public class AppUrlVerifier {

	private final SimpleJsonClient2 jsonClient;

	AppUrlVerifier(SimpleJsonClient2 jsonClient) {
		this.jsonClient = jsonClient;
	}

	public AppUrlVerifier() {
		this(new SimpleJsonClient2());
	}

	/**
	 * Verify the string passed is a valid CHT-Core URL.
	 */
	public AppUrlVerification verify(String appUrl) {
		appUrl = clean(appUrl);

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
	protected String clean(String appUrl) {
		appUrl = appUrl.trim();
		if (appUrl.endsWith("/")) {
			return appUrl.substring(0, appUrl.length()-1);
		}
		return appUrl;
	}
}

@SuppressWarnings("PMD.ShortMethodName")
class AppUrlVerification {
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

class AppUrlVerificationTask implements Callable<AppUrlVerification> {
	private final String appUrl;

	public AppUrlVerificationTask(String appUrl) {
		this.appUrl = appUrl;
	}

	@Override
	public AppUrlVerification call() {
		trace(this, "AppUrlVerificationTask :: Executing call, appUrl=%s", appUrl);

		if (DEBUG && (appUrl == null || appUrl.isEmpty())) {
			throw new RuntimeException("AppUrlVerificationTask :: Cannot verify APP URL because it is not defined.");
		}

		return new AppUrlVerifier().verify(appUrl);
	}
}
