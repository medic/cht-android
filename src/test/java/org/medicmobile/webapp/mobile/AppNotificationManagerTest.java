package org.medicmobile.webapp.mobile;

import static org.junit.Assert.assertEquals;

import android.app.NotificationManager;
import android.content.Context;

import org.json.JSONException;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.medicmobile.webapp.mobile.util.AppDataStore;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.shadows.ShadowNotificationManager;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.concurrent.TimeUnit;

@RunWith(RobolectricTestRunner.class)
public class AppNotificationManagerTest {
	private Context context;
	private AppNotificationManager appNotificationManager;
	private ShadowNotificationManager shadowNotificationManager;
	private long startOfDay;
	private static final long DAY_IN_MILLIS = TimeUnit.DAYS.toMillis(1);

	@Before
	public void setup() {
		context = RuntimeEnvironment.getApplication().getApplicationContext();
		NotificationManager testNotificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
		startOfDay = LocalDate.now()
				.atStartOfDay(ZoneId.systemDefault())
				.toInstant().toEpochMilli();
		appNotificationManager = new AppNotificationManager(context, "");
		shadowNotificationManager = Shadows.shadowOf(testNotificationManager);
	}
	@After
	public void resetDataStore() {
		AppDataStore appDataStore = new AppDataStore(context);
		appDataStore.saveString(AppNotificationManager.TASK_NOTIFICATIONS_KEY, "[]");
		appDataStore.saveLong(AppNotificationManager.TASK_NOTIFICATION_DAY_KEY, 0);
		appDataStore.saveLong(AppNotificationManager.LATEST_NOTIFICATION_TIMESTAMP_KEY, 0);
	}

	@Test
	public void showsNotifications() throws JSONException {
		String jsData = "[" + getJSTaskNotificationString(startOfDay, startOfDay, startOfDay, startOfDay) + "]";
		appNotificationManager.showNotificationsFromJsArray(jsData);
		assertEquals(2, shadowNotificationManager.getAllNotifications().size());
	}

	@Test
	public void notShowTasksMoreThanOnceSameDay() throws JSONException, InterruptedException {
		String jsData = "[" + getJSTaskNotificationString(startOfDay, startOfDay, startOfDay, startOfDay) + "]";

		appNotificationManager.showNotificationsFromJsArray(jsData);
		assertEquals(2, shadowNotificationManager.getAllNotifications().size());

		appNotificationManager.cancelAllNotifications();
		assertEquals(0, shadowNotificationManager.getAllNotifications().size());

		Thread.sleep(1000);
		appNotificationManager.showNotificationsFromJsArray(jsData);
		assertEquals(0, shadowNotificationManager.getAllNotifications().size());

	}

	@Test
	public void showsOnlyNewNotifications() throws JSONException, InterruptedException {
		String jsData = getJSTaskNotificationString(startOfDay, startOfDay, startOfDay, startOfDay);

		appNotificationManager.showNotificationsFromJsArray("[" + jsData + "]");
		assertEquals(2, shadowNotificationManager.getAllNotifications().size());

		String newNotificationData = """
				[
					{
						"readyAt": %d,
						"title": "Task for report 3",
						"contentText": "You have a Task 1",
						"endDate": %d
					},
					%s
				]
				""".formatted(startOfDay + 1000, startOfDay, jsData);

		Thread.sleep(1000);

		appNotificationManager.cancelAllNotifications();
		assertEquals(0, shadowNotificationManager.getAllNotifications().size());
		appNotificationManager.showNotificationsFromJsArray(newNotificationData);
		assertEquals(1, shadowNotificationManager.getAllNotifications().size());

	}

	@Test
	public void notShowTasksPastEndDate() throws JSONException, InterruptedException {
		String jsData = """
					[{
						"readyAt": %d,
						"title": "Task for report past end date",
						"contentText": "You have task past end date",
						"endDate": %d
					}]
				""".formatted(startOfDay - DAY_IN_MILLIS, startOfDay - DAY_IN_MILLIS);


		appNotificationManager.showNotificationsFromJsArray(jsData);
		assertEquals(0, shadowNotificationManager.getAllNotifications().size());
	}

	//should be ordered by readyAt in descending order
	private String getJSTaskNotificationString(long task1StartDate, long task1EndDate, long task2StartDate, long task2EndDate) {
		return """
					{
						"readyAt": %d,
						"title": "Task for report 2",
						"contentText": "You have task 2",
						"endDate": %d
					},
					{
						"readyAt": %d,
						"title": "Task for report 1",
						"contentText": "You have a Task 1",
						"endDate": %d
					}
				""".formatted(task1StartDate, task1EndDate, task2StartDate, task2EndDate);
	}
}
