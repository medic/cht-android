package org.medicmobile.webapp.mobile;

import static android.provider.Settings.ACTION_APP_OPEN_BY_DEFAULT_SETTINGS;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.robolectric.Shadows.shadowOf;

import android.app.Activity;
import android.content.Intent;

import androidx.lifecycle.Lifecycle;
import androidx.test.core.app.ActivityScenario;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockedStatic;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.shadows.ShadowActivity;

@RunWith(RobolectricTestRunner.class)
public class DomainVerificationActivityTest {
	@Test
	public void onClickOk_withSdkVersionGreaterThanS_startAppOpenByDefaultSettings() {
		try (
			MockedStatic<MedicLog> medicLogMock = mockStatic(MedicLog.class);
			ActivityScenario<DomainVerificationActivity> scenario = ActivityScenario.launch(DomainVerificationActivity.class)
		) {
			scenario.onActivity(domainVerificationActivity -> {
				ShadowActivity shadowActivity = shadowOf(domainVerificationActivity);

				domainVerificationActivity.onClickOk(null);

				Intent startedIntent = shadowActivity.getNextStartedActivity();
				assertNotNull(startedIntent);
				assertEquals(ACTION_APP_OPEN_BY_DEFAULT_SETTINGS, startedIntent.getAction());
				assertEquals("package:" + domainVerificationActivity.getPackageName(), startedIntent.getData().toString());

				medicLogMock.verify(() -> MedicLog.trace(
					any(DomainVerificationActivity.class),
					eq("DomainVerificationActivity :: User agreed with prominent disclosure message.")
				));
			});

			scenario.moveToState(Lifecycle.State.DESTROYED);
		}
	}

	@Test
	public void onClickNegative_finishActivity() {
		try (
			MockedStatic<MedicLog> medicLogMock = mockStatic(MedicLog.class);
			ActivityScenario<DomainVerificationActivity> scenario = ActivityScenario.launchActivityForResult(DomainVerificationActivity.class)
		) {
			scenario.onActivity(domainVerificationActivity -> {
				domainVerificationActivity.onClickNegative(null);

				assertEquals(Activity.RESULT_CANCELED, scenario.getResult().getResultCode());

				medicLogMock.verify(() -> MedicLog.trace(
					any(DomainVerificationActivity.class),
					eq("DomainVerificationActivity :: User disagreed with prominent disclosure message.")
				));
			});
		}
	}
}
