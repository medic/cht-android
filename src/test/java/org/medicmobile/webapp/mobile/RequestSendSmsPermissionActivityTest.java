package org.medicmobile.webapp.mobile;

import static android.Manifest.permission.SEND_SMS;
import static android.app.Activity.RESULT_CANCELED;
import static android.app.Activity.RESULT_OK;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.robolectric.RuntimeEnvironment.getApplication;
import static org.robolectric.Shadows.shadowOf;

import android.app.Instrumentation;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;

import androidx.lifecycle.Lifecycle;
import androidx.test.core.app.ActivityScenario;
import androidx.test.espresso.intent.Intents;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockedStatic;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.shadows.ShadowActivity;
import org.robolectric.shadows.ShadowApplicationPackageManager;

@RunWith(RobolectricTestRunner.class)
public class RequestSendSmsPermissionActivityTest {
	private ShadowApplicationPackageManager packageManager;

	@Before
	public void setup() {
		packageManager = (ShadowApplicationPackageManager) shadowOf(getApplication().getPackageManager());
	}

	@Test
	public void onClickAllow_withPermissionGranted_setResolveOk() {
		try(
			MockedStatic<MedicLog> medicLogMock = mockStatic(MedicLog.class);
			ActivityScenario<RequestSendSmsPermissionActivity> scenario = ActivityScenario.launchActivityForResult(RequestSendSmsPermissionActivity.class)
		) {
			scenario.onActivity(requestSendSmsPermissionActivity -> {
				//> GIVEN
				ShadowActivity shadowActivity = shadowOf(requestSendSmsPermissionActivity);
				shadowActivity.grantPermissions(SEND_SMS);

				//> WHEN
				requestSendSmsPermissionActivity.onClickAllow(null);
			});
			scenario.moveToState(Lifecycle.State.DESTROYED);

			//> THEN
			Instrumentation.ActivityResult result = scenario.getResult();
			assertEquals(RESULT_OK, result.getResultCode());
			medicLogMock.verify(() -> MedicLog.trace(
				any(RequestSendSmsPermissionActivity.class),
				eq("RequestSendSmsPermissionActivity :: User agree with prominent disclosure message.")
			));
			medicLogMock.verify(() -> MedicLog.trace(
				any(RequestSendSmsPermissionActivity.class),
				eq("RequestSendSmsPermissionActivity :: User allowed Send SMS permission.")
			));
		}
	}

	@Test
	public void onClickAllow_withPermissionDenied_setResolveCanceled() {
		try(
			MockedStatic<MedicLog> medicLogMock = mockStatic(MedicLog.class);
			ActivityScenario<RequestSendSmsPermissionActivity> scenario = ActivityScenario.launchActivityForResult(RequestSendSmsPermissionActivity.class)
		) {
			scenario.onActivity(requestSendSmsPermissionActivity -> {
				Intents.init();

				//> GIVEN
				ShadowActivity shadowActivity = shadowOf(requestSendSmsPermissionActivity);
				packageManager.setShouldShowRequestPermissionRationale(SEND_SMS, true);

				//> WHEN
				requestSendSmsPermissionActivity.onClickAllow(null);
				Intent permissionIntent = shadowActivity.peekNextStartedActivityForResult().intent;
				shadowActivity.receiveResult(permissionIntent, RESULT_OK, null);

				//> THEN
				assertEquals("android.content.pm.action.REQUEST_PERMISSIONS", permissionIntent.getAction());
				Bundle extras = permissionIntent.getExtras();
				assertNotNull(extras);
				String[] permissions = extras.getStringArray("android.content.pm.extra.REQUEST_PERMISSIONS_NAMES");
				assertEquals(1, permissions.length);
				assertEquals(SEND_SMS, permissions[0]);

				Intents.release();
			});

			Instrumentation.ActivityResult result = scenario.getResult();
			assertEquals(RESULT_CANCELED, result.getResultCode());
			medicLogMock.verify(() -> MedicLog.trace(
				any(RequestSendSmsPermissionActivity.class),
				eq("RequestSendSmsPermissionActivity :: User agree with prominent disclosure message.")
			));
			medicLogMock.verify(() -> MedicLog.trace(
				any(RequestSendSmsPermissionActivity.class),
				eq("RequestSendSmsPermissionActivity :: User rejected Send SMS permission.")
			));
		}
	}

	@Test
	public void onClickAllow_withNeverAskAgainAndPermissionGranted_setResolveOk() {
		try(
			MockedStatic<MedicLog> medicLogMock = mockStatic(MedicLog.class);
			ActivityScenario<RequestSendSmsPermissionActivity> scenario = ActivityScenario.launchActivityForResult(RequestSendSmsPermissionActivity.class)
		) {
			scenario.onActivity(requestSendSmsPermissionActivity -> {
				Intents.init();

				//> GIVEN
				String packageName = "package:" + requestSendSmsPermissionActivity.getPackageName();
				ShadowActivity shadowActivity = shadowOf(requestSendSmsPermissionActivity);
				// Setting "Never ask again" case.
				packageManager.setShouldShowRequestPermissionRationale(SEND_SMS, false);

				//> WHEN
				requestSendSmsPermissionActivity.onClickAllow(null);
				Intent permissionIntent = shadowActivity.peekNextStartedActivityForResult().intent;
				shadowActivity.receiveResult(permissionIntent, RESULT_OK, null);

				shadowActivity.grantPermissions(SEND_SMS);
				Intent settingsIntent = shadowActivity.peekNextStartedActivityForResult().intent;
				shadowActivity.receiveResult(settingsIntent, RESULT_OK, null);

				//> THEN
				assertEquals(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, settingsIntent.getAction());
				assertEquals(packageName, settingsIntent.getData().toString());

				Intents.release();
			});

			Instrumentation.ActivityResult result = scenario.getResult();
			assertEquals(RESULT_OK, result.getResultCode());
			medicLogMock.verify(() -> MedicLog.trace(
				any(RequestSendSmsPermissionActivity.class),
				eq("RequestSendSmsPermissionActivity :: User agree with prominent disclosure message.")
			));
			medicLogMock.verify(() -> MedicLog.trace(
				any(RequestSendSmsPermissionActivity.class),
				eq("RequestSendSmsPermissionActivity :: User rejected Send SMS permission twice or has selected \"never ask again\"." +
					" Sending user to the app's setting to manually grant the permission.")
			));
			medicLogMock.verify(() -> MedicLog.trace(
				any(RequestSendSmsPermissionActivity.class),
				eq("RequestSendSmsPermissionActivity :: User granted Send SMS permission from app's settings.")
			));
		}
	}

	@Test
	public void onClickAllow_withNeverAskAgainAndPermissionDenied_setResolveCanceled() {
		try(
			MockedStatic<MedicLog> medicLogMock = mockStatic(MedicLog.class);
			ActivityScenario<RequestSendSmsPermissionActivity> scenario = ActivityScenario.launchActivityForResult(RequestSendSmsPermissionActivity.class)
		) {
			scenario.onActivity(requestSendSmsPermissionActivity -> {
				Intents.init();

				//> GIVEN
				String packageName = "package:" + requestSendSmsPermissionActivity.getPackageName();
				ShadowActivity shadowActivity = shadowOf(requestSendSmsPermissionActivity);
				// Setting "Never ask again" case.
				packageManager.setShouldShowRequestPermissionRationale(SEND_SMS, false);

				//> WHEN
				requestSendSmsPermissionActivity.onClickAllow(null);
				Intent permissionIntent = shadowActivity.peekNextStartedActivityForResult().intent;
				shadowActivity.receiveResult(permissionIntent, RESULT_OK, null);

				Intent settingsIntent = shadowActivity.peekNextStartedActivityForResult().intent;
				shadowActivity.receiveResult(settingsIntent, RESULT_OK, null);

				//> THEN
				assertEquals(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, settingsIntent.getAction());
				assertEquals(packageName, settingsIntent.getData().toString());

				Intents.release();
			});

			Instrumentation.ActivityResult result = scenario.getResult();
			assertEquals(RESULT_CANCELED, result.getResultCode());
			medicLogMock.verify(() -> MedicLog.trace(
				any(RequestSendSmsPermissionActivity.class),
				eq("RequestSendSmsPermissionActivity :: User agree with prominent disclosure message.")
			));
			medicLogMock.verify(() -> MedicLog.trace(
				any(RequestSendSmsPermissionActivity.class),
				eq("RequestSendSmsPermissionActivity :: User rejected Send SMS permission twice or has selected \"never ask again\"." +
					" Sending user to the app's setting to manually grant the permission.")
			));
			medicLogMock.verify(() -> MedicLog.trace(
				any(RequestSendSmsPermissionActivity.class),
				eq("RequestSendSmsPermissionActivity :: User didn't grant Send SMS permission from app's settings.")
			));
		}
	}

	@Test
	public void onClickNegative_noIntentsStarted_setResolveCanceled() {
		try(
			MockedStatic<MedicLog> medicLogMock = mockStatic(MedicLog.class);
			ActivityScenario<RequestSendSmsPermissionActivity> scenario = ActivityScenario.launchActivityForResult(RequestSendSmsPermissionActivity.class)
		) {
			scenario.onActivity(requestSendSmsPermissionActivity -> {
				Intents.init();
				//> WHEN
				requestSendSmsPermissionActivity.onClickDeny(null);

				//> THEN
				assertEquals(0, Intents.getIntents().size());

				Intents.release();
			});
			scenario.moveToState(Lifecycle.State.DESTROYED);

			Instrumentation.ActivityResult result = scenario.getResult();
			assertEquals(RESULT_CANCELED, result.getResultCode());
			medicLogMock.verify(() -> MedicLog.trace(
				any(RequestSendSmsPermissionActivity.class),
				eq("RequestSendSmsPermissionActivity :: User disagree with prominent disclosure message.")
			));
		}
	}
}
