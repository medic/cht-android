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
import android.webkit.WebView;

import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.concurrent.TimeUnit;

public class AppNotificationManager {
	private static final String CHANNEL_ID = "cht_android_notifications";
	private static final String CHANNEL_NAME = "CHT Android Notifications";
	public static final int REQUEST_NOTIFICATION_PERMISSION = 1001;

	private static volatile AppNotificationManager instance;
	private final Context context;
	private final NotificationManager manager;
	private final String appUrl;
	NotificationForegroundHandler foregroundNotificationHandler;


	private AppNotificationManager(Context context) {
		this.context = context.getApplicationContext();
		SettingsStore settings = SettingsStore.in(this.context);
		appUrl = settings.getAppUrl();
		manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
		createNotificationChannel();
	}

	public static AppNotificationManager getInstance(Context context) {
		if (instance == null) {
			synchronized (AppNotificationManager.class) {
				if (instance == null) {
					instance = new AppNotificationManager(context);
				}
			}
		}
		return instance;
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
		if (!hasNotificationPermission()) {
			stopForegroundNotificationHandler();
		}
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasNotificationPermission()) {
			ActivityCompat.requestPermissions(activity,
					new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQUEST_NOTIFICATION_PERMISSION);
		}
	}

	void startForegroundNotificationHandler(WebView webView, Activity activity) {
		boolean isValidUrl = Utils.isValidNavigationUrl(appUrl, webView.getUrl());
		foregroundNotificationHandler = new NotificationForegroundHandler(webView);
		manager.cancelAll();
		if (hasNotificationPermission() && isValidUrl) {
			foregroundNotificationHandler.start();
		} else {
			requestNotificationPermission(activity);
		}
	}

	public void startNotificationWorker(String url) {
		boolean isValidUrl = Utils.isValidNavigationUrl(appUrl, url);
		if (hasNotificationPermission() && isValidUrl) {
			PeriodicWorkRequest request = new PeriodicWorkRequest.Builder(
					NotificationWorker.class,
					NotificationWorker.WORKER_REPEAT_INTERVAL_MINS,
					TimeUnit.MINUTES
			)
					.setInitialDelay(NotificationWorker.INITIAL_EXECUTION_DELAY_MINS, TimeUnit.MINUTES)
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

	void stopForegroundNotificationHandler() {
		if (foregroundNotificationHandler != null) {
			foregroundNotificationHandler.stop();
		}
	}

	public void stopNotificationWorker() {
		WorkManager.getInstance(context).cancelAllWorkByTag(NotificationWorker.NOTIFICATION_WORK_REQUEST_TAG);
		log(context, "stopNotificationWorker() :: Stopped notification work manager");
	}

	void showMultipleTaskNotifications(JSONArray dataArray) throws JSONException {
		Intent intent = new Intent(context, EmbeddedBrowserActivity.class);
		intent.setData(Uri.parse(appUrl.concat("/#/tasks")));
		for (int i = 0; i < dataArray.length(); i++) {
			JSONObject notification = dataArray.getJSONObject(i);
			String contentText = notification.getString("contentText");
			String title = notification.getString("title");
			long readyAt = notification.getLong("readyAt");
			int notificationId = (int) (readyAt % Integer.MAX_VALUE);
			showNotification(intent, notificationId + i, title, contentText);
		}
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
