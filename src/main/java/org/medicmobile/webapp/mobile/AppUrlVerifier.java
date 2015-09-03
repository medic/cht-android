package org.medicmobile.webapp.mobile;

import java.io.*;
import java.net.*;
import org.json.*;

import static org.medicmobile.webapp.mobile.R.string.*;

import static org.medicmobile.webapp.mobile.BuildConfig.DEBUG;

public class AppUrlVerifier {
	public AppUrlVerififcation verify(String appUrl) {
		try {
			JSONObject json = new SimpleJsonClient2().get(appUrl);
			if(json.has("db_name"))
				return AppUrlVerififcation.ok(appUrl);
			else return AppUrlVerififcation.failure(appUrl, errAppUrl_notACouchDb);
		} catch(MalformedURLException ex) {
			// seems unlikely, as we should have verified this already
			return AppUrlVerififcation.failure(appUrl,
					errInvalidUrl);
		} catch(JSONException ex) {
			return AppUrlVerififcation.failure(appUrl,
					errAppUrl_notACouchDb);
		} catch(IOException ex) {
			if(DEBUG) ex.printStackTrace();
			return AppUrlVerififcation.failure(appUrl,
					errAppUrl_notFound);
		}
	}
}

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
