package org.medicmobile.webapp.mobile;

import java.io.*;
import java.net.*;
import org.json.*;

import static org.medicmobile.webapp.mobile.R.string.*;

import static org.medicmobile.webapp.mobile.BuildConfig.DEBUG;
import static org.medicmobile.webapp.mobile.BuildConfig.DISABLE_APP_URL_VALIDATION;

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
			if(DEBUG) ex.printStackTrace();
			return AppUrlVerififcation.failure(appUrl,
					errAppUrl_serverNotFound);
		}
	}

	private boolean is200(String url) {
		if(DEBUG) log("is200() :: url=%s", url);
		HttpURLConnection conn = null;
		try {
			conn = (HttpURLConnection) new URL(url).openConnection();
			return conn.getResponseCode() == 200;
		} catch (Exception ex) {
			if(DEBUG) ex.printStackTrace();
			return false;
		} finally {
			if(conn != null) try {
				conn.disconnect();
			} catch(Exception ex) {
				if(DEBUG) ex.printStackTrace();
			}
		}
	}

	private void log(String message, Object...extras) {
		if(DEBUG) System.err.println("LOG | AppUrlVerifier::" +
				String.format(message, extras));
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
