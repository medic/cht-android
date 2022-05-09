package org.medicmobile.webapp.mobile;

import static android.Manifest.permission.ACCESS_COARSE_LOCATION;
import static android.Manifest.permission.ACCESS_FINE_LOCATION;
import static android.app.Activity.RESULT_CANCELED;
import static android.app.Activity.RESULT_OK;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.robolectric.RuntimeEnvironment.getApplication;
import static org.robolectric.Shadows.shadowOf;

import android.app.Instrumentation.ActivityResult;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;

import androidx.lifecycle.Lifecycle;
import androidx.test.core.app.ActivityScenario;
import androidx.test.espresso.intent.Intents;
import androidx.test.ext.junit.rules.ActivityScenarioRule;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockedStatic;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowActivity;
import org.robolectric.shadows.ShadowApplicationPackageManager;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=28)
public class RequestLocationPermissionActivityTest {

	@Rule
	public ActivityScenarioRule<RequestLocationPermissionActivity> scenarioRule = new ActivityScenarioRule<>(RequestLocationPermissionActivity.class);

	private ShadowApplicationPackageManager packageManager;

	@Before
	public void setup() {
		packageManager = (ShadowApplicationPackageManager) shadowOf(getApplication().getPackageManager());
	}

	@Test
	public void onClickAllow_withPermissionGranted_setResolveOk() {
		try(MockedStatic<MedicLog> medicLogMock = mockStatic(MedicLog.class)) {
			ActivityScenario<RequestLocationPermissionActivity> scenario = scenarioRule.getScenario();

			scenario.onActivity(requestLocationPermissionActivity -> {
				//> GIVEN
				ShadowActivity shadowActivity = shadowOf(requestLocationPermissionActivity);
				shadowActivity.grantPermissions(ACCESS_FINE_LOCATION, ACCESS_COARSE_LOCATION);

				//> WHEN
				requestLocationPermissionActivity.onClickOk(null);
			});
			scenario.moveToState(Lifecycle.State.DESTROYED);

			//> THEN
			ActivityResult result = scenario.getResult();
			assertEquals(RESULT_OK, result.getResultCode());
			Intent resultIntent = result.getResultData();
			assertNull(resultIntent);

			medicLogMock.verify(() -> MedicLog.trace(
				any(RequestLocationPermissionActivity.class),
				eq("RequestLocationPermissionActivity :: User agree with prominent disclosure message.")
			));
			medicLogMock.verify(() -> MedicLog.trace(
				any(RequestLocationPermissionActivity.class),
				eq("RequestLocationPermissionActivity :: User allowed at least one location permission. Fine:%s, Coarse:%s"),
				eq(true),
				eq(true)
			));
		}
	}

	@Test
	public void onClickAllow_with_COARSE_PermissionGranted_setResolveOk() {
		try (MockedStatic<MedicLog> medicLogMock = mockStatic(MedicLog.class)) {
			ActivityScenario<RequestLocationPermissionActivity> scenario = scenarioRule.getScenario();

			scenario.onActivity(requestLocationPermissionActivity -> {
				//> GIVEN
				ShadowActivity shadowActivity = shadowOf(requestLocationPermissionActivity);

				//> WHEN
				requestLocationPermissionActivity.onClickOk(null);
				// Have to manually pass the result since the ShadowActivity.grantPermissions method
				// does not seem to support only granting some of the requested permissions.
				// https://github.com/robolectric/robolectric/issues/7272
				Intent permsIntent = new Intent();
				permsIntent.putExtra("android.content.pm.extra.REQUEST_PERMISSIONS_NAMES", new String[]{ACCESS_FINE_LOCATION, ACCESS_COARSE_LOCATION});
				permsIntent.putExtra("android.content.pm.extra.REQUEST_PERMISSIONS_RESULTS", new int[]{-1, 0});
				shadowActivity.receiveResult(shadowActivity.getNextStartedActivity(), RESULT_OK, permsIntent);
			});
			scenario.moveToState(Lifecycle.State.DESTROYED);

			//> THEN
			ActivityResult result = scenario.getResult();
			assertEquals(RESULT_OK, result.getResultCode());
			Intent resultIntent = result.getResultData();
			assertNull(resultIntent);

			medicLogMock.verify(() -> MedicLog.trace(
				any(RequestLocationPermissionActivity.class),
				eq("RequestLocationPermissionActivity :: User agree with prominent disclosure message.")
			));
			medicLogMock.verify(() -> MedicLog.trace(
				any(RequestLocationPermissionActivity.class),
				eq("RequestLocationPermissionActivity :: User allowed at least one location permission. Fine:%s, Coarse:%s"),
				eq(false),
				eq(true)
			));
		}
	}

	@Test
	public void onClickAllow_withPermissionDenied_setResolveCanceled() {
		try(MockedStatic<MedicLog> medicLogMock = mockStatic(MedicLog.class)) {
			ActivityScenario<RequestLocationPermissionActivity> scenario = scenarioRule.getScenario();

			scenario.onActivity(requestLocationPermissionActivity -> {
				Intents.init();

				//> GIVEN
				ShadowActivity shadowActivity = shadowOf(requestLocationPermissionActivity);
				packageManager.setShouldShowRequestPermissionRationale(ACCESS_FINE_LOCATION, true);
				packageManager.setShouldShowRequestPermissionRationale(ACCESS_COARSE_LOCATION, true);

				//> WHEN
				requestLocationPermissionActivity.onClickOk(null);
				Intent permissionIntent = shadowActivity.peekNextStartedActivityForResult().intent;
				shadowActivity.receiveResult(permissionIntent, RESULT_OK, null);

				//> THEN
				assertEquals("android.content.pm.action.REQUEST_PERMISSIONS", permissionIntent.getAction());
				Bundle extras = permissionIntent.getExtras();
				assertNotNull(extras);
				String[] permissions = extras.getStringArray("android.content.pm.extra.REQUEST_PERMISSIONS_NAMES");
				assertEquals(2, permissions.length);
				assertEquals(ACCESS_FINE_LOCATION, permissions[0]);
				assertEquals(ACCESS_COARSE_LOCATION, permissions[1]);

				Intents.release();
			});

			ActivityResult result = scenario.getResult();
			assertEquals(RESULT_CANCELED, result.getResultCode());
			Intent resultIntent = result.getResultData();
			assertNull(resultIntent);

			medicLogMock.verify(() -> MedicLog.trace(
				any(RequestLocationPermissionActivity.class),
				eq("RequestLocationPermissionActivity :: User agree with prominent disclosure message.")
			));
			medicLogMock.verify(() -> MedicLog.trace(
				any(RequestLocationPermissionActivity.class),
				eq("RequestLocationPermissionActivity :: User rejected location permission.")
			));
		}
	}

	@Test
	public void onClickAllow_withNeverAskAgainAndPermissionGranted_setResolveOk() {
		try(MockedStatic<MedicLog> medicLogMock = mockStatic(MedicLog.class)) {
			ActivityScenario<RequestLocationPermissionActivity> scenario = scenarioRule.getScenario();

			scenario.onActivity(requestLocationPermissionActivity -> {
				Intents.init();

				//> GIVEN
				String packageName = "package:" + requestLocationPermissionActivity.getPackageName();
				ShadowActivity shadowActivity = shadowOf(requestLocationPermissionActivity);
				// Setting "Never ask again" case.
				packageManager.setShouldShowRequestPermissionRationale(ACCESS_FINE_LOCATION, false);
				packageManager.setShouldShowRequestPermissionRationale(ACCESS_COARSE_LOCATION, false);

				//> WHEN
				requestLocationPermissionActivity.onClickOk(null);
				Intent permissionIntent = shadowActivity.peekNextStartedActivityForResult().intent;
				shadowActivity.receiveResult(permissionIntent, RESULT_OK, null);

				shadowActivity.grantPermissions(ACCESS_FINE_LOCATION, ACCESS_COARSE_LOCATION);
				Intent settingsIntent = shadowActivity.peekNextStartedActivityForResult().intent;
				shadowActivity.receiveResult(settingsIntent, RESULT_OK, null);

				//> THEN
				assertEquals(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, settingsIntent.getAction());
				assertEquals(packageName, settingsIntent.getData().toString());

				Intents.release();
			});

			ActivityResult result = scenario.getResult();
			assertEquals(RESULT_OK, result.getResultCode());
			Intent resultIntent = result.getResultData();
			assertNull(resultIntent);

			medicLogMock.verify(() -> MedicLog.trace(
				any(RequestLocationPermissionActivity.class),
				eq("RequestLocationPermissionActivity :: User agree with prominent disclosure message.")
			));
			medicLogMock.verify(() -> MedicLog.trace(
				any(RequestLocationPermissionActivity.class),
				eq("RequestLocationPermissionActivity :: User granted at least one location permission from app's settings. Fine:%s, Coarse:%s"),
				eq(true),
				eq(true)
			));
		}
	}

	@Test
	public void onClickAllow_withNeverAskAgainAnd_COARSE_PermissionGranted_setResolveOk() {
		try(MockedStatic<MedicLog> medicLogMock = mockStatic(MedicLog.class)) {
			ActivityScenario<RequestLocationPermissionActivity> scenario = scenarioRule.getScenario();

			scenario.onActivity(requestLocationPermissionActivity -> {
				Intents.init();

				//> GIVEN
				String packageName = "package:" + requestLocationPermissionActivity.getPackageName();
				ShadowActivity shadowActivity = shadowOf(requestLocationPermissionActivity);
				// Setting "Never ask again" case.
				packageManager.setShouldShowRequestPermissionRationale(ACCESS_FINE_LOCATION, false);
				packageManager.setShouldShowRequestPermissionRationale(ACCESS_COARSE_LOCATION, false);

				//> WHEN
				requestLocationPermissionActivity.onClickOk(null);
				Intent permissionIntent = shadowActivity.peekNextStartedActivityForResult().intent;
				shadowActivity.receiveResult(permissionIntent, RESULT_OK, null);

				shadowActivity.grantPermissions(ACCESS_COARSE_LOCATION);
				Intent settingsIntent = shadowActivity.peekNextStartedActivityForResult().intent;
				shadowActivity.receiveResult(settingsIntent, RESULT_OK, null);

				//> THEN
				assertEquals(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, settingsIntent.getAction());
				assertEquals(packageName, settingsIntent.getData().toString());

				Intents.release();
			});

			ActivityResult result = scenario.getResult();
			assertEquals(RESULT_OK, result.getResultCode());
			Intent resultIntent = result.getResultData();
			assertNull(resultIntent);

			medicLogMock.verify(() -> MedicLog.trace(
				any(RequestLocationPermissionActivity.class),
				eq("RequestLocationPermissionActivity :: User agree with prominent disclosure message.")
			));
			medicLogMock.verify(() -> MedicLog.trace(
				any(RequestLocationPermissionActivity.class),
				eq("RequestLocationPermissionActivity :: User granted at least one location permission from app's settings. Fine:%s, Coarse:%s"),
				eq(false),
				eq(true)
			));
		}
	}

	@Test
	public void onClickAllow_withNeverAskAgainAndPermissionDenied_setResolveCanceled() {
		try(MockedStatic<MedicLog> medicLogMock = mockStatic(MedicLog.class)) {
			ActivityScenario<RequestLocationPermissionActivity> scenario = scenarioRule.getScenario();

			scenario.onActivity(requestLocationPermissionActivity -> {
				Intents.init();

				//> GIVEN
				String packageName = "package:" + requestLocationPermissionActivity.getPackageName();
				ShadowActivity shadowActivity = shadowOf(requestLocationPermissionActivity);
				// Setting "Never ask again" case.
				packageManager.setShouldShowRequestPermissionRationale(ACCESS_FINE_LOCATION, false);
				packageManager.setShouldShowRequestPermissionRationale(ACCESS_COARSE_LOCATION, false);

				//> WHEN
				requestLocationPermissionActivity.onClickOk(null);
				Intent permissionIntent = shadowActivity.peekNextStartedActivityForResult().intent;
				shadowActivity.receiveResult(permissionIntent, RESULT_OK, null);

				Intent settingsIntent = shadowActivity.peekNextStartedActivityForResult().intent;
				shadowActivity.receiveResult(settingsIntent, RESULT_OK, null);

				//> THEN
				assertEquals(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, settingsIntent.getAction());
				assertEquals(packageName, settingsIntent.getData().toString());

				Intents.release();
			});

			ActivityResult result = scenario.getResult();
			assertEquals(RESULT_CANCELED, result.getResultCode());
			Intent resultIntent = result.getResultData();
			assertNull(resultIntent);

			medicLogMock.verify(() -> MedicLog.trace(
				any(RequestLocationPermissionActivity.class),
				eq("RequestLocationPermissionActivity :: User agree with prominent disclosure message.")
			));
			medicLogMock.verify(() -> MedicLog.trace(
				any(RequestLocationPermissionActivity.class),
				eq("RequestLocationPermissionActivity :: User rejected all location permissions twice or has selected \"never ask again\"." +
					" Sending user to the app's setting to manually grant the permission.")
			));
			medicLogMock.verify(() -> MedicLog.trace(
				any(RequestLocationPermissionActivity.class),
				eq("RequestLocationPermissionActivity :: User didn't grant location permission from app's settings.")
			));
		}
	}

	@Test
	public void onClickNegative_noIntentsStarted_setResolveCanceled() {
		try(MockedStatic<MedicLog> medicLogMock = mockStatic(MedicLog.class)) {
			ActivityScenario<RequestLocationPermissionActivity> scenario = scenarioRule.getScenario();

			scenario.onActivity(requestLocationPermissionActivity -> {
				Intents.init();
				//> WHEN
				requestLocationPermissionActivity.onClickNegative(null);

				//> THEN
				assertEquals(0, Intents.getIntents().size());

				Intents.release();
			});
			scenario.moveToState(Lifecycle.State.DESTROYED);

			ActivityResult result = scenario.getResult();
			assertEquals(RESULT_CANCELED, result.getResultCode());
			Intent resultIntent = result.getResultData();
			assertNull(resultIntent);

			medicLogMock.verify(() -> MedicLog.trace(
				any(RequestLocationPermissionActivity.class),
				eq("RequestLocationPermissionActivity :: User disagree with prominent disclosure message.")
			));
		}
	}
}
