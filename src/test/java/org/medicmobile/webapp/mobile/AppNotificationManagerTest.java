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
		appNotificationManager = spy(new AppNotificationManager(context));
		startOfDay = appNotificationManager.getStartOfDay();
		appDataStore = AppDataStore.getInstance(context);
		useBlockingDataStoreGet();
	}

	@After
	public void resetDataStore() {
		appDataStore.saveString(AppNotificationManager.TASK_NOTIFICATIONS_KEY, "[]");
		appDataStore.saveLong(AppNotificationManager.TASK_NOTIFICATION_DAY_KEY, 0L);
		appDataStore.saveLong(AppNotificationManager.LATEST_NOTIFICATION_TIMESTAMP_KEY, 0L);
		appDataStore.saveLong(AppNotificationManager.MAX_NOTIFICATIONS_TO_SHOW_KEY, 8L);
	}

	@Test
	public void showsAndDismissesAllNotifications() throws JSONException {
		long taskDate = startOfDay;
		String jsData = "[" + getJSTaskNotificationString(taskDate, taskDate, taskDate) + "]";
		appNotificationManager.showNotificationsFromJsArray(jsData);
		assertEquals(2, shadowNotificationManager.getAllNotifications().size());
		appNotificationManager.cancelAllNotifications();
		assertEquals(0, shadowNotificationManager.getAllNotifications().size());

		assertEquals(appDataStore.getLongBlocking(AppNotificationManager.LATEST_NOTIFICATION_TIMESTAMP_KEY, 99L), taskDate);
		assertEquals(appDataStore.getLongBlocking(AppNotificationManager.TASK_NOTIFICATION_DAY_KEY, 99L), startOfDay);
	}

	@Test
	public void showsOnlyMaxAllowedNotifications() throws JSONException {
		String jsData = "[" + getJSTaskNotificationString(startOfDay, startOfDay, startOfDay) + "]";
		appDataStore.saveLongBlocking(AppNotificationManager.MAX_NOTIFICATIONS_TO_SHOW_KEY, 1L);
		appNotificationManager.showNotificationsFromJsArray(jsData);
		assertEquals(1, shadowNotificationManager.getAllNotifications().size());
	}

	@Test
	public void respectsNotificationsOrderAndStoreLatestReadyAtTimestamp() throws JSONException {
		long latestReadyAtTimestamp = startOfDay + 1000 * 60;
		String jsData = getJSTaskNotificationString(startOfDay, startOfDay, startOfDay);
		String newNotificationData = """
				[
					%s,
					{
						"_id": "id_new",
						"readyAt": %d,
						"title": "Task for report 3",
						"contentText": "You have a Task 1",
						"endDate": %d,
						"dueDate": %d
					}
				]
				""".formatted(jsData, latestReadyAtTimestamp, startOfDay, startOfDay);
		appDataStore.saveLongBlocking(AppNotificationManager.MAX_NOTIFICATIONS_TO_SHOW_KEY, 1L);
		appNotificationManager.showNotificationsFromJsArray(newNotificationData);
		assertEquals(1, shadowNotificationManager.getAllNotifications().size());
		assertEquals(latestReadyAtTimestamp, appDataStore.getLongBlocking(AppNotificationManager.LATEST_NOTIFICATION_TIMESTAMP_KEY, 0L));
	}

	@Test
	public void showsNotificationsOnNewDay() throws JSONException {
		long taskDate = startOfDay;
		String jsData = "[" + getJSTaskNotificationString(taskDate, taskDate, taskDate + DAY_IN_MILLIS) + "]";
		assertEquals(0, appDataStore.getLongBlocking(AppNotificationManager.LATEST_NOTIFICATION_TIMESTAMP_KEY, 0L));
		appNotificationManager.showNotificationsFromJsArray(jsData);
		assertEquals(2, shadowNotificationManager.getAllNotifications().size());

		appNotificationManager.cancelAllNotifications();
		//move 1 day
		when(appNotificationManager.getStartOfDay()).thenReturn(startOfDay + DAY_IN_MILLIS);

		appNotificationManager.showNotificationsFromJsArray(jsData);
		assertEquals(2, shadowNotificationManager.getAllNotifications().size());
		assertEquals(taskDate, appDataStore.getLongBlocking(AppNotificationManager.LATEST_NOTIFICATION_TIMESTAMP_KEY, 0L));
	}

	@Test
	public void notShowTasksMoreThanOnceSameDay() throws JSONException {
		String jsData = "[" + getJSTaskNotificationString(startOfDay, startOfDay, startOfDay) + "]";

		appNotificationManager.showNotificationsFromJsArray(jsData);
		assertEquals(2, shadowNotificationManager.getAllNotifications().size());

		appNotificationManager.cancelAllNotifications();
		appNotificationManager.showNotificationsFromJsArray(jsData);
		assertEquals(0, shadowNotificationManager.getAllNotifications().size());

	}

	@Test
	public void showsOnlyNewNotifications() throws JSONException {
		String jsData = getJSTaskNotificationString(startOfDay, startOfDay, startOfDay);

		appNotificationManager.showNotificationsFromJsArray("[" + jsData + "]");
		assertEquals(2, shadowNotificationManager.getAllNotifications().size());

		String newNotificationData = """
				[
					{
						"_id": "id_new",
						"readyAt": %d,
						"title": "Task for report 3",
						"contentText": "You have a Task 1",
						"endDate": %d,
						"dueDate": %d
					},
					%s
				]
				""".formatted(startOfDay + 1000, startOfDay, startOfDay, jsData);
		appNotificationManager.cancelAllNotifications();
		appNotificationManager.showNotificationsFromJsArray(newNotificationData);
		assertEquals(1, shadowNotificationManager.getAllNotifications().size());

	}

	@Test
	public void ShowsOnlyDueTaskNotifications() throws JSONException {

		String jsTaskDueTomorrow = """
					[{
						"_id": "id_due_tomorrow",
						"readyAt": %d,
						"title": "Task for report past end date",
						"contentText": "You have task past end date",
						"endDate": %d,
						"dueDate": %d
					}]
				""".formatted(startOfDay, startOfDay + (DAY_IN_MILLIS * 2), startOfDay + DAY_IN_MILLIS);
		appNotificationManager.showNotificationsFromJsArray(jsTaskDueTomorrow);
		assertEquals(0, shadowNotificationManager.getAllNotifications().size());
		//move 1 day
		when(appNotificationManager.getStartOfDay()).thenReturn(startOfDay + DAY_IN_MILLIS);
		appNotificationManager.showNotificationsFromJsArray(jsTaskDueTomorrow);
		assertEquals(1, shadowNotificationManager.getAllNotifications().size());
	}

	@Test
	public void notShowTasksPastEndDate() throws JSONException {
		long taskReadyAtDate = startOfDay - DAY_IN_MILLIS;
		long taskEndDate = startOfDay - DAY_IN_MILLIS;
		long taskDueDate = startOfDay - DAY_IN_MILLIS;
		String jsTaskDueYesterday = """
					[{
						"_id": "id_past",
						"readyAt": %d,
						"title": "Task for report past end date",
						"contentText": "You have task past end date",
						"endDate": %d,
						"dueDate": %d
					}]
				""".formatted(taskReadyAtDate, taskEndDate, taskDueDate);
		appNotificationManager.showNotificationsFromJsArray(jsTaskDueYesterday);
		assertEquals(0, shadowNotificationManager.getAllNotifications().size());
	}

	private void useBlockingDataStoreGet() {
		doAnswer(invocation -> {
			appDataStore.saveLongBlocking(AppNotificationManager.LATEST_NOTIFICATION_TIMESTAMP_KEY, invocation.getArgument(0));
			return null;
		}).when(appNotificationManager).saveLatestNotificationTimestamp(anyLong());
	}

	private String getJSTaskNotificationString(long readyAt, long dueDate, long endDate) {
		return """
					{
						"_id": "id_01",
						"readyAt": %d,
						"title": "Task for report 2",
						"contentText": "You have task 2",
						"endDate": %d,
						"dueDate": %d
					},
					{
						"_id": "id_02",
						"readyAt": %d,
						"title": "Task for report 1",
						"contentText": "You have a Task 1",
						"endDate": %d,
						"dueDate": %d
					}
				""".formatted(readyAt, endDate, dueDate, readyAt, endDate, dueDate);
	}
}
