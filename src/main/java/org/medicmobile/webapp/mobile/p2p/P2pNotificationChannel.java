package org.medicmobile.webapp.mobile.p2p;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;

/**
	* Manages notifications for P2P sync progress.
	*
	* Creates a dedicated notification channel (required for Android 8.0+)
	* and builds notifications for the foreground service and progress updates.
	*/
@android.annotation.TargetApi(26)
public class P2pNotificationChannel {

	static final String CHANNEL_ID = "cht_p2p_sync";
	private static final String CHANNEL_NAME = "P2P Sync";
	private static final String CHANNEL_DESCRIPTION = "Notifications for peer-to-peer data sync";
	static final int NOTIFICATION_ID = 42001;

	private final Context context;
	private final NotificationManager notificationManager;

	public P2pNotificationChannel(Context context) {
		if (context == null) {
			throw new IllegalArgumentException("context must not be null");
		}
		this.context = context.getApplicationContext();
		this.notificationManager = (NotificationManager) this.context
				.getSystemService(Context.NOTIFICATION_SERVICE);
	}

	/**
		* Create the notification channel. Must be called before showing any notification.
		* Safe to call multiple times — Android ignores duplicate channel creation.
		*/
	public void createChannel() {
		// @TargetApi(26) guarantees API 26+ — no version check needed
		NotificationChannel channel = new NotificationChannel(
				CHANNEL_ID,
				CHANNEL_NAME,
				NotificationManager.IMPORTANCE_LOW
		);
		channel.setDescription(CHANNEL_DESCRIPTION);
		channel.setShowBadge(false);
		channel.enableVibration(false);
		notificationManager.createNotificationChannel(channel);
	}

	/**
		* Build a basic sync notification (used by the foreground service).
		*
		* @param title   notification title
		* @param message notification body text
		* @return the built notification
		*/
	public Notification buildSyncNotification(String title, String message) {
		// P2P requires API 26+ — channel constructor always available
		Notification.Builder builder = new Notification.Builder(context, CHANNEL_ID);

		builder.setContentTitle(title)
				.setContentText(message)
				.setSmallIcon(android.R.drawable.stat_notify_sync)
				.setOngoing(true)
				.setOnlyAlertOnce(true);

		return builder.build();
	}

	/**
		* Update the notification with sync progress.
		*
		* @param docsSynced number of docs synced so far
		* @param totalDocs  total docs to sync (0 if unknown)
		*/
	public void updateProgress(int docsSynced, int totalDocs) {
		// P2P requires API 26+ — channel constructor always available
		Notification.Builder builder = new Notification.Builder(context, CHANNEL_ID);

		String text;
		if (totalDocs > 0) {
			text = "Syncing: " + docsSynced + " / " + totalDocs + " docs";
			builder.setProgress(totalDocs, docsSynced, false);
		} else {
			text = "Syncing: " + docsSynced + " docs";
			builder.setProgress(0, 0, true);
		}

		builder.setContentTitle(CHANNEL_NAME)
				.setContentText(text)
				.setSmallIcon(android.R.drawable.stat_notify_sync)
				.setOngoing(true)
				.setOnlyAlertOnce(true);

		notificationManager.notify(NOTIFICATION_ID, builder.build());
	}

	/**
		* Show a completion notification.
		*
		* @param totalDocs total number of docs synced
		*/
	public void showComplete(int totalDocs) {
		// P2P requires API 26+ — channel constructor always available
		Notification.Builder builder = new Notification.Builder(context, CHANNEL_ID);

		builder.setContentTitle("P2P Sync Complete")
				.setContentText(totalDocs + " docs synced successfully")
				.setSmallIcon(android.R.drawable.stat_notify_sync_noanim)
				.setOngoing(false)
				.setAutoCancel(true);

		notificationManager.notify(NOTIFICATION_ID, builder.build());
	}

	/**
		* Dismiss the notification.
		*/
	public void dismiss() {
		notificationManager.cancel(NOTIFICATION_ID);
	}

	/**
		* Get the notification ID used by this channel.
		*/
	public int getNotificationId() {
		return NOTIFICATION_ID;
	}
}
