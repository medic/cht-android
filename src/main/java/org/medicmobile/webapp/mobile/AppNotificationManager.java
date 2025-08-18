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
import android.webkit.ValueCallback;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import java.util.Objects;
import java.util.concurrent.TimeUnit;

public class AppNotificationManager {
	private static final String CHANNEL_ID = "cht_android_notifications";
	private static final String CHANNEL_NAME = "CHT Android Notifications";
	public static final int REQUEST_NOTIFICATION_PERMISSION = 1001;
	private static boolean hasCheckedForNotificationApi = false;
	private static boolean hasTaskNotificationsApi = false;

	private static volatile AppNotificationManager instance;
	private final Context context;

	NotificationManager manager;

	private AppNotificationManager(Context context) {
		this.context = context.getApplicationContext();
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
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasNotificationPermission()) {
			ActivityCompat.requestPermissions(activity,
					new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQUEST_NOTIFICATION_PERMISSION);
		} else {
			startNotificationWorker();
		}
	}

	void initNotificationWorker(WebView webView, Activity activity) {
		if (!hasNotificationPermission()) {
			stopNotificationWorker();
		}
		if (hasTaskNotificationsApi) {
			startNotificationWorker();
		} else {
			checkTaskNotificationApi(webView, activity);
		}
	}

	private void startNotificationWorker() {
		PeriodicWorkRequest request = new PeriodicWorkRequest.Builder(
				NotificationWorker.class,
				NotificationWorker.WORKER_REPEAT_INTERVAL_MINS,
				TimeUnit.MINUTES
		).addTag(NotificationWorker.TAG).build();

		WorkManager.getInstance(context).enqueueUniquePeriodicWork(
				"appNotifications",
				ExistingPeriodicWorkPolicy.KEEP,
				request
		);
		log(context, "startNotificationWorker() :: Started Notification Worker Manager...");
	}

	private void stopNotificationWorker() {
		WorkManager.getInstance(context).cancelAllWorkByTag(NotificationWorker.TAG);
		log(this, "stopNotificationWorker() :: Stopped notification worker manager");
	}

	private void checkTaskNotificationApi(WebView webView, Activity activity) {
		webView.setWebViewClient(new WebViewClient() {
			@Override
			public void onPageFinished(WebView view, String url) {
				String jsCheckApi = "(() => typeof window.CHTCore.AndroidApi.v1.taskNotifications === 'function')();";
				webView.evaluateJavascript(jsCheckApi, new ValueCallback<String>() {
					@Override
					public void onReceiveValue(String hasApi) {
						if (!hasCheckedForNotificationApi && !Objects.equals(hasApi, "null")) {
							hasCheckedForNotificationApi = true;
							if (Objects.equals(hasApi, "true")) {
								hasTaskNotificationsApi = true;
								requestNotificationPermission(activity);
							}
						}
					}
				});
			}
		});
	}

	void showNotification(String appUrl, int id, String title, String contentText) {
		Intent intent = new Intent(context, EmbeddedBrowserActivity.class);
		intent.setData(Uri.parse(appUrl.concat("/#/tasks")));
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
