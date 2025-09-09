package org.medicmobile.webapp.mobile;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.webkit.WebView;

public class NotificationForegroundHandler {
	public static final String DEBUG_TAG = "NOTIFICATION_HANDLER";
	private final Handler handler = new Handler(Looper.getMainLooper());
	private static final int INTERVAL_MILLIS = 5 * 60 * 1000; //5mins interval
	private static final int INITIAL_EXECUTION_DELAY = NotificationWorker.EXECUTION_TIMEOUT_SECS * 1000;

	private final Runnable runnable;
	private boolean isRunning = false;

	NotificationForegroundHandler(WebView container, String appUrl) {
		String js = "(async function (){" +
				" const api = window.CHTCore.AndroidApi;" +
				" const tasks = await api.v1.taskNotifications();" +
				"medicmobile_android.onGetNotificationResult(JSON.stringify(tasks), '" + appUrl + "');" +
				"})();";
		runnable = new Runnable() {
			@Override
			public void run() {
				container.evaluateJavascript(js, null);
				handler.postDelayed(this, INTERVAL_MILLIS);
			}
		};
	}

	void start() {
		if (!isRunning) {
			handler.postDelayed(runnable, INITIAL_EXECUTION_DELAY);
			isRunning = true;
			Log.d(DEBUG_TAG, "foreground handler started");
		}
	}

	void stop() {
		if (isRunning) {
			handler.removeCallbacks(runnable);
			isRunning = false;
			Log.d(DEBUG_TAG, "foreground handler stopped");
		}
	}
}
