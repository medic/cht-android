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
import android.net.ConnectivityManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;

import android.os.StrictMode;
import android.util.Base64;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.webkit.ConsoleMessage;
import android.webkit.CookieManager;
import android.webkit.GeolocationPermissions;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.Toast;
import androidx.core.content.ContextCompat;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@SuppressWarnings({ "PMD.GodClass", "PMD.TooManyMethods" })
public class EmbeddedBrowserActivity extends Activity {
	Button download_button;
	Button upload_button;
	public String role = "Placeholder";
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
	private List<String> userData;


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
		download_button = findViewById(R.id.download_button);
		upload_button = findViewById(R.id.upload_button);

		download_button.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				String userName = getUserData().get(0);

				container.evaluateJavascript("console.log('"+ "username:"+ userName + "')", null);
				String script = "window.PouchDB('medic-user-"+ userName+"')" +
						".allDocs({include_docs: true, attachments: true})" +
						".then(result => medicmobile_android.saveDocs(JSON.stringify(result),'"+userName+"'));";
				container.evaluateJavascript(script, null);
			}
		});
		Log.d("Screen", getApplicationContext().toString());

		upload_button.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {
				Intent i2 = new Intent(Intent.ACTION_GET_CONTENT);
				i2.setType("*/*");
				i2.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
				startActivityForResult(i2, 106);

			}
		});
		// Add an alarming red border if using configurable (i.e. dev)
		// app with a medic production server.
		if (settings.allowsConfiguration() && appUrl != null && appUrl.contains("app.medicmobile.org")) {
			View webviewContainer = findViewById(R.id.lytWebView);
			webviewContainer.setPadding(10, 10, 10, 10);
			webviewContainer.setBackgroundResource(R.drawable.warning_background);
		}

		// Add a noticeable border to easily identify a training app
		if (BuildConfig.IS_TRAINING_APP) {
			View webviewContainer = findViewById(R.id.lytWebView);
			webviewContainer.setPadding(10, 10, 10, 10);
			webviewContainer.setBackgroundResource(R.drawable.training_background);
		}

		container = findViewById(R.id.wbvMain);
		getFragmentManager()
				.beginTransaction()
				.add(new OpenSettingsDialogFragment(container), OpenSettingsDialogFragment.class.getName())
				.commit();

		configureUserAgent();

		setUpUiClient(container);
		enableRemoteChromeDebugging();
		enableJavascript(container);
		enableStorage(container);

		enableUrlHandlers(container);

		Intent appLinkIntent = getIntent();
		Uri appLinkData = appLinkIntent.getData();
		browseTo(appLinkData);

		if (settings.allowsConfiguration()) {
			toast(redactUrl(appUrl));
		}

		registerRetryConnectionBroadcastReceiver();

		String recentNavigation = settings.getLastUrl();
		if (isValidNavigationUrl(appUrl, recentNavigation)) {
			container.loadUrl(recentNavigation);
		}
		container.setWebViewClient(new WebViewClient(){
			@Override
			public boolean shouldOverrideUrlLoading(WebView view, String url) {
				Log.d("URL used in the client.", url);
				if (!url.contains("login") ) {
					upload_button.setVisibility(View.VISIBLE);
					download_button.setVisibility(View.VISIBLE);
					role = getUserData().get(1);
					role = role.substring(1, role.length() - 1);
					boolean isUpdateRole = role != "Placeholder" && role.equals("chw_supervisor");
					boolean isDownloadRole = role != "Placeholder" && role.equals("chw");
					if (isDownloadRole){
						upload_button.setVisibility(View.GONE);
					}else if (isUpdateRole){
						download_button.setVisibility(View.GONE);
					}
				} else {
					upload_button.setVisibility(View.GONE);
					download_button.setVisibility(View.GONE);
				}
				return super.shouldOverrideUrlLoading(view, url);
			}
			@Override
			public void doUpdateVisitedHistory(WebView view, String url, boolean isReload){
				if (!Objects.isNull(url) && !url.contains("report") && !url.contains("login") && url.split("/").length > 3) {
					Log.d(String.valueOf((url.split("/").length)),"printing URL ...");
					upload_button.setVisibility(View.VISIBLE);
					download_button.setVisibility(View.VISIBLE);
					if (getUserData()!=null){
						role = getUserData().get(1);
						role = role.substring(1, role.length() - 1);
						boolean isUpdateRole = role != "Placeholder" && role.equals("chw_supervisor");
						boolean isDownloadRole = role != "Placeholder" && role.equals("chw");
						if (isDownloadRole){
							upload_button.setVisibility(View.GONE);
						}else if (isUpdateRole){
							download_button.setVisibility(View.GONE);
						}
					}
				} else {
					upload_button.setVisibility(View.GONE);
					download_button.setVisibility(View.GONE);
				}
				super.doUpdateVisitedHistory(view,url,isReload);
			}
			@Override
			public void onPageFinished(WebView view, String url) {
				//page has finished loading
			}
		});
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
			role = getUserData().get(0);
			Log.d("Role is ", role);

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

	@SuppressLint("Recycle")
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
				case PICK_FILE_REQUEST:
					if (resultCode == RESULT_OK && intent != null) {
						if(intent.getClipData() != null) {
							int count = intent.getClipData().getItemCount();
							int currentItem = 0;
							while(currentItem < count) {
								Uri data_uri = intent.getClipData().getItemAt(currentItem).getUri();
								uploadFile(data_uri, currentItem);
								currentItem = currentItem + 1;
							}
						} else{
							Uri data_uri = intent.getData();
							uploadFile(data_uri, 1);
						}
					}
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
				default:
					trace(this, "onActivityResult() :: no handling for requestCode=%s", requestCode.name());
			}
		} catch (Exception ex) {
			String action = intent == null ? null : intent.getAction();
			warn(ex, "Problem handling intent %s (%s) with requestCode=%s & resultCode=%s",
					intent, action, requestCode.name(), resultCode);
		}
	}
	private void uploadFile(Uri data_uri, int currentItem){
		String content;
		try {
			InputStream in = getContentResolver().openInputStream(data_uri);
			BufferedReader r = new BufferedReader(new InputStreamReader(in));
			StringBuilder total = new StringBuilder();
			for (String line; (line = r.readLine()) != null; ) {
				total.append(line);
			}
			if (Build.VERSION.SDK_INT > 9) {
				StrictMode.ThreadPolicy policy =
						new StrictMode.ThreadPolicy.Builder().permitAll().build();
				StrictMode.setThreadPolicy(policy);
			}
			content =total.toString().replaceAll("\"total_rows\".*\"rows\":","\"docs\":");
			String script = "new PouchDB('temp')" +
					".bulkDocs("+content+");";
			container.evaluateJavascript(script, null);
			String script_sync = "window.PouchDB('temp')" +
					".replicate.to('"+appUrl+"/medic').then(result =>" +
					"medicmobile_android.toastResult('Uploaded file number "+(currentItem+1)+" Successfully')).catch(err =>" +
					"medicmobile_android.toastResult(JSON.stringify(err)));";
			container.evaluateJavascript(script_sync, null);
		}catch (Exception e) {
			warn(e, "Could not open the specified file");
			toast("Could not open the specified file");
		}
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
	private void locationRequestResolved() {
		evaluateJavascript("window.CHTCore.AndroidApi.v1.locationPermissionRequestResolved();");
	}

	public List<String> getUserData(){
		List<String> userData = new ArrayList<>();
		try {
			String cookies = CookieManager.getInstance().getCookie(appUrl);
			if (Objects.isNull(cookies)){
				return null;
			}
			Log.d("updating user data", ": updated");

			if ( cookies != null && !cookies.isEmpty()){
				String encodedUserCtxCookie = Arrays.stream(cookies.split(";"))
						.map(field -> field.split("="))
						.filter(pair -> "userCtx".equals(pair[0].trim()))
						.map(pair -> pair[1].trim())
						.findAny()
						.get();
				String userCtxData = URLDecoder.decode(encodedUserCtxCookie, "utf-8")
						.replace("{", "")
						.replace("}", "");
				String userName = Arrays.stream(userCtxData.split(","))
						.map(field -> field.split(":"))
						.filter(pair -> "\"name\"".equals(pair[0].trim()))
						.map(pair -> pair[1].replace("\"", "").trim())
						.findAny()
						.get();
				userData.add(userName);
				role = (Arrays.stream(userCtxData.split(","))
						.map(field -> field.split(":"))
						.filter(pair -> "\"roles\"".equals(pair[0].trim()))
						.map(pair -> pair[1].replace("\"", "").trim())
						.findAny()
						.get());
				userData.add(role);
				return userData;
			}
		} catch (UnsupportedEncodingException e) {
			e.printStackTrace();
		}
		return null;
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
		registerReceiver(broadcastReceiver, new IntentFilter("retryConnection"));
	}

	//> ENUMS
	public enum RequestCode {
		ACCESS_LOCATION_PERMISSION(100),
		ACCESS_STORAGE_PERMISSION(101),
		ACCESS_SEND_SMS_PERMISSION(102),
		CHT_EXTERNAL_APP_ACTIVITY(103),
		GRAB_MRDT_PHOTO_ACTIVITY(104),
		FILE_PICKER_ACTIVITY(105),
		PICK_FILE_REQUEST(106);

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
