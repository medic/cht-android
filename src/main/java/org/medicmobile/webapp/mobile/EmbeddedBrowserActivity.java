package org.medicmobile.webapp.mobile;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.location.*;
import android.net.*;
import android.os.*;
import android.util.*;
import android.view.*;
import android.view.inputmethod.*;
import android.webkit.*;

import java.io.File;

import static org.medicmobile.webapp.mobile.BuildConfig.DEBUG;
import static org.medicmobile.webapp.mobile.BuildConfig.DISABLE_APP_URL_VALIDATION;

public class EmbeddedBrowserActivity extends Activity {
	private static final ValueCallback<String> IGNORE_RESULT = new ValueCallback<String>() {
		public void onReceiveValue(String result) {}
	};

	private final ValueCallback<String> backButtonHandler = new ValueCallback<String>() {
		public void onReceiveValue(String result) {
			if(!"true".equals(result)) {
				EmbeddedBrowserActivity.this.moveTaskToBack(false);
			}
		}
	};

	private WebView container;
	private SettingsStore settings;

	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		this.settings = SettingsStore.in(this);

		this.requestWindowFeature(Window.FEATURE_NO_TITLE);
		setContentView(R.layout.main);

		// Add an alarming red border if using configurable (i.e. dev)
		// app with a medic production server.
		if(settings.allowsConfiguration() &&
				settings.getAppUrl().contains("app.medicmobile.org")) {
			findViewById(R.id.lytWebView).setPadding(10, 10, 10, 10);
		}

		container = (WebView) findViewById(R.id.wbvMain);

		if(DEBUG) enableWebviewLoggingAndDebugging(container);
		enableJavascript(container);
		enableStorage(container);

		enableSmsAndCallHandling(container);

		browseToRoot();
	}

	public boolean onCreateOptionsMenu(Menu menu) {
		if(settings.allowsConfiguration()) {
			getMenuInflater().inflate(R.menu.unbranded_web_menu, menu);
		} else {
			getMenuInflater().inflate(R.menu.web_menu, menu);
		}
		return super.onCreateOptionsMenu(menu);
	}

	public boolean onOptionsItemSelected(MenuItem item) {
		switch(item.getItemId()) {
			case R.id.mnuSettings:
				openSettings();
				return true;
			case R.id.mnuHardRefresh:
				browseToRoot();
				return true;
			case R.id.mnuLogout:
				evaluateJavascript("angular.element(document.body).scope().logout()");
				return true;
			default:
				return super.onOptionsItemSelected(item);
		}
	}

	public void onBackPressed() {
		if(container == null) {
			super.onBackPressed();
		} else {
			container.evaluateJavascript(
					"angular.element(document.body).scope().handleAndroidBack()",
					backButtonHandler);
		}
	}

	public void evaluateJavascript(final String js) {
		container.post(new Runnable() {
			public void run() {
				// `WebView.loadUrl()` seems to be significantly faster than
				// `WebView.evaluateJavascript()` on Tecno Y4.  We may find
				// confusing behaviour on Android 4.4+ when using `loadUrl()`
				// to run JS, in which case we should switch to the second
				// block.
				if(true) {
					container.loadUrl("javascript:" + js);
				} else {
					container.evaluateJavascript(js, IGNORE_RESULT);
				}
			}
		});
	}

	private void openSettings() {
		startActivity(new Intent(this,
				SettingsDialogActivity.class));
		finish();
	}

	private void browseToRoot() {
		String url = settings.getAppUrl() + (DISABLE_APP_URL_VALIDATION ?
				"" : "/medic/_design/medic/_rewrite/");
		if(DEBUG) log("Pointing browser to %s", url);
		container.loadUrl(url);
	}

	private void enableWebviewLoggingAndDebugging(WebView container) {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
			container.setWebContentsDebuggingEnabled(true);
		}

		container.setWebChromeClient(new WebChromeClient() {
			public boolean onConsoleMessage(ConsoleMessage cm) {
				Log.d("Medic Mobile", cm.message() + " -- From line "
						+ cm.lineNumber() + " of "
						+ cm.sourceId());
				return true;
			}

			public void onGeolocationPermissionsShowPrompt(
					String origin,
					GeolocationPermissions.Callback callback) {
				// allow all location requests
				// TODO this should be restricted to the domain
				// set in Settings - issue #1603
				EmbeddedBrowserActivity.this.log(
						"onGeolocationPermissionsShowPrompt() :: origin=%s, callback=%s",
						origin,
						callback);
				callback.invoke(origin, true, true);
			}
		});
	}

	private void enableJavascript(WebView container) {
		container.getSettings().setJavaScriptEnabled(true);

		MedicAndroidJavascript maj = new MedicAndroidJavascript(this);
		maj.setAlert(new Alert(this));

		maj.setLocationManager((LocationManager) this.getSystemService(Context.LOCATION_SERVICE));

		container.addJavascriptInterface(maj, "medicmobile_android");
	}

	private void enableStorage(WebView container) {
		WebSettings webSettings = container.getSettings();
		webSettings.setDatabaseEnabled(true);
		webSettings.setDomStorageEnabled(true);
		File dir = getCacheDir();
		if (!dir.exists()) {
			dir.mkdirs();
		}
		webSettings.setAppCachePath(dir.getPath());
		webSettings.setAppCacheEnabled(true);
	}

	private void enableSmsAndCallHandling(WebView container) {
		container.setWebViewClient(new WebViewClient() {
			public boolean shouldOverrideUrlLoading(WebView view, String url) {
				if(url.startsWith("tel:") || url.startsWith("sms:")) {
					Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
					view.getContext().startActivity(i);
					return true;
				}
				return false;
			}
		});
	}

	private void log(String message, Object...extras) {
		if(DEBUG) System.err.println("LOG | EmbeddedBrowserActivity::" +
				String.format(message, extras));
	}
}
