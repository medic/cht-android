package org.medicmobile.webapp.mobile;

import static org.medicmobile.webapp.mobile.MedicLog.log;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.OptIn;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import org.json.JSONException;
import org.medicmobile.webapp.mobile.util.AppDataStore;

import kotlinx.coroutines.ExperimentalCoroutinesApi;

public class NotificationWorker extends Worker {
	public static final String NOTIFICATION_WORK_REQUEST_TAG = "cht_notification_tag";
	public static final String NOTIFICATION_WORK_NAME = "appNotifications";
	static final int WORKER_REPEAT_INTERVAL_MINS = 15;

	public NotificationWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
		super(context, workerParams);
	}

	@OptIn(markerClass = ExperimentalCoroutinesApi.class)
	@NonNull
	@Override
	public Result doWork() {
		Context context = getApplicationContext();
		SettingsStore settings = SettingsStore.in(context);
		String appUrl = settings.getAppUrl();
		AppDataStore appDataStore = AppDataStore.getInstance(context);
		AppNotificationManager appNotificationManager = new AppNotificationManager(context, appUrl);
		try {
			String result = appDataStore
					.getValue(AppNotificationManager.TASK_NOTIFICATIONS_KEY, "[]")
					.blockingGet();
			appNotificationManager.showNotificationsFromJsArray(result);
			return Result.success();
		} catch (JSONException e) {
			log(e, "error showing notifications");
			return Result.failure();
		}
	}
}
