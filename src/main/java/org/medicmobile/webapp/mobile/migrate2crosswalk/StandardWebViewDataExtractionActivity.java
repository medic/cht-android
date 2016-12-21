package org.medicmobile.webapp.mobile.migrate2crosswalk;

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
import android.widget.*;

import java.io.File;

import org.medicmobile.webapp.mobile.*;

import static org.medicmobile.webapp.mobile.BuildConfig.DEBUG;
import static org.medicmobile.webapp.mobile.BuildConfig.DISABLE_APP_URL_VALIDATION;
import static org.medicmobile.webapp.mobile.Utils.showProgressDialog;

public class StandardWebViewDataExtractionActivity extends Activity {
	private static final ValueCallback<String> IGNORE_RESULT = new ValueCallback<String>() {
		public void onReceiveValue(String result) {}
	};

	private WebView container;
	private SettingsStore settings;

	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		trace("Starting old webview...");

		this.settings = SettingsStore.in(this);

		setContentView(R.layout.data_extraction_webview);

		// Add an alarming red border if using configurable (i.e. dev)
		// app with a medic production server.
		if(settings.allowsConfiguration() &&
				settings.getAppUrl().contains("app.medicmobile.org")) {
			findViewById(R.id.lytWebView).setPadding(10, 10, 10, 10);
		}

		// TODO remove this asymmetric padding after we've finished testing
		findViewById(R.id.lytWebView).setPadding(10, 10, 30, 10);

		container = (WebView) findViewById(R.id.wbvMain);

		if(DEBUG) enableWebviewLoggingAndGeolocation(container);
		enableRemoteChromeDebugging(container);
		enableJavascript(container);
		enableStorage(container);

		enableUrlHandlers(container);

		browseToRoot();

		if(settings.allowsConfiguration()) {
			toast(settings.getAppUrl());
		}

		// TODO disableUserInteraction();

		final ProgressDialog progress = showProgressDialog(this, "Doing important things…");

		new AsyncTask<Void, Void, Void>() {
			protected Void doInBackground(Void..._) {
				// TODO get the number of docs or some other
				// measure of total work to do from the db

				// TODO begin the migration.  When each new doc
				// arrives, chalk up some more progress.

				return null;
			}

			protected void onPostExecute(Void _) {
				progress.dismiss();
				// TODO implement the following once the migration is handled above
				if(true) return;
				startActivity(new Intent(
						StandardWebViewDataExtractionActivity.this,
						EmbeddedBrowserActivity.class));
				StandardWebViewDataExtractionActivity.this.finish();
			}
		}.execute();
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

	private void browseToRoot() {
		String url = settings.getAppUrl() + (DISABLE_APP_URL_VALIDATION ?
				"" : "/medic/_design/medic/_rewrite/");
		if(DEBUG) trace("Pointing browser to %s", url);
		container.loadUrl(url);
	}

	private void enableRemoteChromeDebugging(WebView container) {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
			container.setWebContentsDebuggingEnabled(true);
		}
	}

	private void enableWebviewLoggingAndGeolocation(WebView container) {
		container.setWebChromeClient(new WebChromeClient() {
			public boolean onConsoleMessage(ConsoleMessage cm) {
				Log.d("MedicMobile", String.format("%s:%s | %s",
						cm.sourceId(),
						cm.lineNumber(),
						cm.message()));
				return true;
			}

			public void onGeolocationPermissionsShowPrompt(
					String origin,
					GeolocationPermissions.Callback callback) {
				// allow all location requests
				// TODO this should be restricted to the domain
				// set in Settings - issue #1603
				StandardWebViewDataExtractionActivity.this.trace(
						"onGeolocationPermissionsShowPrompt() :: origin=%s, callback=%s",
						origin,
						callback);
				callback.invoke(origin, true, true);
			}
		});
	}

	private void enableJavascript(WebView container) {
		container.getSettings().setJavaScriptEnabled(true);

		//MedicAndroidJavascript maj = new MedicAndroidJavascript(this);
		//maj.setAlert(new Alert(this));

		//maj.setLocationManager((LocationManager) this.getSystemService(Context.LOCATION_SERVICE));

		//container.addJavascriptInterface(maj, "medicmobile_android");
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

	private void enableUrlHandlers(WebView container) {
		container.setWebViewClient(new WebViewClient() {
			private final CouchReplicationTarget couch = new CouchReplicationTarget();

			public boolean shouldOverrideUrlLoading(WebView view, String url) {
				// Enable SMS and call handling
				if(url.startsWith("tel:") || url.startsWith("sms:")) {
					Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
					view.getContext().startActivity(i);
					return true;
				} else { // TODO if(url matches replication request...) {
					// TODO deny downward replication
					// TODO add SQLite handler for upward replication
					// to save all docs locally
					trace("shouldOverrideUrlLoading()::Could intercept url: %s", url);
				}
				return false;
			}

			public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
				trace("shouldInterceptRequest():: [%s] %s", request.getMethod(), request.getUrl());

				try {
					return couch.handle(request);
				} catch(CouchReplicationTargetException ex) {
					MedicLog.logException(ex, "Failed to handle %s request to %s - will use standard handling.", request.getMethod(), request.getUrl());
				}
				return null;
			}
		});
	}

	private void toast(String message) {
		Toast.makeText(container.getContext(), message, Toast.LENGTH_LONG).show();
	}

	private void trace(String message, Object...extras) {
		MedicLog.trace(this, message, extras);
	}
}
