package org.medicmobile.webapp.mobile;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
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
	/** Any activity result with all 3 low bits set is _not_ a simprints result. */
	private static final int NON_SIMPRINTS_FLAGS = 0x7;
	static final int GRAB_PHOTO = (0 << 3) | NON_SIMPRINTS_FLAGS;
	static final int GRAB_MRDT_PHOTO = (1 << 3) | NON_SIMPRINTS_FLAGS;

	private final static int ACCESS_FINE_LOCATION_PERMISSION_REQUEST = (int)(Math.random() * 1000);

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

	public EmbeddedBrowserActivity() {}

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
			trace(this, "onActivityResult() :: requestCode=%s, resultCode=%s", requestCode, resultCode);
			if((requestCode & NON_SIMPRINTS_FLAGS) == NON_SIMPRINTS_FLAGS) {
				switch(requestCode) {
					case GRAB_PHOTO:
						photoGrabber.process(requestCode, resultCode, i);
						return;
					case GRAB_MRDT_PHOTO:
						String js = mrdt.process(requestCode, resultCode, i);
						trace(this, "Execing JS: %s", js);
						evaluateJavascript(js);
						return;
					default:
						trace(this, "onActivityResult() :: no handling for requestCode=%s", requestCode);
				}
			} else {
				String js = simprints.process(requestCode, i);
				trace(this, "Execing JS: %s", js);
				evaluateJavascript(js);
			}
		} catch(Exception ex) {
			String action = i == null ? null : i.getAction();
			warn(ex, "Problem handling intent %s (%s) with requestCode=%s & resultCode=%s", i, action, requestCode, resultCode);
		}
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
		container.post(new Runnable() {
			public void run() {
				// `WebView.loadUrl()` seems to be significantly faster than
				// `WebView.evaluateJavascript()` on Tecno Y4.  We may find
				// confusing behaviour on Android 4.4+ when using `loadUrl()`
				// to run JS, in which case we should switch to the second
				// block.
				// On switching to XWalkView, we assume the same applies.
				if(true) { // NOPMD
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
		final Activity self = this;
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
				AlertDialog.Builder builder = new AlertDialog.Builder(self);
				builder.setTitle(R.string.geolocationPermissionsTitle);
				builder.setMessage(R.string.geolocationPermissionsDescription).setCancelable(true)
						.setPositiveButton(R.string.geolocationPermissionsAllow, new DialogInterface.OnClickListener() {
							@Override public void onClick(DialogInterface dialog, int id) {
								callback.invoke(origin, true, true);
							}
						})
						.setNegativeButton(R.string.geolocationPermissionsDeny, new DialogInterface.OnClickListener() {
							@Override public void onClick(DialogInterface dialog, int id) {
								callback.invoke(origin, false, false);
							}
						});
				AlertDialog alert = builder.create();
				alert.show();
			}
		});
	}

	public boolean getLocationPermissions() {
		if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PERMISSION_GRANTED) {
			trace(this, "PERMISSIONS GRANTED");
			return true;
		}
		String[] permissions = { Manifest.permission.ACCESS_FINE_LOCATION };
		ActivityCompat.requestPermissions(this, permissions, ACCESS_FINE_LOCATION_PERMISSION_REQUEST);
		return false;
	}

	@Override
	public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
		super.onRequestPermissionsResult(requestCode, permissions, grantResults);
		if (requestCode != ACCESS_FINE_LOCATION_PERMISSION_REQUEST) {
			return;
		}
		String javaScript = "angular.element(document.body).injector().get('AndroidApi').v1.locationPermissionRequestResolved();";
		evaluateJavascript(javaScript);
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
				if(DEBUG) trace(this, "onReceivedLoadError() :: %s,%s,%s", view, request, error);
				String failingUrl = request.getUrl().toString();
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
							"}", error.getErrorCode(), error.getDescription()));
				}
			}
		});
	}

	private void toast(String message) {
		Toast.makeText(container.getContext(), message, Toast.LENGTH_LONG).show();
	}
}
