package org.medicmobile.webapp.mobile;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.webkit.WebView;

public class NotificationForegroundHandler {
	public static final String DEBUG_TAG = "NOTIFICATION_HANDLER";
	private final Handler handler = new Handler(Looper.getMainLooper());
	private static final int INTERVAL_MILLIS = 5 * 60 * 1000; //5mins interval
	private static final int INITIAL_EXECUTION_DELAY = 5_000;

	private final Runnable runnable;

	NotificationForegroundHandler(WebView container) {
		runnable = new Runnable() {
			@Override
			public void run() {
				String js =
						"(async function (){ " +
						"let result = []; " +
						"const api = window.CHTCore && window.CHTCore.AndroidApi; " +
						"if (api && typeof api.v1.taskNotifications === 'function'){ " +
						"result = await api.v1.taskNotifications(); " +
						"} " +
						"medicmobile_android.onGetNotificationResult(JSON.stringify(result)); " +
						"})(); ";
				container.evaluateJavascript(js, null);
				handler.postDelayed(this, INTERVAL_MILLIS);
			}
		};
	}

	void start() {
		handler.postDelayed(runnable, INITIAL_EXECUTION_DELAY);
		Log.d(DEBUG_TAG, "foreground handler started");
	}

	void stop() {
		handler.removeCallbacks(runnable);
		Log.d(DEBUG_TAG, "foreground handler stopped");
	}
}
