package org.medicmobile.webapp.mobile;

import static org.medicmobile.webapp.mobile.ConnectionUtils.connectionErrorToString;
import static org.medicmobile.webapp.mobile.ConnectionUtils.isConnectionError;
import static org.medicmobile.webapp.mobile.MedicLog.log;
import static org.medicmobile.webapp.mobile.MedicLog.trace;
import static org.medicmobile.webapp.mobile.Utils.isUrlRelated;
import static org.medicmobile.webapp.mobile.Utils.restartApp;

import android.annotation.TargetApi;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.webkit.CookieManager;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;

public class UrlHandler extends WebViewClient {
	EmbeddedBrowserActivity parentActivity;
	SettingsStore settings;

	public UrlHandler(EmbeddedBrowserActivity parentActivity, SettingsStore settings) {
		this.parentActivity = parentActivity;
		this.settings = settings;
	}

	@Override
	public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
		Uri uri = request.getUrl();
		if (isUrlRelated(settings.getAppUrl(), uri)) {
			// Load all related URLs in the webview
			return false;
		}

		// Let Android decide what to do with unrelated URLs
		// unrelated URLs include `tel:` and `sms:` uri schemes
		Intent i = new Intent(Intent.ACTION_VIEW, uri);
		view.getContext().startActivity(i);
		return true;
	}

	@Override
	public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
		logError(errorCode, description, failingUrl);

		if (!UrlUtils.isRootUrl(settings, failingUrl)) {
			super.onReceivedError(view, errorCode, description, failingUrl);
			return;
		}

		processError(view, errorCode, description);
	}

	@TargetApi(23)
	@Override
	public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
		String failingUrl = request.getUrl().toString();
		String description = String.valueOf(error.getDescription());
		int code = error.getErrorCode();

		logError(code, description, failingUrl);

		if (!UrlUtils.isRootUrl(settings, failingUrl)) {
			super.onReceivedError(view, request, error);
			return;
		}

		processError(view, code, description);
	}

	private void logError(int errorCode, String errorDescription, String failingUrl) {
		log(this, "onReceivedLoadError() :: url: %s, error code: %s, description: %s",
			failingUrl, errorCode, errorDescription);
	}

	private void processError(WebView view, int errorCode, String errorDescription) {
		if (isConnectionError(errorCode)) {
			String connErrorInfo = connectionErrorToString(errorCode, errorDescription);
			Intent intent = new Intent(view.getContext(), ConnectionErrorActivity.class);
			intent.putExtra("connErrorInfo", connErrorInfo);
			if (parentActivity.isMigrationRunning()) {
				// Activity is not closable if the migration is running
				intent
					.putExtra("isClosable", false)
					.putExtra("backPressedMessage", parentActivity.getString(R.string.waitMigration));
			}
			parentActivity.startActivity(intent);
			return;
		}

		parentActivity.evaluateJavascript(String.format(
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
				"}", errorCode, errorDescription), false);
	}

	/**
	 *  Check how the migration process is going if it was started.
	 *  Because most of the cases after the XWalk -> Webview migration process ends
	 * 	the cookies are not available for unknowns reasons, making the webapp to
	 * 	redirect the user to the login page instead of the main page.
	 * 	If these conditions are met: migration running + /login page + no cookies,
	 * 	the app is restarted to refresh the Webview and prevent the user to
	 * 	login again.
	 */
	@Override public void onPageStarted(WebView view, String url, Bitmap favicon) {
		boolean isMigrationRunning = parentActivity.isMigrationRunning();
		trace(this, "onPageStarted() :: url: %s, isMigrationRunning: %s", url, isMigrationRunning);

		if (isMigrationRunning && url.contains("/login")) {
			parentActivity.setMigrationRunning(false);
			CookieManager cookieManager = CookieManager.getInstance();
			String cookie = cookieManager.getCookie(settings.getAppUrl());
			if (cookie == null) {
				log(this, "onPageStarted() :: Migration process in progress, and " +
					"cookies were not loaded, restarting ...");
				restartApp(view.getContext());
			}
			trace(this, "onPageStarted() :: Cookies loaded, skipping restart");
		}
	}

	@Override public void onPageFinished(WebView view, String url) {
		trace(this, "onPageFinished() :: url: %s", url);
		// Broadcast the event so if the connection error
		// activity is listening it will close
		parentActivity.sendBroadcast(new Intent("onPageFinished"));
	}
}
