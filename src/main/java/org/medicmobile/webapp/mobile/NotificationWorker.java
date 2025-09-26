package org.medicmobile.webapp.mobile;

import static org.medicmobile.webapp.mobile.MedicLog.log;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.annotation.NonNull;
import androidx.concurrent.futures.CallbackToFutureAdapter;
import androidx.work.ListenableWorker;
import androidx.work.WorkerParameters;

import com.google.common.util.concurrent.ListenableFuture;

public class NotificationWorker extends ListenableWorker {
	static final String DEBUG_TAG = "NOTIFICATION_WORKER";
	public static final String NOTIFICATION_WORK_REQUEST_TAG = "cht_notification_tag";
	public static final String NOTIFICATION_WORK_NAME = "appNotifications";
	static final int WORKER_REPEAT_INTERVAL_MINS = 15; //run background worker every 15 mins
	static final int INITIAL_EXECUTION_DELAY_MINS = 2;

	private final SettingsStore settings = SettingsStore.in(getApplicationContext());
	private final String appUrl = settings.getAppUrl();
	private WebView webView;
	private final Handler handler = new Handler(Looper.getMainLooper());
	String notificationsJS = "(async () => { " +
			"const tasks = await window?.CHTCore?.AndroidApi?.v1?.taskNotifications(); " +
			"NotificationWorkerInterface.onGetNotificationResult(JSON.stringify(tasks ?? [])); " +
			"})(); ";

	public NotificationWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
		super(context, workerParams);
	}

	@Override
	public void onStopped() {
		super.onStopped();
		destroyWebView(webView);
	}

	@SuppressLint("SetJavaScriptEnabled")
	@NonNull
	@Override
	public ListenableFuture<Result> startWork() {
		Log.d(DEBUG_TAG, "background worker running......");
		return CallbackToFutureAdapter.getFuture(completer -> {
			handler.post(() -> {
				webView = new WebView(getApplicationContext());
				webView.getSettings().setJavaScriptEnabled(true);
				Object notificationInterface = new Object() {
					@JavascriptInterface
					public void onGetNotificationResult(String data) {
						try {
							AppNotificationManager appNotificationManager = new AppNotificationManager(getApplicationContext());
							appNotificationManager.showNotificationsFromJsArray(data);
							Log.d(DEBUG_TAG, "notification worker ran successfully!");
							completer.set(Result.success());
						} catch (Exception e) {
							log(e, "error checking for notifications");
							completer.set(Result.failure());
						} finally {
							destroyWebView(webView);
						}
					}
				};
				webView.addJavascriptInterface(notificationInterface, "NotificationWorkerInterface");
				enableStorage(webView);
				webView.setWebViewClient(new WebViewClient() {
					boolean triggered = false;
					@Override
					public void onPageFinished(WebView view, String url) {
						if (triggered || (appUrl + "/").equals(url)) {
							return;
						}
						triggered = true;
						view.evaluateJavascript(notificationsJS, null);
					}
				});
				webView.loadUrl(appUrl);
			});
			return "Async notification Task";
		});
	}

	private void enableStorage(WebView wv) {
		WebSettings webSettings = wv.getSettings();
		webSettings.setDomStorageEnabled(true);
		webSettings.setDatabaseEnabled(true);
	}

	private void destroyWebView(WebView wv) {
		if (wv != null) {
			handler.post(() -> {
				wv.stopLoading();
				wv.destroy();
				Log.d(DEBUG_TAG, "clean up successful");
			});
		}
	}
}
