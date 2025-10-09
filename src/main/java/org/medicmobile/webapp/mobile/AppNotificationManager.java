package org.medicmobile.webapp.mobile;

import static org.medicmobile.webapp.mobile.MedicLog.log;

import android.Manifest;
import android.app.Activity;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;

import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;
import androidx.datastore.preferences.core.Preferences;
import androidx.datastore.preferences.core.PreferencesKeys;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.medicmobile.webapp.mobile.util.AppDataStore;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.concurrent.TimeUnit;

public class AppNotificationManager {
	private static final String CHANNEL_ID = "cht_android_notifications";
	private static final String CHANNEL_NAME = "CHT Android Notifications";
	public static final int REQUEST_NOTIFICATION_PERMISSION = 1001;
	public static Preferences.Key<String> TASK_NOTIFICATIONS_KEY =
			PreferencesKeys.stringKey("task_notifications");
	private static final Preferences.Key<Long> TASK_NOTIFICATION_DAY_KEY =
			PreferencesKeys.longKey("cht_task_notification-day");
	private static final Preferences.Key<Long> LATEST_NOTIFICATION_TIMESTAMP_KEY =
			PreferencesKeys.longKey("cht_task_notification_timestamp");

	private final Context context;
	private final NotificationManager manager;
	private final String appUrl;


	public AppNotificationManager(Context context, String appUrl) {
		this.context = context.getApplicationContext();
		this.appUrl = appUrl;
		manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
		createNotificationChannel();
	}

	public boolean hasNotificationPermission() {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
			return ContextCompat.checkSelfPermission(context,
					Manifest.permission.POST_NOTIFICATIONS)
					== PackageManager.PERMISSION_GRANTED;
		}
		//versions below 13
		return true;
	}

	public void requestNotificationPermission(Activity activity) {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasNotificationPermission()) {
			ActivityCompat.requestPermissions(activity,
					new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQUEST_NOTIFICATION_PERMISSION);
		}
	}

	public void startNotificationWorker() {
		if (hasNotificationPermission()) {
			PeriodicWorkRequest request = new PeriodicWorkRequest.Builder(
					NotificationWorker.class,
					NotificationWorker.WORKER_REPEAT_INTERVAL_MINS,
					TimeUnit.MINUTES
			)
					//.setInitialDelay(NotificationWorker.INITIAL_EXECUTION_DELAY_SECS, TimeUnit.SECONDS)
					.addTag(NotificationWorker.NOTIFICATION_WORK_REQUEST_TAG)
					.build();

			WorkManager.getInstance(context).enqueueUniquePeriodicWork(
					NotificationWorker.NOTIFICATION_WORK_NAME,
					ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE,
					request
			);
			log(context, "startNotificationWorker() :: Started Notification Work Manager...");
		}
	}

	public void stopNotificationWorker() {
		WorkManager.getInstance(context).cancelAllWorkByTag(NotificationWorker.NOTIFICATION_WORK_REQUEST_TAG);
		log(context, "stopNotificationWorker() :: Stopped notification work manager");
	}

	void cancelAllNotifications() {
		manager.cancelAll();
	}

	void showMultipleTaskNotifications(JSONArray dataArray) throws JSONException {
		Intent intent = new Intent(context, EmbeddedBrowserActivity.class);
		intent.setData(Uri.parse(appUrl.concat("/#/tasks")));
		//appDataStore.putString();

		for (int i = 0; i < dataArray.length(); i++) {
			JSONObject notification = dataArray.getJSONObject(i);
			String contentText = notification.getString("contentText");
			String title = notification.getString("title");
			long readyAt = notification.getLong("readyAt");
			int notificationId = (int) (readyAt % Integer.MAX_VALUE);
			showNotification(intent, notificationId + i, title, contentText);
		}
	}

	void filterTaskNotification(JSONObject notification) throws JSONException {
		AppDataStore appDataStore = AppDataStore.getInstance(context);
		//LocalDate today = ;
		long startOfDay = LocalDate.now()
				.atStartOfDay(ZoneId.systemDefault())
				.toInstant().toEpochMilli();
		long notificationDay = appDataStore.getLongValue(TASK_NOTIFICATION_DAY_KEY).blockingGet();
		long latestNotificationTimestamp = appDataStore.getLongValue(LATEST_NOTIFICATION_TIMESTAMP_KEY).blockingGet();


		//appDataStore.putString(TASK_NOTIFICATION_DAY_KEY, )
		long latestReadyAt = notification.getLong("readyAt");
		long latestEndDate = notification.getLong("endDate");



	}

	void showNotification(Intent intent, int id, String title, String contentText) {
		intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
		PendingIntent pendingIntent = PendingIntent.getActivity(
				context,
				0,
				intent,
				PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE
		);
		NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
				.setSmallIcon(R.drawable.ic_notification)
				.setContentTitle(title)
				.setContentText(contentText)
				.setAutoCancel(true)
				.setContentIntent(pendingIntent)
				.setPriority(NotificationCompat.PRIORITY_DEFAULT);
		manager.notify(id, builder.build());
	}


	public void showNotificationsFromJsArray(String jsArrayString) throws JSONException {
		JSONArray dataArray = Utils.parseJSArrayData(jsArrayString);
		showMultipleTaskNotifications(dataArray);
	}

	private void createNotificationChannel() {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
			NotificationChannel notificationChannel = new NotificationChannel(
					CHANNEL_ID,
					CHANNEL_NAME,
					NotificationManager.IMPORTANCE_DEFAULT
			);
			manager.createNotificationChannel(notificationChannel);
		}
	}

}