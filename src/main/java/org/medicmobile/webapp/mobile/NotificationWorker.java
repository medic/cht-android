package org.medicmobile.webapp.mobile;

import static org.medicmobile.webapp.mobile.MedicLog.log;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.annotation.NonNull;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class NotificationWorker extends Worker {
	static final String TAG = "NOTIFICATION_WORKER";
	static final int EXECUTION_TIMEOUT = 20;
	static final int WORKER_REPEAT_INTERVAL_MINS = 15; //run background worker every 15 mins
	private final SettingsStore settings = SettingsStore.in(getApplicationContext());
	private final String appUrl = settings.getAppUrl();
	private static boolean hasCheckedForNotificationApi = false;
	private static final String NOTIFICATION_WORK_REQUEST_TAG = "cht_notification_tag";

	public NotificationWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
		super(context, workerParams);
	}

	@SuppressLint("SetJavaScriptEnabled")
	@NonNull
	@Override
	public Result doWork() {
		CountDownLatch latch = new CountDownLatch(1);
		new Handler(Looper.getMainLooper()).post(() -> {
			WebView webView = new WebView(getApplicationContext());
			webView.getSettings().setJavaScriptEnabled(true);
			webView.addJavascriptInterface(new NotificationBridge(getApplicationContext(), latch, appUrl), "CHTNotificationBridge");
			enableStorage(webView);

			webView.setWebViewClient(new WebViewClient() {
				@Override
				public void onPageFinished(WebView view, String url) {
					String js = "(async function (){" +
							" const api = window.CHTCore.AndroidApi;" +
							" const tasks = await api.v1.taskNotifications();" +
							" CHTNotificationBridge.onJsResult(JSON.stringify(tasks));" +
							"})();";
					view.evaluateJavascript(js, null);
				}
			});
			webView.loadUrl(appUrl);
		});
		try {
			boolean completed = latch.await(EXECUTION_TIMEOUT, TimeUnit.SECONDS);
			if (completed) {
				Log.d(TAG, "notification worker ran successfully!");
			} else {
				Log.d(TAG, "notification worker taking too long to complete");
				return Result.failure();
			}
		} catch (InterruptedException e) {
			log(e, "error: notification worker interrupted");
			Thread.currentThread().interrupt();
			return Result.failure();
		}

		return Result.success();
	}

	static void initNotificationWorker(Context context, WebView webView, AppNotificationManager appNotificationManager) {
		webView.setWebViewClient(new WebViewClient() {
			@Override
			public void onPageFinished(WebView view, String url) {
				String jsCheckApi = "(() => typeof window.CHTCore.AndroidApi.v1.taskNotifications === 'function')();";
				webView.evaluateJavascript(jsCheckApi, new ValueCallback<String>() {
					@Override
					public void onReceiveValue(String hasApi) {
						if (!hasCheckedForNotificationApi && !Objects.equals(hasApi, "null")) {
							hasCheckedForNotificationApi = true;
							if (Objects.equals(hasApi, "true") && appNotificationManager.hasNotificationPermission()) {
								startNotificationWorker(context);
							} else {
								//missing notification permissions
								WorkManager.getInstance(context).cancelAllWorkByTag(NOTIFICATION_WORK_REQUEST_TAG);
								log(this, "initNotificationWorker() :: stopped notification worker manager");
							}
						}
					}
				});
			}
		});
	}

	private static void startNotificationWorker(Context context) {
		PeriodicWorkRequest request = new PeriodicWorkRequest.Builder(
				NotificationWorker.class,
				WORKER_REPEAT_INTERVAL_MINS, TimeUnit.MINUTES
		).addTag(NOTIFICATION_WORK_REQUEST_TAG).build();

		WorkManager.getInstance(context).enqueueUniquePeriodicWork(
				"task_notification",
				ExistingPeriodicWorkPolicy.KEEP,
				request
		);
		log(context, "startNotificationWorker() :: Started Notification Worker Manager...");
	}

	private void enableStorage(WebView container) {
		WebSettings webSettings = container.getSettings();
		webSettings.setDomStorageEnabled(true);
		webSettings.setDatabaseEnabled(true);
	}

	public static class NotificationBridge {
		private final Context context;
		private final CountDownLatch latch;
		private final String appUrl;

		NotificationBridge(Context context, CountDownLatch latch, String appUrl) {
			this.context = context;
			this.latch = latch;
			this.appUrl = appUrl;
		}

		@JavascriptInterface
		public void onJsResult(String data) throws JSONException {
			AppNotificationManager appNotificationManager = new AppNotificationManager(context.getApplicationContext());
			JSONArray dataArray = parseData(data);
			for (int i = 0; i < dataArray.length(); i++) {
				JSONObject task = dataArray.getJSONObject(i);
				String contentText = task.getString("contentText");
				String title = task.getString("title");
				long readyAt = task.getLong("readyAt");
				int notificationId = (int) (readyAt % Integer.MAX_VALUE);
				appNotificationManager.showNotification(appUrl, notificationId + i, title, contentText);
			}
			latch.countDown();
		}

		private JSONArray parseData(String data) {
			data = data.replace("^\"|\"$", "")
					.replace("\\\"", "\"");
			try {
				return new JSONArray(data);
			} catch (JSONException e) {
				log(e, "error parsing JS data");
				return new JSONArray();
			}
		}
	}

}
