package org.medicmobile.webapp.mobile;

import static android.Manifest.permission.ACCESS_COARSE_LOCATION;
import static android.Manifest.permission.ACCESS_FINE_LOCATION;
import static android.content.pm.PackageManager.PERMISSION_GRANTED;
import static org.medicmobile.webapp.mobile.BuildConfig.DEBUG;
import static org.medicmobile.webapp.mobile.MedicLog.error;
import static org.medicmobile.webapp.mobile.MedicLog.log;
import static org.medicmobile.webapp.mobile.MedicLog.trace;
import static org.medicmobile.webapp.mobile.MedicLog.warn;
import static org.medicmobile.webapp.mobile.SimpleJsonClient2.redactUrl;
import static org.medicmobile.webapp.mobile.Utils.createUseragentFrom;
import static org.medicmobile.webapp.mobile.Utils.isValidNavigationUrl;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.ActivityManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.webkit.ConsoleMessage;
import android.webkit.GeolocationPermissions;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;

import java.util.Arrays;
import java.util.Optional;

@SuppressWarnings({ "PMD.GodClass", "PMD.TooManyMethods" })
public class EmbeddedBrowserActivity extends Activity {

	private WebView container;
	private SettingsStore settings;
	private String appUrl;
	private MrdtSupport mrdt;
	private FilePickerHandler filePickerHandler;
	private SmsSender smsSender;
	private ChtExternalAppHandler chtExternalAppHandler;
	private boolean isMigrationRunning = false;

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


//> ACTIVITY LIFECYCLE METHODS
	@SuppressLint("ClickableViewAccessibility")
	@Override public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		trace(this, "Starting webview...");

		this.filePickerHandler = new FilePickerHandler(this);
		this.mrdt = new MrdtSupport(this);
		this.chtExternalAppHandler = new ChtExternalAppHandler(this);

		try {
			this.smsSender = SmsSender.createInstance(this);
		} catch(Exception ex) {
			error(ex, "Failed to create SmsSender.");
		}

		this.settings = SettingsStore.in(this);
		this.appUrl = settings.getAppUrl();

		this.requestWindowFeature(Window.FEATURE_NO_TITLE);
		setContentView(R.layout.main);
		View webviewContainer = findViewById(R.id.lytWebView);
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
			ViewCompat.requestApplyInsets(webviewContainer.getRootView());
		}

		// Add an alarming red border if using configurable (i.e. dev)
		// app with a medic production server.
		if (settings.allowsConfiguration() && appUrl != null && appUrl.contains("app.medicmobile.org")) {
			webviewContainer.setPadding(10, 10, 10, 10);
			webviewContainer.setBackgroundResource(R.drawable.warning_background);
		}

		// Add a noticeable border to easily identify a training app
		if (BuildConfig.IS_TRAINING_APP) {
			webviewContainer.setPadding(10, 10, 10, 10);
			webviewContainer.setBackgroundResource(R.drawable.training_background);
		}

		container = findViewById(R.id.wbvMain);

		getFragmentManager()
			.beginTransaction()
			.add(new OpenSettingsDialogFragment(), OpenSettingsDialogFragment.class.getName())
			.commit();

		configureUserAgent();

		setUpUiClient(container);
		enableRemoteChromeDebugging();
		enableJavascript(container);
		enableStorage(container);

		enableUrlHandlers(container);

		if (settings.allowsConfiguration()) {
			toast(redactUrl(appUrl));
		}

		registerRetryConnectionBroadcastReceiver();

		initializeNotifications();

		String recentNavigation = settings.getLastUrl();
		Intent appLinkIntent = getIntent();
		Uri appLinkData = appLinkIntent.getData();
		if (appLinkData != null) {
			// The app has been opened via an app link.
			browseTo(appLinkData);
		} else if (isValidNavigationUrl(appUrl, recentNavigation)) {
			// The app has been opened normally, and the user can start where they left off.
			container.loadUrl(recentNavigation);
		} else {
			// The app has been opened normally, but no previous URL is available. (Maybe it is the first time.)
			browseTo(null);
		}
	}

	@Override
	protected void onNewIntent(Intent intent) {
		Uri appLinkData = intent.getData();
		browseTo(appLinkData);
	}

	@SuppressWarnings("PMD.CallSuperFirst")
	@Override
	protected void onStart() {
		trace(this, "onStart() :: Checking Crosswalk migration ...");
		XWalkMigration xWalkMigration = new XWalkMigration(this.getApplicationContext());
		if (xWalkMigration.hasToMigrate()) {
			log(this, "onStart() :: Running Crosswalk migration ...");
			isMigrationRunning = true;
			Intent intent = new Intent(this, UpgradingActivity.class)
				.putExtra("isClosable", false)
				.putExtra("backPressedMessage", getString(R.string.waitMigration));
			startActivity(intent);
			xWalkMigration.run();
		} else {
			trace(this, "onStart() :: Crosswalk installation not found - skipping migration");
		}
		trace(this, "onStart() :: Checking Crosswalk migration done.");

		if (BuildConfig.IS_TRAINING_APP) {
			toast(getString(R.string.usingTrainingApp));
		}

		super.onStart();
	}

	@Override
	protected void onStop() {
		String recentNavigation = container.getUrl();
		if (isValidNavigationUrl(appUrl, recentNavigation)) {
			try {
				settings.setLastUrl(recentNavigation);
			} catch (SettingsException e) {
				error(e, "Error recording last URL loaded");
			}
		}
		super.onStop();
	}

	@Override public void onBackPressed() {
		trace(this, "onBackPressed()");
		container.evaluateJavascript(
				"angular.element(document.body).injector().get('AndroidApi').v1.back()",
				backButtonHandler);
	}

	@Override
	protected void onActivityResult(int requestCd, int resultCode, Intent intent) {
		Optional<RequestCode> requestCodeOpt = RequestCode.valueOf(requestCd);

		if (!requestCodeOpt.isPresent()) {
			trace(this, "onActivityResult() :: no handling for requestCode=%s", requestCd);
			return;
		}

		RequestCode requestCode = requestCodeOpt.get();

		try {
			trace(this, "onActivityResult() :: requestCode=%s, resultCode=%s", requestCode.name(), resultCode);

			switch (requestCode) {
				case FILE_PICKER_ACTIVITY:
					this.filePickerHandler.processResult(resultCode, intent);
					return;
				case GRAB_MRDT_PHOTO_ACTIVITY:
					processMrdtResult(requestCode, intent);
					return;
				case CHT_EXTERNAL_APP_ACTIVITY:
					processChtExternalAppResult(resultCode, intent);
					return;
				case ACCESS_STORAGE_PERMISSION:
					processStoragePermissionResult(resultCode, intent);
					return;
				case ACCESS_LOCATION_PERMISSION:
					locationRequestResolved();
					return;
				case ACCESS_SEND_SMS_PERMISSION:
					this.smsSender.resumeProcess(resultCode);
					return;
				case ACCESS_P2P_PERMISSION:
					p2pPermissionResolved(resultCode);
					return;
				case P2P_QR_SCAN:
					processP2pQrScanResult(resultCode, intent);
					return;
				default:
					trace(this, "onActivityResult() :: no handling for requestCode=%s", requestCode.name());
			}
		} catch (Exception ex) {
			String action = intent == null ? null : intent.getAction();
			warn(ex, "Problem handling intent %s (%s) with requestCode=%s & resultCode=%s",
				intent, action, requestCode.name(), resultCode);
		}
	}


	@Override
	public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
		super.onRequestPermissionsResult(requestCode, permissions, grantResults);
		if (requestCode == AppNotificationManager.REQUEST_NOTIFICATION_PERMISSION && grantResults.length > 0 &&
				grantResults[0] == PackageManager.PERMISSION_GRANTED) {
			initializeNotifications();
		}
	}

	private void initializeNotifications() {
		AppNotificationManager appNotificationManager = new AppNotificationManager(this);
		appNotificationManager.cancelAllNotifications();
		if (!appNotificationManager.hasNotificationPermission()) {
			appNotificationManager.requestNotificationPermission(this);
			appNotificationManager.stopNotificationWorker();
			return;
		}
		appNotificationManager.startNotificationWorker();
	}

//> ACCESSORS
	MrdtSupport getMrdtSupport() {
		return this.mrdt;
	}

	SmsSender getSmsSender() {
		return this.smsSender;
	}

	ChtExternalAppHandler getChtExternalAppHandler() {
		return this.chtExternalAppHandler;
	}

//> PUBLIC API
	public void evaluateJavascript(final String js) {
		evaluateJavascript(js, true);
	}

	public void evaluateJavascript(final String js, final boolean useLoadUrl) {
		int maxUrlSize = 2097100; // Maximum character limit supported for loading as url.

		if (useLoadUrl && js.length() <= maxUrlSize) {
			// `WebView.loadUrl()` seems to be significantly faster than `WebView.evaluateJavascript()` on Tecno Y4.
			container.post(() -> container.loadUrl("javascript:" + js, null));
		} else {
			container.post(() -> container.evaluateJavascript(js, IGNORE_RESULT));
		}
	}

	public void errorToJsConsole(String message, Object... extras) {
		String formatted = String.format(message, extras);
		String escaped = formatted.replace("'", "\\'");
		evaluateJavascript("console.error('" + escaped + "');");
	}

	public boolean isMigrationRunning() {
		return isMigrationRunning;
	}

	public void setMigrationRunning(boolean migrationRunning) {
		isMigrationRunning = migrationRunning;
	}

	public boolean getLocationPermissions() {
		boolean hasFineLocation = ContextCompat.checkSelfPermission(this, ACCESS_FINE_LOCATION) == PERMISSION_GRANTED;
		boolean hasCoarseLocation = ContextCompat.checkSelfPermission(this, ACCESS_COARSE_LOCATION) == PERMISSION_GRANTED;

		if (hasFineLocation && hasCoarseLocation) {
			trace(this, "getLocationPermissions() :: Fine and Coarse location already granted");
			return true;
		}

		trace(this, "getLocationPermissions() :: Fine or Coarse location not granted before, requesting access...");
		startActivityForResult(
			new Intent(this, RequestLocationPermissionActivity.class),
			RequestCode.ACCESS_LOCATION_PERMISSION.getCode()
		);
		return false;
	}

//> PRIVATE HELPERS
	public boolean getP2pPermissions() {
		String[] perms = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
				? new String[]{ "android.permission.CAMERA", ACCESS_FINE_LOCATION, "android.permission.NEARBY_WIFI_DEVICES" }
				: new String[]{ "android.permission.CAMERA", ACCESS_FINE_LOCATION };

		boolean allGranted = true;
		for (String perm : perms) {
			if (ContextCompat.checkSelfPermission(this, perm) != PERMISSION_GRANTED) {
				allGranted = false;
				break;
			}
		}

		if (allGranted) {
			trace(this, "getP2pPermissions() :: All P2P permissions already granted");
			return true;
		}

		trace(this, "getP2pPermissions() :: P2P permissions not granted, requesting access...");
		startActivityForResult(
			new Intent(this, RequestP2pPermissionsActivity.class),
			RequestCode.ACCESS_P2P_PERMISSION.getCode()
		);
		return false;
	}

	private void p2pPermissionResolved(int resultCode) {
		String granted = resultCode == RESULT_OK ? "true" : "false";
		evaluateJavascript("window.CHTCore.AndroidApi.v1.p2pPermissionRequestResolved(" + granted + ");");
	}

	private void locationRequestResolved() {
		evaluateJavascript("window.CHTCore.AndroidApi.v1.locationPermissionRequestResolved();");
	}

	private void processChtExternalAppResult(int resultCode, Intent intentData) {
		String script = this.chtExternalAppHandler.processResult(resultCode, intentData);
		trace(this, "ChtExternalAppHandler :: Executing JavaScript: %s", script);
		evaluateJavascript(script);
	}

	private void processMrdtResult(RequestCode requestCode, Intent intent) {
		String js = mrdt.process(requestCode, intent);
		trace(this, "Executing JavaScript: %s", js);
		evaluateJavascript(js);
	}

	private void processStoragePermissionResult(int resultCode, Intent intent) {
		String triggerClass = intent == null ? null : intent.getStringExtra(RequestStoragePermissionActivity.TRIGGER_CLASS);

		if (FilePickerHandler.class.getName().equals(triggerClass)) {
			trace(this, "EmbeddedBrowserActivity :: Resuming FilePickerHandler process. Trigger:%s", triggerClass);
			this.filePickerHandler.resumeProcess(resultCode);
			return;
		}

		if (ChtExternalAppHandler.class.getName().equals(triggerClass)) {
			trace(this, "EmbeddedBrowserActivity :: Resuming ChtExternalAppHandler activity. Trigger:%s", triggerClass);
			this.chtExternalAppHandler.resumeActivity(resultCode);
			return;
		}

		trace(
			this,
			"EmbeddedBrowserActivity :: No handling for trigger: %s, requestCode: %s",
			triggerClass,
			RequestCode.ACCESS_STORAGE_PERMISSION.name()
		);
	}

	private void configureUserAgent() {
		String current = WebSettings.getDefaultUserAgent(this);
		container.getSettings().setUserAgentString(createUseragentFrom(current));
	}

	private void browseTo(Uri url) {
		String urlToLoad = this.settings.getUrlToLoad(url);
		trace(this, "Pointing browser to: %s", redactUrl(urlToLoad));
		container.loadUrl(urlToLoad, null);
	}

	private void enableRemoteChromeDebugging() {
		WebView.setWebContentsDebuggingEnabled(true);
	}

	private void setUpUiClient(WebView container) {
		container.setWebChromeClient(new WebChromeClient() {
			@Override public boolean onConsoleMessage(ConsoleMessage cm) {
				if (!DEBUG) {
					return super.onConsoleMessage(cm);
				}
				trace(this, "onConsoleMessage() :: %s:%s | %s", cm.sourceId(), cm.lineNumber(), cm.message());
				return true;
			}

			@Override public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> filePathCallback, FileChooserParams fileChooserParams) {
				filePickerHandler.openPicker(fileChooserParams, filePathCallback);
				return true;
			}

			@Override public void onGeolocationPermissionsShowPrompt(final String origin, final GeolocationPermissions.Callback callback) {
				callback.invoke(origin, true, true);
			}
		});
	}

	@SuppressLint("SetJavaScriptEnabled")
	private void enableJavascript(WebView container) {
		container.getSettings().setJavaScriptEnabled(true);

		MedicAndroidJavascript maj = new MedicAndroidJavascript(this);
		maj.setAlert(new Alert(this));

		maj.setActivityManager((ActivityManager) this.getSystemService(Context.ACTIVITY_SERVICE));

		maj.setConnectivityManager((ConnectivityManager) this.getSystemService(Context.CONNECTIVITY_SERVICE));

		// Initialize P2P bridge (singleton — survives activity recreation)
		org.medicmobile.webapp.mobile.p2p.P2pManager p2pManager =
				org.medicmobile.webapp.mobile.p2p.P2pManager.getInstance(this);
		org.medicmobile.webapp.mobile.p2p.TransitDocManager transitDocManager =
				new org.medicmobile.webapp.mobile.p2p.TransitDocManager();
		org.medicmobile.webapp.mobile.p2p.P2pTracker tracker =
				new org.medicmobile.webapp.mobile.p2p.P2pTracker();
		org.medicmobile.webapp.mobile.p2p.P2pBridgeMethods p2pBridge =
				new org.medicmobile.webapp.mobile.p2p.P2pBridgeMethods(p2pManager, transitDocManager, tracker);
		maj.setP2pBridge(p2pBridge);
		p2pBridge.setWebView(container);

		container.addJavascriptInterface(maj, "medicmobile_android");
	}

	private void enableStorage(WebView container) {
		WebSettings settings = container.getSettings();
		settings.setDomStorageEnabled(true);
		settings.setDatabaseEnabled(true);
	}

	private void enableUrlHandlers(WebView container) {
		container.setWebViewClient(new UrlHandler(this, settings));
	}

	private void toast(String message) {
		if (message != null) {
			Toast.makeText(container.getContext(), message, Toast.LENGTH_LONG).show();
		}
	}

	private void registerRetryConnectionBroadcastReceiver() {
		BroadcastReceiver broadcastReceiver = new BroadcastReceiver() {
			@Override public void onReceive(Context context, Intent intent) {
				String action = intent.getAction();
				if (action.equals("retryConnection")) {
					// user fixed the connection and asked the app
					// to retry the load from the connection error activity
					evaluateJavascript("window.location.reload()", false);
				}
			}
		};
		ContextCompat.registerReceiver(
			getApplicationContext(),
			broadcastReceiver,
			new IntentFilter("retryConnection"),
			ContextCompat.RECEIVER_NOT_EXPORTED
		);
	}

	/**
	 * Launch ZXing QR scanner for P2P peer connection.
	 * The scan result is returned via JavaScript callback.
	 */
	public void scanP2pQrCode() {
		trace(this, "scanP2pQrCode() :: launching ZXing scanner");
		com.journeyapps.barcodescanner.ScanOptions options =
				new com.journeyapps.barcodescanner.ScanOptions();
		options.setDesiredBarcodeFormats(com.journeyapps.barcodescanner.ScanOptions.QR_CODE);
		options.setPrompt("Scan the Supervisor's QR code");
		options.setCameraId(0);
		options.setBeepEnabled(true);
		options.setOrientationLocked(true);
		com.journeyapps.barcodescanner.ScanContract scanContract =
				new com.journeyapps.barcodescanner.ScanContract();
		Intent scanIntent = scanContract.createIntent(this, options);
		startActivityForResult(scanIntent, RequestCode.P2P_QR_SCAN.getCode());
	}

	private void processP2pQrScanResult(int resultCode, Intent intent) {
		if (resultCode != RESULT_OK || intent == null) {
			trace(this, "processP2pQrScanResult() :: scan cancelled or no result");
			evaluateJavascript("window.CHTCore.P2p.onQrScanResult(null);");
			return;
		}
		com.journeyapps.barcodescanner.ScanIntentResult result =
				com.journeyapps.barcodescanner.ScanIntentResult.parseActivityResult(resultCode, intent);
		String contents = result.getContents();
		if (contents == null || contents.isEmpty()) {
			evaluateJavascript("window.CHTCore.P2p.onQrScanResult(null);");
			return;
		}
		// Escape for JavaScript string
		String escaped = contents.replace("\\", "\\\\").replace("'", "\\'").replace("\n", "\\n");
		evaluateJavascript("window.CHTCore.P2p.onQrScanResult('" + escaped + "');");
	}

//> ENUMS
	public enum RequestCode {
		ACCESS_LOCATION_PERMISSION(100),
		ACCESS_STORAGE_PERMISSION(101),
		ACCESS_SEND_SMS_PERMISSION(102),
		CHT_EXTERNAL_APP_ACTIVITY(103),
		GRAB_MRDT_PHOTO_ACTIVITY(104),
		FILE_PICKER_ACTIVITY(105),
		ACCESS_P2P_PERMISSION(106),
		P2P_QR_SCAN(107);

		private final int requestCode;

		RequestCode(int requestCode) {
			this.requestCode = requestCode;
		}

		public static Optional<RequestCode> valueOf(int code) {
			return Arrays
				.stream(RequestCode.values())
				.filter(e -> e.getCode() == code)
				.findFirst();
		}

		public int getCode() {
			return requestCode;
		}
	}

}
