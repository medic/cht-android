package org.medicmobile.webapp.mobile;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.webkit.WebView;
import android.webkit.WebViewClient;

public class NotificationForegroundHandler {
	public static final String DEBUG_TAG = "NOTIFICATION_HANDLER";
	private final Handler handler = new Handler(Looper.getMainLooper());
	private static final int INTERVAL_MILLIS = 5 * 60 * 1000; //5mins interval
	private static final int INITIAL_EXECUTION_DELAY = 2_000;

	private final Runnable task;
	private final WebView webView;
	private boolean isPageLoaded = false;

	NotificationForegroundHandler(WebView container) {
		webView = container;
		task = new Runnable() {
			@Override
			public void run() {
				String js =
						"(async () => { " +
						"const tasks = await window?.CHTCore?.AndroidApi?.v1?.taskNotifications(); " +
						"medicmobile_android.onGetNotificationResult(JSON.stringify(tasks ?? [])); " +
						"})(); ";
				Log.d(DEBUG_TAG, "running handler....");
				webView.evaluateJavascript(js, null);
				handler.postDelayed(this, INTERVAL_MILLIS);
			}
		};
	}

	private void postTask() {
		handler.postDelayed(task, INITIAL_EXECUTION_DELAY);
		Log.d(DEBUG_TAG, "foreground handler started");
	}

	void start() {
		if (isPageLoaded) {
			postTask();
			return;
		}
		webView.setWebViewClient(new WebViewClient(){
			@Override
			public void onPageFinished(WebView view, String url) {
				if (!isPageLoaded) {
					isPageLoaded = true;
					postTask();
				}
			}
		});
	}

	void stop() {
		handler.removeCallbacks(task);
		Log.d(DEBUG_TAG, "foreground handler stopped");
	}
}
