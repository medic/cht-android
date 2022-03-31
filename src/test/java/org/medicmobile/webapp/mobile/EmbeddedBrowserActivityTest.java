package org.medicmobile.webapp.mobile;

import static android.Manifest.permission.ACCESS_COARSE_LOCATION;
import static android.Manifest.permission.ACCESS_FINE_LOCATION;
import static android.app.Activity.RESULT_CANCELED;
import static android.app.Activity.RESULT_OK;
import static android.content.pm.PackageManager.PERMISSION_DENIED;
import static android.content.pm.PackageManager.PERMISSION_GRANTED;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.medicmobile.webapp.mobile.EmbeddedBrowserActivity.RequestCode;
import static org.medicmobile.webapp.mobile.EmbeddedBrowserActivity.LOCATION_PERMISSIONS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;
import static org.robolectric.Shadows.shadowOf;

import android.content.Intent;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View.OnTouchListener;

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
						RequestCode.ACCESS_LOCATION_PERMISSION.getCode()
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

	@Test
	public void onTouchEvent_withVolumeBtnPressed_startsSettingsDialogActivity() {
		scenarioRule
			.getScenario()
			.onActivity(embeddedBrowserActivity -> {
				Intents.init();
				//> GIVEN
				MotionEvent event = mock(MotionEvent.class);
				when(event.getPointerCount()).thenReturn(2);
				when(event.getActionMasked()).thenReturn(MotionEvent.ACTION_POINTER_DOWN);
				when(event.getX(0)).thenReturn((float) 262.99);
				when(event.getX(1)).thenReturn((float) 280.49);

				embeddedBrowserActivity.onKeyDown(KeyEvent.KEYCODE_VOLUME_DOWN, null);
				OnTouchListener onTouchListener = embeddedBrowserActivity.onTouchEvent();

				//>THEN
				onTouchListener.onTouch(null, event);

				when(event.getActionMasked()).thenReturn(MotionEvent.ACTION_MOVE);
				when(event.getX(0)).thenReturn((float) 562.99);
				when(event.getX(1)).thenReturn((float) 580.49);

				onTouchListener.onTouch(null, event);

				//> WHEN
				Intent intent = Intents.getIntents().get(0);
				assertNotNull(intent);
				assertEquals(
					SettingsDialogActivity.class.getName(),
					intent.getComponent().getClassName()
				);
				Intents.release();
			});
	}

	@Test
	public void onTouchEvent_withVolumeBtnReleasedBeforeSwipe_doesntStartActivity() {
		scenarioRule
			.getScenario()
			.onActivity(embeddedBrowserActivity -> {
				Intents.init();
				//> GIVEN
				MotionEvent event = mock(MotionEvent.class);
				when(event.getPointerCount()).thenReturn(2);
				when(event.getActionMasked()).thenReturn(MotionEvent.ACTION_POINTER_DOWN);
				when(event.getX(0)).thenReturn((float) 262.99);
				when(event.getX(1)).thenReturn((float) 280.49);

				embeddedBrowserActivity.onKeyDown(KeyEvent.KEYCODE_VOLUME_DOWN, null);
				OnTouchListener onTouchListener = embeddedBrowserActivity.onTouchEvent();
				embeddedBrowserActivity.onKeyUp(KeyEvent.KEYCODE_VOLUME_DOWN, null);

				//>THEN
				onTouchListener.onTouch(null, event);

				when(event.getActionMasked()).thenReturn(MotionEvent.ACTION_MOVE);
				when(event.getX(0)).thenReturn((float) 562.99);
				when(event.getX(1)).thenReturn((float) 580.49);

				onTouchListener.onTouch(null, event);

				//> WHEN
				assertEquals(0, Intents.getIntents().size());
				Intents.release();
			});
	}

	@Test
	public void onTouchEvent_withWrongVolumeBtnPressed_doesntStartActivity() {
		scenarioRule
			.getScenario()
			.onActivity(embeddedBrowserActivity -> {
				Intents.init();
				//> GIVEN
				MotionEvent event = mock(MotionEvent.class);
				when(event.getPointerCount()).thenReturn(2);
				when(event.getActionMasked()).thenReturn(MotionEvent.ACTION_POINTER_DOWN);
				when(event.getX(0)).thenReturn((float) 262.99);
				when(event.getX(1)).thenReturn((float) 280.49);

				embeddedBrowserActivity.onKeyDown(KeyEvent.KEYCODE_VOLUME_UP, null);
				OnTouchListener onTouchListener = embeddedBrowserActivity.onTouchEvent();

				//>THEN
				onTouchListener.onTouch(null, event);

				when(event.getActionMasked()).thenReturn(MotionEvent.ACTION_MOVE);
				when(event.getX(0)).thenReturn((float) 562.99);
				when(event.getX(1)).thenReturn((float) 580.49);

				onTouchListener.onTouch(null, event);

				//> WHEN
				assertEquals(0, Intents.getIntents().size());
				Intents.release();
			});
	}

	@Test
	public void onTouchEvent_withWrongSwipeLeft_doesntStartActivity() {
		scenarioRule
			.getScenario()
			.onActivity(embeddedBrowserActivity -> {
				Intents.init();
				//> GIVEN
				MotionEvent event = mock(MotionEvent.class);
				when(event.getPointerCount()).thenReturn(2);
				when(event.getActionMasked()).thenReturn(MotionEvent.ACTION_POINTER_DOWN);
				when(event.getX(0)).thenReturn((float) 562.99);
				when(event.getX(1)).thenReturn((float) 580.49);

				embeddedBrowserActivity.onKeyDown(KeyEvent.KEYCODE_VOLUME_DOWN, null);
				OnTouchListener onTouchListener = embeddedBrowserActivity.onTouchEvent();

				//>THEN
				onTouchListener.onTouch(null, event);

				when(event.getActionMasked()).thenReturn(MotionEvent.ACTION_MOVE);
				when(event.getX(0)).thenReturn((float) 162.99);
				when(event.getX(1)).thenReturn((float) 180.49);

				onTouchListener.onTouch(null, event);

				//> WHEN
				assertEquals(0, Intents.getIntents().size());
				Intents.release();
			});
	}

	@Test
	public void onTouchEvent_withSwipeEnd_doesntStartActivity() {
		scenarioRule
			.getScenario()
			.onActivity(embeddedBrowserActivity -> {
				Intents.init();
				//> GIVEN
				MotionEvent event = mock(MotionEvent.class);
				when(event.getPointerCount()).thenReturn(2);
				when(event.getActionMasked()).thenReturn(MotionEvent.ACTION_POINTER_DOWN);
				when(event.getX(0)).thenReturn((float) 162.99);
				when(event.getX(1)).thenReturn((float) 180.49);

				embeddedBrowserActivity.onKeyDown(KeyEvent.KEYCODE_VOLUME_DOWN, null);
				OnTouchListener onTouchListener = embeddedBrowserActivity.onTouchEvent();

				//>THEN
				onTouchListener.onTouch(null, event);

				when(event.getActionMasked()).thenReturn(MotionEvent.ACTION_POINTER_UP);
				onTouchListener.onTouch(null, event);

				when(event.getActionMasked()).thenReturn(MotionEvent.ACTION_MOVE);
				when(event.getX(0)).thenReturn((float) 462.99);
				when(event.getX(1)).thenReturn((float) 480.49);

				onTouchListener.onTouch(null, event);

				//> WHEN
				assertEquals(0, Intents.getIntents().size());
				Intents.release();
			});
	}
}
