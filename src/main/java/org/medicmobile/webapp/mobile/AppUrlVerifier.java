package org.medicmobile.webapp.mobile;

import java.io.*;
import org.json.*;

import static org.medicmobile.webapp.mobile.R.string.*;

import static org.medicmobile.webapp.mobile.BuildConfig.DEBUG;

public class AppUrlVerifier {
	public AppUrlVerififcation verify(String appUrl) {
		try {
			JSONObject json = new SimpleJsonClient().get(appUrl);
			if(json.has("db_name"))
				return AppUrlVerififcation.ok(appUrl);
			else if(json.has("couchdb"))
				return AppUrlVerififcation.couchRootFound(appUrl);
			else return AppUrlVerififcation.failure(appUrl, errAppUrl_notACouchDb);
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
	public final boolean isCouchRoot;
	public final int failure;

	private AppUrlVerififcation(String appUrl, boolean isOk, int failure) {
		this.appUrl = appUrl;
		this.isOk = isOk;
		this.failure = failure;
		this.isCouchRoot = failure == errAppUrl_isCouchRoot;
	}

//> FACTORIES
	public static AppUrlVerififcation ok(String appUrl) {
		return new AppUrlVerififcation(appUrl, true, 0);
	}

	public static AppUrlVerififcation couchRootFound(String appUrl) {
		return failure(appUrl, errAppUrl_isCouchRoot);
	}

	public static AppUrlVerififcation failure(String appUrl, int failure) {
		return new AppUrlVerififcation(appUrl, false, failure);
	}
}
