package org.medicmobile.webapp.mobile;

import android.app.*;
import android.webkit.*;
import android.os.*;

import java.util.regex.*;

public class EmbeddedBrowserActivity extends Activity {
	private static final boolean DEBUG = BuildConfig.DEBUG;
	private static final String APP_URL =
			"https://demo:medic@demo.app.medicmobile.org";
	private static final Pattern BASIC_AUTH = Pattern.compile(
			"(http[s]?://)(\\w*):(\\w*)@([^/:]*)(:\\d*)?(.*)");

	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);

		WebView container = (WebView) findViewById(R.id.WebViewContainer);
		container.getSettings().setJavaScriptEnabled(true);

		String url = handleAuth(container, APP_URL);

		if(DEBUG) log("Pointing browser to %s", APP_URL);
		container.loadUrl(url);
	}

	private String handleAuth(WebView container, String url) {
		final Matcher m = BASIC_AUTH.matcher(url);
		if(!m.matches()) {
			log("URL does not match regex: %s", url);
			return url;
		}

		log("Setting up Basic Auth credentials from %s...", url);

		final String username = m.group(2);
		final String password = m.group(3);
		final String authHost = m.group(4);
		final String authPort = m.group(5);
		final String authRealm = "couch";

		String urlWithoutAuth = m.group(1) +
				m.group(4) +
				(m.group(5) == null ? "" : m.group(5)) +
				(m.group(6) == null ? "" : m.group(6));

		log("username=%s, password=%s, host=%s, port=%s, realm=%s",
				username, password, authHost, authPort, authRealm);

		container.setHttpAuthUsernamePassword(authHost, authRealm,
				username, password);

		container.setWebViewClient(new WebViewClient() {
			public void onReceivedHttpAuthRequest(
					WebView view,
					HttpAuthHandler handler,
					String requestHost,
					String requestRealm) {
				if(DEBUG) log("requestHost = " + requestHost);
				if(DEBUG) log("requestRealm = " + requestRealm);
				if(!((requestHost.equals(authHost) || requestHost.equals(authHost + authPort) &&
						requestRealm.equals(authRealm)))) {
					log("Not providing credntials for %s|%s",
							requestHost, requestRealm);
					return;
				}
				log("Providing credentials %s:%s to %s|%s",
					username, password,
					requestHost, requestRealm);
				handler.proceed(username, password);
			}
		});

		return urlWithoutAuth;
	}

	private void log(String message, Object...extras) {
		if(DEBUG) System.err.println("LOG | EmbeddedBrowserActivity::" +
				String.format(message, extras));
	}
}
