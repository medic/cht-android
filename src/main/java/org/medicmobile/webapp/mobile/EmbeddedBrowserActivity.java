package org.medicmobile.webapp.mobile;

import android.app.*;
import android.content.*;
import android.net.*;
import android.os.*;
import android.util.Log;
import android.view.*;
import android.webkit.*;

import java.io.File;

public class EmbeddedBrowserActivity extends Activity {
	private static final boolean DEBUG = BuildConfig.DEBUG;

	private WebView container;
	private SettingsStore settings;

	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		this.settings = SettingsStore.in(this);

		this.requestWindowFeature(Window.FEATURE_NO_TITLE);
		setContentView(R.layout.main);

		container = (WebView) findViewById(R.id.WebViewContainer);

		if(DEBUG) enableWebviewLogging(container);
		enableJavascript(container);
		enableStorage(container);

		enableSmsAndCallHandling(container);

		String url = settings.getAppUrl() + "/login";
		if(DEBUG) log("Pointing browser to %s", url);
		container.loadUrl(url);
	}

	public boolean onCreateOptionsMenu(Menu menu) {
		if(settings.allowsConfiguration()) {
			getMenuInflater().inflate(R.menu.web_menu, menu);
		}
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

	public void onBackPressed() {
		if(container != null && container.canGoBack()) {
			container.goBack();
		} else super.onBackPressed();
	}

	private void openSettings() {
		startActivity(new Intent(this,
				SettingsDialogActivity.class));
		finish();
	}

	private void enableWebviewLogging(WebView container) {
		container.setWebChromeClient(new WebChromeClient() {
			public boolean onConsoleMessage(ConsoleMessage cm) {
				Log.d("Medic Mobile", cm.message() + " -- From line "
						+ cm.lineNumber() + " of "
						+ cm.sourceId());
				return true;
			}
		});
	}

	private void enableJavascript(WebView container) {
		container.getSettings().setJavaScriptEnabled(true);
		container.addJavascriptInterface(
				new MedicAndroidJavascript(),
				"medicmobile_android"
		);
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
