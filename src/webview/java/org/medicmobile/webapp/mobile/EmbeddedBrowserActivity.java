package org.medicmobile.webapp.mobile;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.app.ActivityManager;
import android.net.Uri;
import android.net.ConnectivityManager;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.webkit.ConsoleMessage;
import android.webkit.GeolocationPermissions;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import java.util.Arrays;

import static android.content.pm.PackageManager.PERMISSION_GRANTED;

import static org.medicmobile.webapp.mobile.BuildConfig.DEBUG;
import static org.medicmobile.webapp.mobile.BuildConfig.DISABLE_APP_URL_VALIDATION;
import static org.medicmobile.webapp.mobile.MedicLog.error;
import static org.medicmobile.webapp.mobile.MedicLog.trace;
import static org.medicmobile.webapp.mobile.MedicLog.warn;
import static org.medicmobile.webapp.mobile.SimpleJsonClient2.redactUrl;
import static org.medicmobile.webapp.mobile.Utils.createUseragentFrom;
import static org.medicmobile.webapp.mobile.Utils.isUrlRelated;

@SuppressWarnings({ "PMD.GodClass", "PMD.TooManyMethods" })
public class EmbeddedBrowserActivity extends LockableActivity {
	/**
	 * Any activity result with all 3 low bits set is _not_ a simprints result.
	 *
	 * The following block of bit-shifted integers are intended for use in the subsystem seen
	 * in the onActivityResult below. These integers respect the reserved block of integers
	 * which are used by simprints. Simprint intents are started in the webapp where a matching
	 * bitmask is used to respect the scheme on that side of things.
	 * */
	private static final int NON_SIMPRINTS_FLAGS = 0x7;
	static final int GRAB_PHOTO_ACTIVITY_REQUEST_CODE = (0 << 3) | NON_SIMPRINTS_FLAGS;
	static final int GRAB_MRDT_PHOTO_ACTIVITY_REQUEST_CODE = (1 << 3) | NON_SIMPRINTS_FLAGS;
	static final int DISCLOSURE_LOCATION_ACTIVITY_REQUEST_CODE = (2 << 3) | NON_SIMPRINTS_FLAGS;

	// Arbitrarily selected value
	private static final int ACCESS_FINE_LOCATION_PERMISSION_REQUEST_CODE = 7038678;

	private static final String[] LOCATION_PERMISSIONS = { Manifest.permission.ACCESS_FINE_LOCATION };

	private static final ValueCallback<String> IGNORE_RESULT = new ValueCallback<String>() {
		public void onReceiveValue(String result) { /* ignore */ }
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
	private String appUrl;
	private SimprintsSupport simprints;
	private MrdtSupport mrdt;
	private PhotoGrabber photoGrabber;
	private SmsSender smsSender;

//> ACTIVITY LIFECYCLE METHODS
	@Override public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		trace(this, "Starting webview...");

		this.simprints = new SimprintsSupport(this);
		this.photoGrabber = new PhotoGrabber(this);
		this.mrdt = new MrdtSupport(this);
		try {
			this.smsSender = new SmsSender(this);
		} catch(Exception ex) {
			error(ex, "Failed to create SmsSender.");
		}

		this.settings = SettingsStore.in(this);
		this.appUrl = settings.getAppUrl();

		this.requestWindowFeature(Window.FEATURE_NO_TITLE);
		setContentView(R.layout.main);

		// Add an alarming red border if using configurable (i.e. dev)
		// app with a medic production server.
		if(settings.allowsConfiguration() &&
				appUrl.contains("app.medicmobile.org")) {
			View webviewContainer = findViewById(R.id.lytWebView);
			webviewContainer.setPadding(10, 10, 10, 10);
			webviewContainer.setBackgroundColor(R.drawable.warning_background);
		}

		container = findViewById(R.id.wbvMain);

		configureUseragent();

		setUpUiClient(container);
		enableRemoteChromeDebugging();
		enableJavascript(container);
		enableStorage(container);

		enableUrlHandlers(container);

		Intent appLinkIntent = getIntent();
		Uri appLinkData = appLinkIntent.getData();
		browseTo(appLinkData);

		if(settings.allowsConfiguration()) {
			toast(redactUrl(appUrl));
		}

	}

	@Override
	protected void onStart() {
		new XWalkMigration(this).run();
		super.onStart();
	}

	@Override public boolean onCreateOptionsMenu(Menu menu) {
		if(settings.allowsConfiguration()) {
			getMenuInflater().inflate(R.menu.unbranded_web_menu, menu);
		} else {
			getMenuInflater().inflate(R.menu.web_menu, menu);
		}
		return super.onCreateOptionsMenu(menu);
	}

	@Override public boolean onOptionsItemSelected(MenuItem item) {
		if (item.getItemId() == R.id.mnuGotoTestPages) {
			evaluateJavascript("window.location.href = 'https://medic.github.io/atp'");
			return true;
		}
		if (item.getItemId() == R.id.mnuSetUnlockCode) {
			changeCode();
			return true;
		}
		if (item.getItemId() == R.id.mnuSettings) {
			openSettings();
			return true;
		}
		if (item.getItemId() == R.id.mnuHardRefresh) {
			browseTo(null);
			return true;
		}
		if (item.getItemId() == R.id.mnuLogout) {
			evaluateJavascript("angular.element(document.body).injector().get('AndroidApi').v1.logout()");
			return true;
		}
		return super.onOptionsItemSelected(item);
	}

	@Override public void onBackPressed() {
		trace(this, "onBackPressed()");
		container.evaluateJavascript(
				"angular.element(document.body).injector().get('AndroidApi').v1.back()",
				backButtonHandler);
	}

	@Override protected void onActivityResult(int requestCode, int resultCode, Intent i) {
		try {
			trace(this, "onActivityResult() :: requestCode=%s, resultCode=%s",
					requestCodeToString(requestCode), resultCode);
			if((requestCode & NON_SIMPRINTS_FLAGS) == NON_SIMPRINTS_FLAGS) {
				switch(requestCode) {
					case GRAB_PHOTO_ACTIVITY_REQUEST_CODE:
						photoGrabber.process(requestCode, resultCode, i);
						return;
					case GRAB_MRDT_PHOTO_ACTIVITY_REQUEST_CODE:
						String js = mrdt.process(requestCode, resultCode, i);
						trace(this, "Execing JS: %s", js);
						evaluateJavascript(js);
						return;
					case DISCLOSURE_LOCATION_ACTIVITY_REQUEST_CODE:
						// User accepted or denied to allow the app to access
						// location data in RequestPermissionActivity
						if (resultCode == RESULT_OK) {                    // user accepted
							// Request to Android location data access
							ActivityCompat.requestPermissions(
									this,
									LOCATION_PERMISSIONS,
									ACCESS_FINE_LOCATION_PERMISSION_REQUEST_CODE);
						} else if (resultCode == RESULT_CANCELED) {        // user rejected
							try {
								this.locationRequestResolved();
								settings.setUserDeniedGeolocation();
							} catch (SettingsException e) {
								error(e, "Error recording negative to access location");
							}
						}
						return;
					default:
						trace(this, "onActivityResult() :: no handling for requestCode=%s",
								requestCodeToString(requestCode));
				}
			} else {
				String js = simprints.process(requestCode, i);
				trace(this, "Execing JS: %s", js);
				evaluateJavascript(js);
			}
		} catch(Exception ex) {
			String action = i == null ? null : i.getAction();
			warn(ex, "Problem handling intent %s (%s) with requestCode=%s & resultCode=%s",
					i, action, requestCodeToString(requestCode), resultCode);
		}
	}

	private String requestCodeToString(int requestCode) {
		if (requestCode == ACCESS_FINE_LOCATION_PERMISSION_REQUEST_CODE) {
			return "ACCESS_FINE_LOCATION_PERMISSION_REQUEST_CODE";
		}
		if (requestCode == DISCLOSURE_LOCATION_ACTIVITY_REQUEST_CODE) {
			return "DISCLOSURE_LOCATION_ACTIVITY_REQUEST_CODE";
		}
		return String.valueOf(requestCode);
	}

//> ACCESSORS
	SimprintsSupport getSimprintsSupport() {
		return this.simprints;
	}

	MrdtSupport getMrdtSupport() {
		return this.mrdt;
	}

	SmsSender getSmsSender() {
		return this.smsSender;
	}

//> PUBLIC API
	public void evaluateJavascript(final String js) {
		evaluateJavascript(js, true);
	}

	public void evaluateJavascript(final String js, final boolean useLoadUrl) {
		container.post(new Runnable() {
			public void run() {
				// `WebView.loadUrl()` seems to be significantly faster than
				// `WebView.evaluateJavascript()` on Tecno Y4.  We may find
				// confusing behaviour on Android 4.4+ when using `loadUrl()`
				// to run JS, in which case we should switch to the second
				// block.
				// On switching to XWalkView, we assume the same applies.
				if(useLoadUrl) { // NOPMD
					container.loadUrl("javascript:" + js, null);
				} else {
					container.evaluateJavascript(js, IGNORE_RESULT);
				}
			}
		});
	}

	public void errorToJsConsole(String message, Object... extras) {
		String formatted = String.format(message, extras);
		String escaped = formatted.replace("'", "\\'");
		evaluateJavascript("console.error('" + escaped + "');");
	}

//> PRIVATE HELPERS
	private void configureUseragent() {
		String current = WebSettings.getDefaultUserAgent(this);
		container.getSettings().setUserAgentString(createUseragentFrom(current));
	}

	private void openSettings() {
		startActivity(new Intent(this,
				SettingsDialogActivity.class));
		finish();
	}

	private String getRootUrl() {
		return appUrl + (DISABLE_APP_URL_VALIDATION ?
				"" : "/medic/_design/medic/_rewrite/");
	}

	private String getUrlToLoad(Uri url) {
		if (url != null) {
			return url.toString();
		}
		return getRootUrl();
	}

	private void browseTo(Uri url) {
		String urlToLoad = getUrlToLoad(url);
		if(DEBUG) trace(this, "Pointing browser to %s", redactUrl(urlToLoad));
		container.loadUrl(urlToLoad, null);
	}

	private void enableRemoteChromeDebugging() {
		WebView.setWebContentsDebuggingEnabled(true);
	}

	private void setUpUiClient(WebView container) {
		container.setWebChromeClient(new WebChromeClient() {
			@Override public boolean onConsoleMessage(ConsoleMessage cm) {
				if(!DEBUG) {
					return super.onConsoleMessage(cm);
				}

				trace(this, "onConsoleMessage() :: %s:%s | %s",
						cm.sourceId(),
						cm.lineNumber(),
						cm.message());
				return true;
			}
			@Override public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> filePathCallback, WebChromeClient.FileChooserParams fileChooserParams){
				if(DEBUG) trace(this, "onShowFileChooser() :: %s,%s,%s", webView, filePathCallback, fileChooserParams);

				boolean capture = fileChooserParams.isCaptureEnabled();
				trace(this, "onShowFileChooser() capture :: %s", capture);
				String[] acceptTypes = fileChooserParams.getAcceptTypes();
				trace(this, "onShowFileChooser() acceptTypes :: %s", Arrays.toString(acceptTypes));

				if(!photoGrabber.canHandle(acceptTypes, capture)) {
					warn(this, "openFileChooser() :: No file chooser is currently implemented for \"accept\" value: %s", Arrays.toString(acceptTypes));
					return false;
				}

				trace(this, "onShowFileChooser() opening chooser");
				photoGrabber.chooser(filePathCallback, capture);
				return true;
			}
			@Override public void onGeolocationPermissionsShowPrompt(final String origin, final GeolocationPermissions.Callback callback) {
				callback.invoke(origin, true, true);
			}
		});
	}

	public boolean getLocationPermissions() {
		if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PERMISSION_GRANTED) {
			trace(this, "getLocationPermissions() :: already granted");
			return true;
		}
		if (settings.hasUserDeniedGeolocation()) {
			trace(this, "getLocationPermissions() :: user has previously denied to share location");
			this.locationRequestResolved();
			return false;
		}
		trace(this, "getLocationPermissions() :: location not granted before, requesting access...");
		startActivityForResult(
				new Intent(this, RequestPermissionActivity.class),
				DISCLOSURE_LOCATION_ACTIVITY_REQUEST_CODE);
		return false;
	}

	@Override
	public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
		super.onRequestPermissionsResult(requestCode, permissions, grantResults);
		if (requestCode != ACCESS_FINE_LOCATION_PERMISSION_REQUEST_CODE) {
			return;
		}
		locationRequestResolved();
	}

	public void locationRequestResolved() {
		evaluateJavascript(
			"angular.element(document.body).injector().get('AndroidApi').v1.locationPermissionRequestResolved();");
	}

	@SuppressLint("SetJavaScriptEnabled")
	private void enableJavascript(WebView container) {
		container.getSettings().setJavaScriptEnabled(true);

		MedicAndroidJavascript maj = new MedicAndroidJavascript(this);
		maj.setAlert(new Alert(this));

		maj.setActivityManager((ActivityManager) this.getSystemService(Context.ACTIVITY_SERVICE));

		maj.setConnectivityManager((ConnectivityManager) this.getSystemService(Context.CONNECTIVITY_SERVICE));

		container.addJavascriptInterface(maj, "medicmobile_android");
	}

	private void enableStorage(WebView container) {
		WebSettings settings = container.getSettings();
		settings.setDomStorageEnabled(true);
		settings.setDatabaseEnabled(true);
	}

	private void enableUrlHandlers(WebView container) {

		container.setWebViewClient(new WebViewClient() {
			@Override
			public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
				Uri uri = request.getUrl();
				if (isUrlRelated(appUrl, uri)) {
					// load all related URLs in the webview
					return false;
				}

				// let Android decide what to do with unrelated URLs
				// unrelated URLs include `tel:` and `sms:` uri schemes
				Intent i = new Intent(Intent.ACTION_VIEW, uri);
				view.getContext().startActivity(i);
				return true;
			}

			@Override
			public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
				String failingUrl = request.getUrl().toString();
				if(DEBUG) trace(this, "onReceivedLoadError() :: %s,%s,%s", failingUrl, error.getErrorCode(), error.getDescription());
				if(!getRootUrl().equals(failingUrl)) {
					super.onReceivedError(view, request, error);
				} else {
					evaluateJavascript(String.format(
							"var body = document.evaluate('/html/body', document);" +
							"body = body.iterateNext();" +
							"if(body) {" +
							"  var content = document.createElement('div');" +
							"  content.innerHTML = '" +
							"<h1>Error loading page</h1>" +
							"<p>[%s] %s</p>" +
							"<button onclick=\"window.location.reload()\">Retry</button>" +
							"';" +
							"  body.appendChild(content);" +
							"}", error.getErrorCode(), error.getDescription()), false);
				}
			}
		});
	}

	private void toast(String message) {
		Toast.makeText(container.getContext(), message, Toast.LENGTH_LONG).show();
	}
}
