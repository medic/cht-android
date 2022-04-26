package org.medicmobile.webapp.mobile;

import static android.Manifest.permission.ACCESS_COARSE_LOCATION;
import static android.Manifest.permission.ACCESS_FINE_LOCATION;
import static android.app.Activity.RESULT_CANCELED;
import static android.app.Activity.RESULT_OK;
import static android.content.pm.PackageManager.PERMISSION_DENIED;
import static android.content.pm.PackageManager.PERMISSION_GRANTED;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.medicmobile.webapp.mobile.EmbeddedBrowserActivity.RequestCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;
import static org.robolectric.Shadows.shadowOf;

import android.content.Intent;

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
						eq("getLocationPermissions() :: Fine and Coarse location already granted")
					));
				});
		}
	}

	@Test
	public void getLocationPermissions_with_COARSE_PermissionsGranted() {
		try(
			MockedStatic<ContextCompat> contextCompatMock = mockStatic(ContextCompat.class);
			MockedStatic<MedicLog> medicLogMock = mockStatic(MedicLog.class);
		) {
			contextCompatMock.when(() -> ContextCompat.checkSelfPermission(any(), eq(ACCESS_FINE_LOCATION))).thenReturn(PERMISSION_DENIED);
			contextCompatMock.when(() -> ContextCompat.checkSelfPermission(any(), eq(ACCESS_COARSE_LOCATION))).thenReturn(PERMISSION_GRANTED);

			scenarioRule
				.getScenario()
				.onActivity(embeddedBrowserActivity -> {
					Intents.init();

					assertFalse(embeddedBrowserActivity.getLocationPermissions());

					Intents.intended(IntentMatchers.hasComponent(RequestLocationPermissionActivity.class.getName()));
					medicLogMock.verify(() -> MedicLog.trace(
						eq(embeddedBrowserActivity),
						eq("getLocationPermissions() :: Fine or Coarse location not granted before, requesting access...")
					));

					Intents.release();
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

					Intents.intended(IntentMatchers.hasComponent(RequestLocationPermissionActivity.class.getName()));
					medicLogMock.verify(() -> MedicLog.trace(
						eq(embeddedBrowserActivity),
						eq("getLocationPermissions() :: Fine or Coarse location not granted before, requesting access...")
					));

					Intents.release();
				});
		}
	}

	@Test
	public void processStoragePermissionResult_withResponseIntent_resumeCHTExternalApp(){
		try(MockedStatic<MedicLog> medicLogMock = mockStatic(MedicLog.class)) {
			scenarioRule
				.getScenario()
				.onActivity(embeddedBrowserActivity -> {
					Intents.init();

					//> GIVEN
					Intent responseIntent = mock(Intent.class);
					when(responseIntent.getStringExtra(RequestStoragePermissionActivity.TRIGGER_CLASS))
						.thenReturn(ChtExternalAppHandler.class.getName());

					//> WHEN
					embeddedBrowserActivity.startActivityForResult(new Intent(), RequestCode.ACCESS_STORAGE_PERMISSION.getCode());

					//> THEN
					ShadowActivity shadowActivity = shadowOf(embeddedBrowserActivity);
					Intent requestIntent = shadowActivity.peekNextStartedActivityForResult().intent;

					shadowActivity.receiveResult(requestIntent, RESULT_CANCELED, responseIntent);
					medicLogMock.verify(() -> MedicLog.trace(
						eq(embeddedBrowserActivity),
						eq("EmbeddedBrowserActivity :: Resuming ChtExternalAppHandler activity. Trigger:%s"),
						eq(ChtExternalAppHandler.class.getName())
					));
					medicLogMock.verify(() -> MedicLog.trace(
						eq(embeddedBrowserActivity),
						eq("EmbeddedBrowserActivity :: No handling for trigger: %s, requestCode:"),
						eq(null),
						eq(RequestCode.ACCESS_STORAGE_PERMISSION.name())
					), never());

					Intents.release();
				});
		}
	}

	@Test
	public void processStoragePermissionResult_withResponseIntent_resumeFilePickerHander(){
		try(MockedStatic<MedicLog> medicLogMock = mockStatic(MedicLog.class)) {
			scenarioRule
				.getScenario()
				.onActivity(embeddedBrowserActivity -> {
					Intents.init();

					//> GIVEN
					Intent responseIntent = mock(Intent.class);
					when(responseIntent.getStringExtra(RequestStoragePermissionActivity.TRIGGER_CLASS))
						.thenReturn(FilePickerHandler.class.getName());

					//> WHEN
					embeddedBrowserActivity.startActivityForResult(new Intent(), RequestCode.ACCESS_STORAGE_PERMISSION.getCode());

					//> THEN
					ShadowActivity shadowActivity = shadowOf(embeddedBrowserActivity);
					Intent requestIntent = shadowActivity.peekNextStartedActivityForResult().intent;

					shadowActivity.receiveResult(requestIntent, RESULT_CANCELED, responseIntent);
					medicLogMock.verify(() -> MedicLog.trace(
						eq(embeddedBrowserActivity),
						eq("EmbeddedBrowserActivity :: Resuming FilePickerHandler process. Trigger:%s"),
						eq(FilePickerHandler.class.getName())
					));
					medicLogMock.verify(() -> MedicLog.trace(
						eq(embeddedBrowserActivity),
						eq("EmbeddedBrowserActivity :: No handling for trigger: %s, requestCode:"),
						eq(null),
						eq(RequestCode.ACCESS_STORAGE_PERMISSION.name())
					), never());

					Intents.release();
				});
		}
	}

	@Test
	public void processStoragePermissionResult_withoutResponseIntent_doesntResumeProcess(){
		try(MockedStatic<MedicLog> medicLogMock = mockStatic(MedicLog.class)) {
			scenarioRule
				.getScenario()
				.onActivity(embeddedBrowserActivity -> {
					Intents.init();

					//> WHEN
					embeddedBrowserActivity.startActivityForResult(new Intent(), RequestCode.ACCESS_STORAGE_PERMISSION.getCode());

					//> THEN
					ShadowActivity shadowActivity = shadowOf(embeddedBrowserActivity);
					Intent requestIntent = shadowActivity.peekNextStartedActivityForResult().intent;

					shadowActivity.receiveResult(requestIntent, RESULT_CANCELED, null);
					medicLogMock.verify(() -> MedicLog.trace(
						eq(embeddedBrowserActivity),
						eq("EmbeddedBrowserActivity :: Resuming FilePickerHandler process. Trigger:%s"),
						eq(FilePickerHandler.class.getName())
					), never());
					medicLogMock.verify(() -> MedicLog.trace(
						eq(embeddedBrowserActivity),
						eq("EmbeddedBrowserActivity :: Resuming ChtExternalAppHandler activity. Trigger:%s"),
						eq(ChtExternalAppHandler.class.getName())
					), never());
					medicLogMock.verify(() -> MedicLog.trace(
						eq(embeddedBrowserActivity),
						eq("EmbeddedBrowserActivity :: No handling for trigger: %s, requestCode: %s"),
						eq(null),
						eq(RequestCode.ACCESS_STORAGE_PERMISSION.name())
					));

					Intents.release();
				});
		}
	}

	@Test
	public void onActivityResult_unknownRequestCode_logRequestCode(){
		try(MockedStatic<MedicLog> medicLogMock = mockStatic(MedicLog.class)) {
			scenarioRule
				.getScenario()
				.onActivity(embeddedBrowserActivity -> {
					Intents.init();

					//> WHEN
					embeddedBrowserActivity.startActivityForResult(new Intent(), 123456789);

					//> THEN
					ShadowActivity shadowActivity = shadowOf(embeddedBrowserActivity);
					Intent requestIntent = shadowActivity.peekNextStartedActivityForResult().intent;

					shadowActivity.receiveResult(requestIntent, RESULT_OK, new Intent());
					medicLogMock.verify(() -> MedicLog.trace(
						eq(embeddedBrowserActivity),
						eq("onActivityResult() :: requestCode=%s, resultCode=%s"),
						any(),
						any()
					), never());
					medicLogMock.verify(() -> MedicLog.trace(
						eq(embeddedBrowserActivity),
						eq("onActivityResult() :: no handling for requestCode=%s"),
						eq(123456789)
					));

					Intents.release();
				});
		}
	}
}
