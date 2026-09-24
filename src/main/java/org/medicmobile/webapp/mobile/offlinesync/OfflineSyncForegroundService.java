package org.medicmobile.webapp.mobile.offlinesync;

import static org.medicmobile.webapp.mobile.MedicLog.log;
import static org.medicmobile.webapp.mobile.MedicLog.warn;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;

import androidx.core.app.NotificationCompat;

import org.medicmobile.webapp.mobile.EmbeddedBrowserActivity;
import org.medicmobile.webapp.mobile.R;

/**
	* Keeps a sharing session alive while the app is not in front.
	*
	* Without this, Android is free to stop the app once the user switches away, which would take
	* the hotspot and the local server with it part way through a transfer. A foreground service is
	* what buys the session the right to keep running, and the notification is the price: the system
	* requires one, and it is also how the user knows the phone is still sharing.
	*/
public class OfflineSyncForegroundService extends Service {

	private static final String CHANNEL_ID = "cht_offline_sync_session";
	private static final String CHANNEL_NAME = "Offline sync";
	private static final int NOTIFICATION_ID = 2001;

	/** What the notification should say, so the caller does not have to know about notifications. */
	private static final String EXTRA_STATUS = "status";
	private static final String EXTRA_DELIVERED = "delivered";

	/** Shown after the session is over, so it must not be the one holding the service up. */
	private static final int RESULT_NOTIFICATION_ID = 2002;

	public static final String STATUS_HOSTING = "hosting";
	public static final String STATUS_SENDING = "sending";

	public static void start(Context context, String status) {
		start(context, status, 0);
	}

	/**
		* Starts or updates the session notification.
		*
		* @param delivered how many bundles have been handed over so far, shown while sending. Not
		*                  a fraction: the bundles are produced as the data is read, so how many
		*                  there will be is not known until the last one is made.
		*/
	public static void start(Context context, String status, int delivered) {
		Intent intent = new Intent(context, OfflineSyncForegroundService.class)
				.putExtra(EXTRA_STATUS, status)
				.putExtra(EXTRA_DELIVERED, delivered);
		try {
			androidx.core.content.ContextCompat.startForegroundService(context, intent);
		} catch (RuntimeException e) {
			// A session still works while the app is in front, so this is not worth abandoning it
			// over: the cost is that the system may stop the session once the user switches away.
			warn(e, "Could not start the sharing service, the session will not survive backgrounding");
		}
	}

	public static void stop(Context context) {
		context.stopService(new Intent(context, OfflineSyncForegroundService.class));
	}

	@Override public int onStartCommand(Intent intent, int flags, int startId) {
		String status = intent == null ? STATUS_HOSTING : intent.getStringExtra(EXTRA_STATUS);
		int delivered = intent == null ? 0 : intent.getIntExtra(EXTRA_DELIVERED, 0);
		createChannel(this);
		startInForeground(buildNotification(status, delivered));
		log(this, "Sharing service running: " + status);
		// Not restarted with a null intent if the system kills us: a session cannot be resumed
		// from nothing, and coming back with a notification for a session that is over would lie.
		return START_NOT_STICKY;
	}

	@Override public void onDestroy() {
		log(this, "Sharing service stopped");
		super.onDestroy();
	}

	/** Not a bound service: callers start and stop it, and it holds no state for them. */
	@Override public IBinder onBind(Intent intent) {
		return null;
	}

	private void startInForeground(Notification notification) {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
			// From Android 14 a foreground service has to declare what it is for, and this one is
			// holding a local network connection open.
			startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE);
			return;
		}
		startForeground(NOTIFICATION_ID, notification);
	}

	/**
		* Leaves a notification behind saying a handover did not finish.
		*
		* The session notification disappears when the service stops, so without this a user who
		* had switched to another app would see it vanish and have no way to tell whether that
		* meant done or broken.
		*/
	public static void reportFailed(Context context) {
		stop(context);
		// The session may never have started, and on Android 8 and later a notification on a
		// channel that does not exist is dropped silently. This one exists to be seen.
		createChannel(context);
		NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
				.setSmallIcon(R.drawable.ic_notification)
				.setContentTitle(context.getString(R.string.offlineSyncSessionTitle))
				.setContentText(context.getString(R.string.offlineSyncSessionFailed))
				.setAutoCancel(true)
				.setPriority(NotificationCompat.PRIORITY_DEFAULT);
		notificationManager(context).notify(RESULT_NOTIFICATION_ID, builder.build());
	}

	private Notification buildNotification(String status, int delivered) {
		Intent open = new Intent(this, EmbeddedBrowserActivity.class)
				.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
		PendingIntent pendingIntent = PendingIntent.getActivity(
				this, 0, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

		return new NotificationCompat.Builder(this, CHANNEL_ID)
				.setSmallIcon(R.drawable.ic_notification)
				.setContentTitle(getString(R.string.offlineSyncSessionTitle))
				.setContentText(textFor(status, delivered))
				.setOngoing(true)
				.setContentIntent(pendingIntent)
				.setPriority(NotificationCompat.PRIORITY_LOW)
				.build();
	}

	private String textFor(String status, int delivered) {
		if (!STATUS_SENDING.equals(status)) {
			return getString(R.string.offlineSyncSessionHosting);
		}
		if (delivered == 0) {
			return getString(R.string.offlineSyncSessionSending);
		}
		return getResources().getQuantityString(R.plurals.offlineSyncSessionSent, delivered, delivered);
	}

	private static void createChannel(Context context) {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
			// Low importance: this is a status the user can check, not something to interrupt them
			// with, and it stays up for as long as the session does.
			NotificationChannel channel = new NotificationChannel(
					CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_LOW);
			notificationManager(context).createNotificationChannel(channel);
		}
	}

	// By name rather than by class: the typed lookup needs API 23 and this app supports 21.
	private static NotificationManager notificationManager(Context context) {
		return (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
	}
}
