package org.medicmobile.webapp.mobile;

import java.io.IOException;
import java.net.MalformedURLException;
import org.json.JSONException;
import org.json.JSONObject;

import static org.medicmobile.webapp.mobile.BuildConfig.DISABLE_APP_URL_VALIDATION;
import static org.medicmobile.webapp.mobile.MedicLog.trace;
import static org.medicmobile.webapp.mobile.R.string.errAppUrl_apiNotReady;
import static org.medicmobile.webapp.mobile.R.string.errAppUrl_appNotFound;
import static org.medicmobile.webapp.mobile.R.string.errAppUrl_serverNotFound;
import static org.medicmobile.webapp.mobile.R.string.errInvalidUrl;
import static org.medicmobile.webapp.mobile.SimpleJsonClient2.redactUrl;

public class AppUrlVerifier {
	public AppUrlVerification verify(String appUrl) {
		if(DISABLE_APP_URL_VALIDATION) {
			return AppUrlVerification.ok(appUrl);
		}

		try {
			JSONObject json = new SimpleJsonClient2().get(appUrl + "/setup/poll");

			if(!json.getString("handler").equals("medic-api"))
				return AppUrlVerification.failure(appUrl, errAppUrl_appNotFound);
			if(!json.getBoolean("ready"))
				return AppUrlVerification.failure(appUrl, errAppUrl_apiNotReady);

			return AppUrlVerification.ok(appUrl);
		} catch(MalformedURLException ex) {
			// seems unlikely, as we should have verified this already
			return AppUrlVerification.failure(appUrl,
					errInvalidUrl);
		} catch(JSONException ex) {
			return AppUrlVerification.failure(appUrl,
					errAppUrl_appNotFound);
		} catch(IOException ex) {
			trace(ex, "Exception caught trying to verify url: %s", redactUrl(appUrl));
			return AppUrlVerification.failure(appUrl,
					errAppUrl_serverNotFound);
		}
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
