package org.medicmobile.webapp.mobile;

import static org.medicmobile.webapp.mobile.BuildConfig.APPLICATION_ID;
import static org.medicmobile.webapp.mobile.BuildConfig.DEBUG;
import static org.medicmobile.webapp.mobile.BuildConfig.VERSION_NAME;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.util.Optional;

final class Utils {
	private Utils() {}

	/**
	 * @see #isValidNavigationUrl(String, String)
	 */
	static boolean isUrlRelated(String appUrl, Uri uriToTest) {
		// android.net.Uri doesn't give us a host for URLs like blob:https://some-project.dev.medicmobile.org/abc-123
		// so we might as well just regex the URL string
		if (uriToTest != null) {
			return isUrlRelated(appUrl, uriToTest.toString());
		}
		return false;
	}

	/**
	 * Valid if the URLs aren't null, and uriToTest has as prefix appUrl,
	 * with or without the "blob:" prefix.
	 */
	static boolean isUrlRelated(String appUrl, String uriToTest) {
		// android.net.Uri doesn't give us a host for URLs like blob:https://some-project.dev.medicmobile.org/abc-123
		// so we might as well just regex the URL string
		if (appUrl != null && uriToTest != null) {
			return uriToTest.matches("^(blob:)?" + appUrl + "/.*$");
		}
		return false;
	}

	/**
	 * Same as {@link #isUrlRelated(String, String)}, and navUrl
	 * isn't the login page nor a rewrite path.
	 */
	static boolean isValidNavigationUrl(String appUrl, String navUrl) {
		boolean isValid = isUrlRelated(appUrl, navUrl);
		if (isValid && !navUrl.matches(".*/(login|_rewrite).*")) {
			return true;
		}
		return false;
	}

	static JSONObject json(Object... keyVals) throws JSONException {
		if(DEBUG && keyVals.length % 2 != 0) throw new AssertionError();
		JSONObject o = new JSONObject();
		for(int i=keyVals.length-1; i>0; i-=2) {
			o.put(keyVals[i-1].toString(), keyVals[i]);
		}
		return o;
	}

	static boolean intentHandlerAvailableFor(Context ctx, Intent intent) {
		return intent.resolveActivity(ctx.getPackageManager()) != null;
	}

	static void startAppActivityChain(Activity a) {
		if(SettingsStore.in(a).hasWebappSettings()) {
			a.startActivity(new Intent(a, EmbeddedBrowserActivity.class));
		} else {
			a.startActivity(new Intent(a, SettingsDialogActivity.class));
		}
		a.finish();
	}

	static String createUseragentFrom(String current) {
		if(current.contains(APPLICATION_ID)) return current;

		return String.format("%s %s/%s",
				current, APPLICATION_ID, VERSION_NAME);
	}

	static void restartApp(Context context) {
		Intent intent = new Intent(context, StartupActivity.class);
		intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
		context.startActivity(intent);
		Runtime.getRuntime().exit(0);
	}

	/**
	 * The file path can be a regular file or a content ("content://" scheme)
	 * @param path {String} File path
	 * @return {Uri}
	 */
	static Optional<Uri> getUriFromFilePath(String path) {
		if (path == null || path.isEmpty()) {
			return Optional.empty();
		}

		Uri parsedPath = Uri.parse(path);
		if ("content".equals(parsedPath.getScheme())) {
			return Optional.of(parsedPath);
		}

		File file = new File(path);
		if (!file.exists()) {
			return Optional.empty();
		}

		return Optional.of(Uri.fromFile(file));
	}

	static boolean isDebug() {
		return DEBUG;
	}
}
