package org.medicmobile.webapp.mobile;

import static android.Manifest.permission.ACCESS_COARSE_LOCATION;
import static android.Manifest.permission.ACCESS_FINE_LOCATION;
import static android.app.Activity.RESULT_CANCELED;
import static android.app.Activity.RESULT_OK;
import static android.content.pm.PackageManager.PERMISSION_DENIED;
import static android.content.pm.PackageManager.PERMISSION_GRANTED;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.medicmobile.webapp.mobile.EmbeddedBrowserActivity.ACCESS_LOCATION_PERMISSION_REQUEST_CODE;
import static org.medicmobile.webapp.mobile.EmbeddedBrowserActivity.LOCATION_PERMISSIONS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.robolectric.Shadows.shadowOf;

import android.content.Intent;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.test.espresso.intent.Intents;
import androidx.test.espresso.intent.matcher.IntentMatchers;
import androidx.test.ext.junit.rules.ActivityScenarioRule;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockedStatic;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowActivity;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=28)
public class EmbeddedBrowserActivityTest {

	@Rule
	public ActivityScenarioRule<EmbeddedBrowserActivity> scenarioRule = new ActivityScenarioRule<>(EmbeddedBrowserActivity.class);

	@Test
	public void isMigrationRunning_returnsFlagCorrectly() {
		scenarioRule
			.getScenario()
			.onActivity(embeddedBrowserActivity -> {
				embeddedBrowserActivity.setMigrationRunning(true);
				assertTrue(embeddedBrowserActivity.isMigrationRunning());

				embeddedBrowserActivity.setMigrationRunning(false);
				assertFalse(embeddedBrowserActivity.isMigrationRunning());
			});
	}

	@Test
	public void getLocationPermissions_withPermissionsGranted_returnsTrue() {
		try(
			MockedStatic<ContextCompat> contextCompatMock = mockStatic(ContextCompat.class);
			MockedStatic<MedicLog> medicLogMock = mockStatic(MedicLog.class);
			) {
			contextCompatMock.when(() -> ContextCompat.checkSelfPermission(any(), eq(ACCESS_FINE_LOCATION))).thenReturn(PERMISSION_GRANTED);
			contextCompatMock.when(() -> ContextCompat.checkSelfPermission(any(), eq(ACCESS_COARSE_LOCATION))).thenReturn(PERMISSION_GRANTED);

			scenarioRule
				.getScenario()
				.onActivity(embeddedBrowserActivity -> {
					assertTrue(embeddedBrowserActivity.getLocationPermissions());
					Intents.times(0);

					medicLogMock.verify(() -> MedicLog.trace(
						eq(embeddedBrowserActivity),
						eq("getLocationPermissions() :: already granted")
					));
				});
		}
	}

	@Test
	public void getLocationPermissions_withPermissionsDenied_returnsFalse() {
		try(
			MockedStatic<ContextCompat> contextCompatMock = mockStatic(ContextCompat.class);
			MockedStatic<MedicLog> medicLogMock = mockStatic(MedicLog.class);
		) {
			contextCompatMock.when(() -> ContextCompat.checkSelfPermission(any(), eq(ACCESS_FINE_LOCATION))).thenReturn(PERMISSION_DENIED);
			contextCompatMock.when(() -> ContextCompat.checkSelfPermission(any(), eq(ACCESS_COARSE_LOCATION))).thenReturn(PERMISSION_DENIED);

			scenarioRule
				.getScenario()
				.onActivity(embeddedBrowserActivity -> {
					Intents.init();

					assertFalse(embeddedBrowserActivity.getLocationPermissions());

					Intents.intended(IntentMatchers.hasComponent(RequestPermissionActivity.class.getName()));
					medicLogMock.verify(() -> MedicLog.trace(
						eq(embeddedBrowserActivity),
						eq("getLocationPermissions() :: location not granted before, requesting access...")
					));

					Intents.release();
				});
		}
	}

	@Test
	public void getLocationPermissions_withPermissionsDenied_requestPermissions() {
		try(
			MockedStatic<ContextCompat> contextCompatMock = mockStatic(ContextCompat.class);
			MockedStatic<ActivityCompat> activityCompatMock = mockStatic(ActivityCompat.class);
		) {
			contextCompatMock.when(() -> ContextCompat.checkSelfPermission(any(), eq(ACCESS_FINE_LOCATION))).thenReturn(PERMISSION_DENIED);
			contextCompatMock.when(() -> ContextCompat.checkSelfPermission(any(), eq(ACCESS_COARSE_LOCATION))).thenReturn(PERMISSION_DENIED);

			scenarioRule
				.getScenario()
				.onActivity(embeddedBrowserActivity -> {
					Intents.init();

					assertFalse(embeddedBrowserActivity.getLocationPermissions());
					Intents.intended(IntentMatchers.hasComponent(RequestPermissionActivity.class.getName()));

					ShadowActivity shadowActivity = shadowOf(embeddedBrowserActivity);
					Intent requestIntent = shadowActivity.peekNextStartedActivityForResult().intent;
					shadowActivity.receiveResult(requestIntent, RESULT_OK, new Intent());

					activityCompatMock.verify(() -> ActivityCompat.requestPermissions(
						embeddedBrowserActivity,
						LOCATION_PERMISSIONS,
						ACCESS_LOCATION_PERMISSION_REQUEST_CODE
					));

					Intents.release();
				});
		}
	}

	@Test
	public void getLocationPermissions_withPermissionsAlreadyDenied_returnsFalse() {
		try(
			MockedStatic<ContextCompat> contextCompatMock = mockStatic(ContextCompat.class);
			MockedStatic<MedicLog> medicLogMock = mockStatic(MedicLog.class);
		) {
			contextCompatMock.when(() -> ContextCompat.checkSelfPermission(any(), eq(ACCESS_FINE_LOCATION))).thenReturn(PERMISSION_DENIED);
			contextCompatMock.when(() -> ContextCompat.checkSelfPermission(any(), eq(ACCESS_COARSE_LOCATION))).thenReturn(PERMISSION_DENIED);

			scenarioRule
				.getScenario()
				.onActivity(embeddedBrowserActivity -> {
					Intents.init();

					assertFalse(embeddedBrowserActivity.getLocationPermissions());
					Intents.intended(IntentMatchers.hasComponent(RequestPermissionActivity.class.getName()));

					ShadowActivity shadowActivity = shadowOf(embeddedBrowserActivity);
					Intent requestIntent = shadowActivity.peekNextStartedActivityForResult().intent;
					shadowActivity.receiveResult(requestIntent, RESULT_CANCELED, new Intent());

					assertFalse(embeddedBrowserActivity.getLocationPermissions());
					medicLogMock.verify(() -> MedicLog.trace(
						eq(embeddedBrowserActivity),
						eq("getLocationPermissions() :: user has previously denied to share location")
					));

					Intents.release();
				});
		}
	}
}
