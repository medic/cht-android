package org.medicmobile.webapp.mobile;

import static org.medicmobile.webapp.mobile.BuildConfig.DISABLE_APP_URL_VALIDATION;

import android.net.Uri;

public class UrlUtils {

	public static String getRootUrl(SettingsStore settings) {
		String appUrl = settings.getAppUrl();
		return appUrl + (DISABLE_APP_URL_VALIDATION ? "" : "/medic/_design/medic/_rewrite/");
	}

	public static String getUrlToLoad(SettingsStore settings, Uri url) {
		return url != null ? url.toString() : getRootUrl(settings);
	}

	public static boolean isRootUrl(SettingsStore settings, String url) {
		return getRootUrl(settings).equals(url);
	}
}
