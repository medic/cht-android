package org.medicmobile.webapp.mobile;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

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

import java.util.concurrent.TimeUnit;

@RunWith(RobolectricTestRunner.class)
public class AppNotificationManagerTest {
	private AppNotificationManager appNotificationManager;
	private ShadowNotificationManager shadowNotificationManager;
	private AppDataStore appDataStore;
	private long startOfDay;
	private static final long DAY_IN_MILLIS = TimeUnit.DAYS.toMillis(1);


	@Before
	public void setup() {
		Context context = RuntimeEnvironment.getApplication().getApplicationContext();
		NotificationManager testNotificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
		shadowNotificationManager = Shadows.shadowOf(testNotificationManager);

		appNotificationManager = spy(new AppNotificationManager(context, ""));

		startOfDay = appNotificationManager.getStartOfDay();
		appDataStore = new AppDataStore(context);
		useBlockingDataStoreGet();
	}

	@After
	public void resetDataStore() {
		appDataStore.saveString(AppNotificationManager.TASK_NOTIFICATIONS_KEY, "[]");
		appDataStore.saveLong(AppNotificationManager.TASK_NOTIFICATION_DAY_KEY, 0);
		appDataStore.saveLong(AppNotificationManager.LATEST_NOTIFICATION_TIMESTAMP_KEY, 0);
	}

	@Test
	public void showsNotifications() throws JSONException {
		String jsData = "[" + getJSTaskNotificationString(startOfDay) + "]";
		appNotificationManager.showNotificationsFromJsArray(jsData);
		assertEquals(2, shadowNotificationManager.getAllNotifications().size());
	}

	@Test
	public void showsNotificationsOnNewDay() throws JSONException {
		long taskReadyAtDate = startOfDay;
		long taskEndDate = startOfDay + DAY_IN_MILLIS;
		String jsData = """
					[{
						"readyAt": %d,
						"title": "Task for report past end date",
						"contentText": "You have task past end date",
						"endDate": %d
					}]
				""".formatted(taskReadyAtDate, taskEndDate);
		assertEquals(0, appDataStore.getLongBlocking(AppNotificationManager.LATEST_NOTIFICATION_TIMESTAMP_KEY, 0));
		appNotificationManager.showNotificationsFromJsArray(jsData);
		assertEquals(1, shadowNotificationManager.getAllNotifications().size());

		appNotificationManager.cancelAllNotifications();
		assertEquals(taskReadyAtDate, appDataStore.getLongBlocking(AppNotificationManager.LATEST_NOTIFICATION_TIMESTAMP_KEY, 0));

		//move 1 day
		when(appNotificationManager.getStartOfDay()).thenReturn(startOfDay + DAY_IN_MILLIS);

		appNotificationManager.showNotificationsFromJsArray(jsData);
		assertEquals(1, shadowNotificationManager.getAllNotifications().size());
		assertEquals(taskReadyAtDate, appDataStore.getLongBlocking(AppNotificationManager.LATEST_NOTIFICATION_TIMESTAMP_KEY, 0));
	}

	@Test
	public void notShowTasksMoreThanOnceSameDay() throws JSONException {
		String jsData = "[" + getJSTaskNotificationString(startOfDay) + "]";

		appNotificationManager.showNotificationsFromJsArray(jsData);
		assertEquals(2, shadowNotificationManager.getAllNotifications().size());

		appNotificationManager.cancelAllNotifications();
		assertEquals(0, shadowNotificationManager.getAllNotifications().size());

		appNotificationManager.showNotificationsFromJsArray(jsData);
		assertEquals(0, shadowNotificationManager.getAllNotifications().size());

	}

	@Test
	public void showsOnlyNewNotifications() throws JSONException {
		String jsData = getJSTaskNotificationString(startOfDay);

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

		appNotificationManager.cancelAllNotifications();
		assertEquals(0, shadowNotificationManager.getAllNotifications().size());

		appNotificationManager.showNotificationsFromJsArray(newNotificationData);
		assertEquals(1, shadowNotificationManager.getAllNotifications().size());

	}

	@Test
	public void notShowTasksPastEndDate() throws JSONException {
		long taskReadyAtDate = startOfDay - DAY_IN_MILLIS;
		long taskEndDate = startOfDay - DAY_IN_MILLIS;
		String jsData = """
					[{
						"readyAt": %d,
						"title": "Task for report past end date",
						"contentText": "You have task past end date",
						"endDate": %d
					}]
				""".formatted(taskReadyAtDate, taskEndDate);


		appNotificationManager.showNotificationsFromJsArray(jsData);
		assertEquals(0, shadowNotificationManager.getAllNotifications().size());
	}

	private void useBlockingDataStoreGet() {
		doAnswer(invocation -> {
			appDataStore.saveLongBlocking(AppNotificationManager.LATEST_NOTIFICATION_TIMESTAMP_KEY, invocation.getArgument(0));
			return null;
		}).when(appNotificationManager).saveLatestNotificationTimestamp(anyLong());
	}

	//should be ordered by readyAt in descending order
	//Returns notifications for tasks created today and end today (1 day tasks)
	private String getJSTaskNotificationString(long taskDate) {
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
				""".formatted(taskDate, taskDate, taskDate, taskDate);
	}
}
