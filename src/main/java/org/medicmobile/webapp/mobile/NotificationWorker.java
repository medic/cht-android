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
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import org.json.JSONException;

import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class NotificationWorker extends Worker {
	static final String DEBUG_TAG = "NOTIFICATION_WORKER";
	public static final String NOTIFICATION_WORK_REQUEST_TAG = "cht_notification_tag";
	public static final String NOTIFICATION_WORK_NAME = "appNotifications";
	static final int EXECUTION_TIMEOUT_SECS = 10;
	static final int WORKER_REPEAT_INTERVAL_MINS = 15; //run background worker every 15 mins
	static final int INITIAL_EXECUTION_DELAY_MINS = 2;

	private final SettingsStore settings = SettingsStore.in(getApplicationContext());
	private final String appUrl = settings.getAppUrl();
	private CountDownLatch latch;
	private WebView webView;
	String notificationsJS =
					"(async function (){ " +
					"let result = null; " +
					"const api = window.CHTCore.AndroidApi; " +
					"if(typeof api.v1.taskNotifications === 'function'){ " +
					"result = await api.v1.taskNotifications(); " +
					"} " +
					"NotificationWorkerBridge.onGetNotificationResult(JSON.stringify(result)); " +
					"})(); ";

	public NotificationWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
		super(context, workerParams);
	}

	@Override
	public void onStopped() {
		super.onStopped();
		if (latch != null) {
			latch.countDown();
		}
	}

	@SuppressLint("SetJavaScriptEnabled")
	@NonNull
	@Override
	public Result doWork() {
		Log.d(DEBUG_TAG, "background worker running......");
		latch = new CountDownLatch(1);
		Handler handler = new Handler(Looper.getMainLooper());
		handler.post(() -> {
			webView = new WebView(getApplicationContext());
			webView.getSettings().setJavaScriptEnabled(true);
			Object notificationInterface = new Object() {
				@JavascriptInterface
				public void onGetNotificationResult(String data) throws JSONException {
					if (!Objects.equals(data, "null")) {
						AppNotificationManager appNotificationManager = new AppNotificationManager(getApplicationContext());
						appNotificationManager.showNotificationsFromJsArray(data);
					}
					latch.countDown();
				}
			};
			webView.addJavascriptInterface(notificationInterface, "NotificationWorkerBridge");
			enableStorage(webView);
			webView.setWebViewClient(new WebViewClient() {
				@Override
				public void onPageFinished(WebView view, String url) {
					view.evaluateJavascript(notificationsJS, null);
				}
			});
			webView.loadUrl(appUrl);
		});

		try {
			boolean completed = latch.await(EXECUTION_TIMEOUT_SECS, TimeUnit.SECONDS);
			if (completed) {
				Log.d(DEBUG_TAG, "notification worker ran successfully!");
				return Result.success();
			} else {
				Log.d(DEBUG_TAG, "notification worker taking too long to complete");
				return Result.failure();
			}
		} catch (InterruptedException e) {
			log(e, "error: notification worker interrupted");
			Thread.currentThread().interrupt();
			return Result.failure();
		} finally {
			handler.post(() -> {
				destroyWebView(webView);
			});
		}
	}

	private void enableStorage(WebView container) {
		WebSettings webSettings = container.getSettings();
		webSettings.setDomStorageEnabled(true);
		webSettings.setDatabaseEnabled(true);
	}

	private void destroyWebView(WebView wv) {
		if (wv != null) {
			wv.stopLoading();
			wv.destroy();
			Log.d(DEBUG_TAG, "clean up successful");
		}
	}
}
