package org.medicmobile.webapp.mobile;

import android.app.*;
import android.content.*;
import android.webkit.*;
import android.os.*;
import android.view.*;

import java.util.regex.*;

public class EmbeddedBrowserActivity extends Activity {
	private static final boolean DEBUG = BuildConfig.DEBUG;

	private final SettingsStore settings;

	public EmbeddedBrowserActivity() {
		this.settings = SettingsStore.in(this);
	}

	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);

		WebView container = (WebView) findViewById(R.id.WebViewContainer);
		container.getSettings().setJavaScriptEnabled(true);
		container.addJavascriptInterface(
				new MedicAndroidJavascript(),
				"medicmobile_android");

		handleAuth(container);

		String url = settings.getAppUrl();
		if(DEBUG) log("Pointing browser to %s", url);
		container.loadUrl(url);
	}

	public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.web_menu, menu);
		return super.onCreateOptionsMenu(menu);
	}

	public boolean onOptionsItemSelected(MenuItem item) {
		switch(item.getItemId()) {
			case R.id.mnuSettings:
				openSettings();
				return true;
			default:
				return super.onOptionsItemSelected(item);
		}
	}

	private void handleAuth(WebView container) {
		final String url = settings.getAppUrl();
		if(DEBUG) log("Setting up Basic Auth credentials for %s...", url);

		final Matcher m = Settings.URL_PATTERN.matcher(url);
		if(!m.matches()) {
			throw new IllegalArgumentException("URL does not appear valid: " + url);
		}
		final String authHost = m.group(1);
		final String authPort = m.group(2);
		final String authRealm = "couch";

		final String username = settings.getUsername();
		final String password = settings.getPassword();

		if(DEBUG) log("username=%s, password=%s, host=%s, port=%s, realm=%s",
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
				if(DEBUG) log("Providing credentials %s:%s to %s|%s",
					username, password,
					requestHost, requestRealm);
				handler.proceed(username, password);
			}
		});
	}

	private void openSettings() {
		startActivity(new Intent(this,
				SettingsDialogActivity.class));
		finish();
	}

	private void log(String message, Object...extras) {
		if(DEBUG) System.err.println("LOG | EmbeddedBrowserActivity::" +
				String.format(message, extras));
	}
}
