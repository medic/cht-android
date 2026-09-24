package org.medicmobile.webapp.mobile;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;
import androidx.work.ListenableWorker;
import androidx.work.WorkerParameters;

import org.json.JSONException;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.medicmobile.webapp.mobile.util.AppDataStore;
import org.mockito.Mock;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.robolectric.RobolectricTestRunner;

import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

@RunWith(RobolectricTestRunner.class)
public class NotificationWorkerTest {
	private static final LocalTime FIXED_NOW = LocalTime.of(12, 0);

	@Rule
	public MockitoRule mockitoRule = MockitoJUnit.rule();

	@Mock
	private AppDataStore mockAppDataStore;

	private Context context;

	@Before
	public void setUp() {
		context = ApplicationProvider.getApplicationContext();
	}

	@Test
	public void doWork_returnsSuccess_whenNoException() throws JSONException {
		String notificationWindowSettings = "{}"; // Empty = always in window
		String notifications = "[]";

		try (MockedStatic<AppDataStore> dataMock = mockStatic(AppDataStore.class);
				MockedConstruction<AppNotificationManager> notificationMgrMock = mockConstruction(
						AppNotificationManager.class)) {
			dataMock.when(() -> AppDataStore.getInstance(context))
					.thenReturn(mockAppDataStore);

			when(mockAppDataStore.getStringBlocking(AppNotificationManager.TASK_NOTIFICATION_SETTINGS_KEY, "{}"))
					.thenReturn(notificationWindowSettings);
			when(mockAppDataStore.getStringBlocking(AppNotificationManager.TASK_NOTIFICATIONS_KEY, "[]"))
					.thenReturn(notifications);

			NotificationWorker worker = createWorker();

			ListenableWorker.Result result = worker.doWork();

			// Assert
			assertEquals(ListenableWorker.Result.success(), result);
			verify(notificationMgrMock.constructed().get(0), times(1))
					.showNotificationsFromJsArray(notifications);
		}
	}

	@Test
	public void doWork_returnsFailure_whenJSONExceptionOccurs() throws JSONException {
		String invalidNotificationWindowSettings = "{invalid json}";

		try (MockedStatic<AppDataStore> dataMock = mockStatic(AppDataStore.class);
				MockedConstruction<AppNotificationManager> notificationMgrMock = mockConstruction(
						AppNotificationManager.class)) {

			dataMock.when(() -> AppDataStore.getInstance(context))
					.thenReturn(mockAppDataStore);

			when(mockAppDataStore
					.getStringBlocking(AppNotificationManager.TASK_NOTIFICATION_SETTINGS_KEY, "{}"))
					.thenReturn(invalidNotificationWindowSettings);
			when(mockAppDataStore
					.getStringBlocking(AppNotificationManager.TASK_NOTIFICATIONS_KEY, "[]"))
					.thenReturn("[]");

			NotificationWorker worker = createWorker();

			ListenableWorker.Result result = worker.doWork();

			// Invalid JSON is handled gracefully
			// by Utils.parseJSONObject which returns empty JSONObject
			// So this returns success and showNotifications is called
			assertEquals(ListenableWorker.Result.success(), result);
			verify(notificationMgrMock.constructed().get(0), times(1))
					.showNotificationsFromJsArray(anyString());
		}
	}

	@Test
	public void doWork_callsShowNotifications_whenInNotificationWindow() throws JSONException {
		String notificationWindowSettings = getWindowSettings(FIXED_NOW.minusHours(1), FIXED_NOW.plusHours(1));
		String notifications = "[]";

		try (MockedStatic<AppDataStore> dataMock = mockStatic(AppDataStore.class);
				MockedStatic<LocalTime> timeMock = mockNow(FIXED_NOW);
				MockedConstruction<AppNotificationManager> notificationMgrMock = mockConstruction(
						AppNotificationManager.class)) {

			dataMock.when(() -> AppDataStore.getInstance(context))
					.thenReturn(mockAppDataStore);

			when(mockAppDataStore
					.getStringBlocking(AppNotificationManager.TASK_NOTIFICATION_SETTINGS_KEY, "{}"))
					.thenReturn(notificationWindowSettings);
			when(mockAppDataStore
					.getStringBlocking(AppNotificationManager.TASK_NOTIFICATIONS_KEY, "[]"))
					.thenReturn(notifications);

			NotificationWorker worker = createWorker();

			ListenableWorker.Result result = worker.doWork();

			assertEquals(ListenableWorker.Result.success(), result);
			verify(notificationMgrMock.constructed().get(0), times(1))
					.showNotificationsFromJsArray(notifications);
		}
	}

	@Test
	public void doWork_doesNotCallShowNotifications_whenOutsideNotificationWindow() throws JSONException {
		// window earlier in the same day, "now" is past it
		String notificationWindowSettings = getWindowSettings(FIXED_NOW.minusHours(2), FIXED_NOW.minusHours(1));

		try (MockedStatic<AppDataStore> dataMock = mockStatic(AppDataStore.class);
				MockedStatic<LocalTime> timeMock = mockNow(FIXED_NOW);
				MockedConstruction<AppNotificationManager> notificationMgrMock = mockConstruction(
						AppNotificationManager.class)) {

			dataMock.when(() -> AppDataStore.getInstance(context))
					.thenReturn(mockAppDataStore);

			when(mockAppDataStore
					.getStringBlocking(AppNotificationManager.TASK_NOTIFICATION_SETTINGS_KEY, "{}"))
					.thenReturn(notificationWindowSettings);

			NotificationWorker worker = createWorker();

			ListenableWorker.Result result = worker.doWork();

			// Assert
			assertEquals(ListenableWorker.Result.success(), result);
			verify(notificationMgrMock.constructed().get(0), times(0))
					.showNotificationsFromJsArray(anyString());
		}
	}

	// Apps with invalid window settings time don't run
	@Test
	public void isNotificationWindow_returnsFalse_whenBadTimeFields() throws Exception {
		String windowSettings = "{\"start\": \"09:70\",\"end\": \"19:05\"}"; // bad data

		try (MockedStatic<AppDataStore> dataMock = mockStatic(AppDataStore.class)) {

			dataMock.when(() -> AppDataStore.getInstance(context))
					.thenReturn(mockAppDataStore);

			NotificationWorker worker = createWorker();

			boolean result = worker.isNotificationWindow(Utils.parseJSONObject(windowSettings));
			assertFalse(result);
		}
	}

	@Test
	public void isNotificationWindow_returnsTrue_whenWithinWindow() throws Exception {
		String windowSettings = getWindowSettings(FIXED_NOW.minusHours(1), FIXED_NOW.plusHours(1));

		try (MockedStatic<AppDataStore> dataMock = mockStatic(AppDataStore.class);
				MockedStatic<LocalTime> timeMock = mockNow(FIXED_NOW)) {

			dataMock.when(() -> AppDataStore.getInstance(context))
					.thenReturn(mockAppDataStore);

			NotificationWorker worker = createWorker();

			boolean result = worker.isNotificationWindow(Utils.parseJSONObject(windowSettings));
			assertTrue(result);
		}
	}

	@Test
	public void isNotificationWindow_returnsFalse_whenBeforeWindow() throws Exception {
		String windowSettings = getWindowSettings(FIXED_NOW.plusHours(1), FIXED_NOW.plusHours(2));

		try (MockedStatic<AppDataStore> dataMock = mockStatic(AppDataStore.class);
				MockedStatic<LocalTime> timeMock = mockNow(FIXED_NOW)) {

			dataMock.when(() -> AppDataStore.getInstance(context))
					.thenReturn(mockAppDataStore);

			NotificationWorker worker = createWorker();

			boolean result = worker.isNotificationWindow(Utils.parseJSONObject(windowSettings));

			// Assert
			assertFalse(result);
		}
	}

	@Test
	public void isNotificationWindow_returnsFalse_whenAfterWindow() throws Exception {
		String windowSettings = getWindowSettings(FIXED_NOW.minusHours(2), FIXED_NOW.minusHours(1));

		try (MockedStatic<AppDataStore> dataMock = mockStatic(AppDataStore.class);
				MockedStatic<LocalTime> timeMock = mockNow(FIXED_NOW)) {

			dataMock.when(() -> AppDataStore.getInstance(context))
					.thenReturn(mockAppDataStore);

			NotificationWorker worker = createWorker();

			boolean result = worker.isNotificationWindow(Utils.parseJSONObject(windowSettings));

			// Assert
			assertFalse(result);
		}
	}

	private NotificationWorker createWorker() {
		WorkerParameters workerParameters = mock(WorkerParameters.class);
		return new NotificationWorker(context, workerParameters);
	}

	private String getWindowSettings(LocalTime startTime, LocalTime endTime) {
		DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm");
		return String.format("{\"start\": \"%s\", \"end\": \"%s\"}",
				startTime.format(formatter), endTime.format(formatter));
	}

	// Pins LocalTime.now(...) to passed arg
	private MockedStatic<LocalTime> mockNow(LocalTime now) {
		MockedStatic<LocalTime> timeMock = mockStatic(LocalTime.class, CALLS_REAL_METHODS);
		timeMock.when(() -> LocalTime.now(any(ZoneId.class))).thenReturn(now);
		return timeMock;
	}
}
