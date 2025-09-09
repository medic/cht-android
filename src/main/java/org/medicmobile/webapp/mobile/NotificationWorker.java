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
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.ProcessLifecycleOwner;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import org.json.JSONArray;
import org.json.JSONException;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class NotificationWorker extends Worker {
	static final String DEBUG_TAG = "NOTIFICATION_WORKER";
	public static final String NOTIFICATION_WORK_REQUEST_TAG = "cht_notification_tag";
	public static final String NOTIFICATION_WORK_NAME = "appNotifications";
	static final int EXECUTION_TIMEOUT_SECS = 10;
	static final int WORKER_REPEAT_INTERVAL_MINS = 15; //run background worker every 15 mins

	private final SettingsStore settings = SettingsStore.in(getApplicationContext());
	private final String appUrl = settings.getAppUrl();
	private WebView webView;
	private final String notificationsJS = "(async function (){" +
			" const api = window.CHTCore.AndroidApi;" +
			" const tasks = await api.v1.taskNotifications();" +
			" NotificationWorkerBridge.onGetNotificationResult(JSON.stringify(tasks), '" + appUrl + "');" +
			" })();";

	public NotificationWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
		super(context, workerParams);
	}

	@SuppressLint("SetJavaScriptEnabled")
	@NonNull
	@Override
	public Result doWork() {
		if (isAppInForeground()) {
			Log.d(DEBUG_TAG, "app in foreground, skipping execution");
			return Result.success();
		}

		Log.d(DEBUG_TAG, "app in background, creating webview");
		CountDownLatch latch = new CountDownLatch(1);
		Handler handler = new Handler(Looper.getMainLooper());

		handler.post(() -> {
			webView = new WebView(getApplicationContext());
			webView.getSettings().setJavaScriptEnabled(true);
			webView.addJavascriptInterface(new NotificationBridge(getApplicationContext(), latch), "NotificationWorkerBridge");
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

	public static class NotificationBridge {
		private final Context context;
		private final CountDownLatch latch;

		NotificationBridge(Context context, CountDownLatch latch) {
			this.context = context;
			this.latch = latch;
		}

		@JavascriptInterface
		public void onGetNotificationResult(String data, String appUrl) throws JSONException {
			AppNotificationManager appNotificationManager = AppNotificationManager.getInstance(context, appUrl);
			JSONArray dataArray = Utils.parseJSArrayData(data);
			appNotificationManager.showMultipleTaskNotifications(dataArray);
			latch.countDown();
		}
	}

	private void enableStorage(WebView container) {
		WebSettings webSettings = container.getSettings();
		webSettings.setDomStorageEnabled(true);
		webSettings.setDatabaseEnabled(true);
	}

	private boolean isAppInForeground() {
		return ProcessLifecycleOwner.get()
				.getLifecycle()
				.getCurrentState()
				.isAtLeast(Lifecycle.State.STARTED);
	}

	private void destroyWebView(WebView wv) {
		if (wv != null) {
			wv.stopLoading();
			wv.getSettings().setJavaScriptEnabled(false);
			wv.loadUrl("about:blank");
			wv.clearHistory();
			wv.clearCache(true);
			wv.removeAllViews();
			wv.destroy();
			wv = null;
		}
	}
}
