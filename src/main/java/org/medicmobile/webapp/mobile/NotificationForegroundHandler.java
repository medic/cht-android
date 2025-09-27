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

	private final Runnable runnable;
	private final WebView webView;

	NotificationForegroundHandler(WebView container) {
		webView = container;
		runnable = new Runnable() {
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

	void start() {
		webView.setWebViewClient(new WebViewClient(){
			boolean isStarted = false;
			@Override
			public void onPageFinished(WebView view, String url) {
				if (isStarted) {
					return;
				}
				isStarted = true;
				handler.postDelayed(runnable, INITIAL_EXECUTION_DELAY);
				Log.d(DEBUG_TAG, "foreground handler started");
			}
		});
	}

	void stop() {
		handler.removeCallbacks(runnable);
		Log.d(DEBUG_TAG, "foreground handler stopped");
	}
}
