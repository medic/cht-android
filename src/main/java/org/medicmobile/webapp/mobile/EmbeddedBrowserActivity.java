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
import android.widget.*;

import java.io.File;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Collections;
import java.util.Map;

import org.medicmobile.webapp.mobile.migrate2crosswalk.FakeCouch;

import org.xwalk.core.XWalkPreferences;
import org.xwalk.core.XWalkResourceClient;
import org.xwalk.core.XWalkSettings;
import org.xwalk.core.XWalkUIClient;
import org.xwalk.core.XWalkView;

import static org.medicmobile.webapp.mobile.BuildConfig.DEBUG;
import static org.medicmobile.webapp.mobile.BuildConfig.DISABLE_APP_URL_VALIDATION;

public class EmbeddedBrowserActivity extends Activity implements MedicJsEvaluator {
	public static final String EXTRA_COOKIES = "medic.cookies";

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

	private XWalkView container;
	private SettingsStore settings;
	private FakeCouch fakeCouch;
	private String cookies;
	private boolean allowServerComms;

	@Override public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		log("Starting XWalk webview...");

		this.settings = SettingsStore.in(this);

		this.requestWindowFeature(Window.FEATURE_NO_TITLE);
		setContentView(R.layout.main);

		// Add an alarming red border if using configurable (i.e. dev)
		// app with a medic production server.
		if(settings.allowsConfiguration() &&
				settings.getAppUrl().contains("app.medicmobile.org")) {
			findViewById(R.id.lytWebView).setPadding(10, 10, 10, 10);
		}

		container = (XWalkView) findViewById(R.id.wbvMain);

		// Get the cookies from the intent, and inject them into the webview
		String cookies = getIntent().getStringExtra(EXTRA_COOKIES);

		// Only allow communication with the server if this view has not
		// been triggered from the StandardWebViewDataExtractionActivity
		if(cookies == null) {
			allowServerComms = true;
		} else {
			this.cookies = cookies;

			allowServerComms = false;

			fakeCouch = new FakeCouch(settings);
			fakeCouch.start(this);
		}

		if(DEBUG) enableWebviewLoggingAndGeolocation(container);
		enableRemoteChromeDebugging(container);
		enableJavascript(container);
		enableStorage(container);

		enableUrlHandlers(container);

		browseToRoot();

		if(settings.allowsConfiguration()) {
			toast(settings.getAppUrl());
		}
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
		switch(item.getItemId()) {
			case R.id.mnuSettings:
				openSettings();
				return true;
			case R.id.mnuHardRefresh:
				browseToRoot();
				return true;
			case R.id.mnuLogout:
				evaluateJavascript("angular.element(document.body).injector().get('AndroidApi').v1.logout()");
				return true;
			default:
				return super.onOptionsItemSelected(item);
		}
	}

	@Override public void onBackPressed() {
		if(container == null) {
			super.onBackPressed();
		} else {
			container.evaluateJavascript(
					"angular.element(document.body).injector().get('AndroidApi').v1.back()",
					backButtonHandler);
		}
	}

	@Override public void onDestroy() {
		super.onDestroy();
		if(fakeCouch != null) fakeCouch.stop();
	}

	public void evaluateJavascript(final String js) {
		container.post(new Runnable() {
			public void run() {
				// `WebView.loadUrl()` seems to be significantly faster than
				// `WebView.evaluateJavascript()` on Tecno Y4.  We may find
				// confusing behaviour on Android 4.4+ when using `loadUrl()`
				// to run JS, in which case we should switch to the second
				// block.
				// On switching to XWalkView, we assume the same applies.
				if(true) {
					container.load("javascript:" + js, null);
				} else {
					container.evaluateJavascript(js, IGNORE_RESULT);
				}
			}
		});
	}

//> MIGRATION-SPECIFIC METHODS
	void setCookies() {
		StringBuilder jsBuilder = new StringBuilder();
		for(String cookie : cookies.split(";")) {
			jsBuilder.append(String.format("document.cookie='%s'; ", cookie));
		}

		jsBuilder.append("window.location.reload()");

		evaluateJavascript(jsBuilder.toString());

		cookies = null;
	}

//> PRIVATE HELPERS
	private void openSettings() {
		Intent i = new Intent(this, SettingsDialogActivity.class);
		i.putExtra(SettingsDialogActivity.EXTRA_RETURN_TO, EmbeddedBrowserActivity.class);
		startActivity(i);
		finish();
	}

	private void browseToRoot() {
		String url = settings.getAppUrl() + (DISABLE_APP_URL_VALIDATION ?
				"" : "/medic/_design/medic/_rewrite/");
		if(DEBUG) log("Pointing browser to %s", url);
		container.load(url, null);
	}

	private void enableRemoteChromeDebugging(XWalkView container) {
		XWalkPreferences.setValue(XWalkPreferences.REMOTE_DEBUGGING, true);
	}

	private void enableWebviewLoggingAndGeolocation(XWalkView container) {
		new XWalkUIClient(container) {
			public boolean onConsoleMessage(ConsoleMessage cm) {
				Log.d("MedicMobile", String.format("%s:%s | %s",
						cm.sourceId(),
						cm.lineNumber(),
						cm.message()));
				return true;
			}

			/*
			 * TODO Crosswalk: re-enable this if required
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
			*/
		};
	}

	private void enableJavascript(XWalkView container) {
		container.getSettings().setJavaScriptEnabled(true);

		MedicAndroidJavascript maj = new MedicAndroidJavascript(this);
		maj.setAlert(new Alert(this));

		maj.setLocationManager((LocationManager) this.getSystemService(Context.LOCATION_SERVICE));

		container.addJavascriptInterface(maj, "medicmobile_android");
	}

	private void enableStorage(XWalkView container) {
		XWalkSettings settings = container.getSettings();

		// N.B. in Crosswalk, database seems to be enabled by default

		settings.setDomStorageEnabled(true);

		// N.B. in Crosswalk, appcache seems to work by default, and
		// there is no option to set the storage path.
	}

	private void enableUrlHandlers(XWalkView container) {
		container.setResourceClient(new XWalkResourceClient(container) {
			@Override public boolean shouldOverrideUrlLoading(XWalkView view, String url) {
				if(url.startsWith("tel:") || url.startsWith("sms:")) {
					Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
					view.getContext().startActivity(i);
					return true;
				}
				return false;
			}

			@Override public WebResourceResponse shouldInterceptLoadRequest(XWalkView view, String urlString) {
				trace("shouldInterceptLoadRequest", "%s", urlString);
				if(allowServerComms) return null;

				if(urlString == null) return null;

				Uri url = Uri.parse(urlString);

				String host = url.getHost();
				if(host == null) return null;

				String configuredHost = Uri.parse(settings.getAppUrl()).getHost();

				//trace("shouldInterceptRequest", "comparing hosts: %s <-> %s", host, configuredHost);

				// TODO safer just to block anything non-localhost?
				// No!  We need to be a bit cleverer than this in this instance -
				// We need to allow the user to:
				//   1. authenticate (although they should already have auth cookies)
				//   2. get the app cache
				//   3. get all the stuff in the app cache
				// I.E. all we should block is comms with couchdb, excepting auth messages!
				if(host.equals(configuredHost)) {
					trace("shouldInterceptLoadRequest", "Should we block %s?", url.getPath());
					if(url.getPath().startsWith("/medic/_design/medic/_rewrite/static/dist/")) {
						return null;
					}
					switch(url.getPath()) {
						case "/medic/_design/medic/_rewrite/":
							return null;
						case "/medic/login":
							if(cookies == null) {
								allowServerComms = true;
								return null;
							} else {
								setCookies();
								break;
							}
						case "/medic/_changes":
							// TODO trigger the replication
							evaluateJavascript(
									"var localDbName = 'medic-user-' + JSON.parse(unescape(decodeURI(" +
									"    document.cookie.split(';').map(function(e) {" +
									"      return e.trim();" +
									"    }).find(function(e) {" +
									"      return e.startsWith('userCtx=');" +
									"    }).split('=', 2)[1]))).name;" +
									"console.log('Replicating local db:', localDbName);" +
									"PouchDB.replicate('http://localhost:8000/medic', localDbName)" +
									"    .then(function() {" +
									"      console.log('Replication complete!  TODO now disable URL blocking and reload the page.');" +
									"    })" +
									"    .catch(function(err) {" +
									"      console.log('Error during replication', err);" +
									"    });");
							break;
					}

					// TODO don't let them talk to couch!
					trace("shouldInterceptLoadRequest", "looks like we should block %s", url);
					Map<String, String> headers = Collections.emptyMap();
					return new WebResourceResponse("text", "utf8", 503,
							"Server blocked.  Local db replication will begin shortly.",
							headers, emptyInputStream());
				}
				return null;
// TODO once loading has completed successfully (presumably we can detect this...somehow), then
// trigger replication from local HTTP server to local pouch.  For now, trigger this manually.
			}

			private InputStream emptyInputStream() {
				return new ByteArrayInputStream(new byte[0]);
			}
		});
	}

	private void toast(String message) {
		Toast.makeText(container.getContext(), message, Toast.LENGTH_LONG).show();
	}

	private void trace(String methodName, String message, Object...extras) {
		MedicLog.trace(this, methodName + "() :: " + message, extras);
	}

	private void log(String message, Object...extras) {
		MedicLog.trace(this, message, extras);
	}
}
