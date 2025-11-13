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
import android.text.TextUtils;

import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.medicmobile.webapp.mobile.util.AppDataStore;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.concurrent.TimeUnit;

public class AppNotificationManager {
	private static final String CHANNEL_ID = "cht_android_notifications";
	private static final String CHANNEL_NAME = "CHT Android Notifications";
	public static final int REQUEST_NOTIFICATION_PERMISSION = 1001;
	public static final String TASK_NOTIFICATIONS_KEY = "task_notifications";
	public static final String TASK_NOTIFICATION_DAY_KEY = "cht_task_notification_day";
	public static final String LATEST_NOTIFICATION_TIMESTAMP_KEY = "cht_task_notification_timestamp";
	public static final String MAX_NOTIFICATIONS_TO_SHOW_KEY = "cht_max_task_notifications";

	private final Context context;
	private final NotificationManager manager;
	private final String appUrl;
	private final AppDataStore appDataStore;

	public AppNotificationManager(Context context) {
		this.context = context.getApplicationContext();
		SettingsStore settings = SettingsStore.in(context);
		this.appUrl = settings.getAppUrl();
		manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
		appDataStore = AppDataStore.getInstance(this.context);
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
					.addTag(NotificationWorker.NOTIFICATION_WORK_REQUEST_TAG)
					.build();

			WorkManager.getInstance(context).enqueueUniquePeriodicWork(
					NotificationWorker.NOTIFICATION_WORK_NAME,
					ExistingPeriodicWorkPolicy.KEEP,
					request
			);
			log(context, "startNotificationWorker() :: Started Notification Work Manager...");
		}
	}

	public void stopNotificationWorker() {
		appDataStore.saveString(TASK_NOTIFICATIONS_KEY, "[]");
		WorkManager.getInstance(context).cancelAllWorkByTag(NotificationWorker.NOTIFICATION_WORK_REQUEST_TAG);
		log(context, "stopNotificationWorker() :: Stopped notification work manager");
	}

	void cancelAllNotifications() {
		manager.cancelAll();
	}

	/**
	 * @param jsArrayString string notifications sorted by readyAt in descending order
	 * @throws JSONException throws JSONException
	 * Method is blocking don't run on UI thread
	 */
	public void showNotificationsFromJsArray(String jsArrayString) throws JSONException {
		JSONArray dataArray = Utils.parseJSArrayData(jsArrayString);
		if (dataArray.length() == 0) {
			return;
		}
		showMultipleTaskNotifications(dataArray);
	}

	private void showMultipleTaskNotifications(JSONArray dataArray) throws JSONException {
		Intent intent = new Intent(context, EmbeddedBrowserActivity.class);
		intent.setData(Uri.parse(TextUtils.concat(appUrl, "/#/tasks").toString()));
		long maxNotifications = appDataStore.getLongBlocking(MAX_NOTIFICATIONS_TO_SHOW_KEY, 8L);
		long latestStoredTimestamp = getLatestStoredTimestamp(getStartOfDay());
		int counter = 0;
		for (int i = 0; i < dataArray.length(); i++) {
			JSONObject notification = dataArray.getJSONObject(i);
			long readyAt = notification.getLong("readyAt");
			long endDate = notification.getLong("endDate");
			long dueDate = notification.getLong("dueDate");
			if (isValidNotification(latestStoredTimestamp, readyAt, dueDate, endDate)) {
				int notificationId = notification.getString("_id").hashCode();
				String contentText = notification.getString("contentText");
				String title = notification.getString("title");
				showNotification(intent, notificationId, title, contentText);
				counter++;
			}
			if (counter >= maxNotifications) {
				break;
			}
		}
		saveLatestNotificationTimestamp(dataArray.getJSONObject(0).getLong("readyAt"));
	}

	private boolean isValidNotification(long latestStoredTimestamp, long readyAt, long dueDate, long endDate) {
		long startOfDay = getStartOfDay();
		return readyAt > latestStoredTimestamp && dueDate <= startOfDay && endDate >= startOfDay;
	}

	public void saveLatestNotificationTimestamp(long value) {
		appDataStore.saveLong(LATEST_NOTIFICATION_TIMESTAMP_KEY, value);
	}

	public long getStartOfDay() {
		return LocalDate.now()
				.atStartOfDay(ZoneId.systemDefault())
				.toInstant().toEpochMilli();
	}

	private long getLatestStoredTimestamp(long startOfDay) {
		if (isNewDay(startOfDay)) {
			return 0;
		}
		return appDataStore.getLongBlocking(LATEST_NOTIFICATION_TIMESTAMP_KEY, 0L);
	}

	private boolean isNewDay(long startOfDay) {
		long storedNotificationDay = appDataStore.getLongBlocking(TASK_NOTIFICATION_DAY_KEY, 0L);
		if (getStartOfDay() != storedNotificationDay) {
			appDataStore.saveLong(TASK_NOTIFICATION_DAY_KEY, startOfDay);
			return true;
		}
		return false;
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
