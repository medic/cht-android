package org.medicmobile.webapp.mobile;

import android.Manifest.permission;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.content.ContextCompat;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.Window;
import android.webkit.ConsoleMessage;
import android.webkit.CookieManager;
import android.webkit.ValueCallback;
import android.widget.Toast;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.json.JSONException;
import org.json.JSONObject;
import org.xwalk.core.XWalkCookieManager;
import org.xwalk.core.XWalkPreferences;
import org.xwalk.core.XWalkResourceClient;
import org.xwalk.core.XWalkSettings;
import org.xwalk.core.XWalkUIClient;
import org.xwalk.core.XWalkView;
import org.xwalk.core.XWalkWebResourceRequest;
import org.xwalk.core.XWalkWebResourceResponse;

import static android.content.pm.PackageManager.PERMISSION_GRANTED;
import static android.location.LocationManager.GPS_PROVIDER;
import static android.location.LocationManager.NETWORK_PROVIDER;
import static android.provider.MediaStore.ACTION_IMAGE_CAPTURE;
import static com.mvc.imagepicker.ImagePicker.getPickImageIntent;
import static java.lang.Boolean.parseBoolean;
import static org.medicmobile.webapp.mobile.BuildConfig.DEBUG;
import static org.medicmobile.webapp.mobile.BuildConfig.DISABLE_APP_URL_VALIDATION;
import static org.medicmobile.webapp.mobile.MedicLog.log;
import static org.medicmobile.webapp.mobile.MedicLog.trace;
import static org.medicmobile.webapp.mobile.MedicLog.warn;
import static org.medicmobile.webapp.mobile.SimpleJsonClient2.redactUrl;
import static org.medicmobile.webapp.mobile.Utils.intentHandlerAvailableFor;
import static org.medicmobile.webapp.mobile.Utils.urlDecode;
import static org.medicmobile.webapp.mobile.Utils.urlEncode;

@SuppressWarnings("PMD.GodClass")
public class EmbeddedBrowserActivity extends LockableActivity {
	/** Any activity result with all 3 low bits set is _not_ a simprints result. */
	private static final int NON_SIMPRINTS_FLAGS = 0x7;
	private static final int PROCESS_FILE = (0 << 3) | NON_SIMPRINTS_FLAGS;

	private static final long FIVE_MINS = 5 * 60 * 1000;
	private static final float ANY_DISTANCE = 0f;

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

	private AuthManager authManager;

	private XWalkView container;
	private SettingsStore settings;
	private SimprintsSupport simprints;

	private ValueCallback<Uri> uploadCallback;

	@Override public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		trace(this, "Starting XWalk webview...");

		this.simprints = new SimprintsSupport(this);

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
//		monster = container.getCookieManager();

		XWalkCookieManager monster = new XWalkCookieManager();
		monster.setAcceptCookie(true);
		authManager = new AuthManager(this, container, monster);

		enableLocationUpdates();
		if(DEBUG) enableWebviewLoggingAndGeolocation(container);
		enableRemoteChromeDebugging();
		enableJavascript(container);
		enableStorage(container);

		enableUrlHandlers(container);

		browseToRoot();

		if(settings.allowsConfiguration()) {
			toast(redactUrl(settings.getAppUrl()));
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
			case R.id.mnuGotoTestPages:
				evaluateJavascript("window.location.href = 'https://medic.github.io/atp'");
				return true;
			case R.id.mnuSetUnlockCode:
				changeCode();
				return true;
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

	@Override public boolean dispatchKeyEvent(KeyEvent event) {
		if(event.getKeyCode() == KeyEvent.KEYCODE_BACK) {
			// With standard android WebView, this would be handled by onBackPressed().  However, that
			// method does not get called when using XWalkView, so we catch the back button here instead.
			// TODO this causes issues with the Samsung long-back-press to trigger menu - the menu opens,
			// but the app also handles the back press :¬/
			if(event.getAction() == KeyEvent.ACTION_UP) {
				container.evaluateJavascript(
						"angular.element(document.body).injector().get('AndroidApi').v1.back()",
						backButtonHandler);
			}

			return true;
		} else {
			return super.dispatchKeyEvent(event);
		}
	}

	@Override protected void onActivityResult(int requestCode, int resultCode, Intent i) {
		trace(this, "onActivityResult() :: requestCode=%s, resultCode=%s", requestCode, resultCode);
		if((requestCode & NON_SIMPRINTS_FLAGS) == NON_SIMPRINTS_FLAGS) {
			switch(requestCode) {
				case PROCESS_FILE:
					if(uploadCallback != null) {
						uploadCallback.onReceiveValue(i == null || resultCode != RESULT_OK ? null : i.getData());
						uploadCallback = null;
					} else warn(this, "uploadCallback is null for requestCode %s", requestCode);
					return;
			}
		} else try {
			String js = simprints.process(requestCode, i);
			trace(this, "Execing JS: %s", js);
			evaluateJavascript(js);
		} catch(Exception ex) {
			warn(ex, "Unhandled intent %s (%s) with requestCode=%s & resultCode=%s", i, i == null ? null : i.getAction(), requestCode, resultCode);
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
				// On switching to XWalkView, we assume the same applies.
				if(true) { // NOPMD
					container.load("javascript:" + js, null);
				} else {
					container.evaluateJavascript(js, IGNORE_RESULT);
				}
			}
		});
	}

//> ACCOUNT SWITCHING
	AuthManager getAuthManager() { return this.authManager; }

//> PRIVATE HELPERS
	private void openSettings() {
		startActivity(new Intent(this,
				SettingsDialogActivity.class));
		finish();
	}

	private String getRootUrl() {
		return settings.getAppUrl() + (DISABLE_APP_URL_VALIDATION ?
				"" : "/medic/_design/medic/_rewrite/");
	}

	private void browseToRoot() {
		String url = getRootUrl();
		if(DEBUG) trace(this, "Pointing browser to %s", redactUrl(url));
		container.load(url, null);
	}

	private void enableRemoteChromeDebugging() {
		XWalkPreferences.setValue(XWalkPreferences.REMOTE_DEBUGGING, true);
	}

	private void enableWebviewLoggingAndGeolocation(XWalkView container) {
		container.setUIClient(new XWalkUIClient(container) {
			public boolean onConsoleMessage(ConsoleMessage cm) {
				trace(this, "onConsoleMessage() :: %s:%s | %s",
						cm.sourceId(),
						cm.lineNumber(),
						cm.message());
				return true;
			}

			@Override public void openFileChooser(XWalkView view, ValueCallback<Uri> callback, String acceptType, String shouldCapture) {
				trace(this, "openFileChooser() :: %s,%s,%s,%s", view, callback, acceptType, shouldCapture);

				uploadCallback = callback;

				boolean capture = parseBoolean(shouldCapture);

				if(acceptType.startsWith("image/") && capture && canStartCamera()) {
					takePhoto();
				} else if(acceptType.startsWith("image/") && !capture) {
					pickImage();
				} else {
					chooseFile(acceptType);
				}
			}

			/*
			 * TODO Crosswalk: re-enable this if required
			public void onGeolocationPermissionsShowPrompt(
					String origin,
					GeolocationPermissions.Callback callback) {
				// allow all location requests
				// TODO this should be restricted to the domain
				// set in Settings - issue #1603
				trace(this, "onGeolocationPermissionsShowPrompt() :: origin=%s, callback=%s",
						origin, callback);
				callback.invoke(origin, true, true);
			}
			*/
		});
	}

	@SuppressLint("SetJavaScriptEnabled")
	private void enableJavascript(XWalkView container) {
		container.getSettings().setJavaScriptEnabled(true);

		MedicAndroidJavascript maj = new MedicAndroidJavascript(this);
		maj.setAlert(new Alert(this));

		maj.setLocationManager((LocationManager) this.getSystemService(Context.LOCATION_SERVICE));

		container.addJavascriptInterface(maj, "medicmobile_android");
	}

	/**
	 * Make the app poll the location providers periodically.  This should mean that calls
	 * to MedicAndroidJavascript.getLocation() are reasonably up-to-date, and an initial
	 * location is likely to have been resolved by the time the user first fills a form and
	 * getLocation() is called.  However, longer-term we are likely to want a more explicit
	 * async getLocation() implementation which is triggered only when needed.
	 * @see https://github.com/medic/medic-projects/issues/2629
	 * @deprecated @see https://github.com/medic/medic-webapp/issues/3781
	 */
	@Deprecated
	private void enableLocationUpdates() {
		if(ContextCompat.checkSelfPermission(this, permission.ACCESS_FINE_LOCATION) != PERMISSION_GRANTED) {
			log("Cannot enable location updates: permission ACCESS_FINE_LOCATION not granted.");
			return;
		}

		LocationManager m = (LocationManager) this.getSystemService(Context.LOCATION_SERVICE);

		m.requestLocationUpdates(GPS_PROVIDER, FIVE_MINS, ANY_DISTANCE, new LocationListener() {
			public void onLocationChanged(Location location) {}
			public void onProviderDisabled(String provider) {}
			public void onProviderEnabled(String provider) {}
			public void onStatusChanged(String provider, int status, Bundle extras) {}
		});

		m.requestLocationUpdates(NETWORK_PROVIDER, FIVE_MINS, ANY_DISTANCE, new LocationListener() {
			public void onLocationChanged(Location location) {}
			public void onProviderDisabled(String provider) {}
			public void onProviderEnabled(String provider) {}
			public void onStatusChanged(String provider, int status, Bundle extras) {}
		});
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
			@Override public void onReceivedResponseHeaders(XWalkView view, XWalkWebResourceRequest request, XWalkWebResourceResponse response) {
				// TODO Auto-generated method stub
				super.onReceivedResponseHeaders(view, request, response);
				trace(this, "onReceivedResponseHeaders() :: request=%s, response=%s", request, response);
				if(response.getStatusCode() == 200) {
					Map<String, String> headers = response.getResponseHeaders();
					String cookies = headers.get("Set-Cookie");
					trace(this, "onReceivedResponseHeaders() :: request=%s, cookies=%s", request, cookies);
				}
			}

			@Override public boolean shouldOverrideUrlLoading(XWalkView view, String url) {
				if(url.startsWith("tel:") || url.startsWith("sms:")) {
					Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
					view.getContext().startActivity(i);
					return true;
				}
				return false;
			}
			@Override public void onLoadFinished(XWalkView view, String urlString) {
				Uri url = Uri.parse(urlString);
				trace(this, "onLoadFinished() :: url=%s, path=%s", urlString, url.getPath());
				authManager.storeCurrentCookies();
				//if("/_session".equals(url.getPath())) {
				//}
			}
			// According to XWalk source code, this method is only called when connection times out
			@Override public void onReceivedLoadError(XWalkView view, int errorCode, String description, String failingUrl) {
				if(errorCode == XWalkResourceClient.ERROR_OK) return;

				log("EmbeddedBrowserActivity.onReceivedLoadError() :: [%s] %s :: %s", errorCode, failingUrl, description);

				if(!getRootUrl().equals(failingUrl)) {
					log("EmbeddedBrowserActivity.onReceivedLoadError() :: ignoring for non-root URL");
				}

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
						"}", errorCode, description));
			}
		});
	}

	private void toast(String message) {
		Toast.makeText(container.getContext(), message, Toast.LENGTH_LONG).show();
	}

//> FILE & CAMERA HANDLERS
	private void takePhoto() {
		startActivityForResult(cameraIntent(), PROCESS_FILE);
	}

	private void pickImage() {
		startActivityForResult(getPickImageIntent(this, getString(R.string.promptChooseImage)), PROCESS_FILE);
	}

	private void chooseFile(String acceptType) {
		Intent i = new Intent(Intent.ACTION_GET_CONTENT);
		i.addCategory(Intent.CATEGORY_OPENABLE);
		i.setType(acceptType);
		startActivityForResult(Intent.createChooser(i, getString(R.string.promptChooseFile)), PROCESS_FILE);
	}

	private boolean canStartCamera() {
		return intentHandlerAvailableFor(this, cameraIntent());
	}

	private static Intent cameraIntent() {
		return new Intent(ACTION_IMAGE_CAPTURE);
	}
}

class AuthManager {
	/** account name -> auth cookies */
	private final Map<String, Map<String, String>> cookies;
	/** maintain a set of empty values used when clearing cookies */
	private final Map<String, String> noCookies = new HashMap<>(); // TODO remove
	private final EmbeddedBrowserActivity parent;
	private final XWalkView container;
	private final XWalkCookieManager monster;

	private String currentAccount;

	AuthManager(EmbeddedBrowserActivity parent, XWalkView container, XWalkCookieManager monster) {
		this.parent = parent;
		this.container = container;
		this.monster = monster;
		cookies = new TreeMap<>();
	}

	Collection<String> getAuthedAccountNames() {
		List<String> names = new LinkedList<>();
		for(String name : cookies.keySet()) {
			Map<String, String> c = cookies.get(name);
			if(c == null) continue;

			String authSessionCookie = c.get("AuthSession");
			if(authSessionCookie == null) continue;
			if(authSessionCookie.length() == 0) continue;

			names.add(name);
		}
		return names;
	}

	boolean switchTo(String account) {
		trace(this, "switchTo() :: requested change to account: %s", account);
		Map<String, String> accountCookies = cookies.get(account);
		if(accountCookies == null) {
			trace(this, "switchTo() :: account '%s' not found", account);
			return false;
		} else {
			trace(this, "switchTo() :: setting cookies for account '%s' to: %s", account, accountCookies);
			setCookies(accountCookies);
			
			container.post(new Runnable() {
				public void run() {
					try {
						String currentUrl = container.getUrl();
						trace(this, "switchTo() :: url=%s", currentUrl);
						if(currentUrl.endsWith("/medic/login")) {
							trace(this, "switchTo() :: looks like the login page - redirecting to :", currentUrl);
							parent.evaluateJavascript("window.location = '/medic/_design/medic/_rewrite/'");
						} else {
							parent.evaluateJavascript("window.location.reload()");
						}
					} catch(Exception ex) {
						warn(ex, "switchTo()");
					}
				}
			});
			reloadPage();
			return true;
		}
	}

	void loginNew() {
//		setCookies(noCookies);
		try {
			container.post(new Runnable() {
				public void run() {
					try {
						monster.removeAllCookie();
						parent.evaluateJavascript("window.location.href = '/medic/login'");
					} catch(Exception ex) {
						warn(ex, "loginNew()");
					}
				}
			});
		} catch(Exception ex) {
			warn(ex, "reloadPage()");
		}
	}

	private void reloadPage() {
		try {
			container.post(new Runnable() {
				public void run() {
					try {
						parent.evaluateJavascript("window.location.reload()");
					} catch(Exception ex) {
						warn(ex, "reloadPage()");
					}
				}
			});
		} catch(Exception ex) {
			warn(ex, "reloadPage()");
		}
	}

	void storeCurrentCookies() {
		trace(this, "storeCurrentCookies() :: ENTRY");
		try {
			monster.flushCookieStore();
			container.post(new Runnable() {
				public void run() {
					container.evaluateJavascript("(function() { return document.cookie; }())", new ValueCallback<String>() {
						public void onReceiveValue(String result) {
							try {
								{
									result = result.replaceAll("^\"", "");
									result = result.replaceAll("\"$", "");

									trace(this, "storeCurrentCookies() :: result=%s", result);
									final Map<String, String> cookies = new HashMap<>();
									add(cookies, result);
									String currentUrl = container.getUrl();
									trace(this, "storeCurrentCookies() :: currentUrl=%s", currentUrl);
									String monsterCookies = monster.getCookie(currentUrl);
									trace(this, "storeCurrentCookies() :: monsterCookies=%s", monsterCookies);
									add(cookies, monsterCookies);
									currentAccount = getAccountName(cookies);
									if(currentAccount != null) AuthManager.this.cookies.put(currentAccount, cookies);
								}


								try {
									CookieManager cm = CookieManager.getInstance();

									java.lang.reflect.Field ccm = cm.getClass().getDeclaredField("mChromeCookieManager");
									ccm.setAccessible(true);
									Object acm = ccm.get(cm);

									java.lang.reflect.Method getCookie = acm.getClass().getDeclaredMethod("nativeGetCookie", String.class);
									getCookie.setAccessible(true);

									Object cookie = getCookie.invoke(acm, container.getUrl());

									trace(this, "Cookie: '%s'", cookie);
								} catch(Exception ex) {
									MedicLog.warn(ex, "getCookie");
								}
							} catch(Exception ex) {
								warn(ex, "storeCurrentCookies()");
							}
						}
					});
				}
			});
		} catch(Exception ex) {
			warn(ex, "storeCurrentCookies()");
		}
	}

	private void add(Map<String, String> store, String s) {
		if(s == null || s.length()==0) return;
		for(String cookie : s.split(";")) {
			String[] cookieParts = cookie.split("=", 2);

			if(cookieParts.length < 2) continue;

			String cookieName = cookieParts[0].trim();
			String cookieValue = urlDecode(cookieParts[1].trim());

			trace(this, "add() :: Adding cookie %s=%s", cookieName, cookieValue);
			store.put(cookieName, cookieValue);
			noCookies.put(cookieName, "");
		}
	}

	private String getAccountName(Map<String, String> cookies) {
		try {
			String jsonString = cookies.get("userCtx");
			trace(this, "getAccountName() :: jsonString=%s", jsonString);

			if(jsonString == null || jsonString.length() == 0) return null;

			JSONObject json = new JSONObject(jsonString);
			trace(this, "getAccountName() :: json=%s", json);

			String name = json.optString("name");
			trace(this, "getAccountName() :: name=%s", name);

			return name;
		} catch(JSONException ex) {
			warn(ex, "getAccountName()");
			return null;
		}
	}

	/** TODO should probably whitelist expected cookies rather than the opposite */
	private String flagsFor(String cookieName) {
		if("AuthSession".equals(cookieName)) {
			return "; HttpOnly"; // TODO add "; Secure; SameSite: Strict"
		} else return "";
	}

	private void setCookies(final Map<String, String> cookies) {
		if(true) {
			container.post(new Runnable() {
				public void run() {
					monster.removeAllCookie();
					String url = container.getUrl();
					for(String cookieName : cookies.keySet()) {
						String rawValue = cookies.get(cookieName);
						String cookieValue = urlEncode(rawValue);
						String cookieString = String.format("%s=%s%s", cookieName, rawValue, flagsFor(cookieName));
						trace("setCookies", "setting cookie: %s", cookieString);
						monster.setCookie(url, cookieString);
					}
				}
			});
		}

		if(false) {
			try {
				trace(this, "setCookies() :: setting cookies to: %s", cookies);

				StringBuilder bob = new StringBuilder();
				for(String cookieName : cookies.keySet()) {
					String rawValue = cookies.get(cookieName);
					trace("setCookies", "setting cookie: %s=%s", cookieName, rawValue);

					String cookieValue = urlEncode(rawValue);
					bob.append(';');
					bob.append(' ');
					bob.append(cookieName);
					bob.append('=');
					bob.append(cookieValue);
				}
				final String cookieString = bob.length() > 0 ? bob.substring(2) : "";

				container.post(new Runnable() {
					public void run() {
						String url = container.getUrl();
						trace(this, "setCookies() :: setting cookies for %s to: %s", url, cookieString);
						monster.setCookie(url, cookieString);
					}
				});
			} catch(Exception ex) {
				warn(ex, "setCookies()");
			}
		}
	}
}

class JsBuilder {
	private final StringBuilder bob = new StringBuilder();

	private boolean finalised;

	public JsBuilder() {}

	public JsBuilder(String command, Object... args) {
		append(command, args);
	}

	void append(String command, Object... args) {
		if(finalised) throw new RuntimeException("Cannot execute more JS commands after a page change.");

		bob.append(String.format(command, args) + ";\n");
	}

	void logVar(String varName) {
		append("console.log('%s = ' + %s)", varName, varName);
	}

	void log(String text, Object... args) {
		append("console.log('" + text + "')", args);
	}

	void to(String url) {
		append("window.location = '%s'", url);
		finalised = true;
	}

	void reloadPage() {
		append("window.location.reload()");
		finalised = true;
	}

	public String toString() {
		MedicLog.trace(this, "toString() :: %s", bob);
		return bob.toString();
	}
}
