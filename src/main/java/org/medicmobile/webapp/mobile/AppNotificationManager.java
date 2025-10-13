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

	private final Context context;
	private final NotificationManager manager;
	private final String appUrl;
	private final AppDataStore appDataStore;

	public AppNotificationManager(Context context, String appUrl) {
		this.context = context.getApplicationContext();
		this.appUrl = appUrl;
		manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
		appDataStore = new AppDataStore(this.context);
		createNotificationChannel();
	}

	public long getStartOfDay() {
		return LocalDate.now()
				.atStartOfDay(ZoneId.systemDefault())
				.toInstant().toEpochMilli();
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

	public void showNotificationsFromJsArray(String jsArrayString) throws JSONException {
		JSONArray dataArray = Utils.parseJSArrayData(jsArrayString);
		if (dataArray.length() == 0) {
			return;
		}
		showMultipleTaskNotifications(dataArray);
	}

	/**
	 * @param dataArray JSONArray notifications sorted by readyAt in descending order
	 * @throws JSONException throws JSONException
	 * Method is blocking don't run on UI thread
	 */
	void showMultipleTaskNotifications(JSONArray dataArray) throws JSONException {
		Intent intent = new Intent(context, EmbeddedBrowserActivity.class);
		intent.setData(Uri.parse(appUrl.concat("/#/tasks")));
		long startOfDay = getStartOfDay();
		long latestStoredTimestamp = getLatestStoredTimestamp(startOfDay);
		for (int i = 0; i < dataArray.length(); i++) {
			JSONObject notification = dataArray.getJSONObject(i);
			long readyAt = notification.getLong("readyAt");
			long endDate = notification.getLong("endDate");
			if (readyAt > latestStoredTimestamp && endDate >= startOfDay) {
				int notificationId = (int) (readyAt % Integer.MAX_VALUE);
				String contentText = notification.getString("contentText");
				String title = notification.getString("title");
				showNotification(intent, notificationId + i, title, contentText);
			}
		}
		appDataStore.saveLong(LATEST_NOTIFICATION_TIMESTAMP_KEY, dataArray.getJSONObject(0).getLong("readyAt"));
	}

	private long getLatestStoredTimestamp(long startOfDay) {
		if (isNewDay(startOfDay)) {
			appDataStore.saveLong(TASK_NOTIFICATION_DAY_KEY, startOfDay);
			return 0;
		}
		return appDataStore.getLongBlocking(LATEST_NOTIFICATION_TIMESTAMP_KEY, 0);
	}

	private boolean isNewDay(long startOfDay) {
		long storedNotificationDay = appDataStore.getLongBlocking(TASK_NOTIFICATION_DAY_KEY, 0);
		return startOfDay != storedNotificationDay;
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