package org.medicmobile.webapp.mobile;

import static org.medicmobile.webapp.mobile.MedicLog.log;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import org.json.JSONException;
import org.medicmobile.webapp.mobile.util.AppDataStore;

public class NotificationWorker extends Worker {
	public static final String NOTIFICATION_WORK_REQUEST_TAG = "cht_notification_tag";
	public static final String NOTIFICATION_WORK_NAME = "appNotifications";
	static final int WORKER_REPEAT_INTERVAL_MINS = 15;

	public NotificationWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
		super(context, workerParams);
	}

	@NonNull
	@Override
	public Result doWork() {
		Context context = getApplicationContext();
		AppDataStore appDataStore = AppDataStore.getInstance(context);
		AppNotificationManager appNotificationManager = new AppNotificationManager(context);
		try {
			String result = appDataStore
					.getStringBlocking(AppNotificationManager.TASK_NOTIFICATIONS_KEY, "[]");
			appNotificationManager.showNotificationsFromJsArray(result);
			return Result.success();
		} catch (JSONException e) {
			log(e, "error showing notifications");
			return Result.failure();
		}
	}
}
