package org.medicmobile.webapp.mobile;

import android.util.Base64;
import android.util.Log;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;

import org.json.JSONException;
import org.json.JSONObject;

import static org.medicmobile.webapp.mobile.BuildConfig.DEBUG;
import static org.medicmobile.webapp.mobile.BuildConfig.LOG_TAG;

/**
 * <p>New and improved - SimpleJsonClient2 is SimpleJsonClient, but using <code>
 * HttpURLConnection</code> instead of <code>DefaultHttpClient</code>.
 * <p>SimpleJsonClient2 should be used in preference to SimpleJsonClient on
 * Android 2.3 (API level 9/Gingerbread) and above.
 * @see java.net.HttpURLConnection
 * @see org.apache.http.impl.client.DefaultHttpClient
 */
public class SimpleJsonClient2 {
	private static final Pattern AUTH_URL = Pattern.compile("(.+)://(.*):(.*)@(.*)");

//> PUBLIC METHODS
	public JSONObject get(String url) throws MalformedURLException, JSONException, IOException {
		if(DEBUG) traceMethod("get", "url", redactUrl(url));
		return get(new URL(url));
	}

	public JSONObject get(URL url) throws JSONException, IOException {
		if(DEBUG) traceMethod("get", "url", redactUrl(url));
		HttpURLConnection conn = null;
		InputStream inputStream = null;
		BufferedReader reader = null;
		try {
			conn = openConnection(url);
			conn.setRequestProperty("Content-Type", "application/json");

			if(conn.getResponseCode() < 400) {
				inputStream = conn.getInputStream();
			} else {
				inputStream = conn.getErrorStream();
			}
			reader = new BufferedReader(new InputStreamReader(inputStream, "UTF-8"), 8);
			StringBuilder bob = new StringBuilder();

			String line = null;
			while((line = reader.readLine()) != null) {
				bob.append(line).append('\n');
			}

			String jsonString = bob.toString();
			if(DEBUG) log("get", "Retrieved JSON: " + jsonString);
			return new JSONObject(jsonString);
		} catch (JSONException | IOException ex) {
			throw ex;
		} finally {
			closeSafely("get", reader);
			closeSafely("get", inputStream);
			closeSafely("get", conn);
		}
	}

//> PUBLIC UTILS
	public static String redactUrl(URL url) {
		return redactUrl(url.toString());
	}
	public static String redactUrl(String url) {
		if(url == null) return null;

		Matcher m = AUTH_URL.matcher(url);
		if(!m.matches()) return url;

		return String.format("%s://%s:%s@%s",
				m.group(1), m.group(2), "****", m.group(4));
	}

//> INSTANCE HELPERS
	private static void traceMethod(String methodName, Object...args) {
		StringBuilder bob = new StringBuilder();
		for(int i=0; i<args.length; i+=2) {
			bob.append(args[i]);
			bob.append('=');
			bob.append(args[i+1]);
			bob.append(';');
		}
		log(methodName, bob.toString());
	}

	private void closeSafely(String method, Closeable c) {
		if(c != null) try {
			c.close();
		} catch(Exception ex) {
			if(DEBUG) log(ex, "SimpleJsonClient2.%s()", method);
		}
	}

	private void closeSafely(String method, HttpURLConnection conn) {
		if(conn != null) try {
			conn.disconnect();
		} catch(Exception ex) {
			if(DEBUG) log(ex, "SimpleJsonClient2.%s()", method);
		}
	}

//> STATIC HELPERS
	@SuppressWarnings("PMD.PreserveStackTrace")
	private static HttpURLConnection openConnection(URL url) throws IOException {
		HttpURLConnection conn = (HttpURLConnection) url.openConnection();

		if(url.getUserInfo() != null) {
			try {
				conn.setRequestProperty("Authorization", "Basic " + encodeCredentials(url.getUserInfo()));
			} catch(Exception ex) {
				// Don't include exception details in case they include auth details
				throw new RuntimeException(String.format("%s caught while setting Authorization header.", ex.getClass()));
			}
		}

		return conn;
	}

	/**
	 * Base64-encode the {@code user-pass} component of HTTP {@code Authorization: Basic}
	 * header.  Note that ISO-8859-1 encoding is used, unless the characterset is
	 * unavailable, in which case UTF-8 is used instead.
	 *
	 * <strong>N.B. passwords with some special characters may not work.</strong>
	 *
	 * @see https://tools.ietf.org/html/rfc2617#section-2
	 */
	@SuppressWarnings("PMD.PreserveStackTrace")
	private static String encodeCredentials(String normal) {
		try {
			return Base64.encodeToString(normal.getBytes("ISO-8859-1"), Base64.NO_WRAP);
		} catch(UnsupportedEncodingException ignored) {
			Log.i(LOG_TAG, "UnsupportedEncodingException thrown trying to encode HTTP basic auth credentials with ISO-8859-1.  Will try UTF-8.");
			try {
				return Base64.encodeToString(normal.getBytes("UTF-8"), Base64.NO_WRAP);
			} catch(UnsupportedEncodingException why) {
				// this should never happen on android, as UTF-8 is always the default encoding
				throw new RuntimeException(why);
			}
		}
	}

	private static void log(String methodName, String message) {
		Log.d(LOG_TAG, "SimpleJsonClient2." + methodName + "() :: " + message);
	}

	private static void log(Exception ex, String message, Object... extras) {
		Log.i(LOG_TAG, String.format(message, extras), ex);
	}
}
