package org.medicmobile.webapp.mobile.p2p;

import android.app.Notification;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

/**
	* Foreground service to keep P2P sync running when the app is in background.
	*
	* Android aggressively kills background services. A foreground service with
	* a persistent notification keeps the process alive during the sync session.
	*
	* Usage:
	*   // Start
	*   Intent i = new Intent(context, P2pForegroundService.class);
	*   i.setAction(P2pForegroundService.ACTION_START);
	*   context.startForegroundService(i);  // API 26+
	*
	*   // Stop
	*   Intent i = new Intent(context, P2pForegroundService.class);
	*   i.setAction(P2pForegroundService.ACTION_STOP);
	*   context.startService(i);
	*/
public class P2pForegroundService extends Service {

	private static final String TAG = "P2pForegroundService";

	public static final String ACTION_START = "org.medicmobile.webapp.mobile.p2p.START";
	public static final String ACTION_STOP = "org.medicmobile.webapp.mobile.p2p.STOP";

	private P2pNotificationChannel notificationChannel;

	@Override
	public void onCreate() {
		super.onCreate();
		notificationChannel = new P2pNotificationChannel(this);
		notificationChannel.createChannel();
		Log.i(TAG, "P2P foreground service created");
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		if (intent == null) {
			Log.w(TAG, "Received null intent, stopping");
			stopSelf();
			return START_NOT_STICKY;
		}

		String action = intent.getAction();
		if (ACTION_START.equals(action)) {
			handleStart();
		} else if (ACTION_STOP.equals(action)) {
			handleStop();
		} else {
			Log.w(TAG, "Unknown action: " + action);
		}

		return START_STICKY;
	}

	@Override
	public IBinder onBind(Intent intent) {
		// Not a bound service
		return null;
	}

	@Override
	public void onDestroy() {
		Log.i(TAG, "P2P foreground service destroyed");
		if (notificationChannel != null) {
			notificationChannel.dismiss();
		}
		super.onDestroy();
	}

	// --- Static helpers ---

	/**
		* Convenience method to start the foreground service.
		*/
	public static void start(Context context) {
		Intent intent = new Intent(context, P2pForegroundService.class);
		intent.setAction(ACTION_START);
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
			context.startForegroundService(intent);
		} else {
			context.startService(intent);
		}
	}

	/**
		* Convenience method to stop the foreground service.
		*/
	public static void stop(Context context) {
		Intent intent = new Intent(context, P2pForegroundService.class);
		intent.setAction(ACTION_STOP);
		context.startService(intent);
	}

	// --- Private ---

	private void handleStart() {
		Log.i(TAG, "Starting foreground P2P sync service");
		Notification notification = notificationChannel.buildSyncNotification(
				"P2P Sync Active",
				"Waiting for peer connections..."
		);
		startForeground(P2pNotificationChannel.NOTIFICATION_ID, notification);
	}

	private void handleStop() {
		Log.i(TAG, "Stopping foreground P2P sync service");
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
			stopForeground(STOP_FOREGROUND_REMOVE);
		} else {
			stopForeground(true); //NOLINT: deprecated but needed for API 21-23
		}
		stopSelf();
	}
}
