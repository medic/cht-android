package org.medicmobile.webapp.mobile;

import java.io.IOException;
import java.net.MalformedURLException;
import org.json.JSONException;
import org.json.JSONObject;

import static org.medicmobile.webapp.mobile.BuildConfig.DEBUG;
import static org.medicmobile.webapp.mobile.BuildConfig.DISABLE_APP_URL_VALIDATION;
import static org.medicmobile.webapp.mobile.MedicLog.trace;
import static org.medicmobile.webapp.mobile.R.string.errAppUrl_apiNotReady;
import static org.medicmobile.webapp.mobile.R.string.errAppUrl_appNotFound;
import static org.medicmobile.webapp.mobile.R.string.errAppUrl_serverNotFound;
import static org.medicmobile.webapp.mobile.R.string.errInvalidUrl;
import static org.medicmobile.webapp.mobile.SimpleJsonClient2.redactUrl;

public class AppUrlVerifier {
	public AppUrlVerififcation verify(String appUrl) {
		if(DISABLE_APP_URL_VALIDATION) {
			return AppUrlVerififcation.ok(appUrl);
		}

		try {
			JSONObject json = new SimpleJsonClient2().get(appUrl + "/setup/poll");

			if(!json.getString("handler").equals("medic-api"))
				return AppUrlVerififcation.failure(appUrl, errAppUrl_appNotFound);
			if(!json.getBoolean("ready"))
				return AppUrlVerififcation.failure(appUrl, errAppUrl_apiNotReady);

			return AppUrlVerififcation.ok(appUrl);
		} catch(MalformedURLException ex) {
			// seems unlikely, as we should have verified this already
			return AppUrlVerififcation.failure(appUrl,
					errInvalidUrl);
		} catch(JSONException ex) {
			return AppUrlVerififcation.failure(appUrl,
					errAppUrl_appNotFound);
		} catch(IOException ex) {
			if(DEBUG) trace(ex, "Exception caught trying to verify url: %s", redactUrl(appUrl));
			return AppUrlVerififcation.failure(appUrl,
					errAppUrl_serverNotFound);
		}
	}
}

@SuppressWarnings("PMD.ShortMethodName")
class AppUrlVerififcation {
	public final String appUrl;
	public final boolean isOk;
	public final int failure;

	private AppUrlVerififcation(String appUrl, boolean isOk, int failure) {
		this.appUrl = appUrl;
		this.isOk = isOk;
		this.failure = failure;
	}

//> FACTORIES
	public static AppUrlVerififcation ok(String appUrl) {
		return new AppUrlVerififcation(appUrl, true, 0);
	}

	public static AppUrlVerififcation failure(String appUrl, int failure) {
		return new AppUrlVerififcation(appUrl, false, failure);
	}
}
